package com.example.openruncoach.ui.plan

import android.app.DatePickerDialog
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.openruncoach.data.AthletePerformanceProfileRepository
import com.example.openruncoach.data.GoalCompletionRepository
import com.example.openruncoach.data.TrainingGoalRepository
import com.example.openruncoach.data.TrainingPlanRepository
import com.example.openruncoach.data.local.OpenRunCoachDatabase
import com.example.openruncoach.domain.goal.GoalType
import com.example.openruncoach.domain.goal.CompletionDateAssessment
import com.example.openruncoach.domain.goal.GoalCompletionMilestone
import com.example.openruncoach.domain.goal.GoalCompletionReadiness
import com.example.openruncoach.domain.plan.TrainingBlock
import com.example.openruncoach.domain.plan.TrainingPlan
import com.example.openruncoach.domain.plan.TrainingWeek
import com.example.openruncoach.domain.plan.isAssessmentPhase
import com.example.openruncoach.domain.workout.DistanceCompletion
import com.example.openruncoach.domain.workout.PaceTarget
import com.example.openruncoach.domain.workout.RpeTarget
import com.example.openruncoach.domain.workout.WorkoutBlock
import com.example.openruncoach.domain.workout.WorkoutDescriptionFormatter
import com.example.openruncoach.domain.workout.WorkoutEstimateCalculator
import com.example.openruncoach.domain.workout.WorkoutPurpose
import com.example.openruncoach.ui.common.formatDistance
import com.example.openruncoach.ui.common.formatDuration
import com.example.openruncoach.ui.common.formatUserDate
import com.example.openruncoach.ui.components.EmptyState
import com.example.openruncoach.ui.components.ItemSpacing
import com.example.openruncoach.ui.components.ScreenPadding
import com.example.openruncoach.ui.components.SectionSpacing
import java.time.format.TextStyle
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

@Composable
fun PlanScreen(
  onOpenGoal: () -> Unit,
  onOpenWorkout: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val viewModel: PlanViewModel = viewModel {
    val database = OpenRunCoachDatabase.getInstance(context)
    val profiles = AthletePerformanceProfileRepository(database)
    val completion = GoalCompletionRepository(database)
    PlanViewModel(
      TrainingPlanRepository(database, completionRepository = completion),
      TrainingGoalRepository(database, profiles),
      profiles,
      completion,
    )
  }
  val state by viewModel.uiState.collectAsStateWithLifecycle()

  Column(
    modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(ScreenPadding),
    verticalArrangement = Arrangement.spacedBy(SectionSpacing),
  ) {
    Text("Training Plan", style = MaterialTheme.typography.headlineMedium)
    state.activeGoal?.let { goal ->
      state.completionMilestone?.let { milestone ->
        GoalMilestoneCard(
          goal.distance.displayName,
          milestone,
          state.completionDateSuggestions,
          state.completionDateAssessment,
          state.showCompletionScheduler,
          viewModel::showCompletionScheduler,
          viewModel::scheduleCompletion,
          viewModel::postponeCompletion,
          state.isGenerating,
        )
      }
    }
    when {
      state.isLoading -> EmptyState("Loading", "Loading your training plan…")
      state.activeGoal == null -> EmptyState(
        "Choose a training goal",
        "A goal is required before OpenRunCoach can generate a base plan.",
        "Choose Goal",
        onOpenGoal,
      )
      state.plan == null -> {
        GoalCard(state.activeGoal!!.distance.displayName, state.activeGoal!!.goalType)
        EmptyState(
          "Create Training Plan",
          "Choose your planned ${state.activeGoal!!.distance.displayName} Completion Run. This will be the milestone for the current training plan, and OpenRunCoach will adapt training toward it.",
          "Choose planned Completion Run",
          viewModel::showCompletionScheduler,
        )
      }
      else -> PlanContent(
        state.plan!!,
        state.activeGoal!!.distance.displayName,
        state.activeGoal!!.goalType,
        state.selectedWeekIndex,
        viewModel::selectWeek,
        onOpenWorkout,
        viewModel::generatePlan,
        state.isGenerating,
      )
    }
    state.errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
  }
}

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
) {
  val context = LocalContext.current
  PlanCard("Goal Milestone") {
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
          val initial = milestone.scheduledDate ?: suggestions.firstOrNull()?.date ?: LocalDate.now().plusDays(3)
          DatePickerDialog(
            context,
            { _, year, month, day -> onSelectDate(LocalDate.of(year, month + 1, day)) },
            initial.year,
            initial.monthValue - 1,
            initial.dayOfMonth,
          ).apply {
            datePicker.minDate = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
          }.show()
        },
        enabled = !busy,
      ) { Text("Choose another date") }
      TextButton(onClick = onPostpone, enabled = !busy) { Text("Not yet") }
    }
  }
}

