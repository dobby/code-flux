<script setup lang="ts">
import { nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { GridStack, type GridStackNode } from 'gridstack'
import 'gridstack/dist/gridstack.min.css'
import WorkspaceWidgetRenderer from './WorkspaceWidgetRenderer.vue'
import type { AnnotationV2, DayDrilldownResponse, PageWidgetResolved, QueryExecutionResponse } from '../types/workspace'

const props = defineProps<{
  pageId: string
  widgets: PageWidgetResolved[]
  widgetData: Record<string, QueryExecutionResponse | undefined>
  widgetAnnotations: Record<string, AnnotationV2[] | undefined>
  drilldown: DayDrilldownResponse | null
  editMode: boolean
  selectedWidgetId: string | null
}>()

const emit = defineEmits<{
  (event: 'select-widget', widgetId: string): void
  (event: 'layout-change', items: Array<{ id: string; layout: { x: number; y: number; w: number; h: number } }>): void
  (event: 'point-click', payload: { widgetId: string; selectedDate: string; dataset: string; seriesField?: string; seriesValue?: string }): void
}>()

const containerRef = ref<HTMLElement | null>(null)
let grid: GridStack | null = null
let suppressChange = false

function initializeGrid() {
  if (!containerRef.value || grid) {
    return
  }

  grid = GridStack.init(
    {
      column: 12,
      cellHeight: 36,
      margin: 12,
      float: true,
      disableDrag: !props.editMode,
      disableResize: !props.editMode,
      animate: false,
      handle: '.widget-tile__header',
    },
    containerRef.value,
  )

  grid.on('change', (_event, items) => {
    if (suppressChange) {
      return
    }
    emit('layout-change', items.map((item) => ({
      id: String(item.id),
      layout: {
        x: item.x ?? 0,
        y: item.y ?? 0,
        w: item.w ?? 6,
        h: item.h ?? 6,
      },
    })))
  })
}

async function syncGrid() {
  await nextTick()
  if (!grid || !containerRef.value) {
    return
  }

  suppressChange = true
  grid.batchUpdate()
  const knownIds = new Set(props.widgets.map((widget) => widget.instance.id))

  for (const node of [...grid.engine.nodes]) {
    if (!knownIds.has(String(node.id))) {
      const element = node.el
      if (element) {
        grid.removeWidget(element, false)
      }
    }
  }

  for (const widget of props.widgets) {
    const selector = `.grid-stack-item[gs-id="${widget.instance.id}"]`
    const element = containerRef.value.querySelector<HTMLElement>(selector)
    if (!element) {
      continue
    }
    if (!grid.engine.nodes.some((node) => String(node.id) === widget.instance.id)) {
      grid.makeWidget(element)
    }
    grid.update(element, {
      id: widget.instance.id,
      x: widget.instance.layout.x,
      y: widget.instance.layout.y,
      w: widget.instance.layout.w,
      h: widget.instance.layout.h,
      minW: widget.instance.layout.minW ?? undefined,
      minH: widget.instance.layout.minH ?? undefined,
      locked: widget.instance.locked,
      noMove: !props.editMode || widget.instance.locked,
      noResize: !props.editMode || widget.instance.locked,
    } as GridStackNode)
  }

  grid.enableMove(props.editMode)
  grid.enableResize(props.editMode)
  grid.batchUpdate(false)
  suppressChange = false
}

watch(() => props.widgets, () => {
  void syncGrid()
}, { deep: true })

watch(() => props.editMode, () => {
  if (grid) {
    grid.enableMove(props.editMode)
    grid.enableResize(props.editMode)
  }
  void syncGrid()
})

onMounted(async () => {
  initializeGrid()
  await syncGrid()
})

onBeforeUnmount(() => {
  grid?.destroy(false)
  grid = null
})
</script>

<template>
  <div ref="containerRef" class="grid-stack page-grid">
    <article
      v-for="widget in widgets"
      :key="widget.instance.id"
      class="grid-stack-item"
      :data-testid="`widget-grid-item-${widget.instance.id}`"
      :gs-id="widget.instance.id"
      :gs-x="widget.instance.layout.x"
      :gs-y="widget.instance.layout.y"
      :gs-w="widget.instance.layout.w"
      :gs-h="widget.instance.layout.h"
      :gs-min-w="widget.instance.layout.minW ?? undefined"
      :gs-min-h="widget.instance.layout.minH ?? undefined"
    >
      <div
        class="grid-stack-item-content widget-tile"
        :class="{
          'widget-tile--selected': selectedWidgetId === widget.instance.id,
          'widget-tile--locked': widget.instance.locked,
        }"
        :data-testid="`widget-tile-${widget.instance.id}`"
        @click="emit('select-widget', widget.instance.id)"
      >
        <header class="widget-tile__header" :data-testid="`widget-header-${widget.instance.id}`">
          <div class="widget-tile__header-copy">
            <div class="widget-tile__meta">
              <span class="dataset-badge">{{ widget.definition?.datasetKey ?? widget.effectiveQuery?.dataset ?? 'content' }}</span>
              <span v-if="widgetAnnotations[widget.instance.id]?.length" class="annotation-badge">
                {{ widgetAnnotations[widget.instance.id]?.length }} notes
              </span>
            </div>
            <strong>{{ widget.effectiveTitle }}</strong>
            <p v-if="widget.effectiveDescription">{{ widget.effectiveDescription }}</p>
          </div>
          <button
            v-if="editMode"
            class="widget-tile__handle"
            type="button"
            title="Drag widget"
            :data-testid="`widget-handle-${widget.instance.id}`"
            @click.stop
          >
            Drag
          </button>
        </header>

        <WorkspaceWidgetRenderer
          :widget="widget"
          :data="widgetData[widget.instance.id]"
          :annotations="widgetAnnotations[widget.instance.id]"
          :drilldown="drilldown"
          :edit-mode="editMode"
          @point-click="(payload) => emit('point-click', {
            widgetId: widget.instance.id,
            selectedDate: payload.selectedDate,
            dataset: widget.effectiveQuery?.dataset ?? 'throughput_daily',
            seriesField: payload.seriesField,
            seriesValue: payload.seriesValue,
          })"
        />
      </div>
    </article>
  </div>
</template>
