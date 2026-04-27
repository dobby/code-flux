<script setup lang="ts">
import { computed, watch } from 'vue'
import VChart from 'vue-echarts'
import type { EChartsOption } from 'echarts'
import '../lib/chart'
import {
  Activity,
  FileCode2,
  Hash,
  RefreshCw,
  TrendingUp,
  Users,
} from 'lucide-vue-next'
import {
  formatContributorMetric,
  useContributorsStore,
} from '../stores/contributors'
import { useActivityStore } from '../stores/activity'
import { useAppearanceStore } from '../stores/appearance'

const explorer = useActivityStore()
const contributors = useContributorsStore()
const appearance = useAppearanceStore()

const refreshKey = computed(() => [
  contributors.metric,
  explorer.effectiveDateRange.from,
  explorer.effectiveDateRange.to,
  explorer.selectedRepoIds.join(','),
  explorer.selectedAuthorIds.join(','),
  explorer.selectedLanguages.join(','),
  explorer.selectedCategories.join(','),
  explorer.selectedProductCodes.join(','),
].join('|'))

watch(refreshKey, () => {
  void contributors.refresh()
}, { immediate: true })

const topRows = computed(() => contributors.leaderboardRows.slice(0, 4))

const topContributorNote = computed(() => {
  if (!contributors.topContributor) {
    return 'No contributor activity'
  }
  return `${formatContributorMetric(contributors.metric, contributors.topContributor.value)} ${contributors.metricLabel.toLowerCase()}`
})

const filesChangedTotal = computed(() => {
  return Math.round(contributors.summaryTotals.files_changed_count ?? 0).toLocaleString('en-US')
})

