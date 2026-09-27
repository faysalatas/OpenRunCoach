package com.example.openruncoach.data

import androidx.room.withTransaction
import com.example.openruncoach.data.local.PlannedWorkoutRecord
import com.example.openruncoach.data.local.OpenRunCoachDatabase
import com.example.openruncoach.data.local.WorkoutAdjustmentEntity
import com.example.openruncoach.data.local.WorkoutExecutionEntity
import com.example.openruncoach.domain.RunFeedback
import com.example.openruncoach.domain.RunSummary
import com.example.openruncoach.domain.workout.AdjustedWorkout
import com.example.openruncoach.domain.workout.WorkoutAdherenceEvaluator
import com.example.openruncoach.domain.workout.WorkoutComparisonBasis
import com.example.openruncoach.domain.workout.WorkoutExecutionEvidence
import com.example.openruncoach.domain.workout.WorkoutExecutionResult
import com.example.openruncoach.domain.workout.WorkoutExecutionStatus
import com.example.openruncoach.domain.workout.WorkoutLifecycleState
import com.example.openruncoach.domain.workout.WorkoutEstimateCalculator
import com.example.openruncoach.domain.workout.WorkoutType
import java.time.Clock
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class WorkoutExecutionRepository(
  private val database: OpenRunCoachDatabase,
  private val clock: Clock = Clock.systemDefaultZone(),
  private val evaluator: WorkoutAdherenceEvaluator = WorkoutAdherenceEvaluator(clock),
  private val athleteProfileRepository: AthletePerformanceProfileRepository? = null,
) {
  private val dao = database.coachingWorkflowDao()

  fun observeForRun(runId: String): Flow<WorkoutExecutionResult?> =
    dao.observeExecutionForRun(runId).map { it?.toDomain() }

  fun observeForWorkout(workoutId: String): Flow<WorkoutExecutionResult?> =
    dao.observeExecution(workoutId).map { it?.toDomain() }

  /**
   * Associates post-run checkout with a uniquely compatible active-plan workout. Date is
   * necessary evidence, but never sufficient: the run must be canonical, the only canonical run
   * on that date, and compatible with exactly one unassociated prescription.
   */
  suspend fun recordForAssociatedWorkout(
    actualRun: RunSummary,
    feedback: RunFeedback,
    evidence: WorkoutExecutionEvidence = WorkoutExecutionEvidence(),
  ): WorkoutExecutionResult? {
    if (!actualRun.isCanonical) return null
    val result = database.withTransaction {
      val existing = dao.getExecutionForRun(actualRun.healthConnectRecordId)
      val workoutRecord = if (existing != null) {
        dao.getWorkout(existing.plannedWorkoutId)
      } else {
        findUnambiguousWorkout(actualRun)
      } ?: return@withTransaction null
      val workout = StructuredWorkoutPersistenceMapper.toDomain(workoutRecord)
      val adjustment = dao.getAdjustment(workout.id)?.toAdjustedWorkout()
      evaluator.evaluate(workout, adjustment, actualRun, feedback, evidence).also { result ->
        dao.upsertExecution(result.toEntity())
        // In this lifecycle state, "completed" means an actual run has been attached. The
        // execution result separately preserves as-planned, minor, partial, and aborted semantics.
        dao.updateLifecycleState(workout.id, WorkoutLifecycleState.COMPLETED.name)
      }
    }
    if (result != null) athleteProfileRepository?.recalculate()
    return result
  }

  /**
   * Recovers authoritative execution rows for checkouts saved before execution persistence was
   * introduced. Ambiguous dates or prescriptions are deliberately left unassociated.
   */
  suspend fun reconcileExistingCheckouts(): Int {
    val created = database.withTransaction {
      val associatedRunIds = dao.getAllExecutions().mapTo(mutableSetOf(), WorkoutExecutionEntity::actualRunId)
      val feedbackByRun = database.runFeedbackDao().getAllFeedback().associate { it.runId to it.toDomain() }
      val runsByDate = database.runDao().getCanonicalRuns()
        .map { it.toRunSummary() }
        .filterNot { it.healthConnectRecordId in associatedRunIds }
        .groupBy { it.startTime.atZone(clock.zone).toLocalDate() }
      val workoutsByDate = dao.getAllUnassociatedActivePlanWorkouts()
        .groupBy { java.time.LocalDate.ofEpochDay(it.workout.dateEpochDay) }
      var inserted = 0
      for ((date, runs) in runsByDate) {
        val run = runs.singleOrNull() ?: continue
        val feedback = feedbackByRun[run.healthConnectRecordId] ?: continue
        val candidates = compatibleWorkouts(workoutsByDate[date].orEmpty(), run)
        val workoutRecord = candidates.singleOrNull() ?: continue
        val workout = StructuredWorkoutPersistenceMapper.toDomain(workoutRecord)
        val adjustment = dao.getAdjustment(workout.id)?.toAdjustedWorkout()
        val result = evaluator.evaluate(workout, adjustment, run, feedback)
        dao.upsertExecution(result.toEntity())
        dao.updateLifecycleState(workout.id, WorkoutLifecycleState.COMPLETED.name)
        inserted += 1
      }
      inserted
    }
    if (created > 0) athleteProfileRepository?.recalculate()
    return created
  }

  private suspend fun findUnambiguousWorkout(actualRun: RunSummary): PlannedWorkoutRecord? {
    val date = actualRun.startTime.atZone(clock.zone).toLocalDate()
    val canonicalRunsOnDate = database.runDao().getCanonicalRuns()
      .map { it.toRunSummary() }
      .filter { it.startTime.atZone(clock.zone).toLocalDate() == date }
    if (canonicalRunsOnDate.singleOrNull()?.healthConnectRecordId != actualRun.healthConnectRecordId) return null
    return compatibleWorkouts(dao.getUnassociatedActivePlanWorkouts(date.toEpochDay()), actualRun)
      .singleOrNull()
  }

  private suspend fun compatibleWorkouts(
    records: List<PlannedWorkoutRecord>,
    actualRun: RunSummary,
  ): List<PlannedWorkoutRecord> = buildList {
    for (record in records) if (isCompatible(record, actualRun)) add(record)
  }

  private suspend fun isCompatible(record: PlannedWorkoutRecord, actualRun: RunSummary): Boolean {
    if (record.workout.createdAtEpochMillis > actualRun.startTime.toEpochMilli()) return false
    val workout = StructuredWorkoutPersistenceMapper.toDomain(record)
    val adjustment = dao.getAdjustment(workout.id)?.toAdjustedWorkout()
    val estimate = WorkoutEstimateCalculator.calculate(workout)
    val intendedDistance = if (adjustment == null) estimate.estimatedTotalDistanceMeters else null
    val intendedDuration = adjustment?.durationMinutes?.times(60L) ?: estimate.estimatedTotalDurationSeconds
    val actualDistance = actualRun.distanceMeters
    val fraction = when {
      intendedDistance != null && intendedDistance > 0.0 && actualDistance != null ->
        actualDistance / intendedDistance
      intendedDuration != null && intendedDuration > 0L -> actualRun.durationSeconds.toDouble() / intendedDuration
      else -> null
    }
    return fraction != null && fraction in 0.5..1.5
  }
}

