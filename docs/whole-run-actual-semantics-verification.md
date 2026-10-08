# Whole-run actual semantics: implementation report and manual verification

Implementation is complete in the working tree. Verification is pending. No Gradle build, test suite, device command, installation, connected test, uninstall, or data clear was run for this task. The real device database/new diagnostic ZIP was not supplied; exact on-device values and split rows must be captured using the checklist below. Existing unrelated working-tree changes are preserved.

## 1. Exact root cause

There were two legitimate data representations with inconsistent consumer precedence. Canonical `RunSummary` contains the selected activity representation. `HealthConnectRunMetricsProvider.build()` independently obtains distance from the inspection's time-window `DISTANCE` total, falling back to the run distance only when that measurement is absent. It calculates the measurement snapshot's pace from its own duration/distance. Samsung enrichment preserves primary/secondary measurement values; those values need not equal an edited canonical activity.

History/Home used canonical records, while Run Detail Overview, split baseline and equal-half input, WorkoutResponse and Profile preferred `RunMetrics` whole-run values. Route splitting already used canonical distance. Consequently a route calibrated to 4680.438 m could be compared against a pace based on 4755.3 m. This is a semantic-consumption defect, not evidence of bad GPS geometry or duplicate matching.

The supplied Oct 1 reference calculates to `848 × 1000 / 1952.169 = 434.38862106713094 s/km` (approximately 7:14/km). Sep 20's exact canonical duration and individual splits were not supplied; its exact pace/split deltas cannot be reported as observed device results.

## 2. Meaning of RunMetrics.distance

`RunMetrics` remains the provider-independent measurement/enrichment container: primary and secondary provider values, confidence, verification, provenance and sensor series. Distance is an available provider/time-window measurement, not necessarily canonical actual activity distance. No measurement is deleted, overwritten, or persisted from resolved summary values. Provider refresh continues to receive the raw provider summary.

## 3. Authoritative actual summary

`RunActualResolver` reuses `RunSummary`; there is no parallel whole-run actual model. The resolved summary retains physical identity, selected canonical provider identity/source and canonical start/end. Valid canonical duration/distance win. Pace is calculated from resolved duration/distance; stored pace and sensor-speed average are never whole-run substitutes. Added per-value provenance identifies canonical, canonical-interval, matched-fallback or unavailable origins. Resolution is read-only and does not change the Room schema.

## 4. Centralized fallback rules

- Distance must be positive and finite. Duration must be positive.
- Missing/invalid duration first recovers a positive canonical start/end duration, using the existing whole-second representation.
- A remaining missing/invalid value may use a HIGH-confidence, non-CONFLICT metric only when both canonical provider-record ID and the exact start/end interval match. Medium/low confidence, other records, longer/original intervals and invalid values are rejected.
- Distance/duration remain unavailable without valid evidence; `RunSummary` retains its existing non-null duration representation with zero and explicit unavailable provenance; summary presentation formats this as unavailable rather than 0:00. Pace is null unless both actual inputs produce a positive finite ratio.
- Mixed canonical/fallback inputs are explicit in provenance. An already-resolved summary keeps fallback provenance downstream; recalculation at repository boundaries always starts from raw saved records.

## 5. Consumers changed

Canonical/recent/ignored run reads, History, Home/Latest Run, Run Detail Overview/Analysis/chart reference, split deltas/halves, completed-workout actuals, Plan execution actuals, WorkoutResponse, Profile and goal observed distance now consume resolved actuals. Detail opened through an old provider ID follows the current canonical physical activity, including its corresponding enrichment snapshot.

Raw `allRuns`, duplicate diagnostics, provider import/matching/canonical selection and source metadata remain separate. No provider matching, identity assignment, relevance, feedback ownership, permission, route import, map, plan-generation or UI-layout rules changed. GoalStrategy/TrainingGoal and goal completion/continuity criteria are unchanged; only goal observed-distance sourcing is aligned.

## 6. Split delta baseline

The shared production `RunPacingSummaryCalculator.summarize()` supplies the baseline actually used for deltas in Detail and diagnostics. `delta = split pace − canonical actual pace`; 401 − 411 is **−10 s/km**, regardless of an enrichment pace of 405. Partial distances, durations, normalized pace and partial status remain intact.

## 7. Equal-distance halves

Both halves use the resolved canonical total distance. Existing genuine-route validation, bounded calibration, cumulative-distance interpolation, edge/gap checks and even-split tolerance remain unchanged. No splitter or route geometry was introduced. Cache validation recognizes a newly available distance fallback when the old cache has no canonical-distance basis. Read fallback does not write cache rows.

## 8. WorkoutResponse and execution impact

