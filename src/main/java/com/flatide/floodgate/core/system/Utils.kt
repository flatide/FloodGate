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

package com.flatide.floodgate.core.system

import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.util.regex.Pattern

object Utils {
    fun checkFirewall(url: String, timeout: Int): Boolean {
        var socket: Socket? = null
        val ip = ArrayList<String>()
        val port = ArrayList<String>()
        var curIP = ""
        var curPort = ""
        try {
            if (url.contains("HOST") && url.contains("PORT")) {    // Oracle tnsnames
                var pattern = Pattern.compile("HOST\\s*=\\s*[\\w-]+\\.[\\w-]+\\.[\\w-]+\\.[\\w-]+")
                var matcher = pattern.matcher(url)
                while (matcher.find()) {
                    val f = matcher.group()
                    val temp = f.split("=")
                    ip.add(temp[1].trim())
                }

                if (ip.size == 0) {
                    throw Exception("Cannot find proper ADDRESS from $url")
                }

                pattern = Pattern.compile("PORT\\s*=\\s*[0-9]+")
                matcher = pattern.matcher(url)
                while (matcher.find()) {
                    val f = matcher.group()
                    val temp = f.split("=")
                    port.add(temp[1].trim())
                }
                if (port.size != ip.size) {
                    throw Exception("PORT not properly coupled with ADDRESS in $url")
                }
            } else {
                val pattern = Pattern.compile("[\\w-]+\\.[\\w-]+\\.[\\w-]+\\.[\\w-]+:[0-9]+")

                val matcher = pattern.matcher(url)
                while (matcher.find()) {
                    val f = matcher.group()
                    val temp = f.split(":")
                    ip.add(temp[0])
                    port.add(temp[1])
                }
                if (ip.size == 0) {
                    throw Exception("Cannot find proper IP:PORT from $url")
                }
            }

            for (i in ip.indices) {
                curIP = ip[i]
                curPort = port[i]

                socket = Socket()
                socket.connect(InetSocketAddress(curIP, Integer.parseInt(curPort)), timeout)
                socket.close()
                socket = null
            }

            return true
        } catch (e: IOException) {
            e.printStackTrace()
            throw Exception("$curIP:$curPort : ${e.message}")
        } finally {
            try {
                if (socket != null) socket.close()
            } catch (e: IOException) {
            }
        }
    }

    fun checkSocket(ip: String, port: Int, timeout: Int): Boolean {
        var socket: Socket? = null

        try {
            socket = Socket()
            socket.connect(InetSocketAddress(ip.trim(), port), timeout)
            socket.close()
            socket = null

            return true
        } catch (e: IOException) {
            e.printStackTrace()
            throw e
        } finally {
            try {
                if (socket != null) socket.close()
            } catch (e: IOException) {
            }
        }
    }
}
