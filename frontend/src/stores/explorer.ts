import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { Router, RouteLocationNormalizedLoaded } from 'vue-router'
import { queryAnalytics } from '../api/client'
import {
  createAnnotationV2,
  deleteAnnotationV2,
  listAnnotationsV2,
  loadDayDrilldown,
  updateAnnotationV2,
} from '../api/workspace'
import type { AnnotationV2, AnnotationTypeV2, DayDrilldownResponse } from '../types/workspace'
import { useDashboardStore } from './dashboard'

export const useExplorerStore = defineStore('explorer', () => {
  const rangePreset = ref<'7d' | '14d' | '30d' | '90d'>('14d')
  const selectedRepoIds = ref<string[]>([])
  const selectedDate = ref('')
  const selectedCommitSha = ref<string | null>(null)
  const analytics = ref<{ day: string; value: number }[]>([])
  const dayDetail = ref<DayDrilldownResponse | null>(null)
  const annotations = ref<AnnotationV2[]>([])
  const loadingAnalytics = ref(false)
  const loadingDay = ref(false)
  const loadingAnnotations = ref(false)
  const annotationDialogOpen = ref(false)
  const editingAnnotation = ref<AnnotationV2 | null>(null)
  const initialized = ref(false)

  // --- Helpers ---

  function normalizeRangePreset(value: unknown): '7d' | '14d' | '30d' | '90d' {
    return value === '7d' || value === '14d' || value === '30d' || value === '90d' ? value : '14d'
  }

  function normalizeRepoIdsFromQuery(value: unknown): string[] {
    if (Array.isArray(value)) {
      return value
        .flatMap((entry) => (typeof entry === 'string' ? entry.split(',') : []))
        .map((entry) => entry.trim())
        .filter(Boolean)
    }
    if (typeof value === 'string') {
      return value
        .split(',')
        .map((entry) => entry.trim())
        .filter(Boolean)
    }
    return []
  }

  // --- Computed ---

  const rangeDays = computed(() => {
    switch (rangePreset.value) {
      case '7d': return 7
      case '14d': return 14
      case '30d': return 30
      case '90d': return 90
    }
  })

  const timeLabel = computed(() => {
    switch (rangePreset.value) {
      case '7d': return 'Last 7 days'
      case '14d': return 'Last 14 days'
      case '30d': return 'Last 30 days'
      case '90d': return 'Last 90 days'
    }
  })

  const repoOptions = computed(() => useDashboardStore().bootstrap?.repos ?? [])

  const repoLabel = computed(() => {
    if (!selectedRepoIds.value.length) {
      return 'All repositories'
    }
    if (selectedRepoIds.value.length === 1) {
      return repoOptions.value.find((repo) => repo.id === selectedRepoIds.value[0])?.displayName ?? selectedRepoIds.value[0]
    }
    return `${selectedRepoIds.value.length} repositories`
  })

  const dayAnnotations = computed(() =>
    annotations.value.filter((a) => a.scopeDate === selectedDate.value),
  )

  const fallbackCommits = computed(() => {
    const repoId = selectedRepoIds.value[0] ?? repoOptions.value[0]?.id ?? 'frontend'
    const day = selectedDate.value || new Date().toISOString().slice(0, 10)
    return [
      {
        repoId,
        commitSha: 'a1b2c3d4e5f6',
        author: 'Eli',
        authoredAt: `${day}T10:12:00Z`,
        subject: 'Refine page toolbar and filters',
        linesAdded: 128,
        linesRemoved: 52,
        issueKeys: ['CFX-123'],
      },
      {
        repoId,
        commitSha: 'b2c3d4e5f6a7',
        author: 'Maya',
        authoredAt: `${day}T12:40:00Z`,
        subject: 'Tighten sync summary and log layout',
        linesAdded: 76,
        linesRemoved: 21,
        issueKeys: [],
      },
      {
        repoId,
        commitSha: 'c3d4e5f6a7b8',
        author: 'Jon',
        authoredAt: `${day}T14:05:00Z`,
        subject: 'Add annotation flow for explorer review',
        linesAdded: 93,
        linesRemoved: 19,
        issueKeys: ['UI-88'],
      },
    ]
  })

  const displayCommits = computed(() =>
    dayDetail.value?.commits.length ? dayDetail.value.commits : fallbackCommits.value,
  )

  const selectedCommit = computed(() => {
    if (!selectedCommitSha.value) {
      return displayCommits.value[0] ?? null
    }
    return (
      displayCommits.value.find((commit) => commit.commitSha === selectedCommitSha.value) ??
      displayCommits.value[0] ??
      null
    )
  })

  const displayCommitFiles = computed(() => {
    if (dayDetail.value?.files.length) {
      const activeCommit = selectedCommit.value
      if (!activeCommit) {
        return []
      }
      return dayDetail.value.files.filter((file) => file.repoId === activeCommit.repoId).slice(0, 12)
    }
    const repoId = selectedCommit.value?.repoId ?? fallbackCommits.value[0].repoId
    return [
      { repoId, filePath: 'frontend/src/views/WorkspacePageView.vue', language: 'Vue', category: 'frontend', subtype: 'view', linesAdded: 42, linesRemoved: 11 },
      { repoId, filePath: 'frontend/src/components/PageGrid.vue', language: 'Vue', category: 'frontend', subtype: 'component', linesAdded: 31, linesRemoved: 8 },
      { repoId, filePath: 'backend/src/main/kotlin/.../WorkspaceServices.kt', language: 'Kotlin', category: 'backend', subtype: 'service', linesAdded: 18, linesRemoved: 6 },
    ]
  })

  // --- Actions ---

  async function loadAnalytics() {
    loadingAnalytics.value = true
    try {
      const today = new Date()
      const start = new Date(today)
      start.setDate(today.getDate() - (rangeDays.value - 1))
      const response = await queryAnalytics({
        dateRange: {
          from: start.toISOString().slice(0, 10),
          to: today.toISOString().slice(0, 10),
        },
        metric: 'commit_count',
        groupBy: 'none',
        filters: {
          authorIds: [],
          repoIds: selectedRepoIds.value,
          languages: [],
          categories: [],
          subtypes: [],
          productCodes: [],
          cohorts: [],
        },
      })
      analytics.value =
        response.series[0]?.points.map((point) => ({ day: point.day, value: point.value })) ?? []
      const visibleDays = new Set(analytics.value.map((point) => point.day))
      if (!selectedDate.value || !visibleDays.has(selectedDate.value)) {
        selectedDate.value =
          analytics.value.at(-1)?.day ?? today.toISOString().slice(0, 10)
      }
    } finally {
      loadingAnalytics.value = false
    }
  }

  async function loadDay() {
    if (!selectedDate.value) {
      return
    }
    loadingDay.value = true
    try {
      dayDetail.value = await loadDayDrilldown({
        selectedDate: selectedDate.value,
        dataset: 'day_activity',
        effectiveFilters: [],
        selectedSeries: null,
      })
      if (!dayDetail.value.commits.some((commit) => commit.commitSha === selectedCommitSha.value)) {
        selectedCommitSha.value = dayDetail.value.commits[0]?.commitSha ?? null
      }
    } finally {
      loadingDay.value = false
    }
  }

  async function loadAnnotationsForDay() {
    if (!selectedDate.value) {
      return
    }
    loadingAnnotations.value = true
    try {
      annotations.value = await listAnnotationsV2({
        dateFrom: selectedDate.value,
        dateTo: selectedDate.value,
      })
    } finally {
      loadingAnnotations.value = false
    }
  }

  async function refreshExplorer() {
    await Promise.all([loadAnalytics(), loadDay(), loadAnnotationsForDay()])
  }

  function selectDate(day: string) {
    selectedCommitSha.value = null
    selectedDate.value = day
  }

  function selectCommit(sha: string) {
    selectedCommitSha.value = sha
  }

  function setRangePreset(preset: '7d' | '14d' | '30d' | '90d') {
    rangePreset.value = preset
  }

  function setRepoFilters(ids: string[]) {
    selectedRepoIds.value = Array.from(
      new Set(
        ids
          .filter((id): id is string => typeof id === 'string')
          .map((id) => id.trim())
          .filter(Boolean),
      ),
    )
  }

  function toggleRepoFilter(id: string) {
    if (selectedRepoIds.value.includes(id)) {
      selectedRepoIds.value = selectedRepoIds.value.filter((repoId) => repoId !== id)
    } else {
      selectedRepoIds.value = [...selectedRepoIds.value, id]
    }
  }

  function openNewAnnotation() {
    editingAnnotation.value = null
    annotationDialogOpen.value = true
  }

  function editAnnotation(annotation: AnnotationV2) {
    editingAnnotation.value = annotation
    annotationDialogOpen.value = true
  }

  function closeAnnotationDialog() {
    annotationDialogOpen.value = false
    editingAnnotation.value = null
  }

  async function handleSaveAnnotation(payload: {
    name: string
    description: string
    annotationType: AnnotationTypeV2
    tags: string[]
  }) {
    if (!selectedDate.value) {
      return
    }
    if (editingAnnotation.value) {
      await updateAnnotationV2(editingAnnotation.value.id, {
        annotationType: payload.annotationType,
        name: payload.name,
        description: payload.description || null,
        title: payload.name,
        body: payload.description || null,
        tags: payload.tags,
      })
    } else {
      await createAnnotationV2({
        targetKind: 'global_date',
        scopeDate: selectedDate.value,
        annotationType: payload.annotationType,
        name: payload.name,
        description: payload.description || null,
        title: payload.name,
        body: payload.description || null,
        tags: payload.tags,
        commitRefs: [],
        scope: { surface: 'explorer' },
      })
    }
    annotationDialogOpen.value = false
    await loadAnnotationsForDay()
  }

  async function handleDeleteAnnotation() {
    if (!editingAnnotation.value) {
      return
    }
    await deleteAnnotationV2(editingAnnotation.value.id)
    annotationDialogOpen.value = false
    await loadAnnotationsForDay()
  }

  function syncQueryToUrl(router: Router) {
    const query: Record<string, string | string[]> = {}
    if (selectedDate.value) {
      query.date = selectedDate.value
    }
    if (rangePreset.value !== '14d') {
      query.range = rangePreset.value
    }
    if (selectedRepoIds.value.length > 0) {
      query.repo =
        selectedRepoIds.value.length === 1 ? selectedRepoIds.value[0] : selectedRepoIds.value
    }
    try {
      void router.replace({ name: 'explorer', query })
    } catch {
      // swallow NavigationFailure
    }
  }

  function initFromQuery(query: RouteLocationNormalizedLoaded['query']) {
    if (typeof query.date === 'string') {
      selectedDate.value = query.date
    }
    rangePreset.value = normalizeRangePreset(query.range)
    selectedRepoIds.value = normalizeRepoIdsFromQuery(query.repo)
    initialized.value = true
  }

  return {
    rangePreset,
    selectedRepoIds,
    selectedDate,
    selectedCommitSha,
    analytics,
    dayDetail,
    annotations,
    loadingAnalytics,
    loadingDay,
    loadingAnnotations,
    annotationDialogOpen,
    editingAnnotation,
    initialized,
    rangeDays,
    timeLabel,
    repoOptions,
    repoLabel,
    dayAnnotations,
    fallbackCommits,
    displayCommits,
    selectedCommit,
    displayCommitFiles,
    loadAnalytics,
    loadDay,
    loadAnnotationsForDay,
    refreshExplorer,
    selectDate,
    selectCommit,
    setRangePreset,
    setRepoFilters,
    toggleRepoFilter,
    openNewAnnotation,
    editAnnotation,
    closeAnnotationDialog,
    handleSaveAnnotation,
    handleDeleteAnnotation,
    syncQueryToUrl,
    initFromQuery,
  }
})
