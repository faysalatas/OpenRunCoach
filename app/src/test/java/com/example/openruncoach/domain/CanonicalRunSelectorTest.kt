package com.example.openruncoach.domain

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CanonicalRunSelectorTest {
  @Test
  fun preferredSourceWins() {
    val completeSamsung = run("samsung", "com.sec.android.app.shealth", heartRate = 150.0, device = SourceDeviceType.WATCH)
    val preferredStrava = run("strava", "com.strava", heartRate = null, device = null)

    val selections = selectCanonicalRuns(listOf(completeSamsung, preferredStrava), "com.strava")

    assertTrue(selections.single { it.runId == "strava" }.isCanonical)
    assertEquals("Preferred workout source", selections.single { it.runId == "strava" }.reason)
    assertFalse(selections.single { it.runId == "samsung" }.isCanonical)
  }

  @Test
  fun watchRecordWinsBeforeCompleteness() {
    val watch = run("watch", "watch.app", heartRate = null, device = SourceDeviceType.WATCH)
    val completeCopy = run("copy", "sync.app", heartRate = 150.0, device = SourceDeviceType.OTHER)

    val canonical = selectCanonicalRuns(listOf(watch, completeCopy), null).single(CanonicalRunSelection::isCanonical)

    assertEquals("watch", canonical.runId)
    assertEquals("Recorded by a watch", canonical.reason)
  }

  @Test
  fun moreCompleteRecordWins() {
    val sparse = run("sparse", "a.package", heartRate = null, device = null)
    val complete = run("complete", "z.package", heartRate = 150.0, device = SourceDeviceType.OTHER)

    val canonical = selectCanonicalRuns(listOf(sparse, complete), null).single(CanonicalRunSelection::isCanonical)

    assertEquals("complete", canonical.runId)
    assertEquals("Most complete workout data", canonical.reason)
  }

  @Test
  fun deterministicFallbackUsesPackageThenRecordId() {
    val laterPackage = run("record-0", "z.package")
    val earlierPackageLaterId = run("record-z", "a.package")
    val earlierPackageEarlierId = run("record-a", "a.package")

    val canonical = selectCanonicalRuns(
      listOf(laterPackage, earlierPackageLaterId, earlierPackageEarlierId), null,
    ).single(CanonicalRunSelection::isCanonical)

    assertEquals("record-a", canonical.runId)
    assertEquals("Deterministic source and record ordering", canonical.reason)
  }

  @Test
  fun nonDuplicateRunsRemainCanonicalWithoutAGroup() {
    val first = run("first", "a.package")
    val later = run("later", "b.package").copy(startTime = Instant.parse("2026-09-20T10:00:00Z"))

    val selections = selectCanonicalRuns(listOf(first, later), null)

    assertEquals(2, selections.size)
    assertTrue(selections.all(CanonicalRunSelection::isCanonical))
    assertTrue(selections.all { it.duplicateGroupId == null })
    assertEquals(setOf("No duplicate detected"), selections.map { it.reason }.toSet())
  }

  @Test
  fun duplicateGroupIsStableAndShared() {
    val selections = selectCanonicalRuns(listOf(run("b", "b.app"), run("a", "a.app")), null)
    val reversed = selectCanonicalRuns(listOf(run("a", "a.app"), run("b", "b.app")), null)

    assertEquals(1, selections.mapNotNull(CanonicalRunSelection::duplicateGroupId).distinct().size)
    assertEquals(
      selections.associate { it.runId to it.duplicateGroupId },
      reversed.associate { it.runId to it.duplicateGroupId },
    )
  }

  private fun run(
    id: String,
    packageName: String,
    heartRate: Double? = null,
    device: SourceDeviceType? = null,
  ) = RunSummary(
    healthConnectRecordId = id,
    startTime = Instant.parse("2026-09-20T08:00:00Z"),
    endTime = Instant.parse("2026-09-20T08:30:00Z"),
    durationSeconds = 1800,
    distanceMeters = 5000.0,
    averageHeartRate = heartRate,
    maxHeartRate = null,
    averagePaceSecondsPerKm = 360.0,
    sourcePackageName = packageName,
    sourceDeviceType = device,
  )
}
