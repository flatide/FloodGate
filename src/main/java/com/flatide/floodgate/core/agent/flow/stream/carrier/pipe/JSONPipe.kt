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

import com.fasterxml.jackson.core.JsonFactory
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.JsonToken
import com.flatide.floodgate.core.agent.flow.stream.carrier.Carrier

import java.io.*

@Suppress("UNCHECKED_CAST")
class JSONPipe : Carrier {
    private val headerTag: String?
    private val dataTag: String
    private var jParser: JsonParser?

    private var header: MutableMap<String, Any?>? = null
    private var buffer: MutableList<Any?>? = null
    private val bufferSize: Int
    private var bufferReadSize: Int = -1

    //private long size = 0;
    private var remains: Long = Int.MAX_VALUE.toLong()

    private var status: Int = 0     // 0: start, 1: seek header or data, 2: in data, 99: error

    private var _isFinished: Boolean = false

    constructor(inputStream: InputStream, size: Long, headerTag: String?, dataTag: String) :
            this(inputStream, size, headerTag, dataTag, 100)

    constructor(inputStream: InputStream, size: Long, headerTag: String?, dataTag: String, bufferSize: Int) {
        this.headerTag = headerTag
        this.dataTag = dataTag
        this.bufferSize = bufferSize

        val jfactory = JsonFactory()

        this.jParser = jfactory.createParser(inputStream)
        //this.size = size;
    }

    override fun flushToFile(filename: String) {
    }

    override fun getSnapshot(): Any? {
        return null
    }

    override fun reset() {
        this.remains = Int.MAX_VALUE.toLong()
        this.status = 0
        this._isFinished = false
    }

    override fun close() {
        if (this.jParser != null) {
            try {
                this.jParser!!.close()
            } catch (e: IOException) {
                e.printStackTrace()
            } finally {
                this.jParser = null
            }
        }
    }

    override fun totalSize(): Long {
        return this.buffer!!.size.toLong()  // 실제 파일 사이즈 고려해 볼 것( 파일이 아닌 스트림일 경우 애매함)
    }

    override fun remainSize(): Long {
        return this.remains    // file offset 고려해 볼 것
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
        try {
            while (true) {
                var token: JsonToken? = null
                if (this.status != 2) {
                    token = this.jParser!!.nextToken()

                    if (token == null) {
                        this._isFinished = true
                        this.remains = 0
                        this.jParser!!.close()
                        break
                    }
                }

                when (this.status) {
                    0 -> {
                        if (token == JsonToken.START_OBJECT) {
                            // JSON stream starts with with '{'
                            this.status = 1
                        } else if (token == JsonToken.START_ARRAY) {
                            // No header, no data tag, only data
                            this.status = 2    // data seeking
                        }
                    }
                    1 -> { // in map
                        val name = this.jParser!!.currentName

                        if (name == this.dataTag) {
                            this.jParser!!.nextToken()
                            if (this.jParser!!.currentToken != JsonToken.START_ARRAY) {
                                throw IllegalStateException("Data cannot be an object.")
                            }
                            this.status = 2
                        } else {
                            if (this.headerTag == null || this.headerTag.isEmpty()) {
                                if (this.header == null) {
                                    this.header = HashMap()
                                }
                                this.header!![name] = getChildAll()
                            } else {
                                if (name == this.headerTag) {
                                    try {
                                        this.header = getChildAll() as MutableMap<String, Any?>?
                                    } catch (e: Exception) {
                                        throw IllegalStateException("Header cannot be an array.")
                                    }
                                } else {
                                    // skip to next field
                                    skipChildAll()
                                }
                            }
                        }
                    }
                    2 -> { // data seeking
                        this.buffer = ArrayList()
                        this.bufferReadSize = 0
                        while (true) {
                            val data = getChildAll()
                            if (data == null) {
                                break
                            } else {
                                this.buffer!!.add(data)
                                this.bufferReadSize++
                                if (this.bufferReadSize == this.bufferSize) {
                                    break
                                }
                            }
                        }

                        if (this.bufferReadSize == 0) {
                            this._isFinished = true
                            this.remains = 0
                            this.jParser!!.close()
                            return 0
                        } else {
                            return this.bufferReadSize.toLong()
                        }
                    }
                }
            }
        } catch (e: Exception) {
            this._isFinished = true
            this.remains = 0
            try {
                this.jParser!!.close()
            } catch (e1: Exception) {
                e.printStackTrace()
            }
            throw e
        }
        return this.bufferReadSize.toLong()
    }

    // 현재 토큰의 하부구조를 모두 가져온다
    // 성능을 위해 non-recursive로 구현
    private fun getChildAll(): Any? {
        var depth = -1

        var fieldName = ""
        var current: Any? = null

        val parentList = ArrayList<Any?>()
        while (true) {
            val token = this.jParser!!.nextToken()

            if (token.isStructStart) {
                depth++

                val child: Any = if (token == JsonToken.START_OBJECT) {
                    HashMap<String, Any?>()
                } else {
                    ArrayList<Any?>()
                }

                if (current != null) {
                    if (current is MutableMap<*, *>) {
                        (current as MutableMap<String, Any?>)[fieldName] = child
                    } else {
                        (current as MutableList<Any?>).add(child)
                    }
                    parentList.add(depth, current)
                } else {
                    parentList.add(depth, child)
                }

                current = child

                continue
            }
            if (token.isStructEnd) {
                if (depth == -1) {
                    return current
                }

                current = parentList.removeAt(depth)

                depth--
                if (depth == -1) {
                    return current
                }

                continue
            }

            if (token == JsonToken.FIELD_NAME) {
                fieldName = this.jParser!!.currentName
            } else {
                var value: Any? = null
                when (token) {
                    JsonToken.VALUE_STRING -> value = this.jParser!!.text
                    JsonToken.VALUE_TRUE -> value = true
                    JsonToken.VALUE_FALSE -> value = false
                    JsonToken.VALUE_NUMBER_INT -> value = this.jParser!!.intValue
                    JsonToken.VALUE_NUMBER_FLOAT -> value = this.jParser!!.floatValue
                    JsonToken.VALUE_NULL -> {}
                    JsonToken.VALUE_EMBEDDED_OBJECT -> value = this.jParser!!.embeddedObject
                    else -> {}
                }

                if (current == null) {
                    return value
                } else if (current is MutableMap<*, *>) {
                    (current as MutableMap<String, Any?>)[fieldName] = value
                } else {
                    (current as MutableList<Any?>).add(value)
                }
            }
        }
    }

    private fun skipChildAll() {
        var depth = 0

        while (true) {
            val token = this.jParser!!.nextToken()

            if (token.isStructStart) {
                depth++
            } else if (token.isStructEnd) {
                depth--
            }

            if (depth == 0) {
                return
            }
        }
    }
}
