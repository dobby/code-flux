import { computed, reactive, ref } from 'vue'
import { defineStore } from 'pinia'
import {
  compareAnalytics,
  createAnnotation,
  deleteAnnotation,
  getBootstrap,
  getFilterOptions,
  getSyncStatus,
  listAnnotations,
  queryAnalytics,
  runSync,
  stopSync,
  updateAnnotation,
} from '../api/client'
import type {
  AnalyticsCompareResponse,
  AnalyticsFilters,
  AnalyticsQueryResponse,
  Annotation,
  AnnotationType,
  BootstrapResponse,
  ComparisonMode,
  FilterOptionsResponse,
  GroupBy,
  Metric,
  SyncStatusResponse,
} from '../types/api'

type DraftAnnotation = {
  annotationId: number | null
  day: string
  title: string
  description: string
  type: AnnotationType
  colorToken: string
}

function formatDateInput(date: Date): string {
  return date.toISOString().slice(0, 10)
}

function subtractDays(source: Date, days: number): Date {
  const next = new Date(source)
  next.setDate(next.getDate() - days)
  return next
}

function createDefaultFilters(): {
  dateRange: { from: string; to: string }
  metric: Metric
  groupBy: GroupBy
  filters: AnalyticsFilters
  comparisonMode: ComparisonMode
} {
  const today = new Date()
  return {
    dateRange: {
      from: formatDateInput(subtractDays(today, 90)),
      to: formatDateInput(today),
    },
    metric: 'lines_added',
    groupBy: 'author',
    filters: {
      authorIds: [],
      repoIds: [],
      languages: [],
      categories: ['production'],
      subtypes: [],
      productCodes: [],
      cohorts: [],
    },
    comparisonMode: 'previous_equivalent_period',
  }
}

