package com.example.openruncoach.domain.workout

import com.example.openruncoach.domain.CardioEffort
import com.example.openruncoach.domain.CompletionStatus
import com.example.openruncoach.domain.MuscleFatigue
import com.example.openruncoach.domain.PainLevel
import com.example.openruncoach.domain.RunFeedback
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CheckInEvaluatorTest {
  private val now = Instant.parse("2026-09-21T06:00:00Z")
  private val evaluator = CheckInEvaluator(Clock.fixed(now, ZoneOffset.UTC))
  private val baseline = simpleDurationWorkout("plan", LocalDate.of(2026, 9, 21), WorkoutType.INTERVALS, 30, 5, 8, now)
  private val volume = RecentTrainingVolume(0, 0, 0.0)

  @Test
  fun significantPainOverridesToRest() {
    val result = evaluator.evaluate(baseline, checkIn(pain = PainLevel.SIGNIFICANT), emptyList(), volume)

    assertEquals(ReadinessStatus.RED, result.readinessStatus)
    assertEquals(WorkoutType.REST, result.adjustedWorkout.type)
    assertTrue(result.reasons.any { it.contains("Significant pain") })
  }

  @Test
  fun moderateAndSevereIllnessOverrideToRest() {
    listOf(IllnessLevel.MODERATE, IllnessLevel.SEVERE).forEach { illness ->
      val result = evaluator.evaluate(baseline, checkIn(illness = illness), emptyList(), volume)

      assertEquals(ReadinessStatus.RED, result.readinessStatus)
      assertEquals(WorkoutType.REST, result.adjustedWorkout.type)
      assertTrue(result.reasons.any { it.contains("illness") })
    }
  }

  @Test
  fun severeSorenessNeverProgressesWorkout() {
    val yellow = evaluator.evaluate(baseline, checkIn(soreness = SorenessLevel.SEVERE), emptyList(), volume)
    val red = evaluator.evaluate(
      baseline,
      checkIn(soreness = SorenessLevel.SEVERE, energy = 1),
      emptyList(),
      volume,
    )

    assertEquals(ReadinessStatus.YELLOW, yellow.readinessStatus)
    assertEquals(WorkoutType.RECOVERY_RUN, yellow.adjustedWorkout.type)
    assertTrue(yellow.adjustedWorkout.durationMinutes <= baseline.durationMinutes)
    assertEquals(ReadinessStatus.RED, red.readinessStatus)
    assertEquals(WorkoutType.RECOVERY_RUN, red.adjustedWorkout.type)
  }

  @Test
  fun abortedPreviousWorkoutWithCurrentPainOverridesToRest() {
    val previousFeedback = RunFeedback(
      "feedback", "run", 8, CardioEffort.HARD, MuscleFatigue.MODERATE,
      PainLevel.MILD, CompletionStatus.ABORTED, null, null, now, now,
    )

    val result = evaluator.evaluate(
      baseline,
      checkIn(pain = PainLevel.MILD),
      emptyList(),
      volume,
      previousFeedback,
    )

    assertEquals(ReadinessStatus.RED, result.readinessStatus)
    assertEquals(WorkoutType.REST, result.adjustedWorkout.type)
    assertTrue(result.reasons.any { it.contains("aborted") })
  }

  @Test
  fun yellowReducesDurationAndIntensityWithoutIncreasingEither() {
    val result = evaluator.evaluate(
      baseline,
      checkIn(energy = 2, sleep = 2),
      emptyList(),
      volume,
    )

    assertEquals(ReadinessStatus.YELLOW, result.readinessStatus)
    assertEquals(WorkoutType.EASY_RUN, result.adjustedWorkout.type)
    assertEquals(24, result.adjustedWorkout.durationMinutes)
    assertTrue(result.adjustedWorkout.durationMinutes <= baseline.durationMinutes)
    assertTrue(result.adjustedWorkout.targetRpeMax <= baseline.targetRpeMax)
    assertTrue(result.adjustments.any { it.contains("Duration reduced") })
  }

  @Test
  fun checkInValidatesEveryOneToFiveField() {
    val invalidValues = listOf(
      checkIn(readiness = 0),
      checkIn(energy = 6),
      checkIn(sleep = 0),
      checkIn(stress = 6),
      checkIn(motivation = 0),
    )

    assertTrue(invalidValues.all { it.validationError() != null })
    assertEquals(null, checkIn().validationError())
  }

  private fun checkIn(
    readiness: Int = 4,
    energy: Int = 4,
    sleep: Int = 4,
    soreness: SorenessLevel = SorenessLevel.NONE,
    pain: PainLevel = PainLevel.NONE,
    stress: Int = 2,
    motivation: Int = 4,
    illness: IllnessLevel = IllnessLevel.NONE,
  ) = PreRunCheckIn(
    LocalDate.of(2026, 9, 21), readiness, energy, sleep, soreness, emptySet(), pain,
    stress, motivation, illness, null,
  )
}
