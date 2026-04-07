<script setup lang="ts">
import { computed, watch } from 'vue'
import VChart from 'vue-echarts'
import type { EChartsOption } from 'echarts'
import { Activity, RefreshCw, Users } from 'lucide-vue-next'
import { useTheme } from '../composables/useTheme'
import '../lib/chart'
import {
  CONTRIBUTOR_METRIC_OPTIONS,
  type ContributorMetric,
  formatContributorMetric,
  useContributorsStore,
} from '../stores/contributors'
import { useExplorerStore } from '../stores/explorer'
import { getSeriesColor } from '../lib/chart'

const explorer = useExplorerStore()
const contributors = useContributorsStore()
const { isDark } = useTheme()

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

const trendChartOption = computed<EChartsOption | null>(() => {
  if (!contributors.trendSeries.length || !contributors.trendDays.length) {
    return null
  }

  return {
    backgroundColor: 'transparent',
    color: contributors.trendSeries.map((_, index) => getSeriesColor(index)),
    animationDuration: 260,
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'line' },
      formatter: (params: unknown) => {
        const rows = (Array.isArray(params) ? params : []).filter(Boolean) as Array<{
          axisValueLabel?: string
          color?: string
          seriesName?: string
          value?: number | string | Array<number | string>
        }>
        if (!rows.length) {
          return ''
        }

        const values = rows
          .map((row) => {
            const raw = Array.isArray(row.value) ? row.value.at(-1) : row.value
            const numeric = Number(raw ?? 0)
            return {
              color: typeof row.color === 'string' ? row.color : '#6366f1',
              label: row.seriesName ?? 'Series',
              value: Number.isFinite(numeric) ? numeric : 0,
            }
          })
          .sort((left, right) => right.value - left.value)

        return [
          rows[0]?.axisValueLabel ?? '',
          ...values.map((entry) =>
            `<span style="display:inline-block;width:8px;height:8px;border-radius:999px;background:${entry.color};margin-right:6px;"></span>${entry.label}: ${formatContributorMetric(contributors.metric, entry.value)}`,
          ),
        ].join('<br/>')
      },
    },
    legend: {
      type: 'scroll',
      bottom: 0,
      icon: 'circle',
      itemWidth: 10,
      itemHeight: 10,
      textStyle: {
        color: isDark.value ? '#a7b2cd' : '#61708d',
        fontSize: 11,
      },
    },
    grid: {
      left: 6,
      right: 6,
      top: 12,
      bottom: 42,
      containLabel: true,
    },
    xAxis: {
      type: 'category',
      data: contributors.trendDays,
      axisLine: { show: false },
      axisTick: { show: false },
      axisLabel: { show: false },
    },
    yAxis: {
      type: 'value',
      minInterval: 1,
      axisLine: { show: false },
      axisTick: { show: false },
      axisLabel: {
        color: isDark.value ? '#8e9abb' : '#7b8aa5',
        fontSize: 11,
      },
      splitLine: {
        lineStyle: {
          color: isDark.value ? 'rgba(120, 136, 168, 0.18)' : 'rgba(148, 163, 184, 0.15)',
        },
      },
    },
    series: contributors.trendSeries.map((series, index) => ({
      name: series.author,
      type: 'line',
      smooth: true,
      showSymbol: false,
      lineStyle: { width: 2.5, color: getSeriesColor(index) },
      areaStyle: index === 0 ? { opacity: 0.08, color: getSeriesColor(index) } : undefined,
      data: series.points.map((point) => point.value),
    })),
  }
})

const pageIntro = computed(() => {
  if (contributors.topContributor) {
    return `Ranking currently reflects ${contributors.metricLabel} across ${explorer.timeLabel}. ${contributors.topContributor.author} leads this slice.`
  }
  return `Ranking currently reflects ${contributors.metricLabel} across ${explorer.timeLabel}. Widen the range or adjust filters to surface contributor movement.`
})

function cardValue(label: string, metric: ContributorMetric | null, value: number) {
  if (label === 'Active Contributors') {
    return Math.round(value).toLocaleString('en-US')
  }
  return formatContributorMetric(metric ?? contributors.metric, value)
}
</script>

