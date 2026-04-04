<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import VChart from 'vue-echarts'
import ChartJsRenderer from '../components/ChartJsRenderer.vue'
import {
  ArrowUpDown,
  BellRing,
  CalendarDays,
  Eye,
  EyeOff,
  Expand,
  Files,
  GitCommitHorizontal,
  LoaderCircle,
  MoreHorizontal,
  Minimize2,
  Plus,
  PencilLine,
  SlidersHorizontal,
  Sparkles,
  TrendingDown,
  TrendingUp,
} from 'lucide-vue-next'
import { buildChartOption, getSeriesColor } from '../lib/chart'
import { useDashboardStore } from '../stores/dashboard'
import type { Annotation, AnnotationType, ChartLibrary, GroupBy, Metric } from '../types/api'

const store = useDashboardStore()
const annotationDialog = ref<HTMLDialogElement | null>(null)
const filterTrigger = ref<HTMLElement | null>(null)
const filterPopover = ref<HTMLElement | null>(null)
const annotationTrigger = ref<HTMLElement | null>(null)
const annotationPopover = ref<HTMLElement | null>(null)
const dateTrigger = ref<HTMLElement | null>(null)
const datePopover = ref<HTMLElement | null>(null)
const comparisonTrigger = ref<HTMLElement | null>(null)
const comparisonPopover = ref<HTMLElement | null>(null)
const toolbarOverflowTrigger = ref<HTMLElement | null>(null)
const toolbarOverflowPopover = ref<HTMLElement | null>(null)
const chartPanel = ref<HTMLElement | null>(null)
const chartToolbar = ref<HTMLElement | null>(null)
const chartRef = ref<InstanceType<typeof VChart> | null>(null)
const chartJsRef = ref<InstanceType<typeof ChartJsRenderer> | null>(null)
const isChartFullscreen = ref(false)
const annotationListOpen = ref(false)
const datePopoverOpen = ref(false)
const comparisonPopoverOpen = ref(false)
const toolbarOverflowOpen = ref(false)
const datePreset = ref('custom')
const anonymizeAuthors = ref(false)
const chartToolbarWidth = ref(0)

const metricOptions: Array<{ value: Metric; label: string }> = [
  { value: 'lines_added', label: 'Lines Added' },
  { value: 'lines_removed', label: 'Lines Removed' },
  { value: 'net_lines', label: 'Net Lines' },
  { value: 'commit_count', label: 'Commit Count' },
  { value: 'file_count', label: 'File Count' },
]

const groupByOptions: Array<{ value: GroupBy; label: string }> = [
  { value: 'author', label: 'Author' },
  { value: 'repo', label: 'Repository' },
  { value: 'cohort', label: 'Cohort' },
  { value: 'language', label: 'Language' },
  { value: 'category', label: 'Category' },
  { value: 'subtype', label: 'Subtype' },
  { value: 'product_code', label: 'Product Code' },
  { value: 'none', label: 'Total' },
]

const annotationTypes: AnnotationType[] = ['adoption', 'process_change', 'release', 'incident', 'milestone', 'custom']
const authorAliases = [
  'Ada Lovelace',
  'Marie Curie',
  'Alan Turing',
  'Katherine Johnson',
  'Rosalind Franklin',
  'Carl Sagan',
  'Emily Dickinson',
  'Maya Angelou',
  'Pablo Neruda',
  'Rachel Carson',
  'James Baldwin',
  'Mary Shelley',
]

const chartOption = computed(() => {
  return buildChartOption({
    analytics: store.analytics,
    comparisonAnalytics: store.comparisonAnalytics,
    annotations: store.annotations,
    metric: store.controls.metric,
    chartMode: store.chartMode,
    comparison: store.comparison,
    comparisonOverlayVisible: store.comparisonOverlayVisible,
    legendVisible: store.legendVisible,
    anonymizeAuthors: anonymizeAuthors.value,
    authorLabelById: Object.fromEntries(authorAliasMap.value),
  })
})

const comparisonDeltaLabel = computed(() => {
  const delta = store.comparison?.delta.metricPercentage
  if (delta == null) {
    return 'n/a'
  }
  return `${delta >= 0 ? '+' : ''}${delta.toFixed(1)}%`
})

const comparisonDeltaTone = computed<'up' | 'down' | 'neutral'>(() => {
  const delta = store.comparison?.delta.metricPercentage
  if (delta == null) {
    return 'neutral'
  }
  return delta >= 0 ? 'up' : 'down'
})

function resolveTrendIcon(tone: 'up' | 'down' | 'neutral') {
  if (tone === 'up') {
    return TrendingUp
  }
  if (tone === 'down') {
    return TrendingDown
  }
  return ArrowUpDown
}

const comparisonDeltaIcon = computed(() => resolveTrendIcon(comparisonDeltaTone.value))

const comparisonCurrentTone = computed(() => comparisonDeltaTone.value)

const comparisonReferenceTone = computed<'up' | 'down' | 'neutral'>(() => {
  if (comparisonDeltaTone.value === 'up') {
    return 'down'
  }
  if (comparisonDeltaTone.value === 'down') {
    return 'up'
  }
  return 'neutral'
})

const comparisonCurrentIcon = computed(() => resolveTrendIcon(comparisonCurrentTone.value))
const comparisonReferenceIcon = computed(() => resolveTrendIcon(comparisonReferenceTone.value))

