package com.example.openruncoach.domain.workout

import com.example.openruncoach.domain.simulation.BuiltInScenario
import com.example.openruncoach.domain.simulation.SimulationFixtures
import com.example.openruncoach.domain.simulation.SimulationRunner
import java.time.Clock
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Test

class WorkoutLifecycleServiceTest {
  private val service = WorkoutLifecycleService()
  private val scenario = SimulationFixtures.scenario(BuiltInScenario.WELL_RECOVERED)
  private val result = SimulationRunner(
    Clock.fixed(SimulationFixtures.fixedInstant, ZoneOffset.UTC),
    IdGenerator { "next-workout" },
  ).run(scenario)

  @Test
  fun completeLifecycleTransitionsInOrderAndKeepsConceptsSeparate() {
    var lifecycle = WorkoutLifecycle(scenario.baselinePlannedWorkout)

    lifecycle = service.transition(lifecycle, WorkoutLifecycleEvent.RequireCheckIn)
    assertEquals(WorkoutLifecycleState.CHECK_IN_REQUIRED, lifecycle.state)

    lifecycle = service.transition(
      lifecycle,
      WorkoutLifecycleEvent.CheckInCompleted(scenario.preRunCheckIn, result.adjustmentDecision),
    )
    assertEquals(WorkoutLifecycleState.READY, lifecycle.state)
    assertNotNull(lifecycle.preRunCheckIn)
    assertNotNull(lifecycle.adjustmentDecision)

    lifecycle = service.transition(lifecycle, WorkoutLifecycleEvent.RunCompleted(scenario.simulatedActualRun))
    assertEquals(WorkoutLifecycleState.COMPLETED, lifecycle.state)
    assertEquals(scenario.simulatedActualRun, lifecycle.actualRun)

    lifecycle = service.transition(lifecycle, WorkoutLifecycleEvent.RequireCheckout)
    assertEquals(WorkoutLifecycleState.CHECKOUT_REQUIRED, lifecycle.state)

    lifecycle = service.transition(
      lifecycle,
      WorkoutLifecycleEvent.AnalysisCompleted(scenario.postRunFeedback, result.coachingDecision),
    )
    assertEquals(WorkoutLifecycleState.ANALYZED, lifecycle.state)
    assertEquals(scenario.postRunFeedback, lifecycle.postRunFeedback)
    assertEquals(result.coachingDecision, lifecycle.coachingDecision)

    lifecycle = service.transition(
      lifecycle,
      WorkoutLifecycleEvent.NextWorkoutCreated(result.nextWorkout),
    )
    assertEquals(WorkoutLifecycleState.NEXT_WORKOUT_CREATED, lifecycle.state)
    assertEquals(result.nextWorkout, lifecycle.nextWorkout)
    assertEquals(scenario.baselinePlannedWorkout, lifecycle.plannedWorkout)
  }

  @Test
  fun invalidTransitionsAreRejected() {
    val planned = WorkoutLifecycle(scenario.baselinePlannedWorkout)

    assertThrows(InvalidWorkoutLifecycleTransition::class.java) {
      service.transition(planned, WorkoutLifecycleEvent.RunCompleted(scenario.simulatedActualRun))
    }
    val checkInRequired = service.transition(planned, WorkoutLifecycleEvent.RequireCheckIn)
    assertThrows(InvalidWorkoutLifecycleTransition::class.java) {
      service.transition(checkInRequired, WorkoutLifecycleEvent.RequireCheckout)
    }
  }
}
