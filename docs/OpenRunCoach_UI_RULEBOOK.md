# OpenRunCoach UI Rulebook

**Status:** Design source of truth  
**Version:** 1.2\
**Applies to:** Android app UI built with Jetpack Compose  
**Visual targets:** `docs/UI/openruncoach-ui-target.png` and, for History and workout details, `docs/UI/history-workout-detail-target.png`

## 1. Purpose

This document defines how OpenRunCoach should look and feel. It is the visual source of truth for all production UI work.

The goal is a calm, premium, data-driven running app that feels focused rather than decorative. The interface should make training information easy to scan without turning every metric into a card, badge, color, or chart.

OpenRunCoach should feel:

- minimal
- calm
- precise
- athletic
- data-driven
- premium without being flashy
- dark-first
- consistent across all screens

When implementing UI changes, preserve existing domain behavior and data semantics. The design must adapt to the product; product logic must not be changed merely to imitate a mockup.

---

## 2. Design hierarchy and precedence

When UI decisions conflict, use this order:

1. Existing product/domain correctness and accessibility
2. This UI Rulebook
3. The approved visual reference image
4. Existing reusable OpenRunCoach components
5. Legacy screen styling
6. New ad-hoc design choices

Do not introduce a new visual language on an individual screen.

---

## 3. Approved visual direction

The approved reference is a restrained dark interface with:

- near-black blue/green-tinted backgrounds
- only subtle differences between background and surfaces
- one dominant mint/turquoise accent
- white primary text and cool gray secondary text
- thin dividers and borders
- large typography for important information
- generous empty space
- flat, low-noise cards
- charts using the same mint accent
- very few decorative colors
- compact bottom navigation
- information grouped by meaning, not by individual metric

The UI must **not** drift toward neon, cyberpunk, glassmorphism, gaming UI, or highly colorful fitness dashboards.

---

## 4. Color system

The palette below is derived from the approved reference and should be encoded as shared theme tokens rather than repeated literal values.

### Core neutrals

| Token | Suggested value | Use |
|---|---:|---|
| `Background` | `#080D10` | Primary app background |
| `BackgroundDeep` | `#020609` | Very deep backdrop / system-edge areas |
| `Surface` | `#0C1215` | Main cards and containers |
| `SurfaceElevated` | `#131B1E` | Selected/elevated sections |
| `SurfaceHighlight` | `#1F3233` | Soft selected or highlighted surface |
| `BorderSubtle` | `#324647` | Thin borders/dividers at low opacity |
| `TextPrimary` | `#F3F4F5` | Main text |
| `TextSecondary` | `#B2BABD` | Secondary text |
| `TextMuted` | `#8B9498` | Labels, metadata, inactive elements |

### Brand/accent

| Token | Suggested value | Use |
|---|---:|---|
| `Accent` | `#59CCBF` | Main interactive/brand accent |
| `AccentMuted` | derived from Accent at low alpha | selected backgrounds, chart fills |

Use **one main accent color** throughout the normal product UI.

Do not assign a unique color to every metric. History may use centrally defined,
low-saturation workout-type accents as narrow row-edge strips; mint remains the
dominant accent. Unknown types stay neutral and must not be inferred from pace.

### Semantic colors

Semantic colors are allowed only when meaning is important:

- success: muted green/mint
- warning: restrained amber
- error/pain/safety: restrained red
- informational: default accent or neutral

Rules:

- Do not color ordinary metrics red/green based on superficial positive/negative interpretation.
- Do not make RPE, HR, pace, distance, continuity, and readiness all different colors by default.
- Prefer text/icon/status changes before adding another color.

---

## 5. Typography

Typography should create hierarchy without relying on many colors or cards.

Use the app's existing Android-safe font stack unless a dedicated font is deliberately introduced later.

### Hierarchy

**Screen title**
- 28–32 sp
- medium/semibold
- primary text

**Hero value / main metric**
- 26–32 sp
- semibold
- used sparingly

**Section title**
- 18–20 sp
- medium/semibold

**Card title / workout title**
- 16–18 sp
- medium/semibold

**Body**
- 14–16 sp
- regular

**Metadata / labels**
- 12–14 sp
- regular
- secondary or muted text

Rules:

