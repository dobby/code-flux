package com.company.throughput.git

import com.company.throughput.classify.ClassificationService
import com.company.throughput.config.AppProperties
import com.company.throughput.config.AuthorConfig
import com.company.throughput.config.AuthorsProperties
import com.company.throughput.config.ClassificationProperties
import com.company.throughput.config.ClassificationRuleConfig
import com.company.throughput.config.DashboardConfigFactory
import com.company.throughput.config.GitProperties
import com.company.throughput.config.RepoConfig
import com.company.throughput.config.RepoListProperties
import com.company.throughput.config.UiDefaultsProperties
import com.company.throughput.dedupe.CanonicalPatchService
import com.company.throughput.mirror.MirrorSyncService
import com.company.throughput.persistence.AggregationService
import com.company.throughput.persistence.BootstrapRepository
import com.company.throughput.persistence.MetadataSeeder
import com.company.throughput.persistence.RawFactRepository
import com.company.throughput.persistence.SqlitePragmasInitializer
import com.company.throughput.persistence.SyncStateRepository
import com.company.throughput.sync.SyncOrchestratorService
import com.company.throughput.sync.SyncRuntimeState
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.io.TempDir
import org.springframework.jdbc.core.simple.JdbcClient
import java.nio.file.Files
import java.nio.file.Path
import java.time.Clock
import java.util.UUID
import javax.sql.DataSource
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource

data class GitTestProject(
    val rootDir: Path,
    val remoteRepo: Path,
    val workRepo: Path,
)

data class TestAppContext(
    val dataSource: DataSource,
    val jdbcClient: JdbcClient,
    val syncStateRepository: SyncStateRepository,
    val rawFactRepository: RawFactRepository,
    val aggregationService: AggregationService,
    val syncOrchestratorService: SyncOrchestratorService,
)

object GitFixtureSupport {
    fun createProject(rootDir: Path): GitTestProject {
        Files.createDirectories(rootDir)
        val remoteRepo = rootDir.resolve("remote.git")
        runGit(rootDir, listOf("init", "--bare", remoteRepo.toString()))

        val workRepo = rootDir.resolve("work")
        runGit(rootDir, listOf("clone", remoteRepo.toString(), workRepo.toString()))
        runGit(workRepo, listOf("checkout", "-b", "main"))

        return GitTestProject(rootDir, remoteRepo, workRepo)
    }

    fun commitFile(
        repo: Path,
        relativePath: String,
        content: ByteArray,
        message: String,
        authorName: String,
        authorEmail: String,
        authoredAt: String,
        committedAt: String = authoredAt,
    ) {
        val filePath = repo.resolve(relativePath)
        Files.createDirectories(filePath.parent)
        Files.write(filePath, content)
        runGit(repo, listOf("add", relativePath))
        runGit(
            repo,
            listOf("commit", "-m", message),
            env = mapOf(
                "GIT_AUTHOR_NAME" to authorName,
                "GIT_AUTHOR_EMAIL" to authorEmail,
                "GIT_AUTHOR_DATE" to authoredAt,
                "GIT_COMMITTER_NAME" to authorName,
                "GIT_COMMITTER_EMAIL" to authorEmail,
                "GIT_COMMITTER_DATE" to committedAt,
            ),
        )
    }

    fun push(repo: Path, branch: String) {
        runGit(repo, listOf("push", "-u", "origin", branch))
    }

    fun branch(repo: Path, branchName: String) {
        runGit(repo, listOf("checkout", "-b", branchName))
    }

    fun checkout(repo: Path, branchName: String) {
        runGit(repo, listOf("checkout", branchName))
    }

    fun merge(repo: Path, branchName: String, message: String, committedAt: String) {
        runGit(
            repo,
            listOf("merge", "--no-ff", branchName, "-m", message),
            env = mapOf(
                "GIT_AUTHOR_NAME" to "Eli",
                "GIT_AUTHOR_EMAIL" to "eli@example.com",
                "GIT_AUTHOR_DATE" to committedAt,
                "GIT_COMMITTER_NAME" to "Eli",
                "GIT_COMMITTER_EMAIL" to "eli@example.com",
                "GIT_COMMITTER_DATE" to committedAt,
            ),
        )
    }

    fun cherryPick(repo: Path, commitSha: String, committedAt: String) {
        runGit(
            repo,
            listOf("cherry-pick", commitSha),
            env = mapOf(
                "GIT_COMMITTER_NAME" to "Eli",
                "GIT_COMMITTER_EMAIL" to "eli@example.com",
                "GIT_COMMITTER_DATE" to committedAt,
            ),
        )
    }

    fun currentSha(repo: Path): String =
        runGit(repo, listOf("rev-parse", "HEAD")).trim()

