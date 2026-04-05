<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ChevronLeft, LoaderCircle, Sparkles } from 'lucide-vue-next'
import { getWidgetDefinition, previewQuery } from '../api/workspace'
import WidgetUsageBanner from '../components/WidgetUsageBanner.vue'
import WorkspaceWidgetRenderer from '../components/WorkspaceWidgetRenderer.vue'
import { useWorkspaceStore } from '../stores/workspace'
import type { QueryExecutionResponse, WidgetDefinition, WidgetKind, WidgetQuerySpec, WidgetVizSpec } from '../types/workspace'

const props = defineProps<{
  widgetId?: string
}>()

const router = useRouter()
const workspace = useWorkspaceStore()

const form = reactive<{
  title: string
  description: string
  tags: string
  kind: WidgetKind
  datasetKey: string
  measureField: string
  aggregation: 'sum' | 'count' | 'avg' | 'min' | 'max'
  groupBy: string
  timeBucket: 'day' | 'week' | 'month'
  chartType: string
  markdown: string
  body: string
}>({
  title: '',
  description: '',
  tags: '',
  kind: 'time_series',
  datasetKey: 'throughput_daily',
  measureField: 'lines_added',
  aggregation: 'sum',
  groupBy: 'repo',
  timeBucket: 'day',
  chartType: 'line',
  markdown: '',
  body: '',
})

const preview = ref<QueryExecutionResponse | null>(null)
const previewError = ref<string | null>(null)
const previewSucceeded = ref(false)
const loading = ref(false)
const previewLoading = ref(false)
const previewTimer = ref<number | null>(null)
const currentWidget = ref<WidgetDefinition | null>(null)

const isEditing = computed(() => Boolean(props.widgetId))
const isQueryWidget = computed(() => !['header_block', 'markdown_block', 'divider_block', 'spacer_block', 'day_explorer'].includes(form.kind))
const currentDataset = computed(() => workspace.datasets.find((dataset) => dataset.key === form.datasetKey))
const datasetOptions = computed(() => workspace.datasets.filter((dataset) => dataset.widgetKinds.includes(form.kind)))
const usageBannerDescription = computed(() => {
  if (!currentWidget.value || currentWidget.value.usageCount <= 0) {
    return ''
  }

  const pages = currentWidget.value.usedOnPages.slice(0, 3)
  const suffix = currentWidget.value.usedOnPages.length > 3
    ? ` and ${currentWidget.value.usedOnPages.length - 3} more`
    : ''
  const label = currentWidget.value.usageCount === 1 ? 'page' : 'pages'
  return `This widget is currently used on ${currentWidget.value.usageCount} ${label}: ${pages.join(', ')}${suffix}. Duplicate it before making page-specific changes.`
})
const controlSummary = computed(() => {
  if (isQueryWidget.value) {
    return `${currentDataset.value?.label || 'Dataset'} - ${form.groupBy || 'No grouping'}`
  }
  if (form.kind === 'markdown_block') {
    return 'Narrative block'
  }
  if (form.kind === 'header_block') {
    return 'Section header'
  }
  return 'Layout block'
})

function buildQuerySpec(): WidgetQuerySpec | null {
  if (!isQueryWidget.value) {
    return form.kind === 'day_explorer'
      ? {
          dataset: 'day_activity',
          groupBy: [],
          filters: [],
          sort: [],
        }
      : null
  }

  return {
    dataset: form.datasetKey as WidgetQuerySpec['dataset'],
    timeBucket: form.kind === 'time_series' || form.kind === 'calendar_heatmap' ? form.timeBucket : null,
    measure: {
      field: form.measureField,
      aggregation: form.aggregation,
    },
    groupBy: form.groupBy ? [form.groupBy] : [],
    filters: [],
    sort: [],
    limit: form.kind === 'distribution' ? 10 : null,
  }
}

