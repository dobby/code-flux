package com.company.throughput.v2.model

import com.fasterxml.jackson.annotation.JsonProperty
import jakarta.validation.Valid
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.NotNull
import java.time.Instant
import java.time.LocalDate

enum class WidgetKind {
    @JsonProperty("time_series")
    TIME_SERIES,

    @JsonProperty("distribution")
    DISTRIBUTION,

    @JsonProperty("metric_card")
    METRIC_CARD,

    @JsonProperty("data_table")
    DATA_TABLE,

    @JsonProperty("calendar_heatmap")
    CALENDAR_HEATMAP,

    @JsonProperty("day_explorer")
    DAY_EXPLORER,

    @JsonProperty("header_block")
    HEADER_BLOCK,

    @JsonProperty("markdown_block")
    MARKDOWN_BLOCK,

    @JsonProperty("divider_block")
    DIVIDER_BLOCK,

    @JsonProperty("spacer_block")
    SPACER_BLOCK,
}

enum class DatasetKey {
    @JsonProperty("throughput_daily")
    THROUGHPUT_DAILY,

    @JsonProperty("throughput_issue_daily")
    THROUGHPUT_ISSUE_DAILY,

    @JsonProperty("repo_state_daily")
    REPO_STATE_DAILY,

    @JsonProperty("file_inventory_current")
    FILE_INVENTORY_CURRENT,

    @JsonProperty("day_activity")
    DAY_ACTIVITY,
}

enum class TimeBucket {
    @JsonProperty("day")
    DAY,

    @JsonProperty("week")
    WEEK,

    @JsonProperty("month")
    MONTH,
}

enum class AggregationType {
    @JsonProperty("sum")
    SUM,

    @JsonProperty("count")
    COUNT,

    @JsonProperty("avg")
    AVG,

    @JsonProperty("min")
    MIN,

    @JsonProperty("max")
    MAX,
}

enum class FilterOperator {
    @JsonProperty("in")
    IN,

    @JsonProperty("not_in")
    NOT_IN,

    @JsonProperty("eq")
    EQ,

    @JsonProperty("neq")
    NEQ,

    @JsonProperty("gte")
    GTE,

    @JsonProperty("lte")
    LTE,

    @JsonProperty("between")
    BETWEEN,

    @JsonProperty("contains")
    CONTAINS,
}

enum class ComparisonModeV2 {
    @JsonProperty("none")
    NONE,

    @JsonProperty("previous_period")
    PREVIOUS_PERIOD,

    @JsonProperty("same_period_last_year")
    SAME_PERIOD_LAST_YEAR,

    @JsonProperty("custom_period")
    CUSTOM_PERIOD,
}

enum class SortDirection {
    @JsonProperty("asc")
    ASC,

    @JsonProperty("desc")
    DESC,
}

enum class AnnotationTypeV2 {
    @JsonProperty("feature")
    FEATURE,

    @JsonProperty("incident")
    INCIDENT,

    @JsonProperty("project")
    PROJECT,

    @JsonProperty("note")
    NOTE,
}

enum class AnnotationTargetKind {
    @JsonProperty("global_date")
    GLOBAL_DATE,

    @JsonProperty("widget_point")
    WIDGET_POINT,
}

data class PageTimeRange(
    val preset: String? = null,
    val from: LocalDate? = null,
    val to: LocalDate? = null,
)

data class PageFilterState(
    @field:NotBlank
    val field: String,
    @field:NotNull
    val op: FilterOperator,
    val values: List<String> = emptyList(),
    val locked: Boolean = false,
)

data class MeasureSpec(
    @field:NotBlank
    val field: String,
    @field:NotNull
    val aggregation: AggregationType,
)

data class FilterSpec(
    @field:NotBlank
    val field: String,
    @field:NotNull
    val op: FilterOperator,
    val values: List<String> = emptyList(),
)

data class SortSpec(
    @field:NotBlank
    val field: String,
    @field:NotNull
    val direction: SortDirection = SortDirection.ASC,
)

data class ComparisonSpec(
    @field:NotNull
    val mode: ComparisonModeV2 = ComparisonModeV2.NONE,
    val from: LocalDate? = null,
    val to: LocalDate? = null,
)

data class WidgetQuerySpec(
    @field:NotNull
    val dataset: DatasetKey,
    val timeBucket: TimeBucket? = null,
    @field:Valid
    val measure: MeasureSpec? = null,
    val groupBy: List<String> = emptyList(),
    val series: String? = null,
    @field:Valid
    val filters: List<FilterSpec> = emptyList(),
    @field:Valid
    val comparison: ComparisonSpec? = null,
    @field:Valid
    val sort: List<SortSpec> = emptyList(),
    @field:Min(1)
    val limit: Int? = null,
)

