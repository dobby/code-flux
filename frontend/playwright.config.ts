import { defineConfig } from '@playwright/test'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const frontendRoot = path.dirname(fileURLToPath(import.meta.url))
const repoRoot = path.resolve(frontendRoot, '..')

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
    baseURL: 'http://127.0.0.1:8085',
    trace: 'on-first-retry',
    screenshot: 'only-on-failure',
  },
  webServer: {
    command:
      'node ./frontend/scripts/prepare-e2e-fixture.mjs && ./scripts/build-release.sh && APP_CONFIG_FILE=./tmp/playwright-e2e/config.yaml APP_BASE_URL=http://127.0.0.1:8085 SERVER_PORT=8085 ./scripts/run-dashboard.sh',
    cwd: repoRoot,
    url: 'http://127.0.0.1:8085/api/health',
    reuseExistingServer: false,
    timeout: 240_000,
  },
})
