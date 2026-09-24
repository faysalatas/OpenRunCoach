package com.example.openruncoach.data

import com.example.openruncoach.data.local.PlannedWorkoutEntity
import com.example.openruncoach.domain.workout.DurationCompletion
import com.example.openruncoach.domain.workout.GuidanceConfidence
import com.example.openruncoach.domain.workout.HeartRateTarget
import com.example.openruncoach.domain.workout.HeartRateZone
import com.example.openruncoach.domain.workout.HeartRateZoneTarget
import com.example.openruncoach.domain.workout.PaceTarget
import com.example.openruncoach.domain.workout.RepeatBlock
import com.example.openruncoach.domain.workout.RpeTarget
import com.example.openruncoach.domain.workout.SingleSegmentBlock
import com.example.openruncoach.domain.workout.StructuredWorkoutSamples
import com.example.openruncoach.domain.workout.WorkoutLifecycleState
import com.example.openruncoach.domain.workout.WorkoutOrigin
import com.example.openruncoach.domain.workout.WorkoutType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StructuredWorkoutPersistenceMapperTest {
  @Test fun structuredWorkoutRoundTripsWithoutOpaqueSerialization() {
    val original = StructuredWorkoutSamples.threshold()
    val record = StructuredWorkoutPersistenceMapper.toRecord(original, WorkoutLifecycleState.CHECK_IN_REQUIRED.name, true)
    val restored = StructuredWorkoutPersistenceMapper.toDomain(record)
    assertEquals(original, restored)
    assertEquals(3, record.blocks.size)
    assertEquals(4, record.blocks.sumOf { it.segments.size })
    assertTrue(record.workout.isPreview)
  }

  @Test fun legacySimpleWorkoutMigratesToEquivalentDurationSegment() {
    val old = PlannedWorkoutEntity(
      "legacy", 20_000, WorkoutType.EASY_RUN.name, 30, 3, 4, 1_000,
      WorkoutLifecycleState.READY.name,
    )
    val migrated = StructuredWorkoutPersistenceMapper.legacyWorkout(old)
    val segment = (migrated.blocks.single() as SingleSegmentBlock).segment
    assertEquals(DurationCompletion(1_800), segment.completion)
    assertEquals(RpeTarget(3, 4), segment.primaryIntensity)
    assertEquals(WorkoutOrigin.MANUAL, migrated.origin)
  }

  @Test fun blockAndSegmentOrderingAreRestoredFromOrderFields() {
    val originalRecord = StructuredWorkoutPersistenceMapper.toRecord(
      StructuredWorkoutSamples.threshold(), WorkoutLifecycleState.PLANNED.name,
    )
    val shuffled = originalRecord.copy(
      blocks = originalRecord.blocks.reversed().map { it.copy(segments = it.segments.reversed()) },
    )
    val restored = StructuredWorkoutPersistenceMapper.toDomain(shuffled)
    assertEquals(listOf(0, 1, 2), restored.blocks.map { it.order })
    assertEquals(listOf(0, 1), (restored.blocks[1] as RepeatBlock).segments.map { it.order })
  }

  @Test fun lifecycleStateIsPreservedOnWorkoutRecord() {
    val record = StructuredWorkoutPersistenceMapper.toRecord(
      StructuredWorkoutSamples.intervals(), WorkoutLifecycleState.ANALYZED.name,
    )
    assertEquals(WorkoutLifecycleState.ANALYZED.name, record.workout.lifecycleState)
    assertEquals(WorkoutType.INTERVALS, StructuredWorkoutPersistenceMapper.toDomain(record).type)
  }

  @Test fun primaryAndSecondaryIntensityTargetsRoundTrip() {
    val source = StructuredWorkoutSamples.easyRun()
    val block = source.blocks.single() as SingleSegmentBlock
    val segment = block.segment.copy(
      primaryIntensity = PaceTarget(360.0, 370.0),
      secondaryIntensities = listOf(
        HeartRateTarget(145, 155),
        HeartRateZoneTarget(HeartRateZone.ZONE_2, GuidanceConfidence.UNCALIBRATED),
      ),
    )
    val workout = source.copy(blocks = listOf(block.copy(segment = segment)))
    val restored = StructuredWorkoutPersistenceMapper.toDomain(
      StructuredWorkoutPersistenceMapper.toRecord(workout, WorkoutLifecycleState.PLANNED.name),
    )
    assertEquals(workout, restored)
  }
}
