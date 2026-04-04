package com.company.throughput.config

import org.springframework.boot.context.properties.bind.Bindable
import org.springframework.boot.context.properties.bind.Binder
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.env.Environment
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.time.LocalDate

@Configuration
class DashboardConfigFactory {
    @Bean
    fun repoListProperties(environment: Environment): RepoListProperties {
        val repos = Binder.get(environment)
            .bind("repos", Bindable.listOf(RepoConfig::class.java))
            .orElse(emptyList())

        return RepoListProperties(entries = repos)
    }

    @Bean
    fun resolvedDashboardConfig(
        appProperties: AppProperties,
        gitProperties: GitProperties,
        repoListProperties: RepoListProperties,
        authorsProperties: AuthorsProperties,
        classificationProperties: ClassificationProperties,
        uiDefaultsProperties: UiDefaultsProperties,
        syncWindowProperties: SyncWindowProperties = SyncWindowProperties(),
    ): ResolvedDashboardConfig {
        val dataDir = Paths.get(appProperties.dataDir).toAbsolutePath().normalize()
        Files.createDirectories(dataDir)
        require(Files.isWritable(dataDir)) { "Configured app.dataDir is not writable: $dataDir" }

        val gitExecutable = gitProperties.executable.trim()
        require(gitExecutable.isNotBlank()) { "git.executable must not be blank" }
        val resolvedGitExecutable = GitExecutableLocator.resolve(gitExecutable)
            ?: throw IllegalStateException("Configured git executable is not available or not executable: $gitExecutable")

        val mirrorDir = gitProperties.mirrorDir?.let { Paths.get(it) } ?: dataDir.resolve("mirrors")
        Files.createDirectories(mirrorDir)
        require(Files.isWritable(mirrorDir)) { "Configured git.mirrorDir is not writable: $mirrorDir" }
        val gitAuth = gitProperties.auth.httpToken
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.let { token ->
                ResolvedGitAuthProperties(
                    httpUsername = gitProperties.auth.httpUsername?.trim()?.ifBlank { null } ?: "oauth2",
                    httpToken = token,
                )
            }

        val repos = repoListProperties.entries
        require(repos.isNotEmpty()) { "At least one repository must be configured" }
        require(repos.any { it.enabled }) { "At least one repository must be enabled" }
        require(repos.map { it.id }.distinct().size == repos.size) { "Repository ids must be unique" }
        repos.forEach { repo ->
            require(repo.cloneUrl.isNotBlank()) { "Repository ${repo.id} cloneUrl must not be blank" }
            require(repo.branchPatterns.isNotEmpty()) { "Repository ${repo.id} branchPatterns must not be empty" }
        }

        val authors = authorsProperties.include.map { author ->
            val normalizedEmails = author.emails.map { it.trim().lowercase() }.filter { it.isNotBlank() }
            val normalizedNames = author.names.map { it.trim() }.filter { it.isNotBlank() }
            require(normalizedEmails.isNotEmpty() || normalizedNames.isNotEmpty()) {
                "Author ${author.id} must define at least one email or name alias"
            }
            ResolvedAuthorConfig(
                id = author.id,
                displayName = author.displayName.trim(),
                emails = normalizedEmails,
                names = normalizedNames,
                cohort = author.cohort?.trim()?.ifBlank { null },
            )
        }
        require(authors.map { it.id }.distinct().size == authors.size) { "Author ids must be unique" }

        val normalizedExtensions = classificationProperties.languageByExtension.entries.associate { (key, value) ->
            key.removePrefix(".").trim().lowercase() to value.trim().lowercase()
        }
        val rules = classificationProperties.rules
        require(rules.map { it.id }.distinct().size == rules.size) { "Classification rule ids must be unique" }
        rules.forEach { rule ->
            require(rule.category in SupportedTaxonomy.categories) {
                "Classification rule ${rule.id} uses unsupported category ${rule.category}"
            }
            require(rule.subtype.isNotBlank()) { "Classification rule ${rule.id} must define subtype" }
        }

        validateDateWindow(
            from = syncWindowProperties.from,
            to = syncWindowProperties.to,
            label = "syncWindow",
        )
        validateDateWindow(
            from = uiDefaultsProperties.defaultDateFrom,
            to = uiDefaultsProperties.defaultDateTo,
            label = "uiDefaults default date range",
        )
        requireSupportedChartLibrary(
            value = uiDefaultsProperties.defaultChartLibrary,
            label = "uiDefaults.defaultChartLibrary",
        )

        return ResolvedDashboardConfig(
            app = appProperties.copy(dataDir = dataDir.toString()),
            git = ResolvedGitProperties(
                executable = resolvedGitExecutable.toString(),
                mirrorDir = mirrorDir.toAbsolutePath().normalize(),
                timeoutSeconds = gitProperties.timeoutSeconds,
                includeMergeCommits = gitProperties.includeMergeCommits,
                deduplicateByPatchId = gitProperties.deduplicateByPatchId,
                useAuthoredDate = gitProperties.useAuthoredDate,
                auth = gitAuth,
            ),
            repos = repos,
            authors = authors,
            classification = ResolvedClassificationProperties(
                languageByExtension = normalizedExtensions,
                rules = rules,
            ),
            uiDefaults = uiDefaultsProperties,
            syncWindow = ResolvedSyncWindowProperties(
                from = syncWindowProperties.from,
                to = syncWindowProperties.to,
            ),
        )
    }

    @Bean
    fun databasePath(resolvedDashboardConfig: ResolvedDashboardConfig): Path =
        Paths.get(resolvedDashboardConfig.app.dataDir).resolve("app.db").toAbsolutePath().normalize()

    private fun validateDateWindow(
        from: LocalDate?,
        to: LocalDate?,
        label: String,
    ) {
        require(from == null || to == null || !to.isBefore(from)) {
            "$label to-date must be on or after the from-date"
        }
    }
}
