<script setup lang="ts">
import {
  Chart,
  CategoryScale,
  Filler,
  Legend,
  LineController,
  LineElement,
  LinearScale,
  PointElement,
  Tooltip,
  type ChartConfiguration,
  type ChartEvent,
  type Plugin,
} from 'chart.js'
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { getSeriesColor } from '../lib/chart'
import type { AnalyticsCompareResponse, AnalyticsPoint, AnalyticsQueryResponse, Annotation } from '../types/api'

Chart.register(CategoryScale, LinearScale, PointElement, LineElement, LineController, Tooltip, Legend, Filler)

const props = defineProps<{
  analytics: AnalyticsQueryResponse | null
  comparisonAnalytics: AnalyticsQueryResponse | null
  comparison: AnalyticsCompareResponse | null
  annotations: Annotation[]
  chartMode: 'line' | 'area'
  comparisonOverlayVisible: boolean
  legendVisible: boolean
  anonymizeAuthors: boolean
  authorLabelById: Record<string, string>
}>()

const emit = defineEmits<{
  (event: 'day-select', day: string): void
}>()

const canvasRef = ref<HTMLCanvasElement | null>(null)
let chartInstance: Chart<'line'> | null = null

const labels = computed(() => {
  const days = new Set<string>()

  for (const series of props.analytics?.series ?? []) {
    for (const point of series.points) {
      days.add(point.day)
    }
  }

  if (props.comparisonOverlayVisible) {
    for (const series of props.comparisonAnalytics?.series ?? []) {
      for (const point of alignComparisonPoints(series.points, props.comparison)) {
        days.add(point.day)
      }
    }
  }

  return Array.from(days).sort()
})

const annotationPlugin: Plugin<'line'> = {
  id: 'codeFluxAnnotations',
  afterDatasetsDraw(chart) {
    const xScale = chart.scales.x
    const yScale = chart.scales.y
    if (!xScale || !yScale) {
      return
    }

    const ctx = chart.ctx
    ctx.save()

    for (const annotation of props.annotations) {
      const index = labels.value.indexOf(annotation.day)
      if (index < 0) {
        continue
      }

      const x = xScale.getPixelForValue(index)
      ctx.strokeStyle = colorForAnnotation(annotation.colorToken)
      ctx.setLineDash([4, 4])
      ctx.lineWidth = 1
      ctx.beginPath()
      ctx.moveTo(x, chart.chartArea.top)
      ctx.lineTo(x, chart.chartArea.bottom)
      ctx.stroke()

      ctx.setLineDash([])
      ctx.fillStyle = getCssVar('--cf-text')
      ctx.font = '12px sans-serif'
      ctx.textAlign = 'left'
      ctx.fillText(annotation.title, Math.min(x + 6, chart.chartArea.right - 80), chart.chartArea.top + 14)
    }

    ctx.restore()
  },
}

function getCssVar(name: string): string {
  return getComputedStyle(document.documentElement).getPropertyValue(name).trim()
}

function colorForAnnotation(colorToken: string | null): string {
  switch (colorToken) {
    case 'warning':
      return '#d97706'
    case 'danger':
      return '#dc2626'
    case 'success':
      return '#16a34a'
    default:
      return '#0f766e'
  }
}

function resolveLabel(key: string, fallback: string): string {
  if (!props.anonymizeAuthors) {
    return fallback
  }
  return props.authorLabelById[key] ?? fallback
}

function alignComparisonPoints(
  points: AnalyticsPoint[],
  comparison: AnalyticsCompareResponse | null,
): AnalyticsPoint[] {
  if (!comparison) {
    return points
  }

  const referenceStart = new Date(comparison.comparison.from)
  const currentStart = new Date(comparison.current.from)
  return points.map((point) => {
    const pointDate = new Date(point.day)
    const offsetDays = Math.round((pointDate.getTime() - referenceStart.getTime()) / 86_400_000)
    const aligned = new Date(currentStart)
    aligned.setDate(currentStart.getDate() + offsetDays)
    return {
      day: aligned.toISOString().slice(0, 10),
      value: point.value,
    }
  })
}

function datasetValues(points: AnalyticsPoint[]): number[] {
  const pointMap = new Map(points.map((point) => [point.day, point.value]))
  return labels.value.map((day) => pointMap.get(day) ?? 0)
}

