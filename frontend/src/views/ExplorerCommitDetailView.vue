<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft } from 'lucide-vue-next'
import {
  createAnnotationV2,
  deleteAnnotationV2,
  getExplorerCommitDetail,
  openCommitInEditor,
  updateAnnotationV2,
} from '../api/workspace'
import type { AnnotationV2, CommitDetailResponse, EditorLaunchResponse } from '../types/workspace'
import ExplorerAnnotationModal from '../components/ExplorerAnnotationModal.vue'

const props = defineProps<{
  repoId: string
  commitSha: string
}>()

const route = useRoute()
const router = useRouter()

const selectedDate = ref(typeof route.query.date === 'string' ? route.query.date : '')
const detail = ref<CommitDetailResponse | null>(null)
const loading = ref(false)
const loadError = ref<string | null>(null)
const openingEditor = ref(false)
const editorLaunch = ref<EditorLaunchResponse | null>(null)
const annotationDialogOpen = ref(false)
const editingAnnotation = ref<AnnotationV2 | null>(null)

let loadSequence = 0

const commit = computed(() => detail.value)
const commitFiles = computed(() => detail.value?.files.slice(0, 20) ?? [])
const commitAnnotations = computed(() => detail.value?.annotations ?? [])
const commitTitle = computed(() => detail.value?.subject ?? props.commitSha)
const commitAuthor = computed(() => detail.value?.authorName ?? 'Unknown author')
const commitDateLabel = computed(() => {
  if (detail.value?.authoredAt) {
    return detail.value.authoredAt.slice(0, 10)
  }
  return selectedDate.value || 'Unknown day'
})
const hasCommitDetail = computed(() => detail.value !== null)
const hasError = computed(() => loadError.value !== null)

async function loadDetail() {
  const sequence = ++loadSequence
  loading.value = true
  loadError.value = null

  try {
    const loaded = await getExplorerCommitDetail(props.repoId, props.commitSha)
    if (sequence !== loadSequence) return
    detail.value = loaded
    editorLaunch.value = null
    selectedDate.value = loaded.authoredAt.slice(0, 10)
  } catch (error) {
    if (sequence !== loadSequence) return
    detail.value = null
    editorLaunch.value = null
    loadError.value = error instanceof Error ? error.message : 'Failed to load commit detail.'
  } finally {
    if (sequence === loadSequence) {
      loading.value = false
    }
  }
}

function goBack() {
  if (window.history.state?.back) {
    router.back()
    return
  }

  void router.push({
    name: 'explorer',
    query: Object.keys(route.query).length > 0
      ? route.query
      : (selectedDate.value ? { date: selectedDate.value } : undefined),
  })
}

function openAnnotationModal(annotation: AnnotationV2 | null = null) {
  editingAnnotation.value = annotation
  annotationDialogOpen.value = true
}

async function handleSaveAnnotation(payload: { name: string; description: string; annotationType: AnnotationV2['annotationType']; tags: string[] }) {
  const scopeDate = selectedDate.value || commit.value?.authoredAt?.slice(0, 10)
  if (!scopeDate) {
    return
  }

  if (editingAnnotation.value) {
    await updateAnnotationV2(editingAnnotation.value.id, {
      annotationType: payload.annotationType,
      name: payload.name,
      description: payload.description || null,
      title: payload.name,
      body: payload.description || null,
      tags: payload.tags,
      commitRefs: [{ repoId: props.repoId, commitSha: props.commitSha }],
    })
  } else {
    await createAnnotationV2({
      targetKind: 'global_date',
      scopeDate,
      annotationType: payload.annotationType,
      name: payload.name,
      description: payload.description || null,
      title: payload.name,
      body: payload.description || null,
      tags: payload.tags,
      commitRefs: [{ repoId: props.repoId, commitSha: props.commitSha }],
      scope: { surface: 'commit_detail' },
    })
  }

  annotationDialogOpen.value = false
  await loadDetail()
}

async function handleDeleteAnnotation() {
  if (!editingAnnotation.value) {
    return
  }

  await deleteAnnotationV2(editingAnnotation.value.id)
  annotationDialogOpen.value = false
  await loadDetail()
}

