package com.example.openruncoach.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
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

private fun OpenRunPalette.chartColors() = RunChartColors(
  pace = accent,
  heartRate = heart,
  cadence = ink,
  power = muted,
  grid = line,
  fill = accent.copy(alpha = 0.14f),
  axis = muted,
  reference = muted,
)

val LocalRunChartColors = staticCompositionLocalOf { LightOpenRunPalette.chartColors() }

/** Material roles are derived from the palette exposed to custom components. */
internal fun OpenRunPalette.materialColorScheme(darkTheme: Boolean): ColorScheme {
  val base = if (darkTheme) darkColorScheme() else lightColorScheme()
  val error = if (darkTheme) OpenRunColors.Error else LightError
  return base.copy(
    primary = accent,
    onPrimary = onAccent,
    primaryContainer = chip,
    onPrimaryContainer = ink,
    inversePrimary = if (darkTheme) LightOpenRunPalette.accent else DarkOpenRunPalette.accent,
    secondary = muted,
    onSecondary = bg,
    secondaryContainer = chip,
    onSecondaryContainer = ink,
    tertiary = good,
    onTertiary = goodBg,
    tertiaryContainer = goodBg,
    onTertiaryContainer = good,
    error = error,
    onError = bg,
    errorContainer = error.copy(alpha = 0.14f),
    onErrorContainer = error,
    background = bg,
    onBackground = ink,
    surface = card,
    onSurface = ink,
    surfaceVariant = chip,
    onSurfaceVariant = muted,
    surfaceTint = Color.Transparent,
    inverseSurface = ink,
    inverseOnSurface = bg,
    surfaceBright = card,
    surfaceDim = bg,
    surfaceContainerLowest = bg,
    surfaceContainerLow = bg,
    surfaceContainer = card,
    surfaceContainerHigh = card,
    surfaceContainerHighest = chip,
    outline = line,
    outlineVariant = line,
    scrim = Color.Black,
  )
}

@Composable
fun OpenRunCoachTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  content: @Composable () -> Unit,
) {
  val palette = if (darkTheme) DarkOpenRunPalette else LightOpenRunPalette
  CompositionLocalProvider(
    LocalOpenRunPalette provides palette,
    LocalRunChartColors provides palette.chartColors(),
  ) {
    MaterialTheme(
      colorScheme = palette.materialColorScheme(darkTheme),
      typography = Typography,
      shapes = OpenRunShapes,
      content = content,
    )
  }
}
