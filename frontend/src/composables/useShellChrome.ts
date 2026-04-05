import { computed, ref } from 'vue'

const shell = window.shellChrome ?? null
const isElectron = ref(shell != null)
const platform = ref(shell?.platform ?? 'browser')

function installCssVars() {
  if (isElectron.value) {
    document.documentElement.dataset.platform = platform.value
    document.documentElement.style.setProperty(
      '--cf-chrome-height',
      `${Math.max(0, Number(shell?.chromeHeight) || 0)}px`,
    )
    document.documentElement.style.setProperty(
      '--cf-mac-traffic-lights-x',
      shell?.macTrafficLights?.x == null ? '16px' : `${shell.macTrafficLights.x}px`,
    )
    document.documentElement.style.setProperty(
      '--cf-mac-traffic-lights-width',
      shell?.macTrafficLights?.width == null ? '72px' : `${shell.macTrafficLights.width}px`,
    )
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
