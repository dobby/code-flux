<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  BarChart3,
  CalendarDays,
  Check,
  ChevronLeft,
  ChevronRight,
  ChevronDown,
  FileText,
  GitBranch,
  Filter,
  LayoutGrid,
  LoaderCircle,
  MoreHorizontal,
  Eye,
  EyeOff,
  PanelLeftClose,
  PanelLeftOpen,
  Pencil,
  Palette,
  Plus,
  RefreshCw,
  Settings2,
  SlidersHorizontal,
  Tag,
  Users,
  X,
} from 'lucide-vue-next'
import { useShellChrome } from './composables/useShellChrome'
import { useWorkspaceStore } from './stores/workspace'
import { useDashboardStore } from './stores/dashboard'
import { useNavigationStore } from './stores/navigation'
import {
  ACTIVITY_CHART_STYLE_OPTIONS,
  ACTIVITY_COMPARE_PRESET_OPTIONS,
  ACTIVITY_EXTRA_FILTER_OPTIONS,
  ACTIVITY_GROUP_BY_OPTIONS,
  ACTIVITY_METRIC_OPTIONS,
  type ActivityComparePresetMode,
  type ActivityExtraFilterKind,
  type ActivityRangePreset,
  useActivityStore,
} from './stores/activity'
import {
  CONTRIBUTOR_METRIC_OPTIONS,
  type ContributorMetric,
  useContributorsStore,
} from './stores/contributors'
import DrilldownDrawer from './components/DrilldownDrawer.vue'
import WorkspacePageHeaderControls from './components/WorkspacePageHeaderControls.vue'

const route = useRoute()
const router = useRouter()
const workspace = useWorkspaceStore()
const dashboard = useDashboardStore()
const navigation = useNavigationStore()
const explorer = useActivityStore()
const contributors = useContributorsStore()
useShellChrome()

const sidebarCollapsed = ref(false)
const expandedSidebarWidth = ref(280)
const floatingControlPadding = 12
const floatingControlGap = 7
const floatingControlButtons = 3
const floatingControlSpacer = 16
const toolbarSafeGap = 18
const trafficLightsX = window.shellChrome?.macTrafficLights?.x ?? 16
const trafficLightsWidth = window.shellChrome?.macTrafficLights?.width ?? 72

const controlsSafeLeft = computed(() => (
  floatingControlPadding
  + trafficLightsX
  + trafficLightsWidth
  + floatingControlSpacer
  + (floatingControlGap * floatingControlButtons)
  + (28 * floatingControlButtons)
  + toolbarSafeGap
))

const contentSurfaceLeft = computed(() => (sidebarCollapsed.value ? 0 : expandedSidebarWidth.value))
const toolbarLeft = computed(() => Math.max(contentSurfaceLeft.value, controlsSafeLeft.value))

const currentPage = computed(() => (
  workspace.pages.find((page) => page.id === route.params.pageId) ?? null
))

const isPageRoute = computed(() => route.name === 'page')
const isExplorerRoute = computed(() => route.name === 'activity' || route.name === 'activity-commit')
const isContributorsRoute = computed(() => route.name === 'contributors')
const isCodebaseRoute = computed(() => route.name === 'codebase' || route.name === 'codebase-repo')
const usesSharedFilterToolbar = computed(() => isExplorerRoute.value || isContributorsRoute.value || isCodebaseRoute.value)
const isSettingsRoute = computed(() => (
  route.name === 'settings-general'
  || route.name === 'settings-jira'
  || route.name === 'settings-sync'
  || route.name === 'settings-appearance'
  || route.name === 'settings-advanced'
))
type ExplorerMenuStyle = { top: string; left?: string; right?: string }
type ExplorerOverflowPanel =
  | 'metric'
  | 'date'
  | 'repo'
  | 'add-filter'
  | 'compare'
  | 'group'
  | 'display'
  | `filter:${ActivityExtraFilterKind}`
  | null
const explorerTimeMenuOpen = ref(false)
const explorerRepoMenuOpen = ref(false)
const explorerMetricMenuOpen = ref(false)
const explorerCompareMenuOpen = ref(false)
const explorerGroupMenuOpen = ref(false)
const explorerDisplayMenuOpen = ref(false)
const explorerFilterMenuOpen = ref(false)
const explorerExtraFilterMenuKind = ref<ActivityExtraFilterKind | null>(null)
const explorerOverflowMenuOpen = ref(false)
const explorerOverflowPanel = ref<ExplorerOverflowPanel>(null)
const explorerTimeMenuStyle = ref<ExplorerMenuStyle>({ top: '0px', left: '0px' })
const explorerRepoMenuStyle = ref<ExplorerMenuStyle>({ top: '0px', left: '0px' })
const explorerMetricMenuStyle = ref<ExplorerMenuStyle>({ top: '0px', left: '0px' })
const explorerCompareMenuStyle = ref<ExplorerMenuStyle>({ top: '0px', left: '0px' })
const explorerGroupMenuStyle = ref<ExplorerMenuStyle>({ top: '0px', left: '0px' })
const explorerDisplayMenuStyle = ref<ExplorerMenuStyle>({ top: '0px', left: '0px' })
const explorerFilterMenuStyle = ref<ExplorerMenuStyle>({ top: '0px', left: '0px' })
const explorerExtraFilterMenuStyle = ref<ExplorerMenuStyle>({ top: '0px', left: '0px' })
const explorerOverflowMenuStyle = ref<ExplorerMenuStyle>({ top: '0px', right: '0px' })
const explorerCustomFromDraft = ref('')
const explorerCustomToDraft = ref('')
const explorerCompareAnchorDraft = ref('')
const explorerCompareFromDraft = ref('')
const explorerCompareToDraft = ref('')
const contentChromeRef = ref<HTMLElement | null>(null)
const contentChromeTitleRef = ref<HTMLElement | null>(null)
const contentChromeActionsRef = ref<HTMLElement | null>(null)
const explorerToolbarRef = ref<HTMLElement | null>(null)
const explorerToolbarMeasureRef = ref<HTMLElement | null>(null)
const overflowedToolbarKeys = ref<string[]>([])
let toolbarResizeObserver: ResizeObserver | null = null

function setMenuPosition(
  styleRef: typeof explorerTimeMenuStyle,
  event: MouseEvent,
  menuWidth: number,
  align: 'start' | 'end' = 'start',
) {
  if (!(event.currentTarget instanceof HTMLElement)) {
    return
  }
  const trigger = event.currentTarget
  const toolbar = trigger.closest('.content-chrome__explorer-toolbar')
  if (!(toolbar instanceof HTMLElement)) {
    return
  }
  const rect = trigger.getBoundingClientRect()
  const toolbarRect = toolbar.getBoundingClientRect()
  const rawLeft = align === 'end'
    ? rect.right - toolbarRect.left - menuWidth
    : rect.left - toolbarRect.left
  const maxLeft = Math.max(0, window.innerWidth - toolbarRect.left - menuWidth - 12)
  styleRef.value = {
    top: `${rect.bottom - toolbarRect.top + 8}px`,
    left: `${Math.max(0, Math.min(rawLeft, maxLeft))}px`,
    right: undefined,
  }
}

function setMenuRightAlignedPosition(
  styleRef: typeof explorerOverflowMenuStyle,
  event: MouseEvent,
) {
  if (!(event.currentTarget instanceof HTMLElement)) {
    return
  }
  const trigger = event.currentTarget
  const header = contentChromeRef.value
  if (!(header instanceof HTMLElement)) {
    return
  }
  const rect = trigger.getBoundingClientRect()
  const headerRect = header.getBoundingClientRect()
  styleRef.value = {
    top: `${rect.bottom - headerRect.top + 8}px`,
    left: undefined,
    right: `${Math.max(0, headerRect.right - rect.right)}px`,
  }
}

const explorerRangeOptions: Array<{ id: Exclude<ActivityRangePreset, 'custom'>; label: string }> = [
  { id: '7d', label: 'Last 7 days' },
  { id: '14d', label: 'Last 14 days' },
  { id: '30d', label: 'Last 30 days' },
  { id: '90d', label: 'Last 90 days' },
]

const shellStyle = computed(() => {
  return {
    '--cf-sidebar-width': `${expandedSidebarWidth.value}px`,
    '--cf-content-left': `${contentSurfaceLeft.value}px`,
    '--cf-toolbar-left': `${toolbarLeft.value}px`,
  }
})

const visibleExplorerFilterKinds = computed(() => (
  explorer.visibleExtraFilterKinds.filter((kind) => !overflowedToolbarKeys.value.includes(`chip-${kind}`))
))

const overflowExplorerFilterKinds = computed(() => (
  explorer.visibleExtraFilterKinds.filter((kind) => overflowedToolbarKeys.value.includes(`chip-${kind}`))
))

