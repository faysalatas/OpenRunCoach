package com.example.openruncoach.data.samsunghealth

import com.samsung.android.sdk.health.data.error.AuthorizationException
import com.samsung.android.sdk.health.data.error.HealthDataException
import com.samsung.android.sdk.health.data.error.InvalidRequestException
import com.samsung.android.sdk.health.data.error.PlatformInternalException
import com.samsung.android.sdk.health.data.error.ResolvablePlatformException

internal fun samsungHealthFailure(
  error: Throwable,
  detected: SamsungHealthDetection,
  operation: String,
): SamsungHealthAvailabilityState {
  val kind = when (error) {
    is ResolvablePlatformException -> SamsungSdkExceptionKind.RESOLVABLE_PLATFORM
    is AuthorizationException -> SamsungSdkExceptionKind.AUTHORIZATION
    is PlatformInternalException -> SamsungSdkExceptionKind.PLATFORM_INTERNAL
    is InvalidRequestException -> SamsungSdkExceptionKind.INVALID_REQUEST
    is HealthDataException -> SamsungSdkExceptionKind.HEALTH_DATA
    is LinkageError -> SamsungSdkExceptionKind.LINKAGE
    else -> SamsungSdkExceptionKind.OTHER
  }
  val healthError = error as? HealthDataException
  return mapSamsungSdkError(
    kind = kind,
    errorCode = healthError?.errorCode,
    errorMessage = healthError?.errorMessage ?: error.message ?: "No exception message",
    exceptionClass = error.javaClass.name,
    hasResolution = (error as? ResolvablePlatformException)?.hasResolution == true,
    detected = detected,
    operation = operation,
  )
}
