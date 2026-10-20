package com.example.openruncoach.domain.plan

import com.example.openruncoach.domain.goal.GoalType
import com.example.openruncoach.domain.goal.TrainingGoal
import com.example.openruncoach.domain.goal.targetPaceSecondsPerKm
import com.example.openruncoach.domain.workout.DistanceCompletion
import com.example.openruncoach.domain.workout.PaceTarget
import com.example.openruncoach.domain.workout.WorkoutEstimateCalculator
import com.example.openruncoach.domain.workout.WorkoutPurpose
import com.example.openruncoach.domain.workout.WorkoutType
import com.example.openruncoach.domain.workout.prescribedRunningDistanceMeters
import kotlin.math.abs

data class TrainingPlanValidationResult(
  val isValid: Boolean,
  val errors: List<String>,
)

class TrainingPlanValidator(
  private val minimumSanePaceSecondsPerKm: Double = 150.0,
  private val maximumSanePaceSecondsPerKm: Double = 900.0,
  private val durationToleranceSeconds: Long = 2L,
) {
  fun validate(
    plan: TrainingPlan,
    goal: TrainingGoal,
    requireGoalDistanceSession: Boolean = false,
  ): TrainingPlanValidationResult {
    val errors = buildList {
      plan.weeks.flatMap { it.workouts }.forEach { entry ->
        val workout = entry.workout
        val segments = workout.blocks.flatMap { it.segments }
        segments.forEach { segment ->
          val distance = segment.completion as? DistanceCompletion
          val pace = (listOf(segment.primaryIntensity) + segment.secondaryIntensities).filterIsInstance<PaceTarget>().firstOrNull()
          if (pace != null) {
            if (pace.minimumSecondsPerKm !in minimumSanePaceSecondsPerKm..maximumSanePaceSecondsPerKm ||
              pace.maximumSecondsPerKm !in minimumSanePaceSecondsPerKm..maximumSanePaceSecondsPerKm
            ) add("${segment.id}: pace range is outside the supported physiological bounds.")
            if (distance != null) {
              val estimate = WorkoutEstimateCalculator.calculate(
                workout.copy(blocks = listOf(com.example.openruncoach.domain.workout.SingleSegmentBlock("validation", 0, segment)))
              )
              val expectedMinimum = (distance.meters / 1_000.0 * pace.minimumSecondsPerKm).toLong()
              val expectedMaximum = (distance.meters / 1_000.0 * pace.maximumSecondsPerKm).toLong()
              if (abs(requireNotNull(estimate.estimatedMinimumDurationSeconds) - expectedMinimum) > durationToleranceSeconds ||
                abs(requireNotNull(estimate.estimatedMaximumDurationSeconds) - expectedMaximum) > durationToleranceSeconds
              ) add("${segment.id}: expected duration does not match distance and pace.")
            }
          }
        }
      }
      if (requireGoalDistanceSession && !hasContinuousGoalDistance(plan, goal.distanceMeters)) {
        add("Plan requires a continuous goal-distance workout.")
      }
      if (goal.goalType == GoalType.COMPLETE_DISTANCE) {
        val ordered = plan.weeks.flatMap { it.workouts }.map { it.workout }
          .sortedWith(compareBy { it.date })
        val completionSessions = ordered.filter { it.purpose == WorkoutPurpose.GOAL_COMPLETION }
        if (completionSessions.size > 1) add("Complete Distance plans may contain only one goal completion session.")
        if (requireGoalDistanceSession && completionSessions.size != 1) {
          add("Complete Distance plans require exactly one goal completion session.")
        }
        completionSessions.singleOrNull()?.let { session ->
          val completionIndex = ordered.indexOf(session)
          val distance = session.prescribedRunningDistanceMeters()
          if (abs(distance - goal.distanceMeters) >= 1.0) add("Goal completion session must prescribe exactly the target running distance.")
          if (session.targetRpeMin != 4 || session.targetRpeMax != 5) add("Completion goal-distance workout must use sustainable RPE 4–5 guidance.")
          ordered.take(completionIndex).forEach { workout ->
            if (workout.prescribedRunningDistanceMeters() >= goal.distanceMeters) {
              add("${workout.id}: pre-goal running distance must remain below the completion target.")
            }
          }
          ordered.drop(completionIndex + 1).forEach { workout ->
            if (workout.type !in setOf(WorkoutType.REST, WorkoutType.RECOVERY_RUN)) {
              add("${workout.id}: only rest or recovery may follow the unresolved goal completion session.")
            }
          }
          ordered.filter { it.id != session.id }.forEach { workout ->
            val spacing = abs(java.time.temporal.ChronoUnit.DAYS.between(session.date, workout.date))
            val demanding = workout.type in setOf(
              WorkoutType.LONG_RUN,
              WorkoutType.TEMPO_RUN,
              WorkoutType.THRESHOLD,
              WorkoutType.INTERVALS,
              WorkoutType.RACE_SPECIFIC,
            )
            if (demanding && spacing <= 2) add("${workout.id}: demanding workout is too close to the Completion Run.")
          }
          val completionWeek = plan.weeks.firstOrNull { week -> week.workouts.any { it.workout.id == session.id } }
          if (completionWeek?.workouts?.count { it.workout.type == WorkoutType.LONG_RUN } != 1) {
            add("The Completion Run must replace the normal Long Run in its week.")
          }
        }
        plan.weeks.filter { it.loadClassification == WeekLoadClassification.RECOVERY }.forEach { recoveryWeek ->
          val previous = plan.weeks.getOrNull(recoveryWeek.weekIndex - 1)?.targetDistanceMeters
          val recovery = recoveryWeek.targetDistanceMeters
          if (previous != null && recovery != null && recovery > 0.0 && recovery >= previous) {
            add("Week ${recoveryWeek.weekIndex + 1}: recovery load must be lower than the preceding week.")
          }
        }
      }
      if (goal.goalType == GoalType.TARGET_TIME && goal.targetTimeSeconds != null) {
        val goalPace = targetPaceSecondsPerKm(goal.targetTimeSeconds, goal.distanceMeters)
        plan.weeks.flatMap { it.workouts }.map { it.workout }
          .filter { it.type == WorkoutType.EASY_RUN }
          .flatMap { it.blocks }.flatMap { it.segments }
          .flatMap { listOf(it.primaryIntensity) + it.secondaryIntensities }
          .filterIsInstance<PaceTarget>()
          .forEach { pace ->
            if (goalPace in pace.minimumSecondsPerKm..pace.maximumSecondsPerKm) {
              add("Easy Run must not use the selected goal pace.")
            }
          }
      }
    }
    return TrainingPlanValidationResult(errors.isEmpty(), errors)
  }

  fun requireValid(plan: TrainingPlan, goal: TrainingGoal, requireGoalDistanceSession: Boolean = false) {
    val result = validate(plan, goal, requireGoalDistanceSession)
    require(result.isValid) { result.errors.joinToString(" ") }
  }

  private fun hasContinuousGoalDistance(plan: TrainingPlan, goalDistanceMeters: Double): Boolean =
    goalDistanceWorkouts(plan, goalDistanceMeters).isNotEmpty()

  private fun goalDistanceWorkouts(plan: TrainingPlan, goalDistanceMeters: Double) =
    plan.weeks.flatMap { it.workouts }.map { it.workout }.filter { workout ->
      workout.blocks.size == 1 && workout.blocks.single().segments.size == 1 &&
        (workout.blocks.single().segments.single().completion as? DistanceCompletion)?.meters
          ?.let { abs(it - goalDistanceMeters) < 1.0 } == true
    }
}
