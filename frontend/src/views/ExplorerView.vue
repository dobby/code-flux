<script setup lang="ts">
import { computed, onMounted, watch } from 'vue'
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
import ExplorerAnnotationModal from '../components/ExplorerAnnotationModal.vue'
import VChart from 'vue-echarts'
import type { EChartsOption } from 'echarts'

const route = useRoute()
const router = useRouter()
const dashboard = useDashboardStore()
const explorer = useExplorerStore()

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

const chartOption = computed<EChartsOption>(() => ({
  backgroundColor: 'transparent',
  animation: true,
  tooltip: {
    trigger: 'axis',
    axisPointer: { type: 'shadow' },
    formatter: (params: unknown) => {
      const points = Array.isArray(params) ? params : []
      const first = points[0] as { axisValueLabel?: string; data?: number } | undefined
      return `${first?.axisValueLabel ?? ''}<br/>${first?.data ?? 0} commits`
    },
  },
  grid: { left: 0, right: 0, top: 8, bottom: 0, containLabel: false },
  xAxis: {
    type: 'category',
    data: explorer.analytics.map((point) => point.day),
    axisLabel: { show: false },
    axisLine: { show: false },
    axisTick: { show: false },
    splitLine: { show: false },
  },
  yAxis: {
    type: 'value',
    axisLabel: { show: false },
    axisLine: { show: false },
    axisTick: { show: false },
    splitLine: { show: false },
  },
  series: [
    {
      type: 'bar',
      data: explorer.analytics.map((point) => ({
        name: point.day,
        value: point.value,
        itemStyle: {
          color: point.day === explorer.selectedDate ? '#6366f1' : 'rgba(148, 163, 184, 0.35)',
          borderRadius: [2, 2, 0, 0],
        },
      })),
      barMaxWidth: 28,
      barMinWidth: 16,
      barCategoryGap: '20%',
      emphasis: { itemStyle: { color: '#6366f1' } },
    },
  ],
}))

