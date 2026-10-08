# Diagnostic dump implementation and manual verification

Verification status: implementation and static review only. No Gradle tests, build, ADB, install, connected tests, uninstall, or data clearing were executed. Existing unrelated working-tree changes were preserved.

## Implementation report

1. **Files changed for this feature**

   - `data/diagnostics/DiagnosticDumpRepository.kt`: export orchestration, domain snapshots, manifest, provider status, archive README.
   - `data/diagnostics/DatabaseTableExporter.kt`: table discovery, schema inspection, streamed rows, SQLite value serialization.
   - `data/diagnostics/DumpFormat.kt`: version/privacy modes, JSON adapters, coordinate projection, credential filtering, staged ZIP entries, safe errors.
   - `data/diagnostics/DumpIntegrityChecker.kt`: reporting-only checks and counts.
   - `ui/settings/DiagnosticDumpViewModel.kt` and `DiagnosticDumpSettings.kt`: picker/export lifecycle, progress, duplicate prevention, full-GPS warning.
   - `ui/settings/SettingsScreen.kt`: Settings placement.
   - `data/local/RunDao.kt`: suspend read of one stored run.
   - `data/WorkoutResponseRepository.kt`: read-only one-run response resolution using the same predicates/evaluator as the production observed response.
   - `data/TrainingPlanRepository.kt`: snapshot reads reusing existing plan mapping without checkout reconciliation.
   - `data/RunRouteRepository.kt`: expose the existing physical-route mapper internally so export can report its selected storage owner and clipping counts alongside the same resolved route.
   - `src/test/.../data/diagnostics/DumpFormatTest.kt` and `src/androidTest/.../data/diagnostics/DiagnosticDumpTest.kt`: focused tests, not executed.
   - `README.md` and this document.

   Kotlin paths above are relative to `app/src/main/java/com/example/openruncoach/`, unless specified otherwise. No dependency, permission, schema migration, canonicalization, coaching, goal, or plan-generation change was required.

