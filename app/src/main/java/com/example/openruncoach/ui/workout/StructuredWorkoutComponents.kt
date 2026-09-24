package com.example.openruncoach.ui.workout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.example.openruncoach.domain.workout.NoIntensityTarget
import com.example.openruncoach.domain.workout.RepeatBlock
import com.example.openruncoach.domain.workout.StructuredWorkout
import com.example.openruncoach.domain.workout.WorkoutBlock
import com.example.openruncoach.domain.workout.WorkoutDescriptionFormatter
import com.example.openruncoach.domain.workout.WorkoutEstimateCalculator
import com.example.openruncoach.domain.workout.WorkoutSegment
import com.example.openruncoach.domain.workout.displayName
import com.example.openruncoach.ui.common.formatDistance
import com.example.openruncoach.ui.common.formatDuration
import com.example.openruncoach.ui.components.ItemSpacing
import com.example.openruncoach.ui.components.ScreenPadding

@Composable
fun StructuredWorkoutDetail(
  workout: StructuredWorkout,
  heading: String? = null,
  modifier: Modifier = Modifier,
) {
  Column(modifier, verticalArrangement = Arrangement.spacedBy(ItemSpacing)) {
    heading?.let { Text(it, style = MaterialTheme.typography.titleLarge) }
    Text(workout.name, style = MaterialTheme.typography.headlineSmall)
    DetailValue("Type", workout.type.displayName())
    DetailValue("Purpose", WorkoutDescriptionFormatter.purpose(workout.purpose))
    EstimateCard(workout)
    Text("Workout Structure", style = MaterialTheme.typography.titleLarge)
    if (workout.blocks.isEmpty()) Text("No running segments")
    workout.blocks.sortedBy(WorkoutBlock::order).forEach { block -> BlockCard(block) }
    Text("Why this workout?", style = MaterialTheme.typography.titleLarge)
    Text(workout.explanation)
    if (workout.cues.isNotEmpty()) {
      Text("Cues", style = MaterialTheme.typography.titleMedium)
      workout.cues.forEach { Text("• $it") }
    }
  }
}

@Composable
private fun EstimateCard(workout: StructuredWorkout) {
  val estimate = WorkoutEstimateCalculator.calculate(workout)
  Card(Modifier.fillMaxWidth()) {
    Column(Modifier.padding(ScreenPadding), verticalArrangement = Arrangement.spacedBy(ItemSpacing)) {
      Text("Estimated Totals", style = MaterialTheme.typography.titleMedium)
      val duration = estimate.estimatedTotalDurationSeconds?.let(::formatDuration)
        ?: estimate.knownDurationSeconds.takeIf { it > 0 }?.let { "At least ${formatDuration(it)} known" }
        ?: "Unknown"
      val distance = estimate.estimatedTotalDistanceMeters?.let(::formatDistance)
        ?: estimate.knownDistanceMeters.takeIf { it > 0 }?.let { "At least ${formatDistance(it)} known" }
        ?: "Unknown"
      DetailValue("Duration", duration)
      DetailValue("Distance", distance)
      if (estimate.repeatBlockCount > 0) {
        DetailValue("Repeat blocks", estimate.repeatBlockCount.toString())
        DetailValue("Total repeated rounds", estimate.totalRepeatedIterations.toString())
      }
    }
  }
}

@Composable
private fun BlockCard(block: WorkoutBlock) {
  Card(Modifier.fillMaxWidth()) {
    Column(Modifier.padding(ScreenPadding), verticalArrangement = Arrangement.spacedBy(ItemSpacing)) {
      if (block is RepeatBlock) {
        Text("${block.repetitions} rounds", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        block.label?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
      } else {
        block.label?.let { Text(it, style = MaterialTheme.typography.titleMedium) }
      }
      block.segments.sortedBy(WorkoutSegment::order).forEachIndexed { index, segment ->
        if (index > 0) HorizontalDivider()
        SegmentContent(segment)
      }
    }
  }
}

@Composable
private fun SegmentContent(segment: WorkoutSegment) {
  Column(verticalArrangement = Arrangement.spacedBy(ItemSpacing)) {
    Text(segment.name, style = MaterialTheme.typography.titleMedium)
    Text(WorkoutDescriptionFormatter.completion(segment.completion), style = MaterialTheme.typography.headlineSmall)
    Text(segment.category.name.lowercase().replaceFirstChar(Char::titlecase))
    if (segment.primaryIntensity !is NoIntensityTarget) {
      DetailValue("Primary", WorkoutDescriptionFormatter.intensity(segment.primaryIntensity))
    }
    segment.secondaryIntensities.forEach { target ->
      DetailValue("Secondary", WorkoutDescriptionFormatter.intensity(target))
    }
    segment.notes?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
  }
}

@Composable
private fun DetailValue(label: String, value: String) {
  Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
    Text(label)
    Text(value)
  }
}
