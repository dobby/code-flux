package com.company.throughput.persistence

import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.time.Instant

data class BootstrapRepoRecord(
    val id: String,
    val displayName: String,
    val productCode: String?,
    val enabled: Boolean,
)

data class BootstrapAuthorRecord(
    val id: String,
    val displayName: String,
    val cohort: String?,
)

@Repository
class BootstrapRepository(
    private val jdbcClient: JdbcClient,
) {
    fun listRepos(): List<BootstrapRepoRecord> =
        jdbcClient.sql(
            """
            SELECT repo_id, display_name, product_code, enabled
            FROM repository
            ORDER BY display_name
            """.trimIndent(),
        )
            .query { rs, _ ->
                BootstrapRepoRecord(
                    id = rs.getString("repo_id"),
                    displayName = rs.getString("display_name"),
                    productCode = rs.getString("product_code"),
                    enabled = rs.getInt("enabled") == 1,
                )
            }
            .list()

    fun listAuthors(): List<BootstrapAuthorRecord> =
        jdbcClient.sql(
            """
            SELECT author_id, display_name, cohort
            FROM author
            WHERE active = 1
            ORDER BY display_name
            """.trimIndent(),
        )
            .query { rs, _ ->
                BootstrapAuthorRecord(
                    id = rs.getString("author_id"),
                    displayName = rs.getString("display_name"),
                    cohort = rs.getString("cohort"),
                )
            }
            .list()

    fun lastSuccessfulSyncAt(): Instant? =
        jdbcClient.sql(
            """
            SELECT MAX(last_successful_synced_at) AS last_successful_synced_at
            FROM repo_sync_state
            """.trimIndent(),
        )
            .query { rs, _ -> rs.getString("last_successful_synced_at") }
            .list()
            .firstOrNull()
            ?.let(::normalizeTimestamp)
            ?.let(Instant::parse)

    private fun normalizeTimestamp(value: String): String =
        if (value.contains("T")) value else value.replace(" ", "T") + "Z"
}
