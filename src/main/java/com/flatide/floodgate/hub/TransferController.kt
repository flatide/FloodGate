package com.flatide.floodgate.hub

import com.flatide.floodgate.core.agent.transfer.TransferProgressManager
import com.flatide.floodgate.core.agent.transfer.TransferStatus

import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter

import java.util.concurrent.Executors

@RestController
@RequestMapping("/transfer")
class TransferController {
    companion object {
        private val ssePool = Executors.newCachedThreadPool()
    }

    @GetMapping("/progress/{transferId}", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun streamProgress(@PathVariable transferId: String): SseEmitter {
        val emitter = SseEmitter(3600000L) // 1 hour timeout

        ssePool.submit {
            try {
                while (true) {
                    val session = TransferProgressManager.get(transferId)
                    if (session == null) {
                        emitter.send(SseEmitter.event()
                            .name("error")
                            .data("{\"message\":\"Transfer not found\"}"))
                        emitter.complete()
                        break
                    }

                    val status = session.status.get()
                    val data = mapOf(
                        "transferId" to session.transferId,
                        "status" to status.name,
                        "bytesTransferred" to session.bytesTransferred.get(),
                        "totalBytes" to session.totalBytes,
                        "percentage" to String.format("%.1f", session.getPercentage()),
                        "bytesPerSecond" to session.getBytesPerSecond().toLong(),
                        "elapsedMs" to session.getElapsedMillis(),
                        "errorMessage" to session.errorMessage
                    )

                    emitter.send(SseEmitter.event()
                        .name("progress")
                        .data(data))

                    if (status == TransferStatus.COMPLETED
                        || status == TransferStatus.FAILED
                        || status == TransferStatus.CANCELLED) {
                        emitter.complete()
                        break
                    }

                    Thread.sleep(500)
                }
            } catch (e: Exception) {
                // 클라이언트 연결 끊김 등
                emitter.completeWithError(e)
            }
        }

        return emitter
    }

    @GetMapping("/active")
    fun getActiveTransfers(): List<Map<String, Any?>> {
        return TransferProgressManager.getActiveSessions().map { session ->
            mapOf(
                "transferId" to session.transferId,
                "datasourceId" to session.datasourceId,
                "filename" to session.filename,
                "status" to session.status.get().name,
                "bytesTransferred" to session.bytesTransferred.get(),
                "totalBytes" to session.totalBytes,
                "percentage" to String.format("%.1f", session.getPercentage()),
                "bytesPerSecond" to session.getBytesPerSecond().toLong()
            )
        }
    }

    @GetMapping("/status/{transferId}")
    fun getTransferStatus(@PathVariable transferId: String): Map<String, Any?> {
        val session = TransferProgressManager.get(transferId)
            ?: return mapOf("error" to "Transfer not found")

        return mapOf(
            "transferId" to session.transferId,
            "datasourceId" to session.datasourceId,
            "filename" to session.filename,
            "status" to session.status.get().name,
            "bytesTransferred" to session.bytesTransferred.get(),
            "totalBytes" to session.totalBytes,
            "percentage" to String.format("%.1f", session.getPercentage()),
            "bytesPerSecond" to session.getBytesPerSecond().toLong(),
            "elapsedMs" to session.getElapsedMillis(),
            "errorMessage" to session.errorMessage
        )
    }
}
