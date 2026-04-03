package com.company.throughput.persistence

import com.company.throughput.config.ResolvedDashboardConfig
import org.springframework.jdbc.core.simple.JdbcClient
import java.time.Instant

class MetadataSeeder(
    private val jdbcClient: JdbcClient,
    private val resolvedDashboardConfig: ResolvedDashboardConfig,
) {
    fun seed() {
        seedRepositories()
        seedAuthors()
    }

    private fun seedRepositories() {
        val now = Instant.now().toString()
        resolvedDashboardConfig.repos.forEach { repo ->
            jdbcClient.sql(
                """
                INSERT INTO repository (repo_id, display_name, clone_url, product_code, enabled, created_at)
                VALUES (:repoId, :displayName, :cloneUrl, :productCode, :enabled, :createdAt)
                ON CONFLICT(repo_id) DO UPDATE SET
                  display_name = excluded.display_name,
                  clone_url = excluded.clone_url,
                  product_code = excluded.product_code,
                  enabled = excluded.enabled
                """.trimIndent(),
            )
                .param("repoId", repo.id)
                .param("displayName", repo.displayName)
                .param("cloneUrl", repo.cloneUrl)
                .param("productCode", repo.productCode)
                .param("enabled", if (repo.enabled) 1 else 0)
                .param("createdAt", now)
                .update()

            jdbcClient.sql(
                """
                INSERT INTO repo_sync_state (repo_id, last_successful_sync_run_id, last_successful_synced_at, last_seen_commit_sha, last_error_message)
                VALUES (:repoId, NULL, NULL, NULL, NULL)
                ON CONFLICT(repo_id) DO NOTHING
                """.trimIndent(),
            )
                .param("repoId", repo.id)
                .update()
        }
    }

    private fun seedAuthors() {
        val now = Instant.now().toString()
        resolvedDashboardConfig.authors.forEach { author ->
            jdbcClient.sql(
                """
                INSERT INTO author (author_id, display_name, cohort, active, created_at)
                VALUES (:authorId, :displayName, :cohort, :active, :createdAt)
                ON CONFLICT(author_id) DO UPDATE SET
                  display_name = excluded.display_name,
                  cohort = excluded.cohort,
                  active = excluded.active
                """.trimIndent(),
            )
                .param("authorId", author.id)
                .param("displayName", author.displayName)
                .param("cohort", author.cohort)
                .param("active", 1)
                .param("createdAt", now)
                .update()

            (author.emails.map { it to null } + author.names.map { null to it })
                .forEach { (email, name) ->
                    jdbcClient.sql(
                        """
                        INSERT INTO author_alias (author_id, alias_email, alias_name)
                        VALUES (:authorId, :aliasEmail, :aliasName)
                        ON CONFLICT(author_id, alias_email, alias_name) DO NOTHING
                        """.trimIndent(),
                    )
                        .param("authorId", author.id)
                        .param("aliasEmail", email)
                        .param("aliasName", name)
                        .update()
                }
        }
    }
}
