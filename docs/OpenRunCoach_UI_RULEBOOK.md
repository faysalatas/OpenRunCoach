# OpenRunCoach UI Rulebook

**Status:** Design source of truth for the redesign
**Version:** 2.0
**Applies to:** Native Android UI built with Jetpack Compose
**Source:** [Redesign composition spec](OpenRunCoach_REDESIGN_SPEC.md), sections 1–4 and 8
**Visual references:** [Redesign reference](UI/redesign/README.md), [design tokens](UI/redesign/design-tokens.json), and light/dark PNG boards in `docs/UI/redesign/`.

## Authority and migration

Version 2.0 supersedes the v1.2 dark-first, blue-green and mint direction, including its old typography, card, chart, navigation and screen-composition rules. The old targets are historical references only:

- [Legacy app target](UI/legacy/openruncoach-ui-target.png)
- [Legacy History and workout-detail target](UI/legacy/history-workout-detail-target.png)

References to the old rulebook and targets in earlier instructions or verification reports describe the superseded direction. Use this rulebook and the redesign spec for new UI work.

The redesign spec overrides this rulebook where they conflict. If the spec and a PNG disagree, stop and ask the user; do not guess. If `design-tokens.json` disagrees with spec section 2, section 2 wins. The resolved selected-week rule is `accent` fill with `onAccent` text in both themes, including dark mode.

This document specifies the target design; Step 0 changes documentation only. Implement later steps one at a time, on a focused branch and PR, after the preceding step is reviewed or merged. Screen compositions live in spec section 5; behavior, missing-data rules and accessibility remain in sections 6, 7 and 10.

Preserve domain logic, ViewModel behavior, data models, database fields and existing route strings. Never invent data: hide elements whose source is absent, and never show sample numbers in production. Keep original prescriptions, effective adjustments and actual performance distinct.

Evolve `theme/Theme.kt`, `OpenRunColors`, `OpenRunShapeTokens`, `OpenRunSpacing` and `ui/components/AppVisuals.kt`, including the existing card, chip, button, disclosure, evidence, section-header and metric components. Do not create a parallel component set. All literal colors belong in the theme package. Fonts must be bundled, with no network loading.

The canvas uses a 390 px board: 1 CSS px maps to 1 dp; text px maps to sp. Exported PNGs may be at a higher pixel density.

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
- `OptionChip(text, selected)`: height 48, pill, 1.5 dp `line` outline on `card`. Default selected option: `ink` fill, `bg` text. Accent-filled selected states, including the Plan week chip and selected date-card check, use `accent` fill with `onAccent` text. Primary buttons and heroes keep `hero` with white text.

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
- `SplitBars`: label (28 dp wide), bar (12 dp high, radius 6, width ∝ speed), value right (40 dp wide). Last split uses `accent`. The fastest split fills 100% of the available track; every other width fraction is `speed / fastestSpeed` (for example, 7:24 versus 7:10 = 430 / 444 ≈ 96.8%). Do not exaggerate differences or impose a minimum bar width. PNG bar lengths are illustrative; this proportional rule is authoritative.
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
- The coach analysis route is defined but has no entry point today. Link it from Run detail (redesign spec section 5.4).
- Add a new goal-creation entry from the Goal screen ("Pick a different goal").
- Edge-to-edge: the hero draws under the status bar. Add status-bar inset padding inside the hero (do not use a separate top bar). Use dark status-bar icons? No: hero is dark in both themes, so use **light** status-bar icons on screens that start with a hero.

---

## 5. Copy rules (human voice)

- Second person, present tense, short sentences, no jargon. "Keep it chatty." Not "Maintain conversational effort."
- A coach text is 1 to 3 sentences: what happened, what it means, what is next.
- Headlines are plain: "Easy does it today.", "How did it go?", "You're getting faster".
- Numbers carry units in the label, not in a long sentence.
- Do not use exclamation marks, emoji, or gamified language (streaks, badges, "crushed it").
- Keep every existing safety and data wording (for example "This describes how the goal compares with observed data. It is not a prediction." and the "Health data stays on this device." line) unchanged.
- Dates: use the device locale (the canvas shows English formats).
