package com.example.openruncoach.data

import com.example.openruncoach.domain.workout.WorkoutComparisonBasis
import com.example.openruncoach.domain.workout.WorkoutExecutionResult
import com.example.openruncoach.domain.workout.WorkoutExecutionStatus
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class WorkoutExecutionPersistenceMapperTest {
  @Test fun resultRoundTripPreservesActualAndIntendedMetricsSeparately() {
    val result = WorkoutExecutionResult(
      plannedWorkoutId = "planned",
      actualRunId = "actual",
      status = WorkoutExecutionStatus.COMPLETED_WITH_MINOR_DEVIATION,
      comparisonBasis = WorkoutComparisonBasis.ADJUSTED_PRESCRIPTION,
      intendedDistanceMeters = null,
      intendedDurationSeconds = 1_800,
      actualDistanceMeters = 3_780.0,
      actualDurationSeconds = 1_720,
      qualityWorkCompletionFraction = null,
      explanation = "The easy aerobic stimulus was completed with a small prescription deviation.",
      evaluatedAt = Instant.parse("2026-09-26T08:30:00Z"),
    )

    assertEquals(result, result.toEntity().toDomain())
  }
}
