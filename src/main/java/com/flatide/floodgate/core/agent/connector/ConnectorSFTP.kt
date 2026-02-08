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
import com.flatide.floodgate.core.agent.flow.rule.MappingRule
import com.flatide.floodgate.core.agent.flow.rule.MappingRuleItem
import com.flatide.floodgate.core.agent.flow.rule.FunctionProcessor
import com.flatide.floodgate.core.agent.flow.FlowTag
import com.flatide.floodgate.core.agent.flow.module.Module
import com.flatide.floodgate.core.agent.flow.module.ModuleContext
import com.flatide.floodgate.core.agent.flow.module.ModuleContext.MODULE_CONTEXT
import com.flatide.floodgate.core.system.FlowEnv
import com.flatide.floodgate.core.system.security.FloodgateSecurity
import com.flatide.floodgate.core.system.utils.PropertyMap
import com.jcraft.jsch.Channel
import com.jcraft.jsch.ChannelSftp
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session

import java.io.BufferedOutputStream
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.sql.Date

import org.apache.logging.log4j.LogManager

class ConnectorSFTP : ConnectorBase() {
    var logger = LogManager.getLogger(ConnectorSFTP::class.java)
    var module: Module? = null

    var channelContext: Context? = null
    var moduleContext: ModuleContext? = null

    private var outputStream: BufferedOutputStream? = null

    private var session: Session? = null
    private var channel: Channel? = null
    private var sftp: ChannelSftp? = null

    private var isLogin: Boolean = false

    private var sent: Int = 0
    private var errorPosition: Int = -1

    private var remoteFile: String = ""

    private class FTPFunctionProcessor : FunctionProcessor {
        override fun process(item: MappingRuleItem): Any? {
            val func = item.sourceName
            val function = MappingRule.Function.valueOf(func)

            return when (function) {
                MappingRule.Function.TARGET_DATE -> {
                    val current = System.currentTimeMillis()
                    Date(current)
                }
                MappingRule.Function.DATE -> {
                    val current = System.currentTimeMillis()
                    Date(current)
                }
                MappingRule.Function.SEQ -> FlowEnv.getSequence()
            }
        }
    }

    companion object {
        val PRE_PROCESSOR: FunctionProcessor = FTPFunctionProcessor()
    }

    override fun getFunctionProcessor(type: String): FunctionProcessor {
        return PRE_PROCESSOR
    }

    override fun connect(context: Context, module: Module) {
        this.module = module

        channelContext = this.module!!.getFlowContext().get(CONTEXT_KEY.CHANNEL_CONTEXT) as Context
        moduleContext = module.getContext()

        @Suppress("UNCHECKED_CAST")
        val connectInfo = module.getContext().get(MODULE_CONTEXT.CONNECT_INFO) as Map<*, *>
        val url = PropertyMap.getString(connectInfo, ConnectorTag.URL)!!
        val user = PropertyMap.getString(connectInfo, ConnectorTag.USER)
        var password = PropertyMap.getString(connectInfo, ConnectorTag.PASSWORD)!!
        password = FloodgateSecurity.decrypt(password)

        val target = PropertyMap.getString(this.module!!.getSequences(), FlowTag.TARGET)
        remoteFile = context.evaluate(target!!)
        val baseDir = remoteFile.substring(0, remoteFile.lastIndexOf("/"))
        remoteFile = remoteFile.substring(remoteFile.lastIndexOf("/") + 1)

        val jsch = JSch()

        try {
            val connect = url!!.split(":")
            session = jsch.getSession(user, connect[0], Integer.parseInt(connect[1]))
            session!!.setPassword(password)

            val config = java.util.Properties()
            config["StrictHostKeyChecking"] = "no"
            session!!.setConfig(config)

            session!!.connect()

            channel = session!!.openChannel("sftp")
            channel!!.connect()

            sftp = channel as ChannelSftp
            //System.out.println("=> Connected to " + host);
            sftp!!.cd(baseDir)
        } catch (e: Exception) {
            e.printStackTrace()
            throw e
        }
    }

    override fun beforeCreate(rule: MappingRule) {
        val context = this.module!!.getContext()
        val header = documentTemplate!!.makeHeader<Any>(context, rule, null)

        writeFile(header, ChannelSftp.OVERWRITE)
    }

    override fun create(items: List<Map<Any?, Any?>>, mappingRule: MappingRule): Int {
        val context = this.module!!.getContext()
        val body = documentTemplate!!.makeBody(context, mappingRule, items, sent.toLong())

        writeFile(body, ChannelSftp.APPEND)

        this.sent += items.size
        return items.size
    }

    override fun createPartially(items: List<Map<Any?, Any?>>, mappingRule: MappingRule): Int {
        val context = this.module!!.getContext()
        val body = documentTemplate!!.makeBody(context, mappingRule, items, sent.toLong())

        writeFile(body, ChannelSftp.APPEND)

        this.sent += items.size
        return items.size
    }

    fun creatingBinary(item: ByteArray, size: Long, sent: Long): Int {
        this.outputStream!!.write(item, 0, size.toInt())
        this.sent += size.toInt()
        return size.toInt()
    }

    override fun afterCreate(rule: MappingRule) {
        val context = this.module!!.getContext()
        val footer = documentTemplate!!.makeFooter<Any>(context, rule, null)
        if (footer.isNotEmpty()) {
            writeFile(footer, ChannelSftp.APPEND)
        }
    }

    fun writeFile(buffer: String, mode: Int): Int {
        @Suppress("UNCHECKED_CAST")
        val connectInfo = this.module!!.getContext().get(MODULE_CONTEXT.CONNECT_INFO) as Map<*, *>
        val code = PropertyMap.getStringDefault(connectInfo, ConnectorTag.CODE, "UTF-8")

        val input: InputStream = ByteArrayInputStream(buffer.toByteArray(charset(code)))

        try {
            this.sftp!!.put(input, remoteFile, mode)
        } catch (e: Exception) {
            e.printStackTrace()
            throw e
        } finally {
            input.close()
        }

        return 0
    }

    override fun beforeRead(rule: MappingRule) {
    }

    override fun readBuffer(rule: MappingRule, buffer: MutableList<Any?>, limit: Int): Int {
        return 0
    }

    override fun readPartially(rule: MappingRule): List<Map<Any?, Any?>> {
        return emptyList()
    }

    override fun afterRead() {
    }

    override fun read(rule: MappingRule): List<Map<Any?, Any?>> {
        return emptyList()
    }

    override fun update(mappingRule: MappingRule, data: Any?): Int {
        return 0
    }

    override fun delete(): Int {
        return 0
    }

    override fun commit() {
    }

    override fun rollback() {
    }

    override fun close() {
        try {
            if (this.outputStream != null) {
                this.outputStream!!.flush()
                this.outputStream!!.close()
            }
        } finally {
            this.outputStream = null
        }
    }

    override fun getSent(): Int {
        return sent
    }

    override fun getErrorPosition(): Int {
        return errorPosition
    }
}
