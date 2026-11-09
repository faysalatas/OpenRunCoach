package com.example.openruncoach.ui.navigation

import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.core.view.WindowCompat
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.example.openruncoach.*
import com.example.openruncoach.data.local.OpenRunCoachDatabase
import com.example.openruncoach.theme.OpenRunCoachTheme
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Real NavHost and destination screens, isolated in-memory database; never MainActivity. */
class NavigationShellTest {
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
  private var database: OpenRunCoachDatabase? = null
  private lateinit var nav: NavHostController

  private fun launch(dark: Boolean) {
    val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), OpenRunCoachDatabase::class.java)
      .allowMainThreadQueries().build()
    database = db
    val environment = OpenRunCoachEnvironment("navigation-fixture", db,
      Clock.fixed(Instant.parse("2026-11-08T09:00:00Z"), ZoneId.of("Europe/Berlin")), false)
    compose.activity.runOnUiThread { compose.activity.enableEdgeToEdge() }
    compose.setContent {
      val config = Configuration(LocalConfiguration.current).apply {
        uiMode = if (dark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
      }
      CompositionLocalProvider(LocalConfiguration provides config, LocalOpenRunCoachEnvironment provides environment) {
        OpenRunCoachTheme {
          nav = rememberNavController()
          MainNavigation(nav)
        }
      }
    }
    compose.waitForIdle()
  }

  @After fun close() { database?.close() }

  @Test fun lightShellAndSettingsEntry() = capture(false)
  @Test fun darkShellAndSettingsEntry() = capture(true)

  private fun capture(dark: Boolean) {
    launch(dark)
    compose.onNodeWithTag("bottom-navigation").assertIsDisplayed()
    listOf("Today", "Plan", "Runs").forEach { label ->
      compose.onNode(hasText(label) and hasAnyAncestor(hasTestTag("bottom-navigation"))).assertIsDisplayed()
    }
    compose.runOnIdle {
      assertFalse(WindowCompat.getInsetsController(compose.activity.window, compose.activity.window.decorView).isAppearanceLightStatusBars)
    }
    val image = compose.onRoot().captureToImage().asAndroidBitmap()
    val directory = InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null)!!
    File(directory, "navigation-${if (dark) "dark" else "light"}.png").outputStream().use {
      assertTrue(image.compress(Bitmap.CompressFormat.PNG, 100, it))
    }
    compose.onNodeWithContentDescription("Settings").performClick()
    compose.onNodeWithTag("bottom-navigation").assertDoesNotExist()
    compose.runOnIdle { assertEquals("settings", nav.currentDestination?.route); compose.activity.onBackPressedDispatcher.onBackPressed() }
    compose.onNodeWithTag("bottom-navigation").assertIsDisplayed()
    compose.runOnIdle { assertEquals("home", nav.currentDestination?.route) }
    for (label in listOf("Plan", "Runs", "Today")) {
      compose.onNode(hasText(label) and hasAnyAncestor(hasTestTag("bottom-navigation"))).performClick()
      compose.onNodeWithTag("bottom-navigation").assertIsDisplayed()
    }
  }

  @Test fun everyExistingRouteAndNewCheckoutStubStillOpens() {
    launch(false)
    val routes = listOf("home", "plan", "history", "settings", "performance-profile", "goal", "workout-preview",
      "planned-workout?workoutId=missing-fixture", "run?runId=missing-fixture", "data-inspector?runId=missing-fixture",
      "coach?runId=missing-fixture", "simulator", "run-checkout?runId=missing-fixture")
    for (route in routes) {
      compose.runOnIdle { nav.navigate(route) }
      compose.waitForIdle()
      compose.runOnIdle { assertEquals(route.substringBefore('?'), nav.currentDestination?.route?.substringBefore('?')) }
      if (route in listOf("home", "plan", "history")) compose.onNodeWithTag("bottom-navigation").assertExists()
      else compose.onNodeWithTag("bottom-navigation").assertDoesNotExist()
    }
    compose.onNodeWithText("Open run details").performClick()
    compose.runOnIdle { assertEquals("run?runId={runId}", nav.currentDestination?.route) }
  }
}
