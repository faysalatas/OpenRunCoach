package com.example.openruncoach.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

data class RunChartColors(
  val pace: Color,
  val heartRate: Color,
  val cadence: Color,
  val power: Color,
)

val LocalRunChartColors = staticCompositionLocalOf {
  RunChartColors(TealAccent, WarmChart, CoolChart, AnthraciteTextSecondary)
}

private val DarkColorScheme = darkColorScheme(
  primary = TealAccent,
  onPrimary = TealOnAccent,
  primaryContainer = Color(0xFF164D45),
  onPrimaryContainer = Color(0xFFC2FFF5),
  secondary = CoolChart,
  onSecondary = Color(0xFF071A32),
  tertiary = WarmChart,
  onTertiary = Color(0xFF330805),
  background = AnthraciteBackground,
  onBackground = AnthraciteText,
  surface = AnthraciteSurface,
  onSurface = AnthraciteText,
  surfaceVariant = AnthraciteVariant,
  onSurfaceVariant = AnthraciteTextSecondary,
  surfaceContainer = AnthraciteSurface,
  surfaceContainerHigh = AnthraciteElevated,
  outline = AnthraciteOutline,
  outlineVariant = AnthraciteOutline,
)

private val LightColorScheme = lightColorScheme(
  primary = LightTeal,
  onPrimary = LightOnTeal,
  primaryContainer = Color(0xFF9EF2E3),
  onPrimaryContainer = Color(0xFF00201C),
  secondary = LightCoolChart,
  tertiary = LightWarmChart,
  background = LightSurface,
  surface = Color.White,
  surfaceVariant = LightSurfaceVariant,
  outline = LightOutline,
)

@Composable
fun OpenRunCoachTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  content: @Composable () -> Unit,
) {
  val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
  val chartColors = if (darkTheme) {
    RunChartColors(TealAccent, WarmChart, CoolChart, AnthraciteTextSecondary)
  } else {
    RunChartColors(LightTeal, LightWarmChart, LightCoolChart, LightOutline)
  }
  CompositionLocalProvider(LocalRunChartColors provides chartColors) {
    MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
  }
}
