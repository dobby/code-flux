<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { previewQuery, getWidgetDefinition } from '../api/workspace'
import { useWorkspaceStore } from '../stores/workspace'
import WorkspaceWidgetRenderer from '../components/WorkspaceWidgetRenderer.vue'
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

const currentDataset = computed(() => workspace.datasets.find((dataset) => dataset.key === form.datasetKey))
const supportsQuery = computed(() => !['header_block', 'markdown_block', 'divider_block', 'spacer_block', 'day_explorer'].includes(form.kind))

function buildQuerySpec(): WidgetQuerySpec | null {
  if (!supportsQuery.value) {
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

async function runPreview() {
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
  }
}

async function save() {
  if (supportsQuery.value && !previewSucceeded.value) {
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
      datasetKey: supportsQuery.value ? form.datasetKey : form.kind === 'day_explorer' ? 'day_activity' : null,
      querySpec: buildQuerySpec(),
      vizSpec: buildVizSpec(),
    })
    await router.push({ name: 'widget-edit', params: { widgetId: widget.id } })
  } finally {
    loading.value = false
  }
}

function hydrate(widget: WidgetDefinition) {
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

onMounted(async () => {
  await workspace.initialize()
  if (props.widgetId) {
    hydrate(await getWidgetDefinition(props.widgetId))
  }
})
</script>

<template>
  <section class="workspace-surface">
    <header class="surface-header">
      <div>
        <p class="workspace-sidebar__eyebrow">Widget editor</p>
        <h2>{{ props.widgetId ? 'Edit widget definition' : 'Create widget definition' }}</h2>
        <p>Reusable widgets define the default query, rendering mode, and narrative framing used across pages.</p>
      </div>
    </header>

    <div class="editor-layout">
      <form class="editor-form" @submit.prevent="save">
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

        <div class="field-grid">
          <label class="field">
            <span>Kind</span>
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

          <label class="field" v-if="supportsQuery">
            <span>Dataset</span>
            <select v-model="form.datasetKey" data-testid="widget-dataset">
              <option v-for="dataset in workspace.datasets.filter((dataset) => dataset.widgetKinds.includes(form.kind))" :key="dataset.key" :value="dataset.key">
                {{ dataset.label }}
              </option>
            </select>
          </label>
        </div>

        <template v-if="supportsQuery">
          <div class="field-grid">
            <label class="field">
              <span>Measure</span>
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

          <div class="field-grid">
            <label class="field">
              <span>Group by</span>
              <select v-model="form.groupBy" data-testid="widget-group-by">
                <option value="">None</option>
                <option v-for="dimension in currentDataset?.dimensions ?? []" :key="dimension.key" :value="dimension.key">{{ dimension.label }}</option>
              </select>
            </label>

            <label class="field" v-if="form.kind === 'time_series' || form.kind === 'calendar_heatmap'">
              <span>Time bucket</span>
              <select v-model="form.timeBucket" data-testid="widget-time-bucket">
                <option value="day">Day</option>
                <option value="week">Week</option>
                <option value="month">Month</option>
              </select>
            </label>
          </div>

          <label class="field">
            <span>Chart type</span>
            <select v-model="form.chartType" data-testid="widget-chart-type">
              <option value="line">Line</option>
              <option value="area">Area</option>
              <option value="bar">Bar</option>
              <option value="donut">Donut</option>
              <option value="pie">Pie</option>
            </select>
          </label>
        </template>

        <label v-else-if="form.kind === 'markdown_block'" class="field">
          <span>Markdown</span>
          <textarea v-model="form.markdown" data-testid="widget-markdown" rows="8" />
        </label>

        <label v-else-if="form.kind === 'header_block'" class="field">
          <span>Supporting text</span>
          <textarea v-model="form.body" data-testid="widget-body" rows="4" />
        </label>

        <div class="editor-actions">
          <button v-if="supportsQuery" class="button button--ghost" data-testid="widget-preview" type="button" @click="runPreview">Run preview</button>
          <button class="button button--primary" data-testid="widget-save" type="submit" :disabled="loading">
            {{ loading ? 'Saving…' : 'Save widget' }}
          </button>
        </div>

        <p v-if="previewError" class="form-error">{{ previewError }}</p>
      </form>

      <section class="editor-preview">
        <div class="editor-preview__header">
          <p class="workspace-sidebar__eyebrow">Preview</p>
          <strong>{{ previewSucceeded ? 'Validated preview' : 'Preview unavailable' }}</strong>
        </div>
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
        />
      </section>
    </div>
  </section>
</template>
