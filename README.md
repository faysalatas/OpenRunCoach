# OpenRunCoach

OpenRunCoach is a local-first adaptive running coach for Android. It reads workout data from Health Connect, combines objective training data with subjective athlete feedback, and adapts future training recommendations.

The long-term goal is a transparent, explainable running coach inspired by feedback-driven training systems. Every recommendation should be traceable to workout data, athlete feedback, and explicit coaching rules.

## Current features

- Native Android app written in Kotlin and Jetpack Compose
- Dark-first Material 3 UI with a near-black blue-green and mint palette
- Bottom navigation with Home, History, Plan, and Settings
- Week and day selector with run indicators
- Read-only Health Connect integration
- Compatibility with Samsung Health and Galaxy Watch workouts when Samsung Health exposes them through Health Connect
- Import of running exercise sessions from the previous 90 days
- Local run history stored with Room
- Compact month-grouped canonical run history with associated-workout titles and persisted route silhouettes when available
- Source application attribution and device metadata when available
- Duplicate candidate detection without deleting source records
- Deterministic physical-run canonicalization for history, totals, and coaching, including trimmed cross-provider copies
- Recoverable ignored-run state and manual run-to-workout assignment
- User-selectable preferred workout source
- Post-run checkout containing:
  - RPE
  - cardio effort
  - muscle fatigue
  - pain
  - completion status
  - optional readiness
  - optional notes
- Explainable coaching analysis with these actions:
  - `PROGRESS`
  - `SMALL_PROGRESS`
  - `MAINTAIN`
  - `REDUCE`
  - `RECOVERY`
- Persistent baseline workouts, pre-run check-ins, and workout adjustments
- Explicit persistent workout lifecycle states
- Purpose-aware persisted workout execution results with visible Plan completion state
- Rule-based readiness evaluation and safety overrides
- Basic next-workout generation in the domain workflow
- Debug-only isolated coaching simulation environment with deterministic presets
- Health Connect Data Inspector for canonical runs, with per-metric permission and source diagnostics
- Experimental Samsung Health Data SDK comparison for canonical runs, including ExerciseLog cadence diagnostics
- Automatic canonical-run metric refresh and best-effort Samsung enrichment in Run Detail and after sync
- Persisted provider-independent heart-rate, speed, cadence, and power samples
- Persisted provider-independent GPS routes and route-derived standardized kilometre splits
- Compact month-grouped History with optional canonical route thumbnails and local Completed Run Detail tabs: Overview, Splits, Analysis, and Coach
- Completed Run Detail overview with persisted route visualization and compact genuine pace, heart-rate, and cadence charts
- Local-tab completed workout detail for overview, splits availability, prescription analysis, and coach interpretation; planned workout detail keeps prescription overview, structured blocks, and coaching context separate from actual evidence
- Persistent long-lived athlete goals with profile-based context, target times, and explainable assessments
- Provider-independent structured workouts with ordered segments, repeat blocks, intensity guidance, estimates, purposes, and cues
- Persistent deterministic training plans with weekly navigation, explainable blocks, recovery weeks, and goal-aware workout selection

OpenRunCoach can generate a base plan explicitly from an active goal and the current Performance Profile. On post-run checkout, a physical run is associated automatically only when it is the sole active physical run on that date and exactly one uncompleted active-plan prescription is a high-confidence distance/duration match; this deliberately preserves the unrelated-same-day safety rule. Calendar date alone is never sufficient. Ambiguous, non-canonical, ignored, incompatible, and pre-prescription runs remain unassociated. Run Detail provides manual assignment, reassignment, and unassignment as a recovery path; checkout then uses the normal persisted execution and response pipeline. A completed canonical run is also evaluated independently against an active Complete Distance goal, regardless of the planned workout's name. Broader production plan adaptation after imports is not yet implemented; the debug simulation environment exercises that completed-workout pipeline with isolated deterministic inputs.

## Diagnostic/support dump

Settings → Advanced / Diagnostics → **Diagnostic dump** exports one local ZIP through Android's file picker, with no storage permission or automatic upload. **Export share-safe dump** is the default action; **Export full dump** first warns that exact GPS coordinates can reveal home, work and frequently visited places. Both actions are available in normal builds and in isolated Simulation Mode.

Dump format version 1 includes every application table discovered from SQLite metadata, schema/relationship information, resolved canonical runs, metric-level provider provenance, actual sensor samples, production routes/splits/continuity, authoritative workout associations and executions, check-ins/checkouts, full structured WorkoutResponses, the saved Performance Profile, AthleteState, goals, plans, structured prescriptions, goal strategy, and a semantic Home snapshot. The current Room schema has 28 application tables; each archive reports its discovered and exported counts rather than assuming this number.

All Room reads share one transaction, with no application-data writes, provider sync, canonical rebuild, checkout reconciliation, profile refresh, or plan regeneration. AthleteState is resolved by the production builder against the **saved** profile and its original calculation time; the dump documents this basis and returns null if no saved profile exists. Settings and current provider permission status are sampled separately and timestamped because they live outside Room. Simulation exports use only the selected synthetic database and skip production settings/providers.

Share-safe routes use local east/north coordinates in meters with an unexported first-valid-point origin, including in raw database rows. Full exports retain GPS coordinates. Credential columns, credential tables/settings, recognized credential text, and sensitive defaults are redacted in both modes; settings are explicitly allowlisted. Notes and health data remain sensitive, and relative route shapes may still be recognizable. No raw SQLite/WAL file, provider refetch, authentication storage, map-service URL, environment variables, or logs are included.

Raw rows and sensor files stream as UTF-8 JSONL. ZIP entries are staged on disk, and optional section failures are recorded in `errors.json`; disk/destination failures fail the operation. Progress appears in Settings and duplicate exports are disabled while the picker/export is active. The archive's `README.txt` explains IDs, units, time semantics, provenance, privacy, and format limitations.

See the [implementation report and complete manual verification checklist](docs/diagnostic-dump-verification.md). Focused JVM and Android tests were added; they have **not** been run, and no build or device verification was performed for this change, as requested.

## Core product idea

The intended coaching loop is:

```text
Athlete Profile
    ↓
Goal
    ↓
Training Plan
    ↓
Planned Workout
    ↓
Pre-Run Check-In
    ↓
Adjusted Workout
    ↓
Actual Run
    ↓
Post-Run Checkout
    ↓
Coach Analysis
    ↓
Next Plan Adaptation
```

A planned workout, an adjusted workout, and the actual completed workout are separate concepts:

- **Planned workout:** the original training prescription.
- **Adjusted workout:** the safe prescription after the athlete's pre-run check-in.
- **Actual workout:** what was recorded and imported after training.

Keeping these separate lets OpenRunCoach explain what was planned, why it changed, what actually happened, and why the next workout changed.

## Goals

The Goal screen, available from Settings, provides a five-step flow in which the athlete chooses the distance and goal type. OpenRunCoach presents current observed context and data limitations, but never silently selects a goal.

### Race distances

- 5K
- 10K
- Half Marathon
- Marathon
- Custom distances from 0.1 km through 200 km

### Goal types

- Complete the distance
- Improve performance
- Reach a target time

Target-time goals accept a finish duration and show the derived pace per kilometre. When a verified benchmark exists, the review shows the required time and percentage change. A `TrainingGoal` describes what the athlete wants to achieve and has no calendar date. It remains active until qualifying performance completes it or the athlete pauses or archives it.

Creating a goal captures a compact starting-performance snapshot: the best and most recent qualifying benchmark, weekly volume, longest recent run, average runs per week, typical easy pace and heart rate, and profile confidence. This snapshot remains fixed while Goal Details shows the latest profile separately. Starting a replacement goal archives the previous active goal; goal history remains in Room.

Goal assessments are descriptive comparisons with observed data. `WELL_SUPPORTED`, `PLAUSIBLE`, `LIMITED_DATA`, and `LARGE_STEP` explain whether a verified benchmark and recent distance history support the selected context. They do not predict success or failure and do not recommend a target automatically.

Race goal distance does not constrain workout distance. A 5K athlete may still perform shorter intervals, threshold and tempo sessions, 7–10 km easy or Zone 2 runs, longer aerobic runs, and recovery sessions. The goal determines training demands rather than the distance of every workout.

## Structured workout model

`PlannedWorkout` remains the identity used by the workout lifecycle and now contains the provider-independent structured prescription. Supported workout types are Easy Run, Recovery Run, Long Run, Run/Walk, Tempo Run, Threshold, Intervals, Strides, Technique, Race Specific, and Rest.

Workouts contain ordered single-segment blocks and explicit repeat blocks:

```text
Workout
├── Warm-up
├── Work interval
├── Recovery
├── Work interval
└── Cool-down
```

Segments use one primary completion condition: duration, distance, repetitions, or open/manual completion. Segment categories distinguish warm-up, work, recovery, cool-down, easy running, strides, drills, walking, and rest. Repeat blocks preserve structures such as `3 × (8 min threshold + 2 min recovery)` without flattening them into unrelated rows.

Provider-independent intensity guidance supports RPE ranges, pace ranges, heart-rate ranges, heart-rate-zone references, and no explicit target. A segment has one primary intensity and may have secondary guardrails. Heart-rate-zone references are marked uncalibrated because zone calculation is not implemented.

Every workout has a primary purpose, explanation, cues, origin, lifecycle state, and optional Goal reference. The estimate calculator reports duration, distance, work, recovery, warm-up/cool-down time, repeat-block count, and total repeated iterations when the prescription provides enough information. These repeat values stay separate, so two drill rounds plus four stride rounds appear as two repeat blocks and six total iterations. Unknown values remain unknown and known portions are shown as partial minimums; for example, an 8 km run without pace guidance does not receive an invented duration, and an 800 metre interval does not receive one without a pace target.

Examples in the developer Workout Model Preview include:

- 35 minute Easy Run at RPE 3–4
- 8 km Long Run at RPE 3–4
- six rounds of 3 minutes running and 1 minute walking
- 10 minute warm-up, three rounds of 8 minute threshold and 2 minute recovery, then a 10 minute cool-down
- six 800 metre intervals with timed recoveries
- fast but relaxed strides
- open/manual technique drills

The normalized Room representation stores workouts, blocks, segments, intensity targets, and cues separately. Existing simple planned workouts are migrated into one equivalent duration segment while preserving their workout ID and lifecycle state. The model can be copied with changed types, segment targets, recovery, or repetition counts when structured check-in adjustment is implemented later.

## Training plans

The Plan screen generates a persistent `TrainingPlan` from the active `TrainingGoal` and current `AthletePerformanceProfile`. A plan contains ordered `TrainingWeek` records and explicit `TrainingBlock` ranges. Blocks currently use Foundation, Base, Build, Specific, and Recovery; Peak and Taper remain available for later rules. Generated entries reference the existing structured workout model and enter its lifecycle in the `PLANNED` state.

`TrainingPlanGenerator` is pure Kotlin, deterministic for the same goal, profile, clock, configuration, and persisted milestone constraint, and independent of Android, Room, Compose, and health providers. An unscheduled plan creates a rolling four-week forecast. A user-selected Completion Run date belongs to the `TrainingPlan` and sets its forecast horizon. Generation version metadata is stored with every plan. Explicit regeneration supersedes the previous active plan while preserving it in Room.

The starting frequency follows observed running frequency and is limited to two through four runs per week. Weekly distance and duration begin from the greater of the last-seven-day total and four-week weekly average, then use confidence-based bounded progression. Every fourth week is a recovery week with reduced volume, long-run load, and quality load. Long runs progress from recent capability with a fixed distance cap per increase; they do not jump directly to the race distance.

`TrainingDataSufficiencyEvaluator` decides whether the profile supports a normal plan, a conservative partial-data plan, or an Assessment Phase. It reports separate support for easy, long-run, and quality pace guidance plus goal-distance benchmark availability, with explicit reasons. When evidence is too weak even for a broad pace range, the generator creates one comfortable structured Assessment Run with RPE guidance and no invented pace. Completed assessment attempts are tracked per goal. Once basic distance, duration, pace, and feedback are usable, the app can proceed conservatively without requiring heart-rate or cadence data.

Low and very-low-confidence profiles with usable evidence stay in a conservative aerobic mode using easy, recovery, run/walk, and long runs with RPE guidance. Threshold and tempo work require recent run count, active weeks, adequate completion, low pain frequency, and sufficient profile confidence. Intervals require stronger consistency and high confidence. Most sessions remain easy, quality sessions are separated from the long run, and complete-distance goals emphasize endurance. Performance and target-time goals may receive controlled quality work when eligible; target pace is context and is not applied to every workout.

Normal Easy Runs, Recovery Runs, Long Runs, and goal-distance sessions prefer an explicit distance plus an explainable pace range. RPE remains a secondary effort guardrail, and expected duration is derived from the prescribed distance and the fast and slow ends of the pace range. When no credible pace evidence exists, the workout keeps its distance and RPE without inventing precise pace guidance. Time remains appropriate for run/walk periods, strides, drills, and selected recovery structures.

