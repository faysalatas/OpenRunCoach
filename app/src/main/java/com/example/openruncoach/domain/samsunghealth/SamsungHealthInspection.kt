package com.example.openruncoach.domain.samsunghealth

import com.example.openruncoach.domain.metrics.TimedMetricSample
import com.example.openruncoach.domain.metrics.movingCadenceAverage
import com.example.openruncoach.domain.route.RunRoute
import java.time.Duration
import java.time.Instant
import kotlin.math.abs

enum class SamsungExerciseKind { RUNNING, TRACK_RUNNING, OTHER }

data class SamsungWorkoutTarget(
  val startTime: Instant,
  val endTime: Instant,
  val durationSeconds: Long,
  val distanceMeters: Double?,
  val canonicalSourceAppId: String? = null,
  val preferWatchSource: Boolean = false,
  val averageHeartRate: Double? = null,
  val maxHeartRate: Double? = null,
  val averageSpeedMetersPerSecond: Double? = null,
  val vo2Max: Double? = null,
  val runId: String? = null,
)

enum class SamsungSourceDeviceType { WATCH, MOBILE, RING, BAND, ACCESSORY, OTHER, UNKNOWN }

data class SamsungCandidateSource(
  val uid: String,
  val dataPointStartTime: Instant?,
  val dataPointEndTime: Instant?,
  val updateTime: Instant?,
  val applicationId: String?,
  val deviceId: String?,
  val deviceType: SamsungSourceDeviceType = SamsungSourceDeviceType.UNKNOWN,
  val manufacturer: String? = null,
  val model: String? = null,
  val deviceName: String? = null,
)

data class SamsungCandidateMetrics(
  val averageHeartRate: Double? = null,
  val maxHeartRate: Double? = null,
  val averageSpeedMetersPerSecond: Double? = null,
  val vo2Max: Double? = null,
  val meanCadence: Double? = null,
  val maxCadence: Double? = null,
  val routePointCount: Int? = null,
  val routeAltitudePointCount: Int? = null,
  val routeAccuracyPointCount: Int? = null,
  val routeRejectedPointCount: Int? = null,
  val routeFirstTimestamp: Instant? = null,
  val routeLastTimestamp: Instant? = null,
  val exerciseLogExists: Boolean = false,
  val totalLogSamples: Int = 0,
  val heartRateSampleCount: Int = 0,
  val speedSampleCount: Int = 0,
  val cadenceSampleCount: Int = 0,
  val powerSampleCount: Int = 0,
  val powerAvailable: Boolean = false,
  val elevationAvailable: Boolean = false,
)

data class SamsungExerciseCandidate(
  val id: String,
  val startTime: Instant,
  val endTime: Instant,
  val durationSeconds: Long,
  val distanceMeters: Double?,
  val exerciseKind: SamsungExerciseKind,
  val source: SamsungCandidateSource = SamsungCandidateSource(
    uid = id,
    dataPointStartTime = startTime,
    dataPointEndTime = endTime,
    updateTime = null,
    applicationId = null,
    deviceId = null,
  ),
  val metrics: SamsungCandidateMetrics = SamsungCandidateMetrics(),
)

data class SamsungWorkoutMatchTolerance(
  val startSeconds: Long = 120,
  val durationSeconds: Long = 120,
  val distanceFraction: Double = 0.05,
)

data class SamsungEnrichmentThresholds(
  val exact: SamsungWorkoutMatchTolerance = SamsungWorkoutMatchTolerance(),
  val overlapStartSeconds: Double = 5.0,
  val minimumCanonicalCoverage: Double = 0.95,
  val minimumExerciseLogSamples: Int = 60,
)

enum class SamsungWorkoutMatchStatus { MATCHED, NOT_FOUND, AMBIGUOUS }

enum class SamsungMatchConfidence { HIGH, MEDIUM, LOW, AMBIGUOUS }

enum class SamsungPhysicalWorkoutRelationship { EXACT_SESSION, OVERLAPPING_SESSION, NO_MATCH, AMBIGUOUS }

enum class SamsungMatchReasonKind { POSITIVE, NEGATIVE, NEUTRAL }

data class SamsungMatchReason(
  val kind: SamsungMatchReasonKind,
  val text: String,
)

data class SamsungCandidateScore(
  val candidate: SamsungExerciseCandidate,
  val score: Int,
  val deviceScore: Int,
  val sourceScore: Int,
  val startScore: Int,
  val durationScore: Int,
  val distanceScore: Int,
  val sharedMetricScore: Int,
  val reasons: List<SamsungMatchReason>,
)

data class SamsungWorkoutMatchResult(
  val status: SamsungWorkoutMatchStatus,
  val selected: SamsungExerciseCandidate? = null,
  val plausibleCandidates: List<SamsungExerciseCandidate> = emptyList(),
  val confidence: SamsungMatchConfidence = SamsungMatchConfidence.AMBIGUOUS,
  val candidateScores: List<SamsungCandidateScore> = emptyList(),
  val reasons: List<SamsungMatchReason> = emptyList(),
  val manualSelection: Boolean = false,
)

