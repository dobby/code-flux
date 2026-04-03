package com.company.throughput.config

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import jakarta.validation.Valid
import jakarta.validation.Validator
import org.springframework.core.env.Environment
import org.springframework.stereotype.Service
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant

data class ConfigFileSnapshot(
    val path: Path,
    val yaml: String,
)

data class ConfigSaveResult(
    val path: Path,
    val savedAt: Instant,
    val restartRequired: Boolean,
)

data class EditableDashboardConfig(
    @field:Valid
    val app: AppProperties = AppProperties(),
    @field:Valid
    val git: GitProperties = GitProperties(),
    @field:Valid
    val repos: List<RepoConfig> = emptyList(),
    @field:Valid
    val authors: AuthorsProperties = AuthorsProperties(),
    @field:Valid
    val classification: ClassificationProperties = ClassificationProperties(),
    @field:Valid
    val uiDefaults: UiDefaultsProperties = UiDefaultsProperties(),
    @field:Valid
    val syncWindow: SyncWindowProperties = SyncWindowProperties(),
)

@Service
class ConfigFileService(
    private val environment: Environment,
    private val validator: Validator,
) {
    private val yamlMapper: ObjectMapper = ObjectMapper(YAMLFactory())
        .registerKotlinModule()
        .findAndRegisterModules()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

    fun readConfigFile(): ConfigFileSnapshot {
        val path = resolveConfigPath()
        require(Files.exists(path)) { "Config file not found at $path" }
        return ConfigFileSnapshot(
            path = path,
            yaml = Files.readString(path, StandardCharsets.UTF_8),
        )
    }

    fun saveConfigFile(rawYaml: String): ConfigSaveResult {
        val normalizedYaml = normalizeYaml(rawYaml)
        validateYaml(normalizedYaml)

        val path = resolveConfigPath()
        Files.createDirectories(path.parent)
        Files.writeString(path, normalizedYaml, StandardCharsets.UTF_8)

        return ConfigSaveResult(
            path = path,
            savedAt = Instant.now(),
            restartRequired = true,
        )
    }

    private fun normalizeYaml(rawYaml: String): String {
        val trimmed = rawYaml.trim()
        require(trimmed.isNotBlank()) { "Config YAML must not be empty" }
        return if (trimmed.endsWith('\n')) trimmed else "$trimmed\n"
    }

    private fun validateYaml(rawYaml: String) {
        val rootNode = yamlMapper.readTree(rawYaml)
        require(rootNode?.isObject == true) { "Config YAML must contain a top-level object" }

        val document = yamlMapper.treeToValue(rootNode, EditableDashboardConfig::class.java)
        val validationErrors = validator.validate(document)
        require(validationErrors.isEmpty()) {
            validationErrors.joinToString("; ") { it.message }
        }

        require(document.repos.isNotEmpty()) { "At least one repository must be configured" }
        require(document.authors.include.isNotEmpty()) { "At least one author must be configured" }
        require(document.repos.map { it.id }.distinct().size == document.repos.size) { "Repository ids must be unique" }
        require(document.authors.include.map { it.id }.distinct().size == document.authors.include.size) { "Author ids must be unique" }
        val syncFrom = document.syncWindow.from
        val syncTo = document.syncWindow.to
        require(syncFrom == null || syncTo == null || !syncTo.isBefore(syncFrom)) {
            "syncWindow.to must be on or after syncWindow.from"
        }
        val defaultDateFrom = document.uiDefaults.defaultDateFrom
        val defaultDateTo = document.uiDefaults.defaultDateTo
        require(
            defaultDateFrom == null ||
                defaultDateTo == null ||
                !defaultDateTo.isBefore(defaultDateFrom),
        ) {
            "uiDefaults.defaultDateTo must be on or after uiDefaults.defaultDateFrom"
        }
        document.authors.include.forEach { author ->
            require(author.emails.isNotEmpty() || author.names.isNotEmpty()) {
                "Author ${author.id} must define at least one email or name alias"
            }
        }
        document.repos.forEach { repo ->
            require(repo.branchPatterns.isNotEmpty()) { "Repository ${repo.id} branchPatterns must not be empty" }
        }
    }

    private fun resolveConfigPath(): Path {
        val configuredPath = environment.getProperty("APP_CONFIG_FILE")
            ?.takeIf { it.isNotBlank() }
            ?.let(Path::of)
        if (configuredPath != null) {
            return configuredPath.toAbsolutePath().normalize()
        }

        val workingDirectory = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize()
        val candidates = listOf(
            workingDirectory.resolve("config/config.yaml"),
            workingDirectory.resolve("../config/config.yaml").normalize(),
        )
        return candidates.firstOrNull(Files::exists) ?: candidates.first()
    }
}