data class WidgetVizSpec(
    val chartType: String? = null,
    val stackMode: String? = null,
    val cumulative: Boolean = false,
    val showLegend: Boolean = true,
    val showAnnotations: Boolean = true,
    val yAxisLabel: String? = null,
    val paletteKey: String? = "default",
    val emptyStateMessage: String? = null,
    val markdown: String? = null,
    val headingLevel: Int? = null,
    val body: String? = null,
    val columns: List<String> = emptyList(),
)

data class LayoutSpec(
    @field:Min(0)
    val x: Int = 0,
    @field:Min(0)
    val y: Int = 0,
    @field:Min(1)
    val w: Int = 6,
    @field:Min(1)
    val h: Int = 6,
    @field:Min(1)
    val minW: Int? = null,
    @field:Min(1)
    val minH: Int? = null,
    @field:Min(1)
    val maxW: Int? = null,
    @field:Min(1)
    val maxH: Int? = null,
)

data class DashboardPage(
    val id: String,
    val slug: String,
    val title: String,
    val description: String?,
    val icon: String?,
    val sortOrder: Int,
    val archived: Boolean,
    val timeRange: PageTimeRange? = null,
    val filters: List<PageFilterState> = emptyList(),
    val createdAt: Instant,
    val updatedAt: Instant,
)

data class WidgetDefinition(
    val id: String,
    val slug: String,
    val kind: WidgetKind,
    val title: String,
    val description: String?,
    val tags: List<String>,
    val datasetKey: DatasetKey?,
    val querySpec: WidgetQuerySpec?,
    val vizSpec: WidgetVizSpec,
    val isSystem: Boolean,
    val version: Int,
    val archived: Boolean,
    val usageCount: Int = 0,
    val usedOnPages: List<String> = emptyList(),
    val createdAt: Instant,
    val updatedAt: Instant,
)

data class PageWidgetInstance(
    val id: String,
    val pageId: String,
    val widgetDefinitionId: String?,
    val kind: WidgetKind,
    val titleOverride: String?,
    val descriptionOverride: String?,
    val queryOverride: WidgetQuerySpec?,
    val vizOverride: WidgetVizSpec?,
    val layout: LayoutSpec,
    val locked: Boolean,
    val sortOrder: Int,
    val createdAt: Instant,
    val updatedAt: Instant,
)

data class PageWidgetResolved(
    val instance: PageWidgetInstance,
    val definition: WidgetDefinition?,
    val effectiveTitle: String,
    val effectiveDescription: String?,
    val effectiveQuery: WidgetQuerySpec?,
    val effectiveViz: WidgetVizSpec,
)

data class AnnotationV2(
    val id: String,
    val targetKind: AnnotationTargetKind,
    val pageWidgetInstanceId: String?,
    val scopeDate: LocalDate?,
    val xValue: String?,
    val yValue: Double?,
    val annotationType: AnnotationTypeV2 = AnnotationTypeV2.NOTE,
    val name: String? = null,
    val description: String? = null,
    val title: String,
    val body: String?,
    val color: String?,
    val tags: List<String> = emptyList(),
    val commitRefs: List<AnnotationCommitRef> = emptyList(),
    val scope: Map<String, Any?>,
    val createdAt: Instant,
    val updatedAt: Instant,
)

data class AnnotationCommitRef(
    val repoId: String,
    val commitSha: String,
)

data class JiraConnectionSettings(
    val enabled: Boolean,
    val baseUrl: String?,
    val authMode: String,
    val verifyTls: Boolean,
    val issueKeyRegex: String,
    val projectKeys: List<String>,
    val lastValidatedAt: Instant?,
    val updatedAt: Instant,
)

data class PageSummaryDto(
    val id: String,
    val slug: String,
    val title: String,
    val description: String?,
    val icon: String?,
    val sortOrder: Int,
    val archived: Boolean,
    val timeRange: PageTimeRange? = null,
    val filters: List<PageFilterState> = emptyList(),
)

data class WidgetCatalogSummaryDto(
    val id: String,
    val slug: String,
    val kind: WidgetKind,
    val title: String,
    val description: String?,
    val tags: List<String>,
    val datasetKey: DatasetKey?,
    val isSystem: Boolean,
    val archived: Boolean,
    val usageCount: Int = 0,
    val usedOnPages: List<String> = emptyList(),
)

data class QuerySchemaFieldDto(
    val key: String,
    val label: String,
)

data class QuerySchemaDatasetDto(
    val key: DatasetKey,
    val label: String,
    val description: String,
    val supportsTime: Boolean,
    val supportsComparison: Boolean,
    val measures: List<QuerySchemaFieldDto>,
    val dimensions: List<QuerySchemaFieldDto>,
    val widgetKinds: List<WidgetKind>,
)

