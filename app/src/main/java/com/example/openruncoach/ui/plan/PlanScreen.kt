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
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.sp
import com.example.openruncoach.theme.LocalOpenRunPalette
import com.example.openruncoach.theme.OpenRunType
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
      profiles, completion, TrainingAvailabilityRepository(database), environment.clock,
    )
  }
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  BackHandler(enabled = state.previousPlan != null) { viewModel.closePreviousPlan() }
  PlanContent(state, LocalDate.now(environment.clock), PlanActions(
    onOpenGoal, onOpenWorkout, viewModel::selectWeek, viewModel::selectPreviousWeek,
    viewModel::showCompletionScheduler, viewModel::hideCompletionScheduler,
    viewModel::scheduleCompletion, viewModel::postponeCompletion, viewModel::generatePlan,
    viewModel::showPlanHistory, viewModel::hidePlanHistory, viewModel::openPreviousPlan, viewModel::closePreviousPlan,
  ), modifier)
}

internal data class PlanActions(
  val goal: () -> Unit = {}, val workout: (String) -> Unit = {},
  val selectWeek: (Int) -> Unit = {}, val selectPreviousWeek: (Int) -> Unit = {},
  val schedule: () -> Unit = {}, val dismissScheduler: () -> Unit = {},
  val selectDate: (LocalDate) -> Unit = {}, val postpone: () -> Unit = {}, val generate: () -> Unit = {},
  val history: () -> Unit = {}, val dismissHistory: () -> Unit = {},
  val previousPlan: (String) -> Unit = {}, val closePrevious: () -> Unit = {},
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PlanContent(state: PlanUiState, today: LocalDate, actions: PlanActions = PlanActions(),
  modifier: Modifier = Modifier, scroll: ScrollState = rememberScrollState(),
) {
  val p = LocalOpenRunPalette.current
  CompositionLocalProvider(LocalContentColor provides p.ink) {
  val previous = state.previousPlan
  val plan = previous ?: state.plan
  val saved = state.previousPlans.firstOrNull { it.id == previous?.id }
  val goal = state.activeGoal?.takeIf { previous == null || it.id == previous.goalId }
  val title = saved?.let { planGoalTitle(it.distance, it.customDistanceMeters, it.goalType) }
    ?: goal?.planTitle() ?: if (previous != null) "Previous plan" else "Your training plan"
  val selected = (if (previous != null) state.previousWeekIndex else state.selectedWeekIndex)
    .coerceIn(0, plan?.weeks?.lastIndex ?: 0)
  val milestone = if (previous != null) previous.completionMilestone else state.completionMilestone
  val distance = saved?.let { it.distance.meters ?: it.customDistanceMeters } ?: goal?.distanceMeters
  val subtitle = milestone?.scheduledDate?.let { date ->
    "Completion Run ${date.format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault()))}" +
      java.time.temporal.ChronoUnit.DAYS.between(today, date).takeIf { it > 0 }?.let {
        " · ${kotlin.math.ceil(it / 7.0).toInt()} weeks left"
      }.orEmpty()
  }
  Column(modifier.fillMaxSize().background(p.bg).testTag("plan-scroll").verticalScroll(scroll),
    horizontalAlignment = Alignment.CenterHorizontally) {
    OpenRunHero(title, eyebrow = if (previous == null) "TRAINING PLAN" else "PREVIOUS PLAN", subtitle = subtitle,
      subtitleStyle = OpenRunType.body,
      backLabel = if (previous != null) "Plan" else null, onBack = actions.closePrevious) {
      FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (plan != null) {
          OpenRunStatusChip("Week ${selected + 1} of ${plan.weeks.size}", tone = OpenRunStatusTone.ON_HERO)
          OpenRunStatusChip("Confidence ${plan.confidence.displayName().lowercase(Locale.getDefault())}", tone = OpenRunStatusTone.ON_HERO)
          if (plan.isAssessmentPhase) OpenRunStatusChip("Assessment Phase", tone = OpenRunStatusTone.ON_HERO)
        }
      }

    }
    OpenRunHeroBody(Modifier.widthIn(max = 600.dp).fillMaxWidth()) {
      when {
        state.isLoading -> Text("Loading goal, weeks and workouts…", style = OpenRunType.body, color = p.muted)
        plan != null && (previous != null || state.activeGoal != null) -> {
          val charts = remember(plan, today, state.workoutMetadata, distance, milestone) {
            planCharts(plan, today, state.workoutMetadata, distance, milestone?.scheduledDate)
          }
          if (charts.longRuns.isNotEmpty()) OpenRunCard {
            Text("Longest run" + distance?.let { ", toward ${formatDistance(it)}" }.orEmpty(), style = OpenRunType.title)
            LongRunBars(charts.longRuns, charts.longDescription)
          }
          if (charts.weeklyDistance.isNotEmpty()) OpenRunCard {
            Text("Weekly distance", style = OpenRunType.title)
            WeeklyDistanceBars(charts.weeklyDistance, charts.weeklyDescription)
            Text("Future weeks show planned distance.", style = OpenRunType.meta, color = p.muted)
          }
          if (previous == null) state.availabilityConflict?.let { Text(it, color = MaterialTheme.colorScheme.error) }
          val selector = if (previous != null) actions.selectPreviousWeek else actions.selectWeek
          val list = rememberLazyListState()
          LaunchedEffect(plan.id, selected) { list.animateScrollToItem(selected) }
          LazyRow(state = list, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().testTag("plan-week-selector").selectableGroup()) {
            itemsIndexed(plan.weeks, key = { _, week -> "${plan.id}:${week.weekIndex}" }) { index, week ->
              OpenRunOptionChip("Week ${week.weekIndex + 1}", index == selected, { selector(index) },
                accentSelected = true, role = Role.Tab)
            }
          }
          val week = plan.weeks[selected]
          val summary = week.summary(today, state.workoutMetadata)
          FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Week ${week.weekIndex + 1} · ${formatPlanWeekRange(week.startDate)}", style = OpenRunType.title)
            Text(when {
              summary.showActuals && summary.actualDistanceMeters != null && summary.plannedDistanceMeters != null ->
                "${formatDistance(summary.actualDistanceMeters)} of ${formatDistance(summary.plannedDistanceMeters)} planned"
              summary.showActuals && summary.actualDistanceMeters != null -> "${formatDistance(summary.actualDistanceMeters)} recorded"
              summary.plannedDistanceMeters != null -> "${formatDistance(summary.plannedDistanceMeters)} planned"
              summary.plannedDurationSeconds != null -> "${formatDuration(summary.plannedDurationSeconds)} planned"
              else -> "${summary.sessionCount} planned sessions"
            }, style = OpenRunType.meta, color = p.muted)
          }
          if (summary.showActuals && summary.actualDistanceMeters == null) Text(
            "Recorded distance is unavailable for one or more sessions.", style = OpenRunType.caption, color = p.muted)
          week.workouts.sortedBy { it.workout.date }.forEach { entry ->
            val actual = state.workoutMetadata[entry.workout.id]?.actual
            val execution = entry.executionResult
            val recorded = actual != null || execution != null
            val actualDistance = if (actual != null) actual.distanceMeters else execution?.actualDistanceMeters
            val actualDuration = actual?.durationSeconds ?: execution?.actualDurationSeconds
            val prescription = entry.workout.listSummary()
            val meta = if (recorded) listOfNotNull(
              actualDistance?.let { "${formatDistance(it)} actual" },
              actual?.averagePaceSecondsPerKm?.let { com.example.openruncoach.ui.common.formatPace(it).replace(" min/km", " /km") },
              actualDuration?.takeIf { it > 0 && actual?.averagePaceSecondsPerKm == null }?.let(::formatActualDuration),
            ).joinToString(" · ") else listOfNotNull(prescription.repetitionLabel,
              prescription.distanceLabel ?: prescription.durationLabel, prescription.paceLabel).joinToString(" · ")
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
              OpenRunWorkoutRow(entry.workout.date.format(DateTimeFormatter.ofPattern("EEE", Locale.getDefault())),
                entry.workout.name, meta.takeIf(String::isNotBlank),
                done = execution?.isCompleted == true, today = entry.workout.date == today,
                future = entry.workout.date > today, onClick = { actions.workout(entry.workout.id) })
              val metadata = state.workoutMetadata[entry.workout.id]
              if ((recorded && execution?.isCompleted != true) || metadata?.adjusted == true) Text(
                entry.statusLabel(metadata) + if (metadata?.adjusted == true) " · Adjusted" else "",
                style = OpenRunType.caption, color = p.muted)
            }
          }
          Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            OpenRunSectionHeader("WHY THIS WEEK")
            Text(week.explanation, style = OpenRunType.body)
          }
          if (previous == null) Text("Future workouts are provisional and may adjust as your training progresses.",
            style = OpenRunType.caption, color = p.muted)
          PlanDetails(plan, week, state, previous != null, actions)
        }
        state.activeGoal == null -> PlanNotice("Choose a training goal", "Set a goal to start your training plan.", "Choose Goal", actions.goal)
        else -> if (milestone != null) PlanNotice("Create your training plan", "Choose a planned Completion Run milestone to build your plan.",
          "Choose planned Completion Run", actions.schedule)
        else PlanNotice("Prepare your training plan", "Review your goal while Completion Run guidance is unavailable.", "Review Goal", actions.goal)
      }
      OpenRunSecondaryButton("Previous plans ›", actions.history)
      state.errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
      Spacer(Modifier.height(12.dp))
    }
  }
  if (previous == null && state.showCompletionScheduler && state.activeGoal != null && milestone != null) {
    ModalBottomSheet(onDismissRequest = actions.dismissScheduler, containerColor = p.bg, contentColor = p.ink, tonalElevation = 0.dp,
      shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp), scrimColor = MaterialTheme.colorScheme.scrim.copy(alpha = .32f)) {
      Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = ScreenPadding).padding(bottom = 24.dp)) {
        GoalMilestoneCard(state.activeGoal.planDistanceLabel(), milestone, state.completionDateSuggestions,
          state.completionDateAssessment, true, actions.schedule, actions.selectDate, actions.postpone, state.isGenerating, today)
        state.errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
      }
    }
  }
  if (state.showPlanHistory) {
    ModalBottomSheet(onDismissRequest = actions.dismissHistory, containerColor = p.bg, contentColor = p.ink, tonalElevation = 0.dp,
      shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp), scrimColor = MaterialTheme.colorScheme.scrim.copy(alpha = .32f)) {
      Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = ScreenPadding).padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Previous plans", style = OpenRunType.cardTitleLg)
        when {
          state.historyLoading -> Text("Loading saved plans…", color = p.muted)
          state.previousPlans.isEmpty() -> Text("No previous plans yet.", color = p.muted)
          else -> state.previousPlans.forEach { savedPlan ->
            OpenRunCard(Modifier.clickable(onClickLabel = "Open previous plan") { actions.previousPlan(savedPlan.id) }) {
              Text(planGoalTitle(savedPlan.distance, savedPlan.customDistanceMeters, savedPlan.goalType), style = OpenRunType.title)
              Text("${savedPlan.status.previousLabel()} · ${savedPlan.weekCount} ${if (savedPlan.weekCount == 1) "week" else "weeks"}", style = OpenRunType.meta, color = p.muted)
              Text(listOfNotNull(formatUserDate(savedPlan.startDate), savedPlan.endDate?.let(::formatUserDate)).joinToString(" – "), style = OpenRunType.caption, color = p.muted)
            }
          }
        }
      }
    }
  }
}
}

