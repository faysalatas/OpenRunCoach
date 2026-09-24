package com.example.openruncoach.domain.workout

import com.example.openruncoach.domain.PainLevel
import java.time.Instant
import java.time.LocalDate

enum class WorkoutType { EASY_RUN, RECOVERY_RUN, LONG_RUN, RUN_WALK, TEMPO_RUN, THRESHOLD, INTERVALS, STRIDES, TECHNIQUE, RACE_SPECIFIC, REST }

enum class WorkoutPurpose { RECOVERY, AEROBIC_BASE, ENDURANCE, LACTATE_THRESHOLD, SPEED, VO2MAX, RUNNING_ECONOMY, TECHNIQUE, RACE_SPECIFIC }

enum class WorkoutOrigin { MANUAL, GENERATED, ADJUSTED, SIMULATION }

data class WorkoutAdjustability(
  val canChangeType: Boolean = true,
  val canChangeTotalDuration: Boolean = true,
  val canChangeSegmentCompletion: Boolean = true,
  val canChangeRepetitions: Boolean = true,
  val canChangeIntensity: Boolean = true,
  val canChangeRecovery: Boolean = true,
)

enum class SegmentCategory { WARMUP, WORK, RECOVERY, COOLDOWN, EASY, STRIDE, DRILL, WALK, REST }

enum class HeartRateZone { ZONE_1, ZONE_2, ZONE_3, ZONE_4, ZONE_5 }

enum class GuidanceConfidence { LOW, MEDIUM, HIGH, UNCALIBRATED }

sealed interface SegmentCompletion

data class DurationCompletion(val seconds: Long) : SegmentCompletion {
  init { require(seconds > 0) { "Segment duration must be positive." } }
}

data class DistanceCompletion(val meters: Double) : SegmentCompletion {
  init { require(meters.isFinite() && meters > 0) { "Segment distance must be positive." } }
}

data class RepetitionCompletion(val repetitions: Int) : SegmentCompletion {
  init { require(repetitions > 0) { "Segment repetitions must be positive." } }
}

data object OpenCompletion : SegmentCompletion

sealed interface IntensityTarget { val confidence: GuidanceConfidence }

data object NoIntensityTarget : IntensityTarget { override val confidence = GuidanceConfidence.HIGH }

data class RpeTarget(
  val minimum: Int,
  val maximum: Int,
  override val confidence: GuidanceConfidence = GuidanceConfidence.HIGH,
) : IntensityTarget {
  init { require(minimum in 1..10 && maximum in minimum..10) { "RPE target is invalid." } }
}

data class PaceTarget(
  val minimumSecondsPerKm: Double,
  val maximumSecondsPerKm: Double,
  override val confidence: GuidanceConfidence = GuidanceConfidence.MEDIUM,
) : IntensityTarget {
  init {
    require(minimumSecondsPerKm.isFinite() && minimumSecondsPerKm > 0) { "Pace target is invalid." }
    require(maximumSecondsPerKm.isFinite() && maximumSecondsPerKm >= minimumSecondsPerKm) { "Pace target is invalid." }
  }
}

data class HeartRateTarget(
  val minimumBpm: Int,
  val maximumBpm: Int,
  override val confidence: GuidanceConfidence = GuidanceConfidence.MEDIUM,
) : IntensityTarget {
  init { require(minimumBpm > 0 && maximumBpm >= minimumBpm) { "Heart-rate target is invalid." } }
}

data class HeartRateZoneTarget(
  val zone: HeartRateZone,
  override val confidence: GuidanceConfidence = GuidanceConfidence.UNCALIBRATED,
) : IntensityTarget

data class WorkoutSegment(
  val id: String,
  val order: Int,
  val category: SegmentCategory,
  val name: String,
  val completion: SegmentCompletion,
  val primaryIntensity: IntensityTarget = NoIntensityTarget,
  val secondaryIntensities: List<IntensityTarget> = emptyList(),
  val notes: String? = null,
) {
  init {
    require(id.isNotBlank()) { "Segment ID cannot be blank." }
    require(order >= 0) { "Segment order cannot be negative." }
    require(name.isNotBlank()) { "Segment name cannot be blank." }
    require(secondaryIntensities.none { it is NoIntensityTarget }) { "Secondary guidance must contain an explicit target." }
  }
}

