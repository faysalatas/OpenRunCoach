package com.example.openruncoach.theme

import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Runs only the theme preview in the test activity, never MainActivity or app data. */
class ThemePreviewTest {
  @get:Rule val compose = createComposeRule()

  @Test fun captureLightPreview() = capturePreview(false, "theme-tokens-light.png")
  @Test fun captureDarkPreview() = capturePreview(true, "theme-tokens-dark.png")

  private fun capturePreview(dark: Boolean, name: String) {
    val config = Configuration().apply {
      uiMode = if (dark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
      fontScale = 1f
    }
    compose.setContent {
      CompositionLocalProvider(LocalConfiguration provides config) { OpenRunThemeTokensPreview() }
    }
    compose.waitForIdle()
    val image = compose.onRoot().captureToImage().asAndroidBitmap()
    val p = if (dark) DarkOpenRunPalette else LightOpenRunPalette
    assertEquals(p.bg, androidx.compose.ui.graphics.Color(image.getPixel(0, 0)))
    val directory = InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null)!!
    File(directory, name).outputStream().use { assertTrue(image.compress(Bitmap.CompressFormat.PNG, 100, it)) }
  }

  @Test fun followsSystemConfigurationAndUpdatesMaterialAndChartRoles() {
    val night = mutableStateOf(false)
    var observed: OpenRunPalette? = null
    var material: androidx.compose.material3.ColorScheme? = null
    var chart: RunChartColors? = null
    compose.setContent {
      val config = Configuration(LocalConfiguration.current).apply {
        uiMode = if (night.value) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
      }
      CompositionLocalProvider(LocalConfiguration provides config) {
        OpenRunCoachTheme {
          observed = LocalOpenRunPalette.current
          material = MaterialTheme.colorScheme
          chart = LocalRunChartColors.current
        }
      }
    }
    fun check(p: OpenRunPalette) {
      assertEquals(p, observed)
      assertEquals(p.bg, material!!.background)
      assertEquals(p.card, material!!.surface)
      assertEquals(p.card, material!!.surfaceContainerHigh)
      assertEquals(p.ink, material!!.onSurface)
      assertEquals(p.muted, material!!.onSurfaceVariant)
      assertEquals(p.line, material!!.outlineVariant)
      assertEquals(p.accent, material!!.primary)
      assertEquals(p.onAccent, material!!.onPrimary)
      assertEquals(p.heart, chart!!.heartRate)
    }
    compose.runOnIdle { check(LightOpenRunPalette); night.value = true }
    compose.runOnIdle { check(DarkOpenRunPalette) }
  }
}
