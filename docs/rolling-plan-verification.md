# Rolling-plan continuation — issue #4

## Implementation

Depends on the availability/scheduling contract in PR #18 (issue #16). The implementation branch is based on `941e8928a0c14928e5f737fce319437c006bfb5c`; review the continuation separately from that dependency. Do not merge the dependency implicitly.

`TrainingPlanGenerator.extend` appends to an active plan for the same active goal. Production runs it inside `PostRunReconciler`, after authoritative evidence/profile/goal-completion updates and before `TrainingAdaptationRepository` computes effective prescriptions. Existing import, enrichment, checkout/edit, association, ignore/restore, app-resume/environment and local-day-rollover paths therefore share one transaction and scheduling policy. No new periodic background service or second planner is introduced.

The default target is today through today + 27 days. Replenishment adds only missing whole seven-day weeks, so coverage can exceed 28 days by up to six days. A long absence starts the next added week today; skipped calendar time gets no manufactured sessions. Old week indices and build/recovery phase persist. Current canonical evidence anchors new load; merely reaching an extension boundary earns no cumulative forecast-volume increase. Near-term progression/safety continues through shared adaptation. Recovery sessions whose practical minimum distance exceeds the reduced budget become explained rest without flattening structured blocks or redistributing omitted distance.

Insufficient evidence produces at most one comfortable RPE-based assessment each new week, or explained rest when it cannot fit availability or milestone recovery. Existing valid assessments are retained. There is no invented pace or checkout. A selected future Completion Run can be created in a new window when normal-plan evidence supports it; saved attempts/dates never move. An unavailable saved date without an existing attempt stays unresolved with an explanation and the existing availability-conflict UI. The six days after a selected attempt allow only rest/recovery. Training can continue afterward without automatically scheduling another attempt. Goals completed during reconciliation stop extension before any new weeks are written.

Persistence appends only new structured workouts, weeks, reasons, blocks and references, and updates the existing plan metadata. It does not call `saveAsActive`, supersede the plan, rewrite old prescriptions or replace their dependent records. Room serializes reload/decision/append across independent repository instances; cancellation/failure rolls back with the input update. Week-based workout IDs and the reloaded horizon make retries/restarts idempotent. Schema remains version 19.

Plan getters/observers, workout presentation and diagnostic exports remain read-only. Home, Plan and Detail continue to consume the same saved prescriptions and dates. No UI components or provider integration changed.

## Automated verification (2026-11-04)

Commands and actual outcomes are recorded below. Full Android builds use the existing local Samsung AAR; Core CI remains SDK-independent. The local SDK emits an existing XML-version warning.

- `./gradlew --no-daemon --no-watch-fs -p ci/core-tests test` — passed, 432 tests, no failures.
- `./gradlew --no-daemon --no-watch-fs test assembleDebug assembleDebugAndroidTest` — passed, 553 JVM tests, no failures; debug APK and instrumentation APK assembled. Instrumentation packaging is not device execution.
- `ANDROID_SERIAL=emulator-5554 ./gradlew --no-daemon --no-watch-fs connectedDebugAndroidTest` — 80 tests, 74 passed, six failed. Seven new `RollingPlanRepositoryTest` cases passed, including concurrent writes, restart/day rollover, transaction rollback, availability, inactive goals, passive reads and diagnostic export. The initial run had one additional growing-plan expectation failure; that expectation was corrected and the next full run removed it.
- `ANDROID_SERIAL=emulator-5554 ./gradlew --no-daemon --no-watch-fs connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.example.openruncoach.data.RollingPlanRepositoryTest,com.example.openruncoach.data.TrainingAdaptationRepositoryTest,com.example.openruncoach.data.PostRunReconcilerTest,com.example.openruncoach.data.PhysicalRunConsistencyTest#planPresentationReadsOneCanonicalActualBeforeCheckoutAndKeepsPreviousPlanAccessible` — passed, 32 tests, no failures, on final sources.
- Final combined verification: `ANDROID_SERIAL=emulator-5554 ./gradlew --no-daemon --no-watch-fs test assembleDebug assembleDebugAndroidTest connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.example.openruncoach.data.RollingPlanRepositoryTest,com.example.openruncoach.data.TrainingAdaptationRepositoryTest,com.example.openruncoach.data.PostRunReconcilerTest,com.example.openruncoach.data.PhysicalRunConsistencyTest#planPresentationReadsOneCanonicalActualBeforeCheckoutAndKeepsPreviousPlanAccessible` — passed: 553 JVM tests, both APK builds and 32 emulator tests, no failures.
- `git diff --check` — passed.

