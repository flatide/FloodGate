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

package com.flatide.floodgate.agent.flow.stream.carrier.pipe

import com.flatide.floodgate.agent.flow.stream.carrier.Carrier

import java.io.*

class BytePipe : Carrier {
    private var inputStream: BufferedInputStream? = null

    private var buffer: ByteArray? = null
    private val bufferSize: Int
    private var bufferReadSize: Int = 0

    private var size: Long = 0
    private var current: Long = 0

    private var _isFinished: Boolean = false

    constructor(filepath: String) : this(filepath, 8192)

    private constructor(filepath: String, bufferSize: Int) : super() {
        this.bufferSize = bufferSize
        val file = File(filepath)
        if (file.exists()) {
            this.size = file.length()
            this.inputStream = BufferedInputStream(FileInputStream(filepath), bufferSize)
        }
    }

    constructor(inputStream: InputStream, size: Long, bufferSize: Int) : super() {
        this.bufferSize = bufferSize
        this.inputStream = BufferedInputStream(inputStream, bufferSize)
        this.size = size
    }

    override fun flushToFile(filename: String) {
    }

    override fun getSnapshot(): Any? {
        return null
    }

    override fun reset() {
        this.bufferReadSize = 0
        this.current = 0
    }

    override fun close() {
        if (this.inputStream != null) {
            try {
                this.inputStream!!.close()
            } catch (e: IOException) {
                e.printStackTrace()
            } finally {
                this.inputStream = null
            }
        }
    }

    override fun totalSize(): Long {
        return this.size
    }

    override fun remainSize(): Long {
        return this.size - this.current
    }

    override fun getHeaderData(): Any? {
        return null
    }

    override fun isFinished(): Boolean {
        return this._isFinished
    }

    override fun getBuffer(): Any? {
        return this.buffer
    }

    override fun getBufferReadSize(): Long {
        return this.bufferReadSize.toLong()
    }

    override fun forward(): Long {
        try {
            // 멀티 쓰레드환경에서 데이타 오염을 방지하기 위해 새로운 객체를 생성
            this.buffer = ByteArray(this.bufferSize)

            this.bufferReadSize = this.inputStream!!.read(this.buffer!!)
            if (this.bufferReadSize == -1) {
                this._isFinished = true
                this.inputStream!!.close()
            } else {
                this.current += this.bufferReadSize
            }
            return this.bufferReadSize.toLong()
        } catch (e: IOException) {
            e.printStackTrace()
            throw e
        }
    }
}
