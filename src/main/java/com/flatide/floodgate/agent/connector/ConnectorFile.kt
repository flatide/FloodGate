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

package com.flatide.floodgate.agent.connector

import com.flatide.floodgate.agent.Context
import com.flatide.floodgate.agent.flow.rule.MappingRule
import com.flatide.floodgate.agent.flow.rule.MappingRuleItem
import com.flatide.floodgate.agent.flow.rule.FunctionProcessor
import com.flatide.floodgate.agent.flow.FlowTag
import com.flatide.floodgate.agent.flow.module.Module
import com.flatide.floodgate.agent.flow.module.ModuleContext
import com.flatide.floodgate.agent.flow.module.ModuleContext.MODULE_CONTEXT
import com.flatide.floodgate.system.FlowEnv
import com.flatide.floodgate.system.utils.PropertyMap

import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.sql.Date

class ConnectorFile : ConnectorBase() {
    // NOTE spring boot의 logback을 사용하려면 LogFactory를 사용해야 하나, 이 경우 log4j 1.x와 충돌함(SoapUI가 사용)
    //Logger logger = LogManager.getLogger(FileConnector.class);
    var module: Module? = null

    private var outputStream: BufferedOutputStream? = null

    private var sent: Int = 0
    private var errorPosition: Int = -1

    private class FileFunctionProcessor : FunctionProcessor {
        override fun process(item: MappingRuleItem): Any? {
            val func = item.sourceName
            val function = MappingRule.Function.valueOf(func)

            return when (function) {
                MappingRule.Function.TARGET_DATE -> {
                    val current = System.currentTimeMillis()
                    Date(current)
                }
                MappingRule.Function.DATE -> {
                    val current = System.currentTimeMillis()
                    Date(current)
                }
                MappingRule.Function.SEQ -> FlowEnv.getSequence()
            }
        }
    }

    companion object {
        val PRE_PROCESSOR: FunctionProcessor = FileFunctionProcessor()
    }

    override fun getFunctionProcessor(type: String): FunctionProcessor {
        return PRE_PROCESSOR
    }

    override fun connect(context: Context, module: Module) {
        this.module = module

        @Suppress("UNCHECKED_CAST")
        val connectInfo = module.getContext().get(MODULE_CONTEXT.CONNECT_INFO) as Map<*, *>
        val url = PropertyMap.getString(connectInfo, ConnectorTag.URL)

        val target = PropertyMap.getString(this.module!!.getSequences(), FlowTag.TARGET)
        val filename = context.evaluate(target!!)
        this.outputStream = BufferedOutputStream(FileOutputStream(File("$url/$filename")))
    }

    override fun beforeCreate(rule: MappingRule) {
        val context = this.module!!.getContext()
        val header = documentTemplate!!.makeHeader<Any>(context, rule, null)
        this.outputStream!!.write(header.toByteArray())
    }

    override fun create(items: List<Map<Any?, Any?>>, mappingRule: MappingRule): Int {
        val context = this.module!!.getContext()
        val body = documentTemplate!!.makeBody(context, mappingRule, items, sent.toLong())

        this.outputStream!!.write(body.toByteArray())

        this.sent += items.size
        return items.size
    }

    override fun createPartially(items: List<Map<Any?, Any?>>, mappingRule: MappingRule): Int {
        return 0
    }

    fun creatingBinary(item: ByteArray, size: Long, sent: Long): Int {
        this.outputStream!!.write(item, 0, size.toInt())
        this.sent += size.toInt()
        return size.toInt()
    }

    override fun afterCreate(rule: MappingRule) {
        val context = this.module!!.getContext()
        val footer = documentTemplate!!.makeFooter<Any>(context, rule, null)
        this.outputStream!!.write(footer.toByteArray())

        this.outputStream!!.flush()
    }

    override fun beforeRead(rule: MappingRule) {
    }

    override fun readBuffer(rule: MappingRule, buffer: MutableList<Any?>, limit: Int): Int {
        return 0
    }

    override fun readPartially(rule: MappingRule): List<Map<Any?, Any?>> {
        return emptyList()
    }

    override fun afterRead() {
    }

    override fun read(rule: MappingRule): List<Map<Any?, Any?>> {
        return emptyList()
    }

    override fun update(mappingRule: MappingRule, data: Any?): Int {
        return 0
    }

    override fun delete(): Int {
        return 0
    }

    override fun commit() {
    }

    override fun rollback() {
    }

    override fun close() {
        try {
            if (this.outputStream != null) {
                this.outputStream!!.flush()
                this.outputStream!!.close()
            }
        } finally {
            this.outputStream = null
        }
    }

    override fun getSent(): Int {
        return sent
    }

    override fun getErrorPosition(): Int {
        return errorPosition
    }
}
