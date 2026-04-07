<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  GitCommitHorizontal,
  ExternalLink,
  Plus,
  Tag,
  File,
} from 'lucide-vue-next'
import { useDashboardStore } from '../stores/dashboard'
import { useExplorerStore } from '../stores/explorer'
import { useTheme } from '../composables/useTheme'
import ExplorerAnnotationModal from '../components/ExplorerAnnotationModal.vue'
import VChart from 'vue-echarts'
import type { EChartsOption } from 'echarts'
import { getSeriesColor } from '../lib/chart'

const route = useRoute()
const router = useRouter()
const dashboard = useDashboardStore()
const explorer = useExplorerStore()
const { isDark } = useTheme()

const metricNoun = computed(() => {
  switch (explorer.metric) {
    case 'lines_added':
      return 'lines added'
    case 'lines_removed':
      return 'lines removed'
    case 'net_lines':
    case 'cumulative_net':
      return 'net lines'
    case 'file_count':
      return 'files'
    default:
      return 'commits'
  }
})

const formattedSelectedDate = computed(() => {
  if (!explorer.selectedDate) return ''
  const date = new Date(explorer.selectedDate + 'T00:00:00')
  return date.toLocaleDateString('en-US', { month: 'short', day: 'numeric' })
})

const selectedDayCommitCount = computed(
  () => explorer.dayDetail?.summary.commitsCount ?? explorer.displayCommits.length,
)

const selectedDayRepoCount = computed(() => {
  const repos = new Set(explorer.displayCommits.map((c) => c.repoId))
  return repos.size
})

const chartSeries = computed(() => {
  const baseSeries = explorer.analytics?.series ?? []
  if (explorer.metric !== 'cumulative_net') {
    return baseSeries
  }

  return baseSeries.map((entry) => {
    let runningTotal = 0
    return {
      ...entry,
      points: entry.points.map((point) => {
        runningTotal += point.value
        return {
          ...point,
          value: runningTotal,
        }
      }),
    }
  })
})

const chartDays = computed(() => {
  const days = new Set<string>()
  for (const series of chartSeries.value) {
    for (const point of series.points) {
      days.add(point.day)
    }
  }
  return Array.from(days).sort()
})

const chartLegendVisible = computed(
  () => explorer.showLegend && explorer.groupBy !== 'none' && chartSeries.value.length > 1,
)

const chartCanvasStyle = computed(() => {
  if (explorer.chartStyle !== 'bar') {
    return { width: '100%' }
  }

  const pointCount = Math.max(chartDays.value.length, 1)
  const seriesCount = Math.max(chartSeries.value.length, 1)
  const dayWidth = explorer.groupBy === 'none'
    ? 56
    : Math.max(68, Math.min(160, seriesCount * 18 + 10))
  const targetWidth = Math.max(360, pointCount * dayWidth + (chartLegendVisible.value ? 84 : 0))

  return {
    width: `max(100%, ${targetWidth}px)`,
  }
})

