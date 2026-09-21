package com.example.openruncoach.domain.simulation

import com.example.openruncoach.domain.RunFeedback
import com.example.openruncoach.domain.RunSummary
import com.example.openruncoach.domain.coaching.CoachingAction
import com.example.openruncoach.domain.workout.PlannedWorkout
import com.example.openruncoach.domain.workout.PreRunCheckIn
import com.example.openruncoach.domain.workout.ReadinessStatus
import com.example.openruncoach.domain.workout.WorkoutType

enum class BuiltInScenario {
  WELL_RECOVERED,
  SORE_LEGS,
  POOR_SLEEP,
  SIGNIFICANT_PAIN,
  MILD_ILLNESS,
  SEVERE_ILLNESS,
  HIGH_RPE_COMPLETED,
  EASY_SUCCESS,
  ABORTED_WITH_PAIN,
  MULTIPLE_BAD_RECOVERY_SIGNALS,
}

data class ExpectedSimulationDecision(
  val readinessStatus: ReadinessStatus,
  val adjustedWorkoutType: WorkoutType,
  val coachingAction: CoachingAction,
  val nextWorkoutType: WorkoutType,
)

data class SimulationScenario(
  val key: BuiltInScenario,
  val displayName: String,
  val initialTrainingHistory: List<RunSummary>,
  val previousFeedback: RunFeedback?,
  val baselinePlannedWorkout: PlannedWorkout,
  val preRunCheckIn: PreRunCheckIn,
  val simulatedActualRun: RunSummary,
  val postRunFeedback: RunFeedback,
  val expected: ExpectedSimulationDecision,
)