function sampleEvenly<T>(values: T[], count: number): T[] {
  if (count <= 0 || values.length === 0) {
    return []
  }
  if (values.length <= count) {
    return values
  }

  const lastIndex = values.length - 1
  const sampled: T[] = []
  const seen = new Set<number>()

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

function normalizeChartHeights(values: number[], minHeight = 38, maxHeight = 122) {
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

const trendBarSamples = computed(() => {
  if (!contributors.trendDays.length || !contributors.trendSeries.length) {
    return []
  }

  const sampledDays = sampleEvenly(
    contributors.trendDays.map((day, index) => ({ day, index })),
    8,
  )
  const palette = ['#C7D2FE', '#A5B4FC', '#818CF8', '#6366F1', '#8B5CF6', '#4F46E5', '#A5B4FC', '#6366F1']
  const totals = sampledDays.map(({ day, index }, paletteIndex) => ({
    day,
    rawValue: contributors.trendSeries
      .slice(0, 4)
      .reduce((sum, series) => sum + Math.max(0, Number(series.points[index]?.value ?? 0)), 0),
    color: palette[paletteIndex % palette.length],
  }))
  const heights = normalizeChartHeights(totals.map((entry) => entry.rawValue))

  return totals.map((entry, index) => ({
    ...entry,
    value: heights[index] ?? 0,
  }))
})

const trendChartOption = computed<EChartsOption | null>(() => {
  if (!trendBarSamples.value.length) {
    return null
  }

  return {
    backgroundColor: 'transparent',
    animation: appearance.animateCharts,
    animationDuration: appearance.animateCharts ? 260 : 0,
    tooltip: {
      trigger: 'item',
      formatter: (params: unknown) => {
        const row = ((params as { data?: { day?: string; rawValue?: number } | null } | null)?.data ?? null)
        if (!row) {
          return ''
        }
        return `${row.day}<br/>${formatContributorMetric(contributors.metric, row.rawValue ?? 0)}`
      },
    },
    grid: {
      left: '8%',
      right: '52%',
      top: 14,
      bottom: 6,
      containLabel: false,
    },
    xAxis: {
      type: 'category',
      data: trendBarSamples.value.map((entry) => entry.day),
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
        barWidth: 18,
        barCategoryGap: '28%',
        data: trendBarSamples.value.map((entry) => ({
          value: entry.value,
          rawValue: entry.rawValue,
          day: entry.day,
          itemStyle: {
            color: entry.color,
            borderRadius: [5, 5, 0, 0],
          },
        })),
      },
    ],
  }
})

const summaryTiles = computed(() => [
  {
    key: 'contributors',
    icon: Hash,
    label: 'Active Contributors',
    value: contributors.activeContributors.toLocaleString('en-US'),
    note: explorer.timeLabel,
  },
  {
    key: 'top',
    icon: Users,
    label: 'Top Contributor',
    value: contributors.topContributor?.author ?? 'No data',
    note: topContributorNote.value,
  },
  {
    key: 'files',
    icon: FileCode2,
    label: 'Files Changed',
    value: filesChangedTotal.value,
    note: 'Across selected repos',
  },
])
</script>

<template>
  <section class="contributors-view" data-testid="contributors-view">
    <div v-if="contributors.error" class="contributors-feedback contributors-feedback--error" data-testid="contributors-error">
      <strong>Contributors could not load.</strong>
      <p>{{ contributors.error }}</p>
      <button type="button" @click="contributors.refresh()">
        <RefreshCw :size="14" />
        Retry
      </button>
    </div>

    <div
      v-else-if="contributors.isEmpty"
      class="contributors-feedback"
      data-testid="contributors-empty-state"
    >
      <Users :size="18" />
      <strong>No contributor activity for this selection.</strong>
      <p>Try widening the date range or clearing narrow repository and author filters.</p>
    </div>

    <div v-else class="contributors-layout">
      <div class="contributors-layout__top">
        <article class="contributors-card contributors-card--leaderboard" data-testid="contributors-leaderboard">
          <div class="contributors-card__header">
            <TrendingUp :size="16" />
            <span>Leaderboard - {{ contributors.metricLabel }}</span>
          </div>
          <div class="contributors-card__surface contributors-card__surface--list">
            <div
              v-for="row in topRows"
              :key="row.author"
              class="contributors-rank-row"
            >
              <span class="contributors-rank-row__name">{{ row.author }}</span>
              <span class="contributors-rank-row__value">
                {{ formatContributorMetric(contributors.metric, row.value) }}
              </span>
            </div>
            <div v-if="contributors.loading && !contributors.loaded" class="contributors-inline-state">
              <Activity :size="14" />
              <span>Loading contributor rankings…</span>
            </div>
          </div>
        </article>

        <article class="contributors-card contributors-card--trend" data-testid="contributors-trend-chart">
          <div class="contributors-card__header">
            <TrendingUp :size="16" />
            <span>Contribution trend</span>
          </div>
          <div class="contributors-card__surface contributors-card__surface--chart">
            <VChart
              v-if="trendChartOption"
              class="contributors-trend-chart"
              :autoresize="true"
              :option="trendChartOption"
            />
            <div v-else class="contributors-inline-state">
              <Activity :size="14" />
              <span>Trend data will appear once activity exists for this range.</span>
            </div>
          </div>
        </article>
      </div>

      <div class="contributors-layout__summary" data-testid="contributors-summary">
        <article
          v-for="tile in summaryTiles"
          :key="tile.key"
          class="contributors-summary-card"
        >
          <div class="contributors-card__header">
            <component :is="tile.icon" :size="16" />
            <span>{{ tile.label }}</span>
          </div>
          <strong>{{ tile.value }}</strong>
          <small>{{ tile.note }}</small>
        </article>
      </div>
    </div>
  </section>
</template>

<style scoped>
.contributors-view {
  min-height: 100%;
  height: 100%;
  padding: 20px;
  color: #162033;
  box-sizing: border-box;
}

.contributors-layout {
  display: grid;
  min-height: 100%;
  height: 100%;
  grid-template-rows: minmax(0, 1fr) 160px;
  gap: 16px;
}

.contributors-layout__top {
  display: grid;
  grid-template-columns: minmax(0, 1.03fr) minmax(360px, 0.97fr);
  gap: 16px;
  align-items: stretch;
  min-height: 0;
}

.contributors-layout__summary {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px;
}

.contributors-card,
.contributors-summary-card,
.contributors-feedback {
  border: 1px solid rgba(148, 163, 184, 0.28);
  border-radius: 10px;
  background: #ffffff;
  box-shadow: 0 1px 0 rgba(148, 163, 184, 0.08);
}

.contributors-card {
  display: flex;
  flex-direction: column;
  gap: 10px;
  min-height: 0;
  height: 100%;
  padding: 16px;
}

.contributors-card__header {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  color: #61708d;
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 0.02em;
}

.contributors-card__header :deep(svg) {
  color: var(--cf-accent);
}

.contributors-card__surface {
  flex: 1;
  min-height: 0;
  border: 1px solid rgba(148, 163, 184, 0.12);
  border-radius: 8px;
  background: #f8fafc;
}

.contributors-card__surface--list {
  display: grid;
  align-content: start;
  gap: 6px;
  padding: 12px 14px;
}

.contributors-rank-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 30px;
  padding: 0 8px;
}