const chartOption = computed<EChartsOption>(() => {
  const series = chartSeries.value.map((entry, index) => {
    const color = explorer.groupBy === 'none'
      ? '#6366f1'
      : getSeriesColor(index)
    const seriesType: 'bar' | 'line' = explorer.chartStyle === 'bar' ? 'bar' : 'line'
    const pointMap = new Map(entry.points.map((point) => [point.day, point.value]))
    const values = chartDays.value.map((day) => pointMap.get(day) ?? 0)

    return {
      id: entry.key,
      name: entry.label,
      type: seriesType,
      smooth: explorer.chartStyle !== 'bar',
      showSymbol: explorer.chartStyle !== 'bar',
      symbolSize: explorer.chartStyle === 'bar' ? 0 : 6,
      emphasis: { focus: 'series' as const },
      lineStyle: explorer.chartStyle === 'bar' ? undefined : { width: 2.5, color },
      areaStyle: explorer.chartStyle === 'area' ? { opacity: 0.12, color } : undefined,
      itemStyle: explorer.chartStyle === 'bar'
        ? {
            color: explorer.groupBy === 'none'
              ? undefined
              : color,
            borderRadius: [3, 3, 0, 0],
          }
        : { color },
      data: explorer.chartStyle === 'bar'
        ? values.map((value, valueIndex) => ({
            value,
            itemStyle: explorer.groupBy === 'none'
              ? {
                  color: chartDays.value[valueIndex] === explorer.selectedDate
                    ? '#6366f1'
                    : 'rgba(148, 163, 184, 0.35)',
                  borderRadius: [3, 3, 0, 0],
                }
              : {
                  color,
                  borderRadius: [3, 3, 0, 0],
                },
          }))
        : values,
      barMaxWidth: explorer.groupBy === 'none' ? 54 : 18,
      barMinWidth: explorer.groupBy === 'none' ? 30 : 10,
      barCategoryGap: explorer.groupBy === 'none' ? '2%' : '8%',
      barGap: explorer.groupBy === 'none' ? '0%' : '12%',
      markLine: index === 0 && explorer.selectedDate
        ? {
            silent: true,
            symbol: 'none',
            animation: false,
            label: { show: false },
            lineStyle: {
              color: 'rgba(99, 102, 241, 0.22)',
              width: 1,
            },
            data: [{ xAxis: explorer.selectedDate }],
          }
        : undefined,
    }
  }) as NonNullable<EChartsOption['series']>

  return {
    backgroundColor: 'transparent',
    animation: true,
    color: chartSeries.value.map((_, index) => getSeriesColor(index)),
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: explorer.chartStyle === 'bar' ? 'shadow' : 'line' },
      formatter: (params: unknown) => formatChartTooltip(params),
    },
    legend: chartLegendVisible.value
      ? {
          type: 'scroll',
          orient: 'vertical',
          top: 8,
          right: 0,
          bottom: 0,
          itemWidth: 10,
          itemHeight: 10,
          textStyle: {
            color: isDark.value ? '#a7b2cd' : '#61708d',
            fontSize: 11,
          },
        }
      : { show: false },
    grid: {
      left: 0,
      right: chartLegendVisible.value ? 76 : 0,
      top: 8,
      bottom: 8,
      containLabel: true,
    },
    xAxis: {
      type: 'category',
      data: chartDays.value,
      axisLabel: { show: false },
      axisLine: { show: false },
      axisTick: { show: false },
      splitLine: { show: false },
    },
    yAxis: {
      type: 'value',
      minInterval: 1,
      splitNumber: 4,
      axisLabel: {
        show: true,
        color: isDark.value ? '#8e9abb' : '#7b8aa5',
        fontSize: 11,
        margin: 10,
        formatter: (value: number) => `${Math.round(value)}`,
      },
      axisLine: { show: false },
      axisTick: { show: false },
      splitLine: {
        show: true,
        lineStyle: {
          color: isDark.value ? 'rgba(120, 136, 168, 0.22)' : 'rgba(148, 163, 184, 0.16)',
        },
      },
    },
    series,
  }
})

function formatChartTooltip(params: unknown) {
  const points = (Array.isArray(params) ? params : []).filter(Boolean) as Array<{
    axisValueLabel?: string
    color?: string
    seriesName?: string
    value?: number | string | Array<number | string>
  }>
  if (!points.length) {
    return ''
  }

  const rows = points
    .map((point) => ({
      label: point.seriesName ?? explorer.metricLabel,
      color: typeof point.color === 'string' ? point.color : '#6366f1',
      value: extractTooltipValue(point.value),
    }))
    .sort((left, right) => right.value - left.value)

  const total = rows.reduce((sum, row) => sum + row.value, 0)
  const lines = rows
    .filter((row) => row.value > 0 || rows.length === 1)
    .map((row) =>
      `<span style="display:inline-block;width:8px;height:8px;border-radius:999px;background:${row.color};margin-right:6px;"></span>${row.label}: ${row.value} ${metricNoun.value}`,
    )

  if (rows.length > 1) {
    lines.push(`<strong>Total: ${total} ${metricNoun.value}</strong>`)
  }

  return `${points[0]?.axisValueLabel ?? ''}<br/>${lines.join('<br/>')}`
}