data class SamsungEnrichmentCandidateScore(
  val candidate: SamsungExerciseCandidate,
  val relationship: SamsungPhysicalWorkoutRelationship,
  val canonicalCoverage: Double,
  val overlapDurationSeconds: Double,
  val physicalConfidence: SamsungMatchConfidence,
  val richnessScore: Int,
  val reasons: List<SamsungMatchReason>,
)

data class SamsungEnrichmentMatchResult(
  val status: SamsungWorkoutMatchStatus,
  val selected: SamsungExerciseCandidate? = null,
  val plausibleCandidates: List<SamsungExerciseCandidate> = emptyList(),
  val confidence: SamsungMatchConfidence = SamsungMatchConfidence.AMBIGUOUS,
  val candidateScores: List<SamsungEnrichmentCandidateScore> = emptyList(),
  val reasons: List<SamsungMatchReason> = emptyList(),
  val manualSelection: Boolean = false,
  val relationship: SamsungPhysicalWorkoutRelationship = SamsungPhysicalWorkoutRelationship.NO_MATCH,
  val canonicalCoverage: Double? = null,
  val overlapDurationSeconds: Double? = null,
)

/** Matches the provider copy of the canonical Health Connect session. */
fun matchSamsungIdentity(
  target: SamsungWorkoutTarget,
  candidates: List<SamsungExerciseCandidate>,
  tolerance: SamsungWorkoutMatchTolerance = SamsungWorkoutMatchTolerance(),
): SamsungWorkoutMatchResult {
  val source = target.canonicalSourceAppId?.takeIf(String::isNotBlank)
  val providerCandidates = if (source == null) candidates else {
    candidates.filter { it.source.applicationId == source }
  }
  return matchSamsungWorkout(target, providerCandidates, tolerance)
}

/** Selects a trusted, sensor-rich representation of the same physical workout. */
fun matchSamsungEnrichment(
  target: SamsungWorkoutTarget,
  candidates: List<SamsungExerciseCandidate>,
  thresholds: SamsungEnrichmentThresholds = SamsungEnrichmentThresholds(),
  identityCandidateId: String? = null,
  manualSelectionCandidateId: String? = null,
): SamsungEnrichmentMatchResult {
  val scores = candidates.mapNotNull { candidate ->
    val assessment = assessEnrichmentRelationship(target, candidate, thresholds)
    if (assessment.relationship == SamsungPhysicalWorkoutRelationship.NO_MATCH) return@mapNotNull null
    val reasons = physicalMatchReasons(target, candidate, assessment) + enrichmentReasons(candidate, identityCandidateId)
    SamsungEnrichmentCandidateScore(
      candidate = candidate,
      relationship = assessment.relationship,
      canonicalCoverage = assessment.canonicalCoverage,
      overlapDurationSeconds = assessment.overlapDurationSeconds,
      physicalConfidence = assessment.confidence,
      richnessScore = enrichmentRichnessScore(candidate),
      reasons = reasons,
    )
  }.sortedWith(
    compareByDescending<SamsungEnrichmentCandidateScore> { it.richnessScore }
      .thenByDescending { it.physicalConfidence.rank }
      .thenBy { abs(Duration.between(target.startTime, it.candidate.startTime).toMillis()) }
      .thenBy { it.candidate.id }
  )
  val plausible = scores.map(SamsungEnrichmentCandidateScore::candidate)
  val trusted = scores.filter {
    it.physicalConfidence == SamsungMatchConfidence.HIGH ||
      it.physicalConfidence == SamsungMatchConfidence.MEDIUM
  }
  val useful = trusted.filter { score ->
    if (score.relationship == SamsungPhysicalWorkoutRelationship.OVERLAPPING_SESSION) {
      score.candidate.metrics.hasTimeSeriesEnrichment
    } else score.candidate.metrics.hasSensorEnrichment
  }
  val manual = manualSelectionCandidateId?.let { id -> useful.firstOrNull { it.candidate.id == id } }
  if (manual != null) {
    val reason = positive("Manual diagnostic enrichment selection")
    return SamsungEnrichmentMatchResult(
      status = SamsungWorkoutMatchStatus.MATCHED,
      selected = manual.candidate,
      plausibleCandidates = plausible,
      confidence = SamsungMatchConfidence.HIGH,
      candidateScores = scores,
      reasons = listOf(reason) + manual.reasons,
      manualSelection = true,
      relationship = manual.relationship,
      canonicalCoverage = manual.canonicalCoverage,
      overlapDurationSeconds = manual.overlapDurationSeconds,
    )
  }
  if (useful.isEmpty()) {
    return SamsungEnrichmentMatchResult(
      status = SamsungWorkoutMatchStatus.NOT_FOUND,
      plausibleCandidates = plausible,
      confidence = if (plausible.isEmpty()) SamsungMatchConfidence.LOW else SamsungMatchConfidence.MEDIUM,
      candidateScores = scores,
      reasons = listOf(neutral("No additional Samsung Health sensor recording was found for this workout")),
      relationship = SamsungPhysicalWorkoutRelationship.NO_MATCH,
    )
  }
  val watchCandidates = useful.filter { it.candidate.source.deviceType == SamsungSourceDeviceType.WATCH }
  if (watchCandidates.size > 1) {
    return SamsungEnrichmentMatchResult(
      status = SamsungWorkoutMatchStatus.AMBIGUOUS,
      plausibleCandidates = plausible,
      confidence = SamsungMatchConfidence.AMBIGUOUS,
      candidateScores = scores,
      reasons = listOf(neutral("Multiple trusted watch recordings could enrich this workout")),
      relationship = SamsungPhysicalWorkoutRelationship.AMBIGUOUS,
    )
  }
  val preferred = watchCandidates.singleOrNull() ?: useful.first()
  val second = useful.firstOrNull { it.candidate.id != preferred.candidate.id }
  if (second != null && watchCandidates.isEmpty() && preferred.richnessScore - second.richnessScore < 100) {
    return SamsungEnrichmentMatchResult(
      status = SamsungWorkoutMatchStatus.AMBIGUOUS,
      plausibleCandidates = plausible,
      confidence = SamsungMatchConfidence.AMBIGUOUS,
      candidateScores = scores,
      reasons = listOf(neutral("Sensor evidence is too similar for automatic enrichment selection")),
      relationship = SamsungPhysicalWorkoutRelationship.AMBIGUOUS,
    )
  }
  return SamsungEnrichmentMatchResult(
    status = SamsungWorkoutMatchStatus.MATCHED,
    selected = preferred.candidate,
    plausibleCandidates = plausible,
    confidence = preferred.physicalConfidence,
    candidateScores = scores,
    reasons = preferred.reasons,
    relationship = preferred.relationship,
    canonicalCoverage = preferred.canonicalCoverage,
    overlapDurationSeconds = preferred.overlapDurationSeconds,
  )
}

