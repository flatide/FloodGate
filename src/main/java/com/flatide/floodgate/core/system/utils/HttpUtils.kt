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

package com.flatide.floodgate.core.system.utils

import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.HashMap

import com.fasterxml.jackson.databind.ObjectMapper

class HttpUtils private constructor(builder: Builder) {
    private var url: String
    private val params: Map<String, String>
    private val properties: Map<String, String>
    private val connectTimeout: Int
    private val readTimeout: Int

    init {
        this.url = builder.url
        this.params = builder.params
        this.properties = builder.properties
        this.connectTimeout = builder.connectTimeout
        this.readTimeout = builder.readTimeout
    }

    companion object {
        fun builder(): Builder {
            return Builder()
        }
    }

    class Builder internal constructor() {
        var url: String = ""
        var params: MutableMap<String, String> = HashMap()
        var properties: MutableMap<String, String> = HashMap()
        var connectTimeout: Int = 1000
        var readTimeout: Int = 5000

        init {
            this.properties["cache-control"] = "no-cache"
            this.properties["Accept"] = "application/json"
            this.properties["Content-Type"] = "application/json"
        }

        fun setUrl(url: String): Builder {
            this.url = url
            return this
        }

        fun addParam(key: String, value: String): Builder {
            this.params[key] = value
            return this
        }

        fun addProperties(key: String, value: String): Builder {
            this.properties[key] = value
            return this
        }

        fun setConnectTimeout(mills: Int): Builder {
            this.connectTimeout = mills
            return this
        }

        fun setReadTimeout(mills: Int): Builder {
            this.readTimeout = mills
            return this
        }

        fun build(): HttpUtils {
            return HttpUtils(this)
        }
    }

    fun get(): String {
        var con: HttpURLConnection? = null

        try {
            var i = 0
            for (key in this.params.keys) {
                if (i == 0) {
                    this.url += "?"
                } else {
                    this.url += "&"
                }
                val value = if (this.params[key] == null) "" else URLEncoder.encode(this.params[key], "UTF-8")
                this.url += (key + "=" + value)
                i++
            }

            val myurl = URL(this.url)
            con = myurl.openConnection() as HttpURLConnection

            con.connectTimeout = this.connectTimeout
            con.readTimeout = this.readTimeout
            con.doOutput = true
            con.requestMethod = "GET"

            for (key in this.properties.keys) {
                con.setRequestProperty(key, this.properties[key])
            }

            val responseCode = con.responseCode
            if (responseCode < 200 || responseCode > 299) {
                val `in` = BufferedReader(InputStreamReader(con.errorStream, "UTF8"))
                var inputLine: String?
                val response = StringBuffer()

                while (`in`.readLine().also { inputLine = it } != null) {
                    response.append(inputLine)
                }
                `in`.close()
                System.err.println(response)
                return response.toString()
            }

            val `in` = BufferedReader(InputStreamReader(con.inputStream, "UTF8"))
            var inputLine: String?
            val response = StringBuffer()

            while (`in`.readLine().also { inputLine = it } != null) {
                response.append(inputLine)
            }
            `in`.close()

            /*ObjectMapper mapper = new ObjectMapper();

            Map<String, Object> map = mapper.readValue(response.toString(), new TypeReference<Map<String, Object>>() {});

            return map;*/
            return response.toString()
        } catch (e: Exception) {
            throw e
        } finally {
            if (con != null) try { con.disconnect() } catch (e: Exception) { }
        }
    }

    fun post(body: Map<*, *>): String {
        val mapper = ObjectMapper()
        val jsonBody = mapper.writeValueAsString(body)
        return post(jsonBody)
    }

