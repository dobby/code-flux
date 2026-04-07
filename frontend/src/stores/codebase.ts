import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { executeWidgetQuery, getCodebaseStructure } from '../api/workspace'
import type { CodebaseStructureResponse, FilterSpec, QueryExecutionResponse, WidgetQuerySpec } from '../types/workspace'
import { useExplorerStore } from './explorer'

export type CodebaseGrowthPoint = {
  day: string
  snapshotLines: number | null
  cumulativeNetLines: number
}

export type CodebaseTreemapSizeMode = 'loc' | 'files' | 'net'

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

function readNumber(value: unknown) {
  const numeric = Number(value ?? 0)
  return Number.isFinite(numeric) ? numeric : 0
}

export const useCodebaseStore = defineStore('codebase', () => {
  const explorer = useExplorerStore()

  const snapshotSeries = ref<QueryExecutionResponse | null>(null)
  const activitySeries = ref<QueryExecutionResponse | null>(null)
  const structure = ref<CodebaseStructureResponse | null>(null)
  const loading = ref(false)
  const loaded = ref(false)
  const error = ref<string | null>(null)
  const lastRequestId = ref(0)

  const growthPoints = computed<CodebaseGrowthPoint[]>(() => {
    const days = enumerateDays(explorer.effectiveDateRange.from, explorer.effectiveDateRange.to)
    const snapshotMap = new Map<string, number>()
    const activityMap = new Map<string, number>()

    for (const row of snapshotSeries.value?.rows ?? []) {
      snapshotMap.set(String(row.bucket ?? ''), readNumber(row.value))
    }
    for (const row of activitySeries.value?.rows ?? []) {
      activityMap.set(String(row.bucket ?? ''), readNumber(row.value))
    }

    let runningNet = 0
    return days.map((day) => {
      runningNet += activityMap.get(day) ?? 0
      return {
        day,
        snapshotLines: snapshotMap.has(day) ? (snapshotMap.get(day) ?? 0) : null,
        cumulativeNetLines: runningNet,
      }
    })
  })

  const latestSnapshotLines = computed(() => {
    const rows = snapshotSeries.value?.rows ?? []
    const latestRow = rows.at(-1)
    return latestRow ? readNumber(latestRow.value) : 0
  })
  const selectedRangeNet = computed(() => (activitySeries.value?.rows ?? []).reduce((sum, row) => sum + readNumber(row.value), 0))
  const snapshotCoverageDays = computed(() => (snapshotSeries.value?.rows ?? []).length)
  const filterSemantics = computed(() => structure.value?.filterSemantics ?? [
    'Date range changes the growth window for both series.',
    'Author, language, category, and product filters apply to cumulative net activity.',
  ])
  const treemap = computed(() => structure.value?.tree ?? null)
  const summary = computed(() => structure.value?.summary ?? null)
  const languageBreakdown = computed(() => structure.value?.languages ?? [])
  const categoryBreakdown = computed(() => structure.value?.categories ?? [])
  const topDirectoryBreakdown = computed(() => structure.value?.topDirectories ?? [])

  function buildSnapshotQuery(repoId: string): WidgetQuerySpec {
    return {
      dataset: 'repo_state_daily',
      timeBucket: 'day',
      measure: {
        field: 'total_lines',
        aggregation: 'sum',
      },
      groupBy: [],
      filters: [
        { field: 'repo', op: 'eq', values: [repoId] },
        { field: 'date', op: 'between', values: [explorer.effectiveDateRange.from, explorer.effectiveDateRange.to] },
      ],
      comparison: null,
      sort: [{ field: 'bucket', direction: 'asc' }],
      limit: null,
    }
  }

  function buildActivityQuery(repoId: string): WidgetQuerySpec {
    const filters: FilterSpec[] = [
      { field: 'repo', op: 'eq', values: [repoId] },
      { field: 'date', op: 'between', values: [explorer.effectiveDateRange.from, explorer.effectiveDateRange.to] },
    ]

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

    return {
      dataset: 'throughput_daily',
      timeBucket: 'day',
      measure: {
        field: 'net_lines',
        aggregation: 'sum',
      },
      groupBy: [],
      filters,
      comparison: null,
      sort: [{ field: 'bucket', direction: 'asc' }],
      limit: null,
    }
  }

  async function refresh(repoId: string) {
    const normalizedRepoId = repoId.trim()
    if (!normalizedRepoId) {
      snapshotSeries.value = null
      activitySeries.value = null
      structure.value = null
      error.value = null
      loaded.value = false
      loading.value = false
      return
    }

    const requestId = ++lastRequestId.value
    loading.value = true
    error.value = null

    try {
      const [snapshotResponse, activityResponse, structureResponse] = await Promise.all([
        executeWidgetQuery({
          effectiveQuery: buildSnapshotQuery(normalizedRepoId),
        }),
        executeWidgetQuery({
          effectiveQuery: buildActivityQuery(normalizedRepoId),
        }),
        getCodebaseStructure({
          repoId: normalizedRepoId,
          dateFrom: explorer.effectiveDateRange.from,
          dateTo: explorer.effectiveDateRange.to,
          authorIds: [...explorer.selectedAuthorIds],
          languages: [...explorer.selectedLanguages],
          categories: [...explorer.selectedCategories],
          productCodes: [...explorer.selectedProductCodes],
        }),
      ])

      if (requestId !== lastRequestId.value) {
        return
      }

      snapshotSeries.value = snapshotResponse
      activitySeries.value = activityResponse
      structure.value = structureResponse
      loaded.value = true
    } catch (caught) {
      if (requestId !== lastRequestId.value) {
        return
      }

      snapshotSeries.value = null
      activitySeries.value = null
      structure.value = null
      loaded.value = true
      error.value = caught instanceof Error ? caught.message : 'Unexpected codebase error'
    } finally {
      if (requestId === lastRequestId.value) {
        loading.value = false
      }
    }
  }

  return {
    snapshotSeries,
    activitySeries,
    loading,
    loaded,
    error,
    growthPoints,
    latestSnapshotLines,
    selectedRangeNet,
    snapshotCoverageDays,
    structure,
    treemap,
    summary,
    languageBreakdown,
    categoryBreakdown,
    topDirectoryBreakdown,
    filterSemantics,
    refresh,
  }
})
