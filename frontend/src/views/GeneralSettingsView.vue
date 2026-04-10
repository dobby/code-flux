<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import {
  Copy,
  FolderSync,
  Minus,
  Plus,
  RefreshCcw,
  Save,
} from 'lucide-vue-next'
import { getConfigBuilder, updateConfigBuilder, updateConfigFile } from '../api/client'
import SettingsWorkspaceLayout from '../components/SettingsWorkspaceLayout.vue'
import type { ConfigAuthor, ConfigClassificationRule, ConfigRepo, EditableDashboardConfig } from '../types/api'
import {
  cloneConfig,
  createEmptyAuthor,
  createEmptyRepo,
  createEmptyRule,
  listToText,
  serializeConfigToYaml,
  setListFromText,
} from '../lib/config-builder'
import { useWorkspaceStore } from '../stores/workspace'

const workspace = useWorkspaceStore()

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

const logLevelOptions = ['TRACE', 'DEBUG', 'INFO', 'WARN', 'ERROR'] as const

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

async function copyValue(value: string, label: string) {
  try {
    await navigator.clipboard.writeText(value)
    notice.value = `${label} copied to clipboard.`
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
    <header class="surface-header surface-header--settings">
      <div>
        <p class="workspace-sidebar__eyebrow">General settings</p>
        <h2>General</h2>
        <p>Review runtime defaults first, then open the deeper repository and YAML sections only when you need them.</p>
      </div>
      <div class="surface-header__actions">
        <button class="button button--primary" data-testid="config-builder-save" type="button" :disabled="builderSaving || !builderConfig" @click="saveBuilder">
          <Save :size="16" />
          {{ builderSaving ? 'Saving…' : 'Save settings' }}
        </button>
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

    <p v-if="error" class="form-error">{{ error }}</p>
    <p v-if="notice" class="form-notice">{{ notice }}</p>

    <div v-if="loading" class="workspace-empty-state workspace-empty-state--compact">
      <strong>Loading config…</strong>
      <p>Fetching the current config file and visual schema.</p>
    </div>

    <template v-else-if="hasConfig && builderConfig">
      <div class="settings-sheet-grid">
        <section class="settings-sheet">
          <div class="settings-sheet__header">Runtime</div>
          <div class="settings-sheet__rows">
            <label class="settings-row">
              <div class="settings-row__copy">
                <strong>Base URL</strong>
                <p>The backend API base URL.</p>
              </div>
              <input v-model="builderConfig.app.baseUrl" data-testid="config-app-base-url" />
            </label>

            <div class="settings-row">
              <div class="settings-row__copy">
                <strong>Data directory</strong>
                <p>Where synced repository data is stored.</p>
              </div>
              <div class="settings-row__control settings-row__control--inline">
                <input v-model="builderConfig.app.dataDir" data-testid="config-app-data-dir" />
                <button class="icon-button" type="button" @click="copyValue(builderConfig.app.dataDir, 'Data directory')">
                  <Copy :size="14" />
                </button>
              </div>
            </div>

            <label class="settings-row">
              <div class="settings-row__copy">
                <strong>Log level</strong>
                <p>Controls verbosity of backend logging.</p>
              </div>
              <select v-model="builderConfig.app.logLevel" data-testid="config-app-log-level">
                <option v-for="option in logLevelOptions" :key="option" :value="option">{{ option }}</option>
              </select>
            </label>
          </div>
        </section>

        <section class="settings-sheet">
          <div class="settings-sheet__header">Git</div>
          <div class="settings-sheet__rows">
            <label class="settings-row">
              <div class="settings-row__copy">
                <strong>Git executable</strong>
                <p>Path to the Git binary.</p>
              </div>
              <input v-model="builderConfig.git.executable" data-testid="config-git-executable" />
            </label>

            <label class="settings-row">
              <div class="settings-row__copy">
                <strong>Clone timeout</strong>
                <p>Maximum seconds to wait for a Git client call.</p>
              </div>
              <input v-model.number="builderConfig.git.timeoutSeconds" data-testid="config-git-timeout" min="1" type="number" />
            </label>

            <div class="settings-row">
              <div class="settings-row__copy">
                <strong>Mirror directory</strong>
                <p>Local directory for bare mirror clones.</p>
              </div>
              <div class="settings-row__control settings-row__control--inline">
                <input v-model="builderConfig.git.mirrorDir" data-testid="config-git-mirror-dir" />
                <button class="icon-button" type="button" @click="copyValue(builderConfig.git.mirrorDir ?? '', 'Mirror directory')">
                  <Copy :size="14" />
                </button>
              </div>
            </div>
          </div>
        </section>

        <section class="settings-sheet">
          <div class="settings-sheet__header">Defaults</div>
          <div class="settings-sheet__rows">
            <label class="settings-row">
              <div class="settings-row__copy">
                <strong>Default metric</strong>
                <p>Metric shown by default in charts.</p>
              </div>
              <select v-model="builderConfig.uiDefaults.defaultMetric">
                <option v-for="option in metricOptions" :key="option.value" :value="option.value">{{ option.label }}</option>
              </select>
            </label>

            <label class="settings-row">
              <div class="settings-row__copy">
                <strong>Chart library</strong>
                <p>Charting engine for visualizations.</p>
              </div>
              <select v-model="builderConfig.uiDefaults.defaultChartLibrary">
                <option v-for="option in chartLibraryOptions" :key="option.value" :value="option.value">{{ option.label }}</option>
              </select>
            </label>
          </div>
        </section>
      </div>

      <details class="settings-expandable" open>
        <summary>Repository state backfill</summary>
        <section class="settings-sheet">
          <div class="settings-sheet__rows settings-sheet__rows--stacked">
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
          </div>
        </section>
      </details>

      <details class="settings-expandable">
        <summary>Repository, author, and classification rules</summary>
        <div class="config-builder">
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
        </div>
      </details>

      <details class="settings-expandable">
        <summary>YAML workstation</summary>
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
      </details>
    </template>
  </SettingsWorkspaceLayout>
</template>
