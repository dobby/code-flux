import { computed } from 'vue'
import { useAppearanceStore } from '../stores/appearance'

export function useTheme() {
  const appearance = useAppearanceStore()
  const theme = computed(() => appearance.resolvedMode)
  const isDark = computed(() => appearance.isDark)

  function toggleTheme() {
    const next = appearance.resolvedMode === 'dark' ? 'light' : 'dark'
    void appearance.updateMode(next)
  }

  return { theme, isDark, toggleTheme }
}
