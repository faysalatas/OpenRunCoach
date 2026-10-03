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
      val granted = dataSource.grantedReadPermissions()
      lastResolvableException = null
      remember(successState(granted))
    } catch (error: CancellationException) {
      throw error
    } catch (error: Throwable) {
      failureState(error, SamsungHealthOperations.GET_EXERCISE_PERMISSION)
    }
  }

  suspend fun requestReadPermission(activity: Activity): SamsungHealthAvailabilityState {
    dataSource.platformPrecheck()?.let { return remember(it) }
    return try {
      dataSource.requestExerciseReadPermission(activity)
      val granted = dataSource.grantedReadPermissions()
      lastResolvableException = null
      remember(successState(granted, SamsungHealthOperations.REQUEST_EXERCISE_PERMISSION))
    } catch (error: CancellationException) {
      throw error
    } catch (error: Throwable) {
      failureState(error, SamsungHealthOperations.REQUEST_EXERCISE_PERMISSION)
    }
  }

  suspend fun requestExerciseLocationReadPermission(activity: Activity): SamsungHealthAvailabilityState {
    dataSource.platformPrecheck()?.let { return remember(it) }
    return try {
      dataSource.requestExerciseLocationReadPermission(activity)
      val granted = dataSource.grantedReadPermissions()
      lastResolvableException = null
      remember(successState(granted, SamsungHealthOperations.REQUEST_EXERCISE_LOCATION_PERMISSION))
    } catch (error: CancellationException) {
      throw error
    } catch (error: Throwable) {
      failureState(error, SamsungHealthOperations.REQUEST_EXERCISE_LOCATION_PERMISSION)
    }
  }

  internal fun failureState(error: Throwable, operation: String): SamsungHealthAvailabilityState {
    val healthError = error as? HealthDataException
    Log.e(
      SamsungHealthDataSource.LOG_TAG,
      "SDK exception: ${error.javaClass.name}; code=${healthError?.errorCode}; message=${healthError?.errorMessage ?: error.message}",
    )
    lastResolvableException = (error as? ResolvablePlatformException)?.takeIf { it.hasResolution }
    val failure = samsungHealthFailure(error, dataSource.samsungHealthDetection(), operation)
    val previous = lastState
    val state = if (operation == SamsungHealthOperations.REQUEST_EXERCISE_LOCATION_PERMISSION) {
      val exercisePermission = previous?.exerciseReadPermission
        ?: SamsungExercisePermissionState.UNABLE_TO_CHECK
      failure.copy(
        status = if (
          failure.status == SamsungHealthAvailability.PERMISSION_REQUIRED &&
          exercisePermission == SamsungExercisePermissionState.GRANTED
        ) SamsungHealthAvailability.AVAILABLE else failure.status,
        message = if (
          failure.status == SamsungHealthAvailability.PERMISSION_REQUIRED &&
          exercisePermission == SamsungExercisePermissionState.GRANTED
        ) SamsungHealthAvailability.AVAILABLE.message() else failure.message,
        exerciseReadPermission = exercisePermission,
        exerciseLocationReadPermission = if (failure.status == SamsungHealthAvailability.PERMISSION_REQUIRED) {
          SamsungExercisePermissionState.NOT_GRANTED
        } else {
          SamsungExercisePermissionState.UNABLE_TO_CHECK
        },
      )
    } else {
      failure.copy(
        exerciseLocationReadPermission = previous?.exerciseLocationReadPermission
          ?: SamsungExercisePermissionState.UNABLE_TO_CHECK,
      )
    }
    return remember(state)
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
    granted: SamsungReadPermissionGrants,
    operation: String = SamsungHealthOperations.GET_EXERCISE_PERMISSION,
  ): SamsungHealthAvailabilityState {
    val status = if (granted.exercise) {
      SamsungHealthAvailability.AVAILABLE
    } else {
      SamsungHealthAvailability.PERMISSION_REQUIRED
    }
    return SamsungHealthAvailabilityState(
      status = status,
      message = status.message(),
      sdkLibraryLoaded = true,
      samsungHealthDetection = SamsungHealthDetection.DETECTED,
      exerciseReadPermission = granted.exercise.toPermissionState(),
      exerciseLocationReadPermission = granted.exerciseLocation.toPermissionState(),
      developerAuthorization = SamsungDeveloperAuthorization.WORKING,
      lastOperation = operation,
      lastException = null,
    )
  }

  private fun remember(state: SamsungHealthAvailabilityState): SamsungHealthAvailabilityState {
    lastState = state
    return state
  }
}

private fun Boolean.toPermissionState(): SamsungExercisePermissionState =
  if (this) SamsungExercisePermissionState.GRANTED else SamsungExercisePermissionState.NOT_GRANTED