- Important values are visually stronger than their labels.
- Do not bold every label.
- Avoid ALL CAPS except for tiny brand/eyebrow text where intentionally used.
- Use tabular numerals where practical for metrics.
- Keep line length comfortable; long explanations should wrap naturally.

---

## 6. Spacing system

Use a small shared spacing scale:

- `4 dp` — micro spacing
- `8 dp` — icon/text spacing
- `12 dp` — compact internal spacing
- `16 dp` — standard component padding
- `20 dp` — comfortable card padding
- `24 dp` — section spacing
- `32 dp` — major screen separation

Preferred screen horizontal padding: **20 dp**.

Rules:

- More whitespace is preferable to more visual separators.
- Do not compensate for weak hierarchy by adding more cards.
- Avoid deeply nested padding layers.
- Sections should feel clearly separated even without borders.

---

## 7. Shapes and borders

The UI should use soft but restrained rounding.

Recommended:

- primary cards: 16–20 dp radius
- compact controls/chips: 10–14 dp radius
- pill controls: full/pill radius only when semantically appropriate
- buttons: 12–16 dp radius

Borders:

- 1 dp maximum for standard cards
- low contrast
- often optional
- selected state may use the accent at low intensity

Do not use heavy outlines around every component.

---

## 8. Surface and card rules

Cards are for meaningful groups, not individual values.

Good:

```text
Today's Workout
Easy Run
5.0 km · 6:00–6:45 /km
[Start]
```

Bad:

```text
[Distance card] [Pace card]
[HR card]       [RPE card]
[Status card]   [Goal card]
```

Individual metric tiles are acceptable when they form one deliberate summary row or grid, but should not dominate every screen.

Cards should be:

- flat or very subtly elevated
- low contrast
- spacious
- visually quiet

No glow effects.
No large gradients as a default surface treatment.
No decorative background images unless a future feature explicitly calls for them.

---

## 9. Icons

Use simple Material-style line icons.

Rules:

- Icons support scanning; they do not decorate every label.
- Prefer one icon per concept/group, not one per line of text.
- Default icon color is `TextSecondary` or `Accent`.
- Avoid multicolor iconography.
- Keep icon sizes consistent (typically 18–24 dp).

---

## 10. Navigation

Bottom navigation should be compact and visually quiet.

Preferred behavior:

- dark surface that visually belongs to the page
- 4–5 destinations maximum
- icon + short label
- active item uses the accent
- inactive items use muted gray
- no giant floating pill behind the entire active tab
- no oversized navigation height

Navigation hierarchy should remain stable across the app.

---

## 11. Tabs and segmented controls

Tabs should resemble the approved reference:

- thin text-based tabs or restrained segmented controls
- active tab uses accent text and/or a thin accent underline
- inactive tabs remain muted
- no rainbow or heavy button-style tabs

Use tabs when switching between related views of the same entity, e.g.:

- Overview / Splits / Analysis / Coach
- Fitness / Load / Trends

Do not use tabs merely to reduce scrolling.

---

## 12. Buttons

### Primary action

- accent fill
- high-contrast dark/white text as appropriate
- clear label
- full-width only when the action is genuinely primary

Examples:

- Start Run
- Continue
- Save

### Secondary action

- surface or transparent background
- subtle border if needed
- no competing bright color

Avoid multiple equally strong primary buttons on one screen.

---

## 13. Status chips

Status chips should be compact and rare.

Good uses:

- Completed
- Upcoming
- Within tolerance
- High cost

Rules:

- Use neutral/mint by default.
- Warning/error colors only for meaningful warnings.
- Do not turn every metadata field into a chip.

---

## 14. Charts and data visualization

Charts should follow the same restrained visual language.

Rules:

- dark background
- mint/turquoise primary line
- subtle grid
- subtle gradient fill beneath line where useful
- gray comparison/reference series
- minimal labeling
- no rainbow series unless multiple distinct data series genuinely require it
- no fake scores or decorative percentages

The chart should answer a question, not merely fill space.

Examples:

- pace over distance
- weekly distance trend
- training volume trend
- heart-rate trend when data exists

Do not create visualizations from data that OpenRunCoach does not actually possess.

---

## 15. Metric presentation

Metrics should generally use:

```text
6:27 /km
Average pace
```

