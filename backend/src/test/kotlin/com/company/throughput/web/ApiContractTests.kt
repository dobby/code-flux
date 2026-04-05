package com.company.throughput.web

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

@SpringBootTest
@AutoConfigureMockMvc
class ApiContractTests(
    @param:Autowired private val mockMvc: MockMvc,
    @param:Autowired private val jdbcClient: JdbcClient,
) {
    companion object {
        private val testDataDir = Files.createTempDirectory("code-flux-api-tests")
        private val testConfigPath = testDataDir.resolve("config.yaml")
        private val exampleConfigPath = listOf(
            Path.of("config/config.example.yaml"),
            Path.of("../config/config.example.yaml"),
        )
            .map { it.toAbsolutePath().normalize() }
            .first(Files::exists)

        @JvmStatic
        @DynamicPropertySource
        fun registerProperties(registry: DynamicPropertyRegistry) {
            registry.add("app.dataDir") { testDataDir.toString() }
            registry.add("git.mirrorDir") { testDataDir.resolve("mirrors").toString() }
            registry.add("app.openBrowserOnStart") { "false" }
            registry.add("git.auth.httpToken") { "" }
            registry.add("APP_CONFIG_FILE") { testConfigPath.toString() }
        }
    }

    @BeforeEach
    fun seedData() {
        Files.writeString(
            testConfigPath,
            Files.readString(exampleConfigPath, StandardCharsets.UTF_8),
            StandardCharsets.UTF_8,
        )

        jdbcClient.sql("DELETE FROM annotation").update()
        jdbcClient.sql("DELETE FROM daily_fact").update()
        jdbcClient.sql("DELETE FROM commit_file_fact").update()
        jdbcClient.sql("DELETE FROM commit_fact").update()
        jdbcClient.sql("UPDATE repo_sync_state SET last_successful_sync_run_id = NULL, last_successful_synced_at = NULL, last_seen_commit_sha = NULL, last_error_message = NULL").update()
        jdbcClient.sql("DELETE FROM sync_run").update()

        jdbcClient.sql(
            """
            INSERT INTO daily_fact (
              day, repo_id, author_id, language, category, subtype, product_code,
              lines_added, lines_removed, net_lines, commit_count, file_count
            ) VALUES
              ('2026-03-01', 'marcando-api', 'eli', 'kotlin', 'production', 'source', 'MARCANDO', 100, 20, 80, 2, 4),
              ('2026-03-02', 'marcando-api', 'eli', 'kotlin', 'production', 'source', 'MARCANDO', 50, 10, 40, 1, 2),
              ('2025-03-01', 'marcando-api', 'eli', 'kotlin', 'production', 'source', 'MARCANDO', 20, 5, 15, 1, 1)
            """.trimIndent(),
        ).update()
    }

    @Test
    fun `filters endpoint returns available values`() {
        mockMvc.perform(get("/api/filters/options"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.authors[0].id").value("eli"))
            .andExpect(jsonPath("$.repos[0].id").value("marcando-api"))
            .andExpect(jsonPath("$.languages[0]").value("kotlin"))
            .andExpect(jsonPath("$.categories[0]").value("production"))
    }

    @Test
    fun `bootstrap endpoint returns safe config summary and normalized last sync`() {
        jdbcClient.sql(
            """
            UPDATE repo_sync_state
            SET last_successful_synced_at = '2026-03-02 10:15:00'
            WHERE repo_id = 'marcando-api'
            """.trimIndent(),
        ).update()

        mockMvc.perform(get("/api/bootstrap"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.appName").value("Git Throughput Dashboard"))
            .andExpect(jsonPath("$.repos[?(@.id == 'marcando-api')]").isNotEmpty)
            .andExpect(jsonPath("$.authors[?(@.id == 'eli')]").isNotEmpty)
            .andExpect(jsonPath("$.lastSuccessfulSyncAt").value("2026-03-02T10:15:00Z"))
            .andExpect(jsonPath("$.uiDefaults.defaultChartLibrary").value("echarts"))
            .andExpect(jsonPath("$.uiDefaults.defaultDateFrom").value("2025-01-01"))
    }

    @Test
    fun `config builder endpoint returns editable config and yaml`() {
        mockMvc.perform(get("/api/config/builder"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.path").value(testConfigPath.toString()))
            .andExpect(jsonPath("$.config.repos[0].id").value("marcando-api"))
            .andExpect(jsonPath("$.config.authors.include[0].id").value("eli"))
            .andExpect(jsonPath("$.yaml").isString)
            .andExpect(jsonPath("$.restartRequired").value(false))
    }

    @Test
    fun `config builder update persists structured config`() {
        mockMvc.perform(
            put("/api/config/builder")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "app": {
                        "baseUrl": "http://localhost:8086",
                        "dataDir": "/tmp/code-flux",
                        "openBrowserOnStart": false,
                        "logLevel": "INFO"
                      },
                      "git": {
                        "executable": "git",
                        "mirrorDir": "/tmp/code-flux/mirrors",
                        "timeoutSeconds": 120,
                        "includeMergeCommits": false,
                        "deduplicateByPatchId": true,
                        "useAuthoredDate": true,
                        "auth": {
                          "httpUsername": "oauth2",
                          "httpToken": "__CODE_FLUX_REDACTED__"
                        }
                      },
                      "repos": [
                        {
                          "id": "marcando-api",
                          "displayName": "Marcando API V2",
                          "cloneUrl": "https://gitlab.encima.be/shop/marcando-api.git",
                          "enabled": true,
                          "productCode": "MARCANDO",
                          "branchPatterns": ["main", "develop"],
                          "excludeBranchPatterns": ["archive/*"],
                          "excludePathGlobs": ["generated/**"]
                        }
                      ],
                      "authors": {
                        "include": [
                          {
                            "id": "eli",
                            "displayName": "Eli",
                            "emails": ["eli@marcando.be"],
                            "names": ["Eli"],
                            "cohort": "agentic"
                          }
                        ]
                      },
                      "classification": {
                        "languageByExtension": {
                          "kt": "kotlin",
                          "ts": "typescript"
                        },
                        "rules": [
                          {
                            "id": "docs",
                            "whenPathMatches": ["**/*.md"],
                            "category": "docs",
                            "subtype": "docs"
                          }
                        ]
                      },
                      "uiDefaults": {
                        "defaultMetric": "lines_added",
                        "defaultGroupBy": "author",
                        "defaultChartLibrary": "echarts",
                        "defaultIncludeCategories": ["production"],
                        "defaultExcludeCategories": ["docs"],
                        "defaultDateFrom": "2025-01-01",
                        "defaultDateTo": "2025-12-31"
                      },
                      "syncWindow": {
                        "from": "2025-01-01",
                        "to": "2025-12-31"
                      }
                    }
                    """.trimIndent(),
                ),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.config.repos[0].displayName").value("Marcando API V2"))
            .andExpect(jsonPath("$.yaml").value(org.hamcrest.Matchers.containsString("Marcando API V2")))
            .andExpect(jsonPath("$.yaml").value(org.hamcrest.Matchers.containsString("__CODE_FLUX_REDACTED__")))
            .andExpect(jsonPath("$.restartRequired").value(true))
    }

    @Test
    fun `analytics query returns series and totals`() {
        mockMvc.perform(
            post("/api/analytics/query")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "dateRange": { "from": "2026-03-01", "to": "2026-03-02" },
                      "metric": "lines_added",
                      "groupBy": "author",
                      "filters": {
                        "authorIds": ["eli"],
                        "repoIds": ["marcando-api"],
                        "languages": ["kotlin"],
                        "categories": ["production"],
                        "subtypes": [],
                        "productCodes": ["MARCANDO"],
                        "cohorts": ["marcando"]
                      }
                    }
                    """.trimIndent(),
                ),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.series[0].key").value("eli"))
            .andExpect(jsonPath("$.series[0].points[0].day").value("2026-03-01"))
            .andExpect(jsonPath("$.totals.linesAdded").value(150))
            .andExpect(jsonPath("$.totals.commitCount").value(3))
    }

    @Test
    fun `compare endpoint returns delta summary`() {
        mockMvc.perform(
            post("/api/analytics/compare")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "current": { "from": "2026-03-01", "to": "2026-03-02" },
                      "comparisonMode": "same_period_last_year",
                      "customComparison": null,
                      "metric": "lines_added",
                      "filters": {
                        "authorIds": ["eli"],
                        "repoIds": ["marcando-api"],
                        "languages": ["kotlin"],
                        "categories": ["production"],
                        "subtypes": [],
                        "productCodes": ["MARCANDO"],
                        "cohorts": ["marcando"]
                      }
                    }
                    """.trimIndent(),
                ),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.current.totals.linesAdded").value(150))
            .andExpect(jsonPath("$.comparison.totals.linesAdded").value(20))
            .andExpect(jsonPath("$.delta.metricAbsolute").value(130))
            .andExpect(jsonPath("$.delta.linesAddedAbsolute").value(130))
    }

    @Test
    fun `annotation crud works`() {
        val createResult = mockMvc.perform(
            post("/api/annotations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "day": "2026-01-15",
                      "title": "Agentic coding started",
                      "description": "Team switched to structured agentic workflows",
                      "type": "adoption",
                      "colorToken": "accent"
                    }
                    """.trimIndent(),
                ),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.title").value("Agentic coding started"))
            .andReturn()

        val annotationId = Regex("\"annotationId\":(\\d+)").find(createResult.response.contentAsString)!!.groupValues[1]

        mockMvc.perform(get("/api/annotations?from=2026-01-01&to=2026-12-31"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].annotationId").value(annotationId.toLong()))

        mockMvc.perform(
            put("/api/annotations/$annotationId")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "title": "Agentic coding rollout",
                      "description": "Updated label",
                      "type": "process_change",
                      "colorToken": "warning"
                    }
                    """.trimIndent(),
                ),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.title").value("Agentic coding rollout"))

        mockMvc.perform(delete("/api/annotations/$annotationId"))
            .andExpect(status().isNoContent)
    }

    @Test
    fun `sync status endpoint returns repo states`() {
        mockMvc.perform(get("/api/sync/status"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.running").value(false))
            .andExpect(jsonPath("$.repos[?(@.repoId == 'marcando-api')]").isNotEmpty)
    }

    @Test
    fun `invalid sync run request returns problem detail`() {
        mockMvc.perform(
            post("/api/sync/run")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"mode":"full"}"""),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.title").value("Invalid request"))
    }

    @Test
    fun `spa routes forward to index html`() {
        mockMvc.perform(get("/widgets/new"))
            .andExpect(status().isOk)

        mockMvc.perform(get("/pages/page-example"))
            .andExpect(status().isOk)
    }
}