const currentTotals = computed(() => store.analytics?.totals ?? null)
const chartLibrary = computed<ChartLibrary>(() => store.bootstrap?.uiDefaults.defaultChartLibrary ?? 'echarts')
const dateRangeSummary = computed(() => {
  const preset = datePresetOptions.find((option) => option.value === datePreset.value)
  if (preset) {
    return preset.label
  }
  return `${store.controls.dateRange.from} to ${store.controls.dateRange.to}`
})
const comparisonModeLabel = computed(() => (
  store.controls.comparisonMode === 'same_period_last_year'
    ? 'Same period last year'
    : 'Previous equivalent period'
))
const authorColorMap = computed(() => new Map(
  (store.controls.groupBy === 'author' ? (store.analytics?.series ?? []) : [])
    .map((series, index) => [series.key, getSeriesColor(index)] as const),
))
const authorAliasMap = computed(() => {
  const authors = (store.options?.authors ?? store.bootstrap?.authors ?? []).slice()
  return new Map(
    authors.map((author, index) => {
      const baseAlias = authorAliases[index % authorAliases.length]
      const cycle = Math.floor(index / authorAliases.length)
      const alias = cycle > 0 ? `${baseAlias} ${cycle + 1}` : baseAlias
      return [author.id, alias] as const
    }),
  )
})
const reposByProductCode = computed(() => {
  const repoGroups = new Map<string, Array<{ id: string; displayName: string }>>()
  for (const repo of store.bootstrap?.repos ?? []) {
    const productCode = repo.productCode ?? 'Unassigned'
    const list = repoGroups.get(productCode) ?? []
    list.push({ id: repo.id, displayName: repo.displayName })
    repoGroups.set(productCode, list)
  }

  return (store.options?.productCodes ?? [])
    .map((productCode) => ({
      productCode,
      repos: (repoGroups.get(productCode) ?? []).slice().sort((left, right) => left.displayName.localeCompare(right.displayName)),
    }))
    .filter((group) => group.repos.length > 0)
})

const toolbarTier = computed<'wide' | 'medium' | 'compact' | 'tight' | 'micro'>(() => {
  if (chartToolbarWidth.value >= 1160) {
    return 'wide'
  }
  if (chartToolbarWidth.value >= 980) {
    return 'medium'
  }
  if (chartToolbarWidth.value >= 840) {
    return 'compact'
  }
  if (chartToolbarWidth.value >= 700) {
    return 'tight'
  }
  return 'micro'
})

const showToolbarTitle = computed(() => toolbarTier.value !== 'micro')
const showInlineGroupBy = computed(() => toolbarTier.value === 'wide' || toolbarTier.value === 'medium')
const showInlineChartMode = computed(() => toolbarTier.value !== 'micro')
const showInlineCompareToggle = computed(() => toolbarTier.value !== 'tight' && toolbarTier.value !== 'micro')
const showInlineLegendToggle = computed(() => toolbarTier.value !== 'tight' && toolbarTier.value !== 'micro')
const showToolbarOverflow = computed(() => (
  !showInlineGroupBy.value ||
  !showInlineChartMode.value ||
  !showInlineCompareToggle.value ||
  !showInlineLegendToggle.value
))
const toolbarDateLabel = computed(() => {
  if (toolbarTier.value === 'wide') {
    return dateRangeSummary.value
  }
  if (toolbarTier.value === 'medium') {
    const preset = datePresetOptions.find((option) => option.value === datePreset.value)
    return preset?.label ?? 'Custom range'
  }
  return 'Dates'
})
const compareToggleLabel = computed(() => {
  if (toolbarTier.value === 'wide') {
    return store.comparisonOverlayVisible ? 'Hide compare' : 'Show compare'
  }
  return 'Compare'
})
const legendToggleLabel = computed(() => {
  if (toolbarTier.value === 'wide') {
    return store.legendVisible ? 'Hide legend' : 'Show legend'
  }
  return 'Legend'
})

let chartToolbarObserver: ResizeObserver | null = null

type DatePresetOption = {
  value: string
  label: string
  getRange: () => { from: string; to: string }
}

function formatLocalDate(date: Date): string {
  return [
    date.getFullYear(),
    String(date.getMonth() + 1).padStart(2, '0'),
    String(date.getDate()).padStart(2, '0'),
  ].join('-')
}

function startOfDay(date: Date): Date {
  return new Date(date.getFullYear(), date.getMonth(), date.getDate())
}

function shiftDays(date: Date, days: number): Date {
  const next = new Date(date)
  next.setDate(next.getDate() + days)
  return next
}

function startOfMonth(date: Date): Date {
  return new Date(date.getFullYear(), date.getMonth(), 1)
}

function endOfMonth(date: Date): Date {
  return new Date(date.getFullYear(), date.getMonth() + 1, 0)
}

function sameDate(left: string, right: string): boolean {
  return left === right
}