Actual whole-run distance/duration/pace and volume comparison now use the resolver, including trusted missing-distance fallback. Completion thresholds, persisted completion status, intended prescription, quality-work segment semantics, continuity, pace-stability, pain/RPE and session-cost rules remain intact. Normal adherence evaluation receives the resolved summary. Automatic association matching still receives the raw provider representation.

Completed-workout and Plan read paths project current actual distance/duration into execution display results while preserving the saved status/prescription/evaluation decision. They do not rewrite historical execution rows. Response remains derived from saved inputs and current canonical actuals.

## 9. Performance Profile impact

Eligible Easy pace, recent/weekly volume, longest distance and benchmark inputs use canonical actual duration/distance/pace. Independent HR/cadence/VO₂max and continuity evidence remain measurements. `CanonicalTrainingEvidence` supplies actual inputs to the existing policy through a private, non-persisted projection; `TrainingEvidencePolicy.kt` was not edited in this task. The standalone 300-second guard, associated-session exception and all other eligibility rules remain unchanged. Tiny fragments retain actual volume/safety evidence without becoming Easy/session evidence because of a longer provider duration. Existing saved profiles await ordinary recalculation; export does not recalculate them.

## 10. Home/Progress impact

History/Home repository reads resolve canonical actuals even without the optional refresh metrics repository. Daily, selected-week and four-week aggregate pace use summed actual durations divided by summed actual distances. They do not average per-run paces or count non-canonical duplicates. Missing/invalid pace inputs leave the aggregate unavailable. Current-week diagnostic contributors expose IDs/distance/duration and the weighted pace. Plan generation is unchanged.

## 11. Diagnostic dump changes

Per-run `summary.json` exposes `canonicalActual` (IDs, interval, distance, duration, calculated pace, source and per-value provenance) separately from `enrichmentMeasurements` (original distance/duration/pace MetricValues, including secondary values). Raw tables and `metrics.json` preserve provider evidence. Existing `route.json` distance/calibration metadata and recorded points remain available. `splits.json` exposes actual distance, whole-run baseline, `baselineSource`, baseline provenance, split deltas and equal halves. Evidence-contributor diagnostics include the actual summary used for policy inputs; Home diagnostics list current-week contributors. ZIP privacy modes/redaction and non-mutating export behavior are unchanged.

## 12. Integrity check

`whole_run_pace_vs_split_baseline` compares resolved canonical pace with the consumed shared production baseline. Difference greater than **0.001 s/km**, a missing counterpart or a nonfinite value reports **REVIEW** with both values; equal/tiny-rounding values pass, and jointly unavailable values pass. Raw provider distances are deliberately not compared for equality. The checker has no repair/write capability. The export still reports `repairsPerformed: false`.

## 13. Files changed for this task

Other modifications already in the working tree are outside this manifest.

Production (28 files):

- [RunSummary.kt](../app/src/main/java/com/example/openruncoach/domain/RunSummary.kt)
- [RunActualResolver.kt](../app/src/main/java/com/example/openruncoach/domain/RunActualResolver.kt) — new
- [CanonicalTrainingEvidence.kt](../app/src/main/java/com/example/openruncoach/domain/evidence/CanonicalTrainingEvidence.kt) — new
- [RunMetrics.kt](../app/src/main/java/com/example/openruncoach/domain/metrics/RunMetrics.kt)
- [AthletePerformanceProfileCalculator.kt](../app/src/main/java/com/example/openruncoach/domain/profile/AthletePerformanceProfileCalculator.kt)
- [WorkoutExecution.kt](../app/src/main/java/com/example/openruncoach/domain/workout/WorkoutExecution.kt)
- [WorkoutResponse.kt](../app/src/main/java/com/example/openruncoach/domain/workout/WorkoutResponse.kt)
- [GoalCompletionEvaluator.kt](../app/src/main/java/com/example/openruncoach/domain/goal/GoalCompletionEvaluator.kt)
- [RunPacingSummary.kt](../app/src/main/java/com/example/openruncoach/domain/route/RunPacingSummary.kt)
- [RunSplitResolver.kt](../app/src/main/java/com/example/openruncoach/domain/route/RunSplitResolver.kt)
- [RunRepository.kt](../app/src/main/java/com/example/openruncoach/data/RunRepository.kt)
- [RunMetricsRepository.kt](../app/src/main/java/com/example/openruncoach/data/RunMetricsRepository.kt)
- [RunMetricsRefreshService.kt](../app/src/main/java/com/example/openruncoach/data/RunMetricsRefreshService.kt) — measurement terminology only
- [RunRouteRepository.kt](../app/src/main/java/com/example/openruncoach/data/RunRouteRepository.kt)
- [StructuredWorkoutRepository.kt](../app/src/main/java/com/example/openruncoach/data/StructuredWorkoutRepository.kt)
- [WorkoutExecutionRepository.kt](../app/src/main/java/com/example/openruncoach/data/WorkoutExecutionRepository.kt)
- [TrainingPlanRepository.kt](../app/src/main/java/com/example/openruncoach/data/TrainingPlanRepository.kt) — read projection/observation only
- [RunDao.kt](../app/src/main/java/com/example/openruncoach/data/local/RunDao.kt)
- [RunMetricsDao.kt](../app/src/main/java/com/example/openruncoach/data/local/RunMetricsDao.kt)
- [RunRouteDao.kt](../app/src/main/java/com/example/openruncoach/data/local/RunRouteDao.kt)
- [RunDetailViewModel.kt](../app/src/main/java/com/example/openruncoach/ui/health/RunDetailViewModel.kt)
- [RunDetailScreen.kt](../app/src/main/java/com/example/openruncoach/ui/health/RunDetailScreen.kt) — value binding only
- [HomeViewModel.kt](../app/src/main/java/com/example/openruncoach/ui/home/HomeViewModel.kt)
- [DiagnosticDumpRepository.kt](../app/src/main/java/com/example/openruncoach/data/diagnostics/DiagnosticDumpRepository.kt)
- [DumpIntegrityChecker.kt](../app/src/main/java/com/example/openruncoach/data/diagnostics/DumpIntegrityChecker.kt)

