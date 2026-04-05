package com.company.throughput.config

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
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
    companion object {
        const val redactedSecretPlaceholder = "__CODE_FLUX_REDACTED__"

        private val secretFieldNames = setOf(
            "httpToken",
            "token",
            "secret",
            "password",
            "apiKey",
        )
    }

    private val yamlMapper: ObjectMapper = ObjectMapper(YAMLFactory())
        .registerKotlinModule()
        .findAndRegisterModules()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

    fun readConfigFile(): ConfigFileSnapshot {
        val path = resolveConfigPath()
        require(Files.exists(path)) { "Config file not found at $path" }
        val rawYaml = Files.readString(path, StandardCharsets.UTF_8)
        return ConfigFileSnapshot(
            path = path,
            yaml = redactSecrets(rawYaml),
        )
    }

    fun readEditableConfig(): EditableDashboardConfig =
        yamlMapper.readValue(readConfigFile().yaml, EditableDashboardConfig::class.java)

    fun saveConfigFile(rawYaml: String): ConfigSaveResult {
        val normalizedYaml = normalizeYaml(
            restoreMaskedSecrets(
                candidateYaml = rawYaml,
                existingYaml = readRawConfigYaml(),
            ),
        )
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

    fun saveEditableConfig(config: EditableDashboardConfig): ConfigSaveResult =
        saveConfigFile(yamlMapper.writeValueAsString(config))

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
        requireSupportedChartLibrary(
            value = document.uiDefaults.defaultChartLibrary,
            label = "uiDefaults.defaultChartLibrary",
        )
        document.authors.include.forEach { author ->
            require(author.emails.isNotEmpty() || author.names.isNotEmpty()) {
                "Author ${author.id} must define at least one email or name alias"
            }
        }
        document.repos.forEach { repo ->
            require(repo.branchPatterns.isNotEmpty()) { "Repository ${repo.id} branchPatterns must not be empty" }
            if (repo.localPath != null) {
                require(repo.localPath.isNotBlank()) { "Repository ${repo.id} localPath must not be blank" }
            }
        }
    }

    private fun readRawConfigYaml(): String {
        val path = resolveConfigPath()
        return if (Files.exists(path)) {
            Files.readString(path, StandardCharsets.UTF_8)
        } else {
            ""
        }
    }

    private fun redactSecrets(rawYaml: String): String {
        if (rawYaml.isBlank()) {
            return rawYaml
        }
        val rootNode = yamlMapper.readTree(rawYaml) ?: return rawYaml
        redactSecretsInPlace(rootNode)
        return yamlMapper.writeValueAsString(rootNode)
    }

    private fun restoreMaskedSecrets(candidateYaml: String, existingYaml: String): String {
        if (candidateYaml.isBlank() || existingYaml.isBlank()) {
            return candidateYaml
        }
        val candidateRoot = yamlMapper.readTree(candidateYaml) ?: return candidateYaml
        val existingRoot = yamlMapper.readTree(existingYaml) ?: return candidateYaml
        restoreMaskedSecretsInPlace(candidateRoot, existingRoot)
        return yamlMapper.writeValueAsString(candidateRoot)
    }

    private fun redactSecretsInPlace(node: JsonNode) {
        when (node) {
            is ObjectNode -> {
                val fieldNames = node.fieldNames().asSequence().toList()
                fieldNames.forEach { fieldName ->
                    val child = node.get(fieldName) ?: return@forEach
                    if (fieldName in secretFieldNames && child.isValueNode && !child.isNull) {
                        node.put(fieldName, redactedSecretPlaceholder)
                    } else {
                        redactSecretsInPlace(child)
                    }
                }
            }
            is ArrayNode -> node.forEach(::redactSecretsInPlace)
        }
    }

    private fun restoreMaskedSecretsInPlace(candidateNode: JsonNode, existingNode: JsonNode?) {
        when (candidateNode) {
            is ObjectNode -> {
                val fieldNames = candidateNode.fieldNames().asSequence().toList()
                fieldNames.forEach { fieldName ->
                    val candidateChild = candidateNode.get(fieldName) ?: return@forEach
                    val existingChild = existingNode?.get(fieldName)
                    if (
                        fieldName in secretFieldNames &&
                        candidateChild.isTextual &&
                        candidateChild.asText() == redactedSecretPlaceholder &&
                        existingChild != null &&
                        existingChild.isValueNode
                    ) {
                        candidateNode.set<JsonNode>(fieldName, existingChild.deepCopy<JsonNode>())
                    } else {
                        restoreMaskedSecretsInPlace(candidateChild, existingChild)
                    }
                }
            }
            is ArrayNode -> candidateNode.forEachIndexed { index, child ->
                restoreMaskedSecretsInPlace(child, existingNode?.get(index))
            }
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
