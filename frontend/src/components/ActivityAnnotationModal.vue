<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { Tag, Flame, Folder, MessageSquare, X, Plus, GitCommitHorizontal } from 'lucide-vue-next'
import type { AnnotationTypeV2, AnnotationV2 } from '../types/workspace'

let modalInstanceSeed = 0

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

const instanceId = ++modalInstanceSeed
const titleId = `explorer-annotation-modal-title-${instanceId}`
const contextId = `explorer-annotation-modal-context-${instanceId}`
const typeGroupId = `explorer-annotation-modal-type-${instanceId}`
const nameInputId = `explorer-annotation-modal-name-${instanceId}`
const descriptionInputId = `explorer-annotation-modal-description-${instanceId}`
const modalRef = ref<HTMLElement | null>(null)
const nameInputRef = ref<HTMLInputElement | null>(null)
let previouslyFocusedElement: HTMLElement | null = null

type TypeOption = { value: AnnotationTypeV2; label: string; icon: typeof Tag }

const typeOptions: TypeOption[] = [
  { value: 'feature', label: 'Feature', icon: Tag },
  { value: 'incident', label: 'Incident', icon: Flame },
  { value: 'project', label: 'Project', icon: Folder },
  { value: 'note', label: 'Note', icon: MessageSquare },
]

const form = reactive({
  name: '',
  description: '',
  annotationType: 'note' as AnnotationTypeV2,
})
const tags = ref<string[]>([])
const tagInput = ref('')
const canSave = computed(() => form.name.trim().length > 0)

function focusInitialField() {
  const target = nameInputRef.value ?? modalRef.value
  target?.focus({ preventScroll: true })
}

function getFocusableElements() {
  const root = modalRef.value
  if (!root) {
    return []
  }
  return Array.from(
    root.querySelectorAll<HTMLElement>(
      [
        'button:not([disabled])',
        'input:not([disabled])',
        'textarea:not([disabled])',
        'select:not([disabled])',
        '[tabindex]:not([tabindex="-1"])',
      ].join(', '),
    ),
  ).filter((element) => element.offsetParent !== null || element === document.activeElement)
}

watch(
  () => [props.open, props.annotation] as const,
  ([open, annotation]) => {
    if (!open) return
    form.name = annotation?.name ?? annotation?.title ?? ''
    form.description = annotation?.description ?? annotation?.body ?? ''
    form.annotationType = annotation?.annotationType ?? 'note'
    tags.value = annotation?.tags ? [...annotation.tags] : []
    tagInput.value = ''
  },
  { immediate: true },
)

watch(
  () => props.open,
  async (open) => {
    if (!open) {
      return
    }
    previouslyFocusedElement = document.activeElement instanceof HTMLElement ? document.activeElement : null
    await nextTick()
    focusInitialField()
  },
  { immediate: true },
)

onBeforeUnmount(() => {
  previouslyFocusedElement?.focus?.({ preventScroll: true })
  previouslyFocusedElement = null
})

function addTag() {
  const v = tagInput.value.trim().replace(/,$/, '').trim()
  if (!v) return
  if (!tags.value.includes(v)) tags.value.push(v)
  tagInput.value = ''
}
function onTagKeydown(e: KeyboardEvent) {
  if (e.key === 'Enter' || e.key === ',') {
    e.preventDefault()
    addTag()
  }
}
function removeTag(tag: string) {
  tags.value = tags.value.filter((t) => t !== tag)
}

function handleSave() {
  if (!canSave.value) {
    focusInitialField()
    return
  }
  emit('save', {
    name: form.name.trim(),
    description: form.description,
    annotationType: form.annotationType,
    tags: tags.value,
  })
}

function handleDialogKeydown(event: KeyboardEvent) {
  if (event.key === 'Escape') {
    event.preventDefault()
    emit('close')
    return
  }
  if (event.key !== 'Tab') {
    return
  }

  const focusables = getFocusableElements()
  if (!focusables.length) {
    event.preventDefault()
    modalRef.value?.focus({ preventScroll: true })
    return
  }

  const first = focusables[0]
  const last = focusables[focusables.length - 1]
  const active = document.activeElement as HTMLElement | null

  if (event.shiftKey) {
    if (active === first || active === modalRef.value) {
      event.preventDefault()
      last.focus({ preventScroll: true })
    }
    return
  }

  if (active === last) {
    event.preventDefault()
    first.focus({ preventScroll: true })
  }
}
</script>

