package com.example.openruncoach.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.openruncoach.data.HealthConnectRepository
import com.example.openruncoach.data.RunFeedbackRepository
import com.example.openruncoach.data.RunRepository
import com.example.openruncoach.data.RunSettingsRepository
import com.example.openruncoach.data.TrainingPlanRepository
import com.example.openruncoach.data.WorkoutResponseRepository
import com.example.openruncoach.data.WorkoutExecutionRepository
import com.example.openruncoach.LocalOpenRunCoachEnvironment
import com.example.openruncoach.domain.coaching.CoachingEngine
import com.example.openruncoach.domain.plan.TrainingPlanGenerator
import com.example.openruncoach.ui.components.CoachSummaryCard
import com.example.openruncoach.ui.components.EmptyState
import com.example.openruncoach.ui.components.NextWorkoutCard
import com.example.openruncoach.ui.components.RunSummaryCard
import com.example.openruncoach.ui.components.ScreenPadding
import com.example.openruncoach.ui.components.SectionSpacing
import com.example.openruncoach.ui.components.WeekDaySelector

@Composable
fun HomeScreen(
  onRunClick: (String) -> Unit,
  onViewAnalysis: (String) -> Unit,
  onViewPlan: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val environment = LocalOpenRunCoachEnvironment.current
  val viewModel: HomeViewModel = viewModel {
    val database = environment.database
    val settingsRepository = RunSettingsRepository(context)
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
      WorkoutResponseRepository(database),
      CoachingEngine(environment.clock),
      environment.clock,
    )
  }
  val state by viewModel.uiState.collectAsStateWithLifecycle()

  Column(
    modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(ScreenPadding),
    verticalArrangement = Arrangement.spacedBy(SectionSpacing),
  ) {
    Text("OpenRunCoach", style = MaterialTheme.typography.headlineMedium)
    state.week?.let { week ->
      WeekDaySelector(week, viewModel::previousWeek, viewModel::nextWeek, viewModel::selectDate)
    }
    when {
      state.isLoading -> EmptyState("Loading", "Loading your runs…")
      state.errorMessage != null -> EmptyState("Runs unavailable", state.errorMessage!!)
      state.selectedRun == null -> EmptyState("No run recorded for this day.", "Select another day or sync runs from Settings.")
      else -> {
        val run = state.selectedRun!!
        RunSummaryCard(run, state.sourceCount, onClick = { onRunClick(run.healthConnectRecordId) })
        state.coachingDecision?.let { decision ->
          CoachSummaryCard(decision, onViewAnalysis = { onViewAnalysis(run.healthConnectRecordId) })
        }
      }
    }
    state.planSummary?.let { summary ->
      NextWorkoutCard(
        weekNumber = summary.weekNumber,
        workoutCount = summary.workoutCount,
        nextWorkoutName = summary.nextWorkoutName,
        nextWorkoutDurationSeconds = summary.nextWorkoutDurationSeconds,
        nextWorkoutDistanceMeters = summary.nextWorkoutDistanceMeters,
        onClick = onViewPlan,
      )
    }
  }
}
