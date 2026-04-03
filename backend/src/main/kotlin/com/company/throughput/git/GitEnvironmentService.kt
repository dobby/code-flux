package com.company.throughput.git

import org.springframework.stereotype.Service

data class GitEnvironmentStatus(
    val available: Boolean,
    val version: String?,
)

@Service
class GitEnvironmentService(
    private val gitCommandRunner: GitCommandRunner,
) {
    fun verify(): GitEnvironmentStatus {
        val output = gitCommandRunner.runGit(args = listOf("--version")).stdout.trim()
        val version = output.removePrefix("git version ").trim().ifBlank { null }
        return GitEnvironmentStatus(
            available = true,
            version = version,
        )
    }
}