<template>
  <section class="contributors-view" data-testid="contributors-view">
    <div class="contributors-shell">
      <section class="contributors-intro-card">
        <div class="contributors-intro-card__header">
          <div class="contributors-intro-card__copy">
            <span class="contributors-eyebrow">Contributor ranking</span>
            <h1>Contributors overview</h1>
            <p>
              Contributors stays global across repositories while following the shared header filters.
              {{ pageIntro }}
            </p>
          </div>
          <div class="contributors-intro-card__leader-card">
            <div class="contributors-intro-card__leader-label">
              <span>Current leader</span>
            </div>
            <template v-if="contributors.topContributor">
              <strong>{{ contributors.topContributor.author }}</strong>
              <small>{{ formatContributorMetric(contributors.metric, contributors.topContributor.value) }}</small>
              <div class="contributors-intro-card__leader-meta">
                <div>
                  <span>Metric</span>
                  <strong>{{ contributors.metricLabel }}</strong>
                </div>
                <div>
                  <span>Window</span>
                  <strong>{{ explorer.timeLabel }}</strong>
                </div>
              </div>
            </template>
            <template v-else>
              <strong>No contributor activity</strong>
              <small>Widen the range or adjust filters.</small>
            </template>
          </div>
        </div>

        <div class="contributors-intro-card__controls">
          <div class="contributors-intro-card__controls-copy">
            <span class="contributors-eyebrow">Metric focus</span>
            <p>Switch the ranking model without changing the shared repository and header scope.</p>
          </div>
          <div class="contributors-metric-switcher" data-testid="contributors-metric-switcher">
            <button
              v-for="option in CONTRIBUTOR_METRIC_OPTIONS"
              :key="option.value"
              class="contributors-metric-switcher__button"
              :class="{ 'contributors-metric-switcher__button--active': contributors.metric === option.value }"
              :data-testid="`contributors-metric-${option.value}`"
              type="button"
              @click="contributors.setMetric(option.value)"
            >
              <span>{{ option.label }}</span>
            </button>
          </div>
        </div>
      </section>

      <div class="contributors-summary" data-testid="contributors-summary">
        <article
          v-for="card in contributors.summaryCards"
          :key="card.key"
          class="contributors-summary__card"
        >
          <span>{{ card.label }}</span>
          <strong>{{ cardValue(card.label, card.metric, card.value) }}</strong>
          <small>
            {{
              card.label === 'Active Contributors'
                ? 'Distinct authors after the active filters are applied.'
                : `${explorer.timeLabel} across the selected scope.`
            }}
          </small>
        </article>
      </div>

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
        <p>Try widening the date range, clearing repository filters, or removing narrow author and language filters.</p>
      </div>

      <div v-else class="contributors-layout">
        <section class="contributors-panel contributors-panel--table">
          <div class="contributors-panel__header">
            <div>
              <span class="contributors-eyebrow">Leaderboard</span>
              <h2>Top contributors by {{ contributors.metricLabel.toLowerCase() }}</h2>
            </div>
            <span class="contributors-panel__meta">
              {{ contributors.activeContributors.toLocaleString('en-US') }} ranked contributors
            </span>
          </div>

          <div v-if="contributors.loading && !contributors.loaded" class="contributors-feedback contributors-feedback--compact">
            <Activity :size="16" />
            <strong>Loading contributors…</strong>
          </div>

          <div v-else class="contributors-table-wrap">
            <table class="contributors-table" data-testid="contributors-leaderboard">
              <thead>
                <tr>
                  <th>Rank</th>
                  <th>Contributor</th>
                  <th>{{ contributors.metricShortLabel }}</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="row in contributors.leaderboardRows.slice(0, 12)" :key="row.author">
                  <td class="contributors-table__rank">{{ row.rank }}</td>
                  <td>
                    <div class="contributors-table__identity">
                      <strong>{{ row.author }}</strong>
                      <span class="contributors-table__bar-track">
                        <span
                          class="contributors-table__bar-fill"
                          :style="{ width: `${Math.max(row.intensity * 100, row.intensity > 0 ? 8 : 0)}%` }"
                        />
                      </span>
                    </div>
                  </td>
                  <td class="contributors-table__value">
                    {{ formatContributorMetric(contributors.metric, row.value) }}
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
        </section>

        <section class="contributors-rail">
          <article class="contributors-panel contributors-panel--trend">
            <div class="contributors-panel__header">
              <div>
                <span class="contributors-eyebrow">Trend</span>
                <h2>How the current leaders moved over time</h2>
              </div>
            </div>
            <div v-if="trendChartOption" class="contributors-trend" data-testid="contributors-trend-chart">
              <VChart :autoresize="true" :option="trendChartOption" />
            </div>
            <div v-else class="contributors-feedback contributors-feedback--compact">
              <Activity :size="16" />
              <strong>Trend data will appear once activity exists for this range.</strong>
            </div>
          </article>

          <article class="contributors-panel contributors-panel--insight">
            <div class="contributors-panel__header">
              <div>
                <span class="contributors-eyebrow">Reading</span>
                <h2>Interpretation</h2>
              </div>
            </div>
            <ul class="contributors-insight-list">
              <li>Ranking responds to the shared repository, author, language, category, and product filters.</li>
              <li>Metric changes do not change page scope; they only change ordering and trend comparisons.</li>
              <li>The trend panel follows the current leaders in the selected metric instead of mixing metrics.</li>
            </ul>
          </article>
        </section>
      </div>
    </div>
  </section>
