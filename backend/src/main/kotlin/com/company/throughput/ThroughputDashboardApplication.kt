package com.company.throughput

import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.ImportRuntimeHints

@SpringBootApplication
@ConfigurationPropertiesScan
@ImportRuntimeHints(com.company.throughput.config.ConfigRuntimeHints::class)
class ThroughputDashboardApplication

fun main(args: Array<String>) {
    runApplication<ThroughputDashboardApplication>(*args)
}
