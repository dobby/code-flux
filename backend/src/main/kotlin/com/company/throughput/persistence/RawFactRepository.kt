package com.company.throughput.persistence

import com.company.throughput.classify.ClassifiedFileStat
import com.company.throughput.dedupe.CanonicalCommitCandidate
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.time.OffsetDateTime

data class CommitFactInput(
    val repoId: String,
    val commitSha: String,
    val authorId: String?,
    val authorName: String,
    val authorEmail: String?,
    val authoredAt: OffsetDateTime,
    val committedAt: OffsetDateTime,
    val subject: String,
    val patchId: String?,
    val isMergeCommit: Boolean,
    val isDuplicatePatch: Boolean,
    val canonicalCommitSha: String?,
    val syncRunId: Long,
)

@Repository
class RawFactRepository(
    private val jdbcClient: JdbcClient,
) {
    fun clearThroughputFacts() {
        jdbcClient.sql("DELETE FROM daily_fact").update()
        jdbcClient.sql("DELETE FROM commit_file_fact").update()
        jdbcClient.sql("DELETE FROM commit_fact").update()
    }

    fun commitExists(repoId: String, commitSha: String): Boolean =
        jdbcClient.sql(
            """
            SELECT COUNT(*)
            FROM commit_fact
            WHERE repo_id = :repoId AND commit_sha = :commitSha
            """.trimIndent(),
        )
            .param("repoId", repoId)
            .param("commitSha", commitSha)
            .query(Int::class.java)
            .single() > 0

    fun insertCommit(input: CommitFactInput) {
        jdbcClient.sql(
            """
            INSERT INTO commit_fact (
              repo_id, commit_sha, author_id, author_name, author_email,
              authored_at, committed_at, subject, patch_id,
              is_merge_commit, is_duplicate_patch, canonical_commit_sha,
              sync_run_id, created_at
            ) VALUES (
              :repoId, :commitSha, :authorId, :authorName, :authorEmail,
              :authoredAt, :committedAt, :subject, :patchId,
              :isMergeCommit, :isDuplicatePatch, :canonicalCommitSha,
              :syncRunId, CURRENT_TIMESTAMP
            )
            """.trimIndent(),
        )
            .param("repoId", input.repoId)
            .param("commitSha", input.commitSha)
            .param("authorId", input.authorId)
            .param("authorName", input.authorName)
            .param("authorEmail", input.authorEmail)
            .param("authoredAt", input.authoredAt.toString())
            .param("committedAt", input.committedAt.toString())
            .param("subject", input.subject)
            .param("patchId", input.patchId)
            .param("isMergeCommit", if (input.isMergeCommit) 1 else 0)
            .param("isDuplicatePatch", if (input.isDuplicatePatch) 1 else 0)
            .param("canonicalCommitSha", input.canonicalCommitSha)
            .param("syncRunId", input.syncRunId)
            .update()
    }

    fun insertCommitFiles(
        repoId: String,
        commitSha: String,
        fileStats: List<ClassifiedFileStat>,
        isCanonicalPatch: Boolean,
    ) {
        fileStats.forEach { stat ->
            jdbcClient.sql(
                """
                INSERT INTO commit_file_fact (
                  repo_id, commit_sha, file_path, old_path, extension,
                  language, category, subtype, product_code,
                  lines_added, lines_removed, net_lines,
                  is_binary, is_generated, is_canonical_patch
                ) VALUES (
                  :repoId, :commitSha, :filePath, :oldPath, :extension,
                  :language, :category, :subtype, :productCode,
                  :linesAdded, :linesRemoved, :netLines,
                  :isBinary, :isGenerated, :isCanonicalPatch
                )
                """.trimIndent(),
            )
                .param("repoId", repoId)
                .param("commitSha", commitSha)
                .param("filePath", stat.filePath)
                .param("oldPath", stat.oldPath)
                .param("extension", stat.extension)
                .param("language", stat.language)
                .param("category", stat.category)
                .param("subtype", stat.subtype)
                .param("productCode", stat.productCode)
                .param("linesAdded", stat.linesAdded)
                .param("linesRemoved", stat.linesRemoved)
                .param("netLines", stat.netLines)
                .param("isBinary", if (stat.isBinary) 1 else 0)
                .param("isGenerated", if (stat.isGenerated) 1 else 0)
                .param("isCanonicalPatch", if (isCanonicalPatch) 1 else 0)
                .update()
        }
    }

    fun findPatchCandidates(repoId: String, patchId: String): List<CanonicalCommitCandidate> =
        jdbcClient.sql(
            """
            SELECT commit_sha, authored_at, committed_at
            FROM commit_fact
            WHERE repo_id = :repoId AND patch_id = :patchId
            """.trimIndent(),
        )
            .param("repoId", repoId)
            .param("patchId", patchId)
            .query { rs, _ ->
                CanonicalCommitCandidate(
                    commitSha = rs.getString("commit_sha"),
                    authoredAt = OffsetDateTime.parse(rs.getString("authored_at")),
                    committedAt = OffsetDateTime.parse(rs.getString("committed_at")),
                )
            }
            .list()

    fun applyCanonicalDecision(repoId: String, patchId: String, canonicalCommitSha: String) {
        jdbcClient.sql(
            """
            UPDATE commit_fact
            SET
              is_duplicate_patch = CASE WHEN commit_sha = :canonicalCommitSha THEN 0 ELSE 1 END,
              canonical_commit_sha = :canonicalCommitSha
            WHERE repo_id = :repoId AND patch_id = :patchId
            """.trimIndent(),
        )
            .param("repoId", repoId)
            .param("patchId", patchId)
            .param("canonicalCommitSha", canonicalCommitSha)
            .update()

        jdbcClient.sql(
            """
            UPDATE commit_file_fact
            SET is_canonical_patch = CASE WHEN commit_sha = :canonicalCommitSha THEN 1 ELSE 0 END
            WHERE repo_id = :repoId
              AND commit_sha IN (
                SELECT commit_sha
                FROM commit_fact
                WHERE repo_id = :repoId AND patch_id = :patchId
              )
            """.trimIndent(),
        )
            .param("repoId", repoId)
            .param("patchId", patchId)
            .param("canonicalCommitSha", canonicalCommitSha)
            .update()
    }
}