@Composable
private fun PlanContent(
  plan: TrainingPlan,
  goalDistance: String,
  goalType: GoalType,
  selectedWeekIndex: Int,
  onSelectWeek: (Int) -> Unit,
  onOpenWorkout: (String) -> Unit,
  onRegenerate: () -> Unit,
  isGenerating: Boolean,
) {
  PlanCard("Goal") {
    Text(goalDistance, style = MaterialTheme.typography.headlineSmall)
    Text(goalType.displayName())
    Text(plan.explanation, style = MaterialTheme.typography.bodySmall)
    Text("Plan confidence: ${plan.confidence.displayName()}", style = MaterialTheme.typography.labelLarge)
  }
  if (plan.isAssessmentPhase) {
    PlanCard("Assessment Phase") {
      Text("Build a reliable running baseline", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
      Text("Complete the assessment at a comfortable effort, then add post-run feedback. OpenRunCoach will recalculate your profile before creating a normal plan.")
    }
  }
  Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(ItemSpacing)) {
    plan.weeks.forEach { week ->
      FilterChip(
        selected = week.weekIndex == selectedWeekIndex,
        onClick = { onSelectWeek(week.weekIndex) },
        label = { Text("Week ${week.weekIndex + 1}") },
      )
    }
  }
  val week = plan.weeks[selectedWeekIndex.coerceIn(plan.weeks.indices)]
  val block = plan.blocks.firstOrNull { week.weekIndex in it.startWeek..it.endWeek }
  WeekContent(week, block, onOpenWorkout)
  OutlinedButton(onClick = onRegenerate, enabled = !isGenerating) {
    Text(if (isGenerating) "Regenerating…" else "Regenerate Plan")
  }
  Text("Regeneration explicitly supersedes the current plan; the earlier plan remains stored.", style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun WeekContent(week: TrainingWeek, block: TrainingBlock?, onOpenWorkout: (String) -> Unit) {
  Column(verticalArrangement = Arrangement.spacedBy(ItemSpacing)) {
    Text("Week ${week.weekIndex + 1}", style = MaterialTheme.typography.headlineSmall)
    Text(formatUserDate(week.startDate))
    block?.let { Text(it.type.displayName(), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleMedium) }
    Text(week.explanation, style = MaterialTheme.typography.titleMedium)
    Text("${week.workouts.size} workouts · ${week.loadClassification.displayName()}")
    week.workouts.forEach { entry ->
      val workout = entry.workout
      val estimate = WorkoutEstimateCalculator.calculate(workout)
      Card(Modifier.fillMaxWidth().clickable { onOpenWorkout(workout.id) }) {
        Column(Modifier.padding(ScreenPadding), verticalArrangement = Arrangement.spacedBy(4.dp)) {
          when (workout.purpose) {
            WorkoutPurpose.GOAL_COMPLETION -> Text("Goal Run", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
            WorkoutPurpose.ASSESSMENT -> Text("Assessment Run", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
            else -> Unit
          }
          Text(
            "${workout.date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())} · ${workout.name}",
            style = MaterialTheme.typography.titleMedium,
          )
          val segments = workout.blocks.flatMap { it.segments }
          val simpleSegment = segments.singleOrNull()
          val simpleDistance = simpleSegment?.completion as? DistanceCompletion
          val simpleTargets = simpleSegment?.let { listOf(it.primaryIntensity) + it.secondaryIntensities }.orEmpty()
          val simplePace = simpleTargets.filterIsInstance<PaceTarget>().firstOrNull()
          val simpleRpe = simpleTargets.filterIsInstance<RpeTarget>().firstOrNull()
          if (simpleDistance != null) Text(formatDistance(simpleDistance.meters), style = MaterialTheme.typography.titleLarge)
          else if (estimate.knownDistanceMeters > 0.0) Text(formatDistance(estimate.knownDistanceMeters))
          if (simplePace != null) {
            Text("Target pace: ${WorkoutDescriptionFormatter.paceRange(simplePace)}")
            if (simplePace.confidence == com.example.openruncoach.domain.workout.GuidanceConfidence.LOW) {
              Text("Pace confidence: Low", style = MaterialTheme.typography.bodySmall)
            }
          } else if (simpleDistance != null) Text("Pace guidance: Not enough data yet")
          expectedDurationText(estimate)?.let { Text("Expected: ~$it") }
          if (simpleRpe != null) Text("RPE ${simpleRpe.minimum}–${simpleRpe.maximum}")
          if (simpleSegment == null) MixedWorkoutSummary(workout.blocks)
          if (workout.purpose == WorkoutPurpose.GOAL_COMPLETION) {
            Text("This is the planned milestone your current plan is building toward. A qualifying observed run can complete the goal earlier.", style = MaterialTheme.typography.titleSmall)
          }
          if (workout.purpose == WorkoutPurpose.ASSESSMENT) {
            Text("Use a comfortable self-selected effort. No precise pace is required.", style = MaterialTheme.typography.titleSmall)
          }
          Text(entry.generationReason, style = MaterialTheme.typography.bodySmall)
        }
      }
    }
    PlanCard("Weekly target") {
      week.targetDistanceMeters?.let { PlanValue("Distance", "~${formatDistance(it)}") }
      week.targetDurationSeconds?.let { PlanValue("Duration", "~${formatDuration(it)}") }
    }
    PlanCard("Why this week?") {
      week.reasons.forEach { Text("• $it") }
    }
  }
}

@Composable
private fun MixedWorkoutSummary(blocks: List<WorkoutBlock>) {
  blocks.flatMap { it.segments }.firstOrNull { it.completion is DistanceCompletion }?.let { segment ->
    val pace = (listOf(segment.primaryIntensity) + segment.secondaryIntensities).filterIsInstance<PaceTarget>().firstOrNull()
    val paceText = pace?.let { " @ ${WorkoutDescriptionFormatter.paceRange(it)}" }.orEmpty()
    Text("${WorkoutDescriptionFormatter.completion(segment.completion)} ${segment.name.lowercase()}$paceText")
  }
  blocks.filterIsInstance<com.example.openruncoach.domain.workout.RepeatBlock>().firstOrNull()?.let { block ->
    val first = block.segments.firstOrNull() ?: return@let
    Text("+ ${block.repetitions} × ${WorkoutDescriptionFormatter.completion(first.completion)} ${first.name.lowercase()}")
  }
}

private fun expectedDurationText(estimate: com.example.openruncoach.domain.workout.WorkoutEstimate): String? {
  val minimum = estimate.estimatedMinimumDurationSeconds ?: return null
  val maximum = estimate.estimatedMaximumDurationSeconds ?: return null
  return WorkoutDescriptionFormatter.durationRange(minimum, maximum)
}

@Composable
private fun GoalCard(distance: String, type: GoalType) {
  PlanCard("Goal") {
    Text(distance, style = MaterialTheme.typography.headlineSmall)
    Text(type.displayName())
  }
}

@Composable
private fun PlanCard(title: String, content: @Composable () -> Unit) {
  Card(Modifier.fillMaxWidth()) {
    Column(Modifier.padding(ScreenPadding), verticalArrangement = Arrangement.spacedBy(ItemSpacing)) {
      Text(title, style = MaterialTheme.typography.titleLarge)
      content()
    }
  }
}

@Composable
private fun PlanValue(label: String, value: String) {
  Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
    Text(label)
    Text(value)
  }
}

private fun Enum<*>.displayName() = name.lowercase().replace('_', ' ').replaceFirstChar(Char::titlecase)

private fun GoalType.displayName() = when (this) {
  GoalType.COMPLETE_DISTANCE -> "Complete the distance"
  GoalType.IMPROVE_PERFORMANCE -> "Improve performance"
  GoalType.TARGET_TIME -> "Target time"
}

private fun formatCompletionDate(date: LocalDate): String =
  "${date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())}, ${formatUserDate(date)}"
