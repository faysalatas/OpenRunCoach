package com.example.openruncoach.data

import androidx.room.withTransaction
import com.example.openruncoach.data.local.OpenRunCoachDatabase
import com.example.openruncoach.data.local.TrainingBlockEntity
import com.example.openruncoach.data.local.TrainingPlanEntity
import com.example.openruncoach.data.local.TrainingPlanWorkoutEntity
import com.example.openruncoach.data.local.TrainingWeekEntity
import com.example.openruncoach.data.local.TrainingWeekReasonEntity
import com.example.openruncoach.domain.goal.TrainingGoal
import com.example.openruncoach.domain.goal.GoalCompletionMilestone
import com.example.openruncoach.domain.goal.GoalCompletionReadiness
import com.example.openruncoach.domain.goal.GoalCompletionMilestoneService
import com.example.openruncoach.domain.plan.PlannedWorkoutEntry
import com.example.openruncoach.domain.plan.TrainingBlock
import com.example.openruncoach.domain.plan.TrainingBlockType
import com.example.openruncoach.domain.plan.TrainingPlan
import com.example.openruncoach.domain.plan.TrainingPlanGenerator
import com.example.openruncoach.domain.plan.TrainingPlanStatus
import com.example.openruncoach.domain.plan.TrainingWeek
import com.example.openruncoach.domain.plan.WeekLoadClassification
import com.example.openruncoach.domain.plan.WeeklyWorkoutSlot
import com.example.openruncoach.domain.profile.AthletePerformanceProfile
import com.example.openruncoach.domain.profile.ProfileConfidence
import com.example.openruncoach.domain.workout.WorkoutLifecycleState
import com.example.openruncoach.domain.workout.withActualSummary
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow

