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

package com.flatide.floodgate.agent.handler

import org.apache.logging.log4j.LogManager

import com.flatide.floodgate.agent.Context

object FloodgateHandlerManager {
    private val logger = LogManager.getLogger(FloodgateHandlerManager::class.java)

    enum class Step {
        CHANNEL_IN,
        CHANNEL_OUT,
        FLOW_IN,
        FLOW_OUT,
        MODULE_IN,
        MODULE_OUT,
        MODULE_PROGRESS
    }

    private val handlerList: MutableMap<String, FloodgateAbstractHandler> = LinkedHashMap()


    fun addHandler(name: String, handler: FloodgateAbstractHandler) {
        if (handlerList[name] != null) {
            return
        }
        handlerList[name] = handler
    }

    fun removeHandler(name: String) {
        handlerList.remove(name)
    }

    fun handle(step: Step, context: Context, `object`: Any?) {
        for ((_, handler) in handlerList) {
            when (step) {
                Step.CHANNEL_IN -> handler.handleChannelIn(context, `object`)
                Step.CHANNEL_OUT -> handler.handleChannelOut(context, `object`)
                Step.FLOW_IN -> handler.handleFlowIn(context, `object`)
                Step.FLOW_OUT -> handler.handleFlowOut(context, `object`)
                Step.MODULE_IN -> handler.handleModuleIn(context, `object`)
                Step.MODULE_OUT -> handler.handleModuleOut(context, `object`)
                Step.MODULE_PROGRESS -> handler.handleModuleProgress(context, `object`)
            }
        }
    }
}
