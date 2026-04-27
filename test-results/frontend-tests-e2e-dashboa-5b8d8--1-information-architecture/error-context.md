# Instructions

- Following Playwright test failed.
- Explain why, be concise, respect Playwright best practices.
- Provide a snippet of code with the fix, if possible.

# Test info

- Name: frontend/tests/e2e/dashboard.spec.ts >> v2 workspace >> sidebar reflects the phase 1 information architecture
- Location: frontend/tests/e2e/dashboard.spec.ts:231:3

# Error details

```
Error: page.goto: Protocol error (Page.navigate): Cannot navigate to invalid URL
Call log:
  - navigating to "/", waiting until "load"

```

# Test source

```ts
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
  176 |     await page.goto('/')
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
> 232 |     await page.goto('/')
      |                ^ Error: page.goto: Protocol error (Page.navigate): Cannot navigate to invalid URL
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
  277 |     await expect(page).toHaveURL(/compareMode=previous_period/)
  278 |     await expect(page.locator('.explorer-chart__compare-summary')).toContainText('Previous period')
  279 | 
  280 |     const compareDates = buildActivityCompareDates(90)
  281 |     await page.getByRole('button', { name: /Previous period/i }).click()
  282 |     await expect(compareMenu).toBeVisible()
  283 |     await compareMenu.locator('input[type="date"]').nth(0).fill(compareDates.anchorDate)
  284 |     await compareMenu.getByRole('button', { name: 'Use custom past date' }).click()
  285 |     await expect(page).toHaveURL(new RegExp(`compareMode=custom_anchor_date.*compareAnchor=${compareDates.anchorDate}`))
  286 |     await expect(page.locator('.explorer-chart__compare-summary')).toContainText('Custom past date')
  287 | 
  288 |     await page.getByRole('button', { name: /Custom past date/i }).click()
  289 |     await expect(compareMenu).toBeVisible()
  290 |     await compareMenu.locator('input[type="date"]').nth(1).fill(compareDates.customRange.from)
  291 |     await compareMenu.locator('input[type="date"]').nth(2).fill(compareDates.customRange.to)
  292 |     await compareMenu.getByRole('button', { name: 'Use custom date range' }).click()
  293 |     await expect(page).toHaveURL(new RegExp(
  294 |       `compareMode=custom_range.*compareFrom=${compareDates.customRange.from}.*compareTo=${compareDates.customRange.to}`,
  295 |     ))
  296 |     await expect(page.locator('.explorer-chart__compare-summary')).toContainText('Custom date range')
  297 | 
  298 |     await expect(page.getByRole('button', { name: /Open full detail/i })).toBeVisible()
  299 | 
  300 |     await page.getByRole('button', { name: /Open full detail/i }).click()
  301 |     await expect(page).toHaveURL(/\/activity\/commit\//)
  302 |     await expect(page.getByText('Activity commit detail')).toBeVisible()
  303 |     await expect(page.locator('.commit-detail__back')).toContainText('Activity')
  304 |   })
  305 | 
  306 |   test('contributors supports metric switching with shared chrome filters', async ({ page }) => {
  307 |     await page.goto('/contributors')
  308 |     await ensureLegacySync(page)
  309 |     await page.goto('/contributors')
  310 | 
  311 |     await expect(page.getByTestId('app-header')).toContainText('Contributors')
  312 |     await expect(page.getByRole('button', { name: /Last 14 days/i })).toBeVisible()
  313 |     await expect(page.getByRole('button', { name: /All repositories/i })).toBeVisible()
  314 | 
  315 |     await page.getByRole('button', { name: /Last 14 days/i }).click()
  316 |     await page.getByRole('menuitemradio', { name: /^Last 90 days$/ }).click()
  317 | 
  318 |     await expect(page.getByTestId('contributors-leaderboard')).toBeVisible()
  319 |     await expect(page.getByTestId('contributors-trend-chart')).toBeVisible()
  320 | 
  321 |     await page.getByTestId('contributors-metric-commits_count').click()
  322 |     await expect(page.getByRole('columnheader', { name: 'Commits' })).toBeVisible()
  323 |     await expect(page.getByRole('button', { name: 'Commit Count' })).toHaveClass(/contributors-metric-switcher__button--active/)
  324 | 
  325 |     await page.getByTestId('contributors-metric-lines_added').click()
  326 |     await expect(page.getByRole('columnheader', { name: 'Added' })).toBeVisible()
  327 |     await expect(page.getByRole('button', { name: 'Lines Added' })).toHaveClass(/contributors-metric-switcher__button--active/)
  328 |   })
  329 | 
  330 |   test('codebase shows growth data with shared header filters', async ({ page }) => {
  331 |     await page.goto('/')
  332 |     await ensureLegacySync(page)
```