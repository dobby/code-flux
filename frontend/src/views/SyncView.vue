<script setup lang="ts">
import { computed } from 'vue'
import { LoaderCircle, RefreshCcw, Square } from 'lucide-vue-next'
import { useDashboardStore } from '../stores/dashboard'

const dashboard = useDashboardStore()

const lastSyncText = computed(() => {
  if (dashboard.syncStatus?.running && dashboard.syncStatus.lastRun?.finishedAt == null && dashboard.syncStatus.lastRun?.startedAt) {
    return new Date(dashboard.syncStatus.lastRun.startedAt).toLocaleString()
  }
  const raw = dashboard.syncStatus?.lastRun?.finishedAt ?? dashboard.bootstrap?.lastSuccessfulSyncAt
  return raw ? new Date(raw).toLocaleString() : 'No successful sync yet'
})

const lastSyncLabel = computed(() => (
  dashboard.syncStatus?.running && dashboard.syncStatus.lastRun?.finishedAt == null
    ? 'Current run started'
    : 'Last finished sync'
))

const syncActionLabel = computed(() => {
  if (dashboard.currentSync?.stopRequested) return 'Stopping…'
  if (dashboard.syncStatus?.running) return 'Stop sync'
  return dashboard.loading.sync ? 'Starting…' : 'Run sync'
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
  <div class="sync-view">
    <div class="sync-view__inner">
      <!-- Status header -->
      <div class="sync-view__status">
        <div class="sync-view__status-row">
          <span class="sync-view__label">{{ lastSyncLabel }}</span>
          <span
            class="sync-status-badge"
            :class="{ 'sync-status-badge--live': dashboard.syncStatus?.running }"
          >
            {{ dashboard.syncStatus?.running ? 'Live' : (dashboard.syncStatus?.lastRun?.status ?? 'Idle') }}
          </span>
        </div>
        <strong class="sync-view__time">{{ lastSyncText }}</strong>
        <p class="sync-view__message">
          <template v-if="dashboard.currentSync && dashboard.syncProgressLabel">
            {{ dashboard.syncProgressLabel }}
            <template v-if="dashboard.syncCurrentRepoLabel"> · Now at {{ dashboard.syncCurrentRepoLabel }}</template>
          </template>
          <template v-else-if="dashboard.syncStatus?.lastRun?.message">
            {{ dashboard.syncStatus.lastRun.message }}
          </template>
          <template v-else>
            Pull repository history before rebuilding pages and snapshots.
          </template>
        </p>
      </div>

      <!-- Progress bar (when running) -->
      <div v-if="dashboard.currentSync" class="sync-view__progress">
        <div class="sync-view__progress-head">
          <span>{{ dashboard.currentSync.stage }}</span>
          <strong>{{ dashboard.syncProgressPercent }}%</strong>
        </div>
        <div class="sync-progress-bar" aria-hidden="true">
          <div class="sync-progress-bar__fill" :style="{ width: `${dashboard.syncProgressPercent}%` }" />
        </div>
      </div>

      <!-- Action button -->
      <button
        class="button sync-view__action"
        :class="dashboard.syncStatus?.running ? 'button--danger' : 'button--primary'"
        data-testid="sync-button"
        :disabled="!!dashboard.currentSync?.stopRequested"
        type="button"
        @click="handleSyncAction"
      >
        <LoaderCircle v-if="dashboard.currentSync?.stopRequested" class="spin" :size="16" />
        <Square v-else-if="dashboard.syncStatus?.running" :size="15" />
        <RefreshCcw v-else :size="16" />
        <span>{{ syncActionLabel }}</span>
      </button>
    </div>
  </div>
</template>
