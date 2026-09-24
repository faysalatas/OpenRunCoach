package com.example.openruncoach.data

import com.example.openruncoach.data.local.RunDao
import com.example.openruncoach.data.local.RunEntity
import com.example.openruncoach.domain.RunSummary
import com.example.openruncoach.domain.PossibleDuplicate
import com.example.openruncoach.domain.SourceDeviceType
import com.example.openruncoach.domain.WorkoutSource
import com.example.openruncoach.domain.findPossibleDuplicates
import com.example.openruncoach.domain.selectCanonicalRuns
import com.example.openruncoach.data.healthconnect.HealthConnectRunMetricsProvider
import com.example.openruncoach.domain.metrics.MetricSource
import com.example.openruncoach.domain.metrics.metricValues
import java.time.Clock
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.CancellationException

class RunRepository(
  private val runDao: RunDao,
  private val healthConnectRepository: HealthConnectRepository,
  private val settingsRepository: RunSettingsRepository,
  private val clock: Clock = Clock.systemUTC(),
  private val runMetricsRepository: RunMetricsRepository? = null,
  private val healthConnectMetricsProvider: HealthConnectRunMetricsProvider = HealthConnectRunMetricsProvider(clock),
) {
  val recentRuns: Flow<List<RunSummary>> =
    runDao.observeRecentRuns().map { runs -> runs.map(RunEntity::toRunSummary) }

  val duplicateDiagnostics: Flow<Map<String, List<PossibleDuplicate>>> =
    runDao.observeAllRuns().map { runs -> findPossibleDuplicates(runs.map(RunEntity::toRunSummary)) }

  val canonicalRuns: Flow<List<RunSummary>> =
    runDao.observeCanonicalRuns().map { runs -> runs.map(RunEntity::toRunSummary) }

  val allRuns: Flow<List<RunSummary>> =
    runDao.observeAllRuns().map { runs -> runs.map(RunEntity::toRunSummary) }

  val knownSources: Flow<List<WorkoutSource>> = runDao.observeAllRuns().map { runs ->
    runs.filter { it.sourcePackageName.isNotBlank() }
      .groupBy(RunEntity::sourcePackageName)
      .map { (packageName, records) ->
        WorkoutSource(
          packageName = packageName,
          appName = records.firstNotNullOfOrNull { it.sourceAppName?.takeIf(String::isNotBlank) } ?: packageName,
        )
      }
      .sortedBy { it.appName.lowercase() }
  }

  fun observeRun(runId: String): Flow<RunSummary?> =
    runDao.observeRun(runId).map { it?.toRunSummary() }

  suspend fun syncRuns(): Int {
    val runs = healthConnectRepository.runningWorkoutsLast90Days()
    val importedAt = clock.millis()
    runDao.upsertAll(runs.map { it.toRunEntity(importedAt) })
    for (savedRun in runDao.getRunsMissingSourceMetadata()) {
      val source = try {
        healthConnectRepository.runningWorkoutSourceById(savedRun.healthConnectRecordId)
      } catch (exception: CancellationException) {
        throw exception
      } catch (exception: Exception) {
        // The old Health Connect record may be gone or outside the current access window.
        null
      }
      if (source != null && source.sourcePackageName.isNotBlank()) {
        runDao.upsertAll(listOf(savedRun.copy(
          sourcePackageName = source.sourcePackageName,
          sourceAppName = source.sourceAppName,
          sourceDeviceManufacturer = source.sourceDeviceManufacturer,
          sourceDeviceModel = source.sourceDeviceModel,
          sourceDeviceType = source.sourceDeviceType?.name,
          sourceRecordLastModifiedTimeEpochMillis = source.sourceRecordLastModifiedTime.toEpochMilli(),
          importedAtEpochMillis = importedAt,
        )))
      }
    }
    refreshCanonicalSelection(settingsRepository.preferredSourcePackage.first())
    return runs.size
  }

  suspend fun refreshCanonicalSelection(preferredSourcePackage: String?) {
    val entities = runDao.getAllRuns()
    val selections = selectCanonicalRuns(entities.map(RunEntity::toRunSummary), preferredSourcePackage)
      .associateBy { it.runId }
    runDao.updateAll(entities.map { entity ->
      val selection = selections.getValue(entity.healthConnectRecordId)
      entity.copy(
        duplicateGroupId = selection.duplicateGroupId,
        isCanonical = selection.isCanonical,
        canonicalSelectionReason = selection.reason,
      )
    })
    val metricsRepository = runMetricsRepository ?: return
    runDao.getAllRuns().filter(RunEntity::isCanonical).forEach { entity ->
      val existing = metricsRepository.getRunMetrics(entity.healthConnectRecordId)
      val containsSamsungEnrichment = existing?.metricValues()?.values?.any {
        it.source == MetricSource.SAMSUNG_HEALTH || it.secondarySource == MetricSource.SAMSUNG_HEALTH
      } == true
      if (!containsSamsungEnrichment) {
        metricsRepository.save(healthConnectMetricsProvider.build(entity.toRunSummary(), null))
      }
    }
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
    sourcePackageName = sourcePackageName,
    sourceAppName = sourceAppName,
    sourceDeviceManufacturer = sourceDeviceManufacturer,
    sourceDeviceModel = sourceDeviceModel,
    sourceDeviceType = sourceDeviceType?.name,
    sourceRecordLastModifiedTimeEpochMillis = sourceRecordLastModifiedTime?.toEpochMilli(),
    duplicateGroupId = duplicateGroupId,
    isCanonical = isCanonical,
    canonicalSelectionReason = canonicalSelectionReason,
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
    sourcePackageName = sourcePackageName,
    sourceAppName = sourceAppName,
    sourceDeviceManufacturer = sourceDeviceManufacturer,
    sourceDeviceModel = sourceDeviceModel,
    sourceDeviceType = sourceDeviceType?.let { SourceDeviceType.valueOf(it) },
    sourceRecordLastModifiedTime = sourceRecordLastModifiedTimeEpochMillis?.let(Instant::ofEpochMilli),
    duplicateGroupId = duplicateGroupId,
    isCanonical = isCanonical,
    canonicalSelectionReason = canonicalSelectionReason,
  )
