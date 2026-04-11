import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import type { Router, RouteLocationNormalizedLoaded } from 'vue-router'
import { queryAnalytics } from '../api/client'
import {
  compareActivity,
  createAnnotationV2,
  deleteAnnotationV2,
  getActivityCommitDetail,
  listAnnotationsV2,
  loadDayDrilldown,
  updateAnnotationV2,
} from '../api/workspace'
import type {
  ActivityCompareMode,
  ActivityCompareResponse,
  AnnotationV2,
  AnnotationTypeV2,
  CommitDetailResponse,
  DayDrilldownResponse,
  FilterSpec,
} from '../types/workspace'
import type { AnalyticsQueryResponse, DateRange, GroupBy, Metric } from '../types/api'
import { useDashboardStore } from './dashboard'

export type ActivityChartStyle = 'bar' | 'line' | 'area'
export type ActivityRangePreset = '7d' | '14d' | '30d' | '90d' | 'custom'
export type ActivityExtraFilterKind = 'author' | 'language' | 'category' | 'product'
export type ActivityMetric = Metric | 'cumulative_net'
export type ActivityComparePresetMode = ActivityCompareMode

export const ACTIVITY_GROUP_BY_OPTIONS: Array<{ value: GroupBy; label: string }> = [
  { value: 'author', label: 'Author' },
  { value: 'repo', label: 'Repository' },
  { value: 'cohort', label: 'Cohort' },
  { value: 'language', label: 'Language' },
  { value: 'category', label: 'Category' },
  { value: 'subtype', label: 'Subtype' },
  { value: 'product_code', label: 'Product Code' },
  { value: 'none', label: 'Total' },
]

export const ACTIVITY_CHART_STYLE_OPTIONS: Array<{ value: ActivityChartStyle; label: string }> = [
  { value: 'line', label: 'Line' },
  { value: 'area', label: 'Area' },
  { value: 'bar', label: 'Bar' },
]

export const ACTIVITY_METRIC_OPTIONS: Array<{ value: ActivityMetric; label: string }> = [
  { value: 'lines_added', label: 'Lines Added' },
  { value: 'lines_removed', label: 'Lines Removed' },
  { value: 'net_lines', label: 'Net Lines' },
  { value: 'cumulative_net', label: 'Cumulative Net' },
  { value: 'commit_count', label: 'Commit Count' },
  { value: 'file_count', label: 'File Count' },
]

export const ACTIVITY_EXTRA_FILTER_OPTIONS: Array<{ value: ActivityExtraFilterKind; label: string }> = [
  { value: 'author', label: 'Author' },
  { value: 'language', label: 'Language' },
  { value: 'category', label: 'Category' },
  { value: 'product', label: 'Product code' },
]

export const ACTIVITY_COMPARE_PRESET_OPTIONS: Array<{ value: Exclude<ActivityComparePresetMode, 'off' | 'custom_anchor_date' | 'custom_range'>; label: string }> = [
  { value: 'previous_period', label: 'Previous period' },
  { value: 'previous_year', label: 'Previous year' },
]

function formatLocalDate(date: Date): string {
  return [
    date.getFullYear(),
    String(date.getMonth() + 1).padStart(2, '0'),
    String(date.getDate()).padStart(2, '0'),
  ].join('-')
}

function shiftDays(source: Date, days: number): Date {
  const next = new Date(source)
  next.setDate(next.getDate() + days)
  return next
}

function buildPresetDateRange(preset: Exclude<ActivityRangePreset, 'custom'>): DateRange {
  const today = new Date()
  switch (preset) {
    case '7d':
      return { from: formatLocalDate(shiftDays(today, -6)), to: formatLocalDate(today) }
    case '14d':
      return { from: formatLocalDate(shiftDays(today, -13)), to: formatLocalDate(today) }
    case '30d':
      return { from: formatLocalDate(shiftDays(today, -29)), to: formatLocalDate(today) }
    case '90d':
      return { from: formatLocalDate(shiftDays(today, -89)), to: formatLocalDate(today) }
  }
}

function normalizeDateInput(value: unknown): string {
  return typeof value === 'string' && /^\d{4}-\d{2}-\d{2}$/.test(value) ? value : ''
}

function isValidDateRange(range: DateRange): boolean {
  return Boolean(range.from && range.to && range.from <= range.to)
}

function backendMetric(metric: ActivityMetric): Metric {
  return metric === 'cumulative_net' ? 'net_lines' : metric
}

function formatDateRangeLabel(range: DateRange): string {
  if (!isValidDateRange(range)) {
    return 'Custom range'
  }

  const from = new Date(`${range.from}T00:00:00`)
  const to = new Date(`${range.to}T00:00:00`)
  const formatOptions: Intl.DateTimeFormatOptions = { month: 'short', day: 'numeric' }
  const includeYear = from.getFullYear() !== to.getFullYear()

  if (includeYear) {
    formatOptions.year = 'numeric'
  }

  return `${from.toLocaleDateString('en-US', formatOptions)} - ${to.toLocaleDateString('en-US', formatOptions)}`
}

