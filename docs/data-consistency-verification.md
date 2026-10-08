# Physical-run data consistency

## Investigation before changing consumers

Existing working-tree changes include Completed Run/History presentation, pacing summaries,
route derivation distance traces, associated workout metadata and diagnostic exports. They are
preserved. The application uses Room DAOs → data repositories → pure Kotlin domain calculators
and evaluators → ViewModels → Compose. Production and simulation share these paths through
`OpenRunCoachEnvironment`; diagnostics read the selected environment in a Room transaction.

* `RunDao.getCanonicalRuns` selects `isCanonical = 1 AND relevance = ACTIVE`.
  `AthletePerformanceProfileRepository.recalculate` passes these runs, canonical metrics, **all
  provider-keyed feedback**, and executions to `AthletePerformanceProfileCalculator.calculate`.
  Its `recent7/recent28` feed run counts, frequency and actual volume. Active weeks, weekly run
  counts, streak, confidence, benchmarks and continuity also consume that active-only collection.
  `qualifiesAsEasy` checks completion, RPE ≤ 4, cardio effort, pain and positive duration/distance/
  pace, but has no recording-fragment guard. Consequently 5.861 m / 54 s with RPE 1 is easy
  evidence for pace, HR and cadence. No existing minimum longitudinal session threshold was found;
  route derivation minimums and prescription adherence tolerances serve different purposes.
* `AthleteStateRepository.rebuild` recalculates the profile, loads canonical runs/metrics and
  `WorkoutResponseRepository.getAll`, then invokes `AthleteStateBuilder`. Volume/frequency come
  directly from the profile; completed/tolerated capability uses responses; demonstrated continuity
  can fall back to the profile's canonical continuity summary. Recent responses feed session cost,
  pain and readiness. There was no recording-fragment gate beyond ACTIVE/canonical selection.
* Run Detail, Home and Coach observe `RunFeedbackRepository.observeFeedbackForRun(providerId)`.
  It and writes directly access `run_feedback.runId`, whose unique key/FK points at a provider
  row. Profile, WorkoutResponse, StructuredWorkoutRepository and checkout reconciliation also
  index feedback by provider ID. `RunDao.repointPhysicalRunReferences` additionally invokes
  `moveFeedbackToCanonical`, choosing a legacy row with LIMIT 1 only if the new canonical record
  has no checkout. This explains provider-dependent identity and hidden duplicate answers.
* `RunMetricsAutoRefreshCoordinator.refresh` calls `RouteSplitDeriver` only in its persist callback,
  only when a fresh provider inspection supplied `selectedRoute`. Fresh metrics can skip this
  callback; absent provider route data never backfills a saved route. `RunRouteRepository.get` and
  `observe` only map stored split rows. Its physical mapper deliberately discards splits when the
  canonical time window clips points, with a comment relying on a later refresh. Thus a saved
  route with absent/invalidated splits has no independent resolution path. The pasted facts do
  not include the raw GPS/time series or import history: they establish missing cache rows, but
  cannot establish whether each real route passes the deriver's gap/outlier/distance checks or
  why those rows were originally absent. No real dump archive was attached to this task.
* `longestCompletedDistanceMeters` intentionally takes the maximum **COMPLETED WorkoutResponse**
  actual distance, whereas demonstrated continuous distance also uses the profile's broader
  canonical continuity evidence. Therefore approximately 2.45 km versus 4.44 km is compatible
  with existing semantics; this task does not redefine the field.

Implementation and manual verification follow below.

The downstream `TrainingDataSufficiencyEvaluator` also has an assessment/planning fallback that
counts basic positive metric snapshots and checks descriptive `totalCanonicalRuns`.
Those planning rules remain unchanged under the explicit prohibition on changing plan generation;
this task corrects profile baseline/frequency/capability and AthleteState evidence. No claim is made
that every planner fallback has been redesigned to consume purpose-specific eligibility.


## Final implementation report

