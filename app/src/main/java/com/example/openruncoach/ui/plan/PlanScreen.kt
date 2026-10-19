package com.example.openruncoach.ui.plan

import android.app.DatePickerDialog
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.openruncoach.LocalOpenRunCoachEnvironment
import com.example.openruncoach.R
import com.example.openruncoach.data.*
import com.example.openruncoach.domain.goal.*
import com.example.openruncoach.domain.plan.*
import com.example.openruncoach.domain.workout.WorkoutPurpose
import com.example.openruncoach.domain.workout.WorkoutType
import com.example.openruncoach.domain.workout.displayName
import com.example.openruncoach.ui.common.formatActualDuration
import com.example.openruncoach.ui.common.formatDistance
import com.example.openruncoach.ui.common.formatDuration
import com.example.openruncoach.ui.common.formatUserDate
import com.example.openruncoach.ui.components.*
import com.example.openruncoach.ui.workout.listSummary
import com.example.openruncoach.ui.workout.planTitle
import com.example.openruncoach.ui.workout.planGoalTitle
import com.example.openruncoach.ui.workout.planDistanceLabel
import com.example.openruncoach.theme.OpenRunShapeTokens
import com.example.openruncoach.theme.OpenRunSpacing
import com.example.openruncoach.theme.WorkoutAccents
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanScreen(onOpenGoal: () -> Unit, onOpenWorkout: (String) -> Unit, modifier: Modifier = Modifier) {
  val environment = LocalOpenRunCoachEnvironment.current
  val viewModel: PlanViewModel = viewModel {
    val database = environment.database
    val profiles = environment.profileRepository()
    val completion = GoalCompletionRepository(database, environment.clock)
    PlanViewModel(
      TrainingPlanRepository(database, generator = TrainingPlanGenerator(environment.clock),
        completionRepository = completion, clock = environment.clock),
      TrainingGoalRepository(database, profiles, TrainingGoalFactory(environment.clock)),
      profiles, completion, environment.clock,
    )
  }
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  val today = LocalDate.now(environment.clock)
  val previous = state.previousPlan
  val plan = previous ?: state.plan
  val previousSummary = state.previousPlans.firstOrNull { it.id == previous?.id }
  val goalTitle = previousSummary?.let { planGoalTitle(it.distance, it.customDistanceMeters, it.goalType) }
    ?: if (previous != null) "Previous plan" else state.activeGoal?.planTitle()
  val selected = if (previous != null) state.previousWeekIndex else state.selectedWeekIndex
  BackHandler(enabled = previous != null) { viewModel.closePreviousPlan() }

  Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(ScreenPadding),
    verticalArrangement = Arrangement.spacedBy(OpenRunSpacing.Compact)) {
    if (previous != null) TextButton(onClick = viewModel::closePreviousPlan, contentPadding = PaddingValues(0.dp)) {
      Text("‹ Back to Plan")
    }
    Text(if (previous != null) "PREVIOUS PLAN" else "TRAINING PLAN",
      style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    when {
      state.isLoading -> {
        OpenRunCard(contentPadding = PaddingValues(16.dp)) {
          Text("Your training plan", style = MaterialTheme.typography.titleLarge)
          Text("Loading goal, weeks and workouts…", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
      }
      plan != null && (previous != null || state.activeGoal != null) -> {
        PlanHero(goalTitle ?: "Training plan", plan,
          if (previous != null) previous.completionMilestone else state.completionMilestone,
          selected, if (previous == null) viewModel::showCompletionScheduler else null)
        PlanWeekSelector(plan, selected, today,
          if (previous != null) viewModel::selectPreviousWeek else viewModel::selectWeek)
        val week = plan.weeks[selected.coerceIn(plan.weeks.indices)]
        WeeklySummary(week, today, state.workoutMetadata, provisional = previous == null)
        Text("Workouts", style = MaterialTheme.typography.titleMedium)
        week.workouts.sortedBy { it.workout.date }.forEach { entry ->
          key(entry.workout.id) {
            PlanWorkoutRow(entry, state.workoutMetadata[entry.workout.id], today, onOpenWorkout)
          }
        }
        key(plan.id, week.weekIndex) {
          WeekCoach(week, plan.blocks.firstOrNull { week.weekIndex in it.startWeek..it.endWeek })
        }
        if (previous == null) Text("Future workouts are provisional and may adjust as your training progresses.",
          style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OpenRunDisclosure("Plan details") {
          Text(plan.explanation, style = MaterialTheme.typography.bodyMedium)
          OpenRunEvidenceRow("Plan confidence", plan.confidence.displayName())
          OpenRunEvidenceRow("Start", formatUserDate(plan.startDate))
          plan.endDate?.let { OpenRunEvidenceRow("Plan horizon", formatUserDate(it)) }
          if (previous == null) {
            if (state.completionMilestone != null) TextButton(onClick = viewModel::showCompletionScheduler,
              enabled = !state.isGenerating) { Text("Schedule / reschedule Completion Run") }
            OutlinedButton(onClick = viewModel::generatePlan, enabled = !state.isGenerating) {
              Text(if (state.isGenerating) "Regenerating…" else "Regenerate Plan")
            }
            Text("Regeneration replaces the current plan; the earlier plan remains in Previous plans.",
              style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
          }
        }
      }
      state.activeGoal == null -> PlanNotice("Choose a training goal",
        "Set a goal to start your training plan.", "Choose Goal", onOpenGoal)
      else -> {
        PlanHero(requireNotNull(goalTitle), null, state.completionMilestone, 0, viewModel::showCompletionScheduler)
        if (state.completionMilestone != null) PlanNotice("Create your training plan", "Choose a planned Completion Run milestone to build your plan.",
          "Choose planned Completion Run", viewModel::showCompletionScheduler)
        else PlanNotice("Prepare your training plan", "Review your goal while Completion Run guidance is unavailable.", "Review Goal", onOpenGoal)
      }
    }
    TextButton(onClick = viewModel::showPlanHistory, contentPadding = PaddingValues(0.dp)) { Text("Previous plans ›") }
    state.errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
  }

  if (previous == null && state.showCompletionScheduler && state.activeGoal != null && state.completionMilestone != null) {
    ModalBottomSheet(onDismissRequest = viewModel::hideCompletionScheduler) {
      Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = ScreenPadding).padding(bottom = 24.dp)) {
        GoalMilestoneCard(state.activeGoal!!.planDistanceLabel(), state.completionMilestone!!,
          state.completionDateSuggestions, state.completionDateAssessment, true,
          viewModel::showCompletionScheduler, viewModel::scheduleCompletion, viewModel::postponeCompletion,
          state.isGenerating, today)
      }
    }
  }
  if (state.showPlanHistory) {
    ModalBottomSheet(onDismissRequest = viewModel::hidePlanHistory) {
      Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = ScreenPadding).padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(OpenRunSpacing.Small)) {
        Text("Previous plans", style = MaterialTheme.typography.titleLarge)
        when {
          state.historyLoading -> Text("Loading saved plans…", color = MaterialTheme.colorScheme.onSurfaceVariant)
          state.previousPlans.isEmpty() -> Text("No previous plans yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
          else -> state.previousPlans.forEach { saved ->
            OpenRunCard(modifier = Modifier.clickable(onClickLabel = "Open previous plan") { viewModel.openPreviousPlan(saved.id) },
              contentPadding = PaddingValues(12.dp), contentSpacing = 4.dp) {
              Text(planGoalTitle(saved.distance, saved.customDistanceMeters, saved.goalType), style = MaterialTheme.typography.titleMedium)
              Text("${saved.status.previousLabel()} · ${saved.weekCount} ${if (saved.weekCount == 1) "week" else "weeks"}",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
              Text(listOfNotNull(formatUserDate(saved.startDate), saved.endDate?.let { formatUserDate(it) }).joinToString(" – "),
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
          }
        }
      }
    }
  }
}

@Composable
private fun PlanHero(title: String, plan: TrainingPlan?, milestone: GoalCompletionMilestone?, selected: Int, onSchedule: (() -> Unit)?) {
  OpenRunCard(contentPadding = PaddingValues(12.dp), contentSpacing = 8.dp) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
      Text(title, style = MaterialTheme.typography.headlineSmall)
      if (plan != null) Text("Week ${selected.coerceIn(plan.weeks.indices) + 1} of ${plan.weeks.size}" +
        if (plan.isAssessmentPhase) " · Assessment Phase" else "",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
      if (milestone != null) {
        val scheduleModifier = if (onSchedule != null) Modifier.clickable(role = Role.Button,
          onClickLabel = if (milestone.scheduledDate != null) "Reschedule Completion Run" else "Schedule Completion Run",
          onClick = onSchedule) else Modifier
        HeroValue(milestone.scheduledDate?.format(DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())) ?: "Not scheduled",
          "Completion Run", Modifier.weight(1f).then(scheduleModifier).heightIn(min = 48.dp))
      }
      if (plan != null) {
        HeroValue("${plan.weeks.size} ${if (plan.weeks.size == 1) "week" else "weeks"}", "Plan horizon", Modifier.weight(1f))
        HeroValue(plan.confidence.displayName(), "Plan confidence", Modifier.weight(1f))
      }
    }
    milestone?.let {
      Text("Completion readiness · " + if (it.readiness == GoalCompletionReadiness.GOAL_COMPLETION_READY) "Ready to attempt" else "Building toward goal",
        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
  }
}

@Composable
private fun HeroValue(value: String, label: String, modifier: Modifier = Modifier) {
  Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
    Text(value, style = MaterialTheme.typography.titleSmall.copy(fontFeatureSettings = "tnum"))
    Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
  }
}

@Composable
private fun PlanWeekSelector(plan: TrainingPlan, selected: Int, today: LocalDate, onSelected: (Int) -> Unit) {
  val scroll = rememberLazyListState()
  LaunchedEffect(plan.id, selected) { scroll.animateScrollToItem(selected.coerceIn(plan.weeks.indices)) }
  Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
    IconButton(onClick = { onSelected(selected - 1) }, enabled = selected > 0) {
      Text("‹", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { contentDescription = "Previous week" })
    }
    LazyRow(Modifier.weight(1f).selectableGroup(), state = scroll, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
      itemsIndexed(plan.weeks, key = { _, week -> "${plan.id}:${week.weekIndex}" }) { index, week ->
        val active = selected == index
        val position = week.position(today)
        Surface(shape = OpenRunShapeTokens.Compact,
          color = if (active) MaterialTheme.colorScheme.surfaceContainerHigh else Color.Transparent) {
          Column(Modifier.widthIn(min = 104.dp).heightIn(min = 64.dp)
            .selectable(active, onClick = { onSelected(index) }, role = Role.Tab)
            .padding(horizontal = 12.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("Week ${week.weekIndex + 1}", style = MaterialTheme.typography.labelLarge,
              color = if (active || position == PlanWeekPosition.CURRENT) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
            Text(formatPlanWeekRange(week.startDate), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(position.label, style = MaterialTheme.typography.labelSmall,
              color = if (position == PlanWeekPosition.CURRENT) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
          }
        }
      }
    }
    IconButton(onClick = { onSelected(selected + 1) }, enabled = selected < plan.weeks.lastIndex) {
      Text("›", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { contentDescription = "Next week" })
    }
  }
}

@Composable
private fun WeeklySummary(week: TrainingWeek, today: LocalDate, metadata: Map<String, PlanWorkoutPresentation>, provisional: Boolean) {
  val summary = remember(week, today, metadata) { week.summary(today, metadata) }
  val actualDistance = summary.actualDistanceMeters
  val plannedDistance = summary.plannedDistanceMeters
  val actualDuration = summary.actualDurationSeconds
  val plannedDuration = summary.plannedDurationSeconds
  val metrics = buildList {
    if (plannedDistance != null) add(OpenRunMetricItem(
      if (summary.showActuals && actualDistance != null) "${formatDistance(actualDistance).removeSuffix(" km")} / ${formatDistance(plannedDistance)}"
      else formatDistance(plannedDistance),
      if (summary.showActuals && actualDistance != null) "Actual / planned distance" else "Planned distance"))
    else if (plannedDuration != null) add(OpenRunMetricItem(
      if (summary.showActuals && actualDuration != null) "${formatDuration(actualDuration)} / ${formatDuration(plannedDuration)}"
      else formatDuration(plannedDuration),
      if (summary.showActuals && actualDuration != null) "Actual / planned duration" else "Planned duration"))
    else if (summary.showActuals && actualDistance != null) add(OpenRunMetricItem(formatDistance(actualDistance), "Recorded distance"))
    add(OpenRunMetricItem(if (summary.showActuals) "${summary.completedSessions} / ${summary.sessionCount}" else summary.sessionCount.toString(),
      if (summary.showActuals) "Sessions completed" else "Planned sessions"))
  }
  OpenRunCard(contentPadding = PaddingValues(12.dp), contentSpacing = 8.dp) {
    Text(when (week.position(today)) {
      PlanWeekPosition.CURRENT -> "This week"
      PlanWeekPosition.PAST -> "Week ${week.weekIndex + 1} · " + if (summary.showActuals) "Recorded progress" else "Planned workouts"
      PlanWeekPosition.FUTURE -> "Week ${week.weekIndex + 1} · " + if (provisional) "Provisional" else "Planned"
    }, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    OpenRunCompactMetrics(metrics, columns = 2)
    if (summary.showActuals && actualDistance != null && plannedDistance != null && plannedDistance > 0) {
      val progress = (actualDistance / plannedDistance).toFloat().coerceIn(0f, 1f)
      Box(Modifier.fillMaxWidth().height(4.dp).background(MaterialTheme.colorScheme.surfaceContainerHighest, OpenRunShapeTokens.Compact)
        .semantics { progressBarRangeInfo = ProgressBarRangeInfo(progress, 0f..1f); contentDescription = "${formatDistance(actualDistance)} recorded of ${formatDistance(plannedDistance)} planned" }) {
        if (progress > 0) Box(Modifier.fillMaxWidth(progress).fillMaxHeight().background(MaterialTheme.colorScheme.primary.copy(alpha = 0.65f), OpenRunShapeTokens.Compact))
      }
    }
    Text(week.workouts.filter { it.workout.type != WorkoutType.REST }.map { it.workout.type.displayName().removeSuffix(" Run") }
      .distinct().joinToString(" / ").ifBlank { "Rest" }, style = MaterialTheme.typography.labelSmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant)
    if (summary.showActuals && actualDistance == null && plannedDistance != null) Text("Recorded distance is unavailable for one or more sessions.",
      style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlanWorkoutRow(entry: PlannedWorkoutEntry, metadata: PlanWorkoutPresentation?, today: LocalDate, onOpen: (String) -> Unit) {
  val workout = entry.workout
  val date = workout.date.format(DateTimeFormatter.ofPattern("EEE, d MMM", Locale.getDefault()))
  if (workout.type == WorkoutType.REST) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(onClickLabel = "Open rest day") { onOpen(workout.id) },
      horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
      Text(date, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
      Column(Modifier.weight(1f)) {
        Text(workout.name, style = MaterialTheme.typography.bodyMedium)
        entry.adjustmentReasons.forEach {
          Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
      }
      if (workout.date == today) Text("TODAY", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
    }
    return
  }
  val prescription = remember(workout) { workout.listSummary() }
  val actual = metadata?.actual
  val execution = entry.executionResult
  val actualDistance = if (actual != null) actual.distanceMeters else execution?.actualDistanceMeters
  val actualDuration = actual?.durationSeconds ?: execution?.actualDurationSeconds
  val recorded = actual != null || execution != null
  val milestone = workout.purpose == WorkoutPurpose.GOAL_COMPLETION
  OpenRunCard(modifier = Modifier.clickable(onClickLabel = "Open ${workout.name}") { onOpen(workout.id) },
    contentPadding = PaddingValues(0.dp), contentSpacing = 0.dp) {
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
      Box(Modifier.width(3.dp).fillMaxHeight().background(if (milestone) MaterialTheme.colorScheme.primary else WorkoutAccents.forType(workout.type)))
      Column(Modifier.weight(1f).padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (milestone) Text("COMPLETION RUN", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          Text(date, Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
          if (workout.date == today) Text("TODAY", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          Text(workout.name, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
          Icon(painterResource(R.drawable.ic_arrow_forward), contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(entry.statusLabel(metadata) + if (metadata?.adjusted == true) " · Adjusted" else "",
          style = MaterialTheme.typography.labelSmall,
          color = if (execution?.isCompleted == true) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
          val volume = prescription.repetitionLabel ?: prescription.distanceLabel ?: prescription.durationLabel
          volume?.let { PrescriptionMetric(
            if (prescription.repetitionLabel != null || prescription.distanceLabel != null) R.drawable.ic_run else R.drawable.ic_stopwatch,
            it + if (recorded) " planned" else "") }
          prescription.paceLabel?.let { PrescriptionMetric(R.drawable.ic_pace, it) }
          (prescription.rpeLabel ?: prescription.effortLabel)?.let {
            PrescriptionMetric(null, it, prescription.intensityBars ?: 0)
          }
        }
        if (prescription.structured) Text("Structured workout" + (if (prescription.repetitionLabel != null) prescription.distanceLabel?.let { " · $it total" }.orEmpty() else ""),
          style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (recorded) Text(listOfNotNull(actualDistance?.let { "${formatDistance(it)} actual" },
          actualDuration?.takeIf { it > 0 }?.let(::formatActualDuration)).joinToString(" · ").ifBlank { "Actual summary unavailable" },
          style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
        if (entry.generationReason.isNotBlank()) Text(concisePlanText(entry.generationReason), maxLines = 2, overflow = TextOverflow.Ellipsis,
          style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        entry.adjustmentReasons.forEach {
          Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
      }
    }
  }
}

@Composable
private fun PrescriptionMetric(icon: Int?, text: String, intensityBars: Int = 0) {
  Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
    if (icon != null) Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    else WorkoutIntensitySignal(intensityBars, MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f))
    Text(text, style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"))
  }
}

@Composable
private fun WeekCoach(week: TrainingWeek, block: TrainingBlock?) {
  OpenRunCard(contentPadding = PaddingValues(12.dp), contentSpacing = 8.dp) {
    Text("Coach", style = MaterialTheme.typography.titleMedium)
    Text(concisePlanText(week.explanation, 110), maxLines = 2, overflow = TextOverflow.Ellipsis,
      style = MaterialTheme.typography.bodyMedium)
    week.reasons.distinct().take(2).forEach {
      Text("• ${concisePlanText(it, 90)}", maxLines = 2, overflow = TextOverflow.Ellipsis,
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    OpenRunDisclosure("Why this week?") {
      Text(week.explanation, style = MaterialTheme.typography.bodyMedium)
      week.reasons.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
      block?.let {
        Text(it.type.displayName(), style = MaterialTheme.typography.labelLarge)
        Text(it.purpose, style = MaterialTheme.typography.bodyMedium)
        Text(it.progressionIntent, style = MaterialTheme.typography.bodyMedium)
      }
    }
  }
}

@Composable
private fun PlanNotice(title: String, message: String, actionLabel: String, onAction: () -> Unit) {
  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Text(title, style = MaterialTheme.typography.titleLarge)
    Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    TextButton(onClick = onAction) { Text(actionLabel) }
  }
}

private fun TrainingPlanStatus.previousLabel(): String = when (this) {
  TrainingPlanStatus.SUPERSEDED -> "Replaced plan"
  TrainingPlanStatus.COMPLETED -> "Completed plan"
  TrainingPlanStatus.ARCHIVED -> "Archived plan"
  TrainingPlanStatus.ACTIVE -> "Previous plan"
}

private fun Enum<*>.displayName(): String = name.lowercase().replace('_', ' ').replaceFirstChar(Char::titlecase)
private fun formatCompletionDate(date: LocalDate): String =
  "${date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())}, ${formatUserDate(date)}"

@Composable
private fun GoalMilestoneCard(
  distanceName: String,
  milestone: GoalCompletionMilestone,
  suggestions: List<com.example.openruncoach.domain.goal.CompletionDateSuggestion>,
  dateAssessment: CompletionDateAssessment?,
  showScheduler: Boolean,
  onShowScheduler: () -> Unit,
  onSelectDate: (LocalDate) -> Unit,
  onPostpone: () -> Unit,
  busy: Boolean,
  today: LocalDate,
) {
  val context = LocalContext.current
  OpenRunCard {
    Text("Goal Milestone", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
    when {
      milestone.scheduledDate != null -> {
        Text("Planned $distanceName Completion Run", style = MaterialTheme.typography.titleMedium)
        Text(formatCompletionDate(milestone.scheduledDate), style = MaterialTheme.typography.titleLarge)
        if (milestone.readiness == GoalCompletionReadiness.GOAL_COMPLETION_READY) {
          Text("You're ready to attempt your continuous $distanceName.")
        } else {
          Text("This is the milestone for the current training plan. Current performance evidence does not yet mark the attempt ready.")
        }
        Text("OpenRunCoach will adapt upcoming training toward this date; a qualifying observed run can complete the goal earlier.")
        dateAssessment?.let {
          Text("Date assessment: ${it.feasibility.name.lowercase().replace('_', ' ').replaceFirstChar(Char::titlecase)}")
          Text(it.explanation, style = MaterialTheme.typography.bodySmall)
        }
        milestone.reasons.lastOrNull { it.contains("postponement", ignoreCase = true) }?.let {
          Text(it, color = MaterialTheme.colorScheme.error)
        }
        if (!showScheduler) OutlinedButton(onClick = onShowScheduler, enabled = !busy) { Text("Reschedule") }
      }
      milestone.readiness == GoalCompletionReadiness.GOAL_COMPLETION_READY -> {
        Text("You're ready to attempt your continuous $distanceName.", style = MaterialTheme.typography.titleMedium)
        Text(if (milestone.promptDismissed) "Completion Run ready to schedule when you choose." else "$distanceName Completion Run · Ready to schedule")
        if (!showScheduler) Button(onClick = onShowScheduler, enabled = !busy) { Text("Choose Completion Run date") }
      }
      else -> {
        Text("Choose your planned $distanceName Completion Run", style = MaterialTheme.typography.titleMedium)
        Text("This sets a plan milestone; it does not mean you're currently ready. OpenRunCoach will adapt training as evidence improves.")
        if (!showScheduler) Button(onClick = onShowScheduler, enabled = !busy) { Text("Choose Completion Run date") }
      }
    }
    if (showScheduler) {
      Text("Choose your planned $distanceName Completion Run", style = MaterialTheme.typography.titleMedium)
      Text("This will be the milestone for the current training plan. Readiness is assessed separately from the chosen date.")
      suggestions.forEach { suggestion ->
        OutlinedButton(onClick = { onSelectDate(suggestion.date) }, enabled = !busy) {
          Column {
            Text(formatCompletionDate(suggestion.date))
            Text(
              (if (suggestion.recommended) "Recommended · " else "") + suggestion.reasons.joinToString(" · "),
              style = MaterialTheme.typography.bodySmall,
            )
          }
        }
      }
      OutlinedButton(
        onClick = {
          val initial = milestone.scheduledDate ?: suggestions.firstOrNull()?.date ?: today.plusDays(3)
          DatePickerDialog(
            context,
            { _, year, month, day -> onSelectDate(LocalDate.of(year, month + 1, day)) },
            initial.year,
            initial.monthValue - 1,
            initial.dayOfMonth,
          ).apply {
            datePicker.minDate = today.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
          }.show()
        },
        enabled = !busy,
      ) { Text("Choose another date") }
      TextButton(onClick = onPostpone, enabled = !busy) { Text("Not yet") }
    }
  }
}
