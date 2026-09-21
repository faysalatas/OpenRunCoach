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

The planned goal system will support:

### Race distances

- 5K
- 10K
- Half Marathon
- Marathon
- Custom distances later

### Goal types

- Complete the distance
- Improve performance
- Reach a target time

Race distance does not equal training distance. A 5K athlete may still perform 8–10 km Zone 2 runs, long easy runs, threshold sessions, intervals, and recovery runs. The goal informs the overall training structure rather than imposing a maximum distance on every session.

## Planned workout model

The current basic workout model supports Easy Run, Recovery Run, Long Run, Tempo Run, Intervals, and Rest. The extensible model is planned to include:

- Easy Run
- Recovery Run
- Long Run
- Run/Walk
- Tempo
- Threshold
- Intervals
- Race-specific workouts
- Technique and drills
- Strides

Workouts will eventually contain structured segments:

```text
Workout
├── Warm-up
├── Work interval
├── Recovery
├── Work interval
└── Cool-down
```

Possible segment targets include duration, distance, pace, RPE, heart-rate zone, repetitions, and recovery duration.

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

The next data investigation will examine availability and provider behavior for:

- cadence
- VO2max
- elevation
- power
- route
- device metadata
- running dynamics, where available

OpenRunCoach does not assume that every Health Connect provider exposes every metric. A dedicated inspection package is reserved for querying raw data for a selected workout without changing production coaching models.

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
├── coaching rules
├── workout lifecycle and readiness evaluation
├── next-workout generation
└── deterministic simulation

Data
├── Health Connect
├── Room
├── DataStore settings
└── repositories
```

Architecture rules:

- Health Connect record classes stay in the Health Connect data layer.
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
- [ ] Health Connect Data Inspector
- [ ] Unified `RunMetrics` model
- [ ] Athlete Performance Profile
- [ ] Goal system
- [ ] Structured workout model
- [x] Pre-run check-in
- [x] Adaptive workout adjustment
- [ ] Planned versus actual workout matching
- [ ] Post-run checkout lifecycle integration
- [ ] Training blocks and weekly plans
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

### Common commands

```bash
./gradlew test
./gradlew assembleDebug
./gradlew installDebug
```

The debug APK is generated at `app/build/outputs/apk/debug/app-debug.apk`.

### Repository instructions

[`AGENTS.md`](AGENTS.md) defines the project's architecture, health-data, development, and Git rules. Codex and other automated contributors should read and follow it before changing the project.

