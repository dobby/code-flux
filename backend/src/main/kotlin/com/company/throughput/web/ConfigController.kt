package com.company.throughput.web

import com.company.throughput.config.AppearanceProperties
import com.company.throughput.config.ConfigFileService
import com.company.throughput.config.EditableDashboardConfig
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

data class ConfigBuilderResponse(
    val path: String,
    val config: EditableDashboardConfig,
    val yaml: String,
    val restartRequired: Boolean,
    val savedAt: Instant? = null,
)

data class AppearanceConfigResponse(
    val appearance: AppearanceProperties,
    val savedAt: Instant? = null,
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

    @GetMapping("/builder")
    fun readConfigBuilder(): ConfigBuilderResponse {
        val snapshot = configFileService.readConfigFile()
        return ConfigBuilderResponse(
            path = snapshot.path.toString(),
            config = configFileService.readEditableConfig(),
            yaml = snapshot.yaml,
            restartRequired = false,
        )
    }

    @GetMapping("/appearance")
    fun readAppearance(): AppearanceConfigResponse =
        AppearanceConfigResponse(
            appearance = configFileService.readEditableConfig().appearance,
        )

    @PutMapping
    fun updateConfig(@RequestBody request: UpdateConfigFileRequest): ConfigFileResponse {
        val result = configFileService.saveConfigFile(request.yaml)
        val snapshot = configFileService.readConfigFile()
        return ConfigFileResponse(
            path = result.path.toString(),
            yaml = snapshot.yaml,
            restartRequired = result.restartRequired,
            savedAt = result.savedAt,
        )
    }

    @PutMapping("/builder")
    fun updateConfigBuilder(@RequestBody request: EditableDashboardConfig): ConfigBuilderResponse {
        val result = configFileService.saveEditableConfig(request)
        val snapshot = configFileService.readConfigFile()
        return ConfigBuilderResponse(
            path = result.path.toString(),
            config = configFileService.readEditableConfig(),
            yaml = snapshot.yaml,
            restartRequired = result.restartRequired,
            savedAt = result.savedAt,
        )
    }

    @PutMapping("/appearance")
    fun updateAppearance(@RequestBody request: AppearanceProperties): AppearanceConfigResponse {
        val currentConfig = configFileService.readEditableConfig()
        val result = configFileService.saveEditableConfig(
            currentConfig.copy(appearance = request),
        )
        return AppearanceConfigResponse(
            appearance = configFileService.readEditableConfig().appearance,
            savedAt = result.savedAt,
        )
    }
}
