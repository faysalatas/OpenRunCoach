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

  @Test
  fun stravaCanonicalWithProviderCopyOnlyHasNoAdditionalEnrichment() {
    val stravaTarget = target.copy(canonicalSourceAppId = "com.strava")
    val copy = candidate("strava-copy", start, 3_600, 10_000.0, sourceAppId = "com.strava")

    val identity = matchSamsungIdentity(stravaTarget, listOf(copy))
    val enrichment = matchSamsungEnrichment(stravaTarget, listOf(copy), identityCandidateId = copy.id)

    assertEquals("strava-copy", identity.selected?.id)
    assertEquals(SamsungWorkoutMatchStatus.NOT_FOUND, enrichment.status)
    assertNull(enrichment.selected)
  }

  @Test
  fun stravaCanonicalUsesWatchRecordingForEnrichmentWhileIdentityStaysStrava() {
    val stravaTarget = target.copy(canonicalSourceAppId = "com.strava")
    val copy = candidate("strava-copy", start, 3_600, 10_000.0, sourceAppId = "com.strava")
    val watch = candidate(
      "watch", start.plusSeconds(4), 3_604, 10_020.0,
      deviceType = SamsungSourceDeviceType.WATCH,
      sourceAppId = "com.sec.android.app.shealth",
      sensorRich = true,
    )

    val identity = matchSamsungIdentity(stravaTarget, listOf(copy, watch))
    val enrichment = matchSamsungEnrichment(stravaTarget, listOf(copy, watch), identityCandidateId = identity.selected?.id)

    assertEquals("strava-copy", identity.selected?.id)
    assertEquals("watch", enrichment.selected?.id)
    assertEquals(SamsungWorkoutMatchStatus.MATCHED, enrichment.status)
  }

  @Test
  fun samsungCanonicalWatchRecordingKeepsExistingEnrichmentBehavior() {
    val samsungTarget = target.copy(canonicalSourceAppId = "com.sec.android.app.shealth", preferWatchSource = true)
    val watch = candidate("watch", start, 3_600, 10_000.0, deviceType = SamsungSourceDeviceType.WATCH, sensorRich = true)

    val identity = matchSamsungIdentity(samsungTarget, listOf(watch))
    val enrichment = matchSamsungEnrichment(samsungTarget, listOf(watch), identityCandidateId = watch.id)

    assertEquals("watch", identity.selected?.id)
    assertEquals("watch", enrichment.selected?.id)
    assertEquals(SamsungMatchConfidence.HIGH, enrichment.confidence)
    assertEquals(SamsungPhysicalWorkoutRelationship.EXACT_SESSION, enrichment.relationship)
  }

  @Test
  fun sameTimeWithDifferentDistanceIsRejectedForEnrichment() {
    val far = candidate("far-watch", start, 3_600, 11_000.0, deviceType = SamsungSourceDeviceType.WATCH, sensorRich = true)
    val result = matchSamsungEnrichment(target, listOf(far))

    assertEquals(SamsungWorkoutMatchStatus.NOT_FOUND, result.status)
    assertTrue(result.plausibleCandidates.isEmpty())
  }

  @Test
  fun multipleTrustedWatchCandidatesRemainAmbiguousForEnrichment() {
    val result = matchSamsungEnrichment(target, listOf(
      candidate("watch-a", start, 3_600, 10_000.0, deviceType = SamsungSourceDeviceType.WATCH, sensorRich = true),
      candidate("watch-b", start.plusSeconds(3), 3_603, 10_010.0, deviceType = SamsungSourceDeviceType.WATCH, sensorRich = true),
    ))

    assertEquals(SamsungWorkoutMatchStatus.AMBIGUOUS, result.status)
    assertNull(result.selected)
  }

  @Test
  fun sourceApplicationMismatchIsAllowedForEnrichment() {
    val stravaTarget = target.copy(canonicalSourceAppId = "com.strava")
    val watch = candidate(
      "samsung-watch", start, 3_600, 10_000.0,
      deviceType = SamsungSourceDeviceType.WATCH,
      sourceAppId = "com.sec.android.app.shealth",
      sensorRich = true,
    )

    val result = matchSamsungEnrichment(stravaTarget, listOf(watch))

    assertEquals(SamsungWorkoutMatchStatus.MATCHED, result.status)
    assertEquals("samsung-watch", result.selected?.id)
    assertTrue(result.reasons.any { it.text.contains("Different source application") })
  }

  @Test
  fun fullCanonicalCoverageWithLongerWatchSessionIsAcceptedAsOverlap() {
    val watch = candidate(
      "long-watch", start.minusMillis(200), 4_200, 11_500.0,
      deviceType = SamsungSourceDeviceType.WATCH,
      sensorRich = true,
    )

    val result = matchSamsungEnrichment(target, listOf(watch))

    assertEquals(SamsungWorkoutMatchStatus.MATCHED, result.status)
    assertEquals(SamsungPhysicalWorkoutRelationship.OVERLAPPING_SESSION, result.relationship)
    assertEquals(1.0, result.canonicalCoverage!!, 0.001)
  }

  @Test
  fun watchEndingBeforeHalfOfCanonicalActivityIsRejected() {
    val watch = candidate(
      "short-watch", start, 1_700, 4_800.0,
      deviceType = SamsungSourceDeviceType.WATCH,
      sensorRich = true,
    )

    val result = matchSamsungEnrichment(target, listOf(watch))

    assertEquals(SamsungWorkoutMatchStatus.NOT_FOUND, result.status)
  }

  @Test
  fun differentExerciseTypeCannotOverlapEnrich() {
    val watch = candidate(
      "bike-watch", start, 4_200, 10_000.0,
      kind = SamsungExerciseKind.OTHER,
      deviceType = SamsungSourceDeviceType.WATCH,
      sensorRich = true,
    )

    assertEquals(SamsungWorkoutMatchStatus.NOT_FOUND, matchSamsungEnrichment(target, listOf(watch)).status)
  }

  @Test
  fun barelyOverlappingSessionIsRejected() {
    val watch = candidate(
      "bare-overlap", target.endTime.minusSeconds(10), 600, 500.0,
      deviceType = SamsungSourceDeviceType.WATCH,
      sensorRich = true,
    )

    assertEquals(SamsungWorkoutMatchStatus.NOT_FOUND, matchSamsungEnrichment(target, listOf(watch)).status)
  }

  @Test
  fun watchSourceWithDifferentPhysicalTimingIsRejected() {
    val watch = candidate(
      "different-time", start.plusSeconds(300), 3_600, 10_000.0,
      deviceType = SamsungSourceDeviceType.WATCH,
      sensorRich = true,
    )

    assertEquals(SamsungWorkoutMatchStatus.NOT_FOUND, matchSamsungEnrichment(target, listOf(watch)).status)
  }

  @Test
  fun twoOverlappingWatchSessionsAreAmbiguous() {
    val result = matchSamsungEnrichment(target, listOf(
      candidate("long-watch-a", start, 4_200, 11_000.0, deviceType = SamsungSourceDeviceType.WATCH, sensorRich = true),
      candidate("long-watch-b", start.plusSeconds(1), 4_300, 11_200.0, deviceType = SamsungSourceDeviceType.WATCH, sensorRich = true),
    ))

    assertEquals(SamsungWorkoutMatchStatus.AMBIGUOUS, result.status)
    assertEquals(SamsungPhysicalWorkoutRelationship.AMBIGUOUS, result.relationship)
  }

  @Test
  fun realWorldStravaAndGalaxyWatchFixtureUsesOverlappingRelationship() {
    val canonicalStart = Instant.parse("2026-09-23T15:55:30Z")
    val canonical = SamsungWorkoutTarget(
      startTime = canonicalStart,
      endTime = Instant.parse("2026-09-23T16:13:42Z"),
      durationSeconds = 1_092,
      distanceMeters = 2_400.0,
      canonicalSourceAppId = "com.strava",
    )
    val watchStart = Instant.parse("2026-09-23T15:55:29.837Z")
    val watch = candidate(
      "galaxy-watch8", watchStart, 1_426, 2_791.739,
      deviceType = SamsungSourceDeviceType.WATCH,
      sourceAppId = "com.sec.android.app.shealth",
      sensorRich = true,
    ).copy(
      endTime = Instant.parse("2026-09-23T16:19:15.940Z"),
      source = candidate(
        "galaxy-watch8", watchStart, 1_426, 2_791.739,
        deviceType = SamsungSourceDeviceType.WATCH,
        sourceAppId = "com.sec.android.app.shealth",
      ).source.copy(manufacturer = "Samsung", model = "SM-L330", deviceName = "Galaxy Watch8"),
      metrics = SamsungCandidateMetrics(
        averageHeartRate = 141.0,
        maxHeartRate = 168.0,
        averageSpeedMetersPerSecond = 1.9575998,
        vo2Max = 39.7,
        meanCadence = 133.66496,
        maxCadence = 172.173,
        exerciseLogExists = true,
        totalLogSamples = 2_898,
        heartRateSampleCount = 1_418,
        speedSampleCount = 1_418,
        cadenceSampleCount = 1_418,
        elevationAvailable = true,
      ),
    )

    val result = matchSamsungEnrichment(canonical, listOf(watch))

    assertEquals(SamsungWorkoutMatchStatus.MATCHED, result.status)
    assertEquals(SamsungPhysicalWorkoutRelationship.OVERLAPPING_SESSION, result.relationship)
    assertEquals(1.0, result.canonicalCoverage!!, 0.001)
    assertEquals("galaxy-watch8", result.selected?.id)
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
    sensorRich: Boolean = false,
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
      averageHeartRate = averageHeartRate ?: if (sensorRich) 150.0 else null,
      maxHeartRate = maxHeartRate,
      averageSpeedMetersPerSecond = if (sensorRich) 2.8 else null,
      exerciseLogExists = sensorRich,
      totalLogSamples = if (sensorRich) 100 else 0,
      heartRateSampleCount = if (sensorRich) 100 else 0,
      speedSampleCount = if (sensorRich) 100 else 0,
      cadenceSampleCount = if (sensorRich) 100 else 0,
      elevationAvailable = sensorRich,
    ),
  )
}