fun matchSamsungWorkout(
  target: SamsungWorkoutTarget,
  candidates: List<SamsungExerciseCandidate>,
  tolerance: SamsungWorkoutMatchTolerance = SamsungWorkoutMatchTolerance(),
  manualSelectionCandidateId: String? = null,
): SamsungWorkoutMatchResult {
  val plausible = plausiblePhysicalCandidates(target, candidates, tolerance)

  val scores = plausible.map { scoreCandidate(target, it, tolerance) }
    .sortedWith(
      compareByDescending<SamsungCandidateScore> { it.score }
        .thenBy { abs(Duration.between(target.startTime, it.candidate.startTime).toMillis()) }
        .thenBy { abs(target.durationSeconds - it.candidate.durationSeconds) }
        .thenBy { distanceDifferenceFraction(target.distanceMeters, it.candidate.distanceMeters) }
        .thenBy { it.candidate.id }
    )

  val manual = manualSelectionCandidateId?.let { selectedId ->
    scores.firstOrNull { it.candidate.id == selectedId }
  }
  if (manual != null) {
    val manualReason = SamsungMatchReason(SamsungMatchReasonKind.POSITIVE, "Manual diagnostic selection")
    return SamsungWorkoutMatchResult(
      status = SamsungWorkoutMatchStatus.MATCHED,
      selected = manual.candidate,
      plausibleCandidates = scores.map { it.candidate },
      confidence = SamsungMatchConfidence.HIGH,
      candidateScores = scores.map { if (it.candidate.id == manual.candidate.id) it.copy(reasons = listOf(manualReason) + it.reasons) else it },
      reasons = listOf(manualReason) + manual.reasons,
      manualSelection = true,
    )
  }

  return when (scores.size) {
    0 -> SamsungWorkoutMatchResult(
      status = SamsungWorkoutMatchStatus.NOT_FOUND,
      confidence = SamsungMatchConfidence.LOW,
    )
    1 -> SamsungWorkoutMatchResult(
      status = SamsungWorkoutMatchStatus.MATCHED,
      selected = scores.single().candidate,
      plausibleCandidates = listOf(scores.single().candidate),
      confidence = SamsungMatchConfidence.HIGH,
      candidateScores = scores,
      reasons = scores.single().reasons,
    )
    else -> selectScoredMatch(scores)
  }
}

private fun plausiblePhysicalCandidates(
  target: SamsungWorkoutTarget,
  candidates: List<SamsungExerciseCandidate>,
  tolerance: SamsungWorkoutMatchTolerance,
): List<SamsungExerciseCandidate> = candidates.asSequence()
  .filter { it.exerciseKind == SamsungExerciseKind.RUNNING || it.exerciseKind == SamsungExerciseKind.TRACK_RUNNING }
  .filter { abs(Duration.between(target.startTime, it.startTime).seconds) <= tolerance.startSeconds }
  .filter { abs(target.durationSeconds - it.durationSeconds) <= tolerance.durationSeconds }
  .filter { it.startTime < target.endTime && it.endTime > target.startTime }
  .filter { candidate ->
    val expected = target.distanceMeters
    val actual = candidate.distanceMeters
    expected == null || actual == null || expected <= 0.0 ||
      abs(expected - actual) / expected <= tolerance.distanceFraction
  }
  .toList()

private data class EnrichmentRelationshipAssessment(
  val relationship: SamsungPhysicalWorkoutRelationship,
  val canonicalCoverage: Double,
  val overlapDurationSeconds: Double,
  val startDifferenceSeconds: Double,
  val endDifferenceSeconds: Double,
  val confidence: SamsungMatchConfidence,
)