The six broad-suite failures were reproduced on unchanged commit `941e8928a0c14928e5f737fce319437c006bfb5c`, exported with `git archive HEAD` to `/tmp/openruncoach-issue4-baseline`, with the existing local SDK/AAR configuration. Baseline command (working directory `/tmp/openruncoach-issue4-baseline`):

```bash
ANDROID_SERIAL=emulator-5554 ./gradlew --no-daemon --no-watch-fs connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.example.openruncoach.data.PhysicalRunConsistencyTest,com.example.openruncoach.data.RunFeedbackRepositoryTest,com.example.openruncoach.data.diagnostics.DiagnosticDumpTest
```

Baseline: 17 tests, 11 passed, the same six failures:

1. `PhysicalRunConsistencyTest.missingSplitReadsAndObservationsDoNotWriteAndUseRealSamples`: SQLite total-changes assertion 1211 versus 1218.
2. `PhysicalRunConsistencyTest.missingDistanceUsesSameReadOnlyFallbackForHistoryDetailAndRouteSplits`: total-changes assertion 412 versus 415.
3. `RunFeedbackRepositoryTest.migrationPreservesExistingRunsAndAddsFeedback`: fixture lacks migration path 1 → 19.
4. `RunFeedbackRepositoryTest.sourceMigrationPreservesRunsAndFeedback`: fixture lacks migration path 2 → 19.
5. `RunFeedbackRepositoryTest.canonicalMigrationPreservesRunsAndFeedback`: fixture lacks migration path 3 → 19.
6. `DiagnosticDumpTest` cannot initialize because `corruptOptionalMetricsStillProduceUsableArchiveWithErrorReport` does not return void. The new standalone rolling diagnostic-export case executes successfully without relying on this broken class.

These baseline failures remain separate follow-up work. The complete instrumentation suite is not green.

## Device verification

Automated Room/workflow execution: passed for the focused continuation tests on `Medium_Phone_API_37.0`, Android 17/API 37, `emulator-5554`. This is separate from an athlete-facing journey or real-provider verification.

Phone/UI verification: **pending**. ADB lists the connected physical phone as `unauthorized`; no phone data was accessed or modified. No manual Home/Plan/Detail journey or real-provider import was claimed. Keep issue #4 open until the following checks are completed:

- On an isolated simulation database, resume immediately before/after the horizon boundary and check that upcoming workouts appear with the same active plan ID. Repeat resume without duplicates.
- Return after the entire forecast expired. Confirm current/future weeks appear, no workouts are manufactured in the absence, and completed/skipped history remains accessible.
- Compare Home, Plan and Detail dates, original structured prescriptions, separate effective adjustments and actual results. Verify a saved completion/checkout/association remains intact after extension and restart.
- Change running-day availability. Confirm retained future sessions stay on their saved dates, new sessions obey availability, and constrained recovery/frequency explanations are visible.
- Exercise insufficient evidence, no available days, and a scheduled Completion Run (including changed availability, a missed attempt and later observed success). Confirm no silent replacement attempt or goal deadline.
- Pause/archive/complete the goal and confirm no active continuation occurs.
- Export a diagnostic ZIP with an expired horizon and confirm export alone does not add weeks.

The PR is a reviewable implementation with passing local JVM/build checks and focused emulator evidence; it does not close the issue or authorize merging.