export const useDashboardStore = defineStore('dashboard', () => {
  const bootstrap = ref<BootstrapResponse | null>(null)
  const options = ref<FilterOptionsResponse | null>(null)
  const syncStatus = ref<SyncStatusResponse | null>(null)
  const analytics = ref<AnalyticsQueryResponse | null>(null)
  const comparisonAnalytics = ref<AnalyticsQueryResponse | null>(null)
  const comparison = ref<AnalyticsCompareResponse | null>(null)
  const annotations = ref<Annotation[]>([])
  const loading = reactive({
    bootstrap: false,
    analytics: false,
    sync: false,
    annotations: false,
  })
  const error = ref<string | null>(null)
  const initialized = ref(false)
  const filterSheetOpen = ref(false)
  const annotationDialogOpen = ref(false)
  const chartMode = ref<'line' | 'area'>('area')
  const comparisonOverlayVisible = ref(true)
  const legendVisible = ref(true)
  const lastAnalyticsRequestId = ref(0)
  const lastComparisonRequestId = ref(0)
  const controls = reactive(createDefaultFilters())
  const draftAnnotation = reactive<DraftAnnotation>({
    annotationId: null,
    day: formatDateInput(new Date()),
    title: '',
    description: '',
    type: 'adoption',
    colorToken: 'accent',
  })

  const selectedMetricLabel = computed(() => {
    const labels: Record<Metric, string> = {
      lines_added: 'Lines Added',
      lines_removed: 'Lines Removed',
      net_lines: 'Net Lines',
      commit_count: 'Commit Count',
      file_count: 'File Count',
    }
    return labels[controls.metric]
  })

  const currentSync = computed(() => syncStatus.value?.current ?? null)
  const syncProgressPercent = computed(() => currentSync.value?.progressPercent ?? 0)
  const syncCurrentRepoLabel = computed(() => {
    const repoId = currentSync.value?.currentRepoId
    if (!repoId) {
      return null
    }
    return bootstrap.value?.repos.find((repo) => repo.id === repoId)?.displayName ?? repoId
  })
  const syncProgressLabel = computed(() => {
    if (!currentSync.value) {
      return null
    }
    return `${currentSync.value.completedRepos}/${currentSync.value.totalRepos} repositories`
  })

  let syncPollTimer: number | undefined

  async function initialize() {
    if (initialized.value) {
      return
    }

    loading.bootstrap = true
    error.value = null

    try {
      const [bootstrapResponse, filterOptions, syncStatusResponse] = await Promise.all([
        getBootstrap(),
        getFilterOptions(),
        getSyncStatus(),
      ])

      bootstrap.value = bootstrapResponse
      options.value = filterOptions
      applySyncStatus(syncStatusResponse)

      controls.metric = bootstrapResponse.uiDefaults.defaultMetric
      controls.groupBy = bootstrapResponse.uiDefaults.defaultGroupBy
      if (bootstrapResponse.uiDefaults.defaultDateFrom) {
        controls.dateRange.from = bootstrapResponse.uiDefaults.defaultDateFrom
      }
      if (bootstrapResponse.uiDefaults.defaultDateTo) {
        controls.dateRange.to = bootstrapResponse.uiDefaults.defaultDateTo
      }
      controls.filters.categories = [...bootstrapResponse.uiDefaults.defaultIncludeCategories]
      controls.filters.repoIds = bootstrapResponse.repos.filter((repo) => repo.enabled).map((repo) => repo.id)
      controls.filters.authorIds = bootstrapResponse.authors.map((author) => author.id)
      controls.filters.cohorts = bootstrapResponse.authors
        .map((author) => author.cohort)
        .filter((cohort): cohort is string => Boolean(cohort))

      initialized.value = true
      await Promise.all([refreshAnalytics(), refreshAnnotations()])
    } catch (caught) {
      error.value = toMessage(caught)
    } finally {
      loading.bootstrap = false
    }
  }

  async function refreshSyncStatus() {
    applySyncStatus(await getSyncStatus())
  }

  function applySyncStatus(status: SyncStatusResponse) {
    const wasRunning = syncStatus.value?.running ?? false
    syncStatus.value = status
    loading.sync = status.running

    if (status.running) {
      startSyncPolling()
    } else {
      stopSyncPolling()
      if (wasRunning && initialized.value) {
        void Promise.all([refreshAnalytics(), refreshAnnotations()])
      }
    }
  }

  function startSyncPolling() {
    if (syncPollTimer != null) {
      return
    }
    syncPollTimer = window.setInterval(() => {
      void refreshSyncStatus()
    }, 1500)
  }

  function stopSyncPolling() {
    if (syncPollTimer == null) {
      return
    }
    window.clearInterval(syncPollTimer)
    syncPollTimer = undefined
  }

  async function refreshAnalytics() {
    if (!initialized.value) {
      return
    }

    loading.analytics = true
    error.value = null
    const analyticsRequestId = ++lastAnalyticsRequestId.value
    const comparisonRequestId = ++lastComparisonRequestId.value

    try {
      const [analyticsResponse, comparisonResponse] = await Promise.all([
        queryAnalytics({
          dateRange: controls.dateRange,
          metric: controls.metric,
          groupBy: controls.groupBy,
          filters: controls.filters,
        }),
        compareAnalytics({
          current: controls.dateRange,
          comparisonMode: controls.comparisonMode,
          customComparison: null,
          metric: controls.metric,
          filters: controls.filters,
        }),
      ])

      if (analyticsRequestId === lastAnalyticsRequestId.value) {
        analytics.value = analyticsResponse
      }
      if (comparisonRequestId === lastComparisonRequestId.value) {
        comparison.value = comparisonResponse
        comparisonAnalytics.value = await queryAnalytics({
          dateRange: {
            from: comparisonResponse.comparison.from,
            to: comparisonResponse.comparison.to,
          },
          metric: controls.metric,
          groupBy: controls.groupBy,
          filters: controls.filters,
        })
      }
    } catch (caught) {
      if (analyticsRequestId === lastAnalyticsRequestId.value) {
        error.value = toMessage(caught)
      }
    } finally {
      loading.analytics = false
    }
  }

  async function refreshAnnotations() {
    loading.annotations = true
    try {
      annotations.value = await listAnnotations(controls.dateRange.from, controls.dateRange.to)
    } finally {
      loading.annotations = false
    }
  }

  async function triggerSync() {
    error.value = null
    try {
      loading.sync = true
      const response = await runSync()
      await refreshSyncStatus()
      if (response.status == 'REJECTED') {
        error.value = 'A sync is already running.'
      }
    } catch (caught) {
      error.value = toMessage(caught)
    } finally {
      if (!syncStatus.value?.running) {
        loading.sync = false
      }
    }
  }

  async function stopRunningSync() {
    error.value = null
    try {
      await stopSync()
      await refreshSyncStatus()
    } catch (caught) {
      error.value = toMessage(caught)
    }
  }

  function openCreateAnnotation(day?: string) {
    draftAnnotation.annotationId = null
    draftAnnotation.day = day ?? controls.dateRange.to
    draftAnnotation.title = ''
    draftAnnotation.description = ''
    draftAnnotation.type = 'adoption'
    draftAnnotation.colorToken = 'accent'
    annotationDialogOpen.value = true
  }

  function openEditAnnotation(annotation: Annotation) {
    draftAnnotation.annotationId = annotation.annotationId
    draftAnnotation.day = annotation.day
    draftAnnotation.title = annotation.title
    draftAnnotation.description = annotation.description ?? ''
    draftAnnotation.type = annotation.type
    draftAnnotation.colorToken = annotation.colorToken ?? 'accent'
    annotationDialogOpen.value = true
  }

  async function saveAnnotation() {
    const payload = {
      day: draftAnnotation.day,
      title: draftAnnotation.title,
      description: draftAnnotation.description || null,
      type: draftAnnotation.type,
      colorToken: draftAnnotation.colorToken || null,
    }

    if (draftAnnotation.annotationId == null) {
      await createAnnotation(payload)
    } else {
      await updateAnnotation(draftAnnotation.annotationId, {
        title: payload.title,
        description: payload.description,
        type: payload.type,
        colorToken: payload.colorToken,
      })
    }

    annotationDialogOpen.value = false
    await refreshAnnotations()
  }

  async function removeAnnotation(annotationId: number) {
    await deleteAnnotation(annotationId)
    annotationDialogOpen.value = false
    await refreshAnnotations()
  }

  return {
    analytics,
    annotations,
    annotationDialogOpen,
    bootstrap,
    chartMode,
    comparison,
    comparisonAnalytics,
    comparisonOverlayVisible,
    legendVisible,
    controls,
    draftAnnotation,
    error,
    filterSheetOpen,
    initialized,
    loading,
    options,
    selectedMetricLabel,
    currentSync,
    syncCurrentRepoLabel,
    syncProgressLabel,
    syncProgressPercent,
    syncStatus,
    initialize,
    openCreateAnnotation,
    openEditAnnotation,
    refreshAnalytics,
    refreshAnnotations,
    refreshSyncStatus,
    removeAnnotation,
    saveAnnotation,
    stopRunningSync,
    triggerSync,
  }
})

function toMessage(caught: unknown): string {
  if (caught instanceof Error) {
    return caught.message
  }
  return 'Unexpected dashboard error'
}
