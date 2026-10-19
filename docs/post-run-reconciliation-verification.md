# Authoritative post-run reconciliation

Implementation is on `feat/post-run-reconciliation`. No Gradle tasks, tests, builds, installation,
ADB, or device automation were run. Automated and real-device verification are pending the user.
Do not push before verification. Issue #9 remains open.

## Architecture and commit boundary

Previously checkout saved feedback, Run Detail separately created an execution and evaluated a
goal from UI snapshots, metric refresh recalculated only Profile, manual assignment had another
execution-writing path, and active-plan getters performed legacy recovery writes. Response and
AthleteState were already derived projections.

`app/src/main/java/com/example/openruncoach/data/PostRunReconciler.kt` now owns post-run derived
updates through `reconcilePhysicalRun(physicalRunId)`. `reconcileAll()` is its explicit batch/startup
wrapper. Input writers join the same Room transaction. All repositories, including recreated
instances, therefore use Room's writer serialization rather than an instance-local/global mutex.
Provider reads remain outside the transaction; import re-reads local identity/relevance when
applying incoming provider records and canonical selection.

The workflow resolves current canonical actual, metric window, relevance, association,
physical-owned checkout, prescription/adjustment, and active goal. It replaces/removes execution,
resolves WorkoutResponse, recomputes the saved Profile and derived AthleteState, evaluates active
goal completion, refreshes milestone readiness, and returns current evidence/coaching in
`PostRunReconciliationResult`. This result is the seam for later adaptation; it creates no future
workout and promises no durable event delivery/outbox.

Events routed here:

- First checkout save, checkout edit, and migration of an authoritative legacy checkout on edit.
- Manual assignment, reassignment, unassignment, and displacement of another run's assignment.
- Health Connect import, canonical/source-preference refresh, and later saved metric enrichment.
- Ignore/restore and explicit app-start/environment recovery.
- Simulator writes after its isolated input boundary, through the same feedback/metric repositories.

Automatic high-confidence association selection remains outside reconciliation and runs only on
the first explicit checkout. It retains the existing competing-date/prescription safeguards.
Restart, enrichment, or editing an existing checkout never rematches an unassigned run.

Association's unique physical-run key and workout key retain one authoritative relationship.
Obsolete raw executions are removed, including rows hidden by normal association-filtered reads.
WorkoutResponse explicitly carries stable `physicalRunId`; its `runId` remains the current canonical
evidence record. AthleteState matches/deduplicates responses physically. No new Room table or
migration is required: response and state remain projections. Unchanged execution/profile
snapshots keep their evaluation timestamps. Missing checkout means no execution/response,
never implicit RPE, pain, completion, or readiness values.

Ignore retains provider records, checkout, and assignment as restoration inputs, while removing
execution and excluding normal evidence through existing relevance/eligibility policy. Restore
rebuilds eligible evidence once. Goal evaluation requires actual checkout, uses the existing
strict evaluator/continuity rules, and can qualify the same physical run after later evidence.
Retroactively reopening completed goals belongs to #5; future adaptation/planning belongs to
#3/#4. Existing matching, enrichment selection, routes/splits, thresholds, and quality-session
evaluators are preserved.

Diagnostics remain observers. Existing per-run `association.json`, `feedback_ownership.json`,
`checkout.json`, `execution.json`, and `response.json` expose the current relationship.
`response.json` now also contains `physicalRunId`. `training_evidence.json` and
`resolved/{performance_profile,athlete_state,active_goal,active_plan}.json` provide longitudinal
context. Archive folder names continue to use encoded canonical provider IDs, so correlate
snapshots by `physicalRunId`, not folder name. SHARE_SAFE/FULL behavior is unchanged.

## Regression coverage added (not executed)

- `PostRunReconcilerTest` (Room): repeat/concurrent reconciliation, late enrichment, preference
  round trips, A→B, unassign/reassign, concurrent checkout edits, missing checkout, ignore/restore,
  database recreation, repeated provider upsert/refresh, late strict goal completion, ignored goal
  evidence, transaction rollback, stale metric windows, never-associated goal completion, quality-work evidence, and obsolete execution cleanup.
