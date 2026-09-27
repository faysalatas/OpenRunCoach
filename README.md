# OpenRunCoach

OpenRunCoach is a local-first adaptive running coach for Android. It reads workout data from Health Connect, combines objective training data with subjective athlete feedback, and adapts future training recommendations.

The long-term goal is a transparent, explainable running coach inspired by feedback-driven training systems. Every recommendation should be traceable to workout data, athlete feedback, and explicit coaching rules.

## Current features

- Native Android app written in Kotlin and Jetpack Compose
- Material 3 UI with an anthracite and teal dark palette plus system light-theme support
- Bottom navigation with Home, History, Plan, and Settings
- Week and day selector with run indicators
- Read-only Health Connect integration
- Compatibility with Samsung Health and Galaxy Watch workouts when Samsung Health exposes them through Health Connect
- Import of running exercise sessions from the previous 90 days
- Local run history stored with Room
- Source application attribution and device metadata when available
- Duplicate candidate detection without deleting source records
- Deterministic canonical run selection for history, totals, and coaching
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
- Compact Run Detail charts for pace, heart rate, and cadence
- Persistent long-lived athlete goals with profile-based context, target times, and explainable assessments
- Provider-independent structured workouts with ordered segments, repeat blocks, intensity guidance, estimates, purposes, and cues
- Persistent deterministic training plans with weekly navigation, explainable blocks, recovery weeks, and goal-aware workout selection

OpenRunCoach can generate a base plan explicitly from an active goal and the current Performance Profile. On post-run checkout, a run is associated automatically only when it is the sole canonical run for the date and exactly one uncompleted active-plan prescription is compatible with its observed distance or duration. Ambiguous dates, duplicate/non-canonical records, incompatible prescriptions, and runs predating the prescription remain unassociated. Existing unambiguous checkouts are reconciled into the same persisted execution association when a plan is loaded. A completed canonical run is also evaluated independently against an active Complete Distance goal, regardless of the planned workout's name. Broader production plan adaptation after imports is not yet implemented; the debug simulation environment exercises that completed-workout pipeline with isolated deterministic inputs.

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

Database version 15 removes the former goal date column and moves scheduling state onto `training_plans`. During migration, an existing active Complete Distance plan receives the old persisted Completion Run date, or the legacy goal date when no explicit milestone date exists. Legacy dates on other goal types are not treated as Completion Run intent because that meaning is ambiguous; the goal itself is preserved without a date. The migration retains goals, distinct assessment reasons in deterministic per-goal order, plans, weeks, blocks, and workout references without destructive fallback. Database version 16 adds the one-to-one persisted workout execution result and its actual-run association.

`GoalCompletionEvaluator` uses the canonical completed run, unified `RunMetrics`, lifecycle state, and feedback to explain whether the full continuous target was demonstrated; a 4.8 km run and a 5 km run with a clearly sustained walking/stopped interval do not complete a continuous 5K goal. Any qualifying 5.00+ km run completes the goal immediately regardless of workout name, clears the future Completion Run milestone, and completes the active plan. If history already demonstrates the distance, the UI says so and suggests Improve Performance or Target Time without changing the user's selection.

For `IMPROVE_PERFORMANCE` and `TARGET_TIME`, race distance is not a training-distance cap. Longer aerobic runs may exceed the race distance, and supported athletes can receive sustainable goal-distance practice before a final target-time attempt. Weekly displayed distance is the sum of explicit prescribed segment distances, while displayed weekly duration is derived when every segment has enough duration or pace information.

Every week stores its focus, load classification, targets, and generation reasons. Each generated workout keeps its purpose, cues, structured segments, and a reason for inclusion. The Home screen shows only a compact current-week summary and link to the full plan.

Normal training prescriptions allow purpose-aware real-world variation. Workout completion and prescription adherence are distinct: a slightly short easy or recovery run can be completed with a minor distance deviation, while an RPE or trusted pace outside the intended easy range remains a meaningful prescription deviation without erasing the completed training volume. Interval, stride, threshold, and drill adherence depends on the relevant quality work when that evidence exists. Actual distance and duration remain authoritative for volume and history; accepting a session never replaces actual metrics with prescribed values. Persisted execution results keep as-planned, minor-deviation, meaningful-prescription-deviation, partial, and aborted outcomes separate, and completed sessions are visibly marked Completed in the Plan after restart. Strict Goal achievement remains a separate evaluation against the observed distance and continuity criteria.

