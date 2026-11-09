package com.example.openruncoach.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.sp
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import com.example.openruncoach.theme.OpenRunColors
import com.example.openruncoach.theme.LocalOpenRunPalette
import com.example.openruncoach.theme.OpenRunType
import androidx.compose.foundation.layout.size
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.example.openruncoach.theme.OpenRunShapeTokens
import com.example.openruncoach.theme.OpenRunSpacing

@Composable
fun OpenRunSurface(
  modifier: Modifier = Modifier,
  content: @Composable ColumnScope.() -> Unit,
) {
  Surface(modifier = modifier, color = MaterialTheme.colorScheme.background) {
    Column(content = content)
  }
}

@Composable
fun OpenRunCard(
  modifier: Modifier = Modifier,
  highlighted: Boolean = false,
  contentPadding: PaddingValues = PaddingValues(OpenRunSpacing.CardContent),
  contentSpacing: Dp = 10.dp,
  content: @Composable ColumnScope.() -> Unit,
) {
  Card(
    modifier = modifier.fillMaxWidth(),
    shape = OpenRunShapeTokens.Card,
    colors = CardDefaults.cardColors(
      containerColor = if (highlighted) {
        LocalOpenRunPalette.current.chip
      } else {
        LocalOpenRunPalette.current.card
      },
    ),
    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
  ) {
    Column(
      modifier = Modifier.padding(contentPadding),
      verticalArrangement = Arrangement.spacedBy(contentSpacing),
      content = content,
    )
  }
}

@Composable
fun OpenRunSectionHeader(
  title: String,
  modifier: Modifier = Modifier,
  trailing: (@Composable RowScope.() -> Unit)? = null,
) {
  Row(
    modifier = modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(title.uppercase(), modifier = Modifier.weight(1f).padding(start = 4.dp, top = 4.dp),
      style = OpenRunType.eyebrow, color = LocalOpenRunPalette.current.muted)
    trailing?.invoke(this)
  }
}

@Composable
fun OpenRunDivider(modifier: Modifier = Modifier) {
  HorizontalDivider(
    modifier = modifier,
    thickness = 1.dp,
    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
  )
}

data class OpenRunMetricItem(val value: String, val label: String)

@Composable
fun OpenRunMetric(
  value: String,
  label: String,
  modifier: Modifier = Modifier,
  onHero: Boolean = false,
) {
  val p = LocalOpenRunPalette.current
  Column(modifier, verticalArrangement = Arrangement.spacedBy(OpenRunSpacing.Micro)) {
    Text(value, style = if (onHero) OpenRunType.heroMetric else OpenRunType.miniValue,
      color = if (onHero) p.onHero else p.ink)
    Text(label, style = OpenRunType.caption, color = if (onHero) p.onHeroSecondary else p.muted)
  }
}

@Composable
fun OpenRunMetricGroup(
  metrics: List<OpenRunMetricItem>,
  modifier: Modifier = Modifier,
  columns: Int = 2,
) {
  require(columns > 0) { "Metric columns must be positive" }
  Column(modifier, verticalArrangement = Arrangement.spacedBy(OpenRunSpacing.Standard)) {
    metrics.chunked(columns).forEach { metricRow ->
      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(OpenRunSpacing.Standard)) {
        metricRow.forEach { metric ->
          OpenRunMetric(metric.value, metric.label, Modifier.weight(1f))
        }
        repeat(columns - metricRow.size) { Box(Modifier.weight(1f)) }
      }
    }
  }
}

enum class OpenRunStatusTone { NEUTRAL, POSITIVE, ON_HERO, WARNING, ERROR }

@Composable
fun OpenRunStatusChip(
  text: String,
  modifier: Modifier = Modifier,
  tone: OpenRunStatusTone = OpenRunStatusTone.NEUTRAL,
  showCheck: Boolean = tone == OpenRunStatusTone.POSITIVE,
) {
  val p = LocalOpenRunPalette.current
  val (container, content) = when (tone) {
    OpenRunStatusTone.NEUTRAL -> p.chip to p.ink
    OpenRunStatusTone.POSITIVE -> p.goodBg to p.good
    OpenRunStatusTone.ON_HERO -> p.onHeroChip to p.onHero
    OpenRunStatusTone.WARNING -> OpenRunColors.Warning.copy(alpha = 0.14f) to OpenRunColors.Warning
    OpenRunStatusTone.ERROR -> MaterialTheme.colorScheme.error.copy(alpha = 0.14f) to MaterialTheme.colorScheme.error
  }
  Surface(modifier = modifier, shape = OpenRunShapeTokens.Chip, color = container, contentColor = content) {
    Row(Modifier.heightIn(min = 32.dp).padding(horizontal = 12.dp, vertical = 5.dp),
      horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
      if (showCheck) OpenRunGlyph(Glyph.Check, content, Modifier.size(16.dp))
      Text(text, style = OpenRunType.meta.copy(fontWeight = FontWeight.SemiBold))
    }
  }
}

