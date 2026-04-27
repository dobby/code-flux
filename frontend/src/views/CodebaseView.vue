<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import VChart from 'vue-echarts'
import type { EChartsOption } from 'echarts'
import { Folder, TrendingUp } from 'lucide-vue-next'
import { useTheme } from '../composables/useTheme'
import '../lib/chart'
import { useDashboardStore } from '../stores/dashboard'
import { useActivityStore } from '../stores/activity'
import { useAppearanceStore } from '../stores/appearance'
import { useCodebaseStore, type CodebaseTreemapSizeMode } from '../stores/codebase'
import type { CodebaseBreakdownRow, CodebaseTreeNode } from '../types/workspace'

const props = defineProps<{
  repoId?: string
}>()

type TreemapDatum = {
  id: string
  name: string
  value: number
  path: string
  kind: string
  linesCount: number
  filesCount: number
  netLines: number
  itemStyle?: Record<string, string | number>
  children?: TreemapDatum[]
}

const route = useRoute()
const router = useRouter()
const dashboard = useDashboardStore()
const explorer = useActivityStore()
const codebase = useCodebaseStore()
const appearance = useAppearanceStore()
const { isDark } = useTheme()

const sizeMode = ref<CodebaseTreemapSizeMode>('loc')

const enabledRepos = computed(() => (dashboard.bootstrap?.repos ?? []).filter((repo) => repo.enabled))
const routeRepoId = computed(() => props.repoId ?? (typeof route.params.repoId === 'string' ? route.params.repoId : ''))
const activeRepoId = computed(() => {
  const enabledRepoIds = new Set(enabledRepos.value.map((repo) => repo.id))
  const selectedRepoId = explorer.selectedRepoIds.find((repoId) => enabledRepoIds.has(repoId)) ?? ''
  return selectedRepoId || enabledRepos.value[0]?.id || ''
})

watch(
  () => [
    enabledRepos.value.map((repo) => repo.id).join(','),
    routeRepoId.value,
    explorer.selectedRepoIds.join(','),
    route.name,
  ] as const,
  async ([repoKey, legacyRepoId, selectedRepoIds, routeName]) => {
    if (!repoKey) {
      return
    }

    const enabledRepoIds = new Set(enabledRepos.value.map((repo) => repo.id))
    const normalizedLegacyRepoId = legacyRepoId && enabledRepoIds.has(legacyRepoId) ? legacyRepoId : ''
    const normalizedSelectedRepoId = selectedRepoIds
      .split(',')
      .find((repoId) => repoId && enabledRepoIds.has(repoId)) ?? ''
    const nextRepoId = normalizedLegacyRepoId || normalizedSelectedRepoId || enabledRepos.value[0]?.id || ''

    if (!nextRepoId) {
      return
    }

    if (explorer.selectedRepoIds.length !== 1 || explorer.selectedRepoIds[0] !== nextRepoId) {
      explorer.setRepoFilters([nextRepoId])
    }

    if (routeName === 'codebase-repo') {
      await router.replace({ name: 'codebase' })
    }
  },
  { immediate: true },
)

watch(
  () => [
    activeRepoId.value,
    explorer.effectiveDateRange.from,
    explorer.effectiveDateRange.to,
    explorer.selectedAuthorIds.join(','),
    explorer.selectedLanguages.join(','),
    explorer.selectedCategories.join(','),
    explorer.selectedProductCodes.join(','),
  ] as const,
  ([repoId]) => {
    void codebase.refresh(repoId)
  },
  { immediate: true },
)

function formatSignedNumber(value: number) {
  const rounded = Math.round(value)
  const prefix = rounded > 0 ? '+' : ''
  return `${prefix}${rounded.toLocaleString('en-US')}`
}

function formatInteger(value: number) {
  return Math.round(value).toLocaleString('en-US')
}

