package com.example.openruncoach.domain

import java.nio.charset.StandardCharsets
import java.util.UUID

data class CanonicalRunSelection(
  val runId: String,
  val duplicateGroupId: String?,
  val isCanonical: Boolean,
  val reason: String,
)

fun selectCanonicalRuns(
  runs: List<RunSummary>,
  preferredSourcePackage: String?,
): List<CanonicalRunSelection> {
  if (runs.isEmpty()) return emptyList()
  val runsById = runs.associateBy(RunSummary::healthConnectRecordId)
  val duplicateLinks = findPossibleDuplicates(runs).mapValues { (_, matches) ->
    matches.map(PossibleDuplicate::otherRunId).toSet()
  }
  val visited = mutableSetOf<String>()
  val selections = mutableListOf<CanonicalRunSelection>()

  for (run in runs.sortedBy(RunSummary::healthConnectRecordId)) {
    if (!visited.add(run.healthConnectRecordId)) continue
    val groupIds = mutableSetOf(run.healthConnectRecordId)
    val pending = ArrayDeque<String>().apply { add(run.healthConnectRecordId) }
    while (pending.isNotEmpty()) {
      duplicateLinks[pending.removeFirst()].orEmpty().forEach { linkedId ->
        if (groupIds.add(linkedId)) {
          visited.add(linkedId)
          pending.add(linkedId)
        }
      }
    }
    val group = groupIds.mapNotNull(runsById::get)
    if (group.size == 1) {
      selections += CanonicalRunSelection(
        runId = run.healthConnectRecordId,
        duplicateGroupId = null,
        isCanonical = true,
        reason = "No duplicate detected",
      )
      continue
    }

    val groupId = stableGroupId(groupIds)
    val (canonical, reason) = chooseCanonical(group, preferredSourcePackage)
    group.forEach { candidate ->
      selections += CanonicalRunSelection(
        runId = candidate.healthConnectRecordId,
        duplicateGroupId = groupId,
        isCanonical = candidate.healthConnectRecordId == canonical.healthConnectRecordId,
        reason = if (candidate.healthConnectRecordId == canonical.healthConnectRecordId) {
          reason
        } else {
          "Duplicate of ${canonical.healthConnectRecordId}; $reason"
        },
      )
    }
  }
  return selections
}

private fun chooseCanonical(
  group: List<RunSummary>,
  preferredSourcePackage: String?,
): Pair<RunSummary, String> {
  var candidates = group
  var reason: String? = null

  if (!preferredSourcePackage.isNullOrBlank()) {
    val preferred = candidates.filter { it.sourcePackageName == preferredSourcePackage }
    if (preferred.isNotEmpty()) {
      candidates = preferred
      reason = "Preferred workout source"
    }
  }

  val watchRecords = candidates.filter { it.sourceDeviceType == SourceDeviceType.WATCH }
  if (watchRecords.isNotEmpty() && watchRecords.size < candidates.size) {
    candidates = watchRecords
    if (reason == null) reason = "Recorded by a watch"
  }

  val highestCompleteness = candidates.maxOf(::completenessScore)
  val complete = candidates.filter { completenessScore(it) == highestCompleteness }
  if (complete.size < candidates.size) {
    candidates = complete
    if (reason == null) reason = "Most complete workout data"
  }

  if (candidates.size > 1 && candidates.all { it.sourceRecordLastModifiedTime != null }) {
    val earliest = candidates.minOf { requireNotNull(it.sourceRecordLastModifiedTime) }
    val earliestRecords = candidates.filter { it.sourceRecordLastModifiedTime == earliest }
    if (earliestRecords.size < candidates.size) {
      candidates = earliestRecords
      if (reason == null) reason = "Earliest source record metadata"
    }
  }

  val chosen = candidates.minWith(
    compareBy<RunSummary>({ it.sourcePackageName }, { it.healthConnectRecordId })
  )
  return chosen to (reason ?: "Deterministic source and record ordering")
}

private fun completenessScore(run: RunSummary): Int =
  (if (run.distanceMeters != null) 1 else 0) +
    (if (run.averageHeartRate != null || run.maxHeartRate != null) 1 else 0) +
    (if (run.sourceDeviceType != null || run.sourceDeviceManufacturer != null || run.sourceDeviceModel != null) 1 else 0)

private fun stableGroupId(ids: Set<String>): String =
  UUID.nameUUIDFromBytes(ids.sorted().joinToString("\u0000").toByteArray(StandardCharsets.UTF_8)).toString()
