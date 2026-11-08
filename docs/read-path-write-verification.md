# Passive read write investigation (#32)

Verified on 2026-11-08. Related issue: [#32](https://github.com/faysalatas/OpenRunCoach/issues/32).

## Finding

Both reported failures reproduce on unchanged main, PR #18 and PR #20. They are test measurement false positives, not a regression introduced by either PR. No production code or schema changes are needed for these failures.

| Unmodified source | Route read/observe test | Missing-distance History/Detail/route test |
|---|---|---|
| main `9a0da508` | FAIL: total_changes 1211 → 1218 | FAIL: 412 → 415 |
| PR #18 `941e8928` | FAIL: 1211 → 1218 | FAIL: 412 → 415 |
| PR #20 `d6efd77c` | FAIL: 1211 → 1218 | FAIL: 412 → 415 |

A temporary synchronous Room QueryCallback captured SQL between the two counter measurements on main. The first test registers observation of `runs`, `run_metrics`, `run_metric_values`, `run_metric_samples`, `run_routes`, `run_route_points` and `run_splits`. Each registration executes `INSERT OR IGNORE INTO room_table_modification_log VALUES(table_id, 0)`: seven inserted bookkeeping rows. The second registers only the first three tables: three rows. Room also creates its temporary invalidation triggers. No INSERT, UPDATE, DELETE or REPLACE targeting an application table was recorded in either measured interval. The SQL callback and logging were removed after diagnosis; no trace or health data is committed.

`total_changes()` counts those temporary bookkeeping rows, so it cannot by itself prove that a passive reader modified application data.

## Reachable application writes: static audit

The following read paths and their relevant production source are identical across the three compared commits:

| Entry point | Reachable work | Application DAO writes |
|---|---|---|
| `RunRepository.canonicalRuns` | `RunDao.observeCanonicalActualRuns` → related metric snapshot/values → `toActualSummary` → `RunActualResolver.resolve` | None; SELECTs and pure mapping/calculation |
| `RunRepository.observeRun` | `RunDao.observeActualRun` → the same actual-value mapping | None |
| `RunRouteRepository.get` | `RunRouteDao.getPhysicalRoute/getPhysicalMetrics` → relation SELECTs → domain mapping → `RunActualResolver` and `RunSplitResolver`/`RouteSplitDeriver` | None |
| `RunRouteRepository.observe` | `observePhysicalRoute/observePhysicalMetrics` → the same resolver | None |
| `RunRouteRepository.observeAll` | `observePhysicalRoutes/getPhysicalMetrics` → the same resolver | None |

`PostRunReconciler` is a constructor dependency of `RunRepository`, but none of these passive paths invokes it. Split cache writes are exposed separately through explicit `backfillSplits` → `backfillPhysicalSplits` → `replaceSplitCache` (DELETE/INSERT). Route save operations and import/canonical-selection/reconciliation operations are separate explicit write workflows.

## Focused correction

The existing two tests still assert that application writes before and after reading are identical. Their guard now installs TEMP row-audit triggers on every application table discovered in `sqlite_master`, excluding SQLite/Android/Room metadata. It records INSERT, UPDATE and DELETE operations, including same-value updates and delete/reinsert pairs, rather than comparing final row counts or only selected table snapshots. The audit is confined to each synthetic in-memory test database; Room's temporary invalidation log is outside that application-table guard.

Two controls verify that the guard detects a same-value DAO update, a raw DELETE followed by DAO reinsertion even when the original row is restored, and an INSERT into a newly discovered application table. Observer registration alone leaves the application write log empty. An additional assertion covers normal Plan presentation and previous-plan observations in the existing presentation test. Original result, fallback, stored-distance and split-cache assertions remain intact.

This guard verifies row mutations in the current in-memory fixture. It does not claim to audit arbitrary schema changes, external connections or production file-backed databases. No provider, coaching, scheduling or user-facing behavior changes.

## Exact commands and actual results

Environment: existing local Android SDK 36 and Samsung AAR; Gradle 9.1.0, JDK 21 launcher with the configured JDK 17 compilation toolchain. Emulator `Medium_Phone_API_37.0`, Android 17/API 37, serial `emulator-5554`. All inputs are synthetic. The original working branch `fix/18-legacy-migration-fixtures` and its local commit `211ad14` were preserved.

Isolated worktrees:

```bash
git worktree add -b fix/read-path-writes /home/fas/Projects/OpenRunCoach/.worktrees/read-path-writes origin/main
git worktree add --detach /tmp/openruncoach-read-path-pr18 941e892
git worktree add --detach /tmp/openruncoach-read-path-pr20 d6efd77
```

The initial worktree move across filesystems failed (`Invalid cross-device link`). The unchanged main worktree was removed and recreated with `git worktree add /tmp/openruncoach-read-path-writes fix/read-path-writes`. Each worktree received ignored local SDK configuration and a symlink to the already-present, ignored Samsung AAR; neither is committed.

```bash
android info
android emulator list
./gradlew --version
adb devices -l
android emulator start --cold Medium_Phone_API_37.0
```

PASS after running outside the filesystem sandbox: SDK/tool versions identified, existing AVD started as `emulator-5554`. Initial sandbox-only attempts to run Android CLI/Gradle failed on read-only lock files. Normal Git SSH access failed on system configuration permissions; an isolated SSH configuration then failed public-key authentication. GitHub access uses the connected integration instead.

Run in each of the three worktrees before any test changes:

```bash
ANDROID_SERIAL=emulator-5554 ./gradlew --no-daemon --no-watch-fs connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.example.openruncoach.data.PhysicalRunConsistencyTest#missingSplitReadsAndObservationsDoNotWriteAndUseRealSamples,com.example.openruncoach.data.PhysicalRunConsistencyTest#missingDistanceUsesSameReadOnlyFallbackForHistoryDetailAndRouteSplits'
```

FAIL on all three commits: 2 tests executed, 2 failures, with the exact counter values above. The same command with a scratch QueryCallback on main reproduced both failures while identifying only Room bookkeeping writes. SQL was inspected using:

```bash
adb -s emulator-5554 logcat -d -v brief -s ReadPathSql:I '*:S'
```

After the focused correction, run in each worktree:

```bash
ANDROID_SERIAL=emulator-5554 ./gradlew --no-daemon --no-watch-fs connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.example.openruncoach.data.PhysicalRunConsistencyTest'
```

PASS: **14/14** tests on main plus this patch; **14/14** on PR #18 plus this patch; **14/14** on PR #20 plus this patch. The PR #20 check preserves that branch's existing rolling-plan presentation assertions. These compatibility patches are local verification only; this change does not update either existing PR branch.

Run in `/tmp/openruncoach-read-path-writes`:

```bash
./gradlew --no-daemon --no-watch-fs -p ci/core-tests test
./gradlew --no-daemon --no-watch-fs test assembleDebug assembleDebugAndroidTest
git diff --check
```

PASS: **401 domain tests**, **521 app JVM tests**, Debug APK and instrumentation APK builds; whitespace check passed. No failures or skipped tests in these executed suites. Existing compiler/deprecation/native-symbol stripping warnings remain. The full instrumentation suite and `DiagnosticDumpTest` were not rerun; this focused change makes no claim to resolve the separately tracked JUnit initialization defect (#24) or migration coverage (#33).

## Acceptance and device status

- Both reported failures reproduced on all three baseline commits; SQL writing source identified.
- Corrected application-write assertions and original behavioral assertions pass on all three source bases.
- Added Plan read coverage and guard controls pass on the emulator.
- Automated emulator verification: **Passed**, 14 tests on each source base.
- Manual phone/UI/provider verification: **Not required** for this test-only correction; none performed.
- Issue closure and merges: not performed. Separate issues #24/#33 and the existing PRs' device checklists remain outside this task.
