# OpenRunCoach Redesign: Composition Spec

**Status:** Proposed design source of truth (replaces UI Rulebook v1.2 once step 0 lands)
**Audience:** Codex (or any coding agent) implementing the redesign in Jetpack Compose
**Canvas:** "OpenRunCoach Concept" design canvas. One board per screen, light and dark, 390 dp wide. Board names are listed in section 5. Exported copies live in `docs/UI/redesign/`.

Read `AGENTS.md`, `docs/repository-workflow.md` and `docs/OpenRunCoach_UI_RULEBOOK.md` first. This spec overrides the rulebook where they conflict. Product logic, ViewModels, repositories and domain models stay as they are.

---

## 0. How to use this spec

1. Work **one step at a time** (section 9). One branch and one PR per step, as `docs/repository-workflow.md` requires.
2. Every step ends with the same checks: `./gradlew test`, `./gradlew assembleDebug` (needs the local Samsung AAR), and `./gradlew --no-daemon -p ci/core-tests test` for domain changes. Record real results in the PR. If a check cannot run, say so. Do not claim a pass.
3. Do not invent data. If a field in this spec has no source in the app, **hide that element** (see section 7). Do not hardcode sample numbers into production UI.
4. Do not rename domain types, routes' string values, or database fields. Presentation only.
5. Reuse existing code first: `theme/Theme.kt`, `OpenRunColors`, `OpenRunShapeTokens`, `OpenRunSpacing`, and `ui/components/AppVisuals.kt` (`OpenRunCard`, `OpenRunStatusChip`, `OpenRunPrimaryButton`, `OpenRunSecondaryButton`, `OpenRunDisclosure`, `OpenRunEvidenceRow`, `OpenRunSectionHeader`, `OpenRunCompactMetrics`). Evolve them. Do not create a parallel component set.
6. The numbers in the canvas are CSS px at 390 px width. **1 px = 1 dp.** Text sizes px = sp.

---

## 1. Design idea

**One job per screen. The goal (Complete 10K) runs through every screen. Warm, human, data-rich.**

| Screen | Question it answers |
|---|---|
| Today | What do I do now? |
| Plan | Am I on track for the goal? |
| Runs | How did I do? |
| Run detail | What happened on this run, and what does it mean? |

Principles:

- A **hero** at the top of every screen. It runs edge to edge and **fades into the page background**. It never ends in a hard edge or a rounded box.
- Below the hero, **flat cards** on the page background. Group by meaning. Maximum about 4 to 6 cards before a section label.
- **Numbers are large and tabular.** Labels are small and muted.
- **A human voice** for coach text. Short sentences, plain words, second person.
- **Light and dark** are equal citizens. Follow the system setting. Both are specified below.
- One accent (terracotta). Green means good news only. No other decorative colors.
- No emoji. No glass, no neon, no shadows. Elevation is 0 everywhere.

---

## 2. Tokens

Put these in `theme/` next to the existing tokens. Extend `OpenRunColors` or add `OpenRunPalette` with a light and a dark instance exposed through a `CompositionLocal` (`LocalOpenRunPalette`). Also map them into the Material3 `lightColorScheme` / `darkColorScheme` (`background`, `surface`, `surfaceContainerHigh`, `onSurface`, `onSurfaceVariant`, `outlineVariant`, `primary`).

### 2.1 Color

| Token | Light | Dark | Use |
|---|---|---|---|
| `bg` | `#F6EEE3` | `#1C1411` | Page background |
| `card` | `#FFFAF3` | `#2A1F1A` | Cards, rows, inputs |
| `ink` | `#2A1C14` | `#F6EEE3` | Primary text, filled bars |
| `muted` | `#6E5B4E` | `#BBA997` | Secondary text, labels |
| `line` | `#E6D8C6` | `#3F3027` | Dividers, bar tracks, outlines |
| `hero` | `#BF4318` | `#A63B15` | Hero background, primary button |
| `accent` | `#BF4318` | `#FF8A5C` | Chart fills, selected progress |
| `accentText` | `#BF4318` | `#FF9A6B` | Accent text, active tab |
| `onAccent` | `#FFFFFF` | `#1C1411` | Text and icons on `accent` fills (white on `#FF8A5C` fails contrast) |
| `good` | `#2F6B4F` | `#8FD6AE` | Good news text, improving trends |
| `goodBg` | `#DDEEE3` | `#233A2E` | Good-news chip background |
| `chip` | `#EFE4D5` | `#3A2C24` | Neutral chip background |
| `nav` | `#FFFAF3` | `#241915` | Bottom bar background |
| `dash` | `#A89583` | `#8C7A6B` | Dashed outline for planned items |
| `band` | `#DDEEE3` | `#2F4A3B` | Target-range band in charts |
| `map` | `#EFE4D5` | `#241915` | Map placeholder / route backdrop |
| `heart` | `#B43A52` | `#FF9AA2` | Heart-rate line |
| `runEasy` | `#3E9C85` | `#5DB7A0` | Run type dot: easy, recovery |
| `runLong` | `#7D9A6E` | `#A9C29A` | Run type dot: long |

