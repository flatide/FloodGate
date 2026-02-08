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

import com.flatide.floodgate.core.agent.Context
import java.util.regex.Pattern

object PropertyMap {
    fun get(map: Map<*, *>, key: Enum<*>): Any? {
        return get(map, key.name)
    }

    fun getDefault(map: Map<*, *>, key: Enum<*>, defaultValue: Any?): Any? {
        return getDefault(map, key.name, defaultValue)
    }

    fun getDefault(map: Map<*, *>, key: String, defaultValue: Any?): Any? {
        var obj = get(map, key)
        if (obj == null) {
            obj = defaultValue
        }
        return obj
    }

    @Suppress("UNCHECKED_CAST")
    fun get(map: Map<*, *>, key: String): Any? {
        var key = key
        val value = map[key]
        if (value == null || key.contains(".")) {
            val keys = key.split(".")
            var current: Map<String, Any?> = map as Map<String, Any?>
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
                        current = child as Map<String, Any?>
                    }
                } else {
                    return null
                }
            }
        }
        return value
    }

    fun getString(map: Map<*, *>, key: Enum<*>): String? {
        return getString(map, key.name)
    }

    fun getString(map: Map<*, *>, key: String): String? {
        val value = get(map, key)
        if (value != null) {
            if (value is String) {
                return value
            } else {
                return value.toString()
            }
        }
        return null
    }

    fun getStringDefault(map: Map<*, *>, key: Enum<*>, defaultValue: String): String {
        return getStringDefault(map, key.name, defaultValue)
    }

    fun getStringDefault(map: Map<*, *>, key: String, defaultValue: String): String {
        var ret = getString(map, key)
        if (ret == null) {
            ret = defaultValue
        }
        return ret
    }

    fun getInteger(map: Map<*, *>, key: Enum<*>): Int? {
        return getInteger(map, key.name)
    }

    fun getInteger(map: Map<*, *>, key: String): Int? {
        val value = get(map, key)
        if (value is Int) {
            return value
        } else if (value is String) {
            return Integer.valueOf(value)
        }

        return null
    }

    fun getIntegerDefault(map: Map<*, *>, key: Enum<*>, defaultValue: Int): Int {
        return getIntegerDefault(map, key.name, defaultValue)
    }

    fun getIntegerDefault(map: Map<*, *>, key: String, defaultValue: Int): Int {
        var ret = getInteger(map, key)
        if (ret == null) {
            ret = defaultValue
        }

        return ret
    }

    /*
        {SEQUENCE.OUTPUT} 형태를 처리한다.
        {SEQUENCE.{INOUT}} 등의 중첩은 처리하지 못한다
     */
    fun evaluate(map: Map<*, *>, str: String): String {
        var str = str
        val pattern = Pattern.compile("\\{[^\\s{}]+\\}")
        val matcher = pattern.matcher(str)

        val findGroup = ArrayList<String>()
        while (matcher.find()) {
            findGroup.add(str.substring(matcher.start(), matcher.end()))
        }

        for (find in findGroup) {
            val value = getString(map, find.substring(1, find.length - 1))
            if (value != null) {
                str = str.replace(find, value)
            } else {
            }
        }

        return str
    }
}
