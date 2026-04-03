package com.company.throughput.config

import jakarta.validation.Valid
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated
import java.time.LocalDate

@Validated
@ConfigurationProperties(prefix = "app")
data class AppProperties(
    @field:NotBlank
    val baseUrl: String = "http://localhost:8080",
    @field:NotBlank
    val dataDir: String = ".code-flux",
    val openBrowserOnStart: Boolean = true,
    @field:NotBlank
    val logLevel: String = "INFO",
)

@Validated
@ConfigurationProperties(prefix = "git")
data class GitProperties(
    @field:NotBlank
    val executable: String = "git",
    val mirrorDir: String? = null,
    @field:Min(1)
    val timeoutSeconds: Int = 120,
    val includeMergeCommits: Boolean = false,
    val deduplicateByPatchId: Boolean = true,
    val useAuthoredDate: Boolean = true,
    @field:Valid
    val auth: GitAuthProperties = GitAuthProperties(),
)

data class GitAuthProperties(
    val httpUsername: String? = null,
    val httpToken: String? = null,
)

@Validated
@ConfigurationProperties(prefix = "authors")
data class AuthorsProperties(
    @field:Valid
    @field:NotEmpty
    val include: List<AuthorConfig> = emptyList(),
)

@Validated
@ConfigurationProperties(prefix = "classification")
data class ClassificationProperties(
    val languageByExtension: Map<String, String> = emptyMap(),
    @field:Valid
    val rules: List<ClassificationRuleConfig> = emptyList(),
)

@Validated
@ConfigurationProperties(prefix = "ui-defaults")
data class UiDefaultsProperties(
    @field:NotBlank
    val defaultMetric: String = "lines_added",
    @field:NotBlank
    val defaultGroupBy: String = "author",
    val defaultIncludeCategories: List<String> = listOf("production"),
    val defaultExcludeCategories: List<String> = listOf("test", "docs", "generated", "config"),
    val defaultDateFrom: LocalDate? = null,
    val defaultDateTo: LocalDate? = null,
)

@Validated
@ConfigurationProperties(prefix = "sync-window")
data class SyncWindowProperties(
    val from: LocalDate? = null,
    val to: LocalDate? = null,
)

data class RepoListProperties(
    @field:Valid
    @field:NotEmpty
    val entries: List<RepoConfig> = emptyList(),
)

data class RepoConfig(
    @field:NotBlank
    val id: String,
    @field:NotBlank
    val displayName: String,
    @field:NotBlank
    val cloneUrl: String,
    val enabled: Boolean = true,
    val productCode: String? = null,
    @field:NotEmpty
    val branchPatterns: List<String> = emptyList(),
    val excludeBranchPatterns: List<String> = emptyList(),
    val excludePathGlobs: List<String> = emptyList(),
)

data class AuthorConfig(
    @field:NotBlank
    val id: String,
    @field:NotBlank
    val displayName: String,
    val emails: List<String> = emptyList(),
    val names: List<String> = emptyList(),
    val cohort: String? = null,
)

data class ClassificationRuleConfig(
    @field:NotBlank
    val id: String,
    val whenPathMatches: List<String> = emptyList(),
    @field:NotBlank
    val category: String,
    @field:NotBlank
    val subtype: String,
)
