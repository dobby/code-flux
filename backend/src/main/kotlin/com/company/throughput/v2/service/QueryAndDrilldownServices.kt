package com.company.throughput.v2.service

import com.company.throughput.v2.model.ComparisonModeV2
import com.company.throughput.v2.model.DatasetKey
import com.company.throughput.v2.model.DayDrilldownRequest
import com.company.throughput.v2.model.DayDrilldownResponse
import com.company.throughput.v2.model.DrilldownCommitRow
import com.company.throughput.v2.model.DrilldownContributorRow
import com.company.throughput.v2.model.DrilldownFileRow
import com.company.throughput.v2.model.DrilldownIssueRow
import com.company.throughput.v2.model.DrilldownSummary
import com.company.throughput.v2.model.ExecuteQueryRequest
import com.company.throughput.v2.model.FilterOperator
import com.company.throughput.v2.model.FilterSpec
import com.company.throughput.v2.model.QueryExecutionResponse
import com.company.throughput.v2.model.QueryPreviewRequest
import com.company.throughput.v2.model.QuerySchemaDatasetDto
import com.company.throughput.v2.model.QuerySchemaFieldDto
import com.company.throughput.v2.model.SortDirection
import com.company.throughput.v2.model.TimeBucket
import com.company.throughput.v2.model.WidgetKind
import com.company.throughput.v2.model.WidgetQuerySpec
import com.company.throughput.v2.repo.PageWidgetInstanceRepository
import com.company.throughput.v2.repo.WidgetDefinitionRepository
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Service
import java.time.LocalDate

internal data class DatasetSpec(
    val key: DatasetKey,
    val label: String,
    val description: String,
    val supportsTime: Boolean,
    val supportsComparison: Boolean,
    val measures: Map<String, String>,
    val dimensions: Map<String, String>,
    val widgetKinds: List<WidgetKind>,
)

private data class BuiltQuery(
    val sql: String,
    val params: Map<String, Any?>,
)

@Service
class QuerySchemaRegistry {
    private val specs = listOf(
        DatasetSpec(
            key = DatasetKey.THROUGHPUT_DAILY,
            label = "Throughput",
            description = "Authored-day deduplicated code movement facts.",
            supportsTime = true,
            supportsComparison = true,
            measures = linkedMapOf(
                "lines_added" to "Lines added",
                "lines_removed" to "Lines removed",
                "net_lines" to "Net lines",
                "commits_count" to "Commit count",
                "files_changed_count" to "Files changed count",
            ),
            dimensions = linkedMapOf(
                "repo" to "Repository",
                "author" to "Author",
                "language" to "Language",
                "category" to "Category",
                "subtype" to "Subtype",
                "productCode" to "Product code",
                "cohort" to "Cohort",
                "date" to "Date",
                "week" to "Week",
                "month" to "Month",
            ),
            widgetKinds = listOf(
                WidgetKind.TIME_SERIES,
                WidgetKind.DISTRIBUTION,
                WidgetKind.METRIC_CARD,
                WidgetKind.DATA_TABLE,
                WidgetKind.CALENDAR_HEATMAP,
            ),
        ),
        DatasetSpec(
            key = DatasetKey.THROUGHPUT_ISSUE_DAILY,
            label = "Throughput + Jira",
            description = "Throughput facts enriched with the commit primary Jira issue.",
            supportsTime = true,
            supportsComparison = true,
            measures = linkedMapOf(
                "lines_added" to "Lines added",
                "lines_removed" to "Lines removed",
                "net_lines" to "Net lines",
                "commits_count" to "Commit count",
                "files_changed_count" to "Files changed count",
            ),
            dimensions = linkedMapOf(
                "repo" to "Repository",
                "author" to "Author",
                "language" to "Language",
                "category" to "Category",
                "subtype" to "Subtype",
                "productCode" to "Product code",
                "issueKey" to "Issue key",
                "issueType" to "Issue type",
                "issueStatus" to "Issue status",
                "issuePriority" to "Issue priority",
                "jiraProjectKey" to "Jira project key",
                "date" to "Date",
                "week" to "Week",
                "month" to "Month",
            ),
            widgetKinds = listOf(
                WidgetKind.TIME_SERIES,
                WidgetKind.DISTRIBUTION,
                WidgetKind.METRIC_CARD,
                WidgetKind.DATA_TABLE,
                WidgetKind.CALENDAR_HEATMAP,
            ),
        ),
        DatasetSpec(
            key = DatasetKey.REPO_STATE_DAILY,
            label = "Repository state",
            description = "Daily default-branch repository composition snapshots.",
            supportsTime = true,
            supportsComparison = true,
            measures = linkedMapOf(
                "total_files" to "Total files",
                "total_lines" to "Total lines",
                "production_files" to "Production files",
                "production_lines" to "Production lines",
                "test_files" to "Test files",
                "test_lines" to "Test lines",
                "docs_files" to "Docs files",
                "docs_lines" to "Docs lines",
                "generated_files" to "Generated files",
                "generated_lines" to "Generated lines",
                "files_count" to "Files count",
                "lines_count" to "Lines count",
            ),
            dimensions = linkedMapOf(
                "repo" to "Repository",
                "language" to "Language",
                "category" to "Category",
                "subtype" to "Subtype",
                "date" to "Snapshot date",
                "week" to "Week",
                "month" to "Month",
            ),
            widgetKinds = listOf(
                WidgetKind.TIME_SERIES,
                WidgetKind.DISTRIBUTION,
                WidgetKind.METRIC_CARD,
                WidgetKind.DATA_TABLE,
            ),
        ),
        DatasetSpec(
            key = DatasetKey.FILE_INVENTORY_CURRENT,
            label = "Current inventory",
            description = "Current default-branch file inventory.",
            supportsTime = false,
            supportsComparison = false,
            measures = linkedMapOf(
                "files_count" to "Files count",
                "lines_count" to "Lines count",
            ),
            dimensions = linkedMapOf(
                "repo" to "Repository",
                "language" to "Language",
                "category" to "Category",
                "subtype" to "Subtype",
                "productCode" to "Product code",
                "filePath" to "File path",
            ),
            widgetKinds = listOf(
                WidgetKind.DISTRIBUTION,
                WidgetKind.METRIC_CARD,
                WidgetKind.DATA_TABLE,
            ),
        ),
        DatasetSpec(
            key = DatasetKey.DAY_ACTIVITY,
            label = "Day activity",
            description = "Drilldown-only dataset assembled for a selected bucket.",
            supportsTime = false,
            supportsComparison = false,
            measures = emptyMap(),
            dimensions = emptyMap(),
            widgetKinds = listOf(WidgetKind.DAY_EXPLORER, WidgetKind.DATA_TABLE),
        ),
    )