async function handleOpenInEditor() {
  openingEditor.value = true
  try {
    const request = {
      repoId: props.repoId,
      commitSha: props.commitSha,
      filePaths: commitFiles.value.map((file) => file.filePath),
    }
    editorLaunch.value = window.codeFluxSecure?.openCommitInEditor
      ? await window.codeFluxSecure.openCommitInEditor(request)
      : await openCommitInEditor(request)
  } finally {
    openingEditor.value = false
  }
}

watch(
  () => [props.commitSha, route.query.date] as const,
  ([, date]) => {
    selectedDate.value = typeof date === 'string' ? date : ''
    void loadDetail()
  },
  { immediate: true },
)
</script>

<template>
  <section class="commit-detail">
    <header class="commit-detail__header">
      <button class="commit-detail__back" type="button" @click="goBack">
        <ArrowLeft :size="15" />
        <span>Explorer</span>
      </button>
      <div class="commit-detail__title">
        <p>Commit detail</p>
        <h1>{{ commitTitle }}</h1>
        <small>{{ props.repoId }} · {{ props.commitSha }}</small>
      </div>
      <button
        class="commit-detail__open"
        type="button"
        :disabled="openingEditor || loading || hasError || !hasCommitDetail || editorLaunch?.available === false"
        :title="editorLaunch?.available === false ? editorLaunch.reason || 'Open in editor unavailable' : undefined"
        @click="handleOpenInEditor"
      >
        {{ openingEditor ? 'Opening…' : 'Open in editor' }}
      </button>
    </header>

    <div v-if="loading" class="commit-detail__empty">Loading commit detail…</div>
    <div v-else-if="hasError" class="commit-detail__empty commit-detail__empty--error">
      <strong>Unable to load this commit.</strong>
      <p>{{ loadError }}</p>
      <button class="commit-detail__secondary" type="button" @click="loadDetail">Retry</button>
    </div>
    <div v-else-if="!hasCommitDetail" class="commit-detail__empty commit-detail__empty--error">
      <strong>No commit data returned.</strong>
      <p>The backend did not provide detail for this commit.</p>
      <button class="commit-detail__secondary" type="button" @click="loadDetail">Retry</button>
    </div>
    <div v-else class="commit-detail__grid">
      <article class="commit-detail__panel">
        <section class="commit-detail__summary">
          <strong>{{ commitTitle }}</strong>
          <p>{{ commitAuthor }} · {{ commitDateLabel }}</p>
          <div class="commit-detail__stats">
            <article><span>Files changed</span><strong>{{ commitFiles.length }}</strong></article>
            <article><span>Lines added</span><strong>{{ detail?.linesAdded ?? 0 }}</strong></article>
            <article><span>Lines removed</span><strong>{{ detail?.linesRemoved ?? 0 }}</strong></article>
          </div>
        </section>

        <section class="commit-detail__section">
          <div class="commit-detail__section-header">
            <div>
              <h2>Annotations</h2>
              <p>Notes attached to the selected day.</p>
            </div>
            <button class="commit-detail__secondary" type="button" @click="openAnnotationModal()">Add annotation</button>
          </div>
          <div v-if="commitAnnotations.length" class="commit-detail__list">
            <button
              v-for="annotation in commitAnnotations"
              :key="annotation.id"
              class="commit-detail__annotation"
              type="button"
              @click="openAnnotationModal(annotation)"
            >
              <strong>{{ annotation.name || annotation.title }}</strong>
              <p>{{ annotation.description || 'No description yet.' }}</p>
              <small>{{ annotation.annotationType }}</small>
            </button>
          </div>
          <div v-else class="commit-detail__empty commit-detail__empty--compact">No annotations on this day.</div>
        </section>
      </article>

      <article class="commit-detail__panel commit-detail__panel--files">
        <div class="commit-detail__section-header">
          <div>
            <h2>Changed files</h2>
            <p>{{ commitFiles.length }} files visible</p>
          </div>
        </div>
        <div class="commit-detail__files">
          <div v-for="file in commitFiles" :key="`${props.repoId}:${file.filePath}`" class="commit-detail__file">
            <div>
              <strong>{{ file.filePath }}</strong>
              <p>{{ file.language ?? 'Unknown language' }}</p>
            </div>
            <span>{{ file.linesAdded }} + / {{ file.linesRemoved }} -</span>
          </div>
          <div v-if="!commitFiles.length" class="commit-detail__empty commit-detail__empty--compact">
            No file changes were returned for this commit.
          </div>
        </div>
      </article>
    </div>

    <ExplorerAnnotationModal
      :open="annotationDialogOpen"
      :annotation="editingAnnotation"
      :day="selectedDate"
      :commit-label="commit ? `${commit.repoId} · ${commit.commitSha.slice(0, 8)}` : null"
      :mode="editingAnnotation ? 'edit' : 'create'"
      @close="annotationDialogOpen = false"
      @delete="handleDeleteAnnotation"
      @save="handleSaveAnnotation"
    />
  </section>