data class FeatureFlagsDto(
    val snapshots: Boolean,
    val jira: Boolean,
    val legacyOverview: Boolean,
)

data class BootstrapV2Response(
    val appName: String,
    val pages: List<PageSummaryDto>,
    val widgets: List<WidgetCatalogSummaryDto>,
    val datasets: List<QuerySchemaDatasetDto>,
    val featureFlags: FeatureFlagsDto,
    val jiraEnabled: Boolean,
)

data class PageStateRequest(
    val timeRange: PageTimeRange? = null,
    val filters: List<PageFilterState> = emptyList(),
)

data class CreatePageRequest(
    @field:NotBlank
    val title: String,
    val description: String? = null,
    val icon: String? = null,
    val timeRange: PageTimeRange? = null,
    val filters: List<PageFilterState> = emptyList(),
)

data class UpdatePageRequest(
    val title: String? = null,
    val description: String? = null,
    val icon: String? = null,
    val timeRange: PageTimeRange? = null,
    val filters: List<PageFilterState>? = null,
)

data class ReorderPagesRequest(
    @field:NotEmpty
    val pageIds: List<String>,
)

data class CreateWidgetDefinitionRequest(
    @field:NotBlank
    val title: String,
    val description: String? = null,
    val tags: List<String> = emptyList(),
    @field:NotNull
    val kind: WidgetKind,
    val datasetKey: DatasetKey? = null,
    @field:Valid
    val querySpec: WidgetQuerySpec? = null,
    @field:Valid
    val vizSpec: WidgetVizSpec = WidgetVizSpec(),
)

data class UpdateWidgetDefinitionRequest(
    val title: String? = null,
    val description: String? = null,
    val tags: List<String>? = null,
    val datasetKey: DatasetKey? = null,
    @field:Valid
    val querySpec: WidgetQuerySpec? = null,
    @field:Valid
    val vizSpec: WidgetVizSpec? = null,
)

data class AddPageWidgetRequest(
    val widgetDefinitionId: String? = null,
    @field:NotNull
    val kind: WidgetKind,
    @field:Valid
    val layout: LayoutSpec = LayoutSpec(),
    val titleOverride: String? = null,
    val descriptionOverride: String? = null,
    @field:Valid
    val queryOverride: WidgetQuerySpec? = null,
    @field:Valid
    val vizOverride: WidgetVizSpec? = null,
    val locked: Boolean = false,
)

data class UpdatePageWidgetRequest(
    val titleOverride: String? = null,
    val descriptionOverride: String? = null,
    @field:Valid
    val queryOverride: WidgetQuerySpec? = null,
    @field:Valid
    val vizOverride: WidgetVizSpec? = null,
    @field:Valid
    val layout: LayoutSpec? = null,
    val locked: Boolean? = null,
)

data class LayoutUpdateItem(
    @field:NotBlank
    val id: String,
    @field:Valid
    val layout: LayoutSpec,
)

data class ReorderLayoutRequest(
    @field:NotBlank
    val pageId: String,
    @field:NotEmpty
    val items: List<LayoutUpdateItem>,
)

data class CreateAnnotationV2Request(
    @field:NotNull
    val targetKind: AnnotationTargetKind,
    val pageWidgetInstanceId: String? = null,
    val scopeDate: LocalDate? = null,
    val xValue: String? = null,
    val yValue: Double? = null,
    val annotationType: AnnotationTypeV2 = AnnotationTypeV2.NOTE,
    val name: String? = null,
    val description: String? = null,
    val title: String? = null,
    val body: String? = null,
    val color: String? = null,
    val tags: List<String> = emptyList(),
    val commitRefs: List<AnnotationCommitRef> = emptyList(),
    val scope: Map<String, Any?> = emptyMap(),
)

data class UpdateAnnotationV2Request(
    val annotationType: AnnotationTypeV2? = null,
    val name: String? = null,
    val description: String? = null,
    val title: String? = null,
    val body: String? = null,
    val color: String? = null,
    val tags: List<String>? = null,
    val commitRefs: List<AnnotationCommitRef>? = null,
)

data class QueryPreviewRequest(
    @field:NotNull
    val widgetKind: WidgetKind,
    @field:Valid
    val query: WidgetQuerySpec? = null,
)

data class ExecuteQueryRequest(
    val widgetInstanceId: String? = null,
    @field:Valid
    val effectiveQuery: WidgetQuerySpec? = null,
    @field:Valid
    val runtimeFilters: List<FilterSpec> = emptyList(),
)

data class QueryExecutionResponse(
    val dataset: DatasetKey,
    val rows: List<Map<String, Any?>>,
    val totals: Map<String, Number>,
    val effectiveQuery: WidgetQuerySpec,
    val comparisonRange: Map<String, String>? = null,
)

