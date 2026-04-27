<script setup lang="ts">
import { computed, onMounted, watch } from 'vue'
import { Plus } from 'lucide-vue-next'
import { useWorkspaceStore } from '../stores/workspace'
import MuuriGrid from '../components/MuuriGrid.vue'

const props = defineProps<{
  pageId: string
}>()

const workspace = useWorkspaceStore()

const page = computed(() => workspace.pages.find((entry) => entry.id === props.pageId) ?? null)
const widgets = computed(() => workspace.pageWidgets[props.pageId] ?? [])
const selectedWidget = computed(() => widgets.value.find((widget) => widget.instance.id === workspace.selectedWidgetId) ?? null)
const saveState = computed(() => workspace.saveStates[props.pageId] ?? 'idle')

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
}

watch(() => props.pageId, () => {
  workspace.setSelectedWidget(null)
  workspace.closePageWidgetPicker()
  void load()
})

onMounted(() => {
  void load()
})
</script>

<template>
  <section v-if="page" class="workspace-page">
    <div class="workspace-page__body" :class="{ 'workspace-page__body--editing': workspace.editMode && selectedWidget }">
      <div class="workspace-page__grid">
        <div v-if="workspace.loadingPages[pageId]" class="workspace-empty-state">
          <strong>Loading page…</strong>
          <p>Resolving widgets, layout, and saved definitions.</p>
        </div>

        <div v-else-if="!widgets.length" class="workspace-empty-state" data-testid="page-empty-state">
          <strong>This page is empty.</strong>
          <p>Add a reusable widget, a section header, or a markdown block to start shaping this dashboard.</p>
          <button class="button button--primary" data-testid="page-add-first-widget" type="button" @click="workspace.openPageWidgetPicker()">
            <Plus :size="16" />
            <span>Add the first widget</span>
          </button>
        </div>

        <MuuriGrid
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
          @open-widget-picker="workspace.openPageWidgetPicker()"
        />
      </div>

      <aside v-if="workspace.editMode && selectedWidget" class="workspace-properties">
        <div class="workspace-properties__header">
          <div>
            <p class="workspace-sidebar__eyebrow">Properties</p>
            <strong>{{ selectedWidget?.effectiveTitle || 'No widget selected' }}</strong>
          </div>
          <span class="save-badge">{{ saveState }}</span>
        </div>

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
      </aside>
    </div>
  </section>
</template>
