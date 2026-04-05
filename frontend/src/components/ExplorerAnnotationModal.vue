<script setup lang="ts">
import { computed, reactive, watch } from 'vue'
import { CircleX, Plus, Tag } from 'lucide-vue-next'
import type { AnnotationTypeV2, AnnotationV2 } from '../types/workspace'

const props = defineProps<{
  open: boolean
  day: string
  commitLabel?: string | null
  mode: 'create' | 'edit'
  annotation?: AnnotationV2 | null
}>()

const emit = defineEmits<{
  (event: 'close'): void
  (event: 'save', payload: { name: string; description: string; annotationType: AnnotationTypeV2; tags: string[] }): void
  (event: 'delete'): void
}>()

const form = reactive({
  name: '',
  description: '',
  annotationType: 'note' as AnnotationTypeV2,
  tagsInput: '',
})

const tagList = computed(() =>
  form.tagsInput
    .split(',')
    .map((value) => value.trim())
    .filter(Boolean),
)

watch(
  () => [props.open, props.annotation] as const,
  ([open, annotation]) => {
    if (!open) {
      return
    }
    form.name = annotation?.name ?? annotation?.title ?? ''
    form.description = annotation?.description ?? annotation?.body ?? ''
    form.annotationType = annotation?.annotationType ?? 'note'
    form.tagsInput = annotation?.tags.join(', ') ?? ''
  },
  { immediate: true },
)
</script>

<template>
  <div v-if="open" class="annotation-modal-shell">
    <button class="annotation-modal-shell__backdrop" type="button" @click="emit('close')" />
    <section class="annotation-modal" role="dialog" aria-modal="true">
      <header class="annotation-modal__header">
        <div>
          <p class="annotation-modal__eyebrow">Annotation</p>
          <h2>{{ mode === 'edit' ? 'Edit annotation' : 'Create annotation' }}</h2>
        </div>
        <button class="annotation-modal__icon" type="button" @click="emit('close')">
          <CircleX :size="16" />
        </button>
      </header>

      <div class="annotation-modal__context">
        <span class="annotation-modal__context-kicker">Context</span>
        <strong>{{ day }}</strong>
        <p>{{ commitLabel || 'Day-level annotation' }}</p>
      </div>

      <section class="annotation-modal__section">
        <span class="annotation-modal__section-label">Type</span>
        <div class="annotation-modal__type-grid">
          <button
            v-for="option in ['feature', 'incident', 'project', 'note'] as const"
            :key="option"
            class="annotation-modal__type-pill"
            :class="{ 'annotation-modal__type-pill--active': form.annotationType === option }"
            type="button"
            @click="form.annotationType = option"
          >
            {{ option }}
          </button>
        </div>
      </section>

      <label class="annotation-modal__field">
        <span>Name</span>
        <input v-model="form.name" placeholder="Spike note" />
      </label>

      <label class="annotation-modal__field">
        <span>Description</span>
        <textarea v-model="form.description" rows="5" placeholder="What changed and why it matters." />
      </label>

      <section class="annotation-modal__section">
        <div class="annotation-modal__section-row">
          <span class="annotation-modal__section-label">Tags</span>
          <span class="annotation-modal__pending">Backend support pending</span>
        </div>
        <div class="annotation-modal__tag-input">
          <Tag :size="14" />
          <input v-model="form.tagsInput" placeholder="frontend, release, risk" />
          <button class="annotation-modal__tag-add" type="button" aria-label="Add tag" @click.prevent>
            <Plus :size="14" />
          </button>
        </div>
        <div class="annotation-modal__chips">
          <span v-for="tag in tagList" :key="tag" class="annotation-modal__chip">{{ tag }}</span>
        </div>
      </section>

      <footer class="annotation-modal__footer">
        <button class="annotation-modal__ghost" type="button" @click="emit('close')">Cancel</button>
        <button v-if="mode === 'edit'" class="annotation-modal__danger" type="button" @click="emit('delete')">Delete</button>
        <button
          class="annotation-modal__primary"
          type="button"
          @click="emit('save', { name: form.name, description: form.description, annotationType: form.annotationType, tags: tagList })"
        >
          Save annotation
        </button>
      </footer>
    </section>
  </div>
