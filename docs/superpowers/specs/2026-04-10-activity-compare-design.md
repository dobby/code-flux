# Activity Compare In V2

## Goal

Restore period comparison on the v2 activity page and expand it beyond the old main-branch behavior.

The restored feature should:

- use `activity` naming throughout the active v2 frontend instead of leaking legacy `explorer` terminology
- support aligned chart overlays for current vs reference periods
- support `previous period`, `previous year`, `custom past date`, and `custom date range`
- keep the main activity experience usable even when compare data fails independently
- expose compare state in the URL so the page is shareable and restorable

The comparison overlay should always be aligned onto the current range day-for-day. True historical date rendering is out of scope for this change.

## Scope

In scope:

- new v2 comparison API for the activity page
- frontend compare state and toolbar controls for `/activity`
- aligned overlay rendering in the activity chart
- summary metadata for current period, reference period, and delta
- validation for custom compare inputs
- renaming active v2 frontend references from `explorer` to `activity` where practical

Out of scope:

- preserving legacy `main` dashboard comparison UI
- adding a toggle between aligned overlay and true historical dates
- redesigning the activity information architecture beyond the compare controls and naming cleanup
- broad backend renames of migration-backed or stable storage identifiers unless required for correctness

## Product Decisions

### Comparison modes

The activity page supports these modes:

- `off`
- `previous_period`
- `previous_year`
- `custom_anchor_date`
- `custom_range`

#### `previous_period`

The reference range is the immediately preceding range with the same number of days as the current range.

#### `previous_year`

The reference range is the same calendar span shifted back one year.

#### `custom_anchor_date`

The user supplies a single past end date. The backend derives a reference range that ends on that date and spans the same number of days as the current range. The derived reference range must end before the current range starts.

#### `custom_range`

The user supplies `from` and `to`. The backend requires the reference range to:

- end before the current range starts
- have the same day count as the current range

### Overlay behavior

When compare is enabled, the chart renders:

- current series as the primary visual layer
- reference series as a dashed lower-emphasis overlay

Reference points are aligned to the current range by day offset. For example, day 1 of the reference period overlays day 1 of the current period.

This applies to:

- ungrouped charts
- grouped charts, where each reference series is matched by series key when available

Missing reference series are omitted instead of synthesized as zero-valued series.

## UX Design

### Toolbar controls

The shared activity toolbar gets a `Compare` control.

The compare menu exposes:

- `Off`
- `Previous period`
- `Previous year`
- `Custom past date`
- `Custom date range`

When `Custom past date` is selected, the menu shows:

- `Reference end date`
- helper copy describing the derived same-length range

When `Custom date range` is selected, the menu shows:

- `Reference from`
- `Reference to`
- helper copy explaining that the reference range must be fully before the current range and must match the current range length

### Summary presentation

The activity surface shows compare summary metadata when compare is enabled:

- current range label
- reference range label
- delta for the active metric

The delta summary should be derived from the backend response rather than recomputed in the view.

### Error handling

Compare failures must not block the main activity chart. The UI should:

- keep the current-period series visible
- suppress the compare overlay
- show a small inline compare-specific error state near the compare controls or summary region

### Naming

Active v2 frontend code should use `activity` naming in place of `explorer` naming where the symbol is part of the current product surface.

Examples:

- `useExplorerStore` becomes `useActivityStore`
- `ExplorerView.vue` becomes `ActivityView.vue`
- `ExplorerAnnotationModal.vue` becomes `ActivityAnnotationModal.vue` if it remains activity-specific
- toolbar labels, helper methods, and state variables should reference `activity`

Stable backend or storage names that are already contract-sensitive may remain unchanged temporarily, but any retained legacy names should be hidden behind activity-named frontend wrappers.

## Backend Design

### New API

Add a dedicated endpoint under the v2 activity surface:

- `POST /api/v2/activity/compare`

This endpoint should not reuse the legacy `/api/analytics/compare` contract directly in the frontend. v2 should have its own request and response types shaped for the activity page.

### Request shape

The request should include:

- current date range
- compare mode
- optional custom anchor date
- optional custom reference range
- metric
- group by
- filters equivalent to the activity page filters

Suggested request model:

```json
{
  "current": { "from": "2026-03-01", "to": "2026-03-14" },
  "mode": "custom_anchor_date",
  "customAnchorDate": "2026-02-10",
  "customRange": null,
  "metric": "commit_count",
  "groupBy": "author",
  "filters": {
    "repoIds": [],
    "authorIds": [],
    "languages": [],
    "categories": [],
    "productCodes": []
  }
}
```

