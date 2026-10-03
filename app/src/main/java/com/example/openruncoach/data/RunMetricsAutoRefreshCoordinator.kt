package com.example.openruncoach.data

import android.content.Context
import com.example.openruncoach.data.healthconnect.HealthConnectRunMetricsProvider
import com.example.openruncoach.data.healthconnect.inspection.HealthConnectInspectionTarget
import com.example.openruncoach.data.healthconnect.inspection.HealthConnectRunDataReader
import com.example.openruncoach.data.local.OpenRunCoachDatabase
import com.example.openruncoach.data.samsunghealth.SamsungHealthDataSource
import com.example.openruncoach.data.samsunghealth.SamsungHealthExerciseInspector
import com.example.openruncoach.data.samsunghealth.SamsungHealthPermissionManager
import com.example.openruncoach.data.samsunghealth.SamsungHealthRunMetricsEnricher
import com.example.openruncoach.data.samsunghealth.SamsungMatchSelectionRepository
import com.example.openruncoach.domain.RunSummary
import com.example.openruncoach.domain.SourceDeviceType
import com.example.openruncoach.domain.metrics.CURRENT_RUN_METRICS_DATA_VERSION
import com.example.openruncoach.domain.metrics.RunMetrics
import com.example.openruncoach.domain.route.RouteSplitDeriver
import com.example.openruncoach.domain.route.RunRoute
import com.example.openruncoach.domain.samsunghealth.preferredRoute
import com.example.openruncoach.domain.samsunghealth.SamsungWorkoutTarget
import java.time.Clock
import java.time.Duration
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class RunMetricsRefreshOutcome(
  val metrics: RunMetrics,
  val refreshed: Boolean,
  val samsungFailure: Boolean = false,
)

fun interface RunMetricsRefresher {
  suspend fun refresh(run: RunSummary, force: Boolean): RunMetricsRefreshOutcome
}

class RunMetricsFreshnessPolicy(
  private val clock: Clock = Clock.systemUTC(),
  private val maximumAge: Duration = Duration.ofHours(24),
) {
  fun needsRefresh(run: RunSummary, metrics: RunMetrics?): Boolean {
    if (metrics == null || metrics.dataVersion < CURRENT_RUN_METRICS_DATA_VERSION) return true
    if (metrics.runId != run.healthConnectRecordId || metrics.startTime != run.startTime || metrics.endTime != run.endTime) return true
    if (run.sourceRecordLastModifiedTime?.isAfter(metrics.generatedAt) == true) return true
    return Duration.between(metrics.generatedAt, clock.instant()) > maximumAge
  }
}

class RunMetricsRefreshEngine(
  private val freshnessPolicy: RunMetricsFreshnessPolicy,
) {
  suspend fun refresh(
    run: RunSummary,
    existing: RunMetrics?,
    force: Boolean,
    loadBase: suspend () -> RunMetrics,
    enrich: suspend (RunMetrics) -> RunMetrics,
    persist: suspend (RunMetrics) -> Unit,
  ): RunMetricsRefreshOutcome {
    if (!force && !freshnessPolicy.needsRefresh(run, existing)) {
      return RunMetricsRefreshOutcome(existing!!, refreshed = false)
    }
    val base = loadBase()
    var samsungFailure = false
    val enriched = try {
      enrich(base)
    } catch (error: CancellationException) {
      throw error
    } catch (_: Throwable) {
      samsungFailure = true
      base
    }
    persist(enriched)
    return RunMetricsRefreshOutcome(enriched, refreshed = true, samsungFailure = samsungFailure)
  }
}

