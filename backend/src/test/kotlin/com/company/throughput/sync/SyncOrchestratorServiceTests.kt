package com.company.throughput.sync

import com.company.throughput.git.GitFixtureSupport
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class SyncOrchestratorServiceTests {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `same sha across multiple refs counts once`() {
        val project = GitFixtureSupport.createProject(tempDir.resolve("same-sha"))
        GitFixtureSupport.commitFile(
            repo = project.workRepo,
            relativePath = "src/main/App.kt",
            content = "fun main() = println(\"hello\")\n".toByteArray(),
            message = "initial",
            authorName = "Eli",
            authorEmail = "eli@example.com",
            authoredAt = "2026-01-01T09:00:00+00:00",
        )
        val sha = GitFixtureSupport.currentSha(project.workRepo)
        GitFixtureSupport.push(project.workRepo, "main")
        GitFixtureSupport.branch(project.workRepo, "release/same")
        GitFixtureSupport.push(project.workRepo, "release/same")

        val context = GitFixtureSupport.createAppContext(project, tempDir.resolve("data"))
        context.syncOrchestratorService.executeIncrementalSync(context.syncStateRepository.startRun())

        val commitCount = context.jdbcClient.sql("SELECT COUNT(*) FROM commit_fact").query(Int::class.java).single()
        val dailyCommitCount = context.jdbcClient.sql("SELECT commit_count FROM daily_fact").query(Int::class.java).single()
        assertEquals(1, commitCount)
        assertEquals(1, dailyCommitCount)
        assertTrue(sha.isNotBlank())
    }

    @Test
    fun `merge into main does not double count original branch work and uses authored day`() {
        val project = GitFixtureSupport.createProject(tempDir.resolve("merge"))
        GitFixtureSupport.commitFile(
            repo = project.workRepo,
            relativePath = "src/main/Seed.kt",
            content = "class Seed\n".toByteArray(),
            message = "seed",
            authorName = "Eli",
            authorEmail = "eli@example.com",
            authoredAt = "2026-01-01T08:00:00+00:00",
        )
        GitFixtureSupport.push(project.workRepo, "main")

        GitFixtureSupport.branch(project.workRepo, "feature/a")
        GitFixtureSupport.commitFile(
            repo = project.workRepo,
            relativePath = "src/main/Feature.kt",
            content = "class Feature\n".toByteArray(),
            message = "feature",
            authorName = "Eli",
            authorEmail = "eli@example.com",
            authoredAt = "2026-01-02T09:00:00+00:00",
            committedAt = "2026-01-04T09:00:00+00:00",
        )
        GitFixtureSupport.push(project.workRepo, "feature/a")

        GitFixtureSupport.checkout(project.workRepo, "main")
        GitFixtureSupport.merge(project.workRepo, "feature/a", "merge feature", "2026-01-05T10:00:00+00:00")
        GitFixtureSupport.push(project.workRepo, "main")

        val context = GitFixtureSupport.createAppContext(project, tempDir.resolve("data"))
        context.syncOrchestratorService.executeIncrementalSync(context.syncStateRepository.startRun())

        val rows = context.jdbcClient.sql(
            """
            SELECT day, SUM(lines_added) AS lines_added, SUM(commit_count) AS commit_count
            FROM daily_fact
            GROUP BY day
            ORDER BY day
            """.trimIndent(),
        ).query { rs, _ -> Triple(rs.getString("day"), rs.getInt("lines_added"), rs.getInt("commit_count")) }.list()

        assertTrue(rows.any { it.first == "2026-01-02" && it.third == 1 })
        assertTrue(rows.none { it.first == "2026-01-05" && it.third > 0 })
    }

    @Test
    fun `cherry picked duplicate patch counts once`() {
        val project = GitFixtureSupport.createProject(tempDir.resolve("cherry-pick"))
        GitFixtureSupport.commitFile(
            repo = project.workRepo,
            relativePath = "src/main/Seed.kt",
            content = "class Seed\n".toByteArray(),
            message = "seed",
            authorName = "Eli",
            authorEmail = "eli@example.com",
            authoredAt = "2026-01-01T08:00:00+00:00",
        )
        GitFixtureSupport.push(project.workRepo, "main")

        GitFixtureSupport.branch(project.workRepo, "feature/a")
        GitFixtureSupport.commitFile(
            repo = project.workRepo,
            relativePath = "src/main/Feature.kt",
            content = "class Feature\n".toByteArray(),
            message = "feature",
            authorName = "Eli",
            authorEmail = "eli@example.com",
            authoredAt = "2026-01-02T09:00:00+00:00",
        )
        val originalSha = GitFixtureSupport.currentSha(project.workRepo)
        GitFixtureSupport.push(project.workRepo, "feature/a")

        GitFixtureSupport.checkout(project.workRepo, "main")
        GitFixtureSupport.branch(project.workRepo, "feature/b")
        GitFixtureSupport.cherryPick(project.workRepo, originalSha, "2026-01-03T10:00:00+00:00")
        GitFixtureSupport.push(project.workRepo, "feature/b")

        val context = GitFixtureSupport.createAppContext(project, tempDir.resolve("data"))
        context.syncOrchestratorService.executeIncrementalSync(context.syncStateRepository.startRun())

        val duplicates = context.jdbcClient.sql(
            "SELECT COUNT(*) FROM commit_fact WHERE is_duplicate_patch = 1",
        ).query(Int::class.java).single()
        val aggregateCommits = context.jdbcClient.sql(
            "SELECT SUM(commit_count) FROM daily_fact",
        ).query(Int::class.java).single()

        assertEquals(1, duplicates)
        assertEquals(3, context.jdbcClient.sql("SELECT COUNT(*) FROM commit_fact").query(Int::class.java).single())
        assertEquals(2, aggregateCommits)
    }

    @Test
    fun `binary files are stored safely and classification rules feed aggregates`() {
        val project = GitFixtureSupport.createProject(tempDir.resolve("classification"))
        GitFixtureSupport.commitFile(
            repo = project.workRepo,
            relativePath = "src/main/App.kt",
            content = "class App\n".toByteArray(),
            message = "production",
            authorName = "Eli",
            authorEmail = "eli@example.com",
            authoredAt = "2026-01-01T08:00:00+00:00",
        )
        GitFixtureSupport.commitFile(
            repo = project.workRepo,
            relativePath = "src/test/AppTest.kt",
            content = "class AppTest\n".toByteArray(),
            message = "test",
            authorName = "Eli",
            authorEmail = "eli@example.com",
            authoredAt = "2026-01-02T08:00:00+00:00",
        )
        GitFixtureSupport.commitFile(
            repo = project.workRepo,
            relativePath = "docs/readme.md",
            content = "# docs\n".toByteArray(),
            message = "docs",
            authorName = "Eli",
            authorEmail = "eli@example.com",
            authoredAt = "2026-01-03T08:00:00+00:00",
        )
        GitFixtureSupport.commitFile(
            repo = project.workRepo,
            relativePath = "generated/output.kt",
            content = "class Generated\n".toByteArray(),
            message = "generated",
            authorName = "Eli",
            authorEmail = "eli@example.com",
            authoredAt = "2026-01-04T08:00:00+00:00",
        )
        GitFixtureSupport.commitFile(
            repo = project.workRepo,
            relativePath = "assets/logo.png",
            content = byteArrayOf(0, 1, 2, 3, 4, 5),
            message = "binary",
            authorName = "Eli",
            authorEmail = "eli@example.com",
            authoredAt = "2026-01-05T08:00:00+00:00",
        )
        GitFixtureSupport.push(project.workRepo, "main")

        val context = GitFixtureSupport.createAppContext(project, tempDir.resolve("data"))
        context.syncOrchestratorService.executeIncrementalSync(context.syncStateRepository.startRun())

        val binaryRows = context.jdbcClient.sql(
            "SELECT COUNT(*) FROM commit_file_fact WHERE is_binary = 1 AND lines_added = 0 AND lines_removed = 0",
        ).query(Int::class.java).single()
        val categories = context.jdbcClient.sql(
            "SELECT category FROM commit_file_fact ORDER BY category",
        ).query(String::class.java).list()

        assertEquals(1, binaryRows)
        assertTrue(categories.containsAll(listOf("production", "test", "docs", "generated", "production")))
    }
}
