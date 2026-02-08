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

import com.flatide.floodgate.core.agent.Context
import com.flatide.floodgate.core.agent.Context.CONTEXT_KEY
import com.flatide.floodgate.core.agent.connector.function.FloodgateFunctionManager
import com.flatide.floodgate.core.agent.flow.rule.MappingRuleItem
import com.flatide.floodgate.core.agent.handler.FloodgateHandlerManager
import com.flatide.floodgate.core.agent.handler.FloodgateHandlerManager.Step
import com.flatide.floodgate.core.agent.flow.rule.MappingRule
import com.flatide.floodgate.core.agent.flow.FlowTag
import com.flatide.floodgate.core.agent.flow.module.Module
import com.flatide.floodgate.core.agent.flow.module.ModuleContext
import com.flatide.floodgate.core.agent.flow.module.ModuleContext.MODULE_CONTEXT
import com.flatide.floodgate.core.agent.flow.rule.FunctionProcessor
import com.flatide.floodgate.core.system.FlowEnv
import com.flatide.floodgate.core.system.security.FloodgateSecurity
import com.flatide.floodgate.core.system.utils.DBUtils
import com.flatide.floodgate.core.system.utils.PropertyMap

//import com.zaxxer.hikari.HikariConfig;
//import com.zaxxer.hikari.HikariDataSource;
import org.apache.logging.log4j.LogManager

import javax.sql.DataSource
import java.sql.*
import java.sql.Date
import java.util.concurrent.ConcurrentHashMap
import java.util.regex.Pattern

@Suppress("UNCHECKED_CAST")
class ConnectorDB : ConnectorBase() {
    // NOTE spring boot의 logback을 사용하려면 LogFactory를 사용해야 하나, 이 경우 log4j 1.x와 충돌함(SoapUI가 사용)

    companion object {
        private val logger = LogManager.getLogger(ConnectorDB::class.java)
        private val pools = ConcurrentHashMap<String, DataSource>()

        val PRE_PROCESSOR_ORACLE: FunctionProcessor = DBFunctionProcessorOracle()
        val PRE_PROCESSOR_TIBERO: FunctionProcessor = DBFunctionProcessorTibero()
        val PRE_PROCESSOR_MYSQL: FunctionProcessor = DBFunctionProcessorMySql()
        val PRE_PROCESSOR_MARIADB: FunctionProcessor = DBFunctionProcessorMariaDB()
        val PRE_PROCESSOR_POSTGRESQL: FunctionProcessor = DBFunctionProcessorPostgreSQL()
        val PRE_PROCESSOR_GREENPLUM: FunctionProcessor = DBFunctionProcessorGreenplum()
        val PRE_PROCESSOR_MSSQL: FunctionProcessor = DBFunctionProcessorMSSQL()
        val PRE_PROCESSOR_DB2: FunctionProcessor = DBFunctionProcessorDB2()

        fun buildInsertSql(context: Context, rule: MappingRule, items: List<Map<String, Any>>): String {
            val mockData = HashMap<String, Any>()
            for (e in items[0].entries) {
                mockData[e.key] = "?"
            }

            val target = context.evaluate("{SEQUENCE.TARGET}")
            val colNames = StringBuilder()
            val values = StringBuilder()
            var idx = 0
            for (item in rule.getRules()) {
                if (idx > 0) {
                    colNames.append(", ")
                    values.append(", ")
                }
                colNames.append(item.targetName)
                values.append(rule.apply(item, mockData) ?: "?")
                idx++
            }
            return "INSERT INTO $target ($colNames) VALUES ($values)"
        }
    }

    var channelContext: Context? = null
    var moduleContext: ModuleContext? = null

    var module: Module? = null

    var connectInfo: Map<*, *>? = null

    private var connection: Connection? = null
    private var fetchSize: Int = 0
    private var sizeForUpdateHandler: Int = 0
    private var flush: Boolean = false
    private var retrieve: Int = 0