Additional unavailable-duration value formatting (no layout change):

- [RunFormatting.kt](../app/src/main/java/com/example/openruncoach/ui/common/RunFormatting.kt)
- [AppComponents.kt](../app/src/main/java/com/example/openruncoach/ui/components/AppComponents.kt)
- [HistoryScreen.kt](../app/src/main/java/com/example/openruncoach/ui/history/HistoryScreen.kt)

Documentation: [README.md](../README.md) and this report.

## 14. Tests added (not executed)

**23 new deterministic tests: 21 JVM tests and 2 Android/Room tests.** An existing dump integration test was also expanded, and the existing Profile pace-trend fixture now expresses pace changes through canonical duration/distance instead of enrichment-only pace overrides.

| File | New tests | Coverage |
| --- | ---: | --- |
| [RunActualResolverTest](../app/src/test/java/com/example/openruncoach/domain/RunActualResolverTest.kt) | 8 | A/E; both supplied regressions, provenance, invalid/missing distance, rejected confidence/conflict/identity/window, trimmed volume, canonical interval recovery and unavailable pace |
| [RunPacingSummaryTest](../app/src/test/java/com/example/openruncoach/domain/route/RunPacingSummaryTest.kt) | 2 | B; −10 vs −4, preserved partial split data, canonical equal-half total and Samsung geometry |
| [RunMetricsEnrichmentTest](../app/src/test/java/com/example/openruncoach/domain/metrics/RunMetricsEnrichmentTest.kt) | 1 | C; production Samsung enrichment clips sensors, canonical corrected summary stays separate from original/aggregate volume |
| [PhysicalRunConsistencyTest](../app/src/androidTest/java/com/example/openruncoach/data/PhysicalRunConsistencyTest.kt) | 2 | D/E; real preference round-trip, History/Detail/old-ID aliases/whole-run consumers agree, Samsung route/HR/cadence retained, read-only missing-distance fallback |
| [HomeFourWeekComparisonsTest](../app/src/test/java/com/example/openruncoach/ui/home/HomeFourWeekComparisonsTest.kt) | 2 | F; weighted selected-week/daily/four-week canonical aggregates with disagreeing provider measurements |
| [WorkoutResponseEvaluatorTest](../app/src/test/java/com/example/openruncoach/domain/workout/WorkoutResponseEvaluatorTest.kt) | 2 | G; canonical volume/pace, existing endurance tolerance/status/cost, central missing-distance fallback, no stale execution-distance substitution |
| [WorkoutAdherenceEvaluatorTest](../app/src/test/java/com/example/openruncoach/domain/workout/WorkoutAdherenceEvaluatorTest.kt) | 1 | Current actual read projection preserves persisted completion/prescription and rejects unrelated physical activity |
| [AthletePerformanceProfileCalculatorTest](../app/src/test/java/com/example/openruncoach/domain/profile/AthletePerformanceProfileCalculatorTest.kt) | 2 | H; canonical Easy pace/volume, matched fallback, tiny fragment still excluded despite long provider duration, enriched cadence intact |
| [WholeRunPaceIntegrityTest](../app/src/test/java/com/example/openruncoach/data/diagnostics/WholeRunPaceIntegrityTest.kt) | 2 | I; intentional mismatch REVIEW, tiny rounding PASS, missing/nonfinite baseline REVIEW, pure read-only checker |
| [GoalCompletionEvaluatorTest](../app/src/test/java/com/example/openruncoach/domain/goal/GoalCompletionEvaluatorTest.kt) | 1 | Corrected canonical distance cannot demonstrate a goal using larger original provider volume |
| [DiagnosticDumpTest](../app/src/androidTest/java/com/example/openruncoach/data/diagnostics/DiagnosticDumpTest.kt) | existing test expanded | SHARE_SAFE canonical/enrichment separation, consumed baseline/deltas, legitimate provider difference PASS, unchanged database state and fragment exclusion |

