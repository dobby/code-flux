import { expect, test, type Dialog, type Page } from '@playwright/test'

test.describe('v2 workspace', () => {
  test('page management from the sidebar works', async ({ page }) => {
    await page.goto('/')

    await expect(page.getByTestId('app-sidebar')).toBeVisible()
    await expect(page.getByTestId('app-header')).toBeVisible()

    const pageTitle = `Sprint pulse ${Date.now()}`
    const pageId = await createPageFromSidebar(page, pageTitle)

    const renamedTitle = `${pageTitle} renamed`
    queueDialogs(page, ['rename', renamedTitle])
    await page.getByTestId(`page-menu-${pageId}`).click()
    await expect(page.getByTestId(`sidebar-page-link-${pageId}`)).toContainText(renamedTitle)

    const countBeforeDuplicate = await page.locator('[data-testid^="sidebar-page-link-"]').count()
    queueDialogs(page, ['duplicate'])
    await page.getByTestId(`page-menu-${pageId}`).click()
    await expect(page.locator('[data-testid^="sidebar-page-link-"]')).toHaveCount(countBeforeDuplicate + 1)
    const duplicatedLink = page.locator('[data-testid^="sidebar-page-link-"]').filter({ hasText: `${renamedTitle} Copy` }).first()
    await expect(duplicatedLink).toBeVisible()
    const duplicatedPageId = (await duplicatedLink.getAttribute('data-testid'))!.replace('sidebar-page-link-', '')

    queueDialogs(page, ['archive', true])
    await page.getByTestId(`page-menu-${duplicatedPageId}`).click()
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

  test('customer pages use shared header controls for time range, filters, and overflow', async ({ page }) => {
    await page.goto('/')

    await createPageFromSidebar(page, `Shared header ${Date.now()}`)
    await expect(page.getByTestId('app-header')).toContainText('Shared header')

    await page.getByTestId('page-time-range').click()
    const timeMenu = page.locator('.content-chrome__explorer-menu').filter({ hasText: 'Custom range' }).last()
    await expect(timeMenu).toBeVisible()
    await timeMenu.getByRole('menuitemradio', { name: /^Last 90 days$/ }).click()
    await expect(page.getByTestId('page-time-range')).toContainText('Last 90 days')

    await addPageFilter(page, { field: 'author', values: 'Ada Lovelace, Grace Hopper' })
    await addPageFilter(page, { field: 'category', values: 'Platform initiatives' })
    await addPageFilter(page, { field: 'branch', values: 'release/2026-q2' })

    await expect(page.getByTestId('app-header')).toContainText('Author:')
    await expect(page.getByTestId('page-add-filter')).toContainText('+ Filter (3)')

    await page.setViewportSize({ width: 720, height: 900 })
    await expect(page.getByTestId('page-toolbar-overflow')).toBeVisible()
    await page.getByTestId('page-toolbar-overflow').click()
    await expect(page.locator('.workspace-page-header-controls__overflow-menu')).toContainText('Branch:')
  })

  test('customer page more actions support rename, duplicate, and archive from the shared header', async ({ page }) => {
    await page.goto('/')

    await createPageFromSidebar(page, `Header actions ${Date.now()}`)

    const renamedTitle = `Header actions renamed ${Date.now()}`
    page.once('dialog', (dialog) => dialog.accept(renamedTitle))
    await page.getByTestId('page-more-actions').click()
    await page.getByRole('button', { name: 'Rename' }).click()
    await expect(page.getByTestId('page-toolbar-title')).toHaveText(renamedTitle)

    const countBeforeDuplicate = await page.locator('[data-testid^="sidebar-page-link-"]').count()
    await page.getByTestId('page-more-actions').click()
    await expect(page.getByRole('button', { name: 'Archive' })).toBeVisible()
    await page.getByRole('button', { name: 'Duplicate' }).click()
    await expect(page.locator('[data-testid^="sidebar-page-link-"]')).toHaveCount(countBeforeDuplicate + 1)

    const duplicatedPageId = currentPageId(page)
    await expect(page.getByTestId('page-toolbar-title')).toContainText(`${renamedTitle} Copy`)

    page.once('dialog', (dialog) => dialog.accept())
    await page.getByTestId('page-more-actions').click()
    await page.getByRole('button', { name: 'Archive' }).click()
    await expect(page.getByTestId(`sidebar-page-link-${duplicatedPageId}`)).toHaveCount(0)
    await expect(page).not.toHaveURL(new RegExp(duplicatedPageId))
  })

  test('layout drag reorder persists after reload', async ({ page }) => {
    await page.goto('/')
    await createPageFromSidebar(page, `Layout page ${Date.now()}`)

    await addWidgetFromDialog(page, 'Current language mix')
    await addWidgetFromDialog(page, 'Current language mix')

    await page.getByTestId('page-edit-mode-toggle').click()
    await expect(page.getByTestId('page-edit-mode-toggle')).toContainText('Done')

    const widgetIds = await page.locator('[data-testid^="widget-grid-item-"]').evaluateAll((nodes) =>
      nodes
        .map((node) => node.getAttribute('data-testid')?.replace('widget-grid-item-', ''))
        .filter((value): value is string => Boolean(value)),
    )
    expect(widgetIds.length).toBeGreaterThanOrEqual(2)

    const draggedWidgetId = widgetIds[1]
    const beforeDrag = await readGridOrder(page)
    await dragWidgetByHandle(page, draggedWidgetId, { x: 340, y: 120 })
    await page.waitForTimeout(1200)
    const afterDrag = await readGridOrder(page)
    expect(afterDrag).not.toEqual(beforeDrag)

    await page.reload()
    await expect(page.locator('[data-testid^="widget-grid-item-"]').first()).toBeVisible()
    expect(await readGridOrder(page)).toEqual(afterDrag)
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
    await ensureCurrentSnapshots(page)

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
      { path: '/activity', expected: /\/activity\/?$/ },
      { path: '/explorer', expected: /\/explorer\/?$/ },
      { path: '/codebase', expected: /\/codebase\/?$/ },
      { path: '/contributors', expected: /\/contributors\/?$/ },
      { path: '/widgets', expected: /\/widgets\/?$/ },
      { path: '/widgets/new', expected: /\/widgets\/new\/?$/ },
      { path: '/settings/general', expected: /\/settings\/general\/?$/ },
      { path: '/settings/sync', expected: /\/settings\/sync\/?$/ },
      { path: '/settings/jira', expected: /\/settings\/jira\/?$/ },
      { path: '/sync', expected: /\/sync\/?$/ },
      { path: '/legacy/overview', expected: /\/legacy\/overview\/?$/ },
    ]

    for (const route of routes) {
      await page.goto(route.path)
      await expect(page).toHaveURL(route.expected)
      await expect(page.getByTestId('app-sidebar')).toBeVisible()
      await expect(page.getByTestId('app-header')).toBeVisible()
    }
  })

  test('activity toolbar can switch the chart metric', async ({ page }) => {
    await page.goto('/activity')

    await expect(page.getByTestId('app-header')).toContainText('Activity')
    await expect(page.getByRole('button', { name: /Commit Count/ })).toBeVisible()

    await page.getByRole('button', { name: /Commit Count/ }).click()
    await page.getByRole('menuitemradio', { name: 'Lines Added' }).click()

    await expect(page.getByRole('button', { name: /Lines Added/ })).toBeVisible()
    await expect(page).toHaveURL(/metric=lines_added/)
  })

  test('activity chart can be resized and exposes area zoom mode', async ({ page }) => {
    await page.goto('/activity?range=90d&group=none')
    await ensureLegacySync(page)
    await page.goto('/activity?range=90d&group=none')

    const chart = page.getByTestId('activity-chart')
    await expect(chart).toBeVisible()
    await expect(page.locator('.explorer-chart__canvas canvas')).toBeVisible()
    await expect(page.getByTestId('activity-chart-zoom')).toBeVisible()
    await expect(page.getByTestId('activity-chart-reset-zoom')).toBeDisabled()

    const before = await chart.boundingBox()
    expect(before).not.toBeNull()

    const handle = page.getByTestId('activity-chart-resize-handle')
    const handleBox = await handle.boundingBox()
    expect(handleBox).not.toBeNull()
    await page.mouse.move(handleBox!.x + handleBox!.width / 2, handleBox!.y + handleBox!.height / 2)
    await page.mouse.down()
    await page.mouse.move(handleBox!.x + handleBox!.width / 2, handleBox!.y + handleBox!.height / 2 + 120)
    await page.mouse.up()

    const after = await chart.boundingBox()
    expect(after).not.toBeNull()
    expect(after!.height).toBeGreaterThan(before!.height + 80)

    await page.getByTestId('activity-chart-zoom').click()
    await expect(page.getByTestId('activity-chart-zoom')).toHaveAttribute('aria-pressed', 'true')
    await page.getByTestId('activity-chart-zoom').click()
    await expect(page.getByTestId('activity-chart-zoom')).toHaveAttribute('aria-pressed', 'false')
    await page.getByTestId('activity-chart-zoom').click()
    await expect(page.getByTestId('activity-chart-zoom')).toHaveAttribute('aria-pressed', 'true')

    const canvasBox = await page.locator('.explorer-chart__canvas').boundingBox()
    expect(canvasBox).not.toBeNull()
    await page.mouse.move(canvasBox!.x + 90, canvasBox!.y + 45)
    await page.mouse.down()
    await page.mouse.move(canvasBox!.x + 260, canvasBox!.y + 130)
    await page.mouse.up()

    await expect(page.getByTestId('activity-chart-reset-zoom')).toBeEnabled()
  })

  test('activity chart can hide point markers for smoother lines', async ({ page }) => {
    await page.goto('/activity?range=90d&group=author')
    await ensureLegacySync(page)
    await page.goto('/activity?range=90d&group=author')

    const chart = page.getByTestId('activity-chart')
    await expect(chart).toHaveAttribute('data-point-markers', 'visible')

    await page.getByTestId('activity-chart-point-markers').click()
    await expect(page.getByTestId('activity-chart-point-markers')).toHaveAttribute('aria-pressed', 'true')
    await expect(chart).toHaveAttribute('data-point-markers', 'hidden')

    await page.reload()
    await expect(page.getByTestId('activity-chart-point-markers')).toHaveAttribute('aria-pressed', 'true')
    await expect(chart).toHaveAttribute('data-point-markers', 'hidden')
  })

  test('activity chart supports a per-metric y-axis cutoff', async ({ page }) => {
    await page.goto('/activity?range=90d&group=none&metric=lines_removed')
    await ensureLegacySync(page)
    await page.goto('/activity?range=90d&group=none&metric=lines_removed')

    const chart = page.getByTestId('activity-chart')
    await expect(chart).toBeVisible()
    await expect(page.locator('.explorer-chart__canvas canvas')).toBeVisible()

    await page.getByTestId('activity-chart-y-max').fill('1000')
    await page.getByTestId('activity-chart-apply-y-max').click()

    await expect(chart).toHaveAttribute('data-y-axis-cutoff', '1000')
    await expect(page.getByTestId('activity-chart-clear-y-max')).toBeEnabled()

    await page.reload()
    await expect(page.getByTestId('activity-chart-y-max')).toHaveValue('1000')
    await expect(chart).toHaveAttribute('data-y-axis-cutoff', '1000')

    await page.getByTestId('activity-chart-clear-y-max').click()
    await expect(page.getByTestId('activity-chart-y-max')).toHaveValue('1000')
    await expect(chart).not.toHaveAttribute('data-y-axis-cutoff', '1000')
  })

  test('activity can anonymize author names', async ({ page }) => {
    await page.goto('/activity?range=90d&group=author')
    await ensureLegacySync(page)
    await page.goto('/activity?range=90d&group=author')

    await expect(page.getByText(/Eli ·/).first()).toBeVisible()
    await page.getByTestId('activity-anonymize-authors').click()

    await expect(page.getByTestId('activity-anonymize-authors')).toHaveAttribute('aria-pressed', 'true')
    const anonymizedMeta = await page.locator('.explorer-split__commit-meta').first().innerText()
    expect(anonymizedMeta).not.toContain('Eli')
    expect(anonymizedMeta).not.toContain('Contributor')
    expect(anonymizedMeta).toContain(' · ')
    await expect(page.getByText(/Eli ·/)).toHaveCount(0)

    await page.reload()
    await expect(page.getByTestId('activity-anonymize-authors')).toHaveAttribute('aria-pressed', 'true')
    const reloadedMeta = await page.locator('.explorer-split__commit-meta').first().innerText()
    expect(reloadedMeta).not.toContain('Eli')
    expect(reloadedMeta).not.toContain('Contributor')
    expect(reloadedMeta).toContain(' · ')
  })

  test('sidebar reflects the phase 1 information architecture', async ({ page }) => {
    await page.goto('/')

    await expect(page.getByTestId('nav-activity')).toBeVisible()
    await expect(page.getByTestId('nav-activity')).toContainText('Activity')
    await expect(page.getByTestId('nav-codebase')).toBeVisible()
    await expect(page.getByTestId('nav-codebase')).toContainText('Codebase')
    await expect(page.getByTestId('nav-contributors')).toBeVisible()
    await expect(page.getByTestId('nav-contributors')).toContainText('Contributors')
    await expect(page.getByTestId('nav-widgets')).toBeVisible()
    await expect(page.getByTestId('nav-widgets')).toContainText('Widget Catalog')
    await expect(page.getByTestId('nav-sync')).toHaveCount(0)
    await expect(page.getByText('Pages')).toBeVisible()

    await page.getByTestId('nav-settings').click()
    await expect(page).toHaveURL(/\/settings\/general$/)

    const settingsLabels = await page
      .locator('[data-testid="app-sidebar"] .sidebar-menu')
      .nth(1)
      .locator('.sidebar-item__label')
      .allInnerTexts()

    expect(settingsLabels).toEqual([
      'General',
      'Sync',
      'Jira',
    ])
  })

  test('activity supports cumulative net and commit detail navigation', async ({ page }) => {
    await page.goto('/activity?range=90d&group=none&metric=cumulative_net')
    await ensureLegacySync(page)
    await page.goto('/activity?range=90d&group=none&metric=cumulative_net')

    await expect(page.getByTestId('app-header')).toContainText('Activity')
    await expect(page.getByText(/selected/i).first()).toBeVisible()

    await expect(page).toHaveURL(/metric=cumulative_net/)
    await expect(page.locator('.explorer-chart__title')).toHaveText('Cumulative Net')

    await page.getByRole('button', { name: /^Compare$/ }).click()
    const compareMenu = page.locator('.content-chrome__explorer-menu').filter({ hasText: 'Custom past date' }).last()
    await expect(compareMenu).toBeVisible()

    await compareMenu.getByRole('menuitemradio', { name: /^Previous period$/ }).click()
    await expect(page).toHaveURL(/compareMode=previous_period/)
    await expect(page.locator('.explorer-chart__compare-summary')).toContainText('Previous period')

    const compareDates = buildActivityCompareDates(90)
    await page.getByRole('button', { name: /Previous period/i }).click()
    await expect(compareMenu).toBeVisible()
    await compareMenu.locator('input[type="date"]').nth(0).fill(compareDates.anchorDate)
    await compareMenu.getByRole('button', { name: 'Use custom past date' }).click()
    await expect(page).toHaveURL(new RegExp(`compareMode=custom_anchor_date.*compareAnchor=${compareDates.anchorDate}`))
    await expect(page.locator('.explorer-chart__compare-summary')).toContainText('Custom past date')

    await page.getByRole('button', { name: /Custom past date/i }).click()
    await expect(compareMenu).toBeVisible()
    await compareMenu.locator('input[type="date"]').nth(1).fill(compareDates.customRange.from)
    await compareMenu.locator('input[type="date"]').nth(2).fill(compareDates.customRange.to)
    await compareMenu.getByRole('button', { name: 'Use custom date range' }).click()
    await expect(page).toHaveURL(new RegExp(
      `compareMode=custom_range.*compareFrom=${compareDates.customRange.from}.*compareTo=${compareDates.customRange.to}`,
    ))
    await expect(page.locator('.explorer-chart__compare-summary')).toContainText('Custom date range')

    await expect(page.getByRole('button', { name: /Open full detail/i })).toBeVisible()

    await page.getByRole('button', { name: /Open full detail/i }).click()
    await expect(page).toHaveURL(/\/activity\/commit\//)
    await expect(page.getByText('Activity commit detail')).toBeVisible()
    await expect(page.locator('.commit-detail__back')).toContainText('Activity')
  })

  test('contributors supports metric switching with shared chrome filters', async ({ page }) => {
    await page.goto('/contributors')
    await ensureLegacySync(page)
    await page.goto('/contributors')

    await expect(page.getByTestId('app-header')).toContainText('Contributors')
    await expect(page.getByRole('button', { name: /Last 14 days/i })).toBeVisible()
    await expect(page.getByRole('button', { name: /All repositories/i })).toBeVisible()

    await page.getByRole('button', { name: /Last 14 days/i }).click()
    await page.getByRole('menuitemradio', { name: /^Last 90 days$/ }).click()

    await expect(page.getByTestId('contributors-leaderboard')).toBeVisible()
    await expect(page.getByTestId('contributors-trend-chart')).toBeVisible()

    await page.getByTestId('contributors-metric-commits_count').click()
    await expect(page.getByRole('columnheader', { name: 'Commits' })).toBeVisible()
    await expect(page.getByRole('button', { name: 'Commit Count' })).toHaveClass(/contributors-metric-switcher__button--active/)

    await page.getByTestId('contributors-metric-lines_added').click()
    await expect(page.getByRole('columnheader', { name: 'Added' })).toBeVisible()
    await expect(page.getByRole('button', { name: 'Lines Added' })).toHaveClass(/contributors-metric-switcher__button--active/)
  })

  test('codebase shows growth data with shared header filters', async ({ page }) => {
    await page.goto('/')
    await ensureLegacySync(page)
    await page.goto('/settings/general')
    await ensureCurrentSnapshots(page)
    await page.goto('/')

    const repos = await page.evaluate(async () => {
      const response = await fetch('/api/bootstrap')
      const body = await response.json()
      return body.repos.filter((repo: { enabled: boolean }) => repo.enabled)
    })

    expect(repos.length).toBeGreaterThan(0)

    await page.getByTestId('nav-codebase').click()
    await expect(page).toHaveURL(/\/codebase$/)
    await expect(page.getByTestId('nav-codebase-children')).toHaveCount(0)
    await expect(page.getByRole('button', { name: new RegExp(repos[0].displayName) })).toBeVisible()
    await expect(page.getByRole('button', { name: /Last 14 days/i })).toBeVisible()
    await expect(page.getByRole('button', { name: /^\+ Filter/ })).toBeVisible()

    await page.getByRole('button', { name: /Last 14 days/i }).click()
    await page.getByRole('menuitemradio', { name: /^Last 90 days$/ }).click()
    await expect(page.getByTestId('codebase-summary-row')).toContainText('Last 90 days')
    await expect(page.getByTestId('codebase-growth-chart')).toBeVisible()
    await expect(page.getByTestId('codebase-treemap')).toBeVisible()
    await expect(page.getByTestId('codebase-snapshot-summary')).toBeVisible()
    await expect(page.getByTestId('codebase-breakdown-languages')).toBeVisible()

    await page.getByTestId('codebase-size-mode-files').click()
    await expect(page.getByTestId('codebase-size-mode-files')).toHaveClass(/codebase-mode-switcher__button--active/)
    await expect(page.getByTestId('codebase-treemap')).toBeVisible()

    await page.getByTestId('codebase-size-mode-net').click()
    await expect(page.getByTestId('codebase-size-mode-net')).toHaveClass(/codebase-mode-switcher__button--active/)

    await page.getByRole('button', { name: /^\+ Filter/ }).click()
    await page.getByRole('button', { name: 'Author' }).click()
    await expect(page.getByRole('button', { name: /^Author$/ })).toBeVisible()
    await page.getByRole('button', { name: /^Author$/ }).click()
    const firstAuthorOption = page.getByRole('menuitemcheckbox').first()
    const firstAuthorLabel = (await firstAuthorOption.textContent())?.trim()
    expect(firstAuthorLabel).toBeTruthy()
    await firstAuthorOption.click()
    await expect(page.getByTestId('codebase-summary-row')).toContainText('Author:')

    const targetRepo = repos[Math.min(1, repos.length - 1)]
    await page.getByRole('button', { name: new RegExp(repos[0].displayName) }).click()
    await page.getByRole('menuitemradio', { name: new RegExp(targetRepo.displayName) }).click()
    await expect(page).toHaveURL(/\/codebase$/)
    await expect(page.getByRole('button', { name: new RegExp(targetRepo.displayName) })).toBeVisible()
  })

  test('sidebar collapse covers the sidebar while keeping controls fixed', async ({ page }) => {
    await page.goto('/')

    const sidebar = page.getByTestId('app-sidebar')
    const surface = page.getByTestId('app-content-surface')
    const header = page.getByTestId('app-header')
    const controls = page.getByTestId('app-floating-controls')

    await expect(sidebar).toBeVisible()
    await expect(surface).toBeVisible()
    await expect(header).toBeVisible()

    const expandedSidebarBox = await sidebar.boundingBox()
    const expandedSurfaceBox = await surface.boundingBox()
    const expandedHeaderBox = await header.boundingBox()
    const controlsBeforeBox = await controls.boundingBox()

    expect(expandedSidebarBox).not.toBeNull()
    expect(expandedSurfaceBox).not.toBeNull()
    expect(expandedHeaderBox).not.toBeNull()
    expect(controlsBeforeBox).not.toBeNull()

    expect(expandedSurfaceBox!.x).toBeCloseTo(expandedSidebarBox!.width, 0)
    expect(expandedHeaderBox!.x).toBeGreaterThanOrEqual((await floatingControlsBoundary(page)) - 1)

    await page.getByTestId('sidebar-toggle').click()
    await page.waitForTimeout(350)

    await expect(sidebar).toBeVisible()
    await expect(surface).toBeVisible()
    await expect(header).toBeVisible()

    const collapsedSurfaceBox = await surface.boundingBox()
    const collapsedHeaderBox = await header.boundingBox()
    const controlsAfterBox = await controls.boundingBox()

    expect(collapsedSurfaceBox).not.toBeNull()
    expect(collapsedHeaderBox).not.toBeNull()
    expect(controlsAfterBox).not.toBeNull()

    expect(controlsAfterBox!.x).toBeCloseTo(controlsBeforeBox!.x, 1)
    expect(controlsAfterBox!.y).toBeCloseTo(controlsBeforeBox!.y, 1)
    expect(collapsedSurfaceBox!.x).toBeLessThan(expandedSurfaceBox!.x)
    expect(collapsedSurfaceBox!.x).toBeLessThanOrEqual(1)
    expect(collapsedHeaderBox!.x).toBeLessThan(expandedHeaderBox!.x)
    expect(collapsedHeaderBox!.x).toBeGreaterThanOrEqual((await floatingControlsBoundary(page)) - 1)
    expect(await contentSurfaceCoversSidebar(page)).toBe(true)
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
  await expect(page.getByTestId('page-toolbar-title')).toHaveText(title)
  return currentPageId(page)
}

async function addWidgetFromDialog(page: Page, widgetTitle: string) {
  await page.getByTestId('page-add-widget').click()
  await expect(page.getByRole('dialog', { name: 'Widget picker' })).toBeVisible()
  await page.getByRole('button', { name: new RegExp(widgetTitle, 'i') }).click()
}

async function addPageFilter(page: Page, payload: { field: string; values: string }) {
  const inlineAddFilter = page.getByTestId('page-add-filter')
  if (await inlineAddFilter.isVisible().catch(() => false)) {
    await inlineAddFilter.click()
  } else {
    await page.getByTestId('page-toolbar-overflow').click()
    const overflowMenu = page.locator('.workspace-page-header-controls__overflow-menu').last()
    await expect(overflowMenu).toBeVisible()
    await overflowMenu.getByRole('button', { name: /^\+ Filter/ }).click()
  }
  const filterMenu = page.locator('.workspace-page-header-controls__filter-menu').last()
  await expect(filterMenu).toBeVisible()
  await filterMenu.locator('select').nth(0).selectOption(payload.field)
  await filterMenu.getByPlaceholder('frontend, api').fill(payload.values)
  await filterMenu.getByRole('button', { name: 'Add filter' }).click()
}

async function dragWidgetByHandle(page: Page, widgetId: string, delta: { x: number; y: number }) {
  await page.evaluate(
    ([targetWidgetId, targetDelta]) => {
      const gridElement = document.querySelector('[data-testid="muuri-page-grid"]') as (HTMLElement & {
        muuri?: {
          getItems: () => Array<{ getElement: () => HTMLElement | undefined }>
          move: (item: HTMLElement, position: number, options: { layout: 'instant' }) => void
        }
      }) | null
      const widgetElement = document.querySelector(`[data-testid="widget-grid-item-${targetWidgetId}"]`) as HTMLElement | null
      if (!gridElement?.muuri || !widgetElement) {
        throw new Error('Muuri widget not available for drag test')
      }
      const items = gridElement.muuri.getItems()
      const currentIndex = items.findIndex((item) => item.getElement() === widgetElement)
      if (currentIndex < 0) {
        throw new Error('Muuri widget item not found')
      }
      const targetOffset = Math.max(1, Math.round((Math.abs(targetDelta.x) + Math.abs(targetDelta.y)) / 240))
      const addSlotOffset = items.at(-1)?.getElement()?.dataset.id === '__page_add_widget_slot__' ? 1 : 0
      const maxIndex = Math.max(0, items.length - 1 - addSlotOffset)
      const direction = currentIndex >= maxIndex ? -1 : 1
      const nextIndex = Math.max(0, Math.min(maxIndex, currentIndex + (direction * targetOffset)))
      gridElement.muuri.move(widgetElement, nextIndex, { layout: 'instant' })
    },
    [widgetId, delta] as const,
  )
}

async function readGridOrder(page: Page) {
  return page.locator('[data-testid^="widget-grid-item-"]').evaluateAll((nodes) =>
    nodes.map((node) => node.getAttribute('data-testid')?.replace('widget-grid-item-', '') ?? ''),
  )
}

function queueDialogs(page: Page, responses: Array<string | true>) {
  const pending = [...responses]
  const handler = async (dialog: Dialog) => {
    const next = pending.shift()
    if (next === undefined) {
      throw new Error('Received unexpected dialog during sidebar page flow')
    }
    if (next === true) {
      await dialog.accept()
    } else {
      await dialog.accept(next)
    }
    if (pending.length === 0) {
      page.off('dialog', handler)
    }
  }
  page.on('dialog', handler)
}

async function floatingControlsBoundary(page: Page) {
  const forwardButton = page.getByRole('button', { name: 'Go forward' })
  const box = await forwardButton.boundingBox()
  if (!box) {
    throw new Error('Forward button not available for shell geometry assertions')
  }
  return box.x + box.width
}

async function contentSurfaceCoversSidebar(page: Page) {
  return page.evaluate(() => {
    const sidebar = document.querySelector('[data-testid="app-sidebar"]')
    if (!(sidebar instanceof HTMLElement)) {
      return false
    }
    const rect = sidebar.getBoundingClientRect()
    const probeX = rect.left + Math.max(24, rect.width / 2)
    const probeY = rect.top + 96
    const element = document.elementFromPoint(probeX, probeY)
    return Boolean(element?.closest('[data-testid="app-content-surface"]'))
  })
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

async function ensureCurrentSnapshots(page: Page) {
  await page.getByTestId('build-current-snapshots').click()

  await expect.poll(
    async () => page.evaluate(async () => {
      const response = await fetch('/api/v2/snapshots/status')
      const body = await response.json()
      return body.items.length
    }),
    { timeout: 30_000 },
  ).toBeGreaterThan(0)
}

function buildActivityCompareDates(spanDays: number) {
  const today = new Date()
  const currentFrom = shiftDate(today, -(spanDays - 1))
  const anchorDate = shiftDate(currentFrom, -7)
  const customRangeTo = shiftDate(currentFrom, -14)
  const customRangeFrom = shiftDate(customRangeTo, -(spanDays - 1))

  return {
    anchorDate: formatDateInput(anchorDate),
    customRange: {
      from: formatDateInput(customRangeFrom),
      to: formatDateInput(customRangeTo),
    },
  }
}

function shiftDate(source: Date, days: number) {
  const date = new Date(source)
  date.setDate(date.getDate() + days)
  return date
}

function formatDateInput(date: Date) {
  return [
    date.getFullYear(),
    String(date.getMonth() + 1).padStart(2, '0'),
    String(date.getDate()).padStart(2, '0'),
  ].join('-')
}

function currentPageId(page: Page) {
  const match = page.url().match(/\/pages\/([^/]+)$/)
  expect(match).not.toBeNull()
  return match![1]
}
