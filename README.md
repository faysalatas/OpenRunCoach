# OpenRunCoach

OpenRunCoach is a local-first adaptive running coach for Android. It reads workout data from Health Connect, combines objective training data with subjective athlete feedback, and adapts future training recommendations.

The long-term goal is a transparent, explainable running coach inspired by feedback-driven training systems. Every recommendation should be traceable to workout data, athlete feedback, and explicit coaching rules.

## Current features

- Native Android app written in Kotlin and Jetpack Compose
- Material 3 UI with system light and dark themes
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
- Rule-based readiness evaluation and safety overrides
- Basic next-workout generation in the domain workflow
- Deterministic developer coaching simulator with built-in scenarios
- Health Connect Data Inspector for canonical runs, with per-metric permission and source diagnostics
- Experimental Samsung Health Data SDK comparison for canonical runs, including ExerciseLog cadence diagnostics
- Persistent athlete goals with profile-based context, target times, optional dates, and explainable assessments
- Provider-independent structured workouts with ordered segments, repeat blocks, intensity guidance, estimates, purposes, and cues

Automatic matching between a planned workout and an imported Health Connect run is not implemented yet. Because of that, the production workflow does not automatically create the next real workout after an import; the domain simulator exercises that complete loop today.

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

Target-time goals accept a finish duration and show the derived pace per kilometre. When a verified benchmark exists, the review shows the required time and percentage change. An optional future target date can be stored without requiring a race event.

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

Run Detail provides an **Inspect Health Data** action for canonical runs. The inspector reads the selected session directly from Health Connect, reports record and sample counts, summaries, permissions, source packages, and unavailable or failed reads independently, and refreshes the persisted unified metric summary. Raw samples are not persisted.

The Android Health Connect API supports record types such as cadence, VO₂max, elevation, power, calories, steps, and exercise routes. API support does not mean a particular watch or provider actually records or publishes that metric. The inspector exists to show the difference on a real device. It source-filters results to the canonical workout provider and labels data found only from another provider instead of combining it silently.

Route inspection requires an explicit action and consent where Health Connect requires it. Raw route points and series samples are not stored in Room.

### Experimental Samsung Health enrichment

Health Connect remains the primary, provider-independent workout source. The optional Samsung Health Data SDK integration enriches the existing inspector for Samsung users; it does not replace imported Health Connect values or affect canonical selection, coaching, or workout generation.

For the selected canonical run, the inspector requests read access only to Samsung's `DataTypes.EXERCISE`. It searches all nearby Samsung running and track-running sessions, then makes two decisions. The identity match finds the Samsung provider copy using source application and physical-workout evidence. The enrichment match classifies a candidate as an exact session or an overlapping session. Exact sessions require close boundaries and distance. Overlapping sessions require an extremely close start, high coverage of the canonical activity, a trusted Watch source, and substantial ExerciseLog data; Watch provenance alone is insufficient. Source application equality is not required for enrichment.

Provider session boundaries can differ for the same outing. A Strava canonical workout may end before its Galaxy Watch recording while still being fully covered by that recording. In that case, OpenRunCoach clips Samsung heart-rate, speed, cadence, and power samples to the canonical start and end, then recomputes their summaries. Canonical Health Connect distance and duration remain authoritative. Full Samsung session values such as VO₂max, calories, and elevation remain diagnostic context when boundaries materially differ and are not silently applied to the shorter canonical activity. Timestamped route-altitude clipping is planned before overlapping sessions can contribute elevation.

The diagnostic view separates the canonical workout, exact provider copy, and best enrichment candidate. Its expandable candidate list includes the Samsung data-point UID and timestamps, source application, device identity, distance, duration, ExerciseLog presence, and heart-rate, speed, cadence, VO₂max, elevation, and power capabilities. Genuinely ambiguous sensor candidates remain visible and can be selected manually for that run. These metrics remain experimental. Cadence is diagnostic data and is not used by the coaching engine. Raw Samsung samples are not persisted.

The developer diagnostics preserve the Samsung SDK operation, exception class, numeric error code, message, resolution availability, platform detection, exercise permission state, and developer authorization state. Missing exercise permission is reported separately from platform availability. Resolvable platform actions are launched only after the developer selects the resolution button; no external Samsung screen opens automatically.

Samsung support is optional. Health Connect and all normal app features continue to work when Samsung Health is absent, permission is denied, the platform is unsupported, or Samsung access is unavailable. The Samsung Health Data SDK requires Android 10 or newer, so the enrichment is unavailable on older supported OpenRunCoach devices.

## Unified run metrics

`RunMetrics` is the provider-independent data foundation for workout metrics. Health Connect always owns the canonical run ID, session start and end, and default metric values. An optional trusted Samsung Health match can add metrics that Health Connect did not expose, including the cadence and elevation values confirmed during development.

Every stored metric summary preserves its primary provider, confidence, verification state, and optional secondary provider value. Health Connect remains primary for overlapping values such as distance and duration. Exact Samsung sessions can verify shared values within centralized tolerances; material differences are marked as conflicts instead of silently replacing Health Connect data. Overlapping sessions contribute only sufficiently covered clipped sensor series, with confidence based on sample count and time coverage. Low-confidence or ambiguous matches are not used automatically. A saved manual selection must still meet the physical relationship and sensor requirements.

Cadence keeps the Samsung session average and a separate moving average. Raw zero cadence samples remain in the in-memory series, while the moving average excludes zero cadence and samples aligned with effectively zero speed. Raw heart-rate, speed, cadence, and power series are not stored in Room yet. Only summary values and their provenance survive app restarts. Drift, running economy, and technique analysis remain planned work.

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
└── deterministic simulation

Data
├── Health Connect
├── optional Samsung Health diagnostic enrichment
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
- Coaching and simulation logic remains Android-independent where possible.

## Simulation

The current developer simulator executes the complete domain timeline with built-in fixed scenarios:

```text
Baseline
→ Check-in
→ Adjustment
→ Simulated actual run
→ Post-run checkout
→ Coach decision
→ Next workout
```

It uses a fixed `Clock`, injected identifiers, fixed workout results, and no real user database. It is useful for repeatable coaching tests, regression testing, and edge cases without using an athlete as the test subject.

The full simulation milestone will use the exact same screens, navigation, check-in flow, adjustment evaluator, checkout, coaching engine, and workout generator as the real workflow. Only its dependencies will differ:

| Real workflow | Simulation workflow |
| --- | --- |
| Health Connect data | `SimulationDataSource` |
| Persistent Room data | Isolated in-memory or test storage |
| Real clock | Fixed clock |
| Real completed workouts | Simulated workout results |

The current developer screen is a timeline runner; shared production screens and isolated simulation storage remain roadmap work.

## Roadmap

- [x] Workout lifecycle and state machine
- [x] Health Connect Data Inspector
- [x] Experimental Samsung Health Data SDK investigation
- [x] Unified `RunMetrics` model
- [x] Athlete Performance Profile
- [x] Goal System
- [x] Structured Workout Model
- [ ] Training Block / Plan Generation
- [ ] Pre-run Check-in / Workout Adjustment
- [x] Basic pre-run check-in and duration adjustment
- [ ] Planned versus actual workout matching
- [ ] Post-run checkout lifecycle integration
- [ ] Technique, cadence, and running economy analysis
- [ ] Full simulation mode using production screens and isolated storage
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
