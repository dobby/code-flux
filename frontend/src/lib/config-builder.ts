import type {
  ConfigAuthor,
  ConfigClassificationRule,
  ConfigRepo,
  EditableDashboardConfig,
} from '../types/api'

export function cloneConfig(config: EditableDashboardConfig): EditableDashboardConfig {
  return JSON.parse(JSON.stringify(config)) as EditableDashboardConfig
}

export function createEmptyRepo(): ConfigRepo {
  return {
    id: '',
    displayName: '',
    cloneUrl: '',
    enabled: true,
    productCode: null,
    branchPatterns: ['main'],
    excludeBranchPatterns: [],
    excludePathGlobs: [],
  }
}

export function createEmptyAuthor(): ConfigAuthor {
  return {
    id: '',
    displayName: '',
    emails: [''],
    names: [''],
    cohort: null,
  }
}

export function createEmptyRule(): ConfigClassificationRule {
  return {
    id: '',
    whenPathMatches: [''],
    category: 'production',
    subtype: 'source',
  }
}

export function setListFromText(value: string): string[] {
  return value
    .split('\n')
    .map((item) => item.trim())
    .filter(Boolean)
}

export function listToText(values: string[]): string {
  return values.join('\n')
}

function indent(level: number): string {
  return '  '.repeat(level)
}

function yamlScalar(value: string | number | boolean | null): string {
  if (value == null) {
    return 'null'
  }
  if (typeof value === 'boolean') {
    return value ? 'true' : 'false'
  }
  if (typeof value === 'number') {
    return String(value)
  }
  return JSON.stringify(value)
}

function yamlList(lines: string[], key: string, values: Array<string | number | boolean | null>, level = 0) {
  lines.push(`${indent(level)}${key}:`)
  if (values.length === 0) {
    lines.push(`${indent(level + 1)}[]`)
    return
  }
  values.forEach((value) => {
    lines.push(`${indent(level + 1)}- ${yamlScalar(value)}`)
  })
}

export function serializeConfigToYaml(config: EditableDashboardConfig): string {
  const lines: string[] = []

  lines.push('app:')
  lines.push(`${indent(1)}baseUrl: ${yamlScalar(config.app.baseUrl)}`)
  lines.push(`${indent(1)}dataDir: ${yamlScalar(config.app.dataDir)}`)
  lines.push(`${indent(1)}openBrowserOnStart: ${yamlScalar(config.app.openBrowserOnStart)}`)
  lines.push(`${indent(1)}logLevel: ${yamlScalar(config.app.logLevel)}`)
  lines.push('')

  lines.push('git:')
  lines.push(`${indent(1)}executable: ${yamlScalar(config.git.executable)}`)
  lines.push(`${indent(1)}mirrorDir: ${yamlScalar(config.git.mirrorDir)}`)
  lines.push(`${indent(1)}timeoutSeconds: ${yamlScalar(config.git.timeoutSeconds)}`)
  lines.push(`${indent(1)}includeMergeCommits: ${yamlScalar(config.git.includeMergeCommits)}`)
  lines.push(`${indent(1)}deduplicateByPatchId: ${yamlScalar(config.git.deduplicateByPatchId)}`)
  lines.push(`${indent(1)}useAuthoredDate: ${yamlScalar(config.git.useAuthoredDate)}`)
  lines.push(`${indent(1)}auth:`)
  lines.push(`${indent(2)}httpUsername: ${yamlScalar(config.git.auth.httpUsername)}`)
  lines.push(`${indent(2)}httpToken: ${yamlScalar(config.git.auth.httpToken)}`)
  lines.push('')

  lines.push('repos:')
  if (config.repos.length === 0) {
    lines.push(`${indent(1)}[]`)
  } else {
    config.repos.forEach((repo) => {
      lines.push(`${indent(1)}- id: ${yamlScalar(repo.id)}`)
      lines.push(`${indent(2)}displayName: ${yamlScalar(repo.displayName)}`)
      lines.push(`${indent(2)}cloneUrl: ${yamlScalar(repo.cloneUrl)}`)
      lines.push(`${indent(2)}enabled: ${yamlScalar(repo.enabled)}`)
      lines.push(`${indent(2)}productCode: ${yamlScalar(repo.productCode)}`)
      yamlList(lines, 'branchPatterns', repo.branchPatterns, 2)
      yamlList(lines, 'excludeBranchPatterns', repo.excludeBranchPatterns, 2)
      yamlList(lines, 'excludePathGlobs', repo.excludePathGlobs, 2)
    })
  }
  lines.push('')

  lines.push('authors:')
  lines.push(`${indent(1)}include:`)
  if (config.authors.include.length === 0) {
    lines.push(`${indent(2)}[]`)
  } else {
    config.authors.include.forEach((author) => {
      lines.push(`${indent(2)}- id: ${yamlScalar(author.id)}`)
      lines.push(`${indent(3)}displayName: ${yamlScalar(author.displayName)}`)
      yamlList(lines, 'emails', author.emails, 3)
      yamlList(lines, 'names', author.names, 3)
      lines.push(`${indent(3)}cohort: ${yamlScalar(author.cohort)}`)
    })
  }
  lines.push('')

  lines.push('classification:')
  lines.push(`${indent(1)}languageByExtension:`)
  const extensions = Object.entries(config.classification.languageByExtension).sort(([left], [right]) => left.localeCompare(right))
  if (extensions.length === 0) {
    lines.push(`${indent(2)}{}`)
  } else {
    extensions.forEach(([extensionKey, language]) => {
      lines.push(`${indent(2)}${extensionKey}: ${yamlScalar(language)}`)
    })
  }
  lines.push(`${indent(1)}rules:`)
  if (config.classification.rules.length === 0) {
    lines.push(`${indent(2)}[]`)
  } else {
    config.classification.rules.forEach((rule) => {
      lines.push(`${indent(2)}- id: ${yamlScalar(rule.id)}`)
      yamlList(lines, 'whenPathMatches', rule.whenPathMatches, 3)
      lines.push(`${indent(3)}category: ${yamlScalar(rule.category)}`)
      lines.push(`${indent(3)}subtype: ${yamlScalar(rule.subtype)}`)
    })
  }
  lines.push('')

  lines.push('uiDefaults:')
  lines.push(`${indent(1)}defaultMetric: ${yamlScalar(config.uiDefaults.defaultMetric)}`)
  lines.push(`${indent(1)}defaultGroupBy: ${yamlScalar(config.uiDefaults.defaultGroupBy)}`)
  lines.push(`${indent(1)}defaultChartLibrary: ${yamlScalar(config.uiDefaults.defaultChartLibrary)}`)
  lines.push(`${indent(1)}defaultDateFrom: ${yamlScalar(config.uiDefaults.defaultDateFrom)}`)
  lines.push(`${indent(1)}defaultDateTo: ${yamlScalar(config.uiDefaults.defaultDateTo)}`)
  yamlList(lines, 'defaultIncludeCategories', config.uiDefaults.defaultIncludeCategories, 1)
  yamlList(lines, 'defaultExcludeCategories', config.uiDefaults.defaultExcludeCategories, 1)
  lines.push('')

  lines.push('syncWindow:')
  lines.push(`${indent(1)}from: ${yamlScalar(config.syncWindow.from)}`)
  lines.push(`${indent(1)}to: ${yamlScalar(config.syncWindow.to)}`)

  return `${lines.join('\n')}\n`
}
