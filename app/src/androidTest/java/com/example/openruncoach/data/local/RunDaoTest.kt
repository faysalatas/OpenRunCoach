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
      dao.upsertAll(listOf(firstRun.copy(distanceMeters = 5000.0, importedAtEpochMillis = 2000)))
      dao.upsertAll(emptyList())

      val runs = dao.observeRecentRuns().first()
      assertEquals(1, runs.size)
      assertEquals(5000.0, runs.single().distanceMeters!!, 0.001)
      assertEquals(2000, runs.single().importedAtEpochMillis)
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