    private var batchSize: Int = 0
    private var query: String = ""
    private var ps: PreparedStatement? = null
    private var resultSet: ResultSet? = null

    private var batchCount: Int = 0

    private var param: List<String>? = null

    private var sent: Int = 0

    private var updateCount: Int = 0

    private var errorPosition: Int = -1

    private var cur: Long = 0

    private class DBFunctionProcessorOracle : FunctionProcessor {
        override fun process(item: MappingRuleItem): Any? {
            val func = item.sourceName
            //MappingRule.Function function = MappingRule.Function.valueOf(func);

            return when (func) {
                "TARGET_DATE" -> {
                    //item.setAction(MappingRuleItem.RuleAction.literal);
                    "sysdate"
                }
                else -> "?"
            }
        }
    }

    private class DBFunctionProcessorTibero : FunctionProcessor {
        override fun process(item: MappingRuleItem): Any? {
            val func = item.sourceName

            return when (func) {
                "TARGET_DATE" -> {
                    //item.setAction(MappingRuleItem.RuleAction.literal);
                    "sysdate"
                }
                else -> "?"
            }
        }
    }

    private class DBFunctionProcessorMySql : FunctionProcessor {
        override fun process(item: MappingRuleItem): Any? {
            val func = item.sourceName

            return when (func) {
                "TARGET_DATE" -> {
                    //item.setAction(MappingRuleItem.RuleAction.literal);
                    "now()"
                }
                else -> "?"
            }
        }
    }

    private class DBFunctionProcessorMariaDB : FunctionProcessor {
        override fun process(item: MappingRuleItem): Any? {
            val func = item.sourceName

            return when (func) {
                "TARGET_DATE" -> {
                    //item.setAction(MappingRuleItem.RuleAction.literal);
                    "now()"
                }
                else -> "?"
            }
        }
    }

    private class DBFunctionProcessorPostgreSQL : FunctionProcessor {
        override fun process(item: MappingRuleItem): Any? {
            val func = item.sourceName

            return when (func) {
                "TARGET_DATE" -> {
                    //item.setAction(MappingRuleItem.RuleAction.literal);
                    "now()"
                }
                else -> "?"
            }
        }
    }

    private class DBFunctionProcessorGreenplum : FunctionProcessor {
        override fun process(item: MappingRuleItem): Any? {
            val func = item.sourceName

            return when (func) {
                "TARGET_DATE" -> {
                    //item.setAction(MappingRuleItem.RuleAction.literal);
                    "now()"
                }
                else -> "?"
            }
        }
    }

    private class DBFunctionProcessorMSSQL : FunctionProcessor {
        override fun process(item: MappingRuleItem): Any? {
            val func = item.sourceName

            return when (func) {
                "TARGET_DATE" -> {
                    //item.setAction(MappingRuleItem.RuleAction.literal);
                    "getDate()"
                }
                else -> "?"
            }
        }
    }

    private class DBFunctionProcessorDB2 : FunctionProcessor {
        override fun process(item: MappingRuleItem): Any? {
            val func = item.sourceName

            return when (func) {
                "TARGET_DATE" -> {
                    //item.setAction(MappingRuleItem.RuleAction.literal);
                    "CURRENT TIMESTAMP"
                }
                else -> "?"
            }
        }
    }

    override fun getFunctionProcessor(type: String): FunctionProcessor {
        return when (type.uppercase()) {
            "ORACLE" -> PRE_PROCESSOR_ORACLE
            "TIBERO" -> PRE_PROCESSOR_TIBERO
            "MYSQL" -> PRE_PROCESSOR_MYSQL
            "MARIADB" -> PRE_PROCESSOR_MARIADB
            "POSTGRESQL" -> PRE_PROCESSOR_POSTGRESQL
            "GREENPLUM" -> PRE_PROCESSOR_GREENPLUM
            "MSSQL" -> PRE_PROCESSOR_MSSQL
            "DB2" -> PRE_PROCESSOR_DB2
            else -> PRE_PROCESSOR_ORACLE
        }
    }

