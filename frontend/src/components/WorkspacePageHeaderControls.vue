<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import {
  CalendarDays,
  Check,
  ChevronDown,
  Edit3,
  Filter,
  Lock,
  LockOpen,
  Maximize2,
  MoreHorizontal,
  Plus,
  X,
} from 'lucide-vue-next'
import PageWidgetPicker from './PageWidgetPicker.vue'
import { useWorkspaceStore } from '../stores/workspace'
import type { FilterOperator, PageFilterState, PageTimeRange, WidgetKind } from '../types/workspace'

const props = defineProps<{
  pageId: string
}>()

type TimePresetId =
  | 'last_7_days'
  | 'last_14_days'
  | 'last_30_days'
  | 'last_90_days'
  | 'last_6_months'
  | 'custom'

type MenuStyle = {
  top: string
  left?: string
  right?: string
}

type UiFilter = PageFilterState & {
  id: string
  index: number
}

type TimeRange = {
  label: string
  from: string
  to: string
  preset: TimePresetId
}

const router = useRouter()
const workspace = useWorkspaceStore()

const toolbarRootRef = ref<HTMLElement | null>(null)
const toolbarMeasureRef = ref<HTMLElement | null>(null)
const pageActionsRef = ref<HTMLElement | null>(null)
const overflowedToolbarKeys = ref<string[]>([])

const timeRangeMenuOpen = ref(false)
const filterMenuOpen = ref(false)
const overflowMenuOpen = ref(false)
const pageActionsMenuOpen = ref(false)

const timeRangeMenuStyle = ref<MenuStyle>({ top: '0px', left: '0px' })
const filterMenuStyle = ref<MenuStyle>({ top: '0px', left: '0px' })
const overflowMenuStyle = ref<MenuStyle>({ top: '0px', right: '0px' })
const pageActionsMenuStyle = ref<MenuStyle>({ top: '0px', right: '0px' })

let toolbarResizeObserver: ResizeObserver | null = null

const page = computed(() => workspace.pages.find((entry) => entry.id === props.pageId) ?? null)
const pageFilters = computed(() => workspace.pageFilters[props.pageId] ?? [])
const pageTimeRange = computed(() => workspace.pageTimeRanges[props.pageId] ?? null)
const catalogWidgets = computed(() => workspace.widgets.filter((widget) => !widget.archived))
const widgetPickerOpen = computed(() => workspace.pageWidgetPickerOpen)

const timePresetDefaults = {
  last_7_days: { label: 'Last 7 days', fromOffset: 7 },
  last_14_days: { label: 'Last 14 days', fromOffset: 14 },
  last_30_days: { label: 'Last 30 days', fromOffset: 30 },
  last_90_days: { label: 'Last 90 days', fromOffset: 90 },
  last_6_months: { label: 'Last 6 months', fromOffset: 180 },
} as const

const filterDimensionLabels: Record<string, string> = {
  repo: 'Repository',
  language: 'Language',
  author: 'Author',
  category: 'Category',
  subtype: 'Subtype',
  issueType: 'Issue type',
  branch: 'Branch',
  filePath: 'File path',
  label: 'Label / tag',
}

const timeRangeDraft = reactive({
  preset: 'last_14_days' as TimePresetId,
  from: '',
  to: '',
})

const filterDraft = reactive({
  field: 'repo',
  op: 'in' as FilterOperator,
  values: '',
  locked: false,
})

const filterDimensionOptions = Object.entries(filterDimensionLabels).map(([value, label]) => ({ value, label }))
const timePresetIds = ['last_7_days', 'last_14_days', 'last_30_days', 'last_90_days', 'last_6_months'] as const

const uiTimeRange = computed<TimeRange>(() => {
  if (pageTimeRange.value) {
    return normalizeTimeRange(pageTimeRange.value)
  }
  return {
    preset: 'last_14_days',
    ...buildPresetRange('last_14_days'),
  }
})

const uiFilters = computed<UiFilter[]>(() => (
  pageFilters.value.map((filter, index) => ({
    ...filter,
    id: `${props.pageId}-${index}-${filter.field}-${filter.op}-${filter.values.join('-')}`,
    index,
  }))
))

const visibleToolbarFilters = computed(() => (
  uiFilters.value.filter((_, index) => !overflowedToolbarKeys.value.includes(`filter-${index}`))
))

