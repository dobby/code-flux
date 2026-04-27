# Instructions

- Following Playwright test failed.
- Explain why, be concise, respect Playwright best practices.
- Provide a snippet of code with the fix, if possible.

# Test info

- Name: frontend/tests/e2e/dashboard.spec.ts >> v2 workspace >> widget creation and add-to-page flow works
- Location: frontend/tests/e2e/dashboard.spec.ts:31:3

# Error details

```
Error: page.goto: Protocol error (Page.navigate): Cannot navigate to invalid URL
Call log:
  - navigating to "/widgets/new", waiting until "load"

```

# Test source

```ts
  1   | import { expect, test, type Dialog, type Page } from '@playwright/test'
  2   | 
  3   | test.describe('v2 workspace', () => {
  4   |   test('page management from the sidebar works', async ({ page }) => {
  5   |     await page.goto('/')
  6   | 
  7   |     await expect(page.getByTestId('app-sidebar')).toBeVisible()
  8   |     await expect(page.getByTestId('app-header')).toBeVisible()
  9   | 
  10  |     const pageTitle = `Sprint pulse ${Date.now()}`
  11  |     const pageId = await createPageFromSidebar(page, pageTitle)
  12  | 
  13  |     const renamedTitle = `${pageTitle} renamed`
  14  |     queueDialogs(page, ['rename', renamedTitle])
  15  |     await page.getByTestId(`page-menu-${pageId}`).click()
  16  |     await expect(page.getByTestId(`sidebar-page-link-${pageId}`)).toContainText(renamedTitle)
  17  | 
  18  |     const countBeforeDuplicate = await page.locator('[data-testid^="sidebar-page-link-"]').count()
  19  |     queueDialogs(page, ['duplicate'])
  20  |     await page.getByTestId(`page-menu-${pageId}`).click()
  21  |     await expect(page.locator('[data-testid^="sidebar-page-link-"]')).toHaveCount(countBeforeDuplicate + 1)
  22  |     const duplicatedLink = page.locator('[data-testid^="sidebar-page-link-"]').filter({ hasText: `${renamedTitle} Copy` }).first()
  23  |     await expect(duplicatedLink).toBeVisible()
  24  |     const duplicatedPageId = (await duplicatedLink.getAttribute('data-testid'))!.replace('sidebar-page-link-', '')
  25  | 
  26  |     queueDialogs(page, ['archive', true])
  27  |     await page.getByTestId(`page-menu-${duplicatedPageId}`).click()
  28  |     await expect(page.getByTestId(`sidebar-page-link-${duplicatedPageId}`)).toHaveCount(0)
  29  |   })
  30  | 
  31  |   test('widget creation and add-to-page flow works', async ({ page }) => {
  32  |     const widgetTitle = `Notes block ${Date.now()}`
> 33  |     await page.goto('/widgets/new')
      |                ^ Error: page.goto: Protocol error (Page.navigate): Cannot navigate to invalid URL
  34  | 
  35  |     await page.getByTestId('widget-title').fill(widgetTitle)
  36  |     await page.getByTestId('widget-description').fill('Playwright-created reusable metric widget.')
  37  |     await page.getByTestId('widget-kind').selectOption('markdown_block')
  38  |     await page.getByTestId('widget-markdown').fill('Playwright saved this reusable markdown widget.')
  39  |     await page.getByTestId('widget-save').click()
  40  | 
  41  |     await expect(page).toHaveURL(/\/widgets\/widget-/)
  42  | 
  43  |     await page.goto('/widgets')
  44  |     await page.getByTestId('widget-search').fill(widgetTitle)
  45  |     await expect(page.locator('[data-testid^="catalog-card-"]').first()).toContainText(widgetTitle)
  46  | 
  47  |     const pageId = await createPageFromSidebar(page, `Widget page ${Date.now()}`)
  48  |     await addWidgetFromDialog(page, widgetTitle)
  49  |     await expect(page.locator(`[data-testid^="widget-tile-"]`).filter({ hasText: widgetTitle })).toBeVisible()
  50  | 
  51  |     await page.reload()
  52  |     await expect(page).toHaveURL(new RegExp(`/pages/${pageId}$`))
  53  |     await expect(page.locator(`[data-testid^="widget-tile-"]`).filter({ hasText: widgetTitle })).toBeVisible()
  54  |   })
  55  | 
  56  |   test('customer pages use shared header controls for time range, filters, and overflow', async ({ page }) => {
  57  |     await page.goto('/')
  58  | 
  59  |     await createPageFromSidebar(page, `Shared header ${Date.now()}`)
  60  |     await expect(page.getByTestId('app-header')).toContainText('Shared header')
  61  | 
  62  |     await page.getByTestId('page-time-range').click()
  63  |     const timeMenu = page.locator('.content-chrome__explorer-menu').filter({ hasText: 'Custom range' }).last()
  64  |     await expect(timeMenu).toBeVisible()
  65  |     await timeMenu.getByRole('menuitemradio', { name: /^Last 90 days$/ }).click()
  66  |     await expect(page.getByTestId('page-time-range')).toContainText('Last 90 days')
  67  | 
  68  |     await addPageFilter(page, { field: 'author', values: 'Ada Lovelace, Grace Hopper' })
  69  |     await addPageFilter(page, { field: 'category', values: 'Platform initiatives' })
  70  |     await addPageFilter(page, { field: 'branch', values: 'release/2026-q2' })
  71  | 
  72  |     await expect(page.getByTestId('app-header')).toContainText('Author:')
  73  |     await expect(page.getByTestId('page-add-filter')).toContainText('+ Filter (3)')
  74  | 
  75  |     await page.setViewportSize({ width: 720, height: 900 })
  76  |     await expect(page.getByTestId('page-toolbar-overflow')).toBeVisible()
  77  |     await page.getByTestId('page-toolbar-overflow').click()
  78  |     await expect(page.locator('.workspace-page-header-controls__overflow-menu')).toContainText('Branch:')
  79  |   })
  80  | 
  81  |   test('customer page more actions support rename, duplicate, and archive from the shared header', async ({ page }) => {
  82  |     await page.goto('/')
  83  | 
  84  |     await createPageFromSidebar(page, `Header actions ${Date.now()}`)
  85  | 
  86  |     const renamedTitle = `Header actions renamed ${Date.now()}`
  87  |     page.once('dialog', (dialog) => dialog.accept(renamedTitle))
  88  |     await page.getByTestId('page-more-actions').click()
  89  |     await page.getByRole('button', { name: 'Rename' }).click()
  90  |     await expect(page.getByTestId('page-toolbar-title')).toHaveText(renamedTitle)
  91  | 
  92  |     const countBeforeDuplicate = await page.locator('[data-testid^="sidebar-page-link-"]').count()
  93  |     await page.getByTestId('page-more-actions').click()
  94  |     await expect(page.getByRole('button', { name: 'Archive' })).toBeVisible()
  95  |     await page.getByRole('button', { name: 'Duplicate' }).click()
  96  |     await expect(page.locator('[data-testid^="sidebar-page-link-"]')).toHaveCount(countBeforeDuplicate + 1)
  97  | 
  98  |     const duplicatedPageId = currentPageId(page)
  99  |     await expect(page.getByTestId('page-toolbar-title')).toContainText(`${renamedTitle} Copy`)
  100 | 
  101 |     page.once('dialog', (dialog) => dialog.accept())
  102 |     await page.getByTestId('page-more-actions').click()
  103 |     await page.getByRole('button', { name: 'Archive' }).click()
  104 |     await expect(page.getByTestId(`sidebar-page-link-${duplicatedPageId}`)).toHaveCount(0)
  105 |     await expect(page).not.toHaveURL(new RegExp(duplicatedPageId))
  106 |   })
  107 | 
  108 |   test('layout drag reorder persists after reload', async ({ page }) => {
  109 |     await page.goto('/')
  110 |     await createPageFromSidebar(page, `Layout page ${Date.now()}`)
  111 | 
  112 |     await addWidgetFromDialog(page, 'Current language mix')
  113 |     await addWidgetFromDialog(page, 'Current language mix')
  114 | 
  115 |     await page.getByTestId('page-edit-mode-toggle').click()
  116 |     await expect(page.getByTestId('page-edit-mode-toggle')).toContainText('Done')
  117 | 
  118 |     const widgetIds = await page.locator('[data-testid^="widget-grid-item-"]').evaluateAll((nodes) =>
  119 |       nodes
  120 |         .map((node) => node.getAttribute('data-testid')?.replace('widget-grid-item-', ''))
  121 |         .filter((value): value is string => Boolean(value)),
  122 |     )
  123 |     expect(widgetIds.length).toBeGreaterThanOrEqual(2)
  124 | 
  125 |     const draggedWidgetId = widgetIds[1]
  126 |     const beforeDrag = await readGridOrder(page)
  127 |     await dragWidgetByHandle(page, draggedWidgetId, { x: 340, y: 120 })
  128 |     await page.waitForTimeout(1200)
  129 |     const afterDrag = await readGridOrder(page)
  130 |     expect(afterDrag).not.toEqual(beforeDrag)
  131 | 
  132 |     await page.reload()
  133 |     await expect(page.locator('[data-testid^="widget-grid-item-"]').first()).toBeVisible()
```