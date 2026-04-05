package com.company.throughput.v2.service

import com.company.throughput.v2.model.AddPageWidgetRequest
import com.company.throughput.v2.model.AnnotationV2
import com.company.throughput.v2.model.CreateAnnotationV2Request
import com.company.throughput.v2.model.CreatePageRequest
import com.company.throughput.v2.model.CreateWidgetDefinitionRequest
import com.company.throughput.v2.model.DashboardPage
import com.company.throughput.v2.model.PageWidgetResolved
import com.company.throughput.v2.model.UpdateAnnotationV2Request
import com.company.throughput.v2.model.UpdateJiraSettingsRequest
import com.company.throughput.v2.model.UpdatePageRequest
import com.company.throughput.v2.model.UpdatePageWidgetRequest
import com.company.throughput.v2.model.UpdateWidgetDefinitionRequest
import com.company.throughput.v2.model.WidgetCatalogSummaryDto
import com.company.throughput.v2.model.WidgetDefinition
import com.company.throughput.v2.model.WidgetVizSpec
import com.company.throughput.v2.repo.AnnotationV2Repository
import com.company.throughput.v2.repo.JiraSettingsRepository
import com.company.throughput.v2.repo.PageRepository
import com.company.throughput.v2.repo.PageWidgetInstanceRepository
import com.company.throughput.v2.repo.WidgetDefinitionRepository
import com.company.throughput.v2.repo.newId
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.LocalDate

