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
  return workspace.jiraStatus.lastSyncAt ? 'Connected' : 'Ready to sync'
})

const cachedIssues = computed(() => workspace.jiraStatus?.cachedIssues ?? 0)

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
    <header class="surface-header surface-header--settings">
      <div>
        <p class="workspace-sidebar__eyebrow">Jira settings</p>
        <h2>Jira</h2>
        <p>Connect to Jira for issue metadata enrichment.</p>
      </div>
    </header>

    <p v-if="feedback" class="form-notice">{{ feedback }}</p>

    <form @submit.prevent="save">
      <div class="settings-sheet-grid">
        <section class="settings-sheet">
          <div class="settings-sheet__header">Connection</div>
          <div class="settings-sheet__rows">
            <label class="settings-row settings-row--toggle">
              <div class="settings-row__copy">
                <strong>Enable Jira enrichment</strong>
                <p>Pull issue metadata only for commit linking.</p>
              </div>
              <span class="settings-switch">
                <input v-model="form.enabled" type="checkbox" />
              </span>
            </label>

            <label class="settings-row">
              <div class="settings-row__copy">
                <strong>Base URL</strong>
                <p>Your Jira instance URL.</p>
              </div>
              <input v-model="form.baseUrl" data-testid="jira-base-url" autocomplete="url" placeholder="https://jira.example.com" />
            </label>

            <label class="settings-row">
              <div class="settings-row__copy">
                <strong>Issue key regex</strong>
                <p>Pattern to extract issue keys from commit messages.</p>
              </div>
              <input v-model="form.issueKeyRegex" data-testid="jira-issue-key-regex" autocomplete="off" />
            </label>

            <label class="settings-row">
              <div class="settings-row__copy">
                <strong>Personal access token</strong>
                <p>Used for authenticating with the Jira API.</p>
              </div>
              <input
                v-model="form.token"
                data-testid="jira-token"
                type="password"
                autocomplete="new-password"
                placeholder="Paste a replacement token to save it"
              />
            </label>

            <label class="settings-row settings-row--toggle">
              <div class="settings-row__copy">
                <strong>Verify TLS</strong>
                <p>Validate SSL certificates when connecting.</p>
              </div>
              <span class="settings-switch">
                <input v-model="form.verifyTls" type="checkbox" />
              </span>
            </label>
          </div>
        </section>

        <section class="settings-sheet">
          <div class="settings-sheet__header">Status</div>
          <div class="settings-sheet__rows">
            <div class="settings-row">
              <div class="settings-row__copy">
                <strong>Connection</strong>
                <p>Last tested connection status.</p>
              </div>
              <div class="settings-pill" :class="{ 'settings-pill--success': statusText === 'Connected' }">
                {{ statusText }}
              </div>
            </div>

            <div class="settings-row">
              <div class="settings-row__copy">
                <strong>Cached issues</strong>
                <p>Cached issue metadata from last sync.</p>
              </div>
              <div class="settings-stat">{{ cachedIssues }}</div>
            </div>
          </div>
        </section>
      </div>

      <div class="editor-actions">
        <button class="button button--ghost" data-testid="jira-test-connection" type="button" :disabled="testing" @click="testConnection">
          {{ testing ? 'Testing…' : 'Test connection' }}
        </button>
        <button class="button button--ghost" data-testid="jira-sync" type="button" :disabled="syncing" @click="sync">
          {{ syncing ? 'Syncing…' : 'Sync Jira metadata' }}
        </button>
        <button class="button button--primary" data-testid="jira-save" type="submit">Save settings</button>
      </div>
    </form>
  </SettingsWorkspaceLayout>
</template>
