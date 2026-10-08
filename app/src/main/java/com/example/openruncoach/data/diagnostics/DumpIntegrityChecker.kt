package com.example.openruncoach.data.diagnostics

internal data class IntegrityCheck(
  val check: String,
  val status: String,
  val count: Int,
  val affectedIds: List<Map<String, Any?>>,
  val note: String? = null,
)

/** Reports persisted facts. No repair, reconciliation, matching, or refresh is called. */
internal class DumpIntegrityChecker(private val tables: DatabaseTableExporter) {
  fun check(name: String, sql: String, note: String? = null): IntegrityCheck {
    val rows = tables.queryRows(sql)
    return IntegrityCheck(name, if (rows.isEmpty()) "PASS" else "REVIEW", rows.size, rows, note)
  }

  fun checks(): List<IntegrityCheck> = listOf(
    check("foreign_key_orphans", "PRAGMA foreign_key_check"),
    check("canonical_runs_without_metrics", """SELECT healthConnectRecordId AS runId FROM runs
      WHERE isCanonical = 1 AND NOT EXISTS (SELECT 1 FROM run_metrics WHERE runId = healthConnectRecordId)
      ORDER BY healthConnectRecordId""", "Missing enrichment is allowed; review rather than assume corruption."),
    check("duplicate_canonical_physical_ids", """SELECT physicalRunId, COUNT(*) AS canonicalCount FROM runs
      WHERE isCanonical = 1 GROUP BY physicalRunId HAVING COUNT(*) > 1 ORDER BY physicalRunId"""),
    check("multiple_feedback_for_physical_run", """SELECT r.physicalRunId, COUNT(*) AS feedbackCount
      FROM run_feedback f JOIN runs r ON r.healthConnectRecordId = f.runId
      GROUP BY r.physicalRunId HAVING COUNT(*) > 1 ORDER BY r.physicalRunId""",
      "Whole-answer authority and every feedback ID/original storage run ID are in feedbackOwnershipConflicts; no answers are merged or deleted."),
    check("route_without_run", """SELECT runId FROM run_routes WHERE NOT EXISTS
      (SELECT 1 FROM runs WHERE healthConnectRecordId = runId) ORDER BY runId"""),
    check("execution_without_run_or_workout", """SELECT plannedWorkoutId, actualRunId FROM workout_executions e
      WHERE NOT EXISTS (SELECT 1 FROM runs r WHERE r.healthConnectRecordId = e.actualRunId)
      OR NOT EXISTS (SELECT 1 FROM planned_workouts w WHERE w.id = e.plannedWorkoutId) ORDER BY plannedWorkoutId"""),
    check("execution_association_mismatch", """SELECT plannedWorkoutId, actualRunId, physicalRunId FROM workout_executions e
      WHERE NOT EXISTS (SELECT 1 FROM workout_run_associations a WHERE a.plannedWorkoutId = e.plannedWorkoutId
        AND a.physicalRunId = e.physicalRunId AND a.actualRunId = e.actualRunId) ORDER BY plannedWorkoutId"""),
    check("association_canonical_mismatch", """SELECT plannedWorkoutId, actualRunId, physicalRunId FROM workout_run_associations a
      WHERE NOT EXISTS (SELECT 1 FROM runs r WHERE r.healthConnectRecordId = a.actualRunId AND r.isCanonical = 1
        AND r.physicalRunId = a.physicalRunId) ORDER BY plannedWorkoutId"""),
    check("active_plan_without_active_goal", """SELECT id AS planId, goalId FROM training_plans p WHERE status = 'ACTIVE'
      AND NOT EXISTS (SELECT 1 FROM training_goals g WHERE g.id = p.goalId AND g.status = 'ACTIVE') ORDER BY id"""),
    check("plan_workout_without_plan", """SELECT planId, workoutId FROM training_plan_workouts w
      WHERE NOT EXISTS (SELECT 1 FROM training_plans p WHERE p.id = w.planId) ORDER BY planId, workoutId"""),
    check("generated_workout_without_plan", """SELECT id AS workoutId FROM planned_workouts w WHERE origin = 'GENERATED'
      AND NOT EXISTS (SELECT 1 FROM training_plan_workouts p WHERE p.workoutId = w.id) ORDER BY id""",
      "Generated baseline/next-workout workflows may legitimately have no plan reference."),
    check("route_with_too_few_points", """SELECT r.runId, COUNT(p.sequence) AS pointCount FROM run_routes r
      LEFT JOIN run_route_points p ON p.runId = r.runId GROUP BY r.runId HAVING COUNT(p.sequence) < 2 ORDER BY r.runId"""),
    check("metrics_without_duration", """SELECT m.runId FROM run_metrics m WHERE NOT EXISTS
      (SELECT 1 FROM run_metric_values v WHERE v.runId = m.runId AND v.metric = 'DURATION') ORDER BY m.runId"""),
  )

  fun counts(): Map<String, Any?> = mapOf(
    "routePoints" to tables.queryRows("SELECT runId, COUNT(*) AS pointCount FROM run_route_points GROUP BY runId ORDER BY runId"),
    "timeSeries" to tables.queryRows("SELECT runId, metricType, COUNT(*) AS sampleCount FROM run_metric_samples GROUP BY runId, metricType ORDER BY runId, metricType"),
    "workoutResponseWithoutExecution" to IntegrityCheck("response_without_execution", "NOT_APPLICABLE", 0, emptyList(),
      "WorkoutResponse is derived only from an existing execution; it is not persisted in a separate table."),
  )
}

/** Compare semantic consumption only; provider/geometry measurements may legitimately disagree. */
internal fun wholeRunPaceIntegrity(runId: String, canonicalPace: Double?, splitBaseline: Double?): IntegrityCheck {
  val mismatch = when {
    canonicalPace == null && splitBaseline == null -> false
    canonicalPace == null || splitBaseline == null -> true
    !canonicalPace.isFinite() || !splitBaseline.isFinite() -> true
    else -> kotlin.math.abs(canonicalPace - splitBaseline) > 0.001
  }
  val rows = if (mismatch) listOf(mapOf<String, Any?>("runId" to runId,
    "canonicalActualPaceSecondsPerKm" to canonicalPace, "consumedSplitBaselineSecondsPerKm" to splitBaseline)) else emptyList()
  return IntegrityCheck("whole_run_pace_vs_split_baseline", if (mismatch) "REVIEW" else "PASS", rows.size, rows,
    "Read-only semantic consumption check; tolerance 0.001 s/km. Raw provider distances are not compared.")
}
