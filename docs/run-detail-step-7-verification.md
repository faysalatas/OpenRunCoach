# Step 7: Run detail

Branch `feat/step-7-run-detail`, stacked on the published Step 6 head. GitHub still reports #56/#57 open and fetched main lacks the shared redesign primitives, so no prerequisite is merged or rewritten here.

The old Overview/Splits/Analysis/Coach tab row is removed. One scroll uses the shared fading hero, metric trio, chips, coach block, real route, pace/HR/cadence cards, proportional split bars, Plan vs. run, mini tiles and link rows. The [approved content inventory](run-detail-step-7-content-map.md) accounts for every old-tab field/action. Full evidence and provenance remain in disclosures; assignment and visibility actions use the same ViewModel handlers. The existing checkout editor remains available until Step 8. RunDetailViewModel, domain models, repositories and database fields are unchanged.

Metric trio kept per spec and Run2.dc.html source; PNG omitted it.

The referenced HTML files are absent locally; the owner's explicit resolution supplies their dimensions. Heart-rate/cadence cards require data, unknown adherence remains unknown, segment pace never becomes a whole-run band, and Fastest 30 seconds is omitted. Actual elapsed-time spacing, chart gaps and prepared scales are retained. Route Canvas/MapLibre colors follow the palette; previews use the existing Canvas fallback to avoid external tiles. Ignore/restore stays visible at the bottom with a confirmation/cancel gate before the existing handler. The inspected old screen called that handler directly; this UI gate supplies the confirmation requested by the owner without changing the write handler.

## Final verification — 2026-11-11

| Command | Result |
|---|---|
| `./gradlew test --rerun-tasks` | Passed, 15 s; 28/28 actionable tasks executed. 596 tests in 59 suites, zero failures/errors/skips. |
| `./gradlew assembleDebug --rerun-tasks` | Passed, 16 s; 39/39 actionable tasks executed. |
| `./gradlew assembleDebugAndroidTest` | Passed; matching final test APK prepared. |
| `adb -s emulator-5554 shell am instrument -w -e class com.example.openruncoach.ui.health.RunDetailPreviewTest,com.example.openruncoach.ui.health.RunDetailMissingDataTest,com.example.openruncoach.ui.navigation.NavigationShellTest com.example.openruncoach.test/androidx.test.runner.AndroidJUnitRunner` | OK (10 tests), 32.412 s, final source/APKs. |
| `git diff --check` | Passed. |

Core CI not run: no domain changes, and its isolated build does not compile UI scope. Existing compiler warnings remain warnings. Earlier development compile errors were corrected; the final results above are after gallery refinements.

## Device / reference evidence

Status: **Passed on emulator**, Medium_Phone_API_37.0 / Android 17, 390 × 844 dp. Physical phone: **Pending**, not tested or modified.

Six preview cases cover both themes at 100%, 130% and 200% fonts, the whole scroll, removed tabs, pace/target-band presence, diagnostics, link callbacks and visibility confirmation/cancellation. A missing-data case verifies HR, cadence, elevation, route/splits and adherence are not invented. Three navigation tests cover all 13 existing destinations and tab visibility.

Compared with both Run detail PNGs: hero/palette, chips, route, chart line/band styling, proportional splits, comparison and links. The metric trio follows the owner's resolved exception; split widths follow the already-approved mathematical rule. Exact evidence remains accessible below the reference's compact composition. No new spec/PNG conflict found.

[Raw screenshots](UI/run-detail-step-7/README.md) use debug-only synthetic inputs, not owner health data. Normal-font pages cover the entire scroll; large-font captures show the first viewport while tests scroll every section. Compose captures omit OS glyphs; no stitching or retouching. Step 8 has not started in this PR.
