import { computed, ref } from 'vue'

const shell = window.shellChrome ?? null
const isElectron = ref(shell != null)
const platform = ref(shell?.platform ?? 'browser')

function installCssVars() {
  if (isElectron.value) {
    document.documentElement.dataset.platform = platform.value
    document.body.style.background = 'transparent'
  }
}

installCssVars()

export function useShellChrome() {
  return {
    isElectron: computed(() => isElectron.value),
    platform: computed(() => platform.value),
  }
}
