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

package com.flatide.floodgate.core.agent.spool

import com.flatide.floodgate.core.ConfigurationManager
import com.flatide.floodgate.core.FloodgateConstants
import com.flatide.floodgate.core.agent.Context
import com.flatide.floodgate.core.agent.flow.Flow
import com.flatide.floodgate.core.agent.flow.stream.FGInputStream

import java.io.File
import java.util.HashMap
import java.util.concurrent.Callable

import org.apache.logging.log4j.LogManager

internal class SpoolJob(
    var flowId: String,
    var target: String,
    var flowInfo: Map<String, Any>,
    var current: FGInputStream?,
    var context: Context
) : Callable<Map<String, Any>> {

    companion object {
        private val logger = LogManager.getLogger(SpoolJob::class.java)
    }

    override fun call(): Map<String, Any> {
        logger.info("Spooled Job " + flowId + " start in thread " + Thread.currentThread().id)
        val spoolingPath = ConfigurationManager.getString(FloodgateConstants.CHANNEL_SPOOLING_FOLDER)

        val result: MutableMap<String, Any> = HashMap()
        try {
            val flow = Flow(flowId, this.target, context)
            flow.prepare(flowInfo, current)

            flow.process()

            result["result"] = "success"
            logger.info("Spooled Job $flowId completed.")

            try {
                val file = File("$spoolingPath/$flowId")
                file.delete()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            result["result"] = "fail"
            result["reason"] = e.message ?: ""
            logger.info("Spooled Job $flowId failed. : ${e.message}")
        }

        return result
    }
}
