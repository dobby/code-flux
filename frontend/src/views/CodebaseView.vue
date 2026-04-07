<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import VChart from 'vue-echarts'
import type { EChartsOption } from 'echarts'
import { Filter, GitBranch, Layers3, LineChart } from 'lucide-vue-next'
import { useDashboardStore } from '../stores/dashboard'
import { useExplorerStore } from '../stores/explorer'
import { useCodebaseStore, type CodebaseTreemapSizeMode } from '../stores/codebase'
import { useTheme } from '../composables/useTheme'
import { getSeriesColor } from '../lib/chart'
import type { CodebaseBreakdownRow, CodebaseTreeNode } from '../types/workspace'
import '../lib/chart'

const props = defineProps<{
  repoId?: string
}>()

type TreemapDatum = {
  id: string
  name: string
  value: number
  path: string
  kind: string
  linesCount: number
  filesCount: number
  netLines: number
  itemStyle?: Record<string, string | number>
  children?: TreemapDatum[]
}

const TREEMAP_MODE_OPTIONS: Array<{
  id: CodebaseTreemapSizeMode
  label: string
  note: string
}> = [
  {
    id: 'loc',
    label: 'LOC',
    note: 'Tile area follows current line count in the visible tree.',
  },
  {
    id: 'files',
    label: 'Files',
    note: 'Tile area follows current file count while keeping the same structure.',
  },
  {
    id: 'net',
    label: 'Net activity',
    note: 'Tile area uses absolute net magnitude in the selected range; color shows gain vs loss.',
  },
]

const ROOT_COLORS = ['#0f766e', '#2563eb', '#f97316', '#9333ea', '#0891b2', '#ca8a04']

const route = useRoute()
const router = useRouter()
const dashboard = useDashboardStore()
const explorer = useExplorerStore()
const codebase = useCodebaseStore()
const { isDark } = useTheme()

const sizeMode = ref<CodebaseTreemapSizeMode>('loc')

const enabledRepos = computed(() => (dashboard.bootstrap?.repos ?? []).filter((repo) => repo.enabled))
const activeRepoId = computed(() => props.repoId ?? (typeof route.params.repoId === 'string' ? route.params.repoId : ''))
const selectedRepo = computed(() => enabledRepos.value.find((repo) => repo.id === activeRepoId.value) ?? null)

watch(
  () => [enabledRepos.value.map((repo) => repo.id).join(','), activeRepoId.value, route.name] as const,
  async ([repoKey, repoId, routeName]) => {
    if (!repoKey) {
      return
    }
    if (!repoId && routeName === 'codebase') {
      await router.replace({
        name: 'codebase-repo',
        params: { repoId: enabledRepos.value[0].id },
      })
    }
  },
  { immediate: true },
)

watch(
  () => [
    activeRepoId.value,
    explorer.effectiveDateRange.from,
    explorer.effectiveDateRange.to,
    explorer.selectedAuthorIds.join(','),
    explorer.selectedLanguages.join(','),
    explorer.selectedCategories.join(','),
    explorer.selectedProductCodes.join(','),
  ] as const,
  ([repoId]) => {
    if (!repoId) {
      return
    }
    void codebase.refresh(repoId)
  },
  { immediate: true },
)

async function openDefaultRepo() {
  const repo = enabledRepos.value[0]
  if (!repo) {
    return
  }
  await router.replace({ name: 'codebase-repo', params: { repoId: repo.id } })
}

function formatSignedNumber(value: number) {
  const rounded = Math.round(value)
  const prefix = rounded > 0 ? '+' : ''
  return `${prefix}${rounded.toLocaleString('en-US')}`
}

function formatInteger(value: number) {
  return Math.round(value).toLocaleString('en-US')
}

function shortSha(value: string | null | undefined) {
  return value ? value.slice(0, 10) : 'Unavailable'
}

function modeValue(node: CodebaseTreeNode, mode: CodebaseTreemapSizeMode) {
  switch (mode) {
    case 'files':
      return node.filesCount
    case 'net':
      return Math.abs(node.netLines)
    case 'loc':
    default:
      return node.linesCount
  }
}

function netColor(netLines: number) {
  if (netLines > 0) {
    return '#0f766e'
  }
  if (netLines < 0) {
    return '#c2410c'
  }
  return '#94a3b8'
}