function extractTooltipValue(value: number | string | Array<number | string> | undefined) {
  const raw = Array.isArray(value) ? value.at(-1) : value
  const numeric = Number(raw ?? 0)
  return Number.isFinite(numeric) ? Math.round(numeric) : 0
}

function handleExplorerChartClick(event: {
  name?: string
  seriesId?: string
  seriesName?: string
}) {
  const day = typeof event.name === 'string' ? event.name : ''
  if (!day) {
    return
  }

  const seriesKey = explorer.groupBy === 'none'
    ? null
    : resolveSeriesKey(event)

  explorer.selectChartPoint(day, seriesKey)
  explorer.syncQueryToUrl(router)
}

function resolveSeriesKey(event: { seriesId?: string; seriesName?: string }) {
  if (typeof event.seriesId === 'string' && event.seriesId) {
    return event.seriesId
  }

  if (typeof event.seriesName === 'string' && event.seriesName) {
    return chartSeries.value.find((series) => series.label === event.seriesName)?.key ?? null
  }

  return null
}

const authorInitial = computed(() => (explorer.selectedCommit?.author ?? '?').slice(0, 1).toUpperCase())

function formatDetailTimestamp(iso: string): string {
  try {
    const d = new Date(iso)
    const date = d.toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric' })
    const time = d.toLocaleTimeString('en-US', { hour: '2-digit', minute: '2-digit', hour12: false })
    return `${date} at ${time}`
  } catch {
    return iso
  }
}

const visibleFiles = computed(() => explorer.displayCommitFiles)
const totalCommitFilesCount = computed(() => explorer.selectedCommitDetail?.files.length ?? 0)
const detailFilesCount = computed(() => totalCommitFilesCount.value)
const extraFileCount = computed(() => Math.max(0, totalCommitFilesCount.value - visibleFiles.value.length))
const supportsNativeFileOpen = computed(
  () => typeof window !== 'undefined' && typeof window.codeFluxSecure?.openCommitFile === 'function',
)
const openingFilePath = ref<string | null>(null)
const fileOpenError = ref<string | null>(null)

function openSelectedCommit() {
  const c = explorer.selectedCommit
  if (!c) return
  void router.push({
    name: 'activity-commit',
    params: { repoId: c.repoId, commitSha: c.commitSha },
    query: explorer.buildQuery(),
  })
}

async function openChangedFile(filePath: string) {
  const commit = explorer.selectedCommit
  if (!commit || !window.codeFluxSecure?.openCommitFile) {
    return
  }

  openingFilePath.value = filePath
  fileOpenError.value = null

  try {
    const response = await window.codeFluxSecure.openCommitFile({
      repoId: commit.repoId,
      commitSha: commit.commitSha,
      filePath,
    })
    if (!response.available || !response.opened) {
      fileOpenError.value = response.reason || `Unable to open ${filePath}.`
    }
  } catch (error) {
    fileOpenError.value = error instanceof Error ? error.message : `Unable to open ${filePath}.`
  } finally {
    if (openingFilePath.value === filePath) {
      openingFilePath.value = null
    }
  }
}

watch(
  () => [
    explorer.rangePreset,
    explorer.customDateRange.from,
    explorer.customDateRange.to,
    explorer.selectedRepoIds.join(','),
    explorer.selectedAuthorIds.join(','),
    explorer.selectedLanguages.join(','),
    explorer.selectedCategories.join(','),
    explorer.selectedProductCodes.join(','),
    explorer.metric,
    explorer.groupBy,
  ] as const,
  () => {
    void explorer.refreshExplorer().then(() => explorer.syncQueryToUrl(router))
  },
)

watch(
  () => explorer.chartStyle,
  () => {
    explorer.syncQueryToUrl(router)
  },
)

