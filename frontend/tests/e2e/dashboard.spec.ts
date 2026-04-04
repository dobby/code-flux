import { expect, test, type Page } from '@playwright/test'

test.describe('dashboard smoke', () => {
  test('dashboard loads shell and settings flow from the sidebar', async ({ page }) => {
    await page.goto('/')

    await expect(page.getByTestId('app-sidebar')).toBeVisible()
    await expect(page.getByTestId('app-header')).toBeVisible()
    await expect(page.getByTestId('sync-area')).toBeVisible()
    await expect(page.getByTestId('filter-toggle')).toBeVisible()
    await expect(page.getByTestId('annotate-button')).toBeVisible()
    await expect(page.getByTestId('chart-empty-state')).toBeVisible()
    await expect(page.getByTestId('annotation-dialog')).not.toBeVisible()

    await page.getByTestId('sidebar-toggle').click()
    await expect(page.locator('.app-shell')).toHaveClass(/app-shell--collapsed/)

    await page.getByTestId('sidebar-settings-toggle').click()
    await expect(page.getByTestId('config-dialog')).toBeVisible()
    await page.getByTestId('config-dialog').getByRole('button', { name: 'Close' }).click()
    await expect(page.getByTestId('config-dialog')).not.toBeVisible()
  })

  test('manual sync produces charted data', async ({ page }) => {
    await page.goto('/')
    await expect(page.getByTestId('chart-empty-state')).toBeVisible()

    const syncButton = page.getByTestId('sync-button')
    await syncButton.click()

    await expect(page.getByTestId('chart-rendered')).toBeVisible({ timeout: 60_000 })
    await expect(page.getByTestId('chart-empty-state')).not.toBeVisible()
    await expect(page.locator('.kpi-card').filter({ hasText: 'Canonical Commits' })).toContainText('2')
  })

  test('annotation create, edit, and delete flow works', async ({ page }) => {
    await page.goto('/')
    await ensureSynced(page)

    const uniqueTitle = `Playwright milestone ${Date.now()}`
    const updatedTitle = `${uniqueTitle} updated`

    await page.getByTestId('annotate-button').click()
    await page.getByTestId('annotation-new-button').click()
    await expect(page.getByTestId('annotation-dialog')).toBeVisible()

    await page.getByTestId('annotation-day').fill('2026-03-15')
    await page.getByTestId('annotation-title').fill(uniqueTitle)
    await page.getByTestId('annotation-type').selectOption('milestone')
    await page.getByTestId('annotation-description').fill('Smoke test marker')
    await page.getByTestId('annotation-save').click()

    await expect(page.getByTestId('annotation-dialog')).not.toBeVisible()
    await page.getByTestId('annotate-button').click()
    await expect(page.getByTestId('annotation-list')).toContainText(uniqueTitle)

    await page
      .getByTestId('annotation-list')
      .getByRole('button', { name: new RegExp(uniqueTitle) })
      .click()
    await expect(page.getByTestId('annotation-dialog')).toBeVisible()

    await page.getByTestId('annotation-title').fill(updatedTitle)
    await page.getByTestId('annotation-save').click()
    await page.getByTestId('annotate-button').click()
    await expect(page.getByTestId('annotation-list')).toContainText(updatedTitle)

    await page
      .getByTestId('annotation-list')
      .getByRole('button', { name: new RegExp(updatedTitle) })
      .click()
    await page.getByTestId('annotation-delete').click()
    await expect(
      page.getByTestId('annotation-list').getByRole('button', { name: new RegExp(updatedTitle) }),
    ).toHaveCount(0)
  })

  test('clicking the chart opens the annotation dialog for a selected day', async ({ page }) => {
    await page.goto('/')
    await ensureSynced(page)

    await page.locator('[data-testid="chart-rendered"] canvas').first().click({
      position: { x: 260, y: 180 },
    })

    await expect(page.getByTestId('annotation-dialog')).toBeVisible()
    await expect(page.getByTestId('annotation-day')).toHaveValue(/\d{4}-\d{2}-\d{2}/)

    await page.getByTestId('annotation-close').click()
    await expect(page.getByTestId('annotation-dialog')).not.toBeVisible()
  })
})

test.describe('shell structure', () => {
  test('sidebar surface owns the top-left corner', async ({ page }) => {
    await page.goto('/')
    const box = await page.locator('.sidebar-surface').boundingBox()
    expect(box).not.toBeNull()
    expect(Math.round(box!.y)).toBe(0)
  })

  test('main chrome starts at y=0', async ({ page }) => {
    await page.goto('/')
    const box = await page.locator('.main-chrome').boundingBox()
    expect(box).not.toBeNull()
    expect(Math.round(box!.y)).toBe(0)
  })

  test('sidebar toggle stays anchored during collapse', async ({ page }) => {
    await page.goto('/')
    const toggle = page.getByTestId('sidebar-toggle')
    const before = await toggle.boundingBox()
    await toggle.click()
    await page.waitForTimeout(280)
    const after = await toggle.boundingBox()
    expect(before).not.toBeNull()
    expect(after).not.toBeNull()
    expect(Math.abs(after!.x - before!.x)).toBeLessThanOrEqual(1)
    expect(Math.abs(after!.y - before!.y)).toBeLessThanOrEqual(1)
  })
})

async function ensureSynced(page: Page) {
  if (await page.getByTestId('chart-rendered').isVisible().catch(() => false)) {
    return
  }

  await page.getByTestId('sync-button').click()
  await expect(page.getByTestId('chart-rendered')).toBeVisible({ timeout: 60_000 })
}
