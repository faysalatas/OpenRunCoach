package com.example.openruncoach.domain.samsunghealth

import com.example.openruncoach.domain.metrics.MetricConfidence
import com.example.openruncoach.domain.route.RunRoute
import com.example.openruncoach.domain.route.RunRoutePoint
import java.time.Instant

/** Selects one confidently matched provider route; routes from different sessions are never merged. */
fun SamsungHealthInspection.preferredRoute(
  runId: String,
  canonicalStart: Instant,
  canonicalEnd: Instant,
): RunRoute? {
  val canonicalRoute = identitySession?.route?.takeIf {
    identityMatch.status == SamsungWorkoutMatchStatus.MATCHED &&
      identityMatch.confidence == SamsungMatchConfidence.HIGH
  }
  canonicalRoute?.forCanonicalWindow(runId, canonicalStart, canonicalEnd)?.let { return it }

  val fallbackRoute = enrichmentSession?.route?.takeIf {
    enrichmentMatch.status == SamsungWorkoutMatchStatus.MATCHED &&
      enrichmentMatch.confidence == SamsungMatchConfidence.HIGH &&
      enrichmentMatch.relationship in setOf(
        SamsungPhysicalWorkoutRelationship.EXACT_SESSION,
        SamsungPhysicalWorkoutRelationship.OVERLAPPING_SESSION,
      )
  }
  fallbackRoute?.forCanonicalWindow(runId, canonicalStart, canonicalEnd)?.let { return it }
  // The best sensor recording need not be the route provider. Only use a unique
  // route from the matcher's existing HIGH assessments; ambiguity never permits borrowing.
  if (enrichmentMatch.status != SamsungWorkoutMatchStatus.MATCHED ||
    enrichmentMatch.confidence != SamsungMatchConfidence.HIGH
  ) return null
  val trustedRouteCandidates = enrichmentMatch.candidateScores.filter {
    it.physicalConfidence == SamsungMatchConfidence.HIGH &&
      it.relationship in setOf(
        SamsungPhysicalWorkoutRelationship.EXACT_SESSION,
        SamsungPhysicalWorkoutRelationship.OVERLAPPING_SESSION,
      )
  }.mapNotNull { score ->
    plausibleSessions.singleOrNull { it.candidate.id == score.candidate.id }?.route
      ?.forCanonicalWindow(runId, canonicalStart, canonicalEnd)
  }
  return trustedRouteCandidates.singleOrNull()
}

private fun RunRoute.forCanonicalWindow(
  canonicalRunId: String,
  canonicalStart: Instant,
  canonicalEnd: Instant,
): RunRoute? {
  if (canonicalEnd.isBefore(canonicalStart)) return null
  val clipped = points.asSequence()
    .filter { !it.timestamp.isBefore(canonicalStart) && !it.timestamp.isAfter(canonicalEnd) }
    .sortedWith(compareBy<RunRoutePoint>(RunRoutePoint::timestamp).thenBy(RunRoutePoint::sequence))
    .mapIndexed { index, point -> point.copy(sequence = index) }
    .toList()
  if (clipped.size < 2) return null
  return copy(
    runId = canonicalRunId,
    confidence = MetricConfidence.HIGH,
    points = clipped,
    rejectedPointCount = rejectedPointCount + (points.size - clipped.size),
  )
}