1. **Tiny-run contamination root cause:** the paths above used ACTIVE/canonical membership for
   longitudinal counts; the old easy predicate accepted any positive duration/distance/pace with
   easy completed feedback. It admitted the 5.861 m run's 9213.445 sec/km value directly into the
   median/range inputs. HR and cadence used the same easy-run set.
2. **Evidence architecture:** pure domain `TrainingEvidencePolicy` returns an eligibility Boolean
   and explicit reason per purpose. The centralized standalone minimum is 300 recorded seconds.
   This conservative product guard rejects all three reported standalone fragments, including
   387.378 m / 124 s, without modifying their relevance. It is not a physiological claim. Associated
   structured workout/execution context supports shorter legitimate sessions; easy evidence still
   needs completed low-effort aerobic context, valid observations, and no significant pain.
3. **Consumers by purpose:**

   | Purpose | Production consumers |
   | --- | --- |
   | SESSION_FREQUENCY | Profile 7/28-day run counts, weekly session counts, average frequency, active weeks, streak, consistency, profile confidence; AthleteState frequency inherits the profile |
   | EASY_PACE / EASY_HEART_RATE / EASY_CADENCE | Corresponding observed profile baselines and easy trends |
   | CAPABILITY | Profile benchmarks, longest supported recent run, continuity/VO2 evidence; AthleteState completed/continuous/tolerated response evidence and capability confidence |
   | ACTUAL_VOLUME | Profile 7/28-day totals and completed calendar-week actual totals, including aborted/short activities; AthleteState inherits totals |
   | SAFETY_RECOVERY | One-per-physical-run profile feedback aggregates, plus AthleteState recent response cost/pain/readiness |
   | ASSOCIATED_WORKOUT | Explicit evidence of a linked structured workout/execution; permits short sessions through the other purpose decisions without redefining execution outcomes |

   `totalCanonicalRuns` still describes active canonical history. Feedback count can exceed meaningful
   session count because short-run pain/effort remains visible. AthleteState readiness continues to
   use structured responses, as before; this task does not invent responses for unassociated runs.
4. **Feedback root cause:** unique `run_feedback.runId` plus provider-row FK and direct keyed
   consumers made checkout identity depend on provider representation. Canonical repointing could
   move one arbitrary legacy row or leave conflicts hidden.
5. **Ownership semantics:** repository results use physicalRunId as `RunFeedback.runId`. All
   production reads/writes, profile, response, reconciliation, manual assignment, structured workout,
   Home/Coach/Run Detail feedback consumers go through the same resolver. Writes resolve the group
   inside a transaction before selecting the stable storage owner. Canonical selection no longer
   repoints feedback. Execution/response/coaching/lifecycle/next-workout/goal evaluation identity
   guards accept physical feedback identity. Their scoring, tolerances, outcomes, goal criteria and
   prescription algorithms are unchanged; provider-ID compatibility remains for legacy in-memory
   callers.
6. **Legacy data:** an existing physical-anchor row wins. Otherwise earliest createdAt, then
   feedback ID, selects one whole legacy answer independently of source preference. Every duplicate
   row, ID, original storage run ID and note remains available in diagnostics. A sole legacy row
   moves with its existing ID/creation timestamp only on explicit save/edit. With multiple legacy
   rows and an empty real anchor, explicit user input creates a new authoritative anchor answer
   while preserving the originals. Custom physical IDs that are not provider-row keys use the
   earliest existing answer's member as stable storage owner, or the lexicographically first
   preserved member for the first write. No contradictory fields are merged.
7. **Migration:** none required; Room schema remains version 18. Logical identity resolution and
   existing preserved identity anchors satisfy the invariant while retaining the existing foreign
   key/unique index. No destructive migration or bulk user-data rewrite was added.
8. **Missing-split root cause:** saved splits were the sole read source; derivation happened only
   when normal metrics refresh obtained a new provider route. The freshness shortcut, missing fresh
   provider data, or clipping after canonical interval changes could leave existing routes without
   splits indefinitely. The exact historical trigger and derivability of the two real routes cannot
   be distinguished from pasted counts alone.
