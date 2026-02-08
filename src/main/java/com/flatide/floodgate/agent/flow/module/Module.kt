/*
 * MIT License
 *
 * Copyright (c) 2022 FLATIDE LC.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.flatide.floodgate.agent.flow.module

import com.flatide.floodgate.ConfigurationManager
import com.flatide.floodgate.FloodgateConstants
import com.flatide.floodgate.agent.template.DocumentTemplate
import com.flatide.floodgate.system.utils.PropertyMap
import com.flatide.floodgate.agent.connector.ConnectorTag
import com.flatide.floodgate.agent.Context
import com.flatide.floodgate.agent.Context.CONTEXT_KEY
import com.flatide.floodgate.agent.connector.Connector
import com.flatide.floodgate.agent.flow.Flow
import com.flatide.floodgate.agent.flow.FlowContext
import com.flatide.floodgate.agent.flow.FlowMockup
import com.flatide.floodgate.agent.flow.FlowTag
import com.flatide.floodgate.agent.flow.module.ModuleContext.MODULE_CONTEXT
import com.flatide.floodgate.agent.connector.ConnectorDB
import com.flatide.floodgate.agent.connector.ConnectorFactory
import com.flatide.floodgate.agent.flow.stream.FGInputStream
import com.flatide.floodgate.agent.flow.stream.FGSharableInputStream
import com.flatide.floodgate.agent.flow.stream.Payload
import com.flatide.floodgate.agent.flow.stream.carrier.Carrier
import com.flatide.floodgate.agent.flow.stream.carrier.container.JSONContainer
import com.flatide.floodgate.agent.handler.FloodgateHandlerManager
import com.flatide.floodgate.agent.handler.FloodgateHandlerManager.Step
import com.flatide.floodgate.agent.flow.rule.MappingRule
import com.flatide.floodgate.agent.meta.MetaManager
import org.apache.logging.log4j.LogManager

import java.util.LinkedList
import java.util.UUID

@Suppress("UNCHECKED_CAST")
class Module(
    val flow: Flow,
    private val name: String,
    private val sequences: Map<String, Any>?
) {
    companion object {
        // NOTE spring boot의 logback을 사용하려면 LogFactory를 사용해야 하나, 이 경우 log4j 1.x와 충돌함(SoapUI가 사용)
        private val logger = LogManager.getLogger(Module::class.java)
    }

    private var flowContext: FlowContext
    private val context: ModuleContext

    val id: String

    private var connector: Connector? = null
    private var connInfo: Map<*, *>? = null

    var progress: Int = 0

    var result: String? = null

    var msg: String = ""

    private var resultList: List<Map<Any?, Any?>>? = null

    init {
        val uuid = UUID.randomUUID()
        this.id = uuid.toString()

        context = ModuleContext()

        flowContext = flow.context!!
        context.add(CONTEXT_KEY.CHANNEL_CONTEXT, flow.channelContext)
    }

    fun getFlowContext(): FlowContext {
        return flowContext
    }

    fun getContext(): ModuleContext {
        return context
    }

    fun getName(): String {
        return name
    }

    fun getSequences(): Map<String, Any> {
        return sequences!!
    }


    /*
        FlowContext의 input에 대한 처리
    */

    fun processBefore(flow: Flow, flowContext: FlowContext) {
        if (flow !is FlowMockup) {
            FloodgateHandlerManager.handle(Step.MODULE_IN, flow.channelContext, this)
        }
        try {
            if (this.sequences != null) {
                val connectRef = this.sequences[FlowTag.CONNECT.name]
                if (connectRef == null) {
                    logger.info(flowContext.id + " : No connect info for module " + this.name)
                } else {
                    if (connectRef is String) {
                        val table = ConfigurationManager.getString(FloodgateConstants.META_SOURCE_TABLE_FOR_DATASOURCE)
                        val connMeta = MetaManager.read(table!!, connectRef)
                        connInfo = connMeta!!["DATA"] as Map<*, *>
                    } else {
                        connInfo = connectRef as Map<*, *>
                    }

                    connector = ConnectorFactory.getConnector(connInfo as Map<String, Any>)

                    val method = connInfo!![ConnectorTag.CONNECTOR.name] as String
                    if (method != "JDBC") {
                        val templateName = this.sequences[FlowTag.TEMPLATE.name] as String?
                        var builtInTemplate = ""
                        if (templateName == null || templateName.isEmpty()) {
                            builtInTemplate = if ("FILE" == method) "JSON" else method
                        }
                        val documentTemplate = DocumentTemplate.get(templateName, builtInTemplate, true)
                        connector!!.documentTemplate = documentTemplate
                    }

                    this.context.add(MODULE_CONTEXT.CONNECT_INFO, connInfo!!)
                    this.context.add(MODULE_CONTEXT.SEQUENCE, this.sequences)

                    if (this.flow is FlowMockup) {
                        val channelContext = flowContext.get(Context.CONTEXT_KEY.CHANNEL_CONTEXT) as Context

                        val itemList = channelContext.get("ITEM") as List<Map<String, Any>>

                        val ruleName = this.sequences[FlowTag.RULE.name] as String
                        val rule = flowContext.rules[ruleName]!!
                        val dbType = connInfo!![ConnectorTag.DBTYPE.toString()] as String?
                        rule.functionProcessor = connector!!.getFunctionProcessor(dbType ?: "")

                        var query = ConnectorDB.buildInsertSql(this.context, rule, itemList)
                        val param = rule.getParam()

                        for (p in param) {
                            query = query.replaceFirst("\\?".toRegex(), p)
                        }
                        channelContext.add("QUERY", query)
                        return
                    }
                }

                connector!!.connect(flowContext, this)

                val action = this.sequences[FlowTag.ACTION.name] as String

                when (FlowTag.valueOf(action)) {
                    FlowTag.CHECK -> {
                        connector!!.check()
                        flowContext.current = null
                        result = "success"
                    }
                    FlowTag.COUNT -> {
                        connector!!.count()
                        flowContext.current = null
                        result = "success"
                    }
                    FlowTag.READ -> {
                        val ruleName = this.sequences[FlowTag.RULE.name] as String
                        val rule = flowContext.rules[ruleName]!!

                        connector!!.beforeRead(rule)
                    }
                    FlowTag.CREATE -> {
                        val ruleName = this.sequences[FlowTag.RULE.name] as String
                        val rule = flowContext.rules[ruleName]!!

                        val dbType = connInfo!![ConnectorTag.DBTYPE.toString()] as String?
                        rule.functionProcessor = connector!!.getFunctionProcessor(dbType ?: "")

                        connector!!.beforeCreate(rule)
                    }
                    FlowTag.DELETE -> {
                        connector!!.delete()
                        result = "success"
                    }
                    else -> {}
                }
            }
        } catch (e: Exception) {
            result = "fail"
            msg = e.message ?: ""
            e.printStackTrace()
            throw e
        }
    }

    fun processPartially(flow: Flow, flowContext: FlowContext, buffer: MutableList<Any?>): List<Any?>? {
        try {
            val action = this.sequences!![FlowTag.ACTION.name] as String

            when (FlowTag.valueOf(action)) {
                FlowTag.READ -> {
                    val ruleName = this.sequences[FlowTag.RULE.name] as String
                    val rule = flowContext.rules[ruleName]!!

                    val limit = PropertyMap.getIntegerDefault(this.sequences, FlowTag.BUFFERSIZE, 1)
                    //List part = connector.readPartially(rule);
                    connector!!.readBuffer(rule, buffer, limit)
                    if (buffer.isEmpty()) {
                        result = "success"
                        return null
                    }

                    val debug = this.sequences[FlowTag.DEBUG.name] as Boolean?
                    if (debug != null && debug) {
                        logger.info(buffer)
                    }

                    return buffer
                }
                FlowTag.CREATE -> {
                    val ruleName = this.sequences[FlowTag.RULE.name] as String
                    val rule = flowContext.rules[ruleName]!!

                    val items: List<Map<Any?, Any?>>? = if (buffer.isEmpty()) {
                        null
                    } else {
                        buffer as List<Map<Any?, Any?>>
                    }

                    connector!!.createPartially(items!!, rule)
                    if (items == null) {
                        result = "success"
                        msg = ""
                    }
                    return null
                }
                else -> return null
            }
        } catch (e: Exception) {
            connector!!.rollback()
            val errorPos = connector!!.getErrorPosition()
            result = "fail"

            var errMsg = e.message
            if (errorPos >= 0) {
                errMsg = "#${errorPos + 1} : $errMsg"
            }
            msg = errMsg ?: ""
            e.printStackTrace()
            throw e
        }
    }

    fun process(flow: Flow, flowContext: FlowContext) {
        try {
            val action = this.sequences!![FlowTag.ACTION.name] as String

            when (FlowTag.valueOf(action)) {
                FlowTag.READ -> {
                    val ruleName = this.sequences[FlowTag.RULE.name] as String
                    val rule = flowContext.rules[ruleName]!!

                    resultList = connector!!.read(rule)

                    if ("BYPASS" == sequences[FlowTag.RESULT.name]) {
                        val data = HashMap<String, Any?>()
                        data["ITEMS"] = resultList
                        val stream: FGInputStream = FGSharableInputStream(JSONContainer(data, "HEADER", "ITEMS"))
                        flowContext.current = stream

                        val debug = this.sequences[FlowTag.DEBUG.name] as Boolean?
                        if (debug != null && debug) {
                            if (stream != null) {
                                val carrier = stream.carrier

                                val temp = carrier.getSnapshot() as Map<*, *>
                                logger.info(temp)
                            }
                        }
                    } else {
                        flowContext.current = null
                    }
                }
                FlowTag.CREATE -> {
                    val ruleName = this.sequences[FlowTag.RULE.name] as String
                    val rule = flowContext.rules[ruleName]!!

                    val dbType = connInfo!![ConnectorTag.DBTYPE.toString()] as String?
                    rule.functionProcessor = connector!!.getFunctionProcessor(dbType ?: "")

                    var payload: Payload? = null

                    val currentStream = flowContext.current
                    if (currentStream != null) {
                        payload = flowContext.current!!.subscribe()
                    }

                    var sent: Long = 0
                    val itemList: MutableList<Map<*, *>> = LinkedList()

                    val batchSize = PropertyMap.getIntegerDefault(this.sequences, FlowTag.BATCHSIZE, 1)
                    try {
                        while (payload!!.next() != -1L) {
                            val dataList = payload.data
                            val length = payload.getReadLength()

                            if (dataList is List<*>) {
                                val temp = dataList as List<Map<*, *>>
                                itemList.addAll(temp)

                                if (batchSize > 0 && itemList.size >= batchSize) {
                                    val sub = itemList.subList(0, batchSize)
                                    connector!!.create(sub as List<Map<Any?, Any?>>, rule)
                                    sent += batchSize
                                    sub.clear()
                                }
                            } else {
                                sent += length
                            }
                        }

                        if (itemList.size > 0) {
                            connector!!.create(itemList as List<Map<Any?, Any?>>, rule)
                            sent += itemList.size
                            itemList.clear()
                        }

                        flowContext.current!!.unsubscribe(payload)
                        flowContext.current = null
                    } catch (e: Exception) {
                        connector!!.rollback()
                        throw e
                    }
                }
                else -> {}
            }

            result = "success"
            msg = ""
        } catch (e: Exception) {
            result = "fail"
            msg = e.message ?: ""
            e.printStackTrace()
            throw e
        }
    }

    fun processAfter(flow: Flow, context: FlowContext) {
        try {
            val action = this.sequences!![FlowTag.ACTION.name] as String

            when (FlowTag.valueOf(action)) {
                FlowTag.READ -> {
                    connector!!.afterRead()

                    //flowContext.current = null;
                }
                FlowTag.CREATE -> {
                    val ruleName = this.sequences[FlowTag.RULE.name] as String
                    val rule = flowContext.rules[ruleName]!!

                    val after = PropertyMap.getStringDefault(this.sequences, "AFTER", "COMMIT")
                    if ("COMMIT" == after) {
                        connector!!.commit()
                    } else if ("ROLLBACK" == after) {
                        connector!!.rollback()
                    }
                    connector!!.afterCreate(rule)

                    flowContext.current = null
                }
                else -> {}
            }

            connector!!.close()

            val next = this.sequences[FlowTag.CALL.name] as String?
            flowContext.setNext(next)
        } catch (e: Exception) {
            result = "fail"
            msg = e.message ?: ""
            e.printStackTrace()
            throw e
        } finally {
            if (flow !is FlowMockup) {
                FloodgateHandlerManager.handle(Step.MODULE_OUT, flow.channelContext, this)
            }
        }
    }
}
