package com.example.openruncoach.data

import com.example.openruncoach.data.local.OpenRunCoachDatabase
import com.example.openruncoach.data.local.PlannedWorkoutRecord
import com.example.openruncoach.data.local.WorkoutAdjustmentEntity
import com.example.openruncoach.domain.MuscleFatigue
import com.example.openruncoach.domain.CardioEffort
import com.example.openruncoach.domain.RunRelevance
import com.example.openruncoach.domain.RunSummary
import com.example.openruncoach.domain.evidence.TrainingEvidencePolicy
import com.example.openruncoach.domain.evidence.TrainingEvidencePurpose
import com.example.openruncoach.domain.adaptation.AdaptedPrescription
import com.example.openruncoach.domain.adaptation.AdaptationObservation
import com.example.openruncoach.domain.adaptation.TrainingAdaptationAction
import com.example.openruncoach.domain.adaptation.TrainingAdaptationPlanner
import com.example.openruncoach.domain.athlete.AthleteState
import com.example.openruncoach.domain.goal.GoalStrategyResolver
import com.example.openruncoach.domain.workout.ReadinessStatus
import com.example.openruncoach.domain.workout.StructuredWorkout
import com.example.openruncoach.domain.workout.WorkoutResponse
import com.google.gson.Gson
import java.time.Clock
import java.time.LocalDate

/** Invoked only inside the post-run transaction, after evidence and goal reconciliation. */
internal class TrainingAdaptationRepository(
  private val database: OpenRunCoachDatabase,
  private val clock: Clock,
) {
  suspend fun reconcile(state: AthleteState, runs: List<RunSummary>, responses: List<WorkoutResponse>) {
    val goal = database.trainingGoalDao().getActiveGoal()?.toDomain() ?: return
    val dao = database.coachingWorkflowDao()
    val today = LocalDate.now(clock)
    val feedbackById = RunFeedbackRepository(database, clock).getAllResolutions()
      .associate { it.physicalRunId to it.authoritativeFeedback }
    val responseById = responses.associateBy { it.physicalRunId }
    val observations = runs.filter { it.relevance == RunRelevance.ACTIVE && !it.startTime.isAfter(clock.instant()) }.mapNotNull { run ->
      val checkout = feedbackById[run.physicalRunId]
      val response = responseById[run.physicalRunId]
      val eligibility = TrainingEvidencePolicy.evaluate(
        run, feedback = checkout, associatedWorkoutId = response?.plannedWorkoutId, observedCompletion = checkout?.completionStatus)
      if (!eligibility.getValue(TrainingEvidencePurpose.SAFETY_RECOVERY).eligible) return@mapNotNull null
      AdaptationObservation(run.physicalRunId, run.startTime.atZone(clock.zone).toLocalDate(),
        checkout?.painLevel,
        checkout?.let { it.muscleFatigue in setOf(MuscleFatigue.MODERATE, MuscleFatigue.SEVERE) ||
          it.cardioEffort in setOf(CardioEffort.HARD, CardioEffort.LIMIT) || it.rpe >= 9 ||
          it.readiness?.let { readiness -> readiness <= 2 } == true } ?: false,
        response, eligibility.getValue(TrainingEvidencePurpose.CAPABILITY).eligible,
        checkout?.let { it.muscleFatigue == MuscleFatigue.SEVERE || it.cardioEffort == CardioEffort.LIMIT || it.readiness == 1 } ?: false)
    }
    val records = dao.getAllUnassociatedActivePlanWorkouts().filter {
      it.workout.dateEpochDay >= today.toEpochDay() && it.workout.lifecycleState in setOf("PLANNED", "CHECK_IN_REQUIRED")
    }.filter { record ->
      // Do not rewrite a potentially performed prescription while its checkout/assignment is
      // unresolved. This freezes evidence context; it does not infer or create an association.
      runs.none { run ->
        run.startTime.atZone(clock.zone).toLocalDate().toEpochDay() == record.workout.dateEpochDay &&
          run.startTime.toEpochMilli() >= record.workout.createdAtEpochMillis
      }
    }.filter { record ->
      val saved = dao.getAdjustment(record.workout.id)
      saved == null || AdaptationAdjustmentCodec.owns(saved)
    }
    val planned = records.map(StructuredWorkoutPersistenceMapper::toDomain)
    val strategy = GoalStrategyResolver().resolve(goal, state)
    TrainingAdaptationPlanner().adapt(planned, strategy, state, observations, today).forEach { adapted ->
      val previous = dao.getAdjustment(adapted.original.id)
      val entity = AdaptationAdjustmentCodec.encode(adapted, clock.millis(), today)
      // Preserve timestamp and avoid invalidation/observer loops on identical input.
      if (previous == null || entity.copy(generatedAtEpochMillis = previous.generatedAtEpochMillis) != previous) {
        dao.upsertAdjustment(entity)
      }
    }
  }
}

