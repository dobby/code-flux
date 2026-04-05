<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import {
  Copy,
  FolderSync,
  Minus,
  Plus,
  RefreshCcw,
  Save,
  WandSparkles,
} from 'lucide-vue-next'
import { getConfigBuilder, updateConfigBuilder, updateConfigFile } from '../api/client'
import SettingsWorkspaceLayout from '../components/SettingsWorkspaceLayout.vue'
import type { ConfigAuthor, ConfigClassificationRule, ConfigRepo, EditableDashboardConfig } from '../types/api'
import { cloneConfig, createEmptyAuthor, createEmptyRepo, createEmptyRule, listToText, serializeConfigToYaml, setListFromText } from '../lib/config-builder'
import { useWorkspaceStore } from '../stores/workspace'

const workspace = useWorkspaceStore()

const activeTab = ref<'builder' | 'yaml'>('builder')
const configPath = ref('')
const builderConfig = ref<EditableDashboardConfig | null>(null)
const languageEntries = ref<Array<{ extension: string; language: string }>>([])
const yaml = ref('')
const yamlDirty = ref(false)
const loading = ref(false)
const builderSaving = ref(false)
const yamlSaving = ref(false)
const notice = ref<string | null>(null)
const error = ref<string | null>(null)

const metricOptions = [
  { value: 'lines_added', label: 'Lines added' },
  { value: 'lines_removed', label: 'Lines removed' },
  { value: 'net_lines', label: 'Net lines' },
  { value: 'commit_count', label: 'Commit count' },
  { value: 'file_count', label: 'File count' },
] as const

const groupByOptions = [
  { value: 'author', label: 'Author' },
  { value: 'repo', label: 'Repository' },
  { value: 'language', label: 'Language' },
  { value: 'category', label: 'Category' },
  { value: 'subtype', label: 'Subtype' },
  { value: 'product_code', label: 'Product code' },
  { value: 'cohort', label: 'Cohort' },
  { value: 'none', label: 'Total' },
] as const

const chartLibraryOptions = [
  { value: 'echarts', label: 'ECharts' },
  { value: 'chartjs', label: 'Chart.js' },
] as const

const classificationCategoryOptions = [
  'production',
  'test',
  'docs',
  'generated',
  'config',
]

const hasConfig = computed(() => builderConfig.value != null)

watch(
  builderConfig,
  (value) => {
    if (!value || yamlDirty.value) {
      return
    }
    yaml.value = serializeConfigToYaml(composeConfigForSave())
  },
  { deep: true },
)

watch(
  languageEntries,
  () => {
    if (!builderConfig.value) {
      return
    }
    builderConfig.value.classification.languageByExtension = Object.fromEntries(
      languageEntries.value
        .map((entry) => [entry.extension.trim(), entry.language.trim()] as const)
        .filter(([extension, language]) => extension && language),
    )
  },
  { deep: true },
)

function toMessage(caught: unknown): string {
  return caught instanceof Error ? caught.message : 'Unexpected configuration error'
}

function syncLanguageEntries(config: EditableDashboardConfig) {
  languageEntries.value = Object.entries(config.classification.languageByExtension)
    .sort(([left], [right]) => left.localeCompare(right))
    .map(([extension, language]) => ({ extension, language }))
}

async function load() {
  loading.value = true
  error.value = null
  notice.value = null

  try {
    const response = await getConfigBuilder()
    configPath.value = response.path
    builderConfig.value = cloneConfig(response.config)
    syncLanguageEntries(response.config)
    yaml.value = response.yaml
    yamlDirty.value = false
    await workspace.refreshSnapshots()
  } catch (caught) {
    error.value = toMessage(caught)
  } finally {
    loading.value = false
  }
}

