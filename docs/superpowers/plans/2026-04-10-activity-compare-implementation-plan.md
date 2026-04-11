# Activity Compare Implementation Plan

## Inputs

- Approved design spec: `docs/superpowers/specs/2026-04-10-activity-compare-design.md`
- Current branch: `feature/v2`
- Constraint: the worktree already contains unrelated edits in v2 frontend and backend files, so the plan must minimize overlap and stage risky renames carefully

## Outcome

Ship compare support on `/activity` with:

- backend-first v2 compare contract
- aligned reference overlays
- compare modes for `previous_period`, `previous_year`, `custom_anchor_date`, and `custom_range`
- independent compare failure handling
- URL-restorable compare state
- active frontend naming cleanup from `explorer` to `activity`

## Delivery Strategy

Implement in five phases:

1. Introduce the new v2 backend compare contract and validation.
2. Add frontend compare state and API wiring without renames.
3. Render the compare UI and aligned overlays on the activity page.
4. Perform the active frontend naming cleanup from `explorer` to `activity`.
5. Add test coverage and regression verification.

This order keeps the feature shippable before the rename pass and reduces the chance that wide renames obscure functional regressions.

## Phase 1: Backend Compare Contract

### Goals

- create a dedicated v2 compare endpoint
- keep legacy compare code untouched
- centralize reference-range derivation and alignment on the server

### Files

- `backend/src/main/kotlin/com/company/throughput/v2/web/V2Controllers.kt`
- `backend/src/main/kotlin/com/company/throughput/v2/service/...` activity-related service file or new focused service
- `backend/src/main/kotlin/com/company/throughput/v2/model/...` request and response models
- related analytics helper code if internal reuse is needed

### Tasks

1. Add request and response models for `POST /api/v2/activity/compare`.
2. Define compare mode enum values:
   - `PREVIOUS_PERIOD`
   - `PREVIOUS_YEAR`
   - `CUSTOM_ANCHOR_DATE`
   - `CUSTOM_RANGE`
3. Implement inclusive current-range day-count calculation.
4. Implement reference-range derivation rules:
   - previous period uses the immediately preceding same-length span
   - previous year shifts the same calendar span back one year
   - custom anchor date derives a same-length range ending on the chosen date
   - custom range validates explicit `from` and `to`
5. Implement validation:
   - custom anchor date required when mode is `CUSTOM_ANCHOR_DATE`
   - derived anchor-date range must end before current range starts
   - custom range `from` and `to` required when mode is `CUSTOM_RANGE`
   - custom range must end before current range starts
   - custom range must have the same inclusive day count as current range
6. Query current and reference series with the same metric, grouping, and filters.
7. Produce `alignedReference` series by shifting reference points onto the current period by offset.
8. Return current totals, reference totals, and delta for the selected metric.

### Backend Notes

- Reuse existing analytics query primitives internally where possible, but do not expose the legacy `/api/analytics/compare` contract to the v2 frontend.
- Keep alignment logic server-side so custom-range validation and overlay data are derived from one source of truth.
- Match grouped series by key and omit missing reference groups rather than fabricating empty series.

### Done Criteria

- `/api/v2/activity/compare` returns a complete payload for all supported modes
- invalid compare requests return explicit validation errors
- aligned reference output is ready for direct chart rendering

## Phase 2: Frontend Compare State And API Wiring

### Goals

- integrate compare data into the current activity store without yet renaming files
- keep compare loading isolated from main analytics loading

### Files

- `frontend/src/api/workspace.ts` or new `frontend/src/api/activity.ts`
- `frontend/src/stores/explorer.ts`
- `frontend/src/types/...` for v2 compare types if needed

### Tasks

1. Add a dedicated frontend client call for `/api/v2/activity/compare`.
2. Add compare request and response TypeScript types matching the new v2 contract.
3. Extend the current activity store with compare state:
   - mode
   - custom anchor date
   - custom range
   - overlay visibility
   - compare payload
   - compare loading
   - compare error
4. Trigger compare refresh from the same inputs that affect analytics:
   - date range
   - metric
   - group by
   - repo, author, language, category, and product filters
   - compare mode and custom compare inputs
5. Keep compare requests independent from the main analytics request so compare failures do not clear current-series data.
6. Normalize compare-off behavior so no request runs when compare is disabled.

### State Model

Suggested internal fields:

- `compareMode`
- `compareAnchorDate`
- `compareReferenceRange`
- `compareOverlayVisible`
- `compareData`
- `loadingCompare`
- `compareError`

### Done Criteria

- compare payloads are fetched and stored independently
- current analytics remain intact when compare fails
- compare-off state is explicit and stable

## Phase 3: Activity UI And Overlay Rendering

### Goals

- expose compare controls in the shared activity toolbar
- render aligned reference overlays and compare summary metadata

### Files

- `frontend/src/App.vue`
- `frontend/src/views/ExplorerView.vue`

### Tasks

