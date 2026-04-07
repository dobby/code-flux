import type {
  AnnotationV2,
  AnnotationCommitRef,
  AnnotationTargetKind,
  AnnotationTypeV2,
  BootstrapV2Response,
  CommitDetailResponse,
  CodebaseStructureRequest,
  CodebaseStructureResponse,
  DayDrilldownResponse,
  EditorLaunchRequest,
  EditorLaunchResponse,
  FilterSpec,
  JiraSettingsResponse,
  JiraSyncStatusResponse,
  LayoutSpec,
  PageFilterState,
  PageSummary,
  PageTimeRange,
  PageWidgetResolved,
  QueryExecutionResponse,
  QuerySchemaDataset,
  SnapshotStatusResponse,
  WidgetDefinition,
  WidgetKind,
  WidgetQuerySpec,
  WidgetVizSpec,
} from '../types/workspace'

async function request<T>(input: string, init?: RequestInit): Promise<T> {
  const response = await fetch(input, {
    headers: {
      Accept: 'application/json',
      'Content-Type': 'application/json',
      ...init?.headers,
    },
    ...init,
  })

  if (!response.ok) {
    const fallback = `${response.status} ${response.statusText}`
    try {
      const body = (await response.json()) as { detail?: string; message?: string; title?: string }
      throw new Error(body.message ?? body.detail ?? body.title ?? fallback)
    } catch {
      throw new Error(fallback)
    }
  }

  if (response.status === 204) {
    return undefined as T
  }

  return (await response.json()) as T
}

export function getV2Bootstrap() {
  return request<BootstrapV2Response>('/api/v2/bootstrap')
}

export function listPages() {
  return request<BootstrapV2Response['pages']>('/api/v2/pages')
}

export function getPageState(pageId: string) {
  return request<PageSummary>(`/api/v2/pages/${pageId}/state`)
}

export function updatePageState(pageId: string, payload: { timeRange?: PageTimeRange | null; filters: PageFilterState[] }) {
  return request<PageSummary>(`/api/v2/pages/${pageId}/state`, {
    method: 'PUT',
    body: JSON.stringify(payload),
  })
}

