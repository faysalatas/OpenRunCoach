package com.example.openruncoach.domain.simulation

import com.example.openruncoach.data.toSampleEntities
import com.example.openruncoach.data.toTimeSeries
import com.example.openruncoach.data.simulation.toMetrics
import com.example.openruncoach.domain.metrics.RunContinuityEvidence
import com.example.openruncoach.domain.metrics.RunContinuityEvaluator
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SimulationScenariosTest {
  private val clock = Clock.fixed(Instant.parse("2026-09-27T12:00:00Z"), ZoneOffset.UTC)
  private val continuityEvaluator = RunContinuityEvaluator()

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

  @Test fun hardContinuous5kBuildsTheExactLoaderInputAndPersistenceEntities() {
    val run = SimulationScenarios.preset(SimulationScenarioKey.HARD_CONTINUOUS_5K).sessions.single().run
    val metrics = run.toMetrics(clock)

    assertEquals("hard-continuous-5k", run.id)
    assertEquals(Instant.parse("2026-09-27T08:00:00Z"), run.startTime)
    assertEquals(1_800L, run.durationSeconds)
    assertEquals(run.startTime.plusSeconds(1_800), metrics.endTime)
    assertEquals(SimulatedMovement.CONTINUOUS, run.movement)
    assertEquals(181, metrics.series.speed.size)
    assertEquals(181, metrics.toSampleEntities().count { it.metricType == "SPEED" })
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

  @Test fun continuousScenarioMovementSurvivesTheProductionMetricMapper() {
    val runs = listOf(SimulationScenarioKey.HARD_FIRST_5K, SimulationScenarioKey.ENDURANCE_THEN_HARD_5K)
      .flatMap { SimulationScenarios.preset(it).sessions }
      .map(SimulatedSessionInput::run)

    runs.forEach { run ->
      val metrics = run.toMetrics(clock)
      val roundTrip = metrics.copy(series = metrics.toSampleEntities().toTimeSeries())
      assertTrue(roundTrip.series.speed.size >= 3)
      assertEquals(run.startTime, roundTrip.series.speed.first().timestamp)
      assertEquals(run.startTime.plusSeconds(run.durationSeconds), roundTrip.series.speed.last().timestamp)
      assertEquals(RunContinuityEvidence.CONTINUOUS, continuityEvaluator.evaluate(roundTrip))
    }
  }

  @Test fun hardFirst5kUsesTheDocumentedCoherentActual() {
    val run = SimulationScenarios.preset(SimulationScenarioKey.HARD_FIRST_5K).runs.single()

    assertEquals(5_000.0, run.distanceMeters, 0.01)
    assertEquals(2_100L, run.durationSeconds)
    assertEquals(420.0, run.averagePaceSecondsPerKm, 0.01)
  }

  @Test fun walkingAndMissingMovementRemainInterruptedAndUnknown() {
    val walking = SimulationScenarios.preset(SimulationScenarioKey.HARD_5K_POOR_RESPONSE)
      .sessions.single().run.toMetrics(clock)
    val missing = SimulationScenarios.preset(SimulationScenarioKey.LITTLE_EVIDENCE)
      .sessions.single().run.toMetrics(clock)

    assertEquals(RunContinuityEvidence.INTERRUPTED, continuityEvaluator.evaluate(
      walking.copy(series = walking.toSampleEntities().toTimeSeries())
    ))
    assertEquals(RunContinuityEvidence.UNKNOWN, continuityEvaluator.evaluate(
      missing.copy(series = missing.toSampleEntities().toTimeSeries())
    ))
  }

  @Test fun scenariosDEAndMRetainTheirContinuityThroughTheProductionMetricMapper() {
    mapOf(
      SimulationScenarioKey.HARD_CONTINUOUS_5K to RunContinuityEvidence.CONTINUOUS,
      SimulationScenarioKey.HARD_5K_POOR_RESPONSE to RunContinuityEvidence.INTERRUPTED,
      SimulationScenarioKey.LITTLE_EVIDENCE to RunContinuityEvidence.UNKNOWN,
    ).forEach { (key, expected) ->
      val metrics = SimulationScenarios.preset(key).sessions.single().run.toMetrics(clock)
      val roundTrip = metrics.copy(series = metrics.toSampleEntities().toTimeSeries())
      assertEquals(key.displayName, expected, continuityEvaluator.evaluate(roundTrip))
    }
  }
}
