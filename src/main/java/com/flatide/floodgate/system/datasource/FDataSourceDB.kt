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

package com.flatide.floodgate.system.datasource

import com.fasterxml.jackson.databind.ObjectMapper
import com.flatide.floodgate.ConfigurationManager
import com.flatide.floodgate.agent.meta.MetaManager
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource

import org.apache.logging.log4j.LogManager

import java.io.BufferedReader
import java.sql.*

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.PreparedStatementCreator
import org.springframework.jdbc.core.RowMapper

class FDataSourceDB constructor(name: String) : FDataSourceDefault(name) {
    companion object {
        private val logger = LogManager.getLogger(MetaManager::class.java)
    }

    private val jdbcTemplate: JdbcTemplate

    var connection: Connection? = null

    var url: String? = null

    var user: String? = null

    var password: String? = null

    var maxPoolSize: Int? = null

    init {
        this.url = ConfigurationManager.getString("datasource.$name.url")
        this.user = ConfigurationManager.getString("datasource.$name.user")
        this.password = ConfigurationManager.getString("datasource.$name.password")
        this.maxPoolSize = ConfigurationManager.getInteger("datasource.$name.maxPoolSize")

        val dataSource: javax.sql.DataSource
        try {
            val config = HikariConfig()
            config.jdbcUrl = this.url
            config.username = this.user
            config.password = this.password
            config.isAutoCommit = true
            config.maximumPoolSize = this.maxPoolSize!!
            dataSource = HikariDataSource(config)
        } catch (e: Exception) {
            e.printStackTrace()
            throw e
        }

        this.jdbcTemplate = JdbcTemplate(dataSource)
    }

    override fun connect(): Boolean {
        return true
    }

    override fun getAllKeys(tableName: String, keyColumn: String): List<String> {
        val query = "SELECT $keyColumn FROM $tableName"
        logger.debug(query)

        return jdbcTemplate.query(
            PreparedStatementCreator { con ->
                con.prepareStatement(query)
            },
            RowMapper<String> { rs, _ ->
                rs.getString(1)
            }
        )
    }

    override fun create(key: String): Boolean {
        return false
    }

    @Suppress("UNCHECKED_CAST")
    override fun read(tableName: String, keyColumn: String, key: String): Map<String, Any> {
        val query = "SELECT * FROM $tableName WHERE $keyColumn = ?"
        logger.debug(query)

        //return (Map<String, Object>) jdbcTemplate.queryForMap(query, new String[] {key});

        val result = jdbcTemplate.query(
            PreparedStatementCreator { con ->
                val psmt = con.prepareStatement(query)
                psmt.setString(1, key)
                psmt
            },
            RowMapper<Map<String, Any>> { rs, _ ->
                val map = HashMap<String, Any>()

                val rsMeta = rs.metaData
                val number = rsMeta.columnCount

                for (i in 1..number) {
                    val name = rsMeta.getColumnName(i)
                    var obj: Any? = rs.getObject(name)

                    if (obj is oracle.sql.TIMESTAMP) {
                        // Jackson cannot (de)serialize oracle.sql.TIMESTAMP, converting it to java.sql.Timestamp
                        obj = obj.timestampValue()
                        map[name] = obj
                    } else if (obj is Clob) {
                        // Jackson cannot convert LOB directly, converting it to String
                        val sb = StringBuilder()
                        val reader = obj.characterStream
                        val br = BufferedReader(reader)

                        try {
                            var b: Int
                            while (br.read().also { b = it } != -1) {
                                sb.append(b.toChar())
                            }

                            br.close()

                            val mapper = ObjectMapper()
                            val json = mapper.readValue(sb.toString(), Map::class.java) as Map<String, Any>
                            map[name] = json
                        } catch (e: Exception) {
                            throw SQLException(e)
                        }
                    }
                }
                map
            }
        )

        if (result != null && result.size > 0) {
            return result[0]
        }

        return emptyMap()
    }

