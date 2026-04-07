<script setup lang="ts">
import { computed } from 'vue'
import { Circle, CircleCheck, GitBranch, LoaderCircle, RefreshCw, Terminal } from 'lucide-vue-next'
import { useDashboardStore } from '../stores/dashboard'
import { useWorkspaceStore } from '../stores/workspace'

const dashboard = useDashboardStore()
const workspace = useWorkspaceStore()

type LogRowStatus = 'done' | 'running' | 'queued'
type LogRowSource = 'git' | 'jira'
interface LogRow {
  id: string
  time: string
  status: LogRowStatus
  source: LogRowSource
  label: string
  detail: string
}

function formatTimeHMS(iso: string | null | undefined): string {
  if (!iso) return '—'
  return new Date(iso).toLocaleTimeString('en-GB', { hour12: false })
}

function relativeTime(iso: string | null | undefined): string {
  if (!iso) return 'recently'
  const diffMs = Date.now() - new Date(iso).getTime()
  const mins = Math.floor(diffMs / 60000)
  if (mins < 1) return 'just now'
  if (mins < 60) return `${mins} minute${mins === 1 ? '' : 's'} ago`
  const hrs = Math.floor(mins / 60)
  if (hrs < 24) return `${hrs} hour${hrs === 1 ? '' : 's'} ago`
  const days = Math.floor(hrs / 24)
  return `${days} day${days === 1 ? '' : 's'} ago`
}

const isRunning = computed(() => dashboard.syncStatus?.running ?? false)

const bannerTitle = computed(() => {
  if (isRunning.value) {
    return 'Sync in progress — Fetching repository data'
  }
  const lastRun = dashboard.syncStatus?.lastRun
  if (!lastRun) return 'Repository sync'
  if (lastRun.status === 'FAILED') return 'Repository sync — Last run failed'
  return 'Repository sync — Idle'
})

const bannerSubtitle = computed(() => {
  if (isRunning.value && dashboard.currentSync) {
    const { completedRepos, totalRepos } = dashboard.currentSync
    const started = relativeTime(dashboard.syncStatus?.lastRun?.startedAt)
    return `Processing commits from ${completedRepos + 1} of ${totalRepos} repositories · Started ${started}`
  }
  const lastRun = dashboard.syncStatus?.lastRun
  if (!lastRun) return 'Repository history is ready for the next run.'
  if (lastRun.status === 'FAILED') return lastRun.message ?? 'The last sync run encountered an error.'
  const repoCount = dashboard.syncStatus?.repos.length ?? 0
  return `Last synced ${relativeTime(lastRun.finishedAt)} · ${repoCount} ${repoCount === 1 ? 'repository' : 'repositories'} tracked`
})

const bannerPercent = computed(() => dashboard.syncProgressPercent ?? 0)

const logRows = computed<LogRow[]>(() => {
  const entries = dashboard.syncStatus?.logEntries ?? []
  const rows: LogRow[] = entries.map((entry) => {
    const status: LogRowStatus =
      entry.status === 'COMPLETED' || entry.status === 'SUCCESS'
        ? 'done'
        : entry.status === 'RUNNING'
          ? 'running'
          : 'queued'
    const source: LogRowSource = entry.sourceKind === 'jira' ? 'jira' : 'git'
    const time = status === 'done'
      ? formatTimeHMS(entry.finishedAt)
      : status === 'running'
        ? formatTimeHMS(entry.startedAt)
        : '—'
    const detail = entry.detail
      ?? (status === 'running' ? 'Fetching commits…' : status === 'queued' ? 'Queued' : '')
    return { id: entry.eventKey, time, status, source, label: entry.label, detail }
  })

  // Inject synthetic Jira row if configured but no jira log entry exists
  const hasJiraEntry = rows.some((r) => r.source === 'jira')
  if (!hasJiraEntry && workspace.jiraStatus?.configured) {
    const jiraDetail = isRunning.value
      ? 'Queued · will run after git sync'
      : workspace.jiraStatus.lastSyncAt
        ? `Last sync ${relativeTime(workspace.jiraStatus.lastSyncAt)}`
        : 'Not yet run'
    rows.push({
      id: 'jira-synthetic',
      time: '—',
      status: isRunning.value ? 'queued' : 'done',
      source: 'jira',
      label: 'Jira — issue enrichment',
      detail: jiraDetail,
    })
  }

  return rows
})

const groupedRows = computed(() => {
  const rows = logRows.value
  const done = rows
    .filter((r) => r.status === 'done' && r.source === 'git')
    .sort((a, b) => a.time.localeCompare(b.time))
  const activeGit = rows
    .filter((r) => r.status !== 'done' && r.source === 'git')
    .sort((a, b) => (a.status === 'running' ? -1 : 1) - (b.status === 'running' ? -1 : 1))
  const jira = rows.find((r) => r.source === 'jira') ?? null
  return { done, activeGit, jira }
})

const needDivider1 = computed(
  () => groupedRows.value.done.length > 0
    && (groupedRows.value.activeGit.length > 0 || groupedRows.value.jira !== null),
)

