<script setup lang="ts">
import { computed } from 'vue'
import SettingsWorkspaceLayout from '../components/SettingsWorkspaceLayout.vue'
import { useDashboardStore } from '../stores/dashboard'
import { useWorkspaceStore } from '../stores/workspace'

const dashboard = useDashboardStore()
const workspace = useWorkspaceStore()

const repoCount = computed(() => dashboard.bootstrap?.repos.length ?? 0)
const pageCount = computed(() => workspace.pages.length)
const widgetCount = computed(() => workspace.widgets.length)
</script>

<template>
  <SettingsWorkspaceLayout title="Advanced" description="Inspect local runtime metadata and workspace counts for debugging and support.">
    <header class="surface-header surface-header--settings">
      <div>
        <p class="workspace-sidebar__eyebrow">Advanced settings</p>
        <h2>Advanced</h2>
        <p>Inspect local workspace metadata without digging through raw storage or API responses.</p>
      </div>
    </header>

    <div class="settings-sheet-grid">
      <section class="settings-sheet">
        <div class="settings-sheet__header">Workspace</div>
        <div class="settings-sheet__rows">
          <div class="settings-row">
            <div class="settings-row__copy">
              <strong>Repositories</strong>
              <p>Repositories currently exposed to the dashboard workspace.</p>
            </div>
            <div class="settings-stat">{{ repoCount }}</div>
          </div>

          <div class="settings-row">
            <div class="settings-row__copy">
              <strong>Pages</strong>
              <p>User-created dashboard pages stored in workspace state.</p>
            </div>
            <div class="settings-stat">{{ pageCount }}</div>
          </div>

          <div class="settings-row">
            <div class="settings-row__copy">
              <strong>Widgets</strong>
              <p>Registered widget definitions available in the local catalog.</p>
            </div>
            <div class="settings-stat">{{ widgetCount }}</div>
          </div>
        </div>
      </section>
    </div>
  </SettingsWorkspaceLayout>
</template>
