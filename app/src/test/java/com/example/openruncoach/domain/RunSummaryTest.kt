package com.example.openruncoach.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RunSummaryTest {
  @Test
  fun paceUsesSessionDurationAndDistance() {
    assertEquals(360.0, averagePaceSecondsPerKm(1800, 5000.0, 4.0)!!, 0.001)
  }

  @Test
  fun paceFallsBackToSpeedWhenDistanceIsMissing() {
    assertEquals(400.0, averagePaceSecondsPerKm(1800, null, 2.5)!!, 0.001)
  }

  @Test
  fun paceIsMissingWhenDistanceAndSpeedCannotBeUsed() {
    assertNull(averagePaceSecondsPerKm(1800, null, null))
    assertNull(averagePaceSecondsPerKm(1800, 0.0, 0.0))
    assertNull(averagePaceSecondsPerKm(0, 5000.0, null))
  }
}
