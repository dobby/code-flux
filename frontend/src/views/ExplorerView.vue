<script setup lang="ts">
import { computed, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  GitCommitHorizontal,
} from 'lucide-vue-next'
import { useDashboardStore } from '../stores/dashboard'
import { useExplorerStore } from '../stores/explorer'
import ExplorerAnnotationModal from '../components/ExplorerAnnotationModal.vue'
import VChart from 'vue-echarts'
import type { EChartsOption } from 'echarts'

const route = useRoute()
const router = useRouter()
const dashboard = useDashboardStore()
const explorer = useExplorerStore()

const chartOption = computed<EChartsOption>(() => ({
  backgroundColor: 'transparent',
  animation: true,
  tooltip: {
    trigger: 'axis',
    formatter: (params: unknown) => {
      const points = Array.isArray(params) ? params : []
      const first = points[0] as { axisValueLabel?: string; data?: number } | undefined
      return `${first?.axisValueLabel ?? ''}<br/>${first?.data ?? 0} commits`
    },
  },
  grid: {
    left: 34,
    right: 24,
    top: 18,
    bottom: 24,
    containLabel: true,
  },
  xAxis: {
    type: 'category',
    data: explorer.analytics.map((point) => point.day.slice(5)),
    axisLabel: {
      color: '#64748b',
      fontSize: 10,
    },
    axisLine: {
      lineStyle: { color: '#d1d5db' },
    },
    axisTick: { show: false },
  },
  yAxis: {
    type: 'value',
    axisLabel: {
      color: '#64748b',
      fontSize: 10,
      showMaxLabel: false,
    },
    splitLine: {
      lineStyle: { color: '#e5e7eb' },
    },
  },
  series: [
    {
      type: 'bar',
      data: explorer.analytics.map((point) => ({
        name: point.day,
        value: point.value,
        itemStyle: {
          color: point.day === explorer.selectedDate ? '#6366f1' : '#a5b4fc',
          borderRadius: [1.5, 1.5, 0, 0],
        },
      })),
      barWidth: 10,
      barCategoryGap: '24%',
      emphasis: {
        itemStyle: {
          color: '#6366f1',
        },
      },
    },
  ],
}))

function onExplorerChartClick(event: unknown) {
  const chartEvent = event as { name?: string; data?: { name?: string } | null; dataIndex?: number; dataIndexInside?: number }
  const day = chartEvent.name || chartEvent.data?.name
  if (!day) {
    return
  }
  const fullDay = explorer.analytics.find((point) => point.day.slice(5) === day)?.day ?? day
  explorer.selectDate(fullDay.length === 10 ? fullDay : day)
  explorer.syncQueryToUrl(router)
}

function openCommit(repoId: string, commitSha: string) {
  explorer.selectCommit(commitSha)
  void router.push({ name: 'explorer-commit', params: { repoId, commitSha }, query: { date: explorer.selectedDate } })
}

watch(
  () => [explorer.rangePreset, explorer.selectedRepoIds] as const,
  () => {
    void explorer.loadAnalytics().then(() => explorer.syncQueryToUrl(router))
  },
)

watch(
  () => explorer.selectedDate,
  () => {
    void Promise.all([explorer.loadDay(), explorer.loadAnnotationsForDay()])
    explorer.syncQueryToUrl(router)
  },
)