</template>

<style scoped>
.contributors-view {
  padding: 74px 20px 20px;
  min-height: 100%;
  color: var(--cf-text);
}

.contributors-shell {
  display: grid;
  gap: 16px;
}

.contributors-intro-card,
.contributors-summary__card,
.contributors-panel,
.contributors-feedback {
  border: 1px solid var(--cf-border);
  border-radius: 18px;
  background:
    linear-gradient(180deg, color-mix(in srgb, var(--cf-surface) 94%, white), var(--cf-surface));
  box-shadow: var(--cf-shadow);
}

.contributors-intro-card {
  padding: 20px;
  background:
    radial-gradient(circle at top left, color-mix(in srgb, var(--cf-accent) 12%, transparent), transparent 42%),
    linear-gradient(180deg, color-mix(in srgb, var(--cf-surface) 94%, white), var(--cf-surface));
}

.contributors-intro-card__header {
  display: grid;
  grid-template-columns: minmax(0, 1.4fr) minmax(240px, 320px);
  align-items: flex-start;
  gap: 18px;
  margin-bottom: 18px;
}

.contributors-intro-card__copy {
  min-width: 0;
}

.contributors-eyebrow {
  display: inline-flex;
  align-items: center;
  font-size: 0.72rem;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: var(--cf-text-tertiary);
}

.contributors-intro-card h1,
.contributors-panel__header h2 {
  margin: 8px 0 8px;
  line-height: 1.08;
}

.contributors-intro-card h1 {
  font-size: 1.4rem;
}

.contributors-panel__header h2 {
  font-size: 1.05rem;
}

.contributors-intro-card p,
.contributors-intro-card__leader-card small,
.contributors-summary__card small,
.contributors-panel__meta,
.contributors-insight-list,
.contributors-intro-card__controls-copy p,
.contributors-feedback p {
  color: var(--cf-text-secondary);
}

.contributors-intro-card p {
  margin: 0;
  max-width: 70ch;
  line-height: 1.55;
}

.contributors-intro-card__leader-card {
  display: grid;
  gap: 10px;
  min-width: 0;
  padding: 16px 18px;
  border: 1px solid color-mix(in srgb, var(--cf-border) 78%, transparent);
  border-radius: 16px;
  background:
    linear-gradient(180deg, color-mix(in srgb, var(--cf-surface) 96%, white), color-mix(in srgb, var(--cf-surface) 92%, transparent));
  box-shadow: inset 0 1px 0 color-mix(in srgb, white 42%, transparent);
}

