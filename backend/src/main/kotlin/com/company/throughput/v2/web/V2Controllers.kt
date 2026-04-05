package com.company.throughput.v2.web

import com.company.throughput.v2.model.AddPageWidgetRequest
import com.company.throughput.v2.model.BootstrapV2Response
import com.company.throughput.v2.model.CreateAnnotationV2Request
import com.company.throughput.v2.model.CreatePageRequest
import com.company.throughput.v2.model.CreateWidgetDefinitionRequest
import com.company.throughput.v2.model.DayDrilldownRequest
import com.company.throughput.v2.model.ExecuteQueryRequest
import com.company.throughput.v2.model.FeatureFlagsDto
import com.company.throughput.v2.model.EditorLaunchRequest
import com.company.throughput.v2.model.JiraSecretRequest
import com.company.throughput.v2.model.JiraSettingsResponse
import com.company.throughput.v2.model.LayoutUpdateItem
import com.company.throughput.v2.model.QueryPreviewRequest
import com.company.throughput.v2.model.ReorderLayoutRequest
import com.company.throughput.v2.model.ReorderPagesRequest
import com.company.throughput.v2.model.SnapshotStatusResponse
import com.company.throughput.v2.model.PageStateRequest
import com.company.throughput.v2.model.UpdateAnnotationV2Request
import com.company.throughput.v2.model.UpdateJiraSettingsRequest
import com.company.throughput.v2.model.UpdatePageRequest
import com.company.throughput.v2.model.UpdatePageWidgetRequest
import com.company.throughput.v2.model.UpdateWidgetDefinitionRequest
import com.company.throughput.v2.service.AnnotationV2Service
import com.company.throughput.v2.service.DayDrilldownService
import com.company.throughput.v2.service.JiraClient
import com.company.throughput.v2.service.JiraSettingsService
import com.company.throughput.v2.service.JiraSyncService
import com.company.throughput.v2.service.ExplorerService
import com.company.throughput.v2.service.PageService
import com.company.throughput.v2.service.QueryExecutionService
import com.company.throughput.v2.service.QuerySchemaRegistry
import com.company.throughput.v2.service.RepoSnapshotService
import com.company.throughput.v2.service.WidgetCatalogService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate

@RestController
@RequestMapping("/api/v2")
class BootstrapV2Controller(
    private val pageService: PageService,
    private val widgetCatalogService: WidgetCatalogService,
    private val querySchemaRegistry: QuerySchemaRegistry,
    private val jiraSettingsService: JiraSettingsService,
) {
    private fun com.company.throughput.v2.model.DashboardPage.toSummary() =
        com.company.throughput.v2.model.PageSummaryDto(
            id = id,
            slug = slug,
            title = title,
            description = description,
            icon = icon,
            sortOrder = sortOrder,
            archived = archived,
            timeRange = timeRange,
            filters = filters,
        )

    @GetMapping("/bootstrap")
    fun bootstrap(): BootstrapV2Response = BootstrapV2Response(
        appName = "Code Flux",
        pages = pageService.list().map { it.toSummary() },
        widgets = widgetCatalogService.summary(),
        datasets = querySchemaRegistry.schema(),
        featureFlags = FeatureFlagsDto(
            snapshots = true,
            jira = true,
            legacyOverview = true,
        ),
        jiraEnabled = jiraSettingsService.get().enabled,
    )
}

@RestController
@RequestMapping("/api/v2/pages")
class PagesV2Controller(
    private val pageService: PageService,
) {
    @GetMapping
    fun list() = pageService.list().map {
        com.company.throughput.v2.model.PageSummaryDto(
            id = it.id,
            slug = it.slug,
            title = it.title,
            description = it.description,
            icon = it.icon,
            sortOrder = it.sortOrder,
            archived = it.archived,
            timeRange = it.timeRange,
            filters = it.filters,
        )
    }

    @PostMapping
    fun create(@Valid @RequestBody request: CreatePageRequest) = pageService.create(request)

    @PatchMapping("/{pageId}")
    fun update(@PathVariable pageId: String, @RequestBody request: UpdatePageRequest) =
        pageService.update(pageId, request)

    @PostMapping("/reorder")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun reorder(@Valid @RequestBody request: ReorderPagesRequest) {
        pageService.reorder(request.pageIds)
    }

    @PostMapping("/{pageId}/duplicate")
    fun duplicate(@PathVariable pageId: String) = pageService.duplicate(pageId)

    @GetMapping("/{pageId}/state")
    fun state(@PathVariable pageId: String) = pageService.get(pageId)

    @PutMapping("/{pageId}/state")
    fun updateState(@PathVariable pageId: String, @Valid @RequestBody request: PageStateRequest) =
        pageService.updateState(pageId, request.timeRange, request.filters)

    @PostMapping("/{pageId}/archive")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun archive(@PathVariable pageId: String) {
        pageService.archive(pageId)
    }

    @GetMapping("/{pageId}/widgets")
    fun widgets(@PathVariable pageId: String) = pageService.listResolvedWidgets(pageId)

    @PostMapping("/{pageId}/widgets")
    fun addWidget(@PathVariable pageId: String, @Valid @RequestBody request: AddPageWidgetRequest) =
        pageService.addWidget(pageId, request)
}

@RestController
@RequestMapping("/api/v2/page-widgets")
class PageWidgetsV2Controller(
    private val pageService: PageService,
) {
    @PatchMapping("/{instanceId}")
    fun update(@PathVariable instanceId: String, @RequestBody request: UpdatePageWidgetRequest) =
        pageService.updateWidget(instanceId, request)

    @PostMapping("/reorder-layout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun reorderLayout(@Valid @RequestBody request: ReorderLayoutRequest) {
        pageService.updateLayouts(request.pageId, request.items.map(LayoutUpdateItem::id).zip(request.items.map(LayoutUpdateItem::layout)))
    }

    @DeleteMapping("/{instanceId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(@PathVariable instanceId: String) {
        pageService.removeWidget(instanceId)
    }
}