2. **ZIP architecture**

   One `OpenRunCoach_Dump_YYYY-MM-DD_HH-mm-ss.zip`, saved by `CreateDocument("application/zip")`, following the [Android Storage Access Framework](https://developer.android.com/training/data-storage/shared/documents-files). Every entry is under `openruncoach-dump/`:

   ```text
   README.txt, manifest.json, summary.json, settings.json
   capabilities.json, integrity.json, errors.json
   database/schema.json
   database/tables/<table>.jsonl
   resolved/canonical_runs.json
   resolved/workout_associations.json
   resolved/workout_responses.json
   resolved/performance_profile.json
   resolved/athlete_state.json
   resolved/athlete_state_context.json
   resolved/active_goal.json, goals.json, goal_strategy.json
   resolved/active_plan.json, plans/<encoded-id>.json
   resolved/workouts/<encoded-id>.json
   resolved/current_home_state.json
   runs/<encoded-canonical-record-id>/...
   providers/health_connect.json, samsung_health.json, canonical_matching.json
   ```

   IDs in filenames are URL-safe Base64 so provider IDs cannot introduce ZIP paths. Canonical summaries map original IDs to directories. Unusual table names are encoded and listed in `summary.json.tableFiles`. No import/restore workflow exists.

3. **Generic Room export**

   Discover main application tables through `sqlite_master`, ordered by name. Exclude SQLite implementation tables, Android metadata, and Room's modification log; represent `room_master_table` in schema metadata. No manually maintained application-table list is used. Read `PRAGMA table_info`, `foreign_key_list`, `index_list`, and `index_xinfo` for column declarations, nullability, primary-key positions, references, and indices. Include views/triggers by name, without potentially sensitive SQL bodies.

   Each cursor row is written directly as one JSONL record. NULL, 64-bit INTEGER, REAL, Unicode TEXT, and BLOB are supported; BLOB is explicitly marked with type/encoding and Base64 data. Nonfinite REAL values use a typed string representation rather than invalid JSON. Sort rows by primary-key position or all columns when there is no PK. Current schema version 18 declares **28 application tables**. Actual exported-file count must be checked in the generated archive; it has not been device-verified.

4. **Snapshot consistency and read-only behavior**

   The selected environment's Room database supplies every raw SQL and suspend DAO read inside one `withTransaction` block. Export code issues SELECT/read-only PRAGMA operations and calls read-only mappings/evaluators; it does not invoke app-data DML. Room reserves its writer connection, so concurrent app writes wait while the database snapshot is exported. The application clock is captured at snapshot start so a long export crossing midnight cannot change Home's effective date partway through. This deliberately favors coherent inspection over allowing concurrent writes. The transaction ends before opening the SAF destination stream.

   DataStore settings and provider permission APIs cannot participate in Room's transaction. Their sampling times and this limitation are explicit in the manifest; saved Samsung inspector selections also include timestamps. Optional provider checks query availability/permissions only, never data records or permission prompts.

5. **Resolved/domain sections**

   Canonical runs and stored source groups; authoritative workout associations; full structured responses; saved Performance Profile; AthleteState; all stored goals; active and historical plans; all structured workout prescriptions with lifecycle status and production estimates; goal strategy; semantic Home state including next/upcoming workouts, latest active canonical run and the production coaching decision. Goal distance and target pace include the production resolved numeric values alongside their stored goal fields.

   `WorkoutResponse` and `AthleteState` are derived models, not separate persisted tables in the current architecture. Responses use the existing production evaluator and saved execution/checkout/metric inputs. AthleteState uses `AthleteStateBuilder` with the **saved profile** and its original `calculatedAt`. Export never invokes `rebuild()` or `recalculate()` because those workflows persist a refreshed profile. Missing saved profile produces null AthleteState; prior section errors and response count are included in the context file. The dump does not claim to capture an arbitrary retained ViewModel instance or refresh stale state.

   Plans use the existing `TrainingPlanPersistenceMapper` via snapshot methods that bypass `reconcileExistingCheckouts`. Goal status and starting evidence are retained. Goal has no completion date; Completion Run dates remain plan milestones. The current goal model has no separate persisted goal-completion-evidence object to invent.

6. **Run-level sections**

   `summary`, `provenance`, scalar `metrics`, underlying resolved `route`, saved `splits`, `continuity`, actual speed/HR/cadence/power JSONL, elevation from actual route altitudes, authoritative `association` with prescription, `execution`, `check_in`, `adjustment`, `checkout`, `response`, and saved `provider_selection`.

   Production route ownership/window clipping and production half-distance pacing are reused. Split delta and half-split direction use `RunPacingSummaryCalculator`. No alternate splitting/continuity logic exists. Missing samples remain missing. The continuity model only represents an enum; the dump includes actual speed/cadence sample counts without inventing confidence/reasons. Full structured prescription variants carry `modelType` labels, including fieldless manual completion/no-intensity variants.

7. **Provenance**

   Canonical summary package/app and selection reason are distinct from each metric's complete `MetricValue` (source, confidence, verification, secondary source/value and reason). Route source/app/confidence is independent; route storage metadata identifies the selected persisted owner and stored versus resolved point/split counts. Samples retain source/confidence/app/device-name fields. Persisted canonical groups, match decisions and selected Samsung inspector candidate IDs are exported without new matching or provider reads.

8. **Integrity checks**

   Foreign-key orphans across automatically discovered tables; canonical runs without metrics; duplicate canonical representatives per physical ID; routes without runs; executions missing runs/workouts; execution/association mismatches; associations pointing to a noncanonical or wrong physical run; active plan without active goal; plan references without plans; generated workouts without plan references; routes with fewer than two points; metric snapshots without duration values. Include actual route-point and per-metric sample counts. Each check has status, count, affected identifiers and optional explanatory note. Missing enrichment is marked for review rather than assumed corruption. Response-without-execution is explicitly not applicable because responses are derived. Nothing is repaired.

9. **SHARE_SAFE coordinates**

   Transform both raw `run_route_points` rows and resolved route points; also redact recognizable GPS pairs in free text. For each route, first valid point establishes an unexported origin. With Earth radius 6,371,000 m:

   ```text
   xMeters = wrapped longitude delta in radians × R × cos(origin latitude)
   yMeters = latitude delta in radians × R
   ```

   Preserve timestamps, altitude and accuracy. Dateline deltas use the short crossing. Invalid points yield null x/y and do not establish an origin. Unsupported coordinate fields/embedded JSON coordinates are redacted in share-safe output. Local projection is approximate over long distances and near poles. Shape and personal notes may still identify places/people; share-safe is not anonymization.

10. **FULL GPS**

    Explicit confirmation displays: “Exact GPS coordinates can reveal home, work and frequently visited places.” Confirmation immediately opens the file picker. FULL retains raw and resolved latitude/longitude and still filters credentials.

11. **Secret filtering**

    No credential columns were found in the current schema's static column audit; absolute GPS is stored only in `run_route_points.latitude/longitude`. Settings use an explicit allowlist. All JSON passes credential/identifier-key filtering and recognized credential-text filtering, including embedded JSON, bearer values, JWT-like values and PEM private keys. Generic raw export also redacts credential tables and keyed sensitive settings; sensitive schema defaults are redacted. No BuildConfig map URL, authentication storage, external file scan, environment-variable dump or exception message/stack is exported. User notes remain requested application data; unknown personal text cannot be guaranteed anonymous.

12. **Progress/threading/memory**

    ViewModel controls picker/export state and prevents repeated launches. Export dispatches to IO; Room uses its transaction context. Status includes preparing, database export, per-run progress, ZIP finalization and saving. Progress has an accessibility live region. Raw rows and series stream, and each ZIP entry is disk-staged before inclusion so optional failures cannot leave invalid partial JSON. Domain route/metric logic materializes at most one run's sample/route data at a time; compact run summaries/responses/plan identities remain in memory. No entire sample corpus is collected. Cancellation is checked between rows/runs/tables. Temporary files are removed on normal completion/failure/cancellation; an interrupted destination file may need manual removal.

    Optional mapping/provider failures are safe entries in `errors.json`; raw inputs remain available. Core database discovery, disk and destination failures fail the export instead of claiming success. The archive requires temporary free space for the compressed ZIP plus its largest staged entry.

13. **Simulator**

    Repository captures the environment's database/clock at construction. Manifest includes `simulationMode=true`, `databaseScope="isolated synthetic database"`, actual dump timestamp and synthetic application-clock time. Simulation skips production preferences, provider status queries and Samsung candidate selections. Core export is in main sources; only the capability inventory marks simulation availability by build type.

14. **Tests added (not run)**

    JVM: relative geometry, first-valid origin, dateline/invalid points, FULL coordinates, coordinate redaction, credential filtering in both modes/nested text, Unicode/scalars/BLOB/nonfinite-value representation, structured variant identity, optional errors with valid ZIP and cleaned staged entries.

    Android: automatic future-table archive coverage; actual SQLite NULL/INTEGER/REAL/TEXT/BLOB serialization; deterministic PK order; sensitive defaults/rows; complete share-safe/FULL synthetic pipeline archives; metric/route provenance; correct canonical/workout/execution links; production response equality; saved-profile AthleteState; simulation manifest; full-archive GPS search; persisted-state and SQLite `total_changes()` equality across export; an eligible pending checkout remaining unassociated while active plan/goal/next-workout snapshots are exported; orphan reporting without repair; corrupt optional metrics retaining usable ZIP/raw rows/errors.

15. **Intentionally excluded**

    SQLite implementation rows, credentials/identifiers/security defaults, raw database/WAL, logs/stacks, APKs, map-service URL, provider record refreshes, new match candidates, unpersisted UI instances, synthetic time-series, and invented completion dates/evidence. No Git revision is included because no existing build-time revision mechanism was found. Extremely verbose sections are not restricted to debug builds.

16. **Manual verification**

    The complete checklist below is returned for the user to execute. None of it has been performed for this change.

## Complete manual verification checklist

1. **Create share-safe dump.** Settings → Advanced / Diagnostics → Diagnostic dump → Export share-safe dump. Expect Android file picker, a suggested `OpenRunCoach_Dump_YYYY-MM-DD_HH-mm-ss.zip` filename (for example `OpenRunCoach_Dump_2026-10-08_18-55-00.zip`), and a saved ZIP without storage-permission prompts. Cancel once and verify export buttons recover. During export, verify visible progress and disabled duplicate actions.

2. **ZIP structure.** Open on desktop. Verify `openruncoach-dump/README.txt`, `manifest.json`, `summary.json`, `database/schema.json`, `database/tables/`, `resolved/`, `runs/` when runs exist, `settings.json`, `integrity.json`, and `errors.json`. Empty database naturally has no per-run entries. Parse JSON and each JSONL line as UTF-8. Optional failures must be recorded without invalid partial entries.

3. **Database coverage.** Compare current Room schema/application tables against `database/tables/`. Current schema 18 has **28** application tables. Return the **discovered application-table count** and **actual exported table-file count** from `summary.json`; they should match for a successful complete dump. Verify empty tables have empty JSONL files, schema metadata includes relationships/indices, and any failed table is explicit in `errors.json`.

4. **Known real run.** Find its original canonical ID in `resolved/canonical_runs.json`, then follow `directory` to the encoded run folder. Verify available summary, provenance, metrics, route, splits, actual series, continuity, association, execution, checkout and structured response. Missing data should be null/empty with errors recorded only for failures. Trace provider record → physical/canonical run → metrics/route → workout/execution → feedback → response → AthleteState → goal/plan.

5. **Provider provenance.** Use a run whose summary and route/enrichment have different providers. Verify, for example, Strava/Health Connect summary and Samsung route/HR are distinct in `provenance.json`. Check scalar secondary-source/confidence/verification fields and sample sources. Confirm no new broad matching occurred.

6. **Workout association.** Use a manually assigned run. Verify canonical record ID, physical-run ID, authoritative association's planned workout ID, execution's actual run ID and full prescription all agree. Verify persisted association source/status. Check an unrelated same-day run remains unassigned.

7. **AthleteState.** Compare `resolved/athlete_state.json` against app capabilities/readiness for the same saved profile timestamp. Check all confidence, continuity, volume, frequency, tolerance, cost and reason fields. Inspect `athlete_state_context.json` and `errors.json`; export must not refresh the profile. If no saved profile exists, state must be null and the limitation documented.

8. **Plan.** Verify active plan ID/goal relationship, plan-owned Completion Run milestone, weeks, targets, full structured workout blocks/segments/intensities/cues, lifecycle/execution status and explanations. Verify historical/superseded plans and goal history are retained. TrainingGoal must not regain a completion date.

9. **Share-safe privacy.** Search the entire extracted archive, especially raw route table JSONL, for a known latitude and longitude. Neither absolute value should occur in structured GPS exports. Route points must instead contain local x/y in meters with a zero first valid point, original timing/altitude/accuracy and plausible shape/deltas. Review any user-entered notes containing personal location text separately.

10. **FULL dump.** Choose Export full dump. Expect the exact GPS warning, then confirm and save. Verify both resolved route files and raw route-point rows contain recorded latitude/longitude. Cancel the warning once and verify no export begins. Credentials must remain filtered.

11. **Secret audit.** Search the archive for known development access/refresh tokens, API credentials, passwords and authorization material if any exist. Expect none. Check settings contain only the preferred workout source, fixed metric-unit context and simulation flag; no map provider URL or developer environment data. Review free-text notes before external sharing.

12. **No side effects.** Record run count, active plan, next workout, AthleteState/saved profile, associations and last sync if shown before export. Export. Verify none changed solely due to export. No provider refresh, permission request, canonical rebuild, checkout reconciliation, plan regeneration or profile persistence should occur.

13. **Simulation.** Enter Simulation Mode and export. Verify `simulationMode=true`, `isolated synthetic database`, synthetic clock context, and only synthetic Room state. Provider files must say `NOT_QUERIED_SIMULATION`; production settings/selection values must not appear. Exit simulation and confirm production state remains intact.

14. **Large run.** Export a run with roughly 1,500 route points plus dense HR/speed/cadence. Verify progress, usable UI, completion without ANR/crash and correct sample/point counts. Check raw and resolved samples and integrity counts. Inspect `errors.json`; check duplicate export actions remain disabled. For a destination/storage failure, verify an honest error and remove any unfinished file.

15. **External review.** Explicitly upload/share the ZIP yourself. Confirm an external reviewer can trace raw provider records, canonical group/summary, per-metric/route provenance, association/execution, feedback, structured WorkoutResponse, AthleteState and goal/plan context without screenshots. Export itself must never upload anything.
