package com.example.openruncoach.data

import com.example.openruncoach.domain.SourceDeviceType
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RunSourceMetadataTest {
  private val lastModified = Instant.parse("2026-09-20T12:00:00Z")

  @Test
  fun mapsResolvedApplicationAndOptionalDevice() {
    val source = mapRunSource(
      packageName = "com.sec.android.app.shealth",
      manufacturer = "Samsung",
      model = "Galaxy Watch",
      deviceType = SourceDeviceType.WATCH,
      sourceRecordLastModifiedTime = lastModified,
      resolveAppName = { "Samsung Health" },
    )

    assertEquals("com.sec.android.app.shealth", source.sourcePackageName)
    assertEquals("Samsung Health", source.sourceAppName)
    assertEquals("Samsung", source.sourceDeviceManufacturer)
    assertEquals("Galaxy Watch", source.sourceDeviceModel)
    assertEquals(SourceDeviceType.WATCH, source.sourceDeviceType)
    assertEquals(lastModified, source.sourceRecordLastModifiedTime)
  }

  @Test
  fun fallsBackToPackageNameWhenApplicationIsUnavailable() {
    val source = mapRunSource(
      packageName = "com.example.provider",
      manufacturer = " ",
      model = null,
      deviceType = null,
      sourceRecordLastModifiedTime = lastModified,
      resolveAppName = { null },
    )

    assertEquals("com.example.provider", source.sourceAppName)
    assertNull(source.sourceDeviceManufacturer)
    assertNull(source.sourceDeviceModel)
    assertEquals("com.example.provider", mapRunSource(
      "com.example.provider", null, null, null, lastModified, { " " },
    ).sourceAppName)
  }
}