watch(
  () => [explorer.selectedDate, explorer.selectedSeriesKey] as const,
  ([selectedDate], [previousDate]) => {
    const requests: Array<Promise<unknown>> = [explorer.loadDay()]
    if (selectedDate !== previousDate) {
      requests.push(explorer.loadAnnotationsForDay())
    }
    void Promise.all(requests).then(() => explorer.syncQueryToUrl(router))
  },
)

watch(
  () => explorer.selectedCommit?.commitSha,
  () => {
    fileOpenError.value = null
    openingFilePath.value = null
    void explorer.loadSelectedCommitDetail()
  },
)

watch(
  () => route.query,
  (query) => {
    explorer.initFromQuery(query)
  },
)

onMounted(async () => {
  if (!dashboard.initialized) {
    await dashboard.initialize()
  }
  if (!explorer.initialized) {
    explorer.initFromQuery(route.query)
  }
  await explorer.refreshExplorer()
})
</script>

<template>
  <section class="explorer-view">
    <section class="explorer-chart">
      <div class="explorer-chart__header">
        <div class="explorer-chart__header-main">
          <span class="explorer-chart__title">{{ explorer.metricLabel }}</span>
          <span v-if="explorer.selectedDate" class="explorer-chart__badge">
            {{ formattedSelectedDate }} selected · {{ selectedDayCommitCount }} commits across {{ selectedDayRepoCount }} repos
          </span>
        </div>
      </div>
      <div class="explorer-chart__canvas-wrap">
        <div v-if="explorer.analyticsError" class="explorer-chart__empty explorer-chart__empty--error">
          <span>{{ explorer.analyticsError }}</span>
          <button class="explorer-chart__retry" type="button" @click="explorer.loadAnalytics()">Retry</button>
        </div>
        <VChart
          v-else-if="chartDays.length"
          class="explorer-chart__canvas"
          :style="chartCanvasStyle"
          :option="chartOption"
          :autoresize="true"
          @click="handleExplorerChartClick"
        />
        <div v-else class="explorer-chart__empty">
          {{ explorer.loadingAnalytics ? 'Loading activity…' : 'No activity yet.' }}
        </div>
      </div>
    </section>

    <section class="explorer-split">
      <!-- LEFT: commit list -->
      <aside class="explorer-split__list-pane">
        <header class="explorer-split__pane-header">
          <span class="explorer-split__pane-title">Commits · {{ formattedSelectedDate || 'No date' }}</span>
          <span class="explorer-split__pane-count">
            {{ explorer.selectedSeriesLabel ? `${explorer.selectedSeriesLabel} · ` : '' }}{{ explorer.displayCommits.length }} commits
          </span>
        </header>
        <div v-if="explorer.dayError" class="explorer-split__empty explorer-split__empty--error">
          <span>{{ explorer.dayError }}</span>
          <button class="explorer-split__retry-btn" type="button" @click="explorer.loadDay()">Retry</button>
        </div>
        <div v-else-if="explorer.loadingDay" class="explorer-split__empty">Loading commits…</div>
        <div v-else-if="!explorer.displayCommits.length" class="explorer-split__empty">No commits for this day.</div>
        <div v-else class="explorer-split__commit-list">
          <button
            v-for="commit in explorer.displayCommits"
            :key="commit.commitSha"
            class="explorer-split__commit-row"
            :class="{ 'explorer-split__commit-row--active': commit.commitSha === explorer.selectedCommit?.commitSha }"
            type="button"
            @click="explorer.selectCommit(commit.commitSha)"
          >
            <GitCommitHorizontal :size="16" class="explorer-split__commit-icon" />
            <span class="explorer-split__commit-body">
              <span class="explorer-split__commit-subject">{{ commit.subject }}</span>
              <span class="explorer-split__commit-meta">
                {{ commit.author }} · {{ commit.repoId }} · +{{ commit.linesAdded }} −{{ commit.linesRemoved }}
              </span>
            </span>
            <span v-if="commit.issueKeys && commit.issueKeys.length" class="explorer-split__commit-tag">
              {{ commit.issueKeys[0] }}
            </span>
          </button>
        </div>
      </aside>

      <!-- RIGHT: detail panel -->
      <section class="explorer-split__detail-pane">
        <header class="explorer-split__pane-header">
          <span class="explorer-split__pane-title">Commit detail</span>
          <button class="explorer-split__open-btn" type="button" @click="openSelectedCommit">
            <ExternalLink :size="13" />
            <span>Open full detail</span>
          </button>
        </header>
        <div v-if="!explorer.selectedCommit" class="explorer-split__empty">Select a commit to see details.</div>
        <div v-else class="explorer-split__detail-body">
          <div class="explorer-detail__commit-info">
            <div class="explorer-detail__subject">{{ explorer.selectedCommit.subject }}</div>
            <div class="explorer-detail__hash">
              {{ explorer.selectedCommit.commitSha.slice(0, 7) }} ·
              {{ formatDetailTimestamp(explorer.selectedCommit.authoredAt) }}
            </div>
            <div class="explorer-detail__author-row">
              <span class="explorer-detail__avatar">{{ authorInitial }}</span>
              <span class="explorer-detail__author-name">{{ explorer.selectedCommit.author }}</span>
              <span class="explorer-detail__author-repo">· {{ explorer.selectedCommit.repoId }}</span>
            </div>
          </div>

          <div class="explorer-detail__divider" />

          <div class="explorer-detail__stats">
            <div class="explorer-detail__stat">
              <span class="explorer-detail__stat-label">Files changed</span>
              <span class="explorer-detail__stat-value explorer-detail__stat-value--neutral">{{ detailFilesCount }}</span>
            </div>
            <div class="explorer-detail__stat">
              <span class="explorer-detail__stat-label">Lines added</span>
              <span class="explorer-detail__stat-value explorer-detail__stat-value--added">+{{ explorer.selectedCommit.linesAdded }}</span>
            </div>
            <div class="explorer-detail__stat">
              <span class="explorer-detail__stat-label">Lines removed</span>
              <span class="explorer-detail__stat-value explorer-detail__stat-value--removed">−{{ explorer.selectedCommit.linesRemoved }}</span>
            </div>
          </div>

          <div class="explorer-detail__divider" />

          <div class="explorer-detail__section">
            <div class="explorer-detail__section-header">
              <span class="explorer-detail__section-title">Annotations</span>
              <button class="explorer-detail__add-btn" type="button" @click="explorer.openNewAnnotation()">
                <Plus :size="12" />
                <span>Add</span>
              </button>
            </div>
            <div v-if="explorer.annotationsError" class="explorer-detail__empty explorer-detail__empty--error">
              <span>{{ explorer.annotationsError }}</span>
              <button class="explorer-detail__retry-btn" type="button" @click="explorer.loadAnnotationsForDay()">Retry</button>
            </div>
            <div v-else-if="explorer.loadingAnnotations" class="explorer-detail__empty">Loading annotations…</div>
            <div v-else-if="!explorer.dayAnnotations.length" class="explorer-detail__empty">No annotations yet.</div>
            <button
              v-for="annotation in explorer.dayAnnotations"
              :key="annotation.id"
              class="explorer-detail__annotation-card"
              type="button"
              @click="explorer.editAnnotation(annotation)"
            >
              <Tag :size="14" class="explorer-detail__annotation-icon" />
              <span class="explorer-detail__annotation-body">
                <span class="explorer-detail__annotation-title">{{ annotation.name || annotation.title || 'Annotation' }}</span>
                <span class="explorer-detail__annotation-desc">
                  {{ annotation.annotationType }} · {{ annotation.description || annotation.body || 'No description' }}
                </span>
              </span>
            </button>
          </div>

          <div class="explorer-detail__divider" />

          <div class="explorer-detail__section">
            <span class="explorer-detail__section-title">Changed files</span>
            <div v-if="explorer.loadingCommitDetail" class="explorer-detail__empty">Loading changed files…</div>
            <div v-else-if="explorer.commitDetailError" class="explorer-detail__empty explorer-detail__empty--error">
              <span>{{ explorer.commitDetailError }}</span>
              <button class="explorer-detail__retry-btn" type="button" @click="explorer.loadSelectedCommitDetail()">Retry</button>
            </div>
            <div v-else-if="!detailFilesCount" class="explorer-detail__empty">
              No changed files were returned for this commit.
            </div>
            <div v-else class="explorer-detail__files">
              <div
                v-for="file in visibleFiles"
                :key="file.filePath"
                class="explorer-detail__file-row"
              >
                <button
                  class="explorer-detail__file-row-button"
                  type="button"
                  :disabled="!supportsNativeFileOpen || openingFilePath === file.filePath"
                  :title="supportsNativeFileOpen ? `Open ${file.filePath}` : 'File opening is only available in the desktop app.'"
                  :aria-label="`Open ${file.filePath}`"
                  @click="openChangedFile(file.filePath)"
                >
                  <File :size="13" class="explorer-detail__file-icon" />
                  <span class="explorer-detail__file-path">{{ file.filePath }}</span>
                  <span class="explorer-detail__file-stats">+{{ file.linesAdded }} −{{ file.linesRemoved }}</span>
                </button>
              </div>
              <button
                v-if="extraFileCount > 0"
                class="explorer-detail__more-files"
                type="button"
                @click="explorer.expandCommitFiles()"
              >
                + {{ extraFileCount }} more files
              </button>
              <div v-if="fileOpenError" class="explorer-detail__empty explorer-detail__empty--error">
                <span>{{ fileOpenError }}</span>
              </div>
            </div>
          </div>
        </div>
      </section>
    </section>

    <ExplorerAnnotationModal
      :open="explorer.annotationDialogOpen"
      :annotation="explorer.editingAnnotation"
      :day="explorer.selectedDate"
      :commit-label="explorer.selectedCommit ? `${explorer.selectedCommit.repoId} · ${explorer.selectedCommit.commitSha.slice(0, 8)}` : null"
      :mode="explorer.editingAnnotation ? 'edit' : 'create'"
      @close="explorer.closeAnnotationDialog()"
      @delete="explorer.handleDeleteAnnotation()"
      @save="(payload) => explorer.handleSaveAnnotation(payload)"
    />
  </section>
