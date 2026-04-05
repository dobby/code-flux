<script setup lang="ts">
import { computed } from 'vue'
import type { WidgetKind } from '../types/workspace'

const props = defineProps<{
  kind: WidgetKind
  title: string
  subtitle?: string | null
}>()

const bars = computed(() => {
  switch (props.kind) {
    case 'time_series':
      return [34, 62, 48, 74, 56, 68]
    case 'distribution':
      return [22, 18, 16, 14]
    case 'metric_card':
      return [12, 24, 18, 26, 21]
    case 'data_table':
      return [32, 24, 18, 22]
    case 'calendar_heatmap':
      return [18, 32, 40, 28, 54, 44, 24, 36, 20, 50, 60, 26]
    case 'day_explorer':
      return [56, 34, 48, 30]
    case 'header_block':
    case 'markdown_block':
      return [72, 48, 64]
    default:
      return [24, 42, 30, 56]
  }
})

const isTable = computed(() => props.kind === 'data_table')
const isHeatmap = computed(() => props.kind === 'calendar_heatmap')
const isMetric = computed(() => props.kind === 'metric_card')
const isDistribution = computed(() => props.kind === 'distribution')
</script>

<template>
  <div class="preview-glyph" :class="`preview-glyph--${kind}`">
    <div v-if="isMetric" class="preview-glyph__metric">
      <div class="preview-glyph__metric-value">347</div>
      <div class="preview-glyph__metric-delta">+8.3% from last period</div>
      <div class="preview-glyph__metric-track">
        <span v-for="bar in bars" :key="bar" class="preview-glyph__metric-bar" :style="{ height: `${bar}%` }" />
      </div>
    </div>

    <div v-else-if="isDistribution" class="preview-glyph__donut-shell">
      <div class="preview-glyph__donut" />
      <div class="preview-glyph__legend">
        <span v-for="bar in bars" :key="bar" class="preview-glyph__legend-row">
          <i class="preview-glyph__legend-dot" />
          <span>{{ bar }}%</span>
        </span>
      </div>
    </div>

    <div v-else-if="isHeatmap" class="preview-glyph__heatmap">
      <span v-for="bar in bars" :key="bar" class="preview-glyph__heatmap-cell" :style="{ opacity: `${Math.min(1, bar / 60)}` }" />
    </div>

    <div v-else-if="isTable" class="preview-glyph__table">
      <span class="preview-glyph__table-head">
        <i />
        <i />
        <i />
      </span>
      <span v-for="bar in bars" :key="bar" class="preview-glyph__table-row">
        <i class="preview-glyph__table-cell preview-glyph__table-cell--wide" />
        <i class="preview-glyph__table-cell" />
        <i class="preview-glyph__table-cell" />
      </span>
    </div>

    <div v-else-if="kind === 'day_explorer'" class="preview-glyph__split">
      <div class="preview-glyph__stack">
        <span v-for="bar in bars" :key="bar" class="preview-glyph__split-line" :style="{ width: `${bar}%` }" />
      </div>
      <div class="preview-glyph__mini-cards">
        <span class="preview-glyph__mini-card" />
        <span class="preview-glyph__mini-card" />
      </div>
    </div>

    <div v-else-if="kind === 'header_block' || kind === 'markdown_block'" class="preview-glyph__copy">
      <span class="preview-glyph__title">{{ title }}</span>
      <span class="preview-glyph__line preview-glyph__line--wide" />
      <span class="preview-glyph__line" />
      <span class="preview-glyph__line preview-glyph__line--short" />
    </div>

    <div v-else class="preview-glyph__bars">
      <span v-for="bar in bars" :key="bar" class="preview-glyph__bar" :style="{ height: `${bar}%` }" />
    </div>

    <div class="preview-glyph__footer">
      <span>{{ title }}</span>
      <span v-if="subtitle">{{ subtitle }}</span>
    </div>
  </div>
</template>

<style scoped>
.preview-glyph {
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  gap: 14px;
  min-height: 100%;
  padding: 14px;
  border: 1px solid rgba(148, 163, 184, 0.28);
  border-radius: 18px;
  background:
    radial-gradient(circle at top right, rgba(99, 102, 241, 0.12), transparent 38%),
    linear-gradient(180deg, rgba(255, 255, 255, 0.98), rgba(245, 247, 255, 0.92));
  color: #0f172a;
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.7);
}

.preview-glyph__footer {
  display: flex;
  flex-direction: column;
  gap: 2px;
  color: #64748b;
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.01em;
}

.preview-glyph__footer span:first-child {
  color: #334155;
  font-size: 12px;
}