const datePresetOptions: DatePresetOption[] = [
  {
    value: 'last_7_days',
    label: 'Last 7 days',
    getRange: () => {
      const today = startOfDay(new Date())
      return { from: formatLocalDate(shiftDays(today, -6)), to: formatLocalDate(today) }
    },
  },
  {
    value: 'last_30_days',
    label: 'Last 30 days',
    getRange: () => {
      const today = startOfDay(new Date())
      return { from: formatLocalDate(shiftDays(today, -29)), to: formatLocalDate(today) }
    },
  },
  {
    value: 'last_90_days',
    label: 'Last 90 days',
    getRange: () => {
      const today = startOfDay(new Date())
      return { from: formatLocalDate(shiftDays(today, -89)), to: formatLocalDate(today) }
    },
  },
  {
    value: 'last_month',
    label: 'Last month',
    getRange: () => {
      const today = startOfDay(new Date())
      const lastMonth = new Date(today.getFullYear(), today.getMonth() - 1, 1)
      return {
        from: formatLocalDate(startOfMonth(lastMonth)),
        to: formatLocalDate(endOfMonth(lastMonth)),
      }
    },
  },
  {
    value: 'year_to_date',
    label: 'Year to date',
    getRange: () => {
      const today = startOfDay(new Date())
      return { from: `${today.getFullYear()}-01-01`, to: formatLocalDate(today) }
    },
  },
  {
    value: 'last_12_months',
    label: 'Last 12 months',
    getRange: () => {
      const today = startOfDay(new Date())
      return { from: formatLocalDate(shiftDays(today, -364)), to: formatLocalDate(today) }
    },
  },
]

let refreshTimer: number | undefined

watch(
  () => ({
    ...store.controls,
    filters: JSON.stringify(store.controls.filters),
    chartMode: store.chartMode,
  }),
  () => {
    window.clearTimeout(refreshTimer)
    refreshTimer = window.setTimeout(() => {
      void Promise.all([store.refreshAnalytics(), store.refreshAnnotations()])
    }, 220)
  },
  { deep: true },
)

watch(
  () => [store.controls.dateRange.from, store.controls.dateRange.to] as const,
  ([from, to]) => {
    const matchingPreset = datePresetOptions.find((option) => {
      const range = option.getRange()
      return sameDate(range.from, from) && sameDate(range.to, to)
    })
    datePreset.value = matchingPreset?.value ?? 'custom'
  },
  { immediate: true },
)

watch(anonymizeAuthors, (value) => {
  window.localStorage.setItem('code-flux-anonymize-authors', String(value))
})

watch(isChartFullscreen, (value) => {
  document.body.style.overflow = value ? 'hidden' : ''
  queueChartResize()
})

watch(showToolbarOverflow, (value) => {
  if (!value) {
    toolbarOverflowOpen.value = false
  }
})

watch(chartToolbar, (element, previous) => {
  if (previous && chartToolbarObserver) {
    chartToolbarObserver.unobserve(previous)
  }

  if (!element || !chartToolbarObserver) {
    return
  }

  chartToolbarObserver.observe(element)
  chartToolbarWidth.value = element.clientWidth
})

watch(
  () => [
    store.analytics,
    store.comparisonAnalytics,
    store.annotations,
    store.legendVisible,
    store.comparisonOverlayVisible,
    store.chartMode,
    chartLibrary.value,
  ],
  async () => {
    await nextTick()
    queueChartResize()
  },
  { deep: true },
)

onMounted(() => {
  chartToolbarObserver = new ResizeObserver((entries) => {
    const [entry] = entries
    if (!entry) {
      return
    }
    chartToolbarWidth.value = entry.contentRect.width
  })
  anonymizeAuthors.value = window.localStorage.getItem('code-flux-anonymize-authors') === 'true'
  document.addEventListener('fullscreenchange', syncFullscreenState)
  document.addEventListener('webkitfullscreenchange', syncFullscreenState as EventListener)
  document.addEventListener('pointerdown', handlePointerDown)
  document.addEventListener('keydown', handleGlobalKeydown)
})

onBeforeUnmount(() => {
  chartToolbarObserver?.disconnect()
  chartToolbarObserver = null
  document.removeEventListener('fullscreenchange', syncFullscreenState)
  document.removeEventListener('webkitfullscreenchange', syncFullscreenState as EventListener)
  document.removeEventListener('pointerdown', handlePointerDown)
  document.removeEventListener('keydown', handleGlobalKeydown)
  document.body.style.overflow = ''
})

watch(
  () => store.annotationDialogOpen,
  async (isOpen) => {
    await nextTick()
    const dialog = annotationDialog.value
    if (!dialog) {
      return
    }

    if (isOpen) {
      store.filterSheetOpen = false
      annotationListOpen.value = false
      if (!dialog.open) {
        dialog.showModal()
      }
      return
    }

    if (dialog.open) {
      dialog.close()
    }
  },
)

function toggleInList(target: string[], value: string) {
  const index = target.indexOf(value)
  if (index >= 0) {
    target.splice(index, 1)
  } else {
    target.push(value)
  }
}

function formatMetric(value: number | undefined) {
  return new Intl.NumberFormat().format(value ?? 0)
}

function setChartMode(mode: 'line' | 'area') {
  store.chartMode = mode
}

function authorSwatchStyle(authorId: string) {
  return {
    '--swatch-color': authorColorMap.value.get(authorId) ?? 'var(--cf-border)',
  }
}

function displayAuthorName(authorId: string, fallback: string) {
  if (!anonymizeAuthors.value) {
    return fallback
  }
  return authorAliasMap.value.get(authorId) ?? fallback
}

