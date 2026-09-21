package com.example.openruncoach.domain.workout

import com.example.openruncoach.domain.PainLevel
import java.time.Instant
import java.time.LocalDate

enum class WorkoutType {
  EASY_RUN,
  TEMPO_RUN,
  INTERVALS,
  LONG_RUN,
  RECOVERY_RUN,
  REST,
}

data class PlannedWorkout(
  val id: String,
  val date: LocalDate,
  val type: WorkoutType,
  val durationMinutes: Int,
  val targetRpeMin: Int,
  val targetRpeMax: Int,
  val createdAt: Instant,
) {
  init {
    require(durationMinutes >= 0) { "Workout duration cannot be negative." }
    require(targetRpeMin in 0..10 && targetRpeMax in targetRpeMin..10) { "Target RPE is invalid." }
    require(type == WorkoutType.REST || durationMinutes > 0) { "A running workout needs a duration." }
  }
}

enum class SorenessLevel { NONE, MILD, MODERATE, SEVERE }

enum class IllnessLevel { NONE, MILD, MODERATE, SEVERE }

enum class BodyArea {
  CALF,
  FOOT_ANKLE,
  KNEE,
  THIGH,
  HIP,
  BACK,
  OTHER,
}

data class PreRunCheckIn(
  val date: LocalDate,
  val readiness: Int,
  val energy: Int,
  val sleepQuality: Int,
  val soreness: SorenessLevel,
  val sorenessAreas: Set<BodyArea>,
  val pain: PainLevel,
  val stress: Int,
  val motivation: Int,
  val illness: IllnessLevel,
  val notes: String?,
) {
  fun validationError(): String? = when {
    readiness !in 1..5 -> "Readiness must be between 1 and 5."
    energy !in 1..5 -> "Energy must be between 1 and 5."
    sleepQuality !in 1..5 -> "Sleep quality must be between 1 and 5."
    stress !in 1..5 -> "Stress must be between 1 and 5."
    motivation !in 1..5 -> "Motivation must be between 1 and 5."
    else -> null
  }
}

enum class ReadinessStatus { GREEN, YELLOW, RED }

data class AdjustedWorkout(
  val originalWorkoutId: String,
  val type: WorkoutType,
  val durationMinutes: Int,
  val targetRpeMin: Int,
  val targetRpeMax: Int,
)

data class WorkoutAdjustmentDecision(
  val originalWorkout: PlannedWorkout,
  val adjustedWorkout: AdjustedWorkout,
  val readinessStatus: ReadinessStatus,
  val reasons: List<String>,
  val adjustments: List<String>,
  val generatedAt: Instant,
)

data class RecentTrainingVolume(
  val runCount: Int,
  val durationSeconds: Long,
  val distanceMeters: Double,
)
