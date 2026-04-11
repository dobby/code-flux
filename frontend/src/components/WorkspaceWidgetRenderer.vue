<script setup lang="ts">
import { computed } from 'vue'
import VChart from 'vue-echarts'
import MarkdownIt from 'markdown-it'
import DOMPurify from 'dompurify'
import '../lib/chart'
import { useAppearanceStore } from '../stores/appearance'
import type { AnnotationV2, DayDrilldownResponse, PageWidgetResolved, QueryExecutionResponse } from '../types/workspace'

const props = defineProps<{
  widget: PageWidgetResolved
  data?: QueryExecutionResponse
  annotations?: AnnotationV2[]
  drilldown?: DayDrilldownResponse | null
  editMode: boolean
  presentation?: 'default' | 'preview'
}>()

const emit = defineEmits<{
  (event: 'point-click', payload: { selectedDate: string; seriesField?: string; seriesValue?: string }): void
}>()

const markdown = new MarkdownIt({
  html: false,
  linkify: true,
  breaks: true,
})

const appearance = useAppearanceStore()
const isPreview = computed(() => props.presentation === 'preview')
const chartMotion = computed(() => ({
  animation: appearance.animateCharts,
  animationDuration: appearance.animateCharts ? 250 : 0,
}))
const metricFootnote = computed(() => props.widget.effectiveDescription || 'Selected range')
const metricValue = computed(() => {
  const totals = props.data?.totals ?? {}
  const field = props.widget.effectiveQuery?.measure?.field ?? ''
  const lookup: Record<string, string> = {
    lines_added: 'linesAdded',
    lines_removed: 'linesRemoved',
    net_lines: 'netLines',
    commit_count: 'commitCount',
    file_count: 'fileCount',
  }
  const key = lookup[field]
  if (key && typeof totals[key] === 'number') {
    return totals[key]
  }
  const fallback = Object.values(totals).find((value) => typeof value === 'number')
  return typeof fallback === 'number' ? fallback : 0
})

const chartOption = computed(() => {
  const rows = props.data?.rows ?? []
  if (!rows.length) {
    return null
  }

  if (props.widget.instance.kind === 'time_series') {
    const grouped = new Map<string, Array<[string, number]>>()
    for (const row of rows) {
      const bucket = String(row.bucket ?? '')
      const label = String(row.group_label ?? 'Total')
      const value = Number(row.value ?? 0)
      const series = grouped.get(label) ?? []
      series.push([bucket, value])
      grouped.set(label, series)
    }
    return {
      ...chartMotion.value,
      tooltip: { trigger: 'axis' },
      legend: { show: false },
      grid: { left: isPreview.value ? 12 : 6, right: isPreview.value ? 12 : 8, top: isPreview.value ? 20 : 8, bottom: isPreview.value ? 20 : 8, containLabel: true },
      xAxis: {
        type: 'category',
        axisLine: { show: false },
        axisTick: { show: false },
        axisLabel: { show: false },
        splitLine: { show: false },
      },
      yAxis: {
        type: 'value',
        name: props.widget.effectiveViz.yAxisLabel ?? undefined,
        axisLine: { show: false },
        axisTick: { show: false },
        axisLabel: { show: false },
        splitLine: { lineStyle: { color: 'rgba(191, 203, 227, 0.55)' } },
      },
      series: Array.from(grouped.entries()).map(([label, points]) => ({
        name: label,
        type: props.widget.effectiveViz.chartType === 'bar' ? 'bar' : 'line',
        smooth: props.widget.effectiveViz.chartType !== 'bar',
        areaStyle: props.widget.effectiveViz.chartType === 'area' ? { color: appearance.accentSubtleColor } : undefined,
        showSymbol: false,
        symbol: 'circle',
        symbolSize: isPreview.value ? 7 : 0,
        lineStyle: { width: isPreview.value ? 2.5 : 2, color: appearance.accentColor },
        itemStyle: { color: appearance.accentColor },
        data: points,
        markLine: props.annotations?.length
          ? {
              silent: true,
              symbol: 'none',
              lineStyle: { color: 'rgba(249, 115, 22, 0.55)', type: 'dashed' },
              data: props.annotations
                .filter((annotation) => annotation.xValue)
                .map((annotation) => ({
                  xAxis: annotation.xValue,
                  label: { formatter: annotation.title },
                })),
            }
          : undefined,
      })),
    }
  }

  if (props.widget.instance.kind === 'distribution') {
    const points = rows.map((row) => ({
      name: String(row.group_label ?? row.group_key ?? 'Unknown'),
      value: Number(row.value ?? 0),
    }))
    if ((props.widget.effectiveViz.chartType ?? 'bar') === 'donut' || props.widget.effectiveViz.chartType === 'pie') {
      return {
        ...chartMotion.value,
        tooltip: { trigger: 'item' },
        legend: { show: false },
        series: [
          {
            type: 'pie',
            radius: props.widget.effectiveViz.chartType === 'donut' ? ['48%', '70%'] : ['0%', '72%'],
            data: points,
          },
        ],
      }
        }
    return {
      ...chartMotion.value,
      tooltip: { trigger: 'axis' },
      grid: { left: isPreview.value ? 12 : 44, right: isPreview.value ? 12 : 6, top: isPreview.value ? 18 : 8, bottom: isPreview.value ? 18 : 8, containLabel: true },
      xAxis: {
        type: 'value',
        axisLine: { show: false },
        axisTick: { show: false },
        axisLabel: { show: false },
        splitLine: { show: false },
      },
      yAxis: {
        type: 'category',
        data: points.map((point) => point.name),
        axisLine: { show: false },
        axisTick: { show: false },
        axisLabel: { color: '#7b88a5', fontSize: 11 },
      },
      series: [
        {
          type: 'bar',
          itemStyle: { color: appearance.accentColor, borderRadius: 4 },
          data: points.map((point) => point.value),
        },
      ],
    }
  }

  if (props.widget.instance.kind === 'calendar_heatmap') {
    const data = rows.map((row) => [String(row.bucket ?? ''), Number(row.value ?? 0)])
    return {
      ...chartMotion.value,
      tooltip: { position: 'top' },
      visualMap: {
        min: 0,
        max: Math.max(...data.map((point) => point[1] as number)),
        calculable: false,
        orient: 'horizontal',
        left: 'center',
        bottom: 0,
      },
      calendar: {
        top: isPreview.value ? 14 : 24,
        left: isPreview.value ? 14 : 24,
        right: isPreview.value ? 14 : 24,
        cellSize: ['auto', 18],
        range: data.length ? String(data[0][0]).slice(0, 4) : new Date().getFullYear(),
      },
      series: [
        {
          type: 'heatmap',
          coordinateSystem: 'calendar',
          data,
        },
      ],
    }
  }

  return null
})

