package com.company.throughput.config

import com.company.throughput.v2.model.AddPageWidgetRequest
import com.company.throughput.v2.model.AggregationType
import com.company.throughput.v2.model.AnnotationCommitRef
import com.company.throughput.v2.model.AnnotationTargetKind
import com.company.throughput.v2.model.AnnotationTypeV2
import com.company.throughput.v2.model.AnnotationV2
import com.company.throughput.v2.model.BootstrapV2Response
import com.company.throughput.v2.model.CommitDetailResponse
import com.company.throughput.v2.model.CommitFileChangeDto
import com.company.throughput.v2.model.ComparisonModeV2
import com.company.throughput.v2.model.ComparisonSpec
import com.company.throughput.v2.model.CreateAnnotationV2Request
import com.company.throughput.v2.model.CreatePageRequest
import com.company.throughput.v2.model.CreateWidgetDefinitionRequest
import com.company.throughput.v2.model.DashboardPage
import com.company.throughput.v2.model.DatasetKey
import com.company.throughput.v2.model.DayDrilldownRequest
import com.company.throughput.v2.model.DayDrilldownResponse
import com.company.throughput.v2.model.DrilldownCommitRow
import com.company.throughput.v2.model.DrilldownContributorRow
import com.company.throughput.v2.model.DrilldownFileRow
import com.company.throughput.v2.model.DrilldownIssueRow
import com.company.throughput.v2.model.DrilldownSeriesSelection
import com.company.throughput.v2.model.DrilldownSummary
import com.company.throughput.v2.model.EditorLaunchRequest
import com.company.throughput.v2.model.EditorLaunchResponse
import com.company.throughput.v2.model.ExecuteQueryRequest
import com.company.throughput.v2.model.FeatureFlagsDto
import com.company.throughput.v2.model.FilterOperator
import com.company.throughput.v2.model.FilterSpec
import com.company.throughput.v2.model.JiraConnectionSettings
import com.company.throughput.v2.model.JiraConnectionTestResponse
import com.company.throughput.v2.model.JiraSecretRequest
import com.company.throughput.v2.model.JiraSettingsResponse
import com.company.throughput.v2.model.JiraSyncStatusResponse
import com.company.throughput.v2.model.LayoutSpec
import com.company.throughput.v2.model.LayoutUpdateItem
import com.company.throughput.v2.model.MeasureSpec
import com.company.throughput.v2.model.PageFilterState
import com.company.throughput.v2.model.PageStateRequest
import com.company.throughput.v2.model.PageSummaryDto
import com.company.throughput.v2.model.PageTimeRange
import com.company.throughput.v2.model.PageWidgetInstance
import com.company.throughput.v2.model.PageWidgetResolved
import com.company.throughput.v2.model.QueryExecutionResponse
import com.company.throughput.v2.model.QueryPreviewRequest
import com.company.throughput.v2.model.QuerySchemaDatasetDto
import com.company.throughput.v2.model.QuerySchemaFieldDto
import com.company.throughput.v2.model.ReorderLayoutRequest
import com.company.throughput.v2.model.ReorderPagesRequest
import com.company.throughput.v2.model.SnapshotStatusItem
import com.company.throughput.v2.model.SnapshotStatusResponse
import com.company.throughput.v2.model.SnapshotTriggerResponse
import com.company.throughput.v2.model.SortDirection
import com.company.throughput.v2.model.SortSpec
import com.company.throughput.v2.model.SyncLogEntryDto
import com.company.throughput.v2.model.TimeBucket
import com.company.throughput.v2.model.UpdateAnnotationV2Request
import com.company.throughput.v2.model.UpdateJiraSettingsRequest
import com.company.throughput.v2.model.UpdatePageRequest
import com.company.throughput.v2.model.UpdatePageWidgetRequest
import com.company.throughput.v2.model.UpdateWidgetDefinitionRequest
import com.company.throughput.v2.model.WidgetCatalogSummaryDto
import com.company.throughput.v2.model.WidgetDefinition
import com.company.throughput.v2.model.WidgetKind
import com.company.throughput.v2.model.WidgetQuerySpec
import com.company.throughput.v2.model.WidgetVizSpec
import org.springframework.aot.hint.MemberCategory
import org.springframework.aot.hint.RuntimeHints
import org.springframework.aot.hint.RuntimeHintsRegistrar

