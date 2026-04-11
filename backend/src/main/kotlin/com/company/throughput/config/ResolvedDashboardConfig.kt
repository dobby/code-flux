package com.company.throughput.config

import java.nio.file.Path
import java.time.LocalDate

data class ResolvedDashboardConfig(
    val app: AppProperties,
    val git: ResolvedGitProperties,
    val repos: List<RepoConfig>,
    val authors: List<ResolvedAuthorConfig>,
    val classification: ResolvedClassificationProperties,
    val uiDefaults: UiDefaultsProperties,
    val syncWindow: ResolvedSyncWindowProperties,
    val appearance: AppearanceProperties,
)

data class ResolvedGitProperties(
    val executable: String,
    val mirrorDir: Path,
    val timeoutSeconds: Int,
    val includeMergeCommits: Boolean,
    val deduplicateByPatchId: Boolean,
    val useAuthoredDate: Boolean,
    val auth: ResolvedGitAuthProperties?,
)

data class ResolvedGitAuthProperties(
    val httpUsername: String,
    val httpToken: String,
)

data class ResolvedAuthorConfig(
    val id: String,
    val displayName: String,
    val emails: List<String>,
    val names: List<String>,
    val cohort: String?,
)

data class ResolvedClassificationProperties(
    val languageByExtension: Map<String, String>,
    val rules: List<ClassificationRuleConfig>,
)

data class ResolvedSyncWindowProperties(
    val from: LocalDate?,
    val to: LocalDate?,
) {
    fun contains(day: LocalDate): Boolean {
        if (from != null && day.isBefore(from)) {
            return false
        }
        if (to != null && day.isAfter(to)) {
            return false
        }
        return true
    }
}
