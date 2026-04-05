<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import SettingsWorkspaceLayout from '../components/SettingsWorkspaceLayout.vue'
import { useWorkspaceStore } from '../stores/workspace'

const workspace = useWorkspaceStore()

const form = reactive({
  enabled: false,
  baseUrl: '',
  verifyTls: true,
  issueKeyRegex: '[A-Z][A-Z0-9]+-[0-9]+',
  projectKeys: '',
  token: '',
})
const feedback = ref<string | null>(null)
const testing = ref(false)
const syncing = ref(false)

const statusText = computed(() => {
  if (!workspace.jiraStatus) {
    return 'Unknown'
  }
  if (!workspace.jiraStatus.configured) {
    return 'Not configured'
  }
  if (!workspace.jiraStatus.secretConfigured) {
    return 'Token missing'
  }
  return workspace.jiraStatus.lastSyncAt ? `Last sync ${new Date(workspace.jiraStatus.lastSyncAt).toLocaleString()}` : 'Ready to sync'
})

async function load() {
  await workspace.refreshJira()
  const settings = workspace.jiraSettings
  if (!settings) {
    return
  }
  form.enabled = settings.enabled
  form.baseUrl = settings.baseUrl || ''
  form.verifyTls = settings.verifyTls
  form.issueKeyRegex = settings.issueKeyRegex
  form.projectKeys = settings.projectKeys.join(', ')
}

async function save() {
  feedback.value = null
  await workspace.saveJiraConfiguration({
    enabled: form.enabled,
    baseUrl: form.baseUrl || null,
    verifyTls: form.verifyTls,
    issueKeyRegex: form.issueKeyRegex,
    projectKeys: form.projectKeys.split(',').map((value) => value.trim()).filter(Boolean),
    token: form.token || null,
  })
  form.token = ''
  feedback.value = 'Jira settings saved.'
}

async function testConnection() {
  testing.value = true
  feedback.value = null
  try {
    const response = await workspace.testJira()
    feedback.value = response.message
    await workspace.refreshJira()
  } finally {
    testing.value = false
  }
}

async function sync() {
  syncing.value = true
  feedback.value = null
  try {
    await workspace.runJiraSync()
    feedback.value = 'Jira metadata sync completed.'
  } finally {
    syncing.value = false
  }
}

onMounted(() => {
  void load()
})
</script>

<template>
  <SettingsWorkspaceLayout
    title="Jira"
    description="Configure the read-only Jira connection, secure token handoff, and enrichment sync without exposing secrets in ordinary config responses."
  >
    <header class="surface-header">
      <div>
        <p class="workspace-sidebar__eyebrow">Jira settings</p>
        <h2>Read-only Jira enrichment</h2>
        <p>Store the non-secret connection details in SQLite and hand the token through the secure desktop path when available.</p>
      </div>
    </header>

    <div class="editor-layout">
      <form class="editor-form" @submit.prevent="save">
        <label class="field field--checkbox">
          <input v-model="form.enabled" type="checkbox" />
          <span>Enable Jira enrichment</span>
        </label>

        <label class="field">
          <span>Base URL</span>
          <input v-model="form.baseUrl" data-testid="jira-base-url" placeholder="https://jira.example.com" />
        </label>

        <label class="field">
          <span>Issue key regex</span>
          <input v-model="form.issueKeyRegex" data-testid="jira-issue-key-regex" />
        </label>

        <label class="field">
          <span>Project allowlist</span>
          <input v-model="form.projectKeys" data-testid="jira-project-keys" placeholder="MCD, PLATFORM" />
        </label>

        <label class="field">
          <span>Personal access token</span>
          <input v-model="form.token" data-testid="jira-token" type="password" placeholder="Paste a replacement token to save it" />
        </label>

        <label class="field field--checkbox">
          <input v-model="form.verifyTls" type="checkbox" />
          <span>Verify TLS certificates</span>
        </label>

        <p v-if="!form.verifyTls" class="form-warning">
          TLS verification is disabled. Keep this explicit and limited to on-prem environments that require it.
        </p>

        <div class="editor-actions">
          <button class="button button--ghost" data-testid="jira-test-connection" type="button" :disabled="testing" @click="testConnection">
            {{ testing ? 'Testing…' : 'Test connection' }}
          </button>
          <button class="button button--ghost" data-testid="jira-sync" type="button" :disabled="syncing" @click="sync">
            {{ syncing ? 'Syncing…' : 'Sync Jira metadata' }}
          </button>
          <button class="button button--primary" data-testid="jira-save" type="submit">Save settings</button>
        </div>

        <p v-if="feedback" class="form-notice">{{ feedback }}</p>
      </form>

      <section class="editor-preview">
        <div class="editor-preview__header">
          <p class="workspace-sidebar__eyebrow">Status</p>
          <strong>{{ statusText }}</strong>
        </div>
        <div class="drilldown-summary-grid">
          <article class="metric-card-mini">
            <span>Configured</span>
            <strong>{{ workspace.jiraStatus?.configured ? 'Yes' : 'No' }}</strong>
          </article>
          <article class="metric-card-mini">
            <span>Token stored</span>
            <strong>{{ workspace.jiraStatus?.secretConfigured ? 'Yes' : 'No' }}</strong>
          </article>
          <article class="metric-card-mini">
            <span>Cached issues</span>
            <strong>{{ workspace.jiraStatus?.cachedIssues ?? 0 }}</strong>
          </article>
          <article class="metric-card-mini">
            <span>Linked commits</span>
            <strong>{{ workspace.jiraStatus?.linkedCommits ?? 0 }}</strong>
          </article>
        </div>
      </section>
    </div>
  </SettingsWorkspaceLayout>
</template>
