package com.example.openruncoach.domain.simulation

import com.example.openruncoach.domain.workout.CheckInEvaluator
import com.example.openruncoach.domain.workout.IdGenerator
import com.example.openruncoach.domain.workout.NextWorkoutGenerator
import com.example.openruncoach.domain.workout.PostRunEvaluator
import com.example.openruncoach.domain.workout.RecentTrainingVolume
import com.example.openruncoach.domain.workout.displayName
import java.time.Clock

class SimulationRunner(
  clock: Clock,
  idGenerator: IdGenerator,
) {
  private val checkInEvaluator = CheckInEvaluator(clock)
  private val postRunEvaluator = PostRunEvaluator(
    com.example.openruncoach.domain.coaching.CoachingEngine(clock)
  )
  private val nextWorkoutGenerator = NextWorkoutGenerator(clock, idGenerator)

  fun run(scenario: SimulationScenario): SimulationResult {
    val canonicalHistory = scenario.initialTrainingHistory.filter { it.isCanonical }
    val volume = RecentTrainingVolume(
      runCount = canonicalHistory.size,
      durationSeconds = canonicalHistory.sumOf { it.durationSeconds },
      distanceMeters = canonicalHistory.mapNotNull { it.distanceMeters }.sum(),
    )
    val adjustment = checkInEvaluator.evaluate(
      scenario.baselinePlannedWorkout,
      scenario.preRunCheckIn,
      canonicalHistory,
      volume,
      scenario.previousFeedback,
    )
    val completedHistory = canonicalHistory + scenario.simulatedActualRun
    val coaching = requireNotNull(
      postRunEvaluator.evaluate(
        adjustment.adjustedWorkout,
        scenario.simulatedActualRun,
        scenario.postRunFeedback,
        completedHistory,
      )
    ) { "The simulated actual run must be the latest canonical run with matching feedback." }
    val nextWorkout = nextWorkoutGenerator.generate(
      adjustment.adjustedWorkout,
      scenario.simulatedActualRun,
      scenario.postRunFeedback,
      coaching,
    )
    return SimulationResult(
      scenario,
      adjustment,
      coaching,
      nextWorkout,
      timeline = createTimeline(scenario, adjustment, coaching, nextWorkout),
    )
  }
}

private fun createTimeline(
  scenario: SimulationScenario,
  adjustment: com.example.openruncoach.domain.workout.WorkoutAdjustmentDecision,
  coaching: com.example.openruncoach.domain.coaching.CoachingDecision,
  nextWorkout: com.example.openruncoach.domain.workout.PlannedWorkout,
): List<SimulationStep> = listOf(
  SimulationStep(
    SimulationStage.BASELINE_WORKOUT,
    "Baseline Workout",
    listOf(
      scenario.baselinePlannedWorkout.type.displayName(),
      "${scenario.baselinePlannedWorkout.durationMinutes} min · RPE ${scenario.baselinePlannedWorkout.targetRpeMin}–${scenario.baselinePlannedWorkout.targetRpeMax}",
    ),
  ),
  SimulationStep(
    SimulationStage.PRE_RUN_CHECK_IN,
    "Pre-run Check-in",
    listOf(
      "Readiness ${scenario.preRunCheckIn.readiness}/5 · Energy ${scenario.preRunCheckIn.energy}/5 · Sleep ${scenario.preRunCheckIn.sleepQuality}/5",
      "Soreness ${scenario.preRunCheckIn.soreness.name.lowercase()} · Pain ${scenario.preRunCheckIn.pain.name.lowercase()} · Illness ${scenario.preRunCheckIn.illness.name.lowercase()}",
    ),
  ),
  SimulationStep(
    SimulationStage.ADJUSTMENT,
    "Adjustment · ${adjustment.readinessStatus}",
    listOf(
      "${adjustment.adjustedWorkout.type.displayName()} · ${adjustment.adjustedWorkout.durationMinutes} min",
    ) + adjustment.reasons + adjustment.adjustments,
  ),
  SimulationStep(
    SimulationStage.SIMULATED_RUN,
    "Simulated Run",
    listOf("${scenario.simulatedActualRun.durationSeconds / 60} min completed"),
  ),
  SimulationStep(
    SimulationStage.POST_RUN_FEEDBACK,
    "Post-run Feedback",
    listOf(
      "RPE ${scenario.postRunFeedback.rpe}/10 · ${scenario.postRunFeedback.completionStatus}",
      "Pain ${scenario.postRunFeedback.painLevel.name.lowercase()} · Fatigue ${scenario.postRunFeedback.muscleFatigue.name.lowercase()}",
    ),
  ),
  SimulationStep(
    SimulationStage.COACH_DECISION,
    "Coach Decision · ${coaching.action}",
    listOf("Score ${coaching.score}") + coaching.reasons,
  ),
  SimulationStep(
    SimulationStage.NEXT_WORKOUT,
    "Next Workout",
    listOf(
      nextWorkout.type.displayName(),
      "${nextWorkout.durationMinutes} min · RPE ${nextWorkout.targetRpeMin}–${nextWorkout.targetRpeMax}",
    ),
  ),
)
