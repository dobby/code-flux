package com.company.throughput.web

import com.company.throughput.analytics.AnalyticsQueryRequest
import com.company.throughput.analytics.AnalyticsQueryResponse
import com.company.throughput.analytics.AnalyticsQueryService
import com.company.throughput.analytics.CompareRequest
import com.company.throughput.analytics.CompareResponse
import com.company.throughput.analytics.FilterOptionsResponse
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api")
class AnalyticsController(
    private val analyticsQueryService: AnalyticsQueryService,
) {
    @GetMapping("/filters/options")
    fun filterOptions(): FilterOptionsResponse = analyticsQueryService.filterOptions()

    @PostMapping("/analytics/query")
    fun query(@Valid @RequestBody request: AnalyticsQueryRequest): AnalyticsQueryResponse =
        analyticsQueryService.query(request)

    @PostMapping("/analytics/compare")
    fun compare(@Valid @RequestBody request: CompareRequest): CompareResponse =
        analyticsQueryService.compare(request)
}
