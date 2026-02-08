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

package com.flatide.floodgate.agent

import com.fasterxml.jackson.databind.ObjectMapper
import com.flatide.floodgate.ConfigurationManager
import com.flatide.floodgate.FloodgateConstants
import com.flatide.floodgate.agent.flow.Flow
import com.flatide.floodgate.agent.flow.FlowTag
import com.flatide.floodgate.agent.flow.stream.FGInputStream
import com.flatide.floodgate.agent.handler.FloodgateHandlerManager
import com.flatide.floodgate.agent.handler.FloodgateHandlerManager.Step
import com.flatide.floodgate.agent.meta.MetaManager
import com.flatide.floodgate.agent.spool.SpoolingManager

import java.io.File
import java.util.HashMap
import java.util.concurrent.Callable

@Suppress("UNCHECKED_CAST")
class ChannelJob(
    var target: String,
    var context: Context,
    var current: FGInputStream
) : Callable<Map<String, Any>> {

    override fun call(): Map<String, Any> {
        var result: MutableMap<String, Any> = HashMap()
        val log: MutableMap<String, Any> = HashMap()

        val flow = Flow(this.target, this.context)

        try {
            // If FLOW exists in request body when API type is Instant Interfacing
            var flowInfo = this.context.get(Context.CONTEXT_KEY.FLOW_META.toString()) as? Map<String, Any>
            if (flowInfo == null) {
                val flowInfoTable = ConfigurationManager.getString(FloodgateConstants.META_SOURCE_TABLE_FOR_FLOW)!!
                val flowMeta = MetaManager.read(flowInfoTable, target)
                flowInfo = flowMeta?.get("DATA") as Map<String, Any>
            }

            val flowId = flow.flowId
            FloodgateHandlerManager.handle(Step.FLOW_IN, context, flow)

            val spooling = flowInfo[FlowTag.SPOOLING.name]
            if (spooling != null && spooling as Boolean) {
                // backup ChannelJob with flowId
                val path = ConfigurationManager.getString(FloodgateConstants.CHANNEL_SPOOLING_FOLDER)
                val folder = File(path)
                if (!folder.exists()) {
                    folder.mkdir()
                }

                val contextMap = this.context.map

                val newContext = HashMap<String, Any>()
                for ((key, value) in contextMap) {
                    if (Context.CONTEXT_KEY.REQUEST_BODY.name != key) {
                        newContext[key] = value
                    }
                }

                val spoolInfo = HashMap<String, Any>()
                spoolInfo["target"] = this.target
                spoolInfo["context"] = newContext

                val mapper = ObjectMapper()
                mapper.writerWithDefaultPrettyPrinter().writeValue(File("$path/$flowId"), spoolInfo)

                SpoolingManager.addJob(flowId)

                result["result"] = "spooled"
                result["ID"] = flowId.toString()
                log["RESULT"] = "spooled"
            } else {
                flow.prepare(flowInfo, current)
                val returnStream = flow.process()
                if (returnStream != null) {
                    val carrier = returnStream.carrier

                    result = carrier.getSnapshot() as MutableMap<String, Any>
                } else {
                    result["result"] = "success"
                    result["reason"] = ""
                }
                log["RESULT"] = "success"
                log["MSG"] = ""
            }
        } catch (e: Exception) {
            e.printStackTrace()
            result["result"] = "fail"
            result["reason"] = e.message ?: ""
            log["RESULT"] = "fail"
            log["MSG"] = e.message ?: ""
        }

        flow.result = log["RESULT"] as String?
        flow.msg = log["MSG"] as String?

        FloodgateHandlerManager.handle(Step.FLOW_OUT, context, flow)

        return result
    }
}
