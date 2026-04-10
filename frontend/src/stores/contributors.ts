import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { executeWidgetQuery } from '../api/workspace'
import type { FilterSpec, QueryExecutionResponse, WidgetQuerySpec } from '../types/workspace'
import { useExplorerStore } from './explorer'

export type ContributorMetric = 'net_lines' | 'lines_added' | 'commits_count' | 'files_changed_count'

type ContributorSummaryMetric = ContributorMetric

export type ContributorLeaderboardRow = {
  rank: number
  author: string
  value: number
  intensity: number
}

export type ContributorTrendSeries = {
  author: string
  points: Array<{ day: string; value: number }>
}

export type ContributorSummaryCard = {
  key: string
  label: string
  metric: ContributorMetric | null
  value: number
  tone: 'accent' | 'warm' | 'cool' | 'neutral'
}

export const CONTRIBUTOR_METRIC_OPTIONS: Array<{ value: ContributorMetric; label: string; shortLabel: string }> = [
  { value: 'net_lines', label: 'Net Lines', shortLabel: 'Net' },
  { value: 'lines_added', label: 'Lines Added', shortLabel: 'Added' },
  { value: 'commits_count', label: 'Commit Count', shortLabel: 'Commits' },
  { value: 'files_changed_count', label: 'Files Changed', shortLabel: 'Files' },
]

const SUMMARY_METRICS: ContributorSummaryMetric[] = [
  'net_lines',
  'lines_added',
  'commits_count',
  'files_changed_count',
]

function readNumeric(value: unknown) {
  const numeric = Number(value ?? 0)
  return Number.isFinite(numeric) ? numeric : 0
}

function enumerateDays(from: string, to: string) {
  const days: string[] = []
  if (!from || !to || from > to) {
    return days
  }

  const cursor = new Date(`${from}T00:00:00`)
  const end = new Date(`${to}T00:00:00`)
  while (cursor <= end) {
    days.push(cursor.toISOString().slice(0, 10))
    cursor.setDate(cursor.getDate() + 1)
  }
  return days
}

function contributorMetricLabel(metric: ContributorMetric) {
  return CONTRIBUTOR_METRIC_OPTIONS.find((option) => option.value === metric)?.label ?? 'Net Lines'
}

function contributorMetricShortLabel(metric: ContributorMetric) {
  return CONTRIBUTOR_METRIC_OPTIONS.find((option) => option.value === metric)?.shortLabel ?? 'Net'
}

