package com.example.openruncoach.ui.home

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.openruncoach.LocalOpenRunCoachEnvironment
import com.example.openruncoach.data.*
import com.example.openruncoach.domain.coaching.CoachingEngine
import com.example.openruncoach.domain.plan.TrainingPlanGenerator
import com.example.openruncoach.theme.*
import com.example.openruncoach.ui.components.*

@Composable
fun HomeScreen(
  onViewPlan: () -> Unit,
  onOpenDetailedProgress: () -> Unit,
  modifier: Modifier = Modifier,
  onOpenSettings: () -> Unit = {},
  onOpenWorkout: (String) -> Unit = {},
  onOpenRun: (String) -> Unit = {},
  onOpenGoal: () -> Unit = onViewPlan,
  onOpenCheckout: (String) -> Unit = {},
  onRetry: () -> Unit = {},
) {
  val context = LocalContext.current
  val environment = LocalOpenRunCoachEnvironment.current
  val viewModel: HomeViewModel = viewModel {
    val database = environment.database
    val settingsRepository = RunSettingsRepository(context)
    val profiles = environment.profileRepository()
    val completion = GoalCompletionRepository(database, environment.clock)
    val executions = WorkoutExecutionRepository(database, environment.clock)
    HomeViewModel(
      RunRepository(database.runDao(), HealthConnectRepository(context), settingsRepository, clock = environment.clock,
        postRunReconciler = environment.postRunReconciler()),
      RunFeedbackRepository(database, environment.clock),
      TrainingPlanRepository(database, generator = TrainingPlanGenerator(environment.clock),
        completionRepository = completion, clock = environment.clock),
      TrainingGoalRepository(database, profiles),
      WorkoutResponseRepository(database), executions, CoachingEngine(environment.clock), environment.clock,
      profileRepository = profiles, routeRepository = RunRouteRepository(database.runRouteDao()),
      metricsRepository = RunMetricsRepository(database.runMetricsDao()),
    )
  }
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  HomeContent(homePresentation(state, environment.clock), HomeActions(
    onOpenSettings, onOpenWorkout, onOpenRun, onOpenGoal, onOpenCheckout, onOpenDetailedProgress, onRetry,
  ), modifier)
}

internal data class HomeActions(
  val settings: () -> Unit = {},
  val workout: (String) -> Unit = {},
  val run: (String) -> Unit = {},
  val goal: () -> Unit = {},
  val checkout: (String) -> Unit = {},
  val form: () -> Unit = {},
  val retry: () -> Unit = {},
)

