<script setup lang="ts">
import { computed, onMounted } from 'vue'
import SettingsWorkspaceLayout from '../components/SettingsWorkspaceLayout.vue'
import { useAppearanceStore } from '../stores/appearance'
import type { AppearanceAccent, AppearanceMode } from '../types/api'

const appearance = useAppearanceStore()

const modeOptions: Array<{ value: AppearanceMode; label: string }> = [
  { value: 'system', label: 'System' },
  { value: 'light', label: 'Light' },
  { value: 'dark', label: 'Dark' },
]

const accentOptions: Array<{ value: AppearanceAccent; label: string; swatch: string }> = [
  { value: 'indigo', label: 'Indigo', swatch: '#6366f1' },
  { value: 'blue', label: 'Blue', swatch: '#2f80ed' },
  { value: 'amber', label: 'Amber', swatch: '#f2c94c' },
]

const controlsDisabled = computed(() => appearance.loading || appearance.saving)
const statusMessage = computed(() => {
  if (appearance.error) {
    return appearance.error
  }
  if (appearance.saving) {
    return 'Saving appearance settings…'
  }
  return null
})

onMounted(() => {
  if (!appearance.initialized) {
    void appearance.initialize()
  }
})

async function handleModeChange(event: Event) {
  const target = event.target as HTMLSelectElement | null
  if (!target) {
    return
  }
  await appearance.updateMode(target.value as AppearanceMode)
}

async function handleAccentChange(value: AppearanceAccent) {
  await appearance.updateAccent(value)
}

async function handleAnimateChartsToggle() {
  await appearance.updateAnimateCharts(!appearance.animateCharts)
}

async function handleCompactRowsToggle() {
  await appearance.updateCompactRows(!appearance.compactRows)
}
</script>

<template>
  <SettingsWorkspaceLayout
    title="Appearance"
    description="Control visual preferences for the app chrome and content density."
  >
    <section class="appearance-settings">
      <header class="surface-header surface-header--settings appearance-settings__header">
        <div>
          <p class="workspace-sidebar__eyebrow">Settings</p>
          <h2>Appearance</h2>
          <p>Control visual preferences for the app chrome and content density.</p>
        </div>
      </header>

      <div
        v-if="appearance.loading && !appearance.initialized"
        class="workspace-empty-state workspace-empty-state--compact"
      >
        <strong>Loading appearance settings…</strong>
        <p>Reading the saved configuration from the backend.</p>
      </div>

      <div v-else class="appearance-settings__grid">
        <section class="appearance-card">
          <div class="appearance-card__title">Theme</div>

          <div class="appearance-card__rows">
            <label class="appearance-row">
              <div class="appearance-row__copy">
                <strong>Mode</strong>
                <p>Pick a fixed mode or follow your OS setting</p>
              </div>

              <div class="appearance-row__control">
                <select
                  class="appearance-select"
                  :value="appearance.mode"
                  :disabled="controlsDisabled"
                  @change="handleModeChange"
                >
                  <option v-for="option in modeOptions" :key="option.value" :value="option.value">
                    {{ option.label }}
                  </option>
                </select>
              </div>
            </label>

            <div class="appearance-row">
              <div class="appearance-row__copy">
                <strong>Accent color</strong>
                <p>Primary tint used for highlights and tags</p>
              </div>

              <div class="appearance-row__control">
                <div class="appearance-swatch-group" role="radiogroup" aria-label="Accent color">
                  <button
                    v-for="option in accentOptions"
                    :key="option.value"
                    type="button"
                    class="appearance-swatch"
                    :class="{ 'is-active': appearance.accent === option.value }"
                    :aria-pressed="appearance.accent === option.value"
                    :disabled="controlsDisabled"
                    :title="option.label"
                    @click="handleAccentChange(option.value)"
                  >
                    <span class="appearance-swatch__dot" :style="{ background: option.swatch }" />
                  </button>
                </div>
              </div>
            </div>
          </div>
        </section>

        <section class="appearance-card">
          <div class="appearance-card__title">Behavior</div>

          <div class="appearance-card__rows">
            <div class="appearance-row">
              <div class="appearance-row__copy">
                <strong>Animate charts</strong>
                <p>Use motion in graphs and chart transitions</p>
              </div>

              <div class="appearance-row__control">
                <button
                  type="button"
                  class="appearance-toggle"
                  :class="{ 'is-on': appearance.animateCharts }"
                  :aria-pressed="appearance.animateCharts"
                  :disabled="controlsDisabled"
                  @click="handleAnimateChartsToggle"
                >
                  <span class="appearance-toggle__thumb" />
                </button>
              </div>
            </div>

            <div class="appearance-row">
              <div class="appearance-row__copy">
                <strong>Compact rows</strong>
                <p>Reduce row height in the shell and dense content areas</p>
              </div>

              <div class="appearance-row__control">
                <button
                  type="button"
                  class="appearance-toggle"
                  :class="{ 'is-on': appearance.compactRows }"
                  :aria-pressed="appearance.compactRows"
                  :disabled="controlsDisabled"
                  @click="handleCompactRowsToggle"
                >
                  <span class="appearance-toggle__thumb" />
                </button>
              </div>
            </div>
          </div>
        </section>
      </div>

      <p v-if="statusMessage" class="appearance-settings__status" :class="{ 'is-error': !!appearance.error }">
        {{ statusMessage }}
      </p>
    </section>
  </SettingsWorkspaceLayout>
