package com.example.openruncoach.domain.simulation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SimulationScenariosTest {
  @Test fun presetsAreDeterministicAndHaveStableRunIdentities() {
    SimulationScenarioKey.entries.forEach { key ->
      assertEquals(SimulationScenarios.preset(key), SimulationScenarios.preset(key))
      assertEquals(
        SimulationScenarios.preset(key).allRuns.map { it.id }.distinct().size,
        SimulationScenarios.preset(key).allRuns.size,
      )
    }
  }

  @Test fun missingHeartRateIsActuallyAbsent() {
    val run = SimulationScenarios.preset(SimulationScenarioKey.MISSING_HEART_RATE).runs.single()

    assertNull(run.averageHeartRate)
    assertNull(run.maxHeartRate)
  }

  @Test fun walkingPresetUsesMovementSeriesInputInsteadOfAResultFlag() {
    val poor = SimulationScenarios.preset(SimulationScenarioKey.HARD_5K_POOR_RESPONSE).runs.single()
    val clean = SimulationScenarios.preset(SimulationScenarioKey.HARD_CONTINUOUS_5K).runs.single()

    assertEquals(SimulatedMovement.WALKING_AND_STRONG_FADE, poor.movement)
    assertEquals(SimulatedMovement.CONTINUOUS, clean.movement)
  }

  @Test fun sameDayAssociationPresetContainsTwoDistinctRuns() {
    val scenario = SimulationScenarios.preset(SimulationScenarioKey.SAME_DAY_ASSOCIATION)

    assertEquals(2, scenario.allRuns.size)
    assertTrue(scenario.allRuns.map { it.startTime }.distinct().size == 2)
    assertTrue(scenario.unrelatedRuns.single().startTime > scenario.sessions.single().run.startTime)
  }

  @Test fun longitudinalPresetsContainInputEvidenceRatherThanAthleteStateResults() {
    assertEquals(4, SimulationScenarios.preset(SimulationScenarioKey.ENDURANCE_THEN_HARD_5K).sessions.size)
    assertEquals(4, SimulationScenarios.preset(SimulationScenarioKey.ONE_BAD_RUN).sessions.size)
    assertEquals(5, SimulationScenarios.preset(SimulationScenarioKey.POSITIVE_EASY_TRAINING).sessions.size)
    assertTrue(!SimulationScenarios.preset(SimulationScenarioKey.LITTLE_EVIDENCE).persistPrimaryCheckout)
  }
}
