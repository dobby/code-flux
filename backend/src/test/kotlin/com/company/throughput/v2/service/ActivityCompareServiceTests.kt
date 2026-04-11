package com.company.throughput.v2.service

import com.company.throughput.analytics.AnalyticsFilters
import com.company.throughput.analytics.AnalyticsGroupBy
import com.company.throughput.analytics.AnalyticsMetric
import com.company.throughput.analytics.DateRange
import com.company.throughput.v2.model.ActivityCompareMode
import com.company.throughput.v2.model.ActivityCompareRequest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import java.nio.file.Files
import java.time.LocalDate

@SpringBootTest
class ActivityCompareServiceTests(
    @param:Autowired private val jdbcClient: JdbcClient,
    @param:Autowired private val activityCompareService: ActivityCompareService,
) {
    companion object {
        private val testDataDir = Files.createTempDirectory("code-flux-activity-compare-tests")

        @JvmStatic
        @DynamicPropertySource
        fun registerProperties(registry: DynamicPropertyRegistry) {
            registry.add("app.dataDir") { testDataDir.toString() }
            registry.add("git.mirrorDir") { testDataDir.resolve("mirrors").toString() }
            registry.add("app.openBrowserOnStart") { "false" }
            registry.add("git.auth.httpToken") { "" }
        }
    }

    @BeforeEach
    fun seedData() {
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
              ('2026-02-28', 'marcando-api', 'eli', 'kotlin', 'production', 'source', 'MARCANDO', 40, 5, 35, 1, 1),
              ('2025-03-01', 'marcando-api', 'eli', 'kotlin', 'production', 'source', 'MARCANDO', 20, 5, 15, 1, 1),
              ('2025-03-02', 'marcando-api', 'eli', 'kotlin', 'production', 'source', 'MARCANDO', 10, 0, 10, 1, 1)
            """.trimIndent(),
        ).update()
    }

    @Test
    fun `custom anchor date derives same-length reference range and aligns series onto current dates`() {
        val response = activityCompareService.compare(
            ActivityCompareRequest(
                current = DateRange(
                    from = LocalDate.parse("2026-03-01"),
                    to = LocalDate.parse("2026-03-02"),
                ),
                mode = ActivityCompareMode.CUSTOM_ANCHOR_DATE,
                customAnchorDate = LocalDate.parse("2026-02-28"),
                metric = AnalyticsMetric.COMMIT_COUNT,
                groupBy = AnalyticsGroupBy.NONE,
                filters = baseFilters(),
            ),
        )

        assertEquals(LocalDate.parse("2026-02-27"), response.reference.from)
        assertEquals(LocalDate.parse("2026-02-28"), response.reference.to)
        assertEquals(3, response.current.totals.commitCount)
        assertEquals(2, response.reference.totals.commitCount)
        assertEquals(1, response.delta.absolute)
        assertEquals(LocalDate.parse("2026-03-01"), response.series.alignedReference[0].points[0].day)
        assertEquals(LocalDate.parse("2026-03-02"), response.series.alignedReference[0].points[1].day)
        assertEquals(1, response.series.alignedReference[0].points[0].value)
        assertEquals(1, response.series.alignedReference[0].points[1].value)
    }

    @Test
    fun `custom range rejects spans that do not match the current range length`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            activityCompareService.compare(
                ActivityCompareRequest(
                    current = DateRange(
                        from = LocalDate.parse("2026-03-01"),
                        to = LocalDate.parse("2026-03-02"),
                    ),
                    mode = ActivityCompareMode.CUSTOM_RANGE,
                    customRange = DateRange(
                        from = LocalDate.parse("2026-02-27"),
                        to = LocalDate.parse("2026-02-27"),
                    ),
                    metric = AnalyticsMetric.COMMIT_COUNT,
                    groupBy = AnalyticsGroupBy.NONE,
                    filters = baseFilters(),
                ),
            )
        }

        assertEquals("customRange must span the same number of days as the current range", error.message)
    }

    private fun baseFilters() = AnalyticsFilters(
        authorIds = listOf("eli"),
        repoIds = listOf("marcando-api"),
        languages = listOf("kotlin"),
        categories = listOf("production"),
        productCodes = listOf("MARCANDO"),
    )
}
