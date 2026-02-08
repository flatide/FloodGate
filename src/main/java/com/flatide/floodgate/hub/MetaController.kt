package com.flatide.floodgate.hub

import com.flatide.floodgate.core.agent.meta.MetaManager
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
class MetaController {

    @GetMapping("/meta/{table}")
    fun list(
        @PathVariable table: String,
        @RequestParam(defaultValue = "") key: String
    ): ResponseEntity<Any> {
        val result = MetaManager.readList(table, key)
        return ResponseEntity.ok(result ?: emptyList<Any>())
    }

    @GetMapping("/meta/{table}/{id}")
    fun read(
        @PathVariable table: String,
        @PathVariable id: String
    ): ResponseEntity<Any> {
        val result = MetaManager.read(table, id)
        return if (result != null && result.isNotEmpty()) {
            ResponseEntity.ok(result)
        } else {
            ResponseEntity.notFound().build()
        }
    }

    @PostMapping("/meta/{table}")
    fun insert(
        @PathVariable table: String,
        @RequestBody data: Map<String, Any>
    ): ResponseEntity<Any> {
        val success = MetaManager.insert(table, "ID", data, true)
        return if (success) {
            ResponseEntity.ok(mapOf("result" to "created"))
        } else {
            ResponseEntity.badRequest().body(mapOf("result" to "failed"))
        }
    }

    @PutMapping("/meta/{table}")
    fun update(
        @PathVariable table: String,
        @RequestBody data: Map<String, Any>
    ): ResponseEntity<Any> {
        val success = MetaManager.update(table, "ID", data, true)
        return if (success) {
            ResponseEntity.ok(mapOf("result" to "updated"))
        } else {
            ResponseEntity.badRequest().body(mapOf("result" to "failed"))
        }
    }

    @DeleteMapping("/meta/{table}/{id}")
    fun delete(
        @PathVariable table: String,
        @PathVariable id: String
    ): ResponseEntity<Any> {
        val success = MetaManager.delete(table, id, true)
        return if (success) {
            ResponseEntity.ok(mapOf("result" to "deleted"))
        } else {
            ResponseEntity.notFound().build()
        }
    }

    @GetMapping("/health")
    fun health(): ResponseEntity<Any> {
        val info = mutableMapOf<String, Any>()
        info["status"] = "UP"
        try {
            info["meta"] = MetaManager.info
        } catch (e: Exception) {
            info["meta"] = "unavailable: ${e.message}"
        }
        return ResponseEntity.ok(info)
    }
}
