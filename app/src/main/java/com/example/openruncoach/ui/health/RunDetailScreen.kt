package com.example.openruncoach.ui.health

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.openruncoach.data.HealthConnectRepository
import com.example.openruncoach.data.RunFeedbackRepository
import com.example.openruncoach.data.RunRepository
import com.example.openruncoach.data.RunMetricsRepository
import com.example.openruncoach.data.RunSettingsRepository
import com.example.openruncoach.data.local.OpenRunCoachDatabase
import com.example.openruncoach.domain.CardioEffort
import com.example.openruncoach.domain.CompletionStatus
import com.example.openruncoach.domain.MuscleFatigue
import com.example.openruncoach.domain.PainLevel
import com.example.openruncoach.domain.PossibleDuplicate
import com.example.openruncoach.domain.RunFeedback
import com.example.openruncoach.domain.RunSummary
import com.example.openruncoach.domain.displaySourceName
import com.example.openruncoach.domain.metrics.MetricConfidence
import com.example.openruncoach.domain.metrics.MetricSource
import com.example.openruncoach.domain.metrics.MetricValue
import com.example.openruncoach.domain.metrics.MetricVerification
import com.example.openruncoach.domain.metrics.RunMetrics
import com.example.openruncoach.ui.common.coachingActionLabel
import com.example.openruncoach.ui.common.formatDistance
import com.example.openruncoach.ui.common.formatDuration
import com.example.openruncoach.ui.common.formatPace
import com.example.openruncoach.ui.common.formatRunDate
import com.example.openruncoach.ui.components.ItemSpacing
import com.example.openruncoach.ui.components.ScreenPadding
import com.example.openruncoach.ui.components.SectionSpacing
import java.util.Locale

@Composable
fun RunDetailScreen(
  runId: String,
  onBack: () -> Unit,
  onViewAnalysis: (String) -> Unit,
  onInspectHealthData: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val viewModel: RunDetailViewModel = viewModel(key = runId) {
    val database = OpenRunCoachDatabase.getInstance(context)
    val settingsRepository = RunSettingsRepository(context)
    RunDetailViewModel(
      runId,
      RunRepository(database.runDao(), HealthConnectRepository(context), settingsRepository),
      RunFeedbackRepository(database),
      RunMetricsRepository(database.runMetricsDao()),
    )
  }
  val state by viewModel.uiState.collectAsStateWithLifecycle()

  Column(
    modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(ScreenPadding),
    verticalArrangement = Arrangement.spacedBy(SectionSpacing),
  ) {
    TextButton(onClick = onBack) { Text("‹ Back") }
    Text("Run Details", style = MaterialTheme.typography.headlineMedium)
    when {
      !state.runLoaded -> Text("Loading run…")
      state.run == null -> Text("Run not found.")
      else -> {
        RunCard(state.run!!)
        state.metrics?.let { RunMetricsCard(it, state.showMetricDetails, viewModel::toggleMetricDetails) }
        if (state.run!!.isCanonical) {
          Button(onClick = { onInspectHealthData(runId) }) { Text("Inspect Health Data") }
        }
        FeedbackCard(state, viewModel)
        state.feedback?.let { CoachCard(state) { onViewAnalysis(runId) } }
        SourceCard(state, viewModel::toggleSourceDetails)
      }
    }
    state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
  }
}

@Composable
private fun RunMetricsCard(metrics: RunMetrics, showDetails: Boolean, onToggleDetails: () -> Unit) {
  DetailCard("Run Metrics") {
    val heartRate = listOfNotNull(
      metrics.averageHeartRate?.value?.let { "${formatNumber(it)} bpm avg" },
      metrics.maxHeartRate?.value?.let { "${formatNumber(it)} max" },
    ).joinToString(" · ")
    if (heartRate.isNotBlank()) LabelValue("Heart rate", heartRate)
    metrics.averagePaceSecondsPerKm?.let { LabelValue("Pace", formatPace(it.value)) }
    val cadence = listOfNotNull(
      metrics.movingAverageCadence?.value?.let { "${formatNumber(it)} spm moving avg" },
      metrics.averageCadence?.value?.let { "${formatNumber(it)} spm session avg" },
    ).joinToString(" · ")
    if (cadence.isNotBlank()) LabelValue("Cadence", cadence)
    metrics.vo2Max?.let { LabelValue("VO₂ max", formatNumber(it.value)) }
    metrics.elevationGainMeters?.let { LabelValue("Elevation", "+${formatNumber(it.value)} m") }
    TextButton(onClick = onToggleDetails) { Text(if (showDetails) "Hide metric details" else "Metric details") }
    if (showDetails) MetricDetails(metrics)
  }
}

