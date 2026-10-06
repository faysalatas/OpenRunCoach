package com.example.openruncoach.domain.samsunghealth

import com.example.openruncoach.domain.metrics.MetricConfidence
import com.example.openruncoach.domain.metrics.MetricSource
import com.example.openruncoach.domain.RunSummary
import com.example.openruncoach.domain.selectCanonicalRuns
import com.example.openruncoach.domain.route.RunRoute
import com.example.openruncoach.domain.route.RunRoutePoint
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SamsungRouteSelectionTest {
  private val start = Instant.parse("2026-09-23T15:55:30Z")
  private val end = start.plusSeconds(1_092)

  @Test
  fun nonSamsungCanonicalSummaryCanUseMatchedSamsungRoute() {
    val route = overlappingInspection(route()).preferredRoute("strava-canonical", start, end)

    assertEquals("strava-canonical", route?.runId)
    assertEquals(MetricSource.SAMSUNG_HEALTH, route?.source)
    assertEquals(MetricConfidence.HIGH, route?.confidence)
  }

  @Test
  fun preferredSummarySourceDoesNotChangePhysicalRoute() {
    val strava = summary("strava", "com.strava")
    val samsung = summary("samsung", "com.sec.android.app.shealth")
    val watch = watchSession()
    for (preference in listOf("com.strava", "com.sec.android.app.shealth", "com.strava")) {
      val selections = selectCanonicalRuns(listOf(strava, samsung), preference)
      val selection = selections.single { it.isCanonical }
      val canonical = listOf(strava, samsung).single { it.healthConnectRecordId == selection.runId }
      val inspected = matchedInspection(target(canonical), listOf(watch))
      val selected = requireNotNull(inspected.preferredRoute(selection.physicalRunId, start, end))

      assertEquals(preference, canonical.sourcePackageName)
      assertEquals("physical-activity", selected.runId)
      assertEquals(route().points, selected.points)
      assertEquals(MetricSource.SAMSUNG_HEALTH, selected.source)
      assertEquals(1, selections.count { it.isCanonical })
    }
  }

  @Test
  fun samsungCanonicalSummaryKeepsExactIdentityRoute() {
    val inspection = inspection(
      identityMatch = identityMatch(SamsungWorkoutMatchStatus.MATCHED, SamsungMatchConfidence.HIGH),
      identitySession = session(route()),
      enrichmentMatch = enrichmentMatch(SamsungWorkoutMatchStatus.NOT_FOUND),
      enrichmentSession = null,
    )

    val selected = inspection.preferredRoute("samsung-canonical", start, end)

    assertEquals("samsung-canonical", selected?.runId)
    assertEquals(3, selected?.points?.size)
  }

  @Test
  fun overlappingRouteIsClippedToTrimmedCanonicalWindow() {
    val raw = watchSession()
    val longerWatch = raw.copy(
      candidate = raw.candidate.copy(
        startTime = start.minusMillis(200), endTime = end.plusSeconds(334),
        durationSeconds = 1_426, distanceMeters = 2_791.739,
        metrics = SamsungCandidateMetrics(routePointCount = 5, exerciseLogExists = true, totalLogSamples = 100),
      ),
      route = route(includeOutsidePoints = true),
    )
    val inspected = matchedInspection(target(summary("strava", "com.strava")), listOf(longerWatch))
    assertEquals(SamsungMatchConfidence.HIGH, inspected.enrichmentMatch.confidence)
    assertEquals(SamsungPhysicalWorkoutRelationship.OVERLAPPING_SESSION, inspected.enrichmentMatch.relationship)
    val selected = inspected.preferredRoute("physical-activity", start, end)

    assertEquals(listOf(start, start.plusSeconds(500), end), selected?.points?.map { it.timestamp })
    assertEquals(listOf(0, 1, 2), selected?.points?.map { it.sequence })
    assertEquals(2, selected?.rejectedPointCount)
  }

  @Test
  fun ambiguousSamsungCandidateCannotLendRoute() {
    val inspection = inspection(
      identityMatch = identityMatch(SamsungWorkoutMatchStatus.NOT_FOUND, SamsungMatchConfidence.LOW),
      identitySession = null,
      enrichmentMatch = enrichmentMatch(
        SamsungWorkoutMatchStatus.AMBIGUOUS,
        SamsungMatchConfidence.AMBIGUOUS,
        SamsungPhysicalWorkoutRelationship.AMBIGUOUS,
      ),
      enrichmentSession = session(route()),
    )

    assertNull(inspection.preferredRoute("canonical", start, end))
  }

  @Test
  fun merelyNearbySamsungRunCannotCrossAttachRoute() {
    val later = summary("later", "com.strava").copy(
      startTime = start.plusSeconds(7_200), endTime = end.plusSeconds(7_200),
    )
    val inspection = matchedInspection(target(later), listOf(watchSession()))

    assertEquals(SamsungWorkoutMatchStatus.NOT_FOUND, inspection.enrichmentMatch.status)
    assertNull(inspection.preferredRoute("other-run-same-day", later.startTime, later.endTime))
  }

  @Test
  fun twoHighConfidenceWatchRecordingsRemainAmbiguousWithRoutes() {
    val first = watchSession()
    val second = first.copy(candidate = first.candidate.copy(id = "second-watch"))
    val inspection = matchedInspection(target(summary("strava", "com.strava")), listOf(first, second))

    assertEquals(SamsungWorkoutMatchStatus.AMBIGUOUS, inspection.enrichmentMatch.status)
    assertNull(inspection.preferredRoute("physical-activity", start, end))
  }

  @Test
  fun unusableIdentityRouteDoesNotHideUsableMatchedEnrichmentRoute() {
    val inspected = inspection(
      identityMatch = identityMatch(SamsungWorkoutMatchStatus.MATCHED, SamsungMatchConfidence.HIGH),
      identitySession = session(route().copy(points = emptyList())),
      enrichmentMatch = enrichmentMatch(
        SamsungWorkoutMatchStatus.MATCHED, SamsungMatchConfidence.HIGH,
        SamsungPhysicalWorkoutRelationship.EXACT_SESSION,
      ),
      enrichmentSession = session(route()),
    )

    assertEquals(route().points, inspected.preferredRoute("physical-activity", start, end)?.points)
  }

  @Test
  fun bestSensorCandidateWithoutRouteDoesNotHideUniqueHighConfidenceRouteCandidate() {
    val watch = watchSession().let { original ->
      original.copy(
        candidate = original.candidate.copy(metrics = SamsungCandidateMetrics(cadenceSampleCount = 100)),
        route = null,
      )
    }
    val routeSession = watchSession().let { original ->
      original.copy(candidate = original.candidate.copy(
        id = "route-session", source = original.candidate.source.copy(deviceType = SamsungSourceDeviceType.MOBILE),
      ))
    }
    val inspected = matchedInspection(target(summary("strava", "com.strava")), listOf(watch, routeSession))

    assertEquals(watch.candidate.id, inspected.enrichmentMatch.selected?.id)
    assertEquals(route().points, inspected.preferredRoute("physical-activity", start, end)?.points)
  }

  @Test
  fun clippingThatLeavesNoUsableRouteReturnsNoRoute() {
    val outside = route().copy(points = listOf(
      point(0, start.minusSeconds(20)),
      point(1, end.plusSeconds(20)),
    ))

    assertNull(overlappingInspection(outside).preferredRoute("canonical", start, end))
  }

  @Test
  fun selectedRouteKeepsSamsungProvenanceUnderCanonicalOwner() {
    val selected = overlappingInspection(route()).preferredRoute("canonical-physical-run", start, end)

    assertEquals("canonical-physical-run", selected?.runId)
    assertEquals(MetricSource.SAMSUNG_HEALTH, selected?.source)
    assertEquals("Samsung Health", selected?.sourceAppName)
    assertTrue(selected!!.points.all { it.timestamp in start..end })
  }

  private fun overlappingInspection(route: RunRoute) = inspection(
    identityMatch = identityMatch(SamsungWorkoutMatchStatus.NOT_FOUND, SamsungMatchConfidence.LOW),
    identitySession = null,
    enrichmentMatch = enrichmentMatch(
      SamsungWorkoutMatchStatus.MATCHED,
      SamsungMatchConfidence.HIGH,
      SamsungPhysicalWorkoutRelationship.OVERLAPPING_SESSION,
    ),
    enrichmentSession = session(route),
  )

  private fun summary(id: String, source: String) = RunSummary(
    healthConnectRecordId = id,
    startTime = start,
    endTime = end,
    durationSeconds = 1_092,
    distanceMeters = 2_400.0,
    averageHeartRate = null,
    maxHeartRate = null,
    averagePaceSecondsPerKm = 455.0,
    sourcePackageName = source,
    physicalRunId = "physical-activity",
  )

  private fun target(run: RunSummary) = SamsungWorkoutTarget(
    startTime = run.startTime,
    endTime = run.endTime,
    durationSeconds = run.durationSeconds,
    distanceMeters = run.distanceMeters,
    canonicalSourceAppId = run.sourcePackageName,
  )

  private fun watchSession(): SamsungExerciseSessionInspection {
    val raw = session(route())
    return raw.copy(candidate = raw.candidate.copy(
      startTime = start, endTime = end, durationSeconds = 1_092, distanceMeters = 2_400.0,
      source = raw.candidate.source.copy(
        applicationId = "com.sec.android.app.shealth", deviceType = SamsungSourceDeviceType.WATCH,
      ),
      metrics = SamsungCandidateMetrics(routePointCount = 3),
    ))
  }

  private fun matchedInspection(
    target: SamsungWorkoutTarget,
    sessions: List<SamsungExerciseSessionInspection>,
  ): SamsungHealthInspection {
    val candidates = sessions.map { it.candidate }
    val identity = matchSamsungIdentity(target, candidates)
    val enrichment = matchSamsungEnrichment(target, candidates, identityCandidateId = identity.selected?.id)
    return inspection(
      identityMatch = identity,
      identitySession = sessions.singleOrNull { it.candidate.id == identity.selected?.id },
      enrichmentMatch = enrichment,
      enrichmentSession = sessions.singleOrNull { it.candidate.id == enrichment.selected?.id },
    ).copy(plausibleSessions = sessions)
  }

  private fun inspection(
    identityMatch: SamsungWorkoutMatchResult,
    identitySession: SamsungExerciseSessionInspection?,
    enrichmentMatch: SamsungEnrichmentMatchResult,
    enrichmentSession: SamsungExerciseSessionInspection?,
  ) = SamsungHealthInspection(
    match = identityMatch,
    session = identitySession,
    providerMetrics = emptyList(),
    sharedDifferences = emptyList(),
    identityMatch = identityMatch,
    identitySession = identitySession,
    enrichmentMatch = enrichmentMatch,
    enrichmentSession = enrichmentSession,
  )

  private fun identityMatch(status: SamsungWorkoutMatchStatus, confidence: SamsungMatchConfidence) =
    SamsungWorkoutMatchResult(status = status, confidence = confidence)

  private fun enrichmentMatch(
    status: SamsungWorkoutMatchStatus,
    confidence: SamsungMatchConfidence = SamsungMatchConfidence.LOW,
    relationship: SamsungPhysicalWorkoutRelationship = SamsungPhysicalWorkoutRelationship.NO_MATCH,
  ) = SamsungEnrichmentMatchResult(
    status = status,
    confidence = confidence,
    relationship = relationship,
  )

  private fun session(route: RunRoute) = SamsungExerciseSessionInspection(
    candidate = SamsungExerciseCandidate(
      id = "watch-session",
      startTime = start.minusSeconds(20),
      endTime = end.plusSeconds(20),
      durationSeconds = 1_132,
      distanceMeters = 2_500.0,
      exerciseKind = SamsungExerciseKind.RUNNING,
    ),
    caloriesKilocalories = null,
    meanHeartRate = null,
    minHeartRate = null,
    maxHeartRate = null,
    meanSpeedMetersPerSecond = null,
    maxSpeedMetersPerSecond = null,
    meanCadence = null,
    maxCadence = null,
    meanPowerWatts = null,
    maxPowerWatts = null,
    vo2Max = null,
    altitudeGainMeters = null,
    altitudeLossMeters = null,
    minAltitudeMeters = null,
    maxAltitudeMeters = null,
    inclineDistanceMeters = null,
    declineDistanceMeters = null,
    customTitle = null,
    comment = null,
    sourceAppId = "com.sec.android.app.shealth",
    sourceDeviceId = "watch",
    sourceDeviceManufacturer = "Samsung",
    sourceDeviceModel = "SM-L330",
    sourceDeviceName = "Galaxy Watch8",
    cadence = CadenceInvestigation(
      sessionMeanCadence = null,
      sessionMaxCadence = null,
      logExists = false,
      totalLogSamples = 0,
      cadenceSampleCount = 0,
      averageCadence = null,
      minimumCadence = null,
      maximumCadence = null,
      firstCadenceTimestamp = null,
      lastCadenceTimestamp = null,
      averageSampleIntervalSeconds = null,
      heartRateAlignment = SampleAlignment(SampleAlignmentStatus.NO_COMPARISON_DATA, 0, 0),
      speedAlignment = SampleAlignment(SampleAlignmentStatus.NO_COMPARISON_DATA, 0, 0),
    ),
    heartRateLogSummary = null,
    speedLogSummary = null,
    powerLogSummary = null,
    countLogSummary = null,
    route = route,
  )

  private fun route(includeOutsidePoints: Boolean = false): RunRoute {
    val inside = listOf(point(0, start), point(1, start.plusSeconds(500)), point(2, end))
    val points = if (includeOutsidePoints) {
      listOf(point(0, start.minusSeconds(20))) + inside + point(4, end.plusSeconds(20))
    } else inside
    return RunRoute(
      runId = "samsung-provider-record",
      source = MetricSource.SAMSUNG_HEALTH,
      sourceAppName = "Samsung Health",
      confidence = MetricConfidence.LOW,
      points = points,
    )
  }

  private fun point(sequence: Int, timestamp: Instant) = RunRoutePoint(
    sequence = sequence,
    timestamp = timestamp,
    latitude = 52.5 + sequence * 0.001,
    longitude = 13.4 + sequence * 0.001,
  )
}