sealed interface WorkoutBlock {
  val id: String
  val order: Int
  val label: String?
  val segments: List<WorkoutSegment>
}

data class SingleSegmentBlock(
  override val id: String,
  override val order: Int,
  val segment: WorkoutSegment,
  override val label: String? = null,
) : WorkoutBlock {
  override val segments: List<WorkoutSegment> = listOf(segment)

  init {
    require(id.isNotBlank()) { "Block ID cannot be blank." }
    require(order >= 0) { "Block order cannot be negative." }
  }
}

data class RepeatBlock(
  override val id: String,
  override val order: Int,
  val repetitions: Int,
  override val segments: List<WorkoutSegment>,
  override val label: String? = null,
) : WorkoutBlock {
  init {
    require(id.isNotBlank()) { "Block ID cannot be blank." }
    require(order >= 0) { "Block order cannot be negative." }
    require(repetitions > 0) { "A repeat block needs at least one repetition." }
    require(segments.isNotEmpty()) { "A repeat block needs at least one segment." }
    require(segments.map { it.order }.distinct().size == segments.size) { "Segment order must be unique within a block." }
  }
}

data class PlannedWorkout(
  val id: String,
  val date: LocalDate,
  val type: WorkoutType,
  val name: String,
  val purpose: WorkoutPurpose,
  val blocks: List<WorkoutBlock>,
  val explanation: String,
  val cues: List<String>,
  val createdAt: Instant,
  val goalId: String? = null,
  val origin: WorkoutOrigin = WorkoutOrigin.MANUAL,
  val adjustability: WorkoutAdjustability = WorkoutAdjustability(),
) {
  init {
    require(id.isNotBlank()) { "Workout ID cannot be blank." }
    require(name.isNotBlank()) { "Workout name cannot be blank." }
    require(explanation.isNotBlank()) { "Workout explanation cannot be blank." }
    require(type == WorkoutType.REST || blocks.isNotEmpty()) { "A running workout needs structured blocks." }
    require(type != WorkoutType.REST || blocks.isEmpty()) { "Rest workouts cannot contain running segments." }
    require(blocks.map { it.order }.distinct().size == blocks.size) { "Block order must be unique." }
  }

  val durationMinutes: Int get() = WorkoutEstimateCalculator.calculate(this).knownDurationSeconds.toInt() / 60

  val targetRpeMin: Int get() = allIntensityTargets().filterIsInstance<RpeTarget>().minOfOrNull(RpeTarget::minimum) ?: 0

  val targetRpeMax: Int get() = allIntensityTargets().filterIsInstance<RpeTarget>().maxOfOrNull(RpeTarget::maximum) ?: 0

  private fun allIntensityTargets(): List<IntensityTarget> = blocks.flatMap { block ->
    block.segments.flatMap { listOf(it.primaryIntensity) + it.secondaryIntensities }
  }
}

/** StructuredWorkout is the existing planned-workout identity with a structured prescription. */
typealias StructuredWorkout = PlannedWorkout

fun simpleDurationWorkout(
  id: String,
  date: LocalDate,
  type: WorkoutType,
  durationMinutes: Int,
  targetRpeMin: Int,
  targetRpeMax: Int,
  createdAt: Instant,
  name: String = type.displayName(),
  purpose: WorkoutPurpose = type.defaultPurpose(),
  explanation: String = type.defaultExplanation(),
  origin: WorkoutOrigin = WorkoutOrigin.MANUAL,
  goalId: String? = null,
): PlannedWorkout {
  require(durationMinutes >= 0) { "Workout duration cannot be negative." }
  require(targetRpeMin in 0..10 && targetRpeMax in targetRpeMin..10) { "Target RPE is invalid." }
  require(type == WorkoutType.REST || durationMinutes > 0) { "A running workout needs a duration." }
  val blocks = if (type == WorkoutType.REST) emptyList() else listOf(
    SingleSegmentBlock(
      id = "$id:block:0",
      order = 0,
      segment = WorkoutSegment(
        id = "$id:segment:0",
        order = 0,
        category = if (type == WorkoutType.RECOVERY_RUN) SegmentCategory.RECOVERY else SegmentCategory.EASY,
        name = name,
        completion = DurationCompletion(durationMinutes * 60L),
        primaryIntensity = if (targetRpeMin > 0) RpeTarget(targetRpeMin, targetRpeMax) else NoIntensityTarget,
      ),
    ),
  )
  return PlannedWorkout(id, date, type, name, purpose, blocks, explanation, emptyList(), createdAt, goalId, origin)
}

