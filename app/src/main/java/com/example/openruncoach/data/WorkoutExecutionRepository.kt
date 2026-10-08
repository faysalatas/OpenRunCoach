package com.example.openruncoach.data

import androidx.room.withTransaction
import com.example.openruncoach.data.local.OpenRunCoachDatabase
import com.example.openruncoach.data.local.PlannedWorkoutRecord
import com.example.openruncoach.data.local.WorkoutAdjustmentEntity
import com.example.openruncoach.data.local.WorkoutExecutionEntity
import com.example.openruncoach.data.local.WorkoutRunAssociationEntity
import com.example.openruncoach.domain.RunActualResolver
import com.example.openruncoach.domain.RunFeedback
import com.example.openruncoach.domain.RunRelevance
import com.example.openruncoach.domain.RunSummary
import com.example.openruncoach.domain.workout.AdjustedWorkout
import com.example.openruncoach.domain.workout.StructuredWorkout
import com.example.openruncoach.domain.workout.WorkoutAdherenceEvaluator
import com.example.openruncoach.domain.workout.WorkoutAssociationDecision
import com.example.openruncoach.domain.workout.WorkoutAssociationEvidence
import com.example.openruncoach.domain.workout.WorkoutComparisonBasis
import com.example.openruncoach.domain.workout.WorkoutEstimateCalculator
import com.example.openruncoach.domain.workout.WorkoutExecutionEvidence
import com.example.openruncoach.domain.workout.WorkoutExecutionResult
import com.example.openruncoach.domain.workout.WorkoutExecutionStatus
import com.example.openruncoach.domain.workout.WorkoutLifecycleState
import com.example.openruncoach.domain.workout.WorkoutType
import com.example.openruncoach.domain.workout.assessWorkoutAssociation
import com.example.openruncoach.domain.workout.displayName
import com.example.openruncoach.domain.workout.hasUnambiguousRunDate
import com.example.openruncoach.domain.workout.resolveWorkoutAssociation
import com.example.openruncoach.domain.workout.withActualSummary
import java.time.Clock
import java.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