function toDrilldownSeriesField(groupBy: GroupBy): string | null {
  switch (groupBy) {
    case 'author':
    case 'repo':
    case 'language':
    case 'category':
    case 'subtype':
    case 'cohort':
      return groupBy
    case 'product_code':
      return 'productCode'
    default:
      return null
  }
}

export const useActivityStore = defineStore('activity', () => {
  const dashboard = useDashboardStore()
  const rangePreset = ref<ActivityRangePreset>('14d')
  const customDateRange = ref<DateRange>(buildPresetDateRange('14d'))
  const selectedRepoIds = ref<string[]>([])
  const selectedAuthorIds = ref<string[]>([])
  const selectedLanguages = ref<string[]>([])
  const selectedCategories = ref<string[]>([])
  const selectedProductCodes = ref<string[]>([])
  const visibleExtraFilterKinds = ref<ActivityExtraFilterKind[]>([])
  const selectedDate = ref('')
  const selectedCommitSha = ref<string | null>(null)
  const selectedSeriesKey = ref<string | null>(null)
  const metric = ref<ActivityMetric>('commit_count')
  const groupBy = ref<GroupBy>('author')
  const chartStyle = ref<ActivityChartStyle>('line')
  const showLegend = ref(true)
  const compareMode = ref<ActivityComparePresetMode>('off')
  const compareAnchorDate = ref('')
  const compareCustomRange = ref<DateRange | null>(null)
  const compareOverlayVisible = ref(true)
  const compareData = ref<ActivityCompareResponse | null>(null)
  const analytics = ref<AnalyticsQueryResponse | null>(null)
  const dayDetail = ref<DayDrilldownResponse | null>(null)
  const annotations = ref<AnnotationV2[]>([])
  const loadingAnalytics = ref(false)
  const loadingCompare = ref(false)
  const loadingDay = ref(false)
  const loadingAnnotations = ref(false)
  const loadingCommitDetail = ref(false)
  const analyticsError = ref<string | null>(null)
  const compareError = ref<string | null>(null)
  const dayError = ref<string | null>(null)
  const annotationsError = ref<string | null>(null)
  const commitDetailError = ref<string | null>(null)
  const annotationDialogOpen = ref(false)
  const editingAnnotation = ref<AnnotationV2 | null>(null)
  const selectedCommitDetail = ref<CommitDetailResponse | null>(null)
  const initialized = ref(false)
  const commitFilesExpanded = ref(false)
  const lastAnalyticsRequestId = ref(0)
  const lastCompareRequestId = ref(0)
  const lastDayRequestId = ref(0)
  const lastAnnotationsRequestId = ref(0)
  const lastCommitDetailRequestId = ref(0)

  // --- Computed ---

  const effectiveDateRange = computed<DateRange>(() => {
    if (rangePreset.value === 'custom' && isValidDateRange(customDateRange.value)) {
      return customDateRange.value
    }
    return buildPresetDateRange(rangePreset.value === 'custom' ? '14d' : rangePreset.value)
  })

  const rangeDays = computed(() => {
    const from = new Date(`${effectiveDateRange.value.from}T00:00:00`)
    const to = new Date(`${effectiveDateRange.value.to}T00:00:00`)
    return Math.max(1, Math.round((to.getTime() - from.getTime()) / 86_400_000) + 1)
  })

  const timeLabel = computed(() => {
    switch (rangePreset.value) {
      case '7d': return 'Last 7 days'
      case '14d': return 'Last 14 days'
      case '30d': return 'Last 30 days'
      case '90d': return 'Last 90 days'
      case 'custom': return formatDateRangeLabel(customDateRange.value)
    }
  })

  const compareEnabled = computed(() => compareMode.value !== 'off')

  const compareModeLabel = computed(() => {
    switch (compareMode.value) {
      case 'previous_period':
        return 'Previous period'
      case 'previous_year':
        return 'Previous year'
      case 'custom_anchor_date':
        return 'Custom past date'
      case 'custom_range':
        return 'Custom date range'
      default:
        return 'Compare'
    }
  })

  const compareReferenceLabel = computed(() => {
    if (!compareData.value) {
      return null
    }
    return formatDateRangeLabel({
      from: compareData.value.reference.from,
      to: compareData.value.reference.to,
    })
  })

  const compareDeltaLabel = computed(() => {
    const percentage = compareData.value?.delta.percentage
    if (percentage == null) {
      return 'n/a'
    }
    return `${percentage >= 0 ? '+' : ''}${percentage.toFixed(1)}%`
  })

  const repoOptions = computed(() => dashboard.bootstrap?.repos ?? [])
  const authorOptions = computed(() => dashboard.bootstrap?.authors ?? [])
  const languageOptions = computed(() => dashboard.options?.languages ?? [])
  const categoryOptions = computed(() => dashboard.options?.categories ?? [])
  const productCodeOptions = computed(() => dashboard.options?.productCodes ?? [])

  const repoLabel = computed(() => {
    if (!selectedRepoIds.value.length) {
      return 'All repositories'
    }
    if (selectedRepoIds.value.length === 1) {
      return repoOptions.value.find((repo) => repo.id === selectedRepoIds.value[0])?.displayName ?? selectedRepoIds.value[0]
    }
    return `${selectedRepoIds.value.length} repositories`
  })

  const groupByLabel = computed(
    () => ACTIVITY_GROUP_BY_OPTIONS.find((option) => option.value === groupBy.value)?.label ?? 'Author',
  )

  const metricLabel = computed(
    () => ACTIVITY_METRIC_OPTIONS.find((option) => option.value === metric.value)?.label ?? 'Commit Count',
  )

  const chartStyleLabel = computed(
    () => ACTIVITY_CHART_STYLE_OPTIONS.find((option) => option.value === chartStyle.value)?.label ?? 'Line',
  )

  const activeFilterCount = computed(() => (
    selectedAuthorIds.value.length +
    selectedLanguages.value.length +
    selectedCategories.value.length +
    selectedProductCodes.value.length
  ))

  const availableExtraFilterKinds = computed(() => (
    ACTIVITY_EXTRA_FILTER_OPTIONS.filter((option) => !visibleExtraFilterKinds.value.includes(option.value))
  ))

  const selectedSeries = computed(() => {
    const field = toDrilldownSeriesField(groupBy.value)
    if (!field || !selectedSeriesKey.value) {
      return null
    }
    return {
      field,
      value: selectedSeriesKey.value,
    }
  })

  const selectedSeriesLabel = computed(() => {
    if (!selectedSeriesKey.value) {
      return null
    }
    return analytics.value?.series.find((series) => series.key === selectedSeriesKey.value)?.label ?? null
  })

  const dayAnnotations = computed(() =>
    annotations.value.filter((a) => a.scopeDate === selectedDate.value),
  )

  const displayCommits = computed(() => dayDetail.value?.commits ?? [])

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
    if (!selectedCommitDetail.value) {
      return []
    }
    return commitFilesExpanded.value
      ? selectedCommitDetail.value.files
      : selectedCommitDetail.value.files.slice(0, 6)
  })

  // --- Actions ---

  function buildEffectiveFilters(): FilterSpec[] {
    const filters: FilterSpec[] = []

    if (selectedRepoIds.value.length) {
      filters.push({
        field: 'repoId',
        op: 'in',
        values: [...selectedRepoIds.value],
      })
    }

    if (selectedAuthorIds.value.length) {
      filters.push({
        field: 'authorId',
        op: 'in',
        values: [...selectedAuthorIds.value],
      })
    }

    if (selectedLanguages.value.length) {
      filters.push({
        field: 'language',
        op: 'in',
        values: [...selectedLanguages.value],
      })
    }

    if (selectedCategories.value.length) {
      filters.push({
        field: 'category',
        op: 'in',
        values: [...selectedCategories.value],
      })
    }

    if (selectedProductCodes.value.length) {
      filters.push({
        field: 'productCode',
        op: 'in',
        values: [...selectedProductCodes.value],
      })
    }

    return filters
  }

  function buildQuery() {
    const query: Record<string, string | string[]> = {}
    if (selectedDate.value) {
      query.date = selectedDate.value
    }
    if (rangePreset.value !== '14d') {
      query.range = rangePreset.value
    }
    if (rangePreset.value === 'custom' && isValidDateRange(customDateRange.value)) {
      query.range = 'custom'
      query.from = customDateRange.value.from
      query.to = customDateRange.value.to
    }
    if (selectedRepoIds.value.length > 0) {
      query.repo =
        selectedRepoIds.value.length === 1 ? selectedRepoIds.value[0] : [...selectedRepoIds.value]
    }
    if (selectedAuthorIds.value.length > 0) {
      query.author =
        selectedAuthorIds.value.length === 1 ? selectedAuthorIds.value[0] : [...selectedAuthorIds.value]
    }
    if (selectedLanguages.value.length > 0) {
      query.language =
        selectedLanguages.value.length === 1 ? selectedLanguages.value[0] : [...selectedLanguages.value]
    }
    if (selectedCategories.value.length > 0) {
      query.category =
        selectedCategories.value.length === 1 ? selectedCategories.value[0] : [...selectedCategories.value]
    }
    if (selectedProductCodes.value.length > 0) {
      query.product =
        selectedProductCodes.value.length === 1 ? selectedProductCodes.value[0] : [...selectedProductCodes.value]
    }
    if (metric.value !== 'commit_count') {
      query.metric = metric.value
    }
    if (groupBy.value !== 'author') {
      query.group = groupBy.value
    }
    if (chartStyle.value !== 'line') {
      query.style = chartStyle.value
    }
    if (selectedSeriesKey.value) {
      query.series = selectedSeriesKey.value
    }
    if (compareMode.value !== 'off') {
      query.compareMode = compareMode.value
      if (compareMode.value === 'custom_anchor_date' && compareAnchorDate.value) {
        query.compareAnchor = compareAnchorDate.value
      }
      if (compareMode.value === 'custom_range' && compareCustomRange.value && isValidDateRange(compareCustomRange.value)) {
        query.compareFrom = compareCustomRange.value.from
        query.compareTo = compareCustomRange.value.to
      }
    }
    return query
  }

  async function loadAnalytics() {
    const requestId = ++lastAnalyticsRequestId.value
    loadingAnalytics.value = true
    analyticsError.value = null
    const queryMetric = backendMetric(metric.value)

    try {
      const response = await queryAnalytics({
        dateRange: effectiveDateRange.value,
        metric: queryMetric,
        groupBy: groupBy.value,
        filters: {
          authorIds: selectedAuthorIds.value,
          repoIds: selectedRepoIds.value,
          languages: selectedLanguages.value,
          categories: selectedCategories.value,
          subtypes: [],
          productCodes: selectedProductCodes.value,
          cohorts: [],
        },
      })
      if (requestId !== lastAnalyticsRequestId.value) {
        return
      }

      analytics.value = response
      if (selectedSeriesKey.value && !response.series.some((series) => series.key === selectedSeriesKey.value)) {
        selectedSeriesKey.value = null
      }
      const visibleDays = new Set(
        response.series.flatMap((series) => series.points.map((point) => point.day)),
      )
      if (!selectedDate.value || !visibleDays.has(selectedDate.value)) {
        selectedDate.value =
          response.series[0]?.points.at(-1)?.day ?? effectiveDateRange.value.to
      }
    } catch (caught) {
      if (requestId !== lastAnalyticsRequestId.value) {
        return
      }
      analytics.value = null
      analyticsError.value = toMessage(caught)
    } finally {
      if (requestId === lastAnalyticsRequestId.value) {
        loadingAnalytics.value = false
      }
    }
  }

  async function loadCompare() {
    const requestId = ++lastCompareRequestId.value
    if (compareMode.value === 'off') {
      compareData.value = null
      compareError.value = null
      loadingCompare.value = false
      return
    }

    if (compareMode.value === 'custom_anchor_date' && !compareAnchorDate.value) {
      compareData.value = null
      compareError.value = null
      loadingCompare.value = false
      return
    }

    if (compareMode.value === 'custom_range' && (!compareCustomRange.value || !isValidDateRange(compareCustomRange.value))) {
      compareData.value = null
      compareError.value = null
      loadingCompare.value = false
      return
    }

    loadingCompare.value = true
    compareError.value = null

    try {
      const response = await compareActivity({
        current: effectiveDateRange.value,
        mode: compareMode.value,
        customAnchorDate: compareMode.value === 'custom_anchor_date' ? compareAnchorDate.value : null,
        customRange: compareMode.value === 'custom_range' ? compareCustomRange.value : null,
        metric: backendMetric(metric.value),
        groupBy: groupBy.value,
        filters: {
          authorIds: selectedAuthorIds.value,
          repoIds: selectedRepoIds.value,
          languages: selectedLanguages.value,
          categories: selectedCategories.value,
          subtypes: [],
          productCodes: selectedProductCodes.value,
          cohorts: [],
        },
      })
      if (requestId !== lastCompareRequestId.value) {
        return
      }
      compareData.value = response
    } catch (caught) {
      if (requestId !== lastCompareRequestId.value) {
        return
      }
      compareData.value = null
      compareError.value = toMessage(caught)
    } finally {
      if (requestId === lastCompareRequestId.value) {
        loadingCompare.value = false
      }
    }
  }

  async function loadDay() {
    if (!selectedDate.value) {
      dayDetail.value = null
      selectedCommitSha.value = null
      return
    }

    const requestId = ++lastDayRequestId.value
    loadingDay.value = true
    dayError.value = null

    try {
      const response = await loadDayDrilldown({
        selectedDate: selectedDate.value,
        dataset: 'throughput_daily',
        effectiveFilters: buildEffectiveFilters(),
        selectedSeries: selectedSeries.value,
      })
      if (requestId !== lastDayRequestId.value) {
        return
      }
      dayDetail.value = response
      if (!dayDetail.value.commits.some((commit) => commit.commitSha === selectedCommitSha.value)) {
        selectedCommitSha.value = dayDetail.value.commits[0]?.commitSha ?? null
      }
      if (!selectedCommitSha.value) {
        selectedCommitDetail.value = null
        commitDetailError.value = null
      }
    } catch (caught) {
      if (requestId !== lastDayRequestId.value) {
        return
      }
      dayDetail.value = null
      selectedCommitSha.value = null
      selectedCommitDetail.value = null
      dayError.value = toMessage(caught)
    } finally {
      if (requestId === lastDayRequestId.value) {
        loadingDay.value = false
      }
    }
  }

  async function loadAnnotationsForDay() {
    if (!selectedDate.value) {
      annotations.value = []
      return
    }

    const requestId = ++lastAnnotationsRequestId.value
    loadingAnnotations.value = true
    annotationsError.value = null

    try {
      const response = await listAnnotationsV2({
        dateFrom: selectedDate.value,
        dateTo: selectedDate.value,
      })
      if (requestId !== lastAnnotationsRequestId.value) {
        return
      }
      annotations.value = response
    } catch (caught) {
      if (requestId !== lastAnnotationsRequestId.value) {
        return
      }
      annotations.value = []
      annotationsError.value = toMessage(caught)
    } finally {
      if (requestId === lastAnnotationsRequestId.value) {
        loadingAnnotations.value = false
      }
    }
  }

  async function loadSelectedCommitDetail() {
    const commit = selectedCommit.value
    if (!commit) {
      selectedCommitDetail.value = null
      commitDetailError.value = null
      loadingCommitDetail.value = false
      return
    }

    const requestId = ++lastCommitDetailRequestId.value
    loadingCommitDetail.value = true
    commitDetailError.value = null

    try {
      const response = await getActivityCommitDetail(commit.repoId, commit.commitSha)
      if (requestId !== lastCommitDetailRequestId.value) {
        return
      }
      selectedCommitDetail.value = response
    } catch (caught) {
      if (requestId !== lastCommitDetailRequestId.value) {
        return
      }
      selectedCommitDetail.value = null
      commitDetailError.value = toMessage(caught)
    } finally {
      if (requestId === lastCommitDetailRequestId.value) {
        loadingCommitDetail.value = false
      }
    }
  }

  async function refreshActivity() {
    await loadAnalytics()
    await Promise.all([loadDay(), loadAnnotationsForDay(), loadCompare()])
    await loadSelectedCommitDetail()
  }

  function selectDate(day: string) {
    selectedCommitSha.value = null
    selectedCommitDetail.value = null
    commitDetailError.value = null
    commitFilesExpanded.value = false
    selectedDate.value = day
  }

  function selectChartPoint(day: string, seriesKey: string | null) {
    selectedSeriesKey.value = seriesKey
    selectDate(day)
  }

  function selectCommit(sha: string) {
    commitFilesExpanded.value = false
    selectedCommitSha.value = sha
  }

  function setRangePreset(preset: Exclude<ActivityRangePreset, 'custom'>) {
    selectedSeriesKey.value = null
    rangePreset.value = preset
  }

  function setCustomDateRange(range: DateRange) {
    const normalizedRange = {
      from: normalizeDateInput(range.from),
      to: normalizeDateInput(range.to),
    }
    if (!isValidDateRange(normalizedRange)) {
      return false
    }
    selectedSeriesKey.value = null
    customDateRange.value = normalizedRange
    rangePreset.value = 'custom'
    return true
  }

  function setGroupBy(nextGroupBy: GroupBy) {
    selectedSeriesKey.value = null
    groupBy.value = nextGroupBy
  }

  function setMetric(nextMetric: ActivityMetric) {
    selectedSeriesKey.value = null
    metric.value = nextMetric
  }

  function setComparePreset(nextMode: Exclude<ActivityComparePresetMode, 'off' | 'custom_anchor_date' | 'custom_range'>) {
    compareMode.value = nextMode
    compareError.value = null
  }

  function disableCompare() {
    compareMode.value = 'off'
    compareData.value = null
    compareError.value = null
    loadingCompare.value = false
  }

  function setCompareAnchor(nextAnchorDate: string) {
    const normalizedAnchor = normalizeDateInput(nextAnchorDate)
    if (!normalizedAnchor) {
      return false
    }
    compareAnchorDate.value = normalizedAnchor
    compareMode.value = 'custom_anchor_date'
    compareError.value = null
    return true
  }

  function setCompareCustomRange(range: DateRange) {
    const normalizedRange = {
      from: normalizeDateInput(range.from),
      to: normalizeDateInput(range.to),
    }
    if (!isValidDateRange(normalizedRange)) {
      return false
    }
    compareCustomRange.value = normalizedRange
    compareMode.value = 'custom_range'
    compareError.value = null
    return true
  }

  function setChartStyle(nextStyle: ActivityChartStyle) {
    chartStyle.value = nextStyle
  }

  function setRepoFilters(ids: string[]) {
    selectedRepoIds.value = normalizeSelection(ids)
  }

  function toggleRepoFilter(id: string) {
    if (selectedRepoIds.value.includes(id)) {
      selectedRepoIds.value = selectedRepoIds.value.filter((repoId) => repoId !== id)
    } else {
      selectedRepoIds.value = [...selectedRepoIds.value, id]
    }
  }

  function setAuthorFilters(ids: string[]) {
    selectedAuthorIds.value = normalizeSelection(ids)
  }

  function toggleAuthorFilter(id: string) {
    if (selectedAuthorIds.value.includes(id)) {
      selectedAuthorIds.value = selectedAuthorIds.value.filter((authorId) => authorId !== id)
    } else {
      selectedAuthorIds.value = [...selectedAuthorIds.value, id]
    }
  }

  function setLanguageFilters(values: string[]) {
    selectedLanguages.value = normalizeSelection(values)
  }

  function toggleLanguageFilter(value: string) {
    if (selectedLanguages.value.includes(value)) {
      selectedLanguages.value = selectedLanguages.value.filter((entry) => entry !== value)
    } else {
      selectedLanguages.value = [...selectedLanguages.value, value]
    }
  }

  function setCategoryFilters(values: string[]) {
    selectedCategories.value = normalizeSelection(values)
  }

  function toggleCategoryFilter(value: string) {
    if (selectedCategories.value.includes(value)) {
      selectedCategories.value = selectedCategories.value.filter((entry) => entry !== value)
    } else {
      selectedCategories.value = [...selectedCategories.value, value]
    }
  }

  function setProductCodeFilters(values: string[]) {
    selectedProductCodes.value = normalizeSelection(values)
  }

  function toggleProductCodeFilter(value: string) {
    if (selectedProductCodes.value.includes(value)) {
      selectedProductCodes.value = selectedProductCodes.value.filter((entry) => entry !== value)
    } else {
      selectedProductCodes.value = [...selectedProductCodes.value, value]
    }
  }

  function clearExtraFilters() {
    selectedAuthorIds.value = []
    selectedLanguages.value = []
    selectedCategories.value = []
    selectedProductCodes.value = []
    visibleExtraFilterKinds.value = []
  }

  function addExtraFilter(kind: ActivityExtraFilterKind) {
    if (!visibleExtraFilterKinds.value.includes(kind)) {
      visibleExtraFilterKinds.value = [...visibleExtraFilterKinds.value, kind]
    }
  }

  function removeExtraFilter(kind: ActivityExtraFilterKind) {
    visibleExtraFilterKinds.value = visibleExtraFilterKinds.value.filter((value) => value !== kind)
    switch (kind) {
      case 'author':
        selectedAuthorIds.value = []
        break
      case 'language':
        selectedLanguages.value = []
        break
      case 'category':
        selectedCategories.value = []
        break
      case 'product':
        selectedProductCodes.value = []
        break
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

  function expandCommitFiles() {
    commitFilesExpanded.value = true
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
        scope: { surface: 'activity' },
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
    const currentRouteName = router.currentRoute.value.name
    if (currentRouteName !== 'activity' && currentRouteName !== 'activity-commit') {
      return
    }
    const query = buildQuery()
    if (queriesMatch(router.currentRoute.value.query, query)) {
      return
    }
    try {
      void router.replace({ name: 'activity', query })
    } catch {
      // swallow NavigationFailure
    }
  }

  function initFromQuery(query: RouteLocationNormalizedLoaded['query']) {
    selectedDate.value = typeof query.date === 'string' ? query.date : ''
    rangePreset.value = normalizeRangePreset(query.range, query.from, query.to)
    customDateRange.value = normalizeCustomDateRange(query.from, query.to, rangePreset.value)
    selectedRepoIds.value = normalizeSelectionFromQuery(query.repo)
    selectedAuthorIds.value = normalizeSelectionFromQuery(query.author)
    selectedLanguages.value = normalizeSelectionFromQuery(query.language)
    selectedCategories.value = normalizeSelectionFromQuery(query.category)
    selectedProductCodes.value = normalizeSelectionFromQuery(query.product)
    metric.value = normalizeMetric(query.metric)
    visibleExtraFilterKinds.value = [
      ...(selectedAuthorIds.value.length ? ['author' as const] : []),
      ...(selectedLanguages.value.length ? ['language' as const] : []),
      ...(selectedCategories.value.length ? ['category' as const] : []),
      ...(selectedProductCodes.value.length ? ['product' as const] : []),
    ]
    groupBy.value = normalizeGroupBy(query.group)
    chartStyle.value = normalizeChartStyle(query.style)
    selectedSeriesKey.value = normalizeSeriesKey(query.series)
    compareMode.value = normalizeCompareMode(query.compareMode)
    compareAnchorDate.value = compareMode.value === 'custom_anchor_date'
      ? normalizeDateInput(query.compareAnchor)
      : ''
    compareCustomRange.value = compareMode.value === 'custom_range'
      ? normalizeOptionalDateRange(query.compareFrom, query.compareTo)
      : null
    if (compareMode.value === 'custom_anchor_date' && !compareAnchorDate.value) {
      compareMode.value = 'off'
    }
    if (compareMode.value === 'custom_range' && !compareCustomRange.value) {
      compareMode.value = 'off'
    }
    initialized.value = true
  }

  return {
    rangePreset,
    customDateRange,
    selectedRepoIds,
    selectedAuthorIds,
    selectedLanguages,
    selectedCategories,
    selectedProductCodes,
    visibleExtraFilterKinds,
    selectedDate,
    selectedCommitSha,
    selectedSeriesKey,
    metric,
    groupBy,
    chartStyle,
    showLegend,
    compareMode,
    compareAnchorDate,
    compareCustomRange,
    compareOverlayVisible,
    compareData,
    analytics,
    dayDetail,
    annotations,
    loadingAnalytics,
    loadingCompare,
    loadingDay,
    loadingAnnotations,
    loadingCommitDetail,
    analyticsError,
    compareError,
    dayError,
    annotationsError,
    commitDetailError,
    annotationDialogOpen,
    editingAnnotation,
    selectedCommitDetail,
    initialized,
    commitFilesExpanded,
    rangeDays,
    effectiveDateRange,
    timeLabel,
    compareEnabled,
    compareModeLabel,
    compareReferenceLabel,
    compareDeltaLabel,
    repoOptions,
    authorOptions,
    languageOptions,
    categoryOptions,
    productCodeOptions,
    repoLabel,
    groupByLabel,
    metricLabel,
    chartStyleLabel,
    activeFilterCount,
    availableExtraFilterKinds,
    selectedSeries,
    selectedSeriesLabel,
    dayAnnotations,
    displayCommits,
    selectedCommit,
    displayCommitFiles,
    buildQuery,
    loadAnalytics,
    loadCompare,
    loadDay,
    loadAnnotationsForDay,
    loadSelectedCommitDetail,
    refreshActivity,
    selectDate,
    selectChartPoint,
    selectCommit,
    setMetric,
    setComparePreset,
    disableCompare,
    setCompareAnchor,
    setCompareCustomRange,
    setRangePreset,
    setCustomDateRange,
    setGroupBy,
    setChartStyle,
    setRepoFilters,
    toggleRepoFilter,
    setAuthorFilters,
    toggleAuthorFilter,
    setLanguageFilters,
    toggleLanguageFilter,
    setCategoryFilters,
    toggleCategoryFilter,
    setProductCodeFilters,
    toggleProductCodeFilter,
    clearExtraFilters,
    addExtraFilter,
    removeExtraFilter,
    openNewAnnotation,
    editAnnotation,
    closeAnnotationDialog,
    expandCommitFiles,
    handleSaveAnnotation,
    handleDeleteAnnotation,
    syncQueryToUrl,
    initFromQuery,
  }
})

