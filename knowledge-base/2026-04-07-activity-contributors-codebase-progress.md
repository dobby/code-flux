# Activity, Contributors, and Codebase Progress

Update this file continuously. Do not wait until the end.

Related plan: [2026-04-07-activity-contributors-codebase-implementation-plan.md](2026-04-07-activity-contributors-codebase-implementation-plan.md)
Related design: [2026-04-07-activity-contributors-codebase-design.md](2026-04-07-activity-contributors-codebase-design.md)

## Status legend

- `[ ]` not started
- `[-]` in progress
- `[x]` done and verified

## Phase 0: baseline and route audit

- [ ] Inventory current sidebar ownership
- [ ] Inventory current Explore routes and links
- [ ] Confirm current Sync navigation entry points
- [ ] Confirm fixed surfaces vs user-created Pages section
- [ ] Confirm ECharts treemap implementation path

## Phase 1: navigation and information architecture

- [ ] Rename Explore navigation label to Activity
- [ ] Reorder top-level menu items
- [ ] Move Sync under Settings
- [ ] Create distinct Pages section for user-created pages
- [ ] Verify no broken navigation routes remain

## Phase 2: Activity surface alignment

- [ ] Rename Explore route and page copy to Activity
- [ ] Verify existing chart behavior still works
- [ ] Verify drilldown still works
- [ ] Verify commit-detail flow still works
- [ ] Verify annotations still work
- [ ] Verify cumulative-net mode is exposed correctly

## Phase 3: Contributors page

- [ ] Add Contributors route and shell
- [ ] Implement leaderboard query path
- [ ] Implement metric switcher
- [ ] Implement summary cards
- [ ] Implement comparison/trend visualization
- [ ] Verify empty states

## Phase 4: Codebase page shell and repo navigation

- [ ] Add top-level Codebase item
- [ ] Add repo child entries
- [ ] Remove Codebase repo selector from header
- [ ] Keep Codebase title in top header only
- [ ] Keep Codebase filters in top header only

## Phase 5: Codebase queries and filters

- [ ] Implement growth query for actual LOC and cumulative net
- [ ] Implement Codebase header filter model
- [ ] Support category filter
- [ ] Support author filter
- [ ] Support language filter
- [ ] Verify filter semantics for structure vs activity sizing

## Phase 6: Codebase treemap and breakdowns

- [ ] Implement ECharts treemap
- [ ] Implement LOC size mode
- [ ] Implement Files size mode
- [ ] Implement Net activity size mode
- [ ] Implement supporting language breakdown
- [ ] Implement supporting category breakdown
- [ ] Implement snapshot summary block

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
