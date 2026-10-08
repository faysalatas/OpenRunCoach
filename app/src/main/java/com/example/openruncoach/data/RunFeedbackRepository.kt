package com.example.openruncoach.data

import androidx.room.withTransaction
import com.example.openruncoach.data.local.OpenRunCoachDatabase
import com.example.openruncoach.data.local.RunFeedbackEntity
import com.example.openruncoach.data.local.RunEntity
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
import kotlinx.coroutines.flow.combine

data class PhysicalRunFeedbackResolution(
  val physicalRunId: String,
  val authoritativeFeedback: RunFeedback?,
  val authoritativeStorageRunId: String?,
  val storageOwnerRunId: String,
  val legacyConflict: Boolean,
  val originalRecords: List<RunFeedbackEntity>,
)

/** Select whole answers, never merge fields; canonical source is deliberately absent from ordering. */
internal fun resolvePhysicalFeedback(
  physicalRunId: String,
  members: List<RunEntity>,
  feedback: List<RunFeedbackEntity>,
): PhysicalRunFeedbackResolution {
  val memberIds = members.mapTo(mutableSetOf()) { it.healthConnectRecordId }
  val records = feedback.filter { it.runId in memberIds }
    .sortedWith(compareBy<RunFeedbackEntity> { if (it.runId == physicalRunId) 0 else 1 }
      .thenBy { it.createdAtEpochMillis }.thenBy { it.id })
  val authoritative = records.firstOrNull()
  val owner = physicalRunId.takeIf { it in memberIds }
    ?: authoritative?.runId ?: memberIds.minOrNull() ?: error("Physical run has no provider records")
  return PhysicalRunFeedbackResolution(physicalRunId,
    authoritative?.toDomain()?.copy(runId = physicalRunId), authoritative?.runId,
    owner, records.size > 1, records)
}

class RunFeedbackRepository(
  private val database: OpenRunCoachDatabase,
  private val clock: Clock = Clock.systemUTC(),
  private val athleteProfileRepository: AthletePerformanceProfileRepository? = null,
  private val idProvider: () -> String = { UUID.randomUUID().toString() },
) {
  private val dao = database.runFeedbackDao()

  suspend fun getFeedbackForRun(runId: String): RunFeedback? =
    getResolution(runId)?.authoritativeFeedback

  suspend fun getResolution(runId: String): PhysicalRunFeedbackResolution? = database.withTransaction {
    val run = database.runDao().getRun(runId) ?: database.runDao().getCanonicalRunByPhysicalId(runId)
      ?: return@withTransaction null
    resolvePhysicalFeedback(run.physicalRunId, database.runDao().getProviderRecords(run.physicalRunId), dao.getAllFeedback())
  }

  suspend fun getAllResolutions(): List<PhysicalRunFeedbackResolution> = database.withTransaction {
    val feedback = dao.getAllFeedback()
    database.runDao().getAllRuns().groupBy { it.physicalRunId }.toSortedMap().map { (id, members) ->
      resolvePhysicalFeedback(id, members, feedback)
    }
  }

  suspend fun getAllFeedback(): List<RunFeedback> = getAllResolutions().mapNotNull { it.authoritativeFeedback }

  fun observeFeedbackForRun(runId: String): Flow<RunFeedback?> =
    combine(database.runDao().observeAllRuns(), dao.observeAllFeedback()) { runs, feedback ->
      val physicalId = runs.firstOrNull { it.healthConnectRecordId == runId }?.physicalRunId ?: runId
      val members = runs.filter { it.physicalRunId == physicalId }
      if (members.isEmpty()) null else resolvePhysicalFeedback(physicalId, members, feedback).authoritativeFeedback
    }

  suspend fun saveFeedback(draft: RunFeedbackDraft): RunFeedback = persist(draft, requireExisting = false)

  suspend fun updateFeedback(draft: RunFeedbackDraft): RunFeedback = persist(draft, requireExisting = true)

  private suspend fun persist(draft: RunFeedbackDraft, requireExisting: Boolean): RunFeedback {
    require(draft.runId.isNotBlank()) { "Run ID is required." }
    draft.validationError()?.let { throw IllegalArgumentException(it) }
    val saved = database.withTransaction {
      val resolution = requireNotNull(getResolution(draft.runId)) { "Run does not exist." }
      if (requireExisting) requireNotNull(resolution.authoritativeFeedback) { "Feedback does not exist for this run." }
      // A sole legacy row can safely move. With conflicts preserve every legacy row and create
      // the explicit user answer at the anchor. Subsequent edits update that authoritative row.
      val existing = resolution.originalRecords.firstOrNull { it.runId == resolution.storageOwnerRunId }
        ?: resolution.originalRecords.singleOrNull()
      val now = clock.millis()
      val entity =
        RunFeedbackEntity(
          id = existing?.id ?: idProvider(),
          runId = resolution.storageOwnerRunId,
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
      entity.toDomain().copy(runId = resolution.physicalRunId)
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
