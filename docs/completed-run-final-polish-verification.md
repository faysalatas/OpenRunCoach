# Completed Run Detail — final visual polish verification

For the user to execute against `docs/UI/history-workout-detail-target.png`.
This pass ran no Gradle tasks, automated tests, installation, or device verification.
Keep existing records; no reset, uninstall, or data clearing is needed.

## 1 — Easy Run hero

Open History → a completed run actually associated with Easy Run.
Expect the existing `hero_easy.webp` behind a compact full-width header, dark gradient,
readable title/date/time and Back action. Overview/Splits/Analysis/Coach stay below it.
Return a screenshot with the header and tabs visible.

Also check actual associations for Recovery (`hero_recovery.webp`), Long Run
(`hero_long_run.webp`), Tempo (`hero_tempo.webp`), Threshold (`hero_threshold.webp`),
Intervals (`hero_intervals.webp`), Strides (`hero_strides.webp`), and Race-specific
(`hero_race_specific.webp`). Recovery has a dedicated asset; it does not share Easy imagery.
An unmatched Run must remain neutral regardless of its pace. The shared existing
mapping for Run/Walk, Technique and Assessment remains unchanged.

## 2 — Overview fit

Open the current completed Long Run, wait for metric refresh to settle, and use the
normal device/font scale. At the top of Overview, without vertical scrolling, expect
local tabs, the four-column metric card, real dark map, metric pill, full selected
chart and average/target summary. The map and plot adapt to available height; this is
not a device-specific layout. Secondary Data sources/Ignore run can be below the fold.
Return one screenshot of the entire initial viewport, plus display/font-scale settings.

Repeat on a smaller viewport and with large text: scrolling must remain available;
text must not shrink, clip or lose data. Wide numeric rows may scroll horizontally.
Missing HR must omit that Overview metric, not fabricate a value. Pending checkout
and update/error notices must remain accessible and may take extra vertical space.

## 3 — Metric pill

Where all three genuine series exist, expect Pace | Heart rate | Cadence in one compact
rounded dark control with equal-width segments, a restrained selected surface/border
and accent text. No navigation-style underline. Touch targets remain at least 48 dp;
the visual inset segment is smaller. Missing series are omitted, not invented.
Return a close screenshot of the selector.

## 4 — Pace chart and summary

Expect a compact thin mint line, fading area fill, three Y labels, subdued grid and
target/reference treatments, and elapsed-time ticks. Genuine gaps remain gaps.
Average pace (primary text) and Target range (restrained accent and small stacked
bars) appear side by side directly below the chart.

For the reviewed Long Run, verify `7:24 /km` and `7:45–9:05 /km` **only if these are
still the actual recorded average and current prescription**. Return the exact two
values shown and a screenshot; never replace actual values to match the example.
Open Data sources → Recording diagnostics: Fastest 30 sec remains available when
known, and the off-scale clipping message appears only when relevant. Neither should
occupy the default chart summary.

For an interval workout, no individual work target may appear as a whole-run band.
In Analysis, work target and actual whole-run average must remain separate rows,
with the existing context warning; no new adherence conclusion is introduced.

## 5 — Heart rate switch

Tap Heart rate. Expect the same plot/summary footprint and map size, no map reload,
genuine HR data, and Average HR/Max HR values when available. Unknown summaries show
`—`, not zero. Return a screenshot and exact summary values. At normal font scale
the Overview should not jump vertically when switching.

## 6 — Cadence switch

Tap Cadence. Expect the same footprint and genuine series. Summary retains the
existing moving average when available (otherwise session average), and genuine max
cadence; unknown values remain `—`. Return screenshot and exact values. Stops/gaps
must remain represented according to the existing chart preparation.

## 7 — Splits table

Open Splits. Expect Km | Pace | Δ | HR, omitting unavailable optional columns.
Full kilometre rows are `1`, `2`, etc.; a final 160 m partial is `0.16` (locale decimal
formatting applies). Km is left aligned; Pace/Δ/HR are right aligned using shared,
explicit column widths. No row cards. No Fastest full split or Slowest full split.
Return a screenshot and every displayed row.

Check Δ still equals the unrounded split pace minus the displayed whole-run average's
underlying value. Example: 6:41 vs 6:27 gives `+0:14` (neutral); 6:14 gives `−0:13`
(restrained mint). A partial split keeps its genuine normalized pace/delta. Missing
average suppresses the Δ column instead of substituting zero. Provenance is a small
muted caption below the table.

## 8 — Split summary

Below the table, expect only Average pace and Positive/Negative/Even split side by
side. Negative/positive values retain signed `/km`; Even split is shown as text,
not an exaggerated direction. Insufficient half-run evidence gives `—` rather than
a made-up result. No split ranking metrics. Return screenshot and exact two values.

## 9 — Secondary split/half-run details

Half-run details is collapsed initially when evidence exists. Expand it and verify
first/second half distance, duration and pace remain available. Each half must still
be half the actual distance; the existing interpolation and ±3 sec/km even tolerance
are unchanged. Return expanded evidence values.

Split details is also collapsed initially. Expand and verify exact split durations
and available cadence remain accessible, without adding columns to the primary table.

## 10 — Analysis

Open Analysis on the reviewed Long Run. Expect one compact completion/session-cost/
volume outcome card, prescription adherence, aligned Planned/Actual columns for
distance/pace/RPE (and prescribed duration when available), continuity and confidence.
The important facts should scan without reading long paragraphs.

For the reviewed case, compare existing evidence: planned `4.80 km`, actual `1.16 km`,
Aborted, Partial volume, High session cost, sustained walking/stopped continuity and
the actual interpretation confidence. Values must remain unchanged by presentation.
Return screenshot and exact outcome, comparison, continuity and confidence values.

Check the lower Planned workout section is compact, e.g. `Long Run · 4.80 km`.
Change association, Unassign, and Assign to workout on an unmatched run remain available
through the existing actions/candidate flow. Reassignment should update header imagery
from the newly associated actual type, never pace.

## 11 — Evidence

Evidence is collapsed by default. Expand it: all existing factual response reasons
must remain available without rewriting or filtering. Return collapsed/expanded
screenshots. Unknown data remains unknown; confidence/outcomes are unchanged.

## 12 — Coach regression

Open Coach. Keep the existing recommendation (Recovery in the reviewed example),
What this run showed with at most four concise observations, Next training implication
and collapsed More details. Expand More details and verify original reasons/confidence/
checkout summary. Edit checkout remains available. Return a screenshot and confirm
the wording/outcomes are unchanged.

## Static-only additions

Presentation regression tests were added for full/partial kilometre labels, the
existing display-scale clipping diagnostic, and the eight bundled hero mappings.
They were not executed in this pass. Map/provider, stored samples, matching,
assignment semantics, split/delta/half-run calculations and coaching/domain logic
were intentionally untouched.
