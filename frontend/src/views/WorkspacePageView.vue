<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { Check, Plus, X } from 'lucide-vue-next'
import { useRouter } from 'vue-router'
import { useWorkspaceStore } from '../stores/workspace'
import type { FilterOperator, PageFilterState, PageTimeRange, WidgetKind } from '../types/workspace'
import PageGrid from '../components/PageGrid.vue'
import PageToolbar from '../components/PageToolbar.vue'
import PageWidgetPicker from '../components/PageWidgetPicker.vue'

const props = defineProps<{
  pageId: string
}>()

type TimePresetId = 'last_7_days' | 'last_14_days' | 'last_30_days' | 'last_90_days' | 'last_6_months' | 'custom'
type UiFilter = PageFilterState & {
  id: string
}
type TimeRange = {
  label: string
  from: string
  to: string
  preset: TimePresetId
}

const router = useRouter()
const workspace = useWorkspaceStore()

const addWidgetPickerOpen = ref(false)
const timeRangePopoverOpen = ref(false)
const filterPopoverOpen = ref(false)
const overflowOpen = ref(false)

const page = computed(() => workspace.pages.find((entry) => entry.id === props.pageId) ?? null)
const widgets = computed(() => workspace.pageWidgets[props.pageId] ?? [])
const selectedWidget = computed(() => widgets.value.find((widget) => widget.instance.id === workspace.selectedWidgetId) ?? null)
const pageFilters = computed(() => workspace.pageFilters[props.pageId] ?? [])
const pageTimeRange = computed(() => workspace.pageTimeRanges[props.pageId] ?? null)
const saveState = computed(() => workspace.saveStates[props.pageId] ?? 'idle')
const catalogWidgets = computed(() => workspace.widgets.filter((widget) => !widget.archived))
const pageFilterUi = ref<UiFilter[]>([])

const timePresetDefaults = {
  last_7_days: { label: 'Last 7 days', fromOffset: 7 },
  last_14_days: { label: 'Last 14 days', fromOffset: 14 },
  last_30_days: { label: 'Last 30 days', fromOffset: 30 },
  last_90_days: { label: 'Last 90 days', fromOffset: 90 },
  last_6_months: { label: 'Last 6 months', fromOffset: 180 },
} as const

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

const timeRangeButtonLabel = computed(() => uiTimeRange.value.label)
const visibleFilterCount = computed(() => (window.innerWidth > 1400 ? 6 : 4))
const uiFilterList = computed(() => pageFilterUi.value)

