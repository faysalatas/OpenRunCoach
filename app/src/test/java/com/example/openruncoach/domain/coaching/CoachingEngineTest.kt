package com.example.openruncoach.domain.coaching

import com.example.openruncoach.domain.CardioEffort
import com.example.openruncoach.domain.CompletionStatus
import com.example.openruncoach.domain.MuscleFatigue
import com.example.openruncoach.domain.PainLevel
import com.example.openruncoach.domain.RunFeedback
import com.example.openruncoach.domain.RunSummary
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CoachingEngineTest {
  private val generatedAt = Instant.parse("2026-09-21T12:00:00Z")
  private val engine = CoachingEngine(Clock.fixed(generatedAt, ZoneOffset.UTC))

  @Test fun progress() = assertAction(
    CoachingAction.PROGRESS, 7,
    feedback(rpe = 2, completion = CompletionStatus.COMPLETED, pain = PainLevel.NONE, muscle = MuscleFatigue.NONE, cardio = CardioEffort.EASY),
  )

  @Test fun smallProgress() = assertAction(
    CoachingAction.SMALL_PROGRESS, 4,
    feedback(rpe = 6, completion = CompletionStatus.COMPLETED, pain = PainLevel.NONE, muscle = MuscleFatigue.MILD, cardio = CardioEffort.APPROPRIATE),
  )

  @Test fun maintain() = assertAction(
    CoachingAction.MAINTAIN, 1,
    feedback(rpe = 6, completion = CompletionStatus.PARTIAL, pain = PainLevel.NONE, muscle = MuscleFatigue.MODERATE, cardio = CardioEffort.APPROPRIATE),
  )

  @Test fun reduce() = assertAction(
    CoachingAction.REDUCE, -3,
    feedback(rpe = 9, completion = CompletionStatus.COMPLETED, pain = PainLevel.MILD, muscle = MuscleFatigue.SEVERE, cardio = CardioEffort.HARD),
  )

  @Test fun recoveryFromScore() = assertAction(
    CoachingAction.RECOVERY, -6,
    feedback(rpe = 10, completion = CompletionStatus.PARTIAL, pain = PainLevel.MILD, muscle = MuscleFatigue.SEVERE, cardio = CardioEffort.LIMIT),
  )

  @Test fun significantPainOverridesScore() {
    assertAction(CoachingAction.RECOVERY, 1, feedback(rpe = 1, pain = PainLevel.SIGNIFICANT))
  }

  @Test fun abortedWorkoutWithPainOverridesScore() {
    assertAction(CoachingAction.RECOVERY, 1, feedback(rpe = 1, completion = CompletionStatus.ABORTED, pain = PainLevel.MILD))
  }

  @Test fun abortedWorkoutWithoutPainOverridesScore() {
    assertAction(CoachingAction.REDUCE, 2, feedback(rpe = 1, completion = CompletionStatus.ABORTED, pain = PainLevel.NONE))
  }

  @Test fun ignoresNonCanonicalLatestRunAndNonCanonicalHistoryVolume() {
    assertNull(engine.analyze(run().copy(isCanonical = false), listOf(run()), feedback()))
    val decision = engine.analyze(
      run(),
      listOf(run(), run("duplicate").copy(isCanonical = false, distanceMeters = 100_000.0)),
      feedback(),
    )
    assertTrue(decision!!.reasons.last().contains("5.0 km across 1 runs"))
    assertEquals(generatedAt, decision.generatedAt)
  }

  @Test fun ignoresAnOlderCanonicalRun() {
    val older = run().copy(startTime = Instant.parse("2026-09-19T08:00:00Z"))
    val newest = run("run-2")

    assertNull(engine.analyze(older, listOf(newest, older), feedback()))
  }

  private fun assertAction(expected: CoachingAction, expectedScore: Int, feedback: RunFeedback) {
    val decision = engine.analyze(run(), listOf(run()), feedback)
    assertEquals(expected, decision?.action)
    assertEquals(expectedScore, decision?.score)
    assertTrue(decision!!.reasons.isNotEmpty())
  }

  private fun run(id: String = "run-1") = RunSummary(
    healthConnectRecordId = id,
    startTime = Instant.parse("2026-09-20T08:00:00Z"),
    endTime = Instant.parse("2026-09-20T08:30:00Z"),
    durationSeconds = 1800,
    distanceMeters = 5000.0,
    averageHeartRate = 140.0,
    maxHeartRate = 160,
    averagePaceSecondsPerKm = 360.0,
  )

  private fun feedback(
    rpe: Int = 4,
    completion: CompletionStatus = CompletionStatus.COMPLETED,
    pain: PainLevel = PainLevel.NONE,
    muscle: MuscleFatigue = MuscleFatigue.MILD,
    cardio: CardioEffort = CardioEffort.APPROPRIATE,
  ) = RunFeedback(
    id = "feedback-1",
    runId = "run-1",
    rpe = rpe,
    cardioEffort = cardio,
    muscleFatigue = muscle,
    painLevel = pain,
    completionStatus = completion,
    readiness = null,
    notes = null,
    createdAt = generatedAt,
    updatedAt = generatedAt,
  )
}
