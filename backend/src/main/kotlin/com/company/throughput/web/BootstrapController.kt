package com.company.throughput.web

import com.company.throughput.config.ResolvedDashboardConfig
import com.company.throughput.persistence.BootstrapAuthorRecord
import com.company.throughput.persistence.BootstrapRepoRecord
import com.company.throughput.persistence.BootstrapRepository
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Instant
import java.time.LocalDate

data class BootstrapRepoDto(
    val id: String,
    val displayName: String,
    val productCode: String?,
    val enabled: Boolean,
)

data class BootstrapAuthorDto(
    val id: String,
    val displayName: String,
    val cohort: String?,
)

data class UiDefaultsDto(
    val defaultMetric: String,
    val defaultGroupBy: String,
    val defaultChartLibrary: String,
    val defaultIncludeCategories: List<String>,
    val defaultExcludeCategories: List<String>,
    val defaultDateFrom: LocalDate?,
    val defaultDateTo: LocalDate?,
)

data class BootstrapResponse(
    val appName: String,
    val lastSuccessfulSyncAt: Instant?,
    val repos: List<BootstrapRepoDto>,
    val authors: List<BootstrapAuthorDto>,
    val uiDefaults: UiDefaultsDto,
)

@RestController
@RequestMapping("/api")
class BootstrapController(
    private val bootstrapRepository: BootstrapRepository,
    private val resolvedDashboardConfig: ResolvedDashboardConfig,
) {
    @GetMapping("/bootstrap")
    fun bootstrap(): BootstrapResponse = BootstrapResponse(
        appName = "Git Throughput Dashboard",
        lastSuccessfulSyncAt = bootstrapRepository.lastSuccessfulSyncAt(),
        repos = bootstrapRepository.listRepos().map { it.toDto() },
        authors = bootstrapRepository.listAuthors().map { it.toDto() },
        uiDefaults = UiDefaultsDto(
            defaultMetric = resolvedDashboardConfig.uiDefaults.defaultMetric,
            defaultGroupBy = resolvedDashboardConfig.uiDefaults.defaultGroupBy,
            defaultChartLibrary = resolvedDashboardConfig.uiDefaults.defaultChartLibrary,
            defaultIncludeCategories = resolvedDashboardConfig.uiDefaults.defaultIncludeCategories,
            defaultExcludeCategories = resolvedDashboardConfig.uiDefaults.defaultExcludeCategories,
            defaultDateFrom = resolvedDashboardConfig.uiDefaults.defaultDateFrom,
            defaultDateTo = resolvedDashboardConfig.uiDefaults.defaultDateTo,
        ),
    )

    private fun BootstrapRepoRecord.toDto(): BootstrapRepoDto = BootstrapRepoDto(
        id = id,
        displayName = displayName,
        productCode = productCode,
        enabled = enabled,
    )

    private fun BootstrapAuthorRecord.toDto(): BootstrapAuthorDto = BootstrapAuthorDto(
        id = id,
        displayName = displayName,
        cohort = cohort,
    )
}
