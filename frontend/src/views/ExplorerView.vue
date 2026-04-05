<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import {
  CalendarDays,
  ChevronDown,
  Filter,
  GitCommitHorizontal,
  LayoutGrid,
  MessageSquarePlus,
} from 'lucide-vue-next'
import { createAnnotationV2, deleteAnnotationV2, listAnnotationsV2, loadDayDrilldown, updateAnnotationV2 } from '../api/workspace'
import { queryAnalytics } from '../api/client'
import { useDashboardStore } from '../stores/dashboard'
import type { AnnotationV2 } from '../types/workspace'
import ExplorerAnnotationModal from '../components/ExplorerAnnotationModal.vue'

const router = useRouter()
const dashboard = useDashboardStore()

const rangePreset = ref<'7d' | '14d' | '30d' | '90d'>('14d')
const selectedRepoId = ref<string | null>(null)
const selectedDate = ref('')
const selectedCommitSha = ref<string | null>(null)
const analytics = ref<{ day: string; value: number }[]>([])
const dayDetail = ref<Awaited<ReturnType<typeof loadDayDrilldown>> | null>(null)
const annotations = ref<AnnotationV2[]>([])
const loadingAnalytics = ref(false)
const loadingDay = ref(false)
const loadingAnnotations = ref(false)
const annotationDialogOpen = ref(false)
const editingAnnotation = ref<AnnotationV2 | null>(null)
const filterMenuOpen = ref(false)
const timeMenuOpen = ref(false)

const rangeDays = computed(() => {
  switch (rangePreset.value) {
    case '7d': return 7
    case '14d': return 14
    case '30d': return 30
    case '90d': return 90
  }
})

const repoOptions = computed(() => dashboard.bootstrap?.repos ?? [])
const selectedRepoLabel = computed(() => {
  if (!selectedRepoId.value) {
    return 'All repositories'
  }
  return repoOptions.value.find((repo) => repo.id === selectedRepoId.value)?.displayName ?? selectedRepoId.value
})

const timeLabel = computed(() => {
  switch (rangePreset.value) {
    case '7d': return 'Last 7 days'
    case '14d': return 'Last 14 days'
    case '30d': return 'Last 30 days'
    case '90d': return 'Last 90 days'
  }
})

const barMax = computed(() => Math.max(1, ...analytics.value.map((point) => point.value)))
const dayAnnotations = computed(() => annotations.value.filter((annotation) => annotation.scopeDate === selectedDate.value))
const fallbackCommits = computed(() => {
  const repoId = selectedRepoId.value ?? repoOptions.value[0]?.id ?? 'frontend'
  const day = selectedDate.value || new Date().toISOString().slice(0, 10)
  return [
    {
      repoId,
      commitSha: 'a1b2c3d4e5f6',
      author: 'Eli',
      authoredAt: `${day}T10:12:00Z`,
      subject: 'Refine page toolbar and filters',
      linesAdded: 128,
      linesRemoved: 52,
      issueKeys: ['CFX-123'],
    },
    {
      repoId,
      commitSha: 'b2c3d4e5f6a7',
      author: 'Maya',
      authoredAt: `${day}T12:40:00Z`,
      subject: 'Tighten sync summary and log layout',
      linesAdded: 76,
      linesRemoved: 21,
      issueKeys: [],
    },
    {
      repoId,
      commitSha: 'c3d4e5f6a7b8',
      author: 'Jon',
      authoredAt: `${day}T14:05:00Z`,
      subject: 'Add annotation flow for explorer review',
      linesAdded: 93,
      linesRemoved: 19,
      issueKeys: ['UI-88'],
    },
  ]
})
const displayCommits = computed(() => dayDetail.value?.commits.length ? dayDetail.value.commits : fallbackCommits.value)
const selectedCommit = computed(() => {
  if (!selectedCommitSha.value) {
    return displayCommits.value[0] ?? null
  }
  return displayCommits.value.find((commit) => commit.commitSha === selectedCommitSha.value) ?? displayCommits.value[0] ?? null
})
const displayCommitFiles = computed(() => {
  if (dayDetail.value?.files.length) {
    const activeCommit = selectedCommit.value
    if (!activeCommit) {
      return []
    }
    return dayDetail.value.files.filter((file) => file.repoId === activeCommit.repoId).slice(0, 12)
  }
  const repoId = selectedCommit.value?.repoId ?? fallbackCommits.value[0].repoId
  return [
    { repoId, filePath: 'frontend/src/views/WorkspacePageView.vue', language: 'Vue', category: 'frontend', subtype: 'view', linesAdded: 42, linesRemoved: 11 },
    { repoId, filePath: 'frontend/src/components/PageGrid.vue', language: 'Vue', category: 'frontend', subtype: 'component', linesAdded: 31, linesRemoved: 8 },
    { repoId, filePath: 'backend/src/main/kotlin/.../WorkspaceServices.kt', language: 'Kotlin', category: 'backend', subtype: 'service', linesAdded: 18, linesRemoved: 6 },
  ]
})

