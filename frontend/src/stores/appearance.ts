import { computed, ref, watchEffect } from 'vue'
import { defineStore } from 'pinia'
import { getAppearanceConfig, updateAppearanceConfig } from '../api/client'
import type { AppearanceAccent, AppearanceConfig, AppearanceMode } from '../types/api'

type ResolvedTheme = 'light' | 'dark'

type AccentPalette = {
  accent: string
  hover: string
  subtle: string
}

const defaultAppearance: AppearanceConfig = {
  mode: 'system',
  accent: 'indigo',
  animateCharts: true,
  compactRows: false,
}

const accentPalettes: Record<AppearanceAccent, Record<ResolvedTheme, AccentPalette>> = {
  indigo: {
    light: {
      accent: '#6366f1',
      hover: '#4f46e5',
      subtle: 'rgba(99, 102, 241, 0.11)',
    },
    dark: {
      accent: '#7da8ff',
      hover: '#9abaff',
      subtle: 'rgba(125, 168, 255, 0.14)',
    },
  },
  blue: {
    light: {
      accent: '#2f80ed',
      hover: '#1f6fd8',
      subtle: 'rgba(47, 128, 237, 0.13)',
    },
    dark: {
      accent: '#6cb8ff',
      hover: '#94cdff',
      subtle: 'rgba(108, 184, 255, 0.16)',
    },
  },
  amber: {
    light: {
      accent: '#f2c94c',
      hover: '#ddb43f',
      subtle: 'rgba(242, 201, 76, 0.18)',
    },
    dark: {
      accent: '#f5d978',
      hover: '#fde68a',
      subtle: 'rgba(245, 217, 120, 0.18)',
    },
  },
}

function toMessage(caught: unknown): string {
  return caught instanceof Error ? caught.message : 'Unexpected appearance settings error'
}

function createSnapshot(config: AppearanceConfig): AppearanceConfig {
  return {
    mode: config.mode,
    accent: config.accent,
    animateCharts: config.animateCharts,
    compactRows: config.compactRows,
  }
}

export const useAppearanceStore = defineStore('appearance', () => {
  const mode = ref<AppearanceMode>(defaultAppearance.mode)
  const accent = ref<AppearanceAccent>(defaultAppearance.accent)
  const animateCharts = ref(defaultAppearance.animateCharts)
  const compactRows = ref(defaultAppearance.compactRows)
  const initialized = ref(false)
  const loading = ref(false)
  const saving = ref(false)
  const error = ref<string | null>(null)
  const lastSavedAt = ref<string | null>(null)
  const systemPrefersDark = ref(false)

  let mediaQueryList: MediaQueryList | null = null
  let mediaQueryListener: ((event: MediaQueryListEvent) => void) | null = null

  const resolvedMode = computed<ResolvedTheme>(() => (
    mode.value === 'system'
      ? (systemPrefersDark.value ? 'dark' : 'light')
      : mode.value
  ))
  const isDark = computed(() => resolvedMode.value === 'dark')
  const currentPalette = computed(() => accentPalettes[accent.value][resolvedMode.value])
  const accentColor = computed(() => currentPalette.value.accent)
  const accentHoverColor = computed(() => currentPalette.value.hover)
  const accentSubtleColor = computed(() => currentPalette.value.subtle)
  const config = computed<AppearanceConfig>(() => ({
    mode: mode.value,
    accent: accent.value,
    animateCharts: animateCharts.value,
    compactRows: compactRows.value,
  }))

  function ensureMediaQueryListener() {
    if (typeof window === 'undefined' || mediaQueryList != null) {
      return
    }

    mediaQueryList = window.matchMedia('(prefers-color-scheme: dark)')
    systemPrefersDark.value = mediaQueryList.matches
    mediaQueryListener = (event: MediaQueryListEvent) => {
      systemPrefersDark.value = event.matches
    }
    if (typeof mediaQueryList.addEventListener === 'function') {
      mediaQueryList.addEventListener('change', mediaQueryListener)
      return
    }
    mediaQueryList.addListener(mediaQueryListener)
  }

  function syncConfig(next: AppearanceConfig) {
    mode.value = next.mode
    accent.value = next.accent
    animateCharts.value = next.animateCharts
    compactRows.value = next.compactRows
  }

  watchEffect(() => {
    if (typeof document === 'undefined') {
      return
    }

    const root = document.documentElement
    root.classList.toggle('dark', resolvedMode.value === 'dark')
    root.classList.toggle('cf-density-compact', compactRows.value)
    root.style.setProperty('--cf-accent', accentColor.value)
    root.style.setProperty('--cf-accent-hover', accentHoverColor.value)
    root.style.setProperty('--cf-accent-subtle', accentSubtleColor.value)
  })

  async function initialize(force = false) {
    ensureMediaQueryListener()
    if (initialized.value && !force) {
      return
    }

    loading.value = true
    error.value = null

    try {
      const response = await getAppearanceConfig()
      syncConfig(response.appearance)
      lastSavedAt.value = response.savedAt
      initialized.value = true
    } catch (caught) {
      error.value = toMessage(caught)
    } finally {
      loading.value = false
    }
  }

  async function persist(next: AppearanceConfig) {
    const previous = createSnapshot(config.value)
    syncConfig(next)
    saving.value = true
    error.value = null

    try {
      const response = await updateAppearanceConfig(config.value)
      syncConfig(response.appearance)
      lastSavedAt.value = response.savedAt
      initialized.value = true
    } catch (caught) {
      syncConfig(previous)
      error.value = toMessage(caught)
      throw caught
    } finally {
      saving.value = false
    }
  }

  async function updateMode(next: AppearanceMode) {
    if (mode.value === next) {
      return
    }
    await persist({ ...config.value, mode: next })
  }

  async function updateAccent(next: AppearanceAccent) {
    if (accent.value === next) {
      return
    }
    await persist({ ...config.value, accent: next })
  }

  async function updateAnimateCharts(next: boolean) {
    if (animateCharts.value === next) {
      return
    }
    await persist({ ...config.value, animateCharts: next })
  }

  async function updateCompactRows(next: boolean) {
    if (compactRows.value === next) {
      return
    }
    await persist({ ...config.value, compactRows: next })
  }

  return {
    mode,
    accent,
    animateCharts,
    compactRows,
    initialized,
    loading,
    saving,
    error,
    lastSavedAt,
    resolvedMode,
    isDark,
    accentColor,
    accentHoverColor,
    accentSubtleColor,
    config,
    initialize,
    updateMode,
    updateAccent,
    updateAnimateCharts,
    updateCompactRows,
  }
})