`WorkoutExecutionResult` is the persisted association describing what actual canonical run was performed against which planned or adjusted prescription. `WorkoutResponse` is deterministic derived evidence describing one session: completion, purpose-aware adherence, actual volume, effort, continuity, pace response, pain, session cost, confidence, and concise reasons. Re-analysis uses stable run and workout identities and replaces no persisted truth, so reopening the app cannot accumulate duplicate response records.

`AthletePerformanceProfile` owns descriptive historical evidence and aggregates such as recent actual volume, frequency, consistency, benchmarks, continuity, and supported pace/heart-rate baselines. `AthleteState` is a deterministic, rebuildable coaching interpretation composed from that profile, canonical history, and `WorkoutResponse`s. It keeps longer-term demonstrated and comfortable/tolerated capability separate from acute readiness and recovery signals. Missing evidence lowers confidence rather than becoming inability or a hard distance ceiling; a hard performance does not automatically establish comfortable training tolerance, and one bad workout can make current readiness conservative without erasing accumulated endurance evidence.

`GoalStrategy` decides which goal-independent capabilities matter for the active Goal. A later Adaptation layer will decide what should change, and `NextWorkoutPlanner` will eventually turn that decision into a concrete structured workout. Neither full adaptation decisions nor next-workout generation are part of the current Athlete State foundation.

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

Route inspection requires an explicit action and consent where Health Connect requires it. Raw route points are not stored in Room.

### Experimental Samsung Health enrichment

Health Connect remains the primary, provider-independent workout source. The optional Samsung Health Data SDK integration supports diagnostics and best-effort normal metric enrichment for Samsung users. It does not replace canonical Health Connect run identity or affect canonical selection, coaching, or workout generation.

For a selected canonical run, the Samsung adapter requests read access only to Samsung's `DataTypes.EXERCISE`. It searches all nearby Samsung running and track-running sessions, then makes two decisions. The identity match finds the Samsung provider copy using source application and physical-workout evidence. The enrichment match classifies a candidate as an exact session or an overlapping session. Exact sessions require close boundaries and distance. Overlapping sessions require an extremely close start, high coverage of the canonical activity, a trusted Watch source, and substantial ExerciseLog data; Watch provenance alone is insufficient. Source application equality is not required for enrichment.

Provider session boundaries can differ for the same outing. A Strava canonical workout may end before its Galaxy Watch recording while still being fully covered by that recording. In that case, OpenRunCoach clips Samsung heart-rate, speed, cadence, and power samples to the canonical start and end, then recomputes their summaries. Canonical Health Connect distance and duration remain authoritative. Full Samsung session values such as VO₂max, calories, and elevation remain diagnostic context when boundaries materially differ and are not silently applied to the shorter canonical activity. Timestamped route-altitude clipping is planned before overlapping sessions can contribute elevation.

The diagnostic view separates the canonical workout, exact provider copy, and best enrichment candidate. Its expandable candidate list includes the Samsung data-point UID and timestamps, source application, device identity, distance, duration, ExerciseLog presence, and heart-rate, speed, cadence, VO₂max, elevation, and power capabilities. Ambiguous sensor candidates remain visible and can be selected manually for that run. These metrics remain experimental. Cadence is not used by the coaching engine.

The developer diagnostics preserve the Samsung SDK operation, exception class, numeric error code, message, resolution availability, platform detection, exercise permission state, and developer authorization state. Missing exercise permission is reported separately from platform availability. Resolvable platform actions are launched only after the developer selects the resolution button; no external Samsung screen opens automatically.

Samsung support is optional. Health Connect and all normal app features continue to work when Samsung Health is absent, permission is denied, the platform is unsupported, or Samsung access is unavailable. The Samsung Health Data SDK requires Android 10 or newer, so the enrichment is unavailable on older supported OpenRunCoach devices.

## Unified run metrics

`RunMetrics` is the provider-independent data foundation for workout metrics. Health Connect always owns the canonical run ID, session start and end, and default metric values. An optional trusted Samsung Health match can add metrics that Health Connect did not expose, including the cadence and elevation values confirmed during development.

Every stored metric summary preserves its primary provider, confidence, verification state, and optional secondary provider value. Health Connect remains primary for overlapping values such as distance and duration. Exact Samsung sessions can verify shared values within centralized tolerances; material differences are marked as conflicts instead of silently replacing Health Connect data. Overlapping sessions contribute only sufficiently covered clipped sensor series, with confidence based on sample count and time coverage. Low-confidence or ambiguous matches are not used automatically. A saved manual selection must still meet the physical relationship and sensor requirements.

