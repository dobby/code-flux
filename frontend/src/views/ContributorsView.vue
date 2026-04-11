<script setup lang="ts">
import { computed, watch } from 'vue'
import VChart from 'vue-echarts'
import type { EChartsOption } from 'echarts'
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

function sampleEvenly<T>(items: T[], count: number) {
  if (items.length <= count) {
    return items
  }

  return Array.from({ length: count }, (_, index) => {
    const itemIndex = Math.round((index / Math.max(count - 1, 1)) * (items.length - 1))
    return items[itemIndex]
  })
}

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

const trendBarValues = computed(() => {
  const sampledDays = sampleEvenly(contributors.trendDays, Math.min(8, contributors.trendDays.length))
  if (!sampledDays.length) {
    return []
  }

  const totals = sampledDays.map((day) => {
    let value = 0
    for (const series of contributors.trendSeries.slice(0, 4)) {
      const point = series.points.find((entry) => entry.day === day)
      value += Math.max(0, point?.value ?? 0)
    }
    return value
  })

  return sampledDays.map((day, index) => ({
    day,
    value: totals[index],
    color: index % 2 === 1 ? appearance.accentColor : '#cbd5e1',
  }))
})

const trendChartOption = computed<EChartsOption>(() => {
  const values = trendBarValues.value
  const maxValue = Math.max(...values.map((entry) => entry.value), 1)

  return {
    backgroundColor: 'transparent',
    animation: appearance.animateCharts,
    animationDuration: appearance.animateCharts ? 250 : 0,
    grid: {
      left: '57%',
      right: 0,
      top: 2,
      bottom: 0,
      containLabel: false,
    },
    tooltip: { show: false },
    xAxis: {
      type: 'category',
      data: values.map((entry) => entry.day),
      show: false,
    },
    yAxis: {
      type: 'value',
      show: false,
      max: Math.max(4, Math.round(maxValue * 1.12)),
    },
    series: [
      {
        type: 'bar',
        silent: true,
        barWidth: 28,
        barMaxWidth: 28,
        barMinHeight: 8,
        barCategoryGap: '28%',
        data: values.map((entry) => ({
          value: entry.value,
          itemStyle: {
            color: entry.color,
            borderRadius: [6, 6, 0, 0],
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
              v-if="trendBarValues.length"
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
  padding: 20px;
  color: #162033;
}

.contributors-layout {
  display: grid;
  gap: 14px;
}

.contributors-layout__top {
  display: grid;
  grid-template-columns: minmax(0, 1.03fr) minmax(360px, 0.97fr);
  gap: 14px;
  align-items: stretch;
}

.contributors-layout__summary {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 14px;
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
  min-height: 312px;
  padding: 14px;
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
  padding: 10px 12px;
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
  min-height: 214px;
  padding: 10px 14px 12px;
}

.contributors-trend-chart {
  width: 100%;
  height: 100%;
}

.contributors-summary-card {
  display: grid;
  gap: 6px;
  min-height: 108px;
  padding: 14px 16px;
}

.contributors-summary-card strong {
  color: #162033;
  font-size: 27px;
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

@media (max-width: 1180px) {
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
