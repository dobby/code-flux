<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch, type CSSProperties } from 'vue'
import Muuri, { type Item } from 'muuri'
import WorkspaceWidgetRenderer from './WorkspaceWidgetRenderer.vue'
import type { AnnotationV2, DayDrilldownResponse, LayoutSpec, PageWidgetResolved, QueryExecutionResponse } from '../types/workspace'

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
  (event: 'layout-change', items: Array<{ id: string; layout: LayoutSpec }>): void
  (event: 'point-click', payload: { widgetId: string; selectedDate: string; dataset: string; seriesField?: string; seriesValue?: string }): void
  (event: 'open-widget-picker'): void
}>()

const gridRef = ref<HTMLElement | null>(null)
const addWidgetSlotId = '__page_add_widget_slot__'
let grid: Muuri | null = null
let resizeObserver: ResizeObserver | null = null
let isSyncingGrid = false
let lastEmittedSignature = ''

const widgetById = computed(() => new Map(props.widgets.map((widget) => [widget.instance.id, widget])))

function toColumnSpan(width: number | undefined | null) {
  if (!width) {
    return 4
  }
  if (width <= 3.5) {
    return 3
  }
  if (width <= 5.5) {
    return 4
  }
  if (width <= 7.5) {
    return 6
  }
  if (width <= 10) {
    return 8
  }
  return 12
}

function toRowSpan(height: number | undefined | null) {
  return Math.max(2, Math.min(16, Math.round(height ?? 6)))
}

function itemStyle(widget: PageWidgetResolved): CSSProperties {
  const span = toColumnSpan(widget.instance.layout.w)
  const rows = toRowSpan(widget.instance.layout.h)
  return {
    width: `${(span / 12) * 100}%`,
    '--widget-min-height': `${rows * 36}px`,
  } as CSSProperties
}

function layoutForOrder(widget: PageWidgetResolved, index: number): LayoutSpec {
  const layout = widget.instance.layout
  return {
    ...layout,
    x: 0,
    y: index,
    w: toColumnSpan(layout.w),
    h: toRowSpan(layout.h),
  }
}

function managedElementIds() {
  if (!gridRef.value) {
    return new Set<string>()
  }
  return new Set(
    Array.from(gridRef.value.querySelectorAll<HTMLElement>('.muuri-grid__item[data-id]'))
      .map((element) => element.dataset.id)
      .filter((value): value is string => Boolean(value)),
  )
}

function orderedWidgetIdsFromGrid() {
  if (!grid) {
    return props.widgets.map((widget) => widget.instance.id)
  }
  return grid.getItems()
    .map((item) => item.getElement()?.dataset.id)
    .filter((id): id is string => Boolean(id && id !== addWidgetSlotId && widgetById.value.has(id)))
}

function emitLayoutFromGrid() {
  if (isSyncingGrid) {
    return
  }

  const orderedIds = orderedWidgetIdsFromGrid()
  const signature = orderedIds.join('|')
  if (signature === lastEmittedSignature) {
    return
  }
  lastEmittedSignature = signature

  emit('layout-change', orderedIds.map((id, index) => {
    const widget = widgetById.value.get(id)
    if (!widget) {
      throw new Error(`Unknown widget in Muuri grid: ${id}`)
    }
    return {
      id,
      layout: layoutForOrder(widget, index),
    }
  }))
}

async function refreshLayout() {
  await nextTick()
  grid?.refreshItems().layout()
}

