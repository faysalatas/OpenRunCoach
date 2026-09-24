package com.example.openruncoach.data.samsunghealth

import android.app.Activity
import android.util.Log
import com.samsung.android.sdk.health.data.error.HealthDataException
import com.samsung.android.sdk.health.data.error.ResolvablePlatformException
import kotlinx.coroutines.CancellationException

class SamsungHealthPermissionManager(
  private val dataSource: SamsungHealthDataSource,
) {
  private var lastResolvableException: ResolvablePlatformException? = null
  private var lastState: SamsungHealthAvailabilityState? = null

  suspend fun availability(): SamsungHealthAvailabilityState {
    dataSource.platformPrecheck()?.let { return remember(it) }
    return try {
      val granted = dataSource.grantedExerciseReadPermission()
      lastResolvableException = null
      if (granted) {
        remember(successState(SamsungHealthAvailability.AVAILABLE, SamsungExercisePermissionState.GRANTED))
      } else {
        remember(successState(SamsungHealthAvailability.PERMISSION_REQUIRED, SamsungExercisePermissionState.NOT_GRANTED))
      }
    } catch (error: CancellationException) {
      throw error
    } catch (error: Throwable) {
      failureState(error, SamsungHealthOperations.GET_EXERCISE_PERMISSION)
    }
  }

  suspend fun requestReadPermission(activity: Activity): SamsungHealthAvailabilityState {
    dataSource.platformPrecheck()?.let { return remember(it) }
    return try {
      val granted = dataSource.requestExerciseReadPermission(activity)
      lastResolvableException = null
      if (granted) {
        remember(successState(
          SamsungHealthAvailability.AVAILABLE,
          SamsungExercisePermissionState.GRANTED,
          SamsungHealthOperations.REQUEST_EXERCISE_PERMISSION,
        ))
      } else {
        remember(successState(
          SamsungHealthAvailability.PERMISSION_REQUIRED,
          SamsungExercisePermissionState.NOT_GRANTED,
          SamsungHealthOperations.REQUEST_EXERCISE_PERMISSION,
        ))
      }
    } catch (error: CancellationException) {
      throw error
    } catch (error: Throwable) {
      failureState(error, SamsungHealthOperations.REQUEST_EXERCISE_PERMISSION)
    }
  }

  internal fun failureState(error: Throwable, operation: String): SamsungHealthAvailabilityState {
    val healthError = error as? HealthDataException
    Log.e(
      SamsungHealthDataSource.LOG_TAG,
      "SDK exception: ${error.javaClass.name}; code=${healthError?.errorCode}; message=${healthError?.errorMessage ?: error.message}",
    )
    lastResolvableException = (error as? ResolvablePlatformException)?.takeIf { it.hasResolution }
    return remember(samsungHealthFailure(error, dataSource.samsungHealthDetection(), operation))
  }

  fun resolveLastIssue(activity: Activity): SamsungHealthAvailabilityState? {
    val error = lastResolvableException ?: return lastState
    Log.d(SamsungHealthDataSource.LOG_TAG, "Resolving Samsung Health platform issue after explicit user action")
    return try {
      error.resolve(activity)
      lastState
    } catch (failure: Throwable) {
      failureState(failure, "ResolvablePlatformException.resolve(activity)")
    }
  }

  private fun successState(
    status: SamsungHealthAvailability,
    permission: SamsungExercisePermissionState,
    operation: String = SamsungHealthOperations.GET_EXERCISE_PERMISSION,
  ) = SamsungHealthAvailabilityState(
    status = status,
    message = status.message(),
    sdkLibraryLoaded = true,
    samsungHealthDetection = SamsungHealthDetection.DETECTED,
    exerciseReadPermission = permission,
    developerAuthorization = SamsungDeveloperAuthorization.WORKING,
    lastOperation = operation,
    lastException = null,
  )

  private fun remember(state: SamsungHealthAvailabilityState): SamsungHealthAvailabilityState {
    lastState = state
    return state
  }
}