data class SyncLogEntryDto(
    val eventKey: String,
    val sourceKind: String,
    val repoId: String? = null,
    val label: String,
    val detail: String? = null,
    val status: String,
    val startedAt: Instant? = null,
    val finishedAt: Instant? = null,
    val progressPercent: Int? = null,
)

data class DrilldownSeriesSelection(
    val field: String,
    val value: String,
)

data class DayDrilldownRequest(
    @field:NotNull
    val selectedDate: LocalDate,
    @field:NotNull
    val dataset: DatasetKey,
    @field:Valid
    val effectiveFilters: List<FilterSpec> = emptyList(),
    val selectedSeries: DrilldownSeriesSelection? = null,
)

data class DrilldownSummary(
    val date: LocalDate,
    val linesAdded: Long,
    val linesRemoved: Long,
    val netLines: Long,
    val commitsCount: Long,
    val filesChangedCount: Long,
    val contributorsCount: Long,
    val jiraIssuesCount: Long,
)

data class DrilldownCommitRow(
    val repoId: String,
    val commitSha: String,
    val author: String,
    val authoredAt: String,
    val subject: String,
    val linesAdded: Long,
    val linesRemoved: Long,
    val issueKeys: List<String>,
)

data class DrilldownFileRow(
    val repoId: String,
    val filePath: String,
    val language: String?,
    val category: String?,
    val subtype: String?,
    val linesAdded: Long,
    val linesRemoved: Long,
)

data class DrilldownContributorRow(
    val author: String,
    val linesAdded: Long,
    val linesRemoved: Long,
    val commitsCount: Long,
    val filesChangedCount: Long,
)

data class DrilldownIssueRow(
    val issueKey: String,
    val summary: String?,
    val issueType: String?,
    val status: String?,
    val priority: String?,
    val browseUrl: String?,
)

data class DayDrilldownResponse(
    val summary: DrilldownSummary,
    val commits: List<DrilldownCommitRow>,
    val files: List<DrilldownFileRow>,
    val contributors: List<DrilldownContributorRow>,
    val jiraIssues: List<DrilldownIssueRow>,
)

data class JiraSettingsResponse(
    val enabled: Boolean,
    val baseUrl: String?,
    val authMode: String,
    val verifyTls: Boolean,
    val issueKeyRegex: String,
    val projectKeys: List<String>,
    val lastValidatedAt: Instant?,
    val secretConfigured: Boolean,
)

data class UpdateJiraSettingsRequest(
    val enabled: Boolean,
    val baseUrl: String?,
    val verifyTls: Boolean,
    val issueKeyRegex: String,
    val projectKeys: List<String> = emptyList(),
)

data class JiraSecretRequest(
    @field:NotBlank
    val token: String,
)

data class JiraConnectionTestResponse(
    val ok: Boolean,
    val message: String,
)

data class JiraSyncStatusResponse(
    val enabled: Boolean,
    val configured: Boolean,
    val secretConfigured: Boolean,
    val lastValidatedAt: Instant?,
    val lastSyncAt: Instant?,
    val cachedIssues: Int,
    val linkedCommits: Int,
)

data class SnapshotStatusItem(
    val repoId: String,
    val refName: String,
    val nextSnapshotDate: LocalDate?,
    val status: String?,
    val latestSnapshotDate: LocalDate?,
)

data class SnapshotStatusResponse(
    val items: List<SnapshotStatusItem>,
)

data class SnapshotTriggerResponse(
    val accepted: Boolean,
    val rebuiltRepos: List<String>,
)

data class CommitFileChangeDto(
    val filePath: String,
    val oldPath: String?,
    val language: String?,
    val category: String?,
    val subtype: String?,
    val linesAdded: Int,
    val linesRemoved: Int,
    val isBinary: Boolean,
)

data class CommitDetailResponse(
    val repoId: String,
    val commitSha: String,
    val authorName: String,
    val authorEmail: String?,
    val authoredAt: Instant,
    val committedAt: Instant,
    val subject: String,
    val parentCommitShas: List<String>,
    val linesAdded: Int,
    val linesRemoved: Int,
    val files: List<CommitFileChangeDto>,
    val annotations: List<AnnotationV2>,
)

data class EditorLaunchRequest(
    val repoId: String,
    val commitSha: String,
    val filePaths: List<String> = emptyList(),
)

data class EditorLaunchResponse(
    val available: Boolean,
    val reason: String? = null,
    val repoId: String,
    val commitSha: String,
    val repoPath: String? = null,
    val editorCommand: String? = null,
    val filePaths: List<String> = emptyList(),
)