@Composable
private fun MetricDetails(metrics: RunMetrics) {
  val device = listOfNotNull(metrics.recordingDeviceManufacturer, metrics.recordingDeviceModel, metrics.recordingDeviceName)
    .distinct().joinToString(" ")
  listOf(
    Triple("Distance", metrics.distanceMeters, "m"),
    Triple("Average heart rate", metrics.averageHeartRate, "bpm"),
    Triple("Maximum heart rate", metrics.maxHeartRate, "bpm"),
    Triple("Average speed", metrics.averageSpeedMetersPerSecond, "m/s"),
    Triple("Cadence", metrics.averageCadence, "spm"),
    Triple("Moving cadence", metrics.movingAverageCadence, "spm"),
    Triple("VO₂ max", metrics.vo2Max, "ml/kg/min"),
    Triple("Elevation gain", metrics.elevationGainMeters, "m"),
  ).forEach { (label, metric, unit) ->
    if (metric != null) {
      Text(label, style = MaterialTheme.typography.titleSmall)
      LabelValue("Value", "${formatNumber(metric.value)} $unit")
      MetricProvenance(metric)
      if (metric.source == MetricSource.SAMSUNG_HEALTH && device.isNotBlank()) LabelValue("Device", device)
    }
  }
}

@Composable
private fun MetricProvenance(metric: MetricValue<out Number>) {
  LabelValue("Source", metric.source.displayName())
  LabelValue("Confidence", metric.confidence.displayName())
  when (metric.verification) {
    MetricVerification.VERIFIED -> LabelValue("Verification", "Verified against ${metric.secondarySource?.displayName() ?: "another provider"}")
    MetricVerification.CLOSE_MATCH -> LabelValue("Verification", "Close match with ${metric.secondarySource?.displayName() ?: "another provider"}")
    MetricVerification.CONFLICT -> LabelValue("Verification", "Provider conflict")
    MetricVerification.UNVERIFIED -> Unit
  }
  metric.verificationReason?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
}

@Composable
private fun RunCard(run: RunSummary) {
  DetailCard("Run") {
    Text(formatRunDate(run.startTime), style = MaterialTheme.typography.titleMedium)
    LabelValue("Distance", formatDistance(run.distanceMeters))
    LabelValue("Duration", formatDuration(run.durationSeconds))
  }
}

@Composable
private fun FeedbackCard(state: RunDetailUiState, viewModel: RunDetailViewModel) {
  DetailCard("Post-run Checkout") {
    when {
      !state.feedbackLoaded -> Text("Loading feedback…")
      state.isEditingFeedback -> FeedbackEditor(state, viewModel)
      state.feedback == null -> {
        Text("Add how the run felt to receive a coach analysis.")
        Button(onClick = viewModel::editFeedback) { Text("Complete checkout") }
      }
      else -> {
        FeedbackSummary(state.feedback)
        Button(onClick = viewModel::editFeedback) { Text("Edit checkout") }
      }
    }
    if (state.saved) Text("Checkout saved.", color = MaterialTheme.colorScheme.primary)
  }
}

@Composable
private fun FeedbackSummary(feedback: RunFeedback) {
  LabelValue("RPE", "${feedback.rpe}/10")
  LabelValue("Pain", feedback.painLevel.displayName())
  LabelValue("Muscle fatigue", feedback.muscleFatigue.displayName())
  LabelValue("Completion", feedback.completionStatus.displayName())
  feedback.readiness?.let { LabelValue("Readiness", "$it/5") }
  feedback.notes?.let { LabelValue("Notes", it) }
}

@Composable
private fun FeedbackEditor(state: RunDetailUiState, viewModel: RunDetailViewModel) {
  Text("How hard was this run?", style = MaterialTheme.typography.titleMedium)
  OutlinedTextField(
    value = state.form.rpe,
    onValueChange = { value -> viewModel.updateForm { it.copy(rpe = value) } },
    label = { Text("RPE: 1–10") },
    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
    singleLine = true,
  )
  ChoiceRow(
    "Cardio",
    listOf(CardioEffort.EASY to "Easy", CardioEffort.APPROPRIATE to "Appropriate", CardioEffort.HARD to "Hard", CardioEffort.LIMIT to "Limit"),
    state.form.cardioEffort,
  ) { selected -> viewModel.updateForm { it.copy(cardioEffort = selected) } }
  ChoiceRow(
    "Muscle fatigue",
    listOf(MuscleFatigue.NONE to "None", MuscleFatigue.MILD to "Mild", MuscleFatigue.MODERATE to "Moderate", MuscleFatigue.SEVERE to "Severe"),
    state.form.muscleFatigue,
  ) { selected -> viewModel.updateForm { it.copy(muscleFatigue = selected) } }
  ChoiceRow(
    "Pain",
    listOf(PainLevel.NONE to "None", PainLevel.MILD to "Mild", PainLevel.SIGNIFICANT to "Significant"),
    state.form.painLevel,
  ) { selected -> viewModel.updateForm { it.copy(painLevel = selected) } }
  ChoiceRow(
    "Completion",
    listOf(CompletionStatus.COMPLETED to "Completed", CompletionStatus.PARTIAL to "Partial", CompletionStatus.ABORTED to "Aborted"),
    state.form.completionStatus,
  ) { selected -> viewModel.updateForm { it.copy(completionStatus = selected) } }
  OutlinedTextField(
    value = state.form.readiness,
    onValueChange = { value -> viewModel.updateForm { it.copy(readiness = value) } },
    label = { Text("Readiness: 1–5 (optional)") },
    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
    singleLine = true,
  )
  OutlinedTextField(
    value = state.form.notes,
    onValueChange = { value -> viewModel.updateForm { it.copy(notes = value) } },
    label = { Text("Notes (optional)") },
    minLines = 3,
  )
  Row(horizontalArrangement = Arrangement.spacedBy(ItemSpacing)) {
    Button(onClick = viewModel::saveFeedback, enabled = !state.isSaving) {
      Text(if (state.feedback == null) "Save checkout" else "Update checkout")
    }
    TextButton(onClick = viewModel::cancelFeedbackEdit, enabled = !state.isSaving) { Text("Cancel") }
  }
  if (state.isSaving) Text("Saving checkout…")
}

