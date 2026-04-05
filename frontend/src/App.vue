<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  ChevronLeft,
  ChevronRight,
  FileText,
  LayoutGrid,
  LoaderCircle,
  MoreHorizontal,
  PanelLeftClose,
  PanelLeftOpen,
  Plus,
  RefreshCcw,
  Settings2,
} from 'lucide-vue-next'
import { useShellChrome } from './composables/useShellChrome'
import { useWorkspaceStore } from './stores/workspace'
import { useDashboardStore } from './stores/dashboard'
import DrilldownDrawer from './components/DrilldownDrawer.vue'

const route = useRoute()
const router = useRouter()
const workspace = useWorkspaceStore()
const dashboard = useDashboardStore()
useShellChrome()

const sidebarCollapsed = ref(false)
const expandedSidebarWidth = ref(280)

const currentPage = computed(() => (
  workspace.pages.find((page) => page.id === route.params.pageId) ?? null
))

const isSettingsRoute = computed(() => (
  route.name === 'settings-general' || route.name === 'settings-jira'
))

const shellStyle = computed(() => {
  if (sidebarCollapsed.value) return {}
  return { '--cf-sidebar-width': `${expandedSidebarWidth.value}px` }
})

const currentSectionLabel = computed(() => {
  switch (route.name) {
    case 'settings-general': return 'General'
    case 'settings-jira': return 'Jira'
    case 'sync': return 'Sync'
    case 'legacy-overview': return 'Legacy Overview'
    case 'widgets':
    case 'widget-new':
    case 'widget-edit': return 'Widget Catalog'
    default: return currentPage.value?.title ?? 'Code Flux'
  }
})

const syncProgressPercent = computed(() => dashboard.syncProgressPercent ?? 0)
const RING_C = 44
const syncRingOffset = computed(() => RING_C - (RING_C * syncProgressPercent.value / 100))

async function initialize() {
  await Promise.all([workspace.initialize(), dashboard.initialize()])
  await workspace.loadWidgetCatalog()
}

function toggleSidebar() {
  sidebarCollapsed.value = !sidebarCollapsed.value
  window.localStorage.setItem('code-flux-v2-sidebar-collapsed', String(sidebarCollapsed.value))
}

function beginSidebarResize(event: MouseEvent) {
  const startX = event.clientX
  const startWidth = expandedSidebarWidth.value

  const handleMove = (e: MouseEvent) => {
    expandedSidebarWidth.value = Math.max(220, Math.min(400, startWidth + (e.clientX - startX)))
  }
  const handleUp = () => {
    window.localStorage.setItem('code-flux-v2-sidebar-width', String(expandedSidebarWidth.value))
    window.removeEventListener('mousemove', handleMove)
    window.removeEventListener('mouseup', handleUp)
  }
  window.addEventListener('mousemove', handleMove)
  window.addEventListener('mouseup', handleUp, { once: true })
}

async function createPagePrompt() {
  const title = window.prompt('Page title')
  if (!title?.trim()) return
  await workspace.createPageAndRefresh({ title: title.trim() })
  const page = workspace.orderedPages.at(-1)
  if (page) await router.push({ name: 'page', params: { pageId: page.id } })
}

async function renamePagePrompt(pageId: string, currentTitle: string) {
  const title = window.prompt('Rename page', currentTitle)
  if (!title?.trim()) return
  await workspace.renamePage(pageId, title.trim())
}

async function archivePagePrompt(pageId: string) {
  if (!window.confirm('Archive this page?')) return
  await workspace.archivePageAndRefresh(pageId)
  if (route.name === 'page' && route.params.pageId === pageId) {
    const next = workspace.orderedPages[0]
    await router.push(next ? { name: 'page', params: { pageId: next.id } } : { name: 'widgets' })
  }
}

async function duplicatePageAndOpen(pageId: string) {
  const page = await workspace.duplicatePageAndRefresh(pageId)
  await router.push({ name: 'page', params: { pageId: page.id } })
}

function openPageMenu(pageId: string, pageTitle: string) {
  const action = window.prompt(`Page: ${pageTitle}\nType: rename, duplicate, archive`)
  if (action === 'rename') void renamePagePrompt(pageId, pageTitle)
  else if (action === 'duplicate') void duplicatePageAndOpen(pageId)
  else if (action === 'archive') void archivePagePrompt(pageId)
}

function navigateToSettings() {
  void router.push({ name: 'settings-general' })
}

onMounted(() => {
  sidebarCollapsed.value = window.localStorage.getItem('code-flux-v2-sidebar-collapsed') === 'true'
  const stored = Number(window.localStorage.getItem('code-flux-v2-sidebar-width') ?? '')
  if (Number.isFinite(stored) && stored >= 220 && stored <= 400) expandedSidebarWidth.value = stored
  void initialize()
})
</script>

