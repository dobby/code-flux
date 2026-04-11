<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import VChart from 'vue-echarts'
import type { EChartsOption } from 'echarts'
import { Folder, TrendingUp } from 'lucide-vue-next'
import { useDashboardStore } from '../stores/dashboard'
import { useActivityStore } from '../stores/activity'
import { useAppearanceStore } from '../stores/appearance'
import { useCodebaseStore, type CodebaseTreemapSizeMode } from '../stores/codebase'
import type { CodebaseBreakdownRow, CodebaseTreeNode } from '../types/workspace'

const props = defineProps<{
  repoId?: string
}>()

const route = useRoute()
const router = useRouter()
const dashboard = useDashboardStore()
const explorer = useActivityStore()
const codebase = useCodebaseStore()
const appearance = useAppearanceStore()

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

function sampleEvenly<T>(items: T[], count: number) {
  if (items.length <= count) {
    return items
  }

  return Array.from({ length: count }, (_, index) => {
    const itemIndex = Math.round((index / Math.max(count - 1, 1)) * (items.length - 1))
    return items[itemIndex]
  })
}

function normalizeSeries(values: number[], count: number) {
  if (!values.length) {
    return Array.from({ length: count }, () => 0)
  }

  if (values.length >= count) {
    return sampleEvenly(values, count)
  }

  return Array.from({ length: count }, (_, index) => values[Math.min(index, values.length - 1)] ?? 0)
}

function clamp(value: number, min: number, max: number) {
  return Math.min(Math.max(value, min), max)
}

function formatInteger(value: number) {
  return Math.round(value).toLocaleString('en-US')
}

function shareLabel(row: CodebaseBreakdownRow, total: number) {
  if (total <= 0) {
    return `${row.label} 0%`
  }
  return `${row.label} ${Math.round((row.linesCount / total) * 100)}%`
}

function netColor(netLines: number) {
  if (netLines > 0) {
    return '#0f9f86'
  }
  if (netLines < 0) {
    return '#d97706'
  }
  return '#94a3b8'
}

const growthBars = computed(() => {
  const actualValues = normalizeSeries(
    codebase.growthPoints
      .map((point) => point.snapshotLines)
      .filter((value): value is number => value !== null),
    5,
  )
  const cumulativeValues = normalizeSeries(
    codebase.growthPoints.map((point) => point.cumulativeNetLines),
    5,
  )

  const actualMax = Math.max(...actualValues, 1)
  const cumulativeMin = Math.min(...cumulativeValues, 0)
  const cumulativeShifted = cumulativeValues.map((value) => value - cumulativeMin)
  const cumulativeMax = Math.max(...cumulativeShifted, 1)

  return {
    actual: actualValues.map((value) => ({
      value,
      height: `${Math.max(22, Math.round((value / actualMax) * 100))}%`,
    })),
    cumulative: cumulativeShifted.map((value, index) => ({
      value: cumulativeValues[index] ?? 0,
      height: `${Math.max(22, Math.round((value / cumulativeMax) * 100))}%`,
    })),
  }
})

const structureNodes = computed(() => [...(codebase.treemap?.children ?? [])]
  .filter((node) => modeValue(node, sizeMode.value) > 0)
  .sort((left, right) => modeValue(right, sizeMode.value) - modeValue(left, sizeMode.value))
  .slice(0, 4))

const structureLayout = computed(() => {
  const [primary, secondary, tertiary, quaternary] = structureNodes.value
  const total = structureNodes.value.reduce((sum, node) => sum + modeValue(node, sizeMode.value), 0)

  const leftShare = primary ? clamp(modeValue(primary, sizeMode.value) / Math.max(total, 1), 0.42, 0.62) : 0.5
  const rightTotal = (secondary ? modeValue(secondary, sizeMode.value) : 0)
    + (tertiary ? modeValue(tertiary, sizeMode.value) : 0)
    + (quaternary ? modeValue(quaternary, sizeMode.value) : 0)
  const topShare = secondary ? clamp(modeValue(secondary, sizeMode.value) / Math.max(rightTotal, 1), 0.45, 0.7) : 0.6
  const bottomLeftShare = tertiary && quaternary
    ? clamp(
        modeValue(tertiary, sizeMode.value)
          / Math.max(modeValue(tertiary, sizeMode.value) + modeValue(quaternary, sizeMode.value), 1),
        0.35,
        0.65,
      )
    : 0.5

  return {
    primary,
    secondary,
    tertiary,
    quaternary,
    leftShare,
    topShare,
    bottomLeftShare,
  }
})

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

function structureFill(node: CodebaseTreeNode | undefined, index: number) {
  if (!node) {
    return '#e2e8f0'
  }
  if (sizeMode.value === 'net') {
    return netColor(node.netLines)
  }

  return ['#3759c9', '#0f9f86', '#d97706', '#94a3b8'][index] ?? '#3759c9'
}

