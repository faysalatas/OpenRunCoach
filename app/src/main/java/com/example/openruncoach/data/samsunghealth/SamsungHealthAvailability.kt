package com.example.openruncoach.data.samsunghealth

enum class SamsungHealthAvailability {
  AVAILABLE,
  PERMISSION_REQUIRED,
  PLATFORM_NOT_INSTALLED,
  PLATFORM_TOO_OLD,
  PLATFORM_DISABLED,
  PLATFORM_NOT_INITIALIZED,
  AUTHORIZATION_FAILED,
  ACCESS_CONTROL_DENIED,
  INVALID_PLATFORM_SIGNATURE,
  CHILD_ACCOUNT_ACCESS_DENIED,
  UNSUPPORTED_OPERATION,
  INVALID_REQUEST,
  CONNECTION_ERROR,
  PLATFORM_INTERNAL_ERROR,
  UNSUPPORTED_ANDROID_VERSION,
  SDK_UNAVAILABLE,
  UNKNOWN_ERROR,
}

enum class SamsungHealthDetection { DETECTED, NOT_DETECTED, UNKNOWN }

enum class SamsungExercisePermissionState { GRANTED, NOT_GRANTED, UNABLE_TO_CHECK }

enum class SamsungDeveloperAuthorization { WORKING, FAILED, UNKNOWN }

enum class SamsungSdkExceptionKind {
  RESOLVABLE_PLATFORM,
  AUTHORIZATION,
  PLATFORM_INTERNAL,
  INVALID_REQUEST,
  HEALTH_DATA,
  LINKAGE,
  OTHER,
}

data class SamsungSdkExceptionInfo(
  val exceptionClass: String,
  val errorCode: Int?,
  val errorMessage: String,
  val hasResolution: Boolean,
)

data class SamsungHealthAvailabilityState(
  val status: SamsungHealthAvailability,
  val message: String,
  val sdkLibraryLoaded: Boolean,
  val samsungHealthDetection: SamsungHealthDetection,
  val exerciseReadPermission: SamsungExercisePermissionState,
  val developerAuthorization: SamsungDeveloperAuthorization,
  val lastOperation: String,
  val lastException: SamsungSdkExceptionInfo? = null,
)

object SamsungSdkErrorCodes {
  const val INVALID_CALLER = 1002
  const val NO_USER_PERMISSION = 2000
  const val UNSUPPORTED_OPERATION = 2001
  const val ACCESS_CONTROL = 2003
  const val INVALID_PLATFORM_SIGNATURE = 2004
  const val CHILD_ACCOUNT_ACCESS = 2005
  const val PLATFORM_NOT_INSTALLED = 3000
  const val OLD_VERSION_PLATFORM = 3001
  const val PLATFORM_DISABLED = 3002
  const val PLATFORM_NOT_INITIALIZED = 3003
  const val CONNECTION_FAIL = 9004
  const val CONNECTION_TIMEOUT = 9006
  const val PLATFORM_DISCONNECTED = 9007
}