1. Add a `Compare` control to the shared activity toolbar in `App.vue`.
2. Support menu options:
   - `Off`
   - `Previous period`
   - `Previous year`
   - `Custom past date`
   - `Custom date range`
3. Render mode-specific inputs:
   - anchor-date input for `Custom past date`
   - `from` and `to` inputs for `Custom date range`
4. Disable compare apply actions for invalid custom inputs before submission.
5. Show helper text describing the derived same-length reference range.
6. In the activity chart view, merge current series with `alignedReference` overlay series.
7. Render reference overlays with:
   - same base color as current series
   - dashed stroke
   - lower visual emphasis
8. Show current range, reference range, and metric delta summary when compare is enabled.
9. Show compare-specific inline errors without replacing the main chart.

### Rendering Notes

- For grouped charts, align overlays by series key.
- Omit overlay series with no matching reference key.
- Keep selected-day interactions and drilldown behavior attached to the primary current-period series.
- Preserve existing cumulative-net behavior for the primary series; confirm whether compare overlays use net-series alignment before any cumulative transformation.

### Done Criteria

- compare controls are usable from `/activity`
- overlays appear and disappear correctly
- compare summary and error states behave independently from main chart loading

## Phase 4: Frontend Naming Cleanup

### Goals

- remove active frontend `explorer` naming from the activity surface
- avoid unnecessary churn in stable backend/storage identifiers

### Files

- `frontend/src/stores/explorer.ts`
- `frontend/src/views/ExplorerView.vue`
- `frontend/src/views/ExplorerCommitDetailView.vue`
- `frontend/src/components/ExplorerAnnotationModal.vue`
- `frontend/src/router/index.ts`
- any direct imports in `App.vue`, `CodebaseView.vue`, `ContributorsView.vue`, and related modules

### Tasks

1. Rename the active store to `useActivityStore`.
2. Rename view/component files to activity naming where they are part of the current user-facing surface.
3. Update import paths and symbol names across the frontend.
4. Preserve route names `activity` and `activity-commit`.
5. Keep temporary aliases only where needed to avoid breaking unrelated modules during the transition.
6. Avoid renaming backend endpoints or migration-backed identifiers unless required for correctness.

### Rename Strategy

- Complete functional compare work first.
- Perform renames in one narrow pass.
- Immediately run TypeScript and test verification after the rename pass to catch missed imports.

### Done Criteria

- active frontend activity code no longer exposes legacy `explorer` names
- route behavior and imports remain stable

## Phase 5: Tests And Verification

### Backend tests

Files likely include:

- `backend/src/test/kotlin/com/company/throughput/web/V2ContractsTests.kt`
- dedicated service tests for compare derivation and alignment

Add coverage for:

- previous period derivation
- previous year derivation
- custom anchor date derivation
- custom range validation failures
- aligned-series date shifting
- grouped-series matching and omission behavior

### Frontend tests

Add or extend coverage for:

- compare state serialization into URL params
- restore-from-URL behavior
- compare load triggers
- compare failures leaving main analytics intact

Target files may include:

- store unit tests if present or newly added
- `frontend/tests/e2e/dashboard.spec.ts`

### End-to-end scenarios

Cover:

- enabling compare on `/activity`
- previous period overlay visible
- previous year overlay visible
- custom anchor date compare
- custom range compare
- invalid custom range disabled or rejected with clear feedback
- compare params restoring the state on page reload

### Manual verification

Run:

- frontend test suite relevant to activity
- backend tests for v2 compare
- local app flow on `/activity`

Manual checks:

- overlay alignment on grouped and ungrouped views
- compare summary accuracy
- compare error isolation
- selected-day and commit-detail interactions still work

## Recommended Execution Order

1. Build backend compare models, service logic, and controller.
2. Add frontend compare API and store state.
3. Wire toolbar controls and activity chart overlays.
4. Add URL serialization and restore logic.
5. Run targeted tests for functional compare behavior.
6. Perform the activity naming cleanup.
7. Re-run tests and smoke-check `/activity` and `/activity/commit/...`.

## Risks And Mitigations

### Unrelated worktree edits

Risk:

- current branch already has unrelated edits in `App.vue`, `CodebaseView.vue`, backend service code, and tests

Mitigation:

- read affected files before patching
- keep compare work narrowly scoped
- avoid reverting or reformatting unrelated areas

### Rename churn

Risk:

- activity naming cleanup can obscure logic regressions

Mitigation:

- complete functional compare changes before renames
- keep renames in a dedicated pass
- verify imports and route wiring immediately after

### Grouped overlay noise

Risk:

- grouped compare overlays may become visually dense

Mitigation:

- keep reference lines dashed and lower-emphasis
- preserve legend control behavior
- omit unmatched reference series

## Shipping Checklist

- new v2 compare endpoint implemented
- compare validation explicit and tested
- compare state serializes into `/activity` URLs
- aligned overlays render correctly
- compare errors degrade independently
- active frontend naming switched from `explorer` to `activity`
- backend, frontend, and e2e coverage updated
- manual smoke test on `/activity` and `/activity/commit/...` passes