@Composable
private fun PlanDetails(plan: TrainingPlan, week: TrainingWeek, state: PlanUiState, previous: Boolean, actions: PlanActions) {
  OpenRunDisclosure("Plan details") {
    Text(plan.explanation)
    OpenRunEvidenceRow("Plan confidence", plan.confidence.displayName())
    OpenRunEvidenceRow("Start", formatUserDate(plan.startDate))
    plan.endDate?.let { OpenRunEvidenceRow("Plan horizon", formatUserDate(it)) }
    state.completionMilestone?.takeIf { !previous }?.let {
      Text("Completion readiness · " + if (it.readiness == GoalCompletionReadiness.GOAL_COMPLETION_READY) "Ready to attempt" else "Building toward goal")
    }
    week.reasons.forEach { Text(it) }
    plan.blocks.firstOrNull { week.weekIndex in it.startWeek..it.endWeek }?.let { Text(it.purpose); Text(it.progressionIntent) }
    week.workouts.forEach { entry ->
      Text(entry.workout.name, style = OpenRunType.title)
      Text(entry.generationReason)
      val prescription = entry.workout.listSummary()
      Text(listOfNotNull(prescription.repetitionLabel, prescription.distanceLabel, prescription.paceLabel,
        prescription.rpeLabel, prescription.durationLabel).joinToString(" · "))
      entry.adjustmentReasons.forEach { Text(it) }
    }
    if (!previous) {
      if (state.completionMilestone != null) OpenRunSecondaryButton("Schedule / reschedule Completion Run", actions.schedule, enabled = !state.isGenerating)
      OpenRunSecondaryButton(if (state.isGenerating) "Regenerating…" else "Regenerate Plan", actions.generate, enabled = !state.isGenerating)
      Text("Regeneration replaces the current plan; the earlier plan remains in Previous plans.", style = OpenRunType.caption)
    }
  }
}

