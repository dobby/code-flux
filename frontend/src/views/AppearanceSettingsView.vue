<script setup lang="ts">
import { computed } from 'vue'
import { Moon, Sun } from 'lucide-vue-next'
import SettingsWorkspaceLayout from '../components/SettingsWorkspaceLayout.vue'
import { useTheme } from '../composables/useTheme'

const { theme, toggleTheme } = useTheme()

const themeLabel = computed(() => (theme.value === 'dark' ? 'Dark' : 'Light'))
</script>

<template>
  <SettingsWorkspaceLayout title="Appearance" description="Adjust the local visual presentation of the dashboard workspace.">
    <header class="surface-header surface-header--settings">
      <div>
        <p class="workspace-sidebar__eyebrow">Appearance settings</p>
        <h2>Appearance</h2>
        <p>Keep the shell readable and restrained while you tune the local theme behavior for this machine.</p>
      </div>
    </header>

    <div class="settings-sheet-grid">
      <section class="settings-sheet">
        <div class="settings-sheet__header">Theme</div>
        <div class="settings-sheet__rows">
          <div class="settings-row">
            <div class="settings-row__copy">
              <strong>Current mode</strong>
              <p>The active theme used by the app shell on this machine.</p>
            </div>
            <div class="settings-pill">{{ themeLabel }}</div>
          </div>

          <div class="settings-row">
            <div class="settings-row__copy">
              <strong>Switch mode</strong>
              <p>Flip the workspace between light and dark presentation.</p>
            </div>
            <div class="settings-row__control">
              <button class="button button--primary" type="button" @click="toggleTheme">
                <Sun v-if="theme === 'dark'" :size="16" />
                <Moon v-else :size="16" />
                Switch to {{ theme === 'dark' ? 'light' : 'dark' }} mode
              </button>
            </div>
          </div>
        </div>
      </section>
    </div>
  </SettingsWorkspaceLayout>
</template>
