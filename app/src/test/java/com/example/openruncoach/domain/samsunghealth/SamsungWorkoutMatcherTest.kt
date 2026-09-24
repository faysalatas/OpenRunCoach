package com.example.openruncoach.domain.samsunghealth

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SamsungWorkoutMatcherTest {
  private val start = Instant.parse("2026-09-20T08:00:00Z")
  private val target = SamsungWorkoutTarget(start, start.plusSeconds(3_600), 3_600, 10_000.0)

  @Test
  fun matchingRunningWorkoutIsSelected() {
    val result = matchSamsungWorkout(target, listOf(candidate("run", start.plusSeconds(30), 3_650, 10_200.0)))

    assertEquals(SamsungWorkoutMatchStatus.MATCHED, result.status)
    assertEquals("run", result.selected?.id)
  }

  @Test
  fun trackRunningIsEligibleButOtherExerciseIsIgnored() {
    val result = matchSamsungWorkout(target, listOf(
      candidate("bike", start, 3_600, 10_000.0, SamsungExerciseKind.OTHER),
      candidate("track", start, 3_600, 10_000.0, SamsungExerciseKind.TRACK_RUNNING),
    ))

    assertEquals("track", result.selected?.id)
  }

  @Test
  fun workoutOutsideTimeToleranceIsNotMatched() {
    val result = matchSamsungWorkout(target, listOf(candidate("late", start.plusSeconds(121), 3_600, 10_000.0)))

    assertEquals(SamsungWorkoutMatchStatus.NOT_FOUND, result.status)
    assertNull(result.selected)
  }

  @Test
  fun workoutOutsideDurationToleranceIsNotMatched() {
    val result = matchSamsungWorkout(target, listOf(candidate("long", start, 3_721, 10_000.0)))

    assertEquals(SamsungWorkoutMatchStatus.NOT_FOUND, result.status)
  }

  @Test
  fun workoutOutsideDistanceToleranceIsNotMatched() {
    val result = matchSamsungWorkout(target, listOf(candidate("far", start, 3_600, 10_501.0)))

    assertEquals(SamsungWorkoutMatchStatus.NOT_FOUND, result.status)
  }

  @Test
  fun missingOptionalDistanceDoesNotPreventTimeBasedMatch() {
    val result = matchSamsungWorkout(target, listOf(candidate("no-distance", start, 3_600, null)))

    assertEquals(SamsungWorkoutMatchStatus.MATCHED, result.status)
  }

  @Test
  fun multiplePlausibleMatchesRemainAmbiguousAndAreRanked() {
    val result = matchSamsungWorkout(target, listOf(
      candidate("less-close", start.plusSeconds(7), 3_607, 10_025.0),
      candidate("closest", start.plusSeconds(5), 3_605, 10_020.0),
    ))

    assertEquals(SamsungWorkoutMatchStatus.AMBIGUOUS, result.status)
    assertNull(result.selected)
    assertEquals(listOf("closest", "less-close"), result.plausibleCandidates.map { it.id })
  }

  @Test
  fun watchWinsForWatchRecordedSamsungCanonicalRun() {
    val watchTarget = target.copy(
      canonicalSourceAppId = "com.sec.android.app.shealth",
      preferWatchSource = true,
    )
    val result = matchSamsungWorkout(watchTarget, listOf(
      candidate("mobile", start, 3_600, 10_000.0, deviceType = SamsungSourceDeviceType.MOBILE),
      candidate("watch", start, 3_600, 10_000.0, deviceType = SamsungSourceDeviceType.WATCH),
    ))

    assertEquals(SamsungWorkoutMatchStatus.MATCHED, result.status)
    assertEquals("watch", result.selected?.id)
    assertEquals(SamsungMatchConfidence.HIGH, result.confidence)
  }

  @Test
  fun watchIsNotGloballyPreferredWhenCanonicalSourceDoesNotSupportThatInference() {
    val result = matchSamsungWorkout(target, listOf(
      candidate("mobile", start, 3_600, 10_000.0, deviceType = SamsungSourceDeviceType.MOBILE),
      candidate("watch", start, 3_600, 10_000.0, deviceType = SamsungSourceDeviceType.WATCH),
    ))

    assertEquals(SamsungWorkoutMatchStatus.AMBIGUOUS, result.status)
    assertNull(result.selected)
  }

  @Test
  fun matchingCanonicalSourceApplicationWins() {
    val sourceTarget = target.copy(canonicalSourceAppId = "com.sec.android.app.shealth")
    val result = matchSamsungWorkout(sourceTarget, listOf(
      candidate("other-app", start, 3_600, 10_000.0, sourceAppId = "example.importer"),
      candidate("samsung", start, 3_600, 10_000.0),
    ))

    assertEquals(SamsungWorkoutMatchStatus.MATCHED, result.status)
    assertEquals("samsung", result.selected?.id)
    assertEquals(SamsungMatchConfidence.HIGH, result.confidence)
  }

  @Test
  fun closeDistanceAloneDoesNotSelectCandidate() {
    val result = matchSamsungWorkout(target, listOf(
      candidate("distance-nearest", start, 3_600, 10_001.0),
      candidate("distance-close", start, 3_600, 10_100.0),
    ))

    assertEquals(SamsungWorkoutMatchStatus.AMBIGUOUS, result.status)
    assertEquals("distance-nearest", result.plausibleCandidates.first().id)
  }

  @Test
  fun substantiallyCloserDurationSelectsCandidate() {
    val result = matchSamsungWorkout(target, listOf(
      candidate("duration-exact", start, 3_600, 10_000.0),
      candidate("duration-off", start, 3_710, 10_000.0),
    ))

    assertEquals(SamsungWorkoutMatchStatus.MATCHED, result.status)
    assertEquals("duration-exact", result.selected?.id)
  }

  @Test
  fun sharedHeartRateSelectsOtherwiseIdenticalCandidate() {
    val heartRateTarget = target.copy(averageHeartRate = 150.0, maxHeartRate = 175.0)
    val result = matchSamsungWorkout(heartRateTarget, listOf(
      candidate("hr-match", start, 3_600, 10_000.0, averageHeartRate = 150.0, maxHeartRate = 175.0),
      candidate("hr-different", start, 3_600, 10_000.0, averageHeartRate = 165.0, maxHeartRate = 190.0),
    ))

    assertEquals(SamsungWorkoutMatchStatus.MATCHED, result.status)
    assertEquals("hr-match", result.selected?.id)
  }

  @Test
  fun deterministicFallbackOrdersTiedCandidatesByIdWithoutSelectingOne() {
    val result = matchSamsungWorkout(target, listOf(
      candidate("z-candidate", start, 3_600, 10_000.0),
      candidate("a-candidate", start, 3_600, 10_000.0),
    ))

    assertEquals(SamsungWorkoutMatchStatus.AMBIGUOUS, result.status)
    assertEquals(listOf("a-candidate", "z-candidate"), result.plausibleCandidates.map { it.id })
  }

  @Test
  fun manualOverrideSelectsPersistedCandidateWithoutChangingInputRuns() {
    val candidates = listOf(
      candidate("first", start, 3_600, 10_000.0),
      candidate("chosen", start, 3_600, 10_000.0),
    )
    val result = matchSamsungWorkout(target, candidates, manualSelectionCandidateId = "chosen")

    assertEquals(SamsungWorkoutMatchStatus.MATCHED, result.status)
    assertEquals("chosen", result.selected?.id)
    assertTrue(result.manualSelection)
    assertEquals(2, result.plausibleCandidates.size)
    assertFalse(result.plausibleCandidates === candidates)
  }

  private fun candidate(
    id: String,
    candidateStart: Instant,
    duration: Long,
    distance: Double?,
    kind: SamsungExerciseKind = SamsungExerciseKind.RUNNING,
    deviceType: SamsungSourceDeviceType = SamsungSourceDeviceType.UNKNOWN,
    sourceAppId: String = "com.sec.android.app.shealth",
    averageHeartRate: Double? = null,
    maxHeartRate: Double? = null,
  ) = SamsungExerciseCandidate(
    id = id,
    startTime = candidateStart,
    endTime = candidateStart.plusSeconds(duration),
    durationSeconds = duration,
    distanceMeters = distance,
    exerciseKind = kind,
    source = SamsungCandidateSource(
      uid = id,
      dataPointStartTime = candidateStart,
      dataPointEndTime = candidateStart.plusSeconds(duration),
      updateTime = null,
      applicationId = sourceAppId,
      deviceId = "device-$id",
      deviceType = deviceType,
    ),
    metrics = SamsungCandidateMetrics(
      averageHeartRate = averageHeartRate,
      maxHeartRate = maxHeartRate,
    ),
  )
}