private fun assessEnrichmentRelationship(
  target: SamsungWorkoutTarget,
  candidate: SamsungExerciseCandidate,
  thresholds: SamsungEnrichmentThresholds,
): EnrichmentRelationshipAssessment {
  val noMatch = EnrichmentRelationshipAssessment(
    SamsungPhysicalWorkoutRelationship.NO_MATCH, 0.0, 0.0, Double.POSITIVE_INFINITY,
    Double.POSITIVE_INFINITY, SamsungMatchConfidence.LOW,
  )
  if (candidate.exerciseKind != SamsungExerciseKind.RUNNING &&
    candidate.exerciseKind != SamsungExerciseKind.TRACK_RUNNING
  ) return noMatch
  val startDifference = abs(Duration.between(target.startTime, candidate.startTime).toMillis()) / 1_000.0
  val endDifference = abs(Duration.between(target.endTime, candidate.endTime).toMillis()) / 1_000.0
  val overlapStart = maxOf(target.startTime, candidate.startTime)
  val overlapEnd = minOf(target.endTime, candidate.endTime)
  val overlapMillis = Duration.between(overlapStart, overlapEnd).toMillis().coerceAtLeast(0)
  val canonicalMillis = Duration.between(target.startTime, target.endTime).toMillis().coerceAtLeast(1)
  val coverage = (overlapMillis.toDouble() / canonicalMillis).coerceIn(0.0, 1.0)
  val distanceDifference = distanceDifferenceFraction(target.distanceMeters, candidate.distanceMeters)
  val exact = startDifference <= thresholds.exact.startSeconds &&
    endDifference <= thresholds.exact.durationSeconds &&
    (distanceDifference == Double.MAX_VALUE || distanceDifference <= thresholds.exact.distanceFraction)
  if (exact) {
    val confidence = if (
      startDifference <= 30.0 && endDifference <= 30.0 &&
      (distanceDifference == Double.MAX_VALUE || distanceDifference <= 0.02)
    ) SamsungMatchConfidence.HIGH else SamsungMatchConfidence.MEDIUM
    return EnrichmentRelationshipAssessment(
      SamsungPhysicalWorkoutRelationship.EXACT_SESSION,
      coverage,
      overlapMillis / 1_000.0,
      startDifference,
      endDifference,
      confidence,
    )
  }
  val overlapping = startDifference <= thresholds.overlapStartSeconds &&
    coverage >= thresholds.minimumCanonicalCoverage &&
    candidate.source.deviceType == SamsungSourceDeviceType.WATCH &&
    candidate.metrics.exerciseLogExists &&
    candidate.metrics.totalLogSamples >= thresholds.minimumExerciseLogSamples &&
    endDifference > thresholds.exact.durationSeconds
  if (!overlapping) return noMatch.copy(
    canonicalCoverage = coverage,
    overlapDurationSeconds = overlapMillis / 1_000.0,
    startDifferenceSeconds = startDifference,
    endDifferenceSeconds = endDifference,
  )
  return EnrichmentRelationshipAssessment(
    SamsungPhysicalWorkoutRelationship.OVERLAPPING_SESSION,
    coverage,
    overlapMillis / 1_000.0,
    startDifference,
    endDifference,
    SamsungMatchConfidence.HIGH,
  )
}

private fun physicalMatchReasons(
  target: SamsungWorkoutTarget,
  candidate: SamsungExerciseCandidate,
  assessment: EnrichmentRelationshipAssessment,
): List<SamsungMatchReason> {
  val reasons = mutableListOf(positive("Running activity"))
  reasons += positive(
    if (assessment.startDifferenceSeconds < 1.0) "Start time differs by <1 sec"
    else "Start time differs by ${"%.1f".format(java.util.Locale.ROOT, assessment.startDifferenceSeconds)} sec"
  )
  reasons += if (assessment.relationship == SamsungPhysicalWorkoutRelationship.OVERLAPPING_SESSION) {
    positive("Canonical activity is ${"%.1f".format(java.util.Locale.ROOT, assessment.canonicalCoverage * 100)}% covered by Samsung session")
  } else {
    positive("Session boundaries and distance are sufficiently close")
  }
  val distanceDifference = distanceDifference(target.distanceMeters, candidate.distanceMeters)
  if (distanceDifference != null) reasons += neutral(
    "Samsung total distance differs by ${"%.1f".format(java.util.Locale.ROOT, distanceDifference)} m"
  ) else reasons += neutral("Distance comparison unavailable")
  val targetApp = target.canonicalSourceAppId
  val candidateApp = candidate.source.applicationId
  if (!targetApp.isNullOrBlank() && !candidateApp.isNullOrBlank() && targetApp != candidateApp) {
    reasons += neutral("Different source application is allowed for enrichment: $candidateApp")
  }
  return reasons
}

