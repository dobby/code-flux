package com.company.throughput.v2.service

import com.company.throughput.config.ResolvedDashboardConfig
import com.company.throughput.v2.model.CommitDetailResponse
import com.company.throughput.v2.model.CommitFileChangeDto
import com.company.throughput.v2.model.EditorLaunchRequest
import com.company.throughput.v2.model.EditorLaunchResponse
import com.company.throughput.v2.model.FileOpenRequest
import com.company.throughput.v2.model.FileOpenResponse
import com.company.throughput.v2.repo.AnnotationV2Repository
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Service
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeParseException

private data class CommitRow(
    val repoId: String,
    val commitSha: String,
    val authorName: String,
    val authorEmail: String?,
    val authoredAt: Instant,
    val committedAt: Instant,
    val subject: String,
)

private data class RepoPathResolution(
    val repoId: String,
    val commitSha: String,
    val repoPath: Path? = null,
    val reason: String? = null,
) {
    val available: Boolean
        get() = reason == null && repoPath != null
}

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
        val repoResolution = resolveRepoPath(request.repoId, request.commitSha)
        if (!repoResolution.available) {
            return EditorLaunchResponse(
                available = false,
                reason = repoResolution.reason,
                repoId = request.repoId,
                commitSha = request.commitSha,
                repoPath = repoResolution.repoPath?.toString(),
            )
        }
        val repoPath = repoResolution.repoPath ?: error("Resolved repository path missing")

        val editorCommand = resolvedDashboardConfig.app.editorCommand?.trim()?.takeIf { it.isNotBlank() }
            ?: return EditorLaunchResponse(
                available = false,
                reason = "app.editorCommand is not configured",
                repoId = request.repoId,
                commitSha = request.commitSha,
                repoPath = repoPath.toString(),
            )

        val resolvedFilePaths = resolveFilePaths(repoPath, request.filePaths)

        return EditorLaunchResponse(
            available = true,
            repoId = request.repoId,
            commitSha = request.commitSha,
            repoPath = repoPath.toString(),
            editorCommand = editorCommand,
            filePaths = resolvedFilePaths,
        )
    }

    fun openFile(request: FileOpenRequest): FileOpenResponse {
        val repoResolution = resolveRepoPath(request.repoId, request.commitSha)
        if (!repoResolution.available) {
            return FileOpenResponse(
                available = false,
                reason = repoResolution.reason,
                repoId = request.repoId,
                commitSha = request.commitSha,
                filePath = request.filePath,
                repoPath = repoResolution.repoPath?.toString(),
            )
        }
        val repoPath = repoResolution.repoPath ?: error("Resolved repository path missing")

        val normalizedFilePath = request.filePath.trim()
        if (normalizedFilePath.isEmpty()) {
            return FileOpenResponse(
                available = false,
                reason = "filePath is required",
                repoId = request.repoId,
                commitSha = request.commitSha,
                filePath = request.filePath,
                repoPath = repoPath.toString(),
            )
        }

        val resolvedPath = resolveFilePath(repoPath, normalizedFilePath)
        if (resolvedPath == null || !Files.exists(resolvedPath)) {
            return FileOpenResponse(
                available = false,
                reason = "Resolved file path does not exist: $normalizedFilePath",
                repoId = request.repoId,
                commitSha = request.commitSha,
                filePath = request.filePath,
                repoPath = repoPath.toString(),
            )
        }

        return FileOpenResponse(
            available = true,
            repoId = request.repoId,
            commitSha = request.commitSha,
            filePath = normalizedFilePath,
            repoPath = repoPath.toString(),
            resolvedPath = resolvedPath.toString(),
        )
    }

    private fun resolveRepoPath(repoId: String, commitSha: String): RepoPathResolution {
        val repo = resolvedDashboardConfig.repos.firstOrNull { it.id == repoId }
            ?: return RepoPathResolution(
                repoId = repoId,
                commitSha = commitSha,
                reason = "Repository $repoId is not configured",
            )

        val repoLocalPath = repo.localPath?.trim()?.takeIf { it.isNotBlank() }
            ?: return RepoPathResolution(
                repoId = repoId,
                commitSha = commitSha,
                reason = "Repository ${repo.id} does not define a localPath",
            )

        val repoPath = Path.of(repoLocalPath).toAbsolutePath().normalize()
        if (!Files.exists(repoPath) || !Files.isDirectory(repoPath)) {
            return RepoPathResolution(
                repoId = repoId,
                commitSha = commitSha,
                repoPath = repoPath,
                reason = "Repository path does not exist: $repoPath",
            )
        }

        return RepoPathResolution(
            repoId = repoId,
            commitSha = commitSha,
            repoPath = repoPath,
        )
    }

    private fun resolveFilePaths(repoPath: Path, filePaths: List<String>): List<String> =
        if (filePaths.isEmpty()) {
            listOf(repoPath.toString())
        } else {
            filePaths.map { candidate ->
                resolveFilePath(repoPath, candidate)?.toString()
                    ?: throw IllegalArgumentException("EDITOR_PATH_OUTSIDE_REPO: $candidate")
            }
        }

    private fun resolveFilePath(repoPath: Path, candidate: String): Path? {
        val normalizedCandidate = candidate.trim().takeIf { it.isNotEmpty() } ?: return null
        val resolved = repoPath.resolve(normalizedCandidate).normalize()
        return resolved.takeIf { it.startsWith(repoPath) }
    }

    private fun parseTimestamp(value: String): Instant {
        val normalized = value.trim()
        return try {
            Instant.parse(normalized)
        } catch (_: DateTimeParseException) {
            try {
                OffsetDateTime.parse(normalized).toInstant()
            } catch (_: DateTimeParseException) {
                LocalDateTime.parse(normalized.replace(" ", "T")).atOffset(ZoneOffset.UTC).toInstant()
            }
        }
    }
}
