package com.example.openruncoach.domain

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RunDuplicateDiagnosticsTest {
  @Test
  fun matchingRunsAreMarkedOnBothSidesWithReasons() {
    val first = run("first", 0, 2400, 5000.0, "com.sec.android.app.shealth", "Samsung Health")
    val second = run("second", 120, 2519, 5249.0, "com.example.other", "Other App")

    val diagnostics = findPossibleDuplicates(listOf(first, second))

    val firstMatch = diagnostics.getValue("first").single()
    assertEquals("second", firstMatch.otherRunId)
    assertEquals("Other App", firstMatch.otherSourceName)
    assertEquals(120, firstMatch.startDifferenceSeconds)
    assertEquals(119, firstMatch.durationDifferenceSeconds)
    assertTrue(firstMatch.distanceDifferencePercent < 5.0)
    assertTrue(firstMatch.differentSourceApplications)
    assertEquals("first", diagnostics.getValue("second").single().otherRunId)
  }

  @Test
  fun insufficientOverlapOrMissingEvidenceIsNeverSilentlyHighConfidence() {
    val base = run("base", 0, 2400, 5000.0)
    val outsideStart = run("late", 121, 2400, 5000.0)
    val outsideDuration = run("long", 0, 2520, 5000.0)
    val outsideDistance = run("far", 0, 2400, 6000.0)
    val missingDistance = run("missing", 0, 2400, null)
    val zeroDistance = run("zero", 0, 2400, 0.0)

    listOf(outsideStart, outsideDistance, missingDistance, zeroDistance).forEach {
      assertTrue(assessPhysicalRunMatch(base, it).decision != PhysicalRunMatchDecision.HIGH)
    }
    assertEquals(PhysicalRunMatchDecision.HIGH, assessPhysicalRunMatch(base, outsideDuration).decision)
  }

  @Test
  fun sameSourceCanStillBeADuplicateButSameRecordCannot() {
    val first = run("first", 0, 2400, 5000.0)
    val second = run("second", 30, 2410, 5005.0)

    assertFalse(findPossibleDuplicates(listOf(first, second)).getValue("first").single().differentSourceApplications)
    assertTrue(findPossibleDuplicates(listOf(first, first.copy(startTime = first.startTime.plusSeconds(30)))).isEmpty())
  }

  @Test
  fun separateSameDayRunsDoNotMatch() {
    val first = run("first", 0, 1_080, 3_600.0, "strava", "Strava")
    val second = run("second", 1_740, 120, 300.0, "samsung", "Samsung Health")

    assertEquals(PhysicalRunMatchDecision.SEPARATE, assessPhysicalRunMatch(first, second).decision)
  }

  @Test
  fun partialOverlapWithoutDistanceEvidenceIsAmbiguous() {
    val first = run("first", 0, 1_200, null, "strava", "Strava")
    val second = run("second", 60, 1_200, null, "samsung", "Samsung Health")

    assertEquals(PhysicalRunMatchDecision.AMBIGUOUS, assessPhysicalRunMatch(first, second).decision)
  }

  private fun run(
    id: String,
    startSeconds: Long,
    durationSeconds: Long,
    distanceMeters: Double?,
    packageName: String = "com.sec.android.app.shealth",
    appName: String = "Samsung Health",
  ) = RunSummary(
    healthConnectRecordId = id,
    startTime = Instant.ofEpochSecond(startSeconds),
    endTime = Instant.ofEpochSecond(startSeconds + durationSeconds),
    durationSeconds = durationSeconds,
    distanceMeters = distanceMeters,
    averageHeartRate = null,
    maxHeartRate = null,
    averagePaceSecondsPerKm = null,
    sourcePackageName = packageName,
    sourceAppName = appName,
  )
}
