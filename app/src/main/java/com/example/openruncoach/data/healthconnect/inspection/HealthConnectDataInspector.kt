package com.example.openruncoach.data.healthconnect.inspection

import android.content.Context
import android.content.pm.PackageManager
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ElevationGainedRecord
import androidx.health.connect.client.records.ExerciseRouteResult
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.PowerRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.SpeedRecord
import androidx.health.connect.client.records.StepsCadenceRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.records.Vo2MaxRecord
import androidx.health.connect.client.records.metadata.DataOrigin
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.example.openruncoach.domain.inspection.AlternateSourceInspection
import com.example.openruncoach.domain.inspection.CountMetricSummary
import com.example.openruncoach.domain.inspection.MetricAvailability
import com.example.openruncoach.domain.inspection.MetricInspection
import com.example.openruncoach.domain.inspection.RouteInspectionStatus
import com.example.openruncoach.domain.inspection.RouteMetricSummary
import com.example.openruncoach.domain.inspection.RunDataInspection
import com.example.openruncoach.domain.inspection.RunMetricType
import com.example.openruncoach.domain.inspection.SessionMetricSummary
import com.example.openruncoach.domain.inspection.TimedNumericSample
import com.example.openruncoach.domain.inspection.TotalMetricSummary
import com.example.openruncoach.domain.inspection.Vo2MaxMetricSummary
import com.example.openruncoach.domain.inspection.Vo2MaxTiming
import com.example.openruncoach.domain.inspection.summarizeCadenceSamples
import com.example.openruncoach.domain.inspection.summarizeHeartRateSamples
import com.example.openruncoach.domain.inspection.summarizePowerSamples
import com.example.openruncoach.domain.inspection.summarizeSpeedSamples
import java.time.Clock
import java.time.Duration
import kotlin.reflect.KClass
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

