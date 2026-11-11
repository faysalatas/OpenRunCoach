# Step 8: Post-run checkout

Branch `feat/step-8-run-checkout`, stacked on Step 7 PR #58. This is the dedicated editor on the existing `run-checkout?runId=` route. Today's existing Add details callback now reaches it; Run detail's checkout row navigates to it. The inline editor and stub are removed. Coach analysis is linked but not redesigned here.

`runDetailViewModel` reuses the same constructor/dependencies and RunDetailViewModel remains byte-for-byte unchanged. Form initialization, `updateForm`, `editFeedback`, `cancelFeedbackEdit` and `saveFeedback` remain authoritative. No repository/domain/database changes. The layout uses shared hero, option chips, cards and primary button. RPE is 1–10, cardio/fatigue/pain/completion retain their enum values, readiness stays optional 1–5 (tap again or Clear readiness), and notes retain the original save normalization. Save/update and error/success messages stay on the editor; cancellation returns without saving. Missing run/loading/error states remain visible. No sample defaults or quick-save inference are added.

## Final verification — 2026-11-11

| Command | Actual result |
|---|---|
| `./gradlew test --rerun-tasks` | Passed, 15 s; 28/28 actionable tasks executed. 601 tests in 60 suites, zero failures/errors/skips. |
| `./gradlew assembleDebug --rerun-tasks` | Passed, 16 s; 39/39 actionable tasks executed. |
| `./gradlew assembleDebugAndroidTest` | Passed; matching final test APK prepared. |
| `adb -s emulator-5554 shell am instrument -w -e class com.example.openruncoach.ui.health.RunCheckoutPreviewTest,com.example.openruncoach.ui.health.RunCheckoutSaveTest,com.example.openruncoach.ui.health.RunDetailPreviewTest,com.example.openruncoach.ui.health.RunDetailMissingDataTest,com.example.openruncoach.ui.navigation.NavigationShellTest,com.example.openruncoach.data.RunFeedbackRepositoryTest#savingAgainUpdatesTheOnlyFeedbackForARun com.example.openruncoach.test/androidx.test.runner.AndroidJUnitRunner` | OK (19 tests), 54.924 s, final source/APKs. |
| `git diff --check` | Passed. |

Five new JVM tests exercise every valid value, outside-range boundaries and optional readiness through the existing validator. The actual ViewModel test rejects nonnumeric/out-of-range values without writes, saves RPE/readiness at both inclusive endpoints, updates the same checkout ID/createdAt, preserves notes normalization and cancels unsaved edits. The existing repository save/update identity test is also rerun. All persistence checks use synthetic in-memory databases.

Core CI not run: production domain sources are unchanged; the isolated build does not compile UI scope. Existing compiler warnings remain warnings.

## Device / reference evidence

Status: **Passed on emulator**, Medium_Phone_API_37.0 / Android 17, 390 × 844 dp. Physical phone: **Pending**, not tested or modified.

Six checkout cases cover light/dark at 100%, 130% and 200% fonts, all fields, readiness clearing, save/update/cancel callbacks and full-scroll text bounds. Six retained Run detail cases plus missing-data coverage verify checkout/coach/inspector callbacks and visibility confirmation. Four navigation cases cover all existing routes and the real Run detail → checkout → Run back path. ViewModel and repository tests verify real saves separately from preview callbacks.

Compared with both Post-run checkout PNGs: hero/title, five-column normal-font RPE grid, wrapping choices, readiness, outlined notes and actions. Larger fonts reduce numeric-grid columns. No new spec/PNG conflict was found. The Step 7 metric-trio resolution remains: Metric trio kept per spec and Run2.dc.html source; PNG omitted it.

[Raw light/dark captures](UI/run-checkout-step-8/README.md) use debug-only synthetic inputs. Normal-font pages cover the entire scroll; larger-font captures show the first viewport while tests inspect all content. No owner health data, screenshot retouching or compositing; OS glyphs are outside Compose root captures. Step 9 has not started.