private fun enrichmentReasons(
  candidate: SamsungExerciseCandidate,
  identityCandidateId: String?,
): List<SamsungMatchReason> = buildList {
  val metrics = candidate.metrics
  if (candidate.id == identityCandidateId) add(neutral("This is also the exact provider copy"))
  if (candidate.source.deviceType == SamsungSourceDeviceType.WATCH) add(positive("Watch recording source"))
  if (metrics.exerciseLogExists) add(positive("ExerciseLog available (${metrics.totalLogSamples} samples)"))
  if (metrics.heartRateSampleCount > 0) add(positive("${metrics.heartRateSampleCount} heart-rate samples"))
  if (metrics.speedSampleCount > 0) add(positive("${metrics.speedSampleCount} speed samples"))
  if (metrics.cadenceSampleCount > 0) add(positive("${metrics.cadenceSampleCount} cadence samples"))
  if (metrics.powerSampleCount > 0) add(positive("${metrics.powerSampleCount} power samples"))
  else if (metrics.powerAvailable) add(positive("Power summary available"))
  if ((metrics.routePointCount ?: 0) > 0) add(positive("${metrics.routePointCount} route points"))
  if (metrics.vo2Max != null) add(positive("VO₂max available"))
  if (metrics.elevationAvailable) add(positive("Elevation available"))
}

private fun enrichmentRichnessScore(candidate: SamsungExerciseCandidate): Int {
  val metrics = candidate.metrics
  return (if (candidate.source.deviceType == SamsungSourceDeviceType.WATCH) 500 else 0) +
    (if (metrics.exerciseLogExists) 120 else 0) +
    (if (metrics.heartRateSampleCount > 0 || metrics.averageHeartRate != null) 80 else 0) +
    (if (metrics.speedSampleCount > 0 || metrics.averageSpeedMetersPerSecond != null) 70 else 0) +
    (if (metrics.cadenceSampleCount > 0 || metrics.meanCadence != null) 80 else 0) +
    (if (metrics.powerSampleCount > 0 || metrics.powerAvailable) 40 else 0) +
    (if ((metrics.routePointCount ?: 0) >= 2) 100 else 0) +
    (if (metrics.vo2Max != null) 60 else 0) +
    (if (metrics.elevationAvailable) 60 else 0)
}

private val SamsungCandidateMetrics.hasSensorEnrichment: Boolean
  get() = averageHeartRate != null || maxHeartRate != null || averageSpeedMetersPerSecond != null ||
    vo2Max != null || meanCadence != null || maxCadence != null || heartRateSampleCount > 0 ||
    speedSampleCount > 0 || cadenceSampleCount > 0 || powerSampleCount > 0 || powerAvailable ||
    elevationAvailable || (routePointCount ?: 0) >= 2

private val SamsungCandidateMetrics.hasTimeSeriesEnrichment: Boolean
  get() = heartRateSampleCount > 0 || speedSampleCount > 0 || cadenceSampleCount > 0 || powerSampleCount > 0 ||
    (routePointCount ?: 0) >= 2

private val SamsungMatchConfidence.rank: Int
  get() = when (this) {
    SamsungMatchConfidence.HIGH -> 3
    SamsungMatchConfidence.MEDIUM -> 2
    SamsungMatchConfidence.LOW -> 1
    SamsungMatchConfidence.AMBIGUOUS -> 0
  }

private fun selectScoredMatch(scores: List<SamsungCandidateScore>): SamsungWorkoutMatchResult {
  val first = scores[0]
  val second = scores[1]
  val totalGap = first.score - second.score
  val provenanceGap = (first.deviceScore + first.sourceScore) - (second.deviceScore + second.sourceScore)
  val nonDistanceSimilarityGap =
    (first.startScore + first.durationScore + first.sharedMetricScore) -
      (second.startScore + second.durationScore + second.sharedMetricScore)
  val confidence = when {
    provenanceGap >= 500 -> SamsungMatchConfidence.HIGH
    totalGap >= 160 && nonDistanceSimilarityGap >= 80 -> SamsungMatchConfidence.MEDIUM
    totalGap >= 80 && nonDistanceSimilarityGap >= 60 -> SamsungMatchConfidence.LOW
    else -> SamsungMatchConfidence.AMBIGUOUS
  }
  return if (confidence == SamsungMatchConfidence.AMBIGUOUS) {
    SamsungWorkoutMatchResult(
      status = SamsungWorkoutMatchStatus.AMBIGUOUS,
      plausibleCandidates = scores.map { it.candidate },
      confidence = confidence,
      candidateScores = scores,
      reasons = listOf(SamsungMatchReason(
        SamsungMatchReasonKind.NEUTRAL,
        "Candidate evidence is too similar for automatic selection",
      )),
    )
  } else {
    SamsungWorkoutMatchResult(
      status = SamsungWorkoutMatchStatus.MATCHED,
      selected = first.candidate,
      plausibleCandidates = scores.map { it.candidate },
      confidence = confidence,
      candidateScores = scores,
      reasons = first.reasons,
    )
  }
}