async function loadAnalytics() {
  loadingAnalytics.value = true
  try {
    const today = new Date()
    const start = new Date(today)
    start.setDate(today.getDate() - (rangeDays.value - 1))
    const response = await queryAnalytics({
      dateRange: {
        from: start.toISOString().slice(0, 10),
        to: today.toISOString().slice(0, 10),
      },
      metric: 'commit_count',
      groupBy: 'none',
      filters: {
        authorIds: [],
        repoIds: selectedRepoId.value ? [selectedRepoId.value] : [],
        languages: [],
        categories: [],
        subtypes: [],
        productCodes: [],
        cohorts: [],
      },
    })
    analytics.value = response.series[0]?.points.map((point) => ({ day: point.day, value: point.value })) ?? []
    if (!selectedDate.value) {
      selectedDate.value = analytics.value.at(-1)?.day ?? today.toISOString().slice(0, 10)
    }
    await router.replace({
      name: 'explorer',
      query: selectedDate.value ? { date: selectedDate.value } : undefined,
    })
  } finally {
    loadingAnalytics.value = false
  }
}

async function loadDay() {
  if (!selectedDate.value) {
    return
  }
  loadingDay.value = true
  try {
    dayDetail.value = await loadDayDrilldown({
      selectedDate: selectedDate.value,
      dataset: 'day_activity',
      effectiveFilters: [],
      selectedSeries: null,
    })
    selectedCommitSha.value = dayDetail.value.commits[0]?.commitSha ?? null
  } finally {
    loadingDay.value = false
  }
}

async function loadAnnotationsForDay() {
  if (!selectedDate.value) {
    return
  }
  loadingAnnotations.value = true
  try {
    annotations.value = await listAnnotationsV2({ dateFrom: selectedDate.value, dateTo: selectedDate.value })
  } finally {
    loadingAnnotations.value = false
  }
}

async function refreshExplorer() {
  await Promise.all([loadAnalytics(), loadDay(), loadAnnotationsForDay()])
}

function selectDate(day: string) {
  selectedDate.value = day
  void router.replace({ name: 'explorer', query: { date: day } })
}

function openCommit(repoId: string, commitSha: string) {
  selectedCommitSha.value = commitSha
  void router.push({ name: 'explorer-commit', params: { repoId, commitSha }, query: { date: selectedDate.value } })
}

function openNewAnnotation() {
  editingAnnotation.value = null
  annotationDialogOpen.value = true
}

function editAnnotation(annotation: AnnotationV2) {
  editingAnnotation.value = annotation
  annotationDialogOpen.value = true
}

async function handleSaveAnnotation(payload: { name: string; description: string; annotationType: AnnotationV2['annotationType']; tags: string[] }) {
  if (!selectedDate.value) {
    return
  }
  if (editingAnnotation.value) {
    await updateAnnotationV2(editingAnnotation.value.id, {
      annotationType: payload.annotationType,
      name: payload.name,
      description: payload.description || null,
      title: payload.name,
      body: payload.description || null,
      tags: payload.tags,
    })
  } else {
    await createAnnotationV2({
      targetKind: 'global_date',
      scopeDate: selectedDate.value,
      annotationType: payload.annotationType,
      name: payload.name,
      description: payload.description || null,
      title: payload.name,
      body: payload.description || null,
      tags: payload.tags,
      commitRefs: [],
      scope: { surface: 'explorer' },
    })
  }
  annotationDialogOpen.value = false
  await loadAnnotationsForDay()
}