9. **Resolved split architecture:** the physical mapper retains canonical time-window clipping,
   stable logical ownership and raw persisted count. `RunSplitResolver` validates cached ordering,
   identity, positive finite values, time continuity and changed distance basis. Missing/stale cache
   uses the unchanged production `RouteSplitDeriver`, canonical summary distance and canonical
   persisted HR/cadence samples. It returns `PERSISTED_CACHE`, `ROUTE_DERIVED_ON_READ` or
   `UNAVAILABLE`, plus persisted and resolved counts. Route points are not changed by resolution.
   Normal refresh separately calls a transactional cache backfill even when metrics are fresh.
   It replaces only split rows for the resolved storage route when their values differ; repeated
   calls are idempotent. New route saves continue to use the existing physical identity anchor.
   Legacy route storage is resolved through the physical group and survives canonical switches.
   Current caches have no sensor-version fingerprint; normal import replaces splits with the new
   route, while this backfill targets absent/structurally stale boundaries. Deriver validation still
   controls gaps, outliers, distance disagreement and minimum partial distance.
10. **Old runs:** get/observe now return production fallback splits without reimport, source change,
    data reset or provider refresh, when the real route/time evidence passes production validation.
    Synthetic routes at 1952.169 m and 4680.438 m have regression expectations of 2 and 5 splits.
    The actual real GPS files were not available, so these counts are not claimed as verified
    results for the user's device.
11. **Diagnostics:** per-run `training_evidence.json`, `feedback_ownership.json`, resolved checkout,
    and split counts/source; aggregate `resolved/training_evidence.json` (canonical/physical IDs,
    timestamps, 28-day membership and decisions) and `resolved/feedback_ownership.json`; integrity
    reports duplicate physical feedback and detailed original rows/selected authority. Dump format
    version 1 receives additive fields/files. Export remains read-only; it calls neither feedback
    writes nor split backfill. Saved profile is explicitly distinguished from current policy
    decisions. Exporting alone does not recalculate an older contaminated profile cache.
12. **AthleteState audit:** longest completed is completed structured/response-backed actual
    distance; demonstrated continuous can additionally come from canonical profile continuity.
    The reported 2.45 km versus 4.44 km therefore has a valid semantic explanation. No formula
    broadening to all canonical runs was made. Evidence purposes now gate capability inputs;
    aborted painful responses remain in recovery evidence.
13. **Files changed for this task** (pre-existing UI, maps, build settings and diagnostic support
    work remain preserved):

    - `domain/evidence/TrainingEvidencePolicy.kt` (new)
    - `domain/profile/AthletePerformanceProfileCalculator.kt`
    - `domain/athlete/AthleteState.kt`
    - `domain/RunFeedback.kt`
    - `domain/route/RunRoute.kt`, `domain/route/RunSplitResolver.kt` (new resolver)
    - `domain/workout/WorkoutExecution.kt`, `WorkoutResponse.kt`, `WorkoutLifecycle.kt`, `NextWorkoutGenerator.kt` (identity guards)
    - `domain/coaching/CoachingEngine.kt`, `domain/goal/GoalCompletionEvaluator.kt` (identity guards)
    - `data/RunFeedbackRepository.kt`, `AthletePerformanceProfileRepository.kt`
    - `data/WorkoutExecutionRepository.kt`, `WorkoutResponseRepository.kt`, `StructuredWorkoutRepository.kt`
    - `data/RunRouteRepository.kt`, `RunMetricsAutoRefreshCoordinator.kt`
    - `data/local/RunDao.kt`, `RunFeedbackDao.kt`, `RunRouteDao.kt`
    - `data/diagnostics/DiagnosticDumpRepository.kt`, `DumpIntegrityChecker.kt`
    - The five test files listed below, plus the existing profile calculator test's updated session-label assertion
    - `README.md`, this report

    Main source paths above are relative to `app/src/main/java/com/example/openruncoach/`.