`PacePrescriptionEngine` derives provider-independent pace guidance from the observed easy-running baseline first, then cautiously falls back to verified benchmarks, recent aggregate performance, or the easy baseline captured with the goal. Low-confidence evidence produces a wider range and an explanation. Long-run guidance stays equal to or slower than normal easy pace. Target pace is reserved for an appropriate final race-specific session; Easy Runs and controlled full-distance runs remain slower.

Goal distance has different meaning by goal type. For `COMPLETE_DISTANCE`, the planner may keep earlier prescribed endurance workouts below the selected target. Walking and rest segments remain separate, while actual observed performance remains authoritative. Performance evidence, rather than calendar position, moves readiness from `GOAL_COMPLETION_NOT_READY` to `GOAL_COMPLETION_READY`. Readiness does not assign a date. The Plan screen offers deterministic dates with recovery spacing, another-date selection, and “Not yet.” The chosen date, independent readiness state, and generated attempt workout ID are stored on the active plan rather than the goal.

Choosing a date creates a planned Completion Run milestone without changing readiness. That date is a strong planner constraint: the generator places exactly one attempt on it, replaces that week's normal Long Run, removes nearby demanding work, and adds rest after the attempt when it falls inside the displayed week. A Complete Distance attempt uses the exact target distance at sustainable RPE 4–5. Rescheduling replaces the active plan milestone and recalculates provisional workouts. The milestone write and active-plan replacement run in one Room transaction so a failed replan cannot leave a partially updated schedule. Date assessment uses `INSUFFICIENT_EVIDENCE`, `AGGRESSIVE`, `REASONABLE`, or `CONSERVATIVE`, considering demonstrated continuous distance, sustained walking/stopped intervals when samples exist, RPE, recent volume, training frequency, profile confidence, and available time. Uncertainty does not block the athlete's selection.

The completion date is a forecast milestone, not a deadline for the goal. Passing it without demonstrated success leaves the goal active and allows another attempt while useful training continues. A qualifying result before the planned date completes the goal immediately, and no repeat attempt is required. The coach may explain that more preparation is useful, but it never silently moves a user-selected date.

Database version 15 removes the former goal date column and moves scheduling state onto `training_plans`. During migration, an existing active Complete Distance plan receives the old persisted Completion Run date, or the legacy goal date when no explicit milestone date exists. Legacy dates on other goal types are not treated as Completion Run intent because that meaning is ambiguous; the goal itself is preserved without a date. The migration retains goals, distinct assessment reasons in deterministic per-goal order, plans, weeks, blocks, and workout references without destructive fallback. Database version 16 adds the one-to-one persisted workout execution result and its actual-run association. Database version 17 adds normalized route points keyed by run and sequence plus persisted measured splits; both cascade with canonical-run deletion. Database version 18 adds stable physical-run identity, recoverable `ACTIVE`/`IGNORED` relevance, and a separate run-to-workout association that exists independently of checkout.

`GoalCompletionEvaluator` uses the canonical completed run, unified `RunMetrics`, lifecycle state, and feedback to explain whether the full continuous target was demonstrated; a 4.8 km run and a 5 km run with a clearly sustained walking/stopped interval do not complete a continuous 5K goal. Any qualifying 5.00+ km run completes the goal immediately regardless of workout name, clears the future Completion Run milestone, and completes the active plan. If history already demonstrates the distance, the UI says so and suggests Improve Performance or Target Time without changing the user's selection.

For `IMPROVE_PERFORMANCE` and `TARGET_TIME`, race distance is not a training-distance cap. Longer aerobic runs may exceed the race distance, and supported athletes can receive sustainable goal-distance practice before a final target-time attempt. Weekly displayed distance is the sum of explicit prescribed segment distances, while displayed weekly duration is derived when every segment has enough duration or pace information.

Every week stores its focus, load classification, targets, and generation reasons. Each generated workout keeps its purpose, cues, structured segments, and a reason for inclusion. The Home screen shows only a compact current-week summary and link to the full plan.

Normal training prescriptions allow purpose-aware real-world variation. Workout completion and prescription adherence are distinct: a slightly short easy or recovery run can be completed with a minor distance deviation, while an RPE or trusted pace outside the intended easy range remains a meaningful prescription deviation without erasing the completed training volume. Interval, stride, threshold, and drill adherence depends on the relevant quality work when that evidence exists. Actual distance and duration remain authoritative for volume and history; accepting a session never replaces actual metrics with prescribed values. Persisted execution results keep as-planned, minor-deviation, meaningful-prescription-deviation, partial, and aborted outcomes separate, and completed sessions are visibly marked Completed in the Plan after restart. Strict Goal achievement remains a separate evaluation against the observed distance and continuity criteria.

Provider records, canonical physical runs, and workout executions are separate layers. `WorkoutRunAssociation` links one stable physical-run identity to at most one planned workout and can be changed without deleting either record. `WorkoutExecutionResult` describes how the current canonical representation performed against that planned or adjusted prescription after checkout. `WorkoutResponse` is deterministic derived evidence describing one session: completion, purpose-aware adherence, actual volume, effort, continuity, pace response, pain, session cost, confidence, and concise reasons. Re-analysis uses stable physical-run and workout identities, so multiple provider copies cannot complete one prescription twice.

`AthletePerformanceProfile` owns descriptive historical evidence and aggregates such as recent actual volume, frequency, consistency, benchmarks, continuity, and supported pace/heart-rate baselines. `AthleteState` is a deterministic, rebuildable coaching interpretation composed from that profile, canonical history, and `WorkoutResponse`s. It keeps longer-term demonstrated and comfortable/tolerated capability separate from acute readiness and recovery signals. Missing evidence lowers confidence rather than becoming inability or a hard distance ceiling; a hard performance does not automatically establish comfortable training tolerance, and one bad workout can make current readiness conservative without erasing accumulated endurance evidence.

`GoalStrategyResolver` now derives `GoalStrategy` from the active `TrainingGoal` and accumulated `AthleteState` capabilities. It is pure Kotlin, deterministic, and provider-independent. `GoalStrategyRepository` exposes the same derived result in production and simulation environments; nothing new is persisted and no migration is required.

```text
TrainingGoal + AthleteState → GoalStrategy
GoalStrategy + WorkoutResponse + readiness → later TrainingAdaptationDecision
TrainingAdaptationDecision → later NextWorkoutPlanner → structured workout
```