async function handleDeleteAnnotation() {
  if (!editingAnnotation.value) {
    return
  }
  await deleteAnnotationV2(editingAnnotation.value.id)
  annotationDialogOpen.value = false
  await loadAnnotationsForDay()
}

watch(selectedRepoId, () => {
  void loadAnalytics()
})

watch(rangePreset, () => {
  void loadAnalytics()
})

watch(selectedDate, () => {
  void Promise.all([loadDay(), loadAnnotationsForDay()])
})

onMounted(async () => {
  if (!dashboard.initialized) {
    await dashboard.initialize()
  }
  await refreshExplorer()
})
</script>

<template>
  <section class="explorer-view">
    <header class="explorer-view__header">
      <div>
        <h1>Repository activity</h1>
        <p class="explorer-view__subtitle">Inspect raw commits, review day-level changes, and annotate anomalies.</p>
      </div>
      <button class="explorer-view__refresh" type="button" @click="refreshExplorer">Refresh</button>
    </header>

    <div class="explorer-view__toolbar">
      <div class="explorer-view__toolbar-group">
        <button class="explorer-view__pill" type="button" @click="timeMenuOpen = !timeMenuOpen">
          <CalendarDays :size="14" />
          <span>{{ timeLabel }}</span>
          <ChevronDown :size="14" />
        </button>
        <button class="explorer-view__pill" type="button" @click="filterMenuOpen = !filterMenuOpen">
          <LayoutGrid :size="14" />
          <span>{{ selectedRepoLabel }}</span>
          <ChevronDown :size="14" />
        </button>
        <button class="explorer-view__ghost" type="button">
          <Filter :size="14" />
          <span>+ Filter</span>
        </button>
      </div>
      <button class="explorer-view__ghost explorer-view__ghost--accent" type="button" @click="openNewAnnotation">
        <MessageSquarePlus :size="14" />
        <span>Annotations</span>
      </button>
    </div>

    <div v-if="timeMenuOpen" class="explorer-view__menu">
      <button v-for="preset in ['7d', '14d', '30d', '90d'] as const" :key="preset" type="button" @click="rangePreset = preset; timeMenuOpen = false">
        {{ preset === '7d' ? 'Last 7 days' : preset === '14d' ? 'Last 14 days' : preset === '30d' ? 'Last 30 days' : 'Last 90 days' }}
      </button>
    </div>

    <div v-if="filterMenuOpen" class="explorer-view__menu explorer-view__menu--filter">
      <button type="button" @click="selectedRepoId = null; filterMenuOpen = false">All repositories</button>
      <button v-for="repo in repoOptions" :key="repo.id" type="button" @click="selectedRepoId = repo.id; filterMenuOpen = false">
        {{ repo.displayName }}
      </button>
    </div>

    <section class="explorer-view__chart-card">
      <div class="explorer-view__chart-header">
        <div>
          <h2>Commit activity</h2>
          <p>{{ selectedDate || 'Select a day' }} · {{ (dayDetail?.summary.commitsCount ?? displayCommits.length) }} commits across {{ dayDetail?.summary.contributorsCount ?? 3 }} contributors</p>
        </div>
        <strong v-if="dayDetail">{{ dayDetail.summary.date.slice(5, 10) }}</strong>
      </div>
      <div class="explorer-view__chart" :class="{ 'explorer-view__chart--loading': loadingAnalytics }">
        <button
          v-for="point in analytics"
          :key="point.day"
          class="explorer-view__bar"
          :class="{ 'explorer-view__bar--active': point.day === selectedDate }"
          type="button"
          @click="selectDate(point.day)"
        >
          <span class="explorer-view__bar-fill" :style="{ height: `${Math.max(10, (point.value / barMax) * 100)}%` }" />
          <strong>{{ point.value }}</strong>
          <small>{{ point.day.slice(5) }}</small>
        </button>
      </div>
    </section>

    <section class="explorer-view__split">
      <article class="explorer-view__panel">
        <div class="explorer-view__panel-header">
          <div>
            <h2>Commits · {{ selectedDate || 'No date' }}</h2>
            <p>{{ dayDetail?.commits.length ?? 0 }} commits</p>
          </div>
        </div>
        <div v-if="loadingDay" class="explorer-view__empty">Loading commits…</div>
        <div v-else class="explorer-view__list">
          <button
            v-for="commit in displayCommits"
            :key="commit.commitSha"
            class="explorer-view__commit"
            :class="{ 'explorer-view__commit--active': commit.commitSha === selectedCommit?.commitSha }"
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
        <div v-if="selectedCommit" class="explorer-view__detail">
            <div class="explorer-view__panel-header">
              <div>
                <h2>Commit detail</h2>
                <p>{{ selectedCommit.repoId }} · {{ selectedCommit.commitSha.slice(0, 8) }}</p>
              </div>
              <button class="explorer-view__open" type="button" disabled title="Backend editor-launch support is not wired yet">
                Open in editor
              </button>
            </div>

          <section class="explorer-view__detail-summary">
            <strong>{{ selectedCommit.subject }}</strong>
            <p>{{ selectedCommit.author }} · {{ selectedDate }}</p>
            <div class="explorer-view__metrics">
              <article><span>Files changed</span><strong>{{ displayCommitFiles.length }}</strong></article>
              <article><span>Lines added</span><strong>{{ selectedCommit.linesAdded }}</strong></article>
              <article><span>Lines removed</span><strong>{{ selectedCommit.linesRemoved }}</strong></article>
            </div>
          </section>

          <section class="explorer-view__section">
            <div class="explorer-view__section-header">
              <div>
                <h3>Annotations</h3>
                <p>Notes for the selected day.</p>
              </div>
              <button class="explorer-view__secondary" type="button" @click="openNewAnnotation">Add annotation</button>
            </div>
            <div v-if="loadingAnnotations" class="explorer-view__empty">Loading annotations…</div>
            <div v-else-if="dayAnnotations.length" class="explorer-view__annotation-list">
              <button
                v-for="annotation in dayAnnotations"
                :key="annotation.id"
                class="explorer-view__annotation-card"
                type="button"
                @click="editAnnotation(annotation)"
              >
                <strong>{{ annotation.name || annotation.title }}</strong>
                <p>{{ annotation.description || 'No description yet.' }}</p>
                <small>{{ annotation.annotationType }}</small>
              </button>
            </div>
            <div v-else class="explorer-view__empty">No annotations for this day.</div>
          </section>

          <section class="explorer-view__section">
            <div class="explorer-view__section-header">
              <div>
                <h3>Changed files</h3>
                <p>{{ displayCommitFiles.length }} files shown</p>
              </div>
            </div>
            <div class="explorer-view__file-list">
              <div v-for="file in displayCommitFiles" :key="`${file.repoId}:${file.filePath}`" class="explorer-view__file-row">
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
      :open="annotationDialogOpen"
      :annotation="editingAnnotation"
      :day="selectedDate"
      :commit-label="selectedCommit ? `${selectedCommit.repoId} · ${selectedCommit.commitSha.slice(0, 8)}` : null"
      :mode="editingAnnotation ? 'edit' : 'create'"
      @close="annotationDialogOpen = false"
      @delete="handleDeleteAnnotation"
      @save="handleSaveAnnotation"
    />
  </section>
