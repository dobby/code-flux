package com.company.throughput.persistence

import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

@Service
class AggregationService(
    private val jdbcClient: JdbcClient,
) {
    @Transactional
    fun rebuildImpacted(repoId: String, impactedDays: Set<LocalDate>) {
        if (impactedDays.isEmpty()) {
            return
        }

        val dayStrings = impactedDays.map(LocalDate::toString).sorted()
        val placeholders = dayStrings.indices.joinToString(", ") { ":day$it" }

        val deleteSpec = jdbcClient.sql(
            """
            DELETE FROM daily_fact
            WHERE repo_id = :repoId
              AND day IN ($placeholders)
            """.trimIndent(),
        ).param("repoId", repoId)
        dayStrings.forEachIndexed { index, day -> deleteSpec.param("day$index", day) }
        deleteSpec.update()

        val insertSpec = jdbcClient.sql(
            """
            INSERT INTO daily_fact (
              day, repo_id, author_id, language, category, subtype, product_code,
              lines_added, lines_removed, net_lines, commit_count, file_count
            )
            SELECT
              substr(cf.authored_at, 1, 10) AS day,
              cff.repo_id,
              cf.author_id,
              COALESCE(cff.language, 'unknown') AS language,
              COALESCE(cff.category, 'unknown') AS category,
              COALESCE(cff.subtype, 'unknown') AS subtype,
              COALESCE(cff.product_code, '') AS product_code,
              SUM(cff.lines_added) AS lines_added,
              SUM(cff.lines_removed) AS lines_removed,
              SUM(cff.net_lines) AS net_lines,
              COUNT(DISTINCT cf.commit_sha) AS commit_count,
              COUNT(*) AS file_count
            FROM commit_fact cf
            JOIN commit_file_fact cff
              ON cff.repo_id = cf.repo_id
             AND cff.commit_sha = cf.commit_sha
            WHERE cff.repo_id = :repoId
              AND cff.is_canonical_patch = 1
              AND cf.author_id IS NOT NULL
              AND substr(cf.authored_at, 1, 10) IN ($placeholders)
            GROUP BY
              substr(cf.authored_at, 1, 10),
              cff.repo_id,
              cf.author_id,
              COALESCE(cff.language, 'unknown'),
              COALESCE(cff.category, 'unknown'),
              COALESCE(cff.subtype, 'unknown'),
              COALESCE(cff.product_code, '')
            """.trimIndent(),
        ).param("repoId", repoId)
        dayStrings.forEachIndexed { index, day -> insertSpec.param("day$index", day) }
        insertSpec.update()
    }
}
