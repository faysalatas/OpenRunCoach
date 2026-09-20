package com.example.openruncoach.domain

import java.time.Instant

enum class CardioEffort { EASY, APPROPRIATE, HARD, LIMIT }

enum class MuscleFatigue { NONE, MILD, MODERATE, SEVERE }

enum class PainLevel { NONE, MILD, SIGNIFICANT }

enum class CompletionStatus { COMPLETED, PARTIAL, ABORTED }

data class RunFeedbackDraft(
  val runId: String,
  val rpe: Int,
  val cardioEffort: CardioEffort,
  val muscleFatigue: MuscleFatigue,
  val painLevel: PainLevel,
  val completionStatus: CompletionStatus,
  val readiness: Int?,
  val notes: String?,
) {
  fun validationError(): String? = when {
    rpe !in 1..10 -> "RPE must be between 1 and 10."
    readiness != null && readiness !in 1..5 -> "Readiness must be between 1 and 5."
    else -> null
  }
}

data class RunFeedback(
  val id: String,
  val runId: String,
  val rpe: Int,
  val cardioEffort: CardioEffort,
  val muscleFatigue: MuscleFatigue,
  val painLevel: PainLevel,
  val completionStatus: CompletionStatus,
  val readiness: Int?,
  val notes: String?,
  val createdAt: Instant,
  val updatedAt: Instant,
)