function onExplorerChartClick(event: unknown) {
  const chartEvent = event as { name?: string }
  const day = chartEvent.name
  if (!day) return
  explorer.selectDate(day)
  explorer.syncQueryToUrl(router)
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

const detailFilesCount = computed(() => explorer.displayCommitFiles.length)
const visibleFiles = computed(() => explorer.displayCommitFiles.slice(0, 6))
const extraFileCount = computed(() => Math.max(0, explorer.displayCommitFiles.length - 6))

function openSelectedCommit() {
  const c = explorer.selectedCommit
  if (!c) return
  void router.push({
    name: 'explorer-commit',
    params: { repoId: c.repoId, commitSha: c.commitSha },
    query: { date: explorer.selectedDate },
  })
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
        <span class="explorer-chart__title">Commit activity</span>
        <span v-if="explorer.selectedDate" class="explorer-chart__badge">
          {{ formattedSelectedDate }} selected · {{ selectedDayCommitCount }} commits across {{ selectedDayRepoCount }} repos
        </span>
      </div>
      <div class="explorer-chart__canvas-wrap">
        <VChart
          v-if="explorer.analytics.length"
          class="explorer-chart__canvas"
          :option="chartOption"
          :autoresize="true"
          @click="onExplorerChartClick"
        />
        <div v-else class="explorer-chart__empty">No activity yet.</div>
      </div>
    </section>

    <section class="explorer-split">
      <!-- LEFT: commit list -->
      <aside class="explorer-split__list-pane">
        <header class="explorer-split__pane-header">
          <span class="explorer-split__pane-title">Commits · {{ formattedSelectedDate || 'No date' }}</span>
          <span class="explorer-split__pane-count">{{ explorer.displayCommits.length }} commits</span>
        </header>
        <div v-if="explorer.loadingDay" class="explorer-split__empty">Loading commits…</div>
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
                {{ commit.author }} · {{ commit.repoId }} · {{ (commit as any).filesChanged ?? 0 }} files · +{{ commit.linesAdded }} −{{ commit.linesRemoved }}
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
            <span>Open in editor</span>
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
            <div v-if="!explorer.dayAnnotations.length" class="explorer-detail__empty">No annotations yet.</div>
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
            <div class="explorer-detail__files">
              <div
                v-for="file in visibleFiles"
                :key="file.filePath"
                class="explorer-detail__file-row"
              >
                <File :size="13" class="explorer-detail__file-icon" />
                <span class="explorer-detail__file-path">{{ file.filePath }}</span>
                <span class="explorer-detail__file-stats">+{{ file.linesAdded }} −{{ file.linesRemoved }}</span>
              </div>
              <button v-if="extraFileCount > 0" class="explorer-detail__more-files" type="button">
                + {{ extraFileCount }} more files
              </button>
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
  gap: 8px;
  width: 100%;
}

.explorer-chart__title {
  font-size: 12px;
  font-weight: 600;
  color: #162033;
  line-height: 1;
}

.explorer-chart__badge {
  margin-left: auto;
  border-radius: 4px;
  background: rgba(99, 102, 241, 0.08);
  padding: 3px 8px;
  font-size: 11px;
  font-weight: 500;
  color: #6366f1;
  line-height: 1.3;
}

.explorer-chart__canvas-wrap {
  flex: 1;
  min-height: 0;
  position: relative;
}

.explorer-chart__canvas {
  width: 100%;
  height: 100%;
}

.explorer-chart__empty {
  display: grid;
  place-items: center;
  height: 100%;
  color: #94a0b8;
  font-size: 11px;
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
  border-right: 1px solid rgba(148, 163, 184, 0.22);
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
  border-bottom: 1px solid rgba(148, 163, 184, 0.22);
  flex-shrink: 0;
}

.explorer-split__detail-pane .explorer-split__pane-header {
  padding: 0 20px;
}

.explorer-split__pane-title {
  font-size: 12px;
  font-weight: 600;
  color: #162033;
}

.explorer-split__pane-count {
  margin-left: auto;
  font-size: 11px;
  font-weight: 500;
  color: #94a0b8;
}

.explorer-split__open-btn {
  margin-left: auto;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 4px 10px;
  border: 1px solid rgba(148, 163, 184, 0.22);
  border-radius: 6px;
  background: transparent;
  color: #61708d;
  font-size: 11px;
  font-weight: 500;
  cursor: pointer;
}

.explorer-split__empty {
  padding: 20px;
  color: #94a0b8;
  font-size: 11px;
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
  border-bottom: 1px solid rgba(148, 163, 184, 0.22);
  background: transparent;
  text-align: left;
  cursor: pointer;
}

.explorer-split__commit-row--active {
  background: rgba(255, 255, 255, 0.6);
}

.explorer-split__commit-icon {
  flex-shrink: 0;
  color: #94a0b8;
}

.explorer-split__commit-row--active .explorer-split__commit-icon {
  color: #6366f1;
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
  color: #162033;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.explorer-split__commit-meta {
  font-size: 11px;
  color: #94a0b8;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.explorer-split__commit-tag {
  flex-shrink: 0;
  border-radius: 4px;
  background: rgba(99, 102, 241, 0.08);
  padding: 2px 6px;
  font-size: 10px;
  font-weight: 500;
  color: #6366f1;
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
  color: #162033;
  line-height: 1.4;
}

.explorer-detail__hash {
  font-size: 11px;
  color: #94a0b8;
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
  color: #61708d;
}

.explorer-detail__author-repo {
  font-size: 12px;
  color: #94a0b8;
}

.explorer-detail__divider {
  height: 1px;
  background: rgba(148, 163, 184, 0.22);
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
  color: #94a0b8;
  text-transform: uppercase;
  letter-spacing: 0.05em;
}

.explorer-detail__stat-value {
  font-size: 18px;
  font-weight: 700;
  line-height: 1.1;
}

.explorer-detail__stat-value--neutral { color: #162033; }
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
  color: #162033;
}

.explorer-detail__add-btn {
  margin-left: auto;
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 3px 8px;
  border: 1px solid rgba(148, 163, 184, 0.22);
  border-radius: 6px;
  background: transparent;
  color: #61708d;
  font-size: 11px;
  font-weight: 500;
  cursor: pointer;
}

.explorer-detail__empty {
  font-size: 11px;
  color: #94a0b8;
}

.explorer-detail__annotation-card {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  padding: 8px 12px;
  border: 1px solid rgba(99, 102, 241, 0.2);
  border-radius: 8px;
  background: rgba(99, 102, 241, 0.04);
  text-align: left;
  cursor: pointer;
}

.explorer-detail__annotation-icon {
  flex-shrink: 0;
  color: #6366f1;
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
  color: #162033;
}

.explorer-detail__annotation-desc {
  font-size: 11px;
  color: #94a0b8;
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
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 4px 0;
  width: 100%;
}

.explorer-detail__file-icon {
  flex-shrink: 0;
  color: #94a0b8;
}

.explorer-detail__file-path {
  flex: 1;
  min-width: 0;
  font-size: 11px;
  font-weight: 500;
  color: #61708d;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.explorer-detail__file-stats {
  flex-shrink: 0;
  font-size: 10px;
  font-weight: 500;
  color: #94a0b8;
}

.explorer-detail__more-files {
  margin-top: 2px;
  padding: 4px 0;
  border: 0;
  background: transparent;
  color: #6366f1;
  font-size: 11px;
  font-weight: 500;
  text-align: left;
  cursor: pointer;
}
</style>
