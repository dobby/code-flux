package com.company.throughput.v2.model

import com.company.throughput.analytics.AnalyticsFilters
import com.company.throughput.analytics.AnalyticsGroupBy
import com.company.throughput.analytics.AnalyticsMetric
import com.company.throughput.analytics.AnalyticsSeries
import com.company.throughput.analytics.AnalyticsTotals
import com.company.throughput.analytics.DateRange
import com.fasterxml.jackson.annotation.JsonProperty
import jakarta.validation.Valid
import jakarta.validation.constraints.NotNull
import java.time.LocalDate

enum class ActivityCompareMode {
    @JsonProperty("previous_period")
    PREVIOUS_PERIOD,

    @JsonProperty("previous_year")
    PREVIOUS_YEAR,

    @JsonProperty("custom_anchor_date")
    CUSTOM_ANCHOR_DATE,

    @JsonProperty("custom_range")
    CUSTOM_RANGE,
}

data class ActivityCompareRequest(
    @field:Valid
    @field:NotNull
    val current: DateRange,
    @field:NotNull
    val mode: ActivityCompareMode,
    val customAnchorDate: LocalDate? = null,
    @field:Valid
    val customRange: DateRange? = null,
    @field:NotNull
    val metric: AnalyticsMetric,
    @field:NotNull
    val groupBy: AnalyticsGroupBy,
    @field:Valid
    val filters: AnalyticsFilters = AnalyticsFilters(),
)

data class ActivityComparePeriodResponse(
    val from: LocalDate,
    val to: LocalDate,
    val totals: AnalyticsTotals,
)

data class ActivityCompareDeltaResponse(
    val absolute: Long,
    val percentage: Double?,
)

data class ActivityCompareSeriesResponse(
    val current: List<AnalyticsSeries>,
    val reference: List<AnalyticsSeries>,
    val alignedReference: List<AnalyticsSeries>,
)

data class ActivityCompareResponse(
    val current: ActivityComparePeriodResponse,
    val reference: ActivityComparePeriodResponse,
    val delta: ActivityCompareDeltaResponse,
    val series: ActivityCompareSeriesResponse,
)
