package com.example.openruncoach.domain

import java.time.Duration
import kotlin.math.abs

data class PossibleDuplicate(
  val otherRunId: String,
  val otherSourceName: String,
  val startDifferenceSeconds: Long,
  val durationDifferenceSeconds: Long,
  val distanceDifferencePercent: Double,
  val differentSourceApplications: Boolean,
)

/** Diagnostic only: neither run is removed or merged. Missing or zero distance cannot qualify. */
fun findPossibleDuplicates(runs: List<RunSummary>): Map<String, List<PossibleDuplicate>> {
  val sorted = runs.sortedBy { it.startTime }
  val matches = mutableMapOf<String, MutableList<PossibleDuplicate>>()
  for (index in sorted.indices) {
    val first = sorted[index]
    for (nextIndex in index + 1 until sorted.size) {
      val second = sorted[nextIndex]
      val startDifference = Duration.between(first.startTime, second.startTime)
      if (startDifference > Duration.ofMinutes(2)) break
      val startDifferenceSeconds = startDifference.seconds
      if (first.healthConnectRecordId == second.healthConnectRecordId) continue

      val durationDifferenceSeconds = abs(first.durationSeconds - second.durationSeconds)
      if (durationDifferenceSeconds >= 120) continue
      val firstDistance = first.distanceMeters
      val secondDistance = second.distanceMeters
      if (firstDistance == null || secondDistance == null ||
        !firstDistance.isFinite() || !secondDistance.isFinite() ||
        firstDistance <= 0 || secondDistance <= 0
      ) continue
      val distanceDifferencePercent =
        abs(firstDistance - secondDistance) / (firstDistance / 2.0 + secondDistance / 2.0) * 100.0
      if (distanceDifferencePercent >= 5.0) continue

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
        )
      )
    }
  }
  return matches
}
