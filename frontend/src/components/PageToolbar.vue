<script setup lang="ts">
import { computed } from 'vue'
import {
  ChevronRight,
  Clock3,
  Edit3,
  Maximize2,
  Lock,
  LockOpen,
  MoreHorizontal,
  Plus,
  X,
} from 'lucide-vue-next'

type ToolbarFilter = {
  id: string
  field: string
  values: string[]
  op: string
  locked: boolean
}

const props = defineProps<{
  title: string
  timeRangeLabel: string
  filters: ToolbarFilter[]
  editMode: boolean
  visibleFilterCount?: number
}>()

const emit = defineEmits<{
  (event: 'toggle-edit-mode'): void
  (event: 'open-time-range'): void
  (event: 'open-filter-picker'): void
  (event: 'open-overflow'): void
  (event: 'request-fullscreen'): void
  (event: 'toggle-filter-lock', filterIndex: number): void
  (event: 'remove-filter', filterIndex: number): void
  (event: 'open-widget-picker'): void
}>()

const visibleFilterLimit = computed(() => props.visibleFilterCount ?? 4)
const visibleFilters = computed(() => props.filters.slice(0, visibleFilterLimit.value))
const hiddenFilterCount = computed(() => Math.max(0, props.filters.length - visibleFilters.value.length))

function formatFilter(filter: ToolbarFilter) {
  return `${filter.field}: ${filter.values.join(', ')}`
}
</script>

<template>
  <header class="page-toolbar">
    <div class="page-toolbar__controls">
      <span class="page-toolbar__title" data-testid="page-toolbar-title">{{ title }}</span>
      <div class="page-toolbar__divider" aria-hidden="true" />

      <button class="button button--ghost" type="button" data-testid="page-time-range" @click="emit('open-time-range')">
        <Clock3 :size="16" />
        <span>{{ timeRangeLabel }}</span>
        <ChevronRight :size="14" />
      </button>

      <div class="page-toolbar__chips" aria-label="Page filters">
        <div
          v-for="(filter, index) in visibleFilters"
          :key="filter.id"
          class="filter-chip page-toolbar__filter-chip"
          :class="filter.locked ? 'filter-chip' : 'filter-chip--static'"
        >
          <button
            class="filter-chip__lock"
            type="button"
            :title="editMode ? 'Pin or unpin this filter' : 'Filter state'"
            :disabled="!editMode"
            @click="emit('toggle-filter-lock', index)"
          >
            <component :is="filter.locked ? Lock : LockOpen" :size="11" aria-hidden="true" />
          </button>
          <span>{{ formatFilter(filter) }}</span>
          <button
            v-if="!filter.locked"
            class="filter-chip__close"
            type="button"
            aria-label="Remove filter"
            @click.stop="emit('remove-filter', index)"
          >
            <X :size="12" />
          </button>
        </div>

        <button
          v-if="hiddenFilterCount"
          class="filter-chip"
          type="button"
          @click="emit('open-overflow')"
        >
          Filters ({{ hiddenFilterCount }})
        </button>
      </div>

      <button class="button button--ghost" type="button" data-testid="add-filter" @click="emit('open-filter-picker')">
        <Plus :size="16" />
        <span>Filter</span>
      </button>

      <div class="page-toolbar__spacer" />

      <button class="icon-button" type="button" title="Fullscreen" @click="emit('request-fullscreen')">
        <Maximize2 :size="16" />
      </button>

      <button class="button button--ghost" type="button" data-testid="page-edit-mode-toggle" @click="emit('toggle-edit-mode')">
        <Edit3 :size="16" />
        <span>{{ editMode ? 'Done' : 'Edit' }}</span>
      </button>

      <button class="icon-button" type="button" title="More actions" @click="emit('open-overflow')">
        <MoreHorizontal :size="16" />
      </button>
    </div>
  </header>
</template>

<style scoped>
.page-toolbar {
  min-height: 44px;
  padding: 0 0 12px;
  border: 0;
  background: transparent;
}

.page-toolbar__controls {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  flex-wrap: wrap;
}

.page-toolbar__title {
  color: #1e293b;
  font-size: 0.8125rem;
  font-weight: 600;
  letter-spacing: -0.01em;
}

.page-toolbar__divider {
  width: 1px;
  height: 18px;
  background: rgba(148, 163, 184, 0.55);
}

.page-toolbar__spacer {
  flex: 1 1 auto;
  min-width: 1rem;
}

.page-toolbar__chips {
  display: flex;
  gap: 0.35rem;
  flex-wrap: wrap;
}

.filter-chip__close {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  margin-left: 0.25rem;
  width: 15px;
  height: 15px;
  border: 0;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.6);
  color: inherit;
  padding: 0;
}

.filter-chip__lock {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 15px;
  height: 15px;
  border: 0;
  border-radius: 999px;
  background: transparent;
  color: inherit;
  padding: 0;
}
</style>
