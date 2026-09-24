package com.example.openruncoach.domain.samsunghealth

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
  val exerciseLogExists: Boolean = false,
  val totalLogSamples: Int = 0,
  val cadenceSampleCount: Int = 0,
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

enum class SamsungWorkoutMatchStatus { MATCHED, NOT_FOUND, AMBIGUOUS }

enum class SamsungMatchConfidence { HIGH, MEDIUM, LOW, AMBIGUOUS }

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

fun matchSamsungWorkout(
  target: SamsungWorkoutTarget,
  candidates: List<SamsungExerciseCandidate>,
  tolerance: SamsungWorkoutMatchTolerance = SamsungWorkoutMatchTolerance(),
  manualSelectionCandidateId: String? = null,
): SamsungWorkoutMatchResult {
  val plausible = candidates.asSequence()
    .filter { it.exerciseKind == SamsungExerciseKind.RUNNING || it.exerciseKind == SamsungExerciseKind.TRACK_RUNNING }
    .filter { abs(Duration.between(target.startTime, it.startTime).seconds) <= tolerance.startSeconds }
    .filter { abs(target.durationSeconds - it.durationSeconds) <= tolerance.durationSeconds }
    .filter { candidate ->
      val expected = target.distanceMeters
      val actual = candidate.distanceMeters
      expected == null || actual == null || expected <= 0.0 ||
        abs(expected - actual) / expected <= tolerance.distanceFraction
    }
    .toList()

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
  compareMetric("VO₂ max", target.vo2Max, candidate.metrics.vo2Max, 5.0, "ml/kg/min")

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
)
