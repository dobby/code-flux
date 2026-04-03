package com.company.throughput.git

import com.company.throughput.config.AppProperties
import com.company.throughput.config.AuthorConfig
import com.company.throughput.config.AuthorsProperties
import com.company.throughput.config.ClassificationProperties
import com.company.throughput.config.DashboardConfigFactory
import com.company.throughput.config.GitProperties
import com.company.throughput.config.RepoConfig
import com.company.throughput.config.RepoListProperties
import com.company.throughput.config.UiDefaultsProperties
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class GitHistoryExtractorTests {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `metadata and numstat parsing work for text and binary files`() {
        val project = GitFixtureSupport.createProject(tempDir.resolve("extractor"))
        GitFixtureSupport.commitFile(
            repo = project.workRepo,
            relativePath = "src/main/App.kt",
            content = "class App\n".toByteArray(),
            message = "app commit",
            authorName = "Eli",
            authorEmail = "eli@example.com",
            authoredAt = "2026-01-02T09:00:00+00:00",
            committedAt = "2026-01-03T09:00:00+00:00",
        )
        GitFixtureSupport.commitFile(
            repo = project.workRepo,
            relativePath = "assets/logo.png",
            content = byteArrayOf(0, 1, 2, 3),
            message = "binary commit",
            authorName = "Eli",
            authorEmail = "eli@example.com",
            authoredAt = "2026-01-04T09:00:00+00:00",
        )
        GitFixtureSupport.push(project.workRepo, "main")

        val resolvedConfig = DashboardConfigFactory().resolvedDashboardConfig(
            appProperties = AppProperties(dataDir = tempDir.resolve("data").toString()),
            gitProperties = GitProperties(executable = "git", mirrorDir = tempDir.resolve("data/mirrors").toString()),
            repoListProperties = RepoListProperties(
                entries = listOf(
                    RepoConfig(
                        id = "fixture",
                        displayName = "Fixture",
                        cloneUrl = project.remoteRepo.toUri().toString(),
                        branchPatterns = listOf("main"),
                    ),
                ),
            ),
            authorsProperties = AuthorsProperties(
                include = listOf(
                    AuthorConfig(
                        id = "eli",
                        displayName = "Eli",
                        emails = listOf("eli@example.com"),
                    ),
                ),
            ),
            classificationProperties = ClassificationProperties(),
            uiDefaultsProperties = UiDefaultsProperties(),
        )
        val runner = GitCommandRunner(resolvedConfig)
        val mirrorPath = project.remoteRepo
        val extractor = GitHistoryExtractor(resolvedConfig, runner)

        val refs = extractor.resolveRefs(resolvedConfig.repos.single(), mirrorPath)
        val commits = extractor.enumerateCommitShas(mirrorPath, refs)

        assertEquals(2, commits.size)

        val metadata = extractor.readMetadata(mirrorPath, commits.first())
        assertEquals("Eli", metadata.authorName)
        assertEquals("eli@example.com", metadata.authorEmail)
        assertEquals("2026-01-02T09:00Z", metadata.authoredAt.toString())
        assertEquals("2026-01-03T09:00Z", metadata.committedAt.toString())

        val binaryStats = extractor.readFileStats(mirrorPath, commits.last())
        assertEquals(1, binaryStats.size)
        assertTrue(binaryStats.single().isBinary)
        assertEquals(0, binaryStats.single().linesAdded)
        assertEquals(0, binaryStats.single().linesRemoved)
    }
}