<template>
  <div class="app-shell" :class="{ 'app-shell--collapsed': sidebarCollapsed }" :style="shellStyle">
    <!-- Sidebar (hidden when collapsed) -->
    <aside class="sidebar" data-testid="app-sidebar">
      <!-- Toolbar: TL gap + collapse + back + forward -->
      <div class="sidebar-toolbar" aria-hidden="true">
        <div class="sidebar-tl-gap" />
        <button
          class="sidebar-toolbar-btn"
          :aria-label="sidebarCollapsed ? 'Expand sidebar' : 'Collapse sidebar'"
          data-testid="sidebar-toggle"
          type="button"
          @click="toggleSidebar"
        >
          <PanelLeftClose :size="15" />
        </button>
        <button class="sidebar-toolbar-btn" aria-label="Go back" type="button" @click="router.back()">
          <ChevronLeft :size="15" />
        </button>
        <button class="sidebar-toolbar-btn" aria-label="Go forward" type="button" @click="router.forward()">
          <ChevronRight :size="15" />
        </button>
      </div>

      <!-- Scrollable content -->
      <div class="sidebar-content">
        <!-- Settings nav swap -->
        <template v-if="isSettingsRoute">
          <nav class="sidebar-menu">
            <button class="sidebar-item" type="button" @click="router.back()">
              <ChevronLeft class="sidebar-icon" :size="15" />
              <span class="sidebar-item__label">Back to app</span>
            </button>
          </nav>
          <div class="sidebar-section-row">
            <span class="sidebar-section-label">Settings</span>
          </div>
          <nav class="sidebar-menu">
            <RouterLink class="sidebar-item" :to="{ name: 'settings-general' }">
              <Settings2 class="sidebar-icon" :size="15" />
              <span class="sidebar-item__label">General</span>
            </RouterLink>
            <RouterLink class="sidebar-item" :to="{ name: 'settings-jira' }">
              <LayoutGrid class="sidebar-icon" :size="15" />
              <span class="sidebar-item__label">Jira</span>
            </RouterLink>
          </nav>
        </template>

        <!-- Primary nav -->
        <template v-else>
          <nav class="sidebar-menu">
            <RouterLink
              class="sidebar-item"
              :class="{ 'sidebar-item--active': route.name === 'sync' }"
              :to="{ name: 'sync' }"
              data-testid="nav-sync"
            >
              <span class="sidebar-icon">
                <svg v-if="dashboard.syncStatus?.running" class="sync-ring" width="15" height="15" viewBox="0 0 18 18">
                  <circle class="sync-ring__track" cx="9" cy="9" r="7" />
                  <circle class="sync-ring__fill" cx="9" cy="9" r="7" :style="{ strokeDashoffset: syncRingOffset }" />
                </svg>
                <RefreshCcw v-else :size="15" />
              </span>
              <span class="sidebar-item__label">Sync</span>
              <span v-if="dashboard.syncStatus?.running" class="sync-status-badge sync-status-badge--live">
                {{ syncProgressPercent }}%
              </span>
            </RouterLink>

            <RouterLink
              class="sidebar-item"
              :class="{ 'sidebar-item--active': route.name === 'widgets' || route.name === 'widget-new' || route.name === 'widget-edit' }"
              :to="{ name: 'widgets' }"
              data-testid="nav-widgets"
            >
              <LayoutGrid class="sidebar-icon" :size="15" />
              <span class="sidebar-item__label">Widget Catalog</span>
            </RouterLink>
          </nav>

          <!-- Pages section -->
          <div class="sidebar-section-row">
            <span class="sidebar-section-label">Pages</span>
            <button class="sidebar-section-btn" data-testid="sidebar-create-page" title="New page" type="button" @click="createPagePrompt">
              <Plus :size="12" />
            </button>
          </div>
          <div class="sidebar-pages">
            <RouterLink
              v-for="page in workspace.orderedPages"
              :key="page.id"
              class="sidebar-item"
              :class="{ 'sidebar-item--active': route.name === 'page' && route.params.pageId === page.id }"
              :data-testid="`sidebar-page-link-${page.id}`"
              :to="{ name: 'page', params: { pageId: page.id } }"
            >
              <FileText class="sidebar-icon" :size="14" />
              <span class="sidebar-item__label">{{ page.title }}</span>
              <button
                class="sidebar-item__more"
                :data-testid="`page-menu-${page.id}`"
                title="Page options"
                type="button"
                @click.prevent="openPageMenu(page.id, page.title)"
              >
                <MoreHorizontal :size="13" />
              </button>
            </RouterLink>
          </div>
        </template>
      </div>

      <!-- Bottom: Settings only -->
      <div class="sidebar-bottom">
        <nav class="sidebar-menu">
          <button
            class="sidebar-item"
            :class="{ 'sidebar-item--active': isSettingsRoute }"
            type="button"
            @click="navigateToSettings"
          >
            <Settings2 class="sidebar-icon" :size="15" />
            <span class="sidebar-item__label">Settings</span>
          </button>
        </nav>
      </div>

      <div class="sidebar-resize-handle" @mousedown.prevent="beginSidebarResize" />
    </aside>

    <!-- Floating content pane -->
    <main class="content-pane">
      <header class="content-chrome" data-testid="app-header">
        <!-- When sidebar is collapsed: TL gap + nav buttons appear left of title -->
        <template v-if="sidebarCollapsed">
          <div class="content-chrome__tl-gap" aria-hidden="true" />
          <button
            class="content-chrome__btn"
            aria-label="Expand sidebar"
            data-testid="sidebar-toggle"
            type="button"
            @click="toggleSidebar"
          >
            <PanelLeftOpen :size="15" />
          </button>
          <button class="content-chrome__btn" aria-label="Go back" type="button" @click="router.back()">
            <ChevronLeft :size="15" />
          </button>
          <button class="content-chrome__btn" aria-label="Go forward" type="button" @click="router.forward()">
            <ChevronRight :size="15" />
          </button>
        </template>
        <span class="content-chrome__title">{{ currentSectionLabel }}</span>
        <div class="content-chrome__spacer" />
        <div class="content-chrome__actions">
          <LoaderCircle v-if="dashboard.syncStatus?.running" class="spin" :size="14" style="color: var(--cf-accent)" />
        </div>
      </header>
      <div class="content-body">
        <RouterView />
      </div>
    </main>

    <DrilldownDrawer />
  </div>
</template>
