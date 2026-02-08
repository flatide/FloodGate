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

package com.flatide.floodgate.core.agent.meta

import com.fasterxml.jackson.databind.ObjectMapper
import com.flatide.floodgate.core.ConfigurationManager
import com.flatide.floodgate.core.system.datasource.FDataSource
import com.flatide.floodgate.core.system.datasource.FDataSourceDB
import com.flatide.floodgate.core.system.datasource.FDataSourceDefault
import com.flatide.floodgate.core.system.datasource.FDataSourceFile
import org.apache.logging.log4j.LogManager

import java.io.BufferedReader
import java.sql.Clob

/*
    TODO Meta의 생성, 삭제, 변경등은 반드시 DataSource에 먼저 반영하고 캐시를 업데이트 하는 순서로 진행할 것!
    캐시 업데이트 타이밍은 실시간일 필요는 없다.
 */
object MetaManager {
    // NOTE spring boot의 logback을 사용하려면 LogFactory를 사용해야 하나, 이 경우 log4j 1.x와 충돌함(SoapUI가 사용)
    private val logger = LogManager.getLogger(MetaManager::class.java)

    // cache - permanent 구조
    // TODO datasource에서 caching하도록 수정할 것
    var cache: MutableMap<String, MetaTable>? = null

    var tableKeyMap: MutableMap<String, String>? = null

    // data source
    var dataSource: FDataSource? = null


