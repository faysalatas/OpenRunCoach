package com.example.openruncoach.ui.components

import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.ScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.test.platform.app.InstrumentationRegistry
import com.example.openruncoach.theme.OpenRunCoachTheme
import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Isolated test activity only; never MainActivity, routes, repositories or health data. */
class ComponentsGalleryTest {
  @get:Rule val compose = createComposeRule()

  @Test fun light100() = capture(false, 1f)
  @Test fun dark100() = capture(true, 1f)
  @Test fun light130() = capture(false, 1.3f)
  @Test fun dark130() = capture(true, 1.3f)
  @Test fun light200() = capture(false, 2f)
  @Test fun dark200() = capture(true, 2f)

  private fun capture(dark: Boolean, scale: Float) {
    val scroll = ScrollState(0)
    compose.setContent {
      val configuration = Configuration(LocalConfiguration.current).apply {
        uiMode = if (dark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
        fontScale = scale
      }
      val density = LocalDensity.current.density
      CompositionLocalProvider(LocalConfiguration provides configuration, LocalDensity provides Density(density, scale)) {
        OpenRunComponentsGallery(scroll)
      }
    }
    compose.waitForIdle()
    compose.onNodeWithText("End of gallery").performScrollTo().assertIsDisplayed()
    compose.onNodeWithContentDescription("Easy pace improving").assertExists()
    compose.onNodeWithContentDescription("Each split gets faster: 7:24, 7:14, 7:10 per km").assertExists()
    val nodes = compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Text), useUnmergedTree = true).fetchSemanticsNodes()
    compose.runOnIdle {
      nodes.forEach { node ->
        val layouts = mutableListOf<TextLayoutResult>()
        node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(layouts)
        layouts.forEach { result ->
          // Paragraph allocation can exceed a wrap-content Text's actual width. Check rendered
          // line bounds instead, allowing only the float-to-integer pixel rounding difference.
          val fits = !result.multiParagraph.didExceedMaxLines &&
            (0 until result.lineCount).all { line ->
              result.getLineLeft(line) >= -1f && result.getLineRight(line) <= result.size.width + 1f &&
                result.getLineBottom(line) <= result.size.height + 1f
            }
          assertTrue("Text clipped at $scale: ${result.layoutInput.text}; size=${result.size}; paragraph=${result.multiParagraph.width} x ${result.multiParagraph.height}", fits)
        }
      }
    }
    compose.runOnIdle { scroll.dispatchRawDelta(-scroll.value.toFloat()) }
    val directory = InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null)!!
    var page = 1
    while (true) {
      compose.waitForIdle()
      val image = compose.onRoot().captureToImage().asAndroidBitmap()
      val name = "gallery-${if (dark) "dark" else "light"}-${(scale * 100).toInt()}-page-$page.png"
      File(directory, name).outputStream().use { assertTrue(image.compress(Bitmap.CompressFormat.PNG, 100, it)) }
      if (scroll.value == scroll.maxValue) break
      compose.runOnIdle { scroll.dispatchRawDelta(image.height.toFloat()) }
      page++
      check(page <= 10) { "Gallery unexpectedly exceeds ten capture pages" }
    }
  }

  @Test fun controlsExposeSelectionAndExpandWithoutChangingAppState() {
    var back = false
    compose.setContent {
      OpenRunCoachTheme {
        Column {
          var selected by remember { mutableStateOf(false) }
          OpenRunOptionChip("Option", selected, { selected = !selected })
          OpenRunDisclosure("Evidence") { Text("Expanded evidence") }
          OpenRunBackLink("Parent", { back = true })
        }
      }
    }
    compose.onNodeWithText("Option").assertIsNotSelected().performClick().assertIsSelected()
    compose.onNodeWithText("Expanded evidence").assertDoesNotExist()
    compose.onNodeWithText("Evidence").performClick()
    compose.onNodeWithText("Expanded evidence").assertIsDisplayed()
    compose.onNodeWithText("Parent").performClick()
    compose.runOnIdle { assertTrue(back) }
  }

  @Test fun missingDataDoesNotRenderChartsOrStatCards() {
    compose.setContent {
      OpenRunCoachTheme {
        Column {
          WeekBars(emptyList(), "absent week")
          LongRunBars(emptyList(), "absent long")
          WeeklyDistanceBars(emptyList(), "absent volume")
          PaceLineChart(listOf(ChartPoint(null)), "absent pace")
          HeartRateChart(emptyList(), "absent HR")
          Sparkline(emptyList(), "absent sparkline")
          SplitBars(emptyList(), "absent splits")
          OpenRunMiniStatCard("absent stat", null)
          Text("Empty state anchor")
        }
      }
    }
    listOf("absent week", "absent long", "absent volume", "absent pace", "absent HR", "absent sparkline", "absent splits")
      .forEach { compose.onNodeWithContentDescription(it).assertDoesNotExist() }
    compose.onNodeWithText("absent stat").assertDoesNotExist()
  }
}