rather than:

```text
Average pace: 6:27 /km
```

for primary summary sections.

A summary row of 3–4 metrics is acceptable when each value is genuinely important.

Compact dashboard and list metrics may use 18–20 sp values with smaller, muted labels;
reserve hero sizing for the primary action or result. Abbreviate pace units to `/km`.
Reflow metric groups into fewer columns on narrow screens or at larger font scales
rather than truncating recorded values. Four metrics should use a balanced 2×2 grid
when a readable single row is not possible.

Completed Run Overview is an exception: use one compact horizontal card of equal
metric columns (normally four), each with a vector icon, value, and muted label.
Separate columns with subtle vertical rules. Omit unavailable metrics and redistribute
columns. Keep the row at narrow widths/large fonts by allowing horizontal scrolling
of readable equal-width columns rather than shrinking values or returning to 2×2.

Examples:

- Distance
- Duration
- Average pace
- Average HR

Do not use more metrics than the user can reasonably scan at once.

---

## 16. Home / Dashboard rules

The Home screen should answer, in order:

1. What matters today?
2. What is my next/current workout?
3. How is this week going?
4. What progress or recent result should I know about?

Target structure:

```text
OPENRUNCOACH

Good evening,
<Name>

Overview | Training | Progress

[Today's / Next Workout]

This Week                 2 / 3 runs
○  ✓  ●  ○  ○  ○  ○

[Key weekly metric]  [Key trend metric]

[Optional concise coach insight]
```

Rules:

- Next workout should dominate the dashboard when one exists.
- Avoid presenting several equally prominent cards.
- Goal information may appear as context, but should not push the actionable workout below excessive dashboard content.
- No invented completion percentages.

---

## 17. Training Plan rules

The Plan screen should prioritize chronological scanability.

Each row/card should expose only the most useful fields:

- day/date
- workout type
- planned distance/duration
- target pace when available
- status

Expanded detail is secondary.

Completed workouts may show actual values, but must never replace the immutable prescription.

Plan UI should visually distinguish:

- completed
- current/next
- future
- rest

without using four loud colors.

A vertical timeline or restrained list is preferred over a dense card grid.

---

## 18. Run Detail rules

The approved History and planned/completed workout-detail reference is
`docs/UI/history-workout-detail-target.png`. Completed and planned workouts share the same
visual family, while completed screens show actual evidence and planned screens show only
the prescription until execution exists.

Run Detail should follow this hierarchy:

```text
Easy Run
Date · time

Overview | Splits | Analysis | Coach

5.0 km   32:18   6:27/km   146 bpm

[Route or primary chart]

Pace | Elevation | Heart Rate

[Selected chart]

[Relevant comparison / target]

[Coach insight]
```

Rules:

- Route/chart gets meaningful visual space.
- Key metrics are shown once, not repeatedly across several cards.
- The analysis section may be richer but should remain secondary to the actual run data.

Confirmed completed-run patterns:

- Reuse bundled workout heroes only from an actual associated workout type, with
  a compact dark-gradient header and local tabs below it. Unmatched runs remain neutral.
  Observe the physical-run association and its prescription reactively, including
  assignments without checkout. Wait for the initial association read before choosing
  the header; an execution result must not independently overwrite its workout metadata.
  Keep the header shallow (about 112 dp at default fonts), allowing readable text to grow.
- Overview sizes its map/plot against the available viewport and font scale so the
  primary content is compact at normal phone sizes; retain readable text and scrolling
  on small screens/large fonts. Its primary order is metric strip, map, metric pill,
  chart, then two-column summary. Keep the four-column icon/value/label metric card;
  measure text before requiring horizontal scrolling instead of imposing wide columns.
  Dashboard plots are approximately 88–116 dp high at default fonts, plus minimal axes;
  maps flex around 120–192 dp. Do not reduce font scaling to meet these proportions.
- Detail routes use a real dark/desaturated street map when available, with mint
  route/start/finish and padded whole-route fitting. MapLibre rendering is independent
  of replaceable style/tile configuration. Keep required map-data attribution visible.
- Reuse the dark Canvas silhouette during map loading, offline/tile/style failure.
  History thumbnails remain Canvas-only; never create a map per list row.
