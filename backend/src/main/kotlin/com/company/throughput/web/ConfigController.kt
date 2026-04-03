package com.company.throughput.web

import com.company.throughput.config.ConfigFileService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Instant

data class ConfigFileResponse(
    val path: String,
    val yaml: String,
    val restartRequired: Boolean,
    val savedAt: Instant? = null,
)

data class UpdateConfigFileRequest(
    val yaml: String,
)

@RestController
@RequestMapping("/api/config")
class ConfigController(
    private val configFileService: ConfigFileService,
) {
    @GetMapping
    fun readConfig(): ConfigFileResponse {
        val snapshot = configFileService.readConfigFile()
        return ConfigFileResponse(
            path = snapshot.path.toString(),
            yaml = snapshot.yaml,
            restartRequired = false,
        )
    }

    @PutMapping
    fun updateConfig(@RequestBody request: UpdateConfigFileRequest): ConfigFileResponse {
        val result = configFileService.saveConfigFile(request.yaml)
        return ConfigFileResponse(
            path = result.path.toString(),
            yaml = request.yaml,
            restartRequired = result.restartRequired,
            savedAt = result.savedAt,
        )
    }
}
