package com.company.throughput.web

import org.hamcrest.Matchers.containsString
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
class V2ContractsTests(
    @param:Autowired private val mockMvc: MockMvc,
    @param:Autowired private val jdbcClient: JdbcClient,
) {
    companion object {
        private val testDataDir = Files.createTempDirectory("code-flux-v2-contracts")
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
    fun seedConfig() {
        Files.writeString(
            testConfigPath,
            Files.readString(exampleConfigPath, StandardCharsets.UTF_8),
            StandardCharsets.UTF_8,
        )
        jdbcClient.sql("DELETE FROM sync_run_events").update()
        jdbcClient.sql("DELETE FROM annotations_v2").update()
        jdbcClient.sql("DELETE FROM page_widget_instances").update()
        jdbcClient.sql("DELETE FROM widget_definitions").update()
        jdbcClient.sql("DELETE FROM dashboard_pages").update()
        jdbcClient.sql("DELETE FROM repo_state_snapshot_breakdowns").update()
        jdbcClient.sql("DELETE FROM repo_state_snapshots").update()
        jdbcClient.sql("DELETE FROM file_inventory_current").update()
        jdbcClient.sql("DELETE FROM commit_file_fact").update()
        jdbcClient.sql("DELETE FROM commit_fact").update()
    }

    @Test
    fun `page state persists time range and filters`() {
        mockMvc.perform(
            post("/api/v2/pages")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "title": "State Page",
                      "description": "Testing",
                      "timeRange": { "preset": "last_14_days" },
                      "filters": [
                        { "field": "repo", "op": "in", "values": ["marcando-api"], "locked": true },
                        { "field": "branch", "op": "eq", "values": ["main"], "locked": false }
                      ]
                    }
                    """.trimIndent(),
                ),
        )
            .andExpect(status().isOk)

        val pageId = jdbcClient.sql("SELECT id FROM dashboard_pages WHERE title = :title")
            .param("title", "State Page")
            .query(String::class.java)
            .single()

        mockMvc.perform(
            put("/api/v2/pages/$pageId/state")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "timeRange": {
                        "preset": "last_30_days",
                        "from": "2026-03-01",
                        "to": "2026-03-30"
                      },
                      "filters": [
                        { "field": "repo", "op": "in", "values": ["marcando-api"], "locked": true }
                      ]
                    }
                    """.trimIndent(),
                ),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.timeRange.preset").value("last_30_days"))
            .andExpect(jsonPath("$.filters[0].locked").value(true))

        mockMvc.perform(get("/api/v2/pages/$pageId/state"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.timeRange.from").value("2026-03-01"))
            .andExpect(jsonPath("$.filters[0].field").value("repo"))
    }

    @Test
    fun `widget usage metadata includes affected pages`() {
        mockMvc.perform(
            post("/api/v2/widgets")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "title": "Usage Widget",
                      "description": "Testing",
                      "tags": ["throughput"],
                      "kind": "metric_card",
                      "datasetKey": "throughput_daily",
                      "vizSpec": { "showLegend": false }
                    }
                    """.trimIndent(),
                ),
        )
            .andExpect(status().isOk)

        val widgetId = jdbcClient.sql("SELECT id FROM widget_definitions WHERE title = :title")
            .param("title", "Usage Widget")
            .query(String::class.java)
            .single()

        mockMvc.perform(
            post("/api/v2/pages")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{ "title": "Usage Page" }""".trimIndent()),
        )
            .andExpect(status().isOk)

        val pageId = jdbcClient.sql("SELECT id FROM dashboard_pages WHERE title = :title")
            .param("title", "Usage Page")
            .query(String::class.java)
            .single()

        mockMvc.perform(
            post("/api/v2/pages/$pageId/widgets")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "widgetDefinitionId": "$widgetId",
                      "kind": "metric_card"
                    }
                    """.trimIndent(),
                ),
        )
            .andExpect(status().isOk)

        mockMvc.perform(get("/api/v2/widgets/$widgetId"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.usageCount").value(1))
            .andExpect(jsonPath("$.usedOnPages[0]").value("Usage Page"))
    }

    @Test
    fun `commit detail includes files and linked annotations`() {
        jdbcClient.sql(
            """
            INSERT INTO commit_fact (
              repo_id, commit_sha, author_id, author_name, author_email,
              authored_at, committed_at, subject, patch_id,
              is_merge_commit, is_duplicate_patch, canonical_commit_sha,
              sync_run_id, created_at
            ) VALUES (
              'marcando-api', 'abc123', 'eli', 'Eli', 'eli@example.com',
              '2026-03-15T10:00:00Z', '2026-03-15T10:05:00Z', 'Add explorer detail', NULL,
              0, 0, 'abc123',
              NULL, CURRENT_TIMESTAMP
            )
            """.trimIndent(),
        ).update()
        jdbcClient.sql(
            """
            INSERT INTO commit_file_fact (
              repo_id, commit_sha, file_path, old_path, extension, language, category, subtype,
              product_code, lines_added, lines_removed, net_lines, is_binary, is_generated, is_canonical_patch
            ) VALUES (
              'marcando-api', 'abc123', 'src/main/App.kt', NULL, 'kt', 'kotlin', 'production', 'source',
              'MARCANDO', 12, 3, 9, 0, 0, 1
            )
            """.trimIndent(),
        ).update()
        jdbcClient.sql(
            """
            INSERT INTO annotations_v2 (
              id, target_kind, page_widget_instance_id, scope_date, x_value, y_value,
              annotation_type, name, description, title, body, color, tags_json, commit_refs_json,
              scope_json, created_at, updated_at
            ) VALUES (
              'annotation-abc123', 'widget_point', NULL, NULL, NULL, NULL,
              'feature', 'Explorer detail', 'Matches the commit detail screen', 'Explorer detail', 'Matches the commit detail screen', 'accent',
              '["explorer"]', '[{"repoId":"marcando-api","commitSha":"abc123"}]',
              '{}', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
            )
            """.trimIndent(),
        ).update()

        mockMvc.perform(get("/api/v2/explorer/commit/marcando-api/abc123"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.files[0].filePath").value("src/main/App.kt"))
            .andExpect(jsonPath("$.annotations[0].name").value("Explorer detail"))
    }

    @Test
    fun `open in editor validates repository path and command availability`() {
        mockMvc.perform(
            post("/api/v2/explorer/open-in-editor")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "repoId": "marcando-api",
                      "commitSha": "abc123",
                      "filePaths": ["src/main/App.kt"]
                    }
                    """.trimIndent(),
                ),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.available").value(false))
            .andExpect(jsonPath("$.reason").isString)
    }

    @Test
    fun `codebase structure allows repos without snapshot history`() {
        jdbcClient.sql(
            """
            INSERT INTO file_inventory_current (
              repo_id, ref_name, commit_sha, file_path, language, category, subtype,
              product_code, line_count, is_binary, updated_at
            ) VALUES (
              'repo-no-snapshots', 'main', 'sha-1', 'src/main/App.kt', 'kotlin', 'production', 'source',
              'MARCANDO', 120, 0, CURRENT_TIMESTAMP
            )
            """.trimIndent(),
        ).update()

        mockMvc.perform(
            post("/api/v2/codebase/structure")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "repoId": "repo-no-snapshots",
                      "dateFrom": "2026-03-01",
                      "dateTo": "2026-03-31",
                      "authorIds": [],
                      "languages": [],
                      "categories": [],
                      "productCodes": []
                    }
                    """.trimIndent(),
                ),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.summary.repoId").value("repo-no-snapshots"))
            .andExpect(jsonPath("$.summary.lastSnapshotDate").doesNotExist())
            .andExpect(jsonPath("$.summary.visibleFiles").value(1))
            .andExpect(jsonPath("$.summary.visibleLines").value(120))
    }

    @Test
    fun `open file validates repository path availability`() {
        mockMvc.perform(
            post("/api/v2/explorer/open-file")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "repoId": "marcando-api",
                      "commitSha": "abc123",
                      "filePath": "src/main/App.kt"
                    }
                    """.trimIndent(),
                ),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.available").value(false))
            .andExpect(jsonPath("$.reason").isString)
    }

    @Test
    fun `activity compare returns aligned reference series and summary metadata`() {
        jdbcClient.sql("DELETE FROM daily_fact").update()
        jdbcClient.sql(
            """
            INSERT INTO daily_fact (
              day, repo_id, author_id, language, category, subtype, product_code,
              lines_added, lines_removed, net_lines, commit_count, file_count
            ) VALUES
              ('2026-03-01', 'marcando-api', 'eli', 'kotlin', 'production', 'source', 'MARCANDO', 100, 20, 80, 2, 4),
              ('2026-03-02', 'marcando-api', 'eli', 'kotlin', 'production', 'source', 'MARCANDO', 50, 10, 40, 1, 2),
              ('2026-02-27', 'marcando-api', 'eli', 'kotlin', 'production', 'source', 'MARCANDO', 60, 15, 45, 1, 2),
              ('2026-02-28', 'marcando-api', 'eli', 'kotlin', 'production', 'source', 'MARCANDO', 40, 5, 35, 1, 1)
            """.trimIndent(),
        ).update()

        mockMvc.perform(
            post("/api/v2/activity/compare")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "current": { "from": "2026-03-01", "to": "2026-03-02" },
                      "mode": "custom_anchor_date",
                      "customAnchorDate": "2026-02-28",
                      "metric": "commit_count",
                      "groupBy": "none",
                      "filters": {
                        "authorIds": ["eli"],
                        "repoIds": ["marcando-api"],
                        "languages": ["kotlin"],
                        "categories": ["production"],
                        "productCodes": ["MARCANDO"]
                      }
                    }
                    """.trimIndent(),
                ),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.current.from").value("2026-03-01"))
            .andExpect(jsonPath("$.current.totals.commitCount").value(3))
            .andExpect(jsonPath("$.reference.from").value("2026-02-27"))
            .andExpect(jsonPath("$.reference.to").value("2026-02-28"))
            .andExpect(jsonPath("$.reference.totals.commitCount").value(2))
            .andExpect(jsonPath("$.delta.absolute").value(1))
            .andExpect(jsonPath("$.series.current[0].key").value("total"))
            .andExpect(jsonPath("$.series.alignedReference[0].points[0].day").value("2026-03-01"))
            .andExpect(jsonPath("$.series.alignedReference[0].points[1].day").value("2026-03-02"))
    }

    @Test
    fun `activity compare rejects custom ranges that do not match current span`() {
        mockMvc.perform(
            post("/api/v2/activity/compare")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "current": { "from": "2026-03-01", "to": "2026-03-02" },
                      "mode": "custom_range",
                      "customRange": { "from": "2026-02-27", "to": "2026-02-27" },
                      "metric": "commit_count",
                      "groupBy": "none",
                      "filters": {}
                    }
                    """.trimIndent(),
                ),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.detail", containsString("same number of days")))
    }
}