<template>
  <div v-if="open" class="am-shell" role="presentation">
    <div class="am-shell__backdrop" aria-hidden="true" @click="emit('close')" />
    <section
      ref="modalRef"
      class="am-modal"
      role="dialog"
      aria-modal="true"
      :aria-labelledby="titleId"
      :aria-describedby="contextId"
      tabindex="-1"
      @keydown="handleDialogKeydown"
    >
      <header class="am-header">
        <Tag :size="18" color="#6366f1" />
        <h2 :id="titleId" class="am-header__title">{{ mode === 'edit' ? 'Edit annotation' : 'Create annotation' }}</h2>
        <button class="am-header__close" type="button" @click="emit('close')">
          <X :size="16" />
        </button>
      </header>

      <div class="am-body">
        <div :id="contextId" class="am-context">
          <GitCommitHorizontal :size="14" color="#94a0b8" />
          <div class="am-context__body">
            <span class="am-context__title">{{ day }}</span>
            <span class="am-context__meta">{{ commitLabel || 'Day-level annotation' }}</span>
          </div>
        </div>

        <fieldset :id="typeGroupId" class="am-field am-field--group">
          <legend class="am-field__label">Annotation type</legend>
          <div class="am-types">
            <button
              v-for="option in typeOptions"
              :key="option.value"
              class="am-type-pill"
              :class="{ 'am-type-pill--active': form.annotationType === option.value }"
              type="button"
              @click="form.annotationType = option.value"
            >
              <component :is="option.icon" :size="13" />
              <span>{{ option.label }}</span>
            </button>
          </div>
        </fieldset>

        <label class="am-field" :for="nameInputId">
          <span class="am-field__label">Name</span>
          <input
            :id="nameInputId"
            ref="nameInputRef"
            v-model="form.name"
            class="am-input"
            placeholder="Spike note"
            required
          />
        </label>

        <label class="am-field" :for="descriptionInputId">
          <span class="am-field__label">Description</span>
          <textarea
            :id="descriptionInputId"
            v-model="form.description"
            class="am-textarea"
            placeholder="What changed and why it matters."
          />
        </label>

        <div class="am-field">
          <span class="am-field__label">Tags (optional)</span>
          <div class="am-tags">
            <span v-for="tag in tags" :key="tag" class="am-chip">
              <span>{{ tag }}</span>
              <button class="am-chip__remove" type="button" @click="removeTag(tag)">
                <X :size="10" />
              </button>
            </span>
            <span class="am-tag-input-wrap">
              <Plus :size="10" color="#94a0b8" />
              <input
                v-model="tagInput"
                placeholder="Add tag"
                @keydown="onTagKeydown"
                @blur="addTag"
              />
            </span>
          </div>
        </div>
      </div>

      <footer class="am-footer">
        <button v-if="mode === 'edit'" class="am-btn am-btn--delete" type="button" @click="emit('delete')">Delete</button>
        <button class="am-btn am-btn--cancel" type="button" @click="emit('close')">Cancel</button>
        <button class="am-btn am-btn--save" type="button" :disabled="!canSave" @click="handleSave">Save annotation</button>
      </footer>
    </section>
  </div>
</template>

