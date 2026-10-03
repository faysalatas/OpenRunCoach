package com.example.openruncoach.data.samsunghealth

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import com.samsung.android.sdk.health.data.HealthDataService
import com.samsung.android.sdk.health.data.HealthDataStore
import com.samsung.android.sdk.health.data.data.HealthDataPoint
import com.samsung.android.sdk.health.data.device.Device
import com.samsung.android.sdk.health.data.permission.AccessType
import com.samsung.android.sdk.health.data.permission.Permission
import com.samsung.android.sdk.health.data.request.DataTypes
import com.samsung.android.sdk.health.data.request.InstantTimeFilter
import com.samsung.android.sdk.health.data.request.Ordering
import java.time.Instant
import kotlinx.coroutines.CancellationException

class SamsungHealthDataSource(context: Context) {
  private val appContext = context.applicationContext
  private val store: HealthDataStore by lazy {
    Log.d(LOG_TAG, "Acquiring store with HealthDataService.getStore(context)")
    HealthDataService.getStore(appContext).also {
      Log.d(LOG_TAG, "Samsung Health Data store acquired")
    }
  }

  val exerciseReadPermission: Permission by lazy {
    Permission.of(DataTypes.EXERCISE, AccessType.READ)
  }

  val exerciseLocationReadPermission: Permission by lazy {
    Permission.of(DataTypes.EXERCISE_LOCATION, AccessType.READ)
  }

  fun platformPrecheck(): SamsungHealthAvailabilityState? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
      return SamsungHealthAvailabilityState(
        status = SamsungHealthAvailability.UNSUPPORTED_ANDROID_VERSION,
        message = SamsungHealthAvailability.UNSUPPORTED_ANDROID_VERSION.message(),
        sdkLibraryLoaded = true,
        samsungHealthDetection = samsungHealthDetection(),
        exerciseReadPermission = SamsungExercisePermissionState.UNABLE_TO_CHECK,
        exerciseLocationReadPermission = SamsungExercisePermissionState.UNABLE_TO_CHECK,
        developerAuthorization = SamsungDeveloperAuthorization.UNKNOWN,
        lastOperation = SamsungHealthOperations.PLATFORM_PRECHECK,
      )
    }
    return null
  }

  fun samsungHealthDetection(): SamsungHealthDetection = try {
      appContext.packageManager.getApplicationInfo(SAMSUNG_HEALTH_PACKAGE, 0)
      SamsungHealthDetection.DETECTED
    } catch (_: PackageManager.NameNotFoundException) {
      SamsungHealthDetection.NOT_DETECTED
    } catch (_: Exception) {
      SamsungHealthDetection.UNKNOWN
    }

  internal suspend fun grantedReadPermissions(): SamsungReadPermissionGrants {
    Log.d(LOG_TAG, "Permission check start: ${SamsungHealthOperations.GET_EXERCISE_PERMISSION}")
    val granted = store.getGrantedPermissions(setOf(exerciseReadPermission, exerciseLocationReadPermission))
    return SamsungReadPermissionGrants(
      exercise = exerciseReadPermission in granted,
      exerciseLocation = exerciseLocationReadPermission in granted,
    ).also { result ->
      Log.d(
        LOG_TAG,
        "Permission check result: exercise=${result.exercise}; exerciseLocation=${result.exerciseLocation}",
      )
    }
  }

  suspend fun readExerciseData(startTime: Instant, endTime: Instant): List<HealthDataPoint> {
    val records = mutableListOf<HealthDataPoint>()
    var pageToken: String? = null
    do {
      val builder = DataTypes.EXERCISE.readDataRequestBuilder
        .setInstantTimeFilter(InstantTimeFilter.of(startTime, endTime))
        .setOrdering(Ordering.ASC)
        .setPageSize(PAGE_SIZE)
      pageToken?.let(builder::setPageToken)
      val response = store.readData(builder.build())
      records += response.dataList
      pageToken = response.pageToken
    } while (!pageToken.isNullOrBlank())
    return records
  }

  suspend fun device(deviceId: String?): Device? {
    if (deviceId.isNullOrBlank()) return null
    return try {
      store.getDeviceManager().getDevice(deviceId)
    } catch (error: CancellationException) {
      throw error
    } catch (_: Exception) {
      null
    }
  }

  internal suspend fun requestExerciseReadPermission(activity: android.app.Activity): Boolean {
    Log.d(LOG_TAG, "Permission request: ${SamsungHealthOperations.REQUEST_EXERCISE_PERMISSION}")
    return store.requestPermissions(setOf(exerciseReadPermission), activity).contains(exerciseReadPermission).also { granted ->
      Log.d(LOG_TAG, "Permission request result: ${if (granted) "granted" else "not granted"}")
    }
  }

  internal suspend fun requestExerciseLocationReadPermission(activity: android.app.Activity): Boolean {
    Log.d(LOG_TAG, "Permission request: ${SamsungHealthOperations.REQUEST_EXERCISE_LOCATION_PERMISSION}")
    return store.requestPermissions(setOf(exerciseLocationReadPermission), activity)
      .contains(exerciseLocationReadPermission)
      .also { granted ->
        Log.d(LOG_TAG, "Exercise location permission result: ${if (granted) "granted" else "not granted"}")
      }
  }

  companion object {
    internal const val LOG_TAG = "OpenRunCoachSamsung"
    private const val SAMSUNG_HEALTH_PACKAGE = "com.sec.android.app.shealth"
    private const val PAGE_SIZE = 1_000
  }
}

internal data class SamsungReadPermissionGrants(
  val exercise: Boolean,
  val exerciseLocation: Boolean,
)