const needDivider2 = computed(
  () => groupedRows.value.jira !== null
    && (groupedRows.value.done.length > 0 || groupedRows.value.activeGit.length > 0),
)

async function handleRunSync() {
  await dashboard.triggerSync()
}
</script>

<template>
  <section class="sync-view">
    <!-- Sync Banner -->
    <div class="sync-banner" :class="{ 'sync-banner--idle': !isRunning }">
      <div class="sync-banner__row">
        <RefreshCw class="sync-banner__icon" :size="18" />
        <div class="sync-banner__text">
          <p class="sync-banner__title">{{ bannerTitle }}</p>
          <p class="sync-banner__subtitle">{{ bannerSubtitle }}</p>
        </div>
        <span v-if="isRunning" class="sync-banner__percent">{{ bannerPercent }}%</span>
        <button
          v-else
          class="sync-banner__action"
          data-testid="sync-button"
          type="button"
          @click="handleRunSync"
        >
          Run sync
        </button>
      </div>
      <div v-if="isRunning" class="sync-banner__track">
        <div class="sync-banner__fill" :style="{ width: bannerPercent + '%' }" />
      </div>
    </div>

    <!-- Sync Log Panel -->
    <div class="sync-log">
      <div class="sync-log__header">
        <Terminal class="sync-log__header-icon" :size="13" />
        <span class="sync-log__header-title">Sync log</span>
        <div class="sync-log__spacer" />
        <span v-if="isRunning" class="sync-log__live">
          <span class="sync-log__live-dot" />
          Live
        </span>
      </div>

      <div class="sync-log__entries">
        <!-- Done rows -->
        <div
          v-for="row in groupedRows.done"
          :key="row.id"
          class="log-row log-row--done"
        >
          <span class="log-row__time">{{ row.time }}</span>
          <CircleCheck class="log-row__status-icon log-row__status-icon--done" :size="13" />
          <GitBranch class="log-row__source-icon" :size="13" />
          <span class="log-row__label">{{ row.label }}</span>
          <span class="log-row__detail">{{ row.detail }}</span>
        </div>

        <div v-if="needDivider1" class="sync-log__divider" />

        <!-- Running + queued git rows -->
        <div
          v-for="row in groupedRows.activeGit"
          :key="row.id"
          class="log-row"
          :class="row.status === 'running' ? 'log-row--running' : 'log-row--queued'"
        >
          <span class="log-row__time">{{ row.time }}</span>
          <LoaderCircle
            v-if="row.status === 'running'"
            class="log-row__status-icon log-row__status-icon--running"
            :size="13"
          />
          <Circle
            v-else
            class="log-row__status-icon log-row__status-icon--queued"
            :size="13"
          />
          <GitBranch
            class="log-row__source-icon"
            :class="{ 'log-row__source-icon--queued': row.status === 'queued' }"
            :size="13"
          />
          <span
            class="log-row__label"
            :class="row.status === 'running' ? 'log-row__label--bold' : 'log-row__label--queued'"
          >{{ row.label }}</span>
          <span class="log-row__detail" :class="{ 'log-row__detail--queued': row.status === 'queued' }">{{ row.detail }}</span>
        </div>

        <div v-if="needDivider2" class="sync-log__divider" />

        <!-- Jira row -->
        <div v-if="groupedRows.jira" class="log-row log-row--jira">
          <span class="log-row__time">{{ groupedRows.jira.time }}</span>
          <Circle class="log-row__status-icon log-row__status-icon--queued" :size="13" />
          <span class="log-row__jira-mark" aria-hidden="true">
            <!-- Atlassian Jira Software mark (three-staircase rectangles) -->
            <svg width="13" height="13" viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg">
              <path d="M11.53 2C11.53 4.65 13.68 6.8 16.33 6.8H18.3V8.7C18.3 11.35 20.45 13.5 23.1 13.5V3A1 1 0 0 0 22.1 2H11.53Z" fill="#2684FF"/>
              <path d="M6.77 6.8C6.77 9.45 8.92 11.6 11.57 11.6H13.54V13.5C13.54 16.15 15.69 18.3 18.34 18.3V7.8A1 1 0 0 0 17.34 6.8H6.77Z" fill="#2684FF" opacity="0.7"/>
              <path d="M2 11.6C2 14.25 4.15 16.4 6.8 16.4H8.77V18.3C8.77 20.95 10.92 23.1 13.57 23.1V12.6A1 1 0 0 0 12.57 11.6H2Z" fill="#2684FF" opacity="0.5"/>
            </svg>
          </span>
          <span class="log-row__label log-row__label--queued">{{ groupedRows.jira.label }}</span>
          <span class="log-row__detail log-row__detail--queued">{{ groupedRows.jira.detail }}</span>
        </div>
      </div>
    </div>
  </section>
</template>

<style scoped>
.sync-view {
  display: flex;
  flex-direction: column;
  gap: 20px;
  padding: 24px;
  min-height: 100%;
  box-sizing: border-box;
}

