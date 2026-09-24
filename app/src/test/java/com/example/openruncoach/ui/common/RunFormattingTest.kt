package com.example.openruncoach.ui.common

import java.time.LocalDate
import java.util.Locale
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RunFormattingTest {
  @Test
  fun userDateUsesTheRequestedLocaleAndNotIsoFormat() {
    val date = LocalDate.of(2026, 12, 24)
    val german = formatUserDate(date, Locale.GERMANY)
    assertFalse(german == "2026-12-24")
    assertTrue(german.contains("2026"))
    assertEquals(german, formatUserDate(date, Locale.GERMANY))
  }
}