</template>

<style scoped>
.explorer-view {
  display: flex;
  flex-direction: column;
  flex: 1;
  min-height: 0;
  overflow: hidden;
  color: #162033;
}

.explorer-chart {
  display: flex;
  flex-direction: column;
  gap: 8px;
  height: 220px;
  flex-shrink: 0;
  padding: 16px 20px;
  border-bottom: 1px solid rgba(148, 163, 184, 0.22);
  background: transparent;
}

.explorer-chart__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  width: 100%;
}

.explorer-chart__header-main {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
  flex: 1;
  flex-wrap: wrap;
}

.explorer-chart__title {
  font-size: 12px;
  font-weight: 600;
  color: var(--cf-text);
  line-height: 1;
}

.explorer-chart__badge {
  margin-left: auto;
  border-radius: 4px;
  background: var(--cf-accent-subtle);
  padding: 3px 8px;
  font-size: 11px;
  font-weight: 500;
  color: var(--cf-accent);
  line-height: 1.3;
}


.explorer-chart__canvas-wrap {
  flex: 1;
  min-height: 0;
  position: relative;
  display: flex;
  justify-content: flex-start;
}

.explorer-chart__canvas {
  max-width: 100%;
  height: 100%;
}

.explorer-chart__empty {
  display: grid;
  place-items: center;
  gap: 8px;
  height: 100%;
  color: var(--cf-text-tertiary);
  font-size: 11px;
  text-align: center;
}

