import { expect, test, type Page } from '@playwright/test'

test.describe('v2 workspace', () => {
  test('page management from the sidebar works', async ({ page }) => {
    await page.goto('/')

    await expect(page.getByTestId('app-sidebar')).toBeVisible()
    await expect(page.getByTestId('app-header')).toBeVisible()

    const pageTitle = `Sprint pulse ${Date.now()}`
    const pageId = await createPageFromSidebar(page, pageTitle)

    const renamedTitle = `${pageTitle} renamed`
    page.once('dialog', (dialog) => dialog.accept(renamedTitle))
    await page.getByTestId(`page-rename-${pageId}`).click()
    await expect(page.getByTestId(`sidebar-page-link-${pageId}`)).toContainText(renamedTitle)

    const orderBeforeMove = await sidebarTitles(page)
    await page.getByTestId(`page-move-up-${pageId}`).click()
    await expect.poll(() => sidebarTitles(page)).not.toEqual(orderBeforeMove)

    const countBeforeDuplicate = await page.locator('[data-testid^="sidebar-page-link-"]').count()
    await page.getByTestId(`page-duplicate-${pageId}`).click()
    await expect(page.locator('[data-testid^="sidebar-page-link-"]')).toHaveCount(countBeforeDuplicate + 1)
    const duplicatedLink = page.locator('[data-testid^="sidebar-page-link-"]').filter({ hasText: `${renamedTitle} Copy` }).first()
    await expect(duplicatedLink).toBeVisible()
    const duplicatedPageId = (await duplicatedLink.getAttribute('data-testid'))!.replace('sidebar-page-link-', '')

    page.once('dialog', (dialog) => dialog.accept())
    await page.getByTestId(`page-archive-${duplicatedPageId}`).click()
    await expect(page.getByTestId(`sidebar-page-link-${duplicatedPageId}`)).toHaveCount(0)
  })

  test('widget creation and add-to-page flow works', async ({ page }) => {
    const widgetTitle = `Notes block ${Date.now()}`
    await page.goto('/widgets/new')

    await page.getByTestId('widget-title').fill(widgetTitle)
    await page.getByTestId('widget-description').fill('Playwright-created reusable metric widget.')
    await page.getByTestId('widget-kind').selectOption('markdown_block')
    await page.getByTestId('widget-markdown').fill('Playwright saved this reusable markdown widget.')
    await page.getByTestId('widget-save').click()

    await expect(page).toHaveURL(/\/widgets\/widget-/)

    await page.goto('/widgets')
    await page.getByTestId('widget-search').fill(widgetTitle)
    await expect(page.locator('[data-testid^="catalog-card-"]').first()).toContainText(widgetTitle)

    const pageId = await createPageFromSidebar(page, `Widget page ${Date.now()}`)
    await addWidgetFromDialog(page, widgetTitle)
    await expect(page.locator(`[data-testid^="widget-tile-"]`).filter({ hasText: widgetTitle })).toBeVisible()

    await page.reload()
    await expect(page).toHaveURL(new RegExp(`/pages/${pageId}$`))
    await expect(page.locator(`[data-testid^="widget-tile-"]`).filter({ hasText: widgetTitle })).toBeVisible()
  })

  test('layout drag and resize persist after reload', async ({ page }) => {
    await page.goto('/')
    await createPageFromSidebar(page, `Layout page ${Date.now()}`)

    await addWidgetFromDialog(page, 'Current language mix')
    await addWidgetFromDialog(page, 'Current language mix')

    await page.getByTestId('page-edit-mode-toggle').click()
    await expect(page.getByTestId('page-edit-mode-label')).toHaveText('View mode')

    const widgetIds = await page.locator('[data-testid^="widget-grid-item-"]').evaluateAll((nodes) =>
      nodes
        .map((node) => node.getAttribute('data-testid')?.replace('widget-grid-item-', ''))
        .filter((value): value is string => Boolean(value)),
    )
    expect(widgetIds.length).toBeGreaterThanOrEqual(2)

    const draggedWidgetId = widgetIds[1]
    const beforeDrag = await readGridLayout(page, draggedWidgetId)
    await dragWidgetByHandle(page, draggedWidgetId, { x: 340, y: 120 })
    await page.waitForTimeout(1200)
    const afterDrag = await readGridLayout(page, draggedWidgetId)
    expect(afterDrag).not.toEqual(beforeDrag)

    const resizedWidgetId = widgetIds[0]
    const beforeResize = await readGridLayout(page, resizedWidgetId)
    await resizeWidget(page, resizedWidgetId, { x: 140, y: 50 })
    await page.waitForTimeout(1200)
    const afterResize = await readGridLayout(page, resizedWidgetId)
    expect(afterResize.w).toBeGreaterThanOrEqual(beforeResize.w)
    expect(afterResize.h).toBeGreaterThanOrEqual(beforeResize.h)

    await page.reload()
    expect(await readGridLayout(page, draggedWidgetId)).toEqual(afterDrag)
    expect(await readGridLayout(page, resizedWidgetId)).toEqual(afterResize)
  })

  test('drilldown tabs and annotation persistence work', async ({ page }) => {
    await page.goto('/')
    await ensureLegacySync(page)
    await createPageFromSidebar(page, `Drilldown page ${Date.now()}`)
    await addWidgetFromDialog(page, 'Daily lines added')
    await page.reload()

    const widgetId = await firstWidgetId(page)
    await page.getByTestId(`chart-inspect-${widgetId}`).click()

    await expect(page.getByTestId('drilldown-date')).not.toHaveText('No day selected')
    await page.getByTestId('drilldown-tab-commits').click()
    await expect(page.getByTestId('drilldown-commits-tab')).toBeVisible()

    await page.getByTestId('drilldown-tab-summary').click()
    const annotationTitle = `Spike note ${Date.now()}`
    await page.getByTestId('annotation-title').fill(annotationTitle)
    await page.getByTestId('annotation-body').fill('Captured from the drilldown drawer.')
    await page.getByTestId('annotation-save').click()

    await expect(page.getByTestId('annotation-list')).toContainText(annotationTitle)
    await expect(page.locator(`[data-testid="widget-tile-${widgetId}"]`)).toContainText('1 notes')

    await page.reload()
    await page.getByTestId(`chart-inspect-${widgetId}`).click()
    await expect(page.getByTestId('annotation-list')).toContainText(annotationTitle)

    const editButton = page.locator('[data-testid^="annotation-edit-"]').first()
    await editButton.click()
    await page.getByTestId('annotation-title').fill(`${annotationTitle} updated`)
    await page.getByTestId('annotation-save').click()
    await expect(page.getByTestId('annotation-list')).toContainText(`${annotationTitle} updated`)

    await editButton.click()
    await page.getByTestId('annotation-delete').click()
    await expect(page.locator('[data-testid^="annotation-edit-"]')).toHaveCount(0)
  })

  test('snapshot build and jira settings boundaries work', async ({ page }) => {
    await page.goto('/')
    await ensureLegacySync(page)
    await page.goto('/settings/general')
    await page.getByTestId('build-current-snapshots').click()

    await expect.poll(
      async () => page.evaluate(async () => {
        const response = await fetch('/api/v2/snapshots/status')
        const body = await response.json()
        return body.items.length
      }),
      { timeout: 30_000 },
    ).toBeGreaterThan(0)

    const pageId = await createPageFromSidebar(page, `Repo state page ${Date.now()}`)
    await page.getByTestId(`sidebar-page-link-${pageId}`).click()
    await addWidgetFromDialog(page, 'Current language mix')
    const widgetId = await firstWidgetId(page)
    await expect(page.getByTestId(`chart-rendered-${widgetId}`)).toBeVisible()

    await page.goto('/settings/jira')
    const token = `playwright-token-${Date.now()}`
    await page.getByTestId('jira-base-url').fill('https://jira.example.com')
    await page.getByTestId('jira-project-keys').fill('CFX')
    await page.getByTestId('jira-token').fill(token)
    await page.getByTestId('jira-save').click()
    await expect(page.getByText('Jira settings saved.')).toBeVisible()

    const settingsPayload = await page.evaluate(async () => {
      const response = await fetch('/api/v2/settings/jira')
      return response.text()
    })
    expect(settingsPayload).not.toContain(token)

    const jiraStatus = await page.evaluate(async () => {
      const response = await fetch('/api/v2/jira/status')
      return response.json()
    })
    expect(jiraStatus.secretConfigured).toBe(true)
  })

  test('route smoke checks for v2 states', async ({ page }) => {
    const routes = [
      { path: '/widgets' },
      { path: '/widgets/new' },
      { path: '/settings/general' },
      { path: '/settings/jira' },
      { path: '/sync' },
      { path: '/explorer' },
      { path: '/legacy/overview' },
    ]

    for (const route of routes) {
      await page.goto(route.path)
      await expect(page).toHaveURL(new RegExp(`${route.path}/?$`))
      await expect(page.getByTestId('app-sidebar')).toBeVisible()
      await expect(page.getByTestId('app-header')).toBeVisible()
    }
  })

  test('legacy overview route remains available', async ({ page }) => {
    await page.goto('/legacy/overview')
    await expect(page.getByTestId('app-sidebar')).toBeVisible()
    await expect(page.getByTestId('app-header')).toBeVisible()
    await expect(page.getByTestId('app-header')).toContainText('Legacy Overview')
  })
})

