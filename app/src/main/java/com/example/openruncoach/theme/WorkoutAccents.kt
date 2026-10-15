package com.example.openruncoach.theme

import androidx.compose.ui.graphics.Color
import com.example.openruncoach.domain.workout.WorkoutType

/** Shared low-saturation workout scanning accents, never performance judgments. */
object WorkoutAccents {
  private val Long = Color(0xFF8CA78C)
  private val Tempo = Color(0xFF9F947D)
  private val Threshold = Color(0xFFAA8992)
  private val Intervals = Color(0xFF958BA7)
  private val Strides = Color(0xFF8D9DAE)
  private val Race = Color(0xFFA59B87)

  fun forType(type: WorkoutType?): Color = when (type) {
    WorkoutType.EASY_RUN, WorkoutType.RECOVERY_RUN -> OpenRunColors.Accent.copy(alpha = 0.7f)
    WorkoutType.LONG_RUN -> Long
    WorkoutType.TEMPO_RUN -> Tempo
    WorkoutType.THRESHOLD -> Threshold
    WorkoutType.INTERVALS -> Intervals
    WorkoutType.STRIDES -> Strides
    WorkoutType.RACE_SPECIFIC -> Race
    else -> OpenRunColors.TextMuted.copy(alpha = 0.6f)
  }
}