function buildTreemapDatum(node: CodebaseTreeNode, mode: CodebaseTreemapSizeMode, depth: number, colorIndex: number): TreemapDatum {
  const children = node.children.map((child, childIndex) => buildTreemapDatum(child, mode, depth + 1, depth === 0 ? childIndex : colorIndex))
  const datum: TreemapDatum = {
    id: node.key,
    name: node.label,
    value: modeValue(node, mode),
    path: node.path || '/',
    kind: node.kind,
    linesCount: node.linesCount,
    filesCount: node.filesCount,
    netLines: node.netLines,
    children: children.length ? children : undefined,
  }

  if (mode === 'net') {
    datum.itemStyle = {
      color: netColor(node.netLines),
      borderColor: isDark.value ? 'rgba(15, 23, 42, 0.88)' : 'rgba(255, 255, 255, 0.82)',
    }
  } else if (depth === 0) {
    datum.itemStyle = {
      color: ROOT_COLORS[colorIndex % ROOT_COLORS.length],
    }
  }

  return datum
}

const activeFilterLabels = computed(() => {
  const labels: string[] = []

  if (explorer.selectedAuthorIds.length) {
    labels.push(explorer.selectedAuthorIds.length === 1
      ? `Author: ${dashboard.bootstrap?.authors.find((author) => author.id === explorer.selectedAuthorIds[0])?.displayName ?? explorer.selectedAuthorIds[0]}`
      : `Authors: ${explorer.selectedAuthorIds.length}`)
  }
  if (explorer.selectedLanguages.length) {
    labels.push(explorer.selectedLanguages.length === 1
      ? `Language: ${explorer.selectedLanguages[0]}`
      : `Languages: ${explorer.selectedLanguages.length}`)
  }
  if (explorer.selectedCategories.length) {
    labels.push(explorer.selectedCategories.length === 1
      ? `Category: ${explorer.selectedCategories[0]}`
      : `Categories: ${explorer.selectedCategories.length}`)
  }
  if (explorer.selectedProductCodes.length) {
    labels.push(explorer.selectedProductCodes.length === 1
      ? `Product: ${explorer.selectedProductCodes[0]}`
      : `Products: ${explorer.selectedProductCodes.length}`)
  }

  return labels
})

const snapshotPointCount = computed(() =>
  codebase.growthPoints.reduce((count, point) => count + (point.snapshotLines === null ? 0 : 1), 0),
)

const growthChartOption = computed<EChartsOption | null>(() => {
  if (!codebase.growthPoints.length) {
    return null
  }

  const showSnapshotSymbols = snapshotPointCount.value > 0 && snapshotPointCount.value <= 8

  return {
    backgroundColor: 'transparent',
    color: [getSeriesColor(0), getSeriesColor(1)],
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

        return [
          rows[0]?.axisValueLabel ?? '',
          ...rows.map((row) => {
            const raw = Array.isArray(row.value) ? row.value.at(-1) : row.value
            const numeric = Number(raw ?? 0)
            return `<span style="display:inline-block;width:8px;height:8px;border-radius:999px;background:${typeof row.color === 'string' ? row.color : '#6366f1'};margin-right:6px;"></span>${row.seriesName}: ${Math.round(Number.isFinite(numeric) ? numeric : 0).toLocaleString('en-US')}`
          }),
        ].join('<br/>')
      },
    },
    legend: {
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
      right: 10,
      top: 10,
      bottom: 42,
      containLabel: true,
    },
    xAxis: {
      type: 'category',
      data: codebase.growthPoints.map((point) => point.day),
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
    series: [
      {
        name: 'Actual LOC',
        type: 'line',
        smooth: true,
        showSymbol: showSnapshotSymbols,
        symbol: 'circle',
        symbolSize: snapshotPointCount.value === 1 ? 10 : 7,
        lineStyle: { width: 2.6, color: getSeriesColor(0) },
        areaStyle: { opacity: 0.08, color: getSeriesColor(0) },
        data: codebase.growthPoints.map((point) => point.snapshotLines),
      },
      {
        name: 'Cumulative Net Activity',
        type: 'line',
        smooth: true,
        showSymbol: false,
        lineStyle: { width: 2.2, type: 'dashed', color: getSeriesColor(1) },
        data: codebase.growthPoints.map((point) => point.cumulativeNetLines),
      },
    ],
  }
})