GoalStrategy describes **WHAT needs development**. WorkoutResponse describes **HOW training was tolerated**, while readiness describes current recovery/safety constraints. The later AdaptationDecision will decide **HOW aggressively training may change**; the later NextWorkoutPlanner will select **WHAT concrete stimulus comes next**. The existing basic next-workout workflow and base-plan generator are unchanged and do not yet consume GoalStrategy.

The strategy exposes ordered primary, secondary, and supporting capability lists, interpretation confidence using the existing `ProfileConfidence` type, and reasons connecting goal demands to observed evidence. The dimensions are aerobic base, distance tolerance, continuous running, weekly volume, threshold, speed, running economy, race-specific endurance, and recovery tolerance. Unlisted capabilities have no assigned priority; there are no weights, fitness scores, predicted results, or success probabilities.

Short completion goals (up to 5 km) initially emphasize continuity and distance tolerance. When at least three tolerated sessions and both continuous and tolerated distance reach a broad 90% consolidation band, distance tolerance and aerobic consistency become primary and continuity remains secondary. This band is not a goal-completion tolerance or readiness test. Intermediate completion distances emphasize distance tolerance and aerobic base. Half-marathon-length goals add volume/recovery tolerance and aerobic durability; marathon-length or longer goals make weekly volume primary. Custom distances use these distance-demand bands, not a multiplied 5K plan.

Improve Performance and Target Time goals gain performance direction when continuous distance and comfortable aerobic tolerance both cover the target and at least three tolerated sessions exist. Short/intermediate performance goals prioritize threshold and race-specific endurance, retain aerobic base/economy, and include speed only as supporting for short distances. Long performance goals keep aerobic base and race-specific endurance primary, supported by distance, weekly volume, threshold, recovery tolerance, and economy. When distance evidence is not established, foundations retain priority. AthleteState does not contain measured current threshold/speed capability: these priorities identify relevant demands, not diagnosed pace deficits. Target time remains context and never supplies easy/threshold pace.

Completed distance never substitutes for continuous distance. Missing continuity or tolerance is explicitly uncertain, not inability. Confidence is LOW when either is missing, fewer than three tolerated or recent continuous observations exist, or AthleteState confidence is low/very low; it is HIGH only with high AthleteState confidence and at least five tolerated and five recent continuous observations, otherwise MEDIUM. Recent volume/frequency explain consistency needs without prescribing load. Current readiness, pain, latest session cost, calendar time, and plan milestones never redefine strategy. Recovery tolerance for longer distances describes sustained training capacity, not a next-session recovery decision. Inactive goals return no strategy; only the existing goal-completion pipeline changes goal status.

## Pre-run check-in

The current check-in records:

- readiness
- energy
- sleep quality
- soreness and soreness areas
- pain
- stress
- motivation
- illness
- optional notes

Safety overrides run before readiness classification. Significant pain and moderate or severe illness result in rest. Severe soreness results in recovery or rest, and combinations of poor sleep, low energy, high stress, low motivation, mild illness, pain, or moderate soreness produce explainable yellow or red readiness states.

Check-in adjustments can currently change workout type, duration, and target effort. The structured workout model will later allow changes to interval count, interval duration, and recovery duration. For example:

```text
Planned:  6 × 800 m
Adjusted: 4 × 800 m
```

or:

```text
Threshold workout → Easy Run
```

## Post-run checkout

The post-run checkout uses the persisted `RunFeedback` model:

- RPE
- cardio effort
- muscle fatigue
- pain
- completion status
- optional readiness
- optional notes

Coach analysis runs after the actual canonical run and checkout are available. Future workout generation happens after the completed run and checkout, rather than directly from the pre-run check-in.

## Workout lifecycle

Each planned workout has an explicit state:

```text
PLANNED
  → CHECK_IN_REQUIRED
  → READY
  → COMPLETED
  → CHECKOUT_REQUIRED
  → ANALYZED
  → NEXT_WORKOUT_CREATED
```

Transitions are handled by an Android-independent domain service. Invalid or out-of-order transitions are rejected. The lifecycle preserves separate references to the baseline plan, check-in and adjustment, actual run, post-run feedback, coaching decision, and generated next workout.

## Health data

Typical Samsung data flow:

```text
Galaxy Watch
    ↓
Samsung Health
    ↓
Health Connect
    ↓
OpenRunCoach
```

OpenRunCoach currently requests read access for:

- `ExerciseSessionRecord`
- `DistanceRecord`
- `HeartRateRecord`
- `SpeedRecord`

Only running exercise sessions are imported. Related metrics are read inside the selected session's time range. Missing distance, heart-rate, speed, source, or device fields are treated as optional.

The Health Connect Data Inspector can query API support and provider availability for:

- cadence
- VO2max
- elevation
- power
- route
- device metadata
- running dynamics, where available

### Health Connect Data Inspector

Run Detail provides an **Inspect Health Data** action for canonical runs. The inspector reads the selected session directly from Health Connect and reports record and sample counts, summaries, permissions, source packages, and unavailable or failed reads independently. It is a diagnostic view and does not persist or refresh normal Run Detail data.

Run Detail loads saved unified metrics immediately, then refreshes missing, outdated, or explicitly requested metrics through the normal repository and ViewModel path. The refresh reads Health Connect data, attempts exact-session or overlapping-session Samsung enrichment when available, replaces the saved metric snapshot and sample set atomically, and updates the screen. The same best-effort enrichment runs after canonical selection during normal synchronization. Samsung errors do not make a Health Connect import fail, and Performance Profile recalculation no longer depends on opening the inspector.

The Android Health Connect API supports record types such as cadence, VO₂max, elevation, power, calories, steps, and exercise routes. API support does not mean a particular watch or provider actually records or publishes that metric. The inspector exists to show the difference on a real device. It source-filters results to the canonical workout provider and labels data found only from another provider instead of combining it silently.

Route inspection requires explicit provider consent. Valid, confidently matched Samsung recorded routes are converted immediately to provider-independent `RunRoute` points and stored locally in Room; Samsung SDK location objects never leave the adapter.

### Experimental Samsung Health enrichment

Health Connect remains the primary, provider-independent workout source. The optional Samsung Health Data SDK integration supports diagnostics and best-effort normal metric enrichment for Samsung users. It does not replace canonical Health Connect run identity or affect canonical selection, coaching, or workout generation.

