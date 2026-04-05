package com.company.throughput.v2.service

import com.company.throughput.classify.ClassificationService
import com.company.throughput.config.RepoConfig
import com.company.throughput.config.ResolvedDashboardConfig
import com.company.throughput.git.GitCommandRunner
import com.company.throughput.git.GitFileStat
import com.company.throughput.git.GitHistoryExtractor
import com.company.throughput.mirror.MirrorSyncService
import com.company.throughput.v2.model.JiraConnectionTestResponse
import com.company.throughput.v2.model.JiraSyncStatusResponse
import com.company.throughput.v2.model.SnapshotStatusResponse
import com.company.throughput.v2.model.SnapshotTriggerResponse
import com.company.throughput.v2.repo.CommitIssueLinkRecord
import com.company.throughput.v2.repo.CommitIssueLinkRepository
import com.company.throughput.v2.repo.JiraIssueCacheRecord
import com.company.throughput.v2.repo.JiraIssueCacheRepository
import com.company.throughput.v2.repo.SnapshotBreakdownInput
import com.company.throughput.v2.repo.SnapshotRepository
import com.company.throughput.v2.repo.ThroughputIssueFactRepository
import org.springframework.boot.ApplicationRunner
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient
import java.time.Instant
import java.time.LocalDate
import kotlin.math.max

@Service
class JiraIssueLinkExtractionService(
    private val jdbcClient: org.springframework.jdbc.core.simple.JdbcClient,
    private val jiraSettingsService: JiraSettingsService,
    private val commitIssueLinkRepository: CommitIssueLinkRepository,
) {
    fun extractAllLinks(): Int {
        val settings = jiraSettingsService.get()
        val regex = Regex(settings.issueKeyRegex)
        val commits = jdbcClient.sql(
            """
            SELECT repo_id, commit_sha, subject
            FROM commit_fact
            ORDER BY authored_at ASC
            """.trimIndent(),
        ).query { rs, _ ->
            Triple(rs.getString("repo_id"), rs.getString("commit_sha"), rs.getString("subject").orEmpty())
        }.list()

        var links = 0
        commits.forEach { (repoId, commitSha, subject) ->
            regex.findAll(subject).map { it.value }.distinct().forEachIndexed { index, issueKey ->
                commitIssueLinkRepository.upsert(
                    CommitIssueLinkRecord(
                        repoId = repoId,
                        commitSha = commitSha,
                        issueKey = issueKey,
                        linkRank = index,
                        isPrimary = index == 0,
                        source = "commit_message",
                    ),
                )
                links += 1
            }
        }
        return links
    }
}

@Service
class JiraClient(
    private val jiraSettingsService: JiraSettingsService,
    private val jiraSecretStore: JiraSecretStore,
) {
    fun testConnection(): JiraConnectionTestResponse {
        val settings = jiraSettingsService.get()
        require(settings.baseUrl != null) { "JIRA_NOT_CONFIGURED: baseUrl is required" }
        require(jiraSettingsService.hasSecret()) { "JIRA_NOT_CONFIGURED: token is required" }
        // Jira does not offer a single lightweight anonymous ping in every deployment shape.
        // We validate the client envelope by hitting the current-user endpoint.
        fetch("rest/api/2/myself", allowFailure = false)
        jiraSettingsService.markValidated()
        return JiraConnectionTestResponse(ok = true, message = "Connection configuration is valid.")
    }

    fun fetchIssue(issueKey: String, allowFailure: Boolean = false): Map<String, Any?>? {
        return fetch("rest/api/2/issue/$issueKey", allowFailure)
    }

    private fun fetch(path: String, allowFailure: Boolean): Map<String, Any?>? {
        val settings = jiraSettingsService.get()
        val token = jiraSecretStore.getToken()
        require(settings.baseUrl != null) { "JIRA_NOT_CONFIGURED: baseUrl is required" }
        require(!token.isNullOrBlank()) { "JIRA_NOT_CONFIGURED: token is required" }
        val restClient = RestClient.builder()
            .baseUrl(settings.baseUrl.trimEnd('/'))
            .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer $token")
            .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
            .build()
        return runCatching {
            @Suppress("UNCHECKED_CAST")
            restClient.get()
                .uri("/$path")
                .retrieve()
                .body(Map::class.java) as Map<String, Any?>
        }.getOrElse { exception ->
            if (allowFailure) emptyMap() else throw IllegalStateException("JIRA_AUTH_FAILED: ${exception.message}", exception)
        }
    }
}