    init {
        try {
            setMetaSource(FDataSourceDefault(), true)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun changeSource(source: String, reset: Boolean): FDataSource {
        if (source != metaSourceName) {
            val type = ConfigurationManager.getString("datasource.$source.type")
            dataSource = when (type) {
                "FILE" -> FDataSourceFile(source)
                "DB" -> FDataSourceDB(source)
                else -> FDataSourceDefault()
            }

            setMetaSource(dataSource!!, reset)
        }

        return this.dataSource!!
    }

    fun close() {
        if (this.cache != null) {
            // TODO store cache to permanent storage
            this.cache = null
            this.tableKeyMap = null
        }

        if (this.dataSource != null) {
            this.dataSource!!.close()
            this.dataSource = null
        }
    }

    val metaSourceName: String
        get() = this.dataSource!!.name

    val info: Map<String, Any>
        get() {
            val info = HashMap<String, Any>()

            info["MetaSource"] = this.dataSource!!.name

            for ((key, value) in this.cache!!) {
                info[key] = value.size()
            }

            return info
        }

    fun setMetaSource(FGDataSource: FDataSource, reset: Boolean) {
        logger.debug("Set MetaSource as " + FGDataSource.name + " with reset = " + reset)
        if (reset) {
            if (this.cache != null) {
                // TODO store cache to permanent storage
                this.cache = null
            }

            this.cache = HashMap()
        }
        this.tableKeyMap = HashMap()
        this.dataSource = FGDataSource
        FGDataSource.connect()
    }

    fun getTable(tableName: String): MetaTable? {
        return this.cache!![tableName]
    }

    /*public void addKeyName(String table, String key) {
        this.tableKeyMap.put(table, key);
    }*/

    fun create(table: String, key: String): Boolean {
        return create(key)
    }

    // 메타 생성
    private fun create(key: String): Boolean {
        if (this.cache!![key] != null) {
            return false
        }

        if (dataSource!!.create(key)) {
            this.cache!![key] = MetaTable()
            return true
        }

        return false
    }

    // 메타 조회
    fun read(tableName: String, key: String, fromSource: Boolean = false): Map<String, Any>? {
        // TODO for testing
        @Suppress("NAME_SHADOWING")
        val fromSource = true

        var table = this.cache!![tableName]
        if (table == null) {
            table = MetaTable()
            this.cache!![tableName] = table
        }

        var keyName = this.tableKeyMap!![tableName]
        if (keyName == null) {
            keyName = "ID"
        }

        if (/*output.get(key) == null || */fromSource) {
            try {
                val result = this.dataSource!!.read(tableName, keyName, key)

                return result
            } catch (e: Exception) {
                e.printStackTrace()
                throw e
            }
        }

        return null
    }

    @Suppress("UNCHECKED_CAST")
    fun readList(tableName: String, key: String, fromSource: Boolean = false): List<Map<String, Any>>? {
        @Suppress("NAME_SHADOWING")
        val fromSource = true

        var resultList: List<Map<String, Any>>? = null
        var table = this.cache!![tableName]
        if (table == null) {
            table = MetaTable()
            this.cache!![tableName] = table
        }

        var keyName = this.tableKeyMap!![tableName]
        if (keyName == null) {
            keyName = "ID"
        }

        if (fromSource) {
            try {
                val mutableResultList = this.dataSource!!.readList(tableName, keyName, key).map { it.toMutableMap() }

                for (result in mutableResultList) {
                    for ((entryKey, obj) in result.entries.toList()) {
                        if (obj is oracle.sql.TIMESTAMP) {
                            // Jackson cannot (de)serialize oracle.sql.TIMESTAMP, converting it to java.sql.Timestamp
                            result[entryKey] = obj.timestampValue()
                        } else if (obj is Clob) {
                            // Jackson cannot convert LOB directly, converting it to String
                            val sb = StringBuilder()
                            val reader = obj.characterStream
                            val br = BufferedReader(reader)

                            var b: Int
                            while (br.read().also { b = it } != -1) {
                                sb.append(b.toChar())
                            }

                            br.close()

                            val mapper = ObjectMapper()
                            val json = mapper.readValue(sb.toString(), Map::class.java) as Map<String, Any>
                            result[entryKey] = json
                        }
                    }
                }

                //table.put(key, result);
                return mutableResultList
            } catch (e: Exception) {
                e.printStackTrace()
                throw e
            }
        }

        return null
    }

    // 메타 수정
    fun insert(tableName: String, keyName: String, data: Map<String, Any>, toSource: Boolean): Boolean {
        // TODO Thread Safe
        var table = this.cache!![tableName]

        if (table == null) {
            table = MetaTable()
            this.cache!![tableName] = table
        }

        /*String keyName = this.tableKeyMap.get(tableName);
        if( keyName == null ) {
            keyName = "ID";
        }*/

        try {
            var result = true
            if (toSource) {
                result = dataSource!!.insert(tableName, keyName, data)
            }
            if (result) {
                table.put(keyName, data)
            }

            return result
        } catch (e: Exception) {
            e.printStackTrace()
            throw e
        }
    }

    fun update(tableName: String, keyName: String, data: Map<String, Any>, toSource: Boolean = false): Boolean {
        // TODO Thread Safe
        val table = this.cache!![tableName] ?: return false

        /*String keyName = this.tableKeyMap.get(tableName);
        if( keyName == null ) {
            keyName = "ID";
        }*/

        try {
            var result = true
            if (toSource) {
                result = dataSource!!.update(tableName, keyName, data)
            }
            if (result) {
                table.put(keyName, data)
            }

            return result
        } catch (e: Exception) {
            e.printStackTrace()
            throw e
        }
    }

    // 메타 삭제
    fun delete(tableName: String, key: String, toSource: Boolean): Boolean {
        val table = this.cache!![tableName] ?: return false

        var keyName = this.tableKeyMap!![tableName]
        if (keyName == null) {
            keyName = "ID"
        }

        try {
            var result = true
            if (toSource) {
                result = dataSource!!.delete(tableName, keyName, key)
            }
            if (result) {
                table.remove(key)
            }
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            throw e
        }
    }

    fun load(tableName: String): Boolean {
        var table = this.cache!![tableName]
        if (table == null) {
            table = MetaTable()//HashMap<>();
            this.cache!![tableName] = table
        }

        var keyName = this.tableKeyMap!![tableName]
        if (keyName == null) {
            keyName = "ID"
        }

        val keyList = this.dataSource!!.getAllKeys(tableName, keyName)

        for (key in keyList) {
            read(tableName, key, true)
        }

        return true
    }

    // 메타 저장 cache -> metaSource
    fun store(tableName: String, all: Boolean): Boolean {
        val table = this.cache!![tableName] ?: return false

        var keyName = this.tableKeyMap!![tableName]
        if (keyName == null) {
            keyName = "ID"
        }

        val rows = table.rows

        for ((key, data) in rows) {
            if (!dataSource!!.update(tableName, keyName, data)) {
                dataSource!!.insert(tableName, keyName, data)
            }
        }

        return true
    }
}
