package com.company.throughput.v2.service

import com.company.throughput.config.ResolvedDashboardConfig
import com.company.throughput.v2.model.CommitDetailResponse
import com.company.throughput.v2.model.CommitFileChangeDto
import com.company.throughput.v2.model.EditorLaunchRequest
import com.company.throughput.v2.model.EditorLaunchResponse
import com.company.throughput.v2.repo.AnnotationV2Repository
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Service
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant

private data class CommitRow(
    val repoId: String,
    val commitSha: String,
    val authorName: String,
    val authorEmail: String?,
    val authoredAt: Instant,
    val committedAt: Instant,
    val subject: String,
)

@Service
class ExplorerService(
    private val jdbcClient: JdbcClient,
    private val annotationRepository: AnnotationV2Repository,
    private val resolvedDashboardConfig: ResolvedDashboardConfig,
) {
    fun commitDetail(repoId: String, commitSha: String): CommitDetailResponse {
        val commit = jdbcClient.sql(
            """
            SELECT repo_id, commit_sha, author_name, author_email, authored_at, committed_at, subject
            FROM commit_fact
            WHERE repo_id = :repoId AND commit_sha = :commitSha
            """.trimIndent(),
        )
            .param("repoId", repoId)
            .param("commitSha", commitSha)
            .query { rs, _ -> CommitRow(
                repoId = rs.getString("repo_id"),
                commitSha = rs.getString("commit_sha"),
                authorName = rs.getString("author_name"),
                authorEmail = rs.getString("author_email"),
                authoredAt = parseTimestamp(rs.getString("authored_at")),
                committedAt = parseTimestamp(rs.getString("committed_at")),
                subject = rs.getString("subject"),
            ) }
            .optional()
            .orElse(null)
            ?: throw IllegalArgumentException("COMMIT_NOT_FOUND: $repoId/$commitSha")

        val files = jdbcClient.sql(
            """
            SELECT file_path, old_path, language, category, subtype, lines_added, lines_removed, is_binary
            FROM commit_file_fact
            WHERE repo_id = :repoId AND commit_sha = :commitSha
            ORDER BY file_path ASC
            """.trimIndent(),
        )
            .param("repoId", repoId)
            .param("commitSha", commitSha)
            .query { rs, _ ->
                CommitFileChangeDto(
                    filePath = rs.getString("file_path"),
                    oldPath = rs.getString("old_path"),
                    language = rs.getString("language"),
                    category = rs.getString("category"),
                    subtype = rs.getString("subtype"),
                    linesAdded = rs.getInt("lines_added"),
                    linesRemoved = rs.getInt("lines_removed"),
                    isBinary = rs.getInt("is_binary") == 1,
                )
            }
            .list()

        val annotations = annotationRepository.list()
            .filter { annotation -> annotation.commitRefs.any { it.repoId == repoId && it.commitSha == commitSha } }

        val totalLinesAdded = files.sumOf(CommitFileChangeDto::linesAdded)
        val totalLinesRemoved = files.sumOf(CommitFileChangeDto::linesRemoved)

        return CommitDetailResponse(
            repoId = commit.repoId,
            commitSha = commit.commitSha,
            authorName = commit.authorName,
            authorEmail = commit.authorEmail,
            authoredAt = commit.authoredAt,
            committedAt = commit.committedAt,
            subject = commit.subject,
            parentCommitShas = emptyList(),
            linesAdded = totalLinesAdded,
            linesRemoved = totalLinesRemoved,
            files = files,
            annotations = annotations,
        )
    }

    fun launchEditor(request: EditorLaunchRequest): EditorLaunchResponse {
        val repo = resolvedDashboardConfig.repos.firstOrNull { it.id == request.repoId }
            ?: return EditorLaunchResponse(
                available = false,
                reason = "Repository ${request.repoId} is not configured",
                repoId = request.repoId,
                commitSha = request.commitSha,
            )

        val repoLocalPath = repo.localPath?.trim()?.takeIf { it.isNotBlank() }
            ?: return EditorLaunchResponse(
                available = false,
                reason = "Repository ${repo.id} does not define a localPath",
                repoId = request.repoId,
                commitSha = request.commitSha,
            )

        val repoPath = Path.of(repoLocalPath).toAbsolutePath().normalize()
        if (!Files.exists(repoPath) || !Files.isDirectory(repoPath)) {
            return EditorLaunchResponse(
                available = false,
                reason = "Repository path does not exist: $repoPath",
                repoId = request.repoId,
                commitSha = request.commitSha,
                repoPath = repoPath.toString(),
            )
        }

        val editorCommand = resolvedDashboardConfig.app.editorCommand?.trim()?.takeIf { it.isNotBlank() }
            ?: return EditorLaunchResponse(
                available = false,
                reason = "app.editorCommand is not configured",
                repoId = request.repoId,
                commitSha = request.commitSha,
                repoPath = repoPath.toString(),
            )

        val resolvedFilePaths = if (request.filePaths.isEmpty()) {
            listOf(repoPath.toString())
        } else {
            request.filePaths.map { candidate ->
                val resolved = repoPath.resolve(candidate).normalize()
                require(resolved.startsWith(repoPath)) { "EDITOR_PATH_OUTSIDE_REPO: $candidate" }
                resolved.toString()
            }
        }

        return EditorLaunchResponse(
            available = true,
            repoId = request.repoId,
            commitSha = request.commitSha,
            repoPath = repoPath.toString(),
            editorCommand = editorCommand,
            filePaths = resolvedFilePaths,
        )
    }

    private fun parseTimestamp(value: String): Instant =
        if (value.contains("T")) {
            Instant.parse(if (value.endsWith("Z")) value else "${value}Z")
        } else {
            Instant.parse(value.replace(" ", "T") + "Z")
        }
}
