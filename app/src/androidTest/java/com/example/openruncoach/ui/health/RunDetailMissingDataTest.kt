package com.example.openruncoach.ui.health

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.openruncoach.theme.OpenRunCoachTheme
import org.junit.Rule
import org.junit.Test

class RunDetailMissingDataTest {
  @get:Rule val compose=createComposeRule()
  @Test fun absentMetricsHideCardsWithoutGuessingValues() {
    val state=RunDetailPreviewData.state().let { it.copy(metrics=null,charts=null,routeData=null,
      feedback=null,workoutResponse=null,coachingDecision=null,run=it.run!!.copy(averageHeartRate=null,maxHeartRate=null)) }
    compose.setContent { OpenRunCoachTheme { RunDetailContent(state,RunDetailPreviewData.clock) } }
    listOf("Heart rate","Recorded heart rate","Cadence","Elevation","Splits","Fastest 30 sec","As planned").forEach { compose.onNodeWithText(it).assertDoesNotExist() }
    compose.onNodeWithText("Complete post-run checkout ›").assertExists()
    compose.onNodeWithText("Ignore run").performScrollTo().assertIsDisplayed()
  }
}
