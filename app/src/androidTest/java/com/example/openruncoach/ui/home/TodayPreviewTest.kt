package com.example.openruncoach.ui.home

import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.compose.foundation.ScrollState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.test.platform.app.InstrumentationRegistry
import com.example.openruncoach.theme.OpenRunCoachTheme
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/** Debug fixtures only: no production activity, database, health records or network. */
@RunWith(Parameterized::class)
class TodayPreviewTest(private val day: String, private val dark: Boolean, private val scale: Float) {
  @get:Rule val compose = createComposeRule()

  @Test fun renderScrollAccessibilityAndEntryPoints() {
    val kind = HomeDayState.valueOf(day)
    val scroll = ScrollState(0)
    val clicks = mutableListOf<String>()
    val actions = HomeActions(settings = { clicks += "settings" }, workout = { clicks += "workout:$it" },
      run = { clicks += "run:$it" }, goal = { clicks += "goal" }, checkout = { clicks += "checkout:$it" },
      form = { clicks += "form" })
    compose.setContent {
      val config = Configuration(LocalConfiguration.current).apply {
        uiMode = if (dark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
        fontScale = scale
      }
      val density = LocalDensity.current.density
      CompositionLocalProvider(LocalConfiguration provides config, LocalDensity provides Density(density, scale)) {
        OpenRunCoachTheme { TodayPreview(kind, scroll, actions) }
      }
    }
    compose.waitForIdle()
    compose.onNodeWithTag("today-quick-facts").performScrollTo().assertIsDisplayed()
    val columns = compose.onAllNodesWithTag("today-fact").fetchSemanticsNodes()
    assertEquals(homePresentation(HomePreviewData.state(kind), HomePreviewData.clock(kind)).quickFacts.size, columns.size)
    val widths = columns.map { it.boundsInRoot.width }
    assertTrue("Quick facts must use equal columns: $widths", widths.max() - widths.min() <= 1f)
    if (kind == HomeDayState.AfterRun) compose.onNodeWithTag("today-checkout").assertExists()
    else compose.onNodeWithTag("today-checkout").assertDoesNotExist()
    compose.onNodeWithText("Overview").assertDoesNotExist()
    compose.onNodeWithText("Just right").assertDoesNotExist()
    compose.onNodeWithText("NEXT UP").performScrollTo().assertIsDisplayed()
    val nodes = compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Text), useUnmergedTree = true).fetchSemanticsNodes()
    compose.runOnIdle {
      nodes.forEach { node ->
        val layouts = mutableListOf<TextLayoutResult>()
        node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(layouts)
        layouts.forEach { result ->
          val fits = !result.multiParagraph.didExceedMaxLines && (0 until result.lineCount).all { line ->
            result.getLineLeft(line) >= -1f && result.getLineRight(line) <= result.size.width + 1f &&
              result.getLineBottom(line) <= result.size.height + 1f
          }
          assertTrue("Clipped $day/$dark/$scale: ${result.layoutInput.text}; size=${result.size}; lines=${(0 until result.lineCount).map { listOf(result.getLineLeft(it), result.getLineRight(it), result.getLineBottom(it)) }}", fits)
        }
      }
      scroll.dispatchRawDelta(-scroll.value.toFloat())
    }
    val directory = InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null)!!
    var page = 1
    while (true) {
      compose.waitForIdle()
      val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
      val filename = "today-${day.lowercase()}-${if (dark) "dark" else "light"}-${(scale * 100).toInt()}-page-$page.png"
      File(directory, filename).outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
      if (scroll.value == scroll.maxValue || scale != 1f) break
      val height = compose.onNodeWithTag("today-scroll").fetchSemanticsNode().boundsInRoot.height
      compose.runOnIdle { scroll.dispatchRawDelta(height) }
      check(++page <= 8) { "Unexpectedly long Today screen" }
    }
    // Callback identities are the exact values later encoded by Navigation.kt.
    compose.onNodeWithContentDescription("Settings").performScrollTo().performClick()
    compose.runOnIdle { assertTrue(clicks.contains("settings")) }
    when (kind) {
      HomeDayState.BeforeRun -> {
        compose.onNodeWithText("See workout").performScrollTo().performClick()
        compose.runOnIdle { assertTrue(clicks.contains("workout:fixture-long")) }
      }
      HomeDayState.AfterRun -> {
        compose.onNodeWithText("See run details").performScrollTo().performClick()
        compose.runOnIdle { assertTrue(clicks.contains("run:fixture-today")) }
        compose.onNodeWithText("Add details ›").performScrollTo().performClick()
        compose.runOnIdle { assertTrue(clicks.contains("checkout:fixture-today")) }
      }
      HomeDayState.RestDay -> {
        compose.onNodeWithText("See next workout").performScrollTo().performClick()
        compose.runOnIdle { assertTrue(clicks.contains("workout:fixture-long")) }
      }
    }
    compose.onNodeWithTag("today-goal").performScrollTo().performClick()
    compose.onNodeWithText("Details").performScrollTo().performClick()
    compose.runOnIdle { assertTrue(clicks.containsAll(listOf("goal", "form"))) }
  }

  companion object {
    @JvmStatic @Parameterized.Parameters(name = "{0}-{1}-{2}") fun cases(): List<Array<Any>> =
      listOf("BeforeRun", "AfterRun", "RestDay").flatMap { day ->
        listOf(false, true).flatMap { dark -> listOf(1f, 1.3f, 2f).map { scale -> arrayOf(day, dark, scale) } }
      }
  }
}
