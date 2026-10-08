# Completed-run UI matching pass

No Gradle build, unit test, connected test, device command, installation, or screenshot verification was performed. All manual checks below are pending. The reference image was inspected directly; final viewport fitting and visual fidelity require the user's device screenshots.

## 1. Files changed

- `app/src/main/java/com/example/openruncoach/ui/health/RunDetailScreen.kt`
- `app/src/main/java/com/example/openruncoach/ui/health/RunDetailViewModel.kt`
- `app/src/main/java/com/example/openruncoach/ui/health/RunCharts.kt`
- `app/src/main/java/com/example/openruncoach/ui/components/CompletedRunMetrics.kt`
- `app/src/main/java/com/example/openruncoach/data/WorkoutExecutionRepository.kt`
- `app/src/androidTest/java/com/example/openruncoach/data/WorkoutRunAssociationRepositoryTest.kt`
- `docs/OpenRunCoach_UI_RULEBOOK.md`
- `README.md`
- `docs/completed-run-ui-matching-verification.md`

## 2. Missing Easy hero: traced code-path cause

The asset and central mapper were correct: `EASY_RUN` already maps to `hero_easy.webp`, and the header already reads `state.plannedWorkout?.type?.heroImageResource()` without remembering the first null value. The missing link was the detail metadata's observation lifecycle. `refreshAssociation(run)` performed a one-shot `assignedWorkout` read only when the run stream emitted. It did not observe `workout_run_associations` or subsequent prescription changes. A separate execution collector also wrote `plannedWorkout`; executions are absent before checkout, and its fallback retained old metadata. A late null association read could overwrite metadata loaded by the other collector. There was no initial association-loading gate. This allowed a neutral or stale header even though History reads authoritative association metadata separately. This is a source-level diagnosis; the user's specific physical-run database was not inspected or reproduced on-device in this task.

## 3. Hero selection fix

One read-only repository Flow observes the existing physical-run association, then switches to the associated persisted prescription's existing Room Flow. Run Detail switches subscriptions only when the physical identity changes. This single stream owns `plannedWorkout`; execution updates and action completion no longer compete to set it. Assignment, reassignment, prescription edits and unassignment now propagate without requiring run-record changes or checkout. Initial association loading finishes before the header renders, avoiding the first neutral-to-image flash. The unchanged central mapper selects all existing heroes; unassociated runs remain neutral and no classification is inferred. The header is shallow, with a restrained gradient and readable title/date. No writes, matching rules, schema or new assets were added.

## 4. Overview density

The shallow header, shorter icon/value/label strip, viewport budget and bounded map/plot heights reserve room for the full primary Overview. Text-measured minimum metric widths replace the old 80 dp-per-column minimum, which caused needless horizontal scrolling at common phone widths. Font scaling and vertical scrolling remain enabled. Route presentation alone receives a shorter height; the map implementation is untouched. Actual one-viewport success is pending manual verification.

## 5. Metric pill

One dark rounded container holds equal-width Pace / Heart Rate / Cadence segments. Only the selected segment receives a quiet mint outline/background; each touch target remains at least 48 dp. Options require drawable real series. A single available series renders directly without a one-item selector. Primary screen tabs retain their existing text/underline style.

## 6. Chart size and style

Default plots use approximately 88–116 dp, with compact padding, muted axes and a lighter three-line grid. Existing sample preparation, elapsed axes, gaps, thin line, subtle area fill, robust pace domain and legitimate target-band extension are unchanged. All metrics use the same plot and summary footprint. Technical clipping text and Fastest 30 sec were already secondary under Data sources → Recording diagnostics and remain there.

## 7. Chart summary

Equal visual columns put values above labels. Canonical prepared average pace stays off-white; a real target stays restrained mint. The parallel-bar range key moves beside its label so it no longer forces an otherwise unnecessary horizontal scroll beside the long pace value. HR retains genuine average/max. Cadence retains the existing moving-average preference, explicitly labelled Avg. moving cadence when used, plus genuine maximum; no measurement semantics change. An unavailable second measurement remains absent while its space is reserved.

## 8. Splits table

Shared stable numeric widths, tabular numerals and right alignment for Pace / Δ / HR are retained; Km stays left aligned. Rows are tighter, with thin separators. Full kilometres remain ordinals and partial splits remain fractions. The existing production delta map supplies every delta without UI recalculation. Negative/faster is mint; positive/slower stays neutral. HR is omitted when absent throughout. Primary row formatting is remembered by splits, deltas and locale. The canonical average and existing equal-distance-half result now sit immediately below the table, ahead of the muted provenance caption. Half-run and split details remain collapsed initially.

## 9. Redundant content

Fastest/Slowest full-split ranking cards were already absent and remain absent; this pass does not claim to remove cards that were not present. The main Split summary contains only canonical average pace and the available existing half-run result. Duration/cadence remain secondary. No replacement rankings were introduced.

## 10. Analysis

One compact Outcome / Session cost / Volume strip uses friendly existing labels and omits semantically unknown volume. The normal-width comparison no longer requires a 352 dp table inside a narrower content area. Distance, pace and checkout RPE are prioritized, actual values are slightly stronger, and duration remains for time-based prescriptions. Quality-work targets stay distinct from whole-run average pace. Continuity is a concise status/detail block; confidence is a row. Compact Planned workout title/distance and Change association / Unassign actions follow immediately. Extra prescription and pace-adherence facts move behind Evidence without changing their values.

