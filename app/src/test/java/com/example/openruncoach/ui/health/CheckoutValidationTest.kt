package com.example.openruncoach.ui.health

import com.example.openruncoach.domain.*
import org.junit.Assert.*
import org.junit.Test

/** Exercises the existing validator used by RunDetailViewModel.saveFeedback, without replacing it. */
class CheckoutValidationTest {
  private fun draft(rpe:Int=4, readiness:Int?=null) = RunFeedbackDraft("fixture",rpe,CardioEffort.APPROPRIATE,
    MuscleFatigue.NONE,PainLevel.NONE,CompletionStatus.COMPLETED,readiness,null)
  @Test fun allRpeValuesOneThroughTenAreValid() { for(rpe in 1..10) assertNull(draft(rpe).validationError()) }
  @Test fun rpeOutsideTheInclusiveRangeIsRejected() { for(rpe in listOf(Int.MIN_VALUE,0,11,Int.MAX_VALUE)) assertEquals("RPE must be between 1 and 10.",draft(rpe).validationError()) }
  @Test fun allReadinessValuesOneThroughFiveAreValid() { for(readiness in 1..5) assertNull(draft(readiness=readiness).validationError()) }
  @Test fun readinessOutsideTheInclusiveRangeIsRejected() { for(readiness in listOf(Int.MIN_VALUE,0,6,Int.MAX_VALUE)) assertEquals("Readiness must be between 1 and 5.",draft(readiness=readiness).validationError()) }
  @Test fun readinessRemainsOptional() { assertNull(draft(readiness=null).validationError()) }
}
