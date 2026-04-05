package com.company.throughput.v2.repo

import com.company.throughput.v2.model.JiraConnectionSettings
import com.company.throughput.v2.model.SnapshotStatusItem
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.time.Instant
import java.time.LocalDate

data class JiraIssueCacheRecord(
    val issueKey: String,
    val issueId: String?,
    val projectKey: String?,
    val summary: String?,
    val issueType: String?,
    val status: String?,
    val priority: String?,
    val assigneeDisplayName: String?,
    val reporterDisplayName: String?,
    val labels: List<String>,
    val components: List<String>,
    val createdAtRemote: String?,
    val updatedAtRemote: String?,
    val resolvedAtRemote: String?,
    val browseUrl: String?,
    val lastFetchedAt: Instant,
    val rawJson: String,
)

data class CommitIssueLinkRecord(
    val repoId: String,
    val commitSha: String,
    val issueKey: String,
    val linkRank: Int,
    val isPrimary: Boolean,
    val source: String,
)

data class RepoStateSnapshotRecord(
    val id: Long,
    val repoId: String,
    val snapshotDate: LocalDate,
    val refName: String,
    val commitSha: String,
)

data class SnapshotBreakdownInput(
    val dimensionKind: String,
    val dimensionValue: String,
    val filesCount: Int,
    val linesCount: Int,
)

@Repository
class JiraSettingsRepository(
    private val jdbcClient: JdbcClient,
    private val objectMapper: ObjectMapper,
) {
    fun get(): JiraConnectionSettings =
        jdbcClient.sql(
            """
            SELECT enabled, base_url, auth_mode, verify_tls, issue_key_regex, project_keys_json, last_validated_at, updated_at
            FROM jira_connection_settings
            WHERE singleton_id = 1
            """.trimIndent(),
        )
            .query { rs, _ ->
                JiraConnectionSettings(
                    enabled = rs.getInt("enabled") == 1,
                    baseUrl = rs.getString("base_url"),
                    authMode = rs.getString("auth_mode"),
                    verifyTls = rs.getInt("verify_tls") == 1,
                    issueKeyRegex = rs.getString("issue_key_regex"),
                    projectKeys = objectMapper.readJson(rs.getString("project_keys_json")),
                    lastValidatedAt = rs.getString("last_validated_at")?.takeIf { it.isNotBlank() }?.let(::parseInstant),
                    updatedAt = parseInstant(rs.getString("updated_at")),
                )
            }
            .single()

    fun update(enabled: Boolean, baseUrl: String?, verifyTls: Boolean, issueKeyRegex: String, projectKeys: List<String>) {
        jdbcClient.sql(
            """
            UPDATE jira_connection_settings
            SET enabled = :enabled,
                base_url = :baseUrl,
                verify_tls = :verifyTls,
                issue_key_regex = :issueKeyRegex,
                project_keys_json = :projectKeysJson,
                updated_at = :updatedAt
            WHERE singleton_id = 1
            """.trimIndent(),
        )
            .param("enabled", if (enabled) 1 else 0)
            .param("baseUrl", baseUrl)
            .param("verifyTls", if (verifyTls) 1 else 0)
            .param("issueKeyRegex", issueKeyRegex)
            .param("projectKeysJson", objectMapper.writeValueAsString(projectKeys))
            .param("updatedAt", Instant.now().toString())
            .update()
    }

    fun markValidated() {
        val now = Instant.now().toString()
        jdbcClient.sql(
            """
            UPDATE jira_connection_settings
            SET last_validated_at = :now, updated_at = :now
            WHERE singleton_id = 1
            """.trimIndent(),
        )
            .param("now", now)
            .update()
    }
}

