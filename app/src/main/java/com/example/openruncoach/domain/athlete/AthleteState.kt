package com.example.openruncoach.domain.athlete

import com.example.openruncoach.domain.PainLevel
import com.example.openruncoach.domain.RunSummary
import com.example.openruncoach.domain.metrics.RunContinuityEvidence
import com.example.openruncoach.domain.profile.AthletePerformanceProfile
import com.example.openruncoach.domain.profile.ProfileConfidence
import com.example.openruncoach.domain.profile.RecentContinuitySummary
import com.example.openruncoach.domain.workout.EffortResponse
import com.example.openruncoach.domain.workout.SessionCost
import com.example.openruncoach.domain.workout.VolumeCompletion
import com.example.openruncoach.domain.workout.WorkoutCompletionOutcome
import com.example.openruncoach.domain.workout.WorkoutPurpose
import com.example.openruncoach.domain.workout.WorkoutResponse
import java.time.Duration
import java.time.Instant
import kotlin.math.max

enum class CurrentReadiness { UNKNOWN, NORMAL, CONSERVATIVE, RECOVERY_PRIORITY }

/**
 * Goal-independent coaching interpretation of accumulated training evidence.
 *
 * Descriptive totals and baselines remain owned by [AthletePerformanceProfile]. This type adds
 * longitudinal capability and current-readiness meaning without imposing planner ceilings.
 */
data class AthleteState(
  val recentActualVolume7DaysMeters: Double,
  val recentActualVolume28DaysMeters: Double,
  val recentRunFrequencyPerWeek: Double,
  val longestCompletedDistanceMeters: Double?,
  val longestDemonstratedContinuousDistanceMeters: Double?,
  val longestToleratedDistanceMeters: Double?,
  val toleratedSessionCount: Int,
  val recentContinuity: RecentContinuitySummary,
  val recentResponseCount: Int,
  val recentElevatedCostCount: Int,
  val recentHighCostCount: Int,
  val latestSessionCost: SessionCost?,
  val capabilityConfidence: ProfileConfidence,
  val currentReadiness: CurrentReadiness,
  val capabilityReasons: List<String>,
  val readinessReasons: List<String>,
  val calculatedAt: Instant,
)

