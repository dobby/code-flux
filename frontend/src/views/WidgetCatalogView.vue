<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { Plus, Search } from 'lucide-vue-next'
import { useRouter } from 'vue-router'
import { useWorkspaceStore } from '../stores/workspace'
import WidgetPreviewGlyph from '../components/WidgetPreviewGlyph.vue'

const router = useRouter()
const workspace = useWorkspaceStore()

const search = ref('')
const kind = ref('')

const kindLabels: Record<string, string> = {
  time_series: 'Time series',
  distribution: 'Distribution',
  metric_card: 'Metric card',
  data_table: 'Data table',
  calendar_heatmap: 'Calendar heatmap',
  day_explorer: 'Day explorer',
  header_block: 'Header block',
  markdown_block: 'Markdown block',
}

const kindOptions = [
  { value: '', label: 'All kinds' },
  ...Object.entries(kindLabels).map(([value, label]) => ({ value, label })),
]

const filteredWidgets = computed(() => workspace.widgets.filter((widget) => {
  if (widget.archived) {
    return false
  }
  if (kind.value && widget.kind !== kind.value) {
    return false
  }

  const haystack = `${widget.title} ${widget.description || ''} ${widget.tags.join(' ')}`.toLowerCase()
  return haystack.includes(search.value.trim().toLowerCase())
}))

const totalWidgets = computed(() => workspace.widgets.filter((widget) => !widget.archived).length)

async function load() {
  await workspace.initialize()
  await workspace.loadWidgetCatalog()
}

async function duplicate(widgetId: string) {
  const widget = await workspace.duplicateWidget(widgetId)
  await router.push({ name: 'widget-edit', params: { widgetId: widget.id } })
}

function kindLabel(widgetKind: string) {
  return kindLabels[widgetKind] ?? widgetKind
}

onMounted(() => {
  void load()
})
</script>

<template>
  <section class="widget-catalog">
    <header class="widget-catalog__header">
      <div class="widget-catalog__heading">
        <p class="widget-catalog__eyebrow">Widget catalog</p>
        <h1>Reusable building blocks</h1>
        <p>Create, duplicate, and edit query-backed widgets and content blocks.</p>
      </div>

      <RouterLink class="widget-catalog__create button button--primary" data-testid="widget-create-link" :to="{ name: 'widget-new' }">
        <Plus :size="16" />
        <span>Create widget</span>
      </RouterLink>
    </header>

    <section class="widget-catalog__toolbar">
      <label class="widget-catalog__search">
        <Search :size="15" />
        <input v-model="search" data-testid="widget-search" placeholder="Search widgets, tags, and descriptions" />
      </label>

      <select v-model="kind" class="widget-catalog__select" data-testid="widget-kind-filter">
        <option v-for="option in kindOptions" :key="option.value || 'all'" :value="option.value">
          {{ option.label }}
        </option>
      </select>

      <div class="widget-catalog__stats">
        <span>{{ totalWidgets }} widgets</span>
        <span>{{ filteredWidgets.length }} shown</span>
      </div>
    </section>

    <div v-if="filteredWidgets.length" class="widget-catalog__grid">
      <article
        v-for="widget in filteredWidgets"
        :key="widget.id"
        class="widget-card"
        :data-testid="`catalog-card-${widget.id}`"
      >
        <div class="widget-card__preview">
          <WidgetPreviewGlyph
            :kind="widget.kind"
            :subtitle="widget.datasetKey || 'content'"
            :title="widget.title"
          />
        </div>

        <div class="widget-card__body">
          <div class="widget-card__meta">
            <span class="widget-card__dataset">{{ widget.datasetKey || 'content' }}</span>
            <span v-if="widget.isSystem" class="widget-card__badge">seeded</span>
            <span class="widget-card__kind">{{ kindLabel(widget.kind) }}</span>
          </div>

          <strong>{{ widget.title }}</strong>
          <p>{{ widget.description || 'No description yet.' }}</p>

          <div v-if="widget.tags.length" class="widget-card__tags">
            <span v-for="tag in widget.tags" :key="tag" class="widget-card__tag">{{ tag }}</span>
          </div>
        </div>

        <div class="widget-card__actions">
          <RouterLink class="button button--ghost" :data-testid="`widget-edit-${widget.id}`" :to="{ name: 'widget-edit', params: { widgetId: widget.id } }">
            Edit
          </RouterLink>
          <button class="button button--ghost" :data-testid="`widget-duplicate-${widget.id}`" type="button" @click="duplicate(widget.id)">
            Duplicate
          </button>
          <button class="button button--danger" :data-testid="`widget-archive-${widget.id}`" type="button" @click="workspace.archiveWidget(widget.id)">
            Archive
          </button>
        </div>
      </article>
    </div>

    <div v-else class="widget-catalog__empty">
      <strong>No widgets match your filter.</strong>
      <p>Try a different search term or clear the kind filter.</p>
    </div>
  </section>