@Composable
internal fun HomeContent(model: HomePresentation, actions: HomeActions = HomeActions(), modifier: Modifier = Modifier,
  scrollState: ScrollState = rememberScrollState(),
) {
  val p = LocalOpenRunPalette.current
  Column(modifier.fillMaxSize().background(p.bg).testTag("today-scroll").verticalScroll(scrollState),
    horizontalAlignment = Alignment.CenterHorizontally) {
    OpenRunHero(title = model.title, eyebrow = model.eyebrow, subtitle = model.subtitle,
      titleStyle = OpenRunType.heroDisplay,
      subtitleStyle = OpenRunType.bodySm.copy(fontSize = 19.sp, lineHeight = 27.sp),
      header = {
        Text(model.date, Modifier.weight(1f), style = OpenRunType.eyebrow.copy(fontSize = 13.sp, lineHeight = 19.sp), color = p.onHeroSecondary)
        OpenRunSettingsIcon(actions.settings)
      },
      eyebrowLeading = if (model.dayState == HomeDayState.AfterRun && !model.loading && model.error == null) {
        { Box(Modifier.size(24.dp).background(p.goodBg, CircleShape), contentAlignment = Alignment.Center) {
          OpenRunGlyph(Glyph.Check, p.good, Modifier.size(16.dp))
        } }
      } else null,
    ) {
      model.reason?.let { Text(it, Modifier.widthIn(max = 270.dp), style = OpenRunType.bodySm, color = p.onHeroSecondary) }
      if (model.heroMetrics.isNotEmpty()) HomeHeroMetrics(model.heroMetrics)
      if (model.dayState == HomeDayState.RestDay && !model.loading && model.error == null) model.nextUp.firstOrNull()?.let {
        Text("Next: ${it.weekday} ${it.day} · ${it.name}", style = OpenRunType.bodySm, color = p.onHeroSecondary)
      }
      when {
        model.runId != null -> OpenRunHeroButton("See run details", { actions.run(model.runId) })
        model.workoutId != null -> {
          OpenRunHeroButton(if (model.dayState == HomeDayState.RestDay) "See next workout" else "See workout",
            { actions.workout(model.workoutId) })
        }
      }
    }
    OpenRunHeroBody(Modifier.widthIn(max = 600.dp).fillMaxWidth()) {
      when {
        model.loading -> {
          Text("Loading your training…", style = OpenRunType.body, color = p.muted)
          repeat(2) { Box(Modifier.fillMaxWidth().height(120.dp).background(p.line, OpenRunShapeTokens.Card)
            .clearAndSetSemantics {}) }
        }
        model.error != null -> OpenRunCard {
          Text("Training data unavailable", style = OpenRunType.title)
          Text(model.error, style = OpenRunType.body)
          OpenRunSecondaryButton("Try again", actions.retry)
        }
        else -> {
          if (model.quickFacts.isNotEmpty()) HomeQuickFacts(model.quickFacts)
          if (model.chips.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            model.chips.forEach { OpenRunStatusChip(it.label, tone = if (it.good) OpenRunStatusTone.POSITIVE else OpenRunStatusTone.NEUTRAL) }
          }
          model.checkoutRunId?.let { runId -> OpenRunCard(Modifier.testTag("today-checkout")) {
            Text("How did it feel?", style = OpenRunType.cardTitleLg)
            Text("Your checkout helps plan what comes next.", style = OpenRunType.bodySm, color = p.muted)
            // A quick choice alone cannot supply the mandatory checkout answers. Never fabricate them.
            TextButton({ actions.checkout(runId) }, contentPadding = PaddingValues(0.dp)) {
              Text("Add details ›", style = OpenRunType.title, color = p.accentText)
            }
          } }
          model.coach?.let { text -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            OpenRunSectionHeader("YOUR COACH")
            Text(text, Modifier.padding(horizontal = 4.dp), style = OpenRunType.body, color = p.ink)
          } }
          model.goal?.let { HomeGoalCard(it, actions.goal) }
          model.week?.let { HomeWeekCard(it) }
          if (model.form.isNotEmpty()) {
            OpenRunSectionHeader("YOUR FORM · LAST 4 WEEKS", trailing = {
              TextButton(actions.form) { Text("Details", style = OpenRunType.caption, color = p.accentText) }
            })
            HomeFormGrid(model.form)
          }
          model.lastRun?.let { run ->
            OpenRunSectionHeader("LAST RUN")
            OpenRunRunRow(run.date, run.title, run.distance, run.pace,
              onClick = { actions.run(run.id) },
              note = if (run.asPlanned) "As planned" else null,
              thumbnail = if (run.route.isNotEmpty()) {
                { RunRouteCanvas(run.route, Modifier.size(64.dp, 48.dp), RunRouteCanvasStyle.COMPACT) }
              } else null)
          }
          if (model.nextUp.isNotEmpty()) {
            OpenRunSectionHeader("NEXT UP")
            model.nextUp.forEach { workout -> OpenRunNextUpRow(workout.weekday, workout.day, workout.name, workout.meta,
              onClick = workout.id?.let { id -> { actions.workout(id) } }) }
          }
        }
      }
      Spacer(Modifier.height(12.dp))
    }
  }
}

@Composable
private fun HomeHeroMetrics(metrics: List<HomeFact>) {
  BoxWithConstraints(Modifier.fillMaxWidth()) {
    val columns = (maxWidth / (105.dp * LocalDensity.current.fontScale)).toInt().coerceIn(1, 3)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
      metrics.chunked(columns).forEach { row -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        row.forEach { OpenRunMetric(it.value, it.label, Modifier.weight(1f), onHero = true) }
        repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
      } }
    }
  }
}

@Composable
private fun HomeQuickFacts(facts: List<HomeFact>) {
  val p = LocalOpenRunPalette.current
  OpenRunCard(Modifier.testTag("today-quick-facts"), contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)) {
    Row(Modifier.fillMaxWidth().drawBehind {
      for (index in 1 until facts.size) {
        val x = size.width * index / facts.size
        drawLine(p.line, Offset(x, 0f), Offset(x, size.height), 1.dp.toPx())
      }
    }) {
      facts.forEach { fact ->
        Column(Modifier.weight(1f).testTag("today-fact").padding(horizontal = 6.dp),
          verticalArrangement = Arrangement.spacedBy(3.dp)) {
          Text(fact.value, style = OpenRunType.cardTitleLg.copy(fontSize = 22.sp, lineHeight = 26.sp))
          Text(fact.label, style = OpenRunType.caption, color = p.muted)
        }
      }
    }
  }
}