@Service
class JiraSyncService(
    private val jdbcClient: org.springframework.jdbc.core.simple.JdbcClient,
    private val jiraClient: JiraClient,
    private val jiraIssueLinkExtractionService: JiraIssueLinkExtractionService,
    private val jiraIssueCacheRepository: JiraIssueCacheRepository,
    private val throughputIssueFactRepository: ThroughputIssueFactRepository,
    private val commitIssueLinkRepository: CommitIssueLinkRepository,
    private val jiraSettingsService: JiraSettingsService,
) {
    fun sync(): JiraSyncStatusResponse {
        val settings = jiraSettingsService.get()
        require(settings.enabled) { "JIRA_NOT_CONFIGURED: Jira is disabled" }
        require(jiraSettingsService.hasSecret()) { "JIRA_NOT_CONFIGURED: Jira token is missing" }

        jiraIssueLinkExtractionService.extractAllLinks()

        val issueKeys = jdbcClient.sql(
            """
            SELECT DISTINCT issue_key
            FROM commit_issue_links
            ORDER BY issue_key
            """.trimIndent(),
        ).query(String::class.java).list()

        issueKeys.forEach { issueKey ->
            val payload = jiraClient.fetchIssue(issueKey) ?: return@forEach
            val fields = payload["fields"] as? Map<*, *> ?: emptyMap<Any?, Any?>()
            val issueType = (fields["issuetype"] as? Map<*, *>)?.get("name")?.toString()
            val status = (fields["status"] as? Map<*, *>)?.get("name")?.toString()
            val priority = (fields["priority"] as? Map<*, *>)?.get("name")?.toString()
            val assignee = (fields["assignee"] as? Map<*, *>)?.get("displayName")?.toString()
            val reporter = (fields["reporter"] as? Map<*, *>)?.get("displayName")?.toString()
            val projectKey = (fields["project"] as? Map<*, *>)?.get("key")?.toString()
            val labels = (fields["labels"] as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList()
            val components = (fields["components"] as? List<*>)?.mapNotNull { (it as? Map<*, *>)?.get("name")?.toString() } ?: emptyList()
            jiraIssueCacheRepository.upsert(
                JiraIssueCacheRecord(
                    issueKey = issueKey,
                    issueId = payload["id"]?.toString(),
                    projectKey = projectKey,
                    summary = fields["summary"]?.toString(),
                    issueType = issueType,
                    status = status,
                    priority = priority,
                    assigneeDisplayName = assignee,
                    reporterDisplayName = reporter,
                    labels = labels,
                    components = components,
                    createdAtRemote = fields["created"]?.toString(),
                    updatedAtRemote = fields["updated"]?.toString(),
                    resolvedAtRemote = fields["resolutiondate"]?.toString(),
                    browseUrl = "${settings.baseUrl?.trimEnd('/')}/browse/$issueKey",
                    lastFetchedAt = Instant.now(),
                    rawJson = payload.toString(),
                ),
            )
        }

        throughputIssueFactRepository.rebuildAll()
        return status()
    }

    fun status(): JiraSyncStatusResponse {
        val settings = jiraSettingsService.get()
        return JiraSyncStatusResponse(
            enabled = settings.enabled,
            configured = !settings.baseUrl.isNullOrBlank(),
            secretConfigured = jiraSettingsService.hasSecret(),
            lastValidatedAt = settings.lastValidatedAt,
            lastSyncAt = jiraIssueCacheRepository.latestFetchAt(),
            cachedIssues = jiraIssueCacheRepository.count(),
            linkedCommits = commitIssueLinkRepository.count(),
        )
    }
}

@Service
class RepoSnapshotService(
    private val resolvedDashboardConfig: ResolvedDashboardConfig,
    private val mirrorSyncService: MirrorSyncService,
    private val gitHistoryExtractor: GitHistoryExtractor,
    private val gitCommandRunner: GitCommandRunner,
    private val classificationService: ClassificationService,
    private val snapshotRepository: SnapshotRepository,
) {
    fun buildCurrentSnapshots(): SnapshotTriggerResponse {
        val rebuilt = resolvedDashboardConfig.repos.filter { it.enabled }.map { repo ->
            val result = buildCurrentSnapshot(repo)
            result.first
        }
        return SnapshotTriggerResponse(accepted = true, rebuiltRepos = rebuilt)
    }

    fun status(): SnapshotStatusResponse = SnapshotStatusResponse(snapshotRepository.status())

    fun backfill(lookbackDays: Int = 30): SnapshotTriggerResponse {
        val rebuilt = mutableListOf<String>()
        resolvedDashboardConfig.repos.filter { it.enabled }.forEach { repo ->
            val mirrorPath = mirrorSyncService.ensureMirror(repo)
            val ref = resolvePrimaryRef(repo, mirrorPath)
            var previousCommitSha: String? = null
            var previousTotals: Map<String, Int>? = null
            var previousBreakdowns: List<SnapshotBreakdownInput>? = null
            var date = LocalDate.now().minusDays(lookbackDays.toLong() - 1L)
            val end = LocalDate.now()
            while (!date.isAfter(end)) {
                val commitSha = resolveCommitBefore(mirrorPath, ref, date)
                if (commitSha != null) {
                    if (commitSha == previousCommitSha && previousTotals != null && previousBreakdowns != null) {
                        snapshotRepository.saveSnapshot(repo.id, date, ref, commitSha, previousTotals!!, previousBreakdowns!!)
                    } else {
                        val built = buildSnapshotForCommit(repo, mirrorPath, ref, commitSha, persistInventory = false)
                        snapshotRepository.saveSnapshot(repo.id, date, ref, commitSha, built.totals, built.breakdowns)
                        previousCommitSha = commitSha
                        previousTotals = built.totals
                        previousBreakdowns = built.breakdowns
                    }
                    snapshotRepository.saveBackfillState(repo.id, ref, date.plusDays(1), "IN_PROGRESS")
                }
                date = date.plusDays(1)
            }
            snapshotRepository.saveBackfillState(repo.id, ref, end.plusDays(1), "COMPLETE")
            rebuilt += repo.id
        }
        return SnapshotTriggerResponse(accepted = true, rebuiltRepos = rebuilt)
    }

    private fun buildCurrentSnapshot(repo: RepoConfig): Pair<String, String> {
        val mirrorPath = mirrorSyncService.ensureMirror(repo)
        val ref = resolvePrimaryRef(repo, mirrorPath)
        val commitSha = gitCommandRunner.runGit(listOf("-C", mirrorPath.toString(), "rev-parse", ref)).stdout.trim()
        val built = buildSnapshotForCommit(repo, mirrorPath, ref, commitSha, persistInventory = true)
        snapshotRepository.saveSnapshot(repo.id, LocalDate.now(), ref, commitSha, built.totals, built.breakdowns)
        return repo.id to commitSha
    }

    private data class BuiltSnapshot(
        val totals: Map<String, Int>,
        val breakdowns: List<SnapshotBreakdownInput>,
    )

    private fun buildSnapshotForCommit(
        repo: RepoConfig,
        mirrorPath: java.nio.file.Path,
        refName: String,
        commitSha: String,
        persistInventory: Boolean,
    ): BuiltSnapshot {
        val files = gitCommandRunner.runGit(
            listOf("-C", mirrorPath.toString(), "ls-tree", "-r", "--name-only", commitSha),
        ).stdout.lineSequence().map(String::trim).filter(String::isNotBlank).toList()

        val inventoryRows = files.mapNotNull { filePath ->
            if (classificationService.isPathExcluded(repo, filePath)) {
                return@mapNotNull null
            }
            val contentResult = runCatching {
                gitCommandRunner.runGit(listOf("-C", mirrorPath.toString(), "show", "$commitSha:$filePath")).stdout
            }.getOrDefault("")
            val isBinary = isProbablyBinary(filePath, contentResult)
            val lineCount = if (isBinary) 0 else max(1, contentResult.lineSequence().count())
            val classified = classificationService.classify(
                repo,
                GitFileStat(
                    filePath = filePath,
                    oldPath = null,
                    linesAdded = lineCount,
                    linesRemoved = 0,
                    isBinary = isBinary,
                ),
            )
            mapOf(
                "filePath" to filePath,
                "language" to classified.language,
                "category" to classified.category,
                "subtype" to classified.subtype,
                "productCode" to classified.productCode,
                "lineCount" to lineCount,
                "isBinary" to if (isBinary) 1 else 0,
            )
        }

        if (persistInventory) {
            snapshotRepository.replaceCurrentInventory(repo.id, refName, commitSha, inventoryRows)
        }

        val totals = mutableMapOf(
            "totalFiles" to inventoryRows.size,
            "totalLines" to inventoryRows.sumOf { (it["lineCount"] as Int) },
            "productionFiles" to 0,
            "productionLines" to 0,
            "testFiles" to 0,
            "testLines" to 0,
            "docsFiles" to 0,
            "docsLines" to 0,
            "generatedFiles" to 0,
            "generatedLines" to 0,
            "unknownFiles" to 0,
            "unknownLines" to 0,
        )

        val breakdownGroups = linkedMapOf<String, MutableMap<String, Pair<Int, Int>>>()
        listOf("language", "category", "subtype").forEach { breakdownGroups[it] = linkedMapOf() }

        inventoryRows.forEach { row ->
            val category = row["category"] as String
            val lineCount = row["lineCount"] as Int
            when (category) {
                "production" -> {
                    totals["productionFiles"] = totals.getValue("productionFiles") + 1
                    totals["productionLines"] = totals.getValue("productionLines") + lineCount
                }
                "test" -> {
                    totals["testFiles"] = totals.getValue("testFiles") + 1
                    totals["testLines"] = totals.getValue("testLines") + lineCount
                }
                "docs" -> {
                    totals["docsFiles"] = totals.getValue("docsFiles") + 1
                    totals["docsLines"] = totals.getValue("docsLines") + lineCount
                }
                "generated" -> {
                    totals["generatedFiles"] = totals.getValue("generatedFiles") + 1
                    totals["generatedLines"] = totals.getValue("generatedLines") + lineCount
                }
                else -> {
                    totals["unknownFiles"] = totals.getValue("unknownFiles") + 1
                    totals["unknownLines"] = totals.getValue("unknownLines") + lineCount
                }
            }

            listOf("language", "category", "subtype").forEach { dimension ->
                val value = row[dimension]?.toString().orEmpty().ifBlank { "unknown" }
                val previous = breakdownGroups.getValue(dimension).getOrDefault(value, 0 to 0)
                breakdownGroups.getValue(dimension)[value] = (previous.first + 1) to (previous.second + lineCount)
            }
        }

        val breakdowns = breakdownGroups.flatMap { (dimension, values) ->
            values.map { (value, counts) ->
                SnapshotBreakdownInput(dimensionKind = dimension, dimensionValue = value, filesCount = counts.first, linesCount = counts.second)
            }
        }
        return BuiltSnapshot(totals = totals, breakdowns = breakdowns)
    }

    private fun resolvePrimaryRef(repo: RepoConfig, mirrorPath: java.nio.file.Path): String =
        gitHistoryExtractor.resolveRefs(repo, mirrorPath)
            .firstOrNull()?.name
            ?: error("No matching refs found for ${repo.id}")

    private fun resolveCommitBefore(mirrorPath: java.nio.file.Path, refName: String, date: LocalDate): String? =
        gitCommandRunner.runGit(
            listOf(
                "-C",
                mirrorPath.toString(),
                "rev-list",
                "-n",
                "1",
                "--before=${date}T23:59:59",
                refName,
            ),
        ).stdout.trim().ifBlank { null }

    private fun isProbablyBinary(filePath: String, content: String): Boolean {
        val lowered = filePath.lowercase()
        if (lowered.endsWith(".png") || lowered.endsWith(".jpg") || lowered.endsWith(".jpeg") || lowered.endsWith(".gif") || lowered.endsWith(".ico") || lowered.endsWith(".pdf")) {
            return true
        }
        return content.contains('\u0000')
    }
}

@Component
class V2SeedRunner(
    private val widgetCatalogService: WidgetCatalogService,
    private val pageService: PageService,
) : ApplicationRunner {
    override fun run(args: org.springframework.boot.ApplicationArguments) {
        if (widgetCatalogService.list(includeArchived = true).isEmpty()) {
            seedWidgets()
        }
        if (pageService.list(includeArchived = true).isEmpty()) {
            seedPages()
        }
    }

    private fun seedWidgets() {
        widgetCatalogService.create(
            com.company.throughput.v2.model.CreateWidgetDefinitionRequest(
                title = "Daily lines added",
                description = "Throughput over time by repository.",
                tags = listOf("throughput", "timeseries"),
                kind = com.company.throughput.v2.model.WidgetKind.TIME_SERIES,
                datasetKey = com.company.throughput.v2.model.DatasetKey.THROUGHPUT_DAILY,
                querySpec = com.company.throughput.v2.model.WidgetQuerySpec(
                    dataset = com.company.throughput.v2.model.DatasetKey.THROUGHPUT_DAILY,
                    timeBucket = com.company.throughput.v2.model.TimeBucket.DAY,
                    measure = com.company.throughput.v2.model.MeasureSpec("lines_added", com.company.throughput.v2.model.AggregationType.SUM),
                    groupBy = listOf("repo"),
                ),
                vizSpec = com.company.throughput.v2.model.WidgetVizSpec(chartType = "line", emptyStateMessage = "No throughput data"),
            ),
        )
        widgetCatalogService.create(
            com.company.throughput.v2.model.CreateWidgetDefinitionRequest(
                title = "Current language mix",
                description = "Current repository composition by language.",
                tags = listOf("repo-state", "distribution"),
                kind = com.company.throughput.v2.model.WidgetKind.DISTRIBUTION,
                datasetKey = com.company.throughput.v2.model.DatasetKey.FILE_INVENTORY_CURRENT,
                querySpec = com.company.throughput.v2.model.WidgetQuerySpec(
                    dataset = com.company.throughput.v2.model.DatasetKey.FILE_INVENTORY_CURRENT,
                    measure = com.company.throughput.v2.model.MeasureSpec("lines_count", com.company.throughput.v2.model.AggregationType.SUM),
                    groupBy = listOf("language"),
                    limit = 8,
                ),
                vizSpec = com.company.throughput.v2.model.WidgetVizSpec(chartType = "donut", emptyStateMessage = "Run a snapshot to populate current inventory"),
            ),
        )
        widgetCatalogService.create(
            com.company.throughput.v2.model.CreateWidgetDefinitionRequest(
                title = "Day explorer",
                description = "Context panel for the selected throughput day.",
                tags = listOf("drilldown"),
                kind = com.company.throughput.v2.model.WidgetKind.DAY_EXPLORER,
                datasetKey = com.company.throughput.v2.model.DatasetKey.DAY_ACTIVITY,
                querySpec = com.company.throughput.v2.model.WidgetQuerySpec(dataset = com.company.throughput.v2.model.DatasetKey.DAY_ACTIVITY),
                vizSpec = com.company.throughput.v2.model.WidgetVizSpec(emptyStateMessage = "Select a day from a compatible chart to inspect activity"),
            ),
        )
    }

    private fun seedPages() {
        pageService.create(com.company.throughput.v2.model.CreatePageRequest(title = "Weekly repo review", description = "Seeded V2 workspace page"))
    }
}