function composeConfigForSave(): EditableDashboardConfig {
  const config = cloneConfig(builderConfig.value as EditableDashboardConfig)
  config.repos = config.repos.map((repo) => ({
    ...repo,
    productCode: repo.productCode?.trim() ? repo.productCode.trim() : null,
    branchPatterns: repo.branchPatterns.filter(Boolean),
    excludeBranchPatterns: repo.excludeBranchPatterns.filter(Boolean),
    excludePathGlobs: repo.excludePathGlobs.filter(Boolean),
  }))
  config.authors.include = config.authors.include.map((author) => ({
    ...author,
    cohort: author.cohort?.trim() ? author.cohort.trim() : null,
    emails: author.emails.filter(Boolean),
    names: author.names.filter(Boolean),
  }))
  config.classification.rules = config.classification.rules.map((rule) => ({
    ...rule,
    whenPathMatches: rule.whenPathMatches.filter(Boolean),
  }))
  config.classification.languageByExtension = Object.fromEntries(
    languageEntries.value
      .map((entry) => [entry.extension.trim(), entry.language.trim()] as const)
      .filter(([extension, language]) => extension && language),
  )
  return config
}

async function saveBuilder() {
  if (!builderConfig.value) {
    return
  }

  builderSaving.value = true
  error.value = null
  notice.value = null

  try {
    const response = await updateConfigBuilder(composeConfigForSave())
    builderConfig.value = cloneConfig(response.config)
    syncLanguageEntries(response.config)
    configPath.value = response.path
    yaml.value = response.yaml
    yamlDirty.value = false
    notice.value = response.restartRequired
      ? 'Visual config saved. Restart the backend or packaged app to apply changes.'
      : 'Visual config saved.'
  } catch (caught) {
    error.value = toMessage(caught)
  } finally {
    builderSaving.value = false
  }
}

async function saveYaml() {
  yamlSaving.value = true
  error.value = null
  notice.value = null

  try {
    const response = await updateConfigFile(yaml.value)
    yaml.value = response.yaml
    yamlDirty.value = false
    notice.value = response.restartRequired
      ? 'YAML config saved. Restart the backend or packaged app to apply changes.'
      : 'YAML config saved.'
    await load()
  } catch (caught) {
    error.value = toMessage(caught)
  } finally {
    yamlSaving.value = false
  }
}

async function copyYaml() {
  try {
    await navigator.clipboard.writeText(yaml.value)
    notice.value = 'YAML copied to clipboard.'
  } catch (caught) {
    error.value = toMessage(caught)
  }
}

async function pasteYaml() {
  try {
    yaml.value = await navigator.clipboard.readText()
    yamlDirty.value = true
    notice.value = 'Clipboard pasted into the YAML editor. Save to apply it.'
  } catch (caught) {
    error.value = toMessage(caught)
  }
}

function addRepo() {
  builderConfig.value?.repos.push(createEmptyRepo())
}

function removeRepo(index: number) {
  builderConfig.value?.repos.splice(index, 1)
}

function addAuthor() {
  builderConfig.value?.authors.include.push(createEmptyAuthor())
}

function removeAuthor(index: number) {
  builderConfig.value?.authors.include.splice(index, 1)
}

function addRule() {
  builderConfig.value?.classification.rules.push(createEmptyRule())
}

function removeRule(index: number) {
  builderConfig.value?.classification.rules.splice(index, 1)
}

function addLanguageEntry() {
  languageEntries.value.push({ extension: '', language: '' })
}

function removeLanguageEntry(index: number) {
  languageEntries.value.splice(index, 1)
}

function updateRepoList(repo: ConfigRepo, key: 'branchPatterns' | 'excludeBranchPatterns' | 'excludePathGlobs', value: string) {
  repo[key] = setListFromText(value)
}

function updateAuthorList(author: ConfigAuthor, key: 'emails' | 'names', value: string) {
  author[key] = setListFromText(value)
}

function updateRuleMatches(rule: ConfigClassificationRule, value: string) {
  rule.whenPathMatches = setListFromText(value)
}