For a selected canonical run, the Samsung adapter checks Samsung's `DataTypes.EXERCISE` read access and separately requests explicit `DataTypes.EXERCISE_LOCATION` consent before reading an already-recorded route. Route denial does not block ordinary exercise or ExerciseLog reads. The adapter searches all nearby Samsung running and track-running sessions, then makes two decisions. The identity match finds the Samsung provider copy using source application and physical-workout evidence. The enrichment match classifies a candidate as an exact session or an overlapping session. Exact sessions require close boundaries and distance. Overlapping sessions require an extremely close start, high coverage of the canonical activity, a trusted Watch source, and substantial ExerciseLog data; Watch provenance alone is insufficient. Source application equality is not required for enrichment.

`RunRoute` stores ordered timestamped coordinates with optional altitude and horizontal accuracy plus source, confidence, and route-distance diagnostics. Route import validates coordinate ranges, optional values, and the canonical time window. Preferred workout source selects the summary representation independently of enrichment: a Strava/Health Connect summary can use a HIGH-confidence exact or overlapping Samsung route. An empty identity route does not hide a valid matched enrichment route. If the chosen sensor session has no route, a unique route from the matcher's existing HIGH-confidence assessments can supply it. Ambiguous matches cannot supply a new route, and points from different sessions are never merged. `RunSplit` is separate from planned workout segments. The implemented derivation priority is source laps, recorded route, distance series, speed series, then unavailable; only recorded-route derivation is active today.

Routes and splits resolve through the established stable physical-run ID for both History and Run Detail. The existing Room schema retains its provider-record foreign key: writes use the preserved identity anchor (the record whose ID equals the physical-run ID, or a deterministic preserved group member for legacy custom identities), while repository results expose the physical-run ID as their owner. Legacy provider-keyed routes remain readable through the same confirmed group. Successful refresh atomically replaces the anchor's route/splits and consolidates obsolete route rows only inside that physical group. Source-preference changes force enrichment refresh when the selected summary changes; cached version-4 metrics refresh once through pipeline version 5. Existing route evidence remains available if Samsung access temporarily fails, and no app-data reset or schema migration is required.

Route-derived distance uses geodesic point-to-point distance with duplicate/noise suppression, impossible-jump rejection, and sampling-gap safeguards. A route within 5% of canonical distance may receive one bounded uniform distance-axis calibration so kilometre boundaries retain the route's temporal shape while the canonical summary remains authoritative. Larger disagreement lowers confidence, and disagreement above 15%, excessive rejected points, or a gap above five minutes suppresses splits. Kilometre boundary timestamps are interpolated between route points. Split heart rate and cadence are averages of genuine timestamped samples inside each split window; absent evidence remains absent.

Provider session boundaries can differ for the same outing. A Strava canonical workout may end before its Galaxy Watch recording while still being fully covered by that recording. In that case, OpenRunCoach clips Samsung route, heart-rate, speed, cadence, and power samples to the canonical start and end, then recomputes sensor summaries. This also applies to small trims within exact-match tolerance. Canonical Health Connect distance and duration remain authoritative. Full Samsung session values such as VO₂max, calories, and elevation remain diagnostic context when the recording extends beyond the canonical interval and are not silently applied to the shorter activity. Overlapping-session elevation derivation remains unimplemented. Legacy route lookups also clip against the current canonical interval and suppress outdated splits until normal refresh rebuilds them.

The diagnostic view separates the canonical workout, exact provider copy, and best enrichment candidate. Its expandable candidate list includes the Samsung data-point UID and timestamps, source application, device identity, distance, duration, ExerciseLog presence, and heart-rate, speed, cadence, VO₂max, elevation, and power capabilities. Ambiguous sensor candidates remain visible and can be selected manually for that run. These metrics remain experimental. Cadence is not used by the coaching engine.

Debug builds additionally expose the stable physical-run ID, selected summary provider-record ID/source, preferred source, Samsung enrichment session/confidence, persisted route source/point count/physical owner, and Samsung sensor counts used in the unified result. See the [source-selection verification checklist](docs/source-enrichment-verification.md) for the Thursday preference A/B check, Tuesday diagnostics, duplicate/association regressions, and trimmed-window checks.

The developer diagnostics preserve the Samsung SDK operation, exception class, numeric error code, message, resolution availability, platform detection, exercise permission state, and developer authorization state. Missing exercise permission is reported separately from platform availability. Resolvable platform actions are launched only after the developer selects the resolution button; no external Samsung screen opens automatically.

Samsung support is optional. Health Connect and all normal app features continue to work when Samsung Health is absent, permission is denied, the platform is unsupported, or Samsung access is unavailable. The Samsung Health Data SDK requires Android 10 or newer, so the enrichment is unavailable on older supported OpenRunCoach devices.

## Unified run metrics

`RunMetrics` is a provider-independent **measurement/enrichment container**, not the authoritative whole-run activity summary. Its distance can be a Health Connect time-window aggregate or an enriched provider measurement, and may legitimately differ from the selected provider's edited/trimmed session distance. Primary/secondary values and their provenance stay preserved. Health Connect owns the canonical provider-record identity; optional trusted Samsung Health enrichment adds independent route/HR/cadence/speed evidence.

`RunActualResolver` reuses `RunSummary` for one authoritative whole-run actual: stable physical identity, the selected canonical start/end, valid canonical duration and distance, and average pace calculated only as duration × 1000 / distance. Stored pace and sensor speed never replace that calculation. Missing/invalid duration first uses the positive canonical start/end interval. A missing/invalid summary value can otherwise use only a positive finite, HIGH-confidence, non-conflicting `RunMetrics` value with the same canonical provider-record ID and **exactly the same interval**; otherwise it is unavailable (the existing non-null duration field uses zero, with explicit provenance and unavailable display formatting). A fallback may therefore combine canonical duration with a matched measured distance, but this is explicit in per-value provenance. No longer original interval can refill a trimmed run's missing distance.

Presentation repository reads resolve this summary and observe metric fallback changes; raw provider mapping remains separate for import, duplicate matching and canonical selection. History, Latest Run, Detail Overview/Analysis/charts, split delta baselines, equal-distance halves, workout actuals/responses, goal observed distance and longitudinal Profile pace/volume use that semantic. HR/cadence/speed and genuine route geometry remain independent enrichment. Source preference may legitimately select a different summary, while physical route ownership and enrichment remain independent.