async function syncGrid() {
  await nextTick()
  if (!grid || !gridRef.value) {
    return
  }

  isSyncingGrid = true
  const currentIds = managedElementIds()
  const existingItems = grid.getItems()
  const removedItems = existingItems.filter((item) => {
    const id = item.getElement()?.dataset.id
    return !id || !currentIds.has(id)
  })
  if (removedItems.length) {
    grid.remove(removedItems, { removeElements: false, layout: false })
  }

  const trackedElements = new Set(grid.getItems().map((item) => item.getElement()).filter(Boolean))
  const newElements = Array.from(gridRef.value.querySelectorAll<HTMLElement>('.muuri-grid__item[data-id]'))
    .filter((element) => !trackedElements.has(element))
  if (newElements.length) {
    grid.add(newElements, { layout: false })
  }

  const orderedItems = [
    ...props.widgets.map((widget) => grid?.getItem(gridRef.value?.querySelector<HTMLElement>(`.muuri-grid__item[data-id="${widget.instance.id}"]`) ?? -1)),
    props.editMode ? grid.getItem(gridRef.value.querySelector<HTMLElement>(`.muuri-grid__item[data-id="${addWidgetSlotId}"]`) ?? -1) : null,
  ].filter((item): item is Item => Boolean(item))

  if (orderedItems.length) {
    grid.sort(orderedItems, { layout: false })
  }

  lastEmittedSignature = props.widgets.map((widget) => widget.instance.id).join('|')
  grid.refreshItems().layout()
  isSyncingGrid = false
}

function initializeGrid() {
  if (!gridRef.value || grid) {
    return
  }

  grid = new Muuri(gridRef.value, {
    items: '.muuri-grid__item',
    dragEnabled: true,
    dragHandle: '.widget-tile__handle',
    dragSort: true,
    dragSortHeuristics: {
      sortInterval: 50,
    },
    dragStartPredicate(item, event) {
      const id = item.getElement()?.dataset.id
      const widget = id ? widgetById.value.get(id) : null
      if (!props.editMode || id === addWidgetSlotId || widget?.instance.locked) {
        return false
      }
      return Muuri.ItemDrag.defaultStartPredicate(item, event, { distance: 6 })
    },
    layout: {
      fillGaps: true,
      rounding: true,
    },
    layoutDuration: 260,
    layoutEasing: 'ease',
  })

  ;(gridRef.value as HTMLElement & { muuri?: Muuri }).muuri = grid
  grid.on('move', emitLayoutFromGrid)
  grid.on('dragReleaseEnd', emitLayoutFromGrid)

  resizeObserver = new ResizeObserver(() => {
    void refreshLayout()
  })
  resizeObserver.observe(gridRef.value)
}

function handleTileClick(widget: PageWidgetResolved) {
  emit('select-widget', widget.instance.id)
}

onMounted(async () => {
  initializeGrid()
  await syncGrid()
})

onBeforeUnmount(() => {
  resizeObserver?.disconnect()
  resizeObserver = null
  grid?.destroy(false)
  grid = null
})

watch(
  () => props.widgets.map((widget) => [
    widget.instance.id,
    widget.instance.layout.x,
    widget.instance.layout.y,
    widget.instance.layout.w,
    widget.instance.layout.h,
    widget.instance.locked,
  ].join(':')).join('|'),
  () => {
    void syncGrid()
  },
)

watch(() => props.editMode, () => {
  void syncGrid()
})

watch(() => [props.widgetData, props.widgetAnnotations, props.drilldown], () => {
  void refreshLayout()
}, { deep: true })
</script>