On the hero (both themes): primary text `#FFFFFF`, secondary text `#FFE2D2`, translucent chip `#FFFFFF` at 20%, lane lines `#FFFFFF` at 15%.
Other workout types (tempo, threshold, intervals, strides, race) keep their existing colors from `OpenRunColors`, adjusted for contrast on `card`.

Rule: filled selected states (week chip, selected date card check) use `accent` + `onAccent`. Primary buttons and the hero use `hero` + white.

Contrast checked: white on `#BF4318` is about 5.4:1. White on `#A63B15` is above 6:1. Muted text on `bg` is at least 4.5:1 in both themes. Keep it that way if you change a value.

### 2.2 Typography

Two families, **bundled** in `res/font/` (no network font loading; the app must work offline):

- **Display and numbers:** Bricolage Grotesque, weights 600 and 700.
- **Text:** Inter, weights 400, 500 and 600.

Numbers use tabular figures (`fontFeatureSettings = "tnum"`).

| Style | Family | Size / line | Weight | Use |
|---|---|---|---|---|
| `heroDisplay` | Bricolage | 56 / 56 | 700 | Today hero title |
| `pageTitle` | Bricolage | 44 / 46 | 700 | Plan, Runs, Run, Settings titles |
| `subTitle` | Bricolage | 40 / 44 | 700 | Checkout, New goal, Inspector titles |
| `heroMetric` | Bricolage | 34 / 38 | 700 | Run detail metric trio |
| `bigNumber` | Bricolage | 38 / 42 | 700 | Week total |
| `miniValue` | Bricolage | 28 / 32 | 700 | Mini stat cards |
| `cardTitleLg` | Bricolage | 20 / 24 | 700 | Goal card title |
| `title` | Inter | 16 / 22 | 600 | Card titles, row titles |
| `body` | Inter | 16 / 24 | 400 | Coach text |
| `bodySm` | Inter | 15 / 22 | 400 | Descriptions |
| `meta` | Inter | 14 / 20 | 400 to 600 | Sub lines, chips |
| `caption` | Inter | 13 / 19 | 400 to 600 | Smallest allowed text |
| `eyebrow` | Inter | 12 / 16 | 600, +1.6 sp tracking, UPPERCASE | Section labels, hero eyebrow |

Nothing below 12 sp. Everything must scale with system font size. Check at 130% and 200%.

### 2.3 Shape and spacing

| Token | Value |
|---|---|
| Page horizontal padding | 20 dp |
| Gap between cards | 14 dp (12 dp inside grids) |
| Card padding | 16 dp (mini stat cards 14 × 16) |
| Card radius | 20 dp |
| Row radius (list rows) | 16 dp |
| Pill buttons and option chips | height ≥ 48 dp, radius 24 to 26 dp |
| Chips (read-only) | height 32 dp, radius 16 dp |
| Input radius | 14 dp |
| Progress bar | 10 dp high, radius 5 dp |
| Touch target | ≥ 48 dp (never below 44 dp) |
| Elevation | 0 dp. No borders on cards. Dividers use `line` |

---

## 3. Core primitives

Implement these in `ui/components/` (extend `AppVisuals.kt` or add `HeroComponents.kt`). Each needs a `@Preview` in light and dark.

### 3.1 `OpenRunHero`
Full-width block with a vertical gradient: `hero` from 0% to 62%, then fading to `bg` at 100%.
```kotlin
Brush.verticalGradient(
  0f to palette.hero, 0.62f to palette.hero, 1f to palette.bg
)
```
- Padding: top 24 dp (16 dp on detail screens that have a back link), horizontal 20 dp, **bottom 72 to 96 dp** (the fade zone is empty on purpose).
- Content column gap 8 dp. Clip to bounds.
- Draws `TrackLanes` in the top right (3.2).
- The body below overlaps the hero by 8 dp (`offset(y = (-8).dp)` or negative top padding via layout) so cards start inside the fade.
- Slots: `eyebrow`, `title`, `subtitle`, `content`.

### 3.2 `TrackLanes`
A decorative `Canvas`, 300 × 190 dp, positioned top end with `offset(x = 90.dp)` (right edge cut off) and top about 50 to 70 dp. Four nested stadium outlines (rounded rectangles) in a 260 × 170 viewport:
`(10,10,240×150, r75)`, `(26,26,208×118, r59)`, `(42,42,176×86, r43)`, `(58,58,144×54, r27)`. Stroke 2 dp, `#FFFFFF` at 15% alpha. No semantics (decorative).