Home's daily/weekly pace and four-week pace comparison use summed canonical actual durations divided by summed canonical actual distances, never means of per-run paces or duplicate provider volumes. Missing actual duration/distance leaves aggregate pace unavailable. Profile supplies resolved actuals to the unchanged `TrainingEvidencePolicy` through a private, non-persisted input adapter; its five-minute standalone fragment guard, associated-workout exceptions, and other eligibility rules are unchanged. Saved profiles await normal recalculation; persisted executions retain their historical completion decisions. Completed-workout/Plan reads project current canonical actual distance/duration without rewriting those decisions. Dump creation does not rebuild or persist either.

The shared split presentation uses canonical actual pace for Δ = split pace − whole-run pace and canonical actual distance for equal-distance halves. Partial split duration/normalized pace and the production route calibration/boundary/interpolation rules are unchanged. Cache validation also recognizes a newly available canonical-distance fallback; reads remain non-mutating.

Per-run dumps now expose `canonicalActual`, `enrichmentMeasurements`, existing route distance/calibration metadata and split `baselineSource`/provenance. A read-only integrity check reports REVIEW when canonical actual pace differs from the production-consumed split baseline by more than 0.001 s/km. Provider raw distances need not equal canonical distance. See the [whole-run actual consistency report and all nine manual checks](docs/whole-run-actual-semantics-verification.md). Tests were added but not run; build/device verification is pending.

Every stored metric summary preserves its primary provider, confidence, verification state, and optional secondary provider value. Health Connect remains primary for overlapping values such as distance and duration. Exact Samsung sessions can verify shared values within centralized tolerances; material differences are marked as conflicts instead of silently replacing Health Connect data. Overlapping sessions contribute only sufficiently covered clipped sensor series, with confidence based on sample count and time coverage. Low-confidence or ambiguous matches are not used automatically. A saved manual selection must still meet the physical relationship and sensor requirements.

Cadence keeps the Samsung session average and a separate moving average. Raw zero cadence samples remain stored, while the moving average and display line exclude zero cadence and samples aligned with effectively zero speed.

Heart-rate, speed, cadence, and power samples are stored in Room using provider-independent records keyed by run, metric type, timestamp, and source. Each refresh replaces the run's sample set, so repeated refreshes do not duplicate data. Overlapping Samsung sessions are clipped to the canonical Health Connect run window before persistence. Sample provenance and confidence are retained without storing Health Connect or Samsung SDK objects.

Run Detail prepares charts in Android-independent domain code. The pace chart validates raw speed, applies a configurable 10-second rolling speed average, and then converts speed to pace without changing stored samples. Zero, invalid, and missing speed create chart gaps while sustained valid walking speed remains visible. Its average reference comes from canonical distance and duration, and Fastest 30 sec pace uses the best rolling 30-second average speed instead of one instantaneous GPS sample. A dynamic min/km scale keeps faster pace higher and rejects isolated display outliers. Cadence stops also render as gaps. Deterministic downsampling limits normal charts to about 240 points while preserving the overall trend and the first and last samples. Heart-rate and cadence charts appear only when data exists, and the model already supports power samples without showing an empty power chart.

The stored series and chart preparation layer can later support heart-rate drift, aerobic decoupling, cadence and pace drift, walk-break and interval detection, pace-versus-heart-rate analysis, pace-versus-cadence analysis, and technique analysis. Those analyses are not implemented yet.

### History and completed-run presentation

History lazily renders compact month-grouped canonical activity rows, using the associated prescription title or the neutral “Run” fallback (never a pace-inferred classification). Real routes share one latitude-aware, aspect-preserving silhouette renderer with Run Detail. Route geometry is remembered, drawing paths are size-cached, and deterministic sampling caps thumbnails at 180 points and detail routes at 2,000 points. Missing routes collapse without placeholders.

Completed runs have local Overview, Splits, Analysis, and Coach tabs below a compact header. An actual associated workout type reuses the existing bundled workout hero and a dark text gradient; unmatched runs keep a neutral header. Recovery has its own existing image. Overview uses one equal-column icon/value/label metric card (four metrics when available; a single scrolling row when accessibility needs more width). Map and plot heights adapt to the available viewport and font scale; the main metrics/map/selector/chart/summary are sized for a single normal phone viewport, while small screens and accessibility text retain scrolling. History adds centrally defined restrained accents from the actual associated workout type, never from pace or a guessed title. Analysis uses one compact outcome card, an aligned planned/actual table, continuity and confidence; full factual reasons are collapsed under Evidence. Assignment/reassignment/unassignment stays available in a compact lower section. Segment pace targets remain separate from whole-run average pace. Coach defaults to the existing recommendation, at most four structured observations and one action-consistent implication; full original evidence and checkout summaries remain under More details. Pending checkout stays accessible above tab content; Data sources stays collapsed and Ignore run stays secondary. No source-selection, persistence, association, or coaching semantics change.

Detail street context is rendered by MapLibre Native Android, independently of the map-data provider. History retains lightweight Canvas thumbnails. Debug defaults to OpenFreeMap's key-free dark style; release defaults to Canvas until a reviewed provider is configured. Set the Gradle property `routeMapStyleUri` to a compatible HTTPS dark style or an `asset://` style backed by your chosen tiles. A blank property disables network maps. No public OpenStreetMap tile endpoint or provider key is hardcoded. Review provider terms, attribution, capacity and privacy before distribution; the development default is not a promised production SLA. The map is lifecycle-managed, geometry is remembered, and camera fitting is keyed to route/size changes, not chart tabs. Real streets render behind the mint route; during loading or tile/style failure the existing Canvas route silhouette remains visible. Cached street tiles may remain usable offline. Attribution stays enabled. MapLibre loads street tiles, not routes from an upload endpoint.

Header metadata observes the persisted physical-run association and prescription directly, including assignments before checkout; execution updates no longer compete to set the hero. Initial association loading finishes before the header is chosen. The shallow header, text-measured metric strip and viewport budget reserve room for the full Overview summary, with approximately 88–116 dp plots and 120–192 dp maps at default fonts. Actual one-viewport fitting remains a manual device check. Analysis prioritizes Outcome / Session cost / Volume, compact Planned/Actual distance/pace/RPE rows, continuity and confidence. Time-based prescriptions retain a duration comparison; extra adherence facts move into collapsed Evidence below the compact association actions.

