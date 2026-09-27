package com.example.openruncoach

import androidx.compose.runtime.staticCompositionLocalOf
import com.example.openruncoach.data.AthletePerformanceProfileRepository
import com.example.openruncoach.data.AthleteStateRepository
import com.example.openruncoach.data.local.OpenRunCoachDatabase
import com.example.openruncoach.domain.profile.AthletePerformanceProfileCalculator
import java.time.Clock

data class OpenRunCoachEnvironment(
  val key: String,
  val database: OpenRunCoachDatabase,
  val clock: Clock,
  val isSimulation: Boolean,
) {
  fun profileRepository() = AthletePerformanceProfileRepository(
    database,
    AthletePerformanceProfileCalculator(clock, clock.zone),
  )

  fun athleteStateRepository() = AthleteStateRepository(database, profileRepository())
}

val LocalOpenRunCoachEnvironment = staticCompositionLocalOf<OpenRunCoachEnvironment> {
  error("OpenRunCoach environment was not provided.")
}