function sampleEvenly<T>(values: T[], count: number): T[] {
  if (count <= 0 || values.length === 0) {
    return []
  }
  if (values.length <= count) {
    return values
  }

  const lastIndex = values.length - 1
  const seen = new Set<number>()
  const sampled: T[] = []

  for (let index = 0; index < count; index += 1) {
    const scaledIndex = Math.round((index * lastIndex) / Math.max(count - 1, 1))
    if (seen.has(scaledIndex)) {
      continue
    }
    seen.add(scaledIndex)
    sampled.push(values[scaledIndex])
  }

  return sampled
}

function normalizeChartHeights(values: number[], minHeight = 36, maxHeight = 118) {
  if (!values.length) {
    return []
  }

  const domainMin = Math.min(...values)
  const domainMax = Math.max(...values)

  if (domainMin === domainMax) {
    return values.map(() => Math.round((minHeight + maxHeight) / 2))
  }

  return values.map((value) => {
    const ratio = (value - domainMin) / (domainMax - domainMin)
    return Math.round(minHeight + ratio * (maxHeight - minHeight))
  })
}

function shareLabel(row: CodebaseBreakdownRow, total: number) {
  if (total <= 0) {
    return `${row.label} 0%`
  }
  return `${row.label} ${Math.round((row.linesCount / total) * 100)}%`
}

function modeValue(node: CodebaseTreeNode, mode: CodebaseTreemapSizeMode) {
  switch (mode) {
    case 'files':
      return node.filesCount
    case 'net':
      return Math.abs(node.netLines)
    case 'loc':
    default:
      return node.linesCount
  }
}

function netColor(netLines: number) {
  if (netLines > 0) {
    return '#0f766e'
  }
  if (netLines < 0) {
    return '#c2410c'
  }
  return '#94a3b8'
}

function buildTreemapDatum(node: CodebaseTreeNode, mode: CodebaseTreemapSizeMode, depth: number, colorIndex: number): TreemapDatum {
  const children = node.children.map((child, childIndex) => buildTreemapDatum(child, mode, depth + 1, depth === 0 ? childIndex : colorIndex))
  const datum: TreemapDatum = {
    id: node.key,
    name: node.label,
    value: modeValue(node, mode),
    path: node.path || '/',
    kind: node.kind,
    linesCount: node.linesCount,
    filesCount: node.filesCount,
    netLines: node.netLines,
    children: children.length ? children : undefined,
  }

  if (mode === 'net') {
    datum.itemStyle = {
      color: netColor(node.netLines),
      borderColor: isDark.value ? 'rgba(15, 23, 42, 0.88)' : 'rgba(255, 255, 255, 0.82)',
    }
  } else if (depth === 0) {
    datum.itemStyle = {
      color: ['#3d5dcc', '#0f9f86', '#d97706', '#94a3b8', '#2563eb', '#9333ea'][colorIndex % 6],
    }
  }

  return datum
}

const languageLines = computed(() => {
  const total = codebase.summary?.visibleLines ?? 0
  return codebase.languageBreakdown.slice(0, 3).map((row) => shareLabel(row, total))
})

const categoryLines = computed(() => {
  const total = codebase.summary?.visibleLines ?? 0
  return codebase.categoryBreakdown.slice(0, 3).map((row) => shareLabel(row, total))
})

const snapshotLines = computed(() => {
  const summary = codebase.summary
  return [
    `Last snapshot: ${summary?.lastSnapshotDate ?? 'Unavailable'}`,
    `Total files: ${formatInteger(summary?.repoFiles ?? 0)}`,
    `Total LOC: ${formatInteger(summary?.repoLines ?? 0)}`,
  ]
})

const summaryCards = computed(() => [
  {
    key: 'languages',
    title: 'Languages',
    lines: languageLines.value.length ? languageLines.value : ['No language data'],
  },
  {
    key: 'categories',
    title: 'Categories',
    lines: categoryLines.value.length ? categoryLines.value : ['No category data'],
  },
  {
    key: 'snapshot',
    title: 'Snapshot',
    lines: snapshotLines.value,
  },
])