@Service
class PageService(
    private val pageRepository: PageRepository,
    private val widgetDefinitionRepository: WidgetDefinitionRepository,
    private val pageWidgetInstanceRepository: PageWidgetInstanceRepository,
) {
    fun list(includeArchived: Boolean = false): List<DashboardPage> = pageRepository.list(includeArchived)

    fun get(id: String): DashboardPage =
        requireNotNull(pageRepository.findById(id)) { "PAGE_NOT_FOUND: $id" }

    fun create(request: CreatePageRequest): DashboardPage = pageRepository.create(
        title = request.title.trim(),
        description = request.description?.trim()?.ifBlank { null },
        icon = request.icon?.trim()?.ifBlank { null },
    )

    fun update(pageId: String, request: UpdatePageRequest): DashboardPage =
        requireNotNull(
            pageRepository.update(
                id = pageId,
                title = request.title?.trim()?.ifBlank { null },
                description = request.description?.trim()?.ifBlank { null },
                icon = request.icon?.trim()?.ifBlank { null },
            ),
        ) { "PAGE_NOT_FOUND: $pageId" }

    @Transactional
    fun duplicate(pageId: String): DashboardPage {
        val page = get(pageId)
        val duplicate = pageRepository.create(
            title = "${page.title} Copy",
            description = page.description,
            icon = page.icon,
        )
        pageWidgetInstanceRepository.listForPage(pageId).forEach { instance ->
            pageWidgetInstanceRepository.create(
                pageId = duplicate.id,
                widgetDefinitionId = instance.widgetDefinitionId,
                kind = instance.kind,
                titleOverride = instance.titleOverride,
                descriptionOverride = instance.descriptionOverride,
                queryOverride = instance.queryOverride,
                vizOverride = instance.vizOverride,
                layout = instance.layout,
                locked = instance.locked,
            )
        }
        return duplicate
    }

    fun archive(pageId: String) {
        require(pageRepository.archive(pageId)) { "PAGE_NOT_FOUND: $pageId" }
    }

    fun reorder(pageIds: List<String>) {
        require(pageIds.isNotEmpty()) { "pageIds must not be empty" }
        pageRepository.reorder(pageIds)
    }

    fun listResolvedWidgets(pageId: String): List<PageWidgetResolved> {
        get(pageId)
        return pageWidgetInstanceRepository.listForPage(pageId).map { instance ->
            val definition = instance.widgetDefinitionId?.let(widgetDefinitionRepository::findById)
            PageWidgetResolved(
                instance = instance,
                definition = definition,
                effectiveTitle = instance.titleOverride ?: definition?.title ?: instance.kind.name.lowercase(),
                effectiveDescription = instance.descriptionOverride ?: definition?.description,
                effectiveQuery = instance.queryOverride ?: definition?.querySpec,
                effectiveViz = instance.vizOverride ?: definition?.vizSpec ?: WidgetVizSpec(),
            )
        }
    }

    fun addWidget(pageId: String, request: AddPageWidgetRequest): PageWidgetResolved {
        get(pageId)
        val definition = request.widgetDefinitionId?.let(widgetDefinitionRepository::findById)
        val instance = pageWidgetInstanceRepository.create(
            pageId = pageId,
            widgetDefinitionId = definition?.id,
            kind = request.kind,
            titleOverride = request.titleOverride,
            descriptionOverride = request.descriptionOverride,
            queryOverride = request.queryOverride,
            vizOverride = request.vizOverride,
            layout = request.layout,
            locked = request.locked,
        )
        return PageWidgetResolved(
            instance = instance,
            definition = definition,
            effectiveTitle = instance.titleOverride ?: definition?.title ?: request.kind.name.lowercase(),
            effectiveDescription = instance.descriptionOverride ?: definition?.description,
            effectiveQuery = instance.queryOverride ?: definition?.querySpec,
            effectiveViz = instance.vizOverride ?: definition?.vizSpec ?: WidgetVizSpec(),
        )
    }

    fun updateWidget(instanceId: String, request: UpdatePageWidgetRequest): PageWidgetResolved {
        val updated = requireNotNull(
            pageWidgetInstanceRepository.update(
                id = instanceId,
                titleOverride = request.titleOverride,
                descriptionOverride = request.descriptionOverride,
                queryOverride = request.queryOverride,
                vizOverride = request.vizOverride,
                layout = request.layout,
                locked = request.locked,
            ),
        ) { "PAGE_WIDGET_NOT_FOUND: $instanceId" }
        val definition = updated.widgetDefinitionId?.let(widgetDefinitionRepository::findById)
        return PageWidgetResolved(
            instance = updated,
            definition = definition,
            effectiveTitle = updated.titleOverride ?: definition?.title ?: updated.kind.name.lowercase(),
            effectiveDescription = updated.descriptionOverride ?: definition?.description,
            effectiveQuery = updated.queryOverride ?: definition?.querySpec,
            effectiveViz = updated.vizOverride ?: definition?.vizSpec ?: WidgetVizSpec(),
        )
    }

    fun removeWidget(instanceId: String) {
        require(pageWidgetInstanceRepository.delete(instanceId)) { "PAGE_WIDGET_NOT_FOUND: $instanceId" }
    }

    fun updateLayouts(pageId: String, items: List<Pair<String, com.company.throughput.v2.model.LayoutSpec>>) {
        get(pageId)
        pageWidgetInstanceRepository.updateLayouts(pageId, items)
    }
}

