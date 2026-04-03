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

@Repository
class SyncStateRepository(
    private val jdbcClient: JdbcClient,
) {
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

    private fun normalizeTimestamp(value: String): String =
        if (value.contains("T")) value else value.replace(" ", "T") + "Z"
}
