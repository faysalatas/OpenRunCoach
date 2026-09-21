package com.example.openruncoach.data

import com.example.openruncoach.domain.SourceDeviceType
import java.time.Instant

internal data class RunSourceMetadata(
  val sourcePackageName: String,
  val sourceAppName: String?,
  val sourceDeviceManufacturer: String?,
  val sourceDeviceModel: String?,
  val sourceDeviceType: SourceDeviceType?,
  val sourceRecordLastModifiedTime: Instant,
)

internal fun mapRunSource(
  packageName: String,
  manufacturer: String?,
  model: String?,
  deviceType: SourceDeviceType?,
  sourceRecordLastModifiedTime: Instant,
  resolveAppName: (String) -> String?,
): RunSourceMetadata {
  val appName = packageName.takeIf(String::isNotBlank)?.let { name ->
    resolveAppName(name)?.takeIf(String::isNotBlank) ?: name
  }
  return RunSourceMetadata(
    sourcePackageName = packageName,
    sourceAppName = appName,
    sourceDeviceManufacturer = manufacturer?.trim()?.takeIf(String::isNotEmpty),
    sourceDeviceModel = model?.trim()?.takeIf(String::isNotEmpty),
    sourceDeviceType = deviceType,
    sourceRecordLastModifiedTime = sourceRecordLastModifiedTime,
  )
}