### 3.3 `OpenRunCard`
`card` background, radius 20, padding 16, vertical gap 10, no border, no elevation. Update the existing component. Remove the 1 dp border.

### 3.4 `SectionLabel`
`eyebrow` style in `muted`, horizontal padding 4 dp, top padding 4 dp.

### 3.5 `Chip(text, tone)`
Height 32, horizontal padding 12, radius 16, `meta` weight 600. Tones: `Neutral` (`chip` bg, `ink` text), `Good` (`goodBg` bg, `good` text, optional check icon 16 dp), `OnHero` (white 20% bg, white text).

### 3.6 Buttons
- `PrimaryButton`: height 48 to 52, pill, `hero` background, white text, `title` weight 600.
- `HeroButton`: pill, white background, text `#7A2A0C`, used on the hero.
- `SecondaryButton`: transparent, 1.5 dp `line` outline, `ink` text.
- `OptionChip(text, selected)`: height 48, pill, 1.5 dp `line` outline on `card`. Selected: `ink` fill, `bg` text.

### 3.7 `KeyValueRow(label, value)`
Space between, 15 sp, top divider `line` (first row has none), 9 dp top padding. Label `muted`, value weight 600, right aligned, tabular figures.

### 3.8 `Metric(value, label)`
Value in `heroMetric` (on hero) or `miniValue`; unit or label below in `caption`, `muted` (on hero: `#FFE2D2`).

### 3.9 `ProgressBar(fraction)`
10 dp, `line` track, `accentText` or `ink` fill, radius 5. Animate fill width once on first composition. Skip animation when reduce-motion is on.

### 3.10 `MiniStatCard(title, value, unit, delta, tone, sparkline?)`
Card with: title (`caption` 600, `muted`), value (`miniValue`) with unit (`meta`, `muted`), delta line (`caption` 600, `good`), optional 120 × 30 sparkline (3 dp stroke, round caps, last point has a 4 dp dot). Used in a 2-column grid with 12 dp gap.

### 3.11 Charts (Canvas based, no library)
- `WeekBars`: 7 columns, gap 8. Done = `ink` fill, radius 6. Today done = `accent` fill. Planned = dashed 2 dp `dash` outline. Today planned = solid 2 dp `accent` outline. Rest = 3 dp `line` bar. Height proportional to km. Weekday initials below (`caption`, `muted`, today in `accentText` bold).
- `LongRunBars`: 7 columns (W1 to W6, goal date). Done = `ink`. Current = `accent`. Future = dashed outline. Goal bar = solid 2 dp `ink` outline on `line` fill. Value label above each bar (12 sp, tabular). Column labels below.
- `WeeklyDistanceBars`: 8 columns, same styling, 11 sp value labels.
- `PaceLineChart`: 3 faint grid lines, 3 dp line, 5 dp dots, labels (`m:ss`) under dots. **Faster is higher.** Optional target `band` rectangle (radius 6).
- `HeartRateChart`: 3 dp `heart` line, 3 grid lines.
- `SplitBars`: label (28 dp wide), bar (12 dp high, radius 6, width ∝ speed), value right (40 dp wide). Last split uses `accent`.
- `Sparkline`: see 3.10.
- Every chart gets a `contentDescription` summarizing the trend in words. Never convey the trend by color alone.

### 3.12 `BottomBar`
3 tabs: **Today, Plan, Runs**. Background `nav`, top divider 1 dp `line`, tab height 56 dp, icon 24 dp (stroke 2 dp), label 12 sp 600. Active = `accentText`, inactive = `muted`. Reuse the drawables `ic_nav_home`, `ic_nav_plan`, `ic_nav_history` (redraw as stroke icons: sun, calendar, route line) and delete `ic_nav_settings` from the bar.

### 3.13 `ListRow` family
- `RunRow`: card, radius 16, min height 60, padding 10 × 14. Left: 10 dp dot in run-type color. Then date (2 lines, 13 sp 600 `muted`, 56 dp wide). Then run name (`title`). Right aligned: distance (`title`, tabular) over pace (`caption`, `muted`).
- `WorkoutRow` (Plan): weekday (36 dp wide, 13 sp 600 `muted`), name + meta, trailing check icon (`good`, 22 dp) if done. Today row = `hero` background with white text and a `TODAY` eyebrow. Future row = dashed outline.
- `NextUpRow`: date block (44 dp: weekday eyebrow over day number in 22 sp Bricolage), name + meta.
- `SettingsRow`: min height 60, title + subtitle, trailing chevron or control; rows grouped in one card with `line` dividers.
- `DisclosureRow`: like `SettingsRow` with a down chevron.