const showInlineDate = computed(() => usesSharedFilterToolbar.value && !overflowedToolbarKeys.value.includes('date'))
const showInlineRepo = computed(() => usesSharedFilterToolbar.value && !overflowedToolbarKeys.value.includes('repo'))
const showInlineAddFilter = computed(() => usesSharedFilterToolbar.value && !overflowedToolbarKeys.value.includes('add-filter'))
const usesMetricToolbarControl = computed(() => isExplorerRoute.value || isContributorsRoute.value)
const showInlineSecondaryControls = computed(() => isExplorerRoute.value && !overflowedToolbarKeys.value.includes('secondary'))
const showInlineMetric = computed(() => usesMetricToolbarControl.value && !overflowedToolbarKeys.value.includes('metric'))
const hasExplorerToolbarOverflow = computed(() => overflowedToolbarKeys.value.length > 0)
const toolbarMetricLabel = computed(() => (
  isContributorsRoute.value ? `Metric: ${contributors.metricLabel}` : explorer.metricLabel
))
const toolbarMetricOptions = computed(() => (
  isContributorsRoute.value ? CONTRIBUTOR_METRIC_OPTIONS : ACTIVITY_METRIC_OPTIONS
))
const compareToolbarLabel = computed(() => (
  explorer.compareEnabled ? explorer.compareModeLabel : 'Compare'
))

const currentSectionLabel = computed(() => {
  switch (route.name) {
    case 'settings-general':
    case 'settings-jira':
    case 'settings-sync':
    case 'settings-appearance':
    case 'settings-advanced':
      return 'Settings'
    case 'activity':
    case 'activity-commit': return 'Activity'
    case 'codebase': return 'Codebase'
    case 'codebase-repo': return 'Codebase'
    case 'contributors': return 'Contributors'
    case 'legacy-overview': return 'Legacy Overview'
    case 'widgets':
    case 'widget-new':
    case 'widget-edit': return 'Widget Catalog'
    case 'page': return currentPage.value?.title ?? 'Page'
    default: return currentPage.value?.title ?? 'Code Flux'
  }
})

const syncMenuOpen = ref(false)
function toggleSyncMenu() { syncMenuOpen.value = !syncMenuOpen.value }
function closeSyncMenu() { syncMenuOpen.value = false }
async function syncMenuReset() {
  closeSyncMenu()
  const ok = window.confirm('Reset all synced throughput data? This clears ingested commits, files, aggregates, and sync history so you can resync from scratch.')
  if (!ok) return
  await dashboard.resetSyncedData()
}
async function syncMenuStop() {
  closeSyncMenu()
  await dashboard.stopRunningSync()
}

async function initialize() {
  await Promise.all([workspace.initialize(), dashboard.initialize()])
  await workspace.loadWidgetCatalog()
}

async function goBackToApp() {
  await router.push(navigation.backToAppTarget)
}

function toggleSidebar() {
  sidebarCollapsed.value = !sidebarCollapsed.value
  window.localStorage.setItem('code-flux-v2-sidebar-collapsed', String(sidebarCollapsed.value))
}