const modeOptions: Array<{ id: CodebaseTreemapSizeMode; label: string }> = [
  { id: 'loc', label: 'LOC' },
  { id: 'files', label: 'Files' },
  { id: 'net', label: 'Net activity' },
]

const growthChartOption = computed<EChartsOption | null>(() => {
  if (!codebase.growthPoints.length) {
    return null
  }

  const actualSource = sampleEvenly(
    codebase.growthPoints.filter((point) => point.snapshotLines !== null),
    5,
  )
  const cumulativeSource = sampleEvenly(codebase.growthPoints, 5)
  const actualRaw = actualSource.map((point) => Number(point.snapshotLines ?? 0))
  const cumulativeRaw = cumulativeSource.map((point) => Math.abs(Number(point.cumulativeNetLines ?? 0)))
  const actualHeights = normalizeChartHeights(actualRaw, 44, 122)
  const cumulativeHeights = normalizeChartHeights(cumulativeRaw, 36, 112)

  if (!actualHeights.length && !cumulativeHeights.length) {
    return null
  }

  const actualPalette = ['#C7D2FE', '#A5B4FC', '#818CF8', '#6366F1', '#4F46E5']
  const cumulativePalette = ['#A7F3D0', '#6EE7B7', '#34D399', '#10B981', '#059669']
  const categories = [
    ...actualHeights.map((_, index) => `actual-${index}`),
    'gap',
    ...cumulativeHeights.map((_, index) => `cumulative-${index}`),
  ]
  const chartData = [
    ...actualHeights.map((value, index) => ({
      value,
      rawValue: actualRaw[index],
      day: actualSource[index]?.day ?? '',
      seriesLabel: 'Actual LOC',
      itemStyle: {
        color: actualPalette[index % actualPalette.length],
        borderRadius: [5, 5, 0, 0],
      },
    })),
    {
      value: 0,
      rawValue: 0,
      day: '',
      seriesLabel: '',
      itemStyle: {
        color: 'rgba(0,0,0,0)',
      },
      tooltip: { show: false },
    },
    ...cumulativeHeights.map((value, index) => ({
      value,
      rawValue: cumulativeRaw[index],
      day: cumulativeSource[index]?.day ?? '',
      seriesLabel: 'Cumulative net',
      itemStyle: {
        color: cumulativePalette[index % cumulativePalette.length],
        borderRadius: [5, 5, 0, 0],
      },
    })),
  ]

  return {
    backgroundColor: 'transparent',
    animation: appearance.animateCharts,
    animationDuration: appearance.animateCharts ? 260 : 0,
    tooltip: {
      trigger: 'item',
      formatter: (params: unknown) => {
        const row = ((params as { data?: { rawValue?: number; day?: string; seriesLabel?: string } | null } | null)?.data ?? null)
        if (!row?.seriesLabel) {
          return ''
        }
        return `${row.seriesLabel}<br/>${row.day}: ${formatInteger(row.rawValue ?? 0)}`
      },
    },
    grid: {
      left: 2,
      right: '42%',
      top: 4,
      bottom: 0,
      containLabel: false,
    },
    xAxis: {
      type: 'category',
      data: categories,
      axisLine: { show: false },
      axisTick: { show: false },
      axisLabel: { show: false },
    },
    yAxis: {
      type: 'value',
      max: 128,
      axisLine: { show: false },
      axisTick: { show: false },
      axisLabel: { show: false },
      splitLine: { show: false },
    },
    series: [
      {
        type: 'bar',
        barWidth: 16,
        barCategoryGap: '22%',
        data: chartData,
      },
    ],
  }
})

const treemapState = computed(() => {
  if (codebase.loading) {
    return 'loading'
  }
  if (!codebase.summary) {
    return 'loading'
  }
  if (codebase.summary.repoFiles === 0) {
    return 'no-structure'
  }
  if (codebase.summary.visibleFiles === 0) {
    return 'filtered-empty'
  }
  if (sizeMode.value === 'net' && codebase.summary.activeFilesInRange === 0) {
    return 'no-activity'
  }
  return 'ready'
})

