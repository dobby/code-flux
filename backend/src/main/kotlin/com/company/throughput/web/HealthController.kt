package com.company.throughput.web

import org.springframework.beans.factory.annotation.Value
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Instant

data class HealthResponse(
    val status: String,
    val appVersion: String,
    val time: Instant,
)

@RestController
@RequestMapping("/api")
class HealthController(
    @param:Value("\${app.version:0.1.0}") private val appVersion: String,
) {
    @GetMapping("/health")
    fun health(): HealthResponse = HealthResponse(
        status = "UP",
        appVersion = appVersion,
        time = Instant.now(),
    )
}