@Repository
class JiraIssueCacheRepository(
    private val jdbcClient: JdbcClient,
    private val objectMapper: ObjectMapper,
) {
    fun upsert(record: JiraIssueCacheRecord) {
        jdbcClient.sql(
            """
            INSERT INTO jira_issue_cache (
              issue_key, issue_id, project_key, summary, issue_type, status, priority,
              assignee_display_name, reporter_display_name, labels_json, components_json,
              created_at_remote, updated_at_remote, resolved_at_remote, browse_url, last_fetched_at, raw_json
            ) VALUES (
              :issueKey, :issueId, :projectKey, :summary, :issueType, :status, :priority,
              :assigneeDisplayName, :reporterDisplayName, :labelsJson, :componentsJson,
              :createdAtRemote, :updatedAtRemote, :resolvedAtRemote, :browseUrl, :lastFetchedAt, :rawJson
            )
            ON CONFLICT(issue_key) DO UPDATE SET
              issue_id = excluded.issue_id,
              project_key = excluded.project_key,
              summary = excluded.summary,
              issue_type = excluded.issue_type,
              status = excluded.status,
              priority = excluded.priority,
              assignee_display_name = excluded.assignee_display_name,
              reporter_display_name = excluded.reporter_display_name,
              labels_json = excluded.labels_json,
              components_json = excluded.components_json,
              created_at_remote = excluded.created_at_remote,
              updated_at_remote = excluded.updated_at_remote,
              resolved_at_remote = excluded.resolved_at_remote,
              browse_url = excluded.browse_url,
              last_fetched_at = excluded.last_fetched_at,
              raw_json = excluded.raw_json
            """.trimIndent(),
        )
            .param("issueKey", record.issueKey)
            .param("issueId", record.issueId)
            .param("projectKey", record.projectKey)
            .param("summary", record.summary)
            .param("issueType", record.issueType)
            .param("status", record.status)
            .param("priority", record.priority)
            .param("assigneeDisplayName", record.assigneeDisplayName)
            .param("reporterDisplayName", record.reporterDisplayName)
            .param("labelsJson", objectMapper.writeValueAsString(record.labels))
            .param("componentsJson", objectMapper.writeValueAsString(record.components))
            .param("createdAtRemote", record.createdAtRemote)
            .param("updatedAtRemote", record.updatedAtRemote)
            .param("resolvedAtRemote", record.resolvedAtRemote)
            .param("browseUrl", record.browseUrl)
            .param("lastFetchedAt", record.lastFetchedAt.toString())
            .param("rawJson", record.rawJson)
            .update()
    }

    fun count(): Int =
        jdbcClient.sql("SELECT COUNT(*) FROM jira_issue_cache")
            .query(Int::class.java)
            .single()

    fun latestFetchAt(): Instant? =
        jdbcClient.sql("SELECT MAX(last_fetched_at) FROM jira_issue_cache")
            .query(String::class.java)
            .optional()
            .orElse(null)
            ?.let(::parseInstant)
}

@Repository
class CommitIssueLinkRepository(
    private val jdbcClient: JdbcClient,
) {
    fun upsert(record: CommitIssueLinkRecord) {
        jdbcClient.sql(
            """
            INSERT INTO commit_issue_links (repo_id, commit_sha, issue_key, link_rank, is_primary, source)
            VALUES (:repoId, :commitSha, :issueKey, :linkRank, :isPrimary, :source)
            ON CONFLICT(repo_id, commit_sha, issue_key) DO UPDATE SET
              link_rank = excluded.link_rank,
              is_primary = excluded.is_primary,
              source = excluded.source
            """.trimIndent(),
        )
            .param("repoId", record.repoId)
            .param("commitSha", record.commitSha)
            .param("issueKey", record.issueKey)
            .param("linkRank", record.linkRank)
            .param("isPrimary", if (record.isPrimary) 1 else 0)
            .param("source", record.source)
            .update()
    }

    fun count(): Int =
        jdbcClient.sql("SELECT COUNT(*) FROM commit_issue_links")
            .query(Int::class.java)
            .single()
}