</template>

<style scoped>
.commit-detail {
  display: grid;
  gap: 12px;
  color: #162033;
}

.commit-detail__header,
.commit-detail__section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.commit-detail__back,
.commit-detail__open,
.commit-detail__secondary {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  border: 0;
  border-radius: 8px;
  padding: 6px 10px;
  font-size: 12px;
  font-weight: 700;
}

.commit-detail__back,
.commit-detail__secondary {
  background: rgba(99, 102, 241, 0.08);
  color: #4f46e5;
}

.commit-detail__open {
  background: #4f46e5;
  color: #fff;
}

.commit-detail__open:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.commit-detail__title p,
.commit-detail__title small,
.commit-detail__section-header p {
  margin: 0;
  color: #64748b;
  font-size: 11px;
}

.commit-detail__title h1 {
  margin: 4px 0;
  color: #111827;
  font-size: 14px;
}

.commit-detail__grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 0.95fr);
  gap: 12px;
}

.commit-detail__panel {
  display: grid;
  gap: 12px;
  border: 1px solid rgba(148, 163, 184, 0.18);
  border-radius: 10px;
  padding: 12px;
  background: rgba(255, 255, 255, 0.96);
  box-shadow: none;
}

.commit-detail__summary {
  display: grid;
  gap: 10px;
  border-radius: 10px;
  padding: 12px;
  background: rgba(99, 102, 241, 0.08);
}

.commit-detail__summary strong {
  color: #111827;
  font-size: 14px;
}

.commit-detail__stats {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8px;
}

.commit-detail__stats article {
  border-radius: 8px;
  padding: 9px 10px;
  background: rgba(255, 255, 255, 0.94);
}

.commit-detail__stats span {
  display: block;
  color: #64748b;
  font-size: 10px;
  text-transform: uppercase;
}

.commit-detail__stats strong {
  color: #111827;
  font-size: 16px;
}

.commit-detail__list,
.commit-detail__files {
  display: grid;
  gap: 6px;
}

.commit-detail__annotation,
.commit-detail__file {
  display: grid;
  gap: 6px;
  border: 1px solid rgba(148, 163, 184, 0.18);
  border-radius: 10px;
  padding: 10px 11px;
  background: rgba(248, 250, 252, 0.96);
  text-align: left;
}

.commit-detail__annotation p,
.commit-detail__file p {
  margin: 0;
  color: #64748b;
  font-size: 11px;
}

.commit-detail__annotation strong,
.commit-detail__file strong {
  color: #111827;
  font-size: 12px;
}

.commit-detail__annotation small {
  color: #94a3b8;
  font-size: 10px;
}

.commit-detail__section {
  display: grid;
  gap: 10px;
}

.commit-detail__section h2 {
  margin: 0;
  color: #111827;
  font-size: 13px;
}

.commit-detail__empty {
  border-radius: 10px;
  padding: 12px;
  background: rgba(248, 250, 252, 0.96);
  color: #64748b;
  font-size: 12px;
}

.commit-detail__empty--error {
  display: grid;
  gap: 8px;
}

.commit-detail__empty--compact {
  min-height: 0;
}

.commit-detail__panel--files {
  align-content: start;
}

.commit-detail__file {
  grid-template-columns: minmax(0, 1fr) auto;
  align-items: center;
}

.commit-detail__file span {
  color: #64748b;
  font-size: 11px;
}

@media (max-width: 1080px) {
  .commit-detail__grid {
    grid-template-columns: 1fr;
  }
}
</style>
