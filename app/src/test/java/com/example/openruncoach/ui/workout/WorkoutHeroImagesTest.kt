package com.example.openruncoach.ui.workout

import com.example.openruncoach.R
import com.example.openruncoach.domain.workout.WorkoutType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WorkoutHeroImagesTest {
  @Test fun knownWorkoutTypesReuseTheirExistingBundledHeroes() {
    val expected = mapOf(
      WorkoutType.EASY_RUN to R.drawable.hero_easy,
      WorkoutType.RECOVERY_RUN to R.drawable.hero_recovery,
      WorkoutType.LONG_RUN to R.drawable.hero_long_run,
      WorkoutType.TEMPO_RUN to R.drawable.hero_tempo,
      WorkoutType.THRESHOLD to R.drawable.hero_threshold,
      WorkoutType.INTERVALS to R.drawable.hero_intervals,
      WorkoutType.STRIDES to R.drawable.hero_strides,
      WorkoutType.RACE_SPECIFIC to R.drawable.hero_race_specific,
    )
    expected.forEach { (type, resource) -> assertEquals(resource, type.heroImageResource()) }
  }

  @Test fun missingTypeHasNoHeroAndRestDoesNotInventOne() {
    val missingType: WorkoutType? = null
    assertNull(missingType?.heroImageResource())
    assertNull(WorkoutType.REST.heroImageResource())
  }
}
