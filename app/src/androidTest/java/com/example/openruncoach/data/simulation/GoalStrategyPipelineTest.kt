package com.example.openruncoach.data.simulation

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.openruncoach.OpenRunCoachEnvironment
import com.example.openruncoach.data.WorkoutResponseRepository
import com.example.openruncoach.data.local.OpenRunCoachDatabase
import com.example.openruncoach.domain.goal.GoalStatus
import com.example.openruncoach.domain.goal.GoalType
import com.example.openruncoach.domain.goal.TrainingCapability
import com.example.openruncoach.domain.metrics.RunContinuityEvidence
import com.example.openruncoach.domain.profile.ProfileConfidence
import com.example.openruncoach.domain.simulation.SimulationScenarioKey
import com.example.openruncoach.domain.workout.SessionCost
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GoalStrategyPipelineTest {
  @Test fun strategyScenariosUsePersistedEvidenceAndRebuildDeterministically() = runBlocking {
    val database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), OpenRunCoachDatabase::class.java).build()
    val clock = Clock.fixed(Instant.parse("2026-09-27T12:00:00Z"), ZoneOffset.UTC)
    val environment = OpenRunCoachEnvironment("strategy-test", database, clock, true)
    try {
      SimulationScenarioKey.entries.filter { it.name.startsWith("GS") }.forEach { key ->
        database.clearAllTables()
        SimulationScenarioLoader(database, clock).load(key)
        val snapshot = environment.goalStrategyRepository().rebuild()
        val goal = requireNotNull(snapshot.goal)
        val strategy = requireNotNull(snapshot.strategy)
        val state = snapshot.athleteState
        assertEquals(GoalStatus.ACTIVE, goal.status)
        assertEquals(snapshot, environment.goalStrategyRepository().rebuild())
        assertFalse(TrainingCapability.SPEED in strategy.primaryCapabilities)
        when (key) {
          SimulationScenarioKey.GS1_BEGINNER_5K -> {
            assertTrue(TrainingCapability.CONTINUOUS_RUNNING in strategy.primaryCapabilities)
            assertTrue(TrainingCapability.DISTANCE_TOLERANCE in strategy.primaryCapabilities)
            assertEquals(ProfileConfidence.LOW, strategy.confidence)
            assertEquals(2_500.0, requireNotNull(state.longestCompletedDistanceMeters), 0.01)
            assertNull(state.longestDemonstratedContinuousDistanceMeters)
          }
          SimulationScenarioKey.GS2_NEARLY_5K -> {
            assertEquals(4_800.0, requireNotNull(state.longestDemonstratedContinuousDistanceMeters), 0.01)
            assertTrue(TrainingCapability.AEROBIC_BASE in strategy.primaryCapabilities)
            assertTrue(TrainingCapability.CONTINUOUS_RUNNING in strategy.secondaryCapabilities)
          }
          SimulationScenarioKey.GS3_PERFORMANCE_5K, SimulationScenarioKey.GS4_TARGET_30 -> {
            assertEquals(8_000.0, requireNotNull(state.longestDemonstratedContinuousDistanceMeters), 0.01)
            assertTrue(TrainingCapability.THRESHOLD in strategy.primaryCapabilities)
            assertTrue(TrainingCapability.RUNNING_ECONOMY in strategy.secondaryCapabilities)
            if (key == SimulationScenarioKey.GS4_TARGET_30) {
              assertEquals(GoalType.TARGET_TIME, goal.goalType)
              assertEquals(1_800L, goal.targetTimeSeconds)
              assertEquals(480.0, requireNotNull(environment.profileRepository().getProfile()?.typicalEasyPace).representative, 0.01)
            } else assertEquals(GoalType.IMPROVE_PERFORMANCE, goal.goalType)
          }
          SimulationScenarioKey.GS5_COMPLETE_10K -> {
            assertEquals(6_000.0, requireNotNull(state.longestToleratedDistanceMeters), 0.01)
            assertTrue(TrainingCapability.DISTANCE_TOLERANCE in strategy.primaryCapabilities)
          }
          SimulationScenarioKey.GS6_HALF_MARATHON -> {
            assertTrue(TrainingCapability.WEEKLY_VOLUME in strategy.secondaryCapabilities)
            assertTrue(TrainingCapability.RECOVERY_TOLERANCE in strategy.secondaryCapabilities)
          }
          SimulationScenarioKey.GS7_LIMITED -> {
            assertEquals(ProfileConfidence.LOW, strategy.confidence)
            assertNull(state.longestDemonstratedContinuousDistanceMeters)
            assertTrue(strategy.reasons.any { it.contains("insufficiently observed") })
          }
          SimulationScenarioKey.GS8_HIGH_COST -> {
            assertEquals(SessionCost.HIGH, state.latestSessionCost)
            assertEquals(setOf(TrainingCapability.DISTANCE_TOLERANCE, TrainingCapability.AEROBIC_BASE), strategy.primaryCapabilities.toSet())
          }
          else -> error("Unexpected strategy scenario")
        }
        println("$key\n$snapshot")
      }
      listOf(
        SimulationScenarioKey.HARD_CONTINUOUS_5K to RunContinuityEvidence.CONTINUOUS,
        SimulationScenarioKey.HARD_5K_POOR_RESPONSE to RunContinuityEvidence.INTERRUPTED,
        SimulationScenarioKey.LITTLE_EVIDENCE to RunContinuityEvidence.UNKNOWN,
      ).forEach { (key, continuity) ->
        database.clearAllTables()
        SimulationScenarioLoader(database, clock).load(key)
        val snapshot = environment.goalStrategyRepository().rebuild()
        val response = WorkoutResponseRepository(database).getAll().singleOrNull()
        if (response == null) {
          assertEquals(RunContinuityEvidence.UNKNOWN, continuity)
          assertEquals(1, snapshot.athleteState.recentContinuity.unknownRuns)
        } else assertEquals(continuity, response.continuity)
      }
    } finally {
      database.close()
    }
  }
}