- Charts use a compact equal-width rounded metric pill, not navigation underlines;
  keep at least 48 dp touch targets and a stable chart/summary footprint when switching.
  Pace / Heart Rate / Cadence sit in one dark rounded container with small internal
  padding and a quiet accent outline only on the selected segment. Offer only drawable
  recorded series; omit the selector entirely when just one metric is available.
  Overview / Splits / Analysis / Coach retain text tabs with an active underline and
  a subtle full-width divider. Use restrained axes/grid, a thin line and
  subtle mint area fill fading to transparent. Keep actual values/gaps unchanged.
- Pace target bands apply only to meaningful continuous whole-run prescriptions,
  never interval work targets. Average/target summaries dominate, with a quiet target key;
  Fastest 30 sec and off-scale clipping notes belong in Recording diagnostics.
  Place two equal visual columns directly below the chart, with value above label.
  Average is off-white; a genuine target is restrained mint with a small parallel-bar
  range key beside its label. HR uses recorded average/max; cadence retains its existing
  moving-average preference, explicitly labelled when moving, and recorded maximum.
  Leave an unavailable second measurement absent while retaining the summary footprint.
- Split Δ is split pace minus canonical whole-run average pace, including partial
  splits. Positive means slower and stays neutral; negative means faster and may
  use restrained mint. Use explicitly aligned Km/Pace/Δ/HR columns (numbers right aligned).
  Keep Km left aligned; full kilometre rows show their ordinal, partial rows their
  fractional distance without repeating the unit. Use tabular numerals and thin row
  dividers, never per-row cards. Omit HR when absent throughout the run. Place the
  average/half-run summary immediately below the table, ahead of the muted provenance.
- Negative/positive split compares two equal-distance halves using genuine cumulative
  distance/time interpolation. Never compare just first/last kilometre or average
  unweighted paces. ±3 sec/km is Even split; suppress short/uncertain results.
- Keep cadence/duration in collapsed Split details. Use only average pace and
  positive/negative/even half-run pacing in the primary summary, with collapsed Half-run
  details. Do not show fastest/slowest split rankings. Provenance is a small muted caption.
- Analysis groups Outcome / Session cost / Volume in one compact strip, with friendly
  values and no invented unknown volume. Use aligned Planned/Actual columns, subtle
  headers/dividers and slightly stronger actual values. Prioritize distance, pace and
  checkout RPE; add duration for time-based prescriptions rather than repeating every
  available metric. Keep continuity as a concise status/detail and confidence as a row.
  Association follows immediately: Planned workout, title/distance, Change association
  and Unassign. Full original reasons and additional adherence facts stay under Evidence,
  collapsed initially. Keep this density at default fonts; allow scrolling at large fonts.
  Do not visually equate an interval work target with whole-run average pace.
- Data sources stays collapsed and Ignore run stays a secondary text action.

---

## 19. Coach Analysis rules

Coach Analysis should explain the run, not overwhelm the user with evaluator internals.

Suggested order:

1. high-level coaching response
2. core actual metrics
3. key execution/adherence evidence
4. selected supporting trends
5. concise takeaway/reasons

Internal domain concepts such as `WorkoutResponse`, continuity, adherence, session cost, and confidence may be surfaced when useful, but should use human-readable labels.

Do not create synthetic overall scores unless the domain model explicitly defines them.

Completed-run Coach defaults to recommendation/status, at most 2–4 concise
evidence-backed observations, and one action-consistent next-training implication.
Keep lower-priority evidence, confidence, RPE/fatigue, and full original reasons under
More details. Analysis remains the factual table; Coach is the short takeaway, not
the same table repeated as paragraphs. No new evaluator or invented conclusion.

---

## 20. Progress / Athlete State rules

The visual reference contains generic fitness/readiness scores. OpenRunCoach must **not** copy those scores unless the domain actually supports them.

Use the same visual layout with real OpenRunCoach evidence.

Possible sections:

### Fitness / capability
- longest continuous distance
- longest tolerated distance
- recent volume
- recent frequency
- demonstrated capabilities

### Load
- 7-day / 28-day volume
- recent session costs
- tolerated aerobic sessions

### Trends
- volume trend
- frequency trend
- pace trend when valid

### Readiness
Use actual readiness state and evidence. Do not invent a 0–100 readiness score.

