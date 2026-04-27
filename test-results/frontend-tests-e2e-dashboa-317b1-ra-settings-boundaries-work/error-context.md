# Instructions

- Following Playwright test failed.
- Explain why, be concise, respect Playwright best practices.
- Provide a snippet of code with the fix, if possible.

# Test info

- Name: frontend/tests/e2e/dashboard.spec.ts >> v2 workspace >> snapshot build and jira settings boundaries work
- Location: frontend/tests/e2e/dashboard.spec.ts:175:3

# Error details

```
Error: page.goto: Protocol error (Page.navigate): Cannot navigate to invalid URL
Call log:
  - navigating to "/", waiting until "load"

```

# Test source

```ts
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
  134 |     expect(await readGridOrder(page)).toEqual(afterDrag)
  135 |   })
  136 | 
  137 |   test('drilldown tabs and annotation persistence work', async ({ page }) => {
  138 |     await page.goto('/')
  139 |     await ensureLegacySync(page)
  140 |     await createPageFromSidebar(page, `Drilldown page ${Date.now()}`)
  141 |     await addWidgetFromDialog(page, 'Daily lines added')
  142 |     await page.reload()
  143 | 
  144 |     const widgetId = await firstWidgetId(page)
  145 |     await page.getByTestId(`chart-inspect-${widgetId}`).click()
  146 | 
  147 |     await expect(page.getByTestId('drilldown-date')).not.toHaveText('No day selected')
  148 |     await page.getByTestId('drilldown-tab-commits').click()
  149 |     await expect(page.getByTestId('drilldown-commits-tab')).toBeVisible()
  150 | 
  151 |     await page.getByTestId('drilldown-tab-summary').click()
  152 |     const annotationTitle = `Spike note ${Date.now()}`
  153 |     await page.getByTestId('annotation-title').fill(annotationTitle)
  154 |     await page.getByTestId('annotation-body').fill('Captured from the drilldown drawer.')
  155 |     await page.getByTestId('annotation-save').click()
  156 | 
  157 |     await expect(page.getByTestId('annotation-list')).toContainText(annotationTitle)
  158 |     await expect(page.locator(`[data-testid="widget-tile-${widgetId}"]`)).toContainText('1 notes')
  159 | 
  160 |     await page.reload()
  161 |     await page.getByTestId(`chart-inspect-${widgetId}`).click()
  162 |     await expect(page.getByTestId('annotation-list')).toContainText(annotationTitle)
  163 | 
  164 |     const editButton = page.locator('[data-testid^="annotation-edit-"]').first()
  165 |     await editButton.click()
  166 |     await page.getByTestId('annotation-title').fill(`${annotationTitle} updated`)
  167 |     await page.getByTestId('annotation-save').click()
  168 |     await expect(page.getByTestId('annotation-list')).toContainText(`${annotationTitle} updated`)
  169 | 
  170 |     await editButton.click()
  171 |     await page.getByTestId('annotation-delete').click()
  172 |     await expect(page.locator('[data-testid^="annotation-edit-"]')).toHaveCount(0)
  173 |   })
  174 | 
  175 |   test('snapshot build and jira settings boundaries work', async ({ page }) => {
> 176 |     await page.goto('/')
      |                ^ Error: page.goto: Protocol error (Page.navigate): Cannot navigate to invalid URL
  177 |     await ensureLegacySync(page)
  178 |     await page.goto('/settings/general')
  179 |     await ensureCurrentSnapshots(page)
  180 | 
  181 |     const pageId = await createPageFromSidebar(page, `Repo state page ${Date.now()}`)
  182 |     await page.getByTestId(`sidebar-page-link-${pageId}`).click()
  183 |     await addWidgetFromDialog(page, 'Current language mix')
  184 |     const widgetId = await firstWidgetId(page)
  185 |     await expect(page.getByTestId(`chart-rendered-${widgetId}`)).toBeVisible()
  186 | 
  187 |     await page.goto('/settings/jira')
  188 |     const token = `playwright-token-${Date.now()}`
  189 |     await page.getByTestId('jira-base-url').fill('https://jira.example.com')
  190 |     await page.getByTestId('jira-project-keys').fill('CFX')
  191 |     await page.getByTestId('jira-token').fill(token)
  192 |     await page.getByTestId('jira-save').click()
  193 |     await expect(page.getByText('Jira settings saved.')).toBeVisible()
  194 | 
  195 |     const settingsPayload = await page.evaluate(async () => {
  196 |       const response = await fetch('/api/v2/settings/jira')
  197 |       return response.text()
  198 |     })
  199 |     expect(settingsPayload).not.toContain(token)
  200 | 
  201 |     const jiraStatus = await page.evaluate(async () => {
  202 |       const response = await fetch('/api/v2/jira/status')
  203 |       return response.json()
  204 |     })
  205 |     expect(jiraStatus.secretConfigured).toBe(true)
  206 |   })
  207 | 
  208 |   test('route smoke checks for v2 states', async ({ page }) => {
  209 |     const routes = [
  210 |       { path: '/activity', expected: /\/activity\/?$/ },
  211 |       { path: '/explorer', expected: /\/explorer\/?$/ },
  212 |       { path: '/codebase', expected: /\/codebase\/?$/ },
  213 |       { path: '/contributors', expected: /\/contributors\/?$/ },
  214 |       { path: '/widgets', expected: /\/widgets\/?$/ },
  215 |       { path: '/widgets/new', expected: /\/widgets\/new\/?$/ },
  216 |       { path: '/settings/general', expected: /\/settings\/general\/?$/ },
  217 |       { path: '/settings/sync', expected: /\/settings\/sync\/?$/ },
  218 |       { path: '/settings/jira', expected: /\/settings\/jira\/?$/ },
  219 |       { path: '/sync', expected: /\/sync\/?$/ },
  220 |       { path: '/legacy/overview', expected: /\/legacy\/overview\/?$/ },
  221 |     ]
  222 | 
  223 |     for (const route of routes) {
  224 |       await page.goto(route.path)
  225 |       await expect(page).toHaveURL(route.expected)
  226 |       await expect(page.getByTestId('app-sidebar')).toBeVisible()
  227 |       await expect(page.getByTestId('app-header')).toBeVisible()
  228 |     }
  229 |   })
  230 | 
  231 |   test('sidebar reflects the phase 1 information architecture', async ({ page }) => {
  232 |     await page.goto('/')
  233 | 
  234 |     await expect(page.getByTestId('nav-activity')).toBeVisible()
  235 |     await expect(page.getByTestId('nav-activity')).toContainText('Activity')
  236 |     await expect(page.getByTestId('nav-codebase')).toBeVisible()
  237 |     await expect(page.getByTestId('nav-codebase')).toContainText('Codebase')
  238 |     await expect(page.getByTestId('nav-contributors')).toBeVisible()
  239 |     await expect(page.getByTestId('nav-contributors')).toContainText('Contributors')
  240 |     await expect(page.getByTestId('nav-widgets')).toBeVisible()
  241 |     await expect(page.getByTestId('nav-widgets')).toContainText('Widget Catalog')
  242 |     await expect(page.getByTestId('nav-sync')).toHaveCount(0)
  243 |     await expect(page.getByText('Pages')).toBeVisible()
  244 | 
  245 |     await page.getByTestId('nav-settings').click()
  246 |     await expect(page).toHaveURL(/\/settings\/general$/)
  247 | 
  248 |     const settingsLabels = await page
  249 |       .locator('[data-testid="app-sidebar"] .sidebar-menu')
  250 |       .nth(1)
  251 |       .locator('.sidebar-item__label')
  252 |       .allInnerTexts()
  253 | 
  254 |     expect(settingsLabels).toEqual([
  255 |       'General',
  256 |       'Sync',
  257 |       'Jira',
  258 |     ])
  259 |   })
  260 | 
  261 |   test('activity supports cumulative net and commit detail navigation', async ({ page }) => {
  262 |     await page.goto('/activity?range=90d&group=none&metric=cumulative_net')
  263 |     await ensureLegacySync(page)
  264 |     await page.goto('/activity?range=90d&group=none&metric=cumulative_net')
  265 | 
  266 |     await expect(page.getByTestId('app-header')).toContainText('Activity')
  267 |     await expect(page.getByText(/selected/i).first()).toBeVisible()
  268 | 
  269 |     await expect(page).toHaveURL(/metric=cumulative_net/)
  270 |     await expect(page.locator('.explorer-chart__title')).toHaveText('Cumulative Net')
  271 | 
  272 |     await page.getByRole('button', { name: /^Compare$/ }).click()
  273 |     const compareMenu = page.locator('.content-chrome__explorer-menu').filter({ hasText: 'Custom past date' }).last()
  274 |     await expect(compareMenu).toBeVisible()
  275 | 
  276 |     await compareMenu.getByRole('menuitemradio', { name: /^Previous period$/ }).click()
```