    override fun connect(context: Context, module: Module) {
        cur = System.currentTimeMillis()

        this.module = module

        connectInfo = module.getContext().get(MODULE_CONTEXT.CONNECT_INFO) as Map<*, *>
        val url = PropertyMap.getString(connectInfo!!, ConnectorTag.URL)!!
        val user = PropertyMap.getString(connectInfo!!, ConnectorTag.USER)!!
        var password = PropertyMap.getString(connectInfo!!, ConnectorTag.PASSWORD)!!

        channelContext = this.module!!.getFlowContext().get(CONTEXT_KEY.CHANNEL_CONTEXT) as Context
        moduleContext = module.getContext()

        /*if( this.name != null && !this.name.isEmpty() ) {
            DataSource dataSource = pools.get(this.name);
            if( dataSource == null ) {
                HikariConfig config = new HikariConfig();
                config.setJdbcUrl(this.url);
                config.setUsername(this.user);
                config.setPassword(this.password);
                dataSource = new HikariDataSource(config);
                pools.put(this.name, dataSource);
            }
            this.connection = dataSource.getConnection();
        } else*/ run {
            password = FloodgateSecurity.decrypt(password)
            this.connection = DriverManager.getConnection(url, user, password)
        }

        this.connection!!.autoCommit = false

        val meta = this.connection!!.metaData
        logger.debug(meta.databaseProductName + " : " + meta.databaseProductVersion)
    }

    override fun beforeCreate(rule: MappingRule) {
        this.batchSize = PropertyMap.getIntegerDefault(module!!.getSequences(), FlowTag.BATCHSIZE, 1)
    }

    override fun createPartially(items: List<Map<Any?, Any?>>, mappingRule: MappingRule): Int {
        if (this.query.isEmpty()) {
            if (items == null || items.isEmpty()) {
                return 0
            }
            this.query = buildInsertSql(moduleContext!!, mappingRule, items as List<Map<String, Any>>)
            this.param = mappingRule.getParam()
            logger.debug(this.query)
            ps = this.connection!!.prepareStatement(this.query)
        }

        try {
            val timeout = PropertyMap.getIntegerDefault(module!!.getSequences(), FlowTag.TIMEOUT, 0)
            if (items != null) {
                for (item in items) {
                    var i = 1
                    for (key in this.param!!) {
                        if (key.startsWith(">")) {
                            val value = FloodgateFunctionManager.processFunction(moduleContext!!, key.substring(1))
                            ps!!.setObject(i++, value)
                        } else if (key.startsWith("{")) {
                            val value = moduleContext!!.get(key.substring(1, key.length - 1))
                            ps!!.setObject(i++, value)
                        } else {
                            ps!!.setObject(i++, item[key])
                        }
                    }

                    ps!!.addBatch()
                    batchCount++
                    if (batchCount >= batchSize) {
                        ps!!.queryTimeout = timeout
                        cur = System.currentTimeMillis()
                        ps!!.executeBatch()
                        this.sent += batchCount
                        batchCount = 0

                        this.module!!.progress = this.sent
                        FloodgateHandlerManager.handle(Step.MODULE_PROGRESS, channelContext!!, this.module)
                    }
                }
                (items as MutableList).clear()
            }
            if (items == null && batchCount > 0) {
                ps!!.queryTimeout = timeout
                ps!!.executeBatch()
                this.sent += batchCount
                this.module!!.progress = this.sent
                FloodgateHandlerManager.handle(Step.MODULE_PROGRESS, channelContext!!, this.module)
            }
        } catch (e: Exception) {
            errorPosition = ps!!.updateCount
            errorPosition += this.sent

            e.printStackTrace()
            throw e
        }

        return sent
    }