### Goal direction
GoalStrategy may be shown as human-readable capability priorities, but must not be presented as measured weakness unless evidence supports that conclusion.

---

## 21. Empty, loading and unknown states

Unknown data should look intentional, not broken.

Use:

- `Not enough data yet`
- `Unknown`
- `No recent evidence`

Do not use `0` when the value is unknown.

Do not invent fallback health/fitness metrics.

Loading states should be subtle and structurally match final content.

---

## 22. Accessibility and Android behavior

The approved mockup is a visual reference, not a license to copy iOS-specific behavior.

Production Android rules still apply:

- minimum 48 dp touch targets
- sufficient text contrast
- support font scaling
- avoid clipped metrics at larger font sizes
- respect system bars/insets
- use Android navigation/back behavior correctly
- support narrow devices
- allow cards to grow vertically for long text

The target is the **visual language**, not an iOS clone.

---

## 23. Animation

Animation should be rare and functional.

Allowed examples:

- short screen/content transitions
- subtle selected-tab transition
- chart reveal
- state change feedback

Avoid:

- continuous decorative movement
- glowing/pulsing backgrounds
- excessive spring animations
- animation that delays access to training data

---

## 24. What OpenRunCoach must NOT look like

Avoid:

- colorful dashboard mosaics
- every metric having its own accent color
- gradients on every card
- glow/neon effects
- glassmorphism as a default style
- excessive nested cards
- tiny dense text
- fake AI branding elements
- generic robot/AI mascots
- decorative fitness scores
- made-up progress percentages
- marketing copy inside functional screens

The product should look like a serious running tool, not a concept-art fitness app.

---

## 25. Reusable Compose primitives

Prefer a small set of shared primitives instead of screen-local styling.

Suggested primitives:

- `OrcScreen`
- `OrcTopBar`
- `OrcCard`
- `OrcSectionHeader`
- `OrcMetric`
- `OrcMetricRow`
- `OrcTabs`
- `OrcStatusChip`
- `OrcPrimaryButton`
- `OrcSecondaryButton`
- `OrcChartContainer`
- `OrcBottomNavigation`

Names may follow existing project conventions.

Do not create a huge design-system framework. Shared primitives should exist only when they reduce inconsistency or duplication.

---

## 26. Implementation rule

UI screens should consume existing ViewModel/domain state.

A visual refactor must not alter:

- TrainingGoal semantics
- WorkoutExecution
- WorkoutResponse
- AthleteState
- GoalStrategy
- plan generation
- continuity evaluation
- completion tolerance
- Health Connect behavior
- Samsung integration
- repository/storage behavior

If a desired UI element requires data the app does not possess, omit or adapt the element rather than inventing data.

---

## 27. Screen review checklist

Before a redesigned screen is considered complete, verify:

### Visual
- Does it clearly belong to the approved OpenRunCoach design system?
- Is mint the only dominant accent?
- Is there enough whitespace?
- Are cards grouped by meaning rather than individual values?
- Is the most important information obvious within 2–3 seconds?

### Data
- Are all values sourced from real app state?
- Are unknown values shown as unknown instead of zero?
- Is no fake score/probability/progress value introduced?
- Are planned and actual values still distinct?

### Interaction
- Are touch targets at least 48 dp?
- Does back navigation behave normally on Android?
- Does text wrap at larger font sizes?
- Does the screen remain usable on narrow devices?

### Consistency
- Does it reuse shared theme/components?
- Are spacing, typography and radius tokens consistent?
- Is a new color/component genuinely necessary?

---

## 28. Rule for AI/Codex UI work

Before making UI changes, an implementation agent must read this file and inspect the approved visual reference.

It must:

1. preserve domain behavior
2. reuse the shared design system
3. avoid introducing new colors/styles without a reason
4. not invent metrics to match a mockup
5. treat the reference as visual direction rather than product-data specification
6. keep debug/simulation UI functional even if it is not visually polished
7. report any requested design element that cannot be implemented faithfully because the required data does not exist

---

## 29. Versioning

When the design direction changes intentionally, update this document first or in the same change.

Do not silently evolve the design system screen by screen.

Suggested changelog convention:

```text
UI Rulebook v1.0 — Minimal dark/mint design system established
```