const growthChartOption = computed<EChartsOption>(() => {
  const actualValues = growthBars.value.actual.map((entry) => entry.value)
  const cumulativeValuesRaw = codebase.growthPoints.map((point) => point.cumulativeNetLines)
  const cumulativeValues = normalizeSeries(
    cumulativeValuesRaw.map((value) => value - Math.min(...cumulativeValuesRaw, 0)),
    5,
  )
  const categories = ['a1', 'a2', 'a3', 'a4', 'a5', 'gap', 'n1', 'n2', 'n3', 'n4', 'n5']
  const palette = [
    appearance.accentSubtleColor,
    appearance.accentSubtleColor,
    appearance.accentColor,
    appearance.accentHoverColor,
    appearance.accentHoverColor,
    'transparent',
    '#a7f3d0',
    '#6ee7b7',
    '#34d399',
    '#10b981',
    '#059669',
  ]
  const values = [...actualValues, 0, ...cumulativeValues]
  const maxValue = Math.max(...values, 1)

  return {
    backgroundColor: 'transparent',
    animation: appearance.animateCharts,
    animationDuration: appearance.animateCharts ? 250 : 0,
    grid: {
      left: 0,
      right: '74%',
      top: 0,
      bottom: 0,
      containLabel: false,
    },
    tooltip: { show: false },
    xAxis: {
      type: 'category',
      data: categories,
      show: false,
    },
    yAxis: {
      type: 'value',
      show: false,
      max: Math.max(4, Math.round(maxValue * 1.14)),
    },
    series: [
      {
        type: 'bar',
        silent: true,
        barWidth: 16,
        barMaxWidth: 16,
        barMinHeight: 8,
        barCategoryGap: '18%',
        data: values.map((value, index) => ({
          value,
          itemStyle: {
            color: palette[index] ?? '#4f46e5',
            borderRadius: value > 0 ? [6, 6, 0, 0] : 0,
          },
        })),
      },
    ],
  }
})

const structureChartOption = computed<EChartsOption>(() => ({
  backgroundColor: 'transparent',
  animation: appearance.animateCharts,
  animationDuration: appearance.animateCharts ? 250 : 0,
  tooltip: { show: false },
  series: [
    {
      type: 'custom',
      coordinateSystem: 'none',
      silent: true,
      data: [0],
      renderItem: ((_params: unknown, api: { getWidth: () => number; getHeight: () => number }) => {
        const width = api.getWidth()
        const height = api.getHeight()
        const gap = 10
        const padding = 2
        const radius = 10
        const innerWidth = Math.max(width - padding * 2, 0)
        const innerHeight = Math.max(height - padding * 2, 0)
        const rightWidth = Math.max(innerWidth - innerWidth * structureLayout.value.leftShare - gap, 0)
        const leftWidth = Math.max(innerWidth - rightWidth - gap, 0)
        const topHeight = Math.max(innerHeight * structureLayout.value.topShare - gap / 2, 0)
        const bottomHeight = Math.max(innerHeight - topHeight - gap, 0)
        const bottomLeftWidth = Math.max(rightWidth * structureLayout.value.bottomLeftShare - gap / 2, 0)
        const bottomRightWidth = Math.max(rightWidth - bottomLeftWidth - gap, 0)

        const children: Array<Record<string, unknown>> = []

        const addTile = (
          node: CodebaseTreeNode | undefined,
          index: number,
          x: number,
          y: number,
          tileWidth: number,
          tileHeight: number,
        ) => {
          if (!node || tileWidth <= 0 || tileHeight <= 0) {
            return
          }

          children.push({
            type: 'rect',
            shape: {
              x,
              y,
              width: tileWidth,
              height: tileHeight,
              r: radius,
            },
            style: {
              fill: structureFill(node, index),
              stroke: '#ffffff',
              lineWidth: 6,
            },
          })
          children.push({
            type: 'text',
            style: {
              x: x + 14,
              y: y + 20,
              text: node.label,
              fill: '#ffffff',
              fontSize: 12,
              fontWeight: 600,
              width: Math.max(tileWidth - 28, 0),
              overflow: 'truncate',
            },
          })
        }

        addTile(structureLayout.value.primary, 0, padding, padding, leftWidth, innerHeight)
        addTile(structureLayout.value.secondary, 1, padding + leftWidth + gap, padding, rightWidth, topHeight)
        addTile(structureLayout.value.tertiary, 2, padding + leftWidth + gap, padding + topHeight + gap, bottomLeftWidth, bottomHeight)
        addTile(structureLayout.value.quaternary, 3, padding + leftWidth + gap + bottomLeftWidth + gap, padding + topHeight + gap, bottomRightWidth, bottomHeight)

        return {
          type: 'group',
          children,
        }
      }) as never,
    },
  ],
}))
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

        <VChart
          class="codebase-growth-chart"
          data-testid="codebase-growth-chart"
          :autoresize="true"
          :option="growthChartOption"
        />
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

        <div v-if="!structureNodes.length" class="codebase-feedback" data-testid="codebase-treemap-empty-no-structure">
          <strong>No current inventory is available for this repository yet.</strong>
          <p>Build snapshots to populate the structure view.</p>
        </div>

        <VChart
          v-else
          class="codebase-structure-chart"
          data-testid="codebase-treemap"
          :autoresize="true"
          :option="structureChartOption"
        />
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
}

.codebase-layout {
  display: grid;
  gap: 14px;
}

.codebase-card,
.codebase-summary-card,
.codebase-feedback {
  border: 1px solid rgba(148, 163, 184, 0.22);
  border-radius: 10px;
  background: #ffffff;
  box-shadow: 0 1px 0 rgba(148, 163, 184, 0.08);
}

.codebase-card {
  display: grid;
  gap: 10px;
  padding: 14px;
}

.codebase-card--growth {
  min-height: 178px;
}

.codebase-card--structure {
  min-height: 248px;
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
  height: 20px;
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
  height: 104px;
  padding: 10px 14px;
  border-radius: 8px;
  background: #f8fafc;
}

.codebase-mode-switcher {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  margin-left: auto;
}

.codebase-mode-switcher__button {
  min-height: 20px;
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
  height: 194px;
  border-radius: 8px;
  background: #f8fafc;
}

.codebase-summary-row {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 14px;
}

.codebase-summary-card {
  display: grid;
  gap: 6px;
  min-height: 110px;
  padding: 14px 16px;
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
