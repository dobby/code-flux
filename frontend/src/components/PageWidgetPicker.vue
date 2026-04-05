<script setup lang="ts">
import { computed, ref } from 'vue'
import type { WidgetDefinition, WidgetKind } from '../types/workspace'

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
  emit('close')
}

function addCatalog(widgetId: string, kind: WidgetKind) {
  emit('add-widget', widgetId, kind)
}

function addContent(kind: WidgetKind) {
  emit('add-content', kind)
}
</script>

<template>
  <dialog :open="open" class="page-widget-picker__dialog" aria-label="Widget picker">
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
  border: none;
  border-radius: 18px;
  width: min(960px, calc(100vw - 3rem));
  max-height: calc(100vh - 4rem);
  padding: 0;
  background: transparent;
}

.page-widget-picker__dialog::backdrop {
  background: rgba(7, 11, 20, 0.45);
  backdrop-filter: blur(8px);
}

.page-widget-picker {
  border-radius: 18px;
  border: 1px solid rgba(105, 126, 169, 0.18);
  background: rgba(255, 255, 255, 0.92);
  color: inherit;
  width: min(960px, calc(100vw - 3rem));
  max-height: calc(100vh - 4rem);
  overflow: auto;
  padding: 1rem;
  display: grid;
  gap: 0.9rem;
}

.page-widget-picker .catalog-picker-card {
  text-align: left;
}

@media (max-width: 680px) {
  .page-widget-picker__dialog {
    width: calc(100vw - 1.5rem);
  }
}
</style>