    fun post(body: String?): String {
        var con: HttpURLConnection? = null

        try {
            var i = 0
            for (key in this.params.keys) {
                if (i == 0) {
                    this.url += "?"
                } else {
                    this.url += "&"
                }
                val value = if (this.params[key] == null) "" else URLEncoder.encode(this.params[key], "UTF-8")
                this.url += (key + "=" + value)
                i++
            }

            val myurl = URL(this.url)
            con = myurl.openConnection() as HttpURLConnection

            con.connectTimeout = this.connectTimeout
            con.readTimeout = this.readTimeout
            con.doOutput = true
            con.requestMethod = "POST"

            for (key in this.properties.keys) {
                con.setRequestProperty(key, this.properties[key])
            }

            if (body != null) {
                val wr = OutputStreamWriter(con.outputStream, "UTF8")
                wr.write(body)
                wr.flush()
            }

            val responseCode = con.responseCode
            if (responseCode < 200 || responseCode > 299) {
                val `in` = BufferedReader(InputStreamReader(con.errorStream, "UTF8"))
                var inputLine: String?
                val response = StringBuffer()

                while (`in`.readLine().also { inputLine = it } != null) {
                    response.append(inputLine)
                }
                `in`.close()
                System.err.println(response)
                return response.toString()
            }

            val `in` = BufferedReader(InputStreamReader(con.inputStream, "UTF8"))
            var inputLine: String?
            val response = StringBuffer()

            while (`in`.readLine().also { inputLine = it } != null) {
                response.append(inputLine)
            }
            `in`.close()

            return response.toString()
        } catch (e: Exception) {
            throw e
        } finally {
            if (con != null) try { con.disconnect() } catch (e: Exception) { }
        }
    }

    fun postFile(`is`: InputStream, fileName: String, fileLength: Int): String {
        val boundary = java.lang.Long.toHexString(System.currentTimeMillis())
        val CRLF = "\r\n"

        var con: HttpURLConnection? = null

        try {
            var i = 0
            for (key in this.params.keys) {
                if (i == 0) {
                    this.url += "?"
                } else {
                    this.url += "&"
                }
                val value = if (this.params[key] == null) "" else URLEncoder.encode(this.params[key], "UTF-8")
                this.url += (key + "=" + value)
                i++
            }

            val myurl = URL(this.url)
            con = myurl.openConnection() as HttpURLConnection

            con.connectTimeout = this.connectTimeout
            con.readTimeout = this.readTimeout
            con.doOutput = true
            con.requestMethod = "POST"

            for (key in this.properties.keys) {
                con.setRequestProperty(key, this.properties[key])
            }

            con.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            con.outputStream.use { output ->
                PrintWriter(OutputStreamWriter(output, "UTF-8"), true).use { writer ->
                    writer.append("--$boundary").append(CRLF)
                    writer.append("Content-Disposition: form-data; name=\"file\"; filename=\"$fileName\"")
                        .append(CRLF)
                    writer.append("Content-Type: application/octet-stream").append(CRLF)
                    // writer.append("Content-Transfer-Encoding: binary").append(CRLF);
                    writer.append(CRLF).flush()

                    val allBytes = ByteArray(fileLength)
                    `is`.read(allBytes)
                    `is`.close()

                    output.write(allBytes)
                    output.flush()

                    writer.append(CRLF).flush()
                    writer.append("--$boundary--").append(CRLF).flush()
                }
            }

            val responseCode = con.responseCode
            if (responseCode < 200 || responseCode > 299) {
                val inReader = BufferedReader(InputStreamReader(con.errorStream, "UTF8"))
                var inputLine: String?
                val response = StringBuffer()

                while (inReader.readLine().also { inputLine = it } != null) {
                    response.append(inputLine)
                }
                inReader.close()
                System.err.println(response)
                return response.toString()
            }

            val inReader = BufferedReader(InputStreamReader(con.inputStream, "UTF8"))
            var inputLine: String?
            val response = StringBuffer()

            while (inReader.readLine().also { inputLine = it } != null) {
                response.append(inputLine)
            }
            inReader.close()

            return response.toString()
        } catch (e: Exception) {
            throw e
        } finally {
            if (con != null) try { con.disconnect() } catch (e: Exception) { }
            try { `is`.close() } catch (e: Exception) { }
        }
    }
}
