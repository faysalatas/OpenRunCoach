package com.example.openruncoach.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.openruncoach.theme.*

private fun Modifier.optionalClick(onClick: (() -> Unit)?) =
  if (onClick == null) this else clickable(role = Role.Button, onClick = onClick)

@Composable
fun OpenRunRunRow(date: String, name: String, distance: String, pace: String?, modifier: Modifier = Modifier,
  dotColor: Color? = null, onClick: (() -> Unit)? = null,
) {
  val p = LocalOpenRunPalette.current
  Surface(modifier.fillMaxWidth().optionalClick(onClick), shape = OpenRunShapeTokens.Row, color = p.card) {
    BoxWithConstraints(Modifier.heightIn(min = 60.dp).padding(horizontal = 14.dp, vertical = 10.dp)) {
      val stack = maxWidth < 280.dp * LocalDensity.current.fontScale
      if (stack) Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
          Canvas(Modifier.size(10.dp)) { drawCircle(dotColor ?: p.muted) }
          Text(date, style = OpenRunType.caption, color = p.muted)
        }
        Text(name, style = OpenRunType.title)
        Text(listOfNotNull(distance, pace).joinToString(" · "), style = OpenRunType.meta)
      } else Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Canvas(Modifier.size(10.dp)) { drawCircle(dotColor ?: p.muted) }
        Text(date, Modifier.width(56.dp), style = OpenRunType.caption.copy(fontWeight = FontWeight.SemiBold), color = p.muted)
        Text(name, Modifier.weight(1f), style = OpenRunType.title)
        Column(horizontalAlignment = Alignment.End) {
          Text(distance, style = OpenRunType.title)
          if (!pace.isNullOrBlank()) Text(pace, style = OpenRunType.caption, color = p.muted)
        }
      }
    }
  }
}

@Composable
fun OpenRunWorkoutRow(day: String, name: String, meta: String?, modifier: Modifier = Modifier,
  done: Boolean = false, today: Boolean = false, future: Boolean = false, onClick: (() -> Unit)? = null,
) {
  val p = LocalOpenRunPalette.current
  val foreground = if (today) p.onHero else p.ink
  val secondary = if (today) p.onHeroSecondary else p.muted
  Surface(modifier.fillMaxWidth().optionalClick(onClick).semantics {
    if (done) stateDescription = "Completed"
    else if (today) stateDescription = "Today"
    else if (future) stateDescription = "Planned"
  }, shape = OpenRunShapeTokens.Row,
    color = if (today) p.hero else p.card) {
    val outline = if (future) Modifier.drawBehind {
      val inset = 1.dp.toPx()
      drawRoundRect(p.dash, topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
        size = androidx.compose.ui.geometry.Size(size.width - 2 * inset, size.height - 2 * inset),
        cornerRadius = CornerRadius(16.dp.toPx()), style = Stroke(2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx()))))
    } else Modifier
    BoxWithConstraints(outline.heightIn(min = 60.dp).padding(14.dp)) {
      val stack = maxWidth < 280.dp * LocalDensity.current.fontScale
      if (stack) Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(day, style = OpenRunType.caption, color = secondary)
        Text(name, style = OpenRunType.title, color = foreground)
        if (!meta.isNullOrBlank()) Text(meta, style = OpenRunType.meta, color = secondary)
        if (today) Text("TODAY", style = OpenRunType.eyebrow, color = foreground)
        if (done) OpenRunStatusChip("Completed", tone = OpenRunStatusTone.POSITIVE)
      } else Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(day, Modifier.width(36.dp), style = OpenRunType.caption.copy(fontWeight = FontWeight.SemiBold), color = secondary)
        Column(Modifier.weight(1f)) {
          Text(name, style = OpenRunType.title, color = foreground)
          if (!meta.isNullOrBlank()) Text(meta, style = OpenRunType.meta, color = secondary)
        }
        if (done) OpenRunGlyph(Glyph.Check, p.good, Modifier.size(22.dp).semantics { contentDescription = "Completed" })
        if (today) Text("TODAY", style = OpenRunType.eyebrow, color = foreground)
      }
    }
  }
}

@Composable
fun OpenRunNextUpRow(weekday: String, day: String, name: String, meta: String?, modifier: Modifier = Modifier,
  onClick: (() -> Unit)? = null,
) {
  val p = LocalOpenRunPalette.current
  Surface(modifier.fillMaxWidth().optionalClick(onClick), shape = OpenRunShapeTokens.Row, color = p.card) {
    Row(Modifier.heightIn(min = 60.dp).padding(14.dp), verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(12.dp)) {
      Column(Modifier.widthIn(min = 44.dp * LocalDensity.current.fontScale), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(weekday, style = OpenRunType.eyebrow, color = p.muted)
        Text(day, style = OpenRunType.cardTitleLg.copy(fontSize = 22.sp))
      }
      Column(Modifier.weight(1f)) {
        Text(name, style = OpenRunType.title)
        if (!meta.isNullOrBlank()) Text(meta, style = OpenRunType.meta, color = p.muted)
      }
    }
  }
}

@Composable
fun OpenRunSettingsRow(title: String, subtitle: String? = null, modifier: Modifier = Modifier,
  firstRow: Boolean = false, onClick: (() -> Unit)? = null, trailing: (@Composable () -> Unit)? = null,
) {
  val p = LocalOpenRunPalette.current
  Column(modifier.fillMaxWidth()) {
    if (!firstRow) HorizontalDivider(color = p.line)
    Row(Modifier.fillMaxWidth().heightIn(min = 60.dp).optionalClick(onClick).padding(vertical = 12.dp),
      horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
      Column(Modifier.weight(1f)) {
        Text(title, style = OpenRunType.title)
        if (!subtitle.isNullOrBlank()) Text(subtitle, style = OpenRunType.bodySm, color = p.muted)
      }
      if (trailing != null) trailing()
      else if (onClick != null) OpenRunGlyph(Glyph.Next, p.muted, Modifier.size(20.dp))
    }
  }
}

@Composable
fun OpenRunDisclosureRow(title: String, expanded: Boolean, onClick: () -> Unit,
  modifier: Modifier = Modifier, subtitle: String? = null, content: @Composable ColumnScope.() -> Unit = {},
) {
  val p = LocalOpenRunPalette.current
  Column(modifier.semantics { stateDescription = if (expanded) "Expanded" else "Collapsed" }) {
    OpenRunSettingsRow(title, subtitle, firstRow = true, onClick = onClick, trailing = {
      OpenRunGlyph(Glyph.Down, p.muted, Modifier.size(20.dp).rotate(if (expanded) 180f else 0f))
    })
    if (expanded) Column(verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
  }
}