</template>

<style scoped>
.widget-catalog {
  display: grid;
  gap: 14px;
  padding: 8px 0 8px;
}

.widget-catalog__header {
  display: flex;
  align-items: start;
  justify-content: space-between;
  gap: 16px;
}

.widget-catalog__heading {
  display: grid;
  gap: 4px;
}

.widget-catalog__eyebrow {
  margin: 0;
  color: #64748b;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.12em;
  text-transform: uppercase;
}

.widget-catalog__heading h1 {
  margin: 0;
  color: #0f172a;
  font-size: 22px;
  font-weight: 700;
  letter-spacing: -0.03em;
}

.widget-catalog__heading p {
  margin: 0;
  max-width: 46ch;
  color: #64748b;
  font-size: 12.5px;
  line-height: 1.45;
}

.widget-catalog__create {
  align-self: start;
  flex-shrink: 0;
}

.widget-catalog__toolbar {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 190px auto;
  gap: 10px;
  align-items: center;
  padding: 10px 12px;
  border: 1px solid rgba(148, 163, 184, 0.18);
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.9);
  box-shadow: 0 10px 24px rgba(148, 163, 184, 0.06);
}

.widget-catalog__search {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
  padding: 0 12px;
  border: 1px solid rgba(148, 163, 184, 0.18);
  border-radius: 10px;
  background: rgba(248, 250, 252, 0.95);
  color: #64748b;
}

.widget-catalog__search input,
.widget-catalog__select {
  width: 100%;
  min-width: 0;
  border: none;
  background: transparent;
  color: #0f172a;
  font: inherit;
  outline: none;
}

.widget-catalog__search input {
  padding: 10px 0;
}

.widget-catalog__select {
  padding: 10px 12px;
  border: 1px solid rgba(148, 163, 184, 0.18);
  border-radius: 10px;
  background: rgba(248, 250, 252, 0.95);
}

.widget-catalog__stats {
  display: inline-flex;
  gap: 8px;
  justify-self: end;
  color: #64748b;
  font-size: 11px;
  font-weight: 700;
}

.widget-catalog__stats span {
  padding: 6px 9px;
  border-radius: 999px;
  background: rgba(99, 102, 241, 0.08);
  color: #4f46e5;
}

.widget-catalog__grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.widget-card {
  display: grid;
  gap: 10px;
  padding: 10px;
  border: 1px solid rgba(148, 163, 184, 0.16);
  border-radius: 16px;
  background:
    linear-gradient(180deg, rgba(255, 255, 255, 0.98), rgba(248, 250, 255, 0.96));
  box-shadow: 0 10px 24px rgba(148, 163, 184, 0.08);
}

.widget-card__preview {
  min-height: 108px;
}

.widget-card__body {
  display: grid;
  gap: 6px;
}

.widget-card__meta {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.widget-card__dataset,
.widget-card__badge,
.widget-card__kind,
.widget-card__tag {
  display: inline-flex;
  align-items: center;
  min-height: 22px;
  padding: 0 8px;
  border-radius: 999px;
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 0.02em;
}

.widget-card__dataset {
  background: rgba(99, 102, 241, 0.1);
  color: #4338ca;
}

.widget-card__badge {
  background: rgba(245, 158, 11, 0.14);
  color: #b45309;
}

.widget-card__kind {
  background: rgba(15, 23, 42, 0.06);
  color: #475569;
}

.widget-card strong {
  color: #0f172a;
  font-size: 13.5px;
  font-weight: 700;
  letter-spacing: -0.015em;
}

.widget-card p {
  margin: 0;
  color: #64748b;
  font-size: 11.5px;
  line-height: 1.4;
}

.widget-card__tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.widget-card__tag {
  background: rgba(15, 23, 42, 0.05);
  color: #475569;
}

.widget-card__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.widget-card__actions :deep(.button) {
  min-height: 24px;
  padding: 0 8px;
  border-radius: 8px;
  font-size: 10px;
}

.widget-catalog__empty {
  display: grid;
  gap: 6px;
  padding: 28px;
  border: 1px dashed rgba(148, 163, 184, 0.28);
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.72);
}

.widget-catalog__empty strong {
  color: #0f172a;
  font-size: 15px;
}

.widget-catalog__empty p {
  margin: 0;
  color: #64748b;
  font-size: 13px;
}

@media (max-width: 1100px) {
  .widget-catalog__header,
  .widget-catalog__toolbar {
    grid-template-columns: 1fr;
  }

  .widget-catalog__stats {
    justify-self: start;
  }

  .widget-catalog__grid {
    grid-template-columns: 1fr;
  }
}
</style>