</template>

<style scoped>
.annotation-modal-shell {
  position: fixed;
  inset: 0;
  z-index: 40;
  display: grid;
  place-items: center;
  padding: 24px;
}

.annotation-modal-shell__backdrop {
  position: absolute;
  inset: 0;
  border: 0;
  background: rgb(15 23 42 / 0.42);
}

.annotation-modal {
  position: relative;
  z-index: 1;
  width: min(460px, 100%);
  display: grid;
  gap: 12px;
  border: 1px solid rgba(148, 163, 184, 0.2);
  border-radius: 12px;
  padding: 14px;
  background: rgba(255, 255, 255, 0.98);
  box-shadow: none;
}

.annotation-modal__header,
.annotation-modal__section-row,
.annotation-modal__footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.annotation-modal__eyebrow,
.annotation-modal__section-label,
.annotation-modal__context-kicker,
.annotation-modal__pending {
  margin: 0;
  color: #6b7280;
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 0.07em;
  text-transform: uppercase;
}

.annotation-modal__header h2 {
  margin: 4px 0 0;
  color: #111827;
  font-size: 14px;
  line-height: 1.1;
}

.annotation-modal__icon {
  display: grid;
  place-items: center;
  width: 28px;
  height: 28px;
  border: 0;
  border-radius: 8px;
  background: #f8fafc;
  color: #334155;
}

.annotation-modal__context {
  display: grid;
  gap: 3px;
  border-radius: 10px;
  padding: 10px 12px;
  background: rgba(99, 102, 241, 0.08);
}

.annotation-modal__context strong {
  color: #312e81;
  font-size: 12px;
}

.annotation-modal__context p {
  margin: 0;
  color: #4f46e5;
  font-size: 11px;
}

.annotation-modal__section {
  display: grid;
  gap: 10px;
}

.annotation-modal__type-grid {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.annotation-modal__type-pill {
  border: 1px solid #dbe3f0;
  border-radius: 999px;
  padding: 6px 10px;
  background: #fff;
  color: #334155;
  font-size: 11px;
  font-weight: 700;
}

.annotation-modal__type-pill--active {
  border-color: #6366f1;
  background: #eef2ff;
  color: #4338ca;
}

.annotation-modal__field {
  display: grid;
  gap: 8px;
}

.annotation-modal__field span {
  color: #334155;
  font-size: 11px;
  font-weight: 700;
}

.annotation-modal__field input,
.annotation-modal__field textarea,
.annotation-modal__tag-input input {
  width: 100%;
  border: 1px solid #dbe3f0;
  border-radius: 8px;
  padding: 8px 10px;
  background: #fff;
  color: #111827;
  font: inherit;
}

.annotation-modal__tag-input {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  align-items: center;
  gap: 6px;
  border: 1px solid #dbe3f0;
  border-radius: 8px;
  padding: 5px 8px;
}

.annotation-modal__tag-input input {
  border: 0;
  padding: 0;
  outline: 0;
}

.annotation-modal__tag-add {
  display: grid;
  place-items: center;
  width: 24px;
  height: 24px;
  border: 0;
  border-radius: 7px;
  background: rgba(99, 102, 241, 0.08);
  color: #4f46e5;
}

.annotation-modal__chips {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.annotation-modal__chip {
  border-radius: 999px;
  padding: 4px 8px;
  background: rgba(99, 102, 241, 0.08);
  color: #4f46e5;
  font-size: 11px;
  font-weight: 700;
}

.annotation-modal__footer {
  padding-top: 4px;
}

.annotation-modal__ghost,
.annotation-modal__danger,
.annotation-modal__primary {
  border: 0;
  border-radius: 8px;
  padding: 7px 10px;
  font-size: 12px;
  font-weight: 700;
}

.annotation-modal__ghost {
  background: #f1f5f9;
  color: #334155;
}

.annotation-modal__danger {
  background: #fef2f2;
  color: #b91c1c;
}

.annotation-modal__primary {
  background: #4f46e5;
  color: #fff;
  margin-left: auto;
}
</style>
