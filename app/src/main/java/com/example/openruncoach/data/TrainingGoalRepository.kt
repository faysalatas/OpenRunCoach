package com.example.openruncoach.data

import com.example.openruncoach.data.local.OpenRunCoachDatabase
import com.example.openruncoach.data.local.TrainingGoalAssessmentReasonEntity
import com.example.openruncoach.data.local.TrainingGoalEntity
import com.example.openruncoach.data.local.TrainingGoalRecord
import com.example.openruncoach.domain.goal.GoalAssessment
import com.example.openruncoach.domain.goal.GoalAssessmentLevel
import com.example.openruncoach.domain.goal.GoalDistance
import com.example.openruncoach.domain.goal.GoalStatus
import com.example.openruncoach.domain.goal.GoalType
import com.example.openruncoach.domain.goal.GoalBenchmarkSnapshot
import com.example.openruncoach.domain.goal.StartingPerformanceSnapshot
import com.example.openruncoach.domain.goal.TrainingGoal
import com.example.openruncoach.domain.goal.TrainingGoalDraft
import com.example.openruncoach.domain.goal.TrainingGoalFactory
import com.example.openruncoach.domain.profile.ProfileConfidence
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class TrainingGoalRepository(
  database: OpenRunCoachDatabase,
  private val profileRepository: AthletePerformanceProfileRepository,
  private val factory: TrainingGoalFactory = TrainingGoalFactory(),
) {
  private val dao = database.trainingGoalDao()

  fun observeActiveGoal(): Flow<TrainingGoal?> = dao.observeActiveGoal().map { it?.toDomain() }

  fun observeGoal(goalId: String): Flow<TrainingGoal?> = dao.observeGoal(goalId).map { it?.toDomain() }

  suspend fun getActiveGoal(): TrainingGoal? = dao.getActiveGoal()?.toDomain()

  suspend fun createGoal(draft: TrainingGoalDraft): TrainingGoal {
    val goal = factory.create(draft, profileRepository.getProfile())
    dao.activate(goal.toRecord())
    return goal
  }

  suspend fun pauseGoal(goalId: String) = dao.updateStatus(goalId, GoalStatus.PAUSED.name)

  suspend fun archiveGoal(goalId: String) = dao.updateStatus(goalId, GoalStatus.ARCHIVED.name)
}

private fun TrainingGoal.toRecord() = TrainingGoalRecord(
  goal = TrainingGoalEntity(
    id, distance.name, customDistanceMeters, goalType.name, targetTimeSeconds,
    createdAt.toEpochMilli(), status.name, confidenceAtCreation.name,
    startingPerformanceSnapshot.bestBenchmark?.durationSeconds,
    startingPerformanceSnapshot.bestBenchmark?.runId,
    startingPerformanceSnapshot.bestBenchmark?.observedAt?.toEpochMilli(),
    startingPerformanceSnapshot.mostRecentBenchmark?.durationSeconds,
    startingPerformanceSnapshot.mostRecentBenchmark?.runId,
    startingPerformanceSnapshot.mostRecentBenchmark?.observedAt?.toEpochMilli(),
    startingPerformanceSnapshot.averageWeeklyDistanceMeters,
    startingPerformanceSnapshot.averageWeeklyDurationSeconds,
    startingPerformanceSnapshot.longestRecentRunDistanceMeters,
    startingPerformanceSnapshot.longestRecentRunDurationSeconds,
    startingPerformanceSnapshot.averageRunsPerWeek,
    startingPerformanceSnapshot.typicalEasyPaceSecondsPerKm,
    startingPerformanceSnapshot.typicalEasyHeartRate,
    startingPerformanceSnapshot.profileConfidence.name,
    startingPerformanceSnapshot.capturedAt.toEpochMilli(),
    assessmentAtCreation.level.name,
  ),
  assessmentReasons = assessmentAtCreation.reasons.mapIndexed { index, reason ->
    TrainingGoalAssessmentReasonEntity(id, index, reason)
  },
)

internal fun TrainingGoalRecord.toDomain(): TrainingGoal {
  fun benchmark(duration: Long?, runId: String?, observedAt: Long?) =
    if (duration != null && runId != null && observedAt != null) GoalBenchmarkSnapshot(duration, runId, Instant.ofEpochMilli(observedAt)) else null
  return TrainingGoal(
    id = goal.id,
    distance = GoalDistance.valueOf(goal.distance),
    customDistanceMeters = goal.customDistanceMeters,
    goalType = GoalType.valueOf(goal.goalType),
    targetTimeSeconds = goal.targetTimeSeconds,
    createdAt = Instant.ofEpochMilli(goal.createdAtEpochMillis),
    status = GoalStatus.valueOf(goal.status),
    startingPerformanceSnapshot = StartingPerformanceSnapshot(
      benchmark(goal.startingBestDurationSeconds, goal.startingBestRunId, goal.startingBestObservedAtEpochMillis),
      benchmark(goal.startingRecentDurationSeconds, goal.startingRecentRunId, goal.startingRecentObservedAtEpochMillis),
      goal.startingAverageWeeklyDistanceMeters,
      goal.startingAverageWeeklyDurationSeconds,
      goal.startingLongestRunDistanceMeters,
      goal.startingLongestRunDurationSeconds,
      goal.startingAverageRunsPerWeek,
      goal.startingTypicalEasyPaceSecondsPerKm,
      goal.startingTypicalEasyHeartRate,
      ProfileConfidence.valueOf(goal.startingProfileConfidence),
      Instant.ofEpochMilli(goal.snapshotCapturedAtEpochMillis),
    ),
    confidenceAtCreation = ProfileConfidence.valueOf(goal.confidenceAtCreation),
    assessmentAtCreation = GoalAssessment(
      GoalAssessmentLevel.valueOf(goal.assessmentLevel),
      assessmentReasons.sortedBy { it.position }.map { it.text },
    ),
  )
}
