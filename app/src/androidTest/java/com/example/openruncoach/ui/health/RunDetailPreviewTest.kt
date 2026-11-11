package com.example.openruncoach.ui.health

import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.compose.foundation.ScrollState
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.*
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

@RunWith(Parameterized::class)
class RunDetailPreviewTest(private val dark: Boolean, private val scale: Float) {
  @get:Rule val compose = createComposeRule()
  @Test fun singleScrollRetainsDataLinksAndConfirmation() {
    val scroll=ScrollState(0)
    val clicks=mutableListOf<String>()
    compose.setContent {
      val config=Configuration(LocalConfiguration.current).apply { uiMode=if(dark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO; fontScale=scale }
      CompositionLocalProvider(LocalInspectionMode provides true,LocalConfiguration provides config,LocalDensity provides Density(LocalDensity.current.density,scale)) {
        OpenRunCoachTheme { RunDetailContent(RunDetailPreviewData.state(),RunDetailPreviewData.clock,
          DetailActions(coach={clicks+="coach"},inspect={clicks+="inspect"},toggleRunRelevance={clicks+="visibility"}),scroll=scroll) }
      }
    }
    compose.onNodeWithText("Overview").assertDoesNotExist()
    compose.onNodeWithText("pace /km").assertExists()
    compose.onNodeWithText("Shaded: target range. Higher means faster.").assertExists()
    compose.onNodeWithText("Fastest 30 sec").assertDoesNotExist()
    var page=1
    while(true) {
      compose.waitForIdle()
      val nodes=compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Text),true).fetchSemanticsNodes()
      compose.runOnIdle { nodes.forEach { node ->
        val results=mutableListOf<TextLayoutResult>()
        node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(results)
        results.forEach { assertFalse("Text clipped: ${it.layoutInput.text}",it.multiParagraph.didExceedMaxLines) }
      } }
      if(scale==1f || page==1) capture("run-${if(dark) "dark" else "light"}-${(scale*100).toInt()}-page-${page}.png")
      if(scroll.value>=scroll.maxValue)break
      val height=compose.onNodeWithTag("run-detail-scroll").fetchSemanticsNode().boundsInRoot.height
      compose.runOnIdle { scroll.dispatchRawDelta(height*.9f) }
      check(++page<40)
    }
    compose.onNodeWithText("Coach analysis").performScrollTo().performClick()
    compose.onNodeWithText("Health data inspector").performScrollTo().performClick()
    compose.onNodeWithText("Data sources").performScrollTo().performClick()
    compose.onNodeWithText("Recording diagnostics").performScrollTo().performClick()
    compose.onNodeWithText("Run Metrics").assertExists()
    compose.onNodeWithText("Ignore run").performScrollTo().performClick()
    compose.onNodeWithText("Cancel").performClick()
    compose.runOnIdle { assertFalse(clicks.contains("visibility")) }
    compose.onNodeWithText("Ignore run").performClick()
    compose.onNodeWithText("Confirm").performClick()
    compose.runOnIdle { assertTrue(clicks.containsAll(listOf("coach","inspect","visibility"))) }
  }
  private fun capture(name:String) {
    val context=InstrumentationRegistry.getInstrumentation().targetContext
    File(context.getExternalFilesDir(null),name).outputStream().use { compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG,100,it) }
  }
  companion object {
    @JvmStatic @Parameterized.Parameters(name="{0}-{1}") fun cases()=listOf(false,true).flatMap { dark -> listOf(1f,1.3f,2f).map { arrayOf<Any>(dark,it) } }
  }
}