14. **Tests added: 22 focused cases, not executed.**

    - JVM `domain/evidence/TrainingEvidencePolicyTest.kt`: 6 cases covering real fragments,
      authoritative short structured workout, aborted significant-pain volume/readiness, ignored
      activity, contaminated easy baselines/frequency, deterministic threshold boundary/reasons.
    - JVM `domain/route/RunSplitResolverTest.kt`: 5 cases covering persisted cache reuse, the two
      reported distances with final partials and real sensor aggregation, stale/corrupt replacement,
      deterministic resolution, unchanged route, insufficient evidence.
    - JVM `domain/goal/GoalCompletionEvaluatorTest.kt`: 1 added case confirming physical checkout
      identity preserves strict goal evaluation and unrelated feedback remains invalid.
    - Android `data/PhysicalRunConsistencyTest.kt`: 9 cases covering noncanonical-only legacy
      feedback, both-provider writes, duplicate conflicts and physical profile feedbackCount, real
      source-preference round trips, custom identities, explicit answer with two preserved legacy
      rows, response lookup/observation through both providers, read-only derived splits with real
      HR/cadence, and repeatable cache backfill/source changes with unchanged route points.
    - Android `data/diagnostics/DiagnosticDumpTest.kt`: 1 added archive case combining a tiny run,
      missing split cache and duplicate feedback, asserting every raw table and SQLite total_changes
      remain unchanged while resolved diagnostics expose the correct production decisions.

    JVM paths are under `app/src/test/java/com/example/openruncoach/`; Android paths are under
    `app/src/androidTest/java/com/example/openruncoach/`.
15. **Validation and limits:** no Gradle build, unit/instrumentation test, adb, install, uninstall,
    connected test, pm clear or real dump generation was executed, per the explicit request.
    Static `git diff --check` passed. A source-only delimiter/whitespace scan passed for 29 touched
    Kotlin files, and SQLite EXPLAIN validated syntax/column compatibility of 32 DAO queries against
    schema 18 in temporary memory. These checks are not compilation or execution of the added tests.
    The pre-existing assessment/planning fallback described above remains an intentional scope
    limit; broad planner evidence plumbing was not changed. Current on-device numerical
    profile values, contributor IDs, feedback conflict IDs and actual route splits must come from
    the manual checks below. Duplicate matching, canonical selection thresholds, source preference,
    route import/GPS points, maps, UI design, continuity math, execution tolerances, response outcomes,
    GoalStrategy, TrainingGoal and plan-generation rules were not altered by this task.
16. **Manual verification:** complete checklist below; all items are pending user execution.

## Complete manual verification checklist (not executed)

### TEST 1 — BUILD

User runs:

```bash
./gradlew test
./gradlew assembleDebug
```

Expected: both pass. Android regression sources are additionally available for your chosen manual
instrumentation workflow; no connected/device command was run here.

### TEST 2 — NEW DIAGNOSTIC DUMP

Create a new SHARE_SAFE diagnostic dump in Settings → Advanced / Diagnostics, and return/upload
it for inspection. The 5.861 m run `cab0974e-5b8d-4415-bf62-fbf30f810db6` should remain ACTIVE and
visible. Its production eligibility output must exclude easy pace/HR/cadence, meaningful frequency
and capability when standalone. See `runs/<encoded-canonical-id>/training_evidence.json`.

### TEST 3 — PERFORMANCE PROFILE

Open Performance Profile first to run the ordinary profile/AthleteState rebuild, then export.
The dump intentionally reads the saved profile without recalculating it. Verify that 9213.445
sec/km (153:33/km) no longer enters the easy baseline from the accidental fragment. Return
`contributingRuns`, `representative`, `observedMinimum`, `observedMaximum` for Easy Pace, Easy HR
and Easy Cadence from `resolved/performance_profile.json`. Do not infer replacement numerical
values from the pasted old summary.

### TEST 4 — RUN FREQUENCY

Return every physical and canonical run ID counting toward meaningful recent frequency. Filter
`resolved/training_evidence.json` to `inLast28Days = true` and
`decisions.SESSION_FREQUENCY.eligible = true`. Verify the 5.861 m/54 s, 63.7 m/48 s and standalone
387.378 m/124 s fragments do not inflate frequency. Compare these IDs with profile `runsLast28Days`
and `averageRunsPerWeek`, after the normal profile rebuild. Do not return only the final number.