function mapTimeOffsetToDate(days: number) {
  const now = new Date()
  const from = new Date(now)
  from.setDate(from.getDate() - days)
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

function syncUiFiltersFromStore() {
  const current = pageFilters.value
  pageFilterUi.value = current.map((filter, index) => {
    return {
      ...filter,
      id: `${props.pageId}-${index}-${filter.field}-${filter.values.join('-')}`,
    }
  })
}

async function applyFiltersToStore() {
  await workspace.setPageFilters(
    props.pageId,
    pageFilterUi.value.map(({ id, ...rest }) => rest),
  )
  refreshAllWidgetData()
}

function syncFilterDraftFromSelection() {
  timeRangeDraft.preset = uiTimeRange.value.preset
  timeRangeDraft.from = uiTimeRange.value.from
  timeRangeDraft.to = uiTimeRange.value.to
}

function refreshAllWidgetData() {
  widgets.value.forEach((widget) => {
    void workspace.refreshWidgetData(widget.instance.id)
  })
}

function openAddWidgetPicker() {
  void workspace.loadWidgetCatalog().then(() => {
    addWidgetPickerOpen.value = true
  })
}

function closeAddWidgetPicker() {
  addWidgetPickerOpen.value = false
}

function openTimeRangePopover() {
  syncFilterDraftFromSelection()
  timeRangePopoverOpen.value = true
}

function openFilterPopover() {
  filterPopoverOpen.value = true
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
  timeRangePopoverOpen.value = false
  refreshAllWidgetData()
}

function closeTimeRangePopover() {
  timeRangePopoverOpen.value = false
}

function setPreset(preset: Exclude<TimePresetId, 'custom'>) {
  const next = buildPresetRange(preset)
  timeRangeDraft.preset = preset
  timeRangeDraft.from = next.from
  timeRangeDraft.to = next.to
}

function setCustomRange() {
  timeRangeDraft.preset = 'custom'
}

async function addFilter() {
  const values = filterDraft.values
    .split(',')
    .map((value) => value.trim())
    .filter(Boolean)

  if (!values.length) {
    return
  }

  pageFilterUi.value = [
    ...pageFilterUi.value,
    {
      id: `${props.pageId}-${Date.now()}`,
      field: filterDraft.field,
      op: filterDraft.op,
      values,
      locked: filterDraft.locked,
    },
  ]
  filterDraft.values = ''
  filterPopoverOpen.value = false
  await applyFiltersToStore()
}

async function removeFilter(index: number) {
  pageFilterUi.value = pageFilterUi.value.filter((_, itemIndex) => itemIndex !== index)
  await applyFiltersToStore()
}

async function toggleFilterLock(index: number) {
  const next = [...pageFilterUi.value]
  next[index] = {
    ...next[index],
    locked: !next[index].locked,
  }
  pageFilterUi.value = next
  await applyFiltersToStore()
}

async function addCatalogWidget(widgetId: string, kind: WidgetKind) {
  await workspace.addWidgetToPage(props.pageId, { widgetDefinitionId: widgetId, kind })
  closeAddWidgetPicker()
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
  closeAddWidgetPicker()
}

async function openOverflowMenu() {
  overflowOpen.value = !overflowOpen.value
}

async function duplicateCurrentPage() {
  if (!page.value) {
    return
  }
  const duplicated = await workspace.duplicatePageAndRefresh(page.value.id)
  overflowOpen.value = false
  await router.push(`/pages/${duplicated.id}`)
}

async function archiveCurrentPage() {
  if (!page.value) {
    return
  }
  await workspace.archivePageAndRefresh(page.value.id)
  await router.push('/')
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
  overflowOpen.value = false
}

async function saveWidgetPanel() {
  if (!selectedWidget.value) {
    return
  }
  await workspace.updateSelectedWidget({
    titleOverride: selectedWidget.value.instance.titleOverride,
    descriptionOverride: selectedWidget.value.instance.descriptionOverride,
    locked: selectedWidget.value.instance.locked,
  })
}

async function removeSelectedWidget() {
  if (!selectedWidget.value) {
    return
  }
  await workspace.removeWidget(selectedWidget.value.instance.id)
  workspace.setSelectedWidget(null)
}

function handleLayoutChange(items: Array<{ id: string; layout: { x: number; y: number; w: number; h: number } }>) {
  workspace.queueLayoutSave(props.pageId, items)
}

async function handlePointClick(payload: {
  widgetId: string
  selectedDate: string
  dataset: string
  seriesField?: string
  seriesValue?: string
}) {
  await workspace.openDrilldown({
    pageId: props.pageId,
    widgetId: payload.widgetId,
    selectedDate: payload.selectedDate,
    dataset: payload.dataset,
    selectedSeries: payload.seriesField && payload.seriesValue
      ? { field: payload.seriesField, value: payload.seriesValue }
      : null,
  })
}

async function load() {
  await workspace.ensurePage(props.pageId)
  syncUiFiltersFromStore()
}

watch(() => props.pageId, () => {
  workspace.setSelectedWidget(null)
  syncUiFiltersFromStore()
  void load()
})

watch(pageFilters, () => {
  syncUiFiltersFromStore()
}, { deep: true })

watch(pageTimeRange, () => {
  syncFilterDraftFromSelection()
}, { deep: true })

onMounted(() => {
  void load()
})
</script>

<template>
  <section v-if="page" class="workspace-page">
    <PageToolbar
      :title="page.title"
      :time-range-label="timeRangeButtonLabel"
      :filters="uiFilterList"
      :edit-mode="workspace.editMode"
      :visible-filter-count="visibleFilterCount"
      @toggle-edit-mode="toggleEditMode"
      @open-time-range="openTimeRangePopover"
      @open-filter-picker="openFilterPopover"
      @open-overflow="openOverflowMenu"
      @request-fullscreen="requestFullscreen"
      @toggle-filter-lock="toggleFilterLock"
      @remove-filter="removeFilter"
      @open-widget-picker="openAddWidgetPicker"
    />

    <div v-if="timeRangePopoverOpen" class="page-popover">
      <div class="page-popover__panel">
        <div class="page-popover__head">
          <strong>Time range</strong>
          <button class="icon-button" type="button" @click="closeTimeRangePopover">
            <X :size="14" />
          </button>
        </div>
        <div class="page-popover__presets">
          <button
            v-for="preset in timePresetIds"
            :key="preset"
            class="button button--ghost"
            :class="{ 'button--primary': timeRangeDraft.preset === preset }"
            type="button"
            @click="setPreset(preset)"
          >
            <Check v-if="timeRangeDraft.preset === preset" :size="14" />
            <span>{{ timePresetLabel(preset) }}</span>
          </button>
        </div>
        <label class="field">
          <span>From</span>
          <input v-model="timeRangeDraft.from" type="date" />
        </label>
        <label class="field">
          <span>To</span>
          <input v-model="timeRangeDraft.to" type="date" />
        </label>
        <div class="page-toolbar__actions">
          <button class="button button--ghost" type="button" @click="timeRangeDraft.preset = 'custom'; setCustomRange()">
            Custom range
          </button>
          <button class="button button--primary" type="button" @click="applyTimeRangeFromDraft">
            Apply
          </button>
        </div>
      </div>
    </div>

    <div v-if="filterPopoverOpen" class="page-popover">
      <div class="page-popover__panel">
        <div class="page-popover__head">
          <strong>Add filter</strong>
          <button class="icon-button" type="button" @click="filterPopoverOpen = false">
            <X :size="14" />
          </button>
        </div>
        <label class="field">
          <span>Dimension</span>
          <select v-model="filterDraft.field">
            <option v-for="item in filterDimensionOptions" :key="item.value" :value="item.value">{{ item.label }}</option>
          </select>
        </label>
        <label class="field">
          <span>Operator</span>
          <select v-model="filterDraft.op">
            <option value="in">in</option>
            <option value="not_in">not in</option>
            <option value="eq">equals</option>
            <option value="contains">contains</option>
          </select>
        </label>
        <label class="field">
          <span>Values (comma-separated)</span>
          <input v-model="filterDraft.values" placeholder="frontend, api" />
        </label>
        <label class="field field--checkbox">
          <input v-model="filterDraft.locked" type="checkbox" />
          <span>Add as locked filter</span>
        </label>
        <div class="page-toolbar__actions">
          <button class="button button--ghost" type="button" @click="filterPopoverOpen = false">Cancel</button>
          <button class="button button--primary" type="button" @click="addFilter">Add filter</button>
        </div>
      </div>
    </div>

    <div class="workspace-page__body">
      <div class="workspace-page__grid">
        <div v-if="workspace.loadingPages[pageId]" class="workspace-empty-state">
          <strong>Loading page…</strong>
          <p>Resolving widgets, layout, and saved definitions.</p>
        </div>

        <div v-else-if="!widgets.length" class="workspace-empty-state" data-testid="page-empty-state">
          <strong>This page is empty.</strong>
          <p>Add a reusable widget, a section header, or a markdown block to start shaping this dashboard.</p>
          <button class="button button--primary" data-testid="page-add-first-widget" type="button" @click="openAddWidgetPicker">
            <Plus :size="16" />
            <span>Add the first widget</span>
          </button>
        </div>

        <PageGrid
          v-else
          :page-id="pageId"
          :widgets="widgets"
          :widget-data="workspace.widgetData"
          :widget-annotations="workspace.widgetAnnotations"
          :drilldown="workspace.drilldown.data"
          :edit-mode="workspace.editMode"
          :selected-widget-id="workspace.selectedWidgetId"
          @select-widget="workspace.setSelectedWidget"
          @layout-change="handleLayoutChange"
          @point-click="handlePointClick"
          @open-widget-picker="openAddWidgetPicker"
        />
      </div>

      <aside v-if="workspace.editMode" class="workspace-properties">
        <div class="workspace-properties__header">
          <div>
            <p class="workspace-sidebar__eyebrow">Properties</p>
            <strong>{{ selectedWidget?.effectiveTitle || 'No widget selected' }}</strong>
          </div>
          <span class="save-badge">{{ saveState }}</span>
        </div>

        <template v-if="selectedWidget">
          <label class="field">
            <span>Title override</span>
            <input v-model="selectedWidget.instance.titleOverride" />
          </label>

          <label class="field">
            <span>Description override</span>
            <textarea v-model="selectedWidget.instance.descriptionOverride" rows="4" />
          </label>

          <label class="field field--checkbox">
            <input v-model="selectedWidget.instance.locked" type="checkbox" />
            <span>Lock widget position</span>
          </label>

          <div class="workspace-properties__actions">
            <button class="button button--primary" type="button" @click="saveWidgetPanel">Save widget overrides</button>
            <button class="button button--danger" type="button" @click="removeSelectedWidget">Remove widget</button>
          </div>
        </template>

        <div v-else class="workspace-empty-state workspace-empty-state--compact">
          <strong>Select a widget.</strong>
          <p>In edit mode the right panel lets you tweak titles, descriptions, and lock state.</p>
        </div>
      </aside>
    </div>

    <PageWidgetPicker
      v-if="addWidgetPickerOpen"
      :open="addWidgetPickerOpen"
      :widgets="catalogWidgets"
      @close="closeAddWidgetPicker"
      @add-widget="addCatalogWidget"
      @add-content="addContentWidget"
    />

    <div v-if="overflowOpen" class="page-overflow">
      <div class="page-overflow__panel">
        <button class="button button--ghost" type="button" @click="renameCurrentPage">Rename</button>
        <button class="button button--ghost" type="button" @click="duplicateCurrentPage">Duplicate</button>
        <button class="button button--danger" type="button" @click="archiveCurrentPage">Archive</button>
        <button class="button button--ghost" type="button" @click="overflowOpen = false">Close</button>
      </div>
    </div>
  </section>
</template>

<style scoped>
.page-popover {
  position: sticky;
  z-index: 6;
  margin-top: -0.25rem;
  display: flex;
  justify-content: flex-end;
}

.page-popover__panel,
.page-overflow__panel {
  width: min(380px, 100%);
  border-radius: 14px;
  border: 1px solid rgba(105, 126, 169, 0.22);
  background: rgba(255, 255, 255, 0.96);
  padding: 0.85rem;
  box-shadow: 0 20px 44px rgba(32, 51, 82, 0.16);
}

.page-popover__head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 0.55rem;
}

.page-popover__presets {
  margin-bottom: 0.6rem;
  display: flex;
  flex-wrap: wrap;
  gap: 0.45rem;
}

.page-overflow {
  position: sticky;
  z-index: 6;
  display: flex;
  justify-content: flex-end;
  margin-top: 0.5rem;
}

.page-overflow__panel {
  width: 220px;
  display: flex;
  flex-direction: column;
  gap: 0.45rem;
}
</style>