Cadence keeps the Samsung session average and a separate moving average. Raw zero cadence samples remain stored, while the moving average and display line exclude zero cadence and samples aligned with effectively zero speed.

Heart-rate, speed, cadence, and power samples are stored in Room using provider-independent records keyed by run, metric type, timestamp, and source. Each refresh replaces the run's sample set, so repeated refreshes do not duplicate data. Overlapping Samsung sessions are clipped to the canonical Health Connect run window before persistence. Sample provenance and confidence are retained without storing Health Connect or Samsung SDK objects.

Run Detail prepares charts in Android-independent domain code. The pace chart validates raw speed, applies a configurable 10-second rolling speed average, and then converts speed to pace without changing stored samples. Zero, invalid, and missing speed create chart gaps while sustained valid walking speed remains visible. Its average reference comes from canonical distance and duration, and Fastest 30 sec pace uses the best rolling 30-second average speed instead of one instantaneous GPS sample. A dynamic min/km scale keeps faster pace higher and rejects isolated display outliers. Cadence stops also render as gaps. Deterministic downsampling limits normal charts to about 240 points while preserving the overall trend and the first and last samples. Heart-rate and cadence charts appear only when data exists, and the model already supports power samples without showing an empty power chart.

The stored series and chart preparation layer can later support heart-rate drift, aerobic decoupling, cadence and pace drift, walk-break and interval detection, pace-versus-heart-rate analysis, pace-versus-cadence analysis, and technique analysis. Those analyses are not implemented yet.

## Athlete Performance Profile

The Performance Profile is reproducible derived data built from canonical runs, unified `RunMetrics`, and post-run feedback. It summarizes 7-day and 28-day volume, completed calendar-week averages, running frequency, active weeks, current weekly streak, longest recent run, subjective feedback, VO₂max, and conservative observational trends. Duplicate and non-canonical records do not contribute.

Easy-running baselines require a completed run with RPE 4 or lower, easy or appropriate cardio effort, no significant pain, and valid distance, duration, and pace. Missing feedback does not qualify a run as easy. The profile uses medians and observed ranges for easy pace, heart rate, and unified cadence; it does not compare cadence against a universal target or generate technique advice.

Standard session benchmarks are supported for 1K, 5K, 10K, half marathon, and marathon distances using a centralized distance tolerance. Personal best and most recent results are kept separately. A 4.8 km run is not treated as a 5K benchmark. Best-segment detection inside longer runs, manual results, and race-result imports remain planned.

Profile confidence is based on explicit recent-run count, represented weeks, feedback coverage, heart-rate and pace availability, and unified-metric reliability. The Performance screen exposes these reasons and data-quality gaps. The latest snapshot is stored as a rebuildable Room cache and recalculated after run synchronization or canonical changes, feedback changes, and metric enrichment. The Goal System uses this profile for context and captures a stable starting snapshot. It does not generate race predictions, zones, or training plans.

## Data sources and duplicates

Multiple applications can write copies of the same workout into Health Connect. OpenRunCoach therefore:

1. Preserves every imported source record.
2. Stores source package, display name, and available device metadata.
3. Detects possible duplicates using similar start time, duration, and distance.
4. Groups duplicate candidates.
5. Selects one canonical run using preferred source, recorder and device evidence, data completeness, source timestamps, and deterministic fallback rules.

Canonical runs are used for history, training volume, latest-run summaries, and coaching. Non-canonical records remain available in run details for diagnostics. The preferred workout source can be changed in Settings.

## Privacy

OpenRunCoach is local-first:

- Health and coaching data remain on the device.
- No cloud account is required.
- No application server is required.
- AI or an LLM is not required for core coaching.
- Health data is not transmitted externally by default.

## Architecture

The app follows MVVM with UI, domain, and data boundaries.

```text
UI
└── Jetpack Compose screens and screen-level ViewModels

Domain
├── canonical run selection and duplicate diagnostics
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

## Roadmap

- [x] Workout lifecycle and state machine
- [x] Health Connect Data Inspector
- [x] Experimental Samsung Health Data SDK investigation
- [x] Unified `RunMetrics` model
- [x] Automatic metric enrichment and Run Detail time-series charts
- [x] Anthracite and teal application theme
- [x] Athlete Performance Profile
- [x] Longitudinal Athlete State and current-readiness interpretation
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
