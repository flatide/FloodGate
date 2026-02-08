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

import java.sql.Connection
import java.sql.DriverManager
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.util.ArrayList
import java.util.HashMap

object DBUtils {

    fun makeURL(dbType: String, ipList: String, port: String, sid: String, delimiter: String): String {
        val ipports = ipList.split(delimiter.toRegex()).toTypedArray()//("\\|");

        for (i in ipports.indices) {
            ipports[i] = ipports[i].trim() + ":" + port
        }

        return makeURL(dbType, ipports, sid)
    }

    fun makeURL(dbType: String, ipport: String, sid: String, delimiter: String): String {
        val ipports = ipport.split(delimiter.toRegex()).toTypedArray()//(",");

        return makeURL(dbType, ipports, sid)
    }

    fun makeURL(dbType: String, ipports: Array<String>, sid: String): String {
        var sid = sid
        when (dbType.trim().uppercase()) {
            "ORACLE" -> {
                if (ipports.size == 1) {
                    if (sid.startsWith("/")) { // for ServiceName
                        return "jdbc:oracle:thin:@" + ipports[0].trim() + sid.trim()
                    }
                    return "jdbc:oracle:thin:@" + ipports[0].trim() + ":" + sid.trim()
                } else {
                    var url = "jdbc:oracle:thin:@(DESCRIPTION=(FAILOVER=ON)(LOAD_BALANCE=OFF)(ADDRESS_LIST="
                    for (i in ipports.indices) {
                        val ip_port = ipports[i].split(":")
                        url += "(ADDRESS=(PROTOCOL=TCP)(HOST=" + ip_port[0] + ")(PORT=" + ip_port[1] + "))"
                    }

                    sid = sid.replace("/", "").trim()
                    return url + ")(CONNECT_DATA=(SERVICE_NAME=" + sid + ")(FAILOVER_MODE=(TYPE=SELECT)(METHOD=BASIC)(RETRIES=25)(DELAY=10))))"
                }
            }
            "TIBERO" -> {
                if (ipports.size == 1) {
                    if (sid.startsWith("/")) { // for ServiceName
                        return "jdbc:tibero:thin:@" + ipports[0].trim() + sid.trim()
                    }
                    return "jdbc:tibero:thin:@" + ipports[0].trim() + ":" + sid.trim()
                } else {
                    var url = "jdbc:tibero:thin:@(DESCRIPTION=(FAILOVER=ON)(LOAD_BALANCE=OFF)(ADDRESS_LIST="
                    for (i in ipports.indices) {
                        val ip_port = ipports[i].split(":")
                        url += "(ADDRESS=(PROTOCOL=TCP)(HOST=" + ip_port[0] + ")(PORT=" + ip_port[1] + "))"
                    }
                     sid = sid.replace("/", "").trim()
                     return url + ")(CONNECT_DATA=(SERVICE_NAME=" + sid + ")(FAILOVER_MODE=(TYPE=SELECT)(METHOD=BASIC)(RETRIES=25)(DELAY=10))))"
                }
            }
            "POSTGRESQL" ->
                return "jdbc:postgresql://" + ipports[0] + "/" + sid
            "GREENPLUM" ->
                return "jdbc:postgresql://" + ipports[0] + "/" + sid
            "MSSQL" ->
                return "jdbc:sqlserver://" + ipports[0] + ";databaseName=" + sid
            "MYSQL_OLD" ->
                return "jdbc:mysql://" + ipports[0] + "/" + sid + "?characterEncoding=UTF-8&useConfigs=maxPerformance"
            "MYSQL" ->
                return "jdbc:mysql://" + ipports[0] + "/" + sid + "?serverTimezone=UTC&characterEncoding=UTF-8&useConfigs=maxPerformance"
            "MARIADB" ->
                return "jdbc:mysql://" + ipports[0] + "/" + sid + "?characterEncoding=UTF-8&useConfigs=maxPerformance"
            "DB2" ->
                return "jdbc:db2://" + ipports[0] + "/" + sid
            else ->
                return ""
        }
    }

    fun addQueryLimitation(dbType: String, columns: String, table: String, limit: Int): String {
        var query = "SELECT $columns FROM $table"

        when (dbType.trim().uppercase()) {
            "ORACLE" ->
                query = query + " WHERE ROWNUM <= " + limit.toString()
            "TIBERO" ->
                query = query + " WHERE ROWNUM <= " + limit.toString()
            "MYSQL" ->
                query = query + " LIMIT " + limit.toString()
            "MARIADB" ->
                query = query + " LIMIT " + limit.toString()
            "POSTGRESQL" ->
                query = query + " LIMIT " + limit.toString()
            "GREENPLUM" ->
                query = query + " LIMIT " + limit.toString()
            "MSSQL" ->
                query = "SELECT TOP " + limit.toString() + " " + columns + " FROM " + table
            "DB2" ->
                query = query + " FETCH FIRST " + limit.toString() + " ROWS ONLY"
            else ->
                query = query + " WHERE ROWNUM <= " + limit.toString()
        }

        return query
    }

    fun connect(url: String, userid: String, passwd: String): String {
        try {
            DriverManager.getConnection(url, userid, passwd).use { con ->
                val databaseMetaData = con.metaData

                // return String.format("JDBC Version : %d.%d", databaseMetaData.getJDBCMajorVersion(), databaseMetaData.getJDBCMinorVersion());
                return String.format("%s : %s", databaseMetaData.databaseProductName, databaseMetaData.databaseProductVersion)
            }
        } catch (e: Exception) {
            throw e
        }
    }

    fun checkTable(url: String, userid: String, passwd: String, table: String): Boolean {
        try {
            DriverManager.getConnection(url, userid, passwd).use { con ->
                val databaseMetaData = con.metaData

                val resultSet = databaseMetaData.getTables(null, null, table, arrayOf("TABLE"))

                return resultSet.next()
            }
        } catch (e: Exception) {
            throw e
        }
    }

    fun getColumnMap(dbType: String, url: String, userid: String, passwd: String, table: String): Map<String, Any> {
        val query = DBUtils.addQueryLimitation(dbType, "*", table, 1)

        DriverManager.getConnection(url, userid, passwd).use { con ->
            con.prepareStatement(query).use { ps ->
                try {
                    val rs = ps.executeQuery()
                    val rsmeta = rs.metaData
                    val count = rsmeta.columnCount

                    val columnList = ArrayList<String>()
                    val columnMap = HashMap<String, Any>()
                    for (i in 1..count) {
                        columnList.add(rsmeta.getColumnLabel(i))

                        val info = HashMap<String, Any>()
                        info["DisplaySize"] = rsmeta.getColumnDisplaySize(i)
                        info["TypeName"] = rsmeta.getColumnTypeName(i)

                        columnMap[rsmeta.getColumnLabel(i)] = info
                    }

                    return columnMap
                } catch (e: Exception) {
                    e.printStackTrace()
                    throw e
                }
            }
        }
    }
}