### Response shape

The response should return:

- current range metadata
- reference range metadata
- current series
- raw reference series on their original dates
- aligned reference series shifted onto the current range
- totals and delta summary for the active metric

Suggested response model:

```json
{
  "current": {
    "from": "2026-03-01",
    "to": "2026-03-14",
    "totals": {}
  },
  "reference": {
    "from": "2026-02-15",
    "to": "2026-02-28",
    "totals": {}
  },
  "delta": {
    "absolute": 0,
    "percentage": null
  },
  "series": {
    "current": [],
    "reference": [],
    "alignedReference": []
  }
}
```

The aligned reference series should be produced by the backend so the frontend does not duplicate alignment logic or risk drifting from server validation rules.

### Backend derivation rules

#### Common helpers

The backend should derive the day count of the current range inclusively. That count drives `previous_period` and `custom_anchor_date`, and validates `custom_range`.

#### Validation

Validation rules:

- `custom_anchor_date` requires a non-null anchor date
- the derived anchor-date reference range must end before the current range starts
- `custom_range` requires both `from` and `to`
- custom reference range must end before the current range starts
- custom reference range must match the current range day count exactly

Invalid requests should return explicit validation messages suitable for direct UI display.

### Query strategy

The endpoint should query:

- current series for the requested metric and grouping
- reference series for the derived reference range with the same metric and grouping
- totals for each side

The implementation may reuse existing analytics query primitives internally, but the public v2 contract should remain activity-specific.

## Frontend Design

### Store

Rename the active store to `activity` naming and add compare state there.

Suggested state:

- `compareMode`
- `compareEnabled`
- `compareCustomAnchorDate`
- `compareCustomRange`
- `compareOverlayVisible`
- `compareResponse`
- `loadingCompare`
- `compareError`

The activity store should load compare data independently from the main chart load, but compare refresh should still be triggered from the same state changes:

- date range
- metric
- group by
- repo filters
- author filters
- language filters
- category filters
- product code filters
- compare mode or custom compare inputs

### Route and query params

`/activity` URLs should include compare state when compare is enabled.

Suggested params:

- `compareMode`
- `compareAnchor`
- `compareFrom`
- `compareTo`

No compare params should be emitted when compare is `off`.

Incoming URLs without compare params should continue to work with compare disabled.

### View rendering

The activity chart should render:

- current series from the normal analytics response
- aligned reference series from the compare response when compare is enabled and overlay is visible

Reference overlays should:

- use matching series colors
- use dashed stroke
- use lower emphasis than the primary line or area

For grouped views, series matching should be key-based.

## File-Level Changes

Expected primary files:

- `frontend/src/stores/explorer.ts` renamed to activity naming
- `frontend/src/views/ExplorerView.vue` renamed to activity naming
- `frontend/src/components/ExplorerAnnotationModal.vue` renamed if it remains activity-scoped
- `frontend/src/App.vue` updated for compare controls in the shared activity toolbar
- `frontend/src/router/index.ts` updated to reference renamed activity view symbols if needed
- `frontend/src/api/workspace.ts` or a new v2 activity API module updated with compare calls
- `backend/src/main/kotlin/com/company/throughput/v2/web/V2Controllers.kt`
- new or expanded v2 service code for activity comparison
- v2 contract tests and frontend e2e tests

Renames should be staged carefully to avoid coupling compare work with unrelated view logic changes already present in the working tree.

## Testing

### Backend

Add tests for:

- `previous_period` range derivation
- `previous_year` range derivation
- `custom_anchor_date` derived reference range
- `custom_range` validation failures
- aligned series day shifting
- grouped-series matching behavior

### Frontend store

Add tests for:

- compare query serialization
- restore-from-URL behavior
- compare refresh triggers
- compare failures not clearing the main activity data

### End-to-end

Add e2e coverage for:

- enabling `previous period`
- enabling `previous year`
- custom anchor date compare
- custom range compare
- invalid custom range input disabled in the UI
- overlay presence on `/activity`
- compare params restoring from URL

## Risks

- the repo already contains unrelated in-progress changes in v2 files, so rename and compare edits must avoid trampling user work
- active/frontend renames can widen the diff if done without a narrow plan
- grouped compare overlays can become visually noisy if the legend and stroke styling are not tuned carefully

## Recommendation

Implement this as a backend-first v2 activity compare feature with activity naming cleanup in the active frontend surface. Keep the legacy compare endpoint untouched and isolate any internal reuse behind new v2 activity-specific request and response models.