function buildVizSpec(): WidgetVizSpec {
  if (form.kind === 'markdown_block') {
    return { markdown: form.markdown || 'Add your note here.' }
  }
  if (form.kind === 'header_block') {
    return { body: form.body || 'Introduce the next section of this page.' }
  }
  return {
    chartType: form.chartType,
    showLegend: true,
    showAnnotations: true,
    emptyStateMessage: 'No matching data',
  }
}

function clearPreviewTimer() {
  if (previewTimer.value != null) {
    window.clearTimeout(previewTimer.value)
    previewTimer.value = null
  }
}

function schedulePreview() {
  clearPreviewTimer()
  if (!isQueryWidget.value) {
    previewSucceeded.value = true
    previewError.value = null
    return
  }

  previewTimer.value = window.setTimeout(() => {
    void runPreview()
  }, 180)
}

async function runPreview() {
  if (!isQueryWidget.value) {
    return
  }

  previewLoading.value = true
  previewError.value = null
  previewSucceeded.value = false
  try {
    preview.value = await previewQuery({
      widgetKind: form.kind,
      query: buildQuerySpec(),
    })
    previewSucceeded.value = true
  } catch (caught) {
    previewError.value = caught instanceof Error ? caught.message : 'Preview failed'
  } finally {
    previewLoading.value = false
  }
}

async function save() {
  if (isQueryWidget.value && !previewSucceeded.value) {
    previewError.value = 'Run a valid preview before saving this widget.'
    return
  }

  loading.value = true
  try {
    const widget = await workspace.saveWidgetDefinition({
      widgetId: props.widgetId,
      title: form.title,
      description: form.description || null,
      tags: form.tags.split(',').map((tag) => tag.trim()).filter(Boolean),
      kind: form.kind,
      datasetKey: isQueryWidget.value ? form.datasetKey : form.kind === 'day_explorer' ? 'day_activity' : null,
      querySpec: buildQuerySpec(),
      vizSpec: buildVizSpec(),
    })
    await router.push({ name: 'widget-edit', params: { widgetId: widget.id } })
  } finally {
    loading.value = false
  }
}

async function duplicateAndCustomize() {
  if (!props.widgetId) {
    return
  }
  const widget = await workspace.duplicateWidget(props.widgetId)
  await router.push({ name: 'widget-edit', params: { widgetId: widget.id } })
}

function hydrate(widget: WidgetDefinition) {
  currentWidget.value = widget
  form.title = widget.title
  form.description = widget.description || ''
  form.tags = widget.tags.join(', ')
  form.kind = widget.kind
  form.datasetKey = widget.datasetKey || 'throughput_daily'
  form.chartType = widget.vizSpec.chartType || 'line'
  form.markdown = widget.vizSpec.markdown || ''
  form.body = widget.vizSpec.body || ''
  form.measureField = widget.querySpec?.measure?.field || 'lines_added'
  form.aggregation = widget.querySpec?.measure?.aggregation || 'sum'
  form.groupBy = widget.querySpec?.groupBy[0] || ''
  form.timeBucket = widget.querySpec?.timeBucket || 'day'
}

watch(
  () => form.kind,
  () => {
    const options = datasetOptions.value
    if (options.length && !options.some((dataset) => dataset.key === form.datasetKey)) {
      form.datasetKey = options[0].key
    }
    if (form.kind === 'markdown_block') {
      form.chartType = 'line'
    }
    schedulePreview()
  },
)

watch(
  () => [form.datasetKey, form.measureField, form.aggregation, form.groupBy, form.timeBucket, form.chartType, form.markdown, form.body, form.title, form.description, form.tags].join('::'),
  () => {
    schedulePreview()
  },
)

onMounted(async () => {
  await workspace.initialize()
  await workspace.loadWidgetCatalog()

  if (props.widgetId) {
    hydrate(await getWidgetDefinition(props.widgetId))
  }

  schedulePreview()
})
</script>

