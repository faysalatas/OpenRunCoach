package com.example.openruncoach.domain.workout

import java.time.Instant

enum class WorkoutAssociationDecision { HIGH, AMBIGUOUS, NO_MATCH }

data class WorkoutAssociationEvidence(
  val plannedWorkoutId: String,
  val workoutCreatedAt: Instant,
  val actualRunStart: Instant,
  val intendedDistanceMeters: Double?,
  val intendedDurationSeconds: Long?,
  val actualDistanceMeters: Double?,
  val actualDurationSeconds: Long,
)

/** Distance or duration compatibility is evidence; a shared calendar date alone is not. */
fun assessWorkoutAssociation(evidence: WorkoutAssociationEvidence): WorkoutAssociationDecision {
  if (evidence.workoutCreatedAt > evidence.actualRunStart) return WorkoutAssociationDecision.NO_MATCH
  val fraction = when {
    evidence.intendedDistanceMeters != null && evidence.intendedDistanceMeters > 0.0 &&
      evidence.actualDistanceMeters != null && evidence.actualDistanceMeters.isFinite() ->
      evidence.actualDistanceMeters / evidence.intendedDistanceMeters
    evidence.intendedDurationSeconds != null && evidence.intendedDurationSeconds > 0L ->
      evidence.actualDurationSeconds.toDouble() / evidence.intendedDurationSeconds
    else -> null
  }
  return when {
    fraction == null -> WorkoutAssociationDecision.NO_MATCH
    fraction in 0.80..1.20 -> WorkoutAssociationDecision.HIGH
    fraction in 0.50..1.50 -> WorkoutAssociationDecision.AMBIGUOUS
    else -> WorkoutAssociationDecision.NO_MATCH
  }
}

fun resolveWorkoutAssociation(decisions: List<Pair<String, WorkoutAssociationDecision>>): String? {
  val high = decisions.filter { it.second == WorkoutAssociationDecision.HIGH }
  if (high.size != 1) return null
  return high.single().first.takeIf { selected ->
    decisions.none { (id, decision) -> id != selected && decision != WorkoutAssociationDecision.NO_MATCH }
  }
}

fun hasUnambiguousRunDate(canonicalPhysicalRunIds: List<String>, actualPhysicalRunId: String): Boolean =
  canonicalPhysicalRunIds.distinct().singleOrNull() == actualPhysicalRunId
