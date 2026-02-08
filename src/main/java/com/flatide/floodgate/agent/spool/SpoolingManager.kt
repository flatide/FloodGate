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

package com.flatide.floodgate.agent.spool

import com.fasterxml.jackson.databind.ObjectMapper
import com.flatide.floodgate.ConfigurationManager
import com.flatide.floodgate.FloodgateConstants
import com.flatide.floodgate.agent.Context
import com.flatide.floodgate.agent.flow.stream.FGSharableInputStream
import com.flatide.floodgate.agent.flow.stream.carrier.container.JSONContainer
import com.flatide.floodgate.agent.meta.MetaManager

import java.io.File
import java.util.LinkedHashMap
import java.util.concurrent.*

import org.apache.logging.log4j.LogManager

object SpoolingManager {
    private val logger = LogManager.getLogger(SpoolingManager::class.java)

    val queue: LinkedBlockingQueue<String> = LinkedBlockingQueue()

    val executor: Array<ThreadPoolExecutor> = Array(4) {
        Executors.newFixedThreadPool(1) as ThreadPoolExecutor
    }

    init {
        val thread = Thread {
            logger.info("Spooling thread started...")
            val spoolingPath = ConfigurationManager.getString(FloodgateConstants.CHANNEL_SPOOLING_FOLDER)
            //String flowInfoTable = ConfigurationManager.getString(FloodgateConstants.META_SOURCE_TABLE_FOR_FLOW);
            val payloadPath = ConfigurationManager.getString(FloodgateConstants.CHANNEL_PAYLOAD_FOLDER)
            try {
                while (true) {
                    try {
                        val cur = System.currentTimeMillis()

                        val flowId = queue.take()

                        val mapper = ObjectMapper()
                        @Suppress("UNCHECKED_CAST")
                        val spoolingInfo = mapper.readValue(File("$spoolingPath/$flowId"), LinkedHashMap::class.java) as Map<String, Any>
                        val target = spoolingInfo["target"] as String

                        //Map<String, Object> flowInfoResult = MetaManager.read(flowInfoTable, target);
                        //Map<String, Object> flowInfo = (Map<String, Object>) flowInfoResult.get("FLOW");

                        @Suppress("UNCHECKED_CAST")
                        val spooledContext = spoolingInfo["context"] as MutableMap<String, Any>
                        // If FLOW exists in request body when API type is Instant Interfacing
                        @Suppress("UNCHECKED_CAST")
                        var flowInfo = spooledContext[Context.CONTEXT_KEY.FLOW_META.toString()] as? Map<String, Any>
                        if (flowInfo == null) {
                            val flowInfoTable = ConfigurationManager.getString(FloodgateConstants.META_SOURCE_TABLE_FOR_FLOW)
                            @Suppress("UNCHECKED_CAST")
                            val flowMeta = MetaManager.read(flowInfoTable!!, target)
                            @Suppress("UNCHECKED_CAST")
                            flowInfo = flowMeta?.get("DATA") as? Map<String, Any>
                        }

                        val channel_id = spooledContext[Context.CONTEXT_KEY.CHANNEL_ID.name] as String

                        @Suppress("UNCHECKED_CAST")
                        val payload = mapper.readValue(File("$payloadPath/$channel_id"), LinkedHashMap::class.java) as Map<String, Any>
                        val current = FGSharableInputStream(JSONContainer(payload, "HEADER", "ITEMS"))

                        spooledContext[Context.CONTEXT_KEY.REQUEST_BODY.name] = payload
                        val context = Context()
                        context.map = spooledContext

                        // 동일 target은 동일 쓰레드에서만 순차적으로 처리될 수 있도록 한다
                        val index = target[target.length - 1].code % 4

                        val job = SpoolJob(flowId, target, flowInfo!!, current, context)
                        executor[index].submit(job)
                    } catch (e: InterruptedException) {
                        throw e
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
            }
        }

        thread.start()
    }


    fun addJob(id: String) {
        this.queue.offer(id)
    }
}
