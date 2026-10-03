package com.example.openruncoach.domain

import java.time.Duration
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

data class PossibleDuplicate(
  val otherRunId: String,
  val otherSourceName: String,
  val startDifferenceSeconds: Long,
  val durationDifferenceSeconds: Long,
  val distanceDifferencePercent: Double,
  val differentSourceApplications: Boolean,
  val decision: PhysicalRunMatchDecision = PhysicalRunMatchDecision.HIGH,
  val reasons: List<String> = emptyList(),
)

data class PhysicalRunMatch(
  val decision: PhysicalRunMatchDecision,
  val reasons: List<String>,
  val overlapSeconds: Long,
  val shorterWindowOverlap: Double,
  val longerWindowOverlap: Double,
  val distanceDifferencePercent: Double?,
)

/**
 * Deterministic provider-record comparison. Only HIGH is safe for automatic grouping;
 * AMBIGUOUS remains two physical runs until the user supplies stronger evidence.
 */
fun assessPhysicalRunMatch(first: RunSummary, second: RunSummary): PhysicalRunMatch {
  if (first.healthConnectRecordId == second.healthConnectRecordId) {
    return PhysicalRunMatch(PhysicalRunMatchDecision.SEPARATE, listOf("Same provider record"), 0, 0.0, 0.0, null)
  }
  val firstDuration = Duration.between(first.startTime, first.endTime).seconds.coerceAtLeast(1)
  val secondDuration = Duration.between(second.startTime, second.endTime).seconds.coerceAtLeast(1)
  val overlap = Duration.between(
    maxOf(first.startTime, second.startTime),
    minOf(first.endTime, second.endTime),
  ).seconds.coerceAtLeast(0)
  val shorterOverlap = overlap.toDouble() / min(firstDuration, secondDuration)
  val longerOverlap = overlap.toDouble() / max(firstDuration, secondDuration)
  val startDifference = abs(Duration.between(first.startTime, second.startTime).seconds)
  val differentSources = first.sourcePackageName.isNotBlank() && second.sourcePackageName.isNotBlank() &&
    first.sourcePackageName != second.sourcePackageName
  val distanceDifference = compatibleDistanceDifference(first.distanceMeters, second.distanceMeters)
  val distanceStrong = distanceDifference != null && distanceDifference <= 25.0
  val exactWindow = startDifference <= 30 && shorterOverlap >= 0.98 && longerOverlap >= 0.95
  val trimmedContainment = shorterOverlap >= 0.90 && longerOverlap >= 0.65

  val reasons = buildList {
    if (overlap > 0) add("Time windows overlap for $overlap seconds")
    if (trimmedContainment) add("One recording is substantially contained in the other")
    if (differentSources) add("Records come from different providers")
    if (distanceDifference != null) add("Distance differs by ${"%.1f".format(java.util.Locale.ROOT, distanceDifference)}%")
    if (distanceDifference == null) add("Comparable distance evidence is unavailable")
  }
  val decision = when {
    // Cross-provider copies may be trimmed, but still need strong session evidence.
    differentSources && trimmedContainment && distanceStrong -> PhysicalRunMatchDecision.HIGH
    // A provider can emit a replacement/trimmed record; require a very close start, strong
    // containment, and tighter distance agreement than a cross-provider copy.
    !differentSources && exactWindow && distanceDifference != null && distanceDifference <= 5.0 ->
      PhysicalRunMatchDecision.HIGH
    !differentSources && startDifference <= 60 && trimmedContainment &&
      distanceDifference != null && distanceDifference <= 15.0 -> PhysicalRunMatchDecision.HIGH
    overlap > 0 && (shorterOverlap >= 0.50 || startDifference <= 120) ->
      PhysicalRunMatchDecision.AMBIGUOUS
    else -> PhysicalRunMatchDecision.SEPARATE
  }
  return PhysicalRunMatch(decision, reasons.ifEmpty { listOf("No meaningful temporal overlap") }, overlap, shorterOverlap, longerOverlap, distanceDifference)
}

/** Diagnostic only: raw records are preserved even when HIGH-confidence records are grouped. */
fun findPossibleDuplicates(runs: List<RunSummary>): Map<String, List<PossibleDuplicate>> {
  val sorted = runs.sortedBy { it.startTime }
  val matches = mutableMapOf<String, MutableList<PossibleDuplicate>>()
  for (index in sorted.indices) {
    val first = sorted[index]
    for (nextIndex in index + 1 until sorted.size) {
      val second = sorted[nextIndex]
      val startDifference = Duration.between(first.startTime, second.startTime)
      if (startDifference > Duration.ofHours(6)) break
      val startDifferenceSeconds = abs(startDifference.seconds)
      if (first.healthConnectRecordId == second.healthConnectRecordId) continue
      val assessment = assessPhysicalRunMatch(first, second)
      if (assessment.decision == PhysicalRunMatchDecision.SEPARATE) continue
      val durationDifferenceSeconds = abs(first.durationSeconds - second.durationSeconds)
      val distanceDifferencePercent = assessment.distanceDifferencePercent ?: Double.NaN

      val differentSources = first.sourcePackageName.isNotBlank() &&
        second.sourcePackageName.isNotBlank() &&
        first.sourcePackageName != second.sourcePackageName
      matches.getOrPut(first.healthConnectRecordId) { mutableListOf() }.add(
        PossibleDuplicate(
          otherRunId = second.healthConnectRecordId,
          otherSourceName = second.displaySourceName,
          startDifferenceSeconds = startDifferenceSeconds,
          durationDifferenceSeconds = durationDifferenceSeconds,
          distanceDifferencePercent = distanceDifferencePercent,
          differentSourceApplications = differentSources,
          decision = assessment.decision,
          reasons = assessment.reasons,
        )
      )
      matches.getOrPut(second.healthConnectRecordId) { mutableListOf() }.add(
        PossibleDuplicate(
          otherRunId = first.healthConnectRecordId,
          otherSourceName = first.displaySourceName,
          startDifferenceSeconds = startDifferenceSeconds,
          durationDifferenceSeconds = durationDifferenceSeconds,
          distanceDifferencePercent = distanceDifferencePercent,
          differentSourceApplications = differentSources,
          decision = assessment.decision,
          reasons = assessment.reasons,
        )
      )
    }
  }
  return matches
}

private fun compatibleDistanceDifference(first: Double?, second: Double?): Double? {
  if (first == null || second == null || !first.isFinite() || !second.isFinite() || first <= 0.0 || second <= 0.0) {
    return null
  }
  return abs(first - second) / ((first + second) / 2.0) * 100.0
}