- `PostRunReconciliationSimulationTest` (Room/debug): identical persisted D/E inputs through
  simulator and production repository boundaries produce equal execution/response/state.
- `AthleteStateBuilderTest` (JVM): provider representations share one physical response contributor.
- `DiagnosticDumpTest` (Room): exporting and reading the active plan cannot reconcile pending
  checkout data; only an explicit input/reconciliation operation changes it.
- Existing source-selection/route and simulator tests now supply the required reconciliation dependency; the canonical-actual fixture includes real checkout and expects recomputed minor-deviation status and current distance.
- Existing dense-running/walking/unknown continuity controls and simulator D/E/M remain applicable.

## Manual checklist

### A. Build verification

User runs `./gradlew test` and `./gradlew assembleDebug`. Expect both to report BUILD SUCCESSFUL,
without Room schema/migration, unresolved dependency, or duplicate-key failures. Return the last
30–50 lines of each failing command, or the success summaries. These commands do not execute
the new Room instrumentation tests; run those manually through the existing test setup when
ready, and report their results separately. No device/connected-test commands were run here.

### B. Git verification

Run `git status --short`, `git log -1 --oneline --decorate`, and
`git show --stat --oneline HEAD`. Expect a clean working tree on
`feat/post-run-reconciliation`, with the reported local commit at HEAD.

### C. Existing completed run

Open History → an existing completed run with checkout. Record date/time, distance, associated
workout, RPE, pain, completion, and Analysis/Coach interpretation. Close/reopen Run Detail, then
restart the app and reopen it. Expect one physical run, one checkout, the same association,
stable response, and no default/reset feedback. Return before/after screenshots and physicalRunId
from the dump when available.

### D. Checkout edit

Use a test/simulator associated run. Record Analysis session cost/Coach evidence, edit checkout
to materially different RPE or pain, save, reopen, and restart/reopen. Expect persisted edits,
the same physicalRunId, one authoritative execution/response, and updated cost/reasons where
warranted. Return before/after checkout and response values and the matching dump entries.

### E. Association correction

Using two planned workouts, assign a run to A, save checkout, record its interpretation, reassign
to B, inspect, unassign, and reassign again. Expect only the current association/interpretation,
no old A execution after B, retained raw run after unassignment, and no duplicated longitudinal
contribution. Return before/after associations and matching association/execution/response JSON.

### F. Ignore/restore

Record weekly volume and Performance state for an eligible run. Ignore it in Run Detail,
reopen Performance, then restore it through History's ignored-run view and reopen Performance.
Expect restorable raw evidence, exclusion while ignored, and eligible evidence returning exactly
once. Return before/ignored/restored volume/state values and diagnostic entries.

### G. Provider/enrichment stability

For an existing multi-provider run, record physicalRunId, canonical source, route/cadence sources,
then use Settings' existing sync or preferred-source selection and reopen Run Detail. Expect one
physical run/checkout/execution/response and the same physicalRunId. Provider summary selection
must not remove valid independently matched enrichment. Return before/after provider/summary,
provenance and metric entries. No test-only UI was added.

### H. Goal and late evidence

Use the Room regression `goalDistanceWithLateContinuousEvidenceCompletesOnlyAfterEnrichment`, or
an available import/simulation mechanism that can add evidence to the same run. Start with a
goal-distance actual and UNKNOWN continuity: goal stays ACTIVE. Save sufficient valid movement
evidence and reconcile/reopen: the same physical run may complete the goal once. Return goal
state, continuity, and physicalRunId before/after. No production controls were added for injecting
test data; existing simulator presets alone do not expose this two-stage evidence edit.

### I. Diagnostic observer check

Settings → Advanced / Diagnostics → Diagnostic dump: export twice immediately without another
action. Compare checkout, association, execution, response, saved Profile, resolved AthleteState,
plan, and goal entries. Only export metadata/time should differ. Dump creation must not trigger
reconciliation or provider refresh. Return dump paths, relevant JSON/JSONL section names and
redacted entries. SHARE_SAFE routes remain relative; FULL retains existing privacy behavior.

