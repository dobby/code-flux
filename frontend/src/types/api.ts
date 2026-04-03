export type Metric = 'lines_added' | 'lines_removed' | 'net_lines' | 'commit_count' | 'file_count'

export type GroupBy =
  | 'none'
  | 'author'
  | 'repo'
  | 'language'
  | 'category'
  | 'subtype'
  | 'product_code'
  | 'cohort'

export type ComparisonMode =
  | 'previous_equivalent_period'
  | 'same_period_last_year'
  | 'custom'

export type AnnotationType =
  | 'adoption'
  | 'process_change'
  | 'release'
  | 'incident'
  | 'milestone'
  | 'custom'

export interface DateRange {
  from: string
  to: string
}

export interface Repo {
  id: string
  displayName: string
  productCode: string | null
  enabled: boolean
}

export interface Author {
  id: string
  displayName: string
  cohort: string | null
}

export interface BootstrapResponse {
  appName: string
  lastSuccessfulSyncAt: string | null
  repos: Repo[]
  authors: Author[]
  uiDefaults: {
    defaultMetric: Metric
    defaultGroupBy: GroupBy
    defaultIncludeCategories: string[]
    defaultExcludeCategories: string[]
    defaultDateFrom: string | null
    defaultDateTo: string | null
  }
}

export interface ConfigFileResponse {
  path: string
  yaml: string
  restartRequired: boolean
  savedAt: string | null
}

export interface SyncRepoStatus {
  repoId: string
  status: 'SUCCESS' | 'FAILED' | 'RUNNING' | string
  lastSuccessfulSyncedAt: string | null
  lastErrorMessage: string | null
}

export interface SyncStatusResponse {
  running: boolean
  current: {
    syncRunId: number
    totalRepos: number
    completedRepos: number
    currentRepoId: string | null
    stage: string
    stopRequested: boolean
    progressPercent: number
  } | null
  lastRun: {
    syncRunId: number
    startedAt: string | null
    finishedAt: string | null
    status: 'SUCCESS' | 'FAILED' | 'RUNNING' | string
    message: string | null
  } | null
  repos: SyncRepoStatus[]
}

export interface FilterOptionsResponse {
  authors: Author[]
  repos: Array<Pick<Repo, 'id' | 'displayName'>>
  languages: string[]
  categories: string[]
  subtypes: string[]
  productCodes: string[]
}

export interface AnalyticsFilters {
  authorIds: string[]
  repoIds: string[]
  languages: string[]
  categories: string[]
  subtypes: string[]
  productCodes: string[]
  cohorts: string[]
}

export interface AnalyticsQueryRequest {
  dateRange: DateRange
  metric: Metric
  groupBy: GroupBy
  filters: AnalyticsFilters
}

export interface AnalyticsPoint {
  day: string
  value: number
}

export interface AnalyticsSeries {
  key: string
  label: string
  points: AnalyticsPoint[]
}

export interface AnalyticsTotals {
  linesAdded: number
  linesRemoved: number
  netLines: number
  commitCount: number
  fileCount: number
}

export interface AnalyticsQueryResponse {
  series: AnalyticsSeries[]
  totals: AnalyticsTotals
}

export interface AnalyticsCompareRequest {
  current: DateRange
  comparisonMode: ComparisonMode
  customComparison: DateRange | null
  metric: Metric
  filters: AnalyticsFilters
}

export interface AnalyticsCompareResponse {
  current: { from: string; to: string; totals: AnalyticsTotals }
  comparison: { from: string; to: string; totals: AnalyticsTotals }
  delta: {
    metricAbsolute: number
    metricPercentage: number | null
    linesAddedAbsolute: number | null
    linesAddedPercentage: number | null
  }
}

export interface Annotation {
  annotationId: number
  day: string
  title: string
  description: string | null
  type: AnnotationType
  colorToken: string | null
}

export interface CreateAnnotationRequest {
  day: string
  title: string
  description: string | null
  type: AnnotationType
  colorToken: string | null
}

export interface UpdateAnnotationRequest {
  title: string
  description: string | null
  type: AnnotationType
  colorToken: string | null
}