private fun scoreCandidate(
  target: SamsungWorkoutTarget,
  candidate: SamsungExerciseCandidate,
  tolerance: SamsungWorkoutMatchTolerance,
): SamsungCandidateScore {
  val reasons = mutableListOf<SamsungMatchReason>()
  var deviceScore = 0
  if (target.preferWatchSource) {
    when (candidate.source.deviceType) {
      SamsungSourceDeviceType.WATCH -> {
        deviceScore = 1_000
        reasons += positive("Galaxy Watch source")
      }
      SamsungSourceDeviceType.MOBILE -> {
        deviceScore = -1_000
        reasons += negative("Phone source for a watch-recorded canonical run")
      }
      else -> reasons += neutral("Source device is not identified as watch or mobile")
    }
  } else {
    reasons += neutral("No watch-source preference applies to this canonical run")
  }

  val targetApp = target.canonicalSourceAppId?.takeIf(String::isNotBlank)
  val candidateApp = candidate.source.applicationId?.takeIf(String::isNotBlank)
  val sourceScore = when {
    targetApp == null || candidateApp == null -> 0
    targetApp == candidateApp -> 500.also { reasons += positive("Source application matches $targetApp") }
    else -> (-100).also { reasons += negative("Different source application: $candidateApp") }
  }

  val startDifferenceMillis = abs(Duration.between(target.startTime, candidate.startTime).toMillis())
  val startScore = scaledScore(startDifferenceMillis.toDouble(), tolerance.startSeconds * 1_000.0, 300)
  reasons += if (startDifferenceMillis == 0L) positive("Exact start-time match")
  else neutral("Start time differs by ${formatSeconds(startDifferenceMillis)} sec")

  val durationDifference = abs(target.durationSeconds - candidate.durationSeconds)
  val durationScore = scaledScore(durationDifference.toDouble(), tolerance.durationSeconds.toDouble(), 250)
  reasons += if (durationDifference == 0L) positive("Exact duration match")
  else neutral("Duration differs by $durationDifference sec")

  val distanceDifference = distanceDifference(target.distanceMeters, candidate.distanceMeters)
  val distanceScore = if (distanceDifference == null || target.distanceMeters == null || target.distanceMeters <= 0.0) 0
  else scaledScore(distanceDifference, target.distanceMeters * tolerance.distanceFraction, 120)
  reasons += when {
    distanceDifference == null -> neutral("Distance comparison unavailable")
    distanceDifference < 0.05 -> positive("Exact distance match")
    else -> neutral("Distance differs by ${"%.1f".format(java.util.Locale.ROOT, distanceDifference)} m")
  }

  var sharedMetricScore = 0
  fun compareMetric(label: String, expected: Double?, actual: Double?, toleranceValue: Double, unit: String) {
    if (expected == null || actual == null || !expected.isFinite() || !actual.isFinite()) return
    val difference = abs(expected - actual)
    val contribution = scaledScore(difference, toleranceValue, 80)
    sharedMetricScore += contribution
    reasons += if (difference <= toleranceValue * 0.1) positive("$label matches")
    else if (difference <= toleranceValue) neutral("$label differs by ${"%.1f".format(java.util.Locale.ROOT, difference)} $unit")
    else negative("$label differs by ${"%.1f".format(java.util.Locale.ROOT, difference)} $unit")
  }
  compareMetric("Average HR", target.averageHeartRate, candidate.metrics.averageHeartRate, 15.0, "bpm")
  compareMetric("Max HR", target.maxHeartRate, candidate.metrics.maxHeartRate, 15.0, "bpm")
  compareMetric("Average speed", target.averageSpeedMetersPerSecond, candidate.metrics.averageSpeedMetersPerSecond, 0.75, "m/s")
  compareMetric("VO₂max", target.vo2Max, candidate.metrics.vo2Max, 5.0, "ml/kg/min")

  return SamsungCandidateScore(
    candidate = candidate,
    score = 100 + deviceScore + sourceScore + startScore + durationScore + distanceScore + sharedMetricScore,
    deviceScore = deviceScore,
    sourceScore = sourceScore,
    startScore = startScore,
    durationScore = durationScore,
    distanceScore = distanceScore,
    sharedMetricScore = sharedMetricScore,
    reasons = listOf(positive("Running exercise type")) + reasons,
  )
}

private fun scaledScore(difference: Double, tolerance: Double, maximum: Int): Int {
  if (!difference.isFinite() || tolerance <= 0.0) return 0
  return (maximum * (1.0 - difference / tolerance).coerceIn(0.0, 1.0)).toInt()
}

private fun distanceDifference(expected: Double?, actual: Double?): Double? =
  if (expected == null || actual == null || !expected.isFinite() || !actual.isFinite()) null else abs(expected - actual)

private fun formatSeconds(milliseconds: Long): String =
  "%.3f".format(java.util.Locale.ROOT, milliseconds / 1_000.0)

private fun positive(text: String) = SamsungMatchReason(SamsungMatchReasonKind.POSITIVE, text)
private fun negative(text: String) = SamsungMatchReason(SamsungMatchReasonKind.NEGATIVE, text)
private fun neutral(text: String) = SamsungMatchReason(SamsungMatchReasonKind.NEUTRAL, text)

private fun distanceDifferenceFraction(expected: Double?, actual: Double?): Double =
  if (expected == null || actual == null || expected <= 0.0) Double.MAX_VALUE
  else abs(expected - actual) / expected

data class SamsungExerciseLogSample(
  val timestamp: Instant,
  val heartRate: Double?,
  val cadence: Double?,
  val speedMetersPerSecond: Double?,
  val powerWatts: Double?,
  val count: Int?,
)

data class ExerciseLogMetricSummary(
  val sampleCount: Int,
  val average: Double,
  val minimum: Double,
  val maximum: Double,
  val firstTimestamp: Instant,
  val lastTimestamp: Instant,
  val averageSampleIntervalSeconds: Double?,
)