@Composable
fun OpenRunTabs(
  tabs: List<String>,
  selectedIndex: Int,
  onSelected: (Int) -> Unit,
  modifier: Modifier = Modifier,
  distributeEvenly: Boolean = false,
) {
  if (distributeEvenly) {
    Row(modifier.fillMaxWidth().selectableGroup()) {
      tabs.forEachIndexed { index, label ->
        val selected = selectedIndex == index
        Column(
          modifier = Modifier.weight(1f).heightIn(min = 48.dp)
            .selectable(selected = selected, onClick = { onSelected(index) }, role = Role.Tab),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.Bottom,
        ) {
          Box(
            Modifier.fillMaxWidth().padding(horizontal = OpenRunSpacing.Small, vertical = OpenRunSpacing.Compact),
            contentAlignment = Alignment.Center,
          ) {
            Text(
              label,
              style = MaterialTheme.typography.labelLarge,
              color = if (selected) MaterialTheme.colorScheme.primary else LocalOpenRunPalette.current.muted,
            )
          }
          Box(
            Modifier.fillMaxWidth().height(2.dp).background(
              if (selected) MaterialTheme.colorScheme.primary
              else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
            ),
          )
        }
      }
    }
    return
  }

  Row(modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).selectableGroup()) {
    tabs.forEachIndexed { index, label ->
      val selected = selectedIndex == index
      Column(
        modifier = Modifier.width(IntrinsicSize.Min).heightIn(min = 48.dp)
          .selectable(selected = selected, onClick = { onSelected(index) }, role = Role.Tab),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Bottom,
      ) {
        Box(Modifier.padding(horizontal = OpenRunSpacing.Standard, vertical = OpenRunSpacing.Compact), contentAlignment = Alignment.Center) {
          Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) MaterialTheme.colorScheme.primary else LocalOpenRunPalette.current.muted,
          )
        }
        Box(
          Modifier.fillMaxWidth().height(2.dp).background(
            if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
          ),
        )
      }
    }
  }
}

/** Compact values reflow on narrow screens or large fonts; never hide recorded digits. */
@Composable
fun OpenRunCompactMetrics(
  metrics: List<OpenRunMetricItem>,
  modifier: Modifier = Modifier,
  columns: Int = 3,
) {
  val fontScale = LocalDensity.current.fontScale
  BoxWithConstraints(modifier.fillMaxWidth()) {
    val capacity = (maxWidth.value / (96f * fontScale)).toInt().coerceIn(1, columns.coerceAtLeast(1))
    val count = if (metrics.size == 4 && capacity == 3) 2 else capacity
    Column(verticalArrangement = Arrangement.spacedBy(OpenRunSpacing.Compact)) {
      metrics.chunked(count).forEach { row ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(OpenRunSpacing.Small)) {
          row.forEach { metric ->
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(OpenRunSpacing.Micro)) {
              Text(metric.value.replace(" min/km", " /km"),
                style = MaterialTheme.typography.titleLarge.copy(fontSize = 19.sp, lineHeight = 24.sp, fontFeatureSettings = "tnum"))
              Text(metric.label, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
          }
          repeat(count - row.size) { Box(Modifier.weight(1f)) }
        }
      }
    }
  }
}

@Composable
fun OpenRunSection(title: String, content: @Composable ColumnScope.() -> Unit) {
  Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(OpenRunSpacing.Small)) {
    OpenRunDivider()
    Text(title, style = MaterialTheme.typography.titleMedium)
    content()
  }
}

/** Shared KeyValueRow, retaining the existing public name and call sites. */
@Composable
fun OpenRunEvidenceRow(label: String, value: String, firstRow: Boolean = true) {
  val p = LocalOpenRunPalette.current
  BoxWithConstraints(Modifier.fillMaxWidth()) {
    val stack = maxWidth < 280.dp * LocalDensity.current.fontScale
    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
      if (!firstRow) HorizontalDivider(color = p.line)
      if (stack) {
        Text(label, style = OpenRunType.bodySm, color = p.muted)
        Text(value, style = OpenRunType.bodySm.copy(fontWeight = FontWeight.SemiBold), color = p.ink)
      } else Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(label, Modifier.weight(1f), style = OpenRunType.bodySm, color = p.muted)
        Text(value, Modifier.weight(1f), style = OpenRunType.bodySm.copy(fontWeight = FontWeight.SemiBold),
          color = p.ink, textAlign = TextAlign.End)
      }
    }
  }
}

@Composable
fun OpenRunDisclosure(title: String, content: @Composable ColumnScope.() -> Unit) {
  var expanded by rememberSaveable { mutableStateOf(false) }
  OpenRunDisclosureRow(title, expanded, onClick = { expanded = !expanded }, content = content)
}

@Composable
fun OpenRunPrimaryButton(
  text: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
) {
  Button(
    onClick = onClick,
    modifier = modifier.heightIn(min = 48.dp),
    enabled = enabled,
    shape = OpenRunShapeTokens.Button,
    elevation = null,
    colors = ButtonDefaults.buttonColors(
      containerColor = LocalOpenRunPalette.current.hero,
      contentColor = LocalOpenRunPalette.current.onHero,
    ),
  ) {
    Text(text, style = OpenRunType.title)
  }
}

@Composable
fun OpenRunSecondaryButton(
  text: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
) {
  OutlinedButton(
    onClick = onClick,
    modifier = modifier.heightIn(min = 48.dp),
    enabled = enabled,
    shape = OpenRunShapeTokens.Button,
    border = BorderStroke(1.5.dp, LocalOpenRunPalette.current.line),
    colors = ButtonDefaults.outlinedButtonColors(contentColor = LocalOpenRunPalette.current.ink),
  ) {
    Text(text, style = OpenRunType.title)
  }
}

@Composable
fun OpenRunInsightCard(
  title: String,
  body: String,
  modifier: Modifier = Modifier,
  icon: (@Composable () -> Unit)? = null,
) {
  OpenRunCard(modifier) {
    Row(horizontalArrangement = Arrangement.spacedBy(OpenRunSpacing.Compact)) {
      icon?.invoke()
      Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(OpenRunSpacing.Micro)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
      }
    }
  }
}
