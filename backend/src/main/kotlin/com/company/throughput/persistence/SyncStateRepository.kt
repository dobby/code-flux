package com.company.throughput.persistence

import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.time.Instant

data class SyncRunRecord(
    val syncRunId: Long,
    val startedAt: Instant,
    val finishedAt: Instant?,
    val status: String,
    val message: String?,
)

data class RepoSyncStateRecord(
    val repoId: String,
    val status: String,
    val lastSuccessfulSyncedAt: Instant?,
    val lastErrorMessage: String?,
)

data class SyncRunEventRecord(
    val eventKey: String,
    val syncRunId: Long,
    val sortOrder: Int,
    val sourceKind: String,
    val repoId: String?,
    val label: String,
    val detail: String?,
    val status: String,
    val startedAt: Instant?,
    val finishedAt: Instant?,
    val progressPercent: Int?,
)

@Repository
class SyncStateRepository(
    private val jdbcClient: JdbcClient,
) {
    fun resetSyncState() {
        jdbcClient.sql(
            """
            UPDATE repo_sync_state
            SET
              last_successful_sync_run_id = NULL,
              last_successful_synced_at = NULL,
              last_seen_commit_sha = NULL,
              last_error_message = NULL
            """.trimIndent(),
        ).update()
        jdbcClient.sql("DELETE FROM sync_run_events").update()
        jdbcClient.sql("DELETE FROM sync_run").update()
    }

    fun abandonIncompleteRuns(message: String) {
        jdbcClient.sql(
            """
            UPDATE sync_run
            SET
              finished_at = CURRENT_TIMESTAMP,
              status = 'ABANDONED',
              message = COALESCE(message, :message)
            WHERE status = 'RUNNING'
              AND finished_at IS NULL
            """.trimIndent(),
        )
            .param("message", message)
            .update()
    }

    fun startRun(): Long {
        jdbcClient.sql(
            """
            INSERT INTO sync_run (started_at, status, message)
            VALUES (CURRENT_TIMESTAMP, 'RUNNING', NULL)
            """.trimIndent(),
        ).update()

        return jdbcClient.sql("SELECT last_insert_rowid()")
            .query(Long::class.java)
            .single()
    }

    fun finishRun(syncRunId: Long, status: String, message: String?) {
        jdbcClient.sql(
            """
            UPDATE sync_run
            SET finished_at = CURRENT_TIMESTAMP, status = :status, message = :message
            WHERE sync_run_id = :syncRunId
            """.trimIndent(),
        )
            .param("syncRunId", syncRunId)
            .param("status", status)
            .param("message", message)
            .update()
    }

    fun markRepoSuccess(repoId: String, syncRunId: Long, lastSeenCommitSha: String?) {
        jdbcClient.sql(
            """
            UPDATE repo_sync_state
            SET
              last_successful_sync_run_id = :syncRunId,
              last_successful_synced_at = CURRENT_TIMESTAMP,
              last_seen_commit_sha = :lastSeenCommitSha,
              last_error_message = NULL
            WHERE repo_id = :repoId
            """.trimIndent(),
        )
            .param("repoId", repoId)
            .param("syncRunId", syncRunId)
            .param("lastSeenCommitSha", lastSeenCommitSha)
            .update()
    }

    fun markRepoFailure(repoId: String, message: String) {
        jdbcClient.sql(
            """
            UPDATE repo_sync_state
            SET last_error_message = :message
            WHERE repo_id = :repoId
            """.trimIndent(),
        )
            .param("repoId", repoId)
            .param("message", message)
            .update()
    }

    fun latestRun(): SyncRunRecord? =
        jdbcClient.sql(
            """
            SELECT sync_run_id, started_at, finished_at, status, message
            FROM sync_run
            ORDER BY sync_run_id DESC
            LIMIT 1
            """.trimIndent(),
        )
            .query { rs, _ ->
                SyncRunRecord(
                    syncRunId = rs.getLong("sync_run_id"),
                    startedAt = Instant.parse(normalizeTimestamp(rs.getString("started_at"))),
                    finishedAt = rs.getString("finished_at")?.let(::normalizeTimestamp)?.let(Instant::parse),
                    status = rs.getString("status"),
                    message = rs.getString("message"),
                )
            }
            .optional()
            .orElse(null)

    fun repoStates(): List<RepoSyncStateRecord> =
        jdbcClient.sql(
            """
            SELECT repo_id, last_successful_synced_at, last_error_message
            FROM repo_sync_state
            ORDER BY repo_id
            """.trimIndent(),
        )
            .query { rs, _ ->
                val lastError = rs.getString("last_error_message")
                val status = when {
                    !lastError.isNullOrBlank() -> "FAILED"
                    rs.getString("last_successful_synced_at") != null -> "SUCCESS"
                    else -> "IDLE"
                }
                RepoSyncStateRecord(
                    repoId = rs.getString("repo_id"),
                    status = status,
                    lastSuccessfulSyncedAt = rs.getString("last_successful_synced_at")
                        ?.let(::normalizeTimestamp)
                        ?.let(Instant::parse),
                    lastErrorMessage = lastError,
                )
            }
            .list()

    fun seedRunEvents(
        syncRunId: Long,
        repoIds: List<String>,
        jiraEnabled: Boolean,
    ) {
        val events = repoIds.mapIndexed { index, repoId ->
            SyncRunEventRecord(
                eventKey = "repo:$repoId",
                syncRunId = syncRunId,
                sortOrder = index,
                sourceKind = "git",
                repoId = repoId,
                label = repoId,
                detail = "Queued",
                status = "QUEUED",
                startedAt = null,
                finishedAt = null,
                progressPercent = null,
            )
        } + SyncRunEventRecord(
            eventKey = "jira:enrichment",
            syncRunId = syncRunId,
            sortOrder = repoIds.size,
            sourceKind = "jira",
            repoId = null,
            label = "Jira enrichment",
            detail = if (jiraEnabled) "Queued" else "Disabled",
            status = "QUEUED",
            startedAt = null,
            finishedAt = null,
            progressPercent = null,
        )

        events.forEach { event ->
            jdbcClient.sql(
                """
                INSERT OR REPLACE INTO sync_run_events (
                  event_key, sync_run_id, sort_order, source_kind, repo_id, label, detail,
                  status, started_at, finished_at, progress_percent, created_at, updated_at
                ) VALUES (
                  :eventKey, :syncRunId, :sortOrder, :sourceKind, :repoId, :label, :detail,
                  :status, :startedAt, :finishedAt, :progressPercent, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                )
                """.trimIndent(),
            )
                .param("eventKey", event.eventKey)
                .param("syncRunId", event.syncRunId)
                .param("sortOrder", event.sortOrder)
                .param("sourceKind", event.sourceKind)
                .param("repoId", event.repoId)
                .param("label", event.label)
                .param("detail", event.detail)
                .param("status", event.status)
                .param("startedAt", event.startedAt?.toString())
                .param("finishedAt", event.finishedAt?.toString())
                .param("progressPercent", event.progressPercent)
                .update()
        }
    }

    fun updateRunEvent(
        eventKey: String,
        status: String,
        detail: String?,
        progressPercent: Int?,
        finishedAt: Instant? = null,
    ) {
        jdbcClient.sql(
            """
            UPDATE sync_run_events
            SET status = :status,
                detail = :detail,
                progress_percent = :progressPercent,
                finished_at = :finishedAt,
                updated_at = CURRENT_TIMESTAMP
            WHERE event_key = :eventKey
            """.trimIndent(),
        )
            .param("eventKey", eventKey)
            .param("status", status)
            .param("detail", detail)
            .param("progressPercent", progressPercent)
            .param("finishedAt", finishedAt?.toString())
            .update()
    }

    fun syncRunEvents(syncRunId: Long): List<SyncRunEventRecord> =
        jdbcClient.sql(
            """
            SELECT event_key, sync_run_id, sort_order, source_kind, repo_id, label, detail,
                   status, started_at, finished_at, progress_percent
            FROM sync_run_events
            WHERE sync_run_id = :syncRunId
            ORDER BY sort_order ASC, event_key ASC
            """.trimIndent(),
        )
            .param("syncRunId", syncRunId)
            .query { rs, _ ->
                SyncRunEventRecord(
                    eventKey = rs.getString("event_key"),
                    syncRunId = rs.getLong("sync_run_id"),
                    sortOrder = rs.getInt("sort_order"),
                    sourceKind = rs.getString("source_kind"),
                    repoId = rs.getString("repo_id"),
                    label = rs.getString("label"),
                    detail = rs.getString("detail"),
                    status = rs.getString("status"),
                    startedAt = rs.getString("started_at")?.let(::normalizeTimestamp)?.let(Instant::parse),
                    finishedAt = rs.getString("finished_at")?.let(::normalizeTimestamp)?.let(Instant::parse),
                    progressPercent = rs.getInt("progress_percent").takeUnless { rs.wasNull() },
                )
            }
            .list()

    private fun normalizeTimestamp(value: String): String =
        if (value.contains("T")) value else value.replace(" ", "T") + "Z"
}