### J. Return to reviewer

Provide unit-test/build results, any separately run Room-test results, `git status --short`,
`git log -1 --oneline --decorate`, C/D/E screenshots if practical, D/E/F/G diagnostic excerpts,
and every expected/observed mismatch. Keep the branch local until verification is complete.

## Files in this change

- `AGENTS.md`
- `README.md`
- `app/src/androidTest/java/com/example/openruncoach/data/PhysicalRunConsistencyTest.kt`
- `app/src/androidTest/java/com/example/openruncoach/data/PostRunReconcilerTest.kt`
- `app/src/androidTest/java/com/example/openruncoach/data/RunRouteRepositoryTest.kt`
- `app/src/androidTest/java/com/example/openruncoach/data/diagnostics/DiagnosticDumpTest.kt`
- `app/src/androidTest/java/com/example/openruncoach/data/simulation/PostRunReconciliationSimulationTest.kt`
- `app/src/androidTest/java/com/example/openruncoach/data/simulation/SimulationScenarioLoaderTest.kt`
- `app/src/debug/java/com/example/openruncoach/data/simulation/SimulationScenarioLoader.kt`
- `app/src/main/java/com/example/openruncoach/AppEnvironment.kt`
- `app/src/main/java/com/example/openruncoach/MainActivity.kt`
- `app/src/main/java/com/example/openruncoach/data/AthletePerformanceProfileRepository.kt`
- `app/src/main/java/com/example/openruncoach/data/AthleteStateRepository.kt`
- `app/src/main/java/com/example/openruncoach/data/GoalCompletionRepository.kt`
- `app/src/main/java/com/example/openruncoach/data/PostRunReconciler.kt`
- `app/src/main/java/com/example/openruncoach/data/RunFeedbackRepository.kt`
- `app/src/main/java/com/example/openruncoach/data/RunMetricsAutoRefreshCoordinator.kt`
- `app/src/main/java/com/example/openruncoach/data/RunMetricsRefreshService.kt`
- `app/src/main/java/com/example/openruncoach/data/RunMetricsRepository.kt`
- `app/src/main/java/com/example/openruncoach/data/RunRepository.kt`
- `app/src/main/java/com/example/openruncoach/data/TrainingPlanRepository.kt`
- `app/src/main/java/com/example/openruncoach/data/WorkoutExecutionRepository.kt`
- `app/src/main/java/com/example/openruncoach/data/WorkoutResponseRepository.kt`
- `app/src/main/java/com/example/openruncoach/data/local/CoachingWorkflowDao.kt`
- `app/src/main/java/com/example/openruncoach/data/local/RunDao.kt`
- `app/src/main/java/com/example/openruncoach/data/local/RunMetricsDao.kt`
- `app/src/main/java/com/example/openruncoach/domain/athlete/AthleteState.kt`
- `app/src/main/java/com/example/openruncoach/domain/workout/WorkoutResponse.kt`
- `app/src/main/java/com/example/openruncoach/ui/coach/CoachDetailScreen.kt`
- `app/src/main/java/com/example/openruncoach/ui/health/RunDetailScreen.kt`
- `app/src/main/java/com/example/openruncoach/ui/health/RunDetailViewModel.kt`
- `app/src/main/java/com/example/openruncoach/ui/history/HistoryScreen.kt`
- `app/src/main/java/com/example/openruncoach/ui/home/HomeScreen.kt`
- `app/src/main/java/com/example/openruncoach/ui/inspection/HealthDataInspectorScreen.kt`
- `app/src/main/java/com/example/openruncoach/ui/plan/PlanScreen.kt`
- `app/src/main/java/com/example/openruncoach/ui/settings/SettingsScreen.kt`
- `app/src/test/java/com/example/openruncoach/domain/athlete/AthleteStateBuilderTest.kt`
- `docs/post-run-reconciliation-verification.md`
