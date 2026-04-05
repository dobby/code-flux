import { defineConfig } from '@playwright/test'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const frontendRoot = path.dirname(fileURLToPath(import.meta.url))
const repoRoot = path.resolve(frontendRoot, '..')
const configuredPort = Number.parseInt(process.env.E2E_PORT ?? '8085', 10)
const e2ePort = Number.isNaN(configuredPort) || configuredPort <= 0 ? 8085 : configuredPort
const baseUrl = `http://127.0.0.1:${e2ePort}`

export default defineConfig({
  testDir: './tests/e2e',
  fullyParallel: false,
  workers: 1,
  timeout: 90_000,
  expect: {
    timeout: 15_000,
  },
  reporter: 'list',
  use: {
    baseURL: baseUrl,
    trace: 'on-first-retry',
    screenshot: 'only-on-failure',
  },
  webServer: {
    command:
      `node ./frontend/scripts/prepare-e2e-fixture.mjs && ./scripts/build-release.sh && E2E_PORT=${e2ePort} APP_CONFIG_FILE=./tmp/playwright-e2e/config.yaml APP_BASE_URL=${baseUrl} SERVER_PORT=${e2ePort} ./scripts/run-dashboard.sh`,
    cwd: repoRoot,
    url: `${baseUrl}/api/health`,
    reuseExistingServer: false,
    timeout: 240_000,
  },
})