### 3.14 Back link
On detail screens, inside the hero: chevron + label (the parent screen name), min height 44, white, 15 sp 600.

---

## 4. Navigation

Existing: `Navigation.kt`, tabs Home, History, Plan, Settings.

New:

- Tabs: **Today** (route `home`), **Plan** (`plan`), **Runs** (route `history`). Keep the route strings to avoid churn. Change only labels and icons.
- **Settings** is no longer a tab. It stays a route (`settings`). Open it from the settings icon button (44 dp, top right of the Today hero, 18% white circle). The bottom bar shows only on `home`, `plan`, `history`.
- Keep all other routes: `performance-profile`, `goal`, `workout-preview` (debug), `planned-workout?workoutId=`, `run?runId=`, `data-inspector?runId=`, `coach?runId=`, `simulator` (debug).
- **New route:** `run-checkout?runId=`. The post-run checkout moves out of the Run detail "Coach" tab into its own screen. Keep the existing save and edit logic in `RunDetailViewModel`.
- The coach analysis route is defined but has no entry point today. Link it from Run detail (section 5.5).
- Add a new goal-creation entry from the Goal screen ("Pick a different goal").
- Edge-to-edge: the hero draws under the status bar. Add status-bar inset padding inside the hero (do not use a separate top bar). Use dark status-bar icons? No: hero is dark in both themes, so use **light** status-bar icons on screens that start with a hero.

---

## 5. Screens

Board names refer to the canvas (each board has a light and a dark version, titled e.g. "Plan · light"). Same composition in both.

**Reference files** (in the repo under `docs/UI/redesign/`): `design-tokens.json` (all colors and sizes), `sources/<Board>.dc.html` (exact markup per board, e.g. `Plan2.dc.html` light, `PlanDark.dc.html` dark), and PNG exports named `<screen>-light.png` / `<screen>-dark.png`. If a PNG and this spec disagree, ask; do not guess. If tokens in `design-tokens.json` and section 2 disagree, section 2 wins.

| Screen | Board (L / D) | Kotlin file today |
|---|---|---|
| Today (before run) | Today · before run | `ui/home/HomeScreen.kt` |
| Today (after run) | Today · after run | `HomeScreen.kt` |
| Today (rest day) | Today · rest day | `HomeScreen.kt` |
| Plan | Plan | `ui/plan/PlanScreen.kt` |
| Runs | Runs | `ui/history/HistoryScreen.kt` |
| Run detail | Run detail | `ui/health/RunDetailScreen.kt` |
| Post-run checkout | Post-run checkout | `RunDetailScreen.kt` (inline today) |
| Coach analysis | Coach insight | `ui/coach/CoachDetailScreen.kt` |
| Workout detail | Workout detail | `ui/workout/WorkoutDetailScreen.kt` |
| Goal date | Goal date | `ui/goal/GoalScreen.kt` |
| New goal (step 2 of 5) | New goal | `GoalScreen.kt` |
| Performance profile | Performance profile | `ui/profile/PerformanceProfileScreen.kt` |
| Settings | Settings | `ui/settings/SettingsScreen.kt` |
| Health data inspector | Health data inspector | `ui/inspection/HealthDataInspectorScreen.kt` |

All screens are one vertical scroll (`LazyColumn` or `Column` + `verticalScroll`) inside the hero + body structure. No tabs inside screens (except where noted).

### 5.1 Today (`HomeScreen`)

The hero is **state aware**. Three states from existing home state: workout planned and not done, workout done today, no workout today. Top to bottom:

1. `OpenRunHero`
   - Row: date eyebrow (`SUN 8 NOV · MORNING`, 13 sp 600, tracking 1.6, `#FFE2D2`) and the settings icon button (right).
   - **Before run:** eyebrow `TODAY'S RUN · LONG` (the workout type), title = workout name (`heroDisplay`), line `8 km · 6:45–7:30 /km` (19 sp), the workout reason (15 sp, `#FFE2D2`, max width 270), then `HeroButton` "See workout" (opens workout detail).
   - **After run:** a 24 dp mint check badge + `DONE · 11:42`, title = workout name (40 to 56 sp), metric trio (km, time, pace /km, `heroMetric`), `HeroButton` "See run details".
   - **Rest day:** eyebrow `TODAY`, title "Rest day", one sentence, then the next workout line.