@Repository
class ThroughputIssueFactRepository(
    private val jdbcClient: JdbcClient,
) {
    fun rebuildAll() {
        jdbcClient.sql("DELETE FROM daily_throughput_issue_fact").update()
        jdbcClient.sql(
            """
            INSERT INTO daily_throughput_issue_fact (
              activity_date, repo_id, author_identity, language, category, subtype, product_code,
              primary_issue_key, primary_issue_type, primary_issue_status, primary_issue_priority, jira_project_key,
              lines_added, lines_removed, net_lines, commits_count, files_changed_count
            )
            SELECT
              DATE(c.authored_at) AS activity_date,
              c.repo_id,
              c.author_id AS author_identity,
              COALESCE(f.language, 'unknown') AS language,
              COALESCE(f.category, 'unknown') AS category,
              COALESCE(f.subtype, 'unknown') AS subtype,
              COALESCE(f.product_code, '') AS product_code,
              l.issue_key AS primary_issue_key,
              i.issue_type AS primary_issue_type,
              i.status AS primary_issue_status,
              i.priority AS primary_issue_priority,
              i.project_key AS jira_project_key,
              SUM(f.lines_added) AS lines_added,
              SUM(f.lines_removed) AS lines_removed,
              SUM(f.net_lines) AS net_lines,
              COUNT(DISTINCT c.commit_sha) AS commits_count,
              COUNT(*) AS files_changed_count
            FROM commit_fact c
            JOIN commit_file_fact f
              ON f.repo_id = c.repo_id
             AND f.commit_sha = c.commit_sha
            LEFT JOIN commit_issue_links l
              ON l.repo_id = c.repo_id
             AND l.commit_sha = c.commit_sha
             AND l.is_primary = 1
            LEFT JOIN jira_issue_cache i
              ON i.issue_key = l.issue_key
            WHERE c.author_id IS NOT NULL
              AND f.is_canonical_patch = 1
            GROUP BY
              DATE(c.authored_at),
              c.repo_id,
              c.author_id,
              COALESCE(f.language, 'unknown'),
              COALESCE(f.category, 'unknown'),
              COALESCE(f.subtype, 'unknown'),
              COALESCE(f.product_code, ''),
              l.issue_key,
              i.issue_type,
              i.status,
              i.priority,
              i.project_key
            """.trimIndent(),
        ).update()
    }
}

