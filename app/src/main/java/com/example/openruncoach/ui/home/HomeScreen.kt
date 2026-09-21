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
import com.example.openruncoach.data.local.OpenRunCoachDatabase
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
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val viewModel: HomeViewModel = viewModel {
    val database = OpenRunCoachDatabase.getInstance(context)
    val settingsRepository = RunSettingsRepository(context)
    HomeViewModel(
      RunRepository(database.runDao(), HealthConnectRepository(context), settingsRepository),
      RunFeedbackRepository(database),
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
    NextWorkoutCard()
  }
}
