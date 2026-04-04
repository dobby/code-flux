interface ShellChrome {
  platform: string
  chromeHeight: number
  macTrafficLights: { x: number; y: number; width: number } | null
}

interface Window {
  shellChrome?: ShellChrome
}