function setRepoGroupSelection(repoIds: string[], selected: boolean) {
  if (selected) {
    for (const repoId of repoIds) {
      if (!store.controls.filters.repoIds.includes(repoId)) {
        store.controls.filters.repoIds.push(repoId)
      }
    }
    return
  }

  store.controls.filters.repoIds = store.controls.filters.repoIds.filter((repoId) => !repoIds.includes(repoId))
}

function applyDatePreset() {
  const preset = datePresetOptions.find((option) => option.value === datePreset.value)
  if (!preset) {
    return
  }
  const range = preset.getRange()
  store.controls.dateRange.from = range.from
  store.controls.dateRange.to = range.to
}

function handleDateInputChange() {
  datePreset.value = 'custom'
}

function selectAnnotation(annotation: Annotation) {
  annotationListOpen.value = false
  store.openEditAnnotation(annotation)
}

function closeAnnotationDialog() {
  store.annotationDialogOpen = false
}

function handleDialogClose() {
  if (store.annotationDialogOpen) {
    store.annotationDialogOpen = false
  }
}

function toggleFilterPopover() {
  const nextValue = !store.filterSheetOpen
  store.filterSheetOpen = nextValue
  if (nextValue) {
    annotationListOpen.value = false
    datePopoverOpen.value = false
    toolbarOverflowOpen.value = false
  }
}

function toggleAnnotationPopover() {
  const nextValue = !annotationListOpen.value
  annotationListOpen.value = nextValue
  if (nextValue) {
    store.filterSheetOpen = false
    datePopoverOpen.value = false
    toolbarOverflowOpen.value = false
  }
}

function toggleDatePopover() {
  const nextValue = !datePopoverOpen.value
  datePopoverOpen.value = nextValue
  if (nextValue) {
    store.filterSheetOpen = false
    annotationListOpen.value = false
    comparisonPopoverOpen.value = false
    toolbarOverflowOpen.value = false
  }
}

function toggleComparisonPopover() {
  const nextValue = !comparisonPopoverOpen.value
  comparisonPopoverOpen.value = nextValue
  if (nextValue) {
    store.filterSheetOpen = false
    annotationListOpen.value = false
    datePopoverOpen.value = false
    toolbarOverflowOpen.value = false
  }
}

function toggleToolbarOverflow() {
  const nextValue = !toolbarOverflowOpen.value
  toolbarOverflowOpen.value = nextValue
  if (nextValue) {
    store.filterSheetOpen = false
    annotationListOpen.value = false
    datePopoverOpen.value = false
    comparisonPopoverOpen.value = false
  }
}

function openAnnotationComposer(day?: string) {
  annotationListOpen.value = false
  store.openCreateAnnotation(day)
}

function handlePointerDown(event: PointerEvent) {
  const target = event.target
  if (!(target instanceof Node)) {
    return
  }

  const insideFilter =
    filterTrigger.value?.contains(target) ||
    filterPopover.value?.contains(target)
  const insideAnnotations =
    annotationTrigger.value?.contains(target) ||
    annotationPopover.value?.contains(target) ||
    annotationDialog.value?.contains(target)
  const insideDateControls =
    dateTrigger.value?.contains(target) ||
    datePopover.value?.contains(target)
  const insideComparison =
    comparisonTrigger.value?.contains(target) ||
    comparisonPopover.value?.contains(target)
  const insideToolbarOverflow =
    toolbarOverflowTrigger.value?.contains(target) ||
    toolbarOverflowPopover.value?.contains(target)

  if (!insideFilter) {
    store.filterSheetOpen = false
  }

  if (!insideAnnotations) {
    annotationListOpen.value = false
  }

  if (!insideDateControls) {
    datePopoverOpen.value = false
  }

  if (!insideComparison) {
    comparisonPopoverOpen.value = false
  }

  if (!insideToolbarOverflow) {
    toolbarOverflowOpen.value = false
  }
}

function handleGlobalKeydown(event: KeyboardEvent) {
  if (event.key !== 'Escape') {
    return
  }

  if (toolbarOverflowOpen.value) {
    toolbarOverflowOpen.value = false
    return
  }

  if (datePopoverOpen.value) {
    datePopoverOpen.value = false
    return
  }

  if (comparisonPopoverOpen.value) {
    comparisonPopoverOpen.value = false
    return
  }

  if (isChartFullscreen.value) {
    void exitChartFullscreen()
    return
  }
  store.filterSheetOpen = false
  annotationListOpen.value = false
}

function handleChartPointer(event: { offsetX: number; offsetY: number }) {
  if (!store.analytics?.series.length) {
    return
  }

  const chart = chartRef.value
  if (!chart?.containPixel({ gridIndex: 0 }, [event.offsetX, event.offsetY])) {
    return
  }

  const coordinate = chart.convertFromPixel({ seriesIndex: 0 }, [event.offsetX, event.offsetY])
  const day = extractChartDay(coordinate)
  if (!day) {
    return
  }

  store.openCreateAnnotation(day)
}

async function toggleChartFullscreen() {
  if (isChartFullscreen.value) {
    await exitChartFullscreen()
    return
  }

  datePopoverOpen.value = false

  const element = chartPanel.value
  if (!element) {
    return
  }

  try {
    await requestChartFullscreen(element)
  } catch {
    isChartFullscreen.value = true
    queueChartResize()
  }
}

function syncFullscreenState() {
  const fullscreenElement = getFullscreenElement()
  const isChartElement = fullscreenElement === chartPanel.value
  isChartFullscreen.value = isChartElement
  queueChartResize()
}