async function createPageFromSidebar(page: Page, title: string): Promise<string> {
  page.once('dialog', (dialog) => dialog.accept(title))
  await page.getByTestId('sidebar-create-page').click()
  await expect(page).toHaveURL(/\/pages\/page-/)
  await expect(page.locator('.workspace-topbar h1')).toHaveText(title)
  return currentPageId(page)
}

async function addWidgetFromDialog(page: Page, widgetTitle: string) {
  await page.getByTestId('page-add-widget').click()
  await expect(page.getByTestId('add-widget-dialog')).toBeVisible()
  await page.getByRole('button', { name: new RegExp(widgetTitle, 'i') }).click()
}

async function dragWidgetByHandle(page: Page, widgetId: string, delta: { x: number; y: number }) {
  await page.evaluate(
    ([targetWidgetId, targetDelta]) => {
      const gridElement = document.querySelector('.grid-stack') as (HTMLElement & { gridstack?: { update: (el: HTMLElement, options: { x: number; y: number }) => void } }) | null
      const widgetElement = document.querySelector(`[data-testid="widget-grid-item-${targetWidgetId}"]`) as (HTMLElement & {
        gridstackNode?: { x?: number; y?: number }
      }) | null
      if (!gridElement?.gridstack || !widgetElement?.gridstackNode) {
        throw new Error('GridStack widget not available for drag test')
      }
      const nextX = Math.max(0, (widgetElement.gridstackNode.x ?? 0) + Math.max(1, Math.round(targetDelta.x / 170)))
      const nextY = Math.max(0, (widgetElement.gridstackNode.y ?? 0) + Math.max(1, Math.round(targetDelta.y / 110)))
      gridElement.gridstack.update(widgetElement, { x: nextX, y: nextY })
    },
    [widgetId, delta] as const,
  )
}

