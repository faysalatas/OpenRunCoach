package com.example.openruncoach.data

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.SpeedRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.example.openruncoach.domain.RunSummary
import com.example.openruncoach.domain.averagePaceSecondsPerKm
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit

class HealthConnectRepository(context: Context) {
  private val appContext = context.applicationContext

  val requiredPermissions: Set<String> =
    setOf(
      HealthPermission.getReadPermission(ExerciseSessionRecord::class),
      HealthPermission.getReadPermission(HeartRateRecord::class),
      HealthPermission.getReadPermission(DistanceRecord::class),
      HealthPermission.getReadPermission(SpeedRecord::class),
    )

  fun availabilityStatus(): Int = HealthConnectClient.getSdkStatus(appContext)

  suspend fun hasAllPermissions(): Boolean {
    if (availabilityStatus() != HealthConnectClient.SDK_AVAILABLE) return false
    val grantedPermissions =
      HealthConnectClient.getOrCreate(appContext).permissionController.getGrantedPermissions()
    return grantedPermissions.containsAll(requiredPermissions)
  }

  suspend fun latestRunningWorkout(): RunSummary? {
    val client = HealthConnectClient.getOrCreate(appContext)
    val now = Instant.now()
    val session =
      try {
        findLatestRunningSession(client, Instant.EPOCH, now)
      } catch (exception: SecurityException) {
        // Some providers reject a query that starts before the default history window.
        findLatestRunningSession(client, now.minus(30, ChronoUnit.DAYS), now)
      } ?: return null
    val durationSeconds = Duration.between(session.startTime, session.endTime).seconds.coerceAtLeast(0)
    if (durationSeconds == 0L) {
      return RunSummary(session.startTime, 0, null, null, null, null)
    }

    val result =
      client.aggregate(
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
    val distanceMeters = result[DistanceRecord.DISTANCE_TOTAL]?.inMeters
    return RunSummary(
      startTime = session.startTime,
      durationSeconds = durationSeconds,
      distanceMeters = distanceMeters,
      averageHeartRate = result[HeartRateRecord.BPM_AVG]?.toDouble(),
      maxHeartRate = result[HeartRateRecord.BPM_MAX],
      averagePaceSecondsPerKm =
        averagePaceSecondsPerKm(
          durationSeconds,
          distanceMeters,
          result[SpeedRecord.SPEED_AVG]?.inMetersPerSecond,
        ),
    )
  }

  private suspend fun findLatestRunningSession(
    client: HealthConnectClient,
    windowStart: Instant,
    windowEnd: Instant,
  ): ExerciseSessionRecord? {
    val sessionWindow = TimeRangeFilter.between(windowStart, windowEnd)
    var pageToken: String? = null
    var latestSession: ExerciseSessionRecord? = null

    do {
      val page =
        client.readRecords(
          ReadRecordsRequest(
            recordType = ExerciseSessionRecord::class,
            timeRangeFilter = sessionWindow,
            pageToken = pageToken,
          )
        )
      for (session in page.records) {
        if (session.exerciseType == ExerciseSessionRecord.EXERCISE_TYPE_RUNNING &&
          session.startTime >= windowStart &&
          session.endTime <= windowEnd &&
          (latestSession == null || session.startTime > latestSession.startTime)
        ) {
          latestSession = session
        }
      }
      pageToken = page.pageToken
    } while (!pageToken.isNullOrEmpty())

    return latestSession
  }
}
