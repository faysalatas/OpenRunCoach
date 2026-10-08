package com.example.openruncoach.domain.workout

import com.example.openruncoach.domain.CompletionStatus
import com.example.openruncoach.domain.PainLevel
import com.example.openruncoach.domain.RunFeedback
import com.example.openruncoach.domain.belongsTo
import com.example.openruncoach.domain.RunSummary
import com.example.openruncoach.domain.coaching.CoachingAction
import com.example.openruncoach.domain.coaching.CoachingDecision
import java.time.Clock
import kotlin.math.roundToInt

fun interface IdGenerator {
  fun nextId(): String
}

class NextWorkoutGenerator(
  private val clock: Clock = Clock.systemUTC(),
  private val idGenerator: IdGenerator,
) {
  fun generate(
    prescribedWorkout: AdjustedWorkout,
    actualRun: RunSummary,
    postRunFeedback: RunFeedback,
    coachingDecision: CoachingDecision,
  ): PlannedWorkout {
    require(postRunFeedback.belongsTo(actualRun)) { "Feedback must belong to the completed run." }
    val baseDuration = prescribedWorkout.durationMinutes.coerceAtLeast(MIN_NEXT_RUN_MINUTES)
    val safetyRest = postRunFeedback.painLevel == PainLevel.SIGNIFICANT ||
      (postRunFeedback.completionStatus == CompletionStatus.ABORTED && postRunFeedback.painLevel != PainLevel.NONE)
    val (type, duration, rpeRange) = when {
      safetyRest -> Triple(WorkoutType.REST, 0, 0..0)
      coachingDecision.action == CoachingAction.PROGRESS -> Triple(
        progressiveType(prescribedWorkout.type),
        increase(baseDuration, 10),
        prescribedWorkout.targetRpeMin..prescribedWorkout.targetRpeMax,
      )
      coachingDecision.action == CoachingAction.SMALL_PROGRESS -> Triple(
        progressiveType(prescribedWorkout.type),
        increase(baseDuration, 5),
        prescribedWorkout.targetRpeMin..prescribedWorkout.targetRpeMax,
      )
      coachingDecision.action == CoachingAction.MAINTAIN -> Triple(
        runnableType(prescribedWorkout.type),
        baseDuration,
        prescribedWorkout.targetRpeMin.coerceAtLeast(2)..prescribedWorkout.targetRpeMax.coerceAtLeast(3),
      )
      coachingDecision.action == CoachingAction.REDUCE -> Triple(
        WorkoutType.EASY_RUN,
        reduce(baseDuration, 15),
        2..4,
      )
      else -> Triple(WorkoutType.RECOVERY_RUN, MIN_NEXT_RUN_MINUTES, 2..3)
    }
    return simpleDurationWorkout(
      id = idGenerator.nextId(),
      date = actualRun.endTime.atZone(clock.zone).toLocalDate().plusDays(2),
      type = type,
      durationMinutes = duration,
      targetRpeMin = rpeRange.first,
      targetRpeMax = rpeRange.last,
      createdAt = clock.instant(),
    )
  }
}

private const val MIN_NEXT_RUN_MINUTES = 15

private fun increase(minutes: Int, percent: Int) = (minutes * (100 + percent) / 100.0).roundToInt()
private fun reduce(minutes: Int, percent: Int) =
  (minutes * (100 - percent) / 100.0).roundToInt().coerceAtLeast(MIN_NEXT_RUN_MINUTES)

private fun progressiveType(type: WorkoutType) = when (type) {
  WorkoutType.REST, WorkoutType.RECOVERY_RUN -> WorkoutType.EASY_RUN
  else -> type
}

private fun runnableType(type: WorkoutType) = if (type == WorkoutType.REST) WorkoutType.RECOVERY_RUN else type
