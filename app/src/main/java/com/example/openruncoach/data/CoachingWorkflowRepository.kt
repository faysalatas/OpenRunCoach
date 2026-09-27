package com.example.openruncoach.data

import androidx.room.withTransaction
import com.example.openruncoach.data.local.CoachingWorkflowDao
import com.example.openruncoach.data.local.OpenRunCoachDatabase
import com.example.openruncoach.data.local.PreRunCheckInEntity
import com.example.openruncoach.data.local.WorkoutAdjustmentEntity
import com.example.openruncoach.domain.PainLevel
import com.example.openruncoach.domain.RunFeedback
import com.example.openruncoach.domain.RunSummary
import com.example.openruncoach.domain.workout.AdjustedWorkout
import com.example.openruncoach.domain.workout.BodyArea
import com.example.openruncoach.domain.workout.CheckInEvaluator
import com.example.openruncoach.domain.workout.IllnessLevel
import com.example.openruncoach.domain.workout.PlannedWorkout
import com.example.openruncoach.domain.workout.PreRunCheckIn
import com.example.openruncoach.domain.workout.ReadinessStatus
import com.example.openruncoach.domain.workout.RecentTrainingVolume
import com.example.openruncoach.domain.workout.SorenessLevel
import com.example.openruncoach.domain.workout.WorkoutAdjustmentDecision
import com.example.openruncoach.domain.workout.WorkoutExecutionResult
import com.example.openruncoach.domain.workout.WorkoutType
import com.example.openruncoach.domain.workout.WorkoutLifecycle
import com.example.openruncoach.domain.workout.WorkoutLifecycleEvent
import com.example.openruncoach.domain.workout.WorkoutLifecycleService
import com.example.openruncoach.domain.workout.WorkoutLifecycleState
import com.example.openruncoach.domain.workout.simpleDurationWorkout
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf

data class PersistedWorkoutLifecycle(
  val baselineWorkout: PlannedWorkout,
  val checkIn: PreRunCheckIn?,
  val adjustmentDecision: WorkoutAdjustmentDecision?,
  val executionResult: WorkoutExecutionResult?,
  val lifecycleState: WorkoutLifecycleState,
) {
  fun toDomainLifecycle() = WorkoutLifecycle(
    plannedWorkout = baselineWorkout,
    state = lifecycleState,
    preRunCheckIn = checkIn,
    adjustmentDecision = adjustmentDecision,
    executionResult = executionResult,
  )
}

