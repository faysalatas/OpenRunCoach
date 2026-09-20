package com.example.openruncoach.data

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.SpeedRecord

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
}