.preview-glyph__bars,
.preview-glyph__metric-track,
.preview-glyph__heatmap,
.preview-glyph__split,
.preview-glyph__table,
.preview-glyph__copy {
  display: flex;
  align-items: end;
  gap: 8px;
  flex: 1 1 auto;
  min-height: 0;
}

.preview-glyph__bars {
  padding: 14px 10px 6px;
}

.preview-glyph__bar {
  flex: 1 1 0;
  min-width: 6px;
  border-radius: 999px 999px 8px 8px;
  background: linear-gradient(180deg, rgba(99, 102, 241, 0.35), rgba(99, 102, 241, 0.92));
  box-shadow: 0 6px 16px rgba(99, 102, 241, 0.15);
}

.preview-glyph__metric {
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  flex: 1 1 auto;
  padding: 2px 2px 0;
}

.preview-glyph__metric-value {
  color: #0f172a;
  font-size: 34px;
  font-weight: 700;
  letter-spacing: -0.04em;
}

.preview-glyph__metric-delta {
  color: #16a34a;
  font-size: 12px;
  font-weight: 600;
}

.preview-glyph__metric-track {
  align-items: end;
  gap: 6px;
  padding-top: 12px;
}

.preview-glyph__metric-bar {
  flex: 1 1 0;
  min-width: 7px;
  border-radius: 999px 999px 6px 6px;
  background: linear-gradient(180deg, rgba(129, 140, 248, 0.24), rgba(99, 102, 241, 0.92));
}

.preview-glyph__donut-shell {
  display: grid;
  grid-template-columns: 1fr auto;
  gap: 12px;
  width: 100%;
  align-items: center;
}

.preview-glyph__donut {
  width: 88px;
  height: 88px;
  border-radius: 50%;
  background:
    conic-gradient(
      from 210deg,
      rgba(99, 102, 241, 0.96) 0 34%,
      rgba(96, 165, 250, 0.9) 34% 58%,
      rgba(251, 191, 36, 0.88) 58% 78%,
      rgba(34, 197, 94, 0.88) 78% 100%
    );
  box-shadow: inset 0 0 0 18px rgba(255, 255, 255, 0.86);
}

.preview-glyph__legend {
  display: grid;
  gap: 8px;
  justify-items: start;
}

.preview-glyph__legend-row {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  color: #475569;
  font-size: 11px;
  font-weight: 600;
}

.preview-glyph__legend-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #6366f1;
}

.preview-glyph__heatmap {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 6px;
  align-content: start;
  padding: 8px 6px 0;
}

.preview-glyph__heatmap-cell {
  aspect-ratio: 1;
  border-radius: 5px;
  background: rgba(99, 102, 241, 0.92);
}

.preview-glyph__table {
  flex-direction: column;
  justify-content: center;
  align-items: stretch;
  gap: 8px;
  padding: 4px 0 0;
}

.preview-glyph__table-head,
.preview-glyph__table-row {
  display: grid;
  grid-template-columns: 1.8fr 1fr 0.8fr;
  gap: 8px;
}

.preview-glyph__table-head i,
.preview-glyph__table-cell {
  display: block;
  height: 10px;
  border-radius: 6px;
  background: rgba(148, 163, 184, 0.38);
}

.preview-glyph__table-cell--wide {
  background: rgba(99, 102, 241, 0.34);
}

.preview-glyph__split {
  align-items: stretch;
  gap: 12px;
}

.preview-glyph__stack {
  display: grid;
  gap: 8px;
  flex: 1 1 auto;
}

.preview-glyph__split-line {
  display: block;
  height: 10px;
  border-radius: 999px;
  background: rgba(99, 102, 241, 0.82);
}

.preview-glyph__mini-cards {
  display: grid;
  gap: 8px;
  width: 44px;
}

.preview-glyph__mini-card {
  height: 32px;
  border-radius: 10px;
  background: rgba(99, 102, 241, 0.16);
}

.preview-glyph__copy {
  flex-direction: column;
  justify-content: center;
  align-items: stretch;
  gap: 10px;
  padding-top: 4px;
}

.preview-glyph__title {
  color: #334155;
  font-size: 13px;
  font-weight: 700;
}

.preview-glyph__line {
  display: block;
  height: 10px;
  width: 82%;
  border-radius: 999px;
  background: rgba(148, 163, 184, 0.36);
}

.preview-glyph__line--wide {
  width: 100%;
}

.preview-glyph__line--short {
  width: 62%;
}

@media (max-width: 900px) {
  .preview-glyph {
    min-height: 220px;
  }
}
</style>