/* ── Banner ── */
.sync-banner {
  display: flex;
  flex-direction: column;
  gap: 10px;
  height: 76px;
  padding: 12px 20px;
  border-radius: 10px;
  background: var(--cf-accent);
  box-sizing: border-box;
  flex-shrink: 0;
}

.sync-banner--idle {
  justify-content: center;
}

.sync-banner__row {
  display: flex;
  align-items: center;
  gap: 12px;
}

.sync-banner__icon {
  color: #fff;
  flex-shrink: 0;
}

.sync-banner__text {
  flex: 1 1 auto;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.sync-banner__title {
  margin: 0;
  font-size: 13px;
  font-weight: 600;
  color: #fff;
  line-height: 1.2;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.sync-banner__subtitle {
  margin: 0;
  font-size: 11px;
  font-weight: 400;
  color: rgba(255, 255, 255, 0.75);
  line-height: 1.2;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.sync-banner__percent {
  font-size: 15px;
  font-weight: 700;
  color: #fff;
  flex-shrink: 0;
}

.sync-banner__action {
  flex-shrink: 0;
  height: 26px;
  padding: 0 12px;
  border: 0;
  border-radius: 6px;
  background: rgba(255, 255, 255, 0.18);
  color: #fff;
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
  transition: background 120ms ease;
}

.sync-banner__action:hover {
  background: rgba(255, 255, 255, 0.28);
}

.sync-banner__track {
  height: 4px;
  border-radius: 2px;
  background: rgba(255, 255, 255, 0.25);
  overflow: hidden;
  flex-shrink: 0;
}

.sync-banner__fill {
  height: 100%;
  border-radius: 2px;
  background: #fff;
  transition: width 400ms ease;
}

/* ── Log panel ── */
.sync-log {
  flex: 1 1 auto;
  display: flex;
  flex-direction: column;
  min-height: 0;
  border: 1px solid var(--cf-border);
  border-radius: 10px;
  background: var(--cf-surface);
  overflow: hidden;
}

.sync-log__header {
  display: flex;
  align-items: center;
  gap: 8px;
  height: 36px;
  padding: 0 16px;
  border-bottom: 1px solid var(--cf-border);
  flex-shrink: 0;
}

.sync-log__header-icon {
  color: var(--cf-text-secondary);
  flex-shrink: 0;
}

.sync-log__header-title {
  font-size: 12px;
  font-weight: 600;
  color: var(--cf-text);
}

.sync-log__spacer {
  flex: 1 1 auto;
}

.sync-log__live {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  height: 18px;
  padding: 0 6px;
  border-radius: 4px;
  background: rgba(34, 197, 94, 0.12);
  color: #22c55e;
  font-size: 10px;
  font-weight: 600;
  line-height: 1;
  flex-shrink: 0;
}

.sync-log__live-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #22c55e;
  flex-shrink: 0;
  animation: live-pulse 1.4s ease-in-out infinite;
}

@keyframes live-pulse {
  0%, 100% { opacity: 1; transform: scale(1); }
  50% { opacity: 0.5; transform: scale(0.85); }
}

.sync-log__entries {
  flex: 1 1 auto;
  display: flex;
  flex-direction: column;
  padding: 12px 16px;
  overflow-y: auto;
  min-height: 0;
}

.sync-log__divider {
  height: 1px;
  background: var(--cf-border);
  margin: 0;
  flex-shrink: 0;
}

/* ── Log rows ── */
.log-row {
  display: flex;
  align-items: center;
  gap: 10px;
  height: 28px;
  flex-shrink: 0;
}

.log-row--running {
  height: 32px;
}

.log-row__time {
  flex: 0 0 60px;
  font-size: 11px;
  font-weight: 400;
  color: var(--cf-text-tertiary);
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
}

.log-row__status-icon {
  flex-shrink: 0;
}

.log-row__status-icon--done {
  color: #22c55e;
}

.log-row__status-icon--running {
  color: var(--cf-accent);
  animation: loader-spin 1s linear infinite;
}

.log-row__status-icon--queued {
  color: var(--cf-border);
}

@keyframes loader-spin {
  to { transform: rotate(360deg); }
}

.log-row__source-icon {
  flex-shrink: 0;
  color: var(--cf-text-secondary);
}

.log-row__source-icon--queued {
  color: var(--cf-text-tertiary);
}

.log-row__jira-mark {
  flex-shrink: 0;
  width: 13px;
  height: 13px;
  display: inline-flex;
  align-items: center;
}

.log-row__label {
  flex-shrink: 0;
  font-size: 12px;
  font-weight: 500;
  color: var(--cf-text);
  white-space: nowrap;
}

.log-row__label--bold {
  font-weight: 600;
}

.log-row__label--queued {
  color: var(--cf-text-tertiary);
}

.log-row__detail {
  flex: 1 1 auto;
  min-width: 0;
  font-size: 12px;
  font-weight: 400;
  color: var(--cf-text-secondary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.log-row__detail--queued {
  color: var(--cf-text-tertiary);
}
</style>
