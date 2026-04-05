<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { useWorkspaceStore } from '../stores/workspace'

const workspace = useWorkspaceStore()

type DrilldownTab = 'summary' | 'commits' | 'files' | 'contributors' | 'jira'

const activeTab = ref<DrilldownTab>('summary')
const composer = reactive({
  mode: 'create' as 'create' | 'edit',
  annotationId: null as string | null,
  title: '',
  body: '',
  color: 'accent',
})

const selectedWidgetId = computed(() => workspace.drilldown.sourceWidgetId)
const selectedDate = computed(() => workspace.drilldown.selectedDate)
const scopedAnnotations = computed(() => {
  const widgetId = selectedWidgetId.value
  const date = selectedDate.value
  if (!widgetId || !date) {
    return []
  }
  return (workspace.widgetAnnotations[widgetId] ?? []).filter((annotation) => (
    annotation.scopeDate === date || annotation.xValue === date
  ))
})

const canAnnotate = computed(() => Boolean(selectedWidgetId.value && selectedDate.value))

function resetComposer() {
  composer.mode = 'create'
  composer.annotationId = null
  composer.title = ''
  composer.body = ''
  composer.color = 'accent'
}

function openCreateComposer() {
  resetComposer()
}

function editAnnotation(annotationId: string) {
  const annotation = scopedAnnotations.value.find((entry) => entry.id === annotationId)
  if (!annotation) {
    return
  }
  composer.mode = 'edit'
  composer.annotationId = annotation.id
  composer.title = annotation.title
  composer.body = annotation.body ?? ''
  composer.color = annotation.color ?? 'accent'
  activeTab.value = 'summary'
}

async function saveAnnotation() {
  if (!selectedWidgetId.value || !selectedDate.value || !composer.title.trim()) {
    return
  }
  if (composer.mode === 'edit' && composer.annotationId) {
    await workspace.updatePointAnnotation(composer.annotationId, selectedWidgetId.value, {
      title: composer.title,
      body: composer.body || null,
      color: composer.color,
    })
  } else {
    await workspace.createPointAnnotation({
      pageWidgetInstanceId: selectedWidgetId.value,
      scopeDate: selectedDate.value,
      xValue: selectedDate.value,
      title: composer.title,
      body: composer.body || null,
    })
  }
  resetComposer()
}

async function removeAnnotation() {
  if (!selectedWidgetId.value || !composer.annotationId) {
    return
  }
  await workspace.deletePointAnnotation(composer.annotationId, selectedWidgetId.value)
  resetComposer()
}

watch(() => workspace.drilldown.open, (open) => {
  if (open) {
    activeTab.value = 'summary'
    resetComposer()
  }
})

watch(selectedDate, () => {
  resetComposer()
})

</script>