<style scoped>
.am-shell {
  position: fixed;
  inset: 0;
  z-index: 40;
  display: flex;
  align-items: flex-start;
  justify-content: flex-end;
  padding: 78px 44px 24px;
}
.am-shell__backdrop {
  position: absolute;
  inset: 0;
  border: 0;
  background: rgba(0, 0, 0, 0.4);
  cursor: pointer;
}
.am-modal {
  position: relative;
  z-index: 1;
  width: 370px;
  max-width: 100%;
  height: auto;
  max-height: calc(100vh - 48px);
  border-radius: 12px;
  background: #ffffff;
  border: 1px solid rgba(148, 163, 184, 0.22);
  display: flex;
  flex-direction: column;
  overflow: hidden;
  outline: none;
}
.am-header {
  display: flex;
  align-items: center;
  gap: 10px;
  height: 42px;
  padding: 0 14px;
  border-bottom: 1px solid rgba(148, 163, 184, 0.22);
  flex-shrink: 0;
}
.am-header__title {
  font-size: 13px;
  font-weight: 700;
  color: #162033;
  margin: 0;
}
.am-header__close {
  margin-left: auto;
  display: grid;
  place-items: center;
  width: 28px;
  height: 28px;
  border: 0;
  border-radius: 6px;
  background: transparent;
  color: #94a0b8;
  cursor: pointer;
}
.am-body {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 12px 14px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.am-context {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 10px;
  border: 1px solid rgba(148, 163, 184, 0.22);
  border-radius: 8px;
  background: #f8f9fa;
}
.am-context__body {
  display: flex;
  flex-direction: column;
  gap: 2px;
  flex: 1;
  min-width: 0;
}
.am-context__title {
  font-size: 12px;
  font-weight: 500;
  color: #162033;
}
.am-context__meta {
  font-size: 11px;
  color: #94a0b8;
}
.am-field {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.am-field--group {
  margin: 0;
  padding: 0;
  border: 0;
  min-inline-size: 0;
}
.am-field--group > legend {
  padding: 0;
}
.am-field__label {
  font-size: 12px;
  font-weight: 600;
  color: #162033;
}
.am-types {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}
.am-type-pill {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 5px 9px;
  border-radius: 6px;
  border: 1px solid rgba(148, 163, 184, 0.22);
  background: transparent;
  color: #61708d;
  font-size: 12px;
  font-weight: 500;
  cursor: pointer;
}
.am-type-pill--active {
  background: #6366f1;
  border-color: #6366f1;
  color: #ffffff;
  font-weight: 600;
}
.am-input,
.am-textarea {
  width: 100%;
  border: 1px solid rgba(148, 163, 184, 0.22);
  border-radius: 8px;
  background: #ffffff;
  color: #162033;
  font: inherit;
  font-size: 13px;
  box-sizing: border-box;
}
.am-input {
  height: 30px;
  padding: 0 10px;
}
.am-textarea {
  min-height: 58px;
  padding: 8px 10px;
  resize: none;
  line-height: 1.5;
}
.am-input:focus,
.am-textarea:focus {
  outline: none;
  border: 1.5px solid #6366f1;
}
.am-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  align-items: center;
}
.am-chip {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 3px 8px;
  border-radius: 4px;
  background: rgba(99, 102, 241, 0.08);
  color: #6366f1;
  font-size: 11px;
  font-weight: 500;
}
.am-chip__remove {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 12px;
  height: 12px;
  border: 0;
  background: transparent;
  color: #6366f1;
  cursor: pointer;
  padding: 0;
}
.am-tag-input-wrap {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 3px 8px;
  border-radius: 4px;
  border: 1px solid rgba(148, 163, 184, 0.22);
  background: transparent;
}
.am-tag-input-wrap input {
  border: 0;
  outline: none;
  background: transparent;
  font: inherit;
  font-size: 11px;
  font-weight: 500;
  color: #162033;
  width: 80px;
}
.am-tag-input-wrap input::placeholder {
  color: #94a0b8;
}
.am-footer {
  display: flex;
  align-items: center;
  gap: 10px;
  height: 48px;
  padding: 0 14px;
  border-top: 1px solid rgba(148, 163, 184, 0.22);
  flex-shrink: 0;
  justify-content: flex-end;
}
.am-btn {
  border-radius: 7px;
  padding: 6px 12px;
  font-size: 12px;
  font-weight: 500;
  cursor: pointer;
}
.am-btn--delete {
  margin-right: auto;
  border: 1px solid rgba(239, 68, 68, 0.3);
  background: transparent;
  color: #ef4444;
}
.am-btn--cancel {
  border: 1px solid rgba(148, 163, 184, 0.22);
  background: transparent;
  color: #61708d;
}
.am-btn--save {
  border: 0;
  background: #6366f1;
  color: #ffffff;
  font-weight: 600;
}
.am-btn--save:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

@media (max-width: 720px) {
  .am-shell {
    justify-content: center;
    padding: 24px;
  }
}
</style>
