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
    assertTrue(selections.single { it.runId == "strava" }.reason.startsWith("Preferred workout source"))
    assertFalse(selections.single { it.runId == "samsung" }.isCanonical)
  }

  @Test
  fun watchRecordWinsBeforeCompleteness() {
    val watch = run("watch", "watch.app", heartRate = null, device = SourceDeviceType.WATCH)
    val completeCopy = run("copy", "sync.app", heartRate = 150.0, device = SourceDeviceType.OTHER)

    val canonical = selectCanonicalRuns(listOf(watch, completeCopy), null).single(CanonicalRunSelection::isCanonical)

    assertEquals("watch", canonical.runId)
    assertTrue(canonical.reason.startsWith("Recorded by a watch"))
  }

  @Test
  fun moreCompleteRecordWins() {
    val sparse = run("sparse", "a.package", heartRate = null, device = null)
    val complete = run("complete", "z.package", heartRate = 150.0, device = SourceDeviceType.OTHER)

    val canonical = selectCanonicalRuns(listOf(sparse, complete), null).single(CanonicalRunSelection::isCanonical)

    assertEquals("complete", canonical.runId)
    assertTrue(canonical.reason.startsWith("Most complete workout data"))
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
    assertTrue(canonical.reason.startsWith("Deterministic source and record ordering"))
  }

  @Test
  fun nonDuplicateRunsRemainCanonicalWithoutAGroup() {
    val first = run("first", "a.package")
    val later = run("later", "b.package").copy(startTime = Instant.parse("2026-09-20T10:00:00Z"))

    val selections = selectCanonicalRuns(listOf(first, later), null)

    assertEquals(2, selections.size)
    assertTrue(selections.all(CanonicalRunSelection::isCanonical))
    assertTrue(selections.all { it.duplicateGroupId == null })
    assertEquals(setOf("No matching provider representation detected"), selections.map { it.reason }.toSet())
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

  @Test
  fun trimmedCrossProviderActivityIsOnePhysicalRun() {
    val samsung = run("samsung", "samsung").copy(
      endTime = Instant.parse("2026-09-20T08:21:00Z"),
      durationSeconds = 1260,
      distanceMeters = 3_780.0,
      physicalRunId = "physical-existing",
    )
    val strava = run("strava", "strava").copy(
      endTime = Instant.parse("2026-09-20T08:18:00Z"),
      durationSeconds = 1080,
      distanceMeters = 3_600.0,
    )

    val selections = selectCanonicalRuns(listOf(samsung, strava), null)

    assertEquals(1, selections.count(CanonicalRunSelection::isCanonical))
    assertTrue(selections.all { it.matchDecision == PhysicalRunMatchDecision.HIGH })
    assertEquals(1, selections.map(CanonicalRunSelection::physicalRunId).distinct().size)
  }

  @Test
  fun existingPhysicalIdentitySurvivesProviderAdditionAndPreferenceChange() {
    val samsung = run("samsung", "samsung").copy(physicalRunId = "physical-1")
    val strava = run("strava", "strava").copy(physicalRunId = "physical-1")
    val added = run("added", "other").copy(physicalRunId = "added")

    val selections = selectCanonicalRuns(listOf(samsung, strava, added), "other")

    assertTrue(selections.all { it.physicalRunId == "physical-1" })
  }

  @Test
  fun realWorldSameDayPatternGroupsCopiesWithoutMergingSeparateActivities() {
    fun timed(id: String, source: String, start: String, duration: Long, distance: Double) =
      run(id, source).copy(
        startTime = Instant.parse(start),
        endTime = Instant.parse(start).plusSeconds(duration),
        durationSeconds = duration,
        distanceMeters = distance,
      )
    val selections = selectCanonicalRuns(listOf(
      timed("samsung-abort", "samsung", "2026-09-29T10:59:00Z", 60, 90.0),
      timed("strava-main", "strava", "2026-09-29T11:01:00Z", 1_080, 3_600.0),
      timed("samsung-main", "samsung", "2026-09-29T11:01:00Z", 1_260, 3_780.0),
      timed("strava-later", "strava", "2026-09-29T11:30:00Z", 120, 280.0),
      timed("samsung-later", "samsung", "2026-09-29T11:30:00Z", 120, 285.0),
    ), null)

    assertEquals(3, selections.count(CanonicalRunSelection::isCanonical))
    assertEquals(
      selections.single { it.runId == "strava-main" }.physicalRunId,
      selections.single { it.runId == "samsung-main" }.physicalRunId,
    )
    assertEquals(
      selections.single { it.runId == "strava-later" }.physicalRunId,
      selections.single { it.runId == "samsung-later" }.physicalRunId,
    )
    assertTrue(selections.single { it.runId == "samsung-abort" }.isCanonical)
  }

  @Test
  fun latestStronglyMatchedSameProviderEditBecomesCanonicalWithoutChangingIdentity() {
    val original = run("original", "provider").copy(
      physicalRunId = "physical-1",
      sourceRecordLastModifiedTime = Instant.parse("2026-09-20T09:00:00Z"),
    )
    val trimmed = run("trimmed", "provider").copy(
      endTime = Instant.parse("2026-09-20T08:27:00Z"),
      durationSeconds = 1_620,
      distanceMeters = 4_600.0,
      sourceRecordLastModifiedTime = Instant.parse("2026-09-20T10:00:00Z"),
    )

    val selections = selectCanonicalRuns(listOf(original, trimmed), null)

    assertTrue(selections.single { it.runId == "trimmed" }.isCanonical)
    assertEquals("physical-1", selections.single { it.runId == "trimmed" }.physicalRunId)
    assertTrue(selections.single { it.runId == "trimmed" }.reason.startsWith("Latest provider edit"))
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
