import { execFileSync } from 'node:child_process'
import { mkdirSync, rmSync, writeFileSync } from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const frontendRoot = path.dirname(fileURLToPath(import.meta.url))
const repoRoot = path.resolve(frontendRoot, '..', '..')
const fixtureRoot = path.join(repoRoot, 'tmp', 'playwright-e2e')
const sourceRepo = path.join(fixtureRoot, 'repos', 'marcando-api.git')
const workRepo = path.join(fixtureRoot, 'worktree')
const runtimeDir = path.join(fixtureRoot, 'runtime')
const configPath = path.join(fixtureRoot, 'config.yaml')

rmSync(fixtureRoot, { recursive: true, force: true })
mkdirSync(path.dirname(sourceRepo), { recursive: true })
mkdirSync(runtimeDir, { recursive: true })

run('git', ['init', '--bare', '--initial-branch=main', sourceRepo])
run('git', ['init', '--initial-branch=main', workRepo])
run('git', ['remote', 'add', 'origin', sourceRepo], { cwd: workRepo })

run('git', ['config', 'user.name', 'Fixture Bot'], { cwd: workRepo })
run('git', ['config', 'user.email', 'fixture@example.com'], { cwd: workRepo })

commitFixtureChange({
  relativePath: 'src/main/kotlin/App.kt',
  content: 'fun main() = println("hello throughput")\n',
  authorName: 'Eli',
  authorEmail: 'eli@marcando.be',
  date: '2026-02-01T09:00:00+01:00',
  message: 'Add app entrypoint',
})

commitFixtureChange({
  relativePath: 'src/test/kotlin/AppTest.kt',
  content: 'class AppTest\n',
  authorName: 'Sander',
  authorEmail: 'sander@encima.be',
  date: '2026-02-03T14:30:00+01:00',
  message: 'Add first test',
})

commitFixtureChange({
  relativePath: 'src/main/kotlin/App.kt',
  content: 'fun main() = println("hello throughput chart")\n',
  authorName: 'Eli',
  authorEmail: 'eli@marcando.be',
  date: '2026-03-01T10:15:00+01:00',
  message: 'Expand production code',
})

run('git', ['push', 'origin', 'main'], { cwd: workRepo })

writeFileSync(
  configPath,
  `app:
  baseUrl: "http://127.0.0.1:8085"
  dataDir: "${runtimeDir}"
  openBrowserOnStart: false
  logLevel: "INFO"

git:
  executable: "git"
  mirrorDir: "${path.join(runtimeDir, 'mirrors')}"
  timeoutSeconds: 120
  includeMergeCommits: false
  deduplicateByPatchId: true
  useAuthoredDate: true

repos:
  - id: "marcando-api"
    displayName: "Marcando API"
    cloneUrl: "${sourceRepo}"
    enabled: true
    productCode: "MARCANDO"
    branchPatterns:
      - "main"
    excludeBranchPatterns: []
    excludePathGlobs:
      - "docs/**"
      - "generated/**"

authors:
  include:
    - id: "eli"
      displayName: "Eli"
      emails:
        - "eli@marcando.be"
      names:
        - "Eli"
      cohort: "agentic"
    - id: "sander"
      displayName: "Sander"
      emails:
        - "sander@encima.be"
      names:
        - "Sander"
      cohort: "non-agentic"

classification:
  languageByExtension:
    kt: "kotlin"
  rules:
    - id: "unit-tests-jvm"
      whenPathMatches:
        - "**/src/test/**"
      category: "test"
      subtype: "unit_test"

uiDefaults:
  defaultMetric: "lines_added"
  defaultGroupBy: "author"
  defaultIncludeCategories:
    - "production"
  defaultExcludeCategories:
    - "test"
    - "docs"
    - "generated"
    - "config"
`,
)

function commitFixtureChange({ relativePath, content, authorName, authorEmail, date, message }) {
  const absolutePath = path.join(workRepo, relativePath)
  mkdirSync(path.dirname(absolutePath), { recursive: true })
  writeFileSync(absolutePath, content)
  run('git', ['add', '.'], { cwd: workRepo })
  run('git', ['commit', '-m', message], {
    cwd: workRepo,
    env: {
      GIT_AUTHOR_NAME: authorName,
      GIT_AUTHOR_EMAIL: authorEmail,
      GIT_AUTHOR_DATE: date,
      GIT_COMMITTER_NAME: authorName,
      GIT_COMMITTER_EMAIL: authorEmail,
      GIT_COMMITTER_DATE: date,
    },
  })
}

function run(command, args, options = {}) {
  execFileSync(command, args, {
    stdio: 'inherit',
    cwd: options.cwd ?? repoRoot,
    env: {
      ...process.env,
      ...options.env,
    },
  })
}
