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

import java.io.BufferedOutputStream
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.sql.Date

import org.apache.commons.net.ftp.FTP
import org.apache.commons.net.ftp.FTPClient
import org.apache.commons.net.ftp.FTPReply
import org.apache.logging.log4j.LogManager

class ConnectorFTP : ConnectorBase() {
    var logger = LogManager.getLogger(ConnectorFTP::class.java)
    var module: Module? = null

    var channelContext: Context? = null
    var moduleContext: ModuleContext? = null

    private var outputStream: BufferedOutputStream? = null

    private var ftp: FTPClient? = null
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

        this.ftp = FTPClient()

        val reply: Int
        val connect = url!!.split(":")

        val timeout = PropertyMap.getIntegerDefault(connectInfo, ConnectorTag.TIMEOUT, 0)

        this.ftp!!.connectTimeout = timeout

        try {
            if (connect.size > 1) {
                this.ftp!!.connect(connect[0], Integer.parseInt(connect[1]))
            } else {
                this.ftp!!.connect(connect[0])
            }
        } catch (e: Exception) {
            e.printStackTrace()
            throw e
        }

        reply = this.ftp!!.replyCode
        if (!FTPReply.isPositiveCompletion(reply)) {
            this.ftp!!.disconnect()
            throw Exception("Error while connecting FTP server" + reply)
        }

        var rt: Boolean
        rt = this.ftp!!.login(user, password)
        if (rt == false) {
            throw Exception("Error while login FTP server")
        }
        this.isLogin = true
        rt = this.ftp!!.setFileType(FTP.BINARY_FILE_TYPE)
        if (rt == false) {
            throw Exception("Error while setting File Type on FTP server")
        }
        rt = this.ftp!!.changeWorkingDirectory(target)
        if (rt == false) {
            throw Exception("Error while changing working Directory on FTP server")
        }

        if (PropertyMap.getStringDefault(connectInfo, ConnectorTag.PASSIVE, "FALSE") == "TRUE") {
            this.ftp!!.enterLocalPassiveMode()
        }

        this.ftp!!.isRemoteVerificationEnabled = false
    }

    override fun beforeCreate(rule: MappingRule) {
        val context = this.module!!.getContext()
        val header = documentTemplate!!.makeHeader<Any>(context, rule, null)

        if (header.isNotEmpty()) {
            appendFile(header)
        }
    }

    override fun create(items: List<Map<Any?, Any?>>, mappingRule: MappingRule): Int {
        val context = this.module!!.getContext()
        val body = documentTemplate!!.makeBody(context, mappingRule, items, sent.toLong())

        appendFile(body)

        this.sent += items.size
        return items.size
    }

    override fun createPartially(items: List<Map<Any?, Any?>>, mappingRule: MappingRule): Int {
        val context = this.module!!.getContext()
        val body = documentTemplate!!.makeBody(context, mappingRule, items, sent.toLong())

        appendFile(body)

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
            appendFile(footer)
        }
    }

    fun appendFile(buffer: String): Int {
        @Suppress("UNCHECKED_CAST")
        val connectInfo = this.module!!.getContext().get(MODULE_CONTEXT.CONNECT_INFO) as Map<*, *>
        val code = PropertyMap.getStringDefault(connectInfo, ConnectorTag.CODE, "UTF-8")

        val input: InputStream = ByteArrayInputStream(buffer.toByteArray(charset(code)))

        try {
            val success = this.ftp!!.appendFile(remoteFile, input)
            if (!success) {
                logger.info("Append to the $remoteFile is failed.")
            }
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
