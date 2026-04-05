<script setup lang="ts">
import { computed } from 'vue'
import { Circle, CircleCheck, GitBranch, LoaderCircle, RefreshCcw, Square, TimerReset } from 'lucide-vue-next'
import { useDashboardStore } from '../stores/dashboard'
import { useWorkspaceStore } from '../stores/workspace'

const dashboard = useDashboardStore()
const workspace = useWorkspaceStore()

type SyncRow = {
  id: string
  label: string
  detail: string
  status: 'completed' | 'running' | 'queued' | 'failed'
  icon: typeof GitBranch
  percent?: number
}

const isRunning = computed(() => dashboard.syncStatus?.running ?? false)

const currentLabel = computed(() => {
  if (isRunning.value && dashboard.currentSync) {
    return dashboard.currentSync.stage
  }
  return dashboard.syncStatus?.lastRun?.status === 'SUCCESS'
    ? 'Sync complete'
    : 'Sync idle'
})

const headline = computed(() => (isRunning.value ? 'Sync in progress' : 'Repository sync'))

const progress = computed(() => dashboard.syncProgressPercent)

const progressText = computed(() => {
  if (dashboard.currentSync) {
    return `${dashboard.syncProgressLabel ?? 'Processing repositories'} · ${dashboard.syncCurrentRepoLabel ?? 'Current repository'}`
  }
  return dashboard.syncStatus?.lastRun?.message ?? 'Repository history is ready for the next run.'
})

const logRows = computed<SyncRow[]>(() => {
  if (dashboard.syncStatus?.logEntries.length) {
    return dashboard.syncStatus.logEntries.map((entry) => ({
      id: entry.eventKey,
      label: entry.label,
      detail: entry.detail ?? (entry.finishedAt ? `Finished ${new Date(entry.finishedAt).toLocaleString()}` : 'Queued'),
      status: entry.status === 'COMPLETED' || entry.status === 'SUCCESS'
        ? 'completed'
        : entry.status === 'RUNNING'
          ? 'running'
          : entry.status === 'FAILED'
            ? 'failed'
            : 'queued',
      icon: entry.sourceKind === 'jira' ? TimerReset : GitBranch,
      percent: entry.progressPercent ?? undefined,
    }))
  }

  const rows: SyncRow[] = []
  const currentRepoId = dashboard.currentSync?.currentRepoId ?? null

  for (const repo of dashboard.syncStatus?.repos ?? []) {
    let status: SyncRow['status'] = 'queued'
    let detail = repo.lastSuccessfulSyncedAt ? `Last synced ${new Date(repo.lastSuccessfulSyncedAt).toLocaleString()}` : 'Queued'
    if (repo.status === 'FAILED' || repo.lastErrorMessage) {
      status = 'failed'
      detail = repo.lastErrorMessage ?? 'Sync failed'
    } else if (repo.repoId === currentRepoId) {
      status = 'running'
      detail = dashboard.currentSync?.stage ?? 'Running'
    } else if (repo.lastSuccessfulSyncedAt) {
      status = 'completed'
    }

    rows.push({
      id: repo.repoId,
      label: repo.repoId,
      detail,
      status,
      icon: GitBranch,
      percent: status === 'running' ? progress.value : undefined,
    })
  }

  if (workspace.jiraStatus?.configured) {
    rows.push({
      id: 'jira-enrichment',
      label: 'Jira enrichment',
      detail: workspace.jiraStatus.lastSyncAt
        ? `Last sync ${new Date(workspace.jiraStatus.lastSyncAt).toLocaleString()}`
        : 'Queued until git sync completes',
      status: dashboard.syncStatus?.running ? 'queued' : 'completed',
      icon: TimerReset,
    })
  }

  return rows
})

async function handleSyncAction() {
  if (dashboard.syncStatus?.running) {
    await dashboard.stopRunningSync()
    return
  }
  await dashboard.triggerSync()
}
</script>

