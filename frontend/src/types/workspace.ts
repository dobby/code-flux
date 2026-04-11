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
export type AnnotationTypeV2 = 'feature' | 'incident' | 'project' | 'note'
export type AnnotationTargetKind = 'global_date' | 'widget_point'
export type ActivityCompareMode = 'off' | 'previous_period' | 'previous_year' | 'custom_anchor_date' | 'custom_range'

export interface PageTimeRange {
  preset?: string | null
  from?: string | null
  to?: string | null
}

export interface PageFilterState {
  field: string
  op: FilterOperator
  values: string[]
  locked: boolean
}

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
  timeRange: PageTimeRange | null
  filters: PageFilterState[]
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
  usageCount: number
  usedOnPages: string[]
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
  targetKind: AnnotationTargetKind
  pageWidgetInstanceId: string | null
  scopeDate: string | null
  xValue: string | null
  yValue: number | null
  annotationType: AnnotationTypeV2
  name: string | null
  description: string | null
  title: string
  body: string | null
  color: string | null
  tags: string[]
  commitRefs: AnnotationCommitRef[]
  scope: Record<string, unknown>
  createdAt: string
  updatedAt: string
}

export interface AnnotationCommitRef {
  repoId: string
  commitSha: string
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

export interface ActivityCompareRequest {
  current: { from: string; to: string }
  mode: Exclude<ActivityCompareMode, 'off'>
  customAnchorDate?: string | null
  customRange?: { from: string; to: string } | null
  metric: 'lines_added' | 'lines_removed' | 'net_lines' | 'commit_count' | 'file_count'
  groupBy: 'none' | 'author' | 'repo' | 'language' | 'category' | 'subtype' | 'product_code' | 'cohort'
  filters: {
    authorIds: string[]
    repoIds: string[]
    languages: string[]
    categories: string[]
    subtypes: string[]
    productCodes: string[]
    cohorts: string[]
  }
}

export interface ActivityComparePoint {
  day: string
  value: number
}

export interface ActivityCompareSeries {
  key: string
  label: string
  points: ActivityComparePoint[]
}

export interface ActivityCompareTotals {
  linesAdded: number
  linesRemoved: number
  netLines: number
  commitCount: number
  fileCount: number
}

export interface ActivityComparePeriod {
  from: string
  to: string
  totals: ActivityCompareTotals
}

export interface ActivityCompareResponse {
  current: ActivityComparePeriod
  reference: ActivityComparePeriod
  delta: {
    absolute: number
    percentage: number | null
  }
  series: {
    current: ActivityCompareSeries[]
    reference: ActivityCompareSeries[]
    alignedReference: ActivityCompareSeries[]
  }
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

export interface CodebaseStructureRequest {
  repoId: string
  dateFrom: string
  dateTo: string
  authorIds: string[]
  languages: string[]
  categories: string[]
  productCodes: string[]
}

export interface CodebaseTreeNode {
  key: string
  label: string
  path: string
  kind: 'directory' | 'file'
  filesCount: number
  linesCount: number
  netLines: number
  children: CodebaseTreeNode[]
}

export interface CodebaseBreakdownRow {
  key: string
  label: string
  filesCount: number
  linesCount: number
  netLines: number
}

export interface CodebaseSummary {
  repoId: string
  refName: string | null
  commitSha: string | null
  lastSnapshotDate: string | null
  repoFiles: number
  repoLines: number
  visibleFiles: number
  visibleLines: number
  languagesCount: number
  categoriesCount: number
  activeFilesInRange: number
  netLinesInRange: number
}

export interface CodebaseStructureResponse {
  summary: CodebaseSummary
  tree: CodebaseTreeNode
  languages: CodebaseBreakdownRow[]
  categories: CodebaseBreakdownRow[]
  topDirectories: CodebaseBreakdownRow[]
  filterSemantics: string[]
}

export interface SyncLogEntry {
  eventKey: string
  sourceKind: string
  repoId: string | null
  label: string
  detail: string | null
  status: string
  startedAt: string | null
  finishedAt: string | null
  progressPercent: number | null
}

export interface CommitFileChange {
  filePath: string
  oldPath: string | null
  language: string | null
  category: string | null
  subtype: string | null
  linesAdded: number
  linesRemoved: number
  isBinary: boolean
}

export interface CommitDetailResponse {
  repoId: string
  commitSha: string
  authorName: string
  authorEmail: string | null
  authoredAt: string
  committedAt: string
  subject: string
  parentCommitShas: string[]
  linesAdded: number
  linesRemoved: number
  files: CommitFileChange[]
  annotations: AnnotationV2[]
}

export interface EditorLaunchRequest {
  repoId: string
  commitSha: string
  filePaths?: string[]
}

export interface FileOpenRequest {
  repoId: string
  commitSha: string
  filePath: string
}

export interface EditorLaunchResponse {
  available: boolean
  reason: string | null
  repoId: string
  commitSha: string
  repoPath: string | null
  editorCommand: string | null
  filePaths: string[]
}

export interface FileOpenResponse {
  available: boolean
  opened: boolean
  reason: string | null
  repoId: string
  commitSha: string
  filePath: string
  repoPath: string | null
  resolvedPath: string | null
}
