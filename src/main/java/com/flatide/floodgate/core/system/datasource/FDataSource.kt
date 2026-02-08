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

package com.flatide.floodgate.core.system.datasource

interface FDataSource {
    fun connect(): Boolean

    var name: String

    fun getAllKeys(tableName: String, keyColumn: String): List<String>

    fun create(key: String): Boolean

    fun read(tableName: String, keyColumn: String, key: String): Map<String, Any>

    fun readList(tableName: String, keyColumn: String, key: String): List<Map<String, Any>>

    fun insert(tableName: String, keyColumn: String, row: Map<String, Any>): Boolean

    fun update(tableName: String, keyColumn: String, row: Map<String, Any>): Boolean

    fun delete(tableName: String, keyColumn: String, key: String): Boolean

    fun deleteAll(): Int

    fun flush()

    fun close()
}
