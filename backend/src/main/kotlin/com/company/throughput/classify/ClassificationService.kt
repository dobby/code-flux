package com.company.throughput.classify

import com.company.throughput.config.ClassificationRuleConfig
import com.company.throughput.config.RepoConfig
import com.company.throughput.config.ResolvedDashboardConfig
import com.company.throughput.git.GitFileStat
import org.springframework.stereotype.Service
import java.nio.file.FileSystems
import java.nio.file.Path
import java.util.Locale

data class ClassifiedFileStat(
    val filePath: String,
    val oldPath: String?,
    val extension: String?,
    val language: String,
    val category: String,
    val subtype: String,
    val productCode: String?,
    val linesAdded: Int,
    val linesRemoved: Int,
    val netLines: Int,
    val isBinary: Boolean,
    val isGenerated: Boolean,
)

@Service
class ClassificationService(
    private val resolvedDashboardConfig: ResolvedDashboardConfig,
) {
    fun classify(repo: RepoConfig, stat: GitFileStat): ClassifiedFileStat {
        val normalizedPath = stat.filePath.trimStart('/')
        val extension = extractExtension(normalizedPath)
        val language = extension
            ?.let { resolvedDashboardConfig.classification.languageByExtension[it] }
            ?: "unknown"

        val matchedRule = firstMatchingRule(normalizedPath)
        val generatedByPath = isGeneratedPath(normalizedPath)
        val category = matchedRule?.category ?: if (generatedByPath) "generated" else defaultCategory(normalizedPath, extension)
        val subtype = matchedRule?.subtype ?: defaultSubtype(category)

        return ClassifiedFileStat(
            filePath = normalizedPath,
            oldPath = stat.oldPath,
            extension = extension,
            language = language,
            category = category,
            subtype = subtype,
            productCode = repo.productCode,
            linesAdded = stat.linesAdded,
            linesRemoved = stat.linesRemoved,
            netLines = stat.linesAdded - stat.linesRemoved,
            isBinary = stat.isBinary,
            isGenerated = generatedByPath || category == "generated",
        )
    }

    fun isPathExcluded(repo: RepoConfig, filePath: String): Boolean {
        val normalizedPath = filePath.trimStart('/')
        return isDefaultExcludedPath(normalizedPath) || repo.excludePathGlobs.any { pattern ->
            FileSystems.getDefault().getPathMatcher("glob:$pattern").matches(Path.of(normalizedPath))
        }
    }

    private fun firstMatchingRule(path: String): ClassificationRuleConfig? =
        resolvedDashboardConfig.classification.rules.firstOrNull { rule ->
            rule.whenPathMatches.any { pattern ->
                FileSystems.getDefault().getPathMatcher("glob:$pattern").matches(Path.of(path))
            }
        }

    private fun defaultCategory(path: String, extension: String?): String {
        val lowercasePath = path.lowercase(Locale.getDefault())
        if (lowercasePath.contains("/src/test/") || lowercasePath.contains("/test/") || lowercasePath.endsWith("test.kt")) {
            return "test"
        }
        if (extension == "md" || lowercasePath.contains("/docs/")) {
            return "docs"
        }
        if (extension in setOf("yml", "yaml", "toml", "json", "ini", "properties", "xml")) {
            return "config"
        }
        return "production"
    }

    private fun defaultSubtype(category: String): String = when (category) {
        "test" -> "test"
        "docs" -> "docs"
        "generated" -> "generated"
        "config" -> "config"
        else -> "source"
    }

    private fun extractExtension(path: String): String? {
        val filename = path.substringAfterLast('/', path)
        val index = filename.lastIndexOf('.')
        if (index <= 0 || index == filename.length - 1) {
            return null
        }
        return filename.substring(index + 1).lowercase(Locale.getDefault())
    }

    private fun isGeneratedPath(path: String): Boolean {
        val lowercasePath = path.lowercase(Locale.getDefault())
        return lowercasePath.contains("/generated/") ||
            lowercasePath.startsWith("generated/") ||
            lowercasePath.contains("/build/generated/") ||
            isDefaultExcludedPath(lowercasePath)
    }

    private fun isDefaultExcludedPath(path: String): Boolean {
        val lowercasePath = path.lowercase(Locale.getDefault())
        val pathSegments = lowercasePath.split('/').filter { it.isNotBlank() }
        return pathSegments.any { it in DEFAULT_EXCLUDED_DIRECTORY_NAMES } ||
            DEFAULT_EXCLUDED_FILE_SUFFIXES.any { lowercasePath.endsWith(it) }
    }

    companion object {
        private val DEFAULT_EXCLUDED_DIRECTORY_NAMES = setOf(
            "dist",
            "build",
            "target",
            "out",
            "coverage",
            "node_modules",
            ".next",
            ".nuxt",
        )

        private val DEFAULT_EXCLUDED_FILE_SUFFIXES = listOf(
            ".jar",
            ".war",
            ".class",
        )
    }
}
