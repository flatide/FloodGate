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

package com.flatide.floodgate.core.agent.flow

import com.flatide.floodgate.core.agent.Context
import com.flatide.floodgate.core.agent.Context.CONTEXT_KEY
import com.flatide.floodgate.core.agent.flow.stream.FGInputStream
import com.flatide.floodgate.core.system.utils.PropertyMap
import com.flatide.floodgate.core.agent.flow.module.Module
import com.flatide.floodgate.core.agent.flow.rule.MappingRule

import java.util.UUID

@Suppress("UNCHECKED_CAST")
open class Flow {
    val channelContext: Context
    var context: FlowContext? = null
        protected set

    val flowId: String
    val targetId: String

    var result: String? = null
    var msg: String? = null

    constructor(targetId: String, context: Context) {
        val id = UUID.randomUUID()

        this.flowId = id.toString()
        this.targetId = targetId

        this.channelContext = context
    }

    constructor(flowId: String, targetId: String, context: Context) {
        this.flowId = flowId
        this.targetId = targetId

        this.channelContext = context
    }

    fun prepare(flowInfo: Map<String, Any>, input: FGInputStream?) {
        this.context = FlowContext(this.flowId, flowInfo)
        this.context!!.current = input

        val method = channelContext.getString(Context.CONTEXT_KEY.HTTP_REQUEST_METHOD)
        var entryMap: Any? = flowInfo[FlowTag.ENTRY.name]
        if (entryMap is Map<*, *>) {
            entryMap = (entryMap as Map<String, String>)[method]
        }
        this.context!!.entry = entryMap as String

        this.context!!.debug = (flowInfo[FlowTag.DEBUG.name] as Boolean?) ?: false
        this.context!!.add(CONTEXT_KEY.CHANNEL_CONTEXT, channelContext)

        // Module
        val mods = flowInfo[FlowTag.MODULE.name] as Map<String, Map<String, Any>>?
        if (mods != null) {
            for (entry in mods.entries) {
                val module = Module(this, entry.key, entry.value)
                this.context!!.modules[entry.key] = module
            }
        }

        // Connect Info
        /*HashMap<String, Object> connectInfoData = (HashMap) meta.get(FlowTag.CONNECT.name());
        if( connectInfoData != null ) {
            this.connectInfo = new ConnectInfo(connectInfoData);
        }*/

        // Rule
        val mappingData = flowInfo[FlowTag.RULE.name] as Map<String, Any>?
        //HashMap<String, Object> mappingData = (HashMap) meta.get(FlowTag.RULE.name());
        if (mappingData != null) {
            for (entry in mappingData.entries) {
                val rule = MappingRule()
                val temp = entry.value as Map<String, String>
                rule.addRule(temp)
                this.context!!.rules[entry.key] = rule
            }
        }
    }

    open fun process(): FGInputStream? {
        var entry = context!!.getString("CHANNEL_CONTEXT.REQUEST_PARAMS.entry")
        if (entry == null || entry.isEmpty()) {
            entry = context!!.entry
        }

        this.context!!.setNext(entry)
        while (this.context!!.hasNext()) {
            val module = this.context!!.next()!!
            var joinModule: Module? = null

            try {
                module.processBefore(this, context!!)

                val joinTarget = PropertyMap.getStringDefault(module.getSequences(), FlowTag.PIPE, "")
                if (joinTarget.isNotEmpty()) {
                    this.context!!.setNext(joinTarget)
                    if (this.context!!.hasNext()) {
                        joinModule = this.context!!.next()
                        joinModule!!.processBefore(this, context!!)
                    } else {
                        throw Exception("Cannot find target module to pipe with.")
                    }

                    var complete = false

                    val buffer = ArrayList<Any?>()
                    while (!complete) {
                        //List part = module.processPartially(this, context, null);
                        module.processPartially(this, context!!, buffer)
                        if (buffer.isEmpty()) {
                            complete = true
                        }
                        joinModule!!.processPartially(this, context!!, buffer)
                    }
                } else {
                    module.process(this, context!!)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                throw e
            } finally {
                try {
                    module.processAfter(this, context!!)
                } finally {
                    if (joinModule != null) {
                        joinModule.processAfter(this, context!!)
                    }
                }
            }
        }

        return context!!.current
    }
}
