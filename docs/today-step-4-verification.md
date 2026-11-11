# Step 4: Today verification

Branch: `feat/step-4-today`, stacked on Step 3 PR #54 (`feat/step-3-navigation-shell`). Scope: redesign spec §5.1 and §9 Step 4. No Step 5 work is included.

Today is one scroll with before-run, after-run and rest-day heroes, shared cards/chips/metrics/charts/rows, and the existing detail destinations. Settings stays in the hero. Original prescriptions, effective adjustments and recorded performance remain distinct.

## Quick facts and reference resolution

The latest user instruction supersedes the earlier before-run-only interpretation. Spec §5.1 now specifies quick facts in all three states, with equal-width columns for exactly the available facts and no blank reserved columns.

| State | Facts and sources | Missing-data behavior |
|---|---|---|
| Before run | Prescribed distance, existing structured-workout duration estimate, prescribed RPE range, distance difference from the previous authoritatively linked long run | Hide unavailable facts; show the comparison only on long-run days with a known prior long run. |
| After run | Recorded average HR, recorded cadence, recorded elevation gain, existing WorkoutResponse effort comparison | Never repeat hero km/time/pace. Hide the whole card below two facts. Unknown comparison or missing checkout never becomes on-target effort. Moving cadence retains an explicit moving label when used. |
| Rest day | `km done`, `runs`, `to go`, `readiness` | Canonical current-week actuals, remaining known weekly planned distance and the existing feedback readiness average. Hide missing facts and hide the card below two facts. Planned distance never becomes recorded distance. |

**Deliberate deviation from the after-run PNG:** the card contains only complementary HR/cadence/elevation/effort-vs-plan facts; the PNG's duplicate km/time/pace stay exclusively in the hero. This deviation and the all-state quick-facts rule were explicitly requested by the user.

The before/rest layouts and the light/dark palette, fading hero, track lanes, typography, cards, dividers, goal progress, WeekBars, form tiles and list rows were compared with the six Today PNG boards in `UI/redesign/`. Actual fixture/source availability determines which optional elements appear. No additional spec/PNG conflict requiring resolution was found.

## Data boundaries

- `HomePresentation.kt` selects the state and applies visibility/formatting rules. A completed run today takes precedence over a remaining prescription; date selection uses the environment clock's zone and completion date, including overnight runs.
- `HomeViewModel` exposes missing UI source fields and read-only profile, route and recorded-metric observations. Existing coaching analysis, workout selection, week actions and all writes remain unchanged. No profile rebuild, metric refresh, reconciliation or feedback save is added.
- Workouts link by their real IDs; completion targets follow the execution's original/adjusted comparison basis. Multi-block/repeated targets never become a whole-run pace target.
- Recorded weekly distance and pace require complete actual inputs. Missing distance leaves a gap, not a rest bar or zero. Completed-session counts continue to use saved execution semantics.
- Profile measures and readiness appear only when available. Unsupported HR/cadence trends, current goal assessment and load-on-plan chips stay hidden. A completion countdown requires an explicitly saved milestone date; a forecast plan end date is insufficient.
- Route thumbnails use recorded points and the existing renderer, now using the current palette. No placeholder route is generated.
- A missing checkout exposes **Add details** through the Step 3 checkout stub to existing run details. Quick-save effort chips remain hidden: the current checkout requires RPE and other answers that a single effort choice cannot supply. No answers, successful save, or default “Just right” selection are fabricated. Standalone checkout implementation remains a later step.

## Automated checks

Final verification on 2026-11-11, after the product/gallery refinements:

| Exact command | Actual result |
|---|---|
| `./gradlew test --rerun-tasks` | **Passed**, 29 s; 28 actionable tasks executed; 581 tests in 57 suites, zero failures/errors/skips. Includes 37 presentation tests. |
| `./gradlew assembleDebug --rerun-tasks` | **Passed**, 20 s; all 39 actionable tasks executed. |
| `ANDROID_SERIAL=emulator-5554 ./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.example.openruncoach.ui.home.TodayPreviewTest,com.example.openruncoach.ui.home.TodayQuickFactsTest,com.example.openruncoach.ui.navigation.NavigationShellTest` | **Passed**, 1 min 6 s; all 23 tests passed. |
| `git diff --check` | **Passed**. |

Core CI (`./gradlew --no-daemon -p ci/core-tests test`) was **not run**: no domain source changed, and that domain-only build does not compile this UI/presentation scope. Both full Android checks above ran successfully. Existing annotation-target/deprecation and SDK XML metadata warnings did not fail the builds.

The state/visibility tests cover all three states, precedence, local dates, stale flags, ignored/non-canonical/future runs, missing and nonfinite values, same-type comparison provenance, structured targets, planned-versus-actual totals, unknown checkout/adherence, original-versus-adjusted comparisons, profile/route omissions and explicit milestone dates. New quick-facts checks cover zero/one/two available facts, matching run/interval identity, real zero elevation, unknown effort comparisons, and rest-day totals.

The first equal-column implementation clipped a caption at 130% fonts; replacing intrinsic row-height measurement fixed it. A subsequent ambiguous `km done` test selector was scoped to the facts card. All final checks above pass.

## Emulator and screenshot evidence

Status: **Passed on emulator** — `Medium_Phone_API_37.0`, Android 17, `emulator-5554`, 390 × 844 dp, density 2. Physical-phone verification: **Pending**, not performed.

- Eighteen render cases cover before/after/rest × light/dark × fonts 100%/130%/200%. They check reachable content, text layout bounds, equal fact columns and detail-entry callback IDs.
- Two additional UI tests verify two-column cards and live disappearance when only one fact remains.
- Three navigation tests open all 13 destinations and verify tab visibility, Settings/back, the checkout-stub link, and light status-bar icon mode in both themes. The navigation sweep uses an isolated in-memory database.
- Design previews and their synthetic data live only in `src/debug`; production Today never uses them. Captures contain no owner health data. Images are unedited Compose-root captures, not mockups. OS status-bar glyphs are outside the capture; their appearance mode is asserted separately by navigation tests.

[Light/dark captures for all three states and larger fonts](UI/today-step-4/README.md). Full normal-font screens are preserved as sequential viewport pages, without stitching or retouching.

Final screenshot-preservation rerun: `adb -s emulator-5554 shell am instrument -w -e class com.example.openruncoach.ui.home.TodayPreviewTest,com.example.openruncoach.ui.home.TodayQuickFactsTest,com.example.openruncoach.ui.navigation.NavigationShellTest com.example.openruncoach.test/androidx.test.runner.AndroidJUnitRunner` — **OK (23 tests)**, 58.93 s. Thirty unedited Today PNG pages are included.
