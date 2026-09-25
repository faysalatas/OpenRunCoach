package com.example.openruncoach.domain.plan

import com.example.openruncoach.domain.goal.GoalType
import com.example.openruncoach.domain.goal.TrainingGoal
import com.example.openruncoach.domain.goal.targetPaceSecondsPerKm
import com.example.openruncoach.domain.profile.AthletePerformanceProfile
import com.example.openruncoach.domain.profile.ProfileConfidence
import com.example.openruncoach.domain.workout.GuidanceConfidence
import com.example.openruncoach.domain.workout.WorkoutPurpose
import com.example.openruncoach.domain.workout.WorkoutType
import kotlin.math.roundToInt

enum class PacePrescriptionSource {
  OBSERVED_EASY_BASELINE,
  RECENT_PERFORMANCE,
  VERIFIED_BENCHMARK,
  GOAL_PACE,
  DERIVED_CONSERVATIVE,
  INSUFFICIENT_DATA,
}

data class PacePrescription(
  val minSecondsPerKm: Double,
  val maxSecondsPerKm: Double,
  val representativeSecondsPerKm: Double,
  val source: PacePrescriptionSource,
  val confidence: GuidanceConfidence,
  val explanation: String,
) {
  init {
    require(minSecondsPerKm.isFinite() && minSecondsPerKm > 0.0)
    require(maxSecondsPerKm.isFinite() && maxSecondsPerKm >= minSecondsPerKm)
    require(representativeSecondsPerKm in minSecondsPerKm..maxSecondsPerKm)
    require(explanation.isNotBlank())
  }
}

data class PacePrescriptionConfig(
  val highConfidenceHalfWidthSeconds: Double = 15.0,
  val mediumConfidenceHalfWidthSeconds: Double = 20.0,
  val lowConfidenceHalfWidthSeconds: Double = 30.0,
  val veryLowConfidenceHalfWidthSeconds: Double = 40.0,
  val longRunSlowdownSeconds: Double = 12.0,
  val recoverySlowdownSeconds: Double = 25.0,
  val controlledGoalPaceOffsetSeconds: Double = 30.0,
  val controlledGoalPaceWidthSeconds: Double = 12.0,
  val thresholdEasyPaceFraction: Double = 0.90,
  val intervalEasyPaceFraction: Double = 0.82,
  val qualityHalfWidthSeconds: Double = 10.0,
  val recentPerformanceEasyFactor: Double = 1.08,
  val benchmarkEasyFactor: Double = 1.22,
  val roundingSeconds: Int = 5,
  val minimumSanePaceSecondsPerKm: Double = 150.0,
  val maximumSanePaceSecondsPerKm: Double = 900.0,
) {
  init {
    require(roundingSeconds > 0)
    require(minimumSanePaceSecondsPerKm < maximumSanePaceSecondsPerKm)
  }
}