2. **Quick facts card** (before run): 4 equal columns with 1 dp dividers: distance, estimated time, effort (RPE range), and `+N km vs last long run` (only if there was a previous run of the same type). Value `title`-size Bricolage 22, label `caption`.
3. **Chips** (after run only): "As planned" (Good), target pace range, "New longest run" if true.
4. **Feel card** (after run, if no checkout yet): title "How did it feel?", subtitle, 3 `OptionChip`s (Easy, Just right, Hard), link "Add details ›" opening the checkout screen. Tapping a chip saves a quick checkout and opens the checkout screen for details.
5. **Coach** block: `SectionLabel` "YOUR COACH", then `body` text (2 to 3 sentences). Source: existing coach summary text. Rewrite strings in the human voice (section 8).
6. **Goal card** (tappable, opens `goal`/`plan`): ring (52 dp, 6 dp stroke, `line` track, `accent` progress) or title only; goal title (`cardTitleLg`), subline `Sat 12 Dec · 5 weeks · Week 2 of 8`, "On track" Good chip (from goal assessment). Two labeled bars: Longest run `7.1 of 10 km`, Weekly distance `n of N km planned`.
7. **This week card:** title, date range, big done km (`bigNumber`) + `of N km`, `WeekBars`, chips (`3 of 4 runs done`, `avg m:ss /km`, `load on plan`).
8. **YOUR FORM · LAST 4 WEEKS:** 2×2 `MiniStatCard`s: Easy pace, Heart rate on easy runs, Cadence (hidden if no data), Readiness (n / 5 with 5 small segments).
9. **LAST RUN:** `RunRow` variant with a 64 × 48 route thumbnail (radius 12, `map` backdrop, 3 dp route line) and `as planned` note.
10. **NEXT UP:** up to 2 `NextUpRow`s.
11. `BottomBar` (Today active).

Rules: never show an empty card. If a source is missing, drop the element (section 7).

### 5.2 Plan (`PlanScreen`)

Hero (title "Complete 10K", eyebrow `TRAINING PLAN`, subline `Completion Run Sat 12 Dec · 5 weeks left`, three `OnHero` chips: Week 2 of 8, On track, Confidence medium). Body:

1. Card "Longest run, toward 10 km" with `LongRunBars`.
2. Card "Weekly distance" with `WeeklyDistanceBars` and a one-line explanation.
3. Horizontal scroller of week `OptionChip`s (Week 1 …). Selected = `accent` fill with `onAccent` text (matches the PNG).
4. Row: "Week 2 · Nov 2–8" and `done of planned km`.
5. `WorkoutRow`s for the week (done, done, done, today).
6. `SectionLabel` "WHY THIS WEEK" + `body` text (existing plan explanation).
7. `BottomBar` (Plan active).

Keep the existing modal sheets (completion scheduler, plan history). Restyle them: `bg` background, 20 dp top radius, scrim 32%.

### 5.3 Runs (`HistoryScreen`)

Hero: eyebrow = month name uppercase, title "Your runs", metric row (runs, km, hours, avg pace /km) in 26 sp. Body:

1. Card "Easy pace" with `PaceLineChart` (4 weekly points) + chip `13 s/km faster` as `good` text + caption "Higher means faster".
2. 2-column mini cards: Heart rate on easy runs, Longest run.
3. Section label `THIS WEEK` with total km; `RunRow`s.
4. Section label `LAST WEEK` with total km; `RunRow`s. Continue older weeks as the user scrolls (lazy).
5. `BottomBar` (Runs active).

Group by calendar week (Mon to Sun) instead of one long list.

### 5.4 Run detail (`RunDetailScreen`)

**Tabs are gone.** One scroll. Hero (back link "Runs", title = run name, subtitle `Wed 4 Nov · 18:12 · <source app>`, metric trio km / time / pace). Body:

1. Chips: "As planned" (Good), `Pace in target 6:50–7:40`, `Effort 2 / 10`.
2. Coach block (2 sentences).
3. Route map card (170 dp, radius 20): reuse `RunRouteMap`. Placeholder uses the `map` color.
4. Card "Pace": `PaceLineChart` with target band; caption.
5. Card "Heart rate": `HeartRateChart`, `avg 146 · max 158 bpm` on the right.
6. Card "Splits": `SplitBars`, one-line summary ("gentle negative split").
7. Card "Plan vs. run": 3 columns (label, planned, actual), rows Distance, Pace, Effort.
8. 3 mini tiles: Cadence, Elevation, (Fastest 30 s only if available).
9. Link card with 3 rows: Post-run checkout, Coach analysis, Health data inspector (chevrons).

Merge the old "Analysis" and "Coach" tab content into items 2, 7 and the coach analysis screen. Keep the "Complete post-run checkout ›" prompt: show it as the feel card on Today and as row 9 here.

### 5.5 Post-run checkout (new route)

Hero small (back link "Run", title "How did it go?", subline = run name, date, distance). Body, each in a card:

1. "How hard was this run?" with `RPE n of 10` on the right; 10 round `OptionChip`s in a 5-column grid; caption "1 is very easy. 10 is all out."
2. One card with four groups: **Cardio** (Easy, Appropriate, Hard, Limit), **Muscle fatigue** (None, Mild, Moderate, Severe), **Pain** (None, Mild, Significant), **Completion** (Completed, Partial, Aborted). Wrapping `OptionChip` rows.
3. "How ready did you feel?" (optional) 1 to 5, caption "1 is drained. 5 is fresh."
4. "Notes" multi-line field (min 84 dp, radius 14, 1.5 dp `line` outline).
5. `PrimaryButton` "Save checkout" (or "Update checkout"), text button "Cancel".

Same fields and validation as the current editor. Only the layout changes.

### 5.6 Coach analysis (`CoachDetailScreen`)

Hero (back link, title = coaching action: Progress / Small progression / Maintain / Reduce / Recovery, subline = first reason). Body cards in the existing order: Outcome, Actual, Execution evidence, Planned, Checkout, Why this matters. Use `KeyValueRow`s inside `OpenRunCard`s and bullets (`bodySm`) for reasons. Keep every existing label string.

### 5.7 Workout detail (`WorkoutDetailScreen`)

Hero (back link, eyebrow = date, title = workout name, Good or Neutral chip for execution status). Replace the three tabs with one scroll:

1. Metrics card (up to 4 tiles: Distance, Target pace, Intensity, Expected duration).
2. "What this run is for" (description, no 260-char cut when it fits).
3. "How it is built": effective prescription blocks as rows (`N × label`, segment name, completion, intensity). Show "Original prescription" in an `OpenRunDisclosure` only when it differs.
4. "Training decision": bullets from adjustment reasons (only if any).
5. "Coach cues": up to 4 bullets.
6. "Recorded execution" card (only after a run): status, actual distance, duration, pace.
7. Pace-guidance confidence row (Low, Medium, High, Uncalibrated) as a chip.

### 5.8 Goal date and new goal

- **Goal date** (`GoalScreen`, overview/date step): hero with back link and title; list of date options as selectable cards (2 dp outline: `accent` when selected), "Choose another date" secondary button, "Pick a different goal ›" text link, "GOOD TO KNOW" block.
- **New goal flow** (5 steps, existing logic): hero with a 5-segment step bar (white filled for done and current, white 30% for the rest), eyebrow `STEP n OF 5`, title = step question. Step 2 shows 3 option cards (radio left, title 18 sp 600, description `bodySm` `muted`, support line `caption` 600 in `good` or a warning tone), "WHY WE ASK" block, and a `PrimaryButton` "Continue". Other steps reuse the same shell: step 1 distance options, step 3 current context key-value card, step 4 goal details, step 5 review with "Start Goal".
- Existing overview content (Current Goal, Goal Details, Starting Performance, Current Comparison, Goal Assessment, Pause, Archive, Choose Another Goal) keeps its strings. Lay it out as cards with `KeyValueRow`s. Put the destructive actions (Pause, Archive) as `SecondaryButton`s at the bottom with the existing note text.

### 5.9 Performance profile (`PerformanceProfileScreen`)

Hero (back link, title "Performance profile", subline). The three tabs (Training, Benchmarks, Evidence) become three stacked groups under `SectionLabel`s: **CAPABILITY AND READINESS** (Longest completed, Continuous, Tolerated; Readiness; Latest session cost; expandable evidence), **TRAINING** (last 7 days, last 28 days, 4-week average, longest recent run), **BENCHMARKS** (per distance, easy running baselines with "Why these values?" disclosure, fitness), **EVIDENCE** (recent feedback, consistency, data confidence with "Why?" disclosure). Keep all existing labels and the empty state ("Not enough running data yet").

### 5.10 Settings (`SettingsScreen`)

Hero (back link "Today", title "Settings", subline). Grouped cards using `SettingsRow`s with `SectionLabel`s:

- **HEALTH DATA:** Health Connect (status dot `good`, "Connected · last sync", "Active" chip; actions "Request permissions" / "Sync runs"), Running sources, Preferred workout source (chips: Automatic + one per source app).
- **TRAINING:** Goal, Performance profile, Regenerate plan (if present).
- **ADVANCED:** Diagnostic dump (share-safe and full export; keep the confirm dialog), Health data inspector entry if applicable.
- **ABOUT:** OpenRunCoach, version, "Health data stays on this device."
- **DEVELOPER TOOLS** (debug builds only): Simulation, Workout Model Preview.

### 5.11 Health data inspector (`HealthDataInspectorScreen`)