## 11. Evidence

Evidence uses the existing disclosure, collapsed initially, with every original factual response reason retained. No interpretation is regenerated. Half-run details, Split details and Recording diagnostics also retain their secondary placement.

## 12. Intentionally unchanged and verification limits

Canonical distance/duration/pace, providers, matching, enrichment, source preferences, metrics, routes, calibration, split derivation/deltas/halves, evidence eligibility, feedback ownership, relevance, continuity, WorkoutResponse, AthleteState, PerformanceProfile, GoalStrategy and plan generation are unchanged. Map rendering/provider/route fitting/fallback and Coach content are unchanged. The repository addition is only a read projection for detail state. Existing hero mapping tests remain; an unexecuted Room regression test was added for late assignment, prescription edits, reassignment and unassignment without checkout. No build, test or device pass is claimed. Screenshot requests below are for the user to provide.

## 13. Complete manual verification checklist

All items are pending and must be performed by the user. The requested checklist follows in full.

TEST 1 — BUILD

User runs:

./gradlew test
./gradlew assembleDebug

Expected:
both pass.

--------------------------------------------------

TEST 2 — EASY HERO

Open a completed run that is associated with Easy Run.

Expected:

- Easy hero image is visible
- not plain black
- title/date readable over gradient
- Overview/Splits/Analysis/Coach remain below hero

Return screenshot.

--------------------------------------------------

TEST 3 — LONG RUN HERO

Open known Long Run.

Expected:

Long Run hero.

This verifies mapping is not hardcoded only for Easy.

--------------------------------------------------

TEST 4 — OVERVIEW FULL VIEWPORT

Open a completed routed run at normal/default font size.

Expected one screenshot can show:

- compact metrics
- full map
- Pace/HR/Cadence pill
- full chart
- Average / Target summary

without manual vertical scrolling.

Return screenshot.

--------------------------------------------------

TEST 5 — PILL SELECTOR

Expected:

Pace | Heart Rate | Cadence

inside one compact rounded container.

It must NOT look like the primary top tabs.

Return screenshot.

--------------------------------------------------

TEST 6 — PACE CHART

Verify:

- genuine data
- compact height
- mint line
- subtle area
- target range
- no developer warning text
- minimal axes/grid

--------------------------------------------------

TEST 7 — PACE SUMMARY

Expected directly below chart:

Average pace
actual canonical pace

Target pace/range
planned target

Verify actual pace equals the canonical value shown elsewhere.

--------------------------------------------------

TEST 8 — HR

Select Heart Rate.

Expected:

same component height
real HR series
Average HR / Max HR

No layout jump.

--------------------------------------------------

TEST 9 — CADENCE

Select Cadence.

Expected:

same component height
real cadence
Average / Max cadence

No layout jump.

--------------------------------------------------

TEST 10 — SPLITS TABLE

Open a run with multiple splits.

Expected:

Km | Pace | Δ | HR

Columns remain visually aligned for every row.

No:

Fastest full split
Slowest full split

Return screenshot.

--------------------------------------------------

TEST 11 — 20 SEP DELTA

Open:

9de042f6-429b-3322-833e-aaa0a8cf9ec6

Expected canonical average approximately:

6:51 /km

Verify displayed Δ values use this same baseline.

The UI must not revert to Samsung's ~6:45 /km provider pace.

--------------------------------------------------

TEST 12 — SPLIT SUMMARY

Expected directly below table:

Average pace

and:

Positive split
Negative split
or Even split

side-by-side.

--------------------------------------------------

TEST 13 — HALF-RUN DETAILS

Expected collapsed initially.

Expand.

Verify first-half and second-half information remains available.

--------------------------------------------------

TEST 14 — ANALYSIS LONG RUN

Open the known aborted Long Run.

Expected top section quickly communicates:

Aborted
High session cost
Partial volume

Then compact:

Planned | Actual

comparison.

Return screenshot.

--------------------------------------------------

TEST 15 — ANALYSIS ACTUAL DATA

Known Long Run should retain approximately:

planned distance = 4.80 km
actual distance = 1.16 km
completion = Aborted
session cost = High
significant pain evidence
RPE = existing checkout value

No semantics changed.

--------------------------------------------------

TEST 16 — ANALYSIS EVIDENCE

Expected:

Evidence

collapsed by default.

Expand.

Existing structured evidence remains available.

--------------------------------------------------

TEST 17 — ASSOCIATION

Expected compact:

Planned workout
Long Run · 4.80 km

with:

Change association
Unassign

Both actions still work.

--------------------------------------------------

TEST 18 — COACH REGRESSION

Coach should remain concise.

Expected:

status/recommendation
2–4 observations
next training implication
More details

No prose wall returns.

--------------------------------------------------

TEST 19 — UNKNOWN RUN

Open a run with no known/associated workout type.

Expected:

no fake Easy/Long/etc hero.

Neutral fallback remains intentional.

--------------------------------------------------

TEST 20 — ACCESSIBILITY

Increase Android font size one step if practical.

Expected:

content remains usable.

Scrolling is acceptable here.

No clipped/overlapping values.
