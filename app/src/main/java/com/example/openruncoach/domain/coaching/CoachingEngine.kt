package com.example.openruncoach.domain.coaching

import com.example.openruncoach.domain.CardioEffort
import com.example.openruncoach.domain.CompletionStatus
import com.example.openruncoach.domain.MuscleFatigue
import com.example.openruncoach.domain.PainLevel
import com.example.openruncoach.domain.RunFeedback
import com.example.openruncoach.domain.RunSummary
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.Locale

enum class CoachingAction { PROGRESS, SMALL_PROGRESS, MAINTAIN, REDUCE, RECOVERY }

data class CoachingDecision(
  val action: CoachingAction,
  val score: Int,
  val reasons: List<String>,
  val generatedAt: Instant,
)

class CoachingEngine(private val clock: Clock = Clock.systemUTC()) {
  fun analyze(
    latestRun: RunSummary,
    recentRunHistory: List<RunSummary>,
    feedback: RunFeedback,
  ): CoachingDecision? {
    val canonicalHistory = recentRunHistory.filter(RunSummary::isCanonical)
    val newestCanonicalRun = canonicalHistory.maxWithOrNull(
      compareBy<RunSummary>({ it.startTime }, { it.healthConnectRecordId })
    )
    if (!latestRun.isCanonical ||
      feedback.runId != latestRun.healthConnectRecordId ||
      newestCanonicalRun?.healthConnectRecordId != latestRun.healthConnectRecordId
    ) return null
    val score = completionScore(feedback.completionStatus) +
      rpeScore(feedback.rpe) +
      painScore(feedback.painLevel) +
      muscleScore(feedback.muscleFatigue) +
      cardioScore(feedback.cardioEffort)
    val action = when {
      feedback.painLevel == PainLevel.SIGNIFICANT -> CoachingAction.RECOVERY
      feedback.completionStatus == CompletionStatus.ABORTED && feedback.painLevel != PainLevel.NONE -> CoachingAction.RECOVERY
      feedback.completionStatus == CompletionStatus.ABORTED -> CoachingAction.REDUCE
      score >= 5 -> CoachingAction.PROGRESS
      score >= 3 -> CoachingAction.SMALL_PROGRESS
      score >= 0 -> CoachingAction.MAINTAIN
      score >= -3 -> CoachingAction.REDUCE
      else -> CoachingAction.RECOVERY
    }
    return CoachingDecision(
      action = action,
      score = score,
      reasons = feedbackReasons(feedback) + recentVolumeReason(latestRun, canonicalHistory),
      generatedAt = clock.instant(),
    )
  }
}

private const val COMPLETED_SCORE = 2
private const val PARTIAL_SCORE = 0
private const val ABORTED_SCORE = -2

private fun completionScore(status: CompletionStatus): Int = when (status) {
  CompletionStatus.COMPLETED -> COMPLETED_SCORE
  CompletionStatus.PARTIAL -> PARTIAL_SCORE
  CompletionStatus.ABORTED -> ABORTED_SCORE
}

private fun rpeScore(rpe: Int): Int = when (rpe) {
  in 1..2 -> 2
  in 3..5 -> 1
  in 6..7 -> 0
  8 -> -1
  else -> -2
}

private fun painScore(pain: PainLevel): Int = when (pain) {
  PainLevel.NONE -> 1
  PainLevel.MILD -> 0
  PainLevel.SIGNIFICANT -> -4
}

private fun muscleScore(fatigue: MuscleFatigue): Int = when (fatigue) {
  MuscleFatigue.NONE -> 1
  MuscleFatigue.MILD -> 0
  MuscleFatigue.MODERATE -> -1
  MuscleFatigue.SEVERE -> -2
}

private fun cardioScore(effort: CardioEffort): Int = when (effort) {
  CardioEffort.EASY, CardioEffort.APPROPRIATE -> 1
  CardioEffort.HARD -> -1
  CardioEffort.LIMIT -> -2
}

private fun feedbackReasons(feedback: RunFeedback): List<String> = listOf(
  when (feedback.completionStatus) {
    CompletionStatus.COMPLETED -> "Workout completed"
    CompletionStatus.PARTIAL -> "Workout partially completed"
    CompletionStatus.ABORTED -> "Workout aborted"
  },
  "RPE ${feedback.rpe}/10",
  when (feedback.painLevel) {
    PainLevel.NONE -> "No pain reported"
    PainLevel.MILD -> "Mild pain reported"
    PainLevel.SIGNIFICANT -> "Significant pain reported"
  },
  when (feedback.muscleFatigue) {
    MuscleFatigue.NONE -> "No muscle fatigue"
    MuscleFatigue.MILD -> "Mild muscle fatigue"
    MuscleFatigue.MODERATE -> "Moderate muscle fatigue"
    MuscleFatigue.SEVERE -> "Severe muscle fatigue"
  },
  when (feedback.cardioEffort) {
    CardioEffort.EASY -> "Cardio effort easy"
    CardioEffort.APPROPRIATE -> "Cardio effort appropriate"
    CardioEffort.HARD -> "Cardio effort hard"
    CardioEffort.LIMIT -> "Cardio effort at the limit"
  },
)

private fun recentVolumeReason(latestRun: RunSummary, history: List<RunSummary>): String {
  val windowStart = latestRun.startTime.minus(Duration.ofDays(7))
  val recent = history.filter { it.startTime >= windowStart && it.startTime <= latestRun.startTime }
    .distinctBy(RunSummary::healthConnectRecordId)
  val kilometers = recent.mapNotNull(RunSummary::distanceMeters).sum() / 1000.0
  return "Recent canonical volume: ${"%.1f".format(Locale.ROOT, kilometers)} km across ${recent.size} runs"
}
