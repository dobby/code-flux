<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import {
  BarChart3,
  LoaderCircle,
  Moon,
  PanelLeftClose,
  PanelLeftOpen,
  RefreshCcw,
  Settings2,
  Square,
  Sun,
} from 'lucide-vue-next'
import { getConfigFile, updateConfigFile } from './api/client'
import { useShellChrome } from './composables/useShellChrome'
import { useTheme } from './composables/useTheme'
import { useDashboardStore } from './stores/dashboard'

const store = useDashboardStore()
const { isDark, toggleTheme } = useTheme()
const { isElectron } = useShellChrome()
const route = useRoute()

const configDialog = ref<HTMLDialogElement | null>(null)
const configDialogOpen = ref(false)
const sidebarCollapsed = ref(false)
const configYaml = ref('')
const configPath = ref('')
const configLoading = ref(false)
const configSaving = ref(false)
const configError = ref<string | null>(null)
const configSaveNotice = ref<string | null>(null)

const lastSyncText = computed(() => {
  if (store.syncStatus?.running && store.syncStatus.lastRun?.finishedAt == null && store.syncStatus.lastRun?.startedAt) {
    return new Date(store.syncStatus.lastRun.startedAt).toLocaleString()
  }
  const raw = store.syncStatus?.lastRun?.finishedAt ?? store.bootstrap?.lastSuccessfulSyncAt
  if (!raw) {
    return 'No successful sync yet'
  }
  return new Date(raw).toLocaleString()
})

const lastSyncLabel = computed(() => (
  store.syncStatus?.running && store.syncStatus.lastRun?.finishedAt == null
    ? 'Current run started'
    : 'Last finished sync'
))

const lastRunStatusText = computed(() => store.syncStatus?.lastRun?.status ?? 'IDLE')
const themeToggleLabel = computed(() => (isDark.value ? 'Light mode' : 'Dark mode'))
const syncActionLabel = computed(() => {
  if (store.currentSync?.stopRequested) {
    return 'Stopping…'
  }
  if (store.syncStatus?.running) {
    return 'Stop Sync'
  }
  return store.loading.sync ? 'Starting…' : 'Run Sync'
})
const syncActionTone = computed(() => (store.syncStatus?.running ? 'button--danger' : 'button--primary'))
const configChartLibraryHint = computed(() => store.bootstrap?.uiDefaults.defaultChartLibrary ?? 'echarts')
const activeScreenLabel = computed(() => {
  if (route.name === 'dashboard') {
    return 'Dashboard'
  }

  const raw = typeof route.name === 'string' ? route.name : 'Code Flux'
  return raw.charAt(0).toUpperCase() + raw.slice(1)
})

watch(
  () => configDialogOpen.value,
  async (isOpen) => {
    await nextTick()
    const dialog = configDialog.value
    if (!dialog) {
      return
    }

    if (isOpen) {
      if (!dialog.open) {
        dialog.showModal()
      }
      return
    }

    if (dialog.open) {
      dialog.close()
    }
  },
)

function handleConfigDialogClose() {
  if (configDialogOpen.value) {
    configDialogOpen.value = false
  }
}

function closeConfigDialog() {
  configDialogOpen.value = false
}

function toggleSidebar() {
  sidebarCollapsed.value = !sidebarCollapsed.value
  window.localStorage.setItem('code-flux-sidebar-collapsed', String(sidebarCollapsed.value))
}

async function handleSyncAction() {
  if (store.syncStatus?.running) {
    await store.stopRunningSync()
    return
  }

  await store.triggerSync()
}

async function openConfigDialogPanel() {
  store.annotationDialogOpen = false
  configDialogOpen.value = true
  configSaveNotice.value = null
  await loadConfigFile()
}

async function loadConfigFile() {
  configLoading.value = true
  configError.value = null

  try {
    const response = await getConfigFile()
    configYaml.value = response.yaml
    configPath.value = response.path
  } catch (caught) {
    configError.value = toMessage(caught)
  } finally {
    configLoading.value = false
  }
}

