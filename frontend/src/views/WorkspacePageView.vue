<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { Plus, Settings2 } from 'lucide-vue-next'
import PageGrid from '../components/PageGrid.vue'
import { useWorkspaceStore } from '../stores/workspace'
import type { FilterSpec, WidgetKind } from '../types/workspace'

const props = defineProps<{
  pageId: string
}>()

const workspace = useWorkspaceStore()

const addWidgetDialog = ref<HTMLDialogElement | null>(null)
const pageFilterDraft = reactive<FilterSpec>({
  field: 'repo',
  op: 'in',
  values: [],
})
const pageFilterInput = ref('')

const page = computed(() => workspace.pages.find((entry) => entry.id === props.pageId) ?? null)
const widgets = computed(() => workspace.pageWidgets[props.pageId] ?? [])
const selectedWidget = computed(() => widgets.value.find((widget) => widget.instance.id === workspace.selectedWidgetId) ?? null)
const pageFilters = computed(() => workspace.pageFilters[props.pageId] ?? [])
const saveState = computed(() => workspace.saveStates[props.pageId] ?? 'idle')

async function load() {
  await workspace.ensurePage(props.pageId)
}

async function openAddWidgetDialog() {
  await workspace.loadWidgetCatalog()
  addWidgetDialog.value?.showModal()
}

function closeAddWidgetDialog() {
  addWidgetDialog.value?.close()
}

async function addCatalogWidget(widgetId: string, kind: WidgetKind) {
  await workspace.addWidgetToPage(props.pageId, { widgetDefinitionId: widgetId, kind })
  closeAddWidgetDialog()
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
  closeAddWidgetDialog()
}

function setEditMode(value: boolean) {
  workspace.setEditMode(value)
  if (!value) {
    workspace.setSelectedWidget(null)
  }
}

function handleLayoutChange(items: Array<{ id: string; layout: { x: number; y: number; w: number; h: number } }>) {
  workspace.queueLayoutSave(props.pageId, items)
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

function addPageFilter() {
  const values = pageFilterInput.value.split(',').map((value) => value.trim()).filter(Boolean)
  if (!values.length) {
    return
  }
  const filters = [...pageFilters.value, { ...pageFilterDraft, values }]
  workspace.setPageFilters(props.pageId, filters)
  pageFilterInput.value = ''
  widgets.value.forEach((widget) => {
    void workspace.refreshWidgetData(widget.instance.id)
  })
}

function removePageFilter(index: number) {
  const filters = pageFilters.value.filter((_filter, filterIndex) => filterIndex !== index)
  workspace.setPageFilters(props.pageId, filters)
  widgets.value.forEach((widget) => {
    void workspace.refreshWidgetData(widget.instance.id)
  })
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

watch(() => props.pageId, () => {
  workspace.setSelectedWidget(null)
  void load()
})

onMounted(() => {
  void load()
})
</script>

<template>
  <section v-if="page" class="workspace-page">
    <header class="workspace-topbar">
      <div>
        <p class="workspace-topbar__eyebrow">Page</p>
        <h1>{{ page.title }}</h1>
        <p v-if="page.description" class="page-toolbar__description">{{ page.description }}</p>
      </div>
      <div class="workspace-topbar__actions">
        <button class="button button--ghost" data-testid="page-edit-mode-toggle" type="button" @click="setEditMode(!workspace.editMode)">
          <span data-testid="page-edit-mode-label">
            {{ workspace.editMode ? 'View mode' : 'Edit mode' }}
          </span>
        </button>
        <button class="button button--primary" data-testid="page-add-widget" type="button" @click="openAddWidgetDialog">
          <Plus :size="16" />
          <span>Add widget</span>
        </button>
      </div>
    </header>

    <section class="page-filter-bar">
      <div class="page-filter-bar__form">
        <select v-model="pageFilterDraft.field">
          <option value="repo">Repository</option>
          <option value="language">Language</option>
          <option value="category">Category</option>
          <option value="subtype">Subtype</option>
          <option value="issueType">Issue type</option>
        </select>
        <input v-model="pageFilterInput" placeholder="Comma-separated values" />
        <button class="button button--ghost" type="button" @click="addPageFilter">Apply filter</button>
      </div>
      <div class="page-filter-bar__chips">
        <button
          v-for="(filter, index) in pageFilters"
          :key="`${filter.field}-${index}`"
          class="filter-chip"
          type="button"
          @click="removePageFilter(index)"
        >
          {{ filter.field }}: {{ filter.values.join(', ') }}
        </button>
      </div>
    </section>

    <div class="workspace-page__body">
      <div class="workspace-page__grid">
        <div v-if="workspace.loadingPages[pageId]" class="workspace-empty-state">
          <strong>Loading page…</strong>
          <p>Resolving widgets, layout, and saved definitions.</p>
        </div>

        <div v-else-if="!widgets.length" class="workspace-empty-state" data-testid="page-empty-state">
          <strong>This page is empty.</strong>
          <p>Add a reusable widget, a section header, or a markdown block to start shaping this dashboard.</p>
          <button class="button button--primary" data-testid="page-add-first-widget" type="button" @click="openAddWidgetDialog">
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

    <dialog ref="addWidgetDialog" class="workspace-dialog" data-testid="add-widget-dialog" @close="closeAddWidgetDialog">
      <div class="workspace-dialog__header">
        <div>
          <p class="workspace-sidebar__eyebrow">Add widget</p>
          <strong>Choose a saved widget or content block</strong>
        </div>
        <button class="icon-button" type="button" @click="closeAddWidgetDialog">
          <Settings2 :size="16" />
        </button>
      </div>

      <section class="workspace-dialog__section">
        <h3>Saved widgets</h3>
        <div class="workspace-dialog__list">
          <button
            v-for="widget in workspace.widgets.filter((entry) => !entry.archived)"
            :key="widget.id"
            class="catalog-picker-card"
            type="button"
            :data-testid="`catalog-widget-option-${widget.id}`"
            @click="addCatalogWidget(widget.id, widget.kind)"
          >
            <strong>{{ widget.title }}</strong>
            <small>{{ widget.description || widget.kind }}</small>
          </button>
        </div>
      </section>

      <section class="workspace-dialog__section">
        <h3>Content blocks</h3>
        <div class="workspace-dialog__list workspace-dialog__list--compact">
          <button class="catalog-picker-card" data-testid="content-widget-option-header" type="button" @click="addContentWidget('header_block')">
            <strong>Header block</strong>
            <small>Editorial section marker</small>
          </button>
          <button class="catalog-picker-card" data-testid="content-widget-option-markdown" type="button" @click="addContentWidget('markdown_block')">
            <strong>Markdown block</strong>
            <small>Narrative notes and guidance</small>
          </button>
          <button class="catalog-picker-card" data-testid="content-widget-option-divider" type="button" @click="addContentWidget('divider_block')">
            <strong>Divider</strong>
            <small>Visual separation</small>
          </button>
        </div>
      </section>
    </dialog>
  </section>
</template>
