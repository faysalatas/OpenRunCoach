package com.example.openruncoach.ui.plan

import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.compose.foundation.ScrollState
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.test.platform.app.InstrumentationRegistry
import com.example.openruncoach.theme.OpenRunCoachTheme
import com.example.openruncoach.theme.LightOpenRunPalette
import com.example.openruncoach.theme.DarkOpenRunPalette
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class PlanPreviewTest(private val mode: String, private val dark: Boolean, private val scale: Float) {
  @get:Rule val compose = createComposeRule()
  @Test fun renderPlanAndExistingActions() {
    val scroll = ScrollState(0)
    val clicks = mutableListOf<String>()
    val initial = PlanPreviewData.state().copy(showCompletionScheduler = mode == "scheduler", showPlanHistory = mode == "history")
    compose.setContent {
      var state by remember { mutableStateOf(initial) }
      val config = Configuration(LocalConfiguration.current).apply {
        uiMode = if (dark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
        fontScale = scale
      }
      val density = LocalDensity.current.density
      CompositionLocalProvider(LocalConfiguration provides config, LocalDensity provides Density(density, scale)) {
        OpenRunCoachTheme { PlanPreview(state, scroll, PlanActions(
          selectWeek = { clicks += "week:$it"; state = state.copy(selectedWeekIndex = it) },
          history = { clicks += "history" }, previousPlan = { clicks += it },
          selectDate = { clicks += "date:$it" }, postpone = { clicks += "postpone" },
          workout = { clicks += it }, schedule = { clicks += "schedule" }, generate = { clicks += "generate" })) }
      }
    }
    compose.waitForIdle()
    if (mode != "plan") {
      compose.onNode(isDialog()).performTouchInput { swipeUp(durationMillis = 600) }
      compose.waitForIdle()
    }
    if (mode == "plan") {
      compose.onNodeWithText("Previous plans ›").performScrollTo().assertIsDisplayed()
      val nodes = compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Text), useUnmergedTree = true).fetchSemanticsNodes()
      compose.runOnIdle {
        nodes.forEach { node ->
          val layouts = mutableListOf<TextLayoutResult>()
          node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(layouts)
          layouts.forEach { result ->
            if (result.layoutInput.text.text == initial.plan!!.weeks[initial.selectedWeekIndex].explanation)
              assertEquals(if (dark) DarkOpenRunPalette.ink else LightOpenRunPalette.ink, result.layoutInput.style.color)
            assertTrue("Clipped at $scale: ${result.layoutInput.text}",
            !result.multiParagraph.didExceedMaxLines && (0 until result.lineCount).all { line ->
              result.getLineRight(line) <= result.size.width + 1f && result.getLineBottom(line) <= result.size.height + 1f }) }
        }
        scroll.dispatchRawDelta(-scroll.value.toFloat())
      }
    }
    val directory = InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null)!!
    var page = 1
    while (true) {
      compose.waitForIdle()
      val bitmap = (if (mode == "plan") compose.onRoot() else compose.onNode(isDialog())).captureToImage().asAndroidBitmap()
      val file = "plan-$mode-${if (dark) "dark" else "light"}-${(scale*100).toInt()}-page-$page.png"
      File(directory, file).outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
      if (mode != "plan" || scale != 1f || scroll.value == scroll.maxValue) break
      val height = compose.onNodeWithTag("plan-scroll").fetchSemanticsNode().boundsInRoot.height
      compose.runOnIdle { scroll.dispatchRawDelta(height) }
      check(++page < 8)
    }
    when(mode) {
      "plan" -> {
        compose.onNodeWithTag("plan-week-selector").performScrollTo().performScrollToIndex(2)
        compose.onNodeWithText("Week 3").performClick().assertIsSelected()
        compose.onNodeWithText("Previous plans ›").performScrollTo().performClick()
        compose.runOnIdle { assertTrue(clicks.containsAll(listOf("week:2", "history"))) }
      }
      "scheduler" -> {
        compose.onNodeWithText("Not yet").performScrollTo().performClick()
        compose.runOnIdle { assertTrue(clicks.contains("postpone")) }
      }
      "history" -> {
        compose.onNode(hasClickAction() and hasText("Complete 10K", substring = true)).performClick()
        compose.runOnIdle { assertTrue(clicks.contains("previous-plan-fixture")) }
      }
    }
  }
  companion object {
    @JvmStatic @Parameterized.Parameters(name = "{0}-{1}-{2}") fun cases(): List<Array<Any>> =
      listOf(false,true).flatMap { dark -> listOf(1f,1.3f,2f).map { arrayOf<Any>("plan",dark,it) } +
        listOf(arrayOf<Any>("scheduler",dark,1f), arrayOf<Any>("history",dark,1f)) }
  }
}
