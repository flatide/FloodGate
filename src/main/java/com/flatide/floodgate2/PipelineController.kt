package com.flatide.floodgate2

import com.flatide.floodgate.agent.ChannelAgent
import com.flatide.floodgate.agent.Context.CONTEXT_KEY
import com.flatide.floodgate.agent.flow.stream.FGSharableInputStream
import com.flatide.floodgate.agent.flow.stream.carrier.container.JSONContainer
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api")
class PipelineController {

    @PostMapping("/{apiName}")
    fun execute(
        @PathVariable apiName: String,
        @RequestParam params: Map<String, String>,
        @RequestBody(required = false) body: Map<String, Any>?
    ): ResponseEntity<Map<String, Any>> {
        val data: Map<String, Any?> = body ?: mapOf("HEADER" to emptyMap<String, Any>(), "ITEMS" to emptyList<Any>())

        val container = JSONContainer(data, "HEADER", "ITEMS")
        val stream = FGSharableInputStream(container)

        val agent = ChannelAgent()
        agent.addContext(CONTEXT_KEY.HTTP_REQUEST_METHOD, "POST")
        agent.addContext(CONTEXT_KEY.REQUEST_PARAMS, params)
        agent.addContext(CONTEXT_KEY.REQUEST_BODY, data)

        val result = agent.process(stream, apiName)

        return ResponseEntity.ok(result)
    }
}