Only source review, whitespace/diff review and static SQL/schema preparation were performed. These are not Kotlin compilation, executed application tests or device verification. The new nested Room read relations and UI/repository bindings await the requested manual build.

## 15. Complete manual verification checklist — return results, do not execute automatically

All nine checks are **PENDING**.

### Test 1 — Build

User runs `./gradlew test` and `./gradlew assembleDebug`. Expected: both pass. Record outputs/result; no agent execution has occurred.

### Test 2 — Sep 20 run

Open physical run **`9de042f6-429b-3322-833e-aaa0a8cf9ec6`**. Record exact History and Overview distance, duration and average pace; they must agree. The supplied canonical reference is approximately **4680.438 m**, with pace approximately **6:51/km**; capture actual duration/precision from the device rather than inventing it.

Open Splits and return the whole-run average baseline plus **every split's pace and Δ**, including the partial split. Expected: every Δ equals its normalized split pace minus the same whole-run average shown in Overview. Provider distance approximately 4755.3 m may remain in diagnostics.

| Surface | Distance | Duration | Average pace |
| --- | --- | --- | --- |
| History | pending | pending | pending |
| Overview | pending | pending | pending |
| Split baseline | — | — | pending |

Return a separate row for each split: ordinal/distance, duration, normalized pace and Δ.

### Test 3 — Oct 1 run

Open physical run **`efd39f0e-4aaf-3554-ad08-e2529027e279`**. Return History distance, Overview distance, Analysis actual distance, split total and split Δ baseline. Expected canonical reference: approximately **1952.169 m**, **848 s / 14:08**, **434.388621 s/km / approximately 7:14/km**. All consumers must use that same semantic. Samsung/Health Connect measurement approximately **2001.5 m** and pace approximately 7:04/km may still appear separately in diagnostics. Apply existing route quality/calibration availability rules; do not fabricate splits when evidence is insufficient.

### Test 4 — Provider enrichment

Use a run with **summary source != Samsung**. Return selected summary source and the available route/HR/cadence/speed sources. Expected: whole-run distance/duration/pace use the chosen canonical summary; Samsung route is still visible, cadence remains available, and HR/speed may still enrich independently.

### Test 5 — Preferred summary source switch

Change preferred summary source and inspect the same physical activity, including Detail previously opened via the old provider ID. Return before/after canonical provider ID, source, distance/duration/pace. Expected: if canonical selection changes, all whole-run consumers change consistently. Route/detail enrichment stays available. Restore the desired preference afterward.

### Test 6 — Trimmed run

Use a known edited/trimmed duplicate. Return canonical start/end, distance/duration/pace, enrichment sample time ranges and split baseline. Expected: actual summary follows the trimmed representation; Samsung samples outside its interval remain excluded; no original provider volume refills the trim; split baseline matches displayed pace.

### Test 7 — Weekly pace

Verify Home/Progress's selected-week average. Return **every included canonical run ID** and its **actual distance/duration**. Calculate `sum(durationSeconds) × 1000 / sum(distanceMeters)` in s/km. Expected: this equals the displayed weekly pace within formatting precision; no duplicate/provider enrichment distances are included. Also check the four-week pace comparison uses the same actual inputs. The dump's `resolved/current_home_state.json` lists current-week contributors; for another selected week, list its contributors explicitly.

### Test 8 — Performance Profile

After ordinary sync/metric/checkout recalculation, create a diagnostic dump. Return eligible Easy run IDs and canonical actual paces, together with saved Profile calculation time and Easy representative/range. Expected: eligible comparable runs use canonical actual pace; the earlier tiny-fragment exclusion remains intact. Inspect `training_evidence.json` and `resolved/training_evidence.json`. Export itself must not recalculate or mutate the saved Profile.

### Test 9 — New SHARE_SAFE dump

Export and upload a **new SHARE_SAFE ZIP** for review. Expected: per-run output clearly distinguishes canonical actual summary, original primary/secondary enrichment measurements, route evidence/raw/calibration metadata, and consumed split baseline/provenance. Integrity must not flag whole-run semantic mismatch for the fixed regressions; legitimate raw-provider distance differences are allowed. Check `errors.json` and confirm `repairsPerformed: false`. Once supplied, the exact real-run values and split rows can be reviewed against Tests 2/3/7.