    fun schema(): List<QuerySchemaDatasetDto> = specs.map { spec ->
        QuerySchemaDatasetDto(
            key = spec.key,
            label = spec.label,
            description = spec.description,
            supportsTime = spec.supportsTime,
            supportsComparison = spec.supportsComparison,
            measures = spec.measures.map { QuerySchemaFieldDto(it.key, it.value) },
            dimensions = spec.dimensions.map { QuerySchemaFieldDto(it.key, it.value) },
            widgetKinds = spec.widgetKinds,
        )
    }

    internal fun require(datasetKey: DatasetKey): DatasetSpec =
        requireNotNull(specs.firstOrNull { it.key == datasetKey }) { "INVALID_WIDGET_QUERY: unsupported dataset ${datasetKey.name.lowercase()}" }
}

@Service
class QueryExecutionService(
    private val jdbcClient: JdbcClient,
    private val querySchemaRegistry: QuerySchemaRegistry,
    private val widgetDefinitionRepository: WidgetDefinitionRepository,
    private val pageWidgetInstanceRepository: PageWidgetInstanceRepository,
) {
    fun preview(request: QueryPreviewRequest): QueryExecutionResponse {
        val query = request.query ?: error("INVALID_WIDGET_QUERY: query is required")
        validateQuery(query, request.widgetKind)
        return executeSpec(query)
    }

    fun execute(request: ExecuteQueryRequest): QueryExecutionResponse {
        val query = when {
            request.effectiveQuery != null -> request.effectiveQuery
            request.widgetInstanceId != null -> {
                val instance = requireNotNull(pageWidgetInstanceRepository.findById(request.widgetInstanceId)) {
                    "PAGE_WIDGET_NOT_FOUND: ${request.widgetInstanceId}"
                }
                val definition = instance.widgetDefinitionId?.let(widgetDefinitionRepository::findById)
                val base = instance.queryOverride ?: definition?.querySpec
                requireNotNull(base) { "INVALID_WIDGET_QUERY: widget instance has no query" }.copy(
                    filters = base.filters + request.runtimeFilters,
                )
            }

            else -> error("INVALID_WIDGET_QUERY: effectiveQuery or widgetInstanceId is required")
        }
        return executeSpec(query)
    }

    private fun executeSpec(query: WidgetQuerySpec): QueryExecutionResponse {
        val spec = querySchemaRegistry.require(query.dataset)
        val built = when (query.dataset) {
            DatasetKey.THROUGHPUT_DAILY -> buildThroughputQuery(query, issue = false)
            DatasetKey.THROUGHPUT_ISSUE_DAILY -> buildThroughputQuery(query, issue = true)
            DatasetKey.REPO_STATE_DAILY -> buildRepoStateQuery(query)
            DatasetKey.FILE_INVENTORY_CURRENT -> buildCurrentInventoryQuery(query)
            DatasetKey.DAY_ACTIVITY -> error("INVALID_WIDGET_QUERY: day_activity is drilldown-only")
        }

        val rows = applyParams(jdbcClient.sql(built.sql), built.params)
            .query { rs, _ ->
                val meta = rs.metaData
                buildMap<String, Any?> {
                    for (index in 1..meta.columnCount) {
                        put(meta.getColumnLabel(index), rs.getObject(index))
                    }
                }
            }
            .list()

        val totals = if (rows.isEmpty()) {
            mapOf("value" to 0)
        } else {
            mapOf("value" to rows.sumOf { (it["value"] as? Number)?.toDouble() ?: 0.0 })
        }

        val comparisonRange = query.comparison
            ?.takeIf { it.mode != ComparisonModeV2.NONE }
            ?.let { resolveComparisonRange(query, it) }

        return QueryExecutionResponse(
            dataset = spec.key,
            rows = rows,
            totals = totals,
            effectiveQuery = query,
            comparisonRange = comparisonRange,
        )
    }

    private fun validateQuery(query: WidgetQuerySpec, widgetKind: WidgetKind) {
        val spec = querySchemaRegistry.require(query.dataset)
        if (widgetKind != WidgetKind.DAY_EXPLORER) {
            require(query.measure != null) { "INVALID_WIDGET_QUERY: measure is required" }
        }
        query.measure?.let { require(it.field in spec.measures.keys) { "INVALID_WIDGET_QUERY: unsupported measure ${it.field}" } }
        query.groupBy.forEach { group ->
            require(group in spec.dimensions.keys) { "INVALID_WIDGET_QUERY: unsupported grouping $group" }
        }
        when (widgetKind) {
            WidgetKind.TIME_SERIES, WidgetKind.CALENDAR_HEATMAP -> require(query.timeBucket != null) {
                "INVALID_WIDGET_QUERY: timeBucket is required"
            }

            WidgetKind.DISTRIBUTION -> require(query.groupBy.size == 1) {
                "INVALID_WIDGET_QUERY: distribution widgets require one grouping"
            }

            WidgetKind.METRIC_CARD -> require(query.groupBy.isEmpty()) {
                "INVALID_WIDGET_QUERY: metric cards cannot group"
            }

            WidgetKind.DAY_EXPLORER -> {
                require(query.dataset == DatasetKey.DAY_ACTIVITY) { "INVALID_WIDGET_QUERY: day explorer uses day_activity" }
            }

            else -> Unit
        }
    }

    private fun buildThroughputQuery(query: WidgetQuerySpec, issue: Boolean): BuiltQuery {
        val table = if (issue) "daily_throughput_issue_fact d" else "daily_fact d LEFT JOIN author a ON a.author_id = d.author_id LEFT JOIN repository r ON r.repo_id = d.repo_id"
        val dateColumn = if (issue) "d.activity_date" else "d.day"
        val metricColumn = when (query.measure!!.field) {
            "lines_added" -> "d.lines_added"
            "lines_removed" -> "d.lines_removed"
            "net_lines" -> "d.net_lines"
            "commits_count" -> if (issue) "d.commits_count" else "d.commit_count"
            "files_changed_count" -> if (issue) "d.files_changed_count" else "d.file_count"
            else -> error("INVALID_WIDGET_QUERY: unsupported measure ${query.measure.field}")
        }

        val groupField = query.groupBy.firstOrNull()
        val bucketExpression = when (query.timeBucket) {
            TimeBucket.DAY -> dateColumn
            TimeBucket.WEEK -> "strftime('%Y-W%W', $dateColumn)"
            TimeBucket.MONTH -> "substr($dateColumn, 1, 7)"
            null -> null
        }
        val groupExpression = when (groupField) {
            null -> null
            "repo" -> if (issue) "d.repo_id" else "COALESCE(r.display_name, d.repo_id)"
            "author" -> if (issue) "COALESCE(d.author_identity, 'unknown')" else "COALESCE(a.display_name, d.author_id)"
            "language" -> "COALESCE(d.language, 'unknown')"
            "category" -> "COALESCE(d.category, 'unknown')"
            "subtype" -> "COALESCE(d.subtype, 'unknown')"
            "productCode" -> "COALESCE(NULLIF(d.product_code, ''), 'unknown')"
            "cohort" -> "COALESCE(NULLIF(a.cohort, ''), 'unknown')"
            "issueKey" -> "COALESCE(d.primary_issue_key, 'unlinked')"
            "issueType" -> "COALESCE(d.primary_issue_type, 'Unlinked')"
            "issueStatus" -> "COALESCE(d.primary_issue_status, 'Unknown')"
            "issuePriority" -> "COALESCE(d.primary_issue_priority, 'Unknown')"
            "jiraProjectKey" -> "COALESCE(d.jira_project_key, 'unknown')"
            else -> error("INVALID_WIDGET_QUERY: unsupported grouping $groupField")
        }

        val selects = mutableListOf<String>()
        val groups = mutableListOf<String>()
        val orders = mutableListOf<String>()
        if (bucketExpression != null) {
            selects += "$bucketExpression AS bucket"
            groups += "bucket"
            orders += "bucket ${sortDirection(query)}"
        }
        if (groupExpression != null) {
            selects += "$groupExpression AS group_key"
            selects += "$groupExpression AS group_label"
            groups += "group_key"
            groups += "group_label"
        }
        selects += "SUM($metricColumn) AS value"
        val filters = buildFilters(query.dataset, query.filters)
        val sql = buildString {
            append("SELECT ${selects.joinToString(", ")} FROM $table")
            if (filters.first.isNotBlank()) {
                append(" WHERE ${filters.first}")
            }
            if (groups.isNotEmpty()) {
                append(" GROUP BY ${groups.joinToString(", ")}")
            }
            if (orders.isNotEmpty()) {
                append(" ORDER BY ${orders.joinToString(", ")}")
            } else if (groupExpression != null) {
                append(" ORDER BY value DESC, group_label ASC")
            }
            query.limit?.let { append(" LIMIT $it") }
        }
        return BuiltQuery(sql = sql, params = filters.second)
    }

    private fun buildRepoStateQuery(query: WidgetQuerySpec): BuiltQuery {
        val measureField = query.measure!!.field
        val breakdownDimension = query.groupBy.firstOrNull()?.takeIf { it in setOf("language", "category", "subtype") }
        val bucketExpression = when (query.timeBucket) {
            TimeBucket.DAY -> "s.snapshot_date"
            TimeBucket.WEEK -> "strftime('%Y-W%W', s.snapshot_date)"
            TimeBucket.MONTH -> "substr(s.snapshot_date, 1, 7)"
            null -> null
        }

        val groupExpression = when (val groupField = query.groupBy.firstOrNull()) {
            null -> null
            "repo" -> "s.repo_id"
            "language", "category", "subtype" -> "b.dimension_value"
            else -> error("INVALID_WIDGET_QUERY: unsupported grouping $groupField for repo_state_daily")
        }

        if (breakdownDimension != null) {
            require(measureField in setOf("files_count", "lines_count")) {
                "INVALID_WIDGET_QUERY: repo state breakdowns only support files_count or lines_count"
            }
        }

        val metricColumn = when (measureField) {
            "total_files" -> "s.total_files"
            "total_lines" -> "s.total_lines"
            "production_files" -> "s.production_files"
            "production_lines" -> "s.production_lines"
            "test_files" -> "s.test_files"
            "test_lines" -> "s.test_lines"
            "docs_files" -> "s.docs_files"
            "docs_lines" -> "s.docs_lines"
            "generated_files" -> "s.generated_files"
            "generated_lines" -> "s.generated_lines"
            "files_count" -> "b.files_count"
            "lines_count" -> "b.lines_count"
            else -> error("INVALID_WIDGET_QUERY: unsupported measure $measureField")
        }
        val from = if (breakdownDimension != null) {
            "repo_state_snapshots s JOIN repo_state_snapshot_breakdowns b ON b.snapshot_id = s.id AND b.dimension_kind = :dimensionKind"
        } else {
            "repo_state_snapshots s"
        }
        val filters = buildFilters(query.dataset, query.filters, extra = breakdownDimension?.let { mapOf("dimensionKind" to it) } ?: emptyMap())
        val selects = mutableListOf<String>()
        val groups = mutableListOf<String>()
        val orders = mutableListOf<String>()
        if (bucketExpression != null) {
            selects += "$bucketExpression AS bucket"
            groups += "bucket"
            orders += "bucket ${sortDirection(query)}"
        }
        if (groupExpression != null) {
            selects += "$groupExpression AS group_key"
            selects += "$groupExpression AS group_label"
            groups += "group_key"
            groups += "group_label"
        }
        selects += "${query.measure.aggregation.name}( $metricColumn ) AS value".replace("MAX", "MAX").replace("SUM", "SUM").replace("AVG", "AVG").replace("COUNT", "COUNT").replace("MIN", "MIN")
        val sql = buildString {
            append("SELECT ${selects.joinToString(", ")} FROM $from")
            if (filters.first.isNotBlank()) {
                append(" WHERE ${filters.first}")
            }
            if (groups.isNotEmpty()) {
                append(" GROUP BY ${groups.joinToString(", ")}")
            }
            if (orders.isNotEmpty()) {
                append(" ORDER BY ${orders.joinToString(", ")}")
            } else if (groupExpression != null) {
                append(" ORDER BY value DESC, group_label ASC")
            }
            query.limit?.let { append(" LIMIT $it") }
        }
        return BuiltQuery(sql = sql, params = filters.second)
    }

    private fun buildCurrentInventoryQuery(query: WidgetQuerySpec): BuiltQuery {
        val metricColumn = when (query.measure!!.field) {
            "files_count" -> "COUNT(*)"
            "lines_count" -> "SUM(COALESCE(i.line_count, 0))"
            else -> error("INVALID_WIDGET_QUERY: unsupported measure ${query.measure.field}")
        }
        val groupExpression = when (val groupField = query.groupBy.firstOrNull()) {
            null -> null
            "repo" -> "i.repo_id"
            "language" -> "COALESCE(i.language, 'unknown')"
            "category" -> "COALESCE(i.category, 'unknown')"
            "subtype" -> "COALESCE(i.subtype, 'unknown')"
            "productCode" -> "COALESCE(NULLIF(i.product_code, ''), 'unknown')"
            "filePath" -> "i.file_path"
            else -> error("INVALID_WIDGET_QUERY: unsupported grouping $groupField")
        }
        val filters = buildFilters(query.dataset, query.filters)
        val selects = mutableListOf<String>()
        val groups = mutableListOf<String>()
        if (groupExpression != null) {
            selects += "$groupExpression AS group_key"
            selects += "$groupExpression AS group_label"
            groups += "group_key"
            groups += "group_label"
        }
        selects += "$metricColumn AS value"
        val sql = buildString {
            append("SELECT ${selects.joinToString(", ")} FROM file_inventory_current i")
            if (filters.first.isNotBlank()) {
                append(" WHERE ${filters.first}")
            }
            if (groups.isNotEmpty()) {
                append(" GROUP BY ${groups.joinToString(", ")}")
            }
            if (groupExpression != null) {
                append(" ORDER BY value DESC, group_label ASC")
            }
            query.limit?.let { append(" LIMIT $it") }
        }
        return BuiltQuery(sql = sql, params = filters.second)
    }

    private fun buildFilters(
        dataset: DatasetKey,
        filters: List<FilterSpec>,
        extra: Map<String, Any?> = emptyMap(),
    ): Pair<String, Map<String, Any?>> {
        val clauses = mutableListOf<String>()
        val params = linkedMapOf<String, Any?>()
        params.putAll(extra)
        filters.forEachIndexed { index, filter ->
            val expression = fieldExpression(dataset, filter.field)
            when (filter.op) {
                FilterOperator.IN, FilterOperator.NOT_IN -> {
                    require(filter.values.isNotEmpty()) { "INVALID_WIDGET_QUERY: filter ${filter.field} requires values" }
                    val placeholders = filter.values.mapIndexed { valueIndex, value ->
                        val key = "filter_${index}_$valueIndex"
                        params[key] = value
                        ":$key"
                    }
                    clauses += "$expression ${if (filter.op == FilterOperator.NOT_IN) "NOT " else ""}IN (${placeholders.joinToString(", ")})"
                }

                FilterOperator.EQ, FilterOperator.NEQ, FilterOperator.GTE, FilterOperator.LTE -> {
                    val key = "filter_${index}_0"
                    params[key] = filter.values.firstOrNull()
                    val operator = when (filter.op) {
                        FilterOperator.EQ -> "="
                        FilterOperator.NEQ -> "!="
                        FilterOperator.GTE -> ">="
                        FilterOperator.LTE -> "<="
                        else -> error("Unsupported operator")
                    }
                    clauses += "$expression $operator :$key"
                }

                FilterOperator.BETWEEN -> {
                    require(filter.values.size == 2) { "INVALID_WIDGET_QUERY: between requires exactly two values" }
                    val fromKey = "filter_${index}_0"
                    val toKey = "filter_${index}_1"
                    params[fromKey] = filter.values[0]
                    params[toKey] = filter.values[1]
                    clauses += "$expression BETWEEN :$fromKey AND :$toKey"
                }

                FilterOperator.CONTAINS -> {
                    val key = "filter_${index}_0"
                    params[key] = "%${filter.values.firstOrNull().orEmpty()}%"
                    clauses += "$expression LIKE :$key"
                }
            }
        }
        return clauses.joinToString(" AND ") to params
    }

    private fun fieldExpression(dataset: DatasetKey, field: String): String = when (dataset) {
        DatasetKey.THROUGHPUT_DAILY -> when (field) {
            "repo" -> "d.repo_id"
            "author" -> "d.author_id"
            "language" -> "d.language"
            "category" -> "d.category"
            "subtype" -> "d.subtype"
            "productCode" -> "COALESCE(NULLIF(d.product_code, ''), 'unknown')"
            "cohort" -> "COALESCE(NULLIF(a.cohort, ''), 'unknown')"
            "date" -> "d.day"
            else -> error("INVALID_WIDGET_QUERY: unsupported filter field $field")
        }

        DatasetKey.THROUGHPUT_ISSUE_DAILY -> when (field) {
            "repo" -> "d.repo_id"
            "author" -> "d.author_identity"
            "language" -> "d.language"
            "category" -> "d.category"
            "subtype" -> "d.subtype"
            "productCode" -> "COALESCE(NULLIF(d.product_code, ''), 'unknown')"
            "issueKey" -> "COALESCE(d.primary_issue_key, 'unlinked')"
            "issueType" -> "COALESCE(d.primary_issue_type, 'Unlinked')"
            "issueStatus" -> "COALESCE(d.primary_issue_status, 'Unknown')"
            "issuePriority" -> "COALESCE(d.primary_issue_priority, 'Unknown')"
            "jiraProjectKey" -> "COALESCE(d.jira_project_key, 'unknown')"
            "date" -> "d.activity_date"
            else -> error("INVALID_WIDGET_QUERY: unsupported filter field $field")
        }

        DatasetKey.REPO_STATE_DAILY -> when (field) {
            "repo" -> "s.repo_id"
            "language", "category", "subtype" -> "b.dimension_value"
            "date" -> "s.snapshot_date"
            else -> error("INVALID_WIDGET_QUERY: unsupported filter field $field")
        }

        DatasetKey.FILE_INVENTORY_CURRENT -> when (field) {
            "repo" -> "i.repo_id"
            "language" -> "i.language"
            "category" -> "i.category"
            "subtype" -> "i.subtype"
            "productCode" -> "COALESCE(NULLIF(i.product_code, ''), 'unknown')"
            "filePath" -> "i.file_path"
            else -> error("INVALID_WIDGET_QUERY: unsupported filter field $field")
        }

        DatasetKey.DAY_ACTIVITY -> error("INVALID_WIDGET_QUERY: day_activity uses the drilldown endpoint")
    }

    private fun sortDirection(query: WidgetQuerySpec): SortDirection =
        query.sort.firstOrNull()?.direction ?: SortDirection.ASC

    private fun resolveComparisonRange(query: WidgetQuerySpec, comparison: com.company.throughput.v2.model.ComparisonSpec): Map<String, String> {
        val dateFilter = query.filters.firstOrNull { it.field == "date" && it.values.isNotEmpty() }
        val range = dateFilter?.values ?: return emptyMap()
        val from = LocalDate.parse(range.first())
        val to = LocalDate.parse(range.last())
        return when (comparison.mode) {
            ComparisonModeV2.PREVIOUS_PERIOD -> {
                val days = to.toEpochDay() - from.toEpochDay() + 1
                mapOf(
                    "from" to from.minusDays(days).toString(),
                    "to" to from.minusDays(1).toString(),
                )
            }

            ComparisonModeV2.SAME_PERIOD_LAST_YEAR -> mapOf(
                "from" to from.minusYears(1).toString(),
                "to" to to.minusYears(1).toString(),
            )

            ComparisonModeV2.CUSTOM_PERIOD -> mapOf(
                "from" to requireNotNull(comparison.from).toString(),
                "to" to requireNotNull(comparison.to).toString(),
            )

            ComparisonModeV2.NONE -> emptyMap()
        }
    }

    private fun applyParams(spec: JdbcClient.StatementSpec, params: Map<String, Any?>): JdbcClient.StatementSpec {
        var current = spec
        params.forEach { (key, value) -> current = current.param(key, value) }
        return current
    }
}

