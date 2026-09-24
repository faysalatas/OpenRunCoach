package com.example.openruncoach.data

import androidx.room.withTransaction
import com.example.openruncoach.data.local.OpenRunCoachDatabase
import com.example.openruncoach.data.local.RunFeedbackEntity
import com.example.openruncoach.domain.CardioEffort
import com.example.openruncoach.domain.CompletionStatus
import com.example.openruncoach.domain.MuscleFatigue
import com.example.openruncoach.domain.PainLevel
import com.example.openruncoach.domain.RunFeedback
import com.example.openruncoach.domain.RunFeedbackDraft
import java.time.Clock
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RunFeedbackRepository(
  private val database: OpenRunCoachDatabase,
  private val clock: Clock = Clock.systemUTC(),
  private val athleteProfileRepository: AthletePerformanceProfileRepository? = null,
) {
  private val dao = database.runFeedbackDao()

  suspend fun getFeedbackForRun(runId: String): RunFeedback? =
    dao.getFeedbackForRun(runId)?.toDomain()

  fun observeFeedbackForRun(runId: String): Flow<RunFeedback?> =
    dao.observeFeedbackForRun(runId).map { it?.toDomain() }

  suspend fun saveFeedback(draft: RunFeedbackDraft): RunFeedback = persist(draft, requireExisting = false)

  suspend fun updateFeedback(draft: RunFeedbackDraft): RunFeedback = persist(draft, requireExisting = true)

  private suspend fun persist(draft: RunFeedbackDraft, requireExisting: Boolean): RunFeedback {
    require(draft.runId.isNotBlank()) { "Run ID is required." }
    draft.validationError()?.let { throw IllegalArgumentException(it) }
    val saved = database.withTransaction {
      val existing = dao.getFeedbackForRun(draft.runId)
      if (requireExisting) requireNotNull(existing) { "Feedback does not exist for this run." }
      val now = clock.millis()
      val entity =
        RunFeedbackEntity(
          id = existing?.id ?: UUID.randomUUID().toString(),
          runId = draft.runId,
          rpe = draft.rpe,
          cardioEffort = draft.cardioEffort.name,
          muscleFatigue = draft.muscleFatigue.name,
          painLevel = draft.painLevel.name,
          completionStatus = draft.completionStatus.name,
          readiness = draft.readiness,
          notes = draft.notes?.trim()?.takeIf(String::isNotEmpty),
          createdAtEpochMillis = existing?.createdAtEpochMillis ?: now,
          updatedAtEpochMillis = now,
        )
      if (existing == null) dao.insertFeedback(entity) else dao.updateFeedback(entity)
      entity.toDomain()
    }
    athleteProfileRepository?.recalculate()
    return saved
  }
}

internal fun RunFeedbackEntity.toDomain() =
  RunFeedback(
    id = id,
    runId = runId,
    rpe = rpe,
    cardioEffort = CardioEffort.valueOf(cardioEffort),
    muscleFatigue = MuscleFatigue.valueOf(muscleFatigue),
    painLevel = PainLevel.valueOf(painLevel),
    completionStatus = CompletionStatus.valueOf(completionStatus),
    readiness = readiness,
    notes = notes,
    createdAt = Instant.ofEpochMilli(createdAtEpochMillis),
    updatedAt = Instant.ofEpochMilli(updatedAtEpochMillis),
  )
