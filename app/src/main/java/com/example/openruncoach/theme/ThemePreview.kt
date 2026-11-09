package com.example.openruncoach.theme

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

/** This same preview is captured by the isolated emulator screenshot tests. */
@Preview(name = "Tokens · light", widthDp = 390, heightDp = 1900, uiMode = Configuration.UI_MODE_NIGHT_NO)
@Preview(name = "Tokens · dark", widthDp = 390, heightDp = 1900, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun OpenRunThemeTokensPreview() {
  OpenRunCoachTheme {
    val p = LocalOpenRunPalette.current
    Surface(color = p.bg) {
      Column(
        Modifier.fillMaxWidth().padding(OpenRunSpacing.ScreenHorizontal),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        Text("THEME TOKENS", style = OpenRunType.eyebrow, color = p.muted)
        Text("OpenRunCoach", style = OpenRunType.miniValue)
        val colors = listOf(
          "bg" to p.bg, "card" to p.card, "ink" to p.ink, "muted" to p.muted,
          "line" to p.line, "hero" to p.hero, "accent" to p.accent,
          "accentText" to p.accentText, "onAccent" to p.onAccent,
          "good" to p.good, "goodBg" to p.goodBg, "chip" to p.chip,
          "nav" to p.nav, "dash" to p.dash, "band" to p.band,
          "map" to p.map, "heart" to p.heart, "runEasy" to p.runEasy, "runLong" to p.runLong,
        )
        colors.chunked(2).forEach { pair ->
          Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            pair.forEach { (name, color) ->
              Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.size(28.dp).background(color, OpenRunShapeTokens.Progress))
                Column {
                  Text(name, style = OpenRunType.caption.copy(fontWeight = FontWeight.SemiBold))
                  Text("#%06X".format(color.toArgb() and 0xFFFFFF), style = OpenRunType.caption, color = p.muted)
                }
              }
            }
          }
        }
        Column(Modifier.fillMaxWidth().background(p.hero, OpenRunShapeTokens.Input).padding(12.dp)) {
          Text("ON HERO", style = OpenRunType.eyebrow, color = p.onHero)
          Text("Secondary · #FFE2D2", style = OpenRunType.caption, color = p.onHeroSecondary)
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Chip · 20%", Modifier.background(p.onHeroChip).padding(4.dp), color = p.onHero, style = OpenRunType.caption)
            Text("Lanes · 15%", Modifier.background(p.trackLanes).padding(4.dp), color = p.onHero, style = OpenRunType.caption)
            Text("Hero button", Modifier.background(p.onHero).padding(4.dp), color = p.heroButtonText, style = OpenRunType.caption)
          }
        }
        Text("accent + onAccent", Modifier.fillMaxWidth().background(p.accent, OpenRunShapeTokens.Button).padding(12.dp),
          color = p.onAccent, style = OpenRunType.title)
        Text("TYPE · BUNDLED FONTS", style = OpenRunType.eyebrow, color = p.muted)
        listOf(
          "heroDisplay · 56/56" to OpenRunType.heroDisplay,
          "pageTitle · 44/46" to OpenRunType.pageTitle,
          "subTitle · 40/44" to OpenRunType.subTitle,
          "heroMetric · 34/38" to OpenRunType.heroMetric,
          "bigNumber · 38/42" to OpenRunType.bigNumber,
          "miniValue · 28/32" to OpenRunType.miniValue,
          "cardTitleLg · 20/24" to OpenRunType.cardTitleLg,
          "title · 16/22 · 600" to OpenRunType.title,
          "body · 16/24 · 400" to OpenRunType.body,
          "bodySm · 15/22 · 400" to OpenRunType.bodySm,
          "meta · 14/20 · 400–600" to OpenRunType.meta,
          "caption · 13/19 · 400–600" to OpenRunType.caption,
          "EYEBROW · 12/16 · +1.6" to OpenRunType.eyebrow,
        ).forEach { (label, style) ->
          Column {
            Text(label, style = OpenRunType.caption, color = p.muted)
            Text("0123456789", style = style)
          }
        }
        Text("Bricolage 600", style = OpenRunType.cardTitleLg.copy(fontWeight = FontWeight.SemiBold))
        Text("Inter 400 · 500 · 600", style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
          Text("Medium", style = OpenRunType.meta.copy(fontWeight = FontWeight.Medium))
          Text("Semibold", style = OpenRunType.meta.copy(fontWeight = FontWeight.SemiBold))
        }
        Text("SHAPE & SPACING · DP", style = OpenRunType.eyebrow, color = p.muted)
        Text("Page ${OpenRunSpacing.ScreenHorizontal.value.toInt()} · card gap ${OpenRunSpacing.CardGap.value.toInt()} · grid ${OpenRunSpacing.GridGap.value.toInt()}\n" +
          "Card padding ${OpenRunSpacing.CardContent.value.toInt()} · mini 14 × 16\n" +
          "Card radius 20 · row 16 · input 14\n" +
          "Pill ≥ ${OpenRunSpacing.PillMinHeight.value.toInt()} high · radius 24–26\n" +
          "Chip ${OpenRunSpacing.ChipHeight.value.toInt()} high · radius 16\n" +
          "Progress ${OpenRunSpacing.ProgressHeight.value.toInt()} high · radius 5\n" +
          "Touch ≥ ${OpenRunSpacing.TouchTargetMin.value.toInt()} · elevation ${OpenRunSpacing.Elevation.value.toInt()}",
          style = OpenRunType.caption)
        Box(Modifier.fillMaxWidth().height(OpenRunSpacing.ProgressHeight).background(p.line, RoundedCornerShape(5.dp)))
      }
    }
  }
}
