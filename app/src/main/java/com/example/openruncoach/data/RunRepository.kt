package com.example.openruncoach.data

import com.example.openruncoach.data.healthconnect.HealthConnectRunMetricsProvider
import com.example.openruncoach.data.local.RunDao
import com.example.openruncoach.data.local.RunEntity
import com.example.openruncoach.data.local.RunMetricsWithValues
import com.example.openruncoach.data.local.RunWithActualMetrics
import com.example.openruncoach.domain.CanonicalRunSelection
import com.example.openruncoach.domain.PhysicalRunMatchDecision
import com.example.openruncoach.domain.PossibleDuplicate
import com.example.openruncoach.domain.RunActualResolver
import com.example.openruncoach.domain.RunRelevance
import com.example.openruncoach.domain.RunSummary
import com.example.openruncoach.domain.SourceDeviceType
import com.example.openruncoach.domain.WorkoutSource
import com.example.openruncoach.domain.findPossibleDuplicates
import com.example.openruncoach.domain.metrics.MetricSource
import com.example.openruncoach.domain.metrics.metricValues
import com.example.openruncoach.domain.selectCanonicalRuns
import java.time.Clock
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

class RunRepository(
  private val runDao: RunDao,
  private val healthConnectRepository: HealthConnectRepository,
  private val settingsRepository: RunSettingsRepository,
  private val clock: Clock = Clock.systemUTC(),
  private val runMetricsRepository: RunMetricsRepository? = null,
  private val healthConnectMetricsProvider: HealthConnectRunMetricsProvider = HealthConnectRunMetricsProvider(clock),
  private val runMetricsRefresher: RunMetricsRefresher? = null,
  private val postRunReconciler: PostRunReconciler,
) {
  val recentRuns: Flow<List<RunSummary>> =
    runDao.observeRecentActualRuns().map { runs -> runs.map { it.toActualSummary() } }

  val duplicateDiagnostics: Flow<Map<String, List<PossibleDuplicate>>> =
    runDao.observeAllRuns().map { runs -> findPossibleDuplicates(runs.map(RunEntity::toRunSummary)) }

  val canonicalRuns: Flow<List<RunSummary>> =
    runDao.observeCanonicalActualRuns().map { runs -> runs.map { it.toActualSummary() } }

  val ignoredRuns: Flow<List<RunSummary>> =
    runDao.observeIgnoredActualRuns().map { runs -> runs.map { it.toActualSummary() } }

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
    runDao.observeActualRun(runId).map { it?.toActualSummary() }

  /** Raw provider summary for refresh/import inputs; resolved fallback values are never written back. */
  suspend fun getProviderRun(runId: String): RunSummary? = runDao.getRun(runId)?.toRunSummary()

  suspend fun syncRuns(): Int {
    val imported = healthConnectRepository.runningWorkoutsLast90Days()
    val importedAt = clock.millis()
    // Provider reads stay outside Room. Re-read local identity/relevance when applying results.
    val sourceRepairs = runDao.getRunsMissingSourceMetadata().mapNotNull { savedRun ->
      val source = try {
        healthConnectRepository.runningWorkoutSourceById(savedRun.healthConnectRecordId)
      } catch (exception: CancellationException) {
        throw exception
      } catch (_: Exception) { null }
      source?.takeIf { it.sourcePackageName.isNotBlank() }?.let { savedRun.healthConnectRecordId to it }
    }.toMap()
    suspend fun persistCurrentInputs(): Map<String, String> {
      val existingById = runDao.getAllRuns().associateBy(RunEntity::healthConnectRecordId)
      runDao.upsertAll(imported.map { run ->
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
      sourceRepairs.forEach { (id, source) ->
        runDao.getRun(id)?.let { current ->
          runDao.upsertAll(listOf(current.copy(
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
      return selectCurrentCanonical(settingsRepository.preferredSourcePackage.first())
    }
    val previous = postRunReconciler.updateInputs { persistCurrentInputs() }
    refreshCanonicalMetrics(previous)
    return imported.size
  }

  suspend fun refreshCanonicalSelection(preferredSourcePackage: String?) {
    val previous = postRunReconciler.updateInputs { selectCurrentCanonical(preferredSourcePackage) }
    refreshCanonicalMetrics(previous)
  }

  private suspend fun selectCurrentCanonical(preferredSourcePackage: String?): Map<String, String> {
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
    return previousCanonicalByPhysicalRunId
  }

  private suspend fun refreshCanonicalMetrics(previousCanonicalByPhysicalRunId: Map<String, String>) {
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
    postRunReconciler.reconcileAll()
  }

  suspend fun setRunIgnored(run: RunSummary, ignored: Boolean) {
    postRunReconciler.setRelevance(run.physicalRunId, ignored)
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

/** Separate from raw mapping used by import, matching, and source preference. */
internal fun RunWithActualMetrics.toActualSummary(): RunSummary =
  RunActualResolver.resolve(run.toRunSummary(), metrics.singleOrNull()?.let {
    RunMetricsWithValues(it.snapshot, it.values, emptyList()).toDomain()
  })
