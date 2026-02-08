package com.flatide.floodgate.hub

import com.flatide.floodgate.core.ConfigBase
import com.flatide.floodgate.core.ConfigurationManager
import com.flatide.floodgate.core.Floodgate
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component
import java.io.File

@ConfigurationProperties(prefix = "floodgate")
open class FloodgateProperties {
    var config: MutableMap<String, Any> = mutableMapOf()
}

@Component
open class FloodgateInitializer(
    private val properties: FloodgateProperties
) : ApplicationRunner {

    override fun run(args: ApplicationArguments) {
        // Create output directory for demo pipeline
        File("./data/output").mkdirs()

        // Bridge Spring config into floodgate_core's ConfigurationManager
        val configBase = object : ConfigBase() {
            init {
                this.config = properties.config
            }
        }
        ConfigurationManager.setConfig(configBase)

        // Initialize all floodgate managers
        Floodgate.init()

        println("FloodGate initialized successfully")
    }
}
