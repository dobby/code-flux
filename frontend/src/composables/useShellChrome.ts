import { computed, ref } from 'vue'

const shell = window.shellChrome ?? null
const isElectron = ref(shell != null)
const platform = ref(shell?.platform ?? 'browser')
const chromeHeight = ref(shell?.chromeHeight ?? 0)

function installCssVars() {
  const root = document.documentElement

  root.style.setProperty('--cf-chrome-height', `${chromeHeight.value}px`)

  if (shell?.macTrafficLights) {
    const { x, width } = shell.macTrafficLights
    root.style.setProperty('--cf-toggle-anchor-left', `${x + width + 24}px`)
  }

  if (isElectron.value) {
    root.dataset.platform = platform.value
    document.body.style.background = 'transparent'
  }
}

installCssVars()

export function useShellChrome() {
  return {
    isElectron: computed(() => isElectron.value),
    platform: computed(() => platform.value),
    chromeHeight: computed(() => chromeHeight.value),
  }
}