/**
 * Versioned structured payload in the existing adjustment TEXT field. Scalar pre-run rows remain
 * readable and retain ownership. No original workout row, ID, or schema is replaced by adaptation.
 */
internal object AdaptationAdjustmentCodec {
  const val KEEP_PREFIX = "ADAPTATION_KEEP_V1:"
  private const val CHANGED_PREFIX = "ADAPTATION_CHANGED_V1:"
  private val gson = Gson()

  fun owns(entity: WorkoutAdjustmentEntity): Boolean =
    entity.adjustments.startsWith(KEEP_PREFIX) || entity.adjustments.startsWith(CHANGED_PREFIX)

  fun effective(entity: WorkoutAdjustmentEntity): StructuredWorkout? {
    if (!owns(entity)) return null
    val saved = gson.fromJson(entity.adjustments.substringAfter(':'), StoredAdaptation::class.java)
    require(saved.prescription.blocks.isNotEmpty() || saved.prescription.workout.type == "REST") {
      "A structured adjustment must retain its running blocks."
    }
    return StructuredWorkoutPersistenceMapper.toDomain(saved.prescription).also {
      require(it.id == entity.plannedWorkoutId) { "Adjustment identity differs from its original workout." }
    }
  }

  fun encode(adapted: AdaptedPrescription, timestamp: Long, evaluatedOn: LocalDate): WorkoutAdjustmentEntity {
    val effective = adapted.effective
    val prefix = if (adapted.decision.action == TrainingAdaptationAction.KEEP) KEEP_PREFIX else CHANGED_PREFIX
    return WorkoutAdjustmentEntity(effective.id, effective.type.name, effective.durationMinutes,
      effective.targetRpeMin, effective.targetRpeMax,
      when (adapted.decision.action) {
        TrainingAdaptationAction.REST -> ReadinessStatus.RED
        TrainingAdaptationAction.REDUCE -> ReadinessStatus.YELLOW
        else -> ReadinessStatus.GREEN
      }.name,
      adapted.decision.reasons.joinToString("\u001F"),
      prefix + gson.toJson(StoredAdaptation(
        StructuredWorkoutPersistenceMapper.toRecord(effective, "PLANNED"), adapted.decision, evaluatedOn.toEpochDay())), timestamp)
  }

  private data class StoredAdaptation(
    val prescription: PlannedWorkoutRecord,
    val decision: com.example.openruncoach.domain.adaptation.TrainingAdaptationDecision,
    val evaluatedOnEpochDay: Long,
  )
}

internal fun WorkoutAdjustmentEntity.adjustmentReasons(): List<String> = reasons.split('\u001F').filter(String::isNotBlank)

/** Shared read-only projection used by Home/Plan and Workout Detail. */
internal fun StructuredWorkout.withEffectiveAdjustment(adjustment: WorkoutAdjustmentEntity?): StructuredWorkout =
  adjustment?.let { AdaptationAdjustmentCodec.effective(it) }?.let { effective ->
    effective.copy(explanation = effective.explanation + "\n\n" + adjustment.adjustmentReasons().joinToString(" "))
  } ?: this
