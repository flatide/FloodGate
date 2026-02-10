package com.flatide.floodgate.hub

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.socket.config.annotation.EnableWebSocket
import org.springframework.web.socket.config.annotation.WebSocketConfigurer
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean

@Configuration
@EnableWebSocket
open class WebSocketConfig(
    private val fileTransferWebSocketHandler: FileTransferWebSocketHandler
) : WebSocketConfigurer {

    override fun registerWebSocketHandlers(registry: WebSocketHandlerRegistry) {
        registry.addHandler(fileTransferWebSocketHandler, "/ws/transfer")
            .setAllowedOrigins("*")
    }

    @Bean
    open fun createWebSocketContainer(): ServletServerContainerFactoryBean {
        val container = ServletServerContainerFactoryBean()
        container.setMaxBinaryMessageBufferSize(128 * 1024)   // 128KB
        container.setMaxTextMessageBufferSize(8 * 1024)        // 8KB
        container.setMaxSessionIdleTimeout(3600000)            // 1 hour
        return container
    }
}