const treemapFeedback = computed(() => {
  switch (treemapState.value) {
    case 'loading':
      return {
        title: 'Codebase structure is loading.',
        body: 'Pulling the current repository inventory and filter-aware structure now.',
      }
    case 'filtered-empty':
      return {
        title: 'No files match the current Codebase filters.',
        body: 'Broaden the author, language, category, or product filters to reveal structure again.',
      }
    case 'no-activity':
      return {
        title: 'No net activity exists in this range.',
        body: 'Switch to LOC or Files, or widen the date range to visualize net movement.',
      }
    case 'no-structure':
    default:
      return {
        title: 'No current inventory is available for this repository yet.',
        body: 'Build snapshots to populate the structure view.',
      }
  }
})

const treemapOption = computed<EChartsOption | null>(() => {
  if (treemapState.value !== 'ready' || !codebase.treemap) {
    return null
  }

  const data = codebase.treemap.children.map((node, index) => buildTreemapDatum(node, sizeMode.value, 0, index))

  return {
    backgroundColor: 'transparent',
    animation: appearance.animateCharts,
    animationDuration: appearance.animateCharts ? 260 : 0,
    tooltip: {
      formatter: (params: unknown) => {
        const dataPoint = ((params as { data?: TreemapDatum | null } | null)?.data ?? null)
        if (!dataPoint) {
          return ''
        }
        return [
          `<strong>${dataPoint.name}</strong>`,
          dataPoint.path === '/' ? 'Path: /' : `Path: ${dataPoint.path}`,
          `LOC: ${formatInteger(dataPoint.linesCount)}`,
          `Files: ${formatInteger(dataPoint.filesCount)}`,
          `Range net: ${formatSignedNumber(dataPoint.netLines)}`,
        ].join('<br/>')
      },
    },
    series: [
      {
        type: 'treemap',
        roam: false,
        nodeClick: 'zoomToNode',
        breadcrumb: {
          show: true,
          height: 28,
          emptyItemWidth: 20,
          itemStyle: {
            color: isDark.value ? 'rgba(15, 23, 42, 0.74)' : 'rgba(255, 255, 255, 0.88)',
            borderColor: isDark.value ? 'rgba(120, 136, 168, 0.28)' : 'rgba(148, 163, 184, 0.25)',
          },
          textStyle: {
            color: isDark.value ? '#d7deef' : '#334155',
          },
        },
        label: {
          show: true,
          color: isDark.value ? '#f8fafc' : '#0f172a',
          formatter: '{b}',
          overflow: 'truncate',
        },
        upperLabel: {
          show: true,
          height: 22,
          color: isDark.value ? '#e2e8f0' : '#0f172a',
        },
        itemStyle: {
          borderColor: isDark.value ? 'rgba(15, 23, 42, 0.84)' : 'rgba(255, 255, 255, 0.82)',
          borderWidth: 1,
          gapWidth: 3,
        },
        levels: [
          {
            itemStyle: {
              borderColorSaturation: 0.65,
              gapWidth: 4,
              borderWidth: 1,
            },
          },
          {
            colorSaturation: [0.28, 0.56],
            itemStyle: {
              gapWidth: 2,
            },
          },
          {
            colorSaturation: [0.18, 0.42],
            itemStyle: {
              gapWidth: 1,
            },
          },
        ],
        data,
      },
    ],
  }
})
</script>