enum class SorenessLevel { NONE, MILD, MODERATE, SEVERE }
enum class IllnessLevel { NONE, MILD, MODERATE, SEVERE }
enum class BodyArea { CALF, FOOT_ANKLE, KNEE, THIGH, HIP, BACK, OTHER }

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

data class AdjustedWorkout(val originalWorkoutId: String, val type: WorkoutType, val durationMinutes: Int, val targetRpeMin: Int, val targetRpeMax: Int)

data class WorkoutAdjustmentDecision(
  val originalWorkout: PlannedWorkout,
  val adjustedWorkout: AdjustedWorkout,
  val readinessStatus: ReadinessStatus,
  val reasons: List<String>,
  val adjustments: List<String>,
  val generatedAt: Instant,
)

data class RecentTrainingVolume(val runCount: Int, val durationSeconds: Long, val distanceMeters: Double)

fun WorkoutType.displayName(): String = when (this) {
  WorkoutType.EASY_RUN -> "Easy Run"
  WorkoutType.RECOVERY_RUN -> "Recovery Run"
  WorkoutType.LONG_RUN -> "Long Run"
  WorkoutType.RUN_WALK -> "Run/Walk"
  WorkoutType.TEMPO_RUN -> "Tempo Run"
  WorkoutType.THRESHOLD -> "Threshold"
  WorkoutType.INTERVALS -> "Intervals"
  WorkoutType.STRIDES -> "Strides"
  WorkoutType.TECHNIQUE -> "Technique"
  WorkoutType.RACE_SPECIFIC -> "Race Specific"
  WorkoutType.REST -> "Rest"
}

fun WorkoutType.defaultPurpose(): WorkoutPurpose = when (this) {
  WorkoutType.RECOVERY_RUN, WorkoutType.REST -> WorkoutPurpose.RECOVERY
  WorkoutType.EASY_RUN, WorkoutType.RUN_WALK -> WorkoutPurpose.AEROBIC_BASE
  WorkoutType.LONG_RUN -> WorkoutPurpose.ENDURANCE
  WorkoutType.TEMPO_RUN, WorkoutType.THRESHOLD -> WorkoutPurpose.LACTATE_THRESHOLD
  WorkoutType.INTERVALS -> WorkoutPurpose.VO2MAX
  WorkoutType.STRIDES -> WorkoutPurpose.RUNNING_ECONOMY
  WorkoutType.TECHNIQUE -> WorkoutPurpose.TECHNIQUE
  WorkoutType.RACE_SPECIFIC -> WorkoutPurpose.RACE_SPECIFIC
}

fun WorkoutType.defaultExplanation(): String = when (defaultPurpose()) {
  WorkoutPurpose.RECOVERY -> "Support recovery while keeping the training routine gentle."
  WorkoutPurpose.AEROBIC_BASE -> "Build aerobic endurance with manageable fatigue."
  WorkoutPurpose.ENDURANCE -> "Build the ability to sustain comfortable running for longer."
  WorkoutPurpose.LACTATE_THRESHOLD -> "Improve the effort you can sustain for longer periods."
  WorkoutPurpose.SPEED -> "Develop speed with controlled, high-quality running."
  WorkoutPurpose.VO2MAX -> "Develop aerobic power through controlled work and recovery."
  WorkoutPurpose.RUNNING_ECONOMY -> "Practice fast, relaxed running with full control."
  WorkoutPurpose.TECHNIQUE -> "Practice movement skills without prescribing a universal cadence."
  WorkoutPurpose.RACE_SPECIFIC -> "Prepare for the demands of the selected event."
}