watch(
  () => [route.query.date, route.query.range, route.query.repo] as const,
  ([date, range, repo]) => {
    const nextDate = typeof date === 'string' ? date : ''
    const nextRange = (value: unknown): '7d' | '14d' | '30d' | '90d' =>
      value === '7d' || value === '14d' || value === '30d' || value === '90d' ? value : '14d'
    const nextRepoIds = (value: unknown): string[] => {
      if (Array.isArray(value)) {
        return value
          .flatMap((entry) => (typeof entry === 'string' ? entry.split(',') : []))
          .map((entry) => entry.trim())
          .filter(Boolean)
      }
      if (typeof value === 'string') {
        return value.split(',').map((entry) => entry.trim()).filter(Boolean)
      }
      return []
    }
    const parsedDate = nextDate
    const parsedRange = nextRange(range)
    const parsedRepoIds = nextRepoIds(repo)
    if (parsedDate && parsedDate !== explorer.selectedDate) {
      explorer.selectDate(parsedDate)
    }
    if (parsedRange !== explorer.rangePreset) {
      explorer.setRangePreset(parsedRange)
    }
    if (parsedRepoIds.join(',') !== explorer.selectedRepoIds.join(',')) {
      explorer.setRepoFilters(parsedRepoIds)
    }
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
    <section class="explorer-view__chart-card">
      <div class="explorer-view__chart-header">
        <div>
          <h2>Commit activity</h2>
          <p>{{ explorer.selectedDate || 'Select a day' }} · {{ (explorer.dayDetail?.summary.commitsCount ?? explorer.displayCommits.length) }} commits across {{ explorer.dayDetail?.summary.contributorsCount ?? 3 }} contributors</p>
        </div>
        <strong v-if="explorer.dayDetail">{{ explorer.dayDetail.summary.date.slice(5, 10) }}</strong>
      </div>
      <div class="explorer-view__chart" :class="{ 'explorer-view__chart--loading': explorer.loadingAnalytics }">
        <VChart
          v-if="explorer.analytics.length"
          class="explorer-view__chart-canvas"
          :option="chartOption"
          :autoresize="true"
          @click="onExplorerChartClick"
        />
        <div v-else class="explorer-view__empty">No activity yet.</div>
      </div>
    </section>

    <section class="explorer-view__split">
      <article class="explorer-view__panel">
        <div class="explorer-view__panel-header">
          <div>
            <h2>Commits · {{ explorer.selectedDate || 'No date' }}</h2>
            <p>{{ explorer.displayCommits.length }} commits</p>
          </div>
        </div>
        <div v-if="explorer.loadingDay" class="explorer-view__empty">Loading commits…</div>
        <div v-else class="explorer-view__list">
          <button
            v-for="commit in explorer.displayCommits"
            :key="commit.commitSha"
            class="explorer-view__commit"
            :class="{ 'explorer-view__commit--active': commit.commitSha === explorer.selectedCommit?.commitSha }"
            type="button"
            @click="openCommit(commit.repoId, commit.commitSha)"
          >
            <GitCommitHorizontal :size="14" />
            <span class="explorer-view__commit-main">
              <strong>{{ commit.subject }}</strong>
              <small>{{ commit.author }} · {{ commit.repoId }} · {{ commit.linesAdded }} + / {{ commit.linesRemoved }} -</small>
            </span>
            <span v-if="commit.issueKeys.length" class="explorer-view__chip">{{ commit.issueKeys[0] }}</span>
          </button>
        </div>
      </article>

      <article class="explorer-view__panel explorer-view__panel--detail">
        <div v-if="explorer.selectedCommit" class="explorer-view__detail">
            <div class="explorer-view__panel-header">
              <div>
                <h2>Commit detail</h2>
                <p>{{ explorer.selectedCommit.repoId }} · {{ explorer.selectedCommit.commitSha.slice(0, 8) }}</p>
              </div>
              <button class="explorer-view__open" type="button" disabled title="Backend editor-launch support is not wired yet">
                Open in editor
              </button>
            </div>

          <section class="explorer-view__detail-summary">
            <strong>{{ explorer.selectedCommit.subject }}</strong>
            <p>{{ explorer.selectedCommit.author }} · {{ explorer.selectedDate }}</p>
            <div class="explorer-view__metrics">
              <article><span>Files changed</span><strong>{{ explorer.displayCommitFiles.length }}</strong></article>
              <article><span>Lines added</span><strong>{{ explorer.selectedCommit.linesAdded }}</strong></article>
              <article><span>Lines removed</span><strong>{{ explorer.selectedCommit.linesRemoved }}</strong></article>
            </div>
          </section>

          <section class="explorer-view__section">
            <div class="explorer-view__section-header">
              <div>
                <h3>Annotations</h3>
                <p>Notes for the selected day.</p>
              </div>
              <button class="explorer-view__secondary" type="button" @click="explorer.openNewAnnotation()">Add annotation</button>
            </div>
            <div v-if="explorer.loadingAnnotations" class="explorer-view__empty">Loading annotations…</div>
            <div v-else-if="explorer.dayAnnotations.length" class="explorer-view__annotation-list">
              <button
                v-for="annotation in explorer.dayAnnotations"
                :key="annotation.id"
                class="explorer-view__annotation-card"
                type="button"
                @click="explorer.editAnnotation(annotation)"
              >
                <div class="explorer-view__annotation-text">
                  <strong>{{ annotation.name || annotation.title }}</strong>
                  <p>{{ annotation.description || 'No description yet.' }}</p>
                </div>
                <small>{{ annotation.annotationType }}</small>
              </button>
            </div>
            <div v-else class="explorer-view__empty">No annotations for this day.</div>
          </section>

          <section class="explorer-view__section">
            <div class="explorer-view__section-header">
              <div>
                <h3>Changed files</h3>
                <p>{{ explorer.displayCommitFiles.length }} files shown</p>
              </div>
            </div>
            <div class="explorer-view__file-list">
              <div v-for="file in explorer.displayCommitFiles" :key="`${file.repoId}:${file.filePath}`" class="explorer-view__file-row">
                <div>
                  <strong>{{ file.filePath }}</strong>
                  <p>{{ file.language ?? 'Unknown language' }}</p>
                </div>
                <span>{{ file.linesAdded }} + / {{ file.linesRemoved }} -</span>
              </div>
            </div>
          </section>
        </div>
        <div v-else class="explorer-view__empty explorer-view__empty--detail">Select a commit to inspect its details.</div>
      </article>
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
  display: grid;
  gap: 8px;
  color: #162033;
  overflow: hidden;
}

.explorer-view__chart-header,
.explorer-view__panel-header,
.explorer-view__section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.explorer-view__chart-header p,
.explorer-view__panel-header p,
.explorer-view__section-header p,
.explorer-view__file-row p {
  margin: 0;
  color: #64748b;
  font-size: 12px;
}

.explorer-view__chart-header h2,
.explorer-view__panel-header h2 {
  margin: 0;
  color: #111827;
  font-size: 13px;
  font-weight: 700;
}

.explorer-view__open,
.explorer-view__secondary {
  border: 0;
  border-radius: 8px;
  padding: 5px 8px;
  font-size: 11px;
  font-weight: 700;
}

.explorer-view__secondary {
  background: #eef2ff;
  color: #4338ca;
}

.explorer-view__chart-card,
.explorer-view__panel {
  border: 1px solid rgba(148, 163, 184, 0.18);
  border-radius: 8px;
  padding: 10px 10px 9px;
  background: rgba(255, 255, 255, 0.96);
  box-shadow: none;
}

.explorer-view__chart {
  padding: 8px 0 2px;
  height: 148px;
}

.explorer-view__chart-canvas {
  width: 100%;
  height: 100%;
}

.explorer-view__split {
  display: grid;
  grid-template-columns: minmax(0, 1.05fr) minmax(0, 1fr);
  gap: 10px;
  align-items: stretch;
}

.explorer-view__list,
.explorer-view__annotation-list,
.explorer-view__file-list {
  display: grid;
  gap: 4px;
}

.explorer-view__commit,
.explorer-view__annotation-card {
  display: grid;
  grid-template-columns: 14px minmax(0, 1fr) auto;
  align-items: start;
  gap: 6px;
  border: 1px solid rgba(148, 163, 184, 0.18);
  border-radius: 8px;
  padding: 6px 8px;
  background: rgba(248, 250, 252, 0.96);
  text-align: left;
}

.explorer-view__commit--active {
  border-color: rgba(99, 102, 241, 0.28);
  background: rgba(99, 102, 241, 0.08);
}

.explorer-view__commit-main strong,
.explorer-view__annotation-card strong,
.explorer-view__file-row strong {
  color: #111827;
  font-size: 11px;
}

.explorer-view__commit-main small,
.explorer-view__annotation-text p,
.explorer-view__annotation-card small {
  display: block;
  margin-top: 2px;
  color: #64748b;
  font-size: 10px;
  line-height: 1.2;
}

.explorer-view__annotation-card {
  grid-template-columns: minmax(0, 1fr) auto;
}

.explorer-view__annotation-card small {
  text-align: right;
  white-space: nowrap;
  margin: 0;
}

.explorer-view__annotation-text {
  display: grid;
  min-width: 0;
  gap: 2px;
}

.explorer-view__chip {
  border-radius: 999px;
  padding: 2px 7px;
  background: #e0e7ff;
  color: #4338ca;
  font-size: 9px;
  font-weight: 700;
  line-height: 1.2;
}

.explorer-view__detail-summary {
  display: grid;
  gap: 7px;
  border-radius: 8px;
  padding: 8px 9px;
  background: rgba(99, 102, 241, 0.08);
}

.explorer-view__detail-summary strong {
  color: #1e1b4b;
  font-size: 12px;
  line-height: 1.2;
}

.explorer-view__metrics {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 6px;
}

.explorer-view__metrics article {
  border-radius: 8px;
  padding: 6px 7px;
  background: rgba(255, 255, 255, 0.9);
}

.explorer-view__metrics span {
  display: block;
  color: #64748b;
  font-size: 9px;
  text-transform: uppercase;
  letter-spacing: 0.08em;
  line-height: 1.2;
}

.explorer-view__metrics strong {
  color: #111827;
  font-size: 14px;
  line-height: 1.2;
}

.explorer-view__section {
  display: grid;
  gap: 6px;
  margin-top: 8px;
}

.explorer-view__file-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  border-bottom: 1px solid rgba(148, 163, 184, 0.2);
  padding: 7px 2px;
  background: transparent;
}

.explorer-view__file-list {
  border-top: 1px solid rgba(148, 163, 184, 0.12);
}

.explorer-view__file-row p {
  margin: 0;
  font-size: 10px;
}

.explorer-view__file-row span {
  font-size: 10px;
  white-space: nowrap;
}

.explorer-view__open {
  background: #4f46e5;
  color: #fff;
}

.explorer-view__empty {
  border-radius: 8px;
  padding: 8px;
  background: rgba(248, 250, 252, 0.96);
  color: #64748b;
  font-size: 11px;
}

.explorer-view__empty--detail {
  min-height: 100%;
  display: grid;
  place-items: center;
}

@media (max-width: 1100px) {
  .explorer-view__split {
    grid-template-columns: 1fr;
  }
}
</style>
