# Activity, Contributors, and Codebase Implementation Plan

Implementation plan for the feature slice defined in [2026-04-07-activity-contributors-codebase-design.md](2026-04-07-activity-contributors-codebase-design.md).

Use [2026-04-07-activity-contributors-codebase-progress.md](2026-04-07-activity-contributors-codebase-progress.md) to track execution continuously.

---

## Goal

Ship the new navigation and page model for:

- `Activity`
- `Codebase`
- `Contributors`
- `Widget Catalog`
- `Pages` as the user-created widget section

While preserving the current working behavior of the existing Explore flow and staying inside the current data-model boundaries.

---

## Scope

### In scope

- rename `Explore` to `Activity`
- move `Sync` under `Settings`
- revise sidebar navigation order and grouping
- add a top-level `Contributors` page
- add a top-level `Codebase` area with repo child pages
- build Codebase growth, filter, and treemap views
- make `ECharts` the treemap implementation path
- add a dedicated feature progress tracker

### Out of scope

- composite contributor activity scoring
- multi-repo treemap support
- historical arbitrary-date tree rendering
- sync-engine redesign
- replacing the generic widget-page examples

---

## Assumptions

- Existing Activity behavior should remain the baseline and must not regress.
- The current throughput and repo-state datasets remain semantically separate.
- `ECharts` is already available and should be extended rather than bypassed.
- The current widget-based page infrastructure remains intact; this slice adds fixed product surfaces alongside it.

---

## Phase 0: baseline and route audit

### Goal

Confirm the current navigation, routes, store boundaries, and screen ownership before restructuring.

### Tasks

- inventory current sidebar rendering and menu item sources
- inventory current Explore routes and dependent links
- identify where Sync currently enters the main navigation
- identify which screens are fixed product surfaces versus user-created pages
- confirm current ECharts setup and extension points for treemap support

### Exit criteria

- route and sidebar ownership is documented in code comments or notes
- no ambiguity remains about fixed pages versus user pages
- treemap implementation path through ECharts is confirmed

---

## Phase 1: navigation and information architecture

### Goal

Reshape the shell so the app navigation matches the approved IA.

### Tasks

- rename sidebar label and route copy from `Explore` to `Activity`
- reorder top-level menu items to:
  - `Activity`
  - `Codebase`
  - `Contributors`
  - `Widget Catalog`
- move `Sync` into `Settings`
- add a separate `Pages` section for user-created widget pages
- ensure fixed product surfaces and user pages are visually distinct
- preserve navigation state and active-item styling

### Exit criteria

- main menu order matches the spec
- Sync is no longer visible as a first-level app menu item
- user-created pages appear only in the `Pages` section
- no broken routes remain from the Explore rename

---

## Phase 2: Activity surface alignment

### Goal

Preserve the existing Explore experience while renaming it to Activity and keeping its semantics tight.

### Tasks

- rename route names, titles, labels, and breadcrumbs to `Activity`
- keep the existing chart, filter, drilldown, annotation, and commit-detail flows working
- verify `cumulative net` remains available as a chart mode if already implemented, or expose it if hidden
- verify Activity still answers change-over-time questions only

### Exit criteria

- current Explore behavior works under the Activity name
- drilldown and commit detail still function end to end
- no Codebase or contributor-ranking concepts leak into Activity beyond existing filters

---

## Phase 3: Contributors page

### Goal

Add a dedicated global contributor surface for ranking and comparison.

### Tasks

- create a fixed `Contributors` page route and shell
- implement metric switcher for:
  - `net_lines`
  - `lines_added`
  - `commits_count`
  - `files_changed_count`
- build leaderboard query path and frontend table rendering
- add lightweight contributor summary cards
- add at least one small comparison/trend visualization
- share time range and filters with the rest of the page chrome

### Exit criteria

- Contributors loads independently of Activity
- ranking order changes correctly with metric selection
- page works across all selected repositories
- empty states are explicit and stable

---

## Phase 4: Codebase page shell and repo navigation

### Goal

Introduce the Codebase navigation area and repo child pages.

### Tasks

- add top-level `Codebase` item with expandable repo children
- generate one repo child page entry per enabled repo
- remove repo selection from the Codebase page header
- ensure Codebase pages use the standard top header pattern:
  - title in header
  - filters in header
  - no duplicate internal title block

### Exit criteria

- Codebase repo pages are discoverable through sidebar children
- selecting a repo child opens the correct page
- Codebase uses the app’s consistent header chrome

---

## Phase 5: Codebase queries and filters

### Goal

Support the data needed for Codebase growth and filtering behavior.

### Tasks

- add repository-specific growth query returning:
  - actual LOC over time from snapshots
  - cumulative net lines from activity facts
- add Codebase filter model in the top header bar
- support filters such as:
  - category
  - author
  - language
  - compatible existing dimensions
- define which filters affect:
  - current structure
  - activity-sized modes
  - both
- document and enforce that behavior consistently

### Exit criteria

- Codebase queries return stable results for the chosen repo
- header filters constrain the page correctly
- filter semantics are explicit in code and UI

---

## Phase 6: Codebase treemap and breakdowns

### Goal

Ship the current-state-first structural view on Codebase.

### Tasks

- implement treemap with `ECharts`
- use current structure as the tree source
- implement size modes:
  - `LOC`
  - `Files`
  - `Net activity in selected range`
- keep the current tree stable while size mode changes
- add supporting breakdown cards or tables for:
  - languages
  - categories
  - snapshot summary

### Exit criteria

- treemap renders with ECharts
- size-mode switching works
- current structure and range-based sizing are not conflated
- supporting breakdowns match the selected repo

---

## Phase 7: QA and regression

### Goal

Verify that the new IA and feature pages ship without breaking existing flows.

### Tasks

- backend tests for contributor ranking modes
- backend tests for Codebase growth query
- backend tests for treemap aggregation and filter semantics
- frontend tests for new sidebar order and Pages grouping
- frontend tests for Codebase repo child navigation
- frontend tests for Codebase header filters
- frontend tests for Contributors metric switching
- regression tests for Activity day drilldown and commit detail

### Exit criteria

- targeted backend and frontend tests pass
- manual verification passes for Activity, Contributors, and Codebase
- no navigation regressions remain

---

## Recommended execution order

1. Phase 0
2. Phase 1
3. Phase 2
4. Phase 3
5. Phase 4
6. Phase 5
7. Phase 6
8. Phase 7

Do not start the treemap implementation before the navigation and Codebase repo-page shell are stable.

---

## Verification commands

Backend:

```bash
cd backend && ./gradlew test
```

Frontend build:

```bash
cd frontend && npm run build
```

Frontend end-to-end:

```bash
cd frontend && npm run test:e2e
```

Packaged app smoke when needed:

```bash
./scripts/build-mac-app.sh
```

---

## Deliverables

- updated fixed-page IA in the app shell
- Activity rename with no regression
- Contributors page
- Codebase repo pages
- ECharts treemap implementation
- feature-specific progress tracker

---

## Notes

- Keep the generic widget-page examples unchanged.
- Use the dedicated progress file for this slice rather than overloading the broad V2 tracker.
- Record deviations or scope shifts in commit messages and, if needed, in the progress file notes section.
