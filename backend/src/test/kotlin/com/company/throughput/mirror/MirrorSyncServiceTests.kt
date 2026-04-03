package com.company.throughput.mirror

import com.company.throughput.git.GitFixtureSupport
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class MirrorSyncServiceTests {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `mirror clone path and update path work`() {
        val project = GitFixtureSupport.createProject(tempDir.resolve("fixture"))
        GitFixtureSupport.commitFile(
            repo = project.workRepo,
            relativePath = "src/main/App.kt",
            content = "fun main() = println(\"hello\")\n".toByteArray(),
            message = "initial",
            authorName = "Eli",
            authorEmail = "eli@example.com",
            authoredAt = "2026-01-01T09:00:00+00:00",
        )
        GitFixtureSupport.push(project.workRepo, "main")

        val context = GitFixtureSupport.createAppContext(project, tempDir.resolve("data"))
        val summary = context.syncOrchestratorService.executeIncrementalSync(context.syncStateRepository.startRun())
        assertTrue(summary.status == "SUCCESS")

        GitFixtureSupport.commitFile(
            repo = project.workRepo,
            relativePath = "src/main/App.kt",
            content = "fun main() = println(\"updated\")\n".toByteArray(),
            message = "update",
            authorName = "Eli",
            authorEmail = "eli@example.com",
            authoredAt = "2026-01-02T09:00:00+00:00",
        )
        GitFixtureSupport.push(project.workRepo, "main")

        val secondSummary = context.syncOrchestratorService.executeIncrementalSync(context.syncStateRepository.startRun())
        assertTrue(secondSummary.status == "SUCCESS")

        val mirrors = tempDir.resolve("data").resolve("mirrors")
        assertTrue(Files.list(mirrors).use { stream -> stream.findAny().isPresent })
    }
}