async function resizeWidget(page: Page, widgetId: string, delta: { x: number; y: number }) {
  await page.evaluate(
    ([targetWidgetId, targetDelta]) => {
      const gridElement = document.querySelector('.grid-stack') as (HTMLElement & { gridstack?: { update: (el: HTMLElement, options: { w: number; h: number }) => void } }) | null
      const widgetElement = document.querySelector(`[data-testid="widget-grid-item-${targetWidgetId}"]`) as (HTMLElement & {
        gridstackNode?: { w?: number; h?: number }
      }) | null
      if (!gridElement?.gridstack || !widgetElement?.gridstackNode) {
        throw new Error('GridStack widget not available for resize test')
      }
      const nextW = Math.max(1, (widgetElement.gridstackNode.w ?? 1) + Math.max(1, Math.round(targetDelta.x / 120)))
      const nextH = Math.max(1, (widgetElement.gridstackNode.h ?? 1) + Math.max(1, Math.round(targetDelta.y / 120)))
      gridElement.gridstack.update(widgetElement, { w: nextW, h: nextH })
    },
    [widgetId, delta] as const,
  )
}

async function readGridLayout(page: Page, widgetId: string) {
  return page.locator(`[data-testid="widget-grid-item-${widgetId}"]`).evaluate((node) => ({
    x: Number(node.getAttribute('gs-x') ?? '0'),
    y: Number(node.getAttribute('gs-y') ?? '0'),
    w: Number(node.getAttribute('gs-w') ?? '0'),
    h: Number(node.getAttribute('gs-h') ?? '0'),
  }))
}

async function sidebarTitles(page: Page) {
  return page.locator('[data-testid^="sidebar-page-link-"] strong').allInnerTexts()
}

async function firstWidgetId(page: Page) {
  const dataTestId = await page.locator('[data-testid^="widget-grid-item-"]').first().getAttribute('data-testid')
  expect(dataTestId).toBeTruthy()
  return dataTestId!.replace('widget-grid-item-', '')
}

async function ensureLegacySync(page: Page) {
  const status = await page.evaluate(async () => {
    const response = await fetch('/api/sync/status')
    return response.json()
  })
  if (status.repos?.some((repo: { lastSuccessfulSyncedAt?: string | null }) => repo.lastSuccessfulSyncedAt)) {
    return
  }

  await page.evaluate(async () => {
    await fetch('/api/sync/run', {
      method: 'POST',
      headers: {
        Accept: 'application/json',
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({ mode: 'incremental' }),
    })
  })

  await expect.poll(
    async () => page.evaluate(async () => {
      const response = await fetch('/api/sync/status')
      const body = await response.json()
      return body.running
    }),
    { timeout: 60_000 },
  ).toBe(false)
}

function currentPageId(page: Page) {
  const match = page.url().match(/\/pages\/([^/]+)$/)
  expect(match).not.toBeNull()
  return match![1]
}
