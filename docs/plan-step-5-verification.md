# Step 5: Plan

Branch `feat/step-5-plan`, based on the published Step 4 head. GitHub still reported #54/#55 open when this task began, so the PR is stacked on `feat/step-4-today`; no prerequisite was merged or rewritten by this task.

Plan now uses the fading hero, source-backed long-run and weekly-distance Canvas charts, accent/onAccent selected week chips, shared workout rows, and the saved explanation under Why this week. The scheduling and history sheets use `bg`, 20 dp top corners, zero tonal elevation and a 32% scrim. Existing date selection, postpone, generation, history, previous-plan/back and workout callbacks retain their ViewModel handlers. PlanViewModel, domain models, repositories and route strings are unchanged.

Chart projections use actual generated weeks, not a fixed sample series. Past observed bars use canonical associated-run actuals, with saved execution actuals only when there is no canonical actual object. A present-but-unknown canonical distance never falls back to a stale execution value. Current/future bars remain explicitly planned. Unknown values disappear; a passed date alone never implies completion. Goal columns require a real goal distance and an explicit Completion Run date. Unsupported current “On track” assessment is hidden. Plan details retain readiness, full reasons, prescription/adjustment context and regeneration wording.

## Final verification — 2026-11-11

| Command | Actual result |
|---|---|
| `./gradlew test --rerun-tasks` | Passed, 14 s; all 28 actionable tasks executed. 587 tests in 58 suites, zero failures/errors/skips. |
| `./gradlew assembleDebug --rerun-tasks` | Passed, 14 s; all 39 actionable tasks executed. |
| `./gradlew assembleDebugAndroidTest` | Passed; prepared the matching final test APK. |
| `adb -s emulator-5554 shell am instrument -w -e class com.example.openruncoach.ui.plan.PlanPreviewTest,com.example.openruncoach.ui.navigation.NavigationShellTest com.example.openruncoach.test/androidx.test.runner.AndroidJUnitRunner` | OK (13 tests), 29.422 s, on the final source/APKs. |
| `git diff --check` | Passed. |

The six added chart tests cover real week/goal column counts, current targets versus past actuals, missing canonical distance, corrected actuals, absence of a saved completion date, missing targets and assessment plans without long runs. Existing Plan presentation regressions also pass. Core CI was not run: no domain source changed and its isolated build does not compile this UI scope. Existing compiler warnings remain warnings.

## Device / reference evidence

Status: **Passed on emulator**, Medium_Phone_API_37.0 / Android 17, 390 × 844 dp at density 2. Physical phone: **Pending**, not tested.

Ten Plan cases cover light/dark main views at 100%, 130% and 200% fonts plus both sheets in each theme. They check text bounds, dark-body ink, week selection and retained sheet actions. Three existing navigation tests cover all 13 destinations and tab visibility. Preview fixtures are debug-only, and the navigation sweep uses an isolated in-memory database. No owner health data is captured.

Compared with `UI/redesign/Plan · light.png` and `Plan · dark.png`: hero/track fade, chart styling, week chips, rows, palette and typography. The previously resolved selected-week override is retained: accent/onAccent, including dark-mode ink text. Optional unsupported assessment data is omitted. No new spec/PNG conflict was found. Screenshot review caught dark text inheritance; the final composition explicitly provides palette ink and tests its dark-body text.

[All 12 raw captures](UI/plan-step-5/README.md). Main captures preserve the full scroll as two viewport pages per theme. Dialog captures preserve transparency outside the sheet; OS glyphs are outside Compose captures. No images are stitched or retouched. Step 6 has not started in this PR.