class WorkoutExecutionRepository(
  private val database: OpenRunCoachDatabase,
  private val clock: Clock = Clock.systemDefaultZone(),
  private val evaluator: WorkoutAdherenceEvaluator = WorkoutAdherenceEvaluator(clock),
  private val athleteProfileRepository: AthletePerformanceProfileRepository? = null,
) {
  private val dao = database.coachingWorkflowDao()

  fun observeForRun(runId: String): Flow<WorkoutExecutionResult?> =
    observeActualExecution(dao.observeExecutionForRun(runId))

  fun observeForWorkout(workoutId: String): Flow<WorkoutExecutionResult?> =
    observeActualExecution(dao.observeExecution(workoutId))

  @OptIn(ExperimentalCoroutinesApi::class)
  private fun observeActualExecution(records: Flow<WorkoutExecutionEntity?>): Flow<WorkoutExecutionResult?> =
    records.flatMapLatest { execution ->
      if (execution == null) flowOf(null) else database.runDao().observeActualRun(execution.physicalRunId).map { run ->
        execution.toDomain().withActualSummary(run?.toActualSummary())
      }
    }

  /** Returns authoritative completed run associations with any persisted workout adjustment applied. */
  suspend fun getCompletedWorkoutTypesByRunId(): Map<String, WorkoutType> = buildMap {
    dao.getAllExecutions().forEach { entity ->
      if (!entity.toDomain().isCompleted) return@forEach
      val workout = dao.getWorkout(entity.plannedWorkoutId) ?: return@forEach
      val type = dao.getAdjustment(entity.plannedWorkoutId)?.toAdjustedWorkout()?.type
        ?: StructuredWorkoutPersistenceMapper.toDomain(workout).type
      val currentRunId = database.runDao().getCanonicalRunByPhysicalId(entity.physicalRunId)
        ?.healthConnectRecordId ?: entity.actualRunId
      put(currentRunId, type)
    }
  }

  /** Returns the persisted prescription title for every currently associated canonical run. */
  suspend fun getAssociatedWorkoutLabelsByRunId(): Map<String, String> =
    getAssociatedWorkoutsByRunId().mapValues { (_, workout) -> workout.name.ifBlank { workout.type.displayName() } }

  /** Read-only prescription metadata for History; association selection/writes are unchanged. */
  suspend fun getAssociatedWorkoutsByRunId(): Map<String, StructuredWorkout> = buildMap {
    dao.getAllAssociations().forEach { association ->
      val record = dao.getWorkout(association.plannedWorkoutId) ?: return@forEach
      val workout = StructuredWorkoutPersistenceMapper.toDomain(record)
      val currentRunId = database.runDao().getCanonicalRunByPhysicalId(association.physicalRunId)
        ?.healthConnectRecordId ?: association.actualRunId
      put(currentRunId, workout)
    }
  }

  /**
   * Associates post-run checkout with a uniquely compatible active-plan workout. Date is
   * necessary evidence, but never sufficient: exactly one prescription must be a HIGH match and
   * no other run or prescription may be a competing candidate.
   */
  suspend fun recordForAssociatedWorkout(
    actualRun: RunSummary,
    feedback: RunFeedback,
    evidence: WorkoutExecutionEvidence = WorkoutExecutionEvidence(),
  ): WorkoutExecutionResult? {
    if (!actualRun.isCanonical || actualRun.relevance != RunRelevance.ACTIVE) return null
    val result = database.withTransaction {
      val existing = dao.getExecutionForPhysicalRun(actualRun.physicalRunId)
      val association = dao.getAssociationForPhysicalRun(actualRun.physicalRunId)
      val workoutRecord: PlannedWorkoutRecord? = if (association != null) {
        dao.getWorkout(association.plannedWorkoutId)
      } else if (existing != null) {
        dao.getWorkout(existing.plannedWorkoutId)
      } else {
        findUnambiguousWorkout(actualRun)
      }
      if (workoutRecord == null) return@withTransaction null
      val workout = StructuredWorkoutPersistenceMapper.toDomain(workoutRecord)
      val adjustment = dao.getAdjustment(workout.id)?.toAdjustedWorkout()
      evaluator.evaluate(workout, adjustment, resolvedActual(actualRun), feedback, evidence).also { result ->
        dao.upsertAssociation(result.toAssociation(association?.source ?: "AUTO", clock.millis()))
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
      val associatedPhysicalRunIds = dao.getAllExecutions().mapTo(mutableSetOf(), WorkoutExecutionEntity::physicalRunId)
      val feedbackByRun = RunFeedbackRepository(database).getAllFeedback().associateBy { it.runId }
      val runs = database.runDao().getCanonicalRuns()
        .map { it.toRunSummary() }
        .filterNot { it.physicalRunId in associatedPhysicalRunIds }
      var inserted = 0
      for (run in runs) {
        val feedback = feedbackByRun[run.physicalRunId] ?: continue
        val workoutRecord = findUnambiguousWorkout(run) ?: continue
        val workout = StructuredWorkoutPersistenceMapper.toDomain(workoutRecord)
        val adjustment = dao.getAdjustment(workout.id)?.toAdjustedWorkout()
        val result = evaluator.evaluate(workout, adjustment, resolvedActual(run), feedback)
        dao.upsertAssociation(result.toAssociation("AUTO", clock.millis()))
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
    val providerRun = database.runDao().getRun(actualRun.healthConnectRecordId)?.toRunSummary() ?: actualRun
    val date = providerRun.startTime.atZone(clock.zone).toLocalDate()
    val canonicalRunsOnDate = database.runDao().getCanonicalRuns()
      .map { it.toRunSummary() }
      .filter { it.startTime.atZone(clock.zone).toLocalDate() == date }
    // Preserve the established safety rule: a second physical run on the date makes automatic
    // assignment ambiguous even when one run looks more compatible. Manual assignment remains
    // available and authoritative.
    if (!hasUnambiguousRunDate(canonicalRunsOnDate.map(RunSummary::physicalRunId), actualRun.physicalRunId)) return null
    val workouts = dao.getUnassociatedActivePlanWorkouts(date.toEpochDay())
    val decisions = workouts.map { it to associationDecision(it, providerRun) }
    val selectedId = resolveWorkoutAssociation(decisions.map { it.first.workout.id to it.second }) ?: return null
    val selected = decisions.single { it.first.workout.id == selectedId }.first
    return selected
  }

  suspend fun assignmentCandidates(actualRun: RunSummary): List<WorkoutAssignmentCandidate> {
    val runDate = actualRun.startTime.atZone(clock.zone).toLocalDate()
    val associationsByWorkoutId = dao.getAllAssociations().associateBy(WorkoutRunAssociationEntity::plannedWorkoutId)
    val candidates = dao.getAllActivePlanWorkouts()
      .filter { record ->
        kotlin.math.abs(record.workout.dateEpochDay - runDate.toEpochDay()) <= 3L
      }
      .map { record ->
        val workout = StructuredWorkoutPersistenceMapper.toDomain(record)
        val estimate = WorkoutEstimateCalculator.calculate(workout)
        val association = associationsByWorkoutId[workout.id]
        WorkoutAssignmentCandidate(
          workout = workout,
          plannedDistanceMeters = estimate.estimatedTotalDistanceMeters,
          plannedDurationSeconds = estimate.estimatedTotalDurationSeconds,
          associationState = when (association?.physicalRunId) {
            null -> WorkoutAssignmentState.AVAILABLE
            actualRun.physicalRunId -> WorkoutAssignmentState.ASSIGNED_TO_THIS_RUN
            else -> WorkoutAssignmentState.ASSIGNED_TO_ANOTHER_RUN
          },
        )
      }
    return selectManualAssignmentCandidates(candidates, runDate)
  }

  suspend fun assignedWorkout(actualRun: RunSummary): WorkoutAssignmentCandidate? {
    val association = dao.getAssociationForPhysicalRun(actualRun.physicalRunId) ?: return null
    val record = dao.getWorkout(association.plannedWorkoutId) ?: return null
    val workout = StructuredWorkoutPersistenceMapper.toDomain(record)
    val estimate = WorkoutEstimateCalculator.calculate(workout)
    return WorkoutAssignmentCandidate(workout, estimate.estimatedTotalDistanceMeters, estimate.estimatedTotalDurationSeconds)
  }

  suspend fun assignManually(actualRun: RunSummary, plannedWorkoutId: String) {
    require(actualRun.isCanonical && actualRun.relevance == RunRelevance.ACTIVE)
    database.withTransaction {
      val workoutRecord = dao.getWorkout(plannedWorkoutId) ?: error("Planned workout not found")
      require(workoutRecord.workout.type != WorkoutType.REST.name)
      val previous = dao.getAssociationForPhysicalRun(actualRun.physicalRunId)
      val occupied = dao.getAssociationForWorkout(plannedWorkoutId)
      if (occupied != null && occupied.physicalRunId != actualRun.physicalRunId) {
        dao.deleteExecutionForPhysicalRun(occupied.physicalRunId)
        dao.deleteAssociationForPhysicalRun(occupied.physicalRunId)
        dao.updateLifecycleState(plannedWorkoutId, WorkoutLifecycleState.PLANNED.name)
      }
      dao.deleteExecutionForPhysicalRun(actualRun.physicalRunId)
      dao.deleteAssociationForPhysicalRun(actualRun.physicalRunId)
      if (previous != null && previous.plannedWorkoutId != plannedWorkoutId) {
        dao.updateLifecycleState(previous.plannedWorkoutId, WorkoutLifecycleState.PLANNED.name)
      }
      dao.upsertAssociation(
        WorkoutRunAssociationEntity(
          plannedWorkoutId = plannedWorkoutId,
          physicalRunId = actualRun.physicalRunId,
          actualRunId = actualRun.healthConnectRecordId,
          source = "MANUAL",
          updatedAtEpochMillis = clock.millis(),
        )
      )
      RunFeedbackRepository(database).getFeedbackForRun(actualRun.physicalRunId)?.let { feedback ->
        val workout = StructuredWorkoutPersistenceMapper.toDomain(workoutRecord)
        val adjustment = dao.getAdjustment(plannedWorkoutId)?.toAdjustedWorkout()
        dao.upsertExecution(evaluator.evaluate(workout, adjustment, resolvedActual(actualRun), feedback).toEntity())
        dao.updateLifecycleState(plannedWorkoutId, WorkoutLifecycleState.COMPLETED.name)
      }
    }
    athleteProfileRepository?.recalculate()
  }

  suspend fun unassign(actualRun: RunSummary) {
    database.withTransaction {
      val association = dao.getAssociationForPhysicalRun(actualRun.physicalRunId)
      dao.deleteExecutionForPhysicalRun(actualRun.physicalRunId)
      dao.deleteAssociationForPhysicalRun(actualRun.physicalRunId)
      association?.let { dao.updateLifecycleState(it.plannedWorkoutId, WorkoutLifecycleState.PLANNED.name) }
    }
    athleteProfileRepository?.recalculate()
  }

  private suspend fun resolvedActual(run: RunSummary): RunSummary =
    database.runDao().getActualRun(run.healthConnectRecordId)?.toActualSummary()
      ?: RunActualResolver.resolve(run)

  private suspend fun associationDecision(
    record: PlannedWorkoutRecord,
    actualRun: RunSummary,
  ): WorkoutAssociationDecision {
    val workout = StructuredWorkoutPersistenceMapper.toDomain(record)
    val adjustment = dao.getAdjustment(workout.id)?.toAdjustedWorkout()
    val estimate = WorkoutEstimateCalculator.calculate(workout)
    val intendedDistance = if (adjustment == null) estimate.estimatedTotalDistanceMeters else null
    val intendedDuration = adjustment?.durationMinutes?.times(60L) ?: estimate.estimatedTotalDurationSeconds
    val actualDistance = actualRun.distanceMeters
    return assessWorkoutAssociation(
      WorkoutAssociationEvidence(
        plannedWorkoutId = workout.id,
        workoutCreatedAt = Instant.ofEpochMilli(record.workout.createdAtEpochMillis),
        actualRunStart = actualRun.startTime,
        intendedDistanceMeters = intendedDistance,
        intendedDurationSeconds = intendedDuration,
        actualDistanceMeters = actualDistance,
        actualDurationSeconds = actualRun.durationSeconds,
      )
    )
  }
}

internal fun WorkoutExecutionResult.toEntity() = WorkoutExecutionEntity(
  plannedWorkoutId = plannedWorkoutId,
  actualRunId = actualRunId,
  physicalRunId = physicalRunId,
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
  physicalRunId = physicalRunId,
)

private fun WorkoutExecutionResult.toAssociation(source: String, updatedAt: Long) = WorkoutRunAssociationEntity(
  plannedWorkoutId = plannedWorkoutId,
  physicalRunId = physicalRunId,
  actualRunId = actualRunId,
  source = source,
  updatedAtEpochMillis = updatedAt,
)

data class WorkoutAssignmentCandidate(
  val workout: com.example.openruncoach.domain.workout.StructuredWorkout,
  val plannedDistanceMeters: Double?,
  val plannedDurationSeconds: Long?,
  val associationState: WorkoutAssignmentState = WorkoutAssignmentState.AVAILABLE,
)

enum class WorkoutAssignmentState {
  AVAILABLE,
  ASSIGNED_TO_THIS_RUN,
  ASSIGNED_TO_ANOTHER_RUN,
}

internal fun selectManualAssignmentCandidates(
  candidates: List<WorkoutAssignmentCandidate>,
  runDate: java.time.LocalDate,
): List<WorkoutAssignmentCandidate> = candidates
  .filter { kotlin.math.abs(it.workout.date.toEpochDay() - runDate.toEpochDay()) <= 3L }
  .sortedWith(
    compareBy<WorkoutAssignmentCandidate> {
      kotlin.math.abs(it.workout.date.toEpochDay() - runDate.toEpochDay())
    }.thenBy { it.workout.date }
  )

internal fun WorkoutAdjustmentEntity.toAdjustedWorkout() = AdjustedWorkout(
  originalWorkoutId = plannedWorkoutId,
  type = WorkoutType.valueOf(adjustedType),
  durationMinutes = adjustedDurationMinutes,
  targetRpeMin = adjustedTargetRpeMin,
  targetRpeMax = adjustedTargetRpeMax,
)
