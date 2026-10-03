package com.example.openruncoach.domain.samsunghealth

import com.example.openruncoach.domain.metrics.MetricConfidence
import com.example.openruncoach.domain.route.RunRoute

/** Selects one confidently matched provider route; routes from different sessions are never merged. */
fun SamsungHealthInspection.preferredRoute(runId: String): RunRoute? {
  val canonicalRoute = identitySession?.route?.takeIf {
    identityMatch.status == SamsungWorkoutMatchStatus.MATCHED &&
      identityMatch.confidence == SamsungMatchConfidence.HIGH
  }
  if (canonicalRoute != null) {
    return canonicalRoute.copy(runId = runId, confidence = MetricConfidence.HIGH)
  }

  val fallbackRoute = enrichmentSession?.route?.takeIf {
    enrichmentMatch.status == SamsungWorkoutMatchStatus.MATCHED &&
      enrichmentMatch.confidence == SamsungMatchConfidence.HIGH &&
      enrichmentMatch.relationship == SamsungPhysicalWorkoutRelationship.EXACT_SESSION
  }
  return fallbackRoute?.copy(runId = runId, confidence = MetricConfidence.HIGH)
}