@Service
class WidgetCatalogService(
    private val widgetDefinitionRepository: WidgetDefinitionRepository,
) {
    fun list(includeArchived: Boolean = false): List<WidgetDefinition> = widgetDefinitionRepository.list(includeArchived)

    fun summary(includeArchived: Boolean = false): List<WidgetCatalogSummaryDto> =
        list(includeArchived).map {
            WidgetCatalogSummaryDto(
                id = it.id,
                slug = it.slug,
                kind = it.kind,
                title = it.title,
                description = it.description,
                tags = it.tags,
                datasetKey = it.datasetKey,
                isSystem = it.isSystem,
                archived = it.archived,
            )
        }

    fun get(id: String): WidgetDefinition =
        requireNotNull(widgetDefinitionRepository.findById(id)) { "WIDGET_NOT_FOUND: $id" }

    fun create(request: CreateWidgetDefinitionRequest): WidgetDefinition = widgetDefinitionRepository.create(
        title = request.title.trim(),
        description = request.description?.trim()?.ifBlank { null },
        tags = request.tags.map(String::trim).filter(String::isNotBlank),
        kind = request.kind,
        datasetKey = request.datasetKey,
        querySpec = request.querySpec,
        vizSpec = request.vizSpec,
    )

    fun update(widgetId: String, request: UpdateWidgetDefinitionRequest): WidgetDefinition =
        requireNotNull(
            widgetDefinitionRepository.update(
                id = widgetId,
                title = request.title?.trim()?.ifBlank { null },
                description = request.description?.trim()?.ifBlank { null },
                tags = request.tags?.map(String::trim)?.filter(String::isNotBlank),
                datasetKey = request.datasetKey,
                querySpec = request.querySpec,
                vizSpec = request.vizSpec,
            ),
        ) { "WIDGET_NOT_FOUND: $widgetId" }

    fun duplicate(widgetId: String): WidgetDefinition =
        requireNotNull(widgetDefinitionRepository.duplicate(widgetId)) { "WIDGET_NOT_FOUND: $widgetId" }

    fun archive(widgetId: String) {
        require(widgetDefinitionRepository.archive(widgetId)) { "WIDGET_NOT_FOUND: $widgetId" }
    }
}

@Service
class AnnotationV2Service(
    private val annotationRepository: AnnotationV2Repository,
) {
    fun list(pageWidgetInstanceId: String?, dateFrom: LocalDate?, dateTo: LocalDate?) =
        annotationRepository.list(pageWidgetInstanceId, dateFrom, dateTo)

    fun create(request: CreateAnnotationV2Request): AnnotationV2 =
        annotationRepository.create(
            AnnotationV2(
                id = newId("annotation"),
                targetKind = request.targetKind,
                pageWidgetInstanceId = request.pageWidgetInstanceId,
                scopeDate = request.scopeDate,
                xValue = request.xValue,
                yValue = request.yValue,
                title = request.title.trim(),
                body = request.body?.trim()?.ifBlank { null },
                color = request.color?.trim()?.ifBlank { null },
                scope = request.scope,
                createdAt = Instant.now(),
                updatedAt = Instant.now(),
            ),
        )

    fun update(annotationId: String, request: UpdateAnnotationV2Request): AnnotationV2 =
        requireNotNull(
            annotationRepository.update(
                id = annotationId,
                title = request.title.trim(),
                body = request.body?.trim()?.ifBlank { null },
                color = request.color?.trim()?.ifBlank { null },
            ),
        ) { "ANNOTATION_NOT_FOUND: $annotationId" }

    fun delete(annotationId: String) {
        require(annotationRepository.delete(annotationId)) { "ANNOTATION_NOT_FOUND: $annotationId" }
    }
}

@Service
class JiraSettingsService(
    private val jiraSettingsRepository: JiraSettingsRepository,
    private val jiraSecretStore: JiraSecretStore,
) {
    fun get() = jiraSettingsRepository.get()

    fun update(request: UpdateJiraSettingsRequest) {
        jiraSettingsRepository.update(
            enabled = request.enabled,
            baseUrl = request.baseUrl?.trim()?.ifBlank { null },
            verifyTls = request.verifyTls,
            issueKeyRegex = request.issueKeyRegex.trim(),
            projectKeys = request.projectKeys.map(String::trim).filter(String::isNotBlank),
        )
    }

    fun hasSecret(): Boolean = jiraSecretStore.isConfigured()

    fun saveSecret(token: String) {
        jiraSecretStore.setToken(token)
    }

    fun markValidated() {
        jiraSettingsRepository.markValidated()
    }
}

@Service
class JiraSecretStore {
    @Volatile
    private var token: String? = null

    fun setToken(value: String) {
        token = value
    }

    fun getToken(): String? = token

    fun isConfigured(): Boolean = !token.isNullOrBlank()
}
