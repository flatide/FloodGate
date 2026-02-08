package com.flatide.floodgate2

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication

@SpringBootApplication
@EnableConfigurationProperties(FloodgateProperties::class)
open class Floodgate2Application

fun main(args: Array<String>) {
    runApplication<Floodgate2Application>(*args)
}