</template>

<style scoped>
.appearance-settings {
  display: grid;
  gap: 10px;
}

.appearance-settings__header {
  margin-bottom: 0;
}

.appearance-settings__header h2 {
  margin: 0 0 2px;
  font-size: 14px;
  letter-spacing: -0.02em;
}

.appearance-settings__header p:not(.workspace-sidebar__eyebrow) {
  margin: 0;
  color: var(--cf-text-secondary);
  font-size: 10px;
  line-height: 1.35;
}

.appearance-settings__grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.appearance-card {
  border: 1px solid color-mix(in srgb, var(--cf-border) 84%, transparent);
  border-radius: 10px;
  background: color-mix(in srgb, var(--cf-surface) 98%, transparent);
  box-shadow: none;
  overflow: hidden;
}

.appearance-card__title {
  padding: 10px 14px 8px;
  border-bottom: 1px solid color-mix(in srgb, var(--cf-border) 68%, transparent);
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  color: var(--cf-text-tertiary);
}

.appearance-card__rows {
  display: grid;
}

.appearance-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 14px;
  align-items: center;
  min-height: 58px;
  padding: 10px 14px;
  border-top: 1px solid color-mix(in srgb, var(--cf-border) 68%, transparent);
}

.appearance-row:first-child {
  border-top: 0;
}

.appearance-row__copy {
  display: grid;
  gap: 2px;
  min-width: 0;
}

.appearance-row__copy strong {
  font-size: 12px;
  font-weight: 600;
  line-height: 1.2;
}

.appearance-row__copy p {
  margin: 0;
  max-width: 29rem;
  color: var(--cf-text-secondary);
  font-size: 10px;
  line-height: 1.35;
}

.appearance-row__control {
  display: inline-flex;
  align-items: center;
  justify-content: flex-end;
}

.appearance-select {
  min-width: 118px;
  height: 26px;
  padding: 0 28px 0 10px;
  border: 1px solid color-mix(in srgb, var(--cf-border) 82%, transparent);
  border-radius: 7px;
  background: color-mix(in srgb, var(--cf-surface) 96%, transparent);
  color: var(--cf-text);
  font-size: 11px;
  font-weight: 600;
  outline: none;
}

.appearance-select:focus {
  border-color: color-mix(in srgb, var(--cf-accent) 35%, var(--cf-border));
  box-shadow: 0 0 0 3px color-mix(in srgb, var(--cf-accent-subtle) 92%, transparent);
}

.appearance-swatch-group {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.appearance-swatch {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 20px;
  height: 20px;
  padding: 0;
  border: 1px solid transparent;
  border-radius: 999px;
  background: transparent;
  cursor: pointer;
  transition: border-color 140ms ease, background-color 140ms ease, transform 140ms ease;
}

.appearance-swatch:hover:not(:disabled) {
  background: color-mix(in srgb, var(--cf-accent-subtle) 74%, transparent);
}

.appearance-swatch.is-active {
  border-color: color-mix(in srgb, var(--cf-text) 12%, transparent);
  background: color-mix(in srgb, var(--cf-surface) 72%, transparent);
}

.appearance-swatch__dot {
  width: 10px;
  height: 10px;
  border-radius: 999px;
  box-shadow: inset 0 0 0 1px rgba(255, 255, 255, 0.45);
}

.appearance-toggle {
  position: relative;
  display: inline-flex;
  align-items: center;
  width: 28px;
  height: 16px;
  padding: 0 2px;
  border: 1px solid color-mix(in srgb, var(--cf-border) 92%, transparent);
  border-radius: 999px;
  background: rgba(148, 163, 184, 0.18);
  cursor: pointer;
  transition: background-color 140ms ease, border-color 140ms ease;
}

.appearance-toggle__thumb {
  width: 10px;
  height: 10px;
  border-radius: 999px;
  background: #ffffff;
  box-shadow: 0 1px 2px rgba(15, 23, 42, 0.22);
  transition: transform 140ms ease;
}

.appearance-toggle.is-on {
  border-color: color-mix(in srgb, var(--cf-accent) 60%, transparent);
  background: var(--cf-accent);
}

.appearance-toggle.is-on .appearance-toggle__thumb {
  transform: translateX(12px);
}

.appearance-toggle:disabled,
.appearance-swatch:disabled,
.appearance-select:disabled {
  opacity: 0.58;
  cursor: default;
}

.appearance-settings__status {
  margin: 0;
  color: var(--cf-text-secondary);
  font-size: 11px;
  line-height: 1.4;
}

.appearance-settings__status.is-error {
  color: var(--cf-danger);
}

@media (max-width: 900px) {
  .appearance-settings__grid {
    grid-template-columns: 1fr;
  }

  .appearance-row {
    grid-template-columns: 1fr;
    justify-items: start;
  }

  .appearance-row__control {
    justify-content: flex-start;
  }
}
</style>
