package com.example.openruncoach.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.openruncoach.theme.*

@Composable
fun OpenRunHero(
  title: String,
  modifier: Modifier = Modifier,
  eyebrow: String? = null,
  subtitle: String? = null,
  backLabel: String? = null,
  onBack: (() -> Unit)? = null,
  header: (@Composable RowScope.() -> Unit)? = null,
  titleStyle: TextStyle = OpenRunType.pageTitle,
  subtitleStyle: TextStyle = OpenRunType.meta,
  eyebrowLeading: (@Composable () -> Unit)? = null,
  content: @Composable ColumnScope.() -> Unit = {},
) {
  val p = LocalOpenRunPalette.current
  Box(modifier.fillMaxWidth().clipToBounds()
    .background(Brush.verticalGradient(0f to p.hero, 0.62f to p.hero, 1f to p.bg))) {
    TrackLanes(Modifier.align(Alignment.TopEnd).offset(x = 90.dp, y = 60.dp))
    CompositionLocalProvider(LocalContentColor provides p.onHero) {
      Column(Modifier.align(Alignment.TopCenter).widthIn(max = 600.dp).fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars.only(WindowInsetsSides.Top)).padding(horizontal = 20.dp)
        .padding(top = if (backLabel == null) 24.dp else 16.dp, bottom = 88.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (header != null) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, content = header)
        if (backLabel != null && onBack != null) OpenRunBackLink(backLabel, onBack)
        if (!eyebrow.isNullOrBlank()) Row(verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          eyebrowLeading?.invoke()
          Text(eyebrow, style = OpenRunType.eyebrow, color = p.onHeroSecondary)
        }
        Text(title, style = titleStyle)
        if (!subtitle.isNullOrBlank()) Text(subtitle, style = subtitleStyle, color = p.onHeroSecondary)
        content()
      }
    }
  }
}

/** Place the body slot here so its first card overlaps the hero fade by 8 dp. */
@Composable
fun OpenRunHeroBody(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
  Column(modifier.offset(y = (-8).dp).padding(horizontal = 20.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp), content = content)
}

@Composable
fun TrackLanes(modifier: Modifier = Modifier) {
  val color = LocalOpenRunPalette.current.trackLanes
  Canvas(modifier.size(300.dp, 190.dp).clearAndSetSemantics {}) {
    repeat(4) { lane ->
      val inset = (10 + 16 * lane).dp.toPx()
      drawRoundRect(color, Offset(inset, inset),
        Size((240 - 32 * lane).dp.toPx(), (150 - 32 * lane).dp.toPx()),
        CornerRadius((75 - 16 * lane).dp.toPx()), style = Stroke(2.dp.toPx()))
    }
  }
}

internal enum class Glyph { Check, Back, Next, Down }

@Composable
internal fun OpenRunGlyph(glyph: Glyph, color: Color, modifier: Modifier = Modifier) {
  Canvas(modifier.clearAndSetSemantics {}) {
    val path = Path()
    when (glyph) {
      Glyph.Check -> { path.moveTo(size.width * .2f, size.height * .5f); path.lineTo(size.width * .43f, size.height * .72f); path.lineTo(size.width * .82f, size.height * .25f) }
      Glyph.Back -> { path.moveTo(size.width * .65f, size.height * .2f); path.lineTo(size.width * .35f, size.height * .5f); path.lineTo(size.width * .65f, size.height * .8f) }
      Glyph.Next -> { path.moveTo(size.width * .35f, size.height * .2f); path.lineTo(size.width * .65f, size.height * .5f); path.lineTo(size.width * .35f, size.height * .8f) }
      Glyph.Down -> { path.moveTo(size.width * .2f, size.height * .35f); path.lineTo(size.width * .5f, size.height * .65f); path.lineTo(size.width * .8f, size.height * .35f) }
    }
    drawPath(path, color, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
  }
}

@Composable
fun OpenRunBackLink(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
  val color = LocalOpenRunPalette.current.onHero
  TextButton(onClick, modifier.heightIn(min = 44.dp), contentPadding = PaddingValues(end = 12.dp)) {
    OpenRunGlyph(Glyph.Back, color, Modifier.size(20.dp))
    Text(label, style = OpenRunType.bodySm.copy(fontWeight = FontWeight.SemiBold), color = color)
  }
}

@Composable
fun OpenRunHeroButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
  val p = LocalOpenRunPalette.current
  Button(onClick, modifier.heightIn(min = 48.dp), enabled = enabled, shape = OpenRunShapeTokens.Button, elevation = null,
    colors = ButtonDefaults.buttonColors(containerColor = p.onHero, contentColor = p.heroButtonText)) {
    Text(text, style = OpenRunType.title)
  }
}