.explorer-chart__empty--error {
  justify-items: center;
}

.explorer-chart__retry {
  border: 1px solid var(--cf-border);
  border-radius: 6px;
  background: transparent;
  color: var(--cf-text-secondary);
  font-size: 11px;
  font-weight: 500;
  padding: 4px 10px;
  cursor: pointer;
}

.explorer-split {
  display: flex;
  flex: 1;
  min-height: 0;
  overflow: hidden;
}

.explorer-split__list-pane {
  width: 520px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  border-right: 1px solid var(--cf-border);
  min-height: 0;
}

.explorer-split__detail-pane {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  min-height: 0;
}

.explorer-split__pane-header {
  display: flex;
  align-items: center;
  gap: 8px;
  height: 40px;
  padding: 0 16px;
  border-bottom: 1px solid var(--cf-border);
  flex-shrink: 0;
}

.explorer-split__detail-pane .explorer-split__pane-header {
  padding: 0 20px;
}

.explorer-split__pane-title {
  font-size: 12px;
  font-weight: 600;
  color: var(--cf-text);
}

.explorer-split__pane-count {
  margin-left: auto;
  font-size: 11px;
  font-weight: 500;
  color: var(--cf-text-tertiary);
}

.explorer-split__open-btn {
  margin-left: auto;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 4px 10px;
  border: 1px solid var(--cf-border);
  border-radius: 6px;
  background: transparent;
  color: var(--cf-text-secondary);
  font-size: 11px;
  font-weight: 500;
  cursor: pointer;
}

