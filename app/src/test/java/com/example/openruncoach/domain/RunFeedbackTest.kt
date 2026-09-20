package com.example.openruncoach.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RunFeedbackTest {
  @Test
  fun rpeMustBeBetweenOneAndTen() {
    assertEquals("RPE must be between 1 and 10.", draft(rpe = 0).validationError())
    assertNull(draft(rpe = 1).validationError())
    assertNull(draft(rpe = 10).validationError())
    assertEquals("RPE must be between 1 and 10.", draft(rpe = 11).validationError())
  }

  @Test
  fun readinessIsOptionalAndMustBeBetweenOneAndFiveWhenPresent() {
    assertNull(draft(readiness = null).validationError())
    assertEquals("Readiness must be between 1 and 5.", draft(readiness = 0).validationError())
    assertNull(draft(readiness = 1).validationError())
    assertNull(draft(readiness = 5).validationError())
    assertEquals("Readiness must be between 1 and 5.", draft(readiness = 6).validationError())
  }

  private fun draft(rpe: Int = 5, readiness: Int? = null) = RunFeedbackDraft(
    runId = "run-1",
    rpe = rpe,
    cardioEffort = CardioEffort.APPROPRIATE,
    muscleFatigue = MuscleFatigue.MILD,
    painLevel = PainLevel.NONE,
    completionStatus = CompletionStatus.COMPLETED,
    readiness = readiness,
    notes = null,
  )
}