const renderedMarkdown = computed(() => {
  const source = props.widget.effectiveViz.markdown ?? props.widget.effectiveViz.body ?? ''
  return DOMPurify.sanitize(markdown.render(source))
})

function handleChartClick(event: { data?: unknown; name?: string; seriesName?: string }) {
  if (props.editMode) {
    return
  }

  const selectedDate = Array.isArray(event.data)
    ? String((event.data as unknown[])[0] ?? '')
    : String(event.name ?? '')

  if (!selectedDate) {
    return
  }
  emit('point-click', {
    selectedDate,
    seriesField: props.widget.effectiveQuery?.groupBy[0],
    seriesValue: event.seriesName,
  })
}

</script>

<template>
  <div class="widget-renderer" :class="{ 'widget-renderer--preview': isPreview }">
    <div v-if="widget.instance.kind === 'header_block'" class="content-block content-block--header">
      <h2>{{ widget.effectiveTitle }}</h2>
      <p v-if="widget.effectiveViz.body">{{ widget.effectiveViz.body }}</p>
    </div>

    <div v-else-if="widget.instance.kind === 'markdown_block'" class="content-block content-block--markdown" v-html="renderedMarkdown" />

    <div v-else-if="widget.instance.kind === 'divider_block'" class="content-block content-block--divider" />

    <div v-else-if="widget.instance.kind === 'metric_card'" class="metric-card" :class="{ 'metric-card--preview': isPreview }">
      <span class="metric-card__title">{{ widget.effectiveTitle }}</span>
      <strong>{{ Number(metricValue).toLocaleString() }}</strong>
      <p class="metric-card__footnote">{{ metricFootnote }}</p>
    </div>

    <div v-else-if="widget.instance.kind === 'data_table'" class="widget-table">
      <table>
        <thead>
          <tr>
            <th v-for="column in Object.keys(data?.rows[0] ?? {})" :key="column">{{ column }}</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="(row, index) in data?.rows ?? []" :key="index">
            <td v-for="(value, key) in row" :key="key">{{ value }}</td>
          </tr>
        </tbody>
      </table>
    </div>

    <div v-else-if="widget.instance.kind === 'day_explorer'" class="day-explorer" :class="{ 'day-explorer--preview': isPreview }">
      <div v-if="!drilldown" class="workspace-empty-state workspace-empty-state--compact">
        <strong>{{ widget.effectiveViz.emptyStateMessage || 'Select a day from a compatible chart.' }}</strong>
        <p>The selected day context will appear here and stay visible while you review the rest of the page.</p>
      </div>
      <template v-else>
        <div class="day-explorer__summary">
          <article class="metric-card-mini">
            <span>Date</span>
            <strong>{{ new Date(drilldown.summary.date).toLocaleDateString() }}</strong>
          </article>
          <article class="metric-card-mini">
            <span>Commits</span>
            <strong>{{ drilldown.summary.commitsCount }}</strong>
          </article>
          <article class="metric-card-mini">
            <span>Files changed</span>
            <strong>{{ drilldown.summary.filesChangedCount }}</strong>
          </article>
          <article class="metric-card-mini">
            <span>Issues</span>
            <strong>{{ drilldown.summary.jiraIssuesCount }}</strong>
          </article>
        </div>
        <div class="day-explorer__lists">
          <div>
            <h3>Changed files</h3>
            <ul>
              <li v-for="file in drilldown.files.slice(0, 6)" :key="`${file.repoId}:${file.filePath}`">{{ file.filePath }}</li>
            </ul>
          </div>
          <div>
            <h3>Commits</h3>
            <ul>
              <li v-for="commit in drilldown.commits.slice(0, 6)" :key="commit.commitSha">{{ commit.commitSha.slice(0, 8) }} - {{ commit.subject }}</li>
            </ul>
          </div>
        </div>
      </template>
    </div>

    <div
      v-else-if="chartOption"
      class="widget-chart"
      :data-testid="`chart-rendered-${widget.instance.id}`"
    >
      <VChart
        autoresize
        :option="chartOption"
        @click="(event: unknown) => handleChartClick(event as { data?: unknown; name?: string; seriesName?: string })"
      />
    </div>

    <div
      v-else
      class="workspace-empty-state workspace-empty-state--compact"
      :data-testid="`chart-empty-state-${widget.instance.id}`"
    >
      <strong>{{ widget.effectiveViz.emptyStateMessage || 'No matching data' }}</strong>
      <p>Adjust the widget query, run sync, or populate snapshots to render this block.</p>
    </div>
  </div>
