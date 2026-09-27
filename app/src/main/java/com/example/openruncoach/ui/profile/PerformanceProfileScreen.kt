package com.example.openruncoach.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.openruncoach.LocalOpenRunCoachEnvironment
import com.example.openruncoach.domain.profile.AthletePerformanceProfile
import com.example.openruncoach.domain.profile.DistanceBenchmark
import com.example.openruncoach.domain.profile.ProfileBaseline
import com.example.openruncoach.domain.profile.ProfileConfidence
import com.example.openruncoach.domain.profile.StandardRunningDistance
import com.example.openruncoach.ui.common.formatDistance
import com.example.openruncoach.ui.common.formatDuration
import com.example.openruncoach.ui.common.formatPace
import com.example.openruncoach.ui.common.formatRunDate
import com.example.openruncoach.ui.components.ItemSpacing
import com.example.openruncoach.ui.components.ScreenPadding
import com.example.openruncoach.ui.components.SectionSpacing
import java.util.Locale

@Composable
fun PerformanceProfileScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
  val environment = LocalOpenRunCoachEnvironment.current
  val viewModel: PerformanceProfileViewModel = viewModel {
    PerformanceProfileViewModel(environment.profileRepository())
  }
  val state by viewModel.uiState.collectAsStateWithLifecycle()

  Column(
    modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(ScreenPadding),
    verticalArrangement = Arrangement.spacedBy(SectionSpacing),
  ) {
    TextButton(onClick = onBack) { Text("‹ Back") }
    Text("Performance Profile", style = MaterialTheme.typography.headlineMedium)
    when {
      state.isLoading -> Text("Building your performance profile…")
      state.profile == null -> Text(state.errorMessage ?: "Performance profile unavailable.")
      state.profile!!.totalCanonicalRuns == 0 -> EmptyProfile()
      else -> ProfileContent(state.profile!!, state.showExplanations, viewModel::toggleExplanations)
    }
    state.errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
  }
}

@Composable
private fun EmptyProfile() {
  ProfileCard("Not enough running data yet") {
    Text("Keep running and adding post-run feedback. OpenRunCoach will build your profile over time.")
  }
}

@Composable
private fun ProfileContent(profile: AthletePerformanceProfile, showExplanations: Boolean, onToggle: () -> Unit) {
  ProfileCard("Current Training") {
    Text("Last 7 days", style = MaterialTheme.typography.titleMedium)
    ProfileValue("Runs", profile.runsLast7Days.toString())
    ProfileValue("Distance", formatDistance(profile.distanceLast7DaysMeters))
    ProfileValue("Duration", formatDuration(profile.durationLast7DaysSeconds))
    Text("Last 28 days", style = MaterialTheme.typography.titleMedium)
    ProfileValue("Runs", profile.runsLast28Days.toString())
    ProfileValue("Distance", formatDistance(profile.distanceLast28DaysMeters))
    ProfileValue("Duration", formatDuration(profile.durationLast28DaysSeconds))
    Text("4-week average", style = MaterialTheme.typography.titleMedium)
    ProfileValue("Distance", "${formatDistance(profile.averageWeeklyDistanceLast4WeeksMeters)}/week")
    ProfileValue("Duration", "${formatDuration(profile.averageWeeklyDurationLast4WeeksSeconds)}/week")
    profile.longestRunLast28Days?.let { longest ->
      Text("Longest recent run", style = MaterialTheme.typography.titleMedium)
      ProfileValue("Distance", formatDistance(longest.distanceMeters))
      ProfileValue("Duration", formatDuration(longest.durationSeconds))
      ProfileValue("Date", formatRunDate(longest.date))
    }
  }

  ProfileCard("Performance Benchmarks") {
    StandardRunningDistance.entries.forEach { target ->
      BenchmarkRow(profile.performanceBenchmarks.first { it.targetDistance == target })
    }
  }

  ProfileCard("Easy Running") {
    BaselineValue("Typical pace", profile.typicalEasyPace) { formatPace(it) }
    BaselineValue("Typical HR", profile.typicalEasyHeartRate) { "${formatNumber(it)} bpm" }
    BaselineValue("Typical cadence", profile.typicalEasyCadence) { "${formatNumber(it)} spm" }
    Text("Easy runs require completed feedback, RPE ≤ 4, easy or appropriate cardio effort, and no significant pain.", style = MaterialTheme.typography.bodySmall)
    TextButton(onClick = onToggle) { Text(if (showExplanations) "Hide calculation details" else "Why these values?") }
    if (showExplanations) {
      listOfNotNull(profile.typicalEasyPace, profile.typicalEasyHeartRate, profile.typicalEasyCadence)
        .flatMap(ProfileBaseline::reasons).distinct().forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
    }
  }

  ProfileCard("Fitness") {
    ProfileValue("VO₂max", profile.vo2MaxLatest?.let(::formatNumber) ?: "Not available")
    ProfileValue("VO₂max trend", profile.vo2MaxTrend.displayName())
    ProfileValue("Easy pace trend", profile.recentPaceTrend.displayName())
    ProfileValue("Recent average HR", profile.recentAverageHeartRate?.let { "${formatNumber(it)} bpm" } ?: "Not available")
  }

  ProfileCard("Recent Feedback") {
    if (profile.feedback.feedbackCount == 0) {
      Text("No post-run feedback in the last 28 days.")
    } else {
      ProfileValue("Runs with feedback", profile.feedback.feedbackCount.toString())
      ProfileValue("Average RPE", profile.feedback.averageRpe?.let(::formatNumber) ?: "Not available")
      ProfileValue("Completed", profile.feedback.completedFraction?.let(::formatPercent) ?: "Not available")
      ProfileValue("Pain reported", profile.feedback.painFrequency?.let(::formatPercent) ?: "Not available")
      ProfileValue("Significant pain", profile.feedback.significantPainFrequency?.let(::formatPercent) ?: "Not available")
      ProfileValue("Average readiness", profile.feedback.averageReadiness?.let(::formatNumber) ?: "Not available")
    }
  }

  ProfileCard("Consistency") {
    Text(profile.consistency.displayName(), style = MaterialTheme.typography.titleMedium)
    Text("${profile.activeWeeksLast8Weeks} of the last 8 weeks active")
    ProfileValue("Runs per week", formatNumber(profile.averageRunsPerWeek))
    ProfileValue("Current weekly streak", "${profile.currentRunningStreakWeeks} weeks")
  }

  ProfileCard("Data Confidence") {
    Text(profile.profileConfidence.displayName(), style = MaterialTheme.typography.titleMedium)
    TextButton(onClick = onToggle) { Text(if (showExplanations) "Hide reasons" else "Why?") }
    if (showExplanations) {
      profile.confidenceReasons.forEach { Text("• $it") }
      profile.dataQualityNotes.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
    }
  }
}