.contributors-rank-row__name {
  color: #334155;
  font-size: 12px;
  font-weight: 500;
}

.contributors-rank-row:first-child .contributors-rank-row__name,
.contributors-rank-row:first-child .contributors-rank-row__value {
  color: #162033;
  font-weight: 600;
}

.contributors-rank-row__value {
  color: #334155;
  font-size: 12px;
  font-weight: 600;
}

.contributors-card__surface--chart {
  display: flex;
  align-items: flex-end;
  min-height: 0;
  padding: 12px 16px;
}

.contributors-trend-chart {
  width: 100%;
  height: 148px;
}

.contributors-summary-card {
  display: grid;
  gap: 8px;
  min-height: 160px;
  padding: 16px;
}

.contributors-summary-card strong {
  color: #162033;
  font-size: 32px;
  font-weight: 700;
  letter-spacing: -0.04em;
  line-height: 1;
}

.contributors-summary-card small {
  color: #61708d;
  font-size: 11px;
  font-weight: 500;
}

.contributors-inline-state {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  color: #61708d;
  font-size: 11px;
  font-weight: 500;
}

.contributors-feedback {
  display: grid;
  justify-items: start;
  gap: 10px;
  padding: 20px;
}

.contributors-feedback strong {
  font-size: 14px;
}

.contributors-feedback p {
  margin: 0;
  color: #61708d;
  font-size: 12px;
  line-height: 1.5;
}

.contributors-feedback button {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  min-height: 28px;
  border: 1px solid rgba(148, 163, 184, 0.22);
  border-radius: 6px;
  background: #ffffff;
  color: #61708d;
  font-size: 12px;
  font-weight: 600;
  padding: 0 10px;
  cursor: pointer;
}

.contributors-feedback--error {
  border-color: rgba(239, 68, 68, 0.24);
}

.dark .contributors-view {
  color: #d7def2;
}

.dark .contributors-card,
.dark .contributors-summary-card,
.dark .contributors-feedback {
  border-color: rgba(103, 122, 160, 0.22);
  background: rgba(19, 25, 36, 0.96);
  box-shadow: none;
}

.dark .contributors-card__header,
.dark .contributors-summary-card small,
.dark .contributors-inline-state,
.dark .contributors-feedback p {
  color: #9aa8c7;
}

.dark .contributors-card__surface {
  border-color: rgba(103, 122, 160, 0.18);
  background: rgba(28, 36, 51, 0.92);
}

.dark .contributors-rank-row__name,
.dark .contributors-rank-row__value {
  color: #cdd7ee;
}

.dark .contributors-rank-row:first-child .contributors-rank-row__name,
.dark .contributors-rank-row:first-child .contributors-rank-row__value,
.dark .contributors-summary-card strong,
.dark .contributors-feedback strong {
  color: #f4f7ff;
}

.dark .contributors-feedback button {
  border-color: rgba(103, 122, 160, 0.24);
  background: rgba(28, 36, 51, 0.92);
  color: #d7def2;
}

.dark .contributors-feedback--error {
  border-color: rgba(248, 113, 113, 0.3);
}

@media (max-width: 1180px) {
  .contributors-layout {
    height: auto;
    grid-template-rows: auto;
  }

  .contributors-layout__top,
  .contributors-layout__summary {
    grid-template-columns: 1fr;
  }

  .contributors-card,
  .contributors-summary-card {
    min-height: 0;
  }
}

@media (max-width: 780px) {
  .contributors-view {
    padding: 16px;
  }
}
</style>