fun mapSamsungSdkError(
  kind: SamsungSdkExceptionKind,
  errorCode: Int?,
  errorMessage: String,
  exceptionClass: String,
  hasResolution: Boolean = false,
  detected: SamsungHealthDetection = SamsungHealthDetection.UNKNOWN,
  operation: String = SamsungHealthOperations.GET_EXERCISE_PERMISSION,
): SamsungHealthAvailabilityState {
  val status = when (errorCode) {
    SamsungSdkErrorCodes.PLATFORM_NOT_INSTALLED -> SamsungHealthAvailability.PLATFORM_NOT_INSTALLED
    SamsungSdkErrorCodes.OLD_VERSION_PLATFORM -> SamsungHealthAvailability.PLATFORM_TOO_OLD
    SamsungSdkErrorCodes.PLATFORM_DISABLED -> SamsungHealthAvailability.PLATFORM_DISABLED
    SamsungSdkErrorCodes.PLATFORM_NOT_INITIALIZED -> SamsungHealthAvailability.PLATFORM_NOT_INITIALIZED
    SamsungSdkErrorCodes.NO_USER_PERMISSION -> SamsungHealthAvailability.PERMISSION_REQUIRED
    SamsungSdkErrorCodes.UNSUPPORTED_OPERATION -> SamsungHealthAvailability.UNSUPPORTED_OPERATION
    SamsungSdkErrorCodes.ACCESS_CONTROL -> SamsungHealthAvailability.ACCESS_CONTROL_DENIED
    SamsungSdkErrorCodes.INVALID_PLATFORM_SIGNATURE -> SamsungHealthAvailability.INVALID_PLATFORM_SIGNATURE
    SamsungSdkErrorCodes.CHILD_ACCOUNT_ACCESS -> SamsungHealthAvailability.CHILD_ACCOUNT_ACCESS_DENIED
    SamsungSdkErrorCodes.CONNECTION_FAIL,
    SamsungSdkErrorCodes.CONNECTION_TIMEOUT,
    SamsungSdkErrorCodes.PLATFORM_DISCONNECTED -> SamsungHealthAvailability.CONNECTION_ERROR
    else -> when (kind) {
      SamsungSdkExceptionKind.AUTHORIZATION -> SamsungHealthAvailability.AUTHORIZATION_FAILED
      SamsungSdkExceptionKind.PLATFORM_INTERNAL -> SamsungHealthAvailability.PLATFORM_INTERNAL_ERROR
      SamsungSdkExceptionKind.INVALID_REQUEST -> SamsungHealthAvailability.INVALID_REQUEST
      SamsungSdkExceptionKind.LINKAGE -> SamsungHealthAvailability.SDK_UNAVAILABLE
      else -> SamsungHealthAvailability.UNKNOWN_ERROR
    }
  }
  val platformDetection = when (status) {
    SamsungHealthAvailability.PLATFORM_NOT_INSTALLED -> SamsungHealthDetection.NOT_DETECTED
    SamsungHealthAvailability.PLATFORM_TOO_OLD,
    SamsungHealthAvailability.PLATFORM_DISABLED,
    SamsungHealthAvailability.PLATFORM_NOT_INITIALIZED -> SamsungHealthDetection.DETECTED
    else -> detected
  }
  val permission = if (status == SamsungHealthAvailability.PERMISSION_REQUIRED) {
    SamsungExercisePermissionState.NOT_GRANTED
  } else SamsungExercisePermissionState.UNABLE_TO_CHECK
  val authorization = when (status) {
    SamsungHealthAvailability.PERMISSION_REQUIRED -> SamsungDeveloperAuthorization.WORKING
    SamsungHealthAvailability.AUTHORIZATION_FAILED,
    SamsungHealthAvailability.ACCESS_CONTROL_DENIED,
    SamsungHealthAvailability.INVALID_PLATFORM_SIGNATURE,
    SamsungHealthAvailability.CHILD_ACCOUNT_ACCESS_DENIED -> SamsungDeveloperAuthorization.FAILED
    else -> SamsungDeveloperAuthorization.UNKNOWN
  }
  return SamsungHealthAvailabilityState(
    status = status,
    message = status.message(),
    sdkLibraryLoaded = kind != SamsungSdkExceptionKind.LINKAGE,
    samsungHealthDetection = platformDetection,
    exerciseReadPermission = permission,
    developerAuthorization = authorization,
    lastOperation = operation,
    lastException = SamsungSdkExceptionInfo(exceptionClass, errorCode, errorMessage, hasResolution),
  )
}

fun SamsungHealthAvailability.message(): String = when (this) {
  SamsungHealthAvailability.AVAILABLE -> "Samsung Health available"
  SamsungHealthAvailability.PERMISSION_REQUIRED -> "Samsung Health available – Exercise permission required"
  SamsungHealthAvailability.PLATFORM_NOT_INSTALLED -> "Samsung Health is not installed"
  SamsungHealthAvailability.PLATFORM_TOO_OLD -> "Samsung Health is too old for this SDK"
  SamsungHealthAvailability.PLATFORM_DISABLED -> "Samsung Health is disabled"
  SamsungHealthAvailability.PLATFORM_NOT_INITIALIZED -> "Samsung Health setup is incomplete"
  SamsungHealthAvailability.AUTHORIZATION_FAILED -> "Samsung developer authorization failed"
  SamsungHealthAvailability.ACCESS_CONTROL_DENIED -> "Samsung Health access control denied this app"
  SamsungHealthAvailability.INVALID_PLATFORM_SIGNATURE -> "Samsung Health rejected the platform signature"
  SamsungHealthAvailability.CHILD_ACCOUNT_ACCESS_DENIED -> "Samsung Health denied access for this account"
  SamsungHealthAvailability.UNSUPPORTED_OPERATION -> "Samsung Health does not support this operation"
  SamsungHealthAvailability.INVALID_REQUEST -> "Samsung Health rejected the SDK request"
  SamsungHealthAvailability.CONNECTION_ERROR -> "Samsung Health connection failed"
  SamsungHealthAvailability.PLATFORM_INTERNAL_ERROR -> "Samsung Health reported an internal platform error"
  SamsungHealthAvailability.UNSUPPORTED_ANDROID_VERSION -> "Samsung Health Data SDK requires Android 10 or newer"
  SamsungHealthAvailability.SDK_UNAVAILABLE -> "Samsung Health Data SDK library is unavailable"
  SamsungHealthAvailability.UNKNOWN_ERROR -> "Samsung Health returned an unknown error"
}

object SamsungHealthOperations {
  const val GET_EXERCISE_PERMISSION = "getGrantedPermissions(DataTypes.EXERCISE)"
  const val REQUEST_EXERCISE_PERMISSION = "requestPermissions(DataTypes.EXERCISE)"
  const val READ_EXERCISE = "readData(DataTypes.EXERCISE)"
  const val PLATFORM_PRECHECK = "Android platform compatibility check"
}
