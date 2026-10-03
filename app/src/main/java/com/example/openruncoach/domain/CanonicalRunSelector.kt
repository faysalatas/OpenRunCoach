package com.example.openruncoach.domain

data class CanonicalRunSelection(
  val runId: String,
  val physicalRunId: String,
  val duplicateGroupId: String?,
  val isCanonical: Boolean,
  val matchDecision: PhysicalRunMatchDecision,
  val reason: String,
)

fun selectCanonicalRuns(
  runs: List<RunSummary>,
  preferredSourcePackage: String?,
): List<CanonicalRunSelection> {
  if (runs.isEmpty()) return emptyList()
  val sortedRuns = runs.sortedBy(RunSummary::healthConnectRecordId)
  val assessments = buildMap<Pair<String, String>, PhysicalRunMatch> {
    sortedRuns.forEachIndexed { index, first ->
      sortedRuns.drop(index + 1).forEach { second ->
        put(orderedPair(first.healthConnectRecordId, second.healthConnectRecordId), assessPhysicalRunMatch(first, second))
      }
    }
  }
  // Requiring HIGH evidence against every member prevents transitive A↔B↔C over-merges.
  val groups = mutableListOf<MutableList<RunSummary>>()
  sortedRuns.forEach { run ->
    val group = groups.firstOrNull { members -> members.all { member ->
      assessments[orderedPair(run.healthConnectRecordId, member.healthConnectRecordId)]?.decision ==
        PhysicalRunMatchDecision.HIGH
    } }
    if (group == null) groups.add(mutableListOf(run)) else group.add(run)
  }
  val selections = mutableListOf<CanonicalRunSelection>()
  for (group in groups) {
    val run = group.singleOrNull()
    if (group.size == 1) {
      val ambiguous = assessments.entries.firstOrNull { (pair, match) ->
        (pair.first == run!!.healthConnectRecordId || pair.second == run.healthConnectRecordId) &&
          match.decision == PhysicalRunMatchDecision.AMBIGUOUS
      }?.value
      selections += CanonicalRunSelection(
        runId = run!!.healthConnectRecordId,
        physicalRunId = run.physicalRunId,
        duplicateGroupId = null,
        isCanonical = true,
        matchDecision = ambiguous?.decision ?: PhysicalRunMatchDecision.SEPARATE,
        reason = ambiguous?.reasons?.joinToString("; ") ?: "No matching provider representation detected",
      )
      continue
    }

    val (canonical, reason) = chooseCanonical(group, preferredSourcePackage)
    val physicalRunId = stablePhysicalRunId(group, canonical)
    val matchReasons = group.flatMapIndexed { index, first ->
      group.drop(index + 1).flatMap { second ->
        assessments.getValue(orderedPair(first.healthConnectRecordId, second.healthConnectRecordId)).reasons
      }
    }.distinct().joinToString("; ")
    group.forEach { candidate ->
      selections += CanonicalRunSelection(
        runId = candidate.healthConnectRecordId,
        physicalRunId = physicalRunId,
        duplicateGroupId = physicalRunId,
        isCanonical = candidate.healthConnectRecordId == canonical.healthConnectRecordId,
        matchDecision = PhysicalRunMatchDecision.HIGH,
        reason = if (candidate.healthConnectRecordId == canonical.healthConnectRecordId) {
          "$reason; $matchReasons"
        } else {
          "Provider representation of $physicalRunId; $matchReasons"
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

  if (group.map(RunSummary::sourcePackageName).distinct().size == 1 &&
    candidates.size > 1 && candidates.all { it.sourceRecordLastModifiedTime != null }
  ) {
    val latest = candidates.maxOf { requireNotNull(it.sourceRecordLastModifiedTime) }
    val editedRecords = candidates.filter { it.sourceRecordLastModifiedTime == latest }
    if (editedRecords.size < candidates.size) {
      candidates = editedRecords
      reason = "Latest provider edit"
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

private fun stablePhysicalRunId(group: List<RunSummary>, canonical: RunSummary): String {
  val persisted = group.groupingBy(RunSummary::physicalRunId).eachCount()
    .filterValues { it > 1 }.keys.sorted().firstOrNull()
  val establishedAlias = group.filter { it.physicalRunId != it.healthConnectRecordId }
    .map(RunSummary::physicalRunId).sorted().firstOrNull()
  return persisted ?: establishedAlias ?: canonical.physicalRunId
}

private fun orderedPair(first: String, second: String): Pair<String, String> =
  if (first <= second) first to second else second to first