</template>

<style scoped>
.widget-renderer {
  display: grid;
  gap: 12px;
}

.widget-renderer--preview {
  gap: 14px;
}

.metric-card {
  display: grid;
  align-content: start;
  gap: 6px;
  height: 100%;
  padding: 0;
  border: 0;
  border-radius: 0;
  background: transparent;
}

.metric-card__title {
  color: #4f5d78;
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.01em;
}

.metric-card strong {
  color: #0f172a;
  font-size: 38px;
  font-weight: 700;
  letter-spacing: -0.05em;
  line-height: 1;
}

.metric-card__footnote {
  margin: auto 0 0;
  color: #94a0b8;
  font-size: 11px;
}

.metric-card--preview {
  padding: 0;
  border-radius: 0;
  background: transparent;
}

.metric-card--preview strong {
  font-size: 44px;
}

.content-block--header,
.content-block--markdown,
.content-block--divider,
.widget-table,
.day-explorer,
.widget-chart {
  border: 0;
  border-radius: 0;
  background: transparent;
}

.content-block--header {
  display: grid;
  gap: 8px;
  padding: 0;
}

.content-block--header h2 {
  margin: 0;
  color: #0f172a;
  font-size: 18px;
  font-weight: 700;
  letter-spacing: -0.03em;
}

.content-block--header p {
  margin: 0;
  color: #64748b;
  font-size: 13px;
  line-height: 1.5;
}

.content-block--markdown {
  padding: 0;
  color: #334155;
  line-height: 1.55;
}

.content-block--divider {
  height: 1px;
  min-height: 1px;
  background: rgba(148, 163, 184, 0.18);
}

.widget-table {
  overflow: hidden;
}

.widget-table table {
  width: 100%;
  border-collapse: collapse;
  font-size: 12px;
}

.widget-table th,
.widget-table td {
  padding: 10px 12px;
  border-bottom: 1px solid rgba(226, 232, 240, 0.9);
  color: #334155;
  text-align: left;
}

.widget-table th {
  color: #64748b;
  font-weight: 700;
}

.day-explorer {
  display: grid;
  gap: 14px;
  padding: 0;
}

.day-explorer--preview {
  gap: 12px;
  padding: 14px;
}

.day-explorer__summary {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
}

.day-explorer__lists {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.day-explorer__lists h3 {
  margin: 0 0 10px;
  color: #0f172a;
  font-size: 13px;
}

.day-explorer__lists ul {
  margin: 0;
  padding-left: 18px;
  color: #475569;
  font-size: 12px;
  line-height: 1.6;
}

.widget-chart {
  position: relative;
  padding: 0;
  min-height: 210px;
  overflow: hidden;
}

.widget-chart--preview {
  min-height: 280px;
}

.widget-chart :deep(.echarts),
.widget-chart :deep(canvas),
.widget-chart :deep(svg) {
  width: 100% !important;
  height: 100% !important;
}

.workspace-empty-state--compact {
  padding: 18px;
}

.workspace-empty-state--compact strong {
  color: #0f172a;
}

.workspace-empty-state--compact p {
  margin: 6px 0 0;
  color: #64748b;
}
</style>