@Composable
private fun BenchmarkRow(benchmark: DistanceBenchmark) {
  Text(benchmark.targetDistance.displayName, style = MaterialTheme.typography.titleMedium)
  val best = benchmark.personalBest
  if (best == null) {
    Text("No benchmark yet")
  } else {
    ProfileValue("Personal best", formatDuration(best.durationSeconds))
    ProfileValue("Pace", formatPace(best.paceSecondsPerKm))
    ProfileValue("Observed distance", formatDistance(best.actualDistanceMeters))
    ProfileValue("Date", formatRunDate(best.date))
    if (benchmark.mostRecent?.runId != best.runId) {
      ProfileValue("Most recent", formatDuration(benchmark.mostRecent!!.durationSeconds))
    }
  }
}

@Composable
private fun BaselineValue(label: String, baseline: ProfileBaseline?, format: (Double) -> String) {
  Text(label, style = MaterialTheme.typography.titleMedium)
  if (baseline == null) {
    Text("Not enough qualifying data")
  } else {
    Text(format(baseline.representative))
    Text("Observed range ${format(baseline.observedMinimum)} – ${format(baseline.observedMaximum)}")
    Text("Based on ${baseline.contributingRuns} runs · ${baseline.confidence.displayName()} confidence", style = MaterialTheme.typography.bodySmall)
  }
}

@Composable
private fun ProfileCard(title: String, content: @Composable () -> Unit) {
  Card(Modifier.fillMaxWidth()) {
    Column(Modifier.padding(ScreenPadding), verticalArrangement = Arrangement.spacedBy(ItemSpacing)) {
      Text(title, style = MaterialTheme.typography.titleLarge)
      content()
    }
  }
}

@Composable
private fun ProfileValue(label: String, value: String) {
  Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
    Text(label)
    Text(value)
  }
}

private fun ProfileConfidence.displayName() = name.lowercase().replace('_', ' ').replaceFirstChar(Char::titlecase)
private fun com.example.openruncoach.domain.profile.TrainingConsistency.displayName() = name.lowercase().replaceFirstChar(Char::titlecase)
private fun com.example.openruncoach.domain.profile.ValueTrend.displayName() = name.lowercase().replace('_', ' ').replaceFirstChar(Char::titlecase)
private fun com.example.openruncoach.domain.profile.FitnessTrend.displayName() = name.lowercase().replace('_', ' ').replaceFirstChar(Char::titlecase)
private fun com.example.openruncoach.domain.profile.PaceTrend.displayName() = name.lowercase().replace('_', ' ').replaceFirstChar(Char::titlecase)
private fun formatNumber(value: Double): String = "%.1f".format(Locale.getDefault(), value)
private fun formatPercent(value: Double): String = "%.0f%%".format(Locale.getDefault(), value * 100.0)
