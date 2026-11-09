package com.example.openruncoach.ui.components

import android.content.res.Configuration
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.openruncoach.theme.*

/** Explicit preview fixtures only. Never called by a production screen or app route. */
@Preview(name = "Gallery · light", widthDp = 390, heightDp = 6000, uiMode = Configuration.UI_MODE_NIGHT_NO)
@Preview(name = "Gallery · dark", widthDp = 390, heightDp = 6000, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Gallery · light · 130%", widthDp = 390, heightDp = 6000, fontScale = 1.3f, uiMode = Configuration.UI_MODE_NIGHT_NO)
@Preview(name = "Gallery · dark · 130%", widthDp = 390, heightDp = 6000, fontScale = 1.3f, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Gallery · light · 200%", widthDp = 390, heightDp = 6000, fontScale = 2f, uiMode = Configuration.UI_MODE_NIGHT_NO)
@Preview(name = "Gallery · dark · 200%", widthDp = 390, heightDp = 6000, fontScale = 2f, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun OpenRunComponentsGallery(scrollState: ScrollState = rememberScrollState()) {
  OpenRunCoachTheme {
    val p = LocalOpenRunPalette.current
    Surface(color = p.bg) {
      Column(Modifier.verticalScroll(scrollState)) {
        OpenRunHero("Complete 10K", eyebrow = "PRIMITIVES · PREVIEW FIXTURES",
          subtitle = "Light and dark · bundled fonts", backLabel = "Gallery", onBack = {}) {
          OpenRunStatusChip("Week 2 of 8", tone = OpenRunStatusTone.ON_HERO)
          OpenRunMetric("8 km", "distance", onHero = true)
          OpenRunHeroButton("See workout", {})
        }
        OpenRunHeroBody {
          OpenRunSectionHeader("Controls")
          OpenRunCard {
            Text("Flat card · no border", style = OpenRunType.title)
            Text("Shared presentation components", style = OpenRunType.body)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
              OpenRunStatusChip("Neutral")
              OpenRunStatusChip("As planned", tone = OpenRunStatusTone.POSITIVE)
            }
            OpenRunPrimaryButton("Save checkout", {})
            OpenRunSecondaryButton("Choose another date", {})
            var selected by remember { mutableStateOf(true) }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
              OpenRunOptionChip("Easy", !selected, { selected = false })
              OpenRunOptionChip("Appropriate", selected, { selected = true })
              OpenRunOptionChip("Week 2", true, {}, accentSelected = true)
            }
            OpenRunEvidenceRow("Distance", "7.1 km")
            OpenRunEvidenceRow("Average pace", "6:58 /km", firstRow = false)
            OpenRunMetric("13.0", "km this week")
            OpenRunProgressBar(.71f, "Longest run: 7.1 of 10 km", animate = false)
          }
          OpenRunSectionHeader("Mini stat cards")
          BoxWithConstraints {
            val columns = if (maxWidth / 2 < 96.dp * LocalDensity.current.fontScale) 1 else 2
            val cards: List<@Composable (Modifier) -> Unit> = listOf(
              { m -> OpenRunMiniStatCard("Easy pace", "6:27", "/km", "13 s faster", m, good = true) {
                Sparkline(listOf(ChartPoint(1f), ChartPoint(2f), ChartPoint(2.2f), ChartPoint(3f)), "Easy pace improving")
              } },
              { m -> OpenRunMiniStatCard("Heart rate, easy", "146", "bpm", "4 bpm lower", m, good = true) },
            )
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
              cards.chunked(columns).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { row.forEach { card -> card(Modifier.weight(1f)) } }
              }
            }
          }
          OpenRunSectionHeader("List rows")
          OpenRunRunRow("Fri\n6 Nov", "Easy run", "5.01 km", "6:25 /km", dotColor = p.runEasy, onClick = {})
          OpenRunWorkoutRow("Mon", "Easy run", "5.02 km · 6:24 /km", done = true, onClick = {})
          OpenRunWorkoutRow("Sun", "Long run", "8 km · 6:45–7:30 /km", today = true, onClick = {})
          OpenRunWorkoutRow("Wed", "Recovery run", "3 km · 7:00–7:45 /km", future = true, onClick = {})
          OpenRunNextUpRow("MON", "9", "Easy run", "5 km · 6:00–6:45 /km", onClick = {})
          OpenRunCard {
            OpenRunSettingsRow("Health Connect", "Connected", firstRow = true, onClick = {})
            OpenRunSettingsRow("Performance profile", "Easy pace and evidence", onClick = {})
            OpenRunDisclosure("Why these values?") { Text("Evidence appears here when expanded.", style = OpenRunType.bodySm) }
          }
          OpenRunSectionHeader("Canvas charts")
          OpenRunCard {
            Text("This week", style = OpenRunType.title)
            WeekBars(listOf(
              ChartBar("M", 5f, state = BarState.Done), ChartBar("T", 0f, state = BarState.Rest),
              ChartBar("W", 3f, state = BarState.Done), ChartBar("T", 0f, state = BarState.Rest),
              ChartBar("F", 5f, state = BarState.Done), ChartBar("S", 0f, state = BarState.Rest),
              ChartBar("S", 8f, state = BarState.TodayPlanned)), "Three runs done; 8 km planned today", animate = false)
          }
          OpenRunCard {
            Text("Longest run, toward 10 km", style = OpenRunType.title)
            LongRunBars(listOf(ChartBar("W1", 7.1f, "7.1", BarState.Done), ChartBar("W2", 8f, "8.0", BarState.Current),
              ChartBar("W3", 8.5f, "8.5", BarState.Planned), ChartBar("W4", 9f, "9.0", BarState.Planned),
              ChartBar("W5", 9f, "9.0", BarState.Planned), ChartBar("W6", 9f, "9.0", BarState.Planned),
              ChartBar("Dec 12", 10f, "10", BarState.Goal)), "Longest run rises from 7.1 toward 10 km", animate = false)
          }
          OpenRunCard {
            Text("Weekly distance", style = OpenRunType.title)
            WeeklyDistanceBars(listOf(20f, 21f, 22f, 23f, 24f, 22f, 18f, 14f).mapIndexed { i, v ->
              ChartBar("W${i + 1}", v, v.toInt().toString(), if (i == 0) BarState.Done else if (i == 1) BarState.Current else BarState.Planned)
            }, "Weekly distance builds to 24 km then eases to 14 km", animate = false)
          }
          OpenRunCard {
            Text("Easy pace", style = OpenRunType.title)
            PaceLineChart(listOf(ChartPoint(400f, "6:40"), ChartPoint(396f, "6:36"), ChartPoint(390f, "6:30"), ChartPoint(387f, "6:27")),
              "Easy pace improves from 6:40 to 6:27 per km; faster is higher", good = true)
            Text("Higher means faster", style = OpenRunType.caption, color = p.muted)
          }
          OpenRunCard {
            Text("Pace with target range", style = OpenRunType.title)
            PaceLineChart(listOf(ChartPoint(460f), ChartPoint(445f), ChartPoint(null), ChartPoint(438f), ChartPoint(430f)),
              "Pace moves toward target; missing samples leave a gap", targetRange = 410f..460f)
          }
          OpenRunCard {
            Text("Heart rate", style = OpenRunType.title)
            HeartRateChart(listOf(ChartPoint(130f), ChartPoint(142f), ChartPoint(146f), ChartPoint(151f)), "Heart rate rises from 130 to 151 bpm")
          }
          OpenRunCard {
            Text("Splits", style = OpenRunType.title)
            SplitBars(listOf(SplitBar("1", 1f / 444, "7:24"), SplitBar("2", 1f / 434, "7:14"), SplitBar(".98", 1f / 430, "7:10")),
              "Each split gets faster: 7:24, 7:14, 7:10 per km")
          }
          OpenRunMiniStatCard("No cadence source", null)
          Text("End of gallery", style = OpenRunType.caption, modifier = Modifier.padding(bottom = 24.dp))
        }
      }
    }
  }
}