function beginSidebarResize(event: MouseEvent) {
  if (sidebarCollapsed.value) {
    return
  }
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

function closeExplorerMenus() {
  explorerTimeMenuOpen.value = false
  explorerRepoMenuOpen.value = false
  explorerMetricMenuOpen.value = false
  explorerCompareMenuOpen.value = false
  explorerGroupMenuOpen.value = false
  explorerDisplayMenuOpen.value = false
  explorerFilterMenuOpen.value = false
  explorerExtraFilterMenuKind.value = null
  explorerOverflowMenuOpen.value = false
  explorerOverflowPanel.value = null
}

function toggleExplorerMetricMenu(event: MouseEvent) {
  setMenuPosition(explorerMetricMenuStyle, event, isContributorsRoute.value ? 220 : 210)
  explorerMetricMenuOpen.value = !explorerMetricMenuOpen.value
  explorerTimeMenuOpen.value = false
  explorerRepoMenuOpen.value = false
  explorerGroupMenuOpen.value = false
  explorerDisplayMenuOpen.value = false
  explorerFilterMenuOpen.value = false
  explorerExtraFilterMenuKind.value = null
  explorerCompareMenuOpen.value = false
  explorerOverflowMenuOpen.value = false
}

function selectToolbarMetric(nextMetric: string) {
  if (isContributorsRoute.value) {
    contributors.setMetric(nextMetric as ContributorMetric)
  } else {
    explorer.setMetric(nextMetric as Parameters<typeof explorer.setMetric>[0])
  }
  closeExplorerMenus()
}

function syncExplorerCustomRangeDraft() {
  const sourceRange = explorer.rangePreset === 'custom'
    ? explorer.customDateRange
    : explorer.effectiveDateRange
  explorerCustomFromDraft.value = sourceRange.from
  explorerCustomToDraft.value = sourceRange.to
}

function syncExplorerCompareDraft() {
  explorerCompareAnchorDraft.value = explorer.compareAnchorDate
  explorerCompareFromDraft.value = explorer.compareCustomRange?.from ?? ''
  explorerCompareToDraft.value = explorer.compareCustomRange?.to ?? ''
}

function daysInclusive(from: string, to: string) {
  const fromDate = new Date(`${from}T00:00:00`)
  const toDate = new Date(`${to}T00:00:00`)
  return Math.max(1, Math.round((toDate.getTime() - fromDate.getTime()) / 86_400_000) + 1)
}

const compareAnchorDraftValid = computed(() => (
  Boolean(
    explorerCompareAnchorDraft.value
    && explorerCompareAnchorDraft.value < explorer.effectiveDateRange.from,
  )
))

const compareRangeDraftValid = computed(() => (
  Boolean(
    explorerCompareFromDraft.value
    && explorerCompareToDraft.value
    && explorerCompareFromDraft.value <= explorerCompareToDraft.value
    && explorerCompareToDraft.value < explorer.effectiveDateRange.from
    && daysInclusive(explorerCompareFromDraft.value, explorerCompareToDraft.value) === explorer.rangeDays,
  )
))

function toggleExplorerTimeMenu(event: MouseEvent) {
  setMenuPosition(explorerTimeMenuStyle, event, 248)
  const nextOpen = !explorerTimeMenuOpen.value
  explorerTimeMenuOpen.value = nextOpen
  if (nextOpen) {
    syncExplorerCustomRangeDraft()
  }
  explorerRepoMenuOpen.value = false
  explorerMetricMenuOpen.value = false
  explorerCompareMenuOpen.value = false
  explorerGroupMenuOpen.value = false
  explorerDisplayMenuOpen.value = false
  explorerFilterMenuOpen.value = false
  explorerExtraFilterMenuKind.value = null
  explorerOverflowMenuOpen.value = false
}

function toggleExplorerRepoMenu(event: MouseEvent) {
  setMenuPosition(explorerRepoMenuStyle, event, 248)
  explorerRepoMenuOpen.value = !explorerRepoMenuOpen.value
  explorerTimeMenuOpen.value = false
  explorerMetricMenuOpen.value = false
  explorerCompareMenuOpen.value = false
  explorerGroupMenuOpen.value = false
  explorerDisplayMenuOpen.value = false
  explorerFilterMenuOpen.value = false
  explorerExtraFilterMenuKind.value = null
  explorerOverflowMenuOpen.value = false
}

function clearRepoFiltersForCurrentRoute() {
  if (isCodebaseRoute.value) {
    const fallbackRepoId = (dashboard.bootstrap?.repos ?? []).find((repo) => repo.enabled)?.id ?? null
    explorer.setRepoFilters(fallbackRepoId ? [fallbackRepoId] : [])
    closeExplorerMenus()
    return
  }

  explorer.setRepoFilters([])
  closeExplorerMenus()
}

function selectRepoFilterForCurrentRoute(repoId: string) {
  if (isCodebaseRoute.value) {
    explorer.setRepoFilters([repoId])
    closeExplorerMenus()
    return
  }

  explorer.toggleRepoFilter(repoId)
}

function toggleExplorerGroupMenu(event: MouseEvent) {
  setMenuPosition(explorerGroupMenuStyle, event, 210)
  explorerGroupMenuOpen.value = !explorerGroupMenuOpen.value
  explorerTimeMenuOpen.value = false
  explorerRepoMenuOpen.value = false
  explorerMetricMenuOpen.value = false
  explorerCompareMenuOpen.value = false
  explorerDisplayMenuOpen.value = false
  explorerFilterMenuOpen.value = false
  explorerExtraFilterMenuKind.value = null
  explorerOverflowMenuOpen.value = false
}

function toggleExplorerDisplayMenu(event: MouseEvent) {
  setMenuPosition(explorerDisplayMenuStyle, event, 210)
  explorerDisplayMenuOpen.value = !explorerDisplayMenuOpen.value
  explorerTimeMenuOpen.value = false
  explorerRepoMenuOpen.value = false
  explorerMetricMenuOpen.value = false
  explorerCompareMenuOpen.value = false
  explorerGroupMenuOpen.value = false
  explorerFilterMenuOpen.value = false
  explorerExtraFilterMenuKind.value = null
  explorerOverflowMenuOpen.value = false
}

function toggleExplorerFilterMenu(event: MouseEvent) {
  setMenuPosition(explorerFilterMenuStyle, event, 180, 'start')
  explorerFilterMenuOpen.value = !explorerFilterMenuOpen.value
  explorerTimeMenuOpen.value = false
  explorerRepoMenuOpen.value = false
  explorerMetricMenuOpen.value = false
  explorerCompareMenuOpen.value = false
  explorerGroupMenuOpen.value = false
  explorerDisplayMenuOpen.value = false
  explorerExtraFilterMenuKind.value = null
  explorerOverflowMenuOpen.value = false
}

function toggleExplorerExtraFilterMenu(kind: ActivityExtraFilterKind, event: MouseEvent) {
  setMenuPosition(explorerExtraFilterMenuStyle, event, 240)
  explorerExtraFilterMenuKind.value = explorerExtraFilterMenuKind.value === kind ? null : kind
  explorerTimeMenuOpen.value = false
  explorerRepoMenuOpen.value = false
  explorerMetricMenuOpen.value = false
  explorerCompareMenuOpen.value = false
  explorerGroupMenuOpen.value = false
  explorerDisplayMenuOpen.value = false
  explorerFilterMenuOpen.value = false
  explorerOverflowMenuOpen.value = false
}

function toggleExplorerOverflowMenu(event: MouseEvent) {
  setMenuRightAlignedPosition(explorerOverflowMenuStyle, event)
  const nextOpen = !explorerOverflowMenuOpen.value
  explorerOverflowMenuOpen.value = nextOpen
  explorerTimeMenuOpen.value = false
  explorerRepoMenuOpen.value = false
  explorerMetricMenuOpen.value = false
  explorerCompareMenuOpen.value = false
  explorerGroupMenuOpen.value = false
  explorerDisplayMenuOpen.value = false
  explorerFilterMenuOpen.value = false
  explorerExtraFilterMenuKind.value = null
  explorerOverflowPanel.value = nextOpen ? explorerOverflowPanel.value : null
}

function toggleExplorerCompareMenu(event: MouseEvent) {
  setMenuPosition(explorerCompareMenuStyle, event, 264)
  const nextOpen = !explorerCompareMenuOpen.value
  explorerCompareMenuOpen.value = nextOpen
  if (nextOpen) {
    syncExplorerCompareDraft()
  }
  explorerTimeMenuOpen.value = false
  explorerRepoMenuOpen.value = false
  explorerMetricMenuOpen.value = false
  explorerGroupMenuOpen.value = false
  explorerDisplayMenuOpen.value = false
  explorerFilterMenuOpen.value = false
  explorerExtraFilterMenuKind.value = null
  explorerOverflowMenuOpen.value = false
}

function applyExplorerComparePreset(mode: Exclude<ActivityComparePresetMode, 'off' | 'custom_anchor_date' | 'custom_range'>) {
  explorer.setComparePreset(mode)
  closeExplorerMenus()
}

function disableExplorerCompare() {
  explorer.disableCompare()
  closeExplorerMenus()
}

function applyExplorerCompareAnchor() {
  if (explorer.setCompareAnchor(explorerCompareAnchorDraft.value)) {
    closeExplorerMenus()
  }
}

function applyExplorerCompareCustomRange() {
  if (explorer.setCompareCustomRange({
    from: explorerCompareFromDraft.value,
    to: explorerCompareToDraft.value,
  })) {
    closeExplorerMenus()
  }
}

function addExplorerExtraFilter(kind: ActivityExtraFilterKind) {
  explorer.addExtraFilter(kind)
  explorerFilterMenuOpen.value = false
  explorerOverflowMenuOpen.value = false
}

function toggleExplorerOverflowPanel(panel: Exclude<ExplorerOverflowPanel, null>) {
  const nextPanel = explorerOverflowPanel.value === panel ? null : panel
  explorerOverflowPanel.value = nextPanel
  if (nextPanel === 'date') {
    syncExplorerCustomRangeDraft()
  }
  if (nextPanel === 'compare') {
    syncExplorerCompareDraft()
  }
}

function isExplorerOverflowPanelOpen(panel: Exclude<ExplorerOverflowPanel, null>) {
  return explorerOverflowPanel.value === panel
}

function addExplorerExtraFilterInOverflow(kind: ActivityExtraFilterKind) {
  explorer.addExtraFilter(kind)
  explorerOverflowPanel.value = `filter:${kind}`
}

function removeExplorerExtraFilter(kind: ActivityExtraFilterKind) {
  explorer.removeExtraFilter(kind)
  if (explorerExtraFilterMenuKind.value === kind) {
    explorerExtraFilterMenuKind.value = null
  }
}

function clearExplorerExtraFilterSelection(kind: ActivityExtraFilterKind) {
  switch (kind) {
    case 'author':
      explorer.setAuthorFilters([])
      break
    case 'language':
      explorer.setLanguageFilters([])
      break
    case 'category':
      explorer.setCategoryFilters([])
      break
    case 'product':
      explorer.setProductCodeFilters([])
      break
  }
}

function extraFilterChipLabel(kind: ActivityExtraFilterKind) {
  switch (kind) {
    case 'author':
      if (explorer.selectedAuthorIds.length === 1) {
        return `Author: ${dashboard.bootstrap?.authors.find((author) => author.id === explorer.selectedAuthorIds[0])?.displayName ?? explorer.selectedAuthorIds[0]}`
      }
      return explorer.selectedAuthorIds.length > 1 ? `Authors: ${explorer.selectedAuthorIds.length}` : 'Author'
    case 'language':
      if (explorer.selectedLanguages.length === 1) {
        return `Language: ${explorer.selectedLanguages[0]}`
      }
      return explorer.selectedLanguages.length > 1 ? `Languages: ${explorer.selectedLanguages.length}` : 'Language'
    case 'category':
      if (explorer.selectedCategories.length === 1) {
        return `Category: ${explorer.selectedCategories[0]}`
      }
      return explorer.selectedCategories.length > 1 ? `Categories: ${explorer.selectedCategories.length}` : 'Category'
    case 'product':
      if (explorer.selectedProductCodes.length === 1) {
        return `Product: ${explorer.selectedProductCodes[0]}`
      }
      return explorer.selectedProductCodes.length > 1 ? `Products: ${explorer.selectedProductCodes.length}` : 'Product code'
  }
}

function extraFilterSelectionCount(kind: ActivityExtraFilterKind) {
  switch (kind) {
    case 'author':
      return explorer.selectedAuthorIds.length
    case 'language':
      return explorer.selectedLanguages.length
    case 'category':
      return explorer.selectedCategories.length
    case 'product':
      return explorer.selectedProductCodes.length
  }
}

function extraFilterMenuLabel(kind: ActivityExtraFilterKind) {
  return ACTIVITY_EXTRA_FILTER_OPTIONS.find((option) => option.value === kind)?.label ?? 'Filter'
}

function extraFilterMenuOptions(kind: ActivityExtraFilterKind) {
  switch (kind) {
    case 'author':
      return (dashboard.bootstrap?.authors ?? []).map((author) => ({
        value: author.id,
        label: author.displayName,
      }))
    case 'language':
      return (dashboard.options?.languages ?? []).map((language) => ({
        value: language,
        label: language,
      }))
    case 'category':
      return (dashboard.options?.categories ?? []).map((category) => ({
        value: category,
        label: category,
      }))
    case 'product':
      return (dashboard.options?.productCodes ?? []).map((productCode) => ({
        value: productCode,
        label: productCode,
      }))
  }
}

function isExtraFilterOptionSelected(kind: ActivityExtraFilterKind, value: string) {
  switch (kind) {
    case 'author':
      return explorer.selectedAuthorIds.includes(value)
    case 'language':
      return explorer.selectedLanguages.includes(value)
    case 'category':
      return explorer.selectedCategories.includes(value)
    case 'product':
      return explorer.selectedProductCodes.includes(value)
  }
}

function toggleExtraFilterOption(kind: ActivityExtraFilterKind, value: string) {
  switch (kind) {
    case 'author':
      explorer.toggleAuthorFilter(value)
      break
    case 'language':
      explorer.toggleLanguageFilter(value)
      break
    case 'category':
      explorer.toggleCategoryFilter(value)
      break
    case 'product':
      explorer.toggleProductCodeFilter(value)
      break
  }
}

function updateToolbarOverflow() {
  const header = contentChromeRef.value
  const title = contentChromeTitleRef.value
  const actions = contentChromeActionsRef.value
  const toolbar = explorerToolbarRef.value
  const measure = explorerToolbarMeasureRef.value
  const overflowButtonWidth = 34
  if (!header || !title || !actions || !toolbar || !measure || !usesSharedFilterToolbar.value) {
    overflowedToolbarKeys.value = []
    return
  }

  const available = header.clientWidth - title.offsetWidth - actions.offsetWidth - 88 - overflowButtonWidth
  if (available <= 0) {
    overflowedToolbarKeys.value = [
      ...(usesMetricToolbarControl.value ? ['metric'] : []),
      'date',
      ...(usesSharedFilterToolbar.value ? ['repo'] : []),
      ...(isExplorerRoute.value ? ['secondary'] : []),
      ...explorer.visibleExtraFilterKinds.map((kind) => `chip-${kind}`),
      'add-filter',
    ]
    return
  }

  const widthFor = (key: string) => {
    const element = measure.querySelector<HTMLElement>(`[data-measure-key="${key}"]`)
    return element ? Math.ceil(element.getBoundingClientRect().width) : 0
  }

  const baseKeys: string[] = []
  const optionalKeys = [
    ...(usesMetricToolbarControl.value ? ['metric'] : []),
    'date',
    ...(usesSharedFilterToolbar.value ? ['repo'] : []),
    ...(isExplorerRoute.value ? ['secondary'] : []),
    ...explorer.visibleExtraFilterKinds.map((kind) => `chip-${kind}`),
    'add-filter',
  ]
  let used = baseKeys.reduce((sum, key) => sum + widthFor(key), 0)
  const nextOverflowed: string[] = []

  for (const key of optionalKeys) {
    const keyWidth = widthFor(key)
    if (used + keyWidth <= available) {
      used += keyWidth
    } else {
      nextOverflowed.push(key)
    }
  }

  overflowedToolbarKeys.value = nextOverflowed
}

function applyExplorerRangePreset(preset: Exclude<ActivityRangePreset, 'custom'>) {
  explorer.setRangePreset(preset)
  closeExplorerMenus()
}

function applyExplorerCustomRange() {
  const applied = explorer.setCustomDateRange({
    from: explorerCustomFromDraft.value,
    to: explorerCustomToDraft.value,
  })
  if (applied) {
    closeExplorerMenus()
  }
}

function handleDocumentPointer(event: MouseEvent) {
  const target = event.target
  if (!(target instanceof Element)) return
  if (
    !target.closest('.content-chrome__explorer-toolbar')
    && !target.closest('.content-chrome__explorer-menu')
    && !target.closest('.content-chrome__ghost--overflow')
  ) {
    closeExplorerMenus()
  }
  if (
    !target.closest('.content-chrome__explorer-menu--sync')
    && !target.closest('[data-testid="sync-more-button"]')
  ) {
    closeSyncMenu()
  }
}

function handleDocumentKeydown(event: KeyboardEvent) {
  if (event.key === 'Escape') {
    closeExplorerMenus()
  }
}

onMounted(() => {
  sidebarCollapsed.value = window.localStorage.getItem('code-flux-v2-sidebar-collapsed') === 'true'
  const stored = Number(window.localStorage.getItem('code-flux-v2-sidebar-width') ?? '')
  if (Number.isFinite(stored) && stored >= 220 && stored <= 400) expandedSidebarWidth.value = stored
  void initialize()
  document.addEventListener('click', handleDocumentPointer)
  document.addEventListener('keydown', handleDocumentKeydown)
  toolbarResizeObserver = new ResizeObserver(() => {
    updateToolbarOverflow()
  })
  if (contentChromeRef.value) {
    toolbarResizeObserver.observe(contentChromeRef.value)
  }
  if (explorerToolbarRef.value) {
    toolbarResizeObserver.observe(explorerToolbarRef.value)
  }
  void nextTick(() => {
    updateToolbarOverflow()
  })
})

watch(usesSharedFilterToolbar, (isRoute) => {
  if (!isRoute) {
    closeExplorerMenus()
  }
})

watch(
  () => route.name,
  () => {
    navigation.syncRoute(route)
    closeExplorerMenus()
    void nextTick(() => {
      updateToolbarOverflow()
    })
  },
  { immediate: true },
)

watch(
  () => [
    sidebarCollapsed.value,
    expandedSidebarWidth.value,
    explorer.metricLabel,
    explorer.timeLabel,
    explorer.repoLabel,
    explorer.groupByLabel,
    explorer.chartStyleLabel,
    explorer.compareModeLabel,
    explorer.compareReferenceLabel ?? '',
    explorer.visibleExtraFilterKinds.join(','),
    explorer.selectedAuthorIds.join(','),
    explorer.selectedLanguages.join(','),
    explorer.selectedCategories.join(','),
    explorer.selectedProductCodes.join(','),
  ] as const,
  () => {
    void nextTick(() => {
      updateToolbarOverflow()
    })
  },
)

onBeforeUnmount(() => {
  document.removeEventListener('click', handleDocumentPointer)
  document.removeEventListener('keydown', handleDocumentKeydown)
  toolbarResizeObserver?.disconnect()
})
</script>

<template>
  <div
    class="app-shell"
    :class="{
      'app-shell--collapsed': sidebarCollapsed,
      'app-shell--settings': isSettingsRoute,
    }"
    :style="shellStyle"
  >
    <div class="sidebar-background" data-testid="app-sidebar-background" aria-hidden="true" />

    <aside class="sidebar" data-testid="app-sidebar">
      <!-- Scrollable content -->
      <div class="sidebar-content">
        <!-- Settings nav swap -->
        <template v-if="isSettingsRoute">
          <nav class="sidebar-menu">
            <button class="sidebar-item" type="button" @click="goBackToApp">
              <ChevronLeft class="sidebar-icon" :size="15" />
              <span class="sidebar-item__label">Back to app</span>
            </button>
          </nav>
          <nav class="sidebar-menu">
            <RouterLink class="sidebar-item" :to="{ name: 'settings-general' }" data-testid="settings-nav-general">
              <Settings2 class="sidebar-icon" :size="15" />
              <span class="sidebar-item__label">General</span>
            </RouterLink>
            <RouterLink class="sidebar-item" :to="{ name: 'settings-appearance' }" data-testid="settings-nav-appearance">
              <Palette class="sidebar-icon" :size="15" />
              <span class="sidebar-item__label">Appearance</span>
            </RouterLink>
            <RouterLink class="sidebar-item" :to="{ name: 'settings-jira' }" data-testid="settings-nav-jira">
              <svg class="sidebar-icon" width="15" height="15" viewBox="0 0 640 640" aria-hidden="true">
                <path
                  fill="currentColor"
                  d="M562.5 305.7C489.6 233 393.1 135.8 321 64C155.5 228.9 78.5 305.7 78.5 305.7C70.6 313.6 70.6 326.4 78.5 334.4C211.3 466.7 140.3 395.9 321 576C700.4 198 336.7 559.3 562.5 334.3C570.5 326.4 570.5 313.6 562.5 305.7zM321 395.7L245 320L321 244.3L397 320L321 395.7z"
                />
              </svg>
              <span class="sidebar-item__label">Jira</span>
            </RouterLink>
            <RouterLink class="sidebar-item" :to="{ name: 'settings-sync' }" data-testid="settings-nav-sync">
              <RefreshCw
                class="sidebar-icon"
                :class="{ 'sidebar-icon--sync-active': dashboard.syncStatus?.running }"
                :size="15"
              />
              <span class="sidebar-item__label">Sync</span>
            </RouterLink>
          </nav>
          <nav class="sidebar-menu sidebar-menu--secondary">
            <RouterLink class="sidebar-item" :to="{ name: 'settings-advanced' }" data-testid="settings-nav-advanced">
              <SlidersHorizontal class="sidebar-icon" :size="15" />
              <span class="sidebar-item__label">Advanced</span>
            </RouterLink>
          </nav>
        </template>

        <!-- Primary nav -->
        <template v-else>
          <nav class="sidebar-menu">
            <RouterLink
              class="sidebar-item"
              :class="{ 'sidebar-item--active': route.name === 'activity' || route.name === 'activity-commit' }"
              :to="{ name: 'activity' }"
              data-testid="nav-activity"
            >
              <BarChart3 class="sidebar-icon" :size="15" />
              <span class="sidebar-item__label">Activity</span>
            </RouterLink>

            <RouterLink
              class="sidebar-item"
              :class="{ 'sidebar-item--active': isCodebaseRoute }"
              :to="{ name: 'codebase' }"
              data-testid="nav-codebase"
            >
              <GitBranch class="sidebar-icon" :size="15" />
              <span class="sidebar-item__label">Codebase</span>
            </RouterLink>

            <RouterLink
              class="sidebar-item"
              :class="{ 'sidebar-item--active': route.name === 'contributors' }"
              :to="{ name: 'contributors' }"
              data-testid="nav-contributors"
            >
              <Users class="sidebar-icon" :size="15" />
              <span class="sidebar-item__label">Contributors</span>
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
      <div v-if="!isSettingsRoute" class="sidebar-bottom">
        <nav class="sidebar-menu">
          <button
            class="sidebar-item"
            :class="{ 'sidebar-item--active': isSettingsRoute }"
            data-testid="nav-settings"
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

    <main class="content-pane" data-testid="app-content-surface">
      <div class="content-body">
        <RouterView />
      </div>
    </main>

    <header ref="contentChromeRef" class="content-chrome" data-testid="app-header">
      <span
        ref="contentChromeTitleRef"
        class="content-chrome__title"
        :data-testid="route.name === 'page' ? 'page-toolbar-title' : undefined"
      >
        {{ currentSectionLabel }}
      </span>
      <WorkspacePageHeaderControls v-if="isPageRoute && currentPage" :page-id="currentPage.id" />
      <template v-if="usesSharedFilterToolbar">
        <div ref="explorerToolbarRef" class="content-chrome__explorer-toolbar">
          <div class="content-chrome__toolbar-group">
            <div v-if="showInlineDate" class="content-chrome__toolbar-item" data-toolbar-key="date">
            <button
              class="content-chrome__pill"
              type="button"
              aria-haspopup="menu"
              :aria-expanded="explorerTimeMenuOpen"
              aria-controls="explorer-time-menu"
              @click="toggleExplorerTimeMenu"
            >
              <CalendarDays :size="13" />
              <span>{{ explorer.timeLabel }}</span>
              <ChevronDown :size="13" />
            </button>
            </div>
            <div v-if="showInlineMetric" class="content-chrome__toolbar-item" data-toolbar-key="metric">
            <button
              class="content-chrome__pill"
              :class="{ 'content-chrome__pill--metric-accent': isContributorsRoute }"
              type="button"
              aria-haspopup="menu"
              :aria-expanded="explorerMetricMenuOpen"
              @click="toggleExplorerMetricMenu"
            >
              <BarChart3 v-if="!isContributorsRoute" :size="13" />
              <span>{{ toolbarMetricLabel }}</span>
              <ChevronDown :size="13" />
            </button>
            </div>
            <div v-if="showInlineRepo" class="content-chrome__toolbar-item" data-toolbar-key="repo">
            <button
              class="content-chrome__pill"
              type="button"
              aria-haspopup="menu"
              :aria-expanded="explorerRepoMenuOpen"
              aria-controls="explorer-repo-menu"
              @click="toggleExplorerRepoMenu"
            >
              <LayoutGrid :size="13" />
              <span>{{ explorer.repoLabel }}</span>
              <ChevronDown :size="13" />
            </button>
            </div>
            <div
              v-for="kind in visibleExplorerFilterKinds"
              :key="kind"
              class="content-chrome__filter-chip content-chrome__toolbar-item"
              :data-toolbar-key="`chip-${kind}`"
            >
              <div class="content-chrome__filter-chip-badge">
                <button
                  class="content-chrome__pill content-chrome__pill--filter"
                  type="button"
                  aria-haspopup="menu"
                  :aria-expanded="explorerExtraFilterMenuKind === kind"
                  @click="toggleExplorerExtraFilterMenu(kind, $event)"
                >
                  <span>{{ extraFilterChipLabel(kind) }}</span>
                  <ChevronDown :size="13" />
                </button>
                <button
                  v-if="!isCodebaseRoute"
                  class="content-chrome__chip-remove content-chrome__chip-remove--inline"
                  :aria-label="`Remove ${extraFilterMenuLabel(kind)} filter`"
                  type="button"
                  @click.stop="removeExplorerExtraFilter(kind)"
                >
                  <X :size="11" />
                </button>
              </div>
            </div>
            <div v-if="showInlineAddFilter" class="content-chrome__toolbar-item" data-toolbar-key="add-filter">
            <button
              class="content-chrome__ghost"
              type="button"
              aria-haspopup="menu"
              :aria-expanded="explorerFilterMenuOpen"
              aria-controls="explorer-filter-menu"
              @click="toggleExplorerFilterMenu"
            >
              <Plus :size="12" />
              <span>Filter</span>
            </button>
            </div>
          </div>
          <div
            v-if="showInlineSecondaryControls"
            class="content-chrome__toolbar-item content-chrome__toolbar-item--secondary"
            data-toolbar-key="secondary"
          >
            <div class="content-chrome__toolbar-divider" aria-hidden="true" />
            <div class="content-chrome__toolbar-group content-chrome__toolbar-group--secondary">
              <button
                class="content-chrome__pill"
                type="button"
                aria-haspopup="menu"
                :aria-expanded="explorerGroupMenuOpen"
                aria-controls="explorer-group-menu"
                @click="toggleExplorerGroupMenu"
              >
                <GitBranch :size="13" />
                <span>{{ `Group by: ${explorer.groupByLabel}` }}</span>
                <ChevronDown :size="13" />
              </button>
              <button
                class="content-chrome__pill"
                type="button"
                aria-haspopup="menu"
                :aria-expanded="explorerCompareMenuOpen"
                @click="toggleExplorerCompareMenu"
              >
                <SlidersHorizontal :size="13" />
                <span>{{ compareToolbarLabel }}</span>
                <ChevronDown :size="13" />
              </button>
              <button
                class="content-chrome__pill"
                type="button"
                aria-haspopup="menu"
                :aria-expanded="explorerDisplayMenuOpen"
                aria-controls="explorer-display-menu"
                @click="toggleExplorerDisplayMenu"
              >
                <BarChart3 :size="13" />
                <span>Display</span>
                <ChevronDown :size="13" />
              </button>
            </div>
          </div>
          <div ref="explorerToolbarMeasureRef" class="content-chrome__explorer-toolbar-measure" aria-hidden="true">
            <div v-if="usesMetricToolbarControl" class="content-chrome__toolbar-item" data-measure-key="metric">
              <div class="content-chrome__pill">
                <BarChart3 v-if="!isContributorsRoute" :size="13" />
                <span>{{ toolbarMetricLabel }}</span>
                <ChevronDown :size="13" />
              </div>
            </div>
            <div class="content-chrome__toolbar-item" data-measure-key="date">
              <div class="content-chrome__pill">
                <CalendarDays :size="13" />
                <span>{{ explorer.timeLabel }}</span>
                <ChevronDown :size="13" />
              </div>
            </div>
            <div class="content-chrome__toolbar-item" data-measure-key="repo">
              <div class="content-chrome__pill">
                <LayoutGrid :size="13" />
                <span>{{ explorer.repoLabel }}</span>
                <ChevronDown :size="13" />
              </div>
            </div>
            <div
              v-for="kind in explorer.visibleExtraFilterKinds"
              :key="`measure-${kind}`"
              class="content-chrome__filter-chip content-chrome__toolbar-item"
              :data-measure-key="`chip-${kind}`"
            >
              <div class="content-chrome__filter-chip-badge">
                <div class="content-chrome__pill content-chrome__pill--filter">
                  <span>{{ extraFilterChipLabel(kind) }}</span>
                  <ChevronDown :size="13" />
                </div>
                <div class="content-chrome__chip-remove content-chrome__chip-remove--inline">
                  <X :size="11" />
                </div>
              </div>
            </div>
            <div class="content-chrome__toolbar-item" data-measure-key="add-filter">
              <div class="content-chrome__ghost">
                <Plus :size="12" />
                <span>Filter</span>
              </div>
            </div>
            <div
              v-if="isExplorerRoute"
              class="content-chrome__toolbar-item content-chrome__toolbar-item--secondary"
              data-measure-key="secondary"
            >
              <div class="content-chrome__toolbar-divider" aria-hidden="true" />
              <div class="content-chrome__toolbar-group content-chrome__toolbar-group--secondary">
                <div class="content-chrome__pill">
                  <GitBranch :size="13" />
                  <span>{{ `Group by: ${explorer.groupByLabel}` }}</span>
                  <ChevronDown :size="13" />
                </div>
                <div class="content-chrome__pill">
                  <SlidersHorizontal :size="13" />
                  <span>{{ compareToolbarLabel }}</span>
                  <ChevronDown :size="13" />
                </div>
                <div class="content-chrome__pill">
                  <BarChart3 :size="13" />
                  <span>Display</span>
                  <ChevronDown :size="13" />
                </div>
              </div>
            </div>
          </div>
          <div
            v-if="explorerMetricMenuOpen"
            class="content-chrome__explorer-menu"
            role="menu"
            :style="{ top: explorerMetricMenuStyle.top, left: explorerMetricMenuStyle.left }"
          >
              <button
                v-for="option in toolbarMetricOptions"
                :key="option.value"
                class="content-chrome__menu-option"
                role="menuitemradio"
                :aria-checked="isContributorsRoute ? contributors.metric === option.value : explorer.metric === option.value"
                type="button"
                @click="selectToolbarMetric(option.value)"
              >
                <Check v-if="isContributorsRoute ? contributors.metric === option.value : explorer.metric === option.value" :size="12" />
                <span v-else class="content-chrome__menu-check-placeholder" />
                {{ option.label }}
              </button>
          </div>
          <div
            v-if="explorerCompareMenuOpen"
            class="content-chrome__explorer-menu"
            role="menu"
            :style="{ top: explorerCompareMenuStyle.top, left: explorerCompareMenuStyle.left }"
          >
            <button
              class="content-chrome__menu-option"
              role="menuitemradio"
              :aria-checked="!explorer.compareEnabled"
              type="button"
              @click="disableExplorerCompare"
            >
              <Check v-if="!explorer.compareEnabled" :size="12" />
              <span v-else class="content-chrome__menu-check-placeholder" />
              Off
            </button>
            <button
              v-for="option in ACTIVITY_COMPARE_PRESET_OPTIONS"
              :key="option.value"
              class="content-chrome__menu-option"
              role="menuitemradio"
              :aria-checked="explorer.compareMode === option.value"
              type="button"
              @click="applyExplorerComparePreset(option.value)"
            >
              <Check v-if="explorer.compareMode === option.value" :size="12" />
              <span v-else class="content-chrome__menu-check-placeholder" />
              {{ option.label }}
            </button>
            <div class="content-chrome__menu-divider" />
            <div class="content-chrome__menu-section">
              <span class="content-chrome__menu-section-title">
                Custom past date
                <Check v-if="explorer.compareMode === 'custom_anchor_date'" :size="12" />
              </span>
              <label class="content-chrome__menu-field">
                <span>Reference end date</span>
                <input v-model="explorerCompareAnchorDraft" type="date" />
              </label>
              <p class="content-chrome__menu-note">
                Uses the same {{ explorer.rangeDays }}-day span and must end before {{ explorer.effectiveDateRange.from }}.
              </p>
              <button
                class="content-chrome__menu-apply"
                type="button"
                :disabled="!compareAnchorDraftValid"
                @click="applyExplorerCompareAnchor"
              >
                Use custom past date
              </button>
            </div>
            <div class="content-chrome__menu-divider" />
            <div class="content-chrome__menu-section">
              <span class="content-chrome__menu-section-title">
                Custom date range
                <Check v-if="explorer.compareMode === 'custom_range'" :size="12" />
              </span>
              <label class="content-chrome__menu-field">
                <span>From</span>
                <input v-model="explorerCompareFromDraft" type="date" />
              </label>
              <label class="content-chrome__menu-field">
                <span>To</span>
                <input v-model="explorerCompareToDraft" type="date" />
              </label>
              <p class="content-chrome__menu-note">
                Must match the current {{ explorer.rangeDays }}-day span and end before {{ explorer.effectiveDateRange.from }}.
              </p>
              <button
                class="content-chrome__menu-apply"
                type="button"
                :disabled="!compareRangeDraftValid"
                @click="applyExplorerCompareCustomRange"
              >
                Use custom date range
              </button>
            </div>
            <div class="content-chrome__menu-divider" />
            <div class="content-chrome__menu-section">
              <span class="content-chrome__menu-section-title">Overlay</span>
              <button
                class="content-chrome__menu-option"
                type="button"
                :disabled="!explorer.compareEnabled || !explorer.compareData"
                @click="explorer.compareOverlayVisible = !explorer.compareOverlayVisible"
              >
                <component :is="explorer.compareOverlayVisible ? EyeOff : Eye" :size="12" />
                {{ explorer.compareOverlayVisible ? 'Hide overlay' : 'Show overlay' }}
              </button>
            </div>
          </div>
          <div
            v-if="explorerTimeMenuOpen"
            id="explorer-time-menu"
            class="content-chrome__explorer-menu content-chrome__explorer-menu--time"
            role="menu"
            :style="{ top: explorerTimeMenuStyle.top, left: explorerTimeMenuStyle.left }"
          >
            <button
              v-for="option in explorerRangeOptions"
              :key="option.id"
              class="content-chrome__menu-option"
              role="menuitemradio"
              :aria-checked="explorer.rangePreset === option.id"
              type="button"
              @click="applyExplorerRangePreset(option.id)"
            >
              <Check v-if="explorer.rangePreset === option.id" :size="12" />
              <span v-else class="content-chrome__menu-check-placeholder" />
              {{ option.label }}
            </button>
            <div class="content-chrome__menu-divider" />
            <div class="content-chrome__menu-section">
              <span class="content-chrome__menu-section-title">
                Custom range
                <Check v-if="explorer.rangePreset === 'custom'" :size="12" />
              </span>
              <label class="content-chrome__menu-field">
                <span>From</span>
                <input v-model="explorerCustomFromDraft" type="date" />
              </label>
              <label class="content-chrome__menu-field">
                <span>To</span>
                <input v-model="explorerCustomToDraft" type="date" />
              </label>
              <button
                class="content-chrome__menu-apply"
                type="button"
                :disabled="!explorerCustomFromDraft || !explorerCustomToDraft || explorerCustomFromDraft > explorerCustomToDraft"
                @click="applyExplorerCustomRange()"
              >
                Apply custom range
              </button>
            </div>
          </div>
          <div
            v-if="explorerRepoMenuOpen"
            id="explorer-repo-menu"
            class="content-chrome__explorer-menu"
            role="menu"
            :style="{ top: explorerRepoMenuStyle.top, left: explorerRepoMenuStyle.left }"
          >
            <button
              v-if="!isCodebaseRoute"
              class="content-chrome__menu-option"
              role="menuitemradio"
              :aria-checked="!explorer.selectedRepoIds.length"
              type="button"
              @click="clearRepoFiltersForCurrentRoute()"
            >
              <Check v-if="!explorer.selectedRepoIds.length" :size="12" />
              <span v-else class="content-chrome__menu-check-placeholder" />
              All repositories
            </button>
            <button
              v-for="repo in (dashboard.bootstrap?.repos ?? []).filter((item) => item.enabled)"
              :key="repo.id"
              class="content-chrome__menu-option"
              :role="isCodebaseRoute ? 'menuitemradio' : 'menuitemcheckbox'"
              :aria-checked="explorer.selectedRepoIds.includes(repo.id)"
              type="button"
              @click="selectRepoFilterForCurrentRoute(repo.id)"
            >
              <Check v-if="explorer.selectedRepoIds.includes(repo.id)" :size="12" />
              <span v-else class="content-chrome__menu-check-placeholder" />
              {{ repo.displayName }}
            </button>
            <button
              v-if="!isCodebaseRoute"
              class="content-chrome__menu-reset"
              type="button"
              @click="clearRepoFiltersForCurrentRoute()"
            >
              Clear selections
            </button>
          </div>
          <div
            v-if="explorerGroupMenuOpen"
            id="explorer-group-menu"
            class="content-chrome__explorer-menu"
            role="menu"
            :style="{ top: explorerGroupMenuStyle.top, left: explorerGroupMenuStyle.left }"
          >
            <button
              v-for="option in ACTIVITY_GROUP_BY_OPTIONS"
              :key="option.value"
              class="content-chrome__menu-option"
              role="menuitemradio"
              :aria-checked="explorer.groupBy === option.value"
              type="button"
              @click="explorer.setGroupBy(option.value); closeExplorerMenus()"
            >
              <Check v-if="explorer.groupBy === option.value" :size="12" />
              <span v-else class="content-chrome__menu-check-placeholder" />
              {{ option.label }}
            </button>
          </div>
          <div
            v-if="explorerDisplayMenuOpen"
            id="explorer-display-menu"
            class="content-chrome__explorer-menu"
            role="menu"
            :style="{ top: explorerDisplayMenuStyle.top, left: explorerDisplayMenuStyle.left }"
          >
            <div class="content-chrome__menu-section">
              <span class="content-chrome__menu-section-title">Chart type</span>
              <button
                v-for="option in ACTIVITY_CHART_STYLE_OPTIONS"
                :key="option.value"
                class="content-chrome__menu-option"
                role="menuitemradio"
                :aria-checked="explorer.chartStyle === option.value"
                type="button"
                @click="explorer.setChartStyle(option.value); closeExplorerMenus()"
              >
                <Check v-if="explorer.chartStyle === option.value" :size="12" />
                <span v-else class="content-chrome__menu-check-placeholder" />
                {{ option.label }}
              </button>
            </div>
            <div class="content-chrome__menu-divider" />
            <div class="content-chrome__menu-section">
              <span class="content-chrome__menu-section-title">Legend</span>
              <button
                class="content-chrome__menu-option"
                type="button"
                :disabled="explorer.groupBy === 'none' || !explorer.analytics?.series || explorer.analytics.series.length <= 1"
                @click="explorer.showLegend = !explorer.showLegend"
              >
                <component :is="explorer.showLegend ? EyeOff : Eye" :size="12" />
                {{ explorer.showLegend ? 'Hide legend' : 'Show legend' }}
              </button>
            </div>
          </div>
          <div
            v-if="explorerFilterMenuOpen"
            id="explorer-filter-menu"
            class="content-chrome__explorer-menu content-chrome__explorer-menu--add-filter"
            role="menu"
            :style="{ top: explorerFilterMenuStyle.top, left: explorerFilterMenuStyle.left }"
          >
            <button
              v-for="option in explorer.availableExtraFilterKinds"
              :key="option.value"
              class="content-chrome__menu-option"
              type="button"
              @click="addExplorerExtraFilter(option.value)"
            >
              <Plus :size="12" />
              {{ option.label }}
            </button>
            <button v-if="!explorer.availableExtraFilterKinds.length" type="button" disabled>
              All filter chips are already added
            </button>
          </div>
          <div
            v-if="explorerExtraFilterMenuKind"
            class="content-chrome__explorer-menu content-chrome__explorer-menu--filters"
            role="menu"
            :style="{ top: explorerExtraFilterMenuStyle.top, left: explorerExtraFilterMenuStyle.left }"
          >
            <div class="content-chrome__menu-header">
              <span class="content-chrome__menu-section-title">{{ extraFilterMenuLabel(explorerExtraFilterMenuKind) }}</span>
              <button
                class="content-chrome__menu-reset"
                type="button"
                :disabled="extraFilterSelectionCount(explorerExtraFilterMenuKind) === 0"
                @click="clearExplorerExtraFilterSelection(explorerExtraFilterMenuKind)"
              >
                Clear
              </button>
            </div>
            <button
              v-for="option in extraFilterMenuOptions(explorerExtraFilterMenuKind)"
              :key="option.value"
              class="content-chrome__menu-option"
              role="menuitemcheckbox"
              :aria-checked="isExtraFilterOptionSelected(explorerExtraFilterMenuKind, option.value)"
              type="button"
              @click="toggleExtraFilterOption(explorerExtraFilterMenuKind, option.value)"
            >
              <Check v-if="isExtraFilterOptionSelected(explorerExtraFilterMenuKind, option.value)" :size="12" />
              <span v-else class="content-chrome__menu-check-placeholder" />
              {{ option.label }}
            </button>
          </div>
        </div>
      </template>
      <template v-if="!isPageRoute">
        <div class="content-chrome__spacer" />
        <div ref="contentChromeActionsRef" class="content-chrome__actions">
        <template v-if="isExplorerRoute">
          <button
            class="content-chrome__ghost"
            type="button"
            @click="explorer.openNewAnnotation()"
          >
            <Tag :size="13" />
            <span>Annotations</span>
          </button>
        </template>
        <template v-else-if="isContributorsRoute">
          <button class="content-chrome__ghost" type="button">
            <Pencil :size="13" />
            <span>Edit</span>
          </button>
          <button class="content-chrome__ghost content-chrome__ghost--overflow" type="button">
            <MoreHorizontal :size="14" />
          </button>
        </template>
        <template v-else-if="isCodebaseRoute">
          <button class="content-chrome__ghost content-chrome__ghost--overflow" type="button">
            <MoreHorizontal :size="14" />
          </button>
        </template>
        <template v-if="route.name === 'settings-sync'">
          <button
            class="content-chrome__ghost content-chrome__ghost--overflow"
            type="button"
            aria-haspopup="menu"
            :aria-expanded="syncMenuOpen"
            data-testid="sync-more-button"
            @click="toggleSyncMenu"
          >
            <MoreHorizontal :size="14" />
          </button>
          <div
            v-if="syncMenuOpen"
            class="content-chrome__explorer-menu content-chrome__explorer-menu--sync"
            role="menu"
          >
            <button
              v-if="dashboard.syncStatus?.running"
              class="content-chrome__menu-option"
              type="button"
              role="menuitem"
              :disabled="dashboard.currentSync?.stopRequested"
              @click="syncMenuStop"
            >
              Stop sync
            </button>
            <button
              class="content-chrome__menu-option"
              type="button"
              role="menuitem"
              :disabled="!!dashboard.syncStatus?.running"
              data-testid="sync-reset-button"
              @click="syncMenuReset"
            >
              Reset data
            </button>
          </div>
        </template>
        <button
          v-if="usesSharedFilterToolbar && hasExplorerToolbarOverflow"
          class="content-chrome__ghost content-chrome__ghost--overflow"
          type="button"
          aria-haspopup="menu"
          :aria-expanded="explorerOverflowMenuOpen"
          @click="toggleExplorerOverflowMenu"
        >
          <MoreHorizontal :size="14" />
        </button>
        <div
          v-if="explorerOverflowMenuOpen"
          class="content-chrome__explorer-menu content-chrome__explorer-menu--overflow"
          role="menu"
          :style="explorerOverflowMenuStyle"
        >
          <div
            v-if="overflowedToolbarKeys.includes('metric') || overflowedToolbarKeys.includes('date') || overflowedToolbarKeys.includes('repo')"
            class="content-chrome__menu-section"
          >
            <span class="content-chrome__menu-section-title">Controls</span>
            <div v-if="overflowedToolbarKeys.includes('metric')" class="content-chrome__menu-accordion">
              <button
                class="content-chrome__menu-option content-chrome__menu-option--accordion"
                type="button"
                :aria-expanded="isExplorerOverflowPanelOpen('metric')"
                @click="toggleExplorerOverflowPanel('metric')"
              >
                <span class="content-chrome__menu-option-main">
                  <BarChart3 :size="12" />
                  {{ toolbarMetricLabel }}
                </span>
                <ChevronDown :size="12" class="content-chrome__menu-caret" :class="{ 'is-open': isExplorerOverflowPanelOpen('metric') }" />
              </button>
              <div v-if="isExplorerOverflowPanelOpen('metric')" class="content-chrome__menu-accordion-body">
                <button
                  v-for="option in toolbarMetricOptions"
                  :key="option.value"
                  class="content-chrome__menu-option"
                  role="menuitemradio"
                  :aria-checked="isContributorsRoute ? contributors.metric === option.value : explorer.metric === option.value"
                  type="button"
                  @click="selectToolbarMetric(option.value)"
                >
                  <Check v-if="isContributorsRoute ? contributors.metric === option.value : explorer.metric === option.value" :size="12" />
                  <span v-else class="content-chrome__menu-check-placeholder" />
                  {{ option.label }}
                </button>
              </div>
            </div>
            <div v-if="overflowedToolbarKeys.includes('date')" class="content-chrome__menu-accordion">
              <button
                class="content-chrome__menu-option content-chrome__menu-option--accordion"
                type="button"
                :aria-expanded="isExplorerOverflowPanelOpen('date')"
                @click="toggleExplorerOverflowPanel('date')"
              >
                <span class="content-chrome__menu-option-main">
                  <CalendarDays :size="12" />
                  {{ explorer.timeLabel }}
                </span>
                <ChevronDown :size="12" class="content-chrome__menu-caret" :class="{ 'is-open': isExplorerOverflowPanelOpen('date') }" />
              </button>
              <div v-if="isExplorerOverflowPanelOpen('date')" class="content-chrome__menu-accordion-body">
                <button
                  v-for="option in explorerRangeOptions"
                  :key="option.id"
                  class="content-chrome__menu-option"
                  role="menuitemradio"
                  :aria-checked="explorer.rangePreset === option.id"
                  type="button"
                  @click="explorer.setRangePreset(option.id)"
                >
                  <Check v-if="explorer.rangePreset === option.id" :size="12" />
                  <span v-else class="content-chrome__menu-check-placeholder" />
                  {{ option.label }}
                </button>
                <div class="content-chrome__menu-divider" />
                <div class="content-chrome__menu-section">
                  <span class="content-chrome__menu-section-title">
                    Custom range
                    <Check v-if="explorer.rangePreset === 'custom'" :size="12" />
                  </span>
                  <label class="content-chrome__menu-field">
                    <span>From</span>
                    <input v-model="explorerCustomFromDraft" type="date" />
                  </label>
                  <label class="content-chrome__menu-field">
                    <span>To</span>
                    <input v-model="explorerCustomToDraft" type="date" />
                  </label>
                  <button
                    class="content-chrome__menu-apply"
                    type="button"
                    :disabled="!explorerCustomFromDraft || !explorerCustomToDraft || explorerCustomFromDraft > explorerCustomToDraft"
                    @click="applyExplorerCustomRange()"
                  >
                    Apply custom range
                  </button>
                </div>
              </div>
            </div>
            <div v-if="overflowedToolbarKeys.includes('repo')" class="content-chrome__menu-accordion">
              <button
                class="content-chrome__menu-option content-chrome__menu-option--accordion"
                type="button"
                :aria-expanded="isExplorerOverflowPanelOpen('repo')"
                @click="toggleExplorerOverflowPanel('repo')"
              >
                <span class="content-chrome__menu-option-main">
                  <LayoutGrid :size="12" />
                  {{ explorer.repoLabel }}
                </span>
                <ChevronDown :size="12" class="content-chrome__menu-caret" :class="{ 'is-open': isExplorerOverflowPanelOpen('repo') }" />
              </button>
              <div v-if="isExplorerOverflowPanelOpen('repo')" class="content-chrome__menu-accordion-body">
                <button
                  v-if="!isCodebaseRoute"
                  class="content-chrome__menu-option"
                  role="menuitemradio"
                  :aria-checked="!explorer.selectedRepoIds.length"
                  type="button"
                  @click="clearRepoFiltersForCurrentRoute()"
                >
                  <Check v-if="!explorer.selectedRepoIds.length" :size="12" />
                  <span v-else class="content-chrome__menu-check-placeholder" />
                  All repositories
                </button>
                <button
                  v-for="repo in (dashboard.bootstrap?.repos ?? []).filter((item) => item.enabled)"
                  :key="repo.id"
                  class="content-chrome__menu-option"
                  :role="isCodebaseRoute ? 'menuitemradio' : 'menuitemcheckbox'"
                  :aria-checked="explorer.selectedRepoIds.includes(repo.id)"
                  type="button"
                  @click="selectRepoFilterForCurrentRoute(repo.id)"
                >
                  <Check v-if="explorer.selectedRepoIds.includes(repo.id)" :size="12" />
                  <span v-else class="content-chrome__menu-check-placeholder" />
                  {{ repo.displayName }}
                </button>
                <button
                  v-if="!isCodebaseRoute"
                  class="content-chrome__menu-reset"
                  type="button"
                  @click="clearRepoFiltersForCurrentRoute()"
                >
                  Clear selections
                </button>
              </div>
            </div>
          </div>
          <div
            v-if="overflowExplorerFilterKinds.length || !showInlineAddFilter"
            class="content-chrome__menu-section"
          >
            <span class="content-chrome__menu-section-title">Filters</span>
            <div
              v-for="kind in overflowExplorerFilterKinds"
              :key="`overflow-${kind}`"
              class="content-chrome__menu-accordion"
            >
              <button
                class="content-chrome__menu-option content-chrome__menu-option--accordion"
                type="button"
                :aria-expanded="isExplorerOverflowPanelOpen(`filter:${kind}`)"
                @click="toggleExplorerOverflowPanel(`filter:${kind}`)"
              >
                <span class="content-chrome__menu-option-main">
                  <Filter :size="12" />
                  {{ extraFilterChipLabel(kind) }}
                </span>
                <ChevronDown :size="12" class="content-chrome__menu-caret" :class="{ 'is-open': isExplorerOverflowPanelOpen(`filter:${kind}`) }" />
              </button>
              <div v-if="isExplorerOverflowPanelOpen(`filter:${kind}`)" class="content-chrome__menu-accordion-body">
                <div class="content-chrome__menu-header">
                  <span class="content-chrome__menu-section-title">{{ extraFilterMenuLabel(kind) }}</span>
                  <button
                    class="content-chrome__menu-reset"
                    type="button"
                    :disabled="extraFilterSelectionCount(kind) === 0"
                    @click="clearExplorerExtraFilterSelection(kind)"
                  >
                    Clear
                  </button>
                </div>
                <button
                  v-for="option in extraFilterMenuOptions(kind)"
                  :key="option.value"
                  class="content-chrome__menu-option"
                  role="menuitemcheckbox"
                  :aria-checked="isExtraFilterOptionSelected(kind, option.value)"
                  type="button"
                  @click="toggleExtraFilterOption(kind, option.value)"
                >
                  <Check v-if="isExtraFilterOptionSelected(kind, option.value)" :size="12" />
                  <span v-else class="content-chrome__menu-check-placeholder" />
                  {{ option.label }}
                </button>
              </div>
            </div>
            <div v-if="!showInlineAddFilter" class="content-chrome__menu-accordion">
              <button
                class="content-chrome__menu-option content-chrome__menu-option--accordion"
                type="button"
                :aria-expanded="isExplorerOverflowPanelOpen('add-filter')"
                @click="toggleExplorerOverflowPanel('add-filter')"
              >
                <span class="content-chrome__menu-option-main">
                  <Plus :size="12" />
                  Add filter
                </span>
                <ChevronDown :size="12" class="content-chrome__menu-caret" :class="{ 'is-open': isExplorerOverflowPanelOpen('add-filter') }" />
              </button>
              <div v-if="isExplorerOverflowPanelOpen('add-filter')" class="content-chrome__menu-accordion-body">
                <button
                  v-for="option in explorer.availableExtraFilterKinds"
                  :key="option.value"
                  class="content-chrome__menu-option"
                  type="button"
                  @click="addExplorerExtraFilterInOverflow(option.value)"
                >
                  <Plus :size="12" />
                  {{ option.label }}
                </button>
                <button v-if="!explorer.availableExtraFilterKinds.length" type="button" disabled>
                  All filter chips are already added
                </button>
              </div>
            </div>
          </div>
          <div
            v-if="isExplorerRoute && !showInlineSecondaryControls"
            class="content-chrome__menu-section"
          >
            <span class="content-chrome__menu-section-title">View</span>
            <div class="content-chrome__menu-accordion">
              <button
                class="content-chrome__menu-option content-chrome__menu-option--accordion"
                type="button"
                :aria-expanded="isExplorerOverflowPanelOpen('group')"
                @click="toggleExplorerOverflowPanel('group')"
              >
                <span class="content-chrome__menu-option-main">
                  <GitBranch :size="12" />
                  {{ `Group by: ${explorer.groupByLabel}` }}
                </span>
                <ChevronDown :size="12" class="content-chrome__menu-caret" :class="{ 'is-open': isExplorerOverflowPanelOpen('group') }" />
              </button>
              <div v-if="isExplorerOverflowPanelOpen('group')" class="content-chrome__menu-accordion-body">
                <button
                  v-for="option in ACTIVITY_GROUP_BY_OPTIONS"
                  :key="option.value"
                  class="content-chrome__menu-option"
                  role="menuitemradio"
                  :aria-checked="explorer.groupBy === option.value"
                  type="button"
                  @click="explorer.setGroupBy(option.value)"
                >
                  <Check v-if="explorer.groupBy === option.value" :size="12" />
                  <span v-else class="content-chrome__menu-check-placeholder" />
                  {{ option.label }}
                </button>
              </div>
            </div>
            <div class="content-chrome__menu-accordion">
              <button
                class="content-chrome__menu-option content-chrome__menu-option--accordion"
                type="button"
                :aria-expanded="isExplorerOverflowPanelOpen('compare')"
                @click="toggleExplorerOverflowPanel('compare')"
              >
                <span class="content-chrome__menu-option-main">
                  <SlidersHorizontal :size="12" />
                  {{ compareToolbarLabel }}
                </span>
                <ChevronDown :size="12" class="content-chrome__menu-caret" :class="{ 'is-open': isExplorerOverflowPanelOpen('compare') }" />
              </button>
              <div v-if="isExplorerOverflowPanelOpen('compare')" class="content-chrome__menu-accordion-body">
                <button
                  class="content-chrome__menu-option"
                  role="menuitemradio"
                  :aria-checked="!explorer.compareEnabled"
                  type="button"
                  @click="disableExplorerCompare"
                >
                  <Check v-if="!explorer.compareEnabled" :size="12" />
                  <span v-else class="content-chrome__menu-check-placeholder" />
                  Off
                </button>
                <button
                  v-for="option in ACTIVITY_COMPARE_PRESET_OPTIONS"
                  :key="`overflow-${option.value}`"
                  class="content-chrome__menu-option"
                  role="menuitemradio"
                  :aria-checked="explorer.compareMode === option.value"
                  type="button"
                  @click="applyExplorerComparePreset(option.value)"
                >
                  <Check v-if="explorer.compareMode === option.value" :size="12" />
                  <span v-else class="content-chrome__menu-check-placeholder" />
                  {{ option.label }}
                </button>
                <div class="content-chrome__menu-divider" />
                <div class="content-chrome__menu-section">
                  <span class="content-chrome__menu-section-title">Custom past date</span>
                  <label class="content-chrome__menu-field">
                    <span>Reference end date</span>
                    <input v-model="explorerCompareAnchorDraft" type="date" />
                  </label>
                  <button
                    class="content-chrome__menu-apply"
                    type="button"
                    :disabled="!compareAnchorDraftValid"
                    @click="applyExplorerCompareAnchor"
                  >
                    Use custom past date
                  </button>
                </div>
                <div class="content-chrome__menu-divider" />
                <div class="content-chrome__menu-section">
                  <span class="content-chrome__menu-section-title">Custom date range</span>
                  <label class="content-chrome__menu-field">
                    <span>From</span>
                    <input v-model="explorerCompareFromDraft" type="date" />
                  </label>
                  <label class="content-chrome__menu-field">
                    <span>To</span>
                    <input v-model="explorerCompareToDraft" type="date" />
                  </label>
                  <button
                    class="content-chrome__menu-apply"
                    type="button"
                    :disabled="!compareRangeDraftValid"
                    @click="applyExplorerCompareCustomRange"
                  >
                    Use custom date range
                  </button>
                </div>
                <div class="content-chrome__menu-divider" />
                <button
                  class="content-chrome__menu-option"
                  type="button"
                  :disabled="!explorer.compareEnabled || !explorer.compareData"
                  @click="explorer.compareOverlayVisible = !explorer.compareOverlayVisible"
                >
                  <component :is="explorer.compareOverlayVisible ? EyeOff : Eye" :size="12" />
                  {{ explorer.compareOverlayVisible ? 'Hide overlay' : 'Show overlay' }}
                </button>
              </div>
            </div>
            <div class="content-chrome__menu-accordion">
              <button
                class="content-chrome__menu-option content-chrome__menu-option--accordion"
                type="button"
                :aria-expanded="isExplorerOverflowPanelOpen('display')"
                @click="toggleExplorerOverflowPanel('display')"
              >
                <span class="content-chrome__menu-option-main">
                  <BarChart3 :size="12" />
                  Display
                </span>
                <ChevronDown :size="12" class="content-chrome__menu-caret" :class="{ 'is-open': isExplorerOverflowPanelOpen('display') }" />
              </button>
              <div v-if="isExplorerOverflowPanelOpen('display')" class="content-chrome__menu-accordion-body">
                <div class="content-chrome__menu-section">
                  <span class="content-chrome__menu-section-title">Chart type</span>
                  <button
                    v-for="option in ACTIVITY_CHART_STYLE_OPTIONS"
                    :key="option.value"
                    class="content-chrome__menu-option"
                    role="menuitemradio"
                    :aria-checked="explorer.chartStyle === option.value"
                    type="button"
                    @click="explorer.setChartStyle(option.value)"
                  >
                    <Check v-if="explorer.chartStyle === option.value" :size="12" />
                    <span v-else class="content-chrome__menu-check-placeholder" />
                    {{ option.label }}
                  </button>
                </div>
                <div class="content-chrome__menu-divider" />
                <div class="content-chrome__menu-section">
                  <span class="content-chrome__menu-section-title">Legend</span>
                  <button
                    class="content-chrome__menu-option"
                    type="button"
                    :disabled="explorer.groupBy === 'none' || !explorer.analytics?.series || explorer.analytics.series.length <= 1"
                    @click="explorer.showLegend = !explorer.showLegend"
                  >
                    <component :is="explorer.showLegend ? EyeOff : Eye" :size="12" />
                    {{ explorer.showLegend ? 'Hide legend' : 'Show legend' }}
                  </button>
                </div>
              </div>
            </div>
          </div>
        </div>
        <LoaderCircle v-if="dashboard.syncStatus?.running" class="spin" :size="14" style="color: var(--cf-accent)" />
        </div>
      </template>
    </header>

    <div class="app-shell__floating-controls" data-testid="app-floating-controls">
      <div class="sidebar-tl-gap" aria-hidden="true" />
      <button
        class="sidebar-toolbar-btn"
        :aria-label="sidebarCollapsed ? 'Expand sidebar' : 'Collapse sidebar'"
        data-testid="sidebar-toggle"
        type="button"
        @click="toggleSidebar"
      >
        <component :is="sidebarCollapsed ? PanelLeftOpen : PanelLeftClose" :size="15" />
      </button>
      <button class="sidebar-toolbar-btn" aria-label="Go back" type="button" @click="router.back()">
        <ChevronLeft :size="15" />
      </button>
      <button class="sidebar-toolbar-btn" aria-label="Go forward" type="button" @click="router.forward()">
        <ChevronRight :size="15" />
      </button>
    </div>

    <DrilldownDrawer v-if="!isSettingsRoute" />
  </div>
</template>
