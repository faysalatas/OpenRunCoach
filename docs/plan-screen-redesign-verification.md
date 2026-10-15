# Plan screen redesign — report and manual verification

No Gradle build, unit test, connected test, device command, installation or screenshot verification was performed. All verification below is pending user execution. The approved general visual reference and UI rulebook were inspected; density and accessibility still require device screenshots.

## 1. Files changed

- `app/src/main/java/com/example/openruncoach/ui/plan/PlanScreen.kt`
- `app/src/main/java/com/example/openruncoach/ui/plan/PlanViewModel.kt`
- `app/src/main/java/com/example/openruncoach/ui/plan/PlanPresentation.kt`
- `app/src/main/java/com/example/openruncoach/ui/workout/WorkoutSummaryPresentation.kt`
- `app/src/main/java/com/example/openruncoach/ui/workout/WorkoutDetailScreen.kt`
- `app/src/main/java/com/example/openruncoach/ui/components/WorkoutIntensitySignal.kt`
- `app/src/main/java/com/example/openruncoach/ui/home/HomeViewModel.kt`
- `app/src/main/java/com/example/openruncoach/ui/home/HomeScreen.kt`
- `app/src/main/java/com/example/openruncoach/theme/WorkoutAccents.kt`
- `app/src/main/java/com/example/openruncoach/data/TrainingPlanRepository.kt`
- `app/src/main/java/com/example/openruncoach/data/local/TrainingPlanDao.kt`
- `app/src/test/java/com/example/openruncoach/ui/plan/PlanPresentationTest.kt`
- `app/src/test/java/com/example/openruncoach/ui/workout/WorkoutSummaryPresentationTest.kt`
- `app/src/androidTest/java/com/example/openruncoach/data/PhysicalRunConsistencyTest.kt`
- `docs/OpenRunCoach_UI_RULEBOOK.md`
- `README.md`
- `docs/plan-screen-redesign-verification.md`

## 2. Previous structure

The screen placed a large generic heading and verbose milestone card above repeated goal/plan metadata, raw week-number tabs, a large week heading, focus/load text and workout rows. Weekly targets and every Why this week reason followed the workout list. Previous plans were preserved in Room but had no browsing UI. Each active-plan/profile emission selected the current week again, overwriting local week selection.

## 3. Compact goal/milestone hero

One compact hero below the Training Plan eyebrow groups the real goal title, selected week/horizon, Completion Run milestone date, saved plan confidence and separately labelled completion readiness. No date is attached to TrainingGoal, no confidence percentage is invented, and not-ready displays Building toward goal. The milestone remains actionable through the existing scheduling/rescheduling flow, now in a secondary sheet. Dismissing the sheet only hides it; the existing explicit Not yet action retains its prior scheduling semantics. No-plan and no-goal notices retain existing goal/scheduling actions, with a goal-review fallback when scheduling evidence is unavailable.

## 4. Week navigation

A compact LazyRow shows Week N, a locale-aware date range and Past / Current / Upcoming text. Selected surface and current-week text provide restrained emphasis, with previous/next controls and 48 dp minimum touch targets. Selection updates local content and scrolls the selected week into view. For the same active plan, background emissions retain the selected index. A different plan initializes to the current week when present. Existing explicit schedule/generation selection behavior is preserved. Returning from Planned Workout Detail keeps the ViewModel selection.

## 5. Weekly summary

One summary precedes workouts. It uses saved week distance or duration targets and the real non-rest session count. Actual/planned volume and completed sessions are separate values. A small volume bar appears only when comparable real actual/planned distances exist. Primary workout types come from the selected prescriptions. Future summaries are labelled Provisional and display planned values, without a misleading zero-completed comparison. No synthetic score or percentage is introduced.

## 6. Canonical actuals versus planned values

The existing active-plan execution projection already resolves canonical actual summaries. A small read-only metadata projection adds existing lifecycle/adjustment flags and authoritative physical-run associations, including recorded runs without checkout. It observes existing canonical actual-run reads and uses the same `toActualSummary()` resolver. Associations are already one-to-one and canonical queries already exclude provider duplicates; no new matching/deduplication policy exists in UI.