@Composable
private fun CoachCard(state: RunDetailUiState, onViewAnalysis: () -> Unit) {
  DetailCard("Coach Analysis") {
    val decision = state.coachingDecision
    if (decision == null) {
      Text("Coach analysis is available for the latest canonical run.")
    } else {
      Text(coachingActionLabel(decision.action), style = MaterialTheme.typography.titleMedium)
      decision.reasons.take(3).forEach { Text("✓ $it") }
      TextButton(onClick = onViewAnalysis) { Text("View Analysis") }
    }
  }
}

@Composable
private fun SourceCard(state: RunDetailUiState, onToggleDetails: () -> Unit) {
  val run = state.run ?: return
  DetailCard("Source") {
    Text(run.displaySourceName, style = MaterialTheme.typography.titleMedium)
    if (state.possibleDuplicates.isNotEmpty()) Text("Possible duplicate", color = MaterialTheme.colorScheme.error)
    TextButton(onClick = onToggleDetails) {
      Text(if (state.showSourceDetails) "Hide duplicate/source details" else "Show duplicate/source details")
    }
    if (state.showSourceDetails) {
      if (run.sourcePackageName.isNotBlank()) LabelValue("Source package", run.sourcePackageName)
      val device = listOfNotNull(run.sourceDeviceManufacturer, run.sourceDeviceModel).joinToString(" ")
      if (device.isNotBlank()) LabelValue("Device", device)
      LabelValue("Record", if (run.isCanonical) "Canonical run" else "Non-canonical duplicate")
      run.canonicalSelectionReason?.let { LabelValue("Selection", it) }
      DuplicateDetails(state.possibleDuplicates)
      if (state.duplicateRecords.size > 1) {
        Text("Duplicate records", style = MaterialTheme.typography.titleMedium)
        state.duplicateRecords.forEach { record ->
          Text("${record.displaySourceName} · ${formatDistance(record.distanceMeters)} · ${if (record.isCanonical) "Canonical" else "Non-canonical"}")
        }
      }
    }
  }
}

@Composable
private fun DuplicateDetails(duplicates: List<PossibleDuplicate>) {
  duplicates.forEach { duplicate ->
    Text(
      "Similar start (${duplicate.startDifferenceSeconds}s), duration (${duplicate.durationDifferenceSeconds}s), " +
        "and distance (%.1f%%).".format(Locale.getDefault(), duplicate.distanceDifferencePercent)
    )
    if (duplicate.differentSourceApplications) Text("Different source: ${duplicate.otherSourceName}")
  }
}

@Composable
private fun DetailCard(title: String, content: @Composable () -> Unit) {
  Card(Modifier.fillMaxWidth()) {
    Column(Modifier.padding(ScreenPadding), verticalArrangement = Arrangement.spacedBy(ItemSpacing)) {
      Text(title, style = MaterialTheme.typography.titleLarge)
      content()
    }
  }
}

@Composable
private fun LabelValue(label: String, value: String) {
  Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
    Text(label, style = MaterialTheme.typography.bodyMedium)
    Text(value, style = MaterialTheme.typography.bodyMedium)
  }
}

@Composable
private fun <T> ChoiceRow(label: String, options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
  Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
    Text(label)
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(ItemSpacing)) {
      options.forEach { (value, text) ->
        FilterChip(selected = selected == value, onClick = { onSelect(value) }, label = { Text(text) })
      }
    }
  }
}

private fun PainLevel.displayName() = when (this) {
  PainLevel.NONE -> "No pain"
  PainLevel.MILD -> "Mild pain"
  PainLevel.SIGNIFICANT -> "Significant pain"
}
private fun MuscleFatigue.displayName() = name.lowercase().replaceFirstChar(Char::titlecase)
private fun CompletionStatus.displayName() = name.lowercase().replaceFirstChar(Char::titlecase)

private fun MetricSource.displayName() = when (this) {
  MetricSource.HEALTH_CONNECT -> "Health Connect"
  MetricSource.SAMSUNG_HEALTH -> "Samsung Health"
  MetricSource.DERIVED -> "Derived"
}

private fun MetricConfidence.displayName() = name.lowercase().replaceFirstChar(Char::titlecase)

private fun formatNumber(value: Double): String = "%.1f".format(Locale.getDefault(), value)
