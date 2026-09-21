package com.example.openruncoach.domain.simulation

import com.example.openruncoach.domain.workout.IdGenerator
import java.time.Clock
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SimulationRunnerTest {
  private val clock = Clock.fixed(SimulationFixtures.fixedInstant, ZoneOffset.UTC)

  @Test
  fun everyBuiltInScenarioExecutesTheCompleteLifecycle() {
    SimulationFixtures.all.forEach { scenario ->
      val result = runner().run(scenario)

      assertEquals(scenario.expected.readinessStatus, result.adjustmentDecision.readinessStatus)
      assertEquals(scenario.expected.adjustedWorkoutType, result.adjustmentDecision.adjustedWorkout.type)
      assertEquals(scenario.expected.coachingAction, result.coachingDecision.action)
      assertEquals(scenario.expected.nextWorkoutType, result.nextWorkout.type)
      assertFalse(result.adjustmentDecision.reasons.isEmpty())
      assertFalse(result.adjustmentDecision.adjustments.isEmpty())
      assertEquals(SimulationStage.entries, result.timeline.map { it.stage })
      assertEquals(scenario.baselinePlannedWorkout, result.adjustmentDecision.originalWorkout)
      assertEquals(scenario.baselinePlannedWorkout.id, result.adjustmentDecision.adjustedWorkout.originalWorkoutId)
    }
  }

  @Test
  fun generatedDurationsAndTypesMatchScenarioRules() {
    val expectedDurations = mapOf(
      BuiltInScenario.WELL_RECOVERED to 33,
      BuiltInScenario.SORE_LEGS to 25,
      BuiltInScenario.POOR_SLEEP to 24,
      BuiltInScenario.SIGNIFICANT_PAIN to 0,
      BuiltInScenario.MILD_ILLNESS to 25,
      BuiltInScenario.SEVERE_ILLNESS to 15,
      BuiltInScenario.HIGH_RPE_COMPLETED to 26,
      BuiltInScenario.EASY_SUCCESS to 33,
      BuiltInScenario.ABORTED_WITH_PAIN to 0,
      BuiltInScenario.MULTIPLE_BAD_RECOVERY_SIGNALS to 15,
    )

    SimulationFixtures.all.forEach { scenario ->
      val result = runner().run(scenario)
      assertEquals(scenario.displayName, expectedDurations.getValue(scenario.key), result.nextWorkout.durationMinutes)
      if (result.adjustmentDecision.readinessStatus != com.example.openruncoach.domain.workout.ReadinessStatus.GREEN) {
        assertTrue(result.adjustmentDecision.adjustedWorkout.durationMinutes <= scenario.baselinePlannedWorkout.durationMinutes)
      }
    }
  }

  @Test
  fun fixedClockAndIdGeneratorProduceIdenticalResults() {
    val scenario = SimulationFixtures.scenario(BuiltInScenario.SORE_LEGS)

    val first = runner().run(scenario)
    val second = runner().run(scenario)

    assertEquals(first, second)
    assertEquals(SimulationFixtures.fixedInstant, first.adjustmentDecision.generatedAt)
    assertEquals(SimulationFixtures.fixedInstant, first.coachingDecision.generatedAt)
    assertEquals("next-workout", first.nextWorkout.id)
  }

  private fun runner() = SimulationRunner(clock, IdGenerator { "next-workout" })
}