Charts appear only with a drawable real sample segment and keep elapsed-time axes. Pace/Heart Rate/Cadence use one rounded local pill selector with equal segments and 48 dp touch targets, not a second navigation underline. With only one genuine metric, the selector is omitted. All selections share the same plot and two-column summary footprint. Compact area fills preserve sample gaps; the existing robust pace scale remains the basis of the visual viewport. Legitimate whole-run target bands extend the display domain if needed; quality work targets remain excluded. Average pace/target summaries put values above labels in equal columns, with a restrained parallel-bar target key beside the label. Fastest 30 sec and relevant off-scale clipping diagnostics remain under Data sources → Recording diagnostics, without modifying stored values or continuity evidence. HR and cadence summaries remain real stored metrics; cadence keeps the existing moving-average preference and explicitly labels it when used. An unavailable second measurement is omitted, never substituted with zero.

Splits retain the existing boundaries and sensor aggregation. Their tight, explicitly aligned primary table is Km/Pace/Δ/HR; full kilometres use their ordinal and partial splits show actual fractional distance. Δ = split pace − whole-run average pace, including normalized partial-split pace. Negative deltas get a restrained mint accent; positive deltas stay neutral. Cadence and exact duration remain under Split details. The summary shows only average pace and positive/negative/even half-run pacing, not fastest/slowest split rankings. Equal-distance halves reuse the existing route validator/calibrator's read-only cumulative distance/time trace; they interpolate the timestamp at half the actual distance, retaining walking/stopped elapsed time. Delta is second-half pace − first-half pace: negative is faster, positive slower, and within ±3 sec/km is Even split. Results require at least 1 km total distance, non-low route confidence, no large gaps, samples no more than 120 seconds apart, calibrated coverage of actual distance, and recording edges within 15 seconds of the canonical window. Insufficient evidence suppresses the calculated metric; Half-run details remains collapsed by default when evidence exists. Nothing is persisted, and no response/continuity/coaching evaluator changes. See the [original verification checklist](docs/history-completed-run-verification.md), [focused polish verification checklist](docs/history-completed-run-polish-verification.md), and [final visual polish checklist](docs/completed-run-final-polish-verification.md).

The [completed-run UI matching report and complete manual checklist](docs/completed-run-ui-matching-verification.md) cover the latest presentation pass. Build, test, screenshot and device checks are pending user verification.

## Athlete Performance Profile

The Performance Profile is reproducible derived data built from canonical runs, unified `RunMetrics`, and post-run feedback. It summarizes 7-day and 28-day volume, completed calendar-week averages, running frequency, active weeks, current weekly streak, longest recent run, subjective feedback, VO₂max, and conservative observational trends. Duplicate and non-canonical records do not contribute.

Run relevance and training evidence eligibility are separate. `TrainingEvidencePolicy` makes purpose-specific decisions with explicit reasons. Standalone recordings need at least five minutes of usable recorded activity for meaningful session frequency, easy baselines and capability evidence; authoritative associated prescriptions can support shorter sessions. This conservative recording-fragment guard is a product heuristic, not a universal physiological threshold. Actual positive recorded volume and safety/feedback evidence remain available for short or aborted active activities. `runsLast7Days`, `runsLast28Days`, weekly session counts, active weeks, streak and frequency describe meaningful sessions; total canonical history count remains descriptive. Ignored activities remain excluded under the existing user-controlled semantics.

Easy-running baselines require a completed run with RPE 4 or lower, easy or appropriate cardio effort, no significant pain, and valid distance, duration, and pace. Missing feedback does not qualify a run as easy. The profile uses canonical actual pace for eligible runs, with medians and observed ranges for easy pace, heart rate, and unified cadence; it does not compare cadence against a universal target or generate technique advice.

Checkout belongs to the stable physical activity. Every production consumer resolves at most one authoritative whole answer, independently of the selected canonical provider. New writes target the preserved physical identity anchor; legacy custom identities retain a deterministic member as the storage anchor to respect the existing Room foreign key. A sole legacy answer resolves immediately and can move safely on an explicit edit. Existing anchor feedback takes precedence over legacy duplicates; otherwise the earliest created answer, then feedback ID, provides deterministic authority. Conflicting rows and notes remain preserved and are reported in diagnostics, without field-by-field merging. Feedback aggregates count each physical run once.

Persisted splits are derived cache data. `RunSplitResolver` reuses valid cached boundaries and otherwise calls the production `RouteSplitDeriver` against the resolved physical route and saved canonical sensor samples. Old routed runs can therefore expose splits without reimport. Reads, including diagnostic exports, never persist this fallback. Normal metric refresh separately backfills missing or stale caches transactionally and idempotently, without changing route points. Diagnostics include feedback ownership/conflicts, per-purpose evidence decisions, and persisted versus resolved split counts and source. The [data consistency report and manual checklist](docs/data-consistency-verification.md) explain verification and the remaining real-device checks.

Standard session benchmarks are supported for 1K, 5K, 10K, half marathon, and marathon distances using a centralized distance tolerance. Personal best and most recent results are kept separately. A 4.8 km run is not treated as a 5K benchmark. Best-segment detection inside longer runs, manual results, and race-result imports remain planned.

Profile confidence is based on explicit recent-run count, represented weeks, feedback coverage, heart-rate and pace availability, and unified-metric reliability. The Performance screen exposes these reasons and data-quality gaps. The latest snapshot is stored as a rebuildable Room cache and recalculated after run synchronization or canonical changes, feedback changes, and metric enrichment. The Goal System uses this profile for context and captures a stable starting snapshot. It does not generate race predictions, zones, or training plans.

## Data sources and duplicates

Multiple applications can write copies of the same workout into Health Connect. OpenRunCoach therefore:

1. Preserves every imported source record.
2. Stores source package, display name, and available device metadata.
3. Compares provider records using explicit `HIGH`, `AMBIGUOUS`, or `SEPARATE` evidence from temporal overlap/containment, source relationship, and compatible distance.
4. Groups only `HIGH` matches; trimmed provider representations can group, while ambiguous or merely same-date runs remain separate.
5. Preserves a stable physical-run ID across provider additions or edits and selects one current canonical representation using preferred source, recorder and device evidence, data completeness, source timestamps, and deterministic fallback rules.

Active canonical physical runs are used for history, training volume, latest-run summaries, goal evidence, AthleteState, and coaching. Non-canonical provider records remain as provenance and appear subtly in Run Detail. Ignoring a run hides the entire physical activity from normal consumers without deleting its provider records; any derived workout association/execution is removed so it cannot advance training, and History's ignored view can restore the run for reassignment. The preferred workout source can be changed in Settings.

## Privacy

OpenRunCoach is local-first:

