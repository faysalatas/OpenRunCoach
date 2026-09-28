package com.example.openruncoach.theme

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
  val grid: Color,
  val fill: Color,
  val axis: Color,
  val reference: Color,
)

private val DarkRunChartColors = RunChartColors(
  pace = OpenRunColors.Accent,
  heartRate = OpenRunColors.TextSecondary,
  cadence = OpenRunColors.Accent,
  power = OpenRunColors.TextMuted,
  grid = OpenRunColors.ChartGrid,
  fill = OpenRunColors.ChartFill,
  axis = OpenRunColors.ChartAxis,
  reference = OpenRunColors.ChartReference,
)

val LocalRunChartColors = staticCompositionLocalOf { DarkRunChartColors }

private val DarkColorScheme = darkColorScheme(
  primary = OpenRunColors.Accent,
  onPrimary = OpenRunColors.OnAccent,
  primaryContainer = OpenRunColors.AccentContainer,
  onPrimaryContainer = OpenRunColors.TextPrimary,
  secondary = OpenRunColors.TextSecondary,
  onSecondary = OpenRunColors.BackgroundDeep,
  secondaryContainer = OpenRunColors.SurfaceHighlight,
  onSecondaryContainer = OpenRunColors.TextPrimary,
  tertiary = OpenRunColors.Warning,
  onTertiary = OpenRunColors.BackgroundDeep,
  tertiaryContainer = OpenRunColors.Warning.copy(alpha = 0.18f),
  onTertiaryContainer = OpenRunColors.Warning,
  error = OpenRunColors.Error,
  onError = OpenRunColors.BackgroundDeep,
  background = OpenRunColors.Background,
  onBackground = OpenRunColors.TextPrimary,
  surface = OpenRunColors.Surface,
  onSurface = OpenRunColors.TextPrimary,
  surfaceVariant = OpenRunColors.SurfaceHighlight,
  onSurfaceVariant = OpenRunColors.TextSecondary,
  surfaceContainerLowest = OpenRunColors.BackgroundDeep,
  surfaceContainerLow = OpenRunColors.Background,
  surfaceContainer = OpenRunColors.Surface,
  surfaceContainerHigh = OpenRunColors.SurfaceElevated,
  surfaceContainerHighest = OpenRunColors.SurfaceHighlight,
  outline = OpenRunColors.BorderSubtle,
  outlineVariant = OpenRunColors.BorderSubtle.copy(alpha = 0.58f),
)

private val LightColorScheme = lightColorScheme(
  primary = LightTeal,
  onPrimary = LightOnTeal,
  primaryContainer = Color(0xFFBCECE4),
  onPrimaryContainer = Color(0xFF00201C),
  secondary = LightTextSecondary,
  tertiary = LightWarning,
  background = LightSurface,
  surface = Color.White,
  surfaceVariant = LightSurfaceVariant,
  outline = LightOutline,
)

@Composable
fun OpenRunCoachTheme(
  darkTheme: Boolean = true,
  content: @Composable () -> Unit,
) {
  val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
  val chartColors = if (darkTheme) {
    DarkRunChartColors
  } else {
    RunChartColors(
      pace = LightTeal,
      heartRate = LightTextSecondary,
      cadence = LightTeal,
      power = LightOutline,
      grid = LightOutline.copy(alpha = 0.35f),
      fill = LightTeal.copy(alpha = 0.12f),
      axis = LightOutline,
      reference = LightOutline,
    )
  }
  CompositionLocalProvider(LocalRunChartColors provides chartColors) {
    MaterialTheme(
      colorScheme = colorScheme,
      typography = Typography,
      shapes = OpenRunShapes,
      content = content,
    )
  }
}
