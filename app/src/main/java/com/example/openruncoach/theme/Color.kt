package com.example.openruncoach.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** Provider-independent design tokens. All literal UI colors live in this package. */
@Immutable
data class OpenRunPalette(
  val bg: Color,
  val card: Color,
  val ink: Color,
  val muted: Color,
  val line: Color,
  val hero: Color,
  val accent: Color,
  val accentText: Color,
  val good: Color,
  val goodBg: Color,
  val chip: Color,
  val nav: Color,
  val dash: Color,
  val band: Color,
  val map: Color,
  val heart: Color,
  val runEasy: Color,
  val runLong: Color,
  val onAccent: Color,
  val onHero: Color = Color.White,
  val onHeroSecondary: Color = Color(0xFFFFE2D2),
  val onHeroChip: Color = Color.White.copy(alpha = 0.20f),
  val trackLanes: Color = Color.White.copy(alpha = 0.15f),
  val heroButtonText: Color = Color(0xFF7A2A0C),
)

val LightOpenRunPalette = OpenRunPalette(
  bg = Color(0xFFF6EEE3),
  card = Color(0xFFFFFAF3),
  ink = Color(0xFF2A1C14),
  muted = Color(0xFF6E5B4E),
  line = Color(0xFFE6D8C6),
  hero = Color(0xFFBF4318),
  accent = Color(0xFFBF4318),
  accentText = Color(0xFFBF4318),
  good = Color(0xFF2F6B4F),
  goodBg = Color(0xFFDDEEE3),
  chip = Color(0xFFEFE4D5),
  nav = Color(0xFFFFFAF3),
  dash = Color(0xFFA89583),
  band = Color(0xFFDDEEE3),
  map = Color(0xFFEFE4D5),
  heart = Color(0xFFB43A52),
  runEasy = Color(0xFF3E9C85),
  runLong = Color(0xFF7D9A6E),
  onAccent = Color(0xFFFFFFFF),
)

val DarkOpenRunPalette = OpenRunPalette(
  bg = Color(0xFF1C1411),
  card = Color(0xFF2A1F1A),
  ink = Color(0xFFF6EEE3),
  muted = Color(0xFFBBA997),
  line = Color(0xFF3F3027),
  hero = Color(0xFFA63B15),
  accent = Color(0xFFFF8A5C),
  accentText = Color(0xFFFF9A6B),
  good = Color(0xFF8FD6AE),
  goodBg = Color(0xFF233A2E),
  chip = Color(0xFF3A2C24),
  nav = Color(0xFF241915),
  dash = Color(0xFF8C7A6B),
  band = Color(0xFF2F4A3B),
  map = Color(0xFF241915),
  heart = Color(0xFFFF9AA2),
  runEasy = Color(0xFF5DB7A0),
  runLong = Color(0xFFA9C29A),
  onAccent = Color(0xFF1C1411),
)

val LocalOpenRunPalette = staticCompositionLocalOf { LightOpenRunPalette }

/** Compatibility names for unmigrated callers; screen/component migration is a later step.
 * Non-composable consumers retain the dark palette until migrated to LocalOpenRunPalette.
 */
object OpenRunColors {
  val Background = DarkOpenRunPalette.bg
  val BackgroundDeep = DarkOpenRunPalette.bg
  val Surface = DarkOpenRunPalette.card
  val SurfaceElevated = DarkOpenRunPalette.card
  val SurfaceHighlight = DarkOpenRunPalette.chip
  val BorderSubtle = DarkOpenRunPalette.line
  val TextPrimary = DarkOpenRunPalette.ink
  val TextSecondary = DarkOpenRunPalette.muted
  val TextMuted = DarkOpenRunPalette.muted
  val Accent = DarkOpenRunPalette.accent
  val AccentContainer = DarkOpenRunPalette.goodBg
  val OnAccent = DarkOpenRunPalette.onAccent
  val Positive = DarkOpenRunPalette.good
  val Warning = Color(0xFFD5AD67)
  val Error = Color(0xFFE18B89)
  val ChartGrid = DarkOpenRunPalette.line
  val ChartFill = DarkOpenRunPalette.accent.copy(alpha = 0.14f)
  val ChartAxis = DarkOpenRunPalette.muted
  val ChartReference = DarkOpenRunPalette.muted
}

internal val LightWarning = Color(0xFF8A641F)
internal val LightError = Color(0xFFB3261E)