async function saveConfig() {
  configSaving.value = true
  configError.value = null
  configSaveNotice.value = null

  try {
    const response = await updateConfigFile(configYaml.value)
    configPath.value = response.path
    configSaveNotice.value = response.restartRequired
      ? 'Config saved. Restart the dashboard to apply backend changes.'
      : 'Config saved.'
  } catch (caught) {
    configError.value = toMessage(caught)
  } finally {
    configSaving.value = false
  }
}

function toMessage(caught: unknown): string {
  return caught instanceof Error ? caught.message : 'Unexpected dashboard error'
}

onMounted(() => {
  sidebarCollapsed.value = window.localStorage.getItem('code-flux-sidebar-collapsed') === 'true'
  void store.initialize()
})
</script>

<template>
  <div
    class="app-shell"
    :class="{ 'app-shell--collapsed': sidebarCollapsed }"
  >
    <aside class="sidebar-surface" data-testid="app-sidebar">
      <div class="sidebar-chrome" aria-hidden="true" />
      <div class="sidebar-body" :class="{ 'app-sidebar--collapsed': sidebarCollapsed }">
        <div class="app-sidebar__top">
          <div class="app-sidebar__masthead">
            <RouterLink class="app-brand" to="/">
              <img alt="" class="app-brand__icon" src="/icons/icon-192.png" />
              <div class="app-brand__copy">
                <span class="app-brand__eyebrow">Flow telemetry</span>
                <strong>Code Flux</strong>
              </div>
            </RouterLink>
          </div>

          <section class="app-sidebar__intro">
            <p class="section-tag">Workspace</p>
            <strong class="app-sidebar__intro-title">{{ activeScreenLabel }}</strong>
            <p class="app-sidebar__intro-copy">
              Local telemetry shell for your development dataset, with sync and settings kept in the rail.
            </p>
          </section>

          <nav class="app-nav app-nav--primary" aria-label="Primary">
            <p class="app-sidebar__section-label">Views</p>
            <RouterLink class="app-nav__link" data-testid="sidebar-nav-dashboard" to="/">
              <BarChart3 :size="18" />
              <span class="app-nav__copy">
                <strong>Dashboard</strong>
                <small>Telemetry overview</small>
              </span>
            </RouterLink>
          </nav>

          <section class="app-sidebar__section">
            <div class="app-sidebar__section-head">
              <div class="app-sidebar__section-copy">
                <p class="section-tag">Sync</p>
                <strong class="app-sidebar__section-title">
                  {{ store.syncStatus?.running ? 'Sync in progress' : 'Ready to refresh' }}
                </strong>
              </div>
              <span class="sync-status-badge" :class="{ 'sync-status-badge--live': store.syncStatus?.running }">
                {{ store.syncStatus?.running ? 'Live' : lastRunStatusText }}
              </span>
            </div>

            <div class="app-sidebar__meta">
              <span class="app-sidebar__meta-label">{{ lastSyncLabel }}</span>
              <strong>{{ lastSyncText }}</strong>
              <p>
                <template v-if="store.currentSync && store.syncProgressLabel">{{ store.syncProgressLabel }}</template>
                <template v-else-if="store.syncStatus?.lastRun?.message">{{ store.syncStatus.lastRun.message }}</template>
                <template v-else>Use the local config from your dev setup and run sync on demand.</template>
              </p>
            </div>

            <div v-if="store.currentSync" class="app-sidebar__progress">
              <div class="sync-progress-card__head">
                <span>{{ store.currentSync.stage }}</span>
                <strong>{{ store.syncProgressPercent }}%</strong>
              </div>
              <div class="sync-progress-bar" aria-hidden="true">
                <div class="sync-progress-bar__fill" :style="{ width: `${store.syncProgressPercent}%` }" />
              </div>
              <p class="popover-note">
                {{ store.syncProgressLabel }}
                <template v-if="store.syncCurrentRepoLabel">
                  · Now at {{ store.syncCurrentRepoLabel }}
                </template>
              </p>
              <p v-if="store.currentSync.stopRequested" class="dialog__feedback dialog__feedback--error">
                Stop requested. The worker will stop after the current repository finishes.
              </p>
            </div>

            <button
              class="button app-sidebar__action"
              :class="syncActionTone"
              data-testid="sync-button"
              :disabled="store.currentSync?.stopRequested"
              type="button"
              @click="handleSyncAction"
            >
              <LoaderCircle v-if="store.currentSync?.stopRequested" class="spin" :size="16" />
              <Square v-else-if="store.syncStatus?.running" :size="15" />
              <RefreshCcw v-else :size="16" />
              <span>{{ syncActionLabel }}</span>
            </button>
          </section>
        </div>

        <div class="app-sidebar__bottom">
          <button class="button button--ghost app-nav__link app-nav__link--button" data-testid="theme-toggle" type="button" @click="toggleTheme()">
            <Sun v-if="isDark" :size="18" />
            <Moon v-else :size="18" />
            <span>{{ themeToggleLabel }}</span>
          </button>

          <button
            class="button button--ghost app-nav__link app-nav__link--button"
            data-testid="sidebar-settings-toggle"
            type="button"
            @click="openConfigDialogPanel"
          >
            <Settings2 :size="18" />
            <span>Settings</span>
          </button>
        </div>
      </div>
    </aside>

    <div class="toggle-anchor">
      <button
        class="sidebar-toggle"
        :aria-label="sidebarCollapsed ? 'Expand sidebar' : 'Collapse sidebar'"
        data-testid="sidebar-toggle"
        type="button"
        @click="toggleSidebar"
      >
        <PanelLeftClose v-if="!sidebarCollapsed" :size="16" />
        <PanelLeftOpen v-else :size="16" />
      </button>
    </div>

    <header class="main-chrome">
      <span class="main-chrome__title">{{ activeScreenLabel }}</span>
      <div class="main-chrome__spacer" />
    </header>

    <div class="main-body">
      <div class="main-body__content">
        <RouterView />
      </div>
    </div>

    <dialog
      ref="configDialog"
      class="dialog"
      data-testid="config-dialog"
      @cancel.prevent="closeConfigDialog"
      @close="handleConfigDialogClose"
    >
      <form class="dialog__card dialog__card--editor" method="dialog" @submit.prevent="saveConfig">
        <div class="panel__header">
          <div>
            <p class="section-tag">Config</p>
            <h2>Edit dashboard config</h2>
          </div>
          <button class="button button--ghost" type="button" @click="closeConfigDialog">Close</button>
        </div>

        <div class="dialog__meta">
          <span>File</span>
          <code>{{ configPath || 'Loading config path…' }}</code>
        </div>
        <p class="dialog__hint">
          This editor controls repositories, authors, UI defaults, and chart library. Set
          <code>uiDefaults.defaultChartLibrary</code>
          to <code>echarts</code> or <code>chartjs</code>. Current default:
          <strong>{{ configChartLibraryHint }}</strong>.
        </p>

        <div v-if="configLoading" class="state">Loading config…</div>
        <label v-else class="dialog__editor">
          Config YAML
          <textarea
            v-model="configYaml"
            class="config-editor"
            data-testid="config-editor"
            rows="22"
            spellcheck="false"
          />
        </label>

        <p v-if="configError" class="dialog__feedback dialog__feedback--error">{{ configError }}</p>
        <p v-else-if="configSaveNotice" class="dialog__feedback dialog__feedback--success">{{ configSaveNotice }}</p>

        <div class="dialog__actions">
          <button class="button button--ghost" :disabled="configLoading || configSaving" type="button" @click="loadConfigFile">Reload</button>
          <button class="button button--primary" :disabled="configLoading || configSaving" data-testid="config-save" type="submit">
            <LoaderCircle v-if="configSaving" class="spin" :size="16" />
            <span>{{ configSaving ? 'Saving…' : 'Save config' }}</span>
          </button>
        </div>
      </form>
    </dialog>
  </div>
</template>