Hero (back link "Run", eyebrow `DIAGNOSTICS`, title "Health data inspector", subline run name, date, distance, source). Body: `SectionLabel` "DETAILED RUN DATA" with two cards (Health Connect, Samsung Health) of `KeyValueRow`s; "WHERE EACH METRIC COMES FROM" card (Duration, Distance, Heart rate, Speed, Cadence, Elevation, VO₂max, Power → source name or "Not available" in `muted`); "MORE DETAILS" card of `DisclosureRow`s (Permissions and SDK status, Provider details, Canonical matching, Raw comparison) that expand in place; "Refresh inspection" `SecondaryButton`; a caption that says this page is for debugging. Keep all existing actions and buttons inside the disclosures.

---

## 6. Behavior

- **Theme:** follow system (`isSystemInDarkTheme()`). Remove the forced dark default (`darkTheme: Boolean = true`). Keep a user override only if one exists today.
- **Motion:** bars and rings animate once on first display (about 500 ms, ease-out). Skip when the system animation scale is 0.
- **Scroll:** hero scrolls with content. No collapsing toolbars.
- **State:** loading shows the hero with title and a skeleton for cards (rows at 20 dp radius in `line`). Errors show a card with the existing message and a retry button.
- **Back:** system back and the hero back link do the same thing.
- **Rotation and tablets:** content max width 600 dp, centered. The hero still fills the width.

---

## 7. Data mapping and what may be missing

Never invent values. Hide an element when its source is empty. Known gaps:

| Element | Where it comes from | If missing |
|---|---|---|
| Estimated time | distance × pace range of the workout | Hide the column |
| `+N km vs last long run` | previous run of the same workout type | Hide the column |
| Readiness n / 5 | existing post-run feedback average (Performance profile) | Hide the tile |
| Easy pace trend, easy HR trend | Performance profile "Fitness" values | Hide the tile or its delta |
| Cadence | run metrics | Hide the tile |
| Elevation | run metrics | Hide the tile |
| Fastest 30 s | **not in the app today** | Omit it |
| Weather | **not in the app** | Not part of this design |
| Weekly distance W3 to W8 | generated plan | Show only weeks that exist |
| Source app name | run source | Hide that part of the subline |
| Heart-rate chart | HR samples | Hide the card |

The canvas shows sample numbers (for example 13.0 km, 6:36 avg, 146 bpm). They are only examples.

---

## 8. Copy rules (human voice)

- Second person, present tense, short sentences, no jargon. "Keep it chatty." Not "Maintain conversational effort."
- A coach text is 1 to 3 sentences: what happened, what it means, what is next.
- Headlines are plain: "Easy does it today.", "How did it go?", "You're getting faster".
- Numbers carry units in the label, not in a long sentence.
- Do not use exclamation marks, emoji, or gamified language (streaks, badges, "crushed it").
- Keep every existing safety and data wording (for example "This describes how the goal compares with observed data. It is not a prediction." and the "Health data stays on this device." line) unchanged.
- Dates: use the device locale (the canvas shows English formats).

---

## 9. Step-by-step plan for Codex

Each step is its own branch and PR. Do not start a step before the previous one is merged or at least reviewed. Each PR description lists what changed, screenshots of light and dark (from `@Preview` or a device), and the exact test results.

### Step 0. Rulebook v2
- **Do:** Replace `docs/OpenRunCoach_UI_RULEBOOK.md` with v2.0 derived from this spec (sections 1 to 4 and 8). Mark the old dark-first mint direction as superseded. Move the old target images to `docs/UI/legacy/`.
- **Done when:** the rulebook no longer contradicts this spec. No code changes.

### Step 1. Tokens, fonts, theme
- **Files:** `theme/Theme.kt`, `theme/` color and type files, `res/font/` (add Bricolage Grotesque and Inter TTFs, with licenses in `docs/`).
- **Do:** Add `OpenRunPalette` (light and dark), `LocalOpenRunPalette`, new `Typography`, map to Material3 schemes. Default `darkTheme` to the system setting.
- **Done when:** a preview shows every token in both themes. Existing screens still compile (they may look different).

### Step 2. Primitives
- **Files:** `ui/components/AppVisuals.kt` (+ new `HeroComponents.kt`, `Charts.kt`).
- **Do:** Build everything in section 3 with previews (light, dark, font scale 1.3 and 2.0).
- **Done when:** a "Gallery" `@Preview` shows all primitives. Charts have content descriptions.

### Step 3. Navigation shell
- **Files:** `Navigation.kt`, nav drawables.
- **Do:** 3-tab bottom bar, settings as a non-tab route reachable from the Today hero, edge-to-edge hero and status bar handling, new `run-checkout` route (stub).
- **Done when:** all existing routes still open. Bottom bar appears only on the three tab routes.