const overflowToolbarFilters = computed(() => (
  uiFilters.value.filter((_, index) => overflowedToolbarKeys.value.includes(`filter-${index}`))
))

const showInlineAddWidget = computed(() => !overflowedToolbarKeys.value.includes('add-widget'))
const showInlineAddFilter = computed(() => true)
const hasToolbarOverflow = computed(() => overflowedToolbarKeys.value.length > 0)

function mapTimeOffsetToDate(days: number) {
  const now = new Date()
  const from = new Date(now)
  from.setDate(from.getDate() - (days - 1))
  return {
    from: from.toISOString().slice(0, 10),
    to: now.toISOString().slice(0, 10),
  }
}

function buildPresetRange(preset: Exclude<TimePresetId, 'custom'>) {
  const presetConfig = timePresetDefaults[preset]
  return {
    ...mapTimeOffsetToDate(presetConfig.fromOffset),
    label: presetConfig.label,
  }
}

function timePresetLabel(preset: Exclude<TimePresetId, 'custom'>) {
  return timePresetDefaults[preset].label
}

function normalizeTimeRange(range: PageTimeRange): TimeRange {
  if (range.preset && range.preset in timePresetDefaults && !range.from && !range.to) {
    return {
      preset: range.preset as Exclude<TimePresetId, 'custom'>,
      ...buildPresetRange(range.preset as Exclude<TimePresetId, 'custom'>),
    }
  }

  if (range.preset && range.preset in timePresetDefaults) {
    const preset = range.preset as Exclude<TimePresetId, 'custom'>
    const presetRange = buildPresetRange(preset)
    return {
      preset,
      from: range.from ?? presetRange.from,
      to: range.to ?? presetRange.to,
      label: presetRange.label,
    }
  }

  return {
    preset: 'custom',
    from: range.from ?? '',
    to: range.to ?? '',
    label: range.from && range.to ? `${range.from} - ${range.to}` : 'Custom range',
  }
}

function syncTimeRangeDraftFromSelection() {
  timeRangeDraft.preset = uiTimeRange.value.preset
  timeRangeDraft.from = uiTimeRange.value.from
  timeRangeDraft.to = uiTimeRange.value.to
}

function setMenuPosition(styleRef: typeof timeRangeMenuStyle, event: MouseEvent, menuWidth: number) {
  if (!(event.currentTarget instanceof HTMLElement)) {
    return
  }
  const trigger = event.currentTarget
  const root = toolbarRootRef.value
  if (!(root instanceof HTMLElement)) {
    return
  }
  const rect = trigger.getBoundingClientRect()
  const rootRect = root.getBoundingClientRect()
  const rawLeft = rect.left - rootRect.left
  const maxLeft = Math.max(0, root.clientWidth - menuWidth - 12)
  styleRef.value = {
    top: `${rect.bottom - rootRect.top + 8}px`,
    left: `${Math.max(0, Math.min(rawLeft, maxLeft))}px`,
    right: undefined,
  }
}

function setMenuRightAlignedPosition(styleRef: typeof overflowMenuStyle, event: MouseEvent) {
  if (!(event.currentTarget instanceof HTMLElement)) {
    return
  }
  const trigger = event.currentTarget
  const root = toolbarRootRef.value
  if (!(root instanceof HTMLElement)) {
    return
  }
  const rect = trigger.getBoundingClientRect()
  const rootRect = root.getBoundingClientRect()
  styleRef.value = {
    top: `${rect.bottom - rootRect.top + 8}px`,
    left: undefined,
    right: `${Math.max(0, rootRect.right - rect.right)}px`,
  }
}

function closeMenus() {
  timeRangeMenuOpen.value = false
  filterMenuOpen.value = false
  overflowMenuOpen.value = false
  pageActionsMenuOpen.value = false
}

