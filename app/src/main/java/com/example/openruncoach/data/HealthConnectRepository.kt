package com.example.openruncoach.data

import android.content.Context
import android.content.pm.PackageManager
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.HealthConnectFeatures
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.SpeedRecord
import androidx.health.connect.client.records.metadata.Device
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.example.openruncoach.domain.RunSummary
import com.example.openruncoach.domain.SourceDeviceType
import com.example.openruncoach.domain.averagePaceSecondsPerKm
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit

class HealthConnectRepository(context: Context) {
  private val appContext = context.applicationContext
  private val runPermissions: Set<String> =
    setOf(
      HealthPermission.getReadPermission(ExerciseSessionRecord::class),
      HealthPermission.getReadPermission(HeartRateRecord::class),
      HealthPermission.getReadPermission(DistanceRecord::class),
      HealthPermission.getReadPermission(SpeedRecord::class),
    )

  val requiredPermissions: Set<String>
    get() =
      if (historyReadAvailable()) runPermissions + HealthPermission.PERMISSION_READ_HEALTH_DATA_HISTORY
      else runPermissions

  fun availabilityStatus(): Int = HealthConnectClient.getSdkStatus(appContext)

  private fun historyReadAvailable(): Boolean =
    availabilityStatus() == HealthConnectClient.SDK_AVAILABLE &&
      HealthConnectClient.getOrCreate(appContext)
        .features.getFeatureStatus(HealthConnectFeatures.FEATURE_READ_HEALTH_DATA_HISTORY) ==
        HealthConnectFeatures.FEATURE_STATUS_AVAILABLE

  suspend fun hasAllPermissions(): Boolean {
    if (availabilityStatus() != HealthConnectClient.SDK_AVAILABLE) return false
    val grantedPermissions =
      HealthConnectClient.getOrCreate(appContext).permissionController.getGrantedPermissions()
    return grantedPermissions.containsAll(requiredPermissions)
  }

  suspend fun runningWorkoutsLast90Days(): List<RunSummary> {
    val client = HealthConnectClient.getOrCreate(appContext)
    val now = Instant.now()
    val sessions =
      try {
        readRunningSessions(client, now.minus(90, ChronoUnit.DAYS), now)
      } catch (exception: SecurityException) {
        // Older Health Connect providers may only expose the default history window.
        readRunningSessions(client, now.minus(30, ChronoUnit.DAYS), now)
      }
    return sessions.sortedByDescending { it.startTime }.map { summarizeSession(client, it) }
  }

  internal suspend fun runningWorkoutSourceById(recordId: String): RunSourceMetadata? {
    val session = HealthConnectClient.getOrCreate(appContext)
      .readRecord(ExerciseSessionRecord::class, recordId).record
    return if (session.exerciseType == ExerciseSessionRecord.EXERCISE_TYPE_RUNNING) {
      sourceFor(session)
    } else null
  }

  private suspend fun readRunningSessions(
    client: HealthConnectClient,
    windowStart: Instant,
    windowEnd: Instant,
  ): List<ExerciseSessionRecord> {
    val sessions = mutableListOf<ExerciseSessionRecord>()
    val sessionWindow = TimeRangeFilter.between(windowStart, windowEnd)
    var pageToken: String? = null
    do {
      val page =
        client.readRecords(
          ReadRecordsRequest(
            recordType = ExerciseSessionRecord::class,
            timeRangeFilter = sessionWindow,
            pageToken = pageToken,
          )
        )
      sessions += page.records.filter { session ->
        session.exerciseType == ExerciseSessionRecord.EXERCISE_TYPE_RUNNING &&
          session.startTime >= windowStart &&
          session.endTime <= windowEnd
      }
      pageToken = page.pageToken
    } while (!pageToken.isNullOrEmpty())
    return sessions.distinctBy { it.metadata.id }
  }

  private suspend fun summarizeSession(
    client: HealthConnectClient,
    session: ExerciseSessionRecord,
  ): RunSummary {
    val durationSeconds = Duration.between(session.startTime, session.endTime).seconds.coerceAtLeast(0)
    val source = sourceFor(session)
    val result =
      if (durationSeconds == 0L) null else client.aggregate(
        AggregateRequest(
          metrics =
            setOf(
              DistanceRecord.DISTANCE_TOTAL,
              HeartRateRecord.BPM_AVG,
              HeartRateRecord.BPM_MAX,
              SpeedRecord.SPEED_AVG,
            ),
          timeRangeFilter = TimeRangeFilter.between(session.startTime, session.endTime),
        )
      )
    val distanceMeters = result?.get(DistanceRecord.DISTANCE_TOTAL)?.inMeters
    return RunSummary(
      healthConnectRecordId = session.metadata.id,
      startTime = session.startTime,
      endTime = session.endTime,
      durationSeconds = durationSeconds,
      distanceMeters = distanceMeters,
      averageHeartRate = result?.get(HeartRateRecord.BPM_AVG)?.toDouble(),
      maxHeartRate = result?.get(HeartRateRecord.BPM_MAX),
      averagePaceSecondsPerKm =
        averagePaceSecondsPerKm(
          durationSeconds,
          distanceMeters,
          result?.get(SpeedRecord.SPEED_AVG)?.inMetersPerSecond,
        ),
      sourcePackageName = source.sourcePackageName,
      sourceAppName = source.sourceAppName,
      sourceDeviceManufacturer = source.sourceDeviceManufacturer,
      sourceDeviceModel = source.sourceDeviceModel,
      sourceDeviceType = source.sourceDeviceType,
      sourceRecordLastModifiedTime = source.sourceRecordLastModifiedTime,
    )
  }

  private fun sourceFor(session: ExerciseSessionRecord): RunSourceMetadata = mapRunSource(
      packageName = session.metadata.dataOrigin.packageName,
      manufacturer = session.metadata.device?.manufacturer,
      model = session.metadata.device?.model,
      deviceType = session.metadata.device?.let { device ->
        if (device.type == Device.TYPE_WATCH) SourceDeviceType.WATCH else SourceDeviceType.OTHER
      },
      sourceRecordLastModifiedTime = session.metadata.lastModifiedTime,
      resolveAppName = ::resolveAppName,
    )

  private fun resolveAppName(packageName: String): String? =
    try {
      val packageManager = appContext.packageManager
      packageManager.getApplicationLabel(packageManager.getApplicationInfo(packageName, 0)).toString()
    } catch (exception: PackageManager.NameNotFoundException) {
      null
    } catch (exception: SecurityException) {
      null
    }
}