export function createPage(payload: { title: string; description?: string | null; icon?: string | null }) {
  return request('/api/v2/pages', {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function updatePage(pageId: string, payload: { title?: string | null; description?: string | null; icon?: string | null }) {
  return request(`/api/v2/pages/${pageId}`, {
    method: 'PATCH',
    body: JSON.stringify(payload),
  })
}

export function duplicatePage(pageId: string) {
  return request(`/api/v2/pages/${pageId}/duplicate`, {
    method: 'POST',
  })
}

export function archivePage(pageId: string) {
  return request(`/api/v2/pages/${pageId}/archive`, {
    method: 'POST',
  })
}

export function reorderPages(pageIds: string[]) {
  return request('/api/v2/pages/reorder', {
    method: 'POST',
    body: JSON.stringify({ pageIds }),
  })
}

export function listPageWidgets(pageId: string) {
  return request<PageWidgetResolved[]>(`/api/v2/pages/${pageId}/widgets`)
}

export function addPageWidget(
  pageId: string,
  payload: {
    widgetDefinitionId?: string | null
    kind: WidgetKind
    layout: LayoutSpec
    titleOverride?: string | null
    descriptionOverride?: string | null
    queryOverride?: WidgetQuerySpec | null
    vizOverride?: WidgetVizSpec | null
    locked?: boolean
  },
) {
  return request<PageWidgetResolved>(`/api/v2/pages/${pageId}/widgets`, {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function updatePageWidget(
  instanceId: string,
  payload: {
    titleOverride?: string | null
    descriptionOverride?: string | null
    queryOverride?: WidgetQuerySpec | null
    vizOverride?: WidgetVizSpec | null
    layout?: LayoutSpec | null
    locked?: boolean | null
  },
) {
  return request<PageWidgetResolved>(`/api/v2/page-widgets/${instanceId}`, {
    method: 'PATCH',
    body: JSON.stringify(payload),
  })
}

export function deletePageWidget(instanceId: string) {
  return request(`/api/v2/page-widgets/${instanceId}`, {
    method: 'DELETE',
  })
}

export function reorderPageLayout(pageId: string, items: Array<{ id: string; layout: LayoutSpec }>) {
  return request('/api/v2/page-widgets/reorder-layout', {
    method: 'POST',
    body: JSON.stringify({ pageId, items }),
  })
}

export function listWidgetDefinitions() {
  return request<WidgetDefinition[]>('/api/v2/widgets')
}

export function getWidgetDefinition(widgetId: string) {
  return request<WidgetDefinition>(`/api/v2/widgets/${widgetId}`)
}

export function createWidgetDefinition(payload: {
  title: string
  description?: string | null
  tags?: string[]
  kind: WidgetKind
  datasetKey?: string | null
  querySpec?: WidgetQuerySpec | null
  vizSpec?: WidgetVizSpec
}) {
  return request<WidgetDefinition>('/api/v2/widgets', {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function updateWidgetDefinition(
  widgetId: string,
  payload: {
    title?: string | null
    description?: string | null
    tags?: string[] | null
    datasetKey?: string | null
    querySpec?: WidgetQuerySpec | null
    vizSpec?: WidgetVizSpec | null
  },
) {
  return request<WidgetDefinition>(`/api/v2/widgets/${widgetId}`, {
    method: 'PATCH',
    body: JSON.stringify(payload),
  })
}

export function duplicateWidgetDefinition(widgetId: string) {
  return request<WidgetDefinition>(`/api/v2/widgets/${widgetId}/duplicate`, {
    method: 'POST',
  })
}

export function archiveWidgetDefinition(widgetId: string) {
  return request(`/api/v2/widgets/${widgetId}/archive`, {
    method: 'POST',
  })
}

export function getQuerySchema() {
  return request<QuerySchemaDataset[]>('/api/v2/query/schema')
}

export function previewQuery(payload: { widgetKind: WidgetKind; query?: WidgetQuerySpec | null }) {
  return request<QueryExecutionResponse>('/api/v2/query/preview', {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function executeWidgetQuery(payload: { widgetInstanceId?: string | null; effectiveQuery?: WidgetQuerySpec | null; runtimeFilters?: FilterSpec[] }) {
  return request<QueryExecutionResponse>('/api/v2/query/execute', {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function listAnnotationsV2(params?: { pageWidgetInstanceId?: string; dateFrom?: string; dateTo?: string }) {
  const search = new URLSearchParams()
  if (params?.pageWidgetInstanceId) search.set('pageWidgetInstanceId', params.pageWidgetInstanceId)
  if (params?.dateFrom) search.set('dateFrom', params.dateFrom)
  if (params?.dateTo) search.set('dateTo', params.dateTo)
  const query = search.toString()
  return request<AnnotationV2[]>(`/api/v2/annotations${query ? `?${query}` : ''}`)
}

export function createAnnotationV2(payload: {
  targetKind: AnnotationTargetKind
  pageWidgetInstanceId?: string | null
  scopeDate?: string | null
  xValue?: string | null
  yValue?: number | null
  annotationType?: AnnotationTypeV2
  name?: string | null
  description?: string | null
  title?: string | null
  body?: string | null
  color?: string | null
  tags?: string[]
  commitRefs?: AnnotationCommitRef[]
  scope?: Record<string, unknown>
}) {
  return request<AnnotationV2>('/api/v2/annotations', {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function updateAnnotationV2(annotationId: string, payload: {
  annotationType?: AnnotationTypeV2 | null
  name?: string | null
  description?: string | null
  title?: string | null
  body?: string | null
  color?: string | null
  tags?: string[] | null
  commitRefs?: AnnotationCommitRef[] | null
}) {
  return request<AnnotationV2>(`/api/v2/annotations/${annotationId}`, {
    method: 'PATCH',
    body: JSON.stringify(payload),
  })
}

export function deleteAnnotationV2(annotationId: string) {
  return request(`/api/v2/annotations/${annotationId}`, {
    method: 'DELETE',
  })
}

export function loadDayDrilldown(payload: {
  selectedDate: string
  dataset: string
  effectiveFilters: FilterSpec[]
  selectedSeries?: { field: string; value: string } | null
}) {
  return request<DayDrilldownResponse>('/api/v2/drilldown/day', {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function getExplorerCommitDetail(repoId: string, commitSha: string) {
  return request<CommitDetailResponse>(`/api/v2/explorer/commit/${encodeURIComponent(repoId)}/${encodeURIComponent(commitSha)}`)
}

export function openCommitInEditor(payload: EditorLaunchRequest) {
  return request<EditorLaunchResponse>('/api/v2/explorer/open-in-editor', {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function getJiraSettings() {
  return request<JiraSettingsResponse>('/api/v2/settings/jira')
}

export function updateJiraSettings(payload: {
  enabled: boolean
  baseUrl?: string | null
  verifyTls: boolean
  issueKeyRegex: string
  projectKeys: string[]
}) {
  return request('/api/v2/settings/jira', {
    method: 'PUT',
    body: JSON.stringify(payload),
  })
}

export function saveJiraSecret(payload: { token: string }) {
  return request('/api/v2/settings/jira/secret', {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function testJiraConnection() {
  return request<{ ok: boolean; message: string }>('/api/v2/settings/jira/test', {
    method: 'POST',
  })
}

export function syncJira() {
  return request<JiraSyncStatusResponse>('/api/v2/jira/sync', {
    method: 'POST',
  })
}

export function getJiraStatus() {
  return request<JiraSyncStatusResponse>('/api/v2/jira/status')
}

export function buildCurrentSnapshots() {
  return request('/api/v2/snapshots/current', {
    method: 'POST',
  })
}

export function backfillSnapshots() {
  return request('/api/v2/snapshots/backfill', {
    method: 'POST',
  })
}

export function getSnapshotStatus() {
  return request<SnapshotStatusResponse>('/api/v2/snapshots/status')
}

export function getCodebaseStructure(payload: CodebaseStructureRequest) {
  return request<CodebaseStructureResponse>('/api/v2/codebase/structure', {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}
