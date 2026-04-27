<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import type { WidgetDefinition, WidgetKind } from '../types/workspace'
import WidgetPreviewGlyph from './WidgetPreviewGlyph.vue'

const props = defineProps<{
  open: boolean
  widgets: WidgetDefinition[]
}>()

const emit = defineEmits<{
  (event: 'close'): void
  (event: 'add-content', kind: WidgetKind): void
  (event: 'add-widget', id: string, kind: WidgetKind): void
}>()

const search = ref('')
const dialogRef = ref<HTMLDialogElement | null>(null)

const filteredWidgets = computed(() => {
  const term = search.value.trim().toLowerCase()
  if (!term) {
    return props.widgets
  }
  return props.widgets.filter((widget) =>
    widget.title.toLowerCase().includes(term) ||
    (widget.description ?? '').toLowerCase().includes(term) ||
    widget.kind.toLowerCase().includes(term),
  )
})

function closePicker() {
  if (dialogRef.value?.open) {
    dialogRef.value.close()
    return
  }
  emit('close')
}

function addCatalog(widgetId: string, kind: WidgetKind) {
  emit('add-widget', widgetId, kind)
}

function addContent(kind: WidgetKind) {
  emit('add-content', kind)
}

function handleDialogClose() {
  emit('close')
}

async function syncDialog(open: boolean) {
  await nextTick()
  const dialog = dialogRef.value
  if (!dialog) {
    return
  }
  if (open && !dialog.open) {
    dialog.showModal()
  } else if (!open && dialog.open) {
    dialog.close()
  }
}

watch(() => props.open, (open) => {
  void syncDialog(open)
})

onMounted(() => {
  void syncDialog(props.open)
})

onBeforeUnmount(() => {
  if (dialogRef.value?.open) {
    dialogRef.value.close()
  }
})
</script>

<template>
  <dialog ref="dialogRef" class="page-widget-picker__dialog" aria-label="Widget picker" data-testid="add-widget-dialog" @close="handleDialogClose">
    <div class="page-widget-picker">
      <header class="workspace-dialog__header">
        <div>
          <p class="workspace-sidebar__eyebrow">Add widget</p>
          <strong>Add from catalog or insert a content block</strong>
        </div>
        <button class="icon-button" type="button" @click="closePicker">✕</button>
      </header>

      <label class="field">
        <span>Search</span>
        <input v-model="search" placeholder="Search saved widgets" />
      </label>

      <section class="workspace-dialog__section">
        <h3>Saved widgets</h3>
        <div class="workspace-dialog__list">
          <button
            v-for="widget in filteredWidgets"
            :key="widget.id"
            class="catalog-picker-card"
            type="button"
            :data-testid="`catalog-widget-option-${widget.id}`"
            @click="addCatalog(widget.id, widget.kind)"
          >
            <WidgetPreviewGlyph
              class="catalog-picker-card__preview"
              :kind="widget.kind"
              :subtitle="widget.datasetKey || 'content'"
              :title="widget.title"
            />
            <strong>{{ widget.title }}</strong>
            <small>{{ widget.description || widget.kind }}</small>
          </button>
        </div>
      </section>

      <section class="workspace-dialog__section">
        <h3>Content blocks</h3>
        <div class="workspace-dialog__list workspace-dialog__list--compact">
          <button class="catalog-picker-card" data-testid="content-widget-option-header" type="button" @click="addContent('header_block')">
            <strong>Header block</strong>
            <small>Editorial section marker</small>
          </button>
          <button class="catalog-picker-card" data-testid="content-widget-option-markdown" type="button" @click="addContent('markdown_block')">
            <strong>Markdown block</strong>
            <small>Narrative notes and guidance</small>
          </button>
          <button class="catalog-picker-card" data-testid="content-widget-option-divider" type="button" @click="addContent('divider_block')">
            <strong>Divider</strong>
            <small>Visual separation</small>
          </button>
        </div>
      </section>
    </div>
  </dialog>
</template>

<style scoped>
.page-widget-picker__dialog {
  position: fixed;
  top: 50%;
  left: 50%;
  z-index: 40;
  transform: translate(-50%, -50%);
  border: none;
  border-radius: 12px;
  width: min(640px, calc(100vw - 3rem));
  max-height: min(520px, calc(100vh - 4rem));
  padding: 0;
  background: transparent;
  overflow: visible;
}

.page-widget-picker__dialog::backdrop {
  background: rgba(7, 11, 20, 0.45);
  backdrop-filter: blur(8px);
}

.page-widget-picker {
  border-radius: 12px;
  border: 1px solid rgba(105, 126, 169, 0.18);
  background: #ffffff;
  color: inherit;
  width: min(640px, calc(100vw - 3rem));
  max-height: min(520px, calc(100vh - 4rem));
  overflow: auto;
  padding: 0.9rem;
  display: grid;
  gap: 0.75rem;
  box-shadow:
    0 18px 44px rgba(18, 38, 76, 0.16);
}

.page-widget-picker .catalog-picker-card {
  display: grid;
  align-content: start;
  min-height: 118px;
  gap: 5px;
  padding: 8px;
  text-align: left;
  overflow: hidden;
}

.page-widget-picker .catalog-picker-card strong,
.page-widget-picker .catalog-picker-card small {
  display: block;
  min-width: 0;
}

.page-widget-picker .catalog-picker-card strong {
  font-size: 12px;
  line-height: 1.25;
}

.page-widget-picker .catalog-picker-card small {
  color: var(--cf-text-secondary);
  font-size: 10px;
  line-height: 1.35;
  display: -webkit-box;
  overflow: hidden;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}

.page-widget-picker .workspace-dialog__list {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(150px, 1fr));
  gap: 8px;
}

.catalog-picker-card__preview {
  height: 48px;
  min-height: 48px !important;
  max-height: 48px;
  padding: 5px;
  border-radius: 6px;
  overflow: hidden;
}

.catalog-picker-card__preview :deep(.preview-glyph__footer) {
  display: none;
}

.catalog-picker-card__preview :deep(.preview-glyph__metric-value) {
  font-size: 16px;
}

.catalog-picker-card__preview :deep(.preview-glyph__donut) {
  width: 34px;
  height: 34px;
  box-shadow: inset 0 0 0 8px rgba(255, 255, 255, 0.9);
}

.catalog-picker-card__preview :deep(.preview-glyph__legend) {
  display: none;
}

.catalog-picker-card__preview :deep(.preview-glyph__heatmap) {
  gap: 3px;
  padding: 2px;
}

.catalog-picker-card__preview :deep(.preview-glyph__heatmap-cell) {
  border-radius: 3px;
}

@media (max-width: 680px) {
  .page-widget-picker__dialog {
    width: calc(100vw - 1.5rem);
  }

  .page-widget-picker .workspace-dialog__list {
    grid-template-columns: 1fr;
  }
}
</style>