const summaryCards = computed(() => {
  const summary = codebase.summary
  return [
    {
      label: 'Latest LOC snapshot',
      value: codebase.latestSnapshotLines.toLocaleString('en-US'),
      note: codebase.snapshotCoverageDays > 0
        ? `${codebase.snapshotCoverageDays} snapshot points in range`
        : 'Run snapshots to populate LOC history',
    },
    {
      label: 'Visible structure',
      value: summary ? `${formatInteger(summary.visibleLines)} LOC` : '0 LOC',
      note: summary
        ? `${formatInteger(summary.visibleFiles)} files${summary.visibleFiles !== summary.repoFiles ? ` of ${formatInteger(summary.repoFiles)}` : ''}`
        : 'Waiting for current inventory',
    },
    {
      label: 'Cumulative net activity',
      value: formatSignedNumber(codebase.selectedRangeNet),
      note: summary
        ? `${formatInteger(summary.activeFilesInRange)} active files in range`
        : 'Respects Codebase header filters',
    },
    {
      label: 'Header scope',
      value: explorer.timeLabel,
      note: activeFilterLabels.value.length
        ? activeFilterLabels.value.join(' · ')
        : 'No extra filters applied',
    },
  ]
})

const activeMode = computed(() => TREEMAP_MODE_OPTIONS.find((option) => option.id === sizeMode.value) ?? TREEMAP_MODE_OPTIONS[0])

const treemapState = computed(() => {
  if (codebase.loading) {
    return 'loading'
  }
  if (!codebase.summary) {
    return 'loading'
  }
  if (codebase.summary.repoFiles === 0) {
    return 'no-structure'
  }
  if (codebase.summary.visibleFiles === 0) {
    return 'filtered-empty'
  }
  if (sizeMode.value === 'net' && codebase.summary.activeFilesInRange === 0) {
    return 'no-activity'
  }
  return 'ready'
})

const treemapOption = computed<EChartsOption | null>(() => {
  if (treemapState.value !== 'ready' || !codebase.treemap) {
    return null
  }

  const data = codebase.treemap.children.map((node, index) => buildTreemapDatum(node, sizeMode.value, 0, index))

  return {
    backgroundColor: 'transparent',
    tooltip: {
      formatter: (params: unknown) => {
        const dataPoint = ((params as { data?: TreemapDatum | null } | null)?.data ?? null)
        if (!dataPoint) {
          return ''
        }
        return [
          `<strong>${dataPoint.name}</strong>`,
          dataPoint.path === '/' ? 'Path: /' : `Path: ${dataPoint.path}`,
          `LOC: ${formatInteger(dataPoint.linesCount)}`,
          `Files: ${formatInteger(dataPoint.filesCount)}`,
          `Range net: ${formatSignedNumber(dataPoint.netLines)}`,
        ].join('<br/>')
      },
    },
    series: [
      {
        type: 'treemap',
        roam: false,
        nodeClick: 'zoomToNode',
        breadcrumb: {
          show: true,
          height: 28,
          emptyItemWidth: 20,
          itemStyle: {
            color: isDark.value ? 'rgba(15, 23, 42, 0.74)' : 'rgba(255, 255, 255, 0.88)',
            borderColor: isDark.value ? 'rgba(120, 136, 168, 0.28)' : 'rgba(148, 163, 184, 0.25)',
          },
          textStyle: {
            color: isDark.value ? '#d7deef' : '#334155',
          },
        },
        label: {
          show: true,
          color: isDark.value ? '#f8fafc' : '#0f172a',
          formatter: '{b}',
          overflow: 'truncate',
        },
        upperLabel: {
          show: true,
          height: 22,
          color: isDark.value ? '#e2e8f0' : '#0f172a',
        },
        itemStyle: {
          borderColor: isDark.value ? 'rgba(15, 23, 42, 0.84)' : 'rgba(255, 255, 255, 0.82)',
          borderWidth: 1,
          gapWidth: 3,
        },
        levels: [
          {
            itemStyle: {
              borderColorSaturation: 0.65,
              gapWidth: 4,
              borderWidth: 1,
            },
          },
          {
            colorSaturation: [0.28, 0.56],
            itemStyle: {
              gapWidth: 2,
            },
          },
          {
            colorSaturation: [0.18, 0.42],
            itemStyle: {
              gapWidth: 1,
            },
          },
        ],
        data,
      },
    ],
  }
})

