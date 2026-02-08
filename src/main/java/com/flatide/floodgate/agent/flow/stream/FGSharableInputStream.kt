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

package com.flatide.floodgate.agent.flow.stream

import com.flatide.floodgate.agent.flow.stream.carrier.Carrier

class FGSharableInputStream(carrier: Carrier) : FGInputStream(carrier) {
    private var _maxSubscriber: Int = 1
    private var _currentSubscriber: Int = 0
    private var _countOfCurrentDone: Int = Int.MAX_VALUE

    private var currentData: Any? = null
    private var currentSize: Long = 0

    init {
        try {
            this.currentSize = this.carrier.forward()

            this.currentData = this.carrier.getBuffer()
            this._countOfCurrentDone = 0
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override var maxSubscriber: Int
        get() = this._maxSubscriber
        set(value) {
            this._maxSubscriber = value
        }

    override fun reset() {
    }

    override fun close() {
    }

    override fun subscribe(): Payload {
        val payload = Payload(this, 0)
        return payload
    }

    override fun unsubscribe(payload: Payload) {
    }

    override fun size(): Long {
        return this.carrier.totalSize()
    }

    override fun remains(payload: Payload): Long {
        return if (payload.data != null) {
            0
        } else {
            currentSize
        }
    }

    override fun getHeader(): Any? {
        return this.carrier.getHeaderData()
    }

    override fun next(payload: Payload): Long {
        if (payload.data != null) {
            return -1
        }

        payload.data = this.currentData
        payload.dataSize = this.currentSize

        return this.currentSize
    }
}
