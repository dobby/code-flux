# Activity, Contributors, and Codebase Design

Design decisions from the April 7, 2026 brainstorming session covering the evolution of the current Explore page into a clearer navigation model with separate Activity, Contributors, and Codebase surfaces.

---

## Summary

The current product mixes time-based code movement exploration with concepts that belong to repository structure and contributor ranking. The revised structure should split these concerns into three clear surfaces:

- `Activity` for code movement over time and day-level drilldown
- `Contributors` for leaderboard-style ranking and contributor stats
- `Codebase` for current repository structure, growth, and composition

This is primarily a navigation, query, and page-composition change. The existing sync model and current persisted datasets are sufficient for a first implementation.

---

## Goals

- Rename the current Explore page to `Activity`
- Keep time-based code movement and drilldown on `Activity`
- Add a top-level `Contributors` page focused on ranking and contributor stats
- Add a collapsible `Codebase` sidebar folder with one child page per repository
- Show both actual repository growth and cumulative net activity on Codebase pages
- Add a treemap-based structural view that is current-state first

## Non-goals

- Do not introduce a single composite "activity score" in v1
- Do not merge Activity and Codebase into one broad workspace page
- Do not support a multi-repo treemap in v1
- Do not add historical folder-tree snapshots for arbitrary past dates in v1
- Do not rewrite the sync engine or core ingestion model

---

## Information Architecture

### Top-level navigation

The user-facing navigation should become:

- `Activity`
- `Contributors`
- `Codebase` (collapsible folder)
- existing settings and other utility areas

### Activity

`Activity` is the renamed Explore page. It remains the place for:

- daily code movement over time
- added, removed, net, and cumulative-net chart views
- time range filtering
- existing repository, author, language, category, and product filtering
- click-through day drilldown
- commit and file inspection
- annotations

`Activity` answers: what changed, when, and by whom.

It should not become the primary place for current repository size or structural composition.

### Contributors

`Contributors` is a new top-level page, global across repositories, with shared time range and filters.

It exists to make ranking and contributor comparison a first-class feature rather than a side panel on Activity.

Core capabilities:

- leaderboard-style ranking
- contributor summary stats
- metric switcher for ranking mode
- lightweight comparison visuals

The ranking metric is user-selectable. The UI should not imply that one metric is the single correct answer.

Supported metric modes in v1:

- `net_lines`
- `lines_added`
- `commits_count`
- `files_changed_count`

No composite activity score in v1.

### Codebase

`Codebase` is a collapsible sidebar folder, visually similar to a file/folder navigation group.

Behavior:

- the folder can be expanded or collapsed
- each enabled repository appears as a child item
- selecting a child opens that repository's Codebase page
- repository selection is removed from the Codebase page toolbar

This structure intentionally matches the decision that Codebase views are single-repo only in v1.

---

## Page Design

### Activity page

The current Explore page already exposes core metric and chart controls. Those controls should be preserved and relabeled as needed rather than re-invented.

Required changes:

- rename routes, labels, menu copy, and surrounding UX language from `Explore` to `Activity`
- preserve existing chart and drilldown behavior
- ensure `cumulative net` is available as a clear chart mode if not already surfaced in a durable way

The page should continue to feel operational and timeline-driven.

### Contributors page

The Contributors page should feel lighter and more playful than Activity while staying grounded in real metrics.

Suggested modules:

- leaderboard table
- contributor summary strip or cards
- small comparison chart or trend panel

The leaderboard should always show which metric is active and should sort descending by that metric by default.

The page remains global across repositories. Repository filtering stays in the shared page controls rather than the sidebar.

### Codebase page

Each repository-specific Codebase page should be current-state first.

Recommended layout:

1. compact growth chart header
2. treemap as the primary visualization
3. supporting structural breakdowns below or beside the treemap

The compact growth chart header should show two series:

- actual LOC over time from snapshots
- cumulative net lines over time from activity facts

The chart is supportive context, not the page's main identity.

If growth analysis later expands materially, it can move into its own page or subpage without changing the v1 data model.

---

## Treemap Design

### Structural rule

The treemap is current-state first.

That means:

- the hierarchy comes from the current repository structure
- tiles represent the current folder/file tree
- time-range-dependent metrics may affect tile size, but not the underlying tree

This distinction must be clear in the UI.

### Repository scope

The treemap is single-repo only in v1.

This avoids:

- multi-repo hierarchy ambiguity
- merged virtual trees
- a repo group layer at the top of the treemap

### Size modes

Initial size modes:

- `LOC`
- `Files`
- `Net activity in selected range`

Definitions:

- `LOC` sizes nodes by current line count
- `Files` sizes nodes by current file count
- `Net activity in selected range` sizes nodes by net lines changed within the active time range while still rendering the current tree

### Supporting breakdowns

Supporting Codebase breakdowns should include:

- category summary
- language summary
- top directories or top structural buckets

These should reinforce the treemap, not compete with it.

---

## Data Model and Query Semantics