function queueChartResize() {
  window.setTimeout(() => {
    chartRef.value?.resize()
    chartJsRef.value?.resize()
  }, 60)
}

function extractChartDay(coordinate: number | string | number[] | string[]): string | null {
  const rawValue = Array.isArray(coordinate) ? coordinate[0] : coordinate
  if (rawValue == null) {
    return null
  }

  if (typeof rawValue === 'string') {
    return rawValue.slice(0, 10)
  }

  const date = new Date(rawValue)
  if (Number.isNaN(date.getTime())) {
    return null
  }

  return [
    date.getFullYear(),
    String(date.getMonth() + 1).padStart(2, '0'),
    String(date.getDate()).padStart(2, '0'),
  ].join('-')
}

function getFullscreenElement(): Element | null {
  const webkitDocument = document as Document & { webkitFullscreenElement?: Element | null }
  return document.fullscreenElement ?? webkitDocument.webkitFullscreenElement ?? null
}

async function requestChartFullscreen(element: HTMLElement) {
  const webkitElement = element as HTMLElement & {
    webkitRequestFullscreen?: () => Promise<void> | void
    webkitRequestFullScreen?: () => Promise<void> | void
  }

  if (element.requestFullscreen) {
    try {
      await element.requestFullscreen({ navigationUI: 'hide' })
      return
    } catch {
      await element.requestFullscreen()
      return
    }
  }

  if (webkitElement.webkitRequestFullscreen) {
    await webkitElement.webkitRequestFullscreen()
    return
  }

  if (webkitElement.webkitRequestFullScreen) {
    await webkitElement.webkitRequestFullScreen()
  }
}

async function exitChartFullscreen() {
  if (!getFullscreenElement()) {
    isChartFullscreen.value = false
    queueChartResize()
    return
  }

  const webkitDocument = document as Document & {
    webkitExitFullscreen?: () => Promise<void> | void
    webkitCancelFullScreen?: () => Promise<void> | void
  }

  if (document.exitFullscreen) {
    await document.exitFullscreen()
    return
  }

  if (webkitDocument.webkitExitFullscreen) {
    await webkitDocument.webkitExitFullscreen()
    return
  }

  if (webkitDocument.webkitCancelFullScreen) {
    await webkitDocument.webkitCancelFullScreen()
  }
}

</script>

