# History / completed-run polish: manual verification

For the user to execute. No Gradle, automated tests, installation, or device verification was executed for this pass. Compare against `docs/UI/history-workout-detail-target.png`. Existing health records must not be cleared.

The later [final visual polish checklist](completed-run-final-polish-verification.md) supersedes visual layout expectations here: chart diagnostics now live in Recording diagnostics, the selector is a pill, and split ranking metrics are removed. Data/calculation/provider regression checks below still apply.

## 1 — Overview metrics

Open a completed run with distance/duration/pace/HR. Expect one short horizontal card with four equal icon/value/label columns and subtle separators. Missing HR removes that column, never fabricates zero. At narrow widths or larger font sizes, the readable single row can scroll instead of becoming 2×2. Return a screenshot and all displayed values.

## 2 — Street map and fallback

In a debug build with the development provider enabled, open a GPS-routed run. Expect real dark streets/paths, muted labels, mint route, start/finish and whole-route fitting with padding and rounded edges. Check visible attribution. Switch Pace/Heart rate/Cadence several times: the map must not reload/refit. Return a screenshot.

Disable connectivity if practical and reopen a route whose street tiles are not already cached. Expect the Canvas route silhouette immediately, not a blank map. Cached real street tiles may remain available offline, which is also valid. A style/tile error must retain the silhouette. Blank `routeMapStyleUri` forces Canvas; release builds without an explicitly configured provider also use Canvas. Return the offline/failure screenshot. No live location permission is needed.

## 3 — Pace graph

Check thin mint line, restrained fading area fill, three Y labels, subtle grid, compact height and clean elapsed-time axis. Genuine slowdowns/stopped gaps must remain represented; area fill must not bridge missing-data gaps. If existing robust scale clips extreme display values, the quiet clipping note must appear under Data sources → Recording diagnostics; Fastest 30 sec is also available there. Average/target metrics dominate. A legitimate continuous target is a faint horizontal band; interval/quality work targets must not be treated as whole-run targets. Return screenshot and exact average pace/target range. Stored source samples and Analysis must be unchanged.

## 4 — History workout types

Open History with associated Easy/Recovery, Long and quality workouts. Expect narrow low-saturation row-edge accents from their actual prescription type, not guessed pace/title. Unknown runs stay neutral. Rows, thumbnails and metadata remain compact, with one canonical physical activity per row. Return screenshot.

## 5 — Split Δ

For every split, return distance, displayed normalized pace, displayed whole-run average and Δ. Formula uses unrounded seconds/km: split pace − whole-run average. Expected examples: 6:41 vs 6:27 → `+0:14`; 6:14 vs 6:27 → `−0:13`. Positive is slower; negative faster. Display rounds only at the end, so subtracting already-rounded labels may differ by one second. Missing whole-run average suppresses Δ rather than substituting zero.

## 6 — Equal-distance negative / positive split

Use a run with trustworthy route evidence and at least 1 km total distance. Expand Half-run details. Return first-half distance, duration and pace; second-half distance, duration and pace; calculated displayed difference and direction label. Each distance must be half the actual total, not an arbitrary kilometre. Difference = second-half pace − first-half pace. Within ±3 sec/km is Even split; otherwise negative is Negative split, positive is Positive split. Durations/pace/delta labels are rounded for display; the calculation uses interpolated timestamps and unrounded values. Walking/stopped elapsed time in the second half must make it slower. No sufficient distance/time coverage means no half-run metric.

## 7 — Partial split

Confirm the final partial split keeps its actual distance/normalized pace and has Δ when a real whole-run average exists. Fastest/Slowest split rankings are no longer shown. Prioritize Km/Pace/Δ/HR; cadence remains available under Split details rather than squeezing the table. Return screenshot and partial values.

## 8 — Coach

Open the current Long Run example. Default should show existing recommendation/status, at most four concise observations (such as stopped after 1.16 of 4.80 km, sustained walking/stopped time, significant pain), and one concise action-consistent implication. No prose wall or repeated Analysis table. Expand More details: full original reasons, confidence and checkout summary must remain accessible. Editing checkout still works. Return collapsed and expanded screenshots.