- Health and coaching data remain on the device.
- No cloud account is required.
- No application server is required.
- AI or an LLM is not required for core coaching.
- Full run traces, metrics, and coaching records are not uploaded. Optional street maps fetch style/tile resources externally; the tile provider sees requested geographic areas and ordinary HTTP metadata, not the local route GeoJSON. Debug uses a development map provider by default; a blank map style disables requests, and release maps require explicit provider configuration.

## Architecture

The app follows MVVM with UI, domain, and data boundaries.

```text
UI
└── Jetpack Compose screens and screen-level ViewModels

Domain
├── canonical run selection, resolved actual-summary semantics and duplicate diagnostics
├── provider-independent RunMetrics and verification rules
├── Athlete Performance Profile calculations
├── goal capability and assessment rules
├── structured workouts, estimates, and descriptions
├── coaching rules
├── workout lifecycle and readiness evaluation
├── next-workout generation
└── provider-independent coaching rules shared by debug simulation

Data
├── Health Connect
├── optional Samsung Health diagnostics and best-effort metric enrichment
├── Room
├── persistent goals and starting snapshots
├── DataStore settings
└── repositories
```

Architecture rules:

- Health Connect record classes stay in the Health Connect data layer.
- Samsung SDK classes stay in the Samsung data adapter.
- Provider adapters convert health records into `RunMetrics` before general domain or UI use.
- Room entities are mapped to domain models before reaching Compose.
- Repositories own data access.
- Business and transition logic stays outside composables.
- Coaching logic remains independent of whether its persisted inputs came from a real provider or the debug simulator.

## Debug simulation environment

Debug builds provide Settings → Developer Tools → Simulation. Release builds contain neither scenario controls nor synthetic-run creation. Loading a preset clears and seeds only a dedicated simulation Room database, switches to a fixed injectable clock, and shows a persistent Simulation Mode banner. Exiting returns the app to the untouched production database.

Presets create deterministic prescriptions, canonical-run inputs, optional metrics, movement samples, and checkout feedback. After that input boundary they use the normal persistence and product pipeline: workout association, `WorkoutExecutionResult`, `WorkoutResponse`, longitudinal `AthleteState`, coaching decisions, strict goal completion, and the shared product screens. The debug Simulation screen exposes the rebuilt Athlete State as readable capability, confidence, volume, continuity, session-cost, and readiness evidence rather than a synthetic fitness score. Scenarios I–M cover a hard first 5K, established endurance followed by a hard 5K, one bad run after good history, repeated positive Easy training, and little evidence. Scenario H persists the authoritative planned execution before a later unrelated same-day run so the newer run cannot replace that association. Walking scenarios provide timestamped speed samples to the existing continuity evaluator; missing metrics remain absent.

| Real workflow | Simulation workflow |
| --- | --- |
| Health Connect data | `SimulationDataSource` |
| Production Room database | Dedicated simulation Room database |
| Real clock | Fixed clock |
| Provider activity inputs | Deterministic synthetic run and metric inputs |

The environment is a manual development tool, not a production user feature. Scenario loading never injects final response, coaching-action, or goal-completion results.

The debug-only Goal Strategy section shows the active goal/status, target-time context when applicable, capability priorities, confidence, and the exact shared resolver reasons. **Rebuild Athlete State** also recalculates strategy from the same rebuilt state. Presets GS1–GS8 add beginner 5K completion, near-continuous 4.8 km, established 5K performance, 5K target 30:00, 10K from a 5–6 km base, half-marathon completion, missing evidence, and a high-cost latest run with retained 10K direction. A–M remain available unchanged. Goal strategy and simulation do not assign any goal date; Completion Run dates continue to belong only to plan milestones.

See the [Goal Strategy verification checklist](docs/goal-strategy-verification.md) for the exact GS1–GS8 outputs and reasons, determinism check, and existing continuity/navigation regression checks.

## Roadmap

- [x] Workout lifecycle and state machine
- [x] Health Connect Data Inspector
- [x] Experimental Samsung Health Data SDK investigation
- [x] Unified `RunMetrics` model
- [x] Automatic metric enrichment and Run Detail time-series charts
- [x] Anthracite and teal application theme
- [x] Athlete Performance Profile
- [x] Longitudinal Athlete State and current-readiness interpretation
- [x] Pure goal-specific capability strategy with debug GS1–GS8 verification
- [x] Goal System
- [x] Structured Workout Model
- [x] Training Block / Weekly Plan Generation
- [x] Training Data Assessment and goal-specific distance semantics
- [ ] Pre-run Check-in / Adaptive Workout Adjustment
- [x] Basic pre-run check-in and duration adjustment
- [x] Unambiguous same-date planned versus actual workout association at checkout
- [ ] Post-run checkout lifecycle integration
- [ ] Post-run Adaptive Plan Progression
- [ ] Threshold / Zone Estimation
- [ ] Technique, cadence, and running economy analysis
- [x] Debug simulation mode using production screens and isolated storage
- [ ] Advanced training metrics
- [ ] Optional additional data providers

## Development

### Requirements

- JDK 17
- Android SDK 36 for compilation; minimum supported Android API 26
- Kotlin 2.3.20
- Gradle 9.1 through the included wrapper
- Android Studio or VS Code
- ADB for installing and debugging builds
- An Android phone with Health Connect for real workout import testing

### Optional Samsung Health Data SDK setup

The proprietary Samsung SDK binary is intentionally not stored in this repository and Gradle does not download it. Developers who want to build the Samsung diagnostic integration must:

1. Download the SDK through the [Samsung Health Data SDK app creation page](https://developer.samsung.com/health/data/process.html) after accepting Samsung's SDK terms.
2. Copy its API AAR to `app/libs/samsung-health-data-api.aar`.
3. Enable Samsung Health Data SDK Developer Mode for local development and data-read testing. Developer Mode is strictly a development facility and is not an end-user setup step.

The Samsung Health Data SDK connection and exercise reads have been confirmed on a development device with Developer Mode enabled. The app module includes the local Samsung Health Data SDK AAR, applies the Kotlin Parcelize plugin required by the SDK's Parcelable APIs, and includes Gson for SDK serialization. The AAR path is listed in `.gitignore`. Public distribution of an app using the SDK may require Samsung registration and partnership approval for the app package and signing certificate.

### Common commands

```bash
./gradlew test
./gradlew assembleDebug
./gradlew installDebug
```

The debug APK is generated at `app/build/outputs/apk/debug/app-debug.apk`.

### Repository instructions

[`AGENTS.md`](AGENTS.md) defines the project's architecture, health-data, development, and Git rules. Codex and other automated contributors should read and follow it before changing the project.
