package com.company.throughput.analytics

import com.fasterxml.jackson.annotation.JsonProperty
import jakarta.validation.Valid
import jakarta.validation.constraints.NotNull
import java.time.LocalDate

data class DateRange(
    @field:NotNull
    val from: LocalDate,
    @field:NotNull
    val to: LocalDate,
) {
    init {
        require(!to.isBefore(from)) { "dateRange.to must be on or after dateRange.from" }
    }
}

enum class AnalyticsMetric {
    @JsonProperty("lines_added")
    LINES_ADDED,

    @JsonProperty("lines_removed")
    LINES_REMOVED,

    @JsonProperty("net_lines")
    NET_LINES,

    @JsonProperty("commit_count")
    COMMIT_COUNT,

    @JsonProperty("file_count")
    FILE_COUNT,
}

enum class AnalyticsGroupBy {
    @JsonProperty("none")
    NONE,

    @JsonProperty("author")
    AUTHOR,

    @JsonProperty("repo")
    REPO,

    @JsonProperty("language")
    LANGUAGE,

    @JsonProperty("category")
    CATEGORY,

    @JsonProperty("subtype")
    SUBTYPE,

    @JsonProperty("product_code")
    PRODUCT_CODE,

    @JsonProperty("cohort")
    COHORT,
}

enum class ComparisonMode {
    @JsonProperty("previous_equivalent_period")
    PREVIOUS_EQUIVALENT_PERIOD,

    @JsonProperty("same_period_last_year")
    SAME_PERIOD_LAST_YEAR,

    @JsonProperty("custom")
    CUSTOM,
}

data class AnalyticsFilters(
    val authorIds: List<String> = emptyList(),
    val repoIds: List<String> = emptyList(),
    val languages: List<String> = emptyList(),
    val categories: List<String> = emptyList(),
    val subtypes: List<String> = emptyList(),
    val productCodes: List<String> = emptyList(),
    val cohorts: List<String> = emptyList(),
)

data class AnalyticsQueryRequest(
    @field:Valid
    @field:NotNull
    val dateRange: DateRange,
    @field:NotNull
    val metric: AnalyticsMetric,
    @field:NotNull
    val groupBy: AnalyticsGroupBy,
    @field:Valid
    val filters: AnalyticsFilters = AnalyticsFilters(),
)

data class AnalyticsSeriesPoint(
    val day: LocalDate,
    val value: Long,
)

data class AnalyticsSeries(
    val key: String,
    val label: String,
    val points: List<AnalyticsSeriesPoint>,
)

data class AnalyticsTotals(
    val linesAdded: Long,
    val linesRemoved: Long,
    val netLines: Long,
    val commitCount: Long,
    val fileCount: Long,
)

data class AnalyticsQueryResponse(
    val series: List<AnalyticsSeries>,
    val totals: AnalyticsTotals,
)

data class CompareRequest(
    @field:Valid
    @field:NotNull
    val current: DateRange,
    @field:NotNull
    val comparisonMode: ComparisonMode,
    @field:Valid
    val customComparison: DateRange? = null,
    @field:NotNull
    val metric: AnalyticsMetric,
    @field:Valid
    val filters: AnalyticsFilters = AnalyticsFilters(),
)

data class ComparePeriodResponse(
    val from: LocalDate,
    val to: LocalDate,
    val totals: AnalyticsTotals,
)

data class CompareDeltaResponse(
    val metricAbsolute: Long,
    val metricPercentage: Double?,
    val linesAddedAbsolute: Long?,
    val linesAddedPercentage: Double?,
)

data class CompareResponse(
    val current: ComparePeriodResponse,
    val comparison: ComparePeriodResponse,
    val delta: CompareDeltaResponse,
)

data class FilterAuthorOption(
    val id: String,
    val displayName: String,
    val cohort: String?,
)

data class FilterRepoOption(
    val id: String,
    val displayName: String,
)

data class FilterOptionsResponse(
    val authors: List<FilterAuthorOption>,
    val repos: List<FilterRepoOption>,
    val languages: List<String>,
    val categories: List<String>,
    val subtypes: List<String>,
    val productCodes: List<String>,
)