data class SamsungClippedSensorData(
  val windowStart: Instant,
  val windowEnd: Instant,
  val heartRate: ExerciseLogMetricSummary?,
  val speed: ExerciseLogMetricSummary?,
  val cadence: ExerciseLogMetricSummary?,
  val movingAverageCadence: Double?,
  val power: ExerciseLogMetricSummary?,
  val samples: List<SamsungExerciseLogSample>,
)

fun clipSamsungSensorData(
  log: List<SamsungExerciseLogSample>,
  windowStart: Instant,
  windowEnd: Instant,
): SamsungClippedSensorData {
  val samples = log.filter { !it.timestamp.isBefore(windowStart) && !it.timestamp.isAfter(windowEnd) }
  val cadence = samples.mapNotNull { sample ->
    sample.cadence?.let { TimedMetricSample(sample.timestamp, it) }
  }
  val speed = samples.mapNotNull { sample ->
    sample.speedMetersPerSecond?.let { TimedMetricSample(sample.timestamp, it) }
  }
  return SamsungClippedSensorData(
    windowStart = windowStart,
    windowEnd = windowEnd,
    heartRate = summarizeExerciseLogMetric(samples, SamsungExerciseLogSample::heartRate),
    speed = summarizeExerciseLogMetric(samples, SamsungExerciseLogSample::speedMetersPerSecond),
    cadence = summarizeExerciseLogMetric(samples, SamsungExerciseLogSample::cadence),
    movingAverageCadence = movingCadenceAverage(cadence, speed),
    power = summarizeExerciseLogMetric(samples, SamsungExerciseLogSample::powerWatts),
    samples = samples,
  )
}

fun summarizeExerciseLogMetric(
  log: List<SamsungExerciseLogSample>?,
  value: (SamsungExerciseLogSample) -> Double?,
): ExerciseLogMetricSummary? {
  val samples = log.orEmpty().mapNotNull { sample ->
    value(sample)?.takeIf { it.isFinite() && it >= 0.0 }?.let { sample.timestamp to it }
  }.sortedBy { it.first }
  if (samples.isEmpty()) return null
  val intervals = samples.zipWithNext { first, second ->
    Duration.between(first.first, second.first).toMillis() / 1000.0
  }.filter { it >= 0.0 }
  return ExerciseLogMetricSummary(
    sampleCount = samples.size,
    average = samples.map { it.second }.average(),
    minimum = samples.minOf { it.second },
    maximum = samples.maxOf { it.second },
    firstTimestamp = samples.first().first,
    lastTimestamp = samples.last().first,
    averageSampleIntervalSeconds = intervals.averageOrNull(),
  )
}

enum class SampleAlignmentStatus { ALIGNED, PARTIALLY_ALIGNED, NOT_ALIGNED, NO_COMPARISON_DATA }

data class SampleAlignment(
  val status: SampleAlignmentStatus,
  val alignedSamples: Int,
  val cadenceSamples: Int,
)

data class CadenceInvestigation(
  val sessionMeanCadence: Double?,
  val sessionMaxCadence: Double?,
  val logExists: Boolean,
  val totalLogSamples: Int,
  val cadenceSampleCount: Int,
  val averageCadence: Double?,
  val minimumCadence: Double?,
  val maximumCadence: Double?,
  val firstCadenceTimestamp: Instant?,
  val lastCadenceTimestamp: Instant?,
  val averageSampleIntervalSeconds: Double?,
  val heartRateAlignment: SampleAlignment,
  val speedAlignment: SampleAlignment,
) {
  val detailedCadenceAvailable: Boolean
    get() = cadenceSampleCount > 0
}

fun investigateCadence(
  sessionMeanCadence: Double?,
  sessionMaxCadence: Double?,
  log: List<SamsungExerciseLogSample>?,
  alignmentToleranceSeconds: Long = 2,
): CadenceInvestigation {
  val samples = log.orEmpty()
  val cadenceSamples = samples.mapNotNull { sample ->
    sample.cadence?.takeIf { it.isFinite() && it >= 0.0 }?.let { sample.timestamp to it }
  }.sortedBy { it.first }
  val intervals = cadenceSamples.zipWithNext { first, second ->
    Duration.between(first.first, second.first).toMillis() / 1000.0
  }.filter { it >= 0.0 }
  val cadenceTimes = cadenceSamples.map { it.first }
  return CadenceInvestigation(
    sessionMeanCadence = sessionMeanCadence.validOptionalMetric(),
    sessionMaxCadence = sessionMaxCadence.validOptionalMetric(),
    logExists = log != null,
    totalLogSamples = samples.size,
    cadenceSampleCount = cadenceSamples.size,
    averageCadence = cadenceSamples.map { it.second }.averageOrNull(),
    minimumCadence = cadenceSamples.minOfOrNull { it.second },
    maximumCadence = cadenceSamples.maxOfOrNull { it.second },
    firstCadenceTimestamp = cadenceSamples.firstOrNull()?.first,
    lastCadenceTimestamp = cadenceSamples.lastOrNull()?.first,
    averageSampleIntervalSeconds = intervals.averageOrNull(),
    heartRateAlignment = alignment(
      cadenceTimes,
      samples.filter { it.heartRate?.isFinite() == true && it.heartRate > 0.0 }.map { it.timestamp },
      alignmentToleranceSeconds,
    ),
    speedAlignment = alignment(
      cadenceTimes,
      samples.filter { it.speedMetersPerSecond?.isFinite() == true && it.speedMetersPerSecond >= 0.0 }.map { it.timestamp },
      alignmentToleranceSeconds,
    ),
  )
}