class HealthConnectDataInspector(
  context: Context,
  private val clock: Clock = Clock.systemUTC(),
) {
  private val appContext = context.applicationContext

  val requiredPermissions: Set<String> = METRIC_PERMISSIONS.values.toSet()

  suspend fun missingPermissions(): Set<String> {
    if (HealthConnectClient.getSdkStatus(appContext) != HealthConnectClient.SDK_AVAILABLE) {
      return requiredPermissions
    }
    val granted = HealthConnectClient.getOrCreate(appContext).permissionController.getGrantedPermissions()
    return requiredPermissions - granted
  }

  suspend fun inspect(target: HealthConnectInspectionTarget): RunDataInspection {
    if (HealthConnectClient.getSdkStatus(appContext) != HealthConnectClient.SDK_AVAILABLE) {
      return RunDataInspection(
        target.exerciseSessionRecordId,
        target.sourcePackageName,
        target.sourceAppName,
        clock.instant(),
        RunMetricType.entries.map { unsupported(it, "Health Connect is unavailable") },
        requiredPermissions,
      )
    }
    val client = HealthConnectClient.getOrCreate(appContext)
    val granted = client.permissionController.getGrantedPermissions()
    val (sessionMetric, routeMetric) = inspectSessionAndRoute(client, target, granted)
    val otherMetrics = coroutineScope {
      listOf(
        async { inspectDistance(client, target, granted) },
        async { inspectHeartRate(client, target, granted) },
        async { inspectSpeed(client, target, granted) },
        async { inspectCadence(client, target, granted) },
        async { inspectVo2Max(client, target, granted) },
        async { inspectElevation(client, target, granted) },
        async { inspectPower(client, target, granted) },
        async { inspectCalories(client, target, granted) },
        async { inspectSteps(client, target, granted) },
      ).map { it.await() }
    }
    return RunDataInspection(
      runId = target.exerciseSessionRecordId,
      sourcePackageName = target.sourcePackageName,
      sourceAppName = target.sourceAppName,
      inspectedAt = clock.instant(),
      metrics = listOf(sessionMetric) + otherMetrics + routeMetric,
      missingPermissions = requiredPermissions - granted,
    )
  }

  private suspend fun inspectSessionAndRoute(
    client: HealthConnectClient,
    target: HealthConnectInspectionTarget,
    granted: Set<String>,
  ): Pair<MetricInspection, MetricInspection> {
    val permission = METRIC_PERMISSIONS.getValue(RunMetricType.EXERCISE_SESSION)
    if (permission !in granted) {
      return permissionRequired(RunMetricType.EXERCISE_SESSION) to permissionRequired(RunMetricType.ROUTE)
    }
    return try {
      val session = client.readRecord(ExerciseSessionRecord::class, target.exerciseSessionRecordId).record
      val packageName = session.metadata.dataOrigin.packageName
      val sourceName = resolveAppName(packageName)
      val sourceNote = if (!target.sourcePackageName.isNullOrBlank() && packageName != target.sourcePackageName) {
        listOf("Record source differs from the canonical run source")
      } else emptyList()
      val sessionMetric = MetricInspection(
        metric = RunMetricType.EXERCISE_SESSION,
        availability = MetricAvailability.AVAILABLE,
        recordCount = 1,
        sourcePackageName = packageName,
        sourceAppName = sourceName,
        summary = SessionMetricSummary(
          exerciseType = exerciseTypeName(session.exerciseType),
          startTime = session.startTime,
          endTime = session.endTime,
          durationSeconds = Duration.between(session.startTime, session.endTime).seconds.coerceAtLeast(0),
          title = session.title,
          notes = session.notes,
          healthConnectRecordId = session.metadata.id,
          deviceManufacturer = session.metadata.device?.manufacturer,
          deviceModel = session.metadata.device?.model,
        ),
        notes = sourceNote,
        recordIds = listOf(session.metadata.id),
      )
      sessionMetric to routeFromSession(session, packageName, sourceName)
    } catch (exception: CancellationException) {
      throw exception
    } catch (exception: SecurityException) {
      permissionRequired(RunMetricType.EXERCISE_SESSION) to permissionRequired(RunMetricType.ROUTE)
    } catch (exception: UnsupportedOperationException) {
      unsupported(RunMetricType.EXERCISE_SESSION, "Exercise session reads are unsupported") to
        routeMetric(RouteInspectionStatus.UNSUPPORTED, MetricAvailability.UNSUPPORTED, "Route access is unsupported")
    } catch (exception: Exception) {
      errorMetric(RunMetricType.EXERCISE_SESSION, exception) to
        routeMetric(RouteInspectionStatus.ERROR, MetricAvailability.ERROR, "Could not determine route status")
    }
  }

  private fun routeFromSession(
    session: ExerciseSessionRecord,
    packageName: String,
    sourceName: String,
  ): MetricInspection = when (val route = session.exerciseRouteResult) {
    is ExerciseRouteResult.Data -> MetricInspection(
      RunMetricType.ROUTE,
      MetricAvailability.AVAILABLE,
      recordCount = 1,
      sourcePackageName = packageName,
      sourceAppName = sourceName,
      summary = RouteMetricSummary(RouteInspectionStatus.AVAILABLE, route.exerciseRoute.route.size),
      notes = listOf("Route points are shown only as a count and are not stored"),
    )
    is ExerciseRouteResult.ConsentRequired -> routeMetric(
      RouteInspectionStatus.CONSENT_REQUIRED,
      MetricAvailability.PERMISSION_REQUIRED,
      "Explicit user consent is required to inspect this route",
      packageName,
      sourceName,
    )
    is ExerciseRouteResult.NoData -> routeMetric(
      RouteInspectionStatus.UNAVAILABLE,
      MetricAvailability.NOT_AVAILABLE,
      "No route was recorded for this workout",
      packageName,
      sourceName,
    )
    else -> routeMetric(
      RouteInspectionStatus.UNSUPPORTED,
      MetricAvailability.UNSUPPORTED,
      "Unknown route status returned by Health Connect",
      packageName,
      sourceName,
    )
  }

  private suspend fun inspectDistance(client: HealthConnectClient, target: HealthConnectInspectionTarget, granted: Set<String>) =
    inspectTotalMetric(client, target, granted, RunMetricType.DISTANCE, DistanceRecord::class) { packageName ->
      val value = client.aggregate(
        aggregateRequest(target, packageName, setOf(DistanceRecord.DISTANCE_TOTAL))
      )[DistanceRecord.DISTANCE_TOTAL]?.inMeters
      value?.let { TotalMetricSummary(it, "m") }
    }

  private suspend fun inspectElevation(client: HealthConnectClient, target: HealthConnectInspectionTarget, granted: Set<String>) =
    inspectTotalMetric(client, target, granted, RunMetricType.ELEVATION_GAINED, ElevationGainedRecord::class) { packageName ->
      client.aggregate(
        aggregateRequest(target, packageName, setOf(ElevationGainedRecord.ELEVATION_GAINED_TOTAL))
      )[ElevationGainedRecord.ELEVATION_GAINED_TOTAL]?.inMeters?.let { TotalMetricSummary(it, "m") }
    }

  private suspend fun inspectCalories(client: HealthConnectClient, target: HealthConnectInspectionTarget, granted: Set<String>) =
    inspectTotalMetric(client, target, granted, RunMetricType.CALORIES, TotalCaloriesBurnedRecord::class) { packageName ->
      client.aggregate(
        aggregateRequest(target, packageName, setOf(TotalCaloriesBurnedRecord.ENERGY_TOTAL))
      )[TotalCaloriesBurnedRecord.ENERGY_TOTAL]?.inKilocalories?.let { TotalMetricSummary(it, "kcal") }
    }

  private suspend fun inspectSteps(client: HealthConnectClient, target: HealthConnectInspectionTarget, granted: Set<String>): MetricInspection =
    safeMetric(RunMetricType.STEPS, granted) {
      val records = sourceRecords(client, StepsRecord::class, target)
      if (records.selected.isEmpty()) return@safeMetric notAvailable(RunMetricType.STEPS, records.alternates)
      val total = client.aggregate(
        aggregateRequest(target, records.selectedPackage, setOf(StepsRecord.COUNT_TOTAL))
      )[StepsRecord.COUNT_TOTAL]
      if (total == null) notAvailable(RunMetricType.STEPS, records.alternates)
      else availableMetric(RunMetricType.STEPS, records, CountMetricSummary(total))
    }

  private suspend fun inspectHeartRate(client: HealthConnectClient, target: HealthConnectInspectionTarget, granted: Set<String>): MetricInspection =
    inspectSeriesMetric(client, target, granted, RunMetricType.HEART_RATE, HeartRateRecord::class,
      samples = { records -> records.flatMap { record -> record.samples.map { TimedNumericSample(it.time, it.beatsPerMinute.toDouble()) } } },
      summarize = ::summarizeHeartRateSamples,
    )

  private suspend fun inspectSpeed(client: HealthConnectClient, target: HealthConnectInspectionTarget, granted: Set<String>): MetricInspection =
    inspectSeriesMetric(client, target, granted, RunMetricType.SPEED, SpeedRecord::class,
      samples = { records -> records.flatMap { record -> record.samples.map { TimedNumericSample(it.time, it.speed.inMetersPerSecond) } } },
      summarize = ::summarizeSpeedSamples,
      extraNotes = { summary ->
        if (summary.average > 0.0) {
          val paceSeconds = (1000.0 / summary.average).toLong()
          listOf("Derived average pace: ${paceSeconds / 60}:${(paceSeconds % 60).toString().padStart(2, '0')} min/km")
        } else emptyList()
      },
    )

  private suspend fun inspectCadence(client: HealthConnectClient, target: HealthConnectInspectionTarget, granted: Set<String>): MetricInspection =
    inspectSeriesMetric(client, target, granted, RunMetricType.CADENCE, StepsCadenceRecord::class,
      samples = { records -> records.flatMap { record -> record.samples.map { TimedNumericSample(it.time, it.rate) } } },
      summarize = ::summarizeCadenceSamples,
    )

  private suspend fun inspectPower(client: HealthConnectClient, target: HealthConnectInspectionTarget, granted: Set<String>): MetricInspection =
    inspectSeriesMetric(client, target, granted, RunMetricType.POWER, PowerRecord::class,
      samples = { records -> records.flatMap { record -> record.samples.map { TimedNumericSample(it.time, it.power.inWatts) } } },
      summarize = ::summarizePowerSamples,
    )

  private suspend fun inspectVo2Max(
    client: HealthConnectClient,
    target: HealthConnectInspectionTarget,
    granted: Set<String>,
  ): MetricInspection = safeMetric(RunMetricType.VO2_MAX, granted) {
    val during = sourceRecords(client, Vo2MaxRecord::class, target)
    val inWorkout = during.selected.filter { !it.time.isBefore(target.startTime) && !it.time.isAfter(target.endTime) }
    val record: Vo2MaxRecord
    val timing: Vo2MaxTiming
    var matchingRecordIds: List<String>
    var matchingRecordCount: Int
    val alternatives = during.alternates.toMutableList()
    val selectedPackage: String?
    if (inWorkout.isNotEmpty()) {
      record = inWorkout.maxBy(Vo2MaxRecord::time)
      timing = Vo2MaxTiming.DURING_WORKOUT
      selectedPackage = during.selectedPackage
      matchingRecordIds = inWorkout.map { it.metadata.id }
      matchingRecordCount = inWorkout.size
    } else {
      val nearbyTarget = target.copy(
        startTime = target.startTime.minus(VO2_NEARBY_WINDOW),
        endTime = target.endTime.plus(VO2_NEARBY_WINDOW),
      )
      val nearby = sourceRecords(client, Vo2MaxRecord::class, nearbyTarget)
      nearby.alternates.forEach { alternate -> if (alternatives.none { it.sourcePackageName == alternate.sourcePackageName }) alternatives += alternate }
      val closest = nearby.selected.minByOrNull { distanceFromWorkout(it.time, target) }
        ?: return@safeMetric notAvailable(RunMetricType.VO2_MAX, alternatives)
      record = closest
      timing = Vo2MaxTiming.NEAR_WORKOUT
      selectedPackage = nearby.selectedPackage
      matchingRecordIds = nearby.selected.map { it.metadata.id }
      matchingRecordCount = nearby.selected.size
    }
    MetricInspection(
      metric = RunMetricType.VO2_MAX,
      availability = MetricAvailability.AVAILABLE,
      recordCount = matchingRecordCount,
      sourcePackageName = selectedPackage,
      sourceAppName = selectedPackage?.let(::resolveAppName),
      summary = Vo2MaxMetricSummary(
        record.vo2MillilitersPerMinuteKilogram,
        record.time,
        measurementMethodName(record.measurementMethod),
        timing,
      ),
      notes = when {
        timing == Vo2MaxTiming.NEAR_WORKOUT -> listOf("Closest value within seven days; it was not recorded during this workout")
        matchingRecordCount > 1 -> listOf("$matchingRecordCount values occurred during the workout; the latest is shown")
        else -> emptyList()
      },
      recordIds = matchingRecordIds,
      alternateSources = alternatives,
    )
  }

  private suspend fun <T : Record> inspectTotalMetric(
    client: HealthConnectClient,
    target: HealthConnectInspectionTarget,
    granted: Set<String>,
    metric: RunMetricType,
    recordType: KClass<T>,
    summary: suspend (String?) -> TotalMetricSummary?,
  ): MetricInspection = safeMetric(metric, granted) {
    val records = sourceRecords(client, recordType, target)
    if (records.selected.isEmpty()) return@safeMetric notAvailable(metric, records.alternates)
    val total = summary(records.selectedPackage)
      ?: return@safeMetric notAvailable(metric, records.alternates)
    availableMetric(metric, records, total)
  }

  private suspend fun <T : Record> inspectSeriesMetric(
    client: HealthConnectClient,
    target: HealthConnectInspectionTarget,
    granted: Set<String>,
    metric: RunMetricType,
    recordType: KClass<T>,
    samples: (List<T>) -> List<TimedNumericSample>,
    summarize: (List<TimedNumericSample>) -> com.example.openruncoach.domain.inspection.SeriesMetricSummary?,
    extraNotes: (com.example.openruncoach.domain.inspection.SeriesMetricSummary) -> List<String> = { emptyList() },
  ): MetricInspection = safeMetric(metric, granted) {
    val records = sourceRecords(client, recordType, target)
    if (records.selected.isEmpty()) return@safeMetric notAvailable(metric, records.alternates)
    val clippedSamples = samples(records.selected).filter { sample ->
      !sample.timestamp.isBefore(target.startTime) && !sample.timestamp.isAfter(target.endTime)
    }
    val result = summarize(clippedSamples) ?: return@safeMetric notAvailable(metric, records.alternates)
    availableMetric(metric, records, result, extraNotes(result), clippedSamples)
  }

  private suspend fun safeMetric(
    metric: RunMetricType,
    granted: Set<String>,
    read: suspend () -> MetricInspection,
  ): MetricInspection {
    val permission = METRIC_PERMISSIONS.getValue(metric)
    if (permission !in granted) return permissionRequired(metric)
    return try {
      read()
    } catch (exception: CancellationException) {
      throw exception
    } catch (exception: SecurityException) {
      permissionRequired(metric)
    } catch (exception: UnsupportedOperationException) {
      unsupported(metric, "This record type is unsupported by the installed Health Connect provider")
    } catch (exception: Exception) {
      errorMetric(metric, exception)
    }
  }

  private suspend fun <T : Record> sourceRecords(
    client: HealthConnectClient,
    recordType: KClass<T>,
    target: HealthConnectInspectionTarget,
  ): SourceRecords<T> {
    val canonicalPackage = target.sourcePackageName?.takeIf(String::isNotBlank)
    if (canonicalPackage != null) {
      val selected = readAll(client, recordType, target, canonicalPackage)
      val all = try {
        readAll(client, recordType, target, null)
      } catch (exception: CancellationException) {
        throw exception
      } catch (exception: Exception) {
        emptyList()
      }
      return SourceRecords(selected, canonicalPackage, alternateSources(all, canonicalPackage))
    }
    val all = readAll(client, recordType, target, null)
    val groups = all.groupBy { it.metadata.dataOrigin.packageName }
    val selectedGroup = groups.entries.sortedWith(
      compareByDescending<Map.Entry<String, List<T>>> { it.value.size }.thenBy { it.key }
    ).firstOrNull()
    return SourceRecords(
      selected = selectedGroup?.value.orEmpty(),
      selectedPackage = selectedGroup?.key,
      alternates = groups.filterKeys { it != selectedGroup?.key }.toAlternates(),
      notes = listOf("Canonical source was unknown; one source was selected deterministically"),
    )
  }

  private suspend fun <T : Record> readAll(
    client: HealthConnectClient,
    recordType: KClass<T>,
    target: HealthConnectInspectionTarget,
    sourcePackageName: String?,
  ): List<T> {
    val records = mutableListOf<T>()
    var pageToken: String? = null
    do {
      val page = client.readRecords(
        ReadRecordsRequest(
          recordType = recordType,
          timeRangeFilter = TimeRangeFilter.between(target.startTime, target.endTime),
          dataOriginFilter = sourcePackageName?.let { setOf(DataOrigin(it)) }.orEmpty(),
          pageToken = pageToken,
        )
      )
      records += page.records
      pageToken = page.pageToken
    } while (!pageToken.isNullOrEmpty())
    return records.distinctBy { it.metadata.id }
  }

  private fun <T : Record> availableMetric(
    metric: RunMetricType,
    records: SourceRecords<T>,
    summary: com.example.openruncoach.domain.inspection.MetricSummary,
    notes: List<String> = emptyList(),
    samples: List<TimedNumericSample> = emptyList(),
  ) = MetricInspection(
    metric = metric,
    availability = MetricAvailability.AVAILABLE,
    recordCount = records.selected.size,
    sourcePackageName = records.selectedPackage,
    sourceAppName = records.selectedPackage?.let(::resolveAppName),
    summary = summary,
    notes = records.notes + notes,
    recordIds = records.selected.map { it.metadata.id },
    alternateSources = records.alternates,
    samples = samples,
  )

  private fun <T : Record> alternateSources(records: List<T>, excludedPackage: String): List<AlternateSourceInspection> =
    records.groupBy { it.metadata.dataOrigin.packageName }
      .filterKeys { it != excludedPackage }
      .toAlternates()

  private fun <T : Record> Map<String, List<T>>.toAlternates(): List<AlternateSourceInspection> =
    entries.sortedBy { it.key }.map { (packageName, records) ->
      AlternateSourceInspection(packageName, resolveAppName(packageName), records.size)
    }

  private fun aggregateRequest(
    target: HealthConnectInspectionTarget,
    packageName: String?,
    metrics: Set<androidx.health.connect.client.aggregate.AggregateMetric<*>>,
  ) = AggregateRequest(
    metrics = metrics,
    timeRangeFilter = TimeRangeFilter.between(target.startTime, target.endTime),
    dataOriginFilter = packageName?.let { setOf(DataOrigin(it)) }.orEmpty(),
  )

  private fun resolveAppName(packageName: String): String = try {
    val manager = appContext.packageManager
    manager.getApplicationLabel(manager.getApplicationInfo(packageName, 0)).toString()
  } catch (exception: PackageManager.NameNotFoundException) {
    packageName
  } catch (exception: SecurityException) {
    packageName
  }

  private data class SourceRecords<T : Record>(
    val selected: List<T>,
    val selectedPackage: String?,
    val alternates: List<AlternateSourceInspection>,
    val notes: List<String> = emptyList(),
  )

  companion object {
    private val VO2_NEARBY_WINDOW: Duration = Duration.ofDays(7)
    private val METRIC_PERMISSIONS: Map<RunMetricType, String> = mapOf(
      RunMetricType.EXERCISE_SESSION to HealthPermission.getReadPermission(ExerciseSessionRecord::class),
      RunMetricType.DISTANCE to HealthPermission.getReadPermission(DistanceRecord::class),
      RunMetricType.HEART_RATE to HealthPermission.getReadPermission(HeartRateRecord::class),
      RunMetricType.SPEED to HealthPermission.getReadPermission(SpeedRecord::class),
      RunMetricType.CADENCE to HealthPermission.getReadPermission(StepsCadenceRecord::class),
      RunMetricType.VO2_MAX to HealthPermission.getReadPermission(Vo2MaxRecord::class),
      RunMetricType.ELEVATION_GAINED to HealthPermission.getReadPermission(ElevationGainedRecord::class),
      RunMetricType.POWER to HealthPermission.getReadPermission(PowerRecord::class),
      RunMetricType.CALORIES to HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class),
      RunMetricType.STEPS to HealthPermission.getReadPermission(StepsRecord::class),
      RunMetricType.ROUTE to HealthPermission.getReadPermission(ExerciseSessionRecord::class),
    )
  }
}

