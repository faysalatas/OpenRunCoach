# Upcoming training adaptation — issue #3

Implementation is local. No Gradle tasks, tests, builds, installation, or device automation were run. No branch was pushed and no issue was changed. This report records the intended behavior and verification still required.

## Architecture

`PostRunReconciler` first reconciles current physical-run evidence, execution, response, longitudinal state, and active-goal completion. In the same Room transaction, `TrainingAdaptationRepository` resolves current active-plan originals and authoritative checkout, including unassigned runs, then invokes the provider-independent `TrainingAdaptationPlanner`. Strategy priorities, purpose-specific responses, athlete capability confidence, and acute observations determine the decision; a concrete structured transformation consumes that decision.

Reconciliation is requested by existing import, canonical preference, metric enrichment, feedback, association, and relevance writers. Explicit plan generation also reconciles. `MainActivity` requests reconciliation on resume/environment change and checks for local date rollover once a minute while resumed. It retries failed resume/day reconciliation without doing provider I/O. Simulation uses its fixed clock and the same coordinator. Plan/response readers and diagnostic exports perform no application-data writes.

The first seven local dates starting today are adaptable. Significant pain, severe acute fatigue/effort, readiness 1, or recovery-priority state produces REST. Other recovery concerns reduce work volume 20%; demanding sessions are suspended. Missing/low-confidence observations prevent progression and suspend demanding sessions, while gentle assessments remain possible. Three distinct, eligible, positive responses of the same purpose within 28 days can support 5% progression in one workout matching a primary strategy capability. A continuity priority requires observed continuity. Quality purposes additionally require explicit quality-work completion of at least 98%; averages do not supply this evidence. Existing purpose-aware evidence eligibility is reused.

Progression changes duration or distance in work segments only. It retains block/segment IDs, repeat counts, recovery, warm-up/cool-down, intensity, and the original scheduled date. Quality progression excludes easy lead-in segments. Unsupported/open completion models or locked volume permissions retain their original with a reason. Safety suspension can override adjustment permissions; it removes running blocks from the effective prescription while retaining the original in full. Goal-completion distance/date is never scaled or rescheduled.

## Persistence and history

Original `planned_workouts` rows remain the baseline. Existing one-row-per-workout `workout_adjustments` stores a versioned payload in its TEXT `adjustments` column: full mapper record, decision/action/capability/reasons, and evaluation day. Prefixes distinguish KEEP from changed prescriptions and legacy pre-run-owned rows. No migration or new dependency is required. Unchanged same-day results preserve their write timestamp; a day change persists the evaluation date so active presentation refreshes even when the action stays the same. Every event starts from the original, preventing cumulative scaling.

Associated/completed, past, pre-run-owned, and potentially performed same-day prescriptions are frozen. A same-day actual that started after the original prescription was created freezes that prescription pending explicit association/checkout; it does not infer matching. Thus a delayed import cannot rewrite the prescription against which the run occurred. Feedback/evidence correction still rebuilds actual execution/response and updates other upcoming eligible workouts. Existing legacy pre-run adjustments remain scalar; completing their reachable structured workflow is issue #6.

`PlannedWorkoutEntry` and Workout Detail expose original, effective, and reasons. Home consumes that plan projection and includes adapted recovery days rather than skipping to a later demanding workout. Plan and Detail display the same reasons; Detail Structure displays original/effective separately, with actual execution separately on Overview. Shared adherence/response evaluators consume the effective structured targets, retaining quality rules and continuity evaluation. Running against an effective rest prescription is a prescription deviation, never successful completion of rest. Diagnostics expose both plan structures and saved adjustments without triggering reconciliation.

## Added regression coverage — not executed

- JVM `TrainingAdaptationPlannerTest`: significant pain, fatigue, repeated Easy progression, one-workout/one-dimension limit, duplicate physical evidence, unknown/low confidence, purpose-specific quality evidence, preserved intervals/recovery/targets, corrections, old pain, irrelevant capabilities, fragment eligibility, locks, rest, forecast boundary.
- Room `TrainingAdaptationRepositoryTest`: authoritative checkout correction; duplicate/concurrent triggers; read-only Plan/Detail agreement; missing checkout; unassigned pain; pre-run ownership and completed history; file database restart/day rollover; structured codec and effective-distance adherence.
- Room `PostRunReconciliationSimulationTest`: equivalent production/simulation inputs produce the same persisted upcoming adjustment for scenarios D/E.

## Manual verification still required

1. Run the added JVM and Room tests, relevant existing reconciliation/response/plan/simulation/diagnostic regressions, and a Gradle build.
2. With an active plan, save significant-pain checkout. Confirm Home recommends the recovery day, Plan shows that same day, and Detail shows the same reason and effective REST, with the full original on Structure.
3. Correct that checkout to pain-free normal effort. Confirm eligible future prescriptions recompute from the original, without accumulating reductions; low confidence still prevents progression.
4. Import or enrich the same physical run twice, switch canonical provider, restart/resume, and repeat refresh. Confirm stable workout/association IDs, one adjustment per workout, preserved original rows, and no duplicate execution/response.
5. Demonstrate three eligible tolerated Easy sessions with sufficient strategy/state confidence. Confirm only one relevant upcoming workout receives 5% work-volume progression, with targets retained. Repeat for quality, first with total data only (no quality progression), then with explicit quality-work evidence.
6. Check missing checkout, low confidence, severe fatigue, and mild pain. Confirm uncertainty is visible and demanding training is withheld conservatively. Confirm gentle assessment remains available without inventing feedback.
7. Reassign/unassign, ignore/restore, and edit checkout for older runs. Confirm authoritative response/state updates while completed prescriptions, assignments, actual volume, and history remain intact.
8. Resume after local midnight; keep the app active over midnight. Confirm the same adaptation policy runs and displayed dates/reasons update. Confirm fixed-clock simulation does not drift to wall-clock dates.
9. Open previous plans and export SHARE_SAFE/FULL diagnostics. Confirm original/effective/actual distinctions and no writes, regeneration, or provider refresh caused by viewing/exporting.
10. Inspect small screens, large fonts, and recovery-day cards on Home/Plan/Detail. Reasons must remain readable without clipping; no new design language was introduced.