<template>
  <main class="dashboard-shell dashboard-page">
    <section v-if="store.error" class="alert alert--error">{{ store.error }}</section>

    <section class="hero-grid">
      <article ref="chartPanel" class="hero-card hero-card--wide hero-card--chart" :class="{ 'hero-card--fullscreen': isChartFullscreen }">
        <div class="hero-card__head">
          <h2 v-if="showToolbarTitle" class="chart-title">{{ store.selectedMetricLabel }}</h2>
          <div ref="chartToolbar" class="chart-toolbar" :class="`chart-toolbar--${toolbarTier}`">
            <label class="chart-toolbar__control">
              <span v-if="toolbarTier === 'wide'">Metric</span>
              <select v-model="store.controls.metric" data-testid="metric-selector">
                <option v-for="option in metricOptions" :key="option.value" :value="option.value">{{ option.label }}</option>
              </select>
            </label>
            <label v-if="showInlineGroupBy" class="chart-toolbar__control">
              <span v-if="toolbarTier === 'wide'">Group by</span>
              <select v-model="store.controls.groupBy" data-testid="group-selector">
                <option v-for="option in groupByOptions" :key="option.value" :value="option.value">{{ option.label }}</option>
              </select>
            </label>
            <div ref="filterTrigger" class="header-popover">
              <button
                class="button button--ghost"
                :aria-expanded="store.filterSheetOpen"
                aria-haspopup="dialog"
                data-testid="filter-toggle"
                type="button"
                @click="toggleFilterPopover"
              >
                <SlidersHorizontal :size="14" />
                <span v-if="toolbarTier !== 'micro'">Filters</span>
              </button>

              <aside
                v-if="store.filterSheetOpen"
                ref="filterPopover"
                class="popover-panel popover-panel--filters"
                data-testid="filter-area"
              >
                <div class="panel__header">
                  <div>
                    <p class="section-tag">Filters</p>
                    <h2>Refine the story</h2>
                  </div>
                  <button class="button button--ghost" type="button" @click="store.filterSheetOpen = false">Done</button>
                </div>

                <p class="popover-note">Selections update the chart, summary, and marker range automatically.</p>

                <div v-if="store.options" class="popover-panel__body filter-groups">
                  <section>
                    <h3>Authors</h3>
                    <label v-for="author in store.options.authors" :key="author.id" class="filter-check">
                      <input
                        :checked="store.controls.filters.authorIds.includes(author.id)"
                        type="checkbox"
                        @change="toggleInList(store.controls.filters.authorIds, author.id)"
                      />
                      <span class="filter-swatch" :style="authorSwatchStyle(author.id)" aria-hidden="true" />
                      <span>{{ displayAuthorName(author.id, author.displayName) }}</span>
                    </label>
                  </section>

                  <section>
                    <h3>Categories</h3>
                    <label v-for="category in store.options.categories" :key="category" class="filter-check">
                      <input
                        :checked="store.controls.filters.categories.includes(category)"
                        type="checkbox"
                        @change="toggleInList(store.controls.filters.categories, category)"
                      />
                      <span>{{ category }}</span>
                    </label>
                  </section>

                  <section>
                    <h3>Languages</h3>
                    <label v-for="language in store.options.languages" :key="language" class="filter-check">
                      <input
                        :checked="store.controls.filters.languages.includes(language)"
                        type="checkbox"
                        @change="toggleInList(store.controls.filters.languages, language)"
                      />
                      <span>{{ language }}</span>
                    </label>
                  </section>

                  <section>
                    <h3>Product codes</h3>
                    <div v-for="group in reposByProductCode" :key="group.productCode" class="filter-group-block">
                      <label class="filter-check filter-check--group">
                        <input
                          :checked="store.controls.filters.productCodes.includes(group.productCode)"
                          type="checkbox"
                          @change="toggleInList(store.controls.filters.productCodes, group.productCode)"
                        />
                        <span>{{ group.productCode }}</span>
                      </label>
                      <div class="filter-group-block__actions">
                        <button class="filter-link" type="button" @click="setRepoGroupSelection(group.repos.map((repo) => repo.id), true)">All</button>
                        <button class="filter-link" type="button" @click="setRepoGroupSelection(group.repos.map((repo) => repo.id), false)">None</button>
                      </div>
                      <div class="filter-subgroup">
                        <label v-for="repo in group.repos" :key="repo.id" class="filter-check filter-check--nested">
                          <input
                            :checked="store.controls.filters.repoIds.includes(repo.id)"
                            type="checkbox"
                            @change="toggleInList(store.controls.filters.repoIds, repo.id)"
                          />
                          <span>{{ repo.displayName }}</span>
                        </label>
                      </div>
                    </div>
                  </section>
                </div>
              </aside>
            </div>
            <div ref="annotationTrigger" class="header-popover">
              <button
                class="button button--ghost"
                :aria-expanded="annotationListOpen"
                aria-haspopup="dialog"
                data-testid="annotate-button"
                type="button"
                @click="toggleAnnotationPopover"
              >
                <PencilLine :size="14" />
                <span v-if="toolbarTier !== 'micro'">Annotate</span>
              </button>

              <aside
                v-if="annotationListOpen"
                ref="annotationPopover"
                class="popover-panel popover-panel--annotations"
                data-testid="annotation-popover"
              >
                <div class="panel__header">
                  <div>
                    <p class="section-tag">Annotations</p>
                    <h2>Story markers</h2>
                  </div>
                  <button class="button button--ghost" data-testid="annotation-new-button" type="button" @click="openAnnotationComposer()">New annotation</button>
                </div>

                <p class="popover-note">Use markers to label launches, incidents, and turning points directly on the timeline.</p>

                <div v-if="store.loading.annotations" class="state state--compact">Loading annotations…</div>
                <div v-else-if="!store.annotations.length" class="state state--compact">No annotations in the selected window.</div>
                <ul v-else class="annotation-list annotation-list--compact" data-testid="annotation-list">
                  <li v-for="annotation in store.annotations" :key="annotation.annotationId">
                    <button class="annotation-pill" :data-testid="`annotation-pill-${annotation.annotationId}`" @click="selectAnnotation(annotation)">
                      <span>{{ annotation.day }}</span>
                      <strong>{{ annotation.title }}</strong>
                      <small>{{ annotation.type }}</small>
                    </button>
                  </li>
                </ul>
              </aside>
            </div>
            <div v-if="showInlineChartMode" class="segmented">
              <button :class="{ active: store.chartMode === 'area' }" @click="setChartMode('area')">Area</button>
              <button :class="{ active: store.chartMode === 'line' }" @click="setChartMode('line')">Line</button>
            </div>
            <button class="button button--ghost" data-testid="anonymize-toggle" type="button" @click="anonymizeAuthors = !anonymizeAuthors">
              <EyeOff v-if="anonymizeAuthors" :size="14" />
              <Eye v-else :size="14" />
              <span v-if="toolbarTier !== 'micro'">{{ anonymizeAuthors ? 'Reveal' : 'Anonymize' }}</span>
            </button>
            <div ref="dateTrigger" class="header-popover">
              <button class="button button--ghost" data-testid="date-controls-toggle" type="button" @click="toggleDatePopover">
                <CalendarDays :size="14" />
                {{ toolbarDateLabel }}
              </button>
              <aside
                v-if="datePopoverOpen"
                ref="datePopover"
                class="popover-panel popover-panel--date"
                data-testid="date-controls-popover"
              >
                <div class="panel__header">
                  <div>
                    <p class="section-tag">Window</p>
                    <h2>Date range</h2>
                  </div>
                  <button class="button button--ghost" type="button" @click="datePopoverOpen = false">Done</button>
                </div>
                <p class="popover-note">{{ store.controls.dateRange.from }} to {{ store.controls.dateRange.to }} · {{ comparisonModeLabel }}</p>
                <div class="popover-panel__body date-popover-grid">
                  <label class="date-popover__preset">
                    Range
                    <select v-model="datePreset" data-testid="date-preset" @change="applyDatePreset">
                      <option value="custom">Custom</option>
                      <option v-for="preset in datePresetOptions" :key="preset.value" :value="preset.value">{{ preset.label }}</option>
                    </select>
                  </label>
                  <label>
                    From
                    <input v-model="store.controls.dateRange.from" data-testid="date-from" type="date" @change="handleDateInputChange" />
                  </label>
                  <label>
                    To
                    <input v-model="store.controls.dateRange.to" data-testid="date-to" type="date" @change="handleDateInputChange" />
                  </label>
                  <label>
                    Compare
                    <select v-model="store.controls.comparisonMode">
                      <option value="previous_equivalent_period">Previous equivalent period</option>
                      <option value="same_period_last_year">Same period last year</option>
                    </select>
                  </label>
                </div>
              </aside>
            </div>
            <div ref="comparisonTrigger" class="header-popover">
              <button class="button button--ghost" data-testid="comparison-summary-toggle" type="button" @click="toggleComparisonPopover">
                <BellRing :size="14" />
                <span v-if="toolbarTier === 'wide' || toolbarTier === 'medium'">Comparison</span>
              </button>
              <aside
                v-if="comparisonPopoverOpen"
                ref="comparisonPopover"
                class="popover-panel popover-panel--comparison"
                data-testid="comparison-summary-popover"
              >
                <div class="panel__header">
                  <div>
                    <p class="section-tag">
                      <BellRing :size="14" />
                      Comparison
                    </p>
                    <h2>Summary</h2>
                  </div>
                  <button class="button button--ghost" type="button" @click="comparisonPopoverOpen = false">Done</button>
                </div>
                <div class="comparison-stack">
                  <div class="comparison-chip" :class="`comparison-chip--${comparisonCurrentTone}`">
                    <span>Current</span>
                    <div class="comparison-chip__value">
                      <component :is="comparisonCurrentIcon" class="comparison-chip__trend" :size="16" />
                      <strong>{{ formatMetric(currentTotals?.linesAdded) }}</strong>
                    </div>
                  </div>
                  <div class="comparison-chip" :class="`comparison-chip--${comparisonReferenceTone}`">
                    <span>Reference</span>
                    <div class="comparison-chip__value">
                      <component :is="comparisonReferenceIcon" class="comparison-chip__trend" :size="16" />
                      <strong>{{ formatMetric(store.comparison?.comparison.totals.linesAdded) }}</strong>
                    </div>
                  </div>
                  <div class="comparison-chip comparison-chip--accent" :class="`comparison-chip--${comparisonDeltaTone}`">
                    <span>Delta</span>
                    <div class="comparison-chip__value">
                      <component :is="comparisonDeltaIcon" class="comparison-chip__trend" :size="16" />
                      <strong>{{ comparisonDeltaLabel }}</strong>
                    </div>
                  </div>
                </div>
                <p class="comparison-footnote">
                  Comparison is computed against the same filters and reference period.
                </p>
              </aside>
            </div>
            <button
              v-if="showInlineCompareToggle"
              class="button button--ghost"
              data-testid="comparison-overlay-toggle"
              type="button"
              @click="store.comparisonOverlayVisible = !store.comparisonOverlayVisible"
            >
              <Eye v-if="store.comparisonOverlayVisible" :size="14" />
              <EyeOff v-else :size="14" />
              {{ compareToggleLabel }}
            </button>
            <button
              v-if="showInlineLegendToggle"
              class="button button--ghost"
              data-testid="legend-toggle"
              type="button"
              @click="store.legendVisible = !store.legendVisible"
            >
              <Eye v-if="store.legendVisible" :size="14" />
              <EyeOff v-else :size="14" />
              {{ legendToggleLabel }}
            </button>
            <div v-if="showToolbarOverflow" ref="toolbarOverflowTrigger" class="header-popover">
              <button class="button button--ghost button--icon" data-testid="chart-toolbar-overflow-toggle" type="button" @click="toggleToolbarOverflow">
                <MoreHorizontal :size="16" />
              </button>
              <aside
                v-if="toolbarOverflowOpen"
                ref="toolbarOverflowPopover"
                class="popover-panel popover-panel--toolbar"
                data-testid="chart-toolbar-overflow-popover"
              >
                <div class="panel__header">
                  <div>
                    <p class="section-tag">Toolbar</p>
                    <h2>More controls</h2>
                  </div>
                  <button class="button button--ghost" type="button" @click="toolbarOverflowOpen = false">Done</button>
                </div>
                <div class="popover-panel__body chart-controls-grid">
                  <label v-if="!showInlineGroupBy">
                    Group by
                    <select v-model="store.controls.groupBy" data-testid="group-selector-overflow">
                      <option v-for="option in groupByOptions" :key="option.value" :value="option.value">{{ option.label }}</option>
                    </select>
                  </label>
                  <div v-if="!showInlineChartMode" class="chart-controls-grid__row">
                    <span class="chart-controls-grid__label">Display</span>
                    <div class="segmented">
                      <button :class="{ active: store.chartMode === 'area' }" @click="setChartMode('area')">Area</button>
                      <button :class="{ active: store.chartMode === 'line' }" @click="setChartMode('line')">Line</button>
                    </div>
                  </div>
                  <div class="chart-controls-grid__actions">
                    <button
                      v-if="!showInlineCompareToggle"
                      class="button button--ghost"
                      type="button"
                      @click="store.comparisonOverlayVisible = !store.comparisonOverlayVisible"
                    >
                      <Eye v-if="store.comparisonOverlayVisible" :size="14" />
                      <EyeOff v-else :size="14" />
                      {{ store.comparisonOverlayVisible ? 'Hide compare' : 'Show compare' }}
                    </button>
                    <button
                      v-if="!showInlineLegendToggle"
                      class="button button--ghost"
                      type="button"
                      @click="store.legendVisible = !store.legendVisible"
                    >
                      <Eye v-if="store.legendVisible" :size="14" />
                      <EyeOff v-else :size="14" />
                      {{ store.legendVisible ? 'Hide legend' : 'Show legend' }}
                    </button>
                  </div>
                </div>
              </aside>
            </div>
            <button class="button button--ghost" data-testid="chart-fullscreen-toggle" type="button" @click="toggleChartFullscreen">
              <Minimize2 v-if="isChartFullscreen" :size="14" />
              <Expand v-else :size="14" />
            </button>
          </div>
        </div>

        <div class="chart-frame" data-testid="chart-area">
          <div v-if="store.loading.bootstrap || store.loading.analytics" class="state state--loading" data-testid="chart-loading-state">
            <LoaderCircle class="spin" :size="22" />
            Loading dashboard data…
          </div>
          <div v-else-if="!store.analytics?.series.length" class="state" data-testid="chart-empty-state">
            <Sparkles :size="20" />
            No chart data yet. Run a sync or widen the filters.
          </div>
          <div v-else class="chart-rendered" data-testid="chart-rendered">
            <VChart
              v-if="chartLibrary === 'echarts'"
              ref="chartRef"
              class="chart"
              :option="chartOption"
              autoresize
              @zr:click="handleChartPointer"
            />
            <ChartJsRenderer
              v-else
              ref="chartJsRef"
              class="chart"
              :analytics="store.analytics"
              :comparison-analytics="store.comparisonAnalytics"
              :comparison="store.comparison"
              :annotations="store.annotations"
              :chart-mode="store.chartMode"
              :comparison-overlay-visible="store.comparisonOverlayVisible"
              :legend-visible="store.legendVisible"
              :anonymize-authors="anonymizeAuthors"
              :author-label-by-id="Object.fromEntries(authorAliasMap)"
              @day-select="openAnnotationComposer"
            />
          </div>
        </div>
      </article>

    </section>

    <section class="kpi-grid">
      <article class="kpi-card">
        <div class="kpi-card__head">
          <span>Lines Added</span>
          <div class="kpi-card__icon kpi-card__icon--accent">
            <Plus :size="15" />
          </div>
        </div>
        <strong>{{ formatMetric(currentTotals?.linesAdded) }}</strong>
      </article>
      <article class="kpi-card">
        <div class="kpi-card__head">
          <span>Net Lines</span>
          <div class="kpi-card__icon kpi-card__icon--warning">
            <ArrowUpDown :size="15" />
          </div>
        </div>
        <strong>{{ formatMetric(currentTotals?.netLines) }}</strong>
      </article>
      <article class="kpi-card">
        <div class="kpi-card__head">
          <span>Canonical Commits</span>
          <div class="kpi-card__icon kpi-card__icon--success">
            <GitCommitHorizontal :size="15" />
          </div>
        </div>
        <strong>{{ formatMetric(currentTotals?.commitCount) }}</strong>
      </article>
      <article class="kpi-card">
        <div class="kpi-card__head">
          <span>Files Changed</span>
          <div class="kpi-card__icon kpi-card__icon--neutral">
            <Files :size="15" />
          </div>
        </div>
        <strong>{{ formatMetric(currentTotals?.fileCount) }}</strong>
      </article>
    </section>
    <dialog
      ref="annotationDialog"
      class="dialog"
      data-testid="annotation-dialog"
      @cancel.prevent="closeAnnotationDialog"
      @close="handleDialogClose"
    >
      <form class="dialog__card" method="dialog" @submit.prevent="store.saveAnnotation()">
        <div class="panel__header">
          <div>
            <p class="section-tag">Annotation</p>
            <h2>{{ store.draftAnnotation.annotationId == null ? 'Create marker' : 'Edit marker' }}</h2>
          </div>
          <button class="button button--ghost" data-testid="annotation-close" type="button" @click="closeAnnotationDialog">Close</button>
        </div>

        <label>
          Day
          <input v-model="store.draftAnnotation.day" data-testid="annotation-day" type="date" />
        </label>
        <label>
          Title
          <input v-model="store.draftAnnotation.title" data-testid="annotation-title" required type="text" />
        </label>
        <label>
          Type
          <select v-model="store.draftAnnotation.type" data-testid="annotation-type">
            <option v-for="type in annotationTypes" :key="type" :value="type">{{ type }}</option>
          </select>
        </label>
        <label>
          Accent
          <select v-model="store.draftAnnotation.colorToken" data-testid="annotation-color">
            <option value="accent">Accent</option>
            <option value="warning">Warning</option>
            <option value="danger">Danger</option>
            <option value="success">Success</option>
          </select>
        </label>
        <label>
          Description
          <textarea v-model="store.draftAnnotation.description" data-testid="annotation-description" rows="4" />
        </label>

        <div class="dialog__actions">
          <button
            v-if="store.draftAnnotation.annotationId != null"
            class="button button--danger"
            data-testid="annotation-delete"
            type="button"
            @click="store.removeAnnotation(store.draftAnnotation.annotationId)"
          >
            Delete
          </button>
          <button class="button button--primary" data-testid="annotation-save" type="submit">Save annotation</button>
        </div>
      </form>
    </dialog>
  </main>
</template>