class TrainingPlanRepository(
  private val database: OpenRunCoachDatabase,
  private val generator: TrainingPlanGenerator = TrainingPlanGenerator(),
  private val completionRepository: GoalCompletionRepository = GoalCompletionRepository(database),
  private val completionService: GoalCompletionMilestoneService = GoalCompletionMilestoneService(),
  private val executionRepository: WorkoutExecutionRepository = WorkoutExecutionRepository(database),
) {
  private val dao = database.trainingPlanDao()
  private val workoutDao = database.coachingWorkflowDao()

  fun observeActivePlan(): Flow<TrainingPlan?> = flow {
    executionRepository.reconcileExistingCheckouts()
    emitAll(combine(
      dao.observeActivePlan(),
      dao.observeActivePlanExecutionRevision(),
      database.runDao().observeCanonicalActualRuns(),
    ) { entity, _, _ -> entity?.let { loadPlan(it) } })
  }

  suspend fun getActivePlan(): TrainingPlan? {
    executionRepository.reconcileExistingCheckouts()
    return dao.getActivePlan()?.let { loadPlan(it) }
  }

  /** Observes saved plan state without checkout reconciliation or other persistence. */
  internal suspend fun getActivePlanSnapshot(): TrainingPlan? = dao.getActivePlan()?.let { loadPlan(it) }

  internal suspend fun getPlanSnapshot(planId: String): TrainingPlan? = dao.getPlan(planId)?.let { loadPlan(it) }

  suspend fun generate(goal: TrainingGoal, profile: AthletePerformanceProfile): TrainingPlan {
    val milestone = completionRepository.refreshReadiness(goal, profile)
    return generate(goal, profile, milestone)
  }

  private suspend fun generate(
    goal: TrainingGoal,
    profile: AthletePerformanceProfile,
    milestone: GoalCompletionMilestone,
  ): TrainingPlan {
    val metrics = RunMetricsRepository(database.runMetricsDao()).getCanonicalRunMetrics()
    val plan = generator.generate(
      goal,
      profile,
      runMetricsHistory = metrics,
      completedAssessmentAttempts = workoutDao.completedAssessmentCount(goal.id),
      completionMilestone = milestone,
    )
    saveAsActive(plan)
    return plan
  }

  suspend fun createWithCompletionDate(
    goal: TrainingGoal,
    profile: AthletePerformanceProfile,
    date: LocalDate,
  ): CompletionScheduleUpdate = database.withTransaction {
    val readiness = completionRepository.refreshReadiness(goal, profile)
    val scheduled = completionService.schedule(goal.id, readiness, date)
    CompletionScheduleDiagnostics.debug(
      "request=create schedule date=$date goalId=${goal.id} activePlanId=null " +
        "scheduleState=${readiness.readiness} completionWorkoutExists=false"
    )
    try {
      val plan = generate(goal, profile, scheduled)
      val milestone = requireNotNull(plan.completionMilestone)
      CompletionScheduleDiagnostics.debug(
        "surroundingReplanResult=success newPlanId=${plan.id} completionWorkoutId=${milestone.workoutId} " +
          "completionDate=${milestone.scheduledDate} finalResult=success"
      )
      CompletionScheduleUpdate(milestone, plan)
    } catch (exception: CancellationException) {
      throw exception
    } catch (exception: Exception) {
      CompletionScheduleDiagnostics.error(
        "surroundingReplanResult=failure finalResult=failure request=create date=$date goalId=${goal.id}",
        exception,
      )
      throw exception
    }
  }

  suspend fun scheduleCompletion(
    goal: TrainingGoal,
    profile: AthletePerformanceProfile,
    date: LocalDate,
  ): CompletionScheduleUpdate = updateCompletionSchedule(goal, profile, "schedule date=$date") {
    completionRepository.scheduleWithResult(goal.id, date)
  }

  suspend fun postponeCompletion(
    goal: TrainingGoal,
    profile: AthletePerformanceProfile,
  ): CompletionScheduleUpdate = updateCompletionSchedule(goal, profile, "postpone") {
    completionRepository.postponeWithResult(goal.id)
  }

  private suspend fun updateCompletionSchedule(
    goal: TrainingGoal,
    profile: AthletePerformanceProfile,
    request: String,
    updateMilestone: suspend () -> GoalCompletionWriteResult,
  ): CompletionScheduleUpdate = database.withTransaction {
    val activePlan = dao.getActivePlan()
    val existingCompletionIds = dao.getActiveCompletionWorkoutIds(goal.id)
    val currentMilestone = activePlan?.toCompletionMilestone()
    CompletionScheduleDiagnostics.debug(
      "request=$request goalId=${goal.id} activePlanId=${activePlan?.id} " +
        "milestonePlanId=${currentMilestone?.planId} completionWorkoutIds=${existingCompletionIds.takeIf { it.isNotEmpty() }} " +
        "milestoneWorkoutId=${currentMilestone?.workoutId} " +
        "completionWorkoutExists=${existingCompletionIds.isNotEmpty()} " +
        "scheduleState=${currentMilestone?.readiness} scheduledDate=${currentMilestone?.scheduledDate}"
    )
    try {
      val write = updateMilestone()
      CompletionScheduleDiagnostics.debug(
        "milestoneWriteOperation=${if (currentMilestone == null) "INSERT" else "UPDATE"} " +
          "milestoneWriteResult=${write.roomResult} scheduleState=${write.milestone.readiness} " +
          "scheduledDate=${write.milestone.scheduledDate}"
      )
      val plan = generate(goal, profile)
      val workouts = plan.weeks.flatMap { it.workouts }.map { it.workout }
      val completion = plan.completionMilestone?.workoutId?.let { id -> workouts.singleOrNull { it.id == id } }
      CompletionScheduleDiagnostics.debug(
        "surroundingReplanResult=success newPlanId=${plan.id} workoutCount=${workouts.size} " +
          "completionWorkoutId=${completion?.id} completionDate=${completion?.date}"
      )
      CompletionScheduleDiagnostics.debug(
        "finalResult=success goalId=${goal.id} planId=${plan.id} scheduleState=${write.milestone.readiness}"
      )
      CompletionScheduleUpdate(requireNotNull(plan.completionMilestone), plan)
    } catch (exception: CancellationException) {
      throw exception
    } catch (exception: Exception) {
      CompletionScheduleDiagnostics.error(
        "surroundingReplanResult=failure finalResult=failure request=$request goalId=${goal.id} " +
        "activePlanId=${activePlan?.id} milestonePlanId=${currentMilestone?.planId}",
        exception,
      )
      throw exception
    }
  }

  suspend fun saveAsActive(plan: TrainingPlan) = database.withTransaction {
    val records = plan.weeks.flatMap { week -> week.workouts.map { it.workout } }
    records.forEach { workout ->
      workoutDao.replaceWorkout(
        StructuredWorkoutPersistenceMapper.toRecord(workout, WorkoutLifecycleState.PLANNED.name, isPreview = false)
      )
    }
    dao.supersedeActivePlans()
    val snapshot = TrainingPlanPersistenceMapper.toSnapshot(plan.copy(status = TrainingPlanStatus.ACTIVE))
    dao.upsertPlan(snapshot.plan)
    dao.upsertWeeks(snapshot.weeks)
    dao.upsertReasons(snapshot.reasons)
    dao.upsertBlocks(snapshot.blocks)
    dao.upsertWorkoutReferences(snapshot.workouts)
  }

  private suspend fun loadPlan(entity: TrainingPlanEntity): TrainingPlan {
    val planId = entity.id
    val references = dao.getWorkoutReferences(planId)
    val workouts = references.associate { reference ->
      reference.workoutId to requireNotNull(workoutDao.getWorkout(reference.workoutId)) {
        "Missing structured workout ${reference.workoutId} for plan $planId"
      }
    }
    val executions = workoutDao.getExecutionsForPlan(planId).associate { execution ->
      execution.plannedWorkoutId to execution.toDomain().withActualSummary(
        database.runDao().getActualRun(execution.physicalRunId)?.toActualSummary())
    }
    return TrainingPlanPersistenceMapper.toDomain(
      TrainingPlanSnapshot(
        entity,
        dao.getWeeks(planId),
        dao.getReasons(planId),
        dao.getBlocks(planId),
        references,
      ),
      workouts.mapValues { StructuredWorkoutPersistenceMapper.toDomain(it.value) },
      executions,
    )
  }
}

