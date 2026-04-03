package com.company.throughput.mirror

import com.company.throughput.config.RepoConfig
import com.company.throughput.config.ResolvedDashboardConfig
import com.company.throughput.git.GitCommandRunner
import org.springframework.stereotype.Service
import java.nio.file.Files
import java.nio.file.Path

@Service
class MirrorSyncService(
    private val resolvedDashboardConfig: ResolvedDashboardConfig,
    private val gitCommandRunner: GitCommandRunner,
) {
    fun mirrorPathFor(repoId: String): Path =
        resolvedDashboardConfig.git.mirrorDir.resolve("${repoId.sanitizeForPath()}.git")

    fun ensureMirror(repo: RepoConfig): Path {
        Files.createDirectories(resolvedDashboardConfig.git.mirrorDir)
        val mirrorPath = mirrorPathFor(repo.id)

        if (Files.exists(mirrorPath.resolve("HEAD"))) {
            gitCommandRunner.runGit(
                args = listOf("-C", mirrorPath.toString(), "remote", "update", "--prune"),
            )
            return mirrorPath
        }

        gitCommandRunner.runGit(
            args = listOf("clone", "--mirror", repo.cloneUrl, mirrorPath.toString()),
        )
        return mirrorPath
    }

    private fun String.sanitizeForPath(): String = replace(Regex("[^A-Za-z0-9._-]"), "_")
}
