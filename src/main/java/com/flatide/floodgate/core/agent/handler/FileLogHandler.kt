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

package com.flatide.floodgate.core.agent.handler

import org.apache.logging.log4j.LogManager

import com.flatide.floodgate.core.agent.Context
import com.flatide.floodgate.core.agent.Context.CONTEXT_KEY
import com.flatide.floodgate.core.agent.flow.Flow
import com.flatide.floodgate.core.agent.flow.module.Module

class FileLogHandler : FloodgateAbstractHandler {
    companion object {
        private val logger = LogManager.getLogger(FileLogHandler::class.java)
    }

    override fun handleChannelIn(context: Context, `object`: Any?) {
        val cur = System.currentTimeMillis()
        context.add(CONTEXT_KEY.CHANNEL_START_TIME, cur)

        val id = context.getString(CONTEXT_KEY.CHANNEL_ID)
        val api = context.getString(CONTEXT_KEY.API)

        logger.info(String.format("Channel %s (%s) is started.", id, api))
    }

    override fun handleChannelOut(context: Context, `object`: Any?) {
        val cur = System.currentTimeMillis()
        val start = context.getDefault(CONTEXT_KEY.CHANNEL_START_TIME, java.lang.Long.valueOf(0)) as Long

        val id = context.getString(CONTEXT_KEY.CHANNEL_ID)

        logger.info(String.format("Channel %s is done : %s ms elapsed.", id, cur - start))
    }

    override fun handleFlowIn(context: Context, `object`: Any?) {
        val cur = System.currentTimeMillis()
        context.add(CONTEXT_KEY.FLOW_START_TIME, cur)

        val flow = `object` as Flow
        val id = flow.flowId
        val parentId = context.getString(CONTEXT_KEY.CHANNEL_ID)
        val target = flow.targetId

        logger.info(String.format("Flow %s of %s (%s) is started.", id, parentId, target))
    }

    override fun handleFlowOut(context: Context, `object`: Any?) {
        val cur = System.currentTimeMillis()
        val start = context.getDefault(CONTEXT_KEY.FLOW_START_TIME, java.lang.Long.valueOf(0)) as Long

        val flow = `object` as Flow
        val id = flow.flowId

        logger.info(String.format("Flow %s is done : %s ms elapsed.", id, cur - start))
    }

    override fun handleModuleIn(context: Context, `object`: Any?) {
        val cur = System.currentTimeMillis()
        context.add(CONTEXT_KEY.MODULE_START_TIME, cur)

        val module = `object` as Module
        val id = module.id
        val parentId = module.flow.flowId

        logger.info(String.format("Module %s of %s (%s) is started.", id, parentId, module.getName()))
    }

    override fun handleModuleOut(context: Context, `object`: Any?) {
        val cur = System.currentTimeMillis()
        val start = context.getDefault(CONTEXT_KEY.MODULE_START_TIME, java.lang.Long.valueOf(0)) as Long

        val module = `object` as Module
        val id = module.id

        logger.info(String.format("Module %s is done : %s ms elapsed.", id, cur - start))
    }

    override fun handleModuleProgress(context: Context, `object`: Any?) {
    }
}
