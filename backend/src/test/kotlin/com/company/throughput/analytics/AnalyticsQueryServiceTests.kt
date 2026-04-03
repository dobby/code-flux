package com.company.throughput.analytics

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
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
class AnalyticsQueryServiceTests(
    @param:Autowired private val jdbcClient: JdbcClient,
    @param:Autowired private val analyticsQueryService: AnalyticsQueryService,
) {
    companion object {
        private val testDataDir = Files.createTempDirectory("code-flux-analytics-tests")

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
    fun `previous equivalent period comparison computes deltas`() {
        val response = analyticsQueryService.compare(
            CompareRequest(
                current = DateRange(
                    from = LocalDate.parse("2026-03-01"),
                    to = LocalDate.parse("2026-03-02"),
                ),
                comparisonMode = ComparisonMode.PREVIOUS_EQUIVALENT_PERIOD,
                metric = AnalyticsMetric.LINES_ADDED,
                filters = baseFilters(),
            ),
        )

        assertEquals(150, response.current.totals.linesAdded)
        assertEquals(100, response.comparison.totals.linesAdded)
        assertEquals(50, response.delta.metricAbsolute)
        assertEquals(50.0, response.delta.metricPercentage)
        assertEquals(50, response.delta.linesAddedAbsolute)
        assertEquals(50.0, response.delta.linesAddedPercentage)
    }

    @Test
    fun `same period last year comparison handles non lines added metrics`() {
        val response = analyticsQueryService.compare(
            CompareRequest(
                current = DateRange(
                    from = LocalDate.parse("2026-03-01"),
                    to = LocalDate.parse("2026-03-02"),
                ),
                comparisonMode = ComparisonMode.SAME_PERIOD_LAST_YEAR,
                metric = AnalyticsMetric.COMMIT_COUNT,
                filters = baseFilters(),
            ),
        )

        assertEquals(3, response.current.totals.commitCount)
        assertEquals(2, response.comparison.totals.commitCount)
        assertEquals(1, response.delta.metricAbsolute)
        assertEquals(50.0, response.delta.metricPercentage)
        assertNull(response.delta.linesAddedAbsolute)
        assertNull(response.delta.linesAddedPercentage)
    }

    @Test
    fun `custom comparison returns null percentage when baseline is zero`() {
        val response = analyticsQueryService.compare(
            CompareRequest(
                current = DateRange(
                    from = LocalDate.parse("2026-03-01"),
                    to = LocalDate.parse("2026-03-02"),
                ),
                comparisonMode = ComparisonMode.CUSTOM,
                customComparison = DateRange(
                    from = LocalDate.parse("2024-03-01"),
                    to = LocalDate.parse("2024-03-02"),
                ),
                metric = AnalyticsMetric.LINES_ADDED,
                filters = baseFilters(),
            ),
        )

        assertEquals(150, response.delta.metricAbsolute)
        assertNull(response.delta.metricPercentage)
        assertEquals(150, response.delta.linesAddedAbsolute)
        assertNull(response.delta.linesAddedPercentage)
    }

    private fun baseFilters() = AnalyticsFilters(
        authorIds = listOf("eli"),
        repoIds = listOf("marcando-api"),
        languages = listOf("kotlin"),
        categories = listOf("production"),
        productCodes = listOf("MARCANDO"),
        cohorts = listOf("marcando"),
    )
}
