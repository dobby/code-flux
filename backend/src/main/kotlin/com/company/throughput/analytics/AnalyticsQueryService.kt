package com.company.throughput.analytics

import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Service
import java.time.LocalDate

private data class QueryParts(
    val sql: String,
    val params: Map<String, Any?>,
)

private data class SeriesRow(
    val day: LocalDate,
    val key: String,
    val label: String,
    val value: Long,
)

@Service
class AnalyticsQueryService(
    private val jdbcClient: JdbcClient,
) {
    fun query(request: AnalyticsQueryRequest): AnalyticsQueryResponse {
        val from = request.dateRange.from
        val to = request.dateRange.to
        val queryParts = buildFilterClause(from, to, request.filters)
        val metricColumn = metricColumn(request.metric)
        val keyExpression = keyExpression(request.groupBy)
        val labelExpression = labelExpression(request.groupBy)

        val seriesSql =
            """
            SELECT
              d.day AS day,
              $keyExpression AS series_key,
              $labelExpression AS series_label,
              SUM($metricColumn) AS metric_value
            FROM daily_fact d
            LEFT JOIN author a ON a.author_id = d.author_id
            LEFT JOIN repository r ON r.repo_id = d.repo_id
            ${queryParts.sql}
            GROUP BY d.day, series_key, series_label
            ORDER BY d.day ASC
            """.trimIndent()

        val seriesRows = applyParams(jdbcClient.sql(seriesSql), queryParts.params)
            .query { rs, _ ->
                SeriesRow(
                    day = LocalDate.parse(rs.getString("day")),
                    key = rs.getString("series_key"),
                    label = rs.getString("series_label"),
                    value = rs.getLong("metric_value"),
                )
            }
            .list()

        val totalsSql =
            """
            SELECT
              COALESCE(SUM(d.lines_added), 0) AS lines_added,
              COALESCE(SUM(d.lines_removed), 0) AS lines_removed,
              COALESCE(SUM(d.net_lines), 0) AS net_lines,
              COALESCE(SUM(d.commit_count), 0) AS commit_count,
              COALESCE(SUM(d.file_count), 0) AS file_count
            FROM daily_fact d
            LEFT JOIN author a ON a.author_id = d.author_id
            LEFT JOIN repository r ON r.repo_id = d.repo_id
            ${queryParts.sql}
            """.trimIndent()

        val totals = applyParams(jdbcClient.sql(totalsSql), queryParts.params)
            .query { rs, _ ->
                AnalyticsTotals(
                    linesAdded = rs.getLong("lines_added"),
                    linesRemoved = rs.getLong("lines_removed"),
                    netLines = rs.getLong("net_lines"),
                    commitCount = rs.getLong("commit_count"),
                    fileCount = rs.getLong("file_count"),
                )
            }
            .single()

        val totalsBySeries = seriesRows.groupBy { it.key }.mapValues { (_, rows) -> rows.sumOf { it.value } }
        val labelsBySeries = seriesRows.associate { it.key to it.label }
        val groupedSeries = seriesRows.groupBy { it.key }
            .entries
            .sortedWith(
                compareByDescending<Map.Entry<String, List<SeriesRow>>> { totalsBySeries[it.key] ?: 0L }
                    .thenBy { labelsBySeries[it.key] ?: it.key },
            )
            .map { (key, rows) ->
                AnalyticsSeries(
                    key = key,
                    label = labelsBySeries[key] ?: key,
                    points = rows.sortedBy { it.day }.map { AnalyticsSeriesPoint(it.day, it.value) },
                )
            }

        return AnalyticsQueryResponse(series = groupedSeries, totals = totals)
    }

    fun compare(request: CompareRequest): CompareResponse {
        val comparisonRange = when (request.comparisonMode) {
            ComparisonMode.PREVIOUS_EQUIVALENT_PERIOD -> {
                val days = request.current.to.toEpochDay() - request.current.from.toEpochDay() + 1
                DateRange(
                    from = request.current.from.minusDays(days),
                    to = request.current.from.minusDays(1),
                )
            }

            ComparisonMode.SAME_PERIOD_LAST_YEAR -> DateRange(
                from = request.current.from.minusYears(1),
                to = request.current.to.minusYears(1),
            )

            ComparisonMode.CUSTOM -> request.customComparison ?: throw IllegalArgumentException("customComparison is required when comparisonMode is custom")
        }

        val currentTotals = query(
            AnalyticsQueryRequest(
                dateRange = request.current,
                metric = request.metric,
                groupBy = AnalyticsGroupBy.NONE,
                filters = request.filters,
            ),
        ).totals
        val comparisonTotals = query(
            AnalyticsQueryRequest(
                dateRange = comparisonRange,
                metric = request.metric,
                groupBy = AnalyticsGroupBy.NONE,
                filters = request.filters,
            ),
        ).totals

        val currentMetricValue = metricValue(request.metric, currentTotals)
        val comparisonMetricValue = metricValue(request.metric, comparisonTotals)
        val absolute = currentMetricValue - comparisonMetricValue
        val percentage = if (comparisonMetricValue == 0L) null else (absolute.toDouble() / comparisonMetricValue.toDouble()) * 100.0

        return CompareResponse(
            current = ComparePeriodResponse(request.current.from, request.current.to, currentTotals),
            comparison = ComparePeriodResponse(comparisonRange.from, comparisonRange.to, comparisonTotals),
            delta = CompareDeltaResponse(
                metricAbsolute = absolute,
                metricPercentage = percentage,
                linesAddedAbsolute = absolute.takeIf { request.metric == AnalyticsMetric.LINES_ADDED },
                linesAddedPercentage = percentage.takeIf { request.metric == AnalyticsMetric.LINES_ADDED },
            ),
        )
    }

    fun filterOptions(): FilterOptionsResponse {
        val authors = jdbcClient.sql(
            """
            SELECT DISTINCT a.author_id, a.display_name, a.cohort
            FROM daily_fact d
            JOIN author a ON a.author_id = d.author_id
            ORDER BY a.display_name
            """.trimIndent(),
        ).query { rs, _ ->
            FilterAuthorOption(
                id = rs.getString("author_id"),
                displayName = rs.getString("display_name"),
                cohort = rs.getString("cohort"),
            )
        }.list()

        val repos = jdbcClient.sql(
            """
            SELECT DISTINCT r.repo_id, r.display_name
            FROM daily_fact d
            JOIN repository r ON r.repo_id = d.repo_id
            ORDER BY r.display_name
            """.trimIndent(),
        ).query { rs, _ ->
            FilterRepoOption(
                id = rs.getString("repo_id"),
                displayName = rs.getString("display_name"),
            )
        }.list()

        fun distinct(column: String): List<String> =
            jdbcClient.sql(
                """
                SELECT DISTINCT $column AS value
                FROM daily_fact
                WHERE $column IS NOT NULL AND $column != ''
                ORDER BY value
                """.trimIndent(),
            )
                .query(String::class.java)
                .list()

        return FilterOptionsResponse(
            authors = authors,
            repos = repos,
            languages = distinct("language"),
            categories = distinct("category"),
            subtypes = distinct("subtype"),
            productCodes = distinct("product_code"),
        )
    }

    private fun metricColumn(metric: AnalyticsMetric): String = when (metric) {
        AnalyticsMetric.LINES_ADDED -> "d.lines_added"
        AnalyticsMetric.LINES_REMOVED -> "d.lines_removed"
        AnalyticsMetric.NET_LINES -> "d.net_lines"
        AnalyticsMetric.COMMIT_COUNT -> "d.commit_count"
        AnalyticsMetric.FILE_COUNT -> "d.file_count"
    }

    private fun metricValue(metric: AnalyticsMetric, totals: AnalyticsTotals): Long = when (metric) {
        AnalyticsMetric.LINES_ADDED -> totals.linesAdded
        AnalyticsMetric.LINES_REMOVED -> totals.linesRemoved
        AnalyticsMetric.NET_LINES -> totals.netLines
        AnalyticsMetric.COMMIT_COUNT -> totals.commitCount
        AnalyticsMetric.FILE_COUNT -> totals.fileCount
    }

    private fun keyExpression(groupBy: AnalyticsGroupBy): String = when (groupBy) {
        AnalyticsGroupBy.NONE -> "'total'"
        AnalyticsGroupBy.AUTHOR -> "d.author_id"
        AnalyticsGroupBy.REPO -> "d.repo_id"
        AnalyticsGroupBy.LANGUAGE -> "d.language"
        AnalyticsGroupBy.CATEGORY -> "d.category"
        AnalyticsGroupBy.SUBTYPE -> "d.subtype"
        AnalyticsGroupBy.PRODUCT_CODE -> "COALESCE(NULLIF(d.product_code, ''), 'unknown')"
        AnalyticsGroupBy.COHORT -> "COALESCE(NULLIF(a.cohort, ''), 'unknown')"
    }

    private fun labelExpression(groupBy: AnalyticsGroupBy): String = when (groupBy) {
        AnalyticsGroupBy.NONE -> "'Total'"
        AnalyticsGroupBy.AUTHOR -> "COALESCE(a.display_name, d.author_id)"
        AnalyticsGroupBy.REPO -> "COALESCE(r.display_name, d.repo_id)"
        AnalyticsGroupBy.LANGUAGE -> "d.language"
        AnalyticsGroupBy.CATEGORY -> "d.category"
        AnalyticsGroupBy.SUBTYPE -> "d.subtype"
        AnalyticsGroupBy.PRODUCT_CODE -> "COALESCE(NULLIF(d.product_code, ''), 'Unknown')"
        AnalyticsGroupBy.COHORT -> "COALESCE(NULLIF(a.cohort, ''), 'unknown')"
    }

    private fun buildFilterClause(from: LocalDate, to: LocalDate, filters: AnalyticsFilters): QueryParts {
        val clauses = mutableListOf<String>()
        val params = linkedMapOf<String, Any?>(
            "from" to from.toString(),
            "to" to to.toString(),
        )

        clauses += "d.day BETWEEN :from AND :to"
        appendInClause("d.author_id", "authorId", filters.authorIds, clauses, params)
        appendInClause("d.repo_id", "repoId", filters.repoIds, clauses, params)
        appendInClause("d.language", "language", filters.languages, clauses, params)
        appendInClause("d.category", "category", filters.categories, clauses, params)
        appendInClause("d.subtype", "subtype", filters.subtypes, clauses, params)
        appendInClause("COALESCE(NULLIF(d.product_code, ''), 'unknown')", "productCode", filters.productCodes, clauses, params)
        appendInClause("COALESCE(NULLIF(a.cohort, ''), 'unknown')", "cohort", filters.cohorts, clauses, params)

        return QueryParts(
            sql = "WHERE ${clauses.joinToString(" AND ")}",
            params = params,
        )
    }

    private fun appendInClause(
        expression: String,
        keyPrefix: String,
        values: List<String>,
        clauses: MutableList<String>,
        params: MutableMap<String, Any?>,
    ) {
        if (values.isEmpty()) {
            return
        }

        val placeholders = values.mapIndexed { index, value ->
            val key = "${keyPrefix}_$index"
            params[key] = value
            ":$key"
        }

        clauses += "$expression IN (${placeholders.joinToString(", ")})"
    }

    private fun applyParams(spec: JdbcClient.StatementSpec, params: Map<String, Any?>): JdbcClient.StatementSpec {
        var current = spec
        params.forEach { (key, value) -> current = current.param(key, value) }
        return current
    }
}