private fun permissionRequired(metric: RunMetricType) = MetricInspection(
  metric,
  MetricAvailability.PERMISSION_REQUIRED,
  notes = listOf("Read permission has not been granted"),
)

private fun unsupported(metric: RunMetricType, message: String) = MetricInspection(
  metric,
  MetricAvailability.UNSUPPORTED,
  notes = listOf(message),
)

private fun errorMetric(metric: RunMetricType, exception: Exception) = MetricInspection(
  metric,
  MetricAvailability.ERROR,
  notes = listOf("Read failed: ${exception::class.simpleName ?: "Unknown error"}"),
)

private fun notAvailable(
  metric: RunMetricType,
  alternatives: List<AlternateSourceInspection> = emptyList(),
) = MetricInspection(
  metric,
  MetricAvailability.NOT_AVAILABLE,
  notes = listOf("No data recorded for this workout from the canonical source"),
  alternateSources = alternatives,
)

private fun routeMetric(
  status: RouteInspectionStatus,
  availability: MetricAvailability,
  note: String,
  packageName: String? = null,
  sourceName: String? = null,
) = MetricInspection(
  RunMetricType.ROUTE,
  availability,
  sourcePackageName = packageName,
  sourceAppName = sourceName,
  summary = RouteMetricSummary(status),
  notes = listOf(note),
)

private fun exerciseTypeName(type: Int): String =
  ExerciseSessionRecord.EXERCISE_TYPE_INT_TO_STRING_MAP[type]
    ?.lowercase()
    ?.replace('_', ' ')
    ?.replaceFirstChar { it.titlecase() }
    ?: "Exercise type $type"

private fun measurementMethodName(method: Int): String =
  Vo2MaxRecord.MEASUREMENT_METHOD_INT_TO_STRING_MAP[method]
    ?.lowercase()
    ?.replace('_', ' ')
    ?.replaceFirstChar { it.titlecase() }
    ?: "Method $method"

private fun distanceFromWorkout(
  timestamp: java.time.Instant,
  target: HealthConnectInspectionTarget,
): Duration = when {
  timestamp.isBefore(target.startTime) -> Duration.between(timestamp, target.startTime)
  timestamp.isAfter(target.endTime) -> Duration.between(target.endTime, timestamp)
  else -> Duration.ZERO
}
