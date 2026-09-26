package com.example.openruncoach.data

import androidx.room.withTransaction
import com.example.openruncoach.data.local.OpenRunCoachDatabase
import com.example.openruncoach.data.local.TrainingPlanEntity
import com.example.openruncoach.domain.goal.GoalCompletionMilestone
import com.example.openruncoach.domain.goal.GoalCompletionEvaluator
import com.example.openruncoach.domain.goal.GoalCompletionResult
import com.example.openruncoach.domain.goal.GoalCompletionMilestoneService
import com.example.openruncoach.domain.goal.GoalCompletionReadiness
import com.example.openruncoach.domain.goal.GoalCompletionReadinessEvaluator
import com.example.openruncoach.domain.goal.GoalStatus
import com.example.openruncoach.domain.goal.TrainingGoal
import com.example.openruncoach.domain.profile.AthletePerformanceProfile
import com.example.openruncoach.domain.RunFeedback
import com.example.openruncoach.domain.RunSummary
import com.example.openruncoach.domain.metrics.RunMetrics
import com.example.openruncoach.domain.plan.TrainingPlanStatus
import com.example.openruncoach.domain.workout.WorkoutLifecycleState
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class GoalCompletionRepository(
  private val database: OpenRunCoachDatabase,
  private val clock: Clock = Clock.systemDefaultZone(),
  private val evaluator: GoalCompletionReadinessEvaluator = GoalCompletionReadinessEvaluator(),
  private val service: GoalCompletionMilestoneService = GoalCompletionMilestoneService(clock),
  private val completionEvaluator: GoalCompletionEvaluator = GoalCompletionEvaluator(),
) {
  private val dao = database.trainingPlanDao()
  private val goalDao = database.trainingGoalDao()

  fun observe(goalId: String): Flow<GoalCompletionMilestone?> =
    dao.observeActivePlanForGoal(goalId).map { it?.toCompletionMilestone() }

  suspend fun refreshReadiness(goal: TrainingGoal, profile: AthletePerformanceProfile): GoalCompletionMilestone {
    val activePlan = dao.getActivePlanForGoal(goal.id)
    val current = activePlan?.toCompletionMilestone()
    val evaluated = evaluator.evaluate(goal, profile, current, clock.instant()).copy(
      planId = activePlan?.id,
      workoutId = activePlan?.plannedCompletionRunId,
    )
    if (activePlan != null && current != null && (
        current.readiness != evaluated.readiness ||
          current.scheduledDate != evaluated.scheduledDate ||
          current.promptDismissed != evaluated.promptDismissed ||
          current.workoutId != evaluated.workoutId
      )
    ) persist(activePlan, evaluated)
    return evaluated
  }

  suspend fun schedule(goalId: String, date: LocalDate): GoalCompletionMilestone {
    return scheduleWithResult(goalId, date).milestone
  }

  internal suspend fun scheduleWithResult(goalId: String, date: LocalDate): GoalCompletionWriteResult {
    val activePlan = requireNotNull(dao.getActivePlanForGoal(goalId)) {
      "No active training plan exists for Completion Run scheduling."
    }
    val current = activePlan.toCompletionMilestone()
    val scheduled = service.schedule(goalId, current, date)
    return GoalCompletionWriteResult(scheduled, persist(activePlan, scheduled))
  }

  suspend fun postpone(goalId: String): GoalCompletionMilestone {
    return postponeWithResult(goalId).milestone
  }

  internal suspend fun postponeWithResult(goalId: String): GoalCompletionWriteResult {
    val activePlan = requireNotNull(dao.getActivePlanForGoal(goalId)) {
      "No active training plan exists for Completion Run scheduling."
    }
    val current = activePlan.toCompletionMilestone()
    val postponed = service.postpone(current)
    return GoalCompletionWriteResult(postponed, persist(activePlan, postponed))
  }

  suspend fun evaluateObservedRun(
    goal: TrainingGoal,
    canonicalRun: RunSummary,
    metrics: RunMetrics?,
    feedback: RunFeedback?,
  ): GoalCompletionResult {
    val result = completionEvaluator.evaluate(
      goal = goal,
      canonicalRun = canonicalRun,
      metrics = metrics,
      workoutState = WorkoutLifecycleState.COMPLETED,
      feedback = feedback,
    )
    if (result.completed) markCompleted(goal.id)
    return result
  }

  suspend fun markCompleted(goalId: String): GoalCompletionMilestone? = database.withTransaction {
    val activePlan = dao.getActivePlanForGoal(goalId)
    val cancelled = activePlan?.let { plan ->
      service.cancelForCompletedGoal(plan.toCompletionMilestone()).also {
        persist(plan, it)
        dao.updateStatus(plan.id, TrainingPlanStatus.COMPLETED.name)
      }
    }
    goalDao.updateStatus(goalId, GoalStatus.COMPLETED.name)
    cancelled
  }

  private suspend fun persist(plan: TrainingPlanEntity, milestone: GoalCompletionMilestone): Int {
    require(milestone.planId == plan.id) {
      "Stale Completion Run milestone: active plan ${plan.id} does not match ${milestone.planId}."
    }
    return dao.updateCompletionMilestone(
      planId = plan.id,
      goalId = milestone.goalId,
      dateEpochDay = milestone.scheduledDate?.toEpochDay(),
      workoutId = milestone.workoutId,
      readiness = milestone.readiness.name,
      promptDismissed = milestone.promptDismissed,
      updatedAtEpochMillis = milestone.updatedAt.toEpochMilli(),
    ).also { affected ->
      check(affected == 1) {
        "Completion Run milestone update affected $affected rows for goal ${milestone.goalId}, plan ${plan.id}."
      }
    }
  }
}

internal data class GoalCompletionWriteResult(
  val milestone: GoalCompletionMilestone,
  val roomResult: Int,
)

internal fun TrainingPlanEntity.toCompletionMilestone() = GoalCompletionMilestone(
  goalId = goalId,
  readiness = completionReadiness.toGoalCompletionReadiness(),
  scheduledDate = plannedCompletionDateEpochDay?.let(LocalDate::ofEpochDay),
  promptDismissed = completionPromptDismissed,
  reasons = listOf("Saved Completion Run scheduling state."),
  updatedAt = Instant.ofEpochMilli(completionUpdatedAtEpochMillis),
  planId = id,
  workoutId = plannedCompletionRunId,
)

internal fun String.toGoalCompletionReadiness(): GoalCompletionReadiness = when (this) {
  GoalCompletionReadiness.GOAL_COMPLETION_READY.name -> GoalCompletionReadiness.GOAL_COMPLETION_READY
  else -> GoalCompletionReadiness.GOAL_COMPLETION_NOT_READY
}