class ConfigRuntimeHints : RuntimeHintsRegistrar {
    override fun registerHints(hints: RuntimeHints, classLoader: ClassLoader?) {
        val memberCategories = arrayOf(
            MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
            MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS,
            MemberCategory.INVOKE_DECLARED_METHODS,
            MemberCategory.INVOKE_PUBLIC_METHODS,
            MemberCategory.DECLARED_FIELDS,
        )

        registerTypes(
            hints,
            memberCategories,
            listOf(
                EditableDashboardConfig::class.java,
                AppProperties::class.java,
                GitProperties::class.java,
                GitAuthProperties::class.java,
                AuthorsProperties::class.java,
                AuthorConfig::class.java,
                RepoConfig::class.java,
                ClassificationProperties::class.java,
                ClassificationRuleConfig::class.java,
                UiDefaultsProperties::class.java,
                SyncWindowProperties::class.java,
                AppearanceProperties::class.java,
            ),
        )

        registerTypes(
            hints,
            memberCategories,
            listOf(
                WidgetKind::class.java,
                DatasetKey::class.java,
                TimeBucket::class.java,
                AggregationType::class.java,
                FilterOperator::class.java,
                ComparisonModeV2::class.java,
                SortDirection::class.java,
                AnnotationTypeV2::class.java,
                AnnotationTargetKind::class.java,
                PageTimeRange::class.java,
                PageFilterState::class.java,
                MeasureSpec::class.java,
                FilterSpec::class.java,
                SortSpec::class.java,
                ComparisonSpec::class.java,
                WidgetQuerySpec::class.java,
                WidgetVizSpec::class.java,
                LayoutSpec::class.java,
                DashboardPage::class.java,
                WidgetDefinition::class.java,
                PageWidgetInstance::class.java,
                PageWidgetResolved::class.java,
                AnnotationV2::class.java,
                AnnotationCommitRef::class.java,
                JiraConnectionSettings::class.java,
                PageSummaryDto::class.java,
                WidgetCatalogSummaryDto::class.java,
                QuerySchemaFieldDto::class.java,
                QuerySchemaDatasetDto::class.java,
                FeatureFlagsDto::class.java,
                BootstrapV2Response::class.java,
                PageStateRequest::class.java,
                CreatePageRequest::class.java,
                UpdatePageRequest::class.java,
                ReorderPagesRequest::class.java,
                CreateWidgetDefinitionRequest::class.java,
                UpdateWidgetDefinitionRequest::class.java,
                AddPageWidgetRequest::class.java,
                UpdatePageWidgetRequest::class.java,
                LayoutUpdateItem::class.java,
                ReorderLayoutRequest::class.java,
                CreateAnnotationV2Request::class.java,
                UpdateAnnotationV2Request::class.java,
                QueryPreviewRequest::class.java,
                ExecuteQueryRequest::class.java,
                QueryExecutionResponse::class.java,
                SyncLogEntryDto::class.java,
                DrilldownSeriesSelection::class.java,
                DayDrilldownRequest::class.java,
                DrilldownSummary::class.java,
                DrilldownCommitRow::class.java,
                DrilldownFileRow::class.java,
                DrilldownContributorRow::class.java,
                DrilldownIssueRow::class.java,
                DayDrilldownResponse::class.java,
                JiraSettingsResponse::class.java,
                UpdateJiraSettingsRequest::class.java,
                JiraSecretRequest::class.java,
                JiraConnectionTestResponse::class.java,
                JiraSyncStatusResponse::class.java,
                SnapshotStatusItem::class.java,
                SnapshotStatusResponse::class.java,
                SnapshotTriggerResponse::class.java,
                CommitFileChangeDto::class.java,
                CommitDetailResponse::class.java,
                EditorLaunchRequest::class.java,
                EditorLaunchResponse::class.java,
            ),
        )

        registerTypes(
            hints,
            memberCategories,
            listOf(
                emptyList<Any>()::class.java,
                emptySet<Any>()::class.java,
                emptyMap<Any, Any>()::class.java,
            ),
        )
    }

    private fun registerTypes(
        hints: RuntimeHints,
        memberCategories: Array<MemberCategory>,
        types: List<Class<*>>,
    ) {
        types.distinct().forEach { type ->
            hints.reflection().registerType(type, *memberCategories)
        }
    }
}