@Composable
private fun PlanNotice(title: String, message: String, actionLabel: String, onAction: () -> Unit) {
  OpenRunCard { Text(title, style = OpenRunType.cardTitleLg); Text(message, style = OpenRunType.body)
    OpenRunPrimaryButton(actionLabel, onAction) }
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
        if (!showScheduler) OpenRunSecondaryButton("Reschedule", onShowScheduler, enabled = !busy)
      }
      milestone.readiness == GoalCompletionReadiness.GOAL_COMPLETION_READY -> {
        Text("You're ready to attempt your continuous $distanceName.", style = MaterialTheme.typography.titleMedium)
        Text(if (milestone.promptDismissed) "Completion Run ready to schedule when you choose." else "$distanceName Completion Run · Ready to schedule")
        if (!showScheduler) OpenRunPrimaryButton("Choose Completion Run date", onShowScheduler, enabled = !busy)
      }
      else -> {
        Text("Choose your planned $distanceName Completion Run", style = MaterialTheme.typography.titleMedium)
        Text("This sets a plan milestone; it does not mean you're currently ready. OpenRunCoach will adapt training as evidence improves.")
        if (!showScheduler) OpenRunPrimaryButton("Choose Completion Run date", onShowScheduler, enabled = !busy)
      }
    }
    if (showScheduler) {
      Text("Choose your planned $distanceName Completion Run", style = MaterialTheme.typography.titleMedium)
      Text("This will be the milestone for the current training plan. Readiness is assessed separately from the chosen date.")
      suggestions.forEach { suggestion ->
        OutlinedButton(onClick = { onSelectDate(suggestion.date) }, enabled = !busy,
          shape = OpenRunShapeTokens.Button, border = androidx.compose.foundation.BorderStroke(1.5.dp, LocalOpenRunPalette.current.line),
          colors = ButtonDefaults.outlinedButtonColors(contentColor = LocalOpenRunPalette.current.ink)) {
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
        enabled = !busy, shape = OpenRunShapeTokens.Button,
        border = androidx.compose.foundation.BorderStroke(1.5.dp, LocalOpenRunPalette.current.line),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = LocalOpenRunPalette.current.ink),
      ) { Text("Choose another date") }
      TextButton(onClick = onPostpone, enabled = !busy) { Text("Not yet") }
    }
  }
}