data class CompletionScheduleUpdate(
  val milestone: com.example.openruncoach.domain.goal.GoalCompletionMilestone,
  val plan: TrainingPlan,
)

internal data class TrainingPlanSnapshot(
  val plan: TrainingPlanEntity,
  val weeks: List<TrainingWeekEntity>,
  val reasons: List<TrainingWeekReasonEntity>,
  val blocks: List<TrainingBlockEntity>,
  val workouts: List<TrainingPlanWorkoutEntity>,
)

internal object TrainingPlanPersistenceMapper {
  fun toSnapshot(plan: TrainingPlan): TrainingPlanSnapshot = TrainingPlanSnapshot(
    plan = TrainingPlanEntity(
      plan.id, plan.goalId, plan.createdAt.toEpochMilli(), plan.startDate.toEpochDay(), plan.endDate?.toEpochDay(),
      plan.generationVersion, plan.explanation, plan.confidence.name, plan.status.name,
      plan.completionMilestone?.scheduledDate?.toEpochDay(),
      plan.completionMilestone?.workoutId,
      plan.completionMilestone?.readiness?.name ?: GoalCompletionReadiness.GOAL_COMPLETION_NOT_READY.name,
      plan.completionMilestone?.promptDismissed ?: false,
      plan.completionMilestone?.updatedAt?.toEpochMilli() ?: plan.createdAt.toEpochMilli(),
    ),
    weeks = plan.weeks.map { week ->
      TrainingWeekEntity(
        plan.id, week.weekIndex, week.startDate.toEpochDay(), week.targetDistanceMeters,
        week.targetDurationSeconds, week.loadClassification.name, week.explanation,
      )
    },
    reasons = plan.weeks.flatMap { week ->
      week.reasons.mapIndexed { index, reason -> TrainingWeekReasonEntity(plan.id, week.weekIndex, index, reason) }
    },
    blocks = plan.blocks.mapIndexed { index, block ->
      TrainingBlockEntity(plan.id, index, block.type.name, block.startWeek, block.endWeek, block.purpose, block.progressionIntent)
    },
    workouts = plan.weeks.flatMap { week ->
      week.workouts.mapIndexed { index, entry ->
        TrainingPlanWorkoutEntity(plan.id, week.weekIndex, index, entry.workout.id, entry.slot.name, entry.generationReason)
      }
    },
  )