private fun alignment(
  cadenceTimes: List<Instant>,
  comparisonTimes: List<Instant>,
  toleranceSeconds: Long,
): SampleAlignment {
  if (cadenceTimes.isEmpty() || comparisonTimes.isEmpty()) {
    return SampleAlignment(SampleAlignmentStatus.NO_COMPARISON_DATA, 0, cadenceTimes.size)
  }
  val aligned = cadenceTimes.count { cadenceTime ->
    comparisonTimes.any { abs(Duration.between(cadenceTime, it).seconds) <= toleranceSeconds }
  }
  val ratio = aligned.toDouble() / cadenceTimes.size
  val status = when {
    ratio >= 0.8 -> SampleAlignmentStatus.ALIGNED
    aligned > 0 -> SampleAlignmentStatus.PARTIALLY_ALIGNED
    else -> SampleAlignmentStatus.NOT_ALIGNED
  }
  return SampleAlignment(status, aligned, cadenceTimes.size)
}

private fun Double?.validOptionalMetric(): Double? = this?.takeIf { it.isFinite() && it >= 0.0 }

private fun List<Double>.averageOrNull(): Double? = if (isEmpty()) null else average()

data class SamsungExerciseSessionInspection(
  val candidate: SamsungExerciseCandidate,
  val caloriesKilocalories: Double?,
  val meanHeartRate: Double?,
  val minHeartRate: Double?,
  val maxHeartRate: Double?,
  val meanSpeedMetersPerSecond: Double?,
  val maxSpeedMetersPerSecond: Double?,
  val meanCadence: Double?,
  val maxCadence: Double?,
  val meanPowerWatts: Double?,
  val maxPowerWatts: Double?,
  val vo2Max: Double?,
  val altitudeGainMeters: Double?,
  val altitudeLossMeters: Double?,
  val minAltitudeMeters: Double?,
  val maxAltitudeMeters: Double?,
  val inclineDistanceMeters: Double?,
  val declineDistanceMeters: Double?,
  val customTitle: String?,
  val comment: String?,
  val sourceAppId: String?,
  val sourceDeviceId: String?,
  val sourceDeviceManufacturer: String?,
  val sourceDeviceModel: String?,
  val sourceDeviceName: String?,
  val cadence: CadenceInvestigation,
  val heartRateLogSummary: ExerciseLogMetricSummary?,
  val speedLogSummary: ExerciseLogMetricSummary?,
  val powerLogSummary: ExerciseLogMetricSummary?,
  val countLogSummary: ExerciseLogMetricSummary?,
  val exerciseLogSamples: List<SamsungExerciseLogSample> = emptyList(),
  val route: RunRoute? = null,
)

enum class ProviderMetric { DISTANCE, DURATION, AVERAGE_HEART_RATE, MAX_HEART_RATE, SPEED, VO2_MAX, CADENCE, POWER, ELEVATION }

data class ProviderMetricPresence(
  val metric: ProviderMetric,
  val healthConnectAvailable: Boolean,
  val samsungHealthAvailable: Boolean,
)

data class SharedMetricDifference(
  val metric: ProviderMetric,
  val healthConnectValue: Double,
  val samsungHealthValue: Double,
  val unit: String,
) {
  val absoluteDifference: Double get() = samsungHealthValue - healthConnectValue
}

data class SamsungHealthInspection(
  val match: SamsungWorkoutMatchResult,
  val session: SamsungExerciseSessionInspection?,
  val providerMetrics: List<ProviderMetricPresence>,
  val sharedDifferences: List<SharedMetricDifference>,
  val identityMatch: SamsungWorkoutMatchResult = match,
  val identitySession: SamsungExerciseSessionInspection? = session,
  val enrichmentMatch: SamsungEnrichmentMatchResult = SamsungEnrichmentMatchResult(
    status = match.status,
    selected = match.selected,
    plausibleCandidates = match.plausibleCandidates,
    confidence = match.confidence,
    reasons = match.reasons,
    manualSelection = match.manualSelection,
    relationship = when (match.status) {
      SamsungWorkoutMatchStatus.MATCHED -> SamsungPhysicalWorkoutRelationship.EXACT_SESSION
      SamsungWorkoutMatchStatus.AMBIGUOUS -> SamsungPhysicalWorkoutRelationship.AMBIGUOUS
      SamsungWorkoutMatchStatus.NOT_FOUND -> SamsungPhysicalWorkoutRelationship.NO_MATCH
    },
  ),
  val enrichmentSession: SamsungExerciseSessionInspection? = session,
  val plausibleSessions: List<SamsungExerciseSessionInspection> = listOfNotNull(session),
  val clippedEnrichment: SamsungClippedSensorData? = null,
)
