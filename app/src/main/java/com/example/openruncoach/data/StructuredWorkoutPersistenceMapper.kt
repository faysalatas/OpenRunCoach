package com.example.openruncoach.data

import com.example.openruncoach.data.local.PlannedWorkoutEntity
import com.example.openruncoach.data.local.PlannedWorkoutRecord
import com.example.openruncoach.data.local.WorkoutBlockEntity
import com.example.openruncoach.data.local.WorkoutBlockRecord
import com.example.openruncoach.data.local.WorkoutCueEntity
import com.example.openruncoach.data.local.WorkoutIntensityTargetEntity
import com.example.openruncoach.data.local.WorkoutSegmentEntity
import com.example.openruncoach.data.local.WorkoutSegmentRecord
import com.example.openruncoach.domain.workout.DistanceCompletion
import com.example.openruncoach.domain.workout.DurationCompletion
import com.example.openruncoach.domain.workout.GuidanceConfidence
import com.example.openruncoach.domain.workout.HeartRateTarget
import com.example.openruncoach.domain.workout.HeartRateZone
import com.example.openruncoach.domain.workout.HeartRateZoneTarget
import com.example.openruncoach.domain.workout.IntensityTarget
import com.example.openruncoach.domain.workout.NoIntensityTarget
import com.example.openruncoach.domain.workout.OpenCompletion
import com.example.openruncoach.domain.workout.PaceTarget
import com.example.openruncoach.domain.workout.PlannedWorkout
import com.example.openruncoach.domain.workout.RepeatBlock
import com.example.openruncoach.domain.workout.RepetitionCompletion
import com.example.openruncoach.domain.workout.RpeTarget
import com.example.openruncoach.domain.workout.SegmentCategory
import com.example.openruncoach.domain.workout.SingleSegmentBlock
import com.example.openruncoach.domain.workout.WorkoutBlock
import com.example.openruncoach.domain.workout.WorkoutAdjustability
import com.example.openruncoach.domain.workout.WorkoutOrigin
import com.example.openruncoach.domain.workout.WorkoutPurpose
import com.example.openruncoach.domain.workout.WorkoutSegment
import com.example.openruncoach.domain.workout.WorkoutType
import com.example.openruncoach.domain.workout.defaultExplanation
import com.example.openruncoach.domain.workout.displayName
import com.example.openruncoach.domain.workout.simpleDurationWorkout
import java.time.Instant
import java.time.LocalDate

object StructuredWorkoutPersistenceMapper {
  fun toRecord(workout: PlannedWorkout, lifecycleState: String, isPreview: Boolean = false): PlannedWorkoutRecord {
    val entity = PlannedWorkoutEntity(
      workout.id, workout.date.toEpochDay(), workout.type.name, workout.durationMinutes,
      workout.targetRpeMin, workout.targetRpeMax, workout.createdAt.toEpochMilli(), lifecycleState,
      workout.name, workout.purpose.name, workout.explanation, workout.goalId, workout.origin.name, isPreview,
      workout.adjustability.canChangeType,
      workout.adjustability.canChangeTotalDuration,
      workout.adjustability.canChangeSegmentCompletion,
      workout.adjustability.canChangeRepetitions,
      workout.adjustability.canChangeIntensity,
      workout.adjustability.canChangeRecovery,
    )
    val blocks = workout.blocks.map { block ->
      WorkoutBlockRecord(
        WorkoutBlockEntity(
          block.id, workout.id, block.order,
          if (block is RepeatBlock) BlockType.REPEAT.name else BlockType.SINGLE.name,
          if (block is RepeatBlock) block.repetitions else 1,
          block.label,
        ),
        block.segments.map { segment ->
          val completion = segment.completion
          WorkoutSegmentRecord(
            WorkoutSegmentEntity(
              segment.id, block.id, segment.order, segment.category.name, segment.name,
              completion.typeName(),
              (completion as? DurationCompletion)?.seconds,
              (completion as? DistanceCompletion)?.meters,
              (completion as? RepetitionCompletion)?.repetitions,
              segment.notes,
            ),
            (listOf(segment.primaryIntensity) + segment.secondaryIntensities).mapIndexed { position, target ->
              target.toEntity(segment.id, position)
            },
          )
        },
      )
    }
    return PlannedWorkoutRecord(entity, blocks, workout.cues.mapIndexed { index, cue -> WorkoutCueEntity(workout.id, index, cue) })
  }

