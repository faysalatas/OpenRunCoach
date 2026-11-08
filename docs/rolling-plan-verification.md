# Rolling-plan continuation — issue #4

## Implementation

PR #18 (availability/scheduling, issue #16) is merged. This review integrates main `2ef3905` into rolling-plan tip `d6efd77c` and applies issue #34 on the existing PR #20 branch. Schema remains version 19 with the shared migration registry through 18→19.

`TrainingPlanGenerator.extend` appends to an active plan for the same active goal. Production runs it inside `PostRunReconciler`, after authoritative evidence/profile/goal-completion updates and before `TrainingAdaptationRepository` computes effective prescriptions. Existing import, enrichment, checkout/edit, association, ignore/restore, app-resume/environment and local-day-rollover paths therefore share one transaction and scheduling policy. No new periodic background service or second planner is introduced.

The default target is today through today + 27 days. Replenishment adds only missing whole seven-day weeks, so coverage can exceed 28 days by up to six days. A long absence starts the next added week today; skipped calendar time gets no manufactured sessions. Old week indices and build/recovery phase persist. Current canonical evidence anchors new load; merely reaching an extension boundary earns no cumulative forecast-volume increase. Near-term progression/safety continues through shared adaptation. Recovery sessions whose practical minimum distance exceeds the reduced budget become explained rest without flattening structured blocks or redistributing omitted distance.

Insufficient evidence produces at most one comfortable RPE-based assessment each new week, or explained rest when it cannot fit availability or milestone recovery. Existing valid assessments are retained. There is no invented pace or checkout. A selected future Completion Run can be created in a new window when normal-plan evidence supports it; saved attempts/dates never move. An unavailable saved date without an existing attempt stays unresolved with an explanation and the existing availability-conflict UI. The six days after a selected attempt allow only rest/recovery. Training can continue afterward without automatically scheduling another attempt. Goals completed during reconciliation stop extension before any new weeks are written.

Persistence appends only new structured workouts, weeks, reasons, blocks and references, and updates the existing plan metadata. It does not call `saveAsActive`, supersede the plan, rewrite old prescriptions or replace their dependent records. Room serializes reload/decision/append across independent repository instances; cancellation/failure rolls back with the input update. Week-based workout IDs and the reloaded horizon make retries/restarts idempotent. Schema remains version 19.

Plan getters/observers, workout presentation and diagnostic exports remain read-only. Home, Plan and Detail continue to consume the same saved prescriptions and dates. No UI components or provider integration changed.

## Review corrections — issue #34

- **R1:** `TrainingPlanGenerator.needsExtension` is the shared pure horizon gate, including the configured forecast length and local date. `TrainingPlanRepository` checks it before loading metrics, assessment counts or availability. When extension is needed, a canonical, active, interval-matched `ActualMetricSnapshot` projection loads only summary rows and scalar values. Existing full-series APIs remain intact. The same scalar mapper is reused with empty series; `TrainingDataSufficiencyEvaluator` consumes only those scalar values and is unchanged.
- **R2:** `PlanForecastExtender` is implemented by the plan repository and injected into `PostRunReconciler`. The default repository is created once per reconciler, not inside `reconcile`. The interface is public to preserve the existing public constructors and allow injection; no DI framework was added. Call direction is documented as generate → reconcile → extend; extension never calls generation or reconciliation. Full composition-root work remains #35.
- **R3:** This report and the availability report now describe the integrated source and actual new checks. Historical failures and manual verification are kept distinct from the current automated results.
- **R4:** `buildForecast` has a KDoc explaining fresh generation and continuation. Its entire implementation body and all existing generator test cases are unchanged; a golden-master-backed split remains #37.

## Automated verification — 2026-11-08

Verified the resolved source from parents `d6efd77c` and main `2ef3905`, plus the #34 changes described above, in `/tmp/openruncoach-rolling-review`.

The tested source was published as commit `211ee66f533cd3309e88399cc7167e92f6c0cc81`. This subsequent documentation update records that exact reference; implementation and tests are unchanged.

