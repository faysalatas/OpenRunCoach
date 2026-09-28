package com.example.openruncoach.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.openruncoach.LocalOpenRunCoachEnvironment
import com.example.openruncoach.data.HealthConnectRepository
import com.example.openruncoach.data.RunFeedbackRepository
import com.example.openruncoach.data.RunRepository
import com.example.openruncoach.data.RunSettingsRepository
import com.example.openruncoach.data.TrainingGoalRepository
import com.example.openruncoach.data.TrainingPlanRepository
import com.example.openruncoach.data.WorkoutExecutionRepository
import com.example.openruncoach.data.WorkoutResponseRepository
import com.example.openruncoach.domain.RunSummary
import com.example.openruncoach.domain.coaching.CoachingDecision
import com.example.openruncoach.domain.coaching.CoachingEngine
import com.example.openruncoach.domain.plan.TrainingPlanGenerator
import com.example.openruncoach.theme.OpenRunColors
import com.example.openruncoach.theme.OpenRunSpacing
import com.example.openruncoach.ui.common.coachingActionLabel
import com.example.openruncoach.ui.common.formatDistance
import com.example.openruncoach.ui.common.formatDuration
import com.example.openruncoach.ui.common.formatPace
import com.example.openruncoach.ui.common.formatUserDate
import com.example.openruncoach.ui.components.OpenRunCard
import com.example.openruncoach.ui.components.OpenRunInsightCard
import com.example.openruncoach.ui.components.OpenRunMetricItem
import com.example.openruncoach.ui.components.OpenRunSectionHeader
import com.example.openruncoach.ui.components.ScreenPadding
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun HomeScreen(
  onRunClick: (String) -> Unit,
  onViewAnalysis: (String) -> Unit,
  onViewPlan: () -> Unit,
  onViewGoal: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val environment = LocalOpenRunCoachEnvironment.current
  val viewModel: HomeViewModel = viewModel {
    val database = environment.database
    val settingsRepository = RunSettingsRepository(context)
    val profiles = environment.profileRepository()
    val completion = com.example.openruncoach.data.GoalCompletionRepository(database, environment.clock)
    HomeViewModel(
      RunRepository(database.runDao(), HealthConnectRepository(context), settingsRepository, clock = environment.clock),
      RunFeedbackRepository(database, environment.clock),
      TrainingPlanRepository(
        database,
        generator = TrainingPlanGenerator(environment.clock),
        completionRepository = completion,
        executionRepository = WorkoutExecutionRepository(database, environment.clock),
      ),
      TrainingGoalRepository(database, profiles),
      WorkoutResponseRepository(database),
      CoachingEngine(environment.clock),
      environment.clock,
    )
  }
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  val errorMessage = state.errorMessage

  Column(
    modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState())
      .padding(horizontal = ScreenPadding, vertical = OpenRunSpacing.Standard),
    verticalArrangement = Arrangement.spacedBy(OpenRunSpacing.Comfortable),
  ) {
    HomeHeader(state.greeting)

    when {
      state.isLoading -> Text(
        "Loading your training…",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      errorMessage != null -> OpenRunInsightCard(
        title = "Training data unavailable",
        body = errorMessage,
      )
      else -> HomeContent(
        state = state,
        onRunClick = onRunClick,
        onViewAnalysis = onViewAnalysis,
        onViewPlan = onViewPlan,
        onViewGoal = onViewGoal,
        onPreviousWeek = viewModel::previousWeek,
        onNextWeek = viewModel::nextWeek,
      )
    }
    Spacer(Modifier.size(OpenRunSpacing.Small))
  }
}

@Composable
private fun HomeHeader(greeting: String) {
  Column(verticalArrangement = Arrangement.spacedBy(OpenRunSpacing.Small)) {
    Text(
      "OPENRUNCOACH",
      style = MaterialTheme.typography.labelMedium.copy(
        fontWeight = FontWeight.Medium,
        letterSpacing = 2.sp,
      ),
      color = OpenRunColors.TextSecondary,
    )
    Text(greeting, style = MaterialTheme.typography.headlineMedium)
  }
}

