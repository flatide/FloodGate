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

package com.flatide.floodgate.core.agent.logging

import com.flatide.floodgate.core.ConfigurationManager
import com.flatide.floodgate.core.system.datasource.FDataSource
import com.flatide.floodgate.core.system.datasource.FDataSourceDB
import com.flatide.floodgate.core.system.datasource.FDataSourceDefault
import com.flatide.floodgate.core.system.datasource.FDataSourceFile
import org.apache.logging.log4j.LogManager

/*
    MetaManager와 달리 메모리에 캐시하지 않는다
 */

object LoggingManager {
    // NOTE spring boot의 logback을 사용하려면 LogFactory를 사용해야 하나, 이 경우 log4j 1.x와 충돌함(SoapUI가 사용)
    private val logger = LogManager.getLogger(LoggingManager::class.java)

    // data source
    var dataSource: FDataSource? = null

    var tableKeyMap: MutableMap<String, String>? = null

    init {
        try {
            initDataSource(FDataSourceDefault())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }


    fun initDataSource(dataSource: FDataSource, reset: Boolean = true) {
        logger.debug("Set DataSource as " + dataSource.name + " with reset = " + reset)

        this.tableKeyMap = HashMap()
        this.dataSource = dataSource
        dataSource.connect()
    }

    fun changeSource(source: String, reset: Boolean): FDataSource {
        if (source != this.dataSource!!.name) {
            val type = ConfigurationManager.getString("datasource.$source.type")
            dataSource = when (type) {
                "FILE" -> FDataSourceFile(source)
                "DB" -> FDataSourceDB(source)
                else -> FDataSourceDefault()
            }

            initDataSource(dataSource!!, reset)
        }

        return this.dataSource!!
    }

    fun close() {
        this.tableKeyMap = null

        if (this.dataSource != null) {
            this.dataSource!!.close()
            this.dataSource = null
        }
    }

    // 메타 생성
    private fun create(key: String): Boolean {
        return dataSource!!.create(key)
    }

    // 메타 수정
    fun insert(tableName: String, keyName: String, data: Map<String, Any>): Boolean {
        try {
            return dataSource!!.insert(tableName, keyName, data)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return false
    }

    // 메타 수정
    fun update(tableName: String, keyName: String, data: Map<String, Any>): Boolean {
        try {
            return dataSource!!.update(tableName, keyName, data)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return false
    }

    fun read(tableName: String, keyName: String, data: Map<String, Any>): Boolean {
        try {
            return dataSource!!.update(tableName, keyName, data)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return false
    }
}
