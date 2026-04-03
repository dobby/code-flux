package com.company.throughput.persistence

import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Service
import java.time.LocalDate

@Service
class ImpactedDayAggregationService(
    private val jdbcClient: JdbcClient,
) {
    fun rebuildImpactedDays(repoId: String, impactedDays: Set<LocalDate>) {
        if (impactedDays.isEmpty()) {
            return
        }

        impactedDays.sorted().forEach { day ->
            deleteDay(repoId = repoId, day = day)
            insertDay(repoId = repoId, day = day)
        }
    }

    private fun deleteDay(repoId: String, day: LocalDate) {
        jdbcClient.sql(
            """
            DELETE FROM daily_fact
            WHERE repo_id = :repoId
              AND day = :day
            """.trimIndent(),
        )
            .param("repoId", repoId)
            .param("day", day.toString())
            .update()
    }

    private fun insertDay(repoId: String, day: LocalDate) {
        jdbcClient.sql(
            """
            INSERT INTO daily_fact (
              day,
              repo_id,
              author_id,
              language,
              category,
              subtype,
              product_code,
              lines_added,
              lines_removed,
              net_lines,
              commit_count,
              file_count
            )
            SELECT
              DATE(c.authored_at) AS day,
              c.repo_id,
              c.author_id,
              COALESCE(f.language, 'unknown') AS language,
              COALESCE(f.category, 'production') AS category,
              COALESCE(f.subtype, 'source') AS subtype,
              COALESCE(f.product_code, '') AS product_code,
              SUM(f.lines_added) AS lines_added,
              SUM(f.lines_removed) AS lines_removed,
              SUM(f.net_lines) AS net_lines,
              COUNT(DISTINCT f.commit_sha) AS commit_count,
              COUNT(*) AS file_count
            FROM commit_file_fact f
            JOIN commit_fact c
              ON c.repo_id = f.repo_id
             AND c.commit_sha = f.commit_sha
            WHERE c.repo_id = :repoId
              AND DATE(c.authored_at) = :day
              AND c.author_id IS NOT NULL
              AND f.is_canonical_patch = 1
              AND f.is_binary = 0
            GROUP BY
              DATE(c.authored_at),
              c.repo_id,
              c.author_id,
              COALESCE(f.language, 'unknown'),
              COALESCE(f.category, 'production'),
              COALESCE(f.subtype, 'source'),
              COALESCE(f.product_code, '')
            """.trimIndent(),
        )
            .param("repoId", repoId)
            .param("day", day.toString())
            .update()
    }
}