class AthleteStateBuilder {
  fun build(
    profile: AthletePerformanceProfile,
    canonicalRuns: List<RunSummary>,
    workoutResponses: List<WorkoutResponse>,
  ): AthleteState {
    val runsById = canonicalRuns.asSequence()
      .filter(RunSummary::isCanonical)
      .filter { !it.startTime.isAfter(profile.calculatedAt) }
      .distinctBy(RunSummary::healthConnectRecordId)
      .associateBy(RunSummary::healthConnectRecordId)
    val responses = workoutResponses.asSequence()
      .distinctBy { it.runId to it.plannedWorkoutId }
      .mapNotNull { response -> runsById[response.runId]?.let { response to it } }
      .sortedWith(compareBy<Pair<WorkoutResponse, RunSummary>> { it.second.startTime }.thenBy { it.first.runId })
      .toList()
    val recentResponses = responses.filter { (_, run) ->
      !run.startTime.isBefore(profile.calculatedAt.minus(Duration.ofDays(7))) &&
        !run.startTime.isAfter(profile.calculatedAt)
    }
    val recentCapabilityResponses = responses.filter { (_, run) ->
      !run.startTime.isBefore(profile.calculatedAt.minus(Duration.ofDays(28))) &&
        !run.startTime.isAfter(profile.calculatedAt)
    }
    val recentCanonicalRunCount = runsById.values.count { run ->
      !run.startTime.isBefore(profile.calculatedAt.minus(Duration.ofDays(28))) &&
        !run.startTime.isAfter(profile.calculatedAt)
    }
    val recentContinuity = continuitySummary(
      recentCapabilityResponses,
      recentCanonicalRunCount,
      profile.recentContinuity,
    )
    val completedDistances = responses.mapNotNull { (response, _) ->
      response.actualDistanceMeters?.takeIf { response.completion == WorkoutCompletionOutcome.COMPLETED }
    }
    val continuousDistances = responses.mapNotNull { (response, _) ->
      response.actualDistanceMeters?.takeIf {
        response.completion == WorkoutCompletionOutcome.COMPLETED &&
          response.continuity == RunContinuityEvidence.CONTINUOUS
      }
    }
    val tolerated = responses.filter { (response, _) -> response.isToleratedAerobicEvidence() }
    val toleratedDistances = tolerated.mapNotNull { it.first.actualDistanceMeters }
    val latest = recentResponses.lastOrNull()?.first
    val elevatedCount = recentResponses.count { it.first.sessionCost == SessionCost.ELEVATED }
    val highCount = recentResponses.count { it.first.sessionCost == SessionCost.HIGH }
    val painfulCount = recentResponses.count { it.first.pain != PainLevel.NONE }
    val significantPainCount = recentResponses.count { it.first.pain == PainLevel.SIGNIFICANT }
    val readiness = readiness(recentResponses.size, latest, elevatedCount, highCount, painfulCount, significantPainCount)
    val confidence = capabilityConfidence(
      profile.profileConfidence,
      recentCapabilityResponses.size,
      recentCapabilityResponses.count { it.first.isToleratedAerobicEvidence() },
    )
    val longestContinuous = maxNullable(
      continuousDistances.maxOrNull(),
      recentContinuity.longestContinuousDistanceMeters,
    )

    return AthleteState(
      recentActualVolume7DaysMeters = profile.distanceLast7DaysMeters,
      recentActualVolume28DaysMeters = profile.distanceLast28DaysMeters,
      recentRunFrequencyPerWeek = profile.averageRunsPerWeek,
      longestCompletedDistanceMeters = completedDistances.maxOrNull(),
      longestDemonstratedContinuousDistanceMeters = longestContinuous,
      longestToleratedDistanceMeters = toleratedDistances.maxOrNull(),
      toleratedSessionCount = tolerated.size,
      recentContinuity = recentContinuity,
      recentResponseCount = recentResponses.size,
      recentElevatedCostCount = elevatedCount,
      recentHighCostCount = highCount,
      latestSessionCost = latest?.sessionCost,
      capabilityConfidence = confidence,
      currentReadiness = readiness.first,
      capabilityReasons = capabilityReasons(
        completedDistances.maxOrNull(),
        longestContinuous,
        toleratedDistances.maxOrNull(),
        tolerated.size,
        confidence,
      ),
      readinessReasons = readiness.second,
      calculatedAt = profile.calculatedAt,
    )
  }

  private fun WorkoutResponse.isToleratedAerobicEvidence(): Boolean =
    purpose in setOf(WorkoutPurpose.RECOVERY, WorkoutPurpose.AEROBIC_BASE, WorkoutPurpose.ENDURANCE) &&
      completion == WorkoutCompletionOutcome.COMPLETED &&
      volumeCompletion in setOf(VolumeCompletion.MET, VolumeCompletion.SUBSTANTIALLY_COMPLETED) &&
      effortResponse in setOf(EffortResponse.BELOW_TARGET, EffortResponse.ON_TARGET) &&
      sessionCost in setOf(SessionCost.LOW, SessionCost.NORMAL) &&
      pain == PainLevel.NONE &&
      continuity != RunContinuityEvidence.INTERRUPTED

