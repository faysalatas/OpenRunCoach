package com.example.openruncoach.domain.workout

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WorkoutAssociationResolverTest {
  private val start = Instant.parse("2026-09-29T09:00:00Z")

  @Test
  fun oneClearlyPlausibleWorkoutCanAutoAssociate() {
    val decision = assessWorkoutAssociation(evidence("easy", intendedDistance = 4_000.0, actualDistance = 3_900.0))

    assertEquals(WorkoutAssociationDecision.HIGH, decision)
    assertEquals("easy", resolveWorkoutAssociation(listOf("easy" to decision)))
  }

  @Test
  fun twoPlausibleWorkoutsRemainUnmatched() {
    assertNull(resolveWorkoutAssociation(listOf(
      "easy" to WorkoutAssociationDecision.HIGH,
      "long" to WorkoutAssociationDecision.HIGH,
    )))
  }

  @Test
  fun ambiguousCompetitorPreventsSilentSelection() {
    assertNull(resolveWorkoutAssociation(listOf(
      "intended" to WorkoutAssociationDecision.HIGH,
      "other" to WorkoutAssociationDecision.AMBIGUOUS,
    )))
  }

  @Test
  fun sameDateWithoutDistanceOrDurationEvidenceIsNotAMatch() {
    assertEquals(
      WorkoutAssociationDecision.NO_MATCH,
      assessWorkoutAssociation(evidence("unknown", intendedDistance = null, actualDistance = null, intendedDuration = null)),
    )
  }

  @Test
  fun unrelatedSecondPhysicalRunOnSameDatePreservesNoAutoAssociationRule() {
    assertEquals(false, hasUnambiguousRunDate(listOf("intended", "unrelated"), "intended"))
    assertEquals(true, hasUnambiguousRunDate(listOf("intended", "intended"), "intended"))
  }

  private fun evidence(
    id: String,
    intendedDistance: Double?,
    actualDistance: Double?,
    intendedDuration: Long? = 1_800,
  ) = WorkoutAssociationEvidence(
    plannedWorkoutId = id,
    workoutCreatedAt = start.minusSeconds(86_400),
    actualRunStart = start,
    intendedDistanceMeters = intendedDistance,
    intendedDurationSeconds = intendedDuration,
    actualDistanceMeters = actualDistance,
    actualDurationSeconds = 1_800,
  )
}