  fun toDomain(
    snapshot: TrainingPlanSnapshot,
    workoutsById: Map<String, com.example.openruncoach.domain.workout.StructuredWorkout>,
    executionsByWorkoutId: Map<String, com.example.openruncoach.domain.workout.WorkoutExecutionResult> = emptyMap(),
  ): TrainingPlan {
    val reasons = snapshot.reasons.groupBy { it.weekIndex }
    val references = snapshot.workouts.groupBy { it.weekIndex }
    return TrainingPlan(
      id = snapshot.plan.id,
      goalId = snapshot.plan.goalId,
      createdAt = Instant.ofEpochMilli(snapshot.plan.createdAtEpochMillis),
      startDate = LocalDate.ofEpochDay(snapshot.plan.startDateEpochDay),
      endDate = snapshot.plan.endDateEpochDay?.let(LocalDate::ofEpochDay),
      weeks = snapshot.weeks.sortedBy { it.weekIndex }.map { week ->
        TrainingWeek(
          week.weekIndex,
          LocalDate.ofEpochDay(week.startDateEpochDay),
          references[week.weekIndex].orEmpty().sortedBy { it.workoutOrder }.map { reference ->
            PlannedWorkoutEntry(
              WeeklyWorkoutSlot.valueOf(reference.slot),
              requireNotNull(workoutsById[reference.workoutId]),
              reference.generationReason,
              executionsByWorkoutId[reference.workoutId],
            )
          },
          week.targetDistanceMeters,
          week.targetDurationSeconds,
          WeekLoadClassification.valueOf(week.loadClassification),
          week.explanation,
          reasons[week.weekIndex].orEmpty().sortedBy { it.position }.map { it.text },
        )
      },
      blocks = snapshot.blocks.sortedBy { it.blockIndex }.map { block ->
        TrainingBlock(
          TrainingBlockType.valueOf(block.type), block.startWeek, block.endWeek,
          block.purpose, block.progressionIntent,
        )
      },
      generationVersion = snapshot.plan.generationVersion,
      explanation = snapshot.plan.explanation,
      confidence = ProfileConfidence.valueOf(snapshot.plan.confidence),
      status = TrainingPlanStatus.valueOf(snapshot.plan.status),
      completionMilestone = GoalCompletionMilestone(
        goalId = snapshot.plan.goalId,
        readiness = snapshot.plan.completionReadiness.toGoalCompletionReadiness(),
        scheduledDate = snapshot.plan.plannedCompletionDateEpochDay?.let(LocalDate::ofEpochDay),
        promptDismissed = snapshot.plan.completionPromptDismissed,
        reasons = listOf("Saved Completion Run scheduling state."),
        updatedAt = Instant.ofEpochMilli(snapshot.plan.completionUpdatedAtEpochMillis),
        planId = snapshot.plan.id,
        workoutId = snapshot.plan.plannedCompletionRunId,
      ),
    )
  }
}