    override fun create(items: List<Map<Any?, Any?>>, mappingRule: MappingRule): Int {
        if (this.query.isEmpty()) {
            this.query = buildInsertSql(moduleContext!!, mappingRule, items as List<Map<String, Any>>)
            this.param = mappingRule.getParam()
            logger.debug(this.query)
            ps = this.connection!!.prepareStatement(this.query)
        }

        try {
            var count = 0
            val timeout = PropertyMap.getIntegerDefault(module!!.getSequences(), FlowTag.TIMEOUT, 0)
            for (item in items) {
                var i = 1
                for (key in this.param!!) {
                    if (key.startsWith(">")) {
                        val value = FloodgateFunctionManager.processFunction(moduleContext!!, key.substring(1))
                        ps!!.setObject(i++, value)
                    } else if (key.startsWith("{")) {
                        val value = moduleContext!!.get(key.substring(1, key.length - 1))
                        ps!!.setObject(i++, value)
                    } else {
                        ps!!.setObject(i++, item[key])
                    }
                }

                ps!!.addBatch()
                count++
                if (count >= batchSize) {
                    ps!!.queryTimeout = timeout
                    ps!!.executeBatch()
                    this.sent += count
                    count = 0

                    this.module!!.progress = this.sent
                    FloodgateHandlerManager.handle(Step.MODULE_PROGRESS, channelContext!!, this.module)
                }
            }
            if (count > 0) {
                ps!!.queryTimeout = timeout
                ps!!.executeBatch()
                this.sent += count
                this.module!!.progress = this.sent
                FloodgateHandlerManager.handle(Step.MODULE_PROGRESS, channelContext!!, this.module)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            throw e
        }

        return sent
    }

    override fun afterCreate(rule: MappingRule) {
    }

    override fun commit() {
        this.connection!!.commit()
    }

    override fun rollback() {
        this.sent = 0
        this.connection!!.rollback()
        this.module!!.progress = 0
        FloodgateHandlerManager.handle(Step.MODULE_PROGRESS, channelContext!!, this.module)
    }

    override fun beforeRead(rule: MappingRule) {
        val table = PropertyMap.getString(this.module!!.getSequences(), FlowTag.TARGET)
        val sql = PropertyMap.getString(this.module!!.getSequences(), FlowTag.SQL)
        val condition = PropertyMap.getString(this.module!!.getSequences(), FlowTag.CONDITION)

        var query = ""

        if (sql != null) {
            query = sql
        } else {
            query = "SELECT "
            val sourceSet = LinkedHashSet<String>()

            for (item in rule.getRules()) {
                when (item.action) {
                    MappingRuleItem.RuleAction.system -> {}
                    MappingRuleItem.RuleAction.function -> {}
                    MappingRuleItem.RuleAction.literal -> {
                        // it is possible that source column is exist in literal
                        val source = item.sourceName

                        val pattern = Pattern.compile("\\$.+?\\$")
                        val matcher = pattern.matcher(source)

                        while (matcher.find()) {
                            val col = source.substring(matcher.start() + 1, matcher.end() - 1)
                            sourceSet.add(col)
                        }
                    }
                    MappingRuleItem.RuleAction.reference -> {
                        sourceSet.add(item.sourceName)
                        // source column
                    }
                    MappingRuleItem.RuleAction.order -> {}
                    else -> {}
                }
            }

            var i = 0
            if (sourceSet.isEmpty()) {
                // when column for selection is not exist
                query += " * "
            } else {
                for (source in sourceSet) {
                    if (i > 0) {
                        query += ", "
                    }
                    query += source
                    i++
                }
            }

            query += " FROM $table"
            if (condition != null && condition.isNotEmpty()) {
                query += " WHERE $condition"
            }
            logger.debug(query)
        }

        this.fetchSize = PropertyMap.getIntegerDefault(this.module!!.getSequences(), FlowTag.FETCHSIZE, 0)
        this.sizeForUpdateHandler = if (fetchSize < 1000) 3000 else fetchSize * 3
        this.flush = PropertyMap.getDefault(this.module!!.getSequences(), FlowTag.FLUSH, java.lang.Boolean.valueOf(false)) as Boolean

        this.ps = this.connection!!.prepareStatement(query)
        this.resultSet = ps!!.executeQuery()

        if (fetchSize > 0) {
            this.resultSet!!.fetchSize = fetchSize
        }
    }

    override fun readBuffer(rule: MappingRule, buffer: MutableList<Any?>, limit: Int): Int {
        val rsmeta = this.resultSet!!.metaData

        val count = rsmeta.columnCount
        var c = 0
        while (this.resultSet!!.next()) {
            if (!this.flush) {
                val column = LinkedHashMap<String, Any?>()
                for (i in 1..count) {
                    var row: Any? = this.resultSet!!.getObject(i)

                    if (row is oracle.sql.TIMESTAMP) {
                        // Jackson cannot (de)serialize oracle.sql.TIMESTAMP, converting it to java.sql.Timestamp
                        row = row.timestampValue()
                    }
                    // TODO process Clob and skip Blob
                    column[rsmeta.getColumnLabel(i)] = row
                }

                buffer.add(column)
            }

            c++
            this.updateCount++
            if (this.updateCount > this.sizeForUpdateHandler) {
                this.updateCount = 0
                this.module!!.progress = retrieve
                FloodgateHandlerManager.handle(Step.MODULE_PROGRESS, this.channelContext!!, this.module)
            }
            if (c >= limit) {
                this.retrieve += c
                c = 0
                break
            }
        }
        if (buffer.isEmpty()) {
            this.retrieve += c
            this.module!!.progress = retrieve
            FloodgateHandlerManager.handle(Step.MODULE_PROGRESS, this.channelContext!!, this.module)
        }

        return buffer.size
    }

    override fun readPartially(rule: MappingRule): List<Map<Any?, Any?>> {
        val result = ArrayList<Map<Any?, Any?>>()

        val rsmeta = this.resultSet!!.metaData

        val count = rsmeta.columnCount
        var c = 0
        while (this.resultSet!!.next()) {
            if (!this.flush) {
                val column = LinkedHashMap<Any?, Any?>()
                for (i in 1..count) {
                    var row: Any? = this.resultSet!!.getObject(i)

                    if (row is oracle.sql.TIMESTAMP) {
                        // Jackson cannot (de)serialize oracle.sql.TIMESTAMP, converting it to java.sql.Timestamp
                        row = row.timestampValue()
                    }
                    // TODO process Clob and skip Blob
                    column[rsmeta.getColumnLabel(i)] = row
                }

                result.add(column)
            }

            c++
            if (c >= this.sizeForUpdateHandler) {
                this.retrieve += c
                c = 0
                this.module!!.progress = retrieve
                FloodgateHandlerManager.handle(Step.MODULE_PROGRESS, this.channelContext!!, this.module)
                break
            }
        }
        if (c > 0) {
            this.retrieve += c
            this.module!!.progress = retrieve
            FloodgateHandlerManager.handle(Step.MODULE_PROGRESS, this.channelContext!!, this.module)
        }

        return result
    }

    override fun afterRead() {
    }

    override fun read(rule: MappingRule): List<Map<Any?, Any?>> {
        val result = ArrayList<Map<Any?, Any?>>()
        val rsmeta = this.resultSet!!.metaData

        val count = rsmeta.columnCount
        var c = 0
        while (this.resultSet!!.next()) {
            if (!this.flush) {
                val column = LinkedHashMap<Any?, Any?>()
                for (i in 1..count) {
                    var row: Any? = this.resultSet!!.getObject(i)

                    if (row is oracle.sql.TIMESTAMP) {
                        // Jackson cannot (de)serialize oracle.sql.TIMESTAMP, converting it to java.sql.Timestamp
                        row = row.timestampValue()
                    }
                    // TODO process Clob and skip Blob
                    column[rsmeta.getColumnLabel(i)] = row
                }

                result.add(column)
            }

            c++
            if (c >= sizeForUpdateHandler) {
                retrieve += c
                c = 0
                this.module!!.progress = retrieve
                FloodgateHandlerManager.handle(Step.MODULE_PROGRESS, channelContext!!, this.module)
            }
        }
        if (c > 0) {
            retrieve += c
            this.module!!.progress = retrieve
            FloodgateHandlerManager.handle(Step.MODULE_PROGRESS, channelContext!!, this.module)
        }

        return result
    }

    override fun check() {
        val table = PropertyMap.getString(this.module!!.getSequences(), FlowTag.TARGET)

        val databaseMetaData = this.connection!!.metaData
        val resultSet = databaseMetaData.getTables(null, null, table, arrayOf("TABLE"))
        val exist = resultSet.next()

        if (!exist) {
            throw Exception("$table is not exist.")
        }
    }

    override fun count() {
        val table = PropertyMap.getString(this.module!!.getSequences(), FlowTag.TARGET)
        val sql = PropertyMap.getString(this.module!!.getSequences(), FlowTag.SQL)
        val condition = PropertyMap.getString(this.module!!.getSequences(), FlowTag.CONDITION)

        var query = ""

        if (sql != null) {
            query = sql
        } else {
            query = "SELECT COUNT(*) AS COUNT "
            query += " FROM $table"
            if (condition != null && condition.isNotEmpty()) {
                query += " WHERE $condition"
            }
            logger.debug(query)
        }

        this.connection!!.prepareStatement(query).use { ps ->
            val rs = ps.executeQuery()
            if (rs.next()) {
                val count = rs.getInt("COUNT")
                this.module!!.progress = count
                FloodgateHandlerManager.handle(Step.MODULE_PROGRESS, channelContext!!, this.module)
            }
        }
    }

    /*
    @Override
    public List<Map> read(Map rule) throws Exception {
        StringBuilder cols = new StringBuilder();

        if( rule !=null && rule.size() > 0 ) {
            int i = 0;
            for (Object sCol : rule.keySet()) {
                if (i > 0) {
                    cols.append(", ");
                }
                cols.append(sCol).append(" AS ").append(rule.get(sCol));

                i++;
            }
        } else {
            cols.append("*");
        }

        String query = "SELECT " + cols + " FROM " + context.evaluate(getOutput());
        System.out.println(query);

        PreparedStatement ps = this.connection.prepareStatement(query);

        ResultSet rs = ps.executeQuery();
        ResultSetMetaData rsmeta = rs.getMetaData();

        List<Map> result = new ArrayList<>();
        int count = rsmeta.getColumnCount();
        while(rs.next() ) {
            Map<String, Object> column = new LinkedHashMap<>();
            for( int i = 1; i <= count; i++ ) {
                Object row = rs.getObject(i);

                column.put(rsmeta.getColumnLabel(i), row);
            }

            result.add(column);
        }

        ps.close();

        return result;
    }
    */

    override fun update(mappingRule: MappingRule, data: Any?): Int {
        return 0
    }

    override fun delete(): Int {
        val table = PropertyMap.getString(this.module!!.getSequences(), FlowTag.TARGET)

        val truncateSQL = "DELETE FROM $table"
        this.connection!!.prepareStatement(truncateSQL).use { ps ->
            val timeout = PropertyMap.getIntegerDefault(this.module!!.getSequences(), FlowTag.TIMEOUT, 0)
            ps.queryTimeout = timeout
            ps.execute()
            this.connection!!.commit()
        }
        return 0
    }

    override fun close() {
        try { if (this.connection != null) this.connection!!.close() } finally { this.connection = null }
        try { if (this.ps != null) this.ps!!.close() } finally { this.ps = null }
        try { if (this.resultSet != null) this.resultSet!!.close() } finally { this.resultSet = null }
    }

    override fun getSent(): Int {
        return sent
    }

    override fun getErrorPosition(): Int {
        return errorPosition
    }
}
