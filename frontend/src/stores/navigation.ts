import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import type { RouteLocationNormalizedLoaded, RouteLocationRaw } from 'vue-router'

type MainMenuRouteName =
  | 'activity'
  | 'activity-commit'
  | 'codebase'
  | 'codebase-repo'
  | 'contributors'
  | 'widgets'
  | 'widget-new'
  | 'widget-edit'
  | 'page'

type AppSection =
  | 'activity'
  | 'codebase'
  | 'contributors'
  | 'widgets'
  | 'pages'
  | 'settings'

type StoredRouteValue = string | string[] | null

type StoredMainRoute = {
  name: MainMenuRouteName
  params?: Record<string, string | string[]>
  query?: Record<string, StoredRouteValue>
}

const STORAGE_KEY = 'code-flux-v2-last-main-route'

const MAIN_MENU_ROUTE_NAMES = new Set<MainMenuRouteName>([
  'activity',
  'activity-commit',
  'codebase',
  'codebase-repo',
  'contributors',
  'widgets',
  'widget-new',
  'widget-edit',
  'page',
])

function isMainMenuRouteName(value: unknown): value is MainMenuRouteName {
  return typeof value === 'string' && MAIN_MENU_ROUTE_NAMES.has(value as MainMenuRouteName)
}

function isSettingsRouteName(value: unknown): boolean {
  return value === 'settings-general'
    || value === 'settings-jira'
    || value === 'settings-sync'
    || value === 'settings-appearance'
    || value === 'settings-advanced'
}

function resolveSection(name: MainMenuRouteName): Exclude<AppSection, 'settings'> {
  switch (name) {
    case 'activity':
    case 'activity-commit':
      return 'activity'
    case 'codebase':
    case 'codebase-repo':
      return 'codebase'
    case 'contributors':
      return 'contributors'
    case 'widgets':
    case 'widget-new':
    case 'widget-edit':
      return 'widgets'
    case 'page':
      return 'pages'
  }
}

function normalizeParamRecord(
  source: RouteLocationNormalizedLoaded['params'],
): Record<string, string | string[]> | undefined {
  const entries = Object.entries(source)
    .map(([key, value]) => {
      if (Array.isArray(value)) {
        return [key, value.map(String)] as const
      }
      return [key, String(value)] as const
    })

  return entries.length ? Object.fromEntries(entries) : undefined
}

function normalizeQueryRecord(
  source: RouteLocationNormalizedLoaded['query'],
): Record<string, StoredRouteValue> | undefined {
  const entries = Object.entries(source)
    .map(([key, value]) => {
      if (Array.isArray(value)) {
        return [key, value.map((item) => item == null ? null : String(item))] as const
      }
      return [key, value == null ? null : String(value)] as const
    })

  return entries.length ? Object.fromEntries(entries) : undefined
}

function toStoredMainRoute(route: RouteLocationNormalizedLoaded): StoredMainRoute | null {
  if (!isMainMenuRouteName(route.name)) {
    return null
  }

  return {
    name: route.name,
    params: normalizeParamRecord(route.params),
    query: normalizeQueryRecord(route.query),
  }
}

function readStoredMainRoute(): StoredMainRoute | null {
  const raw = window.localStorage.getItem(STORAGE_KEY)
  if (!raw) {
    return null
  }

  try {
    const parsed = JSON.parse(raw) as StoredMainRoute
    if (!isMainMenuRouteName(parsed?.name)) {
      return null
    }
    return parsed
  } catch {
    return null
  }
}

function persistStoredMainRoute(route: StoredMainRoute | null) {
  if (route) {
    window.localStorage.setItem(STORAGE_KEY, JSON.stringify(route))
    return
  }

  window.localStorage.removeItem(STORAGE_KEY)
}

export const useNavigationStore = defineStore('navigation', () => {
  const currentSection = ref<AppSection | null>(null)
  const lastMainRoute = ref<StoredMainRoute | null>(readStoredMainRoute())

  function syncRoute(route: RouteLocationNormalizedLoaded) {
    if (isSettingsRouteName(route.name)) {
      currentSection.value = 'settings'
      return
    }

    const storedRoute = toStoredMainRoute(route)
    if (!storedRoute) {
      currentSection.value = null
      return
    }

    lastMainRoute.value = storedRoute
    currentSection.value = resolveSection(storedRoute.name)
    persistStoredMainRoute(storedRoute)
  }

  const backToAppTarget = computed<RouteLocationRaw>(() => {
    if (!lastMainRoute.value) {
      return { name: 'activity' }
    }

    return {
      name: lastMainRoute.value.name,
      params: lastMainRoute.value.params,
      query: lastMainRoute.value.query,
    }
  })

  return {
    currentSection,
    lastMainRoute,
    backToAppTarget,
    syncRoute,
  }
})
