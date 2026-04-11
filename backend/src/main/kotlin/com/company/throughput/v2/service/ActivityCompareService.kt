package com.company.throughput.v2.service

import com.company.throughput.analytics.AnalyticsMetric
import com.company.throughput.analytics.AnalyticsQueryRequest
import com.company.throughput.analytics.AnalyticsQueryService
import com.company.throughput.analytics.AnalyticsSeries
import com.company.throughput.analytics.AnalyticsSeriesPoint
import com.company.throughput.analytics.AnalyticsTotals
import com.company.throughput.analytics.DateRange
import com.company.throughput.v2.model.ActivityCompareDeltaResponse
import com.company.throughput.v2.model.ActivityCompareMode
import com.company.throughput.v2.model.ActivityComparePeriodResponse
import com.company.throughput.v2.model.ActivityCompareRequest
import com.company.throughput.v2.model.ActivityCompareResponse
import com.company.throughput.v2.model.ActivityCompareSeriesResponse
import org.springframework.stereotype.Service

@Service
class ActivityCompareService(
    private val analyticsQueryService: AnalyticsQueryService,
) {
    fun compare(request: ActivityCompareRequest): ActivityCompareResponse {
        val referenceRange = deriveReferenceRange(request)

        val currentResponse = analyticsQueryService.query(
            AnalyticsQueryRequest(
                dateRange = request.current,
                metric = request.metric,
                groupBy = request.groupBy,
                filters = request.filters,
            ),
        )
        val referenceResponse = analyticsQueryService.query(
            AnalyticsQueryRequest(
                dateRange = referenceRange,
                metric = request.metric,
                groupBy = request.groupBy,
                filters = request.filters,
            ),
        )

        val currentMetricValue = metricValue(request.metric, currentResponse.totals)
        val referenceMetricValue = metricValue(request.metric, referenceResponse.totals)
        val absoluteDelta = currentMetricValue - referenceMetricValue
        val percentageDelta = if (referenceMetricValue == 0L) null else (absoluteDelta.toDouble() / referenceMetricValue.toDouble()) * 100.0

        return ActivityCompareResponse(
            current = ActivityComparePeriodResponse(
                from = request.current.from,
                to = request.current.to,
                totals = currentResponse.totals,
            ),
            reference = ActivityComparePeriodResponse(
                from = referenceRange.from,
                to = referenceRange.to,
                totals = referenceResponse.totals,
            ),
            delta = ActivityCompareDeltaResponse(
                absolute = absoluteDelta,
                percentage = percentageDelta,
            ),
            series = ActivityCompareSeriesResponse(
                current = currentResponse.series,
                reference = referenceResponse.series,
                alignedReference = alignSeries(referenceResponse.series, referenceRange, request.current),
            ),
        )
    }

    private fun deriveReferenceRange(request: ActivityCompareRequest): DateRange {
        val current = request.current
        val dayCount = current.to.toEpochDay() - current.from.toEpochDay() + 1L

        return when (request.mode) {
            ActivityCompareMode.PREVIOUS_PERIOD -> DateRange(
                from = current.from.minusDays(dayCount),
                to = current.from.minusDays(1),
            )

            ActivityCompareMode.PREVIOUS_YEAR -> DateRange(
                from = current.from.minusYears(1),
                to = current.to.minusYears(1),
            )

            ActivityCompareMode.CUSTOM_ANCHOR_DATE -> {
                val anchorDate = requireNotNull(request.customAnchorDate) {
                    "customAnchorDate is required when mode is custom_anchor_date"
                }
                val reference = DateRange(
                    from = anchorDate.minusDays(dayCount - 1L),
                    to = anchorDate,
                )
                require(reference.to.isBefore(current.from)) {
                    "customAnchorDate must derive a reference range that ends before the current range starts"
                }
                reference
            }

            ActivityCompareMode.CUSTOM_RANGE -> {
                val reference = requireNotNull(request.customRange) {
                    "customRange is required when mode is custom_range"
                }
                require(reference.to.isBefore(current.from)) {
                    "customRange must end before the current range starts"
                }
                val referenceDayCount = reference.to.toEpochDay() - reference.from.toEpochDay() + 1L
                require(referenceDayCount == dayCount) {
                    "customRange must span the same number of days as the current range"
                }
                reference
            }
        }
    }

    private fun metricValue(metric: AnalyticsMetric, totals: AnalyticsTotals): Long = when (metric) {
        AnalyticsMetric.LINES_ADDED -> totals.linesAdded
        AnalyticsMetric.LINES_REMOVED -> totals.linesRemoved
        AnalyticsMetric.NET_LINES -> totals.netLines
        AnalyticsMetric.COMMIT_COUNT -> totals.commitCount
        AnalyticsMetric.FILE_COUNT -> totals.fileCount
    }

    private fun alignSeries(
        series: List<AnalyticsSeries>,
        referenceRange: DateRange,
        currentRange: DateRange,
    ): List<AnalyticsSeries> = series.map { entry ->
        entry.copy(
            points = entry.points.map { point ->
                val offsetDays = point.day.toEpochDay() - referenceRange.from.toEpochDay()
                AnalyticsSeriesPoint(
                    day = currentRange.from.plusDays(offsetDays),
                    value = point.value,
                )
            },
        )
    }
}