internal fun WorkoutExecutionResult.toEntity() = WorkoutExecutionEntity(
  plannedWorkoutId = plannedWorkoutId,
  actualRunId = actualRunId,
  status = status.name,
  comparisonBasis = comparisonBasis.name,
  intendedDistanceMeters = intendedDistanceMeters,
  intendedDurationSeconds = intendedDurationSeconds,
  actualDistanceMeters = actualDistanceMeters,
  actualDurationSeconds = actualDurationSeconds,
  qualityWorkCompletionFraction = qualityWorkCompletionFraction,
  explanation = explanation,
  evaluatedAtEpochMillis = evaluatedAt.toEpochMilli(),
)

internal fun WorkoutExecutionEntity.toDomain() = WorkoutExecutionResult(
  plannedWorkoutId = plannedWorkoutId,
  actualRunId = actualRunId,
  status = WorkoutExecutionStatus.valueOf(status),
  comparisonBasis = WorkoutComparisonBasis.valueOf(comparisonBasis),
  intendedDistanceMeters = intendedDistanceMeters,
  intendedDurationSeconds = intendedDurationSeconds,
  actualDistanceMeters = actualDistanceMeters,
  actualDurationSeconds = actualDurationSeconds,
  qualityWorkCompletionFraction = qualityWorkCompletionFraction,
  explanation = explanation,
  evaluatedAt = Instant.ofEpochMilli(evaluatedAtEpochMillis),
)

internal fun WorkoutAdjustmentEntity.toAdjustedWorkout() = AdjustedWorkout(
  originalWorkoutId = plannedWorkoutId,
  type = WorkoutType.valueOf(adjustedType),
  durationMinutes = adjustedDurationMinutes,
  targetRpeMin = adjustedTargetRpeMin,
  targetRpeMax = adjustedTargetRpeMax,
)