    fun createAppContext(project: GitTestProject, dataDir: Path, repoId: String = "fixture"): TestAppContext {
        val resolvedConfig = DashboardConfigFactory().resolvedDashboardConfig(
            appProperties = AppProperties(
                baseUrl = "http://localhost:8086",
                dataDir = dataDir.toString(),
                openBrowserOnStart = false,
                logLevel = "INFO",
            ),
            gitProperties = GitProperties(executable = "git", mirrorDir = dataDir.resolve("mirrors").toString()),
            repoListProperties = RepoListProperties(
                entries = listOf(
                    RepoConfig(
                        id = repoId,
                        displayName = "Fixture Repo",
                        cloneUrl = project.remoteRepo.toUri().toString(),
                        enabled = true,
                        productCode = "TEST",
                        branchPatterns = listOf("main", "feature/*", "release/*"),
                        excludeBranchPatterns = emptyList(),
                        excludePathGlobs = emptyList(),
                    ),
                ),
            ),
            authorsProperties = AuthorsProperties(
                include = listOf(
                    AuthorConfig(
                        id = "eli",
                        displayName = "Eli",
                        emails = listOf("eli@example.com"),
                        names = listOf("Eli"),
                        cohort = "agentic",
                    ),
                ),
            ),
            classificationProperties = ClassificationProperties(
                languageByExtension = mapOf(
                    "kt" to "kotlin",
                    "java" to "java",
                    "ts" to "typescript",
                    "md" to "markdown",
                    "png" to "binary",
                ),
                rules = listOf(
                    ClassificationRuleConfig(
                        id = "unit-tests",
                        whenPathMatches = listOf("**/src/test/**", "**/*.spec.ts", "**/*.test.ts"),
                        category = "test",
                        subtype = "unit_test",
                    ),
                    ClassificationRuleConfig(
                        id = "docs",
                        whenPathMatches = listOf("**/*.md", "**/docs/**"),
                        category = "docs",
                        subtype = "docs",
                    ),
                    ClassificationRuleConfig(
                        id = "generated",
                        whenPathMatches = listOf("**/generated/**"),
                        category = "generated",
                        subtype = "generated",
                    ),
                ),
            ),
            uiDefaultsProperties = UiDefaultsProperties(),
        )

        val dataSource = createDataSource(dataDir.resolve("app.db"))
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate()
        SqlitePragmasInitializer(dataSource).apply()
        val jdbcClient = JdbcClient.create(dataSource)
        MetadataSeeder(jdbcClient, resolvedConfig).seed()

        val syncStateRepository = SyncStateRepository(jdbcClient)
        val rawFactRepository = RawFactRepository(jdbcClient)
        val aggregationService = AggregationService(jdbcClient)
        val gitCommandRunner = GitCommandRunner(resolvedConfig, Clock.systemUTC())
        val syncOrchestratorService = SyncOrchestratorService(
            resolvedDashboardConfig = resolvedConfig,
            syncStateRepository = syncStateRepository,
            syncRuntimeState = SyncRuntimeState(),
            gitEnvironmentService = GitEnvironmentService(gitCommandRunner),
            mirrorSyncService = MirrorSyncService(resolvedConfig, gitCommandRunner),
            gitHistoryExtractor = GitHistoryExtractor(resolvedConfig, gitCommandRunner),
            authorMatcher = AuthorMatcher(resolvedConfig),
            canonicalPatchService = CanonicalPatchService(),
            classificationService = ClassificationService(resolvedConfig),
            rawFactRepository = rawFactRepository,
            aggregationService = aggregationService,
        )

        return TestAppContext(
            dataSource = dataSource,
            jdbcClient = jdbcClient,
            syncStateRepository = syncStateRepository,
            rawFactRepository = rawFactRepository,
            aggregationService = aggregationService,
            syncOrchestratorService = syncOrchestratorService,
        )
    }

    private fun createDataSource(databasePath: Path): DataSource =
        HikariDataSource(
            HikariConfig().apply {
                jdbcUrl = "jdbc:sqlite:${databasePath.toAbsolutePath()}"
                driverClassName = "org.sqlite.JDBC"
                maximumPoolSize = 1
            },
        )

    private fun runGit(
        workingDirectory: Path,
        args: List<String>,
        env: Map<String, String> = emptyMap(),
    ): String {
        val command = listOf("git") + args
        val process = ProcessBuilder(command)
            .directory(workingDirectory.toFile())
            .apply { environment().putAll(env) }
            .start()

        val stdout = process.inputStream.bufferedReader().readText()
        val stderr = process.errorStream.bufferedReader().readText()
        val exitCode = process.waitFor()
        check(exitCode == 0) {
            "Git command failed (${command.joinToString(" ")}): $stderr"
        }
        return stdout
    }
}