@Repository
class SnapshotRepository(
    private val jdbcClient: JdbcClient,
) {
    fun replaceCurrentInventory(
        repoId: String,
        refName: String,
        commitSha: String,
        rows: List<Map<String, Any?>>,
    ) {
        jdbcClient.sql(
            """
            DELETE FROM file_inventory_current
            WHERE repo_id = :repoId AND ref_name = :refName
            """.trimIndent(),
        )
            .param("repoId", repoId)
            .param("refName", refName)
            .update()

        rows.forEach { row ->
            jdbcClient.sql(
                """
                INSERT INTO file_inventory_current (
                  repo_id, ref_name, commit_sha, file_path, language, category, subtype, product_code,
                  line_count, is_binary, updated_at
                ) VALUES (
                  :repoId, :refName, :commitSha, :filePath, :language, :category, :subtype, :productCode,
                  :lineCount, :isBinary, :updatedAt
                )
                """.trimIndent(),
            )
                .param("repoId", repoId)
                .param("refName", refName)
                .param("commitSha", commitSha)
                .param("filePath", row["filePath"])
                .param("language", row["language"])
                .param("category", row["category"])
                .param("subtype", row["subtype"])
                .param("productCode", row["productCode"])
                .param("lineCount", row["lineCount"])
                .param("isBinary", row["isBinary"])
                .param("updatedAt", Instant.now().toString())
                .update()
        }
    }

    fun saveSnapshot(
        repoId: String,
        snapshotDate: LocalDate,
        refName: String,
        commitSha: String,
        totals: Map<String, Int>,
        breakdowns: List<SnapshotBreakdownInput>,
    ) {
        jdbcClient.sql(
            """
            INSERT INTO repo_state_snapshots (
              repo_id, snapshot_date, ref_name, commit_sha,
              total_files, total_lines, production_files, production_lines, test_files, test_lines,
              docs_files, docs_lines, generated_files, generated_lines, unknown_files, unknown_lines, created_at
            ) VALUES (
              :repoId, :snapshotDate, :refName, :commitSha,
              :totalFiles, :totalLines, :productionFiles, :productionLines, :testFiles, :testLines,
              :docsFiles, :docsLines, :generatedFiles, :generatedLines, :unknownFiles, :unknownLines, :createdAt
            )
            ON CONFLICT(repo_id, snapshot_date, ref_name) DO UPDATE SET
              commit_sha = excluded.commit_sha,
              total_files = excluded.total_files,
              total_lines = excluded.total_lines,
              production_files = excluded.production_files,
              production_lines = excluded.production_lines,
              test_files = excluded.test_files,
              test_lines = excluded.test_lines,
              docs_files = excluded.docs_files,
              docs_lines = excluded.docs_lines,
              generated_files = excluded.generated_files,
              generated_lines = excluded.generated_lines,
              unknown_files = excluded.unknown_files,
              unknown_lines = excluded.unknown_lines,
              created_at = excluded.created_at
            """.trimIndent(),
        )
            .param("repoId", repoId)
            .param("snapshotDate", snapshotDate.toString())
            .param("refName", refName)
            .param("commitSha", commitSha)
            .param("totalFiles", totals.getValue("totalFiles"))
            .param("totalLines", totals.getValue("totalLines"))
            .param("productionFiles", totals.getValue("productionFiles"))
            .param("productionLines", totals.getValue("productionLines"))
            .param("testFiles", totals.getValue("testFiles"))
            .param("testLines", totals.getValue("testLines"))
            .param("docsFiles", totals.getValue("docsFiles"))
            .param("docsLines", totals.getValue("docsLines"))
            .param("generatedFiles", totals.getValue("generatedFiles"))
            .param("generatedLines", totals.getValue("generatedLines"))
            .param("unknownFiles", totals.getValue("unknownFiles"))
            .param("unknownLines", totals.getValue("unknownLines"))
            .param("createdAt", Instant.now().toString())
            .update()

        val snapshotId = jdbcClient.sql(
            """
            SELECT id
            FROM repo_state_snapshots
            WHERE repo_id = :repoId AND snapshot_date = :snapshotDate AND ref_name = :refName
            """.trimIndent(),
        )
            .param("repoId", repoId)
            .param("snapshotDate", snapshotDate.toString())
            .param("refName", refName)
            .query(Long::class.java)
            .single()

        jdbcClient.sql("DELETE FROM repo_state_snapshot_breakdowns WHERE snapshot_id = :snapshotId")
            .param("snapshotId", snapshotId)
            .update()
        breakdowns.forEach { breakdown ->
            jdbcClient.sql(
                """
                INSERT INTO repo_state_snapshot_breakdowns (
                  snapshot_id, dimension_kind, dimension_value, files_count, lines_count
                ) VALUES (
                  :snapshotId, :dimensionKind, :dimensionValue, :filesCount, :linesCount
                )
                """.trimIndent(),
            )
                .param("snapshotId", snapshotId)
                .param("dimensionKind", breakdown.dimensionKind)
                .param("dimensionValue", breakdown.dimensionValue)
                .param("filesCount", breakdown.filesCount)
                .param("linesCount", breakdown.linesCount)
                .update()
        }
    }

    fun saveBackfillState(repoId: String, refName: String, nextSnapshotDate: LocalDate, status: String) {
        jdbcClient.sql(
            """
            INSERT INTO snapshot_backfill_state (repo_id, ref_name, next_snapshot_date, status, updated_at)
            VALUES (:repoId, :refName, :nextSnapshotDate, :status, :updatedAt)
            ON CONFLICT(repo_id, ref_name) DO UPDATE SET
              next_snapshot_date = excluded.next_snapshot_date,
              status = excluded.status,
              updated_at = excluded.updated_at
            """.trimIndent(),
        )
            .param("repoId", repoId)
            .param("refName", refName)
            .param("nextSnapshotDate", nextSnapshotDate.toString())
            .param("status", status)
            .param("updatedAt", Instant.now().toString())
            .update()
    }

    fun status(): List<SnapshotStatusItem> =
        jdbcClient.sql(
            """
            SELECT
              base.repo_id,
              base.ref_name,
              b.next_snapshot_date,
              b.status,
              MAX(s.snapshot_date) AS latest_snapshot_date
            FROM (
              SELECT repo_id, ref_name FROM repo_state_snapshots
              UNION
              SELECT repo_id, ref_name FROM snapshot_backfill_state
            ) base
            LEFT JOIN repo_state_snapshots s
              ON s.repo_id = base.repo_id
             AND s.ref_name = base.ref_name
            LEFT JOIN snapshot_backfill_state b
              ON b.repo_id = base.repo_id
             AND b.ref_name = base.ref_name
            GROUP BY base.repo_id, base.ref_name, b.next_snapshot_date, b.status
            ORDER BY base.repo_id, base.ref_name
            """.trimIndent(),
        )
            .query { rs, _ ->
                SnapshotStatusItem(
                    repoId = rs.getString("repo_id"),
                    refName = rs.getString("ref_name"),
                    nextSnapshotDate = parseLocalDate(rs.getString("next_snapshot_date")),
                    status = rs.getString("status"),
                    latestSnapshotDate = parseLocalDate(rs.getString("latest_snapshot_date")),
                )
            }
            .list()
}