Current/past week volume includes genuine associated recorded distance, including partial/stopped sessions. Completed-session counts use only existing `executionResult.isCompleted`. A recorded run before checkout contributes actual volume without inventing session completion. Current canonical distance, when available, takes precedence over historical execution fields; unknown canonical distance stays unknown rather than falling back to stale distance. An incomplete set of recorded distances suppresses the distance aggregate rather than presenting a partial sum as a complete total. Original prescription targets are unchanged.

## 7. Workout cards

Compact dark cards use a shared semantic accent strip, short localized date, readable workout name, genuine status, chevron and wrapping icon metadata. Existing runner/pace assets and the extracted Home effort-bar glyph are reused. No new color mapper, image asset or hero-per-row was added. Rationale is shortened from the stored generation reason and limited to two visible lines. Explicit rest entries use compact rows. Every row opens the existing Planned Workout Detail.

## 8. Status presentation

Today is an explicit small label. Execution labels are shared with Planned Workout Detail, preserving completed/minor-deviation/prescription-deviation/partial/stopped distinctions. Actual lifecycle supplies Check-in required, Ready, Checkout required or Completed where applicable; existing adjustment records add Adjusted. Recorded associations without checkout are labelled accordingly. A past date alone never creates a missed/failed lifecycle state. Original prescription metadata stays visible, while real actual distance/duration occupies a separate result line.

## 9. Structured summaries and Home consistency

Home and Plan now share one prescription/goal display projection backed by existing estimate and description formatters. A genuine single-segment continuous prescription can display its pace target. Repeated/multi-block sessions instead show a real repeated-work summary when available (for example 6 × 400 m), a Structured workout label and genuine work RPE/effort. A unique pace target inside a work segment is not promoted to whole-run pace. Detailed blocks remain in the existing Structure tab. Planned Workout Detail only shares extracted status/intensity labels; its layout and navigation were not redesigned. Home's workout selection, weekly progress and coaching behavior were not changed.

## 10. Why this week

The Coach card shortens the stored week explanation and at most two existing reasons without deriving new coaching. The full explanation, all week reasons and existing block purpose/progression intent remain under Why this week?, collapsed initially for each selected week. Plan explanation and regeneration are secondary under Plan details. The adaptive/provisional nature of future work is stated once, outside individual workout cards.

## 11. Completion Run

Existing GOAL_COMPLETION purpose receives a restrained Completion Run label and mint milestone strip. Its actual prescription is used without altering distance or readiness. It still opens the normal workout detail. No milestone is generated or moved by presentation.

## 12. Plan history

Previous plans is a secondary action inside Plan. A small sheet reads existing non-active saved plans, plus plans whose goals are no longer active, with friendly Replaced plan / Completed plan / Archived plan / Previous plan wording and real stored dates/week counts. Opening one reads the existing plan snapshot and reuses local week navigation/workout cards. This view has no regeneration, activation, or scheduling action; Android Back returns to the active Plan with its selected week retained. No new bottom-navigation destination, archive persistence or active-plan switching logic was added.

## 13. Rulebook and documentation

The Plan rules now specify the compact hero, local dated navigation, weekly summary, canonical actual/progress distinction, shared compact workout cards/icons/accents, short rationale, collapsed details, future provisional language and archive access inside Plan. README documents the presentation and read-only projections. Neither document is used as a change log.

## 14. Intentionally unchanged and verification limits

TrainingGoal/date ownership, GoalStrategy, AthleteState, active-plan selection, generation, week/workout scheduling, milestone generation, progression, readiness evaluators, prescriptions, adjustments, WorkoutExecution decisions, completion rules, canonical selection/resolution, superseding/archive writes and persistence/schema are unchanged. Existing scheduler/regenerator actions still call their original repository operations. Repository/DAO additions are read-only presentation queries over existing tables. There are no new dependencies or migrations. Planned Workout Detail and other screen layouts are preserved apart from shared formatter/glyph extraction and the shared Home/Plan structured summary rule.