<template>
  <section class="sync-view">
    <header class="sync-view__header">
      <div>
        <p class="sync-view__eyebrow">Sync</p>
        <h1>{{ headline }}</h1>
        <p class="sync-view__subtitle">
          {{ progressText }}
        </p>
      </div>
      <button
        class="sync-view__action"
        :class="isRunning ? 'sync-view__action--danger' : 'sync-view__action--primary'"
        data-testid="sync-button"
        :disabled="!!dashboard.currentSync?.stopRequested"
        type="button"
        @click="handleSyncAction"
      >
        <LoaderCircle v-if="dashboard.currentSync?.stopRequested" class="spin" :size="16" />
        <Square v-else-if="isRunning" :size="15" />
        <RefreshCcw v-else :size="16" />
        <span>{{ isRunning ? 'Stop sync' : 'Run sync' }}</span>
      </button>
    </header>

    <section class="sync-view__banner" :class="{ 'sync-view__banner--running': isRunning }">
      <div class="sync-view__banner-copy">
        <span class="sync-view__banner-kicker">Repository ingestion</span>
        <strong>{{ currentLabel }}</strong>
        <p>{{ dashboard.syncStatus?.lastRun?.message ?? 'Processing commits and preparing repository snapshots.' }}</p>
      </div>
      <div class="sync-view__banner-meta">
        <strong>{{ progress }}%</strong>
        <span>{{ dashboard.syncCurrentRepoLabel ?? (dashboard.currentSync?.currentRepoId ?? 'All repositories') }}</span>
      </div>
      <div class="sync-view__progress" aria-hidden="true">
        <div class="sync-view__progress-fill" :style="{ width: `${progress}%` }" />
      </div>
    </section>

    <section class="sync-view__log">
      <div class="sync-view__log-header">
        <div>
          <h2>Sync log</h2>
          <p class="sync-view__log-note">Live</p>
        </div>
        <span class="sync-view__live-pill" :class="{ 'sync-view__live-pill--active': isRunning }">
          <Circle :size="8" />
          {{ isRunning ? 'Live' : 'Idle' }}
        </span>
      </div>

      <div class="sync-view__rows">
        <article
          v-for="row in logRows"
          :key="row.id"
          class="sync-view__row"
          :class="`sync-view__row--${row.status}`"
        >
          <div class="sync-view__row-icon">
            <component :is="row.status === 'completed' ? CircleCheck : row.status === 'running' ? LoaderCircle : row.status === 'failed' ? Circle : row.icon" :size="15" />
          </div>
          <div class="sync-view__row-copy">
            <strong>{{ row.label }}</strong>
            <p>{{ row.detail }}</p>
          </div>
          <div class="sync-view__row-meta">
            <span v-if="row.percent != null">{{ row.percent }}%</span>
            <span v-else>{{ row.status }}</span>
          </div>
        </article>
      </div>
    </section>
  </section>
</template>

<style scoped>
.sync-view {
  display: grid;
  gap: 14px;
  padding: 0;
  max-width: 1040px;
}

.sync-view__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.sync-view__eyebrow,
.sync-view__log-note {
  margin: 0 0 4px;
  color: #6b7280;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.sync-view__header h1 {
  margin: 0;
  color: #111827;
  font-size: 14px;
  font-weight: 700;
  line-height: 1;
}

.sync-view__subtitle {
  margin: 2px 0 0;
  color: #64748b;
  font-size: 12px;
}

.sync-view__action {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  border: 0;
  border-radius: 8px;
  min-height: 32px;
  padding: 6px 12px;
  font-size: 12px;
  font-weight: 700;
  cursor: pointer;
}

.sync-view__action--primary {
  background: #4f46e5;
  color: #fff;
}

.sync-view__action--danger {
  background: #ef4444;
  color: #fff;
}

.sync-view__banner {
  position: relative;
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 10px 16px;
  overflow: hidden;
  border-radius: 10px;
  padding: 12px 14px 10px;
  background: linear-gradient(180deg, #6366f1 0%, #4f46e5 100%);
  color: #fff;
}

.sync-view__banner-copy {
  display: grid;
  gap: 4px;
}

.sync-view__banner-kicker {
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  opacity: 0.86;
}

.sync-view__banner-copy strong {
  font-size: 14px;
  font-weight: 700;
}

.sync-view__banner-copy p {
  margin: 0;
  max-width: 720px;
  font-size: 11px;
  opacity: 0.88;
}

.sync-view__banner-meta {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  justify-content: center;
  grid-row: 1 / span 2;
  gap: 6px;
  font-size: 11px;
}

.sync-view__banner-meta strong {
  font-size: 16px;
  line-height: 1;
}

.sync-view__progress {
  grid-column: 1 / -1;
  overflow: hidden;
  border-radius: 999px;
  height: 2px;
  background: rgb(255 255 255 / 16%);
}

.sync-view__progress-fill {
  height: 100%;
  border-radius: inherit;
  background: #fff;
}

.sync-view__log {
  display: grid;
  gap: 10px;
  border-radius: 10px;
  border: 1px solid rgba(148, 163, 184, 0.22);
  padding: 10px 0;
  background: #fff;
  box-shadow: none;
}

.sync-view__log-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 0 14px;
}

.sync-view__log-header h2 {
  margin: 0;
  color: #111827;
  font-size: 15px;
  font-weight: 700;
}

.sync-view__live-pill {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  border-radius: 999px;
  padding: 6px 10px;
  background: #ecfdf5;
  color: #059669;
  font-size: 12px;
  font-weight: 700;
}

.sync-view__live-pill--active {
  background: #eef2ff;
  color: #4f46e5;
}

.sync-view__rows {
  display: grid;
  gap: 0;
}

.sync-view__row {
  display: grid;
  grid-template-columns: 24px minmax(0, 1fr) auto;
  align-items: center;
  gap: 12px;
  padding: 8px 14px;
  background: transparent;
  border-top: 1px solid rgba(226, 232, 240, 0.72);
}

.sync-view__row:first-child {
  border-top: 0;
}

.sync-view__row--running {
  background: #eef2ff;
  color: #4338ca;
}

.sync-view__row--failed {
  background: #fef2f2;
  color: #b91c1c;
}

.sync-view__row-icon {
  display: grid;
  place-items: center;
  color: inherit;
}

.sync-view__row-copy strong {
  display: block;
  margin: 0;
  color: #111827;
  font-size: 12px;
  font-weight: 700;
}

.sync-view__row-copy p {
  margin: 2px 0 0;
  color: #64748b;
  font-size: 11px;
}

.sync-view__row-meta {
  color: #64748b;
  font-size: 11px;
  font-weight: 700;
}
</style>