@Service
class DayDrilldownService(
    private val jdbcClient: JdbcClient,
) {
    fun load(request: DayDrilldownRequest): DayDrilldownResponse {
        require(request.dataset in setOf(DatasetKey.THROUGHPUT_DAILY, DatasetKey.THROUGHPUT_ISSUE_DAILY)) {
            "INVALID_DRILLDOWN: only throughput datasets support day drilldown in V2"
        }
        val filters = buildDrilldownFilters(request)
        val summary = applyParams(
            jdbcClient.sql(
                """
                SELECT
                  COALESCE(SUM(f.lines_added), 0) AS lines_added,
                  COALESCE(SUM(f.lines_removed), 0) AS lines_removed,
                  COALESCE(SUM(f.net_lines), 0) AS net_lines,
                  COUNT(DISTINCT c.commit_sha) AS commits_count,
                  COUNT(*) AS files_changed_count,
                  COUNT(DISTINCT c.author_id) AS contributors_count,
                  COUNT(DISTINCT l.issue_key) AS jira_issues_count
                FROM commit_fact c
                JOIN commit_file_fact f
                  ON f.repo_id = c.repo_id
                 AND f.commit_sha = c.commit_sha
                LEFT JOIN commit_issue_links l
                  ON l.repo_id = c.repo_id
                 AND l.commit_sha = c.commit_sha
                LEFT JOIN jira_issue_cache i
                  ON i.issue_key = l.issue_key
                WHERE DATE(c.authored_at) = :selectedDate
                ${filters.first}
                """.trimIndent(),
            ).param("selectedDate", request.selectedDate.toString()),
            filters.second,
        ).query { rs, _ ->
            DrilldownSummary(
                date = request.selectedDate,
                linesAdded = rs.getLong("lines_added"),
                linesRemoved = rs.getLong("lines_removed"),
                netLines = rs.getLong("net_lines"),
                commitsCount = rs.getLong("commits_count"),
                filesChangedCount = rs.getLong("files_changed_count"),
                contributorsCount = rs.getLong("contributors_count"),
                jiraIssuesCount = rs.getLong("jira_issues_count"),
            )
        }.single()

        val commits = applyParams(
            jdbcClient.sql(
                """
                SELECT
                  c.repo_id,
                  c.commit_sha,
                  COALESCE(c.author_name, c.author_id) AS author_name,
                  c.authored_at,
                  c.subject,
                  SUM(f.lines_added) AS lines_added,
                  SUM(f.lines_removed) AS lines_removed,
                  GROUP_CONCAT(DISTINCT l.issue_key) AS issue_keys
                FROM commit_fact c
                JOIN commit_file_fact f
                  ON f.repo_id = c.repo_id
                 AND f.commit_sha = c.commit_sha
                LEFT JOIN commit_issue_links l
                  ON l.repo_id = c.repo_id
                 AND l.commit_sha = c.commit_sha
                LEFT JOIN jira_issue_cache i
                  ON i.issue_key = l.issue_key
                WHERE DATE(c.authored_at) = :selectedDate
                ${filters.first}
                GROUP BY c.repo_id, c.commit_sha, author_name, c.authored_at, c.subject
                ORDER BY lines_added DESC, c.authored_at ASC
                """.trimIndent(),
            ).param("selectedDate", request.selectedDate.toString()),
            filters.second,
        ).query { rs, _ ->
            DrilldownCommitRow(
                repoId = rs.getString("repo_id"),
                commitSha = rs.getString("commit_sha"),
                author = rs.getString("author_name"),
                authoredAt = rs.getString("authored_at"),
                subject = rs.getString("subject"),
                linesAdded = rs.getLong("lines_added"),
                linesRemoved = rs.getLong("lines_removed"),
                issueKeys = rs.getString("issue_keys")?.split(",")?.filter(String::isNotBlank) ?: emptyList(),
            )
        }.list()

        val files = applyParams(
            jdbcClient.sql(
                """
                SELECT
                  f.repo_id,
                  f.file_path,
                  f.language,
                  f.category,
                  f.subtype,
                  SUM(f.lines_added) AS lines_added,
                  SUM(f.lines_removed) AS lines_removed
                FROM commit_fact c
                JOIN commit_file_fact f
                  ON f.repo_id = c.repo_id
                 AND f.commit_sha = c.commit_sha
                LEFT JOIN commit_issue_links l
                  ON l.repo_id = c.repo_id
                 AND l.commit_sha = c.commit_sha
                LEFT JOIN jira_issue_cache i
                  ON i.issue_key = l.issue_key
                WHERE DATE(c.authored_at) = :selectedDate
                ${filters.first}
                GROUP BY f.repo_id, f.file_path, f.language, f.category, f.subtype
                ORDER BY lines_added DESC, f.file_path ASC
                """.trimIndent(),
            ).param("selectedDate", request.selectedDate.toString()),
            filters.second,
        ).query { rs, _ ->
            DrilldownFileRow(
                repoId = rs.getString("repo_id"),
                filePath = rs.getString("file_path"),
                language = rs.getString("language"),
                category = rs.getString("category"),
                subtype = rs.getString("subtype"),
                linesAdded = rs.getLong("lines_added"),
                linesRemoved = rs.getLong("lines_removed"),
            )
        }.list()

        val contributors = applyParams(
            jdbcClient.sql(
                """
                SELECT
                  COALESCE(c.author_name, c.author_id) AS author_name,
                  SUM(f.lines_added) AS lines_added,
                  SUM(f.lines_removed) AS lines_removed,
                  COUNT(DISTINCT c.commit_sha) AS commits_count,
                  COUNT(*) AS files_changed_count
                FROM commit_fact c
                JOIN commit_file_fact f
                  ON f.repo_id = c.repo_id
                 AND f.commit_sha = c.commit_sha
                LEFT JOIN commit_issue_links l
                  ON l.repo_id = c.repo_id
                 AND l.commit_sha = c.commit_sha
                LEFT JOIN jira_issue_cache i
                  ON i.issue_key = l.issue_key
                WHERE DATE(c.authored_at) = :selectedDate
                ${filters.first}
                GROUP BY author_name
                ORDER BY lines_added DESC, author_name ASC
                """.trimIndent(),
            ).param("selectedDate", request.selectedDate.toString()),
            filters.second,
        ).query { rs, _ ->
            DrilldownContributorRow(
                author = rs.getString("author_name"),
                linesAdded = rs.getLong("lines_added"),
                linesRemoved = rs.getLong("lines_removed"),
                commitsCount = rs.getLong("commits_count"),
                filesChangedCount = rs.getLong("files_changed_count"),
            )
        }.list()

        val issues = applyParams(
            jdbcClient.sql(
                """
                SELECT DISTINCT
                  i.issue_key,
                  i.summary,
                  i.issue_type,
                  i.status,
                  i.priority,
                  i.browse_url
                FROM commit_fact c
                JOIN commit_file_fact f
                  ON f.repo_id = c.repo_id
                 AND f.commit_sha = c.commit_sha
                JOIN commit_issue_links l
                  ON l.repo_id = c.repo_id
                 AND l.commit_sha = c.commit_sha
                LEFT JOIN jira_issue_cache i
                  ON i.issue_key = l.issue_key
                WHERE DATE(c.authored_at) = :selectedDate
                ${filters.first}
                ORDER BY i.issue_key ASC
                """.trimIndent(),
            ).param("selectedDate", request.selectedDate.toString()),
            filters.second,
        ).query { rs, _ ->
            DrilldownIssueRow(
                issueKey = rs.getString("issue_key"),
                summary = rs.getString("summary"),
                issueType = rs.getString("issue_type"),
                status = rs.getString("status"),
                priority = rs.getString("priority"),
                browseUrl = rs.getString("browse_url"),
            )
        }.list()

        return DayDrilldownResponse(
            summary = summary,
            commits = commits,
            files = files,
            contributors = contributors,
            jiraIssues = issues,
        )
    }

    private fun buildDrilldownFilters(request: DayDrilldownRequest): Pair<String, Map<String, Any?>> {
        val clauses = mutableListOf<String>()
        val params = linkedMapOf<String, Any?>()
        request.effectiveFilters.forEachIndexed { index, filter ->
            val expression = when (filter.field) {
                "repo" -> "c.repo_id"
                "author" -> "c.author_id"
                "language" -> "f.language"
                "category" -> "f.category"
                "subtype" -> "f.subtype"
                "productCode" -> "COALESCE(NULLIF(f.product_code, ''), 'unknown')"
                "issueKey" -> "COALESCE(l.issue_key, 'unlinked')"
                "issueType" -> "COALESCE(i.issue_type, 'Unlinked')"
                "issueStatus" -> "COALESCE(i.status, 'Unknown')"
                "issuePriority" -> "COALESCE(i.priority, 'Unknown')"
                else -> null
            } ?: return@forEachIndexed

            if (filter.values.isEmpty()) {
                return@forEachIndexed
            }
            val placeholders = filter.values.mapIndexed { valueIndex, value ->
                val key = "drill_${index}_$valueIndex"
                params[key] = value
                ":$key"
            }
            clauses += "AND $expression IN (${placeholders.joinToString(", ")})"
        }

        request.selectedSeries?.let { series ->
            val expression = when (series.field) {
                "repo" -> "c.repo_id"
                "author" -> "c.author_id"
                "language" -> "f.language"
                "category" -> "f.category"
                "subtype" -> "f.subtype"
                "productCode" -> "COALESCE(NULLIF(f.product_code, ''), 'unknown')"
                "cohort" -> "COALESCE(NULLIF(a.cohort, ''), 'unknown')"
                else -> null
            }
            if (expression != null) {
                params["selectedSeriesValue"] = series.value
                clauses += "AND $expression = :selectedSeriesValue"
            }
        }
        return clauses.joinToString(" ", prefix = " ") to params
    }

    private fun applyParams(spec: JdbcClient.StatementSpec, params: Map<String, Any?>): JdbcClient.StatementSpec {
        var current = spec
        params.forEach { (key, value) -> current = current.param(key, value) }
        return current
    }
}