class PacePrescriptionEngine(
  private val config: PacePrescriptionConfig = PacePrescriptionConfig(),
) {
  fun prescribe(
    profile: AthletePerformanceProfile,
    goal: TrainingGoal,
    workoutType: WorkoutType,
    workoutPurpose: WorkoutPurpose,
    workoutDistanceMeters: Double,
    currentBlock: TrainingBlockType,
    profileConfidence: ProfileConfidence = profile.profileConfidence,
  ): PacePrescription? {
    if (!workoutDistanceMeters.isFinite() || workoutDistanceMeters <= 0.0) return null
    if (workoutType == WorkoutType.RACE_SPECIFIC && goal.goalType == GoalType.TARGET_TIME &&
      currentBlock == TrainingBlockType.SPECIFIC
    ) {
      targetPace(goal)?.let { goalPace ->
        return range(
          goalPace,
          0.0,
          PacePrescriptionSource.GOAL_PACE,
          GuidanceConfidence.HIGH,
          "This final race-specific session uses the pace implied by the athlete's selected target time.",
        )
      }
    }
    val easy = easyBaseline(profile, goal, profileConfidence)
      ?.let { keepEasyRangeSlowerThanGoal(it, goal) }
      ?: return null
    return when {
      workoutType == WorkoutType.LONG_RUN -> shifted(
        easy,
        config.longRunSlowdownSeconds,
        PacePrescriptionSource.DERIVED_CONSERVATIVE,
        "Long-run pace is derived from the observed easy baseline with a more conservative range.",
      )
      workoutType == WorkoutType.RECOVERY_RUN -> shifted(
        easy,
        config.recoverySlowdownSeconds,
        PacePrescriptionSource.DERIVED_CONSERVATIVE,
        "Recovery pace is deliberately slower than the normal easy range.",
      )
      workoutType == WorkoutType.THRESHOLD || workoutType == WorkoutType.TEMPO_RUN -> qualityFromEasy(
        easy,
        config.thresholdEasyPaceFraction,
        "Controlled quality pace is conservatively derived from the athlete's easy-running evidence.",
      )
      workoutType == WorkoutType.INTERVALS -> qualityFromEasy(
        easy,
        config.intervalEasyPaceFraction,
        "Interval pace is derived only because the plan's stronger quality eligibility checks passed.",
      )
      (workoutType == WorkoutType.RACE_SPECIFIC || workoutPurpose == WorkoutPurpose.RACE_SPECIFIC) &&
        goal.goalType == GoalType.TARGET_TIME -> {
        val goalPace = targetPace(goal)
        if (goalPace != null) {
          range(
            maxOf(goalPace + config.controlledGoalPaceOffsetSeconds, easy.representativeSecondsPerKm - 20.0),
            config.controlledGoalPaceWidthSeconds,
            PacePrescriptionSource.DERIVED_CONSERVATIVE,
            minOf(easy.confidence, GuidanceConfidence.MEDIUM),
            "This controlled goal-distance pace is slower than target pace and is intended for sustainable pacing practice.",
          )
        } else easy
      }
      workoutType == WorkoutType.RACE_SPECIFIC || workoutPurpose == WorkoutPurpose.RACE_SPECIFIC -> range(
        easy.representativeSecondsPerKm - 8.0,
        width(profileConfidence),
        PacePrescriptionSource.DERIVED_CONSERVATIVE,
        easy.confidence,
        "This goal-distance run stays near easy-to-steady effort and is not a maximal benchmark.",
      )
      else -> easy
    }
  }

  private fun easyBaseline(
    profile: AthletePerformanceProfile,
    goal: TrainingGoal,
    confidence: ProfileConfidence,
  ): PacePrescription? {
    profile.typicalEasyPace?.let { baseline ->
      val effectiveConfidence = minOf(confidence, baseline.confidence)
      val explanation = if (effectiveConfidence <= ProfileConfidence.LOW || baseline.contributingRuns < 3) {
        "Pace guidance is broad because limited comparable easy runs are available; use RPE as the guardrail."
      } else {
        "Pace guidance comes from comparable completed easy runs in the Athlete Performance Profile."
      }
      return range(
        baseline.representative,
        width(effectiveConfidence),
        PacePrescriptionSource.OBSERVED_EASY_BASELINE,
        effectiveConfidence.toGuidanceConfidence(),
        explanation,
      )
    }
    profile.performanceBenchmarks.asSequence()
      .flatMap { listOfNotNull(it.mostRecent, it.personalBest).asSequence() }
      .maxByOrNull { it.date }
      ?.let { benchmark ->
        return range(
          benchmark.paceSecondsPerKm * config.benchmarkEasyFactor,
          width(ProfileConfidence.LOW),
          PacePrescriptionSource.VERIFIED_BENCHMARK,
          GuidanceConfidence.LOW,
          "Easy pace is conservatively derived from a verified performance because no supported easy baseline exists.",
        )
      }
    if (profile.distanceLast28DaysMeters > 0.0 && profile.durationLast28DaysSeconds > 0L) {
      val observed = profile.durationLast28DaysSeconds / (profile.distanceLast28DaysMeters / 1_000.0)
      return range(
        observed * config.recentPerformanceEasyFactor,
        width(ProfileConfidence.VERY_LOW),
        PacePrescriptionSource.RECENT_PERFORMANCE,
        GuidanceConfidence.LOW,
        "Pace guidance is a broad conservative estimate from recent aggregate running because no easy baseline exists.",
      )
    }
    return goal.startingPerformanceSnapshot.typicalEasyPaceSecondsPerKm?.let { captured ->
      range(
        captured,
        width(ProfileConfidence.VERY_LOW),
        PacePrescriptionSource.DERIVED_CONSERVATIVE,
        GuidanceConfidence.LOW,
        "Pace guidance uses the easy pace captured when the goal was created and remains deliberately broad.",
      )
    }
  }

  private fun targetPace(goal: TrainingGoal): Double? = goal.targetTimeSeconds?.let {
    targetPaceSecondsPerKm(it, goal.distanceMeters)
  }

  private fun keepEasyRangeSlowerThanGoal(
    easy: PacePrescription,
    goal: TrainingGoal,
  ): PacePrescription {
    if (goal.goalType != GoalType.TARGET_TIME) return easy
    val goalPace = targetPace(goal) ?: return easy
    if (easy.minSecondsPerKm > goalPace) return easy
    val halfWidth = (easy.maxSecondsPerKm - easy.minSecondsPerKm) / 2.0
    return range(
      representative = goalPace + 20.0 + halfWidth,
      halfWidth = halfWidth,
      source = easy.source,
      confidence = easy.confidence,
      explanation = easy.explanation + " The easy range stays slower than the selected race pace.",
    )
  }

  private fun qualityFromEasy(
    easy: PacePrescription,
    factor: Double,
    explanation: String,
  ): PacePrescription = range(
    easy.representativeSecondsPerKm * factor,
    config.qualityHalfWidthSeconds,
    PacePrescriptionSource.DERIVED_CONSERVATIVE,
    minOf(easy.confidence, GuidanceConfidence.MEDIUM),
    explanation,
  )

  private fun shifted(
    source: PacePrescription,
    seconds: Double,
    paceSource: PacePrescriptionSource,
    explanation: String,
  ): PacePrescription = range(
    source.representativeSecondsPerKm + seconds,
    (source.maxSecondsPerKm - source.minSecondsPerKm) / 2.0,
    paceSource,
    source.confidence,
    explanation,
  )

  private fun range(
    representative: Double,
    halfWidth: Double,
    source: PacePrescriptionSource,
    confidence: GuidanceConfidence,
    explanation: String,
  ): PacePrescription {
    val center = round(representative).coerceIn(
      config.minimumSanePaceSecondsPerKm + halfWidth,
      config.maximumSanePaceSecondsPerKm - halfWidth,
    )
    return PacePrescription(
      minSecondsPerKm = round(center - halfWidth),
      maxSecondsPerKm = round(center + halfWidth),
      representativeSecondsPerKm = center,
      source = source,
      confidence = confidence,
      explanation = explanation,
    )
  }

  private fun width(confidence: ProfileConfidence): Double = when (confidence) {
    ProfileConfidence.HIGH -> config.highConfidenceHalfWidthSeconds
    ProfileConfidence.MEDIUM -> config.mediumConfidenceHalfWidthSeconds
    ProfileConfidence.LOW -> config.lowConfidenceHalfWidthSeconds
    ProfileConfidence.VERY_LOW -> config.veryLowConfidenceHalfWidthSeconds
  }

  private fun round(value: Double): Double =
    (value / config.roundingSeconds).roundToInt() * config.roundingSeconds.toDouble()
}

private fun ProfileConfidence.toGuidanceConfidence(): GuidanceConfidence = when (this) {
  ProfileConfidence.VERY_LOW, ProfileConfidence.LOW -> GuidanceConfidence.LOW
  ProfileConfidence.MEDIUM -> GuidanceConfidence.MEDIUM
  ProfileConfidence.HIGH -> GuidanceConfidence.HIGH
}
