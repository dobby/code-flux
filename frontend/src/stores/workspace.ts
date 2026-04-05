import { computed, reactive, ref } from 'vue'
import { defineStore } from 'pinia'
import {
  addPageWidget,
  archivePage,
  archiveWidgetDefinition,
  buildCurrentSnapshots,
  createAnnotationV2,
  createPage,
  createWidgetDefinition,
  deleteAnnotationV2,
  deletePageWidget,
  duplicatePage,
  duplicateWidgetDefinition,
  executeWidgetQuery,
  getJiraSettings,
  getJiraStatus,
  getQuerySchema,
  getSnapshotStatus,
  getV2Bootstrap,
  listWidgetDefinitions,
  listAnnotationsV2,
  listPageWidgets,
  loadDayDrilldown,
  reorderPageLayout,
  reorderPages,
  saveJiraSecret,
  syncJira,
  testJiraConnection,
  updateAnnotationV2,
  updateJiraSettings,
  updatePage,
  updatePageWidget,
  updateWidgetDefinition,
} from '../api/workspace'
import type {
  AnnotationV2,
  BootstrapV2Response,
  DayDrilldownResponse,
  FilterSpec,
  JiraSettingsResponse,
  JiraSyncStatusResponse,
  LayoutSpec,
  PageSummary,
  PageWidgetResolved,
  QueryExecutionResponse,
  QuerySchemaDataset,
  SnapshotStatusResponse,
  WidgetDefinition,
  WidgetKind,
  WidgetQuerySpec,
  WidgetVizSpec,
} from '../types/workspace'

type SaveState = 'idle' | 'saving' | 'saved' | 'error'

