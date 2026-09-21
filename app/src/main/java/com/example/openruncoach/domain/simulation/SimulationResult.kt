package com.example.openruncoach.domain.simulation

import com.example.openruncoach.domain.coaching.CoachingDecision
import com.example.openruncoach.domain.workout.PlannedWorkout
import com.example.openruncoach.domain.workout.WorkoutAdjustmentDecision

data class SimulationResult(
  val scenario: SimulationScenario,
  val adjustmentDecision: WorkoutAdjustmentDecision,
  val coachingDecision: CoachingDecision,
  val nextWorkout: PlannedWorkout,
  val timeline: List<SimulationStep>,
)
