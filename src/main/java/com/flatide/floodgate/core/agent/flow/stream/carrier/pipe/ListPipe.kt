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

package com.flatide.floodgate.core.agent.flow.stream.carrier.pipe

import com.flatide.floodgate.core.agent.flow.stream.carrier.Carrier

@Suppress("UNCHECKED_CAST")
class ListPipe<T> constructor(
    private var header: Map<*, *>?,
    private var data: List<T>?,
    private val bufferSize: Int = data!!.size
) : Carrier {
    private var buffer: MutableList<T>? = null

    private var current: Int = 0

    private var _isFinished: Boolean = false

    override fun flushToFile(filename: String) {
    }

    override fun getSnapshot(): Any? {
        return null
    }

    override fun reset() {
        this.current = 0
        this._isFinished = false
    }

    override fun close() {
        this.header = null
        this.data = null
    }

    override fun totalSize(): Long {
        return this.data!!.size.toLong()
    }

    override fun remainSize(): Long {
        return (this.data!!.size - this.current).toLong()
    }

    override fun getHeaderData(): Any? {
        return header
    }

    override fun isFinished(): Boolean {
        return this._isFinished
    }

    override fun getBuffer(): Any? {
        return this.buffer
    }

    override fun getBufferReadSize(): Long {
        return this.buffer!!.size.toLong()
    }

    override fun forward(): Long {
        this.buffer = ArrayList()

        for (i in 0 until this.bufferSize) {
            if (this.current >= this.data!!.size) {
                break
            }
            val data = this.data!![this.current++]
            this.buffer!!.add(data)
        }

        if (this.buffer!!.size == 0) {
            this._isFinished = true
        }

        return this.buffer!!.size.toLong()
    }
}
