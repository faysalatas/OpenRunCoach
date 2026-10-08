package com.example.openruncoach.ui.health

import com.example.openruncoach.domain.workout.*
import com.example.openruncoach.domain.metrics.PreparedRunChart
import com.example.openruncoach.domain.metrics.RunChartPoint
import com.example.openruncoach.domain.metrics.RunChartScale
import com.example.openruncoach.domain.metrics.MetricConfidence
import com.example.openruncoach.domain.route.RunSplit
import com.example.openruncoach.domain.route.RunSplitDerivation
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

class CompletedRunPresentationTest {
  @Test fun deltaFormattingKeepsDirectionAndNormalizedSeconds() {
    assertEquals("+0:14", formatPaceDelta(14.0))
    assertEquals("−0:13", formatPaceDelta(-13.0))
    assertEquals("0:00", formatPaceDelta(0.1))
    assertEquals("+1:05", formatPaceDelta(65.0))
  }

  @Test fun kilometreLabelsKeepFullOrdinalAndActualPartialDistance() {
    val split = RunSplit("run", 1, RunSplitDerivation.ROUTE_DERIVED, Instant.EPOCH,
      Instant.EPOCH.plusSeconds(453), 1_000.0, 453.0, 453.0,
      partial = false, confidence = MetricConfidence.HIGH)
    assertEquals("1", split.kilometreLabel())
    assertEquals("5", split.copy(ordinal = 5).kilometreLabel())
    // Use the current locale's decimal separator, just like the rest of the UI.
    assertEquals("%.2f".format(java.util.Locale.getDefault(), 0.16),
      split.copy(ordinal = 2, distanceMeters = 160.0, partial = true).kilometreLabel())
  }

  @Test fun clippingDiagnosticRetainsTheExistingDisplayScaleAndTargetExtension() {
    val chart = PreparedRunChart(listOf(listOf(RunChartPoint(0.0, 460.0), RunChartPoint(10.0, 540.0))),
      scale = RunChartScale(400.0, 500.0, listOf(400.0, 450.0, 500.0), true))
    assertTrue(chart.hasClippedPaceSamples(null))
    assertFalse(chart.hasClippedPaceSamples(PaceTarget(465.0, 545.0)))
    assertFalse(chart.copy(scale = null).hasClippedPaceSamples(null))
    assertEquals(540.0, chart.points.last().value, 0.0)
    assertEquals(500.0, chart.scale?.maximumValue)
  }
  private val target = PaceTarget(400.0, 480.0)
  private val segment = WorkoutSegment("segment", 0, SegmentCategory.EASY, "Easy", DurationCompletion(600), target)
  private fun workout(type: WorkoutType, purpose: WorkoutPurpose, blocks: List<WorkoutBlock>) =
    PlannedWorkout("workout", LocalDate.of(2026, 9, 26), type, "Workout", purpose, blocks,
      "Existing prescription", emptyList(), Instant.EPOCH)

  @Test fun singleAerobicSegmentCanSupplyWholeRunTarget() {
    assertEquals(target, workout(WorkoutType.EASY_RUN, WorkoutPurpose.AEROBIC_BASE,
      listOf(SingleSegmentBlock("block", 0, segment))).wholeRunPaceTarget())
  }

  @Test fun qualityTypeOrPurposeNeverSuppliesWholeRunTarget() {
    val blocks = listOf(SingleSegmentBlock("block", 0, segment))
    assertNull(workout(WorkoutType.INTERVALS, WorkoutPurpose.AEROBIC_BASE, blocks).wholeRunPaceTarget())
    assertNull(workout(WorkoutType.EASY_RUN, WorkoutPurpose.LACTATE_THRESHOLD, blocks).wholeRunPaceTarget())
  }

  @Test fun repeatedWorkAndMultiblockTargetsAreNotWholeRunTargets() {
    assertNull(workout(WorkoutType.EASY_RUN, WorkoutPurpose.AEROBIC_BASE,
      listOf(RepeatBlock("repeat", 0, 3, listOf(segment)))).wholeRunPaceTarget())
    assertNull(workout(WorkoutType.EASY_RUN, WorkoutPurpose.AEROBIC_BASE,
      listOf(SingleSegmentBlock("first", 0, segment), SingleSegmentBlock("second", 1, segment))).wholeRunPaceTarget())
    val missing: StructuredWorkout? = null
    assertNull(missing.wholeRunPaceTarget())
  }
}