function buildConfiguration(): ChartConfiguration<'line'> {
  const datasets = [
    ...(props.analytics?.series ?? []).map((series, index) => ({
      label: resolveLabel(series.key, series.label),
      data: datasetValues(series.points),
      borderColor: getSeriesColor(index),
      backgroundColor: withAlpha(getSeriesColor(index), 0.12),
      borderWidth: 3,
      pointRadius: 0,
      pointHoverRadius: 3,
      fill: props.chartMode === 'area',
      tension: 0.35,
    })),
    ...(props.comparisonOverlayVisible
      ? (props.comparisonAnalytics?.series ?? []).map((series, index) => ({
          label: `${resolveLabel(series.key, series.label)} reference`,
          data: datasetValues(alignComparisonPoints(series.points, props.comparison)),
          borderColor: getSeriesColor(index),
          backgroundColor: 'transparent',
          borderWidth: 2,
          pointRadius: 0,
          pointHoverRadius: 3,
          fill: false,
          tension: 0.35,
          borderDash: [6, 4],
        }))
      : []),
  ]

  return {
    type: 'line',
    data: {
      labels: labels.value,
      datasets,
    },
    options: {
      responsive: true,
      maintainAspectRatio: false,
      interaction: {
        mode: 'index',
        intersect: false,
      },
      plugins: {
        legend: {
          display: props.legendVisible,
          position: 'right',
          labels: {
            color: getCssVar('--cf-text-secondary'),
            boxWidth: 12,
            boxHeight: 12,
          },
        },
        tooltip: {
          callbacks: {
            label(context) {
              const value = typeof context.parsed.y === 'number' ? Math.round(context.parsed.y) : context.parsed.y
              return `${context.dataset.label}: ${value}`
            },
          },
        },
      },
      scales: {
        x: {
          ticks: {
            color: getCssVar('--cf-text-secondary'),
            maxRotation: 0,
            autoSkip: true,
            maxTicksLimit: 8,
          },
          grid: {
            display: false,
          },
        },
        y: {
          ticks: {
            color: getCssVar('--cf-text-secondary'),
          },
          grid: {
            color: getCssVar('--cf-border'),
          },
        },
      },
      onClick: (event: ChartEvent, _elements, chart) => {
        if (typeof event.x !== 'number') {
          return
        }
        const index = nearestLabelIndex(chart as Chart<'line'>, event.x)
        const day = labels.value[index]
        if (day) {
          emit('day-select', day)
        }
      },
    },
    plugins: [annotationPlugin],
  }
}

function nearestLabelIndex(chart: Chart<'line'>, offsetX: number): number {
  const scale = chart.scales.x
  const rawIndex = scale.getValueForPixel(offsetX)
  if (typeof rawIndex !== 'number' || Number.isNaN(rawIndex)) {
    return 0
  }
  return Math.max(0, Math.min(labels.value.length - 1, Math.round(rawIndex)))
}

function withAlpha(hex: string, alpha: number): string {
  const normalized = hex.replace('#', '')
  const bigint = Number.parseInt(normalized, 16)
  const red = (bigint >> 16) & 255
  const green = (bigint >> 8) & 255
  const blue = bigint & 255
  return `rgba(${red}, ${green}, ${blue}, ${alpha})`
}

function renderChart() {
  const canvas = canvasRef.value
  if (!canvas) {
    return
  }

  chartInstance?.destroy()
  chartInstance = new Chart(canvas, buildConfiguration())
}

function resize() {
  chartInstance?.resize()
}

defineExpose({
  resize,
})

onMounted(() => {
  renderChart()
})

onBeforeUnmount(() => {
  chartInstance?.destroy()
  chartInstance = null
})

watch(
  () => [
    props.analytics,
    props.comparisonAnalytics,
    props.comparison,
    props.annotations,
    props.chartMode,
    props.comparisonOverlayVisible,
    props.legendVisible,
    props.anonymizeAuthors,
    props.authorLabelById,
  ],
  () => {
    renderChart()
  },
  { deep: true },
)
</script>

<template>
  <canvas ref="canvasRef" class="chart chart--chartjs" />
</template>
