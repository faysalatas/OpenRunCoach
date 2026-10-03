package com.example.openruncoach.data

import com.example.openruncoach.domain.workout.WorkoutAssociationDecision
import com.example.openruncoach.domain.workout.WorkoutType
import com.example.openruncoach.domain.workout.resolveWorkoutAssociation
import com.example.openruncoach.domain.workout.simpleDurationWorkout
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutAssignmentCandidateSelectorTest {
  private val runDate = LocalDate.of(2026, 10, 1)

  @Test fun sameDayWorkoutAppearsInManualCandidates() {
    val selected = selectManualAssignmentCandidates(listOf(candidate("same-day", runDate)), runDate)

    assertEquals(listOf("same-day"), selected.map { it.workout.id })
  }

  @Test fun lowAutomaticConfidenceDoesNotHideManualCandidateOrTriggerAutomaticSelection() {
    val selected = selectManualAssignmentCandidates(listOf(candidate("same-day", runDate)), runDate)

    assertEquals("same-day", selected.single().workout.id)
    assertNull(resolveWorkoutAssociation(listOf("same-day" to WorkoutAssociationDecision.AMBIGUOUS)))
  }

  @Test fun workoutAssociatedWithAnotherRunRemainsVisibleAsRepairable() {
    val selected = selectManualAssignmentCandidates(
      listOf(candidate("same-day", runDate, WorkoutAssignmentState.ASSIGNED_TO_ANOTHER_RUN)),
      runDate,
    )

    assertEquals(WorkoutAssignmentState.ASSIGNED_TO_ANOTHER_RUN, selected.single().associationState)
  }

  @Test fun sameDayCandidateRanksBeforeNearbyWorkouts() {
    val selected = selectManualAssignmentCandidates(
      listOf(
        candidate("next", runDate.plusDays(3)),
        candidate("previous", runDate.minusDays(2)),
        candidate("same-day", runDate),
      ),
      runDate,
    )

    assertEquals(listOf("same-day", "previous", "next"), selected.map { it.workout.id })
  }

  @Test fun unrelatedAncientWorkoutIsNotIncluded() {
    val selected = selectManualAssignmentCandidates(
      listOf(candidate("ancient", runDate.minusDays(30)), candidate("same-day", runDate)),
      runDate,
    )

    assertEquals(listOf("same-day"), selected.map { it.workout.id })
  }

  @Test fun automaticAssociationStillRequiresOneHighConfidenceCandidate() {
    assertNull(resolveWorkoutAssociation(listOf("same-day" to WorkoutAssociationDecision.NO_MATCH)))
    assertNull(resolveWorkoutAssociation(listOf(
      "same-day" to WorkoutAssociationDecision.HIGH,
      "nearby" to WorkoutAssociationDecision.HIGH,
    )))
    assertTrue(resolveWorkoutAssociation(listOf("same-day" to WorkoutAssociationDecision.HIGH)) == "same-day")
  }

  private fun candidate(
    id: String,
    date: LocalDate,
    state: WorkoutAssignmentState = WorkoutAssignmentState.AVAILABLE,
  ) = WorkoutAssignmentCandidate(
    workout = simpleDurationWorkout(
      id = id,
      date = date,
      type = WorkoutType.EASY_RUN,
      durationMinutes = 30,
      targetRpeMin = 2,
      targetRpeMax = 4,
      createdAt = Instant.parse("2026-09-20T08:00:00Z"),
    ),
    plannedDistanceMeters = null,
    plannedDurationSeconds = 1_800,
    associationState = state,
  )
}
