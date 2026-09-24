package com.example.openruncoach.data.samsunghealth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SamsungHealthAvailabilityTest {
  @Test
  fun platformNotInstalledIsMappedPrecisely() {
    val state = state(SamsungSdkExceptionKind.RESOLVABLE_PLATFORM, SamsungSdkErrorCodes.PLATFORM_NOT_INSTALLED)

    assertEquals(SamsungHealthAvailability.PLATFORM_NOT_INSTALLED, state.status)
    assertEquals(SamsungHealthDetection.NOT_DETECTED, state.samsungHealthDetection)
  }

  @Test
  fun oldPlatformIsMappedPrecisely() {
    val state = state(SamsungSdkExceptionKind.RESOLVABLE_PLATFORM, SamsungSdkErrorCodes.OLD_VERSION_PLATFORM)

    assertEquals(SamsungHealthAvailability.PLATFORM_TOO_OLD, state.status)
    assertEquals(SamsungHealthDetection.DETECTED, state.samsungHealthDetection)
  }

  @Test
  fun disabledPlatformIsMappedPrecisely() {
    assertEquals(
      SamsungHealthAvailability.PLATFORM_DISABLED,
      state(SamsungSdkExceptionKind.RESOLVABLE_PLATFORM, SamsungSdkErrorCodes.PLATFORM_DISABLED).status,
    )
  }

  @Test
  fun uninitializedPlatformIsMappedPrecisely() {
    assertEquals(
      SamsungHealthAvailability.PLATFORM_NOT_INITIALIZED,
      state(SamsungSdkExceptionKind.RESOLVABLE_PLATFORM, SamsungSdkErrorCodes.PLATFORM_NOT_INITIALIZED).status,
    )
  }

  @Test
  fun missingPermissionRemainsDistinctFromUnavailable() {
    val state = state(SamsungSdkExceptionKind.AUTHORIZATION, SamsungSdkErrorCodes.NO_USER_PERMISSION)

    assertEquals(SamsungHealthAvailability.PERMISSION_REQUIRED, state.status)
    assertEquals(SamsungExercisePermissionState.NOT_GRANTED, state.exerciseReadPermission)
    assertEquals(SamsungDeveloperAuthorization.WORKING, state.developerAuthorization)
  }

  @Test
  fun accessControlFailureIsMappedPrecisely() {
    val state = state(SamsungSdkExceptionKind.AUTHORIZATION, SamsungSdkErrorCodes.ACCESS_CONTROL)

    assertEquals(SamsungHealthAvailability.ACCESS_CONTROL_DENIED, state.status)
    assertEquals(SamsungDeveloperAuthorization.FAILED, state.developerAuthorization)
  }

  @Test
  fun invalidPlatformSignatureIsMappedPrecisely() {
    val state = state(SamsungSdkExceptionKind.AUTHORIZATION, SamsungSdkErrorCodes.INVALID_PLATFORM_SIGNATURE)

    assertEquals(SamsungHealthAvailability.INVALID_PLATFORM_SIGNATURE, state.status)
    assertEquals(SamsungDeveloperAuthorization.FAILED, state.developerAuthorization)
  }

  @Test
  fun unknownErrorRetainsTechnicalDetails() {
    val state = mapSamsungSdkError(
      kind = SamsungSdkExceptionKind.HEALTH_DATA,
      errorCode = 4567,
      errorMessage = "Unexpected SDK response",
      exceptionClass = "example.UnknownSamsungException",
      hasResolution = true,
    )

    assertEquals(SamsungHealthAvailability.UNKNOWN_ERROR, state.status)
    assertEquals(4567, state.lastException?.errorCode)
    assertEquals("Unexpected SDK response", state.lastException?.errorMessage)
    assertEquals("example.UnknownSamsungException", state.lastException?.exceptionClass)
    assertTrue(state.lastException?.hasResolution == true)
    assertFalse(state.message.contains("unavailable", ignoreCase = true))
  }

  private fun state(kind: SamsungSdkExceptionKind, code: Int) = mapSamsungSdkError(
    kind = kind,
    errorCode = code,
    errorMessage = "SDK error $code",
    exceptionClass = "SamsungSdkException",
    detected = SamsungHealthDetection.UNKNOWN,
  )
}
