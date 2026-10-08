package com.example.openruncoach.ui.health

import com.example.openruncoach.domain.PainLevel
import com.example.openruncoach.domain.coaching.CoachingAction
import com.example.openruncoach.domain.coaching.CoachingDecision
import com.example.openruncoach.domain.metrics.RunContinuityEvidence
import com.example.openruncoach.domain.workout.*
import java.time.Instant
import org.junit.Assert.*
import org.junit.Test

class CoachPresentationTest {
  private val response = WorkoutResponse(
    runId = "run", plannedWorkoutId = "workout", purpose = WorkoutPurpose.ENDURANCE,
    completion = WorkoutCompletionOutcome.ABORTED, adherence = PrescriptionAdherence.MEANINGFUL_DEVIATION,
    volumeCompletion = VolumeCompletion.PARTIAL, effortResponse = EffortResponse.ABOVE_TARGET,
    paceResponse = PaceResponse.UNKNOWN, continuity = RunContinuityEvidence.INTERRUPTED,
    paceStability = PaceStability.STRONG_FADE, sessionCost = SessionCost.HIGH,
    pain = PainLevel.SIGNIFICANT, confidence = WorkoutResponseConfidence.HIGH,
    actualDistanceMeters = 1160.0, actualDurationSeconds = 515, actualPaceSecondsPerKm = 444.0,
    plannedDistanceMeters = 4800.0, plannedDurationSeconds = null,
    plannedPaceMinimumSecondsPerKm = 465.0, plannedPaceMaximumSecondsPerKm = 545.0,
    plannedRpeMinimum = 3, plannedRpeMaximum = 4, reportedRpe = 9,
    qualityWorkCompletionFraction = null, reasons = listOf("Original adherence reason", "Original cost reason"),
  )

  @Test fun longRunDefaultIsShortEvidenceBackedAndDoesNotChangeDomainValues() {
    val decision = CoachingDecision(CoachingAction.RECOVERY, -5, response.reasons, Instant.EPOCH)
    val state = RunDetailUiState(workoutResponse = response, coachingDecision = decision)
    val presentation = coachPresentation(state)
    assertEquals(4, presentation.observations.size)
    assertTrue(presentation.observations.first().startsWith("Stopped after"))
    assertTrue("Sustained walking/stopped time detected" in presentation.observations)
    assertTrue("Significant pain reported" in presentation.observations)
    assertEquals("Prioritize recovery before progressing the next session.", presentation.implication)
    assertEquals(listOf("Original adherence reason", "Original cost reason"), state.workoutResponse?.reasons)
    assertEquals(WorkoutCompletionOutcome.ABORTED, state.workoutResponse?.completion)
    assertEquals(SessionCost.HIGH, state.workoutResponse?.sessionCost)
  }

  @Test fun absentEvidenceDoesNotInventAnObservationOrRecommendation() {
    val presentation = coachPresentation(RunDetailUiState())
    assertTrue(presentation.observations.isEmpty())
    assertNull(presentation.implication)
  }

  @Test fun recommendationWordingUsesOnlyTheExistingDecisionAction() {
    val presentation = coachPresentation(RunDetailUiState(workoutResponse = response,
      coachingDecision = CoachingDecision(CoachingAction.MAINTAIN, 0, listOf("Existing reason"), Instant.EPOCH)))
    assertEquals("Maintain the current training load.", presentation.implication)
    assertTrue(presentation.observations.size <= 4)
  }
}
