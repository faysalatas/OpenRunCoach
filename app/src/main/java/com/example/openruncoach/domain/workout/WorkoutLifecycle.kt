package com.example.openruncoach.domain.workout

import com.example.openruncoach.domain.RunFeedback
import com.example.openruncoach.domain.belongsTo
import com.example.openruncoach.domain.RunSummary
import com.example.openruncoach.domain.coaching.CoachingDecision

enum class WorkoutLifecycleState {
  PLANNED,
  CHECK_IN_REQUIRED,
  READY,
  COMPLETED,
  CHECKOUT_REQUIRED,
  ANALYZED,
  NEXT_WORKOUT_CREATED,
}

data class WorkoutLifecycle(
  val plannedWorkout: PlannedWorkout,
  val state: WorkoutLifecycleState = WorkoutLifecycleState.PLANNED,
  val preRunCheckIn: PreRunCheckIn? = null,
  val adjustmentDecision: WorkoutAdjustmentDecision? = null,
  val actualRun: RunSummary? = null,
  val executionResult: WorkoutExecutionResult? = null,
  val postRunFeedback: RunFeedback? = null,
  val coachingDecision: CoachingDecision? = null,
  val nextWorkout: PlannedWorkout? = null,
)

sealed interface WorkoutLifecycleEvent {
  data object RequireCheckIn : WorkoutLifecycleEvent

  data class CheckInCompleted(
    val checkIn: PreRunCheckIn,
    val adjustmentDecision: WorkoutAdjustmentDecision,
  ) : WorkoutLifecycleEvent

  data class RunCompleted(
    val actualRun: RunSummary,
    val executionResult: WorkoutExecutionResult? = null,
  ) : WorkoutLifecycleEvent

  data object RequireCheckout : WorkoutLifecycleEvent

  data class AnalysisCompleted(
    val feedback: RunFeedback,
    val decision: CoachingDecision,
  ) : WorkoutLifecycleEvent

  data class NextWorkoutCreated(val workout: PlannedWorkout) : WorkoutLifecycleEvent
}

class InvalidWorkoutLifecycleTransition(
  current: WorkoutLifecycleState,
  event: WorkoutLifecycleEvent,
) : IllegalStateException("Cannot apply ${event::class.simpleName} while workout is $current.")

class WorkoutLifecycleService {
  fun transition(
    lifecycle: WorkoutLifecycle,
    event: WorkoutLifecycleEvent,
  ): WorkoutLifecycle = when {
    lifecycle.state == WorkoutLifecycleState.PLANNED && event is WorkoutLifecycleEvent.RequireCheckIn ->
      lifecycle.copy(state = WorkoutLifecycleState.CHECK_IN_REQUIRED)

    lifecycle.state == WorkoutLifecycleState.CHECK_IN_REQUIRED && event is WorkoutLifecycleEvent.CheckInCompleted -> {
      require(event.adjustmentDecision.originalWorkout.id == lifecycle.plannedWorkout.id) {
        "The adjustment must reference the lifecycle's planned workout."
      }
      lifecycle.copy(
        state = WorkoutLifecycleState.READY,
        preRunCheckIn = event.checkIn,
        adjustmentDecision = event.adjustmentDecision,
      )
    }

    lifecycle.state == WorkoutLifecycleState.READY && event is WorkoutLifecycleEvent.RunCompleted -> {
      require(event.executionResult?.plannedWorkoutId?.let { it == lifecycle.plannedWorkout.id } != false) {
        "The execution result must reference the lifecycle's planned workout."
      }
      require(event.executionResult?.actualRunId?.let { it == event.actualRun.healthConnectRecordId } != false) {
        "The execution result must reference the lifecycle's actual run."
      }
      lifecycle.copy(
        state = WorkoutLifecycleState.COMPLETED,
        actualRun = event.actualRun,
        executionResult = event.executionResult,
      )
    }

    lifecycle.state == WorkoutLifecycleState.COMPLETED && event is WorkoutLifecycleEvent.RequireCheckout ->
      lifecycle.copy(state = WorkoutLifecycleState.CHECKOUT_REQUIRED)

    lifecycle.state == WorkoutLifecycleState.CHECKOUT_REQUIRED && event is WorkoutLifecycleEvent.AnalysisCompleted -> {
      require(lifecycle.actualRun?.let(event.feedback::belongsTo) == true) {
        "Post-run feedback must belong to the completed run."
      }
      lifecycle.copy(
        state = WorkoutLifecycleState.ANALYZED,
        postRunFeedback = event.feedback,
        coachingDecision = event.decision,
      )
    }

    lifecycle.state == WorkoutLifecycleState.ANALYZED && event is WorkoutLifecycleEvent.NextWorkoutCreated ->
      lifecycle.copy(state = WorkoutLifecycleState.NEXT_WORKOUT_CREATED, nextWorkout = event.workout)

    else -> throw InvalidWorkoutLifecycleTransition(lifecycle.state, event)
  }
}
