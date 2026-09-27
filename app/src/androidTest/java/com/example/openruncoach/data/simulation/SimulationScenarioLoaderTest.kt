package com.example.openruncoach.data.simulation

import androidx.room.Room
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.openruncoach.data.AthletePerformanceProfileRepository
import com.example.openruncoach.data.AthleteStateRepository
import com.example.openruncoach.data.HealthConnectRepository
import com.example.openruncoach.data.RunMetricsRepository
import com.example.openruncoach.data.RunRepository
import com.example.openruncoach.data.RunSettingsRepository
import com.example.openruncoach.data.WorkoutResponseRepository
import com.example.openruncoach.data.local.OpenRunCoachDatabase
import com.example.openruncoach.domain.metrics.RunContinuityEvidence
import com.example.openruncoach.domain.metrics.RunSampleMetricType
import com.example.openruncoach.domain.profile.AthletePerformanceProfileCalculator
import com.example.openruncoach.domain.simulation.SimulationScenarioKey
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SimulationScenarioLoaderTest {
  @Test fun hardContinuous5kPersistsAndReloadsEveryGeneratedSpeedSample() = withDatabase { database ->
    load(database, SimulationScenarioKey.HARD_CONTINUOUS_5K)

    assertSpeedSamples(database, "hard-continuous-5k", 181)
    val response = WorkoutResponseRepository(database).getAll().single()
    val athleteStateResult = AthleteStateRepository(
      database,
      AthletePerformanceProfileRepository(
        database,
        AthletePerformanceProfileCalculator(CLOCK, CLOCK.zone),
      ),
    ).rebuildWithTrace()

    assertEquals(RunContinuityEvidence.CONTINUOUS, response.continuity)
    assertEquals(181, athleteStateResult.continuityTrace.single().directSpeedSampleCount)
    assertEquals(181, athleteStateResult.continuityTrace.single().collectionSpeedSampleCount)
    assertEquals(181, athleteStateResult.continuityTrace.single().coachPathSpeedSampleCount)
    assertEquals(
      RunContinuityEvidence.CONTINUOUS,
      athleteStateResult.continuityTrace.single().athleteStateContinuity,
    )
    assertEquals(
      5_000.0,
      requireNotNull(athleteStateResult.state.longestDemonstratedContinuousDistanceMeters),
      0.01,
    )
  }

  @Test fun longitudinalAndWalkingScenariosRetainTheirRawMovementSeries() = withDatabase { database ->
    load(database, SimulationScenarioKey.HARD_FIRST_5K)
    assertSpeedSamples(database, "hard-first-5k", 211)

    database.clearAllTables()
    load(database, SimulationScenarioKey.ENDURANCE_THEN_HARD_5K)
    mapOf(
      "j-easy-6k" to 295,
      "j-easy-7k" to 341,
      "j-easy-8k" to 385,
      "j-hard-5k" to 181,
    ).forEach { (runId, count) -> assertSpeedSamples(database, runId, count) }

    database.clearAllTables()
    load(database, SimulationScenarioKey.HARD_5K_POOR_RESPONSE)
    assertTrue(speedSampleCount(database, "hard-walking-5k") > 0)
  }

  @Test fun missingMovementStaysMissingAndReloadRestoresScenarioD() = withDatabase { database ->
    load(database, SimulationScenarioKey.LITTLE_EVIDENCE)
    assertSpeedSamples(database, "m-unassessed-run", 0)

    database.clearAllTables()
    load(database, SimulationScenarioKey.HARD_CONTINUOUS_5K)
    assertSpeedSamples(database, "hard-continuous-5k", 181)
  }

  @Test fun simulationCanonicalRefreshWithoutProviderMetricsPreservesSyntheticSamples() =
    withDatabase { database ->
      load(database, SimulationScenarioKey.HARD_CONTINUOUS_5K)
      val context = ApplicationProvider.getApplicationContext<Context>()
      val repository = RunRepository(
        database.runDao(),
        HealthConnectRepository(context),
        RunSettingsRepository(context),
        clock = CLOCK,
        runMetricsRepository = null,
        runMetricsRefresher = null,
      )

      repository.refreshCanonicalSelection(preferredSourcePackage = null)

      assertSpeedSamples(database, "hard-continuous-5k", 181)
      assertEquals(
        RunContinuityEvidence.CONTINUOUS,
        WorkoutResponseRepository(database).getAll().single().continuity,
      )
    }

  @Test fun switchingDToEToDDoesNotRetainAResponseFromAnotherGeneration() = withDatabase { database ->
    load(database, SimulationScenarioKey.HARD_CONTINUOUS_5K)
    assertEquals(RunContinuityEvidence.CONTINUOUS, currentResponse(database).continuity)

    database.clearAllTables()
    load(database, SimulationScenarioKey.HARD_5K_POOR_RESPONSE)
    assertEquals(RunContinuityEvidence.INTERRUPTED, currentResponse(database).continuity)

    database.clearAllTables()
    load(database, SimulationScenarioKey.HARD_CONTINUOUS_5K)
    assertEquals(RunContinuityEvidence.CONTINUOUS, currentResponse(database).continuity)
  }

  private fun withDatabase(block: suspend (OpenRunCoachDatabase) -> Unit) = runBlocking {
    val database = Room.inMemoryDatabaseBuilder(
      ApplicationProvider.getApplicationContext(),
      OpenRunCoachDatabase::class.java,
    ).build()
    try {
      block(database)
    } finally {
      database.close()
    }
  }

  private suspend fun load(database: OpenRunCoachDatabase, key: SimulationScenarioKey) {
    SimulationScenarioLoader(database, CLOCK).load(key)
  }

  private suspend fun assertSpeedSamples(
    database: OpenRunCoachDatabase,
    runId: String,
    expected: Int,
  ) {
    assertEquals(expected, speedSampleCount(database, runId))
    assertEquals(
      expected,
      RunMetricsRepository(database.runMetricsDao()).getRunMetrics(runId)?.series?.speed?.size ?: 0,
    )
  }

  private suspend fun speedSampleCount(database: OpenRunCoachDatabase, runId: String): Int =
    database.runMetricsDao().countSamples(runId, RunSampleMetricType.SPEED.name)

  private suspend fun currentResponse(database: OpenRunCoachDatabase) =
    WorkoutResponseRepository(database).getAll().single()

  private companion object {
    val CLOCK: Clock = Clock.fixed(Instant.parse("2026-09-27T12:00:00Z"), ZoneOffset.UTC)
  }
}
