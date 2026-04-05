export type WidgetKind =
  | 'time_series'
  | 'distribution'
  | 'metric_card'
  | 'data_table'
  | 'calendar_heatmap'
  | 'day_explorer'
  | 'header_block'
  | 'markdown_block'
  | 'divider_block'
  | 'spacer_block'

export type DatasetKey =
  | 'throughput_daily'
  | 'throughput_issue_daily'
  | 'repo_state_daily'
  | 'file_inventory_current'
  | 'day_activity'

export type TimeBucket = 'day' | 'week' | 'month'
export type AggregationType = 'sum' | 'count' | 'avg' | 'min' | 'max'
export type FilterOperator = 'in' | 'not_in' | 'eq' | 'neq' | 'gte' | 'lte' | 'between' | 'contains'
export type ComparisonModeV2 = 'none' | 'previous_period' | 'same_period_last_year' | 'custom_period'

export interface MeasureSpec {
  field: string
  aggregation: AggregationType
}

export interface FilterSpec {
  field: string
  op: FilterOperator
  values: string[]
}

export interface SortSpec {
  field: string
  direction: 'asc' | 'desc'
}

export interface ComparisonSpec {
  mode: ComparisonModeV2
  from?: string | null
  to?: string | null
}

export interface WidgetQuerySpec {
  dataset: DatasetKey
  timeBucket?: TimeBucket | null
  measure?: MeasureSpec | null
  groupBy: string[]
  series?: string | null
  filters: FilterSpec[]
  comparison?: ComparisonSpec | null
  sort: SortSpec[]
  limit?: number | null
}

export interface WidgetVizSpec {
  chartType?: string | null
  stackMode?: string | null
  cumulative?: boolean
  showLegend?: boolean
  showAnnotations?: boolean
  yAxisLabel?: string | null
  paletteKey?: string | null
  emptyStateMessage?: string | null
  markdown?: string | null
  headingLevel?: number | null
  body?: string | null
  columns?: string[]
}

export interface LayoutSpec {
  x: number
  y: number
  w: number
  h: number
  minW?: number | null
  minH?: number | null
  maxW?: number | null
  maxH?: number | null
}

export interface PageSummary {
  id: string
  slug: string
  title: string
  description: string | null
  icon: string | null
  sortOrder: number
  archived: boolean
}

export interface WidgetCatalogSummary {
  id: string
  slug: string
  kind: WidgetKind
  title: string
  description: string | null
  tags: string[]
  datasetKey: DatasetKey | null
  isSystem: boolean
  archived: boolean
}

export interface WidgetDefinition extends WidgetCatalogSummary {
  querySpec: WidgetQuerySpec | null
  vizSpec: WidgetVizSpec
  version: number
  createdAt: string
  updatedAt: string
}

export interface PageWidgetInstance {
  id: string
  pageId: string
  widgetDefinitionId: string | null
  kind: WidgetKind
  titleOverride: string | null
  descriptionOverride: string | null
  queryOverride: WidgetQuerySpec | null
  vizOverride: WidgetVizSpec | null
  layout: LayoutSpec
  locked: boolean
  sortOrder: number
  createdAt: string
  updatedAt: string
}

export interface PageWidgetResolved {
  instance: PageWidgetInstance
  definition: WidgetDefinition | null
  effectiveTitle: string
  effectiveDescription: string | null
  effectiveQuery: WidgetQuerySpec | null
  effectiveViz: WidgetVizSpec
}

export interface QuerySchemaField {
  key: string
  label: string
}

export interface QuerySchemaDataset {
  key: DatasetKey
  label: string
  description: string
  supportsTime: boolean
  supportsComparison: boolean
  measures: QuerySchemaField[]
  dimensions: QuerySchemaField[]
  widgetKinds: WidgetKind[]
}

export interface BootstrapV2Response {
  appName: string
  pages: PageSummary[]
  widgets: WidgetCatalogSummary[]
  datasets: QuerySchemaDataset[]
  featureFlags: {
    snapshots: boolean
    jira: boolean
    legacyOverview: boolean
  }
  jiraEnabled: boolean
}

export interface QueryExecutionResponse {
  dataset: DatasetKey
  rows: Array<Record<string, string | number | null>>
  totals: Record<string, number>
  effectiveQuery: WidgetQuerySpec
  comparisonRange?: Record<string, string> | null
}

export interface AnnotationV2 {
  id: string
  targetKind: 'global_date' | 'widget_point'
  pageWidgetInstanceId: string | null
  scopeDate: string | null
  xValue: string | null
  yValue: number | null
  title: string
  body: string | null
  color: string | null
  scope: Record<string, unknown>
  createdAt: string
  updatedAt: string
}

export interface DrilldownSummary {
  date: string
  linesAdded: number
  linesRemoved: number
  netLines: number
  commitsCount: number
  filesChangedCount: number
  contributorsCount: number
  jiraIssuesCount: number
}

export interface DrilldownCommitRow {
  repoId: string
  commitSha: string
  author: string
  authoredAt: string
  subject: string
  linesAdded: number
  linesRemoved: number
  issueKeys: string[]
}

export interface DrilldownFileRow {
  repoId: string
  filePath: string
  language: string | null
  category: string | null
  subtype: string | null
  linesAdded: number
  linesRemoved: number
}

export interface DrilldownContributorRow {
  author: string
  linesAdded: number
  linesRemoved: number
  commitsCount: number
  filesChangedCount: number
}

export interface DrilldownIssueRow {
  issueKey: string
  summary: string | null
  issueType: string | null
  status: string | null
  priority: string | null
  browseUrl: string | null
}

export interface DayDrilldownResponse {
  summary: DrilldownSummary
  commits: DrilldownCommitRow[]
  files: DrilldownFileRow[]
  contributors: DrilldownContributorRow[]
  jiraIssues: DrilldownIssueRow[]
}

export interface JiraSettingsResponse {
  enabled: boolean
  baseUrl: string | null
  authMode: string
  verifyTls: boolean
  issueKeyRegex: string
  projectKeys: string[]
  lastValidatedAt: string | null
  secretConfigured: boolean
}

export interface JiraSyncStatusResponse {
  enabled: boolean
  configured: boolean
  secretConfigured: boolean
  lastValidatedAt: string | null
  lastSyncAt: string | null
  cachedIssues: number
  linkedCommits: number
}

export interface SnapshotStatusItem {
  repoId: string
  refName: string
  nextSnapshotDate: string | null
  status: string | null
  latestSnapshotDate: string | null
}

export interface SnapshotStatusResponse {
  items: SnapshotStatusItem[]
}
