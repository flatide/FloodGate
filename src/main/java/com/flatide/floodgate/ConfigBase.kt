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

package com.flatide.floodgate

abstract class ConfigBase {
    var config: MutableMap<String, Any> = HashMap()

    fun get(path: String): Any? {
        val keys = path.split(".")

        var cur: Any? = this.config
        for (i in keys.indices) {
            if (cur == null || cur is String) {
                return null
            }

            val key = keys[i]

            if (cur is String) {
                // unreachable due to check above, kept for 1:1
            } else if (cur is Map<*, *>) {
                cur = (cur as Map<String, Any?>)[key]
            } else if (cur is List<*>) {
                val num = Integer.parseInt(key)
                cur = (cur as List<Any?>)[num]
            }
        }

        return cur
    }
}
