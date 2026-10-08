# History and completed-run manual verification

For the user to execute. No build, automated tests, or device verification was performed for this UI pass. Use existing real records; do not clear app data. Compare with `docs/UI/history-workout-detail-target.png`.

## 1 — History with route

Open History and find a canonical physical run with Samsung route enrichment. Expect one compact row, a mint route thumbnail, correct distance/duration/average pace, and no separate Samsung/Strava duplicate row. Return a screenshot and those three values.

## 2 — History without route

Find a run with no valid route. Expect a clean text-only row, no empty thumbnail or “Route unavailable” text, and consistent content alignment. Return a screenshot.

## 3 — Real Overview

Open a real routed run. Confirm title and local date/time, correct available distance/duration/average pace/average HR, and a large centered canonical route below the metrics. Missing HR must not create a fake metric. The route must remain available independently of summary provider preference. Return a screenshot and actual metric values. Also check a run with no route: charts move up without an empty map card.

## 4 — Pace

Select Pace. Confirm the chart uses genuine speed-derived pace, readable min/km scaling, and elapsed-time labels. An appropriate single-segment aerobic prescription may show its target range; intervals/threshold/strides or repeated/multi-block work must not show their segment target as a whole-run band. Return a screenshot, average pace, Fastest 30 sec pace if present, and target range if legitimately present. Missing/isolated samples must not manufacture a chart tab.

## 5 — Heart rate

Select Heart rate when present. Confirm actual HR samples, readable bpm scale and elapsed-time labels, no invented zones, and average/maximum only if real stored summaries exist. Return screenshot and the exact displayed summaries. If adequate HR samples do not exist, the tab should be absent.

## 6 — Cadence

Select Cadence when present. Confirm genuine cadence samples and spm summaries, including whether the label says Moving average or Average cadence. Samsung enrichment must work when summary source is not Samsung. Return screenshot and exact average/maximum values. No adequate cadence samples means no tab.

## 7 — Splits

Open Splits for a routed run. Confirm actual split distances (full standardized splits `1.00 km`, final partial its actual distance), rather than ordinal distances. HR/cadence values appear only with real evidence; cadence moves to Split details on narrow screens. An individual missing value in an otherwise real column stays `—`. Open Split details. Return a screenshot and, for every split in order: ordinal, distance, duration, average pace, average HR, average cadence, and derivation. Mark unavailable values as unavailable, not zero.

## 8 — Split provenance

For GPS-derived splits, confirm the quiet note “Derived from recorded GPS route.” Source laps/speed/distance samples must show their actual corresponding derivation, not a GPS claim. Confirm average pace and fastest/slowest full-split summaries are factual; partial splits are excluded from full-split comparisons. Return the displayed note and summaries.

## 9 — Analysis association

Open the Thursday run that was manually assigned. Confirm the association persists, Outcome and Planned vs. actual appear, and Evidence shows existing interpretation confidence/reasons. Change association must still work; Unassign must remain accessible. Return screenshots and actual planned/actual values. For quality work, whole-run average pace is context only, not evidence of segment pace adherence.

## 10 — Unmatched run

Open an unmatched run and select Analysis. Confirm prescription comparison is unavailable and Assign to workout remains clearly accessible. Exercise the existing assignment flow. When checkout is pending, confirm its action is visible above Overview/Splits/Analysis content and opens the existing checkout editor on Coach. Return a screenshot.

## 11 — Source preference

Change preferred workout source using existing Settings, then reopen the same physical run. Confirm the canonical History row remains singular and valid route/cadence enrichment does not arbitrarily disappear. Expand Data sources: report Summary, Route, Cadence, Heart rate where available. Recording diagnostics must retain existing source/duplicate/metric and inspector actions, with debug IDs absent from the compact source summary. Return before/after screenshots and source names. Inspect Coach after checkout: interpretation must use existing evidence/decision reasons without an invented score.

## 12 — Short run

Open a run shorter than 1 km (for example, 390 m). Confirm no crash, no fabricated full 1 km split, and a clean layout. A real partial split is acceptable if the existing engine produced one; otherwise the small unavailable message is appropriate. Return actual split distance/duration/pace when present. Check layout at larger font size and narrow width. If existing test routes include vertical/horizontal/very short paths, confirm they remain centered and proportionate.

## 13 — Continuity regression

In the existing simulator, confirm D → Continuous, E → Interrupted (displayed as sustained walking/stopped interval in Analysis), M → Unknown (displayed as not enough movement samples). Report exact shown values. No simulator or evaluator changes are part of this UI task.