@OptIn(ExperimentalCoroutinesApi::class)
class CoachingWorkflowRepository(
  private val database: OpenRunCoachDatabase,
  private val clock: Clock = Clock.systemDefaultZone(),
  private val idFactory: () -> String = { UUID.randomUUID().toString() },
) {
  private val dao: CoachingWorkflowDao = database.coachingWorkflowDao()
  private val evaluator = CheckInEvaluator(clock)
  private val lifecycleService = WorkoutLifecycleService()

  val currentLifecycle: Flow<PersistedWorkoutLifecycle?> = dao.observeLatestPlannedWorkout().flatMapLatest { entity ->
    if (entity == null) flowOf(null)
    else combine(
      dao.observeCheckIn(entity.workout.id),
      dao.observeAdjustment(entity.workout.id),
      dao.observeExecution(entity.workout.id),
    ) { checkIn, adjustment, execution ->
      val baseline = StructuredWorkoutPersistenceMapper.toDomain(entity)
      PersistedWorkoutLifecycle(
        baseline,
        checkIn?.toDomain(),
        adjustment?.toDomain(baseline),
        execution?.toDomain(),
        WorkoutLifecycleState.valueOf(entity.workout.lifecycleState),
      )
    }
  }

  suspend fun ensureInitialWorkout() {
    if (dao.workoutCount() > 0) return
    val workout = simpleDurationWorkout(
      id = idFactory(),
      date = LocalDate.now(clock),
      type = WorkoutType.EASY_RUN,
      durationMinutes = 30,
      targetRpeMin = 3,
      targetRpeMax = 4,
      createdAt = clock.instant(),
    )
    dao.replaceWorkout(StructuredWorkoutPersistenceMapper.toRecord(workout, WorkoutLifecycleState.PLANNED.name))
    val lifecycle = lifecycleService.transition(
      WorkoutLifecycle(workout),
      WorkoutLifecycleEvent.RequireCheckIn,
    )
    dao.updateLifecycleState(workout.id, lifecycle.state.name)
  }

  suspend fun evaluateAndSaveCheckIn(
    baseline: PlannedWorkout,
    checkIn: PreRunCheckIn,
    recentCanonicalRuns: List<RunSummary>,
    recentVolume: RecentTrainingVolume,
    previousFeedback: RunFeedback? = null,
  ): WorkoutAdjustmentDecision {
    val decision = evaluator.evaluate(baseline, checkIn, recentCanonicalRuns, recentVolume, previousFeedback)
    database.withTransaction {
      val currentState = requireNotNull(dao.getLifecycleState(baseline.id)) { "Planned workout does not exist." }
      val lifecycle = lifecycleService.transition(
        WorkoutLifecycle(baseline, state = WorkoutLifecycleState.valueOf(currentState)),
        WorkoutLifecycleEvent.CheckInCompleted(checkIn, decision),
      )
      dao.upsertCheckIn(checkIn.toEntity(baseline.id, clock.instant()))
      dao.upsertAdjustment(decision.toEntity())
      dao.updateLifecycleState(baseline.id, lifecycle.state.name)
    }
    return decision
  }

  suspend fun transitionLifecycle(
    lifecycle: WorkoutLifecycle,
    event: WorkoutLifecycleEvent,
  ): WorkoutLifecycle = database.withTransaction {
    val persistedState = dao.getLifecycleState(lifecycle.plannedWorkout.id)
      ?.let(WorkoutLifecycleState::valueOf)
      ?: error("Planned workout does not exist.")
    require(persistedState == lifecycle.state) {
      "Lifecycle state changed before this transition could be saved."
    }
    lifecycleService.transition(lifecycle, event).also { updated ->
      dao.updateLifecycleState(updated.plannedWorkout.id, updated.state.name)
    }
  }
}

private const val LIST_SEPARATOR = "\u001F"

private fun PreRunCheckIn.toEntity(plannedWorkoutId: String, createdAt: Instant) = PreRunCheckInEntity(
  plannedWorkoutId,
  date.toEpochDay(),
  readiness,
  energy,
  sleepQuality,
  soreness.name,
  sorenessAreas.joinToString(LIST_SEPARATOR) { it.name },
  pain.name,
  stress,
  motivation,
  illness.name,
  notes?.trim()?.takeIf(String::isNotEmpty),
  createdAt.toEpochMilli(),
)

private fun PreRunCheckInEntity.toDomain() = PreRunCheckIn(
  LocalDate.ofEpochDay(dateEpochDay),
  readiness,
  energy,
  sleepQuality,
  SorenessLevel.valueOf(soreness),
  sorenessAreas.splitStoredValues().mapTo(mutableSetOf(), BodyArea::valueOf),
  PainLevel.valueOf(pain),
  stress,
  motivation,
  IllnessLevel.valueOf(illness),
  notes,
)

private fun WorkoutAdjustmentDecision.toEntity() = WorkoutAdjustmentEntity(
  originalWorkout.id,
  adjustedWorkout.type.name,
  adjustedWorkout.durationMinutes,
  adjustedWorkout.targetRpeMin,
  adjustedWorkout.targetRpeMax,
  readinessStatus.name,
  reasons.joinToString(LIST_SEPARATOR),
  adjustments.joinToString(LIST_SEPARATOR),
  generatedAt.toEpochMilli(),
)

private fun WorkoutAdjustmentEntity.toDomain(baseline: PlannedWorkout) = WorkoutAdjustmentDecision(
  baseline,
  AdjustedWorkout(
    baseline.id,
    WorkoutType.valueOf(adjustedType),
    adjustedDurationMinutes,
    adjustedTargetRpeMin,
    adjustedTargetRpeMax,
  ),
  ReadinessStatus.valueOf(readinessStatus),
  reasons.splitStoredValues(),
  adjustments.splitStoredValues(),
  Instant.ofEpochMilli(generatedAtEpochMillis),
)

private fun String.splitStoredValues(): List<String> =
  if (isBlank()) emptyList() else split(LIST_SEPARATOR)