export const useWorkspaceStore = defineStore('workspace', () => {
  const bootstrap = ref<BootstrapV2Response | null>(null)
  const pages = ref<PageSummary[]>([])
  const widgets = ref<WidgetDefinition[]>([])
  const datasets = ref<QuerySchemaDataset[]>([])
  const pageWidgets = reactive<Record<string, PageWidgetResolved[]>>({})
  const widgetData = reactive<Record<string, QueryExecutionResponse | undefined>>({})
  const widgetAnnotations = reactive<Record<string, AnnotationV2[]>>({})
  const pageFilters = reactive<Record<string, FilterSpec[]>>({})
  const saveStates = reactive<Record<string, SaveState>>({})
  const loadingPages = reactive<Record<string, boolean>>({})
  const loadingWidgets = reactive<Record<string, boolean>>({})
  const initialized = ref(false)
  const initializing = ref(false)
  const error = ref<string | null>(null)
  const editMode = ref(false)
  const selectedWidgetId = ref<string | null>(null)
  const jiraSettings = ref<JiraSettingsResponse | null>(null)
  const jiraStatus = ref<JiraSyncStatusResponse | null>(null)
  const snapshotStatus = ref<SnapshotStatusResponse | null>(null)
  const drilldown = reactive<{
    open: boolean
    loading: boolean
    sourceWidgetId: string | null
    selectedDate: string | null
    dataset: string | null
    data: DayDrilldownResponse | null
  }>({
    open: false,
    loading: false,
    sourceWidgetId: null,
    selectedDate: null,
    dataset: null,
    data: null,
  })

  const orderedPages = computed(() => pages.value.slice().sort((left, right) => left.sortOrder - right.sortOrder))

  async function initialize() {
    if (initialized.value || initializing.value) {
      return
    }
    initializing.value = true
    error.value = null
    try {
      const [bootstrapResponse, schemaResponse, jiraSettingsResponse, jiraStatusResponse, snapshotStatusResponse] = await Promise.all([
        getV2Bootstrap(),
        getQuerySchema(),
        getJiraSettings(),
        getJiraStatus(),
        getSnapshotStatus(),
      ])
      bootstrap.value = bootstrapResponse
      pages.value = bootstrapResponse.pages
      datasets.value = schemaResponse
      jiraSettings.value = jiraSettingsResponse
      jiraStatus.value = jiraStatusResponse
      snapshotStatus.value = snapshotStatusResponse
      initialized.value = true
    } catch (caught) {
      error.value = toMessage(caught)
      throw caught
    } finally {
      initializing.value = false
    }
  }

  async function refreshBootstrap() {
    const response = await getV2Bootstrap()
    bootstrap.value = response
    pages.value = response.pages
  }

  async function loadWidgetCatalog() {
    widgets.value = await listWidgetDefinitions()
  }

  async function ensurePage(pageId: string) {
    if (!initialized.value) {
      await initialize()
    }
    if (pageWidgets[pageId]) {
      return
    }
    await loadPage(pageId)
  }

  async function loadPage(pageId: string) {
    loadingPages[pageId] = true
    error.value = null
    try {
      const widgetsForPage = await listPageWidgets(pageId)
      pageWidgets[pageId] = widgetsForPage
      if (!pageFilters[pageId]) {
        pageFilters[pageId] = []
      }
      await Promise.all(widgetsForPage.map((widget) => refreshWidgetData(widget.instance.id)))
    } catch (caught) {
      error.value = toMessage(caught)
      throw caught
    } finally {
      loadingPages[pageId] = false
    }
  }

  async function refreshWidgetData(instanceId: string) {
    const widget = findWidgetInstance(instanceId)
    if (!widget || !widget.effectiveQuery || widget.instance.kind === 'day_explorer') {
      return
    }
    loadingWidgets[instanceId] = true
    try {
      const [data, annotations] = await Promise.all([
        executeWidgetQuery({
          widgetInstanceId: instanceId,
          runtimeFilters: pageFilters[widget.instance.pageId] ?? [],
        }),
        listAnnotationsV2({ pageWidgetInstanceId: instanceId }),
      ])
      widgetData[instanceId] = data
      widgetAnnotations[instanceId] = annotations
    } finally {
      loadingWidgets[instanceId] = false
    }
  }

  async function createPageAndRefresh(payload: { title: string; description?: string | null }) {
    await createPage(payload)
    await refreshBootstrap()
  }

  async function renamePage(pageId: string, title: string) {
    await updatePage(pageId, { title })
    await refreshBootstrap()
  }

  async function duplicatePageAndRefresh(pageId: string) {
    const duplicated = await duplicatePage(pageId)
    await refreshBootstrap()
    return duplicated as PageSummary
  }

  async function archivePageAndRefresh(pageId: string) {
    await archivePage(pageId)
    await refreshBootstrap()
  }

  async function movePage(pageId: string, direction: -1 | 1) {
    const current = orderedPages.value
    const index = current.findIndex((page) => page.id === pageId)
    if (index < 0) {
      return
    }
    const nextIndex = index + direction
    if (nextIndex < 0 || nextIndex >= current.length) {
      return
    }
    const reordered = current.slice()
    const [page] = reordered.splice(index, 1)
    reordered.splice(nextIndex, 0, page)
    await reorderPages(reordered.map((item) => item.id))
    await refreshBootstrap()
  }

  async function addWidgetToPage(pageId: string, payload: { widgetDefinitionId?: string | null; kind: WidgetKind; vizOverride?: WidgetVizSpec | null }) {
    const nextLayout = getNextLayout(pageId, payload.kind)
    await addPageWidget(pageId, {
      widgetDefinitionId: payload.widgetDefinitionId ?? null,
      kind: payload.kind,
      layout: nextLayout,
      vizOverride: payload.vizOverride ?? null,
    })
    await loadPage(pageId)
  }

  async function updateSelectedWidget(payload: { titleOverride?: string | null; descriptionOverride?: string | null; locked?: boolean | null }) {
    if (!selectedWidgetId.value) {
      return
    }
    await updatePageWidget(selectedWidgetId.value, payload)
    const pageId = findPageIdForWidget(selectedWidgetId.value)
    if (pageId) {
      await loadPage(pageId)
    }
  }

  let layoutSaveTimer: number | null = null

  function queueLayoutSave(pageId: string, items: Array<{ id: string; layout: LayoutSpec }>) {
    saveStates[pageId] = 'saving'
    if (layoutSaveTimer != null) {
      window.clearTimeout(layoutSaveTimer)
    }
    layoutSaveTimer = window.setTimeout(async () => {
      try {
        await reorderPageLayout(pageId, items)
        saveStates[pageId] = 'saved'
        await loadPage(pageId)
      } catch {
        saveStates[pageId] = 'error'
      }
    }, 350)
  }

  async function removeWidget(instanceId: string) {
    const pageId = findPageIdForWidget(instanceId)
    await deletePageWidget(instanceId)
    if (pageId) {
      await loadPage(pageId)
    }
  }

  async function openDrilldown(payload: { pageId: string; widgetId: string; selectedDate: string; dataset: string; selectedSeries?: { field: string; value: string } | null }) {
    drilldown.open = true
    drilldown.loading = true
    drilldown.sourceWidgetId = payload.widgetId
    drilldown.selectedDate = payload.selectedDate
    drilldown.dataset = payload.dataset
    try {
      drilldown.data = await loadDayDrilldown({
        selectedDate: payload.selectedDate,
        dataset: payload.dataset,
        effectiveFilters: pageFilters[payload.pageId] ?? [],
        selectedSeries: payload.selectedSeries ?? null,
      })
    } finally {
      drilldown.loading = false
    }
  }

  function closeDrilldown() {
    drilldown.open = false
  }

  async function createPointAnnotation(payload: {
    pageWidgetInstanceId: string
    scopeDate: string
    xValue: string
    title: string
    body?: string | null
  }) {
    const annotation = await createAnnotationV2({
      targetKind: 'widget_point',
      pageWidgetInstanceId: payload.pageWidgetInstanceId,
      scopeDate: payload.scopeDate,
      xValue: payload.xValue,
      title: payload.title,
      body: payload.body ?? null,
      color: 'accent',
      scope: { source: 'chart' },
    })
    widgetAnnotations[payload.pageWidgetInstanceId] = [
      ...(widgetAnnotations[payload.pageWidgetInstanceId] ?? []),
      annotation,
    ]
    return annotation
  }

  async function updatePointAnnotation(annotationId: string, pageWidgetInstanceId: string, payload: { title: string; body?: string | null; color?: string | null }) {
    const updated = await updateAnnotationV2(annotationId, payload)
    widgetAnnotations[pageWidgetInstanceId] = (widgetAnnotations[pageWidgetInstanceId] ?? []).map((annotation) =>
      annotation.id === annotationId ? updated : annotation,
    )
  }

  async function deletePointAnnotation(annotationId: string, pageWidgetInstanceId: string) {
    await deleteAnnotationV2(annotationId)
    widgetAnnotations[pageWidgetInstanceId] = (widgetAnnotations[pageWidgetInstanceId] ?? []).filter((annotation) => annotation.id !== annotationId)
  }

  async function saveWidgetDefinition(payload: {
    widgetId?: string
    title: string
    description?: string | null
    tags?: string[]
    kind: WidgetKind
    datasetKey?: string | null
    querySpec?: WidgetQuerySpec | null
    vizSpec?: WidgetVizSpec
  }) {
    const widget = payload.widgetId
      ? await updateWidgetDefinition(payload.widgetId, payload)
      : await createWidgetDefinition(payload)
    await loadWidgetCatalog()
    await refreshBootstrap()
    return widget
  }

  async function duplicateWidget(widgetId: string) {
    const widget = await duplicateWidgetDefinition(widgetId)
    await loadWidgetCatalog()
    return widget
  }

  async function archiveWidget(widgetId: string) {
    await archiveWidgetDefinition(widgetId)
    await loadWidgetCatalog()
    await refreshBootstrap()
  }

  async function refreshJira() {
    jiraSettings.value = await getJiraSettings()
    jiraStatus.value = await getJiraStatus()
  }

  async function saveJiraConfiguration(payload: {
    enabled: boolean
    baseUrl?: string | null
    verifyTls: boolean
    issueKeyRegex: string
    projectKeys: string[]
    token?: string | null
  }) {
    await updateJiraSettings(payload)
    if (payload.token?.trim()) {
      if (window.codeFluxSecure?.saveJiraToken) {
        await window.codeFluxSecure.saveJiraToken(payload.token)
      } else {
        await saveJiraSecret({ token: payload.token })
      }
    }
    await refreshJira()
  }

  async function testJira() {
    return testJiraConnection()
  }

  async function runJiraSync() {
    jiraStatus.value = await syncJira()
  }

  async function buildSnapshots() {
    await buildCurrentSnapshots()
    snapshotStatus.value = await getSnapshotStatus()
  }

  async function refreshSnapshots() {
    snapshotStatus.value = await getSnapshotStatus()
  }

  function findPageIdForWidget(widgetId: string) {
    return Object.entries(pageWidgets).find(([, items]) => items.some((item) => item.instance.id === widgetId))?.[0] ?? null
  }

  function findWidgetInstance(widgetId: string) {
    const entry = Object.values(pageWidgets).flat().find((item) => item.instance.id === widgetId)
    return entry ?? null
  }

  function getNextLayout(pageId: string, kind: WidgetKind): LayoutSpec {
    const widgetsForPage = pageWidgets[pageId] ?? []
    const nextY = widgetsForPage.reduce((maxY, widget) => Math.max(maxY, widget.instance.layout.y + widget.instance.layout.h), 0)
    if (kind === 'header_block') {
      return { x: 0, y: nextY, w: 12, h: 2, minW: 6, minH: 2 }
    }
    if (kind === 'markdown_block') {
      return { x: 0, y: nextY, w: 12, h: 4, minW: 6, minH: 3 }
    }
    if (kind === 'metric_card') {
      return { x: 0, y: nextY, w: 4, h: 4, minW: 3, minH: 3 }
    }
    if (kind === 'distribution') {
      return { x: 0, y: nextY, w: 6, h: 8, minW: 4, minH: 5 }
    }
    if (kind === 'day_explorer') {
      return { x: 0, y: nextY, w: 12, h: 8, minW: 6, minH: 6 }
    }
    return { x: 0, y: nextY, w: 12, h: 8, minW: 6, minH: 6 }
  }

  function setSelectedWidget(widgetId: string | null) {
    selectedWidgetId.value = widgetId
  }

  function setEditMode(value: boolean) {
    editMode.value = value
  }

  function setPageFilters(pageId: string, filters: FilterSpec[]) {
    pageFilters[pageId] = filters
  }

  return {
    bootstrap,
    pages,
    widgets,
    datasets,
    pageWidgets,
    widgetData,
    widgetAnnotations,
    pageFilters,
    saveStates,
    loadingPages,
    loadingWidgets,
    initialized,
    initializing,
    error,
    orderedPages,
    editMode,
    selectedWidgetId,
    jiraSettings,
    jiraStatus,
    snapshotStatus,
    drilldown,
    initialize,
    refreshBootstrap,
    loadWidgetCatalog,
    ensurePage,
    loadPage,
    refreshWidgetData,
    createPageAndRefresh,
    renamePage,
    duplicatePageAndRefresh,
    archivePageAndRefresh,
    movePage,
    addWidgetToPage,
    updateSelectedWidget,
    queueLayoutSave,
    removeWidget,
    openDrilldown,
    closeDrilldown,
    createPointAnnotation,
    updatePointAnnotation,
    deletePointAnnotation,
    saveWidgetDefinition,
    duplicateWidget,
    archiveWidget,
    refreshJira,
    saveJiraConfiguration,
    testJira,
    runJiraSync,
    buildSnapshots,
    refreshSnapshots,
    findPageIdForWidget,
    setSelectedWidget,
    setEditMode,
    setPageFilters,
  }
})

function toMessage(caught: unknown) {
  return caught instanceof Error ? caught.message : 'Unexpected workspace error'
}