class RunMetricsAutoRefreshCoordinator(
  private val healthConnectReader: HealthConnectRunDataReader,
  private val samsungInspector: SamsungHealthExerciseInspector,
  private val samsungSelectionRepository: SamsungMatchSelectionRepository,
  private val healthConnectMetricsProvider: HealthConnectRunMetricsProvider,
  private val samsungMetricsEnricher: SamsungHealthRunMetricsEnricher,
  private val metricsRepository: RunMetricsRepository,
  private val routeRepository: RunRouteRepository? = null,
  private val routeSplitDeriver: RouteSplitDeriver = RouteSplitDeriver(),
  private val athleteProfileRepository: AthletePerformanceProfileRepository? = null,
  private val freshnessPolicy: RunMetricsFreshnessPolicy = RunMetricsFreshnessPolicy(),
) : RunMetricsRefresher {
  private val mutex = Mutex()
  private val engine = RunMetricsRefreshEngine(freshnessPolicy)

  override suspend fun refresh(run: RunSummary, force: Boolean): RunMetricsRefreshOutcome = mutex.withLock {
    val existing = metricsRepository.getRunMetrics(run.healthConnectRecordId)
    var healthConnect: com.example.openruncoach.domain.inspection.RunDataInspection? = null
    var selectedRoute: RunRoute? = null
    engine.refresh(
      run = run,
      existing = existing,
      force = force,
      loadBase = {
        healthConnect = try {
          healthConnectReader.read(
            HealthConnectInspectionTarget(
              exerciseSessionRecordId = run.healthConnectRecordId,
              startTime = run.startTime,
              endTime = run.endTime,
              sourcePackageName = run.sourcePackageName.takeIf(String::isNotBlank),
              sourceAppName = run.sourceAppName,
            )
          )
        } catch (error: CancellationException) {
          throw error
        } catch (_: Exception) {
          null
        }
        healthConnectMetricsProvider.build(run, healthConnect)
      },
      enrich = { base ->
        if (!run.isCanonical) base else {
          val samsung = samsungInspector.inspect(
            target = SamsungWorkoutTarget(
              startTime = run.startTime,
              endTime = run.endTime,
              durationSeconds = run.durationSeconds,
              distanceMeters = run.distanceMeters,
              canonicalSourceAppId = run.sourcePackageName.takeIf(String::isNotBlank),
              preferWatchSource = run.sourcePackageName == SAMSUNG_HEALTH_PACKAGE &&
                run.sourceDeviceType == SourceDeviceType.WATCH,
              averageHeartRate = run.averageHeartRate,
              maxHeartRate = run.maxHeartRate?.toDouble(),
              runId = run.healthConnectRecordId,
            ),
            healthConnectInspection = healthConnect,
            manualSelectionCandidateId = samsungSelectionRepository.selectedCandidateId(run.healthConnectRecordId),
          ).inspection
          selectedRoute = samsung?.preferredRoute(run.healthConnectRecordId)
          samsungMetricsEnricher.enrich(base, samsung)
        }
      },
      persist = { metrics ->
        metricsRepository.save(metrics)
        selectedRoute?.let { route ->
          val result = routeSplitDeriver.derive(route, run.distanceMeters, metrics)
          routeRepository?.save(result.route, result.splits)
        }
        athleteProfileRepository?.recalculate()
      },
    )
  }

  companion object {
    private const val SAMSUNG_HEALTH_PACKAGE = "com.sec.android.app.shealth"

    fun create(
      context: Context,
      database: OpenRunCoachDatabase,
      athleteProfileRepository: AthletePerformanceProfileRepository? = null,
    ): RunMetricsAutoRefreshCoordinator {
      val metricsRepository = RunMetricsRepository(database.runMetricsDao())
      val samsungDataSource = SamsungHealthDataSource(context)
      return RunMetricsAutoRefreshCoordinator(
        healthConnectReader = HealthConnectRunDataReader(context),
        samsungInspector = SamsungHealthExerciseInspector(
          samsungDataSource,
          SamsungHealthPermissionManager(samsungDataSource),
        ),
        samsungSelectionRepository = SamsungMatchSelectionRepository(context),
        healthConnectMetricsProvider = HealthConnectRunMetricsProvider(),
        samsungMetricsEnricher = SamsungHealthRunMetricsEnricher(),
        metricsRepository = metricsRepository,
        routeRepository = RunRouteRepository(database.runRouteDao()),
        athleteProfileRepository = athleteProfileRepository,
      )
    }
  }
}
