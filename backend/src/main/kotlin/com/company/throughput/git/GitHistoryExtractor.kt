package com.company.throughput.git

import com.company.throughput.config.RepoConfig
import com.company.throughput.config.ResolvedDashboardConfig
import org.springframework.stereotype.Service
import java.nio.file.FileSystems
import java.nio.file.Path
import java.time.OffsetDateTime

data class RepoRef(
    val name: String,
    val normalizedName: String,
)

data class GitCommitMetadata(
    val commitSha: String,
    val authoredAt: OffsetDateTime,
    val committedAt: OffsetDateTime,
    val authorName: String,
    val authorEmail: String?,
    val subject: String,
    val parentCommitShas: List<String>,
)

data class GitFileStat(
    val filePath: String,
    val oldPath: String?,
    val linesAdded: Int,
    val linesRemoved: Int,
    val isBinary: Boolean,
)

@Service
class GitHistoryExtractor(
    private val resolvedDashboardConfig: ResolvedDashboardConfig,
    private val gitCommandRunner: GitCommandRunner,
) {
    fun resolveRefs(repo: RepoConfig, mirrorPath: Path): List<RepoRef> {
        val result = gitCommandRunner.runGit(
            args = listOf(
                "-C",
                mirrorPath.toString(),
                "for-each-ref",
                "--format=%(refname:short)",
                "refs/heads",
                "refs/remotes",
            ),
        )

        val includeMatchers = repo.branchPatterns.map { compileGlobMatcher(it) }
        val excludeMatchers = repo.excludeBranchPatterns.map { compileGlobMatcher(it) }

        return result.stdout
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .filterNot { it.endsWith("/HEAD") || it == "HEAD" }
            .map { name -> RepoRef(name = name, normalizedName = normalizeRefName(name)) }
            .filter { ref ->
                includeMatchers.any { matcher -> matcher(ref.name) || matcher(ref.normalizedName) } &&
                    excludeMatchers.none { matcher -> matcher(ref.name) || matcher(ref.normalizedName) }
            }
            .distinctBy { it.name }
            .toList()
    }

    fun enumerateCommitShas(
        mirrorPath: Path,
        refs: List<RepoRef>,
    ): List<String> {
        if (refs.isEmpty()) {
            return emptyList()
        }

        val args = mutableListOf(
            "-C",
            mirrorPath.toString(),
            "rev-list",
            "--topo-order",
            "--reverse",
        )
        if (!resolvedDashboardConfig.git.includeMergeCommits) {
            args += "--no-merges"
        }
        resolvedDashboardConfig.syncWindow.from?.let { args += "--since=${it}T00:00:00" }
        resolvedDashboardConfig.syncWindow.to?.let { args += "--until=${it}T23:59:59" }
        args += refs.map { it.name }

        return gitCommandRunner.runGit(args = args)
            .stdout
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .toList()
    }

    fun readMetadata(mirrorPath: Path, commitSha: String): GitCommitMetadata {
        val result = gitCommandRunner.runGit(
            args = listOf(
                "-C",
                mirrorPath.toString(),
                "show",
                "-s",
                "--format=%H%x00%aI%x00%cI%x00%an%x00%ae%x00%s%x00%P",
                commitSha,
            ),
        )
        val payload = result.stdout.trimEnd('\n')
        val fields = payload.split('\u0000')
        require(fields.size == 7) {
            "Unexpected metadata payload for commit $commitSha: ${result.stdout}"
        }
        return GitCommitMetadata(
            commitSha = fields[0],
            authoredAt = OffsetDateTime.parse(fields[1]),
            committedAt = OffsetDateTime.parse(fields[2]),
            authorName = fields[3],
            authorEmail = fields[4].ifBlank { null },
            subject = fields[5],
            parentCommitShas = fields[6].trim().split(Regex("\\s+")).filter { it.isNotBlank() },
        )
    }

    fun readFileStats(mirrorPath: Path, commitSha: String): List<GitFileStat> =
        gitCommandRunner.runGit(
            args = listOf(
                "-C",
                mirrorPath.toString(),
                "show",
                "--numstat",
                "--format=",
                "--find-renames",
                "--find-copies",
                commitSha,
            ),
        )
            .stdout
            .lineSequence()
            .map { it.trimEnd() }
            .filter { it.isNotBlank() }
            .mapNotNull { parseNumstatLine(it) }
            .toList()

    fun computePatchId(mirrorPath: Path, commitSha: String): String? {
        if (!resolvedDashboardConfig.git.deduplicateByPatchId) {
            return null
        }

        val patch = gitCommandRunner.runGit(
            args = listOf(
                "-C",
                mirrorPath.toString(),
                "show",
                "--format=",
                "--patch",
                commitSha,
            ),
        ).stdout

        if (patch.isBlank()) {
            return null
        }

        val patchIdOutput = gitCommandRunner.runGit(
            args = listOf("patch-id", "--stable"),
            stdin = patch,
        ).stdout.trim()

        if (patchIdOutput.isBlank()) {
            return null
        }

        return patchIdOutput.lineSequence().first().split(Regex("\\s+")).firstOrNull()
    }

    private fun parseNumstatLine(line: String): GitFileStat? {
        val parts = line.split('\t')
        if (parts.size < 3) {
            return null
        }

        val (oldPath, filePath) = parseRenamePath(parts.drop(2).joinToString("\t"))
        val isBinary = parts[0] == "-" || parts[1] == "-"
        val linesAdded = if (isBinary) 0 else parts[0].toIntOrNull() ?: 0
        val linesRemoved = if (isBinary) 0 else parts[1].toIntOrNull() ?: 0

        return GitFileStat(
            filePath = filePath,
            oldPath = oldPath,
            linesAdded = linesAdded,
            linesRemoved = linesRemoved,
            isBinary = isBinary,
        )
    }

    private fun parseRenamePath(pathSpec: String): Pair<String?, String> {
        if (!pathSpec.contains(" => ")) {
            return null to pathSpec
        }

        val braceStart = pathSpec.indexOf('{')
        val braceEnd = if (braceStart >= 0) pathSpec.indexOf('}', braceStart + 1) else -1
        if (braceStart >= 0 && braceEnd > braceStart) {
            val inside = pathSpec.substring(braceStart + 1, braceEnd)
            val renameSplit = inside.split(" => ", limit = 2)
            if (renameSplit.size == 2) {
                val prefix = pathSpec.substring(0, braceStart)
                val suffix = pathSpec.substring(braceEnd + 1)
                val oldPath = prefix + renameSplit[0] + suffix
                val newPath = prefix + renameSplit[1] + suffix
                return oldPath to newPath
            }
        }

        val split = pathSpec.split(" => ", limit = 2)
        if (split.size == 2) {
            return split[0] to split[1]
        }

        return null to pathSpec
    }

    private fun normalizeRefName(refName: String): String {
        val segments = refName.split('/')
        if (segments.size > 1 && segments.first().isNotBlank()) {
            return segments.drop(1).joinToString("/")
        }
        return refName
    }

    private fun compileGlobMatcher(pattern: String): (String) -> Boolean {
        val matcher = FileSystems.getDefault().getPathMatcher("glob:$pattern")
        return { value ->
            val normalizedValue = value.trimStart('/')
            matcher.matches(Path.of(normalizedValue))
        }
    }
}