<template>
  <section class="codebase-view" data-testid="codebase-view">
    <div v-if="codebase.error" class="codebase-feedback codebase-feedback--error" data-testid="codebase-growth-error">
      <strong>Codebase data could not load.</strong>
      <p>{{ codebase.error }}</p>
    </div>

    <div v-else class="codebase-layout">
      <article class="codebase-card codebase-card--growth">
        <div class="codebase-card__header">
          <div class="codebase-card__title">
            <TrendingUp :size="16" />
            <span>Growth Overview</span>
          </div>
          <div class="codebase-card__legend">
            <div class="codebase-card__legend-pill codebase-card__legend-pill--actual">
              <span class="codebase-card__legend-dot" />
              <span>Actual LOC</span>
            </div>
            <div class="codebase-card__legend-pill codebase-card__legend-pill--net">
              <span class="codebase-card__legend-dot" />
              <span>Cumulative net</span>
            </div>
          </div>
        </div>

        <div class="codebase-card__surface codebase-card__surface--growth">
          <VChart
            v-if="growthChartOption"
            class="codebase-growth-chart"
            data-testid="codebase-growth-chart"
            :autoresize="true"
            :option="growthChartOption"
          />
          <div v-else class="codebase-feedback codebase-feedback--surface" data-testid="codebase-growth-chart-empty">
            <strong>No growth history is available for this repository yet.</strong>
            <p>Expand the date range or build snapshots to populate the time-series view.</p>
          </div>
        </div>
      </article>

      <article class="codebase-card codebase-card--structure" data-testid="codebase-treemap">
        <div class="codebase-card__header">
          <div class="codebase-card__title">
            <Folder :size="16" />
            <span>Current Structure</span>
          </div>
          <div class="codebase-mode-switcher" data-testid="codebase-size-mode-switcher">
            <button
              v-for="option in modeOptions"
              :key="option.id"
              class="codebase-mode-switcher__button"
              :class="{ 'codebase-mode-switcher__button--active': sizeMode === option.id }"
              :data-testid="`codebase-size-mode-${option.id}`"
              type="button"
              @click="sizeMode = option.id"
            >
              {{ option.label }}
            </button>
          </div>
        </div>

        <div class="codebase-card__surface codebase-card__surface--structure">
          <div v-if="!treemapOption" class="codebase-feedback codebase-feedback--surface" data-testid="codebase-treemap-empty-no-structure">
            <strong>{{ treemapFeedback.title }}</strong>
            <p>{{ treemapFeedback.body }}</p>
          </div>

          <VChart
            v-else
            class="codebase-structure-chart"
            data-testid="codebase-treemap"
            :autoresize="true"
            :option="treemapOption"
          />
        </div>
      </article>

      <div class="codebase-summary-row" data-testid="codebase-summary-row">
        <article
          v-for="card in summaryCards"
          :key="card.key"
          class="codebase-summary-card"
          :data-testid="card.key === 'languages' ? 'codebase-breakdown-languages' : card.key === 'categories' ? 'codebase-breakdown-categories' : 'codebase-snapshot-summary'"
        >
          <span class="codebase-summary-card__title">{{ card.title }}</span>
          <div class="codebase-summary-card__lines">
            <span v-for="line in card.lines" :key="line">{{ line }}</span>
          </div>
        </article>
      </div>
    </div>
  </section>
</template>

<style scoped>
.codebase-view {
  min-height: 100%;
  padding: 24px;
  box-sizing: border-box;
}

.codebase-layout {
  display: grid;
  gap: 16px;
}

.codebase-card,
.codebase-summary-card,
.codebase-feedback {
  border: 1px solid rgba(148, 163, 184, 0.22);
  border-radius: 10px;
  background: #ffffff;
  box-shadow: none;
}

.codebase-card {
  display: grid;
  gap: 12px;
  padding: 16px;
}

.codebase-card--growth {
  min-height: 208px;
}

.codebase-card--structure {
  min-height: 288px;
}

.codebase-card__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.codebase-card__title {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
  color: #61708d;
  font-size: 12px;
  font-weight: 600;
}

.codebase-card__title :deep(svg) {
  color: var(--cf-accent);
}

.codebase-card__surface {
  border-radius: 8px;
  background: #f8fafc;
}

.codebase-card__surface--growth {
  padding: 12px;
}

.codebase-card__surface--structure {
  min-height: 222px;
  overflow: hidden;
}

.codebase-card__legend {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  margin-left: auto;
}