export const useContributorsStore = defineStore('contributors', () => {
  const explorer = useExplorerStore()

  const metric = ref<ContributorMetric>('net_lines')
  const leaderboard = ref<QueryExecutionResponse | null>(null)
  const trend = ref<QueryExecutionResponse | null>(null)
  const summaryTotals = ref<Partial<Record<ContributorSummaryMetric, number>>>({})
  const loading = ref(false)
  const loaded = ref(false)
  const error = ref<string | null>(null)
  const lastRequestId = ref(0)

  const metricLabel = computed(() => contributorMetricLabel(metric.value))
  const metricShortLabel = computed(() => contributorMetricShortLabel(metric.value))

  const leaderboardRows = computed<ContributorLeaderboardRow[]>(() => {
    const rows = leaderboard.value?.rows ?? []
    const maxMagnitude = rows.reduce((largest, row) => {
      return Math.max(largest, Math.abs(readNumeric(row.value)))
    }, 0)

    return rows.map((row, index) => {
      const value = readNumeric(row.value)
      return {
        rank: index + 1,
        author: String(row.group_label ?? row.group_key ?? 'Unknown contributor'),
        value,
        intensity: maxMagnitude > 0 ? Math.abs(value) / maxMagnitude : 0,
      }
    })
  })

  const activeContributors = computed(() => leaderboardRows.value.length)

  const summaryCards = computed<ContributorSummaryCard[]>(() => {
    const cards: ContributorSummaryCard[] = [
      {
        key: 'active-metric',
        label: metricLabel.value,
        metric: metric.value,
        value: summaryTotals.value[metric.value] ?? 0,
        tone: 'accent',
      },
      {
        key: 'contributors',
        label: 'Active Contributors',
        metric: null,
        value: activeContributors.value,
        tone: 'warm',
      },
    ]

    for (const entry of SUMMARY_METRICS) {
      if (entry === metric.value) {
        continue
      }
      cards.push({
        key: entry,
        label: contributorMetricLabel(entry),
        metric: entry,
        value: summaryTotals.value[entry] ?? 0,
        tone: cards.length % 2 === 0 ? 'cool' : 'neutral',
      })
      if (cards.length === 4) {
        break
      }
    }

    return cards
  })

  const trendDays = computed(() => enumerateDays(
    explorer.effectiveDateRange.from,
    explorer.effectiveDateRange.to,
  ))

  const trendSeries = computed<ContributorTrendSeries[]>(() => {
    const topAuthors = leaderboardRows.value.slice(0, 4).map((row) => row.author)
    if (!topAuthors.length) {
      return []
    }

    const grouped = new Map<string, Map<string, number>>()
    for (const row of trend.value?.rows ?? []) {
      const author = String(row.group_label ?? row.group_key ?? 'Unknown contributor')
      if (!topAuthors.includes(author)) {
        continue
      }
      const bucket = String(row.bucket ?? '')
      const byDay = grouped.get(author) ?? new Map<string, number>()
      byDay.set(bucket, readNumeric(row.value))
      grouped.set(author, byDay)
    }

    return topAuthors.map((author) => {
      const values = grouped.get(author) ?? new Map<string, number>()
      return {
        author,
        points: trendDays.value.map((day) => ({
          day,
          value: values.get(day) ?? 0,
        })),
      }
    })
  })

  const topContributor = computed(() => leaderboardRows.value[0] ?? null)
  const isEmpty = computed(() => loaded.value && !loading.value && !leaderboardRows.value.length)

  function buildFilters(): FilterSpec[] {
    const filters: FilterSpec[] = [
      {
        field: 'date',
        op: 'between',
        values: [explorer.effectiveDateRange.from, explorer.effectiveDateRange.to],
      },
    ]

    if (explorer.selectedRepoIds.length) {
      filters.push({ field: 'repo', op: 'in', values: [...explorer.selectedRepoIds] })
    }
    if (explorer.selectedAuthorIds.length) {
      filters.push({ field: 'author', op: 'in', values: [...explorer.selectedAuthorIds] })
    }
    if (explorer.selectedLanguages.length) {
      filters.push({ field: 'language', op: 'in', values: [...explorer.selectedLanguages] })
    }
    if (explorer.selectedCategories.length) {
      filters.push({ field: 'category', op: 'in', values: [...explorer.selectedCategories] })
    }
    if (explorer.selectedProductCodes.length) {
      filters.push({ field: 'productCode', op: 'in', values: [...explorer.selectedProductCodes] })
    }

    return filters
  }

  function buildQuery(args: {
    metric: ContributorMetric
    timeBucket?: 'day'
    grouped?: boolean
  }): WidgetQuerySpec {
    return {
      dataset: 'throughput_daily',
      timeBucket: args.timeBucket ?? null,
      measure: {
        field: args.metric,
        aggregation: 'sum',
      },
      groupBy: args.grouped ? ['author'] : [],
      filters: buildFilters(),
      comparison: null,
      sort: args.timeBucket
        ? [{ field: 'bucket', direction: 'asc' }]
        : args.grouped
          ? [{ field: 'value', direction: 'desc' }]
          : [],
      limit: null,
    }
  }

  async function refresh() {
    const requestId = ++lastRequestId.value
    loading.value = true
    error.value = null

    try {
      const summaryMetrics = SUMMARY_METRICS.filter((entry) => entry !== metric.value)
      const [leaderboardResponse, trendResponse, ...summaryResponses] = await Promise.all([
        executeWidgetQuery({
          effectiveQuery: buildQuery({ metric: metric.value, grouped: true }),
        }),
        executeWidgetQuery({
          effectiveQuery: buildQuery({ metric: metric.value, grouped: true, timeBucket: 'day' }),
        }),
        ...summaryMetrics.map((entry) =>
          executeWidgetQuery({
            effectiveQuery: buildQuery({ metric: entry }),
          })),
      ])

      if (requestId !== lastRequestId.value) {
        return
      }

      leaderboard.value = leaderboardResponse
      trend.value = trendResponse
      summaryTotals.value = summaryMetrics.reduce<Partial<Record<ContributorSummaryMetric, number>>>((totals, entry, index) => {
        totals[entry] = readNumeric(summaryResponses[index]?.totals.value)
        return totals
      }, {
        [metric.value]: readNumeric(leaderboardResponse.totals.value),
      })
      loaded.value = true
    } catch (caught) {
      if (requestId !== lastRequestId.value) {
        return
      }

      leaderboard.value = null
      trend.value = null
      summaryTotals.value = {}
      loaded.value = true
      error.value = caught instanceof Error ? caught.message : 'Unexpected contributor error'
    } finally {
      if (requestId === lastRequestId.value) {
        loading.value = false
      }
    }
  }

  function setMetric(nextMetric: ContributorMetric) {
    metric.value = nextMetric
  }

  return {
    metric,
    metricLabel,
    metricShortLabel,
    summaryTotals,
    leaderboard,
    trend,
    loading,
    loaded,
    error,
    leaderboardRows,
    activeContributors,
    summaryCards,
    trendDays,
    trendSeries,
    topContributor,
    isEmpty,
    setMetric,
    refresh,
  }
})

export function formatContributorMetric(metric: ContributorMetric, value: number) {
  const rounded = Math.round(value)
  if (metric === 'net_lines' && rounded > 0) {
    return `+${rounded.toLocaleString('en-US')}`
  }
  return rounded.toLocaleString('en-US')
}