<template>
  <aside
    class="drilldown-drawer"
    :class="{ 'drilldown-drawer--open': workspace.drilldown.open }"
    data-testid="drilldown-drawer"
  >
    <div class="drilldown-drawer__header">
      <div>
        <p class="workspace-sidebar__eyebrow">Activity detail</p>
        <strong data-testid="drilldown-date">{{ workspace.drilldown.selectedDate || 'No day selected' }}</strong>
      </div>
      <button class="icon-button" type="button" @click="workspace.closeDrilldown">Close</button>
    </div>

    <div v-if="workspace.drilldown.loading" class="workspace-empty-state">
      <strong>Loading drilldown…</strong>
      <p>Resolving commits, files, contributors, and linked issues.</p>
    </div>

    <template v-else-if="workspace.drilldown.data">
      <div class="drilldown-tabs">
        <button
          v-for="tab in ['summary', 'commits', 'files', 'contributors', 'jira']"
          :key="tab"
          class="drilldown-tab"
          :class="{ 'drilldown-tab--active': activeTab === tab }"
          type="button"
          :data-testid="`drilldown-tab-${tab}`"
          @click="activeTab = tab as DrilldownTab"
        >
          {{ tab }}
        </button>
      </div>

      <section v-if="activeTab === 'summary'" class="drilldown-section" data-testid="drilldown-summary-tab">
        <div class="drilldown-summary-grid">
          <article class="metric-card-mini">
            <span>Lines added</span>
            <strong>{{ workspace.drilldown.data.summary.linesAdded }}</strong>
          </article>
          <article class="metric-card-mini">
            <span>Commits</span>
            <strong>{{ workspace.drilldown.data.summary.commitsCount }}</strong>
          </article>
          <article class="metric-card-mini">
            <span>Files changed</span>
            <strong>{{ workspace.drilldown.data.summary.filesChangedCount }}</strong>
          </article>
          <article class="metric-card-mini">
            <span>Contributors</span>
            <strong>{{ workspace.drilldown.data.summary.contributorsCount }}</strong>
          </article>
        </div>

        <section class="drilldown-section">
          <div class="drilldown-section__header">
            <div>
              <h2>Point annotations</h2>
              <p class="popover-note">These notes persist against the selected widget and day.</p>
            </div>
            <button
              class="button button--ghost"
              type="button"
              data-testid="annotation-new"
              :disabled="!canAnnotate"
              @click="openCreateComposer"
            >
              New note
            </button>
          </div>

          <div v-if="scopedAnnotations.length" class="drilldown-list" data-testid="annotation-list">
            <article
              v-for="annotation in scopedAnnotations"
              :key="annotation.id"
              class="drilldown-list__item"
            >
              <div>
                <strong>{{ annotation.title }}</strong>
                <p>{{ annotation.body || 'No additional context.' }}</p>
                <small>{{ annotation.updatedAt }}</small>
              </div>
              <button
                class="button button--ghost"
                type="button"
                :data-testid="`annotation-edit-${annotation.id}`"
                @click="editAnnotation(annotation.id)"
              >
                Edit
              </button>
            </article>
          </div>
          <div v-else class="workspace-empty-state workspace-empty-state--compact">
            <strong>No annotations for this day.</strong>
            <p>Create a note to explain a spike, deployment, or repository event.</p>
          </div>

          <form class="drilldown-annotation-form" data-testid="annotation-form" @submit.prevent="saveAnnotation">
            <label class="field">
              <span>Title</span>
              <input v-model="composer.title" data-testid="annotation-title" required />
            </label>
            <label class="field">
              <span>Notes</span>
              <textarea v-model="composer.body" data-testid="annotation-body" rows="4" />
            </label>
            <div class="drilldown-annotation-actions">
              <button class="button button--primary" data-testid="annotation-save" type="submit">
                {{ composer.mode === 'edit' ? 'Update note' : 'Save note' }}
              </button>
              <button class="button button--ghost" type="button" @click="resetComposer">Clear</button>
              <button
                v-if="composer.mode === 'edit'"
                class="button button--danger"
                data-testid="annotation-delete"
                type="button"
                @click="removeAnnotation"
              >
                Delete
              </button>
            </div>
          </form>
        </section>
      </section>

      <section v-else-if="activeTab === 'commits'" class="drilldown-section" data-testid="drilldown-commits-tab">
        <h2>Commits</h2>
        <div class="drilldown-list">
          <article v-for="commit in workspace.drilldown.data.commits.slice(0, 12)" :key="commit.commitSha" class="drilldown-list__item">
            <div>
              <strong>{{ commit.commitSha.slice(0, 8) }}</strong>
              <p>{{ commit.subject }}</p>
              <small>{{ commit.author }} · {{ new Date(commit.authoredAt).toLocaleString() }}</small>
            </div>
            <span>+{{ commit.linesAdded }} / -{{ commit.linesRemoved }}</span>
          </article>
        </div>
      </section>

      <section v-else-if="activeTab === 'files'" class="drilldown-section" data-testid="drilldown-files-tab">
        <h2>Files</h2>
        <div class="drilldown-list">
          <article v-for="file in workspace.drilldown.data.files.slice(0, 12)" :key="`${file.repoId}:${file.filePath}`" class="drilldown-list__item">
            <div>
              <strong>{{ file.filePath }}</strong>
              <small>{{ file.language || 'unknown' }} · {{ file.category || 'unknown' }}</small>
            </div>
            <span>+{{ file.linesAdded }} / -{{ file.linesRemoved }}</span>
          </article>
        </div>
      </section>

      <section v-else-if="activeTab === 'contributors'" class="drilldown-section" data-testid="drilldown-contributors-tab">
        <h2>Contributors</h2>
        <div class="drilldown-list">
          <article v-for="contributor in workspace.drilldown.data.contributors.slice(0, 10)" :key="contributor.author" class="drilldown-list__item">
            <div>
              <strong>{{ contributor.author }}</strong>
              <small>{{ contributor.commitsCount }} commits · {{ contributor.filesChangedCount }} files</small>
            </div>
            <span>+{{ contributor.linesAdded }} / -{{ contributor.linesRemoved }}</span>
          </article>
        </div>
      </section>

      <section v-else class="drilldown-section" data-testid="drilldown-jira-tab">
        <h2>Linked Jira issues</h2>
        <div v-if="workspace.drilldown.data.jiraIssues.length" class="drilldown-list">
          <article v-for="issue in workspace.drilldown.data.jiraIssues" :key="issue.issueKey" class="drilldown-list__item">
            <div>
              <strong>{{ issue.issueKey }}</strong>
              <p>{{ issue.summary || 'No summary cached' }}</p>
              <small>{{ issue.issueType || 'Unknown type' }} · {{ issue.status || 'Unknown status' }}</small>
            </div>
            <a v-if="issue.browseUrl" class="text-link" :href="issue.browseUrl" target="_blank" rel="noreferrer">Open</a>
          </article>
        </div>
        <div v-else class="workspace-empty-state workspace-empty-state--compact">
          <strong>No linked Jira issues</strong>
          <p>The selected day did not match any issue-linked commits under the current filters.</p>
        </div>
      </section>
    </template>

    <div v-else class="workspace-empty-state workspace-empty-state--compact">
      <strong>Pick a day from a compatible widget.</strong>
      <p>Time-series charts and the heatmap will open this drawer with the selected activity bucket.</p>
    </div>
  </aside>
</template>
