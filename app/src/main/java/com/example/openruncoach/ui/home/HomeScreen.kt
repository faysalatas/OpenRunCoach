package com.example.openruncoach.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
import com.example.openruncoach.domain.coaching.CoachingEngine
import com.example.openruncoach.domain.plan.TrainingPlanGenerator
import com.example.openruncoach.theme.OpenRunColors
import com.example.openruncoach.theme.OpenRunShapeTokens
import com.example.openruncoach.theme.OpenRunSpacing
import com.example.openruncoach.ui.common.formatDistance
import com.example.openruncoach.ui.common.formatPace
import com.example.openruncoach.ui.common.formatUserDate
import com.example.openruncoach.ui.components.OpenRunCard
import com.example.openruncoach.ui.components.WorkoutIntensitySignal
import com.example.openruncoach.ui.components.OpenRunInsightCard
import com.example.openruncoach.ui.components.OpenRunTabs
import com.example.openruncoach.ui.components.ScreenPadding
import com.example.openruncoach.ui.workout.heroImageResource
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

private enum class HomeTab { OVERVIEW, TRAINING, PROGRESS }

@Composable
fun HomeScreen(
  onViewPlan: () -> Unit,
  onOpenDetailedProgress: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val environment = LocalOpenRunCoachEnvironment.current
  val viewModel: HomeViewModel = viewModel {
    val database = environment.database
    val settingsRepository = RunSettingsRepository(context)
    val profiles = environment.profileRepository()
    val completion = com.example.openruncoach.data.GoalCompletionRepository(database, environment.clock)
    val executions = WorkoutExecutionRepository(database, environment.clock)
    HomeViewModel(
      RunRepository(database.runDao(), HealthConnectRepository(context), settingsRepository, clock = environment.clock),
      RunFeedbackRepository(database, environment.clock),
      TrainingPlanRepository(
        database,
        generator = TrainingPlanGenerator(environment.clock),
        completionRepository = completion,
        executionRepository = executions,
      ),
      TrainingGoalRepository(database, profiles),
      WorkoutResponseRepository(database),
      executions,
      CoachingEngine(environment.clock),
      environment.clock,
    )
  }
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  val errorMessage = state.errorMessage
  var selectedTab by rememberSaveable { mutableStateOf(HomeTab.OVERVIEW) }

  Column(
    modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState())
      .padding(horizontal = ScreenPadding, vertical = OpenRunSpacing.Compact),
    verticalArrangement = Arrangement.spacedBy(OpenRunSpacing.Compact),
  ) {
    HomeHeader(state.greeting, LocalDate.now(environment.clock))
    OpenRunTabs(
      tabs = listOf("Overview", "Training", "Progress"),
      selectedIndex = selectedTab.ordinal,
      onSelected = { index -> selectedTab = HomeTab.entries[index] },
      distributeEvenly = true,
    )

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
      else -> when (selectedTab) {
        HomeTab.OVERVIEW -> OverviewContent(
          state = state,
          onViewPlan = onViewPlan,
          onPreviousWeek = viewModel::previousWeek,
          onNextWeek = viewModel::nextWeek,
        )
        HomeTab.TRAINING -> TrainingContent(state, onViewPlan)
        HomeTab.PROGRESS -> ProgressContent(state.progress, onOpenDetailedProgress)
      }
    }
    Spacer(Modifier.size(OpenRunSpacing.Small))
  }
}