function updateToolbarOverflow() {
  const root = toolbarRootRef.value
  const actions = pageActionsRef.value
  const measure = toolbarMeasureRef.value
  const overflowButtonWidth = 34

  if (!root || !actions || !measure) {
    overflowedToolbarKeys.value = []
    return
  }

  const widthFor = (key: string) => {
    const element = measure.querySelector<HTMLElement>(`[data-measure-key="${key}"]`)
    return element ? Math.ceil(element.getBoundingClientRect().width) : 0
  }

  const baseKeys = ['date']
  const optionalKeys = [
    ...uiFilters.value.map((_, index) => `filter-${index}`),
    'add-widget',
  ]

  const available = root.clientWidth - actions.offsetWidth - 12 - overflowButtonWidth
  if (available <= 0) {
    overflowedToolbarKeys.value = optionalKeys
    return
  }

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

function setCustomRange() {
  timeRangeDraft.preset = 'custom'
}

async function applyPresetRange(preset: Exclude<TimePresetId, 'custom'>) {
  const next = buildPresetRange(preset)
  timeRangeDraft.preset = preset
  timeRangeDraft.from = next.from
  timeRangeDraft.to = next.to
  await workspace.setPageTimeRange(props.pageId, {
    preset,
    from: next.from,
    to: next.to,
  })
  await workspace.refreshPageWidgets(props.pageId)
  closeMenus()
}

async function applyTimeRangeFromDraft() {
  const range: PageTimeRange = timeRangeDraft.preset === 'custom'
    ? {
      preset: 'custom',
      from: timeRangeDraft.from || null,
      to: timeRangeDraft.to || null,
    }
    : {
      preset: timeRangeDraft.preset,
      from: timeRangeDraft.from || null,
      to: timeRangeDraft.to || null,
    }

  if (!range.from || !range.to) {
    return
  }

  await workspace.setPageTimeRange(props.pageId, range)
  await workspace.refreshPageWidgets(props.pageId)
  closeMenus()
}

function openTimeRangeMenu(event: MouseEvent) {
  setMenuPosition(timeRangeMenuStyle, event, 248)
  const nextOpen = !timeRangeMenuOpen.value
  closeMenus()
  timeRangeMenuOpen.value = nextOpen
  if (nextOpen) {
    syncTimeRangeDraftFromSelection()
  }
}

function openFilterMenu(event: MouseEvent) {
  setMenuPosition(filterMenuStyle, event, 288)
  const nextOpen = !filterMenuOpen.value
  closeMenus()
  filterMenuOpen.value = nextOpen
}

function openOverflowMenu(event: MouseEvent) {
  setMenuRightAlignedPosition(overflowMenuStyle, event)
  const nextOpen = !overflowMenuOpen.value
  closeMenus()
  overflowMenuOpen.value = nextOpen
}

function openPageActionsMenu(event: MouseEvent) {
  setMenuRightAlignedPosition(pageActionsMenuStyle, event)
  const nextOpen = !pageActionsMenuOpen.value
  closeMenus()
  pageActionsMenuOpen.value = nextOpen
}

async function addFilter() {
  const values = filterDraft.values
    .split(',')
    .map((value) => value.trim())
    .filter(Boolean)

  if (!values.length) {
    return
  }

  await workspace.setPageFilters(props.pageId, [
    ...pageFilters.value,
    {
      field: filterDraft.field,
      op: filterDraft.op,
      values,
      locked: filterDraft.locked,
    },
  ])
  await workspace.refreshPageWidgets(props.pageId)
  filterDraft.values = ''
  filterDraft.locked = false
  closeMenus()
}

async function removeFilter(filterIndex: number) {
  await workspace.setPageFilters(
    props.pageId,
    pageFilters.value.filter((_, index) => index !== filterIndex),
  )
  await workspace.refreshPageWidgets(props.pageId)
}

async function toggleFilterLock(filterIndex: number) {
  const next = [...pageFilters.value]
  next[filterIndex] = {
    ...next[filterIndex],
    locked: !next[filterIndex].locked,
  }
  await workspace.setPageFilters(props.pageId, next)
  await workspace.refreshPageWidgets(props.pageId)
}

async function openWidgetPicker() {
  closeMenus()
  await workspace.openPageWidgetPicker()
}

function closeWidgetPicker() {
  workspace.closePageWidgetPicker()
}

async function addCatalogWidget(widgetId: string, kind: WidgetKind) {
  await workspace.addWidgetToPage(props.pageId, { widgetDefinitionId: widgetId, kind })
  closeWidgetPicker()
}

async function addContentWidget(kind: WidgetKind) {
  await workspace.addWidgetToPage(props.pageId, {
    kind,
    vizOverride: kind === 'markdown_block'
      ? { markdown: 'Add your narrative here.' }
      : kind === 'header_block'
        ? { body: 'Add supporting context below this section header.' }
        : {},
  })
  closeWidgetPicker()
}

function toggleEditMode() {
  const nextMode = !workspace.editMode
  workspace.setEditMode(nextMode)
  if (!nextMode) {
    workspace.setSelectedWidget(null)
  }
}

async function requestFullscreen() {
  const target = document.documentElement
  if (document.fullscreenElement) {
    await document.exitFullscreen()
    return
  }
  await target.requestFullscreen()
}

async function renameCurrentPage() {
  if (!page.value) {
    return
  }
  const nextTitle = window.prompt('Rename page', page.value.title)
  if (!nextTitle?.trim()) {
    return
  }
  await workspace.renamePage(page.value.id, nextTitle.trim())
  closeMenus()
}

async function duplicateCurrentPage() {
  if (!page.value) {
    return
  }
  const duplicated = await workspace.duplicatePageAndRefresh(page.value.id)
  closeMenus()
  await router.push({ name: 'page', params: { pageId: duplicated.id } })
}

async function archiveCurrentPage() {
  if (!page.value || !window.confirm('Archive this page?')) {
    return
  }
  await workspace.archivePageAndRefresh(page.value.id)
  closeMenus()
  const next = workspace.orderedPages[0]
  await router.push(next ? { name: 'page', params: { pageId: next.id } } : { name: 'widgets' })
}

function formatPageFilter(filter: PageFilterState) {
  const label = filterDimensionLabels[filter.field] ?? filter.field
  const values = filter.values.join(', ')

  switch (filter.op) {
    case 'in':
    case 'eq':
      return `${label}: ${values}`
    case 'not_in':
      return `${label}: not ${values}`
    case 'contains':
      return `${label}: contains ${values}`
    case 'between':
      return `${label}: ${filter.values[0] ?? ''}..${filter.values[1] ?? ''}`
    default:
      return `${label}: ${filter.op} ${values}`
  }
}

function handleDocumentPointer(event: MouseEvent) {
  const target = event.target
  if (!(target instanceof Element)) {
    return
  }
  if (!target.closest('.workspace-page-header-controls')) {
    closeMenus()
  }
}

function handleDocumentKeydown(event: KeyboardEvent) {
  if (event.key === 'Escape') {
    closeMenus()
  }
}

watch(
  () => [
    props.pageId,
    uiTimeRange.value.label,
    uiFilters.value.map((filter) => `${filter.field}:${filter.op}:${filter.values.join(',')}:${filter.locked}`).join('|'),
    workspace.editMode,
  ] as const,
  () => {
    void nextTick(() => {
      updateToolbarOverflow()
    })
  },
  { immediate: true },
)

onMounted(() => {
  syncTimeRangeDraftFromSelection()
  document.addEventListener('click', handleDocumentPointer)
  document.addEventListener('keydown', handleDocumentKeydown)
  toolbarResizeObserver = new ResizeObserver(() => {
    updateToolbarOverflow()
  })
  if (toolbarRootRef.value) {
    toolbarResizeObserver.observe(toolbarRootRef.value)
  }
})

onBeforeUnmount(() => {
  toolbarResizeObserver?.disconnect()
  document.removeEventListener('click', handleDocumentPointer)
  document.removeEventListener('keydown', handleDocumentKeydown)
  workspace.closePageWidgetPicker()
})
</script>

<template>
  <div ref="toolbarRootRef" class="workspace-page-header-controls">
    <div class="content-chrome__explorer-toolbar">
      <div class="content-chrome__toolbar-group">
        <div class="content-chrome__toolbar-item" data-toolbar-key="date">
          <button
            class="content-chrome__pill"
            data-testid="page-time-range"
            type="button"
            aria-haspopup="menu"
            :aria-expanded="timeRangeMenuOpen"
            @click="openTimeRangeMenu"
          >
            <CalendarDays :size="13" />
            <span>{{ uiTimeRange.label }}</span>
            <ChevronDown :size="13" />
          </button>
        </div>

        <div
          v-for="filter in visibleToolbarFilters"
          :key="filter.id"
          class="content-chrome__filter-chip content-chrome__toolbar-item"
          :data-toolbar-key="`filter-${filter.index}`"
        >
          <div class="content-chrome__filter-chip-badge">
            <button
              class="workspace-page-header-controls__chip-toggle"
              type="button"
              :title="filter.locked ? 'Unlock filter' : 'Lock filter'"
              @click="toggleFilterLock(filter.index)"
            >
              <component :is="filter.locked ? Lock : LockOpen" :size="11" />
            </button>
            <div class="content-chrome__pill content-chrome__pill--filter workspace-page-header-controls__chip-label">
              <span>{{ formatPageFilter(filter) }}</span>
            </div>
            <button
              v-if="!filter.locked"
              class="content-chrome__chip-remove content-chrome__chip-remove--inline"
              :aria-label="`Remove ${formatPageFilter(filter)}`"
              type="button"
              @click="removeFilter(filter.index)"
            >
              <X :size="11" />
            </button>
          </div>
        </div>

        <div v-if="showInlineAddWidget" class="content-chrome__toolbar-item" data-toolbar-key="add-widget">
          <button
            class="content-chrome__ghost"
            data-testid="page-add-widget"
            type="button"
            @click="openWidgetPicker"
          >
            <Plus :size="13" />
            <span>Add Widget</span>
          </button>
        </div>

        <div v-if="showInlineAddFilter" class="content-chrome__toolbar-item" data-toolbar-key="add-filter">
          <button
            class="content-chrome__ghost"
            data-testid="page-add-filter"
            type="button"
            aria-haspopup="menu"
            :aria-expanded="filterMenuOpen"
            @click="openFilterMenu"
          >
            <Filter :size="13" />
            <span>{{ uiFilters.length ? `+ Filter (${uiFilters.length})` : '+ Filter' }}</span>
          </button>
        </div>
      </div>

      <div ref="toolbarMeasureRef" class="content-chrome__explorer-toolbar-measure" aria-hidden="true">
        <div class="content-chrome__toolbar-item" data-measure-key="date">
          <div class="content-chrome__pill">
            <CalendarDays :size="13" />
            <span>{{ uiTimeRange.label }}</span>
            <ChevronDown :size="13" />
          </div>
        </div>
        <div
          v-for="filter in uiFilters"
          :key="`measure-${filter.id}`"
          class="content-chrome__toolbar-item"
          :data-measure-key="`filter-${filter.index}`"
        >
          <div class="content-chrome__filter-chip-badge">
            <div class="workspace-page-header-controls__chip-toggle">
              <component :is="filter.locked ? Lock : LockOpen" :size="11" />
            </div>
            <div class="content-chrome__pill content-chrome__pill--filter workspace-page-header-controls__chip-label">
              <span>{{ formatPageFilter(filter) }}</span>
            </div>
            <div v-if="!filter.locked" class="content-chrome__chip-remove content-chrome__chip-remove--inline">
              <X :size="11" />
            </div>
          </div>
        </div>
        <div class="content-chrome__toolbar-item" data-measure-key="add-widget">
          <div class="content-chrome__ghost">
            <Plus :size="13" />
            <span>Add Widget</span>
          </div>
        </div>
        <div class="content-chrome__toolbar-item" data-measure-key="add-filter">
          <div class="content-chrome__ghost">
            <Filter :size="13" />
            <span>{{ uiFilters.length ? `+ Filter (${uiFilters.length})` : '+ Filter' }}</span>
          </div>
        </div>
      </div>

      <div
        v-if="timeRangeMenuOpen"
        class="content-chrome__explorer-menu content-chrome__explorer-menu--time"
        role="menu"
        :style="{ top: timeRangeMenuStyle.top, left: timeRangeMenuStyle.left }"
      >
        <button
          v-for="preset in timePresetIds"
          :key="preset"
          class="content-chrome__menu-option"
          role="menuitemradio"
          :aria-checked="timeRangeDraft.preset === preset"
          type="button"
          @click="applyPresetRange(preset)"
        >
          <Check v-if="timeRangeDraft.preset === preset" :size="12" />
          <span v-else class="content-chrome__menu-check-placeholder" />
          {{ timePresetLabel(preset) }}
        </button>
        <div class="content-chrome__menu-divider" />
        <div class="content-chrome__menu-section">
          <span class="content-chrome__menu-section-title">
            Custom range
            <Check v-if="timeRangeDraft.preset === 'custom'" :size="12" />
          </span>
          <label class="content-chrome__menu-field">
            <span>From</span>
            <input v-model="timeRangeDraft.from" type="date" @focus="setCustomRange" />
          </label>
          <label class="content-chrome__menu-field">
            <span>To</span>
            <input v-model="timeRangeDraft.to" type="date" @focus="setCustomRange" />
          </label>
          <button
            class="content-chrome__menu-apply"
            type="button"
            :disabled="!timeRangeDraft.from || !timeRangeDraft.to || timeRangeDraft.from > timeRangeDraft.to"
            @click="applyTimeRangeFromDraft"
          >
            Apply custom range
          </button>
        </div>
      </div>

      <div
        v-if="filterMenuOpen"
        class="content-chrome__explorer-menu workspace-page-header-controls__filter-menu"
        role="menu"
        :style="{ top: filterMenuStyle.top, left: filterMenuStyle.left }"
      >
        <div class="content-chrome__menu-section">
          <label class="content-chrome__menu-field">
            <span>Dimension</span>
            <select v-model="filterDraft.field">
              <option v-for="item in filterDimensionOptions" :key="item.value" :value="item.value">{{ item.label }}</option>
            </select>
          </label>
          <label class="content-chrome__menu-field">
            <span>Operator</span>
            <select v-model="filterDraft.op">
              <option value="in">in</option>
              <option value="not_in">not in</option>
              <option value="eq">equals</option>
              <option value="contains">contains</option>
            </select>
          </label>
          <label class="content-chrome__menu-field">
            <span>Values (comma-separated)</span>
            <input v-model="filterDraft.values" placeholder="frontend, api" />
          </label>
          <label class="workspace-page-header-controls__checkbox-row">
            <input v-model="filterDraft.locked" type="checkbox" />
            <span>Add as locked filter</span>
          </label>
          <div class="workspace-page-header-controls__menu-actions">
            <button class="content-chrome__menu-option" type="button" @click="closeMenus">Cancel</button>
            <button class="content-chrome__menu-apply" type="button" :disabled="!filterDraft.values.trim()" @click="addFilter">
              Add filter
            </button>
          </div>
        </div>
      </div>
    </div>

    <div class="content-chrome__spacer" />

    <div ref="pageActionsRef" class="content-chrome__actions">
      <button
        class="content-chrome__ghost"
        data-testid="page-edit-mode-toggle"
        type="button"
        @click="toggleEditMode"
      >
        <Edit3 :size="13" />
        <span>{{ workspace.editMode ? 'Done' : 'Edit' }}</span>
      </button>
      <button
        class="content-chrome__ghost content-chrome__ghost--overflow"
        data-testid="page-fullscreen-toggle"
        type="button"
        aria-label="Fullscreen"
        @click="requestFullscreen"
      >
        <Maximize2 :size="14" />
      </button>
      <button
        class="content-chrome__ghost content-chrome__ghost--overflow"
        data-testid="page-more-actions"
        type="button"
        aria-haspopup="menu"
        :aria-expanded="pageActionsMenuOpen"
        @click="openPageActionsMenu"
      >
        <MoreHorizontal :size="14" />
      </button>
      <button
        v-if="hasToolbarOverflow"
        class="content-chrome__ghost content-chrome__ghost--overflow"
        data-testid="page-toolbar-overflow"
        type="button"
        aria-haspopup="menu"
        :aria-expanded="overflowMenuOpen"
        @click="openOverflowMenu"
      >
        <MoreHorizontal :size="14" />
      </button>
    </div>

    <div
      v-if="pageActionsMenuOpen"
      class="content-chrome__explorer-menu"
      role="menu"
      :style="pageActionsMenuStyle"
    >
      <button class="content-chrome__menu-option" type="button" @click="renameCurrentPage">Rename</button>
      <button class="content-chrome__menu-option" type="button" @click="duplicateCurrentPage">Duplicate</button>
      <button class="content-chrome__menu-option" type="button" @click="archiveCurrentPage">Archive</button>
    </div>

    <div
      v-if="overflowMenuOpen"
      class="content-chrome__explorer-menu content-chrome__explorer-menu--overflow workspace-page-header-controls__overflow-menu"
      role="menu"
      :style="overflowMenuStyle"
    >
      <div v-if="overflowToolbarFilters.length" class="content-chrome__menu-section">
        <span class="content-chrome__menu-section-title">Filters</span>
        <div
          v-for="filter in overflowToolbarFilters"
          :key="`overflow-${filter.id}`"
          class="workspace-page-header-controls__overflow-filter"
        >
          <span>{{ formatPageFilter(filter) }}</span>
          <div class="workspace-page-header-controls__overflow-filter-actions">
            <button
              class="content-chrome__menu-reset"
              type="button"
              @click="toggleFilterLock(filter.index)"
            >
              {{ filter.locked ? 'Unlock' : 'Lock' }}
            </button>
            <button
              v-if="!filter.locked"
              class="content-chrome__menu-reset"
              type="button"
              @click="removeFilter(filter.index)"
            >
              Remove
            </button>
          </div>
        </div>
      </div>
      <div v-if="!showInlineAddWidget" class="content-chrome__menu-section">
        <span class="content-chrome__menu-section-title">Controls</span>
        <button v-if="!showInlineAddWidget" class="content-chrome__menu-option" type="button" @click="openWidgetPicker">
          <Plus :size="12" />
          Add Widget
        </button>
      </div>
    </div>

    <PageWidgetPicker
      v-if="widgetPickerOpen"
      :open="widgetPickerOpen"
      :widgets="catalogWidgets"
      @close="closeWidgetPicker"
      @add-widget="addCatalogWidget"
      @add-content="addContentWidget"
    />
  </div>
</template>

<style scoped>
.workspace-page-header-controls {
  position: relative;
  display: flex;
  align-items: center;
  gap: 8px;
  flex: 1 1 auto;
  min-width: 0;
}

.workspace-page-header-controls__chip-toggle {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 20px;
  height: 20px;
  border: 0;
  border-right: 1px solid rgba(148, 163, 184, 0.18);
  background: transparent;
  color: #64748b;
  padding: 0;
}

.workspace-page-header-controls__chip-label {
  max-width: min(260px, 28vw);
  overflow: hidden;
}

.workspace-page-header-controls__chip-label span {
  overflow: hidden;
  text-overflow: ellipsis;
}

.workspace-page-header-controls__filter-menu {
  min-width: 288px;
}

.workspace-page-header-controls__filter-menu select,
.workspace-page-header-controls__filter-menu input {
  min-height: 30px;
  border: 1px solid rgba(148, 163, 184, 0.24);
  border-radius: 8px;
  background: #f8fafc;
  padding: 6px 8px;
  color: #0f172a;
  font-size: 12px;
  font-weight: 600;
}

.workspace-page-header-controls__filter-menu select:focus,
.workspace-page-header-controls__filter-menu input:focus {
  outline: 1px solid color-mix(in srgb, var(--cf-accent) 28%, transparent);
  border-color: color-mix(in srgb, var(--cf-accent) 32%, transparent);
}

.workspace-page-header-controls__checkbox-row {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  color: #475569;
  font-size: 12px;
  font-weight: 600;
}

.workspace-page-header-controls__menu-actions {
  display: flex;
  gap: 8px;
}

.workspace-page-header-controls__menu-actions > * {
  flex: 1 1 0;
}

.workspace-page-header-controls__overflow-menu {
  min-width: 280px;
}

.workspace-page-header-controls__overflow-filter {
  display: grid;
  gap: 8px;
  padding: 8px 10px;
  border-radius: 8px;
  background: #f8fafc;
  color: #0f172a;
  font-size: 12px;
  font-weight: 600;
}

.workspace-page-header-controls__overflow-filter-actions {
  display: flex;
  gap: 8px;
}

.workspace-page-header-controls__overflow-filter-actions > * {
  flex: 1 1 0;
}

.dark .workspace-page-header-controls__chip-toggle {
  border-right-color: rgba(103, 122, 160, 0.2);
  color: #8e9abb;
}

.dark .workspace-page-header-controls__filter-menu select,
.dark .workspace-page-header-controls__filter-menu input {
  border-color: rgba(103, 122, 160, 0.24);
  background: rgba(28, 36, 51, 0.92);
  color: #edf2ff;
}

.dark .workspace-page-header-controls__checkbox-row {
  color: #a7b2cd;
}

.dark .workspace-page-header-controls__overflow-filter {
  background: rgba(28, 36, 51, 0.88);
  color: #d7def2;
}
</style>