function queriesMatch(
  currentQuery: RouteLocationNormalizedLoaded['query'],
  nextQuery: Record<string, string | string[]>,
) {
  const currentDate = typeof currentQuery.date === 'string' ? currentQuery.date : ''
  const nextDate = typeof nextQuery.date === 'string' ? nextQuery.date : ''
  const currentRange = normalizeRangePreset(currentQuery.range, currentQuery.from, currentQuery.to)
  const nextRange = normalizeRangePreset(nextQuery.range, nextQuery.from, nextQuery.to)
  const currentCustomRange = normalizeCustomDateRange(currentQuery.from, currentQuery.to, currentRange)
  const nextCustomRange = normalizeCustomDateRange(nextQuery.from, nextQuery.to, nextRange)
  const currentRepoIds = normalizeSelectionFromQuery(currentQuery.repo)
  const nextRepoIds = normalizeSelectionFromQuery(nextQuery.repo)
  const currentAuthorIds = normalizeSelectionFromQuery(currentQuery.author)
  const nextAuthorIds = normalizeSelectionFromQuery(nextQuery.author)
  const currentLanguages = normalizeSelectionFromQuery(currentQuery.language)
  const nextLanguages = normalizeSelectionFromQuery(nextQuery.language)
  const currentCategories = normalizeSelectionFromQuery(currentQuery.category)
  const nextCategories = normalizeSelectionFromQuery(nextQuery.category)
  const currentProductCodes = normalizeSelectionFromQuery(currentQuery.product)
  const nextProductCodes = normalizeSelectionFromQuery(nextQuery.product)
  const currentMetric = normalizeMetric(currentQuery.metric)
  const nextMetric = normalizeMetric(nextQuery.metric)
  const currentGroupBy = normalizeGroupBy(currentQuery.group)
  const nextGroupBy = normalizeGroupBy(nextQuery.group)
  const currentChartStyle = normalizeChartStyle(currentQuery.style)
  const nextChartStyle = normalizeChartStyle(nextQuery.style)
  const currentSeriesKey = normalizeSeriesKey(currentQuery.series)
  const nextSeriesKey = normalizeSeriesKey(nextQuery.series)
  const currentCompareMode = normalizeCompareMode(currentQuery.compareMode)
  const nextCompareMode = normalizeCompareMode(nextQuery.compareMode)
  const currentCompareAnchor = currentCompareMode === 'custom_anchor_date' ? normalizeDateInput(currentQuery.compareAnchor) : ''
  const nextCompareAnchor = nextCompareMode === 'custom_anchor_date' ? normalizeDateInput(nextQuery.compareAnchor) : ''
  const currentCompareRange = currentCompareMode === 'custom_range'
    ? normalizeOptionalDateRange(currentQuery.compareFrom, currentQuery.compareTo)
    : null
  const nextCompareRange = nextCompareMode === 'custom_range'
    ? normalizeOptionalDateRange(nextQuery.compareFrom, nextQuery.compareTo)
    : null

  return currentDate === nextDate &&
    currentRange === nextRange &&
    currentCustomRange.from === nextCustomRange.from &&
    currentCustomRange.to === nextCustomRange.to &&
    currentAuthorIds.length === nextAuthorIds.length &&
    currentAuthorIds.every((authorId, index) => authorId === nextAuthorIds[index]) &&
    currentLanguages.length === nextLanguages.length &&
    currentLanguages.every((language, index) => language === nextLanguages[index]) &&
    currentCategories.length === nextCategories.length &&
    currentCategories.every((category, index) => category === nextCategories[index]) &&
    currentProductCodes.length === nextProductCodes.length &&
    currentProductCodes.every((productCode, index) => productCode === nextProductCodes[index]) &&
    currentMetric === nextMetric &&
    currentGroupBy === nextGroupBy &&
    currentSeriesKey === nextSeriesKey &&
    currentChartStyle === nextChartStyle &&
    currentCompareMode === nextCompareMode &&
    currentCompareAnchor === nextCompareAnchor &&
    (currentCompareRange?.from ?? '') === (nextCompareRange?.from ?? '') &&
    (currentCompareRange?.to ?? '') === (nextCompareRange?.to ?? '') &&
    currentRepoIds.length === nextRepoIds.length &&
    currentRepoIds.every((repoId, index) => repoId === nextRepoIds[index])
}

