# Step 6: Runs

Branch `feat/step-6-runs`, stacked on `feat/step-5-plan` (PR #56). The Step 5 base retains the published Step 4 head because GitHub still reported those prerequisite PRs open when this task started. This task does not merge or rewrite them.

Runs uses the shared fading hero, PaceLineChart, MiniStatCard, section labels and RunRow family. The hero summarizes the current calendar week, matching the PNG's three runs / 13 km; its month eyebrow provides date context. The ignored-run entry and run-detail callbacks remain available. History is lazy and groups by local Monday–Sunday in a pure presentation mapper; repository selection, stored records, route strings and domain logic are unchanged.

Easy-pace observations reuse the existing four-week calculator and authoritative completed workout types, never inferred classifications. Missing weeks remain gaps; a missing trend hides its card. The existing profile supplies typical easy heart rate and the longest run over 28 days. The unsupported easy-heart-rate comparison from the PNG is hidden. Ignored mode omits active-profile/training insights. Missing distances hide incomplete totals; missing row metrics disappear. The ViewModel only adds read-only profile/type inputs for presentation, retaining its existing selection and grouping.

## Final verification — 2026-11-11

| Command | Actual result |
|---|---|
| `./gradlew test --rerun-tasks` | Passed, 14 s; all 28 actionable tasks executed. 596 tests in 59 suites, zero failures/errors/skips. |
| `./gradlew assembleDebug --rerun-tasks` | Passed, 14 s; all 39 actionable tasks executed. |
| `./gradlew assembleDebugAndroidTest` | Passed; matching final test APK prepared (up-to-date). |
| `adb -s emulator-5554 shell am instrument -w -e class com.example.openruncoach.ui.history.HistoryPreviewTest,com.example.openruncoach.ui.navigation.NavigationShellTest com.example.openruncoach.test/androidx.test.runner.AndroidJUnitRunner` | OK (9 tests), 21.983 s, on final source/APKs. |
| `git diff --check` | Passed. |

Nine new unit tests cover Monday/Sunday membership across locales, month/year boundaries, local midnight and daylight saving, missing totals, preserved item identity/ignored status, empty history, localized older labels, unsupported easy observations, ignored-mode insights and weighted current-week hero totals. Core CI was not run: this scope changes no domain source, and its isolated build does not compile UI code. Existing compiler warnings remain warnings.

## Device / reference evidence

Status: **Passed on emulator**, Medium_Phone_API_37.0 / Android 17, 390 × 844 dp at density 2. Physical phone: **Pending**, not tested.

Six Runs cases exercise both themes at 100%, 130% and 200% font scales, scroll through every week, check text bounds and verify original run-ID and ignored-toggle callbacks. Three navigation cases exercise all 13 existing destinations and tab visibility. Preview fixtures are debug-only; navigation uses an isolated in-memory database. No owner health data is captured.

Compared with `UI/redesign/Runs · light.png` and `Runs · dark.png`: palette, hero/track fade, four-point chart, two-column mini cards, week headings, run dots/rows and bottom navigation. No new spec/PNG conflict was found. Source-backed values differ from illustrative numbers; no invented heart-rate delta is displayed. Existing ignored-run access is retained. Larger text reflows metrics/cards and uses the existing chart's horizontal scrolling.

[All eight raw captures](UI/runs-step-6/README.md). Normal-font captures cover the full scroll in two viewport pages per theme; larger-font captures show the first viewport, while automated checks scroll all content. OS status/navigation glyphs are outside Compose root captures. No images are stitched or retouched. Step 7 has not started.
