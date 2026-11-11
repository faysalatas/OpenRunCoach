package com.example.openruncoach.ui.history

import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.CompositionLocalProvider
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
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class HistoryPreviewTest(private val dark: Boolean, private val scale: Float) {
  @get:Rule val compose = createComposeRule()
  @Test fun allWeeksScrollAndOpenWithoutClipping() {
    val state = HistoryPreviewData.state()
    val scroll = LazyListState(0,0)
    val clicks = mutableListOf<String>()
    compose.setContent {
      val config = Configuration(LocalConfiguration.current).apply {
        uiMode = if(dark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
        fontScale=scale
      }
      val density=LocalDensity.current.density
      CompositionLocalProvider(LocalConfiguration provides config,LocalDensity provides Density(density,scale)) {
        OpenRunCoachTheme { RunsPreview(state,scroll,{clicks+=it},{clicks+="ignored"}) }
      }
    }
    compose.waitForIdle()
    val directory=InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null)!!
    var page=1
    while(true) {
      compose.waitForIdle()
      val nodes=compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Text),useUnmergedTree=true).fetchSemanticsNodes()
      compose.runOnIdle { nodes.forEach { node ->
        val layouts=mutableListOf<TextLayoutResult>()
        node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(layouts)
        layouts.forEach { result -> assertTrue("Clipped $dark/$scale: ${result.layoutInput.text}",
          !result.multiParagraph.didExceedMaxLines && (0 until result.lineCount).all {
            result.getLineRight(it)<=result.size.width+1f && result.getLineBottom(it)<=result.size.height+1f }) }
      } }
      if(scale==1f || page==1) {
        val bitmap=compose.onRoot().captureToImage().asAndroidBitmap()
        File(directory,"runs-${if(dark) "dark" else "light"}-${(scale*100).toInt()}-page-$page.png").outputStream().use {
          assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,it))
        }
      }
      if(!scroll.canScrollForward)break
      val before=scroll.firstVisibleItemIndex to scroll.firstVisibleItemScrollOffset
      val height=compose.onNodeWithTag("runs-scroll").fetchSemanticsNode().boundsInRoot.height
      compose.runOnIdle { scroll.dispatchRawDelta(height) }
      compose.waitForIdle()
      assertNotEquals(before,scroll.firstVisibleItemIndex to scroll.firstVisibleItemScrollOffset)
      check(++page<20)
    }
    val last=state.groups.flatMap { it.runs }.minBy { it.run.startTime }.run.healthConnectRecordId
    compose.onNodeWithTag("history-run:$last").assertIsDisplayed().performClick()
    compose.onNodeWithTag("runs-scroll").performScrollToNode(hasText("Ignored (2)"))
    compose.onNodeWithText("Ignored (2)").performClick()
    compose.runOnIdle { assertTrue(clicks.containsAll(listOf(last,"ignored"))) }
  }
  companion object {
    @JvmStatic @Parameterized.Parameters(name="{0}-{1}") fun cases():List<Array<Any>> =
      listOf(false,true).flatMap { dark -> listOf(1f,1.3f,2f).map { arrayOf<Any>(dark,it) } }
  }
}