const snapshotFacts = computed(() => {
  const summary = codebase.summary
  if (!summary) {
    return []
  }
  return [
    {
      label: 'Last snapshot',
      value: summary.lastSnapshotDate ?? 'Not built yet',
    },
    {
      label: 'Current ref',
      value: summary.refName ?? 'Unavailable',
    },
    {
      label: 'Current commit',
      value: shortSha(summary.commitSha),
    },
    {
      label: 'Repo baseline',
      value: `${formatInteger(summary.repoLines)} LOC across ${formatInteger(summary.repoFiles)} files`,
    },
    {
      label: 'Visible slice',
      value: `${formatInteger(summary.visibleLines)} LOC across ${formatInteger(summary.visibleFiles)} files`,
    },
    {
      label: 'Range net',
      value: `${formatSignedNumber(summary.netLinesInRange)} across ${formatInteger(summary.activeFilesInRange)} active files`,
    },
  ]
})

function breakdownDescription(row: CodebaseBreakdownRow) {
  return `${formatInteger(row.linesCount)} LOC · ${formatInteger(row.filesCount)} files · ${formatSignedNumber(row.netLines)} net`
}
</script>

<template>
  <section class="workspace-surface codebase-view" data-testid="codebase-view">
    <div v-if="!enabledRepos.length" class="workspace-empty-state" data-testid="codebase-empty-no-repos">
      <strong>No enabled repositories are available for Codebase.</strong>
      <p>Enable at least one repository in settings before wiring repo-specific Codebase pages.</p>
    </div>

    <div
      v-else-if="activeRepoId && !selectedRepo"
      class="workspace-empty-state"
      data-testid="codebase-invalid-repo"
    >
      <strong>This repository is not available in the current Codebase scope.</strong>
      <p>The requested repo was not found among enabled repositories.</p>
      <button class="codebase-empty-action" type="button" @click="openDefaultRepo()">
        Open first enabled repository
      </button>
    </div>

    <div v-else-if="selectedRepo" class="codebase-shell" data-testid="codebase-shell">
      <div class="codebase-growth-card">
        <div class="codebase-growth-card__header">
          <div>
            <span class="codebase-eyebrow">Growth context</span>
            <h2>{{ selectedRepo.displayName }}</h2>
            <p>
              Actual LOC comes from repository snapshots. Cumulative net activity comes from authored throughput facts
              for the same date window.
            </p>
          </div>
          <div class="codebase-growth-card__repo-meta">
            <span>Repository</span>
            <strong>{{ selectedRepo.id }}</strong>
          </div>
        </div>

        <div v-if="codebase.error" class="codebase-feedback codebase-feedback--error" data-testid="codebase-growth-error">
          <strong>Codebase growth data could not load.</strong>
          <p>{{ codebase.error }}</p>
        </div>

        <div
          v-else-if="!growthChartOption && !codebase.loading"
          class="codebase-feedback"
          data-testid="codebase-growth-empty"
        >
          <strong>No growth data is available for this repository and range yet.</strong>
          <p>Run sync and snapshots, or widen the date range.</p>
        </div>

        <div v-else class="codebase-growth-card__chart" data-testid="codebase-growth-chart">
          <VChart v-if="growthChartOption" :autoresize="true" :option="growthChartOption" />
        </div>
      </div>

      <div class="codebase-summary-row" data-testid="codebase-summary-row">
        <article v-for="card in summaryCards" :key="card.label" class="codebase-summary-card">
          <span>{{ card.label }}</span>
          <strong>{{ card.value }}</strong>
          <small>{{ card.note }}</small>
        </article>
      </div>

      <section class="codebase-treemap-card">
        <div class="codebase-treemap-card__header">
          <div>
            <span class="codebase-eyebrow">Current structure</span>
            <h3>Treemap</h3>
            <p>{{ activeMode.note }}</p>
          </div>
          <div class="codebase-mode-switcher" data-testid="codebase-size-mode-switcher">
            <button
              v-for="option in TREEMAP_MODE_OPTIONS"
              :key="option.id"
              class="codebase-mode-switcher__button"
              :class="{ 'codebase-mode-switcher__button--active': sizeMode === option.id }"
              :data-testid="`codebase-size-mode-${option.id}`"
              type="button"
              @click="sizeMode = option.id"
            >
              {{ option.label }}
            </button>
          </div>
        </div>

        <div
          v-if="treemapState === 'no-structure'"
          class="codebase-feedback"
          data-testid="codebase-treemap-empty-no-structure"
        >
          <strong>No current inventory is available for this repository yet.</strong>
          <p>Build current snapshots to populate the tree and structural breakdowns.</p>
        </div>

        <div
          v-else-if="treemapState === 'filtered-empty'"
          class="codebase-feedback"
          data-testid="codebase-treemap-empty-filtered"
        >
          <strong>No current files match the structural filters.</strong>
          <p>Try clearing language, category, or product filters to widen the tree.</p>
        </div>

        <div
          v-else-if="treemapState === 'no-activity'"
          class="codebase-feedback"
          data-testid="codebase-treemap-empty-no-activity"
        >
          <strong>No file activity landed in the selected range.</strong>
          <p>The current tree is still available, but the Net activity size mode has nothing to size against yet.</p>
        </div>

        <div v-else class="codebase-treemap-card__chart" data-testid="codebase-treemap">
          <VChart v-if="treemapOption" :autoresize="true" :option="treemapOption" />
        </div>
      </section>

      <div class="codebase-breakdowns-grid">
        <section class="codebase-shell__panel">
          <div class="codebase-shell__panel-header">
            <Filter :size="16" />
            <strong>Filter semantics</strong>
          </div>
          <ul class="codebase-shell__list" data-testid="codebase-filter-semantics">
            <li v-for="entry in codebase.filterSemantics" :key="entry">{{ entry }}</li>
          </ul>
          <div class="codebase-filter-tags">
            <span v-if="activeFilterLabels.length" v-for="label in activeFilterLabels" :key="label">{{ label }}</span>
            <span v-else>No extra filters applied</span>
          </div>
        </section>

        <section class="codebase-shell__panel" data-testid="codebase-breakdown-languages">
          <div class="codebase-shell__panel-header">
            <Layers3 :size="16" />
            <strong>Languages</strong>
          </div>
          <div v-if="codebase.languageBreakdown.length" class="codebase-breakdown-list">
            <article v-for="row in codebase.languageBreakdown" :key="row.key" class="codebase-breakdown-row">
              <div>
                <strong>{{ row.label }}</strong>
                <small>{{ breakdownDescription(row) }}</small>
              </div>
            </article>
          </div>
          <p v-else>No language data in the current structural slice.</p>
        </section>

        <section class="codebase-shell__panel" data-testid="codebase-breakdown-categories">
          <div class="codebase-shell__panel-header">
            <GitBranch :size="16" />
            <strong>Categories</strong>
          </div>
          <div v-if="codebase.categoryBreakdown.length" class="codebase-breakdown-list">
            <article v-for="row in codebase.categoryBreakdown" :key="row.key" class="codebase-breakdown-row">
              <div>
                <strong>{{ row.label }}</strong>
                <small>{{ breakdownDescription(row) }}</small>
              </div>
            </article>
          </div>
          <p v-else>No category data in the current structural slice.</p>
        </section>

        <section class="codebase-shell__panel" data-testid="codebase-breakdown-top-directories">
          <div class="codebase-shell__panel-header">
            <LineChart :size="16" />
            <strong>Top directories</strong>
          </div>
          <div v-if="codebase.topDirectoryBreakdown.length" class="codebase-breakdown-list">
            <article v-for="row in codebase.topDirectoryBreakdown" :key="row.key" class="codebase-breakdown-row">
              <div>
                <strong>{{ row.label }}</strong>
                <small>{{ breakdownDescription(row) }}</small>
              </div>
            </article>
          </div>
          <p v-else>No directory summary is available for this slice.</p>
        </section>

        <section class="codebase-shell__panel" data-testid="codebase-snapshot-summary">
          <div class="codebase-shell__panel-header">
            <GitBranch :size="16" />
            <strong>Snapshot summary</strong>
          </div>
          <div class="codebase-facts-grid">
            <article v-for="fact in snapshotFacts" :key="fact.label" class="codebase-fact">
              <span>{{ fact.label }}</span>
              <strong>{{ fact.value }}</strong>
            </article>
          </div>
        </section>
      </div>
    </div>
  </section>