```bash
./gradlew --no-daemon --no-watch-fs -p ci/core-tests test
ANDROID_SERIAL=emulator-5554 ./gradlew --no-daemon --no-watch-fs test assembleDebug assembleDebugAndroidTest connectedDebugAndroidTest
git diff --check
git diff --cached --check
```

- **PASS: 434 Core domain tests**, including all existing rolling-plan cases plus horizon boundary/configuration/timezone/inactive-plan checks.
- **PASS: 555 app JVM tests**, both Debug APK builds, and the **entire 92-test instrumentation suite**; zero failures, errors or skipped tests.
- **PASS:** whitespace checks. Source comparison confirms the `buildForecast` body, all pre-existing `TrainingPlanGeneratorTest` cases and the sufficiency evaluator remain unchanged.
- `RollingPlanRepositoryTest` now has nine passing cases. The original seven still cover concurrent appends, retained rows, availability, inactive goals, rollback/retry, restart/day rollover and passive reads/export. New cases exercise a covered horizon against actual Room queries and reuse of an injected forecast extender.
- The covered-horizon test records zero SELECTs against metric snapshots/values/samples or availability while retaining the same saved plan. This verifies the real persistence path as well as the pure unit-tested gate.
- `RunMetricsRepositoryTest` proves scalar parity with the existing canonical full loader, excludes non-canonical/ignored/stale-interval records, records no sensor-sample SELECT in the new summary path, and confirms the unchanged full loader still returns all 181 seeded speed samples.
- All existing `PostRunReconcilerTest` and `TrainingAdaptationRepositoryTest` cases pass in the full suite, as do the six diagnostic-export tests and schema 1–18→19 migration loop.

Environment: existing Android SDK 36 and ignored local Samsung AAR; Gradle 9.1.0, JDK 21 launcher with configured JDK 17 compilation toolchain. Emulator `Medium_Phone_API_37.0`, Android 17/API 37, serial `emulator-5554`; synthetic data only. Existing compiler/deprecation/native-symbol warnings remain. GitHub Core CI is a separate check after PR #20 is retargeted to main.

### Historical failures

The earlier 2026-11-04 verification recorded 432 Core tests, 553 app JVM tests, and 74/80 passing instrumentation tests on the original source. Those original revisions were not rerun during this review. The six failures are resolved by the main changes now integrated: two Room-invalidation `total_changes()` false positives (#32 / PR #46), three incomplete legacy fixture migration registrations (#33 / PR #48), and the non-void diagnostic test initialization (#24 / PR #47). They are not current failures; all 92 tests in this integrated suite pass.

## Device verification

Automated Room/workflow execution: passed for all 92 instrumentation tests on `Medium_Phone_API_37.0`, Android 17/API 37, `emulator-5554`. This is separate from an athlete-facing journey or real-provider verification.

Phone/UI verification: **pending**. No physical phone was accessed, its connection status was not rechecked, and no manual Home/Plan/Detail journey or real-provider import is claimed. Keep issue #4 open until the following checks are completed:

- On an isolated simulation database, resume immediately before/after the horizon boundary and check that upcoming workouts appear with the same active plan ID. Repeat resume without duplicates.
- Return after the entire forecast expired. Confirm current/future weeks appear, no workouts are manufactured in the absence, and completed/skipped history remains accessible.
- Compare Home, Plan and Detail dates, original structured prescriptions, separate effective adjustments and actual results. Verify a saved completion/checkout/association remains intact after extension and restart.
- Change running-day availability. Confirm retained future sessions stay on their saved dates, new sessions obey availability, and constrained recovery/frequency explanations are visible.
- Exercise insufficient evidence, no available days, and a scheduled Completion Run (including changed availability, a missed attempt and later observed success). Confirm no silent replacement attempt or goal deadline.
- Pause/archive/complete the goal and confirm no active continuation occurs.
- Export a diagnostic ZIP with an expired horizon and confirm export alone does not add weeks.

The PR is a reviewable implementation with passing local JVM/build checks and full automated emulator evidence; it does not close the issue or authorize merging.