@Composable
fun OpenRunOptionChip(text: String, selected: Boolean, onClick: () -> Unit,
  modifier: Modifier = Modifier, accentSelected: Boolean = false, enabled: Boolean = true,
  role: Role = Role.RadioButton,
) {
  val p = LocalOpenRunPalette.current
  Surface(modifier = modifier.heightIn(min = 48.dp)
    .selectable(selected, enabled = enabled, role = role, onClick = onClick),
    shape = OpenRunShapeTokens.Button,
    color = if (selected) { if (accentSelected) p.accent else p.ink } else p.card,
    contentColor = if (selected) { if (accentSelected) p.onAccent else p.bg } else p.ink,
    border = if (selected) null else androidx.compose.foundation.BorderStroke(1.5.dp, p.line)) {
    Box(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), contentAlignment = Alignment.Center) {
      Text(text, style = OpenRunType.title)
    }
  }
}

/** One reveal per appearance; Android's reduced-motion preference is respected. */
@Composable
internal fun chartReveal(animate: Boolean): Float {
  val context = LocalContext.current
  val preview = LocalInspectionMode.current
  val reduced = android.provider.Settings.Global.getFloat(context.contentResolver,
    android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
  val skip = !animate || preview || reduced
  val reveal = remember(skip) { Animatable(if (skip) 1f else 0f) }
  LaunchedEffect(skip) { if (!skip) reveal.animateTo(1f, tween(500, easing = LinearOutSlowInEasing)) }
  return reveal.value
}

@Composable
fun OpenRunProgressBar(fraction: Float, contentDescription: String, modifier: Modifier = Modifier,
  useInk: Boolean = false, animate: Boolean = true,
) {
  if (!fraction.isFinite()) return
  val p = LocalOpenRunPalette.current
  val value = fraction.coerceIn(0f, 1f)
  val reveal = chartReveal(animate)
  Canvas(modifier.fillMaxWidth().height(10.dp).semantics {
    this.contentDescription = contentDescription
    progressBarRangeInfo = ProgressBarRangeInfo(value, 0f..1f)
  }) {
    drawRoundRect(p.line, cornerRadius = CornerRadius(5.dp.toPx()))
    if (value > 0) drawRoundRect(if (useInk) p.ink else p.accentText,
      size = Size(size.width * value * reveal, size.height), cornerRadius = CornerRadius(5.dp.toPx()))
  }
}

@Composable
fun OpenRunMiniStatCard(title: String, value: String?, unit: String? = null, delta: String? = null,
  modifier: Modifier = Modifier, good: Boolean = false, sparkline: (@Composable () -> Unit)? = null,
) {
  if (value.isNullOrBlank()) return
  val p = LocalOpenRunPalette.current
  OpenRunCard(modifier, contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)) {
    Text(title, style = OpenRunType.caption.copy(fontWeight = FontWeight.SemiBold), color = p.muted)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp), itemVerticalAlignment = Alignment.Bottom) {
      Text(value, style = OpenRunType.miniValue)
      if (!unit.isNullOrBlank()) Text(unit, style = OpenRunType.meta, color = p.muted)
    }
    if (!delta.isNullOrBlank()) Text(delta, style = OpenRunType.caption.copy(fontWeight = FontWeight.SemiBold),
      color = if (good) p.good else p.muted)
    sparkline?.invoke()
  }
}
