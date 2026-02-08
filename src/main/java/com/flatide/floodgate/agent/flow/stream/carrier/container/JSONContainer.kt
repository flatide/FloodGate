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

package com.flatide.floodgate.agent.flow.stream.carrier.container

import com.fasterxml.jackson.databind.ObjectMapper
import com.flatide.floodgate.agent.flow.stream.carrier.Carrier

import java.io.File
import java.io.IOException

@Suppress("UNCHECKED_CAST")
class JSONContainer constructor(
    private val data: Map<String, Any?>,
    private val headerTag: String,
    private val dataTag: String
) : Carrier {
    private val buffer: List<Any?>?
    private val bufferSize: Int
    private var bufferReadSize: Int = -1

    private var _isFinished: Boolean = false

    init {
        this.buffer = data[dataTag] as List<Any?>?
        if (this.buffer != null) {
            this.bufferSize = this.buffer.size
        } else {
            this.bufferSize = 0
        }
    }

    override fun flushToFile(filename: String) {
        try {
            val path = filename.substring(0, filename.lastIndexOf("/"))
            val folder = File(path)

            if (!folder.exists()) {
                if (!folder.mkdir()) {
                    throw IOException("Cannot make folder $path")
                }
            }

            val mapper = ObjectMapper()
            mapper.writerWithDefaultPrettyPrinter().writeValue(File(filename), data)
        } catch (e: Exception) {
            e.printStackTrace()
            throw e
        }
    }

    override fun getSnapshot(): Any? {
        return this.data
    }

    override fun reset() {
        this._isFinished = false
    }

    override fun close() {
    }

    override fun totalSize(): Long {
        return this.buffer!!.size.toLong()
    }

    override fun remainSize(): Long {
        if (this._isFinished) {
            return 0    // file offset 고려해 볼 것
        }
        return this.buffer!!.size.toLong()
    }

    override fun getHeaderData(): Any? {
        return this.data[this.headerTag]
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
        if (this._isFinished) {
            return -1
        }
        this._isFinished = true
        return this.buffer!!.size.toLong()
    }
}
