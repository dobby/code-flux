package com.company.throughput.config

import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class ConfigurationValidationTests {
    @Test
    fun `missing author aliases fail validation`() {
        assertThrows(IllegalArgumentException::class.java) {
            DashboardConfigFactory().resolvedDashboardConfig(
                appProperties = AppProperties(),
                gitProperties = GitProperties(),
                repoListProperties = RepoListProperties(
                    entries = listOf(
                        RepoConfig(
                            id = "repo",
                            displayName = "Repo",
                            cloneUrl = "https://example/repo.git",
                            branchPatterns = listOf("main"),
                        ),
                    ),
                ),
                authorsProperties = AuthorsProperties(
                    include = listOf(
                        AuthorConfig(
                            id = "eli",
                            displayName = "Eli",
                        ),
                    ),
                ),
                classificationProperties = ClassificationProperties(),
                uiDefaultsProperties = UiDefaultsProperties(),
            )
        }
    }

    @Test
    fun `duplicate repository ids fail validation`() {
        assertThrows(IllegalArgumentException::class.java) {
            DashboardConfigFactory().resolvedDashboardConfig(
                appProperties = AppProperties(),
                gitProperties = GitProperties(),
                repoListProperties = RepoListProperties(
                    entries = listOf(
                        RepoConfig(
                            id = "repo",
                            displayName = "Repo",
                            cloneUrl = "https://example/repo.git",
                            branchPatterns = listOf("main"),
                        ),
                        RepoConfig(
                            id = "repo",
                            displayName = "Repo 2",
                            cloneUrl = "https://example/repo-2.git",
                            branchPatterns = listOf("develop"),
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
        }
    }

    @Test
    fun `unsupported classification category fails validation`() {
        assertThrows(IllegalArgumentException::class.java) {
            DashboardConfigFactory().resolvedDashboardConfig(
                appProperties = AppProperties(),
                gitProperties = GitProperties(),
                repoListProperties = RepoListProperties(
                    entries = listOf(
                        RepoConfig(
                            id = "repo",
                            displayName = "Repo",
                            cloneUrl = "https://example/repo.git",
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
                classificationProperties = ClassificationProperties(
                    rules = listOf(
                        ClassificationRuleConfig(
                            id = "bad-rule",
                            whenPathMatches = listOf("**/*.kt"),
                            category = "nonsense",
                            subtype = "unknown",
                        ),
                    ),
                ),
                uiDefaultsProperties = UiDefaultsProperties(),
            )
        }
    }

    @Test
    fun `invalid git executable fails with clear message`() {
        val exception = assertThrows(IllegalStateException::class.java) {
            DashboardConfigFactory().resolvedDashboardConfig(
                appProperties = AppProperties(),
                gitProperties = GitProperties(executable = "/definitely/missing/git"),
                repoListProperties = RepoListProperties(
                    entries = listOf(
                        RepoConfig(
                            id = "repo",
                            displayName = "Repo",
                            cloneUrl = "https://example/repo.git",
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
        }

        kotlin.test.assertTrue(exception.message!!.contains("git executable"))
    }

    @Test
    fun `unsupported chart library fails validation`() {
        val exception = assertThrows(IllegalArgumentException::class.java) {
            DashboardConfigFactory().resolvedDashboardConfig(
                appProperties = AppProperties(),
                gitProperties = GitProperties(),
                repoListProperties = RepoListProperties(
                    entries = listOf(
                        RepoConfig(
                            id = "repo",
                            displayName = "Repo",
                            cloneUrl = "https://example/repo.git",
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
                uiDefaultsProperties = UiDefaultsProperties(defaultChartLibrary = "plotly"),
            )
        }

        kotlin.test.assertTrue(exception.message!!.contains("defaultChartLibrary"))
    }
}
