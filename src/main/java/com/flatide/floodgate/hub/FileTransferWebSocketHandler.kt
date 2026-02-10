package com.flatide.floodgate.hub

import com.fasterxml.jackson.databind.ObjectMapper
import com.flatide.floodgate.core.ConfigurationManager
import com.flatide.floodgate.core.FloodgateConstants
import com.flatide.floodgate.core.agent.connector.StreamingConnectorAdapter
import com.flatide.floodgate.core.agent.transfer.TransferProgressManager
import com.flatide.floodgate.core.agent.transfer.TransferSession
import com.flatide.floodgate.core.agent.transfer.TransferStatus

import org.apache.logging.log4j.LogManager
import org.springframework.stereotype.Component
import org.springframework.web.socket.BinaryMessage
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import org.springframework.web.socket.handler.AbstractWebSocketHandler

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

@Component
open class FileTransferWebSocketHandler : AbstractWebSocketHandler() {
    companion object {
        private val logger = LogManager.getLogger(FileTransferWebSocketHandler::class.java)
        private val mapper = ObjectMapper()
        private val writerPool = Executors.newCachedThreadPool()
    }

    // WebSocket session ID → transfer ID 매핑
    private val sessionTransferMap = ConcurrentHashMap<String, String>()

    override fun handleTextMessage(session: WebSocketSession, message: TextMessage) {
        val payload = message.payload
        val json = mapper.readValue(payload, Map::class.java)
        val action = json["action"] as? String ?: return

        when (action) {
            "start" -> handleStart(session, json)
            "complete" -> handleComplete(session)
            else -> sendError(session, "Unknown action: $action")
        }
    }

    override fun handleBinaryMessage(session: WebSocketSession, message: BinaryMessage) {
        val transferId = sessionTransferMap[session.id] ?: run {
            sendError(session, "No active transfer")
            return
        }

        val transferSession = TransferProgressManager.get(transferId) ?: run {
            sendError(session, "Transfer session not found")
            return
        }

        if (transferSession.status.get() != TransferStatus.TRANSFERRING) {
            sendError(session, "Transfer not in TRANSFERRING state")
            return
        }

        val bytes = ByteArray(message.payloadLength)
        message.payload.get(bytes)

        // queue.put()은 큐가 가득 차면 블로킹 → TCP 흐름 제어 → 클라이언트 backpressure
        transferSession.queue.put(bytes)
    }

    override fun afterConnectionClosed(session: WebSocketSession, status: CloseStatus) {
        val transferId = sessionTransferMap.remove(session.id) ?: return
        val transferSession = TransferProgressManager.get(transferId) ?: return

        // 아직 진행 중이면 취소 처리
        if (transferSession.status.get() == TransferStatus.TRANSFERRING
            || transferSession.status.get() == TransferStatus.READY) {
            transferSession.status.set(TransferStatus.CANCELLED)
            transferSession.queue.offer(TransferSession.END_SENTINEL)
        }
    }

    private fun handleStart(session: WebSocketSession, json: Map<*, *>) {
        val datasource = json["datasource"] as? String
        val filename = json["filename"] as? String
        val fileSize = (json["fileSize"] as? Number)?.toLong() ?: 0L

        if (datasource.isNullOrBlank() || filename.isNullOrBlank()) {
            sendError(session, "datasource and filename are required")
            return
        }

        val maxConcurrent = ConfigurationManager.getInteger(FloodgateConstants.TRANSFER_MAX_CONCURRENT) ?: 10
        if (TransferProgressManager.getActiveCount() >= maxConcurrent) {
            sendError(session, "Max concurrent transfers reached ($maxConcurrent)")
            return
        }

        val capacity = ConfigurationManager.getInteger(FloodgateConstants.TRANSFER_BUFFER_QUEUE_CAPACITY) ?: 32
        val transferId = UUID.randomUUID().toString()

        val transferSession = TransferSession(
            transferId = transferId,
            datasourceId = datasource,
            filename = filename,
            totalBytes = fileSize,
            capacity = capacity
        )

        TransferProgressManager.register(transferSession)
        sessionTransferMap[session.id] = transferId

        // Writer 스레드 시작
        writerPool.submit {
            runWriter(transferSession)
        }

        transferSession.status.set(TransferStatus.TRANSFERRING)

        val response = mapOf(
            "action" to "ready",
            "transferId" to transferId,
            "queueCapacity" to capacity
        )
        session.sendMessage(TextMessage(mapper.writeValueAsString(response)))
    }

    private fun handleComplete(session: WebSocketSession) {
        val transferId = sessionTransferMap[session.id] ?: run {
            sendError(session, "No active transfer")
            return
        }

        val transferSession = TransferProgressManager.get(transferId) ?: return

        // END_SENTINEL로 writer 스레드에 완료 신호
        transferSession.queue.put(TransferSession.END_SENTINEL)
    }

    private fun runWriter(transferSession: TransferSession) {
        var adapter: StreamingConnectorAdapter? = null
        try {
            adapter = StreamingConnectorAdapter(
                transferSession.datasourceId,
                transferSession.filename
            )
            val outputStream = adapter.open()

            while (true) {
                val chunk = transferSession.queue.take()

                // END_SENTINEL 확인 (참조 비교)
                if (chunk === TransferSession.END_SENTINEL) {
                    break
                }

                outputStream.write(chunk)
                transferSession.addBytes(chunk.size.toLong())
            }

            outputStream.flush()

            if (transferSession.status.get() == TransferStatus.TRANSFERRING) {
                transferSession.status.set(TransferStatus.COMPLETED)
            }

            logger.info("Transfer completed: ${transferSession.transferId}, " +
                "${transferSession.bytesTransferred.get()} bytes")
        } catch (e: Exception) {
            logger.error("Transfer failed: ${transferSession.transferId}", e)
            transferSession.status.set(TransferStatus.FAILED)
            transferSession.errorMessage = e.message
        } finally {
            adapter?.close()
        }
    }

    private fun sendError(session: WebSocketSession, message: String) {
        try {
            val response = mapOf("action" to "error", "message" to message)
            session.sendMessage(TextMessage(mapper.writeValueAsString(response)))
        } catch (e: Exception) {
            logger.error("Failed to send error message", e)
        }
    }
}
