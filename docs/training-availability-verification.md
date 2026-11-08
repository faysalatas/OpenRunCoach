# Training availability — issue #16

## Problem and result

Related issue: [#16](https://github.com/faysalatas/OpenRunCoach/issues/16).

Generated plans previously used fixed day offsets without an athlete-owned availability setting. Settings now offers Unavailable / Available / Preferred for every weekday. Saving changes only the separate athlete setting. Explicit plan generation consumes it through one provider-independent domain scheduler; normal reads preserve saved dates. An unavailable Completion Run date produces a visible conflict instead of being moved.

## Scope and architecture

- `TrainingAvailability` is a domain value: a hard available-day set plus a preferred subset. Missing settings retain legacy offsets; an explicit empty set permits no running.
- `TrainingAvailabilityRepository` owns a singleton Room row independent of goals, plans and providers. Database version 19 adds only this table through `MIGRATION_18_19`; no existing rows are updated. No health permissions or dependencies are added. Simulator and production databases retain their existing isolation.
- `TrainingSchedulePolicy` places only new structured prescriptions within each seven-day plan week. Retained workouts on either side of a window are immutable constraints for continuation. It preserves pinned milestones first, maximizes safe session count, then favors endurance, aerobic work, preferred days and proximity to original offsets. Deterministic search order resolves ties.
- Minimum demanding spacing is two calendar days, or three when a Completion Run is involved. Demanding includes long runs, quality, race-specific work and strides. No same-date running workouts. The scheduler omits sessions instead of redistributing their load; saved week reasons explain the reduction and name omitted sessions. Rest-only weeks contain an explicit zero-load Rest entry; rest does not require an available running day.
- Normal plans and assessments use the same policy. The existing quality-eligibility and structured-workout rules remain upstream. Generation metadata advances to version 5. Original prescriptions contain their resulting scheduled dates; later effective adjustments and actual performance stay separate.
- Saving availability never regenerates. Existing deliberate regeneration / milestone actions replace the active forecast while preserving the old plan. Home, Plan and details read the same persisted structured-workout dates. Plan's observed presentation uses the existing pure readiness evaluator without persistence, so availability updates cannot trigger readiness writes.
- Suggested Completion Run dates exclude unavailable days. A saved milestone can conflict after settings change: Settings and Plan show the conflict, its saved date remains unchanged, and a subsequent generation attempt rejects it. The athlete can explicitly select another date or make that day available.

## Acceptance criteria and remaining work

| Issue #16 criterion | Implementation / evidence |
| --- | --- |
| Select days with hard versus soft semantics | Settings controls and domain model implemented; phone verification pending. |
| Persist independently of a generated plan | Singleton athlete table and repository implemented; persistence tests pass on the emulator; manual restart/UI verification pending. |
| Initial generation and rolling extension share scheduling | Initial generation, assessments and rolling extension use the same policy on this PR #20 branch; fixed-clock and Room continuation tests pass. Manual horizon journey pending. |
| Demanding spacing and no multiple workouts per day | Bounded placement policy checks both retained boundaries and newly placed sessions; automated tests pass. |
| Conservatively reduce and explain impossible frequency | Omit sessions, retain their structured prescriptions only when selected, recalculate totals, persist reasons. Automated tests pass. |
| Respect explicit Completion Run and surface conflicts | Pinned date plus typed unavailable-day conflict; existing milestone transaction rolls back failed replacement. Phone verification pending. |
| Preserve completed workouts / actual runs | Settings writes only the availability table. Existing explicit regeneration archives the preceding plan. Observer/persistence tests pass on the emulator; manual restart/UI verification pending. |
| Deliberate movement only; non-mutating reads | Settings saves never replan; Plan presentation no longer calls the readiness writer. Phone verification pending. |
| Missing settings preserve deterministic behavior | Policy returns original proposals unchanged when settings/context are absent; explicit empty is distinct. Automated tests pass. |
| Fixed-clock scenarios | 2/3/4 days, adjacent-only days, preferences, impossible spacing, retained boundaries, setting changes, empty/reset, assessment and milestone conflicts are covered by passing automated tests. |
| Home / Plan / detail date consistency | Same saved workout records, plus passing constrained-date mapper round-trip test. Phone screenshots pending. |

Automatic horizon replenishment, event triggers, continuation persistence/idempotency and progression across extensions remain [#4](https://github.com/faysalatas/OpenRunCoach/issues/4). On this integrated PR #20 branch it reads current availability for explicit continuation and passes retained prescriptions plus the milestone to `TrainingSchedulePolicy.schedule`. See the rolling-plan report for the current evidence and remaining phone checklist. Reachable pre-run check-in remains #6 and must not change these settings implicitly.

Schema `19.json` is already tracked and remains unchanged. The shared migration registry now includes `MIGRATION_18_19`; the inherited chain test discovers all 19 schemas and validates upgrades from every historical version. This issue remains open pending the manual phone checklist; rolling extension is integrated and automatically tested on this branch.

## Automated verification

Updated 2026-11-08 for PR #20's review (#34), integrating main `2ef3905` into rolling tip `d6efd77c`. The original availability behavior, UI, migration SQL and schema 19 remain unchanged. The current source includes the shared migration registry, passive-read guards and explicit Unit test methods from main. Rolling continuation uses the same availability contract; its horizon gate, scalar-only evidence loading and injected reconciler connection are described in [the rolling-plan report](rolling-plan-verification.md).

Run from the isolated worktree `/tmp/openruncoach-rolling-review`:

```bash
./gradlew --no-daemon --no-watch-fs -p ci/core-tests test
ANDROID_SERIAL=emulator-5554 ./gradlew --no-daemon --no-watch-fs test assembleDebug assembleDebugAndroidTest connectedDebugAndroidTest
git diff --check
git diff --cached --check
```

PASS: **434 Core domain tests**, **555 app JVM tests**, both Debug APK builds and the **entire 92-test instrumentation suite**. Zero failures, errors or skipped tests. Both whitespace checks passed.

The final suite includes the previously contradictory Completion Run boundary fixture and its separate unavailable-Monday regression, availability persistence/history-preservation tests, the seeded 18→19 migration test, all legacy feedback fixtures and the six diagnostic-export tests. Room's full-chain loop now covers all 18 historical starting versions through schema 19. No production migration SQL or exported schema changed during this integration.

Environment: existing local Samsung AAR and Android SDK 36, Gradle 9.1.0, JDK 21 launcher with the configured JDK 17 toolchain. Emulator `Medium_Phone_API_37.0`, Android 17/API 37, serial `emulator-5554`; synthetic data only. Existing compiler/deprecation/native-symbol warnings remain. Core CI and full Android verification remain distinct checks.

Historical context: the owner previously reported a failing `TrainingSchedulePolicyTest.completionDateIsPinnedAndProtectedAcrossTheWeekBoundary` before commit `941e892`. That commit corrected the fixture without changing scheduling rules. The new results above supersede the earlier unrun/failure statements for this integration source; no phone verification is inferred.

## Device verification

**Manual phone status: Pending.** Automated emulator verification is recorded above; no physical phone was inspected or changed.

Use your usual Android Studio deployment workflow after the commands above. Upgrade an existing installation to exercise migration; do not clear its data. Record phone model, Android version, build/commit, locale, timezone, current date, profile confidence and observed running frequency. A mature profile requesting four runs makes frequency reduction and demanding spacing easiest to observe. A low-data profile should remain in its existing Assessment Phase.

1. **Upgrade / legacy fallback.** Before changing Settings, record an existing future workout's title, ID if visible, date, distance/duration and type, and a completed run's actual values. Open Settings → Running days. Expect no constraints selected on upgrade, not guessed weekdays. Navigate Home → Plan → workout detail and return. Existing dates and actual values stay unchanged.
2. **Three states and explicit save.** Tap Choose running days. All days initially show Available. Set every day Unavailable, then set Monday / Wednesday / Saturday Available and Saturday Preferred. Change a choice and tap Discard changes; confirm it reverts. Re-enter the desired choices and tap Save running days. Expect a saved notice explaining deliberate regeneration. Leave/reopen Settings, then restart the app: the three states persist. Saving alone must not move any saved workout or change readiness.
3. **Apply three days deliberately.** First ensure the saved Completion Run's weekday is available, or explicitly change it through Plan → Plan details → Schedule / reschedule Completion Run. Then open Plan → Plan details → Regenerate Plan. Inspect all displayed weeks. Every non-rest workout falls on Monday, Wednesday or Saturday; each date has at most one run. Requested frequency may be lower than three due to evidence/recovery; it must never be raised merely because days are available. Expand Why this week? to see genuine reasons and reductions. Previous plans retains the earlier forecast.
4. **Cross-screen dates.** Record the next non-rest workout's title and scheduled date in Plan. Open it and record the detail date; return to Home and record the next-workout date and any current-week indicator. Expect the same saved date for the same workout on all three surfaces. Reopen after app restart and verify again. Check distance/duration, segment structure and target pace/RPE as well as dates.
5. **Two days / frequency reduction.** Save only Tuesday and Saturday Available, with Saturday Preferred. Verify saved dates remain unchanged until regeneration. Resolve any milestone conflict, regenerate, then inspect every week. Expect no more than two runs/week, no same-day doubles, safe demanding spacing, and a reduction/omission explanation if the observed requested frequency exceeded two. Dropped distance must not be added to another workout.
6. **Four days / soft preference.** Save Monday / Wednesday / Friday / Sunday Available, with Sunday Preferred; resolve the milestone if necessary and regenerate. Expect only these weekdays and at most four runs, with Sunday favored when safe. A Preferred choice must not exclude another Available day needed for safe spacing. A lower-evidence profile can still prescribe fewer runs.
7. **Adjacent-only days.** Save Saturday and Sunday Available and regenerate after resolving any milestone conflict. Expect at most two runs; no pair of demanding sessions on adjacent dates. With an eligible quality session competing with a long run, quality can be omitted in favor of aerobic work. Examine Sunday→Monday and other week boundaries, not only within one week. Ordinary demanding sessions need ≥2 calendar days; demanding work around Completion needs ≥3.
8. **Completion conflict and rollback.** Keep a future saved Completion Run. In Settings mark its weekday Unavailable and Save. Expect a warning there and in Plan, with the exact saved date unchanged. Tap Regenerate Plan: expect a visible unavailable-day conflict and no replacement plan. Open the Completion scheduler, use its another-date picker to select an unavailable date and confirm: expect the same explicit conflict, the sheet remaining usable, and the previous milestone/active plan unchanged. Suggestions must exclude unavailable weekdays. Explicitly choose an available date, or restore the saved weekday, and confirm successful regeneration pins exactly one Completion Run to that date. Check previous and completion weeks for spacing, and the absence of a duplicate long run in the completion week.
9. **No days and reset.** For an existing plan, use the Completion scheduler's existing Not yet / postpone action to clear its future milestone. Save all weekdays Unavailable, then regenerate. Expect zero running volume and explicit Rest entries, with no invented running days. Now choose Use existing schedule without day constraints and Save. Current Rest entries remain until the next deliberate regeneration; then legacy deterministic scheduling returns. Explicit empty and missing/reset must behave differently.
10. **Assessment and history.** In the separate simulator environment, or with an already low-data profile, choose just one Available weekday. Generate an assessment through the existing plan flow using an available milestone date. Expect one comfortable assessment on that weekday with its existing structured RPE guidance, no fabricated pace, and no extra workouts. Return to the real environment: its availability must be unchanged. Revisit the completed run and earlier plan recorded in step 1; original prescriptions, lifecycle and actual values remain intact after setting changes/regeneration.
11. **Non-mutating reads / accessibility.** Navigate and restart repeatedly without regeneration; dates must not change. If using Android Studio Database Inspector, record workout dates/counts before and after reads and saving availability; only `training_availability` should change on a settings save. Verify 200% font scale and a narrow screen: all weekday labels, controls, Save and conflict text remain readable and reachable by scrolling; accessibility labels identify both weekday and state.

Additional edges: one available day; all seven available with no Preferred days; Preferred→Unavailable removes the preference; Completion on the first date of a plan week; a month/year boundary; app restart after save but before regeneration; repeated regeneration; preserved completed and still-valid saved future prescriptions while merely browsing. Use the rolling-plan phone checklist on this branch for automatic post-expiry extension; that manual journey remains pending.

Return these review artifacts:

- Settings screenshot with all seven selected day states, plus the saved notice and any conflict.
- Plan screenshots for an ordinary week, an adjacent-only week, Why this week? with omission reasons, and the Completion Run week plus its preceding week.
- Home / Plan / detail screenshots for the same workout, showing its title and exact date.
- Before/after completed-run values and original prescription date, plus saved versus regenerated plan dates.
- A small table per scenario: selected days, preferred days, requested frequency from Why this week?, actual runs/week, demanding workout types/dates, planned weekly distance/duration, and explicit Completion date.
- For failures, the exact tap sequence and expected versus observed values. Copy Android Studio Logcat's `OpenRunCoach.CompletionSchedule` exception/stack trace for scheduling failures; for a crash include the complete first fatal exception.

## Files changed

```text
README.md
docs/training-availability-verification.md
app/src/main/java/com/example/openruncoach/domain/plan/TrainingAvailability.kt
app/src/main/java/com/example/openruncoach/domain/plan/TrainingSchedulePolicy.kt
app/src/main/java/com/example/openruncoach/domain/plan/TrainingPlan.kt
app/src/main/java/com/example/openruncoach/domain/plan/TrainingPlanGenerator.kt
app/src/main/java/com/example/openruncoach/domain/plan/TrainingPlanValidator.kt
app/src/main/java/com/example/openruncoach/data/TrainingAvailabilityRepository.kt
app/src/main/java/com/example/openruncoach/data/TrainingPlanRepository.kt
app/src/main/java/com/example/openruncoach/data/local/TrainingAvailabilityDao.kt
app/src/main/java/com/example/openruncoach/data/local/OpenRunCoachDatabase.kt
app/src/main/java/com/example/openruncoach/ui/settings/TrainingAvailabilitySettings.kt
app/src/main/java/com/example/openruncoach/ui/settings/SettingsScreen.kt
app/src/main/java/com/example/openruncoach/ui/settings/SettingsViewModel.kt
app/src/main/java/com/example/openruncoach/ui/plan/PlanScreen.kt
app/src/main/java/com/example/openruncoach/ui/plan/PlanViewModel.kt
app/src/test/java/com/example/openruncoach/domain/plan/TrainingSchedulePolicyTest.kt
app/src/test/java/com/example/openruncoach/domain/plan/TrainingPlanGeneratorTest.kt
app/src/test/java/com/example/openruncoach/data/TrainingPlanPersistenceMapperTest.kt
app/src/androidTest/java/com/example/openruncoach/data/TrainingAvailabilityRepositoryTest.kt
app/src/androidTest/java/com/example/openruncoach/data/local/TrainingAvailabilityMigrationTest.kt
```

## Risks and follow-up

Automated checks and schema upgrades pass as recorded above. The manual phone checklist remains pending; rolling extension and the #34 review fixes are integrated and automatically tested on this branch, while its manual horizon journey remains pending. Do not close #16 or claim real-data/UI verification based on these automated results. This integration adds no separate in-place rescheduler and does not modify long-term availability through check-in.