@Composable
private fun HomeGoalCard(goal: HomeGoalPresentation, onClick: () -> Unit) {
  val p = LocalOpenRunPalette.current
  OpenRunCard(Modifier.testTag("today-goal").clickable(role = Role.Button, onClick = onClick)) {
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
      goal.progress?.let { fraction ->
        val reveal = chartReveal(true)
        Canvas(Modifier.size(52.dp).semantics {
          contentDescription = "${goal.title}, longest recorded run progress"
          progressBarRangeInfo = ProgressBarRangeInfo(fraction, 0f..1f)
        }) {
          val inset = 3.dp.toPx()
          val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
          drawArc(p.line, -90f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(6.dp.toPx()))
          drawArc(p.accent, -90f, 360f * fraction * reveal, false, Offset(inset, inset), arcSize,
            style = Stroke(6.dp.toPx(), cap = StrokeCap.Round))
        }
      }
      Column(Modifier.weight(1f)) {
        Text(goal.title, style = OpenRunType.cardTitleLg)
        goal.subtitle?.let { Text(it, style = OpenRunType.meta, color = p.muted) }
      }
    }
    goal.bars.forEachIndexed { index, bar ->
      FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(bar.label, style = OpenRunType.meta)
        Text(bar.value, style = OpenRunType.meta.copy(fontWeight = FontWeight.SemiBold))
      }
      OpenRunProgressBar(bar.fraction, "${bar.label}: ${bar.value}", useInk = index > 0)
    }
  }
}

@Composable
private fun HomeWeekCard(week: HomeWeekPresentation) {
  val p = LocalOpenRunPalette.current
  OpenRunCard(Modifier.testTag("today-week")) {
    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
      Text("This week", style = OpenRunType.title)
      Text(week.dateRange, style = OpenRunType.meta, color = p.muted)
    }
    if (week.distance != null) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), itemVerticalAlignment = Alignment.Bottom) {
      Text(week.distance, style = OpenRunType.bigNumber)
      Text(if (week.plannedDistance != null) "of ${week.plannedDistance} km" else "km done", style = OpenRunType.meta, color = p.muted)
    } else week.plannedDistance?.let { Text("$it km planned", style = OpenRunType.miniValue) }
    WeekBars(week.bars, week.description)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      week.chips.forEach { OpenRunStatusChip(it.label, tone = if (it.good) OpenRunStatusTone.POSITIVE else OpenRunStatusTone.NEUTRAL) }
    }
  }
}

@Composable
private fun HomeFormGrid(stats: List<HomeStat>) {
  BoxWithConstraints(Modifier.fillMaxWidth()) {
    val gridWidth = maxWidth
    val columns = if (gridWidth < 280.dp * LocalDensity.current.fontScale) 1 else 2
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
      stats.chunked(columns).forEach { row ->
        val density = LocalDensity.current
        var rowHeight by remember(row, gridWidth, density.fontScale) { mutableIntStateOf(0) }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
          row.forEach { stat ->
            OpenRunMiniStatCard(stat.title, stat.value, stat.unit, stat.delta,
              Modifier.weight(1f).heightIn(min = with(density) { rowHeight.toDp() }).onSizeChanged {
                if (it.height > rowHeight) rowHeight = it.height
              }, good = stat.good, sparkline = when {
                stat.readiness != null -> { { ReadinessSegments(stat.readiness) } }
                stat.points.any { it.value != null } -> { { Sparkline(stat.points,
                  "Weekly easy-run pace over the last four weeks. Higher means faster; gaps mean missing data.", good = stat.good) } }
                else -> null
              })
          }
          repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
        }
      }
    }
  }
}

@Composable
private fun ReadinessSegments(value: Float) {
  val p = LocalOpenRunPalette.current
  Canvas(Modifier.fillMaxWidth().height(8.dp).semantics {
    contentDescription = "Average reported readiness, $value out of 5"
    progressBarRangeInfo = ProgressBarRangeInfo(value, 0f..5f)
  }) {
    val gap = 4.dp.toPx()
    val width = (size.width - gap * 4) / 5
    repeat(5) { index ->
      val offset = Offset(index * (width + gap), 0f)
      drawRoundRect(p.line, offset, Size(width, size.height), CornerRadius(4.dp.toPx()))
      val fraction = (value - index).coerceIn(0f, 1f)
      if (fraction > 0) drawRoundRect(p.good, offset, Size(width * fraction, size.height), CornerRadius(4.dp.toPx()))
    }
  }
}