### Step 4. Today
- **Files:** `ui/home/HomeScreen.kt` (+ `HomeProgressTrend.kt`, `HomeViewModel.kt` only if a UI model field is missing; do not change domain logic).
- **Do:** Section 5.1, all three states. Add a presentation mapper (for example `HomePresentation.kt`) with unit tests for the state selection and hidden-element rules.
- **Done when:** previews exist for before, after and rest in both themes. Hidden-data rules tested.

### Step 5. Plan
- **Files:** `ui/plan/PlanScreen.kt`, `PlanPresentation.kt`.
- **Do:** Section 5.2. Restyle the two bottom sheets.
- **Done when:** long-run and weekly distance charts render from real plan data.

### Step 6. Runs
- **Files:** `ui/history/HistoryScreen.kt`, `HistoryViewModel.kt` (grouping by week in the presentation layer only).
- **Do:** Section 5.3.
- **Done when:** week grouping is unit tested (Mon to Sun, locale-safe).

### Step 7. Run detail
- **Files:** `ui/health/RunDetailScreen.kt`, `RunCharts.kt`, `CompletedRunPresentation.kt`, `CoachPresentation.kt`.
- **Do:** Section 5.4. Remove the tab row. Keep all data logic.
- **Done when:** nothing from the old four tabs is lost (compare with the old tab content list in section 5.4).

### Step 8. Post-run checkout
- **Files:** new `ui/health/RunCheckoutScreen.kt`, route in `Navigation.kt`, reuse `RunDetailViewModel` save logic.
- **Do:** Section 5.5. Wire Today's feel card and Run detail row 9.
- **Done when:** save and update behave exactly as before. Add a test for validation (RPE range 1 to 10, readiness 1 to 5).

### Step 9. Coach analysis
- **Files:** `ui/coach/CoachDetailScreen.kt`.
- **Do:** Section 5.6 and add the entry point from Run detail.

### Step 10. Workout detail
- **Files:** `ui/workout/WorkoutDetailScreen.kt`, `StructuredWorkoutComponents.kt`, `WorkoutHeroImages.kt` (hero images are replaced by the gradient hero. Delete unused images only after checking `WorkoutPreviewScreen` and other users).

### Step 11. Goal and new goal flow
- **Files:** `ui/goal/GoalScreen.kt`.
- **Do:** Section 5.8. Keep the 5-step state machine untouched.

### Step 12. Performance profile
- **Files:** `ui/profile/PerformanceProfileScreen.kt`. Section 5.9.

### Step 13. Settings
- **Files:** `ui/settings/SettingsScreen.kt`, `DiagnosticDumpSettings.kt`, the debug `DeveloperToolsSettings.kt`. Section 5.10.

### Step 14. Health data inspector
- **Files:** `ui/inspection/HealthDataInspectorScreen.kt`. Section 5.11. Restyle only. Keep all actions.

### Step 15. Polish and cleanup
- Accessibility pass (section 10), font scale 200% check on every screen, remove dead styles and unused drawables, update `docs/` verification notes, add final screenshots to `docs/UI/`.
- Search the code for hard-coded colors and `Color(0x` outside the theme package. There should be none.

---

## 10. Accessibility checklist

- Touch targets ≥ 48 dp (44 dp minimum for the back link and icon buttons).
- Text contrast ≥ 4.5:1 for text under 24 sp, ≥ 3:1 for large text, in both themes.
- Color is never the only signal: good/bad also has text or an icon, chart trends have a text description.
- Every icon-only button has a `contentDescription`. Decorative drawings (track lanes) have none.
- Selected state of `OptionChip`s uses `selected` semantics and `Role.RadioButton` or `Role.Checkbox`.
- Charts expose one summarizing `contentDescription` each.
- Layout survives 200% font size without clipping. Rows wrap. Grids fall back to one column when width per item is under 96 dp × font scale.
- Respect reduce-motion.

---

## 11. Do not

- Do not change domain, data or ViewModel behavior to match the mock.
- Do not add dependencies for charts or fonts that load over the network.
- Do not show placeholder or sample data in production builds.
- Do not add per-screen colors, extra accents or gradients other than the hero fade.
- Do not rebuild a screen from scratch if the existing composable's logic can be kept. Re-skin and re-compose it.
- Do not merge steps. Do not leave a screen half old and half new inside one PR without noting it.

---

## 12. Acceptance (whole redesign)

1. All 14 screens match their boards in light and dark at 390 dp width (spacing within 2 dp, same type styles, same colors).
2. The hero fades into the background on every screen. No hard edge.
3. No element shows data the app does not have.
4. `./gradlew test`, core CI and `assembleDebug` pass (or the blocker is recorded in the PR).
5. No literal colors outside the theme package. No font loaded from the network.
6. Accessibility checklist passes at font scale 1.0, 1.3 and 2.0.
