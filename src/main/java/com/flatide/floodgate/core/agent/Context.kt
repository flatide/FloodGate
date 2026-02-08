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

package com.flatide.floodgate.core.agent

import org.apache.logging.log4j.LogManager
import java.util.regex.Pattern

open class Context {
    enum class CONTEXT_KEY {
        API,
        CHANNEL_ID,

        REQUEST_PATH_VARIABLES,
        REQUEST_PARAMS,
        REQUEST_BODY,
        HTTP_REQUEST_METHOD,

        API_META,
        FLOW_META,

        CHANNEL_CONTEXT,
        FLOW_CONTEXT,

        CHANNEL_START_TIME,
        CHANNEL_ELAPSED,
        FLOW_START_TIME,
        MODULE_START_TIME,

        LATEST_RESULT,
        LATEST_MSG,
    }

    var map: MutableMap<String, Any> = HashMap()

    override fun toString(): String {
        return this.map.toString()
    }

    fun add(key: String, value: Any) {
        this.map[key] = value
    }

    fun add(key: Enum<*>, value: Any) {
        add(key.name, value)
    }

    fun get(key: Enum<*>): Any? {
        return get(key.name)
    }

    fun getDefault(key: Enum<*>, defaultValue: Any?): Any? {
        return getDefault(key.name, defaultValue)
    }

    fun getDefault(key: String, defaultValue: Any?): Any? {
        var obj = get(key)
        if (obj == null) {
            obj = defaultValue
        }
        return obj
    }

    @Suppress("UNCHECKED_CAST")
    open fun get(key: String): Any? {
        var key = key
        // . 구분자를 포함한 상태의 키가 있는지 먼저 검사
        val value = this.map[key]
        if (value == null || key.contains(".")) {
            // . 구분자로 구성된 경우 자식 context 검색
            val keys = key.split(".")
            var current: Map<String, Any> = this.map
            for (k in keys) {
                val child = current[k]
                if (child != null) {
                    if (key.contains(".")) {
                        key = key.substring(key.indexOf(".") + 1)
                    } else {
                        return child
                    }
                    if (child is Context) {
                        return child.get(key)
                    } else if (child is Map<*, *>) {
                        current = child as Map<String, Any>
                    }
                } else {
                    return null
                }
            }
        }
        return value
    }

    fun getString(key: Enum<*>): String? {
        return getString(key.name)
    }

    fun getString(key: String): String? {
        val value = get(key)
        if (value != null) {
            if (value is String) {
                return value
            } else {
                return value.toString()
            }
        }

        return null
    }

    fun getStringDefault(key: Enum<*>, defaultValue: String): String {
        return getStringDefault(key.name, defaultValue)
    }

    fun getStringDefault(key: String, defaultValue: String): String {
        var ret = getString(key)
        if (ret == null) {
            ret = defaultValue
        }
        return ret
    }

    fun getInteger(key: Enum<*>): Int? {
        return getInteger(key.name)
    }

    fun getInteger(key: String): Int? {
        val value = get(key)
        if (value is Int) {
            return value
        } else if (value is String) {
            return Integer.valueOf(value)
        }

        return null
    }

    fun getIntegerDefault(key: Enum<*>, defaultValue: Int): Int {
        return getIntegerDefault(key.name, defaultValue)
    }

    fun getIntegerDefault(key: String, defaultValue: Int): Int {
        var ret = getInteger(key)
        if (ret == null) {
            ret = defaultValue
        }

        return ret
    }

    /*
        {SEQUENCE.OUTPUT} 형태를 처리한다.
        {SEQUENCE.{INOUT}} 등의 중첩은 처리하지 못한다
     */
    fun evaluate(str: String): String {
        var str = str
        val pattern = Pattern.compile("\\{[^\\s{}]+\\}")
        val matcher = pattern.matcher(str)

        val findGroup = ArrayList<String>()
        while (matcher.find()) {
            findGroup.add(str.substring(matcher.start(), matcher.end()))
        }

        for (find in findGroup) {
            val value = getString(find.substring(1, find.length - 1))
            if (value != null) {
                str = str.replace(find, value)
            } else {
                logger.error("$find is wrong expression.")
            }
        }

        return str
    }

    companion object {
        private val logger = LogManager.getLogger(Context::class.java)
    }
}
