package com.example.openruncoach.data

import com.example.openruncoach.data.local.RunDao
import com.example.openruncoach.data.local.RunEntity
import com.example.openruncoach.domain.RunSummary
import java.time.Clock
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RunRepository(
  private val runDao: RunDao,
  private val healthConnectRepository: HealthConnectRepository,
  private val clock: Clock = Clock.systemUTC(),
) {
  val recentRuns: Flow<List<RunSummary>> =
    runDao.observeRecentRuns().map { runs -> runs.map(RunEntity::toRunSummary) }

  fun observeRun(runId: String): Flow<RunSummary?> =
    runDao.observeRun(runId).map { it?.toRunSummary() }

  suspend fun syncRuns(): Int {
    val runs = healthConnectRepository.runningWorkoutsLast90Days()
    val importedAt = clock.millis()
    runDao.upsertAll(runs.map { it.toRunEntity(importedAt) })
    return runs.size
  }
}

private fun RunSummary.toRunEntity(importedAt: Long) =
  RunEntity(
    healthConnectRecordId = healthConnectRecordId,
    startTimeEpochMillis = startTime.toEpochMilli(),
    endTimeEpochMillis = endTime.toEpochMilli(),
    durationSeconds = durationSeconds,
    distanceMeters = distanceMeters,
    averageHeartRate = averageHeartRate,
    maxHeartRate = maxHeartRate,
    averagePaceSecondsPerKm = averagePaceSecondsPerKm,
    importedAtEpochMillis = importedAt,
  )

private fun RunEntity.toRunSummary() =
  RunSummary(
    healthConnectRecordId = healthConnectRecordId,
    startTime = Instant.ofEpochMilli(startTimeEpochMillis),
    endTime = Instant.ofEpochMilli(endTimeEpochMillis),
    durationSeconds = durationSeconds,
    distanceMeters = distanceMeters,
    averageHeartRate = averageHeartRate,
    maxHeartRate = maxHeartRate,
    averagePaceSecondsPerKm = averagePaceSecondsPerKm,
  )
