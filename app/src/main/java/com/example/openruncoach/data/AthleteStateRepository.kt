package com.example.openruncoach.data

import com.example.openruncoach.data.local.OpenRunCoachDatabase
import com.example.openruncoach.domain.RunSummary
import com.example.openruncoach.domain.RunRelevance
import com.example.openruncoach.domain.athlete.AthleteState
import com.example.openruncoach.domain.athlete.AthleteStateBuilder
import com.example.openruncoach.domain.metrics.RunContinuityEvidence
import java.time.Instant
import kotlinx.coroutines.flow.first

data class AthleteStateContinuityTrace(
  val runId: String,
  val startTime: Instant,
  val distanceMeters: Double?,
  val responseFound: Boolean,
  val athleteStateContinuity: RunContinuityEvidence?,
  val directResponseContinuity: RunContinuityEvidence?,
  val directSpeedSampleCount: Int,
  val collectionSpeedSampleCount: Int,
  val coachPathSpeedSampleCount: Int,
  val roomRawSpeedSampleCount: Int,
  val roomRelationSpeedSampleCount: Int,
  val roomMappedSpeedSampleCount: Int,
)

data class AthleteStateRepositoryIdentityTrace(
  val databaseIdentity: String,
  val metricsDaoIdentity: String,
  val metricsRepositoryIdentity: String,
  val workoutResponseRepositoryIdentity: String,
)

data class AthleteStateRebuildResult(
  val state: AthleteState,
  val continuityTrace: List<AthleteStateContinuityTrace>,
  val repositoryIdentity: AthleteStateRepositoryIdentityTrace,
)

/** Rebuilds AthleteState from authoritative canonical history, profile aggregates, and responses. */
class AthleteStateRepository(
  private val database: OpenRunCoachDatabase,
  private val profileRepository: AthletePerformanceProfileRepository,
  private val builder: AthleteStateBuilder = AthleteStateBuilder(),
) {
  suspend fun rebuild(): AthleteState = rebuildWithTrace().state

  suspend fun rebuildWithTrace(): AthleteStateRebuildResult {
    val profile = profileRepository.recalculate()
    val runs = database.runDao().getCanonicalRuns().mapNotNull {
      database.runDao().getActualRun(it.healthConnectRecordId)?.toActualSummary()
    }
    val metricsDao = database.runMetricsDao()
    val responseRepository = WorkoutResponseRepository(database)
    val metricsRepository = RunMetricsRepository(metricsDao)
    val responses = responseRepository.getAll()
    val responsesByRun = responses.associateBy { it.physicalRunId }
    val metricsByRun = metricsRepository.getCanonicalRunMetrics()
      .associateBy { it.runId }
    val trace = mutableListOf<AthleteStateContinuityTrace>()
    runs
      .filter { it.isCanonical && it.relevance == RunRelevance.ACTIVE }
      .filter { !it.startTime.isAfter(profile.calculatedAt) }
      .distinctBy(RunSummary::healthConnectRecordId)
      .sortedBy(RunSummary::startTime)
      .forEach { run ->
        val response = responsesByRun[run.physicalRunId]
        val rawSpeedSampleCount = metricsDao.countSamples(
          run.healthConnectRecordId,
          com.example.openruncoach.domain.metrics.RunSampleMetricType.SPEED.name,
        )
        val relation = metricsDao.getRunMetrics(run.healthConnectRecordId)
        val directMetrics = metricsRepository.getRunMetrics(run.healthConnectRecordId)
        val coachPathMetrics = metricsRepository.observeRunMetrics(run.healthConnectRecordId).first()
        val directResponse = responseRepository.observeForRun(run.healthConnectRecordId).first()
        trace += AthleteStateContinuityTrace(
          runId = run.healthConnectRecordId,
          startTime = run.startTime,
          distanceMeters = response?.actualDistanceMeters ?: run.distanceMeters,
          responseFound = response != null,
          athleteStateContinuity = response?.continuity,
          directResponseContinuity = directResponse?.continuity,
          directSpeedSampleCount = directMetrics?.series?.speed?.size ?: 0,
          collectionSpeedSampleCount = metricsByRun[run.healthConnectRecordId]?.series?.speed?.size ?: 0,
          coachPathSpeedSampleCount = coachPathMetrics?.series?.speed?.size ?: 0,
          roomRawSpeedSampleCount = rawSpeedSampleCount,
          roomRelationSpeedSampleCount = relation?.samples?.count {
            it.metricType == com.example.openruncoach.domain.metrics.RunSampleMetricType.SPEED.name
          } ?: 0,
          roomMappedSpeedSampleCount = relation?.toDomain()?.series?.speed?.size ?: 0,
        )
      }
    return AthleteStateRebuildResult(
      state = builder.build(profile, runs, responses),
      continuityTrace = trace,
      repositoryIdentity = AthleteStateRepositoryIdentityTrace(
        databaseIdentity = database.identity(),
        metricsDaoIdentity = metricsDao.identity(),
        metricsRepositoryIdentity = metricsRepository.identity(),
        workoutResponseRepositoryIdentity = responseRepository.identity(),
      ),
    )
  }
}

private fun Any.identity() = "${javaClass.simpleName}@${System.identityHashCode(this)}"
