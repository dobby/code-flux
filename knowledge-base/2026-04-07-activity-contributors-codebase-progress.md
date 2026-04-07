# Activity, Contributors, and Codebase Progress

Update this file continuously. Do not wait until the end.

Related plan: [2026-04-07-activity-contributors-codebase-implementation-plan.md](2026-04-07-activity-contributors-codebase-implementation-plan.md)
Related design: [2026-04-07-activity-contributors-codebase-design.md](2026-04-07-activity-contributors-codebase-design.md)

## Status legend

- `[ ]` not started
- `[-]` in progress
- `[x]` done and verified

## Phase 0: baseline and route audit

- [x] Inventory current sidebar ownership
- [x] Inventory current Explore routes and links
- [x] Confirm current Sync navigation entry points
- [x] Confirm fixed surfaces vs user-created Pages section
- [x] Confirm ECharts treemap implementation path

## Phase 1: navigation and information architecture

- [x] Rename Explore navigation label to Activity
- [x] Reorder top-level menu items
- [x] Move Sync under Settings
- [x] Create distinct Pages section for user-created pages
- [x] Verify no broken navigation routes remain

## Phase 2: Activity surface alignment

- [x] Rename Explore route and page copy to Activity
- [x] Verify existing chart behavior still works
- [x] Verify drilldown still works
- [x] Verify commit-detail flow still works
- [x] Verify annotations still work
- [x] Verify cumulative-net mode is exposed correctly

## Phase 3: Contributors page

- [x] Add Contributors route and shell
- [x] Implement leaderboard query path
- [x] Implement metric switcher
- [x] Implement summary cards
- [x] Implement comparison/trend visualization
- [x] Verify empty states

## Phase 4: Codebase page shell and repo navigation

- [x] Add top-level Codebase item
- [x] Add repo child entries
- [x] Remove Codebase repo selector from header
- [x] Keep Codebase title in top header only
- [x] Keep Codebase filters in top header only

## Phase 5: Codebase queries and filters

- [x] Implement growth query for actual LOC and cumulative net
- [x] Implement Codebase header filter model
- [x] Support category filter
- [x] Support author filter
- [x] Support language filter
- [x] Verify filter semantics for structure vs activity sizing

## Phase 6: Codebase treemap and breakdowns

- [x] Implement ECharts treemap
- [x] Implement LOC size mode
- [x] Implement Files size mode
- [x] Implement Net activity size mode
- [x] Implement supporting language breakdown
- [x] Implement supporting category breakdown
- [x] Implement snapshot summary block

## Phase 7: QA and regression

- [ ] Add backend tests for contributor ranking
- [ ] Add backend tests for Codebase growth query
- [ ] Add backend tests for treemap aggregation
- [ ] Add frontend tests for sidebar order and Pages grouping
- [ ] Add frontend tests for Contributors metric switching
- [ ] Add frontend tests for Codebase repo navigation
- [ ] Add frontend tests for Codebase header filters
- [ ] Re-run Activity regression checks

## Notes

### 2026-04-07 planning state

- [x] Design spec written and approved
- [x] Pencil mockups updated and reviewed
- [x] Implementation plan created
- [x] Dedicated progress tracker created

### 2026-04-07 Phase 0 audit

- [x] Baseline audit written in `knowledge-base/2026-04-07-activity-contributors-codebase-phase-0-audit.md`
- [x] Confirmed `frontend/src/App.vue` owns the current sidebar IA and `Pages` section rendering
- [x] Confirmed Explore rename will require route, redirect, and route-name updates, not just label changes
- [x] Confirmed Sync is currently a first-level route and sidebar entry and will need a settings-nav home
- [x] Confirmed fixed product surfaces are static routes while user-created pages come from `workspace.orderedPages`
- [x] Confirmed treemap should extend the existing `vue-echarts` and `frontend/src/lib/chart.ts` path

### 2026-04-07 Phase 1 navigation

- [x] Added `Activity`, `Codebase`, and `Contributors` navigation entries in the approved order
- [x] Moved Sync into the Settings navigation and kept `/sync` working as an alias to `/settings/sync`
- [x] Renamed the primary Explore route to `Activity` while keeping `/explorer` and `/explorer/commit/...` aliases working
- [x] Added placeholder `Codebase` and `Contributors` route shells so the IA is navigable before later feature phases
- [x] Updated backend SPA forwarding to recognize the new client-side routes
- [x] Fixed Activity query-sync behavior so leaving Activity does not bounce the app back onto `/activity`
- [x] Verified with `npm run build`
- [x] Verified with Playwright: `dashboard.spec.ts` route smoke checks and sidebar IA checks on port `8085`

### 2026-04-07 Phase 2 activity alignment