  fun toDomain(record: PlannedWorkoutRecord): PlannedWorkout {
    if (record.blocks.isEmpty() && record.workout.type != WorkoutType.REST.name) return legacyWorkout(record.workout)
    val entity = record.workout
    val blocks: List<WorkoutBlock> = record.blocks.sortedBy { it.block.blockOrder }.map { blockRecord ->
      val segments = blockRecord.segments.sortedBy { it.segment.segmentOrder }.map { segmentRecord ->
        val segment = segmentRecord.segment
        val targets = segmentRecord.intensityTargets.sortedBy { it.position }.map(WorkoutIntensityTargetEntity::toDomain)
        WorkoutSegment(
          segment.id,
          segment.segmentOrder,
          SegmentCategory.valueOf(segment.category),
          segment.name,
          segment.toCompletion(),
          targets.firstOrNull() ?: NoIntensityTarget,
          targets.drop(1),
          segment.notes,
        )
      }
      if (BlockType.valueOf(blockRecord.block.blockType) == BlockType.REPEAT) {
        RepeatBlock(blockRecord.block.id, blockRecord.block.blockOrder, blockRecord.block.repetitions, segments, blockRecord.block.label)
      } else {
        SingleSegmentBlock(blockRecord.block.id, blockRecord.block.blockOrder, segments.single(), blockRecord.block.label)
      }
    }
    val type = WorkoutType.valueOf(entity.type)
    return PlannedWorkout(
      entity.id,
      LocalDate.ofEpochDay(entity.dateEpochDay),
      type,
      entity.name.ifBlank(type::displayName),
      WorkoutPurpose.valueOf(entity.purpose),
      blocks,
      entity.explanation.ifBlank(type::defaultExplanation),
      record.cues.sortedBy { it.position }.map { it.text },
      Instant.ofEpochMilli(entity.createdAtEpochMillis),
      entity.goalId,
      WorkoutOrigin.valueOf(entity.origin),
      WorkoutAdjustability(
        entity.canChangeType,
        entity.canChangeTotalDuration,
        entity.canChangeSegmentCompletion,
        entity.canChangeRepetitions,
        entity.canChangeIntensity,
        entity.canChangeRecovery,
      ),
    )
  }

  fun legacyWorkout(entity: PlannedWorkoutEntity): PlannedWorkout {
    val type = WorkoutType.valueOf(entity.type)
    return simpleDurationWorkout(
      entity.id, LocalDate.ofEpochDay(entity.dateEpochDay), type, entity.durationMinutes,
      entity.targetRpeMin, entity.targetRpeMax, Instant.ofEpochMilli(entity.createdAtEpochMillis),
      entity.name.ifBlank(type::displayName), WorkoutPurpose.valueOf(entity.purpose),
      entity.explanation.ifBlank(type::defaultExplanation), WorkoutOrigin.valueOf(entity.origin), entity.goalId,
    )
  }
}

private enum class BlockType { SINGLE, REPEAT }
private enum class CompletionType { DURATION, DISTANCE, REPETITIONS, OPEN }
private enum class TargetType { NONE, RPE, PACE, HEART_RATE, HEART_RATE_ZONE }

private fun com.example.openruncoach.domain.workout.SegmentCompletion.typeName() = when (this) {
  is DurationCompletion -> CompletionType.DURATION.name
  is DistanceCompletion -> CompletionType.DISTANCE.name
  is RepetitionCompletion -> CompletionType.REPETITIONS.name
  OpenCompletion -> CompletionType.OPEN.name
}

private fun WorkoutSegmentEntity.toCompletion() = when (CompletionType.valueOf(completionType)) {
  CompletionType.DURATION -> DurationCompletion(requireNotNull(durationSeconds))
  CompletionType.DISTANCE -> DistanceCompletion(requireNotNull(distanceMeters))
  CompletionType.REPETITIONS -> RepetitionCompletion(requireNotNull(completionRepetitions))
  CompletionType.OPEN -> OpenCompletion
}

private fun IntensityTarget.toEntity(segmentId: String, position: Int): WorkoutIntensityTargetEntity = when (this) {
  NoIntensityTarget -> WorkoutIntensityTargetEntity(segmentId, position, TargetType.NONE.name, null, null, null, confidence.name)
  is RpeTarget -> WorkoutIntensityTargetEntity(segmentId, position, TargetType.RPE.name, minimum.toDouble(), maximum.toDouble(), null, confidence.name)
  is PaceTarget -> WorkoutIntensityTargetEntity(segmentId, position, TargetType.PACE.name, minimumSecondsPerKm, maximumSecondsPerKm, null, confidence.name)
  is HeartRateTarget -> WorkoutIntensityTargetEntity(segmentId, position, TargetType.HEART_RATE.name, minimumBpm.toDouble(), maximumBpm.toDouble(), null, confidence.name)
  is HeartRateZoneTarget -> WorkoutIntensityTargetEntity(segmentId, position, TargetType.HEART_RATE_ZONE.name, null, null, zone.name, confidence.name)
}

private fun WorkoutIntensityTargetEntity.toDomain(): IntensityTarget {
  val confidenceValue = GuidanceConfidence.valueOf(confidence)
  return when (TargetType.valueOf(targetType)) {
    TargetType.NONE -> NoIntensityTarget
    TargetType.RPE -> RpeTarget(requireNotNull(minimumValue).toInt(), requireNotNull(maximumValue).toInt(), confidenceValue)
    TargetType.PACE -> PaceTarget(requireNotNull(minimumValue), requireNotNull(maximumValue), confidenceValue)
    TargetType.HEART_RATE -> HeartRateTarget(requireNotNull(minimumValue).toInt(), requireNotNull(maximumValue).toInt(), confidenceValue)
    TargetType.HEART_RATE_ZONE -> HeartRateZoneTarget(HeartRateZone.valueOf(requireNotNull(heartRateZone)), confidenceValue)
  }
}