    override fun readList(tableName: String, keyColumn: String, key: String): List<Map<String, Any>> {
        var query = "SELECT * FROM $tableName"
        if (key.isNotEmpty()) {
            query += " WHERE $keyColumn like ?"
        }
        logger.debug(query)

        // TODO modify using RowMapper
        return if (key.isNotEmpty()) {
            jdbcTemplate.queryForList(query, "%$key%")
        } else {
            jdbcTemplate.queryForList(query)
        }
    }

    override fun insert(tableName: String, keyColumn: String, row: Map<String, Any>): Boolean {
        val colList = ArrayList<String>()

        val query = StringBuilder()
        query.append("INSERT INTO ")
        query.append(tableName)
        query.append(" ( ")

        val param = StringBuilder()
        param.append(" ) VALUES ( ")

        var i = 0
        for (col in row.keys) {
            if (i > 0) {
                query.append(", ")
            }
            query.append(col)
            colList.add(col)

            if (i > 0) {
                param.append(", ")
            }
            param.append("?")
            i++
        }
        param.append(" ) ")
        query.append(param)

        logger.debug(query.toString())

        val count = jdbcTemplate.update(PreparedStatementCreator { con ->
            val psmt = con.prepareStatement(query.toString())

            var idx = 1
            for (col in colList) {
                val data = row[col]
                if (data == null) {
                    psmt.setNull(idx++, Types.NULL)
                } else {
                    when (data) {
                        is String -> psmt.setString(idx++, data)
                        is Int -> psmt.setInt(idx++, data)
                        is java.sql.Date -> psmt.setDate(idx++, data)
                        is Time -> psmt.setTime(idx++, data)
                        is Timestamp -> psmt.setTimestamp(idx++, data)
                        else -> {
                            val mapper = ObjectMapper()
                            try {
                                val json = mapper.writeValueAsString(data)
                                psmt.setString(idx++, json)
                            } catch (e: Exception) {
                                e.printStackTrace()
                                psmt.setObject(idx++, data)
                            }
                        }
                    }
                }
            }
            psmt
        })
        return count != 0
    }

    override fun update(tableName: String, keyColumn: String, row: Map<String, Any>): Boolean {
        val colList = ArrayList<String>()

        val mutableRow = row.toMutableMap()
        val key = mutableRow.remove(keyColumn) as String

        val query = StringBuilder()
        query.append("UPDATE ")
        query.append(tableName)
        query.append(" SET ")

        var i = 0
        for (col in mutableRow.keys) {
            if (i > 0) {
                query.append(", ")
            }
            query.append(col)
            colList.add(col)
            query.append(" = ?")
            i++
        }
        query.append(" WHERE $keyColumn = ? ")

        logger.debug(query.toString())
        val count = jdbcTemplate.update(PreparedStatementCreator { con ->
            val psmt = con.prepareStatement(query.toString())

            var idx = 1

            for (col in colList) {
                val data = mutableRow[col]
                if (data == null) {
                    psmt.setNull(idx++, Types.NULL)
                } else {
                    when (data) {
                        is String -> psmt.setString(idx++, data)
                        is Int -> psmt.setInt(idx++, data)
                        is java.sql.Date -> psmt.setDate(idx++, data)
                        is Time -> psmt.setTime(idx++, data)
                        is Timestamp -> psmt.setTimestamp(idx++, data)
                        else -> {
                            val mapper = ObjectMapper()
                            try {
                                val json = mapper.writeValueAsString(data)
                                psmt.setString(idx++, json)
                            } catch (e: Exception) {
                                psmt.setObject(idx++, data)
                            }
                        }
                    }
                }
            }

            psmt.setString(idx, key)
            psmt
        })

        return count != 0
    }

    override fun delete(tableName: String, keyColumn: String, key: String): Boolean {
        val query = StringBuilder()
        query.append("DELETE FROM ")
            .append(tableName)
            .append(" WHERE ")
            .append(keyColumn)
            .append(" = ?")

        val count = jdbcTemplate.update(query.toString(), key)
        return count != 0
    }

    override fun deleteAll(): Int {
        return 0
    }

    override fun flush() {
    }

    override fun close() {
        try {
            if (this.connection != null) {
                this.connection!!.close()
                this.connection = null
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