@RestController
@RequestMapping("/api/v2/widgets")
class WidgetCatalogV2Controller(
    private val widgetCatalogService: WidgetCatalogService,
) {
    @GetMapping
    fun list() = widgetCatalogService.list()

    @PostMapping
    fun create(@Valid @RequestBody request: CreateWidgetDefinitionRequest) = widgetCatalogService.create(request)

    @GetMapping("/{widgetId}")
    fun get(@PathVariable widgetId: String) = widgetCatalogService.get(widgetId)

    @PatchMapping("/{widgetId}")
    fun update(@PathVariable widgetId: String, @RequestBody request: UpdateWidgetDefinitionRequest) =
        widgetCatalogService.update(widgetId, request)

    @PostMapping("/{widgetId}/duplicate")
    fun duplicate(@PathVariable widgetId: String) = widgetCatalogService.duplicate(widgetId)

    @PostMapping("/{widgetId}/archive")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun archive(@PathVariable widgetId: String) {
        widgetCatalogService.archive(widgetId)
    }
}

@RestController
@RequestMapping("/api/v2/query")
class QueryV2Controller(
    private val querySchemaRegistry: QuerySchemaRegistry,
    private val queryExecutionService: QueryExecutionService,
) {
    @GetMapping("/schema")
    fun schema() = querySchemaRegistry.schema()

    @PostMapping("/preview")
    fun preview(@Valid @RequestBody request: QueryPreviewRequest) = queryExecutionService.preview(request)

    @PostMapping("/execute")
    fun execute(@RequestBody request: ExecuteQueryRequest) = queryExecutionService.execute(request)
}

@RestController
@RequestMapping("/api/v2/annotations")
class AnnotationsV2Controller(
    private val annotationService: AnnotationV2Service,
) {
    @GetMapping
    fun list(
        @RequestParam(required = false) pageWidgetInstanceId: String?,
        @RequestParam(required = false) dateFrom: LocalDate?,
        @RequestParam(required = false) dateTo: LocalDate?,
    ) = annotationService.list(pageWidgetInstanceId, dateFrom, dateTo)

    @PostMapping
    fun create(@Valid @RequestBody request: CreateAnnotationV2Request) = annotationService.create(request)

    @PatchMapping("/{annotationId}")
    fun update(@PathVariable annotationId: String, @Valid @RequestBody request: UpdateAnnotationV2Request) =
        annotationService.update(annotationId, request)

    @DeleteMapping("/{annotationId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(@PathVariable annotationId: String) {
        annotationService.delete(annotationId)
    }
}

@RestController
@RequestMapping("/api/v2/drilldown")
class DrilldownV2Controller(
    private val dayDrilldownService: DayDrilldownService,
) {
    @PostMapping("/day")
    fun day(@Valid @RequestBody request: DayDrilldownRequest) = dayDrilldownService.load(request)
}

@RestController
@RequestMapping("/api/v2/explorer")
class ExplorerV2Controller(
    private val explorerService: ExplorerService,
) {
    @GetMapping("/commit/{repoId}/{commitSha}")
    fun commitDetail(
        @PathVariable repoId: String,
        @PathVariable commitSha: String,
    ) = explorerService.commitDetail(repoId, commitSha)

    @PostMapping("/open-in-editor")
    fun openInEditor(@Valid @RequestBody request: EditorLaunchRequest) =
        explorerService.launchEditor(request)
}

@RestController
@RequestMapping("/api/v2/settings/jira")
class JiraSettingsV2Controller(
    private val jiraSettingsService: JiraSettingsService,
    private val jiraClient: JiraClient,
) {
    @GetMapping
    fun get(): JiraSettingsResponse {
        val settings = jiraSettingsService.get()
        return JiraSettingsResponse(
            enabled = settings.enabled,
            baseUrl = settings.baseUrl,
            authMode = settings.authMode,
            verifyTls = settings.verifyTls,
            issueKeyRegex = settings.issueKeyRegex,
            projectKeys = settings.projectKeys,
            lastValidatedAt = settings.lastValidatedAt,
            secretConfigured = jiraSettingsService.hasSecret(),
        )
    }

    @PutMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun update(@RequestBody request: UpdateJiraSettingsRequest) {
        jiraSettingsService.update(request)
    }

    @PostMapping("/secret")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun saveSecret(@Valid @RequestBody request: JiraSecretRequest) {
        jiraSettingsService.saveSecret(request.token)
    }

    @PostMapping("/test")
    fun testConnection() = jiraClient.testConnection()
}

@RestController
@RequestMapping("/api/v2/jira")
class JiraV2Controller(
    private val jiraSyncService: JiraSyncService,
) {
    @PostMapping("/sync")
    fun sync() = jiraSyncService.sync()

    @GetMapping("/status")
    fun status() = jiraSyncService.status()
}

@RestController
@RequestMapping("/api/v2/snapshots")
class SnapshotsV2Controller(
    private val repoSnapshotService: RepoSnapshotService,
) {
    @PostMapping("/current")
    fun current() = repoSnapshotService.buildCurrentSnapshots()

    @PostMapping("/backfill")
    fun backfill() = repoSnapshotService.backfill()

    @GetMapping("/status")
    fun status(): SnapshotStatusResponse = repoSnapshotService.status()
}
