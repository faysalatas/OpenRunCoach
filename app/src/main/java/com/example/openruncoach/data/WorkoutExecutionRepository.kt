package com.example.openruncoach.data

import androidx.room.withTransaction
import com.example.openruncoach.data.local.OpenRunCoachDatabase
import com.example.openruncoach.data.local.PlannedWorkoutRecord
import com.example.openruncoach.data.local.WorkoutAdjustmentEntity
import com.example.openruncoach.data.local.WorkoutExecutionEntity
import com.example.openruncoach.data.local.WorkoutRunAssociationEntity
import com.example.openruncoach.domain.RunRelevance
import com.example.openruncoach.domain.RunSummary
import com.example.openruncoach.domain.workout.AdjustedWorkout
import com.example.openruncoach.domain.workout.StructuredWorkout
import com.example.openruncoach.domain.workout.WorkoutAssociationDecision
import com.example.openruncoach.domain.workout.WorkoutAssociationEvidence
import com.example.openruncoach.domain.workout.WorkoutComparisonBasis
import com.example.openruncoach.domain.workout.WorkoutEstimateCalculator
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
  private val athleteProfileRepository: AthletePerformanceProfileRepository? = null,
) {
  private val dao = database.coachingWorkflowDao()

  fun observeForRun(runId: String): Flow<WorkoutExecutionResult?> =
    observeActualExecution(dao.observeExecutionForRun(runId))

  /** Read-only detail metadata, including associations that do not yet have a checkout. */
  @OptIn(ExperimentalCoroutinesApi::class)
  fun observeAssociatedWorkout(physicalRunId: String): Flow<StructuredWorkout?> =
    dao.observeAssociationForPhysicalRun(physicalRunId).flatMapLatest { association ->
      if (association == null) flowOf(null)
      else dao.observeWorkout(association.plannedWorkoutId).map { record ->
        record?.let(StructuredWorkoutPersistenceMapper::toDomain)
      }
    }

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

  /** Input selection only, used for the first explicit checkout. Reconciliation never matches dates. */
  internal suspend fun selectAutomaticAssociation(physicalRunId: String) {
    val run = database.runDao().getCanonicalRunByPhysicalId(physicalRunId)?.toRunSummary() ?: return
    if (run.relevance != RunRelevance.ACTIVE || dao.getAssociationForPhysicalRun(physicalRunId) != null) return
    val record = findUnambiguousWorkout(run) ?: return
    dao.upsertAssociation(WorkoutRunAssociationEntity(
      record.workout.id, physicalRunId, run.healthConnectRecordId, "AUTO", clock.millis(),
    ))
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

  suspend fun assignManually(actualRun: RunSummary, plannedWorkoutId: String) = database.withTransaction {
    val current = requireNotNull(database.runDao().getCanonicalRunByPhysicalId(actualRun.physicalRunId))
    require(current.relevance == RunRelevance.ACTIVE.name)
    val workoutRecord = dao.getWorkout(plannedWorkoutId) ?: error("Planned workout not found")
    require(workoutRecord.workout.type != WorkoutType.REST.name)
    val previous = dao.getAssociationForPhysicalRun(current.physicalRunId)
    val occupied = dao.getAssociationForWorkout(plannedWorkoutId)
    if (occupied != null && occupied.physicalRunId != current.physicalRunId) {
      dao.deleteAssociationForPhysicalRun(occupied.physicalRunId)
    }
    dao.deleteAssociationForPhysicalRun(current.physicalRunId)
    if (previous != null && previous.plannedWorkoutId != plannedWorkoutId) {
      dao.updateLifecycleState(previous.plannedWorkoutId, WorkoutLifecycleState.PLANNED.name)
    }
    dao.upsertAssociation(WorkoutRunAssociationEntity(
      plannedWorkoutId, current.physicalRunId, current.healthConnectRecordId, "MANUAL", clock.millis(),
    ))
    val reconciler = reconciler()
    if (occupied != null && occupied.physicalRunId != current.physicalRunId) {
      reconciler.reconcilePhysicalRun(occupied.physicalRunId)
    }
    reconciler.reconcilePhysicalRun(current.physicalRunId)
    Unit
  }

  suspend fun unassign(actualRun: RunSummary) = database.withTransaction {
    val association = dao.getAssociationForPhysicalRun(actualRun.physicalRunId)
    dao.deleteAssociationForPhysicalRun(actualRun.physicalRunId)
    association?.let { dao.updateLifecycleState(it.plannedWorkoutId, WorkoutLifecycleState.PLANNED.name) }
    reconciler().reconcilePhysicalRun(actualRun.physicalRunId)
    Unit
  }

  private fun reconciler() = athleteProfileRepository?.let { PostRunReconciler(database, clock, it) }
    ?: PostRunReconciler(database, clock)

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