### Existing semantic boundary

The existing model already separates:

- throughput facts
- repository state snapshots
- current file inventory
- drilldown detail

That boundary should be preserved.

### Activity dataset usage

Use current throughput facts for:

- lines added, removed, and net over time
- cumulative net lines
- contributor rankings and summaries
- day drilldown

Relevant existing sources:

- `daily_fact`
- `commit_fact`
- `commit_file_fact`

### Codebase dataset usage

Use repository-state and current-inventory data for:

- actual LOC over time
- file-count growth over time where needed
- current structural treemap
- current language/category/subtype composition

Relevant existing sources:

- `repo_state_snapshots`
- `repo_state_snapshot_breakdowns`
- `file_inventory_current`

### Key semantic rule

The UI must not imply that:

- cumulative net lines
- and actual snapshot LOC

are the same thing.

They are distinct series with different meaning and can diverge because of:

- snapshot cadence
- excluded paths
- classification changes
- binary/generated handling
- historical rebuild differences

### V1 viability assessment

The requested v1 feature set is supported by the current sync model.

What is needed:

- new or extended query endpoints
- page-specific frontend composition
- navigation changes
- treemap aggregation logic over current inventory and time-range activity

What is not needed:

- new core sync primitives
- historical folder snapshot persistence
- a wholesale data-model redesign

---

## Backend Implications

### Activity

Backend work should remain incremental:

- preserve existing activity query behavior
- add or clarify support for cumulative-net series output where necessary
- keep current day drilldown intact

### Contributors

Contributors needs query support for:

- ranking contributors by selected metric
- contributor summary cards or aggregates
- shared filter and time-range handling consistent with Activity

This can be built on top of current throughput facts without a new sync pipeline.

### Codebase

Codebase needs backend support for:

- a repository-specific growth query returning actual LOC and cumulative net series
- current-tree treemap aggregation from `file_inventory_current`
- path-prefix aggregation of `commit_file_fact` inside a time range for the `Net activity` size mode

The important limitation is acceptable in v1:

- no "show me the exact historical tree at an arbitrary past date"

That feature would require richer persisted structural history and is intentionally deferred.

---

## Frontend Implications

### Navigation

Required navigation changes:

- rename `Explore` to `Activity`
- add top-level `Contributors`
- add collapsible `Codebase` folder
- add one repository child entry under Codebase per enabled repo

### Toolbar behavior

Activity:

- preserve existing shared activity filters and time range controls

Contributors:

- use shared time range and filters
- keep repository filtering in controls, not in the sidebar

Codebase:

- remove repository selection from the toolbar
- preserve time range if needed for growth chart and activity-sized treemap modes
- expose the treemap size-mode switcher in the page header or local control bar

### Empty states

Codebase empty states must be explicit:

- no snapshots yet
- snapshot exists but structural data is sparse
- selected time range has no activity for the `Net activity` mode

Show all configured repos in the Codebase folder even when a repo has no snapshot yet. Let the page explain the missing data rather than hiding the repo and making the IA unstable.

---

## Edge Cases

- Repositories with no snapshots should render a clear "build snapshots" empty state.
- Repositories with mostly unclassified files should still render using `unknown` buckets.
- Cumulative net can diverge materially from actual LOC; this is expected and should be presented as such.
- Contributor leaderboards can produce very different winners depending on metric; this is a feature, not a bug, and the selected metric should stay explicit.
- Codebase pages should remain single-repo even if the rest of the app supports multi-repo filters elsewhere.

---

## Testing

### Backend

- cumulative growth query coverage
- contributor ranking by each supported metric
- treemap aggregation by current LOC, file count, and range net activity
- Codebase repo scoping rules

### Frontend

- renamed Activity navigation and route behavior
- Contributors metric switching and sorting
- collapsible Codebase folder interactions
- repository child page selection
- Codebase treemap mode switching
- empty-state rendering for missing snapshots and empty activity ranges

### Regression

- existing Activity day drilldown
- commit detail flow
- annotation behavior on renamed Activity surface

---

## Recommended Implementation Shape

Implement this as a focused product evolution rather than a platform rewrite:

1. rename Explore to Activity and preserve existing behavior
2. add Contributors on top of existing throughput queries
3. add Codebase repo child pages backed by existing snapshot and inventory facts
4. add the new query shapes required for growth and treemap sizing

This keeps the rollout aligned with the current system boundaries and avoids speculative infrastructure work.

---

## Acceptance Criteria

- The sidebar shows `Activity`, `Contributors`, and a collapsible `Codebase` folder.
- The current Explore page is fully renamed to `Activity` without breaking drilldown behavior.
- Contributors is a separate page with metric-switched leaderboard behavior.
- Codebase has one child page per repository and no repo selector in its toolbar.
- Codebase pages show both actual LOC growth and cumulative net growth.
- Codebase treemap supports `LOC`, `Files`, and `Net activity in selected range`.
- The implementation ships without a sync-engine rewrite or new historical tree snapshot model.