  private fun continuitySummary(
    recentResponses: List<Pair<WorkoutResponse, RunSummary>>,
    recentCanonicalRunCount: Int,
    profileSummary: RecentContinuitySummary,
  ): RecentContinuitySummary {
    val responsesByRun = recentResponses.distinctBy { it.first.runId }
    if (responsesByRun.size != recentCanonicalRunCount) return profileSummary
    return RecentContinuitySummary(
      continuousRuns = responsesByRun.count { it.first.continuity == RunContinuityEvidence.CONTINUOUS },
      interruptedRuns = responsesByRun.count { it.first.continuity == RunContinuityEvidence.INTERRUPTED },
      unknownRuns = responsesByRun.count { it.first.continuity == RunContinuityEvidence.UNKNOWN },
      longestContinuousDistanceMeters = responsesByRun.mapNotNull { (response, _) ->
        response.actualDistanceMeters?.takeIf {
          response.completion == WorkoutCompletionOutcome.COMPLETED &&
            response.continuity == RunContinuityEvidence.CONTINUOUS
        }
      }.maxOrNull(),
    )
  }

  private fun capabilityConfidence(
    profileConfidence: ProfileConfidence,
    responseCount: Int,
    toleratedCount: Int,
  ): ProfileConfidence = when {
    responseCount == 0 -> if (profileConfidence == ProfileConfidence.VERY_LOW) ProfileConfidence.VERY_LOW else ProfileConfidence.LOW
    profileConfidence == ProfileConfidence.HIGH || toleratedCount >= 5 -> ProfileConfidence.HIGH
    profileConfidence == ProfileConfidence.MEDIUM || toleratedCount >= 3 -> ProfileConfidence.MEDIUM
    else -> ProfileConfidence.LOW
  }

  private fun readiness(
    responseCount: Int,
    latest: WorkoutResponse?,
    elevatedCount: Int,
    highCount: Int,
    painfulCount: Int,
    significantPainCount: Int,
  ): Pair<CurrentReadiness, List<String>> = when {
    responseCount == 0 -> CurrentReadiness.UNKNOWN to listOf(
      "No recent structured workout responses are available to interpret current readiness."
    )
    significantPainCount > 0 || highCount >= 2 || painfulCount >= 2 -> CurrentReadiness.RECOVERY_PRIORITY to buildList {
      if (significantPainCount > 0) add("Significant pain was reported in recent training.")
      if (highCount >= 2) add("Multiple recent sessions had high session cost.")
      if (painfulCount >= 2) add("Pain was reported across multiple recent sessions.")
    }
    latest?.pain == PainLevel.MILD -> CurrentReadiness.CONSERVATIVE to listOf(
      "Mild pain was reported in the latest session."
    )
    latest?.sessionCost == SessionCost.HIGH -> CurrentReadiness.CONSERVATIVE to listOf(
      "The latest session had high cost and may require additional recovery."
    )
    elevatedCount >= 2 -> CurrentReadiness.CONSERVATIVE to listOf(
      "Several recent sessions were more costly than intended."
    )
    else -> CurrentReadiness.NORMAL to listOf(
      "Recent workout responses do not show a repeated recovery or safety concern."
    )
  }

  private fun capabilityReasons(
    longestCompleted: Double?,
    longestContinuous: Double?,
    longestTolerated: Double?,
    toleratedCount: Int,
    confidence: ProfileConfidence,
  ): List<String> = buildList {
    if (longestCompleted != null) add("Completed distance has been observed up to ${distance(longestCompleted)}.")
    if (longestContinuous != null) add("Continuous running has been demonstrated up to ${distance(longestContinuous)}.")
    if (longestTolerated != null) {
      add("$toleratedCount aerobic ${if (toleratedCount == 1) "session provides" else "sessions provide"} comfortable/tolerated evidence up to ${distance(longestTolerated)}.")
    }
    if (isEmpty()) add("Only limited recent running evidence is available; missing evidence is not evidence of inability.")
    if (confidence == ProfileConfidence.LOW || confidence == ProfileConfidence.VERY_LOW) {
      add("Capability interpretation remains ${confidence.name.lowercase().replace('_', ' ')} confidence.")
    }
  }

  private fun distance(meters: Double) = "%.2f km".format(java.util.Locale.ROOT, meters / 1_000.0)

  private fun maxNullable(first: Double?, second: Double?): Double? = when {
    first == null -> second
    second == null -> first
    else -> max(first, second)
  }
}
