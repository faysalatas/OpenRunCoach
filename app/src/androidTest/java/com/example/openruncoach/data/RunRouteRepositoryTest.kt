package com.example.openruncoach.data

import androidx.room.Room
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.openruncoach.data.local.OpenRunCoachDatabase
import com.example.openruncoach.data.local.RunEntity
import com.example.openruncoach.domain.metrics.MetricConfidence
import com.example.openruncoach.domain.metrics.MetricSource
import com.example.openruncoach.domain.metrics.MetricValue
import com.example.openruncoach.domain.metrics.RunMetrics
import com.example.openruncoach.domain.route.RunRoute
import com.example.openruncoach.domain.route.RunRoutePoint
import com.example.openruncoach.domain.route.RunSplit
import com.example.openruncoach.domain.route.RunSplitDerivation
import java.time.Instant
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RunRouteRepositoryTest {
  @Test
  fun canonicalRefreshConsolidatesLegacyRoutesUnderStableOwnerWithoutDuplicates() = runBlocking {
    val database = Room.inMemoryDatabaseBuilder(
      ApplicationProvider.getApplicationContext(), OpenRunCoachDatabase::class.java,
    ).build()
    try {
      database.runDao().upsertAll(listOf(
        run("strava", isCanonical = true),
        run("samsung", isCanonical = false),
      ))
      val repository = RunRouteRepository(database.runRouteDao())
      repository.save(route("samsung"), listOf(split("samsung")))
      assertEquals(PHYSICAL_RUN_ID, repository.get("strava")?.route?.runId)

      repository.saveForPhysicalRun(route("samsung"), listOf(split("samsung")), PHYSICAL_RUN_ID)
      repository.saveForPhysicalRun(route("strava"), listOf(split("strava")), PHYSICAL_RUN_ID)

      assertEquals(PHYSICAL_RUN_ID, repository.get("strava")?.route?.runId)
      assertEquals(PHYSICAL_RUN_ID, repository.get("samsung")?.route?.runId)
      assertEquals(1, database.runRouteDao().getAllRoutes().size)
      assertEquals(PHYSICAL_RUN_ID, database.runRouteDao().getAllRoutes().single().runId)
      val saved = requireNotNull(database.runRouteDao().getRoute(PHYSICAL_RUN_ID))
      assertEquals(2, saved.points.size)
      assertEquals(1, saved.splits.size)
      assertEquals(PHYSICAL_RUN_ID, saved.splits.single().runId)
      assertNull(database.runRouteDao().getRoute("samsung"))
    } finally {
      database.close()
    }
  }

  @Test
  fun legacyCustomIdentityUsesStableLookupWithoutSchemaChange() = withDatabase { database ->
    val customId = "legacy-physical-alias"
    database.runDao().upsertAll(listOf(
      run("strava", true).copy(physicalRunId = customId),
      run("samsung", false).copy(physicalRunId = customId),
    ))
    val routes = RunRouteRepository(database.runRouteDao())
    routes.saveForPhysicalRun(route("strava"), emptyList(), customId)

    assertEquals(customId, routes.get("strava")?.route?.runId)
    assertEquals(customId, routes.get(customId)?.route?.runId)
    assertEquals(setOf(customId), routes.observeAll().first().keys)
    assertEquals(1, database.runRouteDao().getAllRoutes().size)
  }

  @Test
  fun sourcePreferenceRoundTripKeepsOneHistoryRunAndOnePhysicalRoute() = withDatabase { database ->
    database.runDao().upsertAll(listOf(run("strava", true), run("samsung", false)))
    val routes = RunRouteRepository(database.runRouteDao())
    routes.saveForPhysicalRun(route("strava"), emptyList(), PHYSICAL_RUN_ID)
    val context = ApplicationProvider.getApplicationContext<Context>()
    val forced = mutableListOf<Boolean>()
    val repository = RunRepository(
      database.runDao(), HealthConnectRepository(context), RunSettingsRepository(context),
      runMetricsRefresher = RunMetricsRefresher { summary, force ->
        forced += force
        routes.saveForPhysicalRun(route(summary.healthConnectRecordId), emptyList(), summary.physicalRunId)
        RunMetricsRefreshOutcome(
          RunMetrics(
            runId = summary.healthConnectRecordId,
            startTime = summary.startTime,
            endTime = summary.endTime,
            durationSeconds = MetricValue(summary.durationSeconds, MetricSource.HEALTH_CONNECT, MetricConfidence.HIGH),
            generatedAt = START,
          ),
          refreshed = true,
        )
      },
    )
    for (source in listOf("com.strava", "com.sec.android.app.shealth", "com.strava")) {
      repository.refreshCanonicalSelection(source)
      val canonical = database.runDao().getCanonicalRuns().single()
      assertEquals(source, canonical.sourcePackageName)
      assertEquals(PHYSICAL_RUN_ID, canonical.physicalRunId)
      assertEquals(route("strava").points, routes.get(canonical.healthConnectRecordId)?.route?.points)
      assertEquals(MetricSource.SAMSUNG_HEALTH, routes.get(canonical.healthConnectRecordId)?.route?.source)
      assertEquals(setOf(PHYSICAL_RUN_ID), routes.observeAll().first().keys)
      assertEquals(1, database.runRouteDao().getAllRoutes().size)
    }
    assertEquals(listOf(false, true, true), forced)
  }

  @Test
  fun preferenceChangeWithFailedRefreshKeepsExistingRoute() = withDatabase { database ->
    database.runDao().upsertAll(listOf(run("strava", true), run("samsung", false)))
    val routes = RunRouteRepository(database.runRouteDao())
    routes.save(route("strava"), emptyList())
    val context = ApplicationProvider.getApplicationContext<Context>()
    val repository = RunRepository(
      database.runDao(), HealthConnectRepository(context), RunSettingsRepository(context),
      runMetricsRefresher = RunMetricsRefresher { _, _ -> error("Samsung temporarily unavailable") },
    )

    repository.refreshCanonicalSelection("com.sec.android.app.shealth")

    assertEquals("samsung", database.runDao().getCanonicalRuns().single().healthConnectRecordId)
    assertEquals(PHYSICAL_RUN_ID, routes.get("samsung")?.route?.runId)
    assertEquals(route("strava").points, routes.observe("samsung").first()?.route?.points)
  }

  @Test
  fun legacyRouteLookupClipsToCurrentCanonicalInterval() = withDatabase { database ->
    database.runDao().upsertAll(listOf(run("strava", true)))
    val routes = RunRouteRepository(database.runRouteDao())
    val original = route("strava").copy(points = listOf(
      RunRoutePoint(0, START.minusSeconds(10), 52.499, 13.399),
      RunRoutePoint(1, START, 52.5, 13.4),
      RunRoutePoint(2, START.plusSeconds(10), 52.501, 13.401),
      RunRoutePoint(3, START.plusSeconds(1_810), 52.502, 13.402),
    ))
    routes.save(original, listOf(split("strava").copy(startTime = START.minusSeconds(10))))

    val selected = requireNotNull(routes.get("strava"))

    assertEquals(listOf(START, START.plusSeconds(10)), selected.route.points.map { it.timestamp })
    assertEquals(listOf(0, 1), selected.route.points.map { it.sequence })
    assertTrue(selected.splits.isEmpty())
    // Lookup does not modify the stored/raw points while rebuilding is pending.
    assertEquals(4, database.runRouteDao().getRoute("strava")?.points?.size)
    assertEquals(1, database.runRouteDao().getRoute("strava")?.splits?.size)
  }

  @Test
  fun separateRunOnSameDayCannotReceiveOrLoseAnotherPhysicalRunsRoute() = withDatabase { database ->
    database.runDao().upsertAll(listOf(
      run("strava", true),
      run("samsung", false),
      run("later", true).copy(physicalRunId = "later", duplicateGroupId = null),
    ))
    val routes = RunRouteRepository(database.runRouteDao())
    routes.save(route("later"), emptyList())
    assertNull(routes.get("strava"))
    routes.saveForPhysicalRun(route("samsung"), emptyList(), PHYSICAL_RUN_ID)
    assertEquals("later", routes.get("later")?.route?.runId)
    assertEquals(2, database.runRouteDao().getAllRoutes().size)
  }

  private fun withDatabase(block: suspend (OpenRunCoachDatabase) -> Unit) = runBlocking {
    val database = Room.inMemoryDatabaseBuilder(
      ApplicationProvider.getApplicationContext(), OpenRunCoachDatabase::class.java,
    ).build()
    try { block(database) } finally { database.close() }
  }

  private fun run(id: String, isCanonical: Boolean) = RunEntity(
    healthConnectRecordId = id,
    startTimeEpochMillis = START.toEpochMilli(),
    endTimeEpochMillis = START.plusSeconds(1_800).toEpochMilli(),
    durationSeconds = 1_800,
    distanceMeters = 5_000.0,
    averageHeartRate = null,
    maxHeartRate = null,
    averagePaceSecondsPerKm = 360.0,
    importedAtEpochMillis = START.plusSeconds(2_000).toEpochMilli(),
    physicalRunId = PHYSICAL_RUN_ID,
    duplicateGroupId = PHYSICAL_RUN_ID,
    isCanonical = isCanonical,
    sourcePackageName = if (id == "strava") "com.strava" else "com.sec.android.app.shealth",
  )

  private fun route(runId: String) = RunRoute(
    runId = runId,
    source = MetricSource.SAMSUNG_HEALTH,
    sourceAppName = "Samsung Health",
    confidence = MetricConfidence.HIGH,
    points = listOf(
      RunRoutePoint(0, START, 52.5, 13.4),
      RunRoutePoint(1, START.plusSeconds(10), 52.501, 13.401),
    ),
  )

  private fun split(runId: String) = RunSplit(
    runId = runId,
    ordinal = 1,
    derivation = RunSplitDerivation.ROUTE_DERIVED,
    startTime = START,
    endTime = START.plusSeconds(10),
    distanceMeters = 100.0,
    durationSeconds = 10.0,
    averagePaceSecondsPerKm = 100.0,
    partial = true,
    confidence = MetricConfidence.HIGH,
  )

  private companion object {
    const val PHYSICAL_RUN_ID = "strava"
    val START: Instant = Instant.parse("2026-09-23T15:55:30Z")
  }
}
