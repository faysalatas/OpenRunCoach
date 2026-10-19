package com.example.openruncoach.data.simulation

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.openruncoach.data.PostRunReconciler
import com.example.openruncoach.data.RunFeedbackRepository
import com.example.openruncoach.data.RunMetricsRepository
import com.example.openruncoach.data.WorkoutExecutionRepository
import com.example.openruncoach.data.TrainingPlanRepository
import com.example.openruncoach.data.local.OpenRunCoachDatabase
import com.example.openruncoach.data.toRunSummary
import com.example.openruncoach.domain.RunFeedbackDraft
import com.example.openruncoach.domain.simulation.SimulationScenarioKey
import com.example.openruncoach.domain.plan.*
import com.example.openruncoach.domain.profile.ProfileConfidence
import com.example.openruncoach.domain.workout.StructuredWorkoutSamples
import com.example.openruncoach.domain.workout.WorkoutType
import java.time.LocalDate
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
        // Give both storage paths the same future prescription and active goal. The shared
        // coordinator must persist the same adaptation, rather than a simulator-only decision.
        production.trainingGoalDao().activate(simulation.trainingGoalDao().getActiveGoal()!!)
        val date = LocalDate.now(clock)
        val future = StructuredWorkoutSamples.easyRun().copy(date = date.plusDays(1), goalId = "simulation-goal")
        val week = TrainingWeek(0, date, listOf(PlannedWorkoutEntry(WeeklyWorkoutSlot.EARLY_WEEK, future, "Parity fixture")),
          null, null, WeekLoadClassification.NORMAL, "Parity week", listOf("Parity fixture"))
        val plan = TrainingPlan("future-plan", "simulation-goal", clock.instant(), date, date.plusDays(6),
          listOf(week), emptyList(), 1, "Parity plan", ProfileConfidence.LOW, TrainingPlanStatus.ACTIVE)
        for (db in listOf(simulation, production)) {
          TrainingPlanRepository(db, clock = clock).saveAsActive(plan)
          PostRunReconciler(db, clock).reconcileAll()
        }
        val adjustment = simulation.coachingWorkflowDao().getAdjustment(future.id)!!
        assertEquals(adjustment, production.coachingWorkflowDao().getAdjustment(future.id))
        assertEquals(if (key == SimulationScenarioKey.HARD_5K_POOR_RESPONSE) WorkoutType.REST.name else WorkoutType.EASY_RUN.name,
          adjustment.adjustedType)
      } finally { simulation.close(); production.close() }
    }
  }
}