## 9 — Analysis

Verify existing factual values remain unchanged for that Long Run: planned 4.80 km, actual 1.16 km, aborted, partial volume, high session cost. Verify continuity, adherence and confidence against the prior build. Return screenshot and exact values. Ignore run remains secondary, Data sources collapsed by default, and pending checkout accessible above Overview content.

## 10 — Provider regression

Change preferred workout source. Reopen the same physical run: canonical summary changes according to existing selection while valid high-confidence route/cadence enrichment remains. History must not create a duplicate provider row. Return before/after screenshots and source names from Data sources.

## 11 — Workout association

Verify Assign to workout on an unmatched run, Change association and Unassign on an assigned run. Existing canonical association and checkout behavior must remain unchanged. Return association screenshots and report the selected prescription.

## Added unit tests (not executed here)

- `RunPacingSummaryTest`: all nine requested Δ/half-split cases plus reuse of existing route calibration, low confidence and edge coverage safety.
- `RouteMapGeometryTest`: padded whole-route camera fitting, vertical/horizontal/zero-span/short routes, invalid coordinates, date line, deterministic point limits; no network renderer tests.
- `CompletedRunPresentationTest`: signed delta formatting plus preserved whole-run target eligibility tests.
- `CoachPresentationTest`: four-observation cap, actual Long Run evidence, action-only implication wording, unavailable evidence, unchanged original reasons/outcomes.

## Files edited / added by this refinement

- `app/src/main/java/com/example/openruncoach/ui/history/`: `HistoryScreen.kt`, `HistoryViewModel.kt`.
- `app/src/main/java/com/example/openruncoach/ui/health/`: `RunDetailScreen.kt`, `RunDetailViewModel.kt`, `RunCharts.kt`, `CompletedRunPresentation.kt`, `CoachPresentation.kt`.
- `app/src/main/java/com/example/openruncoach/ui/components/`: `CompletedRunMetrics.kt`, `RunRouteMap.kt`, `RouteMapGeometry.kt`. The existing Canvas renderer and its prior geometry tests are reused/preserved.
- `app/src/main/java/com/example/openruncoach/theme/WorkoutAccents.kt`.
- `app/src/main/java/com/example/openruncoach/data/WorkoutExecutionRepository.kt`: read-only associated-prescription metadata; selection/writes unchanged.
- `app/src/main/java/com/example/openruncoach/domain/route/`: `RouteSplitDeriver.kt` (opt-in, non-persisted distance/time output only), `RunPacingSummary.kt`.
- `app/src/main/res/drawable/`: `ic_heart.xml`, `ic_pace.xml`.
- Build configuration: `app/build.gradle.kts`, `gradle/libs.versions.toml`, `app/src/main/AndroidManifest.xml`.
- Tests: `app/src/test/java/com/example/openruncoach/domain/route/RunPacingSummaryTest.kt`, `app/src/test/java/com/example/openruncoach/ui/components/RouteMapGeometryTest.kt`, `app/src/test/java/com/example/openruncoach/ui/health/CompletedRunPresentationTest.kt`, `app/src/test/java/com/example/openruncoach/ui/health/CoachPresentationTest.kt`.
- Documentation: `README.md`, `docs/OpenRunCoach_UI_RULEBOOK.md`, `docs/history-completed-run-verification.md`, `docs/history-completed-run-polish-verification.md`.

## Provider configuration and privacy

MapLibre Native Android is only the renderer. `routeMapStyleUri` is a replaceable HTTPS or asset style configuration. Debug defaults to OpenFreeMap Dark with no embedded key; release requires an explicitly reviewed provider, otherwise Canvas. Review licensing/attribution/privacy/capacity before distribution; no production availability promise is made for a public development endpoint. Tile/style requests expose the viewed geographic area and ordinary HTTP metadata to that provider. Route GeoJSON and health/coaching metrics stay local; location tracking is not enabled. A blank URI disables network maps. See [MapLibre setup](https://github.com/maplibre/maplibre-native/blob/main/platform/android/docs/getting-started.md) and [OpenFreeMap mobile/style documentation](https://openfreemap.org/quick_start/).
