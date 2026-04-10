import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { BarChart, CustomChart, HeatmapChart, LineChart, PieChart, TreemapChart } from 'echarts/charts'
import {
  CalendarComponent,
  DataZoomComponent,
  GridComponent,
  LegendComponent,
  MarkLineComponent,
  TitleComponent,
  TooltipComponent,
} from 'echarts/components'
import type { EChartsOption } from 'echarts'
import type { AnalyticsCompareResponse, AnalyticsQueryResponse, Annotation, Metric } from '../types/api'

use([
  CanvasRenderer,
  BarChart,
  CustomChart,
  HeatmapChart,
  LineChart,
  PieChart,
  TreemapChart,
  CalendarComponent,
  GridComponent,
  TooltipComponent,
  LegendComponent,
  DataZoomComponent,
  MarkLineComponent,
  TitleComponent,
])

const palette = ['#0f766e', '#2563eb', '#f97316', '#c026d3', '#ea580c', '#16a34a']

const accentByToken: Record<string, string> = {
  accent: '#0f766e',
  warning: '#d97706',
  danger: '#dc2626',
  success: '#16a34a',
}

function getCssVar(name: string): string {
  return getComputedStyle(document.documentElement).getPropertyValue(name).trim()
}

export function buildChartOption(args: {
  analytics: AnalyticsQueryResponse | null
  comparisonAnalytics: AnalyticsQueryResponse | null
  annotations: Annotation[]
  metric: Metric
  chartMode: 'line' | 'area'
  comparison: AnalyticsCompareResponse | null
  comparisonOverlayVisible: boolean
  legendVisible: boolean
  anonymizeAuthors: boolean
  authorLabelById: Record<string, string>
}): EChartsOption {
  const currentSeries = (args.analytics?.series ?? []).map((series, index) => ({
    name: resolveSeriesLabel(series.key, series.label, args.anonymizeAuthors, args.authorLabelById),
    type: 'line' as const,
    smooth: true,
    showSymbol: false,
    emphasis: { focus: 'series' },
    lineStyle: { width: 3, color: getSeriesColor(index) },
    areaStyle: args.chartMode === 'area' ? { opacity: 0.12, color: palette[index % palette.length] } : undefined,
    data: series.points.map((point) => [point.day, point.value]),
    markLine:
      index === 0 && args.annotations.length > 0
        ? {
            symbol: 'none',
            lineStyle: { type: 'dashed', opacity: 0.5 },
            label: { color: getCssVar('--cf-text'), formatter: ({ name }: { name: string }) => name },
            data: args.annotations.map((annotation) => ({
              name: annotation.title,
              xAxis: annotation.day,
              lineStyle: {
                color: accentByToken[annotation.colorToken ?? 'accent'] ?? accentByToken.accent,
              },
            })),
          }
        : undefined,
  }))

  const referenceSeries = args.comparisonOverlayVisible
    ? (args.comparisonAnalytics?.series ?? []).map((series, index) => ({
        name: `${resolveSeriesLabel(series.key, series.label, args.anonymizeAuthors, args.authorLabelById)} reference`,
        type: 'line' as const,
        smooth: true,
        showSymbol: false,
        emphasis: { focus: 'series' },
        lineStyle: {
          width: 2,
          type: 'dashed' as const,
          opacity: 0.7,
          color: getSeriesColor(index),
        },
        areaStyle: undefined,
        data: alignComparisonPoints(series.points, args.comparison),
      }))
    : []

  const series = [...currentSeries, ...referenceSeries]

  return {
    backgroundColor: 'transparent',
    color: palette,
    animationDuration: 300,
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'cross' },
      valueFormatter: (value: unknown) =>
        `${Math.round(Number(Array.isArray(value) ? value[0] : value ?? 0))}`,
    },
    legend: args.legendVisible
      ? {
          type: 'scroll',
          orient: 'vertical',
          top: 44,
          right: 8,
          bottom: 56,
          textStyle: { color: getCssVar('--cf-text-secondary') },
          itemWidth: 12,
          itemHeight: 12,
        }
      : { show: false },
    grid: {
      left: 24,
      right: args.legendVisible ? 176 : 16,
      top: 32,
      bottom: 48,
      containLabel: true,
    },
    xAxis: {
      type: 'time',
      axisLine: { lineStyle: { color: getCssVar('--cf-border') } },
      axisLabel: { color: getCssVar('--cf-text-secondary') },
      splitLine: { show: false },
    },
    yAxis: {
      type: 'value',
      axisLabel: { color: getCssVar('--cf-text-secondary') },
      splitLine: { lineStyle: { color: getCssVar('--cf-border') } },
    },
    dataZoom: [
      { type: 'inside', zoomLock: false },
      { type: 'slider', bottom: 4, height: 18, brushSelect: false },
    ],
    title: undefined,
    series: series as EChartsOption['series'],
  }
}

export function getSeriesColor(index: number): string {
  return palette[index % palette.length]
}

function alignComparisonPoints(
  points: Array<{ day: string; value: number }>,
  comparison: AnalyticsCompareResponse | null,
): Array<[string, number]> {
  if (!comparison) {
    return points.map((point) => [point.day, point.value])
  }

  const referenceStart = new Date(comparison.comparison.from)
  const currentStart = new Date(comparison.current.from)
  return points.map((point) => {
    const pointDate = new Date(point.day)
    const offsetDays = Math.round((pointDate.getTime() - referenceStart.getTime()) / 86_400_000)
    const aligned = new Date(currentStart)
    aligned.setDate(currentStart.getDate() + offsetDays)
    return [aligned.toISOString().slice(0, 10), point.value]
  })
}

function resolveSeriesLabel(
  key: string,
  fallback: string,
  anonymizeAuthors: boolean,
  authorLabelById: Record<string, string>,
): string {
  if (!anonymizeAuthors) {
    return fallback
  }
  return authorLabelById[key] ?? fallback
}