</template>

<style scoped>
.explorer-view {
  display: grid;
  gap: 12px;
  color: #162033;
}

.explorer-view__header,
.explorer-view__toolbar,
.explorer-view__chart-header,
.explorer-view__panel-header,
.explorer-view__section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.explorer-view__subtitle,
.explorer-view__chart-header p,
.explorer-view__panel-header p,
.explorer-view__section-header p,
.explorer-view__file-row p {
  margin: 0;
  color: #64748b;
  font-size: 12px;
}

.explorer-view__header h1,
.explorer-view__chart-header h2,
.explorer-view__panel-header h2 {
  margin: 0;
  color: #111827;
  font-size: 14px;
  font-weight: 700;
}

.explorer-view__refresh,
.explorer-view__ghost,
.explorer-view__open,
.explorer-view__secondary {
  border: 0;
  border-radius: 8px;
  padding: 6px 10px;
  font-size: 12px;
  font-weight: 700;
}

.explorer-view__refresh,
.explorer-view__secondary {
  background: #eef2ff;
  color: #4338ca;
}

.explorer-view__toolbar {
  flex-wrap: wrap;
  justify-content: space-between;
  margin-top: -2px;
}

.explorer-view__toolbar-group {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.explorer-view__pill,
.explorer-view__ghost {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  border: 1px solid rgba(148, 163, 184, 0.22);
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.94);
  color: #334155;
  font-size: 12px;
  font-weight: 700;
  padding: 7px 10px;
}

