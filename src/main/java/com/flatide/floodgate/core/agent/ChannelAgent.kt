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

package com.flatide.floodgate.core.agent

import com.flatide.floodgate.core.ConfigurationManager
import com.flatide.floodgate.core.FloodgateConstants
import com.flatide.floodgate.core.agent.Context.CONTEXT_KEY
import com.flatide.floodgate.core.agent.flow.stream.FGInputStream
import com.flatide.floodgate.core.agent.handler.FloodgateHandlerManager
import com.flatide.floodgate.core.agent.handler.FloodgateHandlerManager.Step
import com.flatide.floodgate.core.agent.meta.MetaManager

import java.io.File
import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.ThreadPoolExecutor

import org.apache.logging.log4j.LogManager

@Suppress("UNCHECKED_CAST")
class ChannelAgent {
    companion object {
        private val logger = LogManager.getLogger(ChannelAgent::class.java)
    }

    private val context: Context = Context()

    fun addContext(key: CONTEXT_KEY, value: Any) {
        this.context.add(key, value)
    }

    fun addContext(key: String, value: Any) {
        this.context.add(key, value)
    }

    fun getContext(key: CONTEXT_KEY): Any? {
        return this.context.get(key)
    }

    fun getContext(key: String): Any? {
        return this.context.get(key)
    }

    fun process(stream: FGInputStream, api: String, meta: Map<String, Any>): Map<String, Any> {
        val apiMeta = meta["API"] as? Map<String, Any>
        if (apiMeta != null) {
            addContext(CONTEXT_KEY.API_META, apiMeta)
        }

        val flowInfo = meta["FLOW"] as? Map<String, Any>
        if (flowInfo != null) {
            addContext(CONTEXT_KEY.FLOW_META, flowInfo)
        }

        return process(stream, api)
    }

    fun process(current: FGInputStream, api: String): Map<String, Any> {
        val result: MutableMap<String, Any> = HashMap()

        var channelId = getContext(CONTEXT_KEY.CHANNEL_ID) as? String
        if (channelId == null || channelId.isEmpty()) {
            // Unique ID 생성
            val id = UUID.randomUUID()
            channelId = id.toString()
            addContext(CONTEXT_KEY.CHANNEL_ID, channelId)
        }

        addContext(CONTEXT_KEY.API, api)

        // API 정보 확인
        val apiTable = ConfigurationManager.getString(FloodgateConstants.META_SOURCE_TABLE_FOR_API)!!
        var apiMeta: Map<String, Any>? = MetaManager.read(apiTable, api) as? Map<String, Any>
        if (apiMeta == null) {
            apiMeta = getContext(CONTEXT_KEY.API_META) as? Map<String, Any>
            if (apiMeta == null) {
                throw Exception("Cannot find resource $api")
            }
        }
        val apiInfo = apiMeta["DATA"] as Map<String, Any>

        FloodgateHandlerManager.handle(Step.CHANNEL_IN, context, null)

        /*
            "TARGET": {
                "": ["IF_ID1", "IF_ID2"],
                "CD0001": ["IF_ID3"] }
         */
        val targetList: MutableList<String> = ArrayList()

        val targetMap = apiInfo[ApiTag.TARGET.name] as? Map<String, List<String>>
        if (targetMap != null && targetMap.isNotEmpty()) {
            val params = getContext(CONTEXT_KEY.REQUEST_PARAMS) as? Map<String, Any>
            val targets = params?.get("targets") as? String

            if (targets != null && targets.isNotEmpty()) {
                val split = targets.split(",".toRegex())
                for (t in split) {
                    val group = targetMap[t]
                    if (group != null) {
                        targetList.addAll(group)
                    } else {
                        targetList.add(t.trim().uppercase())
                    }
                }
            } else {
                // 아래 두가지 모두 가능
                targetMap.values.forEach { targetList.addAll(it) }
                //targetList = targetMap.values.stream().flatMap(x -> x.stream()).collect(Collectors.toList());
            }
        } else {
            val paths = getContext(CONTEXT_KEY.REQUEST_PATH_VARIABLES) as? Map<String, Any>
            val target = paths?.get("target") as? String
            if (target != null) {
                targetList.add(target)
            }
        }

        logger.debug(targetList)

        // 페이로드 저장 true인 경우
        // 페이로드 저장은 호출시의 데이타에 한정한다
        if (apiInfo[ApiTag.BACKUP_PAYLOAD.name] as Boolean == true) {
            val carrier = current.carrier
            try {
                val path = ConfigurationManager.getString(FloodgateConstants.CHANNEL_PAYLOAD_FOLDER)
                val folder = File(path)
                if (!folder.exists()) {
                    folder.mkdir()
                }
                carrier.flushToFile("$path/$channelId")
            } catch (e: Exception) {
                // intentionally empty
            }
        }

        var logString = ""
        try {
            val concurrencyInfo = apiInfo[ApiTag.CONCURRENCY.name] as? Map<String, Any>

            // 병렬실행인 경우
            if (concurrencyInfo != null && concurrencyInfo[ApiTag.ENABLE.name] as Boolean == true) {
                println("Thread Max: " + concurrencyInfo[ApiTag.THREAD_MAX.name])

                val executor = Executors.newCachedThreadPool() as ThreadPoolExecutor

                val futures = arrayOfNulls<Future<Map<String, Any>>>(targetList.size)

                var i = 0
                for (target in targetList) {
                    //TODO current를 복제해서 사용할 것 2021.07.06
                    val job = ChannelJob(target, this.context, current)
                    futures[i] = executor.submit(job)
                    i++
                }

                i = 0
                for (target in targetList) {
                    result[target] = futures[i]!!.get()
                    i++
                }

            } else {
                for (target in targetList) {
                    //Map<String, Object> flowInfo = MetaManager.get(flowInfoTable, target);
                    val job = ChannelJob(target, this.context, current)

                    try {
                        result[target] = job.call()
                    } catch (e: Exception) {
                        e.printStackTrace()
                        result[target] = e.message ?: ""
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            logString = e.message ?: ""
        }

        var success = true
        for ((_, value) in result) {
            val v = value as Map<String, Any>
            val r = v["result"] as? String
            if ("fail" == r) {
                success = false
                break
            }
        }

        addContext(CONTEXT_KEY.LATEST_RESULT, if (success) "success" else "fail")
        addContext(CONTEXT_KEY.LATEST_MSG, logString)

        FloodgateHandlerManager.handle(Step.CHANNEL_OUT, context, null)

        val response: MutableMap<String, Any> = HashMap()
        response["result"] = result

        return response
    }
}
