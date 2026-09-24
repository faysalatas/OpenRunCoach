package com.example.openruncoach.domain.workout

import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StructuredWorkoutModelTest {
  @Test fun easyRunUsesOneDurationSegmentWithoutIntervalStructure() {
    val workout = StructuredWorkoutSamples.easyRun()
    assertEquals(WorkoutType.EASY_RUN, workout.type)
    assertEquals(1, workout.blocks.size)
    assertTrue(workout.blocks.single() is SingleSegmentBlock)
  }

  @Test fun durationSegmentRepresentsSecondsExplicitly() {
    val completion = StructuredWorkoutSamples.easyRun().blocks.single().segments.single().completion
    assertEquals(DurationCompletion(2_100), completion)
  }

  @Test fun distanceSegmentRepresentsMetersExplicitly() {
    val completion = StructuredWorkoutSamples.longRun().blocks.single().segments.single().completion
    assertEquals(DistanceCompletion(8_000.0), completion)
  }

  @Test fun openManualSegmentNeedsNoFakeDurationOrDistance() {
    val drills = StructuredWorkoutSamples.technique().blocks[1].segments
    assertTrue(drills.all { it.completion == OpenCompletion })
  }

  @Test fun rpeTargetPreservesRange() {
    val target = StructuredWorkoutSamples.easyRun().blocks.single().segments.single().primaryIntensity as RpeTarget
    assertEquals(3, target.minimum)
    assertEquals(4, target.maximum)
  }

  @Test fun paceTargetPreservesPaceRange() {
    val target = PaceTarget(360.0, 370.0)
    assertEquals(360.0, target.minimumSecondsPerKm, 0.01)
    assertEquals("6:00–6:10 min/km", WorkoutDescriptionFormatter.intensity(target))
  }

  @Test fun heartRateTargetPreservesBpmRange() {
    assertEquals("145–155 bpm", WorkoutDescriptionFormatter.intensity(HeartRateTarget(145, 155)))
  }

  @Test fun heartRateZoneIsExplicitlyUncalibratedByDefault() {
    val target = HeartRateZoneTarget(HeartRateZone.ZONE_2)
    assertEquals(GuidanceConfidence.UNCALIBRATED, target.confidence)
    assertTrue(WorkoutDescriptionFormatter.intensity(target).contains("uncalibrated"))
  }

  @Test fun segmentSupportsPrimaryAndSecondaryGuidance() {
    val segment = segment(
      completion = DurationCompletion(600),
      primary = RpeTarget(3, 4),
      secondary = listOf(HeartRateZoneTarget(HeartRateZone.ZONE_2)),
    )
    assertTrue(segment.primaryIntensity is RpeTarget)
    assertTrue(segment.secondaryIntensities.single() is HeartRateZoneTarget)
  }

  @Test fun intervalBlockIsStoredAsOneRepeatBlock() {
    val repeat = StructuredWorkoutSamples.intervals().blocks[1] as RepeatBlock
    assertEquals(6, repeat.repetitions)
    assertEquals(2, repeat.segments.size)
    assertTrue(repeat.segments[0].completion is DistanceCompletion)
    assertTrue(repeat.segments[1].completion is DurationCompletion)
  }

  @Test fun thresholdBlockHasThreeEightMinuteWorkAndTwoMinuteRecoveryRounds() {
    val repeat = StructuredWorkoutSamples.threshold().blocks[1] as RepeatBlock
    assertEquals(3, repeat.repetitions)
    assertEquals(DurationCompletion(480), repeat.segments[0].completion)
    assertEquals(DurationCompletion(120), repeat.segments[1].completion)
  }

  @Test fun runWalkBlockUsesDistinctWalkCategory() {
    val repeat = StructuredWorkoutSamples.runWalk().blocks.single() as RepeatBlock
    assertEquals(6, repeat.repetitions)
    assertEquals(SegmentCategory.WALK, repeat.segments[1].category)
  }

  @Test fun stridesAreFastButRelaxedRatherThanMaxEffort() {
    val workout = StructuredWorkoutSamples.strides()
    val stride = (workout.blocks[1] as RepeatBlock).segments[0]
    assertEquals(SegmentCategory.STRIDE, stride.category)
    assertTrue(stride.notes.orEmpty().contains("relaxed", ignoreCase = true))
    assertTrue(workout.cues.any { it.contains("not a maximum", ignoreCase = true) })
  }

  @Test fun techniqueContainsManualDrillsAndRepeatedStrides() {
    val workout = StructuredWorkoutSamples.technique()
    assertTrue((workout.blocks[1] as RepeatBlock).segments.all { it.category == SegmentCategory.DRILL })
    assertEquals(4, (workout.blocks[2] as RepeatBlock).repetitions)
  }

  @Test fun simpleDurationEstimateIsComplete() {
    val estimate = WorkoutEstimateCalculator.calculate(StructuredWorkoutSamples.easyRun())
    assertEquals(2_100L, estimate.estimatedTotalDurationSeconds)
    assertEquals(2_100L, estimate.knownDurationSeconds)
  }

  @Test fun repeatDurationEstimateMultipliesEveryRound() {
    val estimate = WorkoutEstimateCalculator.calculate(StructuredWorkoutSamples.runWalk())
    assertEquals(1_440L, estimate.estimatedTotalDurationSeconds)
    assertEquals(1, estimate.repeatBlockCount)
    assertEquals(6, estimate.totalRepeatedIterations)
  }

  @Test fun thresholdEstimateIncludesWarmupRoundsAndCooldown() {
    val estimate = WorkoutEstimateCalculator.calculate(StructuredWorkoutSamples.threshold())
    assertEquals(3_000L, estimate.estimatedTotalDurationSeconds)
    assertEquals(50, estimate.estimatedTotalDurationSeconds!! / 60)
    assertEquals(1_440L, estimate.workDurationSeconds)
    assertEquals(360L, estimate.recoveryDurationSeconds)
    assertEquals(1_200L, estimate.warmupCooldownDurationSeconds)
    assertEquals(1, estimate.repeatBlockCount)
    assertEquals(3, estimate.totalRepeatedIterations)
  }

  @Test fun techniqueEstimateKeepsIndependentRepeatBlocksAndPartialDuration() {
    val estimate = WorkoutEstimateCalculator.calculate(StructuredWorkoutSamples.technique())
    assertEquals(2, estimate.repeatBlockCount)
    assertEquals(6, estimate.totalRepeatedIterations)
    assertNull(estimate.estimatedTotalDurationSeconds)
    assertEquals(1_520L, estimate.knownDurationSeconds)
  }

  @Test fun stridesEstimateReportsOneBlockFourRoundsAndCompleteDuration() {
    val estimate = WorkoutEstimateCalculator.calculate(StructuredWorkoutSamples.strides())
    assertEquals(1, estimate.repeatBlockCount)
    assertEquals(4, estimate.totalRepeatedIterations)
    assertEquals(1_820L, estimate.estimatedTotalDurationSeconds)
  }

  @Test fun intervalsEstimateKeepsKnownDistanceAndPartialDuration() {
    val estimate = WorkoutEstimateCalculator.calculate(StructuredWorkoutSamples.intervals())
    assertEquals(1, estimate.repeatBlockCount)
    assertEquals(6, estimate.totalRepeatedIterations)
    assertNull(estimate.estimatedTotalDurationSeconds)
    assertEquals(2_040L, estimate.knownDurationSeconds)
    assertNull(estimate.estimatedTotalDistanceMeters)
    assertEquals(4_800.0, estimate.knownDistanceMeters, 0.01)
  }

  @Test fun easyRunEstimateHasNoRepeatBlocks() {
    val estimate = WorkoutEstimateCalculator.calculate(StructuredWorkoutSamples.easyRun())
    assertEquals(0, estimate.repeatBlockCount)
    assertEquals(0, estimate.totalRepeatedIterations)
  }

  @Test fun distanceWorkoutWithoutPaceHasUnknownDuration() {
    val estimate = WorkoutEstimateCalculator.calculate(StructuredWorkoutSamples.longRun())
    assertNull(estimate.estimatedTotalDurationSeconds)
    assertEquals(8_000.0, estimate.estimatedTotalDistanceMeters!!, 0.01)
  }

  @Test fun paceAllowsDistanceSegmentDurationEstimate() {
    val workout = workout(
      listOf(SingleSegmentBlock("block", 0, segment(DistanceCompletion(5_000.0), PaceTarget(360.0, 360.0))))
    )
    assertEquals(1_800L, WorkoutEstimateCalculator.calculate(workout).estimatedTotalDurationSeconds)
  }

  @Test fun humanReadableThresholdSummaryIsConcise() {
    val summary = WorkoutDescriptionFormatter.summary(StructuredWorkoutSamples.threshold())
    assertTrue(summary.contains("3 x 8 min"))
    assertTrue(summary.contains("2 min easy recovery"))
    assertTrue(summary.contains("warm-up"))
    assertTrue(summary.contains("cool-down"))
  }

  @Test fun fixtureOriginIsDeterministicSimulationData() {
    assertTrue(StructuredWorkoutSamples.all.all { it.origin == WorkoutOrigin.SIMULATION })
    assertEquals("sample-easy", StructuredWorkoutSamples.all.first().id)
    assertEquals("sample-technique", StructuredWorkoutSamples.all.last().id)
  }

  @Test fun structuredWorkoutDeclaresFutureAdjustmentCapabilities() {
    val metadata = StructuredWorkoutSamples.intervals().adjustability
    assertTrue(metadata.canChangeType)
    assertTrue(metadata.canChangeSegmentCompletion)
    assertTrue(metadata.canChangeRepetitions)
    assertTrue(metadata.canChangeIntensity)
    assertTrue(metadata.canChangeRecovery)
  }

  @Test fun lifecycleAcceptsStructuredWorkoutWithoutChangingItsIdentity() {
    val workout = StructuredWorkoutSamples.threshold()
    val lifecycle = WorkoutLifecycleService().transition(WorkoutLifecycle(workout), WorkoutLifecycleEvent.RequireCheckIn)
    assertEquals(workout.id, lifecycle.plannedWorkout.id)
    assertEquals(WorkoutLifecycleState.CHECK_IN_REQUIRED, lifecycle.state)
    assertEquals(3, (lifecycle.plannedWorkout.blocks[1] as RepeatBlock).repetitions)
  }

  @Test fun repetitionCompletionSupportsDrillCounts() {
    assertEquals("10 reps", WorkoutDescriptionFormatter.completion(RepetitionCompletion(10)))
  }

  @Test fun tempoAndThresholdRemainDistinctTypes() {
    assertEquals(WorkoutType.TEMPO_RUN, StructuredWorkoutSamples.tempo().type)
    assertEquals(WorkoutType.THRESHOLD, StructuredWorkoutSamples.threshold().type)
    assertFalse(StructuredWorkoutSamples.tempo().type == StructuredWorkoutSamples.threshold().type)
  }

  private fun segment(
    completion: SegmentCompletion,
    primary: IntensityTarget = NoIntensityTarget,
    secondary: List<IntensityTarget> = emptyList(),
  ) = WorkoutSegment("segment", 0, SegmentCategory.WORK, "Work", completion, primary, secondary)

  private fun workout(blocks: List<WorkoutBlock>) = PlannedWorkout(
    "workout", LocalDate.of(2026, 9, 28), WorkoutType.INTERVALS, "Intervals", WorkoutPurpose.VO2MAX,
    blocks, "Test workout.", emptyList(), Instant.parse("2026-09-24T12:00:00Z"), origin = WorkoutOrigin.MANUAL,
  )
}
