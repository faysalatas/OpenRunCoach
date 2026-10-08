package com.example.openruncoach.ui.workout

import androidx.annotation.DrawableRes
import com.example.openruncoach.R
import com.example.openruncoach.domain.workout.WorkoutType

/** Shared semantic mapping to bundled artwork for Home and workout/run detail headers. */
@DrawableRes
fun WorkoutType.heroImageResource(): Int? = when (this) {
  WorkoutType.EASY_RUN -> R.drawable.hero_easy
  WorkoutType.RECOVERY_RUN -> R.drawable.hero_recovery
  WorkoutType.LONG_RUN -> R.drawable.hero_long_run
  WorkoutType.TEMPO_RUN -> R.drawable.hero_tempo
  WorkoutType.THRESHOLD -> R.drawable.hero_threshold
  WorkoutType.INTERVALS -> R.drawable.hero_intervals
  WorkoutType.STRIDES -> R.drawable.hero_strides
  WorkoutType.RACE_SPECIFIC -> R.drawable.hero_race_specific
  WorkoutType.RUN_WALK,
  WorkoutType.TECHNIQUE,
  WorkoutType.ASSESSMENT -> R.drawable.hero_easy
  WorkoutType.REST -> null
}