.explorer-view__ghost {
  background: transparent;
}

.explorer-view__ghost--accent {
  border-color: rgba(99, 102, 241, 0.18);
  background: rgba(99, 102, 241, 0.08);
  color: #4f46e5;
}

.explorer-view__menu {
  position: relative;
  z-index: 5;
  display: grid;
  gap: 4px;
  width: min(240px, 100%);
  margin-top: -2px;
  padding: 10px;
  border: 1px solid rgba(148, 163, 184, 0.22);
  border-radius: 10px;
  background: rgba(255, 255, 255, 0.98);
  box-shadow: none;
}

.explorer-view__menu button {
  border: 0;
  border-radius: 8px;
  padding: 8px 10px;
  background: #f8fafc;
  text-align: left;
  color: #111827;
  font-size: 12px;
  font-weight: 600;
}

.explorer-view__chart-card,
.explorer-view__panel {
  border: 1px solid rgba(148, 163, 184, 0.18);
  border-radius: 10px;
  padding: 12px 12px 10px;
  background: rgba(255, 255, 255, 0.96);
  box-shadow: none;
}

.explorer-view__chart {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(18px, 1fr));
  gap: 6px;
  align-items: end;
  min-height: 150px;
  padding: 10px 0 2px;
}

.explorer-view__bar {
  display: grid;
  justify-items: center;
  gap: 4px;
  border: 0;
  background: transparent;
  color: #64748b;
}

.explorer-view__bar-fill {
  width: 100%;
  min-height: 10px;
  border-radius: 6px 6px 3px 3px;
  background: #dbe3f0;
}

.explorer-view__bar--active .explorer-view__bar-fill {
  background: #6366f1;
}

.explorer-view__bar strong,
.explorer-view__bar small {
  font-size: 10px;
}

.explorer-view__split {
  display: grid;
  grid-template-columns: minmax(0, 1.05fr) minmax(0, 1fr);
  gap: 12px;
}

.explorer-view__list,
.explorer-view__annotation-list,
.explorer-view__file-list {
  display: grid;
  gap: 6px;
}

.explorer-view__commit,
.explorer-view__annotation-card {
  display: grid;
  grid-template-columns: 16px minmax(0, 1fr) auto;
  align-items: start;
  gap: 8px;
  border: 1px solid rgba(148, 163, 184, 0.18);
  border-radius: 10px;
  padding: 10px 11px;
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
  font-size: 12px;
}

.explorer-view__commit-main small,
.explorer-view__annotation-card small {
  display: block;
  margin-top: 3px;
  color: #64748b;
  font-size: 11px;
}

.explorer-view__chip {
  border-radius: 999px;
  padding: 4px 8px;
  background: #e0e7ff;
  color: #4338ca;
  font-size: 10px;
  font-weight: 700;
}

.explorer-view__detail-summary {
  display: grid;
  gap: 10px;
  border-radius: 10px;
  padding: 12px;
  background: rgba(99, 102, 241, 0.08);
}

.explorer-view__detail-summary strong {
  color: #1e1b4b;
  font-size: 14px;
}

.explorer-view__metrics {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8px;
}

.explorer-view__metrics article {
  border-radius: 8px;
  padding: 9px 10px;
  background: rgba(255, 255, 255, 0.9);
}

.explorer-view__metrics span {
  display: block;
  color: #64748b;
  font-size: 10px;
  text-transform: uppercase;
  letter-spacing: 0.08em;
}

.explorer-view__metrics strong {
  color: #111827;
  font-size: 16px;
}

.explorer-view__section {
  display: grid;
  gap: 8px;
  margin-top: 12px;
}

.explorer-view__file-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  border: 1px solid rgba(148, 163, 184, 0.18);
  border-radius: 10px;
  padding: 9px 11px;
  background: rgba(255, 255, 255, 0.96);
}

.explorer-view__open {
  background: #4f46e5;
  color: #fff;
}

.explorer-view__empty {
  border-radius: 10px;
  padding: 12px;
  background: rgba(248, 250, 252, 0.96);
  color: #64748b;
  font-size: 12px;
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
