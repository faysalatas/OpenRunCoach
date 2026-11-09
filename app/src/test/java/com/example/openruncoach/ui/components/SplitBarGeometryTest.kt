package com.example.openruncoach.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class SplitBarGeometryTest {
  @Test fun sevenTwentyFourVersusSevenTenIsNinetySixPointEightPercent() {
    val fraction = splitBarWidthFraction(1f / 444f, 1f / 430f)
    assertEquals(0.9684685f, fraction, 0.000001f)
    assertEquals(96.8f, kotlin.math.round(fraction * 1000f) / 10f, 0.0001f)
  }

  @Test fun fastestSplitOccupiesTheWholeTrack() {
    assertEquals(1f, splitBarWidthFraction(1f / 430f, 1f / 430f), 0f)
  }

  @Test fun zeroAndVerySlowSpeedsHaveNoArtificialMinimumOrExaggeration() {
    assertEquals(0f, splitBarWidthFraction(0f, 1f), 0f)
    assertEquals(0.00001f, splitBarWidthFraction(0.00001f, 1f), 0f)
    assertEquals(0.25f, splitBarWidthFraction(0.5f, 2f), 0f)
  }
}