</template>

<style scoped>
.codebase-view {
  padding: 74px 20px 20px;
}

.codebase-shell {
  display: grid;
  gap: 16px;
  min-height: 100%;
}

.codebase-growth-card,
.codebase-summary-card,
.codebase-shell__panel,
.codebase-treemap-card {
  border: 1px solid var(--cf-border);
  border-radius: 18px;
  background:
    linear-gradient(180deg, color-mix(in srgb, var(--cf-surface) 94%, white), var(--cf-surface));
  box-shadow: var(--cf-shadow);
}

.codebase-growth-card,
.codebase-treemap-card {
  padding: 20px;
  background:
    radial-gradient(circle at top left, color-mix(in srgb, var(--cf-accent) 12%, transparent), transparent 42%),
    linear-gradient(180deg, color-mix(in srgb, var(--cf-surface) 94%, white), var(--cf-surface));
}

.codebase-growth-card__header,
.codebase-treemap-card__header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 16px;
}

.codebase-eyebrow {
  display: inline-flex;
  align-items: center;
  font-size: 0.72rem;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: var(--cf-text-tertiary);
}

.codebase-growth-card__header h2,
.codebase-treemap-card__header h3 {
  margin: 8px 0 8px;
  line-height: 1.08;
}

.codebase-growth-card__header h2 {
  font-size: 1.45rem;
}

