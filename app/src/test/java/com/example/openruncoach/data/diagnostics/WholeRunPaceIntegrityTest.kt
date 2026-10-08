package com.example.openruncoach.data.diagnostics

import org.junit.Assert.*
import org.junit.Test

class WholeRunPaceIntegrityTest {
  @Test fun intentionalConsumedBaselineMismatchProducesReadOnlyReview() {
    val check = wholeRunPaceIntegrity("physical-run", 411.0, 405.0)
    assertEquals("REVIEW", check.status)
    assertEquals(1, check.count)
    assertEquals(411.0, check.affectedIds.single()["canonicalActualPaceSecondsPerKm"])
    assertEquals(405.0, check.affectedIds.single()["consumedSplitBaselineSecondsPerKm"])
    // Pure inputs/output: no database or repository write capability is passed to this checker.
    assertEquals(check, wholeRunPaceIntegrity("physical-run", 411.0, 405.0))
  }

  @Test fun canonicalAgreementAndTinyRoundingPassWhileMissingConsumedValueIsReview() {
    assertEquals("PASS", wholeRunPaceIntegrity("run", 411.0, 411.0000001).status)
    assertEquals("PASS", wholeRunPaceIntegrity("run", null, null).status)
    assertEquals("REVIEW", wholeRunPaceIntegrity("run", 411.0, null).status)
    assertEquals("REVIEW", wholeRunPaceIntegrity("run", 411.0, Double.NaN).status)
  }
}