.codebase-card__legend-pill {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  height: 22px;
  padding: 0 8px;
  border-radius: 999px;
  font-size: 11px;
  font-weight: 500;
}

.codebase-card__legend-pill--actual {
  background: color-mix(in srgb, var(--cf-accent) 10%, transparent);
  color: var(--cf-accent);
}

.codebase-card__legend-pill--net {
  background: #ecfdf5;
  color: #047857;
}

.codebase-card__legend-dot {
  width: 8px;
  height: 8px;
  border-radius: 999px;
  background: currentColor;
}

.codebase-growth-chart {
  height: 144px;
}

.codebase-mode-switcher {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  margin-left: auto;
}

.codebase-mode-switcher__button {
  min-height: 22px;
  border: 1px solid rgba(148, 163, 184, 0.22);
  border-radius: 999px;
  background: #ffffff;
  color: #61708d;
  font-size: 11px;
  font-weight: 500;
  padding: 0 8px;
  cursor: pointer;
}

.codebase-mode-switcher__button--active {
  border-color: color-mix(in srgb, var(--cf-accent) 16%, transparent);
  background: color-mix(in srgb, var(--cf-accent) 10%, transparent);
  color: var(--cf-accent);
  font-weight: 600;
}

.codebase-structure-chart {
  height: 222px;
}

.codebase-summary-row {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px;
}

.codebase-summary-card {
  display: grid;
  gap: 8px;
  min-height: 148px;
  padding: 16px;
}

.codebase-summary-card__title {
  color: #61708d;
  font-size: 12px;
  font-weight: 600;
}

.codebase-summary-card__lines {
  display: grid;
  gap: 5px;
}

.codebase-summary-card__lines span {
  display: block;
  color: #162033;
  font-size: 12px;
  font-weight: 500;
}

.codebase-feedback {
  display: grid;
  gap: 10px;
  padding: 20px;
}

.codebase-feedback--surface {
  min-height: 100%;
  align-content: center;
}

.codebase-feedback strong {
  font-size: 14px;
}

.codebase-feedback p {
  margin: 0;
  color: #61708d;
  font-size: 12px;
  line-height: 1.5;
}

.codebase-feedback--error {
  border-color: rgba(239, 68, 68, 0.24);
}

.dark .codebase-view {
  color: #d7def2;
}

.dark .codebase-card,
.dark .codebase-summary-card,
.dark .codebase-feedback {
  border-color: rgba(103, 122, 160, 0.22);
  background: rgba(19, 25, 36, 0.96);
}

.dark .codebase-card__title,
.dark .codebase-summary-card__title,
.dark .codebase-feedback p {
  color: #9aa8c7;
}

.dark .codebase-card__surface {
  background: rgba(28, 36, 51, 0.92);
}

.dark .codebase-card__legend-pill--actual {
  background: rgba(125, 168, 255, 0.16);
  color: #9abaff;
}

.dark .codebase-card__legend-pill--net {
  background: rgba(16, 185, 129, 0.16);
  color: #6ee7b7;
}

.dark .codebase-mode-switcher__button {
  border-color: rgba(103, 122, 160, 0.24);
  background: rgba(28, 36, 51, 0.92);
  color: #9aa8c7;
}

.dark .codebase-mode-switcher__button--active {
  border-color: rgba(125, 168, 255, 0.3);
  background: rgba(125, 168, 255, 0.14);
  color: #9abaff;
}

.dark .codebase-summary-card__lines span,
.dark .codebase-feedback strong {
  color: #f4f7ff;
}

.dark .codebase-feedback--error {
  border-color: rgba(248, 113, 113, 0.3);
}

@media (max-width: 1100px) {
  .codebase-summary-row {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 780px) {
  .codebase-view {
    padding: 16px;
  }

  .codebase-card__header {
    flex-wrap: wrap;
  }

  .codebase-card__legend,
  .codebase-mode-switcher {
    margin-left: 0;
  }
}
</style>
