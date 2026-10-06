package com.example.openruncoach.data

import com.example.openruncoach.data.local.RunDao
import com.example.openruncoach.data.local.RunEntity
import com.example.openruncoach.domain.RunSummary
import com.example.openruncoach.domain.CanonicalRunSelection
import com.example.openruncoach.domain.PossibleDuplicate
import com.example.openruncoach.domain.SourceDeviceType
import com.example.openruncoach.domain.PhysicalRunMatchDecision
import com.example.openruncoach.domain.RunRelevance
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
  private val athleteProfileRepository: AthletePerformanceProfileRepository? = null,
  private val runMetricsRefresher: RunMetricsRefresher? = null,
) {
  val recentRuns: Flow<List<RunSummary>> =
    runDao.observeRecentRuns().map { runs -> runs.map(RunEntity::toRunSummary) }

  val duplicateDiagnostics: Flow<Map<String, List<PossibleDuplicate>>> =
    runDao.observeAllRuns().map { runs -> findPossibleDuplicates(runs.map(RunEntity::toRunSummary)) }

  val canonicalRuns: Flow<List<RunSummary>> =
    runDao.observeCanonicalRuns().map { runs -> runs.map(RunEntity::toRunSummary) }

  val ignoredRuns: Flow<List<RunSummary>> =
    runDao.observeIgnoredCanonicalRuns().map { runs -> runs.map(RunEntity::toRunSummary) }

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
    val existingById = runDao.getAllRuns().associateBy(RunEntity::healthConnectRecordId)
    runDao.upsertAll(runs.map { run ->
      val incoming = run.toRunEntity(importedAt)
      existingById[incoming.healthConnectRecordId]?.let { existing ->
        incoming.copy(
          physicalRunId = existing.physicalRunId,
          relevance = existing.relevance,
          duplicateGroupId = existing.duplicateGroupId,
          isCanonical = existing.isCanonical,
          canonicalSelectionReason = existing.canonicalSelectionReason,
          canonicalMatchDecision = existing.canonicalMatchDecision,
        )
      } ?: incoming
    })
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
    val previousCanonicalByPhysicalRunId = entities.filter(RunEntity::isCanonical)
      .associate { it.physicalRunId to it.healthConnectRecordId }
    val selections = selectCanonicalRuns(entities.map(RunEntity::toRunSummary), preferredSourcePackage)
      .associateBy { it.runId }
    val ignoredPhysicalRunIds = entities.filter { it.relevance == RunRelevance.IGNORED.name }
      .mapTo(mutableSetOf(), RunEntity::physicalRunId)
    runDao.updateAll(entities.map { entity ->
      val selection = selections.getValue(entity.healthConnectRecordId)
      entity.copy(
        duplicateGroupId = selection.duplicateGroupId,
        physicalRunId = selection.physicalRunId,
        isCanonical = selection.isCanonical,
        canonicalSelectionReason = selection.reason,
        canonicalMatchDecision = selection.matchDecision.name,
        relevance = if (selection.physicalRunId in ignoredPhysicalRunIds) {
          RunRelevance.IGNORED.name
        } else entity.relevance,
      )
    })
    selections.values.filter(CanonicalRunSelection::isCanonical).forEach { selection ->
      runDao.repointPhysicalRunReferences(selection.physicalRunId, selection.runId)
    }
    val canonicalEntities = runDao.getCanonicalRuns()
    if (runMetricsRefresher != null) {
      canonicalEntities.forEach { entity ->
        try {
          val canonicalChanged = previousCanonicalByPhysicalRunId[entity.physicalRunId] !=
            entity.healthConnectRecordId
          runMetricsRefresher.refresh(entity.toRunSummary(), force = canonicalChanged)
        } catch (exception: CancellationException) {
          throw exception
        } catch (_: Exception) {
          // Metrics are best-effort; imported canonical runs remain available.
        }
      }
    } else runMetricsRepository?.let { metricsRepository ->
      canonicalEntities.forEach { entity ->
        val existing = metricsRepository.getRunMetrics(entity.healthConnectRecordId)
        val containsSamsungEnrichment = existing?.metricValues()?.values?.any {
          it.source == MetricSource.SAMSUNG_HEALTH || it.secondarySource == MetricSource.SAMSUNG_HEALTH
        } == true
        if (!containsSamsungEnrichment) {
          metricsRepository.save(healthConnectMetricsProvider.build(entity.toRunSummary(), null))
        }
      }
    }
    athleteProfileRepository?.recalculate()
  }

  suspend fun setRunIgnored(run: RunSummary, ignored: Boolean) {
    if (ignored) runDao.ignorePhysicalRun(run.physicalRunId)
    else runDao.updateRelevance(run.physicalRunId, RunRelevance.ACTIVE.name)
    athleteProfileRepository?.recalculate()
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
    physicalRunId = physicalRunId,
    relevance = relevance.name,
    canonicalMatchDecision = canonicalMatchDecision.name,
  )

internal fun RunEntity.toRunSummary() =
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
    physicalRunId = physicalRunId.ifBlank { healthConnectRecordId },
    relevance = RunRelevance.valueOf(relevance),
    canonicalMatchDecision = PhysicalRunMatchDecision.valueOf(canonicalMatchDecision),
  )
