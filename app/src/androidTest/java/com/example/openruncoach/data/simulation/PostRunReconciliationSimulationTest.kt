package com.example.openruncoach.data.simulation

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.openruncoach.data.PostRunReconciler
import com.example.openruncoach.data.RunFeedbackRepository
import com.example.openruncoach.data.RunMetricsRepository
import com.example.openruncoach.data.WorkoutExecutionRepository
import com.example.openruncoach.data.local.OpenRunCoachDatabase
import com.example.openruncoach.data.toRunSummary
import com.example.openruncoach.domain.RunFeedbackDraft
import com.example.openruncoach.domain.simulation.SimulationScenarioKey
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PostRunReconciliationSimulationTest {
  @Test fun equivalentPersistedInputsUseTheSameProductionReconciliationForDAndE() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val clock = Clock.fixed(Instant.parse("2026-09-27T12:00:00Z"), ZoneOffset.UTC)
    for (key in listOf(SimulationScenarioKey.HARD_CONTINUOUS_5K, SimulationScenarioKey.HARD_5K_POOR_RESPONSE)) {
      val simulation = Room.inMemoryDatabaseBuilder(context, OpenRunCoachDatabase::class.java).build()
      val production = Room.inMemoryDatabaseBuilder(context, OpenRunCoachDatabase::class.java).build()
      try {
        SimulationScenarioLoader(simulation, clock).load(key)
        val row = simulation.runDao().getCanonicalRuns().single()
        val association = simulation.coachingWorkflowDao().getAssociationForPhysicalRun(row.physicalRunId)!!
        val prescription = simulation.coachingWorkflowDao().getWorkout(association.plannedWorkoutId)!!
        val checkout = RunFeedbackRepository(simulation, clock).getFeedbackForRun(row.physicalRunId)!!
        production.runDao().upsertAll(listOf(row))
        production.coachingWorkflowDao().replaceWorkout(prescription)
        RunMetricsRepository(production, clock).save(
          RunMetricsRepository(simulation.runMetricsDao()).getRunMetrics(row.healthConnectRecordId)!!,
        )
        WorkoutExecutionRepository(production, clock).assignManually(row.toRunSummary(), association.plannedWorkoutId)
        RunFeedbackRepository(production, clock).saveFeedback(RunFeedbackDraft(
          row.physicalRunId, checkout.rpe, checkout.cardioEffort, checkout.muscleFatigue,
          checkout.painLevel, checkout.completionStatus, checkout.readiness, checkout.notes,
        ))
        val expected = PostRunReconciler(simulation, clock).reconcilePhysicalRun(row.physicalRunId)
        val actual = PostRunReconciler(production, clock).reconcilePhysicalRun(row.physicalRunId)
        assertEquals(expected.execution, actual.execution)
        assertEquals(expected.response, actual.response)
        assertEquals(expected.athleteState, actual.athleteState)
      } finally { simulation.close(); production.close() }
    }
  }
}