.explorer-split__empty {
  padding: 20px;
  color: var(--cf-text-tertiary);
  font-size: 11px;
}

.explorer-split__empty--error {
  display: grid;
  gap: 8px;
}

.explorer-split__retry-btn,
.explorer-detail__retry-btn {
  justify-self: start;
  border: 1px solid var(--cf-border);
  border-radius: 6px;
  background: transparent;
  color: var(--cf-text-secondary);
  font-size: 11px;
  font-weight: 500;
  padding: 4px 10px;
  cursor: pointer;
}

.explorer-split__commit-list {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
}

.explorer-split__commit-row {
  width: 100%;
  display: flex;
  align-items: center;
  gap: 10px;
  min-height: 56px;
  padding: 8px 16px;
  border: 0;
  border-bottom: 1px solid var(--cf-border);
  background: transparent;
  text-align: left;
  cursor: pointer;
}

.explorer-split__commit-row--active {
  background: color-mix(in srgb, var(--cf-accent-subtle) 70%, var(--cf-surface) 30%);
}

.explorer-split__commit-icon {
  flex-shrink: 0;
  color: var(--cf-text-tertiary);
}

.explorer-split__commit-row--active .explorer-split__commit-icon {
  color: var(--cf-accent);
}

.explorer-split__commit-body {
  display: flex;
  flex-direction: column;
  gap: 2px;
  flex: 1;
  min-width: 0;
}

