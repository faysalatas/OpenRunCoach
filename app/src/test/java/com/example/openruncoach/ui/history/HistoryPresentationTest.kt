package com.example.openruncoach.ui.history

import com.example.openruncoach.domain.RunSummary
import com.example.openruncoach.domain.RunRelevance
import java.time.*
import java.util.Locale
import org.junit.Assert.*
import org.junit.Test

class HistoryPresentationTest {
  private val monday = LocalDate.of(2026, 11, 2)
  private fun item(id: String, instant: String, distance: Double? = 1_000.0) = HistoryRunItem(
    RunSummary(id, Instant.parse(instant), Instant.parse(instant).plusSeconds(600), 600, distance, null, null, 600.0), "", "Run")
  @Test fun mondayThroughSundayAreOneGroupRegardlessOfLocaleFirstDay() {
    val items = listOf(item("mon","2026-11-02T10:00:00Z"), item("sun","2026-11-08T10:00:00Z"), item("prev","2026-11-01T10:00:00Z"))
    for(locale in listOf(Locale.US, Locale.GERMANY, Locale.forLanguageTag("ar-SA"))) {
      val groups = groupRunsByCalendarWeek(items, monday.plusDays(6), ZoneOffset.UTC, locale)
      assertEquals(listOf(monday, monday.minusWeeks(1)), groups.map { it.weekStart })
      assertEquals(listOf("sun", "mon"), groups[0].runs.map { it.run.healthConnectRecordId })
      assertEquals("THIS WEEK", groups[0].title)
      assertEquals("LAST WEEK", groups[1].title)
    }
  }
  @Test fun weekCrossesMonthAndYearWithoutSplittingRecords() {
    val groups = groupRunsByCalendarWeek(listOf(item("dec","2026-12-31T10:00:00Z"), item("jan","2027-01-03T10:00:00Z")),
      LocalDate.of(2027,1,4), ZoneOffset.UTC)
    assertEquals(1,groups.size)
    assertEquals(LocalDate.of(2026,12,28),groups.single().weekStart)
    assertEquals("LAST WEEK",groups.single().title)
  }
  @Test fun groupingUsesLocalDateRatherThanUtcDateAtMidnightAndDstBoundary() {
    val zone = ZoneId.of("Europe/Berlin")
    val sunday = item("sunday","2026-10-25T22:30:00Z") // Sunday 23:30 after DST change.
    val monday = item("monday","2026-10-25T23:30:00Z") // Monday 00:30.
    val groups = groupRunsByCalendarWeek(listOf(sunday,monday), LocalDate.of(2026,10,26),zone)
    assertEquals(listOf(LocalDate.of(2026,10,26),LocalDate.of(2026,10,19)),groups.map { it.weekStart })
    assertSame(monday,groups.first().runs.single())
  }
  @Test fun missingDistanceHidesTotalRatherThanSummingOnlyKnownRuns() {
    val groups = groupRunsByCalendarWeek(listOf(item("known","2026-11-02T10:00:00Z"),item("unknown","2026-11-04T10:00:00Z",null)),monday,ZoneOffset.UTC)
    assertNull(groups.single().distanceMeters)
    assertEquals(2,groups.single().runs.size)
  }
  @Test fun groupingPreservesIdentityMetadataAndIgnoredSelectionWithoutWrites() {
    val ignored = item("ignored","2026-11-02T10:00:00Z").let { it.copy(run = it.run.copy(relevance = RunRelevance.IGNORED), workoutTypeLabel = "Recorded title") }
    val original = listOf(ignored)
    val grouped = groupRunsByCalendarWeek(original,monday,ZoneOffset.UTC)
    assertSame(ignored,grouped.single().runs.single())
    assertEquals("Recorded title",grouped.single().runs.single().workoutTypeLabel)
    assertEquals(RunRelevance.IGNORED,original.single().run.relevance)
  }
  @Test fun emptyHistoryHasNoPhantomWeekAndOlderLabelsAreLocalized() {
    assertTrue(groupRunsByCalendarWeek(emptyList(),monday,ZoneOffset.UTC).isEmpty())
    val old = listOf(item("old","2026-09-01T10:00:00Z"))
    val us = groupRunsByCalendarWeek(old,monday,ZoneOffset.UTC,Locale.US).single()
    val de = groupRunsByCalendarWeek(old,monday,ZoneOffset.UTC,Locale.GERMANY).single()
    assertEquals(us.weekStart,de.weekStart)
    assertNotEquals(us.title,de.title)
  }
  @Test fun unknownCheckoutTypeDoesNotBecomeAnEasyPaceObservation() {
    val state = HistoryPreviewData.state().copy(completedWorkoutTypesByRunId = emptyMap(),profile = null)
    val result = historyPresentation(state,HistoryPreviewData.clock)
    assertTrue(result.pacePoints.isEmpty())
    assertNull(result.easyHeartRate)
    assertNull(result.longestDistance)
  }
  @Test fun ignoredModeDoesNotShowActiveProfileOrTrainingTrend() {
    val result = historyPresentation(HistoryPreviewData.state().copy(showIgnored = true),HistoryPreviewData.clock)
    assertTrue(result.pacePoints.isEmpty())
    assertNull(result.easyHeartRate)
    assertNull(result.longestDistance)
    assertTrue(result.weeks.isNotEmpty())
  }
  @Test fun heroTotalsMatchTheCurrentWeekAndPreserveWeightedActualPace() {
    val result = historyPresentation(HistoryPreviewData.state(),HistoryPreviewData.clock,Locale.US)
    assertEquals("3",result.metrics.first { it.label=="runs" }.value)
    assertEquals("13.0",result.metrics.first { it.label=="km" }.value)
    assertEquals("6:30",result.metrics.first { it.label=="avg /km" }.value)
    assertEquals(4,result.weeks.size)
  }
}
