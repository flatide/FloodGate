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

package com.flatide.floodgate.core.agent.connector

object ConnectorFactory {
    //Logger logger = LogManager.getLogger(ConnectorFactory.class);

    fun getConnector(info: Map<String, Any>): ConnectorBase? {
        val method = info[ConnectorTag.CONNECTOR.name] as String

        val con: ConnectorBase = when (method) {
            "JDBC" -> {
                val dbType = info[ConnectorTag.DBTYPE.name] as String
                loadDriver(dbType)
                ConnectorDB()
            }
            "FILE" -> ConnectorFile()
            "FTP" -> ConnectorFTP()
            "SFTP" -> ConnectorSFTP()
            else -> return null
        }

        return con
    }

    private fun loadDriver(dbType: String) {
        when (dbType.trim().lowercase()) {
            "oracle" -> Class.forName("oracle.jdbc.driver.OracleDriver")
            "mysql" -> Class.forName("com.mysql.cj.jdbc.Driver")
            "mysql_old" -> Class.forName("com.mysql.jdbc.Driver")
            "mariadb" -> Class.forName("com.mysql.jdbc.Driver")
            "postgresql" -> Class.forName("org.postgresql.Driver")
            "greenplum" -> Class.forName("org.postgresql.Driver")
            "mssql" -> Class.forName("com.microsoft.sqlserver.jdbc.SQLServerDriver")
            "db2" -> Class.forName("com.ibm.db2.jcc.DB2Driver")
            "tibero" -> Class.forName("com.tmax.tibero.jdbc.TbDriver")
            else -> {}
        }
    }
}
