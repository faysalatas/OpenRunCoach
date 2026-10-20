package com.example.openruncoach.data

import com.example.openruncoach.data.local.OpenRunCoachDatabase
import com.example.openruncoach.data.local.TrainingAvailabilityEntity
import com.example.openruncoach.domain.plan.TrainingAvailability
import java.time.DayOfWeek
import kotlinx.coroutines.flow.map

class TrainingAvailabilityRepository(database: OpenRunCoachDatabase) {
  private val dao = database.trainingAvailabilityDao()
  val availability = dao.observe().map { it?.toDomain() }
  suspend fun get(): TrainingAvailability? = dao.get()?.toDomain()

  /** This write never touches plans, workouts, runs, readiness or reconciliation. */
  suspend fun save(value: TrainingAvailability?) {
    if (value == null) dao.reset()
    else dao.save(TrainingAvailabilityEntity(
      availableDaysMask = value.availableDays.mask(), preferredDaysMask = value.preferredDays.mask(),
    ))
  }
}

internal fun TrainingAvailabilityEntity.toDomain() = TrainingAvailability(
  DayOfWeek.entries.filter { availableDaysMask and (1 shl (it.value - 1)) != 0 }.toSet(),
  DayOfWeek.entries.filter { preferredDaysMask and (1 shl (it.value - 1)) != 0 }.toSet(),
)

private fun Set<DayOfWeek>.mask(): Int = fold(0) { mask, day -> mask or (1 shl (day.value - 1)) }