<template>
  <div ref="gridRef" class="muuri-grid page-grid" data-testid="muuri-page-grid">
    <article
      v-for="(widget, index) in widgets"
      :key="widget.instance.id"
      class="muuri-grid__item"
      :class="{ 'muuri-grid__item--locked': widget.instance.locked }"
      :data-id="widget.instance.id"
      :data-testid="`widget-grid-item-${widget.instance.id}`"
      :data-grid-x="layoutForOrder(widget, index).x"
      :data-grid-y="layoutForOrder(widget, index).y"
      :data-grid-w="layoutForOrder(widget, index).w"
      :data-grid-h="layoutForOrder(widget, index).h"
      :gs-x="layoutForOrder(widget, index).x"
      :gs-y="layoutForOrder(widget, index).y"
      :gs-w="layoutForOrder(widget, index).w"
      :gs-h="layoutForOrder(widget, index).h"
      :style="itemStyle(widget)"
    >
      <div
        class="muuri-grid__item-content widget-tile"
        :class="{
          'widget-tile--selected': selectedWidgetId === widget.instance.id,
          'widget-tile--locked': widget.instance.locked,
          'widget-tile--editable': editMode,
        }"
        :data-testid="`widget-tile-${widget.instance.id}`"
        @click="handleTileClick(widget)"
      >
        <div v-if="editMode" class="widget-tile__edit-controls">
          <span class="widget-tile__span-badge">
            span {{ toColumnSpan(widget.instance.layout.w) }}/12
          </span>
          <button
            class="widget-tile__handle"
            type="button"
            :title="widget.instance.locked ? 'Widget is locked' : 'Drag widget'"
            :data-testid="`widget-handle-${widget.instance.id}`"
            :disabled="widget.instance.locked"
            @click.stop
          >
            Drag
          </button>
        </div>

        <header
          v-if="widget.instance.kind !== 'metric_card'"
          class="widget-tile__header"
          :data-testid="`widget-header-${widget.instance.id}`"
        >
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

    <article
      v-if="editMode"
      class="muuri-grid__item muuri-grid__item--add"
      :data-id="addWidgetSlotId"
      data-add-widget-slot="1"
      data-testid="widget-grid-add-slot"
      style="width: 100%; --widget-min-height: 108px;"
      @click="emit('open-widget-picker')"
    >
      <div class="muuri-grid__item-content widget-tile page-grid__add-slot">
        <button class="page-grid__add-slot-btn" type="button">+ Add widget</button>
      </div>
    </article>
  </div>
</template>

<style scoped>
.muuri-grid {
  position: relative;
  min-height: 620px;
  margin: -7px;
}

.muuri-grid__item {
  position: absolute;
  z-index: 1;
  box-sizing: border-box;
  padding: 7px;
}

.muuri-grid__item.muuri-item-dragging {
  z-index: 4;
}

.muuri-grid__item.muuri-item-releasing {
  z-index: 3;
}

.muuri-grid__item-content.widget-tile {
  position: relative;
  min-height: var(--widget-min-height);
  height: auto;
}

.widget-tile--editable {
  padding-top: 2.35rem;
}

.widget-tile__edit-controls {
  position: absolute;
  top: 0.55rem;
  right: 0.6rem;
  z-index: 2;
  display: inline-flex;
  align-items: center;
  gap: 0.4rem;
}

.widget-tile__span-badge {
  border-radius: 999px;
  border: 1px solid rgba(99, 102, 241, 0.18);
  padding: 0.15rem 0.45rem;
  color: #51617d;
  background: rgba(99, 102, 241, 0.06);
  font-size: 0.66rem;
  font-weight: 700;
  letter-spacing: 0.02em;
}

.widget-tile__handle {
  border: 0;
  border-radius: 7px;
  padding: 0.25rem 0.5rem;
  font: inherit;
  cursor: move;
  background: color-mix(in srgb, var(--cf-accent) 8%, transparent);
  color: #51617d;
  font-size: 0.75rem;
  font-weight: 700;
}

.widget-tile__handle:disabled {
  cursor: not-allowed;
  opacity: 0.55;
}

.page-grid__add-slot {
  border: 1px dashed rgba(148, 163, 184, 0.6);
  display: grid;
  place-items: center;
  cursor: pointer;
  background: rgba(248, 250, 255, 0.92);
}

.page-grid__add-slot-btn {
  border: 1px dashed rgba(148, 163, 184, 0.72);
  border-radius: 8px;
  color: #51617d;
  background: transparent;
  padding: 0.55rem 0.85rem;
  font: inherit;
  font-weight: 600;
}

@media (max-width: 900px) {
  .muuri-grid__item {
    width: 100% !important;
  }
}
</style>
