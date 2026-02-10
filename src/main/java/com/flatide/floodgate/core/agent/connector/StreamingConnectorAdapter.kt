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

import com.flatide.floodgate.core.ConfigurationManager
import com.flatide.floodgate.core.FloodgateConstants
import com.flatide.floodgate.core.agent.meta.MetaManager
import com.flatide.floodgate.core.system.security.FloodgateSecurity

import com.jcraft.jsch.ChannelSftp
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session

import org.apache.commons.net.ftp.FTP
import org.apache.commons.net.ftp.FTPClient
import org.apache.commons.net.ftp.FTPReply
import org.apache.logging.log4j.LogManager

import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream

class StreamingConnectorAdapter(
    private val datasourceId: String,
    private val filename: String
) : AutoCloseable {
    companion object {
        private val logger = LogManager.getLogger(StreamingConnectorAdapter::class.java)
    }

    private var outputStream: OutputStream? = null
    private var ftpClient: FTPClient? = null
    private var sftpSession: Session? = null
    private var sftpChannel: ChannelSftp? = null
    private var connectorType: String = ""

    @Suppress("UNCHECKED_CAST")
    fun open(): OutputStream {
        val tableName = ConfigurationManager.getString(FloodgateConstants.META_SOURCE_TABLE_FOR_DATASOURCE)
            ?: "FG_DATASOURCE"
        val meta = MetaManager.read(tableName, datasourceId)
            ?: throw IllegalArgumentException("Datasource not found: $datasourceId")

        val data = meta["DATA"] as? Map<String, Any>
            ?: throw IllegalArgumentException("Datasource DATA is missing or invalid: $datasourceId")

        connectorType = (data[ConnectorTag.CONNECTOR.name] as? String ?: "").uppercase()
        val url = data[ConnectorTag.URL.name] as? String
            ?: throw IllegalArgumentException("Datasource URL is missing: $datasourceId")

        return when (connectorType) {
            "FILE" -> openFile(url)
            "FTP" -> openFtp(data, url)
            "SFTP" -> openSftp(data, url)
            else -> throw IllegalArgumentException("Unsupported connector type for streaming: $connectorType")
        }
    }

    private fun openFile(url: String): OutputStream {
        val dir = File(url)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        val file = File(dir, filename)
        logger.info("Streaming to file: ${file.absolutePath}")
        outputStream = BufferedOutputStream(FileOutputStream(file), 65536)
        return outputStream!!
    }

    private fun openFtp(data: Map<String, Any>, url: String): OutputStream {
        val user = data[ConnectorTag.USER.name] as? String ?: ""
        val rawPassword = data[ConnectorTag.PASSWORD.name] as? String ?: ""
        val password = decryptSafe(rawPassword)
        val passive = (data[ConnectorTag.PASSIVE.name] as? String ?: "FALSE").uppercase() == "TRUE"
        val timeout = (data[ConnectorTag.TIMEOUT.name] as? Number)?.toInt() ?: 0

        val ftp = FTPClient()
        ftpClient = ftp

        val parts = url.split(":")
        ftp.connectTimeout = timeout

        if (parts.size > 1) {
            ftp.connect(parts[0], parts[1].toInt())
        } else {
            ftp.connect(parts[0])
        }

        val reply = ftp.replyCode
        if (!FTPReply.isPositiveCompletion(reply)) {
            ftp.disconnect()
            throw Exception("FTP connection failed: $reply")
        }

        if (!ftp.login(user, password)) {
            throw Exception("FTP login failed")
        }

        if (!ftp.setFileType(FTP.BINARY_FILE_TYPE)) {
            throw Exception("FTP setFileType failed")
        }

        if (passive) {
            ftp.enterLocalPassiveMode()
        }

        ftp.isRemoteVerificationEnabled = false

        // 파일명에서 디렉토리와 파일명 분리
        val lastSlash = filename.lastIndexOf("/")
        val remoteDir: String?
        val remoteFile: String
        if (lastSlash >= 0) {
            remoteDir = filename.substring(0, lastSlash)
            remoteFile = filename.substring(lastSlash + 1)
        } else {
            remoteDir = null
            remoteFile = filename
        }

        if (remoteDir != null && remoteDir.isNotEmpty()) {
            ftp.changeWorkingDirectory(remoteDir)
        }

        logger.info("Streaming to FTP: $url/$filename")
        val ftpOutputStream = ftp.storeFileStream(remoteFile)
            ?: throw Exception("FTP storeFileStream failed: ${ftp.replyString}")
        outputStream = BufferedOutputStream(ftpOutputStream, 65536)
        return outputStream!!
    }

    private fun openSftp(data: Map<String, Any>, url: String): OutputStream {
        val user = data[ConnectorTag.USER.name] as? String ?: ""
        val rawPassword = data[ConnectorTag.PASSWORD.name] as? String ?: ""
        val password = decryptSafe(rawPassword)

        val parts = url.split(":")
        val host = parts[0]
        val port = if (parts.size > 1) parts[1].toInt() else 22

        val jsch = JSch()
        val session = jsch.getSession(user, host, port)
        sftpSession = session
        session.setPassword(password)

        val config = java.util.Properties()
        config["StrictHostKeyChecking"] = "no"
        session.setConfig(config)
        session.connect()

        val channel = session.openChannel("sftp") as ChannelSftp
        sftpChannel = channel
        channel.connect()

        // 파일명에서 디렉토리와 파일명 분리
        val lastSlash = filename.lastIndexOf("/")
        val remoteDir: String?
        val remoteFile: String
        if (lastSlash >= 0) {
            remoteDir = filename.substring(0, lastSlash)
            remoteFile = filename.substring(lastSlash + 1)
        } else {
            remoteDir = null
            remoteFile = filename
        }

        if (remoteDir != null && remoteDir.isNotEmpty()) {
            channel.cd(remoteDir)
        }

        logger.info("Streaming to SFTP: $url/$filename")
        val sftpOutputStream = channel.put(remoteFile, ChannelSftp.OVERWRITE)
        outputStream = BufferedOutputStream(sftpOutputStream, 65536)
        return outputStream!!
    }

    private fun decryptSafe(password: String): String {
        return try {
            FloodgateSecurity.decrypt(password)
        } catch (e: Exception) {
            // security provider가 설정되지 않은 경우 원본 비밀번호 사용
            password
        }
    }

    override fun close() {
        try {
            outputStream?.flush()
            outputStream?.close()
        } catch (e: Exception) {
            logger.error("Error closing output stream", e)
        } finally {
            outputStream = null
        }

        // FTP: completePendingCommand 호출 필요
        try {
            ftpClient?.let { ftp ->
                ftp.completePendingCommand()
                ftp.logout()
                ftp.disconnect()
            }
        } catch (e: Exception) {
            logger.error("Error closing FTP connection", e)
        } finally {
            ftpClient = null
        }

        // SFTP 정리
        try {
            sftpChannel?.disconnect()
            sftpSession?.disconnect()
        } catch (e: Exception) {
            logger.error("Error closing SFTP connection", e)
        } finally {
            sftpChannel = null
            sftpSession = null
        }
    }
}
