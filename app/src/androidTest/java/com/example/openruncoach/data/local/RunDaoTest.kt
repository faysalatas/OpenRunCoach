package com.example.openruncoach.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RunDaoTest {
  @Test
  fun upsertUpdatesExistingRunAndKeepsRunsMissingFromNextSync() = runBlocking {
    val database =
      Room.inMemoryDatabaseBuilder(
          ApplicationProvider.getApplicationContext(),
          OpenRunCoachDatabase::class.java,
        )
        .build()
    try {
      val dao = database.runDao()
      val firstRun = runEntity("health-connect-id", 1000)
      dao.upsertAll(listOf(firstRun))
      assertEquals(listOf(firstRun), dao.getRunsMissingSourceMetadata())
      dao.upsertAll(listOf(firstRun.copy(
        distanceMeters = 5000.0,
        importedAtEpochMillis = 2000,
        sourcePackageName = "com.sec.android.app.shealth",
        sourceAppName = "Samsung Health",
        sourceDeviceManufacturer = "Samsung",
        sourceDeviceModel = "Galaxy Watch",
        sourceDeviceType = "WATCH",
        sourceRecordLastModifiedTimeEpochMillis = 1500,
      )))
      dao.upsertAll(emptyList())

      val runs = dao.observeRecentRuns().first()
      assertEquals(1, runs.size)
      assertEquals(5000.0, runs.single().distanceMeters!!, 0.001)
      assertEquals(2000, runs.single().importedAtEpochMillis)
      assertEquals("com.sec.android.app.shealth", runs.single().sourcePackageName)
      assertEquals("Samsung Health", runs.single().sourceAppName)
      assertEquals("Samsung", runs.single().sourceDeviceManufacturer)
      assertEquals("Galaxy Watch", runs.single().sourceDeviceModel)
      assertEquals(emptyList<RunEntity>(), dao.getRunsMissingSourceMetadata())
    } finally {
      database.close()
    }
  }

  @Test
  fun recentRunsReturnsNewestTen() = runBlocking {
    val database =
      Room.inMemoryDatabaseBuilder(
          ApplicationProvider.getApplicationContext(),
          OpenRunCoachDatabase::class.java,
        )
        .build()
    try {
      val dao = database.runDao()
      dao.upsertAll((1..12).map { runEntity("run-$it", it.toLong()) })

      val runs = dao.observeRecentRuns().first()
      assertEquals(10, runs.size)
      assertEquals("run-12", runs.first().healthConnectRecordId)
      assertEquals("run-3", runs.last().healthConnectRecordId)
    } finally {
      database.close()
    }
  }

  @Test
  fun recentRunsOnlyReturnsCanonicalRecords() = runBlocking {
    val database = Room.inMemoryDatabaseBuilder(
      ApplicationProvider.getApplicationContext(), OpenRunCoachDatabase::class.java,
    ).build()
    try {
      database.runDao().upsertAll(listOf(
        runEntity("canonical", 2000),
        runEntity("duplicate", 3000).copy(isCanonical = false, duplicateGroupId = "group-1"),
      ))

      assertEquals(listOf("canonical"), database.runDao().observeRecentRuns().first().map { it.healthConnectRecordId })
      assertEquals(2, database.runDao().observeAllRuns().first().size)
    } finally {
      database.close()
    }
  }
}

private fun runEntity(id: String, startTimeEpochMillis: Long) =
  RunEntity(
    healthConnectRecordId = id,
    startTimeEpochMillis = startTimeEpochMillis,
    endTimeEpochMillis = startTimeEpochMillis + 1000,
    durationSeconds = 1,
    distanceMeters = null,
    averageHeartRate = null,
    maxHeartRate = null,
    averagePaceSecondsPerKm = null,
    importedAtEpochMillis = 1000,
  )