- [x] Updated Activity-facing copy so the main route, commit-detail back path, and commit-detail header all use `Activity`
- [x] Replaced the misleading split-pane CTA copy from `Open in editor` to `Open full detail`
- [x] Added `Cumulative Net` as an Activity metric option without widening the backend analytics contract
- [x] Implemented `Cumulative Net` as a frontend cumulative transform over `net_lines` series returned by the existing analytics endpoint
- [x] Kept Activity query syncing route-safe while preserving the existing chart, drilldown, annotation, and commit-detail flows
- [x] Verified with `npm run build`
- [x] Verified with Playwright on port `8085`: route smoke, sidebar IA, and Activity cumulative-net plus commit-detail navigation

### 2026-04-07 Phase 3 contributors

- [x] Replaced the Contributors placeholder with a real fixed surface backed by the existing V2 query executor over `throughput_daily`
- [x] Added a dedicated contributors store with metric switching for `net_lines`, `lines_added`, `commits_count`, and `files_changed_count`
- [x] Built a ranked contributor leaderboard, summary cards, and a top-contributors trend panel without widening the backend contract
- [x] Extended the shared top chrome so Contributors uses the same time range, repo, and extra-filter controls as Activity
- [x] Kept explicit empty-state handling when the selected range or filters produce no contributor activity
- [x] Verified with `npm run build`
- [x] Verified with Playwright on port `8085`: route smoke, sidebar IA, Activity regression, and Contributors metric switching with shared chrome filters

### 2026-04-07 Phase 4 codebase shell

- [x] Added a repo-scoped Codebase route at `/codebase/:repoId` while keeping `/codebase` as the stable entry path
- [x] Converted the sidebar Codebase item into a collapsible repo group driven by enabled repositories from bootstrap data
- [x] Added visible repo child entries with active-state highlighting and repo-specific navigation targets
- [x] Replaced the Codebase placeholder with a repo-scoped shell that keeps the page title in the app header instead of duplicating it inside the content body
- [x] Kept repo selection out of the page chrome so later Codebase phases can use the standard top-header pattern for title and filters
- [x] Verified with `npm run build`
- [x] Verified with Playwright on port `8085`: route smoke, sidebar IA, Activity regression, Contributors regression, and Codebase repo-child navigation

### 2026-04-07 Phase 5 codebase queries and filters

- [x] Added a dedicated `codebase` store that queries `repo_state_daily` for actual LOC snapshots and `throughput_daily` for net activity without widening the backend contract
- [x] Implemented the growth chart as a dual-series view with repo-wide snapshot LOC and frontend cumulative net activity over the selected range
- [x] Extended the shared top chrome so Codebase uses the standard date and extra-filter controls while keeping repo selection in the sidebar
- [x] Wired author, language, category, and product filters into the Codebase activity query path and surfaced active filter labels in the page summary
- [x] Made filter semantics explicit in the Codebase UI: date affects both series, extra filters affect cumulative activity only, and structural filtering is deferred to Phase 6
- [x] Verified with `npm run build`
- [x] Verified with Playwright on port `8085`: route smoke, sidebar IA, Activity regression, Contributors regression, and Codebase growth plus shared-header filters

### 2026-04-07 Phase 6 codebase treemap

- [x] Added a dedicated backend Codebase structure endpoint that aggregates current inventory into a stable path tree and joins ranged `commit_file_fact` net activity without changing the sync pipeline
- [x] Implemented an ECharts treemap for the repo-scoped Codebase page with `LOC`, `Files`, and `Net activity` size modes over the same underlying current tree
- [x] Made Net activity sizing explicit as absolute tile area with signed gain/loss preserved in labels and tooltips
- [x] Added supporting Codebase panels for language composition, category composition, top directories, and snapshot summary metadata
- [x] Updated Codebase filter semantics so language/category/product narrow current structure while author remains activity-only
- [x] Verified with `npm run build`
- [x] Verified with Playwright on port `8085`: route smoke, Activity regression, Contributors regression, and Codebase treemap plus size-mode switching

### 2026-04-07 browser validation follow-up

- [x] Re-validated the implemented phases in a live browser session against the running dev app on `http://127.0.0.1:4173`
- [x] Tightened Contributors page composition so the intro area, metric controls, and leader card align with the shared app shell and Codebase card language
- [x] Fixed Codebase growth summary so `Latest LOC snapshot` uses the latest actual snapshot row instead of showing `0` for ranges with missing end-day snapshots
- [x] Fixed sparse-snapshot growth rendering so `Actual LOC` shows a visible marker when the selected range contains only a few snapshot points
- [x] Re-verified Activity metric switching, `Cumulative Net`, and `Open full detail` navigation in-browser
- [x] Re-verified the Settings IA move so `Sync` is accessible under `/settings/sync`
