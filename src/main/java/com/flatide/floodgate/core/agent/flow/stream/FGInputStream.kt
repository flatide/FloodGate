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

abstract class FGInputStream(
    val carrier: Carrier
) {
    open var maxSubscriber: Int = 1

    var currentSubscriber: Int = 0

    var countOfCurrentDone: Int = Int.MAX_VALUE

    private val payloads: ArrayList<Payload> = ArrayList()

    private var currentData: Any? = null
    private var currentSize: Long = 0

    fun increaseCountOfCurrentDone() {
        this.countOfCurrentDone++
    }

    open fun reset() {
        this.carrier.reset()
    }

    open fun close() {
    }

    @Synchronized
    open fun subscribe(): Payload {
        val payload = Payload(this, this.currentSubscriber++)
        this.payloads.add(payload)
        return payload
    }

    @Synchronized
    open fun unsubscribe(payload: Payload) {
        val id = payload.id
        this.payloads.removeAt(id)
        this.currentSubscriber--
    }

    open fun size(): Long {
        return this.carrier.totalSize()
    }

    open fun remains(payload: Payload): Long {
        return this.carrier.remainSize()
    }

    open fun getHeader(): Any? {
        return this.carrier.getHeaderData()
    }

    open fun next(payload: Payload): Long {
        if (this.carrier.isFinished()) {
            return -1
        }

        if (this.countOfCurrentDone >= this.currentSubscriber) {
            this.currentSize = this.carrier.forward()

            this.currentData = this.carrier.getBuffer()
            this.countOfCurrentDone = 0
        }

        payload.data = this.currentData
        payload.dataSize = this.currentSize

        this.countOfCurrentDone++

        return this.currentSize
    }
}
