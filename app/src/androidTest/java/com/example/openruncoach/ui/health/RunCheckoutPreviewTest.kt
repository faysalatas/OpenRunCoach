package com.example.openruncoach.ui.health

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
import com.example.openruncoach.domain.*
import com.example.openruncoach.theme.OpenRunCoachTheme
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class RunCheckoutPreviewTest(private val dark:Boolean,private val scale:Float) {
  @get:Rule val compose=createComposeRule()
  @Test fun allFieldsRemainSelectableAndReadable() {
    val scroll=ScrollState(0)
    var state by mutableStateOf(RunDetailPreviewData.state().copy(feedback=null,form=FeedbackForm("4",readiness="4",notes="Comfortable and relaxed.")))
    var saved:FeedbackForm?=null
    var cancelled=false
    compose.setContent {
      val config=Configuration(LocalConfiguration.current).apply { uiMode=if(dark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO; fontScale=scale }
      CompositionLocalProvider(LocalConfiguration provides config,LocalDensity provides Density(LocalDensity.current.density,scale)) {
        OpenRunCoachTheme { RunCheckoutContent(state,RunDetailPreviewData.clock,onChange={ change -> state=state.copy(form=change(state.form)) },
          onSave={saved=state.form},onCancel={cancelled=true},scroll=scroll) }
      }
    }
    var page=1
    while(true) {
      compose.waitForIdle()
      val nodes=compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Text),true).fetchSemanticsNodes()
      compose.runOnIdle { nodes.forEach { node ->
        val layouts=mutableListOf<TextLayoutResult>()
        node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(layouts)
        layouts.forEach { assertTrue("Clipped ${it.layoutInput.text}",!it.multiParagraph.didExceedMaxLines && (0 until it.lineCount).all { line -> it.getLineRight(line)<=it.size.width+1f && it.getLineBottom(line)<=it.size.height+1f }) }
      } }
      if(scale==1f || page==1) {
        val directory=InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null)
        File(directory,"checkout-${if(dark) "dark" else "light"}-${(scale*100).toInt()}-page-$page.png").outputStream().use { compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG,100,it) }
      }
      if(scroll.value>=scroll.maxValue)break
      val height=compose.onNodeWithTag("run-checkout-scroll").fetchSemanticsNode().boundsInRoot.height
      compose.runOnIdle { scroll.dispatchRawDelta(height*.9f) };check(++page<30)
    }
    compose.onNodeWithTag("checkout-rpe-10").performScrollTo().performClick().assertIsSelected()
    compose.onNodeWithText("Limit").performScrollTo().performClick()
    compose.onNodeWithText("Severe").performScrollTo().performClick()
    compose.onNodeWithText("Significant").performScrollTo().performClick()
    compose.onNodeWithText("Aborted").performScrollTo().performClick()
    compose.onNodeWithTag("checkout-readiness-5").performScrollTo().performClick().assertIsSelected()
    compose.onNodeWithText("Clear readiness").performScrollTo().performClick()
    compose.runOnIdle { assertEquals("",state.form.readiness) }
    compose.onNodeWithTag("checkout-readiness-1").performScrollTo().performClick()
    compose.onNodeWithTag("checkout-notes").performScrollTo().performTextReplacement("Notes changed")
    compose.onNodeWithText("Save checkout").performScrollTo().performClick()
    compose.runOnIdle {
      assertEquals(FeedbackForm("10",CardioEffort.LIMIT,MuscleFatigue.SEVERE,PainLevel.SIGNIFICANT,CompletionStatus.ABORTED,"1","Notes changed"),saved)
      state=state.copy(feedback=RunDetailPreviewData.state().feedback)
    }
    compose.onNodeWithText("Update checkout").assertExists()
    compose.onNodeWithText("Cancel").performScrollTo().performClick()
    compose.runOnIdle { assertTrue(cancelled) }
  }
  companion object { @JvmStatic @Parameterized.Parameters(name="{0}-{1}") fun cases()=listOf(false,true).flatMap { dark -> listOf(1f,1.3f,2f).map { arrayOf<Any>(dark,it) } } }
}
