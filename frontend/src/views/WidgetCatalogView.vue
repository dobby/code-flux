<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Plus } from 'lucide-vue-next'
import { useWorkspaceStore } from '../stores/workspace'

const router = useRouter()
const workspace = useWorkspaceStore()
const search = ref('')
const kind = ref('')

const filteredWidgets = computed(() => workspace.widgets.filter((widget) => {
  if (kind.value && widget.kind !== kind.value) {
    return false
  }
  const haystack = `${widget.title} ${widget.description || ''} ${widget.tags.join(' ')}`.toLowerCase()
  return haystack.includes(search.value.trim().toLowerCase())
}))

async function load() {
  await workspace.initialize()
  await workspace.loadWidgetCatalog()
}

async function duplicate(widgetId: string) {
  const widget = await workspace.duplicateWidget(widgetId)
  await router.push({ name: 'widget-edit', params: { widgetId: widget.id } })
}

onMounted(() => {
  void load()
})
</script>

<template>
  <section class="workspace-surface">
    <header class="surface-header">
      <div>
        <p class="workspace-sidebar__eyebrow">Widget catalog</p>
        <h2>Reusable building blocks</h2>
        <p>Create, duplicate, and edit query-backed widgets and content blocks.</p>
      </div>
      <RouterLink class="button button--primary" data-testid="widget-create-link" :to="{ name: 'widget-new' }">
        <Plus :size="16" />
        <span>Create widget</span>
      </RouterLink>
    </header>

    <section class="surface-toolbar">
      <input v-model="search" data-testid="widget-search" placeholder="Search widgets" />
      <select v-model="kind" data-testid="widget-kind-filter">
        <option value="">All kinds</option>
        <option value="time_series">Time series</option>
        <option value="distribution">Distribution</option>
        <option value="metric_card">Metric card</option>
        <option value="data_table">Data table</option>
        <option value="calendar_heatmap">Calendar heatmap</option>
        <option value="day_explorer">Day explorer</option>
        <option value="header_block">Header block</option>
        <option value="markdown_block">Markdown block</option>
      </select>
    </section>

    <div class="catalog-grid">
      <article v-for="widget in filteredWidgets" :key="widget.id" class="catalog-card" :data-testid="`catalog-card-${widget.id}`">
        <div class="catalog-card__meta">
          <span class="dataset-badge">{{ widget.datasetKey || 'content' }}</span>
          <span v-if="widget.isSystem" class="annotation-badge">seeded</span>
        </div>
        <strong>{{ widget.title }}</strong>
        <p>{{ widget.description || 'No description yet.' }}</p>
        <div class="catalog-card__tags">
          <span v-for="tag in widget.tags" :key="tag" class="filter-chip filter-chip--static">{{ tag }}</span>
        </div>
        <div class="catalog-card__actions">
          <RouterLink class="button button--ghost" :data-testid="`widget-edit-${widget.id}`" :to="{ name: 'widget-edit', params: { widgetId: widget.id } }">Edit</RouterLink>
          <button class="button button--ghost" :data-testid="`widget-duplicate-${widget.id}`" type="button" @click="duplicate(widget.id)">Duplicate</button>
          <button class="button button--danger" :data-testid="`widget-archive-${widget.id}`" type="button" @click="workspace.archiveWidget(widget.id)">Archive</button>
        </div>
      </article>
    </div>
  </section>
</template>