onMounted(() => {
  void load()
})
</script>

<template>
  <SettingsWorkspaceLayout
    title="General"
    description="Manage snapshots, repository config, and the editable local config file from a dedicated content-area settings page."
  >
    <header class="surface-header">
      <div>
        <p class="workspace-sidebar__eyebrow">General settings</p>
        <h2>Config builder</h2>
        <p>Use the builder as the primary editing surface, then switch to YAML when you need direct copy, paste, or hand edits.</p>
      </div>
      <div class="surface-header__actions">
        <button class="button button--ghost" data-testid="build-current-snapshots" type="button" @click="workspace.buildSnapshots">
          <FolderSync :size="16" />
          Build current snapshots
        </button>
        <button class="button button--ghost" type="button" @click="load">
          <RefreshCcw :size="16" />
          Reload
        </button>
      </div>
    </header>

    <div class="settings-tabs" role="tablist" aria-label="Config editor tabs">
      <button class="settings-tab" :class="{ 'settings-tab--active': activeTab === 'builder' }" data-testid="config-tab-builder" type="button" @click="activeTab = 'builder'">
        <WandSparkles :size="16" />
        Builder
      </button>
      <button class="settings-tab" :class="{ 'settings-tab--active': activeTab === 'yaml' }" data-testid="config-tab-yaml" type="button" @click="activeTab = 'yaml'">
        <Copy :size="16" />
        YAML
      </button>
    </div>

    <p v-if="error" class="form-error">{{ error }}</p>
    <p v-if="notice" class="form-notice">{{ notice }}</p>

    <div v-if="loading" class="workspace-empty-state workspace-empty-state--compact">
      <strong>Loading config…</strong>
      <p>Fetching the current config file and visual schema.</p>
    </div>

    <template v-else-if="hasConfig && builderConfig">
      <div class="editor-layout">
        <div v-if="activeTab === 'builder'" class="config-builder">
          <section class="config-builder__section">
          <div class="config-builder__section-head">
            <div>
              <p class="workspace-sidebar__eyebrow">Runtime</p>
              <strong>App and Git process</strong>
            </div>
            <button class="button button--primary" data-testid="config-builder-save" type="button" :disabled="builderSaving" @click="saveBuilder">
              <Save :size="16" />
              {{ builderSaving ? 'Saving…' : 'Save builder config' }}
            </button>
          </div>

          <div class="field-grid field-grid--triple">
            <label class="field">
              <span>Base URL</span>
              <input v-model="builderConfig.app.baseUrl" data-testid="config-app-base-url" />
            </label>
            <label class="field">
              <span>Data directory</span>
              <input v-model="builderConfig.app.dataDir" data-testid="config-app-data-dir" />
            </label>
            <label class="field">
              <span>Log level</span>
              <input v-model="builderConfig.app.logLevel" data-testid="config-app-log-level" />
            </label>
          </div>

          <div class="field-grid field-grid--triple">
            <label class="field">
              <span>Git executable</span>
              <input v-model="builderConfig.git.executable" data-testid="config-git-executable" />
            </label>
            <label class="field">
              <span>Mirror directory</span>
              <input v-model="builderConfig.git.mirrorDir" data-testid="config-git-mirror-dir" />
            </label>
            <label class="field">
              <span>Timeout seconds</span>
              <input v-model.number="builderConfig.git.timeoutSeconds" data-testid="config-git-timeout" min="1" type="number" />
            </label>
          </div>

          <div class="field-grid field-grid--triple">
            <label class="field">
              <span>Git auth username</span>
              <input v-model="builderConfig.git.auth.httpUsername" data-testid="config-git-username" />
            </label>
            <label class="field field--wide">
              <span>Git auth token</span>
              <input v-model="builderConfig.git.auth.httpToken" data-testid="config-git-token" />
            </label>
          </div>

          <div class="settings-toggle-grid">
            <label class="field field--checkbox">
              <input v-model="builderConfig.app.openBrowserOnStart" data-testid="config-open-browser" type="checkbox" />
              <span>Open browser on start</span>
            </label>
            <label class="field field--checkbox">
              <input v-model="builderConfig.git.includeMergeCommits" data-testid="config-include-merges" type="checkbox" />
              <span>Include merge commits</span>
            </label>
            <label class="field field--checkbox">
              <input v-model="builderConfig.git.deduplicateByPatchId" data-testid="config-dedupe-patch" type="checkbox" />
              <span>Deduplicate by patch ID</span>
            </label>
            <label class="field field--checkbox">
              <input v-model="builderConfig.git.useAuthoredDate" data-testid="config-authored-date" type="checkbox" />
              <span>Use authored date</span>
            </label>
          </div>
          </section>

          <section class="config-builder__section">
          <div class="config-builder__section-head">
            <div>
              <p class="workspace-sidebar__eyebrow">Repositories</p>
              <strong>Sync targets and branch rules</strong>
            </div>
            <button class="button button--ghost" data-testid="config-add-repo" type="button" @click="addRepo">
              <Plus :size="16" />
              Add repository
            </button>
          </div>

          <div class="config-stack">
            <article v-for="(repo, index) in builderConfig.repos" :key="`repo-${index}`" class="config-card">
              <div class="config-card__head">
                <strong>{{ repo.displayName || `Repository ${index + 1}` }}</strong>
                <button class="icon-button" type="button" :data-testid="`config-remove-repo-${index}`" @click="removeRepo(index)">
                  <Minus :size="14" />
                </button>
              </div>

              <div class="field-grid field-grid--triple">
                <label class="field"><span>ID</span><input v-model="repo.id" /></label>
                <label class="field"><span>Display name</span><input v-model="repo.displayName" /></label>
                <label class="field"><span>Product code</span><input v-model="repo.productCode" /></label>
              </div>

              <div class="field-grid">
                <label class="field field--wide"><span>Clone URL</span><input v-model="repo.cloneUrl" /></label>
                <label class="field field--checkbox config-card__toggle">
                  <input v-model="repo.enabled" type="checkbox" />
                  <span>Enabled</span>
                </label>
              </div>

              <div class="field-grid field-grid--triple">
                <label class="field">
                  <span>Branch patterns</span>
                  <textarea :value="listToText(repo.branchPatterns)" rows="5" @input="updateRepoList(repo, 'branchPatterns', ($event.target as HTMLTextAreaElement).value)" />
                </label>
                <label class="field">
                  <span>Exclude branch patterns</span>
                  <textarea :value="listToText(repo.excludeBranchPatterns)" rows="5" @input="updateRepoList(repo, 'excludeBranchPatterns', ($event.target as HTMLTextAreaElement).value)" />
                </label>
                <label class="field">
                  <span>Exclude path globs</span>
                  <textarea :value="listToText(repo.excludePathGlobs)" rows="5" @input="updateRepoList(repo, 'excludePathGlobs', ($event.target as HTMLTextAreaElement).value)" />
                </label>
              </div>
            </article>
          </div>
          </section>

          <section class="config-builder__section">
          <div class="config-builder__section-head">
            <div>
              <p class="workspace-sidebar__eyebrow">Authors</p>
              <strong>Identity matching and cohorts</strong>
            </div>
            <button class="button button--ghost" data-testid="config-add-author" type="button" @click="addAuthor">
              <Plus :size="16" />
              Add author
            </button>
          </div>

          <div class="config-stack">
            <article v-for="(author, index) in builderConfig.authors.include" :key="`author-${index}`" class="config-card">
              <div class="config-card__head">
                <strong>{{ author.displayName || `Author ${index + 1}` }}</strong>
                <button class="icon-button" type="button" :data-testid="`config-remove-author-${index}`" @click="removeAuthor(index)">
                  <Minus :size="14" />
                </button>
              </div>

              <div class="field-grid field-grid--triple">
                <label class="field"><span>ID</span><input v-model="author.id" /></label>
                <label class="field"><span>Display name</span><input v-model="author.displayName" /></label>
                <label class="field"><span>Cohort</span><input v-model="author.cohort" /></label>
              </div>

              <div class="field-grid field-grid--double">
                <label class="field">
                  <span>Emails</span>
                  <textarea :value="listToText(author.emails)" rows="4" @input="updateAuthorList(author, 'emails', ($event.target as HTMLTextAreaElement).value)" />
                </label>
                <label class="field">
                  <span>Names</span>
                  <textarea :value="listToText(author.names)" rows="4" @input="updateAuthorList(author, 'names', ($event.target as HTMLTextAreaElement).value)" />
                </label>
              </div>
            </article>
          </div>
          </section>

          <section class="config-builder__section">
          <div class="config-builder__section-head">
            <div>
              <p class="workspace-sidebar__eyebrow">Classification</p>
              <strong>Language map and rule system</strong>
            </div>
            <div class="editor-actions">
              <button class="button button--ghost" type="button" @click="addLanguageEntry">
                <Plus :size="16" />
                Add extension
              </button>
              <button class="button button--ghost" type="button" @click="addRule">
                <Plus :size="16" />
                Add rule
              </button>
            </div>
          </div>

          <div class="config-card">
            <strong>Language by extension</strong>
            <div class="config-stack config-stack--tight">
              <div v-for="(entry, index) in languageEntries" :key="`lang-${index}`" class="field-grid field-grid--double field-grid--actions">
                <label class="field"><span>Extension</span><input v-model="entry.extension" /></label>
                <label class="field"><span>Language</span><input v-model="entry.language" /></label>
                <button class="icon-button" type="button" :data-testid="`config-remove-language-${index}`" @click="removeLanguageEntry(index)">
                  <Minus :size="14" />
                </button>
              </div>
            </div>
          </div>

          <div class="config-stack">
            <article v-for="(rule, index) in builderConfig.classification.rules" :key="`rule-${index}`" class="config-card">
              <div class="config-card__head">
                <strong>{{ rule.id || `Rule ${index + 1}` }}</strong>
                <button class="icon-button" type="button" :data-testid="`config-remove-rule-${index}`" @click="removeRule(index)">
                  <Minus :size="14" />
                </button>
              </div>
              <div class="field-grid field-grid--triple">
                <label class="field"><span>Rule ID</span><input v-model="rule.id" /></label>
                <label class="field">
                  <span>Category</span>
                  <select v-model="rule.category">
                    <option v-for="category in classificationCategoryOptions" :key="category" :value="category">{{ category }}</option>
                  </select>
                </label>
                <label class="field"><span>Subtype</span><input v-model="rule.subtype" /></label>
              </div>
              <label class="field">
                <span>Path match globs</span>
                <textarea :value="listToText(rule.whenPathMatches)" rows="4" @input="updateRuleMatches(rule, ($event.target as HTMLTextAreaElement).value)" />
              </label>
            </article>
          </div>
          </section>

          <section class="config-builder__section">
          <div class="config-builder__section-head">
            <div>
              <p class="workspace-sidebar__eyebrow">Defaults</p>
              <strong>Initial workspace behavior</strong>
            </div>
          </div>

          <div class="field-grid field-grid--triple">
            <label class="field">
              <span>Default metric</span>
              <select v-model="builderConfig.uiDefaults.defaultMetric">
                <option v-for="option in metricOptions" :key="option.value" :value="option.value">{{ option.label }}</option>
              </select>
            </label>
            <label class="field">
              <span>Default group by</span>
              <select v-model="builderConfig.uiDefaults.defaultGroupBy">
                <option v-for="option in groupByOptions" :key="option.value" :value="option.value">{{ option.label }}</option>
              </select>
            </label>
            <label class="field">
              <span>Default chart library</span>
              <select v-model="builderConfig.uiDefaults.defaultChartLibrary">
                <option v-for="option in chartLibraryOptions" :key="option.value" :value="option.value">{{ option.label }}</option>
              </select>
            </label>
          </div>

          <div class="field-grid field-grid--triple">
            <label class="field"><span>Default date from</span><input v-model="builderConfig.uiDefaults.defaultDateFrom" type="date" /></label>
            <label class="field"><span>Default date to</span><input v-model="builderConfig.uiDefaults.defaultDateTo" type="date" /></label>
            <label class="field"><span>Sync window from</span><input v-model="builderConfig.syncWindow.from" type="date" /></label>
            <label class="field"><span>Sync window to</span><input v-model="builderConfig.syncWindow.to" type="date" /></label>
          </div>

          <div class="field-grid field-grid--double">
            <label class="field">
              <span>Default include categories</span>
              <textarea :value="listToText(builderConfig.uiDefaults.defaultIncludeCategories)" rows="4" @input="builderConfig.uiDefaults.defaultIncludeCategories = setListFromText(($event.target as HTMLTextAreaElement).value)" />
            </label>
            <label class="field">
              <span>Default exclude categories</span>
              <textarea :value="listToText(builderConfig.uiDefaults.defaultExcludeCategories)" rows="4" @input="builderConfig.uiDefaults.defaultExcludeCategories = setListFromText(($event.target as HTMLTextAreaElement).value)" />
            </label>
          </div>
          </section>
        </div>

        <div v-else class="config-yaml">
          <section class="config-builder__section">
            <div class="config-builder__section-head">
              <div>
                <p class="workspace-sidebar__eyebrow">YAML workstation</p>
                <strong>Direct config editing</strong>
              </div>
              <div class="editor-actions">
                <button class="button button--ghost" data-testid="config-copy-yaml" type="button" @click="copyYaml">
                  <Copy :size="16" />
                  Copy full YAML
                </button>
                <button class="button button--ghost" data-testid="config-paste-yaml" type="button" @click="pasteYaml">
                  <Plus :size="16" />
                  Paste from clipboard
                </button>
                <button class="button button--primary" data-testid="config-save-yaml" type="button" :disabled="yamlSaving" @click="saveYaml">
                  <Save :size="16" />
                  {{ yamlSaving ? 'Saving…' : 'Save YAML' }}
                </button>
              </div>
            </div>

            <div class="field">
              <span>Config file</span>
              <input :value="configPath" disabled />
            </div>
            <div class="field">
              <span>YAML</span>
              <textarea v-model="yaml" data-testid="config-yaml" class="config-yaml__editor" rows="28" @input="yamlDirty = true" />
            </div>
            <p class="form-notice">Copy the current file as-is, or paste a full replacement and then save it back through the same validation path.</p>
          </section>
        </div>

        <section class="editor-preview">
          <div class="editor-preview__header">
            <p class="workspace-sidebar__eyebrow">Snapshot status</p>
            <strong>Repository state backfill</strong>
          </div>
          <div v-if="workspace.snapshotStatus?.items.length" class="drilldown-list">
            <article v-for="item in workspace.snapshotStatus.items" :key="`${item.repoId}:${item.refName}`" class="drilldown-list__item">
              <div>
                <strong>{{ item.repoId }}</strong>
                <p>{{ item.refName }}</p>
                <small>Latest snapshot: {{ item.latestSnapshotDate || 'none yet' }}</small>
              </div>
              <span>{{ item.status || 'idle' }}</span>
            </article>
          </div>
          <div v-else class="workspace-empty-state workspace-empty-state--compact">
            <strong>No snapshots recorded yet.</strong>
            <p>Run the current snapshot builder to populate repository-state widgets and history.</p>
          </div>
        </section>
      </div>
    </template>
  </SettingsWorkspaceLayout>
</template>