<template>
  <section class="widget-editor">
    <header class="widget-editor__header">
      <RouterLink class="widget-editor__back" :to="{ name: 'widgets' }">
        <ChevronLeft :size="15" />
        <span>Catalog</span>
      </RouterLink>

      <div class="widget-editor__title">
        <p class="widget-editor__eyebrow">Widget editor</p>
        <h1>{{ isEditing ? 'Edit widget' : 'Create widget' }}</h1>
        <p>{{ controlSummary }}</p>
      </div>

      <div class="widget-editor__actions">
        <button class="widget-editor__primary button button--primary" data-testid="widget-save" type="button" :disabled="loading" @click="save">
          <LoaderCircle v-if="loading" class="spin" :size="16" />
          <Sparkles v-else :size="16" />
          <span>{{ loading ? 'Saving…' : 'Save widget' }}</span>
        </button>
      </div>
    </header>

    <WidgetUsageBanner
      v-if="isEditing && currentWidget && currentWidget.usageCount > 0"
      :title="`Used on ${currentWidget.usageCount} ${currentWidget.usageCount === 1 ? 'page' : 'pages'}`"
      :description="usageBannerDescription"
      @duplicate="duplicateAndCustomize"
    />

    <div class="widget-editor__layout">
      <form class="widget-editor__panel widget-editor__panel--form" @submit.prevent="save">
        <section class="editor-section">
          <div class="editor-section__header">
            <p>Data</p>
            <span>Source, metric, and grouping</span>
          </div>

          <label class="field">
            <span>Template</span>
            <select v-model="form.kind" data-testid="widget-kind">
              <option value="time_series">Time series</option>
              <option value="distribution">Distribution</option>
              <option value="metric_card">Metric card</option>
              <option value="data_table">Data table</option>
              <option value="calendar_heatmap">Calendar heatmap</option>
              <option value="day_explorer">Day explorer</option>
              <option value="header_block">Header block</option>
              <option value="markdown_block">Markdown block</option>
              <option value="divider_block">Divider</option>
            </select>
          </label>

          <label v-if="isQueryWidget" class="field">
            <span>Source</span>
            <select v-model="form.datasetKey" data-testid="widget-dataset">
              <option v-for="dataset in datasetOptions" :key="dataset.key" :value="dataset.key">
                {{ dataset.label }}
              </option>
            </select>
          </label>

          <div v-if="isQueryWidget" class="field-grid">
            <label class="field">
              <span>Metric</span>
              <select v-model="form.measureField" data-testid="widget-measure">
                <option v-for="measure in currentDataset?.measures ?? []" :key="measure.key" :value="measure.key">{{ measure.label }}</option>
              </select>
            </label>

            <label class="field">
              <span>Aggregation</span>
              <select v-model="form.aggregation" data-testid="widget-aggregation">
                <option value="sum">Sum</option>
                <option value="count">Count</option>
                <option value="avg">Average</option>
                <option value="min">Min</option>
                <option value="max">Max</option>
              </select>
            </label>
          </div>

          <div v-if="isQueryWidget" class="field-grid">
            <label class="field">
              <span>Group by</span>
              <select v-model="form.groupBy" data-testid="widget-group-by">
                <option value="">None</option>
                <option v-for="dimension in currentDataset?.dimensions ?? []" :key="dimension.key" :value="dimension.key">{{ dimension.label }}</option>
              </select>
            </label>

            <label v-if="form.kind === 'time_series' || form.kind === 'calendar_heatmap'" class="field">
              <span>Time bucket</span>
              <select v-model="form.timeBucket" data-testid="widget-time-bucket">
                <option value="day">Day</option>
                <option value="week">Week</option>
                <option value="month">Month</option>
              </select>
            </label>
          </div>
        </section>

        <section v-if="isQueryWidget" class="editor-section">
          <div class="editor-section__header">
            <p>Visualization</p>
            <span>Choose the shape of the output</span>
          </div>

          <div class="viz-pills">
            <button
              v-for="option in ['line', 'area', 'bar', 'donut', 'pie']"
              :key="option"
              class="viz-pill"
              :class="{ 'viz-pill--active': form.chartType === option }"
              type="button"
              @click="form.chartType = option"
            >
              {{ option }}
            </button>
          </div>

          <button class="button button--ghost widget-editor__preview-trigger" type="button" @click="runPreview">
            {{ previewLoading ? 'Refreshing preview…' : 'Run preview' }}
          </button>
        </section>

        <section class="editor-section">
          <div class="editor-section__header">
            <p>Filters</p>
            <span>Widget-specific filters</span>
          </div>

          <div class="filter-chip-row">
            <span class="filter-chip filter-chip--static">repo: frontend</span>
            <span class="filter-chip filter-chip--static">branch: main</span>
            <button class="filter-chip filter-chip--ghost" type="button">+ Add filter</button>
          </div>

          <p class="editor-section__note">
            Filters narrow this widget further than the page scope. The toolbar and page-level filtering model are handled elsewhere.
          </p>
        </section>

        <section class="editor-section">
          <div class="editor-section__header">
            <p>Display</p>
            <span>Title and description</span>
          </div>

          <label class="field">
            <span>Title</span>
            <input v-model="form.title" data-testid="widget-title" required />
          </label>

          <label class="field">
            <span>Description</span>
            <textarea v-model="form.description" data-testid="widget-description" rows="3" />
          </label>

          <label class="field">
            <span>Tags</span>
            <input v-model="form.tags" data-testid="widget-tags" placeholder="repo-state, weekly, review" />
          </label>

          <label v-if="form.kind === 'markdown_block'" class="field">
            <span>Markdown</span>
            <textarea v-model="form.markdown" data-testid="widget-markdown" rows="8" />
          </label>

          <label v-else-if="form.kind === 'header_block'" class="field">
            <span>Supporting text</span>
            <textarea v-model="form.body" data-testid="widget-body" rows="4" />
          </label>
        </section>

        <p v-if="previewError" class="widget-editor__error">{{ previewError }}</p>
      </form>

      <section class="widget-editor__panel widget-editor__panel--preview">
        <div class="widget-editor__preview-header">
          <div>
            <p class="widget-editor__eyebrow">Live preview</p>
            <strong>{{ previewSucceeded ? 'Validated preview' : 'Preview unavailable' }}</strong>
          </div>
          <span v-if="previewLoading" class="widget-editor__loading">Refreshing…</span>
        </div>

        <div class="widget-editor__preview-stage">
          <WorkspaceWidgetRenderer
            :widget="{
              instance: {
                id: 'preview',
                pageId: 'preview',
                widgetDefinitionId: props.widgetId ?? null,
                kind: form.kind,
                titleOverride: form.title,
                descriptionOverride: form.description,
                queryOverride: buildQuerySpec(),
                vizOverride: buildVizSpec(),
                layout: { x: 0, y: 0, w: 12, h: 8 },
                locked: false,
                sortOrder: 0,
                createdAt: '',
                updatedAt: '',
              },
              definition: null,
              effectiveTitle: form.title || 'Untitled widget',
              effectiveDescription: form.description || null,
              effectiveQuery: buildQuerySpec(),
              effectiveViz: buildVizSpec(),
            }"
            :data="preview || undefined"
            :annotations="[]"
            :drilldown="null"
            :edit-mode="false"
            presentation="preview"
          />
        </div>
      </section>
    </div>
  </section>
