package com.example.openruncoach.domain.workout

import com.example.openruncoach.domain.CardioEffort
import com.example.openruncoach.domain.CompletionStatus
import com.example.openruncoach.domain.MuscleFatigue
import com.example.openruncoach.domain.PainLevel
import com.example.openruncoach.domain.RunFeedback
import com.example.openruncoach.domain.RunSummary
import com.example.openruncoach.domain.coaching.CoachingEngine
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Test

class WorkoutLifecycleServiceTest {
  private val service = WorkoutLifecycleService()
  private val now = Instant.parse("2026-09-21T12:00:00Z")
  private val clock = Clock.fixed(now, ZoneOffset.UTC)
  private val plannedWorkout = StructuredWorkoutSamples.easyRun()
  private val checkIn = PreRunCheckIn(
    LocalDate.of(2026, 9, 21), 5, 5, 5, SorenessLevel.NONE, emptySet(),
    PainLevel.NONE, 2, 5, IllnessLevel.NONE, null,
  )
  private val actualRun = RunSummary(
    "actual-run", now.minusSeconds(1_800), now, 1_800, 4_800.0, 140.0, 160, 375.0,
  )
  private val feedback = RunFeedback(
    "feedback", actualRun.healthConnectRecordId, 4, CardioEffort.APPROPRIATE,
    MuscleFatigue.NONE, PainLevel.NONE, CompletionStatus.COMPLETED, 4, null, now, now,
  )
  private val adjustment = CheckInEvaluator(clock).evaluate(
    plannedWorkout,
    checkIn,
    emptyList(),
    RecentTrainingVolume(0, 0, 0.0),
    null,
  )
  private val execution = WorkoutAdherenceEvaluator(clock).evaluate(
    plannedWorkout, adjustment.adjustedWorkout, actualRun, feedback,
  )
  private val coaching = requireNotNull(CoachingEngine(clock).analyze(actualRun, listOf(actualRun), feedback, execution))
  private val nextWorkout = NextWorkoutGenerator(clock, IdGenerator { "next-workout" }).generate(
    adjustment.adjustedWorkout, actualRun, feedback, coaching,
  )

  @Test
  fun completeLifecycleTransitionsInOrderAndKeepsConceptsSeparate() {
    var lifecycle = WorkoutLifecycle(plannedWorkout)

    lifecycle = service.transition(lifecycle, WorkoutLifecycleEvent.RequireCheckIn)
    assertEquals(WorkoutLifecycleState.CHECK_IN_REQUIRED, lifecycle.state)

    lifecycle = service.transition(
      lifecycle,
      WorkoutLifecycleEvent.CheckInCompleted(checkIn, adjustment),
    )
    assertEquals(WorkoutLifecycleState.READY, lifecycle.state)
    assertNotNull(lifecycle.preRunCheckIn)
    assertNotNull(lifecycle.adjustmentDecision)

    lifecycle = service.transition(lifecycle, WorkoutLifecycleEvent.RunCompleted(actualRun, execution))
    assertEquals(WorkoutLifecycleState.COMPLETED, lifecycle.state)
    assertEquals(actualRun, lifecycle.actualRun)
    assertEquals(execution, lifecycle.executionResult)

    lifecycle = service.transition(lifecycle, WorkoutLifecycleEvent.RequireCheckout)
    assertEquals(WorkoutLifecycleState.CHECKOUT_REQUIRED, lifecycle.state)

    lifecycle = service.transition(
      lifecycle,
      WorkoutLifecycleEvent.AnalysisCompleted(feedback, coaching),
    )
    assertEquals(WorkoutLifecycleState.ANALYZED, lifecycle.state)
    assertEquals(feedback, lifecycle.postRunFeedback)
    assertEquals(coaching, lifecycle.coachingDecision)

    lifecycle = service.transition(
      lifecycle,
      WorkoutLifecycleEvent.NextWorkoutCreated(nextWorkout),
    )
    assertEquals(WorkoutLifecycleState.NEXT_WORKOUT_CREATED, lifecycle.state)
    assertEquals(nextWorkout, lifecycle.nextWorkout)
    assertEquals(plannedWorkout, lifecycle.plannedWorkout)
  }

  @Test
  fun invalidTransitionsAreRejected() {
    val planned = WorkoutLifecycle(plannedWorkout)

    assertThrows(InvalidWorkoutLifecycleTransition::class.java) {
      service.transition(planned, WorkoutLifecycleEvent.RunCompleted(actualRun))
    }
    val checkInRequired = service.transition(planned, WorkoutLifecycleEvent.RequireCheckIn)
    assertThrows(InvalidWorkoutLifecycleTransition::class.java) {
      service.transition(checkInRequired, WorkoutLifecycleEvent.RequireCheckout)
    }
  }
}
