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
import com.flatide.floodgate.core.agent.flow.Flow
import com.flatide.floodgate.core.agent.flow.stream.FGInputStream
import com.flatide.floodgate.core.agent.meta.MetaManager

@Suppress("UNCHECKED_CAST")
class PushAgent : Spoolable() {
    var context: Context = Context()

    fun addContext(key: String, value: Any) {
        this.context.add(key, value)
    }

    fun process(data: FGInputStream, ifId: String): Map<String, Any> {

        // 페이로드 저장


        // 잡이 즉시처리인지 풀링인지 확인
        // 풀링일 경우
        // 즉시처리인 경우

        val result: MutableMap<String, Any> = HashMap()

        try {
            //String tableName = ConfigurationManager.getString(FloodgateConstants.META_SOURCE_TABLE_FOR_FLOW);
            //Map<String, Object> flowInfoResult = MetaManager.read( tableName, ifId);
            //Map<String, Object> flowInfo = (Map<String, Object>) flowInfoResult.get("FLOW");

            // If FLOW exists in request body when API type is Instant Interfacing
            var flowInfo = this.context.get(Context.CONTEXT_KEY.FLOW_META.toString()) as? Map<String, Any>
            if (flowInfo == null) {
                val flowInfoTable = ConfigurationManager.getString(FloodgateConstants.META_SOURCE_TABLE_FOR_FLOW)!!
                val flowMeta = MetaManager.read(flowInfoTable, ifId)
                flowInfo = flowMeta?.get("DATA") as Map<String, Any>
            }
            val flow = Flow(ifId, this.context)
            flow.prepare(flowInfo, data)
            flow.process()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return result
    }
}