@Composable
private fun HomeHeader(greeting: String, today: LocalDate) {
  Column(
    modifier = Modifier.padding(top = OpenRunSpacing.Small, bottom = OpenRunSpacing.Micro),
    verticalArrangement = Arrangement.spacedBy(OpenRunSpacing.Standard),
  ) {
    Text(
      "OPENRUNCOACH",
      style = MaterialTheme.typography.labelMedium.copy(
        fontWeight = FontWeight.Medium,
        letterSpacing = 2.sp,
      ),
      color = OpenRunColors.TextSecondary,
    )
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
      Text(greeting, Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall)
      Text(
        today.format(DateTimeFormatter.ofPattern("MMM d", Locale.getDefault())),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

@Composable
private fun OverviewContent(
  state: HomeUiState,
  onViewPlan: () -> Unit,
  onPreviousWeek: () -> Unit,
  onNextWeek: () -> Unit,
) {
  WorkoutHero(state.nextWorkout, state.hasActivePlan, onViewPlan)
  state.week?.let { week -> CurrentWeekSection(week, onPreviousWeek, onNextWeek) }
  state.weeklyMetrics?.let { metrics -> WeeklyMetricCards(metrics) }
}

@Composable
private fun TrainingContent(state: HomeUiState, onViewPlan: () -> Unit) {
  Text("Training", style = MaterialTheme.typography.titleLarge)
  WorkoutHero(state.nextWorkout, state.hasActivePlan, onViewPlan)
  Column(verticalArrangement = Arrangement.spacedBy(OpenRunSpacing.Small)) {
    Text("Upcoming", style = MaterialTheme.typography.titleMedium)
    val following = state.upcomingWorkouts.drop(1)
    if (following.isEmpty()) {
      Text(
        if (state.hasActivePlan) "No additional workouts are currently scheduled."
        else "Create a plan when you are ready to schedule training.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    } else {
      following.forEach { workout -> UpcomingWorkoutRow(workout) }
    }
  }
  Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
    Text(
      if (state.hasActivePlan) "Active training plan" else "No active plan",
      Modifier.weight(1f),
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    TextButton(onClick = onViewPlan) { Text("Open full plan ›") }
  }
}

@Composable
private fun UpcomingWorkoutRow(workout: HomeWorkoutSummary) {
  Row(
    Modifier.fillMaxWidth().padding(vertical = OpenRunSpacing.Small),
    horizontalArrangement = Arrangement.spacedBy(OpenRunSpacing.Standard),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(
      workout.date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
      modifier = Modifier.weight(0.22f),
      style = MaterialTheme.typography.labelLarge,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Column(Modifier.weight(0.78f), verticalArrangement = Arrangement.spacedBy(OpenRunSpacing.Micro)) {
      Text(workout.name, style = MaterialTheme.typography.titleSmall)
      Text(
        (listOf(formatUserDate(workout.date)) + workout.details.take(1)).joinToString(" · "),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

@Composable
private fun ProgressContent(progress: HomeProgressSummary?, onViewProgress: () -> Unit) {
  Text("Progress", style = MaterialTheme.typography.titleLarge)
  Text("Last 4 weeks", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
  if (progress == null) {
    Text("Progress data is unavailable.", color = MaterialTheme.colorScheme.onSurfaceVariant)
    return
  }
  OpenRunCard(contentPadding = PaddingValues(OpenRunSpacing.Standard)) {
    Text("Easy pace", style = MaterialTheme.typography.titleMedium)
    Text("Comparable completed Easy runs", style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant)
    FourWeekPaceChart(progress.weeks)
    FourWeekValueLabels(progress.weeks, pace = true)
    val first = progress.firstEasyPaceSecondsPerKm
    val latest = progress.latestEasyPaceSecondsPerKm
    val change = progress.paceChangeSecondsPerKm
    if (first != null && latest != null && change != null) {
      Text(
        "${formatPaceClock(first)} → ${formatPaceClock(latest)} /km",
        style = MaterialTheme.typography.titleMedium,
      )
      Text(
        when {
          change > 0L -> "$change s/km faster"
          change < 0L -> "${abs(change)} s/km slower"
          else -> "No change in observed pace"
        },
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    } else {
      Text("Not enough comparable runs yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
  }
  OpenRunCard(contentPadding = PaddingValues(OpenRunSpacing.Standard)) {
    Text("Weekly distance", style = MaterialTheme.typography.titleMedium)
    Text("All recorded running activity", style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant)
    DistanceBars(progress.weeks.map(HomeProgressWeek::totalDistanceMeters))
    FourWeekValueLabels(progress.weeks, pace = false)
  }
  Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
    TextButton(onClick = onViewProgress) { Text("View detailed progress ›") }
  }
}

@Composable
private fun FourWeekPaceChart(weeks: List<HomeProgressWeek>) {
  val values = weeks.map(HomeProgressWeek::easyPaceSecondsPerKm)
  val known = values.mapNotNull { it }
  val accent = MaterialTheme.colorScheme.primary
  val grid = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
  Canvas(Modifier.fillMaxWidth().height(116.dp)) {
    if (known.isEmpty()) return@Canvas
    repeat(3) { index ->
      val y = size.height * index / 2f
      drawLine(grid, androidx.compose.ui.geometry.Offset(0f, y), androidx.compose.ui.geometry.Offset(size.width, y), 1.dp.toPx())
    }
    val minimum = known.minOrNull() ?: return@Canvas
    val maximum = known.maxOrNull() ?: return@Canvas
    val range = (maximum - minimum).takeIf { it > 0.0 }
    val horizontalInset = 4.dp.toPx()
    fun point(index: Int, value: Double) = androidx.compose.ui.geometry.Offset(
      x = if (values.size <= 1) size.width / 2f
      else horizontalInset + index * (size.width - horizontalInset * 2f) / (values.size - 1),
      // Lower min/km is faster, so it is intentionally drawn higher.
      y = range?.let { ((value - minimum) / it).toFloat() * size.height * 0.72f + size.height * 0.14f }
        ?: size.height / 2f,
    )
    val path = Path()
    var drawing = false
    values.forEachIndexed { index, value ->
      if (value == null) {
        drawing = false
      } else {
        val current = point(index, value)
        if (drawing) path.lineTo(current.x, current.y) else path.moveTo(current.x, current.y)
        drawing = true
        drawCircle(accent, 3.dp.toPx(), current)
      }
    }
    drawPath(path, accent, style = Stroke(2.dp.toPx()))
  }
}

@Composable
private fun FourWeekValueLabels(weeks: List<HomeProgressWeek>, pace: Boolean) {
  Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(OpenRunSpacing.Micro)) {
    weeks.forEach { week ->
      Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
          if (pace) week.easyPaceSecondsPerKm?.let(::formatPaceClock) ?: "—"
          else formatDistance(week.totalDistanceMeters),
          style = MaterialTheme.typography.labelMedium,
        )
        Text(
          week.startDate.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT).withLocale(Locale.getDefault())),
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
  }
}

@Composable
private fun WorkoutHero(workout: HomeWorkoutSummary?, hasActivePlan: Boolean, onViewPlan: () -> Unit) {
  Surface(
    modifier = Modifier.fillMaxWidth().clickable(onClickLabel = "View training plan", onClick = onViewPlan),
    shape = OpenRunShapeTokens.Card,
    color = MaterialTheme.colorScheme.surfaceContainerHigh,
  ) {
    Box(Modifier.fillMaxWidth().heightIn(min = 168.dp)) {
      workout?.type?.heroImageResource()?.let { imageResource ->
        Image(
          painter = painterResource(imageResource),
          contentDescription = null,
          modifier = Modifier.matchParentSize(),
          contentScale = ContentScale.Crop,
        )
        Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = 0.18f)))
        Box(Modifier.matchParentSize().background(Brush.horizontalGradient(
          0f to Color.Black.copy(alpha = 0.70f),
          0.68f to Color.Black.copy(alpha = 0.24f),
          1f to Color.Black.copy(alpha = 0.06f),
        )))
        Box(Modifier.matchParentSize().background(Brush.verticalGradient(
          0f to Color.Transparent,
          1f to Color.Black.copy(alpha = 0.60f),
        )))
      }
      Column(
        Modifier.matchParentSize().padding(
          start = OpenRunSpacing.Standard,
          top = OpenRunSpacing.Compact,
          end = OpenRunSpacing.Standard,
          bottom = OpenRunSpacing.Comfortable,
        ),
        verticalArrangement = Arrangement.spacedBy(OpenRunSpacing.Micro),
      ) {
        Text(
          when {
            workout?.isToday == true -> "Today's Workout"
            workout != null -> "Next Workout"
            else -> "Training"
          },
          style = MaterialTheme.typography.labelLarge,
          color = if (workout != null) Color.White.copy(alpha = 0.84f) else MaterialTheme.colorScheme.primary,
        )
        if (workout != null) {
          Text(
            workout.name,
            modifier = Modifier.padding(end = 48.dp),
            style = MaterialTheme.typography.headlineSmall,
            color = Color.White,
          )
          Text(
            if (workout.isToday) "Today" else formatUserDate(workout.date),
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.78f),
          )
          Spacer(Modifier.weight(1f))
          HeroMetadata(workout)
        } else {
          Text(
            if (hasActivePlan) "No upcoming workout" else "No active training plan",
            style = MaterialTheme.typography.headlineSmall,
          )
          Text(
            if (hasActivePlan) "Open your plan to review what comes next."
            else "Create a plan to get your next workout.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          Spacer(Modifier.weight(1f))
          Text("View plan ›", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
      }
      if (workout != null) {
        Box(
          modifier = Modifier.align(Alignment.CenterEnd).padding(end = OpenRunSpacing.Standard)
            .size(40.dp).background(Color.Black.copy(alpha = 0.22f), CircleShape),
          contentAlignment = Alignment.Center,
        ) {
          Icon(
            painter = painterResource(com.example.openruncoach.R.drawable.ic_arrow_forward),
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(20.dp),
          )
        }
      }
    }
  }
}

private enum class HeroMetadataKind { DISTANCE, PACE, INTENSITY }

@Composable
private fun HeroMetadata(workout: HomeWorkoutSummary) {
  val items = buildList {
    workout.distanceLabel?.let { add(HeroMetadataKind.DISTANCE to it) }
    workout.paceLabel?.let { add(HeroMetadataKind.PACE to it) }
    workout.intensity?.let { add(HeroMetadataKind.INTENSITY to it.label) }
  }
  if (items.isEmpty()) return
  Row(
    Modifier.fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    items.forEachIndexed { index, (kind, label) ->
      if (index > 0) {
        Box(Modifier.width(17.dp).height(20.dp), contentAlignment = Alignment.Center) {
          VerticalDivider(
            thickness = 1.dp,
            color = Color.White.copy(alpha = 0.28f),
          )
        }
      }
      Row(
        Modifier.weight(
          when {
            items.size < 3 -> 1f
            kind == HeroMetadataKind.PACE -> 1.35f
            kind == HeroMetadataKind.DISTANCE -> 0.9f
            else -> 1f
          },
        ),
        horizontalArrangement = Arrangement.spacedBy(OpenRunSpacing.Micro, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        when (kind) {
          HeroMetadataKind.DISTANCE -> Icon(
            painterResource(com.example.openruncoach.R.drawable.ic_run),
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.86f),
            modifier = Modifier.size(18.dp),
          )
          HeroMetadataKind.PACE -> Icon(
            painterResource(com.example.openruncoach.R.drawable.ic_stopwatch),
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.86f),
            modifier = Modifier.size(18.dp),
          )
          HeroMetadataKind.INTENSITY -> WorkoutIntensitySignal(workout.intensity?.activeBars ?: 0)
        }
        Text(label, style = MaterialTheme.typography.labelMedium, color = Color.White)
      }
    }
  }
}

@Composable
private fun CurrentWeekSection(
  week: HomeWeekSummary,
  onPreviousWeek: () -> Unit,
  onNextWeek: () -> Unit,
) {
  val defaultSelection = week.days.firstOrNull { it.isToday }
    ?: week.days.firstOrNull { it.hasRun || it.hasPlannedWorkout }
    ?: week.days.first()
  var selectedDate by rememberSaveable(week.dateRangeLabel) { mutableStateOf(defaultSelection.date.toString()) }
  val selectedDay = week.days.firstOrNull { it.date.toString() == selectedDate } ?: defaultSelection
  Column(
    modifier = Modifier.padding(horizontal = OpenRunSpacing.Micro, vertical = OpenRunSpacing.Micro),
    verticalArrangement = Arrangement.spacedBy(OpenRunSpacing.Small),
  ) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
      Text(
        if (week.isCurrentWeek) "This Week" else "Selected Week",
        Modifier.weight(1f),
        style = MaterialTheme.typography.titleMedium,
      )
      Text(
        week.progressLabel,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
      TextButton(
        onClick = onPreviousWeek,
        modifier = Modifier.semantics { contentDescription = "Previous week" },
      ) { Text("‹", style = MaterialTheme.typography.titleLarge) }
      Text(
        week.dateRangeLabel,
        Modifier.weight(1f),
        textAlign = TextAlign.Center,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      TextButton(
        onClick = onNextWeek,
        modifier = Modifier.semantics { contentDescription = "Next week" },
      ) { Text("›", style = MaterialTheme.typography.titleLarge) }
    }
    Row(Modifier.fillMaxWidth()) {
      week.days.forEach { day ->
        WeekDayIndicator(
          day = day,
          selected = day.date == selectedDay.date,
          onSelect = { selectedDate = day.date.toString() },
          modifier = Modifier.weight(1f),
        )
      }
    }
    Row(
      Modifier.fillMaxWidth().padding(top = OpenRunSpacing.Micro),
      horizontalArrangement = Arrangement.spacedBy(OpenRunSpacing.Small, Alignment.CenterHorizontally),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
        selectedDay.date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault()),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Text("·", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline)
      Text(
        selectedDay.workoutTypeLabel ?: "No run or workout",
        style = MaterialTheme.typography.labelLarge,
        color = if (selectedDay.workoutTypeLabel != null) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

@Composable
private fun WeekDayIndicator(
  day: HomeWeekDay,
  selected: Boolean,
  onSelect: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val description = buildString {
    append(day.date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault()))
    if (day.isToday) append(", today")
    when {
      day.hasRun -> append(", run recorded")
      day.hasPlannedWorkout -> append(", workout planned")
      else -> append(", no planned workout")
    }
  }
  Column(
    modifier = modifier.heightIn(min = 48.dp).clickable(onClickLabel = "Select ${day.dayLabel}", onClick = onSelect),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
  ) {
    Surface(
      modifier = Modifier.size(20.dp).semantics { contentDescription = description },
      shape = CircleShape,
      color = when {
        day.isToday && day.hasRun -> MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
        day.hasRun -> MaterialTheme.colorScheme.primary
        day.hasPlannedWorkout -> MaterialTheme.colorScheme.surfaceContainerHighest
        else -> Color.Transparent
      },
      border = when {
        selected -> BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
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
          color = when {
            day.isToday && day.hasRun -> MaterialTheme.colorScheme.primary
            day.hasRun -> MaterialTheme.colorScheme.onPrimary
            else -> MaterialTheme.colorScheme.onSurfaceVariant
          },
        )
      }
    }
    Text(
      day.dayLabel,
      style = MaterialTheme.typography.labelSmall,
      color = if (day.isToday || selected) MaterialTheme.colorScheme.primary else OpenRunColors.TextMuted,
    )
  }
}

@Composable
private fun WeeklyMetricCards(metrics: HomeWeeklyMetrics) {
  Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(OpenRunSpacing.Compact)) {
    WeeklyMetricCard(
      label = if (metrics.distanceIsPlanned) "Planned Distance" else "Total Distance",
      value = formatDistance(metrics.totalDistanceMeters),
      modifier = Modifier.weight(1f),
      supporting = formatDistanceComparison(metrics.distanceDifferenceMeters),
    ) { DistanceBars(metrics.days.map(HomeDailyMetric::distanceMeters)) }
    WeeklyMetricCard(
      label = "Avg. Pace",
      value = formatCompactPace(metrics.averagePaceSecondsPerKm),
      modifier = Modifier.weight(1f),
      supporting = if (metrics.runCount == 0) "No actual runs"
      else formatPaceComparison(metrics.paceDifferenceSecondsPerKm),
    ) { PaceSparkline(metrics.days.map(HomeDailyMetric::averagePaceSecondsPerKm)) }
  }
}

@Composable
private fun WeeklyMetricCard(
  label: String,
  value: String,
  modifier: Modifier,
  supporting: String? = null,
  chart: @Composable () -> Unit,
) {
  val cardHeight = (156f * LocalDensity.current.fontScale.coerceIn(1f, 1.5f)).dp
  OpenRunCard(
    modifier = modifier.height(cardHeight),
    contentPadding = PaddingValues(OpenRunSpacing.Compact),
    contentSpacing = OpenRunSpacing.Micro,
  ) {
    Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text(value, style = MaterialTheme.typography.titleLarge)
    Text(
      supporting.orEmpty(),
      minLines = 2,
      maxLines = 2,
      style = MaterialTheme.typography.labelSmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.weight(1f))
    chart()
  }
}

@Composable
private fun DistanceBars(values: List<Double?>) {
  val accent = MaterialTheme.colorScheme.primary
  Canvas(Modifier.fillMaxWidth().height(34.dp)) {
    if (values.isEmpty()) return@Canvas
    val maximum = values.mapNotNull { it }.maxOrNull()?.takeIf { it > 0.0 }
    val slotWidth = size.width / values.size
    val barWidth = slotWidth * 0.42f
    values.forEachIndexed { index, value ->
      if (value == null) return@forEachIndexed
      val fraction = if (maximum != null) (value / maximum).toFloat() else 0f
      val barHeight = size.height * fraction
      val left = index * slotWidth + (slotWidth - barWidth) / 2f
      drawRoundRect(
        color = accent.copy(alpha = 0.78f),
        topLeft = androidx.compose.ui.geometry.Offset(left, size.height - barHeight.coerceAtLeast(2.dp.toPx())),
        size = androidx.compose.ui.geometry.Size(barWidth, barHeight.coerceAtLeast(2.dp.toPx())),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx()),
      )
    }
  }
}

@Composable
private fun PaceSparkline(values: List<Double?>) {
  val accent = MaterialTheme.colorScheme.primary
  val known = values.mapNotNull { it }
  Canvas(Modifier.fillMaxWidth().height(34.dp)) {
    if (values.isEmpty() || known.isEmpty()) return@Canvas
    val minimum = known.minOrNull() ?: return@Canvas
    val maximum = known.maxOrNull() ?: return@Canvas
    val range = (maximum - minimum).takeIf { it > 0.0 }
    fun point(index: Int, value: Double) = androidx.compose.ui.geometry.Offset(
      x = if (values.size == 1) size.width / 2f else index * size.width / (values.size - 1),
      y = range?.let { ((value - minimum) / it).toFloat() * size.height * 0.72f + size.height * 0.14f }
        ?: size.height / 2f,
    )
    val path = Path()
    var drawing = false
    values.forEachIndexed { index, value ->
      if (value == null) {
        drawing = false
      } else {
        val current = point(index, value)
        if (drawing) path.lineTo(current.x, current.y) else path.moveTo(current.x, current.y)
        drawing = true
        drawCircle(accent, radius = 1.8.dp.toPx(), center = current)
      }
    }
    drawPath(path, accent, style = Stroke(width = 1.5.dp.toPx()))
  }
}

private fun formatCompactPace(paceSecondsPerKm: Double?): String =
  formatPace(paceSecondsPerKm).replace(" min/km", " /km")

private fun formatPaceComparison(differenceSecondsPerKm: Double?): String {
  differenceSecondsPerKm ?: return "Not enough 4-week history yet"
  val seconds = abs(differenceSecondsPerKm.roundToLong())
  return when {
    differenceSecondsPerKm > 0.5 -> "$seconds sec/km faster vs. last 4 weeks"
    differenceSecondsPerKm < -0.5 -> "$seconds sec/km slower vs. last 4 weeks"
    else -> "No pace change vs. last 4 weeks"
  }
}

private fun formatDistanceComparison(differenceMeters: Double?): String {
  differenceMeters ?: return "Not enough 4-week history yet"
  val differenceKm = differenceMeters / 1_000.0
  return when {
    differenceKm > 0.005 -> "+%.2f km vs. last 4 weeks".format(Locale.getDefault(), differenceKm)
    differenceKm < -0.005 -> "%.2f km vs. last 4 weeks".format(Locale.getDefault(), differenceKm)
    else -> "No distance change vs. last 4 weeks"
  }
}

private fun formatPaceClock(paceSecondsPerKm: Double): String {
  val seconds = paceSecondsPerKm.roundToLong()
  return "%d:%02d".format(Locale.getDefault(), seconds / 60, seconds % 60)
}
