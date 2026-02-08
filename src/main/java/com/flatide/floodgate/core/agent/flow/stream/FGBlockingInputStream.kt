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

package com.flatide.floodgate.core.agent.flow.stream

import com.flatide.floodgate.core.agent.flow.stream.carrier.Carrier

import java.util.ArrayList
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.BlockingQueue

class FGBlockingInputStream(carrier: Carrier) : FGInputStream(carrier) {
    private val currentDataList: MutableList<BlockingQueue<Any>> = ArrayList()
    private var currentSize: Long = 0

    override var maxSubscriber: Int
        get() = super.maxSubscriber
        set(value) {
            super.maxSubscriber = value

            for (i in 0 until value) {
                val queue: BlockingQueue<Any> = ArrayBlockingQueue(1)
                this.currentDataList.add(queue)
            }
        }

    override fun next(payload: Payload): Long {
        try {
            synchronized(this) {
                var needForward = true
                for (i in 0 until super.maxSubscriber) {
                    val queue = this.currentDataList[i]
                    if (queue.size > 0) {
                        needForward = false
                    }
                }

                if (needForward) {
                    if (!carrier.isFinished()) {
                        this.currentSize = carrier.forward()
                        if (this.currentSize > 0) {
                            val data = carrier.getBuffer()!!
                            for (i in 0 until super.maxSubscriber) {
                                val queue = this.currentDataList[i]
                                queue.put(data)
                            }
                        }
                    }
                }
            }
            if (!carrier.isFinished()) {
                val queue = this.currentDataList[payload.id]
                payload.data = queue.take()
                payload.dataSize = this.currentSize
            }
        } catch (e: Exception) {
            this.currentSize = -1
            e.printStackTrace()
        }

        return this.currentSize
    }
}