.explorer-split__commit-subject {
  font-size: 12px;
  font-weight: 500;
  color: var(--cf-text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.explorer-split__commit-meta {
  font-size: 11px;
  color: var(--cf-text-tertiary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.explorer-split__commit-tag {
  flex-shrink: 0;
  border-radius: 4px;
  background: var(--cf-accent-subtle);
  padding: 2px 6px;
  font-size: 10px;
  font-weight: 500;
  color: var(--cf-accent);
}

.explorer-split__detail-body {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 16px 20px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.explorer-detail__commit-info {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.explorer-detail__subject {
  font-size: 14px;
  font-weight: 600;
  color: var(--cf-text);
  line-height: 1.4;
}

.explorer-detail__hash {
  font-size: 11px;
  color: var(--cf-text-tertiary);
}

.explorer-detail__author-row {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 2px;
}

.explorer-detail__avatar {
  display: grid;
  place-items: center;
  width: 18px;
  height: 18px;
  border-radius: 50%;
  background: #6366f1;
  color: white;
  font-size: 10px;
  font-weight: 600;
}

.explorer-detail__author-name {
  font-size: 12px;
  font-weight: 500;
  color: var(--cf-text-secondary);
}

.explorer-detail__author-repo {
  font-size: 12px;
  color: var(--cf-text-tertiary);
}

.explorer-detail__divider {
  height: 1px;
  background: var(--cf-border);
  flex-shrink: 0;
}

.explorer-detail__stats {
  display: flex;
  gap: 16px;
}

.explorer-detail__stat {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.explorer-detail__stat-label {
  font-size: 10px;
  font-weight: 500;
  color: var(--cf-text-tertiary);
  text-transform: uppercase;
  letter-spacing: 0.05em;
}

.explorer-detail__stat-value {
  font-size: 18px;
  font-weight: 700;
  line-height: 1.1;
}

.explorer-detail__stat-value--neutral { color: var(--cf-text); }
.explorer-detail__stat-value--added { color: #22c55e; }
.explorer-detail__stat-value--removed { color: #ef4444; }

.explorer-detail__section {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.explorer-detail__section-header {
  display: flex;
  align-items: center;
  gap: 8px;
}

.explorer-detail__section-title {
  font-size: 12px;
  font-weight: 600;
  color: var(--cf-text);
}

.explorer-detail__add-btn {
  margin-left: auto;
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 3px 8px;
  border: 1px solid var(--cf-border);
  border-radius: 6px;
  background: transparent;
  color: var(--cf-text-secondary);
  font-size: 11px;
  font-weight: 500;
  cursor: pointer;
}

.explorer-detail__empty {
  font-size: 11px;
  color: var(--cf-text-tertiary);
}

.explorer-detail__empty--error {
  display: grid;
  gap: 8px;
}

.explorer-detail__annotation-card {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  padding: 8px 12px;
  border: 1px solid color-mix(in srgb, var(--cf-accent) 22%, transparent);
  border-radius: 8px;
  background: color-mix(in srgb, var(--cf-accent) 8%, transparent);
  text-align: left;
  cursor: pointer;
}

.explorer-detail__annotation-icon {
  flex-shrink: 0;
  color: var(--cf-accent);
}

.explorer-detail__annotation-body {
  display: flex;
  flex-direction: column;
  gap: 2px;
  flex: 1;
  min-width: 0;
}

.explorer-detail__annotation-title {
  font-size: 12px;
  font-weight: 600;
  color: var(--cf-text);
}

.explorer-detail__annotation-desc {
  font-size: 11px;
  color: var(--cf-text-tertiary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.explorer-detail__files {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.explorer-detail__file-row {
  width: 100%;
}

.explorer-detail__file-row-button {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 4px 0;
  width: 100%;
  border: 0;
  background: transparent;
  text-align: left;
  cursor: pointer;
}

.explorer-detail__file-row-button:hover:not(:disabled) {
  border-radius: 6px;
  background: color-mix(in srgb, var(--cf-accent) 10%, transparent);
}

.explorer-detail__file-row-button:hover:not(:disabled) .explorer-detail__file-path {
  color: var(--cf-text);
}

.explorer-detail__file-row-button:disabled {
  cursor: default;
  opacity: 0.72;
}

.explorer-detail__file-icon {
  flex-shrink: 0;
  color: var(--cf-text-tertiary);
}

.explorer-detail__file-path {
  flex: 1;
  min-width: 0;
  display: block;
  font-size: 11px;
  font-weight: 500;
  color: var(--cf-text-secondary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  direction: rtl;
  text-align: left;
  unicode-bidi: plaintext;
}

.explorer-detail__file-stats {
  flex-shrink: 0;
  font-size: 10px;
  font-weight: 500;
  color: var(--cf-text-tertiary);
}

.explorer-detail__more-files {
  margin-top: 2px;
  padding: 4px 0;
  border: 0;
  background: transparent;
  color: var(--cf-accent);
  font-size: 11px;
  font-weight: 500;
  text-align: left;
  cursor: pointer;
}

</style>