.contributors-intro-card__leader-label span,
.contributors-intro-card__leader-meta span,
.contributors-summary__card span {
  font-size: 0.72rem;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.contributors-intro-card__leader-card strong {
  display: block;
  font-size: 1.35rem;
  line-height: 1.05;
}

.contributors-intro-card__leader-card small {
  display: block;
  font-size: 0.9rem;
}

.contributors-intro-card__leader-meta {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
  padding-top: 10px;
  border-top: 1px solid color-mix(in srgb, var(--cf-border) 72%, transparent);
}

.contributors-intro-card__leader-meta strong {
  font-size: 0.95rem;
  margin-top: 4px;
}

.contributors-intro-card__controls {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 16px;
  padding-top: 16px;
  border-top: 1px solid color-mix(in srgb, var(--cf-border) 78%, transparent);
}

.contributors-intro-card__controls-copy {
  display: grid;
  gap: 6px;
  max-width: 30rem;
}

.contributors-intro-card__controls-copy p {
  margin: 0;
  line-height: 1.45;
}

.contributors-metric-switcher {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.contributors-metric-switcher__button,
.contributors-feedback button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 10px 14px;
  border: 1px solid var(--cf-border);
  border-radius: 999px;
  background: color-mix(in srgb, var(--cf-surface) 94%, transparent);
  color: var(--cf-text-secondary);
  font-size: 0.84rem;
  font-weight: 600;
  cursor: pointer;
  transition:
    border-color 140ms ease,
    color 140ms ease,
    background 140ms ease;
}

.contributors-metric-switcher__button:hover,
.contributors-feedback button:hover {
  color: var(--cf-text);
  border-color: color-mix(in srgb, var(--cf-accent) 28%, var(--cf-border));
}

.contributors-metric-switcher__button--active {
  border-color: color-mix(in srgb, var(--cf-accent) 60%, var(--cf-border));
  background: color-mix(in srgb, var(--cf-accent-subtle) 80%, transparent);
  color: color-mix(in srgb, var(--cf-accent) 82%, black);
}

.contributors-summary {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px;
}

.contributors-summary__card {
  display: grid;
  gap: 8px;
  padding: 18px;
}

.contributors-summary__card strong {
  font-size: 1.3rem;
  line-height: 1;
}

.contributors-layout {
  display: grid;
  grid-template-columns: minmax(0, 1.45fr) minmax(320px, 0.95fr);
  gap: 16px;
  min-height: 0;
}

.contributors-panel {
  padding: 18px;
}

.contributors-panel__header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 16px;
}

.contributors-table-wrap {
  overflow: auto;
}

.contributors-table {
  width: 100%;
  border-collapse: collapse;
}

.contributors-table th,
.contributors-table td {
  padding: 12px 0;
  border-bottom: 1px solid color-mix(in srgb, var(--cf-border) 82%, transparent);
  text-align: left;
}

.contributors-table th {
  font-size: 0.72rem;
  font-weight: 700;
  color: var(--cf-text-tertiary);
  text-transform: uppercase;
  letter-spacing: 0.08em;
}

.contributors-table__rank {
  width: 52px;
  color: var(--cf-text-tertiary);
  font-weight: 700;
}

.contributors-table__value {
  width: 148px;
  text-align: right;
  font-variant-numeric: tabular-nums;
  font-weight: 700;
}

.contributors-table__identity {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.contributors-table__identity strong {
  font-size: 0.95rem;
}

.contributors-table__bar-track {
  position: relative;
  display: block;
  width: 100%;
  height: 8px;
  border-radius: 999px;
  overflow: hidden;
  background: color-mix(in srgb, var(--cf-border) 76%, transparent);
}

.contributors-table__bar-fill {
  position: absolute;
  inset: 0 auto 0 0;
  border-radius: 999px;
  background: linear-gradient(90deg, #2563eb, #0f766e);
}

.contributors-rail {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.contributors-trend {
  height: 270px;
}

.contributors-insight-list {
  margin: 0;
  padding-left: 18px;
  line-height: 1.55;
}

.contributors-insight-list li + li {
  margin-top: 10px;
}

.contributors-feedback {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 10px;
  padding: 20px;
}

.contributors-feedback--compact {
  min-height: 180px;
  justify-content: center;
}

.contributors-feedback strong {
  font-size: 1rem;
}

.contributors-feedback p {
  margin: 0;
  line-height: 1.5;
}

.contributors-feedback--error {
  border-color: color-mix(in srgb, var(--cf-danger) 32%, var(--cf-border));
}

@media (max-width: 1180px) {
  .contributors-summary,
  .contributors-layout {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 860px) {
  .contributors-intro-card__header {
    grid-template-columns: 1fr;
  }

  .contributors-intro-card__controls {
    flex-direction: column;
    align-items: stretch;
  }
}

@media (max-width: 780px) {
  .contributors-view {
    padding: 66px 14px 16px;
  }

  .contributors-summary {
    grid-template-columns: 1fr 1fr;
  }

  .contributors-table__value {
    width: 108px;
  }
}

@media (max-width: 560px) {
  .contributors-summary {
    grid-template-columns: 1fr;
  }

  .contributors-metric-switcher__button {
    width: 100%;
  }
}
</style>