Focused unexecuted tests cover date ranges, week selection retention, canonical actual precedence, unknown distance, recorded-but-unchecked runs, partial/stopped volume, future/no-evidence behavior, rest/session counts, status semantics, and continuous versus structured prescription formatting. An unexecuted Room case covers two provider records producing one canonical Plan actual before checkout and access to a saved replaced plan. No build, test or device pass is claimed. Screenshot requests below are for the user to provide.

## 15. Complete manual verification checklist

Every item is pending. The requested checklist follows in full.

TEST 1 — BUILD

User runs:

./gradlew test
./gradlew assembleDebug

Expected:
both pass.

--------------------------------------------------

TEST 2 — PLAN TOP

Open Plan.

Expected initial viewport shows:

- Training Plan
- active goal/milestone hero
- week navigation
- weekly summary
- beginning of workout list

without an oversized header.

Return screenshot.

--------------------------------------------------

TEST 3 — GOAL DATE SEMANTICS

Expected:

goal itself has NO completion date.

The date shown belongs to:

Completion Run / plan milestone.

Verify wording does not say "Goal date".

--------------------------------------------------

TEST 4 — CURRENT WEEK

Select current week.

Expected:

- clearly selected
- correct date range
- actual vs planned progress where relevant
- upcoming/completed workouts correctly represented

--------------------------------------------------

TEST 5 — FUTURE WEEK

Select a future week.

Expected:

- planned volume
- planned sessions
- no misleading "0 completed" failure presentation
- future/adaptive status remains clear

--------------------------------------------------

TEST 6 — PAST WEEK

Select a past week with completed runs.

Expected:

- prescribed workouts remain visible
- actual results visible
- canonical runs count once
- Samsung + Strava duplicates do not double-count volume

--------------------------------------------------

TEST 7 — EASY WORKOUT CARD

Verify an Easy workout card contains compactly:

date
Easy Run
distance
target pace
effort/status

No giant rationale.

Return screenshot.

--------------------------------------------------

TEST 8 — LONG RUN CARD

Verify Long Run uses its semantic accent.

Target/prescription values must be genuine.

--------------------------------------------------

TEST 9 — STRUCTURED WORKOUT

Open a week containing Intervals/Tempo/Threshold if available.

Expected:

the Plan card does NOT misleadingly show interval work pace as a whole-run
target.

Structured prescription summary remains semantically correct.

--------------------------------------------------

TEST 10 — TODAY

If a workout is scheduled today:

Expected:

TODAY is immediately recognizable.

Any existing Check-in required state remains accessible.

--------------------------------------------------

TEST 11 — COMPLETED WORKOUT

Find associated completed workout.

Expected:

planned prescription remains visible.

Actual result is shown separately.

Example:

2.00 km planned
1.95 km actual

No planned value overwritten by actual.

--------------------------------------------------

TEST 12 — WORKOUT DETAIL

Tap workout.

Expected:

existing Planned Workout Detail opens.

Overview / Structure / Coach still work.

Back returns to same selected Plan week.

--------------------------------------------------

TEST 13 — WHY THIS WEEK

Expected:

short Coach summary visible.

Detailed Why this week content collapsed by default.

Expand.

Existing full rationale remains available.

--------------------------------------------------

TEST 14 — COMPLETION RUN

Navigate to week containing Completion Run.

Expected:

milestone workout visibly distinguished
but still consistent with app design.

--------------------------------------------------

TEST 15 — WEEK PROGRESS CALCULATION

For current/past week return:

planned distance
actual distance
completed session count

Verify actual distance uses canonical actual run values and no duplicate
provider records.

--------------------------------------------------

TEST 16 — PLAN HISTORY

Verify previous/superseded plans remain accessible INSIDE Plan.

No new bottom-navigation destination.

--------------------------------------------------

TEST 17 — NO ACTIVE PLAN

If practical through simulator/test scenario:

verify no-plan state remains functional and provides existing next action.

--------------------------------------------------

TEST 18 — ACCESSIBILITY

Increase Android font size one step.

Expected:

cards remain usable
important values do not overlap
scrolling remains functional