@Composable
private fun HomeContent(
  state: HomeUiState,
  onRunClick: (String) -> Unit,
  onViewAnalysis: (String) -> Unit,
  onViewPlan: () -> Unit,
  onViewGoal: () -> Unit,
  onPreviousWeek: () -> Unit,
  onNextWeek: () -> Unit,
) {
  state.nextWorkout?.let { workout -> NextWorkoutHero(workout, onViewPlan) }

  state.week?.let { week -> CurrentWeekSection(week, onPreviousWeek, onNextWeek) }
  state.weeklyMetrics?.let { metrics -> WeeklyMetrics(metrics) }
  state.latestRun?.let { run ->
    LatestRunSection(run, state.coachingDecision, onRunClick, onViewAnalysis)
  }
  GoalPlanContext(state.activeGoal, state.hasActivePlan, onViewGoal, onViewPlan)
}

@Composable
private fun NextWorkoutHero(workout: HomeWorkoutSummary, onViewPlan: () -> Unit) {
  OpenRunCard(highlighted = true) {
    Text(
      if (workout.isToday) "Today's Workout" else "Next Workout",
      style = MaterialTheme.typography.labelLarge,
      color = MaterialTheme.colorScheme.primary,
    )
    Text(workout.name, style = MaterialTheme.typography.headlineSmall)
    Text(
      if (workout.isToday) "Today" else formatUserDate(workout.date),
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    if (workout.details.isNotEmpty()) {
      Text(workout.details.joinToString("  ·  "), style = MaterialTheme.typography.bodyLarge)
    }
    TextButton(onClick = onViewPlan, modifier = Modifier.align(Alignment.End)) { Text("View plan") }
  }
}

@Composable
private fun CurrentWeekSection(
  week: HomeWeekSummary,
  onPreviousWeek: () -> Unit,
  onNextWeek: () -> Unit,
) {
  Column(verticalArrangement = Arrangement.spacedBy(OpenRunSpacing.Small)) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
      Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(OpenRunSpacing.Micro)) {
        Text(
          if (week.isCurrentWeek) "This Week" else "Week",
          style = MaterialTheme.typography.titleLarge,
          color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
          week.dateRangeLabel,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      TextButton(
        onClick = onPreviousWeek,
        modifier = Modifier.semantics { contentDescription = "Previous week" },
      ) { Text("‹", style = MaterialTheme.typography.titleLarge) }
      TextButton(
        onClick = onNextWeek,
        modifier = Modifier.semantics { contentDescription = "Next week" },
      ) { Text("›", style = MaterialTheme.typography.titleLarge) }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
      Text(
        week.progressLabel,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
      week.days.forEach { day -> WeekDayIndicator(day) }
    }
  }
}

@Composable
private fun WeekDayIndicator(day: HomeWeekDay) {
  val description = buildString {
    append(day.date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault()))
    if (day.isToday) append(", today")
    when {
      day.hasRun -> append(", run recorded")
      day.hasPlannedWorkout -> append(", workout planned")
      else -> append(", no planned workout")
    }
  }
  Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(OpenRunSpacing.Micro)) {
    Surface(
      modifier = Modifier.size(24.dp).semantics { contentDescription = description },
      shape = CircleShape,
      color = when {
        day.hasRun -> MaterialTheme.colorScheme.primary
        day.hasPlannedWorkout -> MaterialTheme.colorScheme.surfaceContainerHighest
        else -> Color.Transparent
      },
      border = when {
        day.isToday -> BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
        day.hasPlannedWorkout -> BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.65f))
        else -> BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
      },
    ) {
      Box(contentAlignment = Alignment.Center) {
        Text(
          when {
            day.hasRun -> "✓"
            day.hasPlannedWorkout -> "•"
            else -> ""
          },
          style = MaterialTheme.typography.labelSmall,
          color = if (day.hasRun) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
    Text(
      day.dayLabel,
      style = MaterialTheme.typography.labelSmall,
      color = if (day.isToday) MaterialTheme.colorScheme.primary else OpenRunColors.TextMuted,
    )
  }
}

@Composable
private fun WeeklyMetrics(metrics: HomeWeeklyMetrics) {
  val values = buildList {
    add(OpenRunMetricItem(metrics.runCount.toString(), "Runs"))
    add(OpenRunMetricItem(formatDistance(metrics.totalDistanceMeters), "Total distance"))
    metrics.averagePaceSecondsPerKm?.let { add(OpenRunMetricItem(formatCompactPace(it), "Average pace")) }
  }
  CompactMetricRow(values)
}

@Composable
private fun LatestRunSection(
  run: RunSummary,
  decision: CoachingDecision?,
  onRunClick: (String) -> Unit,
  onViewAnalysis: (String) -> Unit,
) {
  Column(verticalArrangement = Arrangement.spacedBy(OpenRunSpacing.Small)) {
    OpenRunSectionHeader("Latest Run")
    OpenRunCard(
      contentPadding = PaddingValues(horizontal = OpenRunSpacing.Standard, vertical = OpenRunSpacing.Compact),
      contentSpacing = OpenRunSpacing.Small,
    ) {
      CompactMetricRow(
        listOf(
          OpenRunMetricItem(formatDistance(run.distanceMeters), "Distance"),
          OpenRunMetricItem(formatDuration(run.durationSeconds), "Duration"),
          OpenRunMetricItem(formatCompactPace(run.averagePaceSecondsPerKm), "Average pace"),
        ),
      )
      TextButton(
        onClick = { onRunClick(run.healthConnectRecordId) },
        modifier = Modifier.align(Alignment.End),
      ) { Text("View run") }
    }
    decision?.let { CoachContext(run, it, onViewAnalysis) }
  }
}

@Composable
private fun GoalPlanContext(
  goal: HomeGoalSummary?,
  hasActivePlan: Boolean,
  onViewGoal: () -> Unit,
  onViewPlan: () -> Unit,
) {
  Column(verticalArrangement = Arrangement.spacedBy(OpenRunSpacing.Small)) {
    goal?.let {
      Text("Current goal", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
      Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(it.label, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
        TextButton(onClick = onViewGoal) { Text("View goal ›") }
      }
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
      Text(
        if (hasActivePlan) "Active training plan" else "No active plan",
        Modifier.weight(1f),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      TextButton(onClick = onViewPlan) { Text("View plan") }
    }
  }
}

@Composable
private fun CoachContext(
  run: RunSummary,
  decision: CoachingDecision,
  onViewAnalysis: (String) -> Unit,
) {
  OpenRunCard(
    contentPadding = PaddingValues(horizontal = OpenRunSpacing.Standard, vertical = OpenRunSpacing.Compact),
    contentSpacing = OpenRunSpacing.Small,
  ) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
      Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(OpenRunSpacing.Micro)) {
        Text("Coach · ${coachingActionLabel(decision.action)}", style = MaterialTheme.typography.titleMedium)
        Text(
          decision.reasons.firstOrNull() ?: "Your latest run has been analyzed.",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      TextButton(onClick = { onViewAnalysis(run.healthConnectRecordId) }) { Text("View") }
    }
  }
}

@Composable
private fun CompactMetricRow(metrics: List<OpenRunMetricItem>) {
  Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(OpenRunSpacing.Small)) {
    metrics.forEach { metric ->
      Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(OpenRunSpacing.Micro)) {
        Text(
          metric.value,
          style = MaterialTheme.typography.titleLarge.copy(fontSize = 19.sp, lineHeight = 24.sp),
          maxLines = 1,
          softWrap = false,
          overflow = TextOverflow.Ellipsis,
        )
        Text(
          metric.label,
          style = MaterialTheme.typography.labelSmall,
          color = OpenRunColors.TextMuted,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
    }
  }
}

private fun formatCompactPace(paceSecondsPerKm: Double?): String =
  formatPace(paceSecondsPerKm).replace(" min/km", " /km")
