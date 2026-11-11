package com.example.openruncoach.ui.home

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.openruncoach.theme.OpenRunCoachTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Checks the rendered card threshold, including live source removal, without repositories. */
class TodayQuickFactsTest {
  @get:Rule val compose = createComposeRule()

  @Test fun afterRunTwoEqualColumnsDisappearWhenOnlyOneFactRemains() {
    val fixture = HomePreviewData.state(HomeDayState.AfterRun)
    val sources = fixture.todaySources!!
    val state = mutableStateOf(fixture.copy(todaySources = sources.copy(
      latestMetrics = sources.latestMetrics!!.copy(elevationGainMeters = null))))
    compose.setContent { OpenRunCoachTheme {
      HomeContent(homePresentation(state.value, HomePreviewData.clock(HomeDayState.AfterRun)))
    } }
    assertTwoEqualColumns()
    compose.onNodeWithText("avg heart rate").assertExists()
    compose.onNodeWithText("cadence", substring = false).assertExists()
    compose.runOnIdle { state.value = fixture.copy(todaySources = sources.copy(latestMetrics = null)) }
    compose.onNodeWithTag("today-quick-facts").assertDoesNotExist()
  }

  @Test fun restDayTwoEqualColumnsDisappearWhenOnlyRunCountIsKnown() {
    val fixture = HomePreviewData.state(HomeDayState.RestDay)
    val sources = fixture.todaySources!!
    val state = mutableStateOf(fixture.copy(todaySources = sources.copy(plan = null, profile = null)))
    compose.setContent { OpenRunCoachTheme {
      HomeContent(homePresentation(state.value, HomePreviewData.clock(HomeDayState.RestDay)))
    } }
    assertTwoEqualColumns()
    compose.onNode(hasText("km done") and hasAnyAncestor(hasTestTag("today-quick-facts"))).assertExists()
    compose.runOnIdle { state.value = fixture.copy(todaySources = sources.copy(plan = null, profile = null,
      canonicalRuns = sources.canonicalRuns.map { it.copy(distanceMeters = null) })) }
    compose.onNodeWithTag("today-quick-facts").assertDoesNotExist()
  }

  private fun assertTwoEqualColumns() {
    compose.onNodeWithTag("today-quick-facts").performScrollTo().assertIsDisplayed()
    val columns = compose.onAllNodesWithTag("today-fact").fetchSemanticsNodes()
    assertEquals(2, columns.size)
    assertEquals(columns[0].boundsInRoot.width, columns[1].boundsInRoot.width, 1f)
  }
}