.codebase-treemap-card__header h3 {
  font-size: 1.3rem;
}

.codebase-growth-card__header p,
.codebase-growth-card__repo-meta span,
.codebase-growth-card__repo-meta strong,
.codebase-feedback p,
.codebase-summary-card small,
.codebase-shell__panel p,
.codebase-shell__list,
.codebase-treemap-card__header p,
.codebase-breakdown-row small,
.codebase-fact span {
  color: var(--cf-text-secondary);
}

.codebase-growth-card__repo-meta {
  display: grid;
  gap: 6px;
  min-width: 190px;
  padding: 14px 16px;
  border: 1px solid color-mix(in srgb, var(--cf-border) 78%, transparent);
  border-radius: 14px;
  background: color-mix(in srgb, var(--cf-surface) 92%, transparent);
}

.codebase-growth-card__repo-meta span,
.codebase-summary-card span,
.codebase-fact span {
  font-size: 0.72rem;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.codebase-growth-card__chart {
  height: 320px;
}

.codebase-feedback {
  display: grid;
  gap: 8px;
  padding: 18px;
  border-radius: 14px;
  background: color-mix(in srgb, var(--cf-accent-subtle) 48%, transparent);
}

.codebase-feedback--error {
  border: 1px solid color-mix(in srgb, var(--cf-danger) 28%, var(--cf-border));
}

.codebase-summary-row {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px;
}

.codebase-summary-card {
  display: grid;
  gap: 8px;
  padding: 18px;
}

.codebase-summary-card strong,
.codebase-fact strong {
  font-size: 1.2rem;
}

.codebase-treemap-card__chart {
  height: 540px;
}

.codebase-mode-switcher {
  display: inline-flex;
  flex-wrap: wrap;
  gap: 8px;
}

.codebase-mode-switcher__button,
.codebase-empty-action {
  padding: 10px 14px;
  border: 1px solid var(--cf-border);
  border-radius: 999px;
  background: transparent;
  color: var(--cf-text);
  cursor: pointer;
}

.codebase-mode-switcher__button--active {
  border-color: color-mix(in srgb, var(--cf-accent) 60%, var(--cf-border));
  background: color-mix(in srgb, var(--cf-accent-subtle) 80%, transparent);
  color: color-mix(in srgb, var(--cf-accent) 82%, black);
}

.codebase-breakdowns-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}

.codebase-shell__panel {
  display: flex;
  flex-direction: column;
  gap: 14px;
  padding: 20px;
}

.codebase-shell__panel-header {
  display: inline-flex;
  align-items: center;
  gap: 10px;
}

.codebase-shell__list {
  margin: 0;
  padding-left: 18px;
  line-height: 1.55;
}

.codebase-shell__list li + li {
  margin-top: 8px;
}

.codebase-filter-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.codebase-filter-tags span {
  padding: 8px 10px;
  border-radius: 999px;
  background: color-mix(in srgb, var(--cf-accent-subtle) 72%, transparent);
  color: var(--cf-text-secondary);
  font-size: 0.8rem;
  font-weight: 600;
}

.codebase-breakdown-list {
  display: grid;
  gap: 10px;
}

.codebase-breakdown-row {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  padding: 12px 14px;
  border-radius: 14px;
  background: color-mix(in srgb, var(--cf-accent-subtle) 58%, transparent);
}

.codebase-breakdown-row strong {
  display: block;
  margin-bottom: 4px;
}

.codebase-facts-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.codebase-fact {
  display: grid;
  gap: 6px;
  padding: 12px 14px;
  border-radius: 14px;
  background: color-mix(in srgb, var(--cf-accent-subtle) 58%, transparent);
}

@media (max-width: 1180px) {
  .codebase-summary-row,
  .codebase-breakdowns-grid,
  .codebase-facts-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 860px) {
  .codebase-growth-card__header,
  .codebase-treemap-card__header {
    flex-direction: column;
  }

  .codebase-treemap-card__chart {
    height: 460px;
  }
}

@media (max-width: 720px) {
  .codebase-view {
    padding: 66px 14px 16px;
  }
}
</style>