function normalizeRangePreset(value: unknown, from?: unknown, to?: unknown): ActivityRangePreset {
  if (normalizeDateInput(from) && normalizeDateInput(to)) {
    return 'custom'
  }
  return value === '7d' || value === '14d' || value === '30d' || value === '90d' || value === 'custom'
    ? value
    : '14d'
}

function normalizeCustomDateRange(from: unknown, to: unknown, preset: ActivityRangePreset): DateRange {
  const normalizedRange = {
    from: normalizeDateInput(from),
    to: normalizeDateInput(to),
  }
  if (isValidDateRange(normalizedRange)) {
    return normalizedRange
  }
  return buildPresetDateRange(preset === 'custom' ? '14d' : preset)
}

function normalizeOptionalDateRange(from: unknown, to: unknown): DateRange | null {
  const normalizedRange = {
    from: normalizeDateInput(from),
    to: normalizeDateInput(to),
  }
  return isValidDateRange(normalizedRange) ? normalizedRange : null
}

function normalizeSelection(values: string[]) {
  return Array.from(
    new Set(
      values
        .filter((value): value is string => typeof value === 'string')
        .map((value) => value.trim())
        .filter(Boolean),
    ),
  )
}

function normalizeSelectionFromQuery(value: unknown): string[] {
  if (Array.isArray(value)) {
    return normalizeSelection(value.flatMap((entry) => (typeof entry === 'string' ? entry.split(',') : [])))
  }
  if (typeof value === 'string') {
    return normalizeSelection(value.split(','))
  }
  return []
}

function normalizeGroupBy(value: unknown): GroupBy {
  return ACTIVITY_GROUP_BY_OPTIONS.some((option) => option.value === value) ? value as GroupBy : 'author'
}

function normalizeMetric(value: unknown): ActivityMetric {
  return ACTIVITY_METRIC_OPTIONS.some((option) => option.value === value) ? value as ActivityMetric : 'commit_count'
}

function normalizeChartStyle(value: unknown): ActivityChartStyle {
  return ACTIVITY_CHART_STYLE_OPTIONS.some((option) => option.value === value)
    ? value as ActivityChartStyle
    : 'line'
}

function normalizeSeriesKey(value: unknown): string | null {
  return typeof value === 'string' && value.trim() ? value.trim() : null
}

function normalizeCompareMode(value: unknown): ActivityComparePresetMode {
  return value === 'previous_period'
    || value === 'previous_year'
    || value === 'custom_anchor_date'
    || value === 'custom_range'
    || value === 'off'
    ? value
    : 'off'
}

function toMessage(caught: unknown) {
  return caught instanceof Error ? caught.message : 'Unexpected activity error'
}