### TEST 5 — ABORTED LONG RUN

Inspect `d1dfbfe3-ce54-4330-8a42-66648df0475f`. Confirm actual approximately 1160.2 m remains in
volume, checkout pain remains SIGNIFICANT, execution/response remains ABORTED, and readiness
retains the significant-pain recovery signal. It must remain active training, without global
minimum-distance filtering. Check ACTUAL_VOLUME and SAFETY_RECOVERY decisions alongside response.

### TEST 6 — PHYSICAL-RUN FEEDBACK

Inspect physical run `015fc27e-fa56-4c95-8144-52765a406448`. Return physicalRunId, authoritative
feedback ID, authoritative storage ID, all original feedback IDs/storage run IDs and answers/notes,
and `legacyConflict`. The Samsung physical-anchor answer should win when present; the Strava
`39284363-217a-360b-bb45-18eabd4f1593` row and its notes must remain preserved for diagnostics.
Production consumers expose exactly one whole checkout.

### TEST 7 — SOURCE SWITCH

Change preferred workout source for a duplicate group, then change it back. Confirm the checkout
ID and answers remain the same for the physical activity. Open/edit from the selected canonical
record; both provider representations must resolve writes to the same authoritative checkout.
Verify the source change itself neither moves feedback nor creates a provider-specific answer.

### TEST 8 — FEEDBACK COUNT

Return profile `feedback.feedbackCount` and `runsLast28Days`, with the physical-run IDs contributing
feedback in the last 28 days. Use resolved ownership and canonical run timestamps. A physical
activity must count at most once, even with both Samsung and Strava legacy answers. Feedback
count may differ from meaningful session count because safety feedback from fragments is retained.

### TEST 9 — SEPTEMBER 20 SPLITS

Open `9de042f6-429b-3322-833e-aaa0a8cf9ec6` (4680.438 m, formerly 1862 points and zero splits).
Production route-derived splits should reach Run Detail without reimport if validation succeeds.
Return each split's distance, duration, pace, HR, cadence, derivation/confidence and route provenance,
plus persistedSplitCount/resolvedSplitCount/splitDerivationSource. A valid calibrated route should
produce four full kilometres plus a final partial. Do not invent sensor values when unavailable.

### TEST 10 — OCTOBER 1 SPLITS

Open `efd39f0e-4aaf-3554-ad08-e2529027e279` (1952.169 m, formerly 838 points and zero splits).
Verify the same production resolution; expect one kilometre and a final partial when validated.
Return counts/source and each split's actual values/provenance.

### TEST 11 — SPLIT IDEMPOTENCE

Reopen and refresh the same runs repeatedly, including a source switch. Split count and values
must remain stable for unchanged evidence, with no duplicate rows or route-point changes. Normal
refresh may persist the fallback; reopening or exporting alone must not persist it. Repeated
backfill must leave one stable set for the same physical route.

### TEST 12 — DUMP READ-ONLY GUARANTEE

Record run count, feedback count, split table count, active plan ID, saved profile calculation time
and available AthleteState before export. Create the dump without running normal refresh or other
writers concurrently. Recheck: dump creation must not persist derived splits, rewrite/migrate
feedback, recalculate/save the profile or alter application state. A resolved count greater than
persisted count is expected and must not itself cause cache persistence.

### TEST 13 — INTEGRITY REPORT

Check `integrity.json` for `multiple_feedback_for_physical_run` and detailed
`feedbackOwnershipConflicts`. Check per-run and resolved ownership/evidence files, and split
persisted/resolved counts/source. Original IDs and legacy notes should remain inspectable. Ensure
`repairsPerformed = false`; investigate optional errors rather than treating absent sections as
successful negative results.

### TEST 14 — LONGEST COMPLETED AUDIT

Explain the original approximately 2449.98 m completed versus 4439.7 m demonstrated-continuous
values by comparing COMPLETED structured WorkoutResponses with profile canonical continuity
contributors. "Longest completed" remains response-backed completed training; it does not mean
longest canonical activity of any association/completion state. No silent broadening was made.