</template>

<style scoped>
.widget-editor {
  display: grid;
  gap: 16px;
  padding: 12px 4px 8px;
}

.widget-editor__header {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  gap: 18px;
  align-items: center;
}

.widget-editor__back {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 10px 12px;
  border: 1px solid rgba(148, 163, 184, 0.22);
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.88);
  color: #334155;
  text-decoration: none;
  font-size: 13px;
  font-weight: 700;
}

.widget-editor__title {
  display: grid;
  gap: 4px;
}

.widget-editor__eyebrow {
  margin: 0;
  color: #64748b;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.12em;
  text-transform: uppercase;
}

.widget-editor__title h1 {
  margin: 0;
  color: #0f172a;
  font-size: 28px;
  font-weight: 700;
  letter-spacing: -0.04em;
}

.widget-editor__title p {
  margin: 0;
  color: #64748b;
  font-size: 14px;
}

.widget-editor__actions {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.widget-editor__ghost,
.widget-editor__primary {
  align-self: center;
}

.widget-editor__layout {
  display: grid;
  grid-template-columns: 320px minmax(0, 1fr);
  gap: 18px;
  align-items: start;
}

.widget-editor__panel {
  min-width: 0;
  border: 1px solid rgba(148, 163, 184, 0.22);
  border-radius: 22px;
  background: rgba(255, 255, 255, 0.95);
  box-shadow: 0 18px 38px rgba(148, 163, 184, 0.1);
}

.widget-editor__panel--form {
  display: grid;
  gap: 18px;
  padding: 16px;
  max-height: calc(100vh - 280px);
  overflow: auto;
}

.widget-editor__panel--preview {
  display: grid;
  gap: 16px;
  padding: 16px;
  min-height: calc(100vh - 300px);
}

.widget-editor__preview-header {
  display: flex;
  align-items: start;
  justify-content: space-between;
  gap: 12px;
}

.widget-editor__preview-header strong {
  color: #0f172a;
  font-size: 16px;
  font-weight: 700;
}

.widget-editor__loading {
  color: #6366f1;
  font-size: 12px;
  font-weight: 700;
}

.widget-editor__preview-stage {
  flex: 1 1 auto;
  display: grid;
  place-items: center;
  padding: 18px;
  border: 1px solid rgba(148, 163, 184, 0.18);
  border-radius: 20px;
  background:
    radial-gradient(circle at top right, rgba(99, 102, 241, 0.08), transparent 30%),
    linear-gradient(180deg, rgba(250, 251, 255, 0.92), rgba(243, 245, 255, 0.78));
}

.editor-section {
  display: grid;
  gap: 14px;
}

.editor-section__header {
  display: grid;
  gap: 2px;
}

.editor-section__header p {
  margin: 0;
  color: #0f172a;
  font-size: 13px;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.editor-section__header span,
.editor-section__note {
  color: #64748b;
  font-size: 12px;
  line-height: 1.45;
}

.editor-section__note {
  margin: 0;
}

.field {
  display: grid;
  gap: 8px;
}

.field span {
  color: #475569;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.02em;
}

.field input,
.field textarea,
.field select {
  width: 100%;
  border: 1px solid rgba(148, 163, 184, 0.24);
  border-radius: 14px;
  background: rgba(248, 250, 252, 0.92);
  color: #0f172a;
  font: inherit;
  outline: none;
}

.field input,
.field select {
  min-height: 46px;
  padding: 0 14px;
}

.field textarea {
  min-height: 96px;
  padding: 14px;
  resize: vertical;
}

.field-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.viz-pills {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.viz-pill,
.filter-chip--ghost {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-height: 34px;
  padding: 0 12px;
  border: 1px solid rgba(148, 163, 184, 0.24);
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.9);
  color: #475569;
  font-size: 12px;
  font-weight: 700;
  cursor: pointer;
}

.viz-pill--active {
  border-color: rgba(99, 102, 241, 0.28);
  background: rgba(99, 102, 241, 0.12);
  color: #4338ca;
}

.filter-chip-row {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.filter-chip--ghost {
  color: #4338ca;
  background: rgba(99, 102, 241, 0.08);
}

.widget-editor__preview-stage :deep(.widget-renderer) {
  width: min(100%, 620px);
}

.widget-editor__error {
  margin: 0;
  color: #dc2626;
  font-size: 13px;
  font-weight: 600;
}

@media (max-width: 1100px) {
  .widget-editor__header,
  .widget-editor__layout {
    grid-template-columns: 1fr;
  }

  .widget-editor__actions {
    justify-content: start;
  }

  .field-grid {
    grid-template-columns: 1fr;
  }

  .widget-editor__panel--form,
  .widget-editor__panel--preview {
    max-height: none;
  }
}
</style>
