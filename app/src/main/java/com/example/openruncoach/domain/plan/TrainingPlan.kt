package com.example.openruncoach.domain.plan

import com.example.openruncoach.domain.profile.ProfileConfidence
import com.example.openruncoach.domain.goal.GoalCompletionMilestone
import com.example.openruncoach.domain.workout.StructuredWorkout
import com.example.openruncoach.domain.workout.WorkoutExecutionResult
import java.time.Instant
import java.time.LocalDate

enum class TrainingPlanStatus { ACTIVE, SUPERSEDED, COMPLETED, ARCHIVED }

enum class TrainingBlockType { FOUNDATION, BASE, BUILD, SPECIFIC, PEAK, TAPER, RECOVERY }

enum class WeekLoadClassification { RECOVERY, NORMAL, BUILD, HIGH }

enum class WeeklyWorkoutSlot { EARLY_WEEK, MID_WEEK, LATE_WEEK, WEEKEND }

data class PlannedWorkoutEntry(
  val slot: WeeklyWorkoutSlot,
  val workout: StructuredWorkout,
  val generationReason: String,
  val executionResult: WorkoutExecutionResult? = null,
  val originalWorkout: StructuredWorkout = workout,
  val adjustmentReasons: List<String> = emptyList(),
)

data class TrainingWeek(
  val weekIndex: Int,
  val startDate: LocalDate,
  val workouts: List<PlannedWorkoutEntry>,
  val targetDistanceMeters: Double?,
  val targetDurationSeconds: Long?,
  val loadClassification: WeekLoadClassification,
  val explanation: String,
  val reasons: List<String>,
) {
  init {
    require(weekIndex >= 0)
    require(workouts.isNotEmpty())
    require(workouts.map { it.workout.id }.distinct().size == workouts.size)
    require(targetDistanceMeters == null || targetDistanceMeters >= 0.0)
    require(targetDurationSeconds == null || targetDurationSeconds >= 0L)
    require(explanation.isNotBlank())
    require(reasons.isNotEmpty())
  }
}

data class TrainingBlock(
  val type: TrainingBlockType,
  val startWeek: Int,
  val endWeek: Int,
  val purpose: String,
  val progressionIntent: String,
) {
  init {
    require(startWeek >= 0 && endWeek >= startWeek)
    require(purpose.isNotBlank())
    require(progressionIntent.isNotBlank())
  }
}

data class TrainingPlan(
  val id: String,
  val goalId: String,
  val createdAt: Instant,
  val startDate: LocalDate,
  val endDate: LocalDate?,
  val weeks: List<TrainingWeek>,
  val blocks: List<TrainingBlock>,
  val generationVersion: Int,
  val explanation: String,
  val confidence: ProfileConfidence,
  val status: TrainingPlanStatus,
  val completionMilestone: GoalCompletionMilestone? = null,
) {
  init {
    require(id.isNotBlank() && goalId.isNotBlank())
    require(weeks.isNotEmpty())
    require(weeks.map { it.weekIndex } == weeks.indices.toList())
    require(explanation.isNotBlank())
    require(generationVersion > 0)
    require(completionMilestone == null || completionMilestone.planId == id)
    require(completionMilestone == null || completionMilestone.goalId == goalId)
  }
}

val TrainingPlan.isAssessmentPhase: Boolean
  get() = weeks.flatMap { it.workouts }.all { it.workout.type == com.example.openruncoach.domain.workout.WorkoutType.ASSESSMENT }

fun activateTrainingPlan(existing: List<TrainingPlan>, newPlan: TrainingPlan): List<TrainingPlan> =
  existing.map { if (it.status == TrainingPlanStatus.ACTIVE) it.copy(status = TrainingPlanStatus.SUPERSEDED) else it } +
    newPlan.copy(status = TrainingPlanStatus.ACTIVE)

const val TRAINING_PLAN_GENERATOR_VERSION = 5
