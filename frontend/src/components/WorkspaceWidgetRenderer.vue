<script setup lang="ts">
import { computed } from 'vue'
import VChart from 'vue-echarts'
import MarkdownIt from 'markdown-it'
import DOMPurify from 'dompurify'
import type { AnnotationV2, DayDrilldownResponse, PageWidgetResolved, QueryExecutionResponse } from '../types/workspace'

const props = defineProps<{
  widget: PageWidgetResolved
  data?: QueryExecutionResponse
  annotations?: AnnotationV2[]
  drilldown?: DayDrilldownResponse | null
  editMode: boolean
}>()

const emit = defineEmits<{
  (event: 'point-click', payload: { selectedDate: string; seriesField?: string; seriesValue?: string }): void
}>()

const markdown = new MarkdownIt({
  html: false,
  linkify: true,
  breaks: true,
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
      tooltip: { trigger: 'axis' },
      legend: { show: props.widget.effectiveViz.showLegend ?? true },
      grid: { left: 24, right: 16, top: 30, bottom: 24, containLabel: true },
      xAxis: { type: 'category' },
      yAxis: { type: 'value', name: props.widget.effectiveViz.yAxisLabel ?? undefined },
      series: Array.from(grouped.entries()).map(([label, points]) => ({
        name: label,
        type: props.widget.effectiveViz.chartType === 'bar' ? 'bar' : 'line',
        smooth: props.widget.effectiveViz.chartType !== 'bar',
        areaStyle: props.widget.effectiveViz.chartType === 'area' ? {} : undefined,
        showSymbol: true,
        symbol: 'circle',
        symbolSize: 10,
        lineStyle: { width: 3 },
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
        tooltip: { trigger: 'item' },
        legend: { show: true, bottom: 0 },
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
      tooltip: { trigger: 'axis' },
      grid: { left: 24, right: 16, top: 24, bottom: 24, containLabel: true },
      xAxis: { type: 'value' },
      yAxis: { type: 'category', data: points.map((point) => point.name) },
      series: [
        {
          type: 'bar',
          data: points.map((point) => point.value),
        },
      ],
    }
  }

  if (props.widget.instance.kind === 'calendar_heatmap') {
    const data = rows.map((row) => [String(row.bucket ?? ''), Number(row.value ?? 0)])
    return {
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
        top: 24,
        left: 24,
        right: 24,
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

function inspectMostRecentPoint() {
  if (props.editMode || props.widget.instance.kind !== 'time_series') {
    return
  }
  const rows = props.data?.rows ?? []
  const lastRow = [...rows].reverse().find((row) => Number(row.value ?? 0) > 0) ?? rows.at(-1)
  const selectedDate = String(lastRow?.bucket ?? '')
  if (!selectedDate) {
    return
  }
  emit('point-click', {
    selectedDate,
    seriesField: props.widget.effectiveQuery?.groupBy[0],
    seriesValue: lastRow?.group_label ? String(lastRow.group_label) : undefined,
  })
}
</script>

<template>
  <div class="widget-renderer">
    <div v-if="widget.instance.kind === 'header_block'" class="content-block content-block--header">
      <h2>{{ widget.effectiveTitle }}</h2>
      <p v-if="widget.effectiveViz.body">{{ widget.effectiveViz.body }}</p>
    </div>

    <div v-else-if="widget.instance.kind === 'markdown_block'" class="content-block content-block--markdown" v-html="renderedMarkdown" />

    <div v-else-if="widget.instance.kind === 'divider_block'" class="content-block content-block--divider" />

    <div v-else-if="widget.instance.kind === 'metric_card'" class="metric-card">
      <span>{{ widget.effectiveDescription || 'Current value' }}</span>
      <strong>{{ Number(data?.totals.value ?? 0).toLocaleString() }}</strong>
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

    <div v-else-if="widget.instance.kind === 'day_explorer'" class="day-explorer">
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
              <li v-for="commit in drilldown.commits.slice(0, 6)" :key="commit.commitSha">{{ commit.commitSha.slice(0, 8) }} · {{ commit.subject }}</li>
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
      <button
        v-if="widget.instance.kind === 'time_series' && !editMode"
        class="button button--ghost widget-chart__inspect"
        type="button"
        :data-testid="`chart-inspect-${widget.instance.id}`"
        @click="inspectMostRecentPoint"
      >
        Inspect latest day
      </button>
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
