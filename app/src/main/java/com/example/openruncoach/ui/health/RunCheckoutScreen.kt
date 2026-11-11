package com.example.openruncoach.ui.health

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.openruncoach.LocalOpenRunCoachEnvironment
import com.example.openruncoach.domain.*
import com.example.openruncoach.theme.*
import com.example.openruncoach.ui.common.formatDistance
import com.example.openruncoach.ui.components.*
import java.time.Clock
import java.time.format.DateTimeFormatter
import java.util.Locale

/** A new layout over the same form, validation and save/update path as Run detail. */
@Composable
fun RunCheckoutScreen(runId: String, onBack: () -> Unit, modifier: Modifier = Modifier) {
  val viewModel = runDetailViewModel(runId)
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  LaunchedEffect(state.feedbackLoaded) { if (state.feedbackLoaded) viewModel.editFeedback() }
  RunCheckoutContent(state, LocalOpenRunCoachEnvironment.current.clock,
    onBack = onBack, onChange = viewModel::updateForm, onSave = viewModel::saveFeedback,
    onCancel = { viewModel.cancelFeedbackEdit(); onBack() }, modifier = modifier)
}

@Composable
internal fun RunCheckoutContent(state: RunDetailUiState, clock: Clock, onBack: () -> Unit = {},
  onChange: ((FeedbackForm) -> FeedbackForm) -> Unit = {}, onSave: () -> Unit = {}, onCancel: () -> Unit = {},
  modifier: Modifier = Modifier, scroll: ScrollState = rememberScrollState()) {
  val p = LocalOpenRunPalette.current
  val enabled = !state.isSaving
  CompositionLocalProvider(LocalContentColor provides p.ink) {
    Column(modifier.fillMaxSize().background(p.bg).imePadding().verticalScroll(scroll).testTag("run-checkout-scroll")) {
      val subtitle = state.run?.let { run -> listOfNotNull(runTitle(state),
        run.startTime.atZone(clock.zone).format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault())),
        run.distanceMeters?.let(::formatDistance)).joinToString(" · ") }
      OpenRunHero("How did it go?", backLabel = "Run", onBack = onBack, subtitle = subtitle, titleStyle = OpenRunType.subTitle)
      Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        OpenRunHeroBody(Modifier.widthIn(max = 600.dp).fillMaxWidth().padding(bottom = 24.dp)) {
          when {
            !state.runLoaded || !state.feedbackLoaded -> Text("Loading feedback…", style = OpenRunType.body)
            state.run == null -> OpenRunCard { Text("Run not found.", style = OpenRunType.body) }
            else -> {
              OpenRunCard {
                FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                  Text("How hard was this run?", style = OpenRunType.title)
                  Text(state.form.rpe.takeIf { it.isNotBlank() }?.let { "RPE $it of 10" } ?: "Choose RPE", style = OpenRunType.meta, color = p.muted)
                }
                NumberChoices(1..10, state.form.rpe, "rpe", enabled) { selected -> onChange { it.copy(rpe = selected) } }
                Text("1 is very easy. 10 is all out.", style = OpenRunType.meta, color = p.muted)
              }
              OpenRunCard {
                CheckoutChoices("Cardio", CardioEffort.entries.map { it to it.name.choiceLabel() }, state.form.cardioEffort, enabled) { selected -> onChange { it.copy(cardioEffort = selected) } }
                CheckoutChoices("Muscle fatigue", MuscleFatigue.entries.map { it to it.name.choiceLabel() }, state.form.muscleFatigue, enabled) { selected -> onChange { it.copy(muscleFatigue = selected) } }
                CheckoutChoices("Pain", PainLevel.entries.map { it to it.name.choiceLabel() }, state.form.painLevel, enabled) { selected -> onChange { it.copy(painLevel = selected) } }
                CheckoutChoices("Completion", CompletionStatus.entries.map { it to it.name.choiceLabel() }, state.form.completionStatus, enabled) { selected -> onChange { it.copy(completionStatus = selected) } }
              }
              OpenRunCard {
                FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                  Text("How ready did you feel?", style = OpenRunType.title)
                  Text("optional", style = OpenRunType.meta, color = p.muted)
                }
                NumberChoices(1..5, state.form.readiness, "readiness", enabled) { selected ->
                  onChange { it.copy(readiness = if (it.readiness == selected) "" else selected) }
                }
                Text("1 is drained. 5 is fresh.", style = OpenRunType.meta, color = p.muted)
                if (state.form.readiness.isNotBlank()) TextButton({ onChange { it.copy(readiness = "") } }, enabled = enabled) { Text("Clear readiness") }
              }
              OpenRunCard {
                Text("Notes", style = OpenRunType.title)
                OutlinedTextField(state.form.notes, { value -> onChange { it.copy(notes = value) } },
                  modifier = Modifier.fillMaxWidth().heightIn(min = 84.dp).testTag("checkout-notes")
                    .border(1.5.dp, p.line, RoundedCornerShape(14.dp)),
                  enabled = enabled, minLines = 3, shape = RoundedCornerShape(14.dp), textStyle = OpenRunType.bodySm,
                  colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = p.line.copy(alpha = 0f), unfocusedBorderColor = p.line.copy(alpha = 0f)))
              }
              if (state.isSaving) Text("Saving checkout…", style = OpenRunType.meta)
              if (state.saved) Text("Checkout saved.", style = OpenRunType.body, color = p.good)
              OpenRunPrimaryButton(if (state.feedback == null) "Save checkout" else "Update checkout", onSave,
                Modifier.fillMaxWidth(), enabled = enabled)
              TextButton(onCancel, Modifier.fillMaxWidth().heightIn(min = 48.dp), enabled = enabled) { Text("Cancel", style = OpenRunType.title, color = p.muted) }
            }
          }
          state.error?.let { Text(it, style = OpenRunType.bodySm, color = MaterialTheme.colorScheme.error) }
        }
      }
    }
  }
}

@Composable
private fun NumberChoices(range: IntRange, selected: String, tag: String, enabled: Boolean, onSelect: (String) -> Unit) {
  BoxWithConstraints(Modifier.fillMaxWidth().selectableGroup()) {
    val columns = (maxWidth / (56.dp * LocalDensity.current.fontScale)).toInt().coerceIn(1, 5)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
      range.toList().chunked(columns).forEach { row -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        row.forEach { n -> OpenRunOptionChip(n.toString(), selected == n.toString(), { onSelect(n.toString()) },
          Modifier.weight(1f).testTag("checkout-$tag-$n"), enabled = enabled) }
        repeat(columns-row.size) { Spacer(Modifier.weight(1f)) }
      } }
    }
  }
}

@Composable
private fun <T> CheckoutChoices(title: String, values: List<Pair<T, String>>, selected: T, enabled: Boolean, onSelect: (T) -> Unit) {
  Text(title, style = OpenRunType.title)
  FlowRow(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
    values.forEach { (value,label) -> OpenRunOptionChip(label,selected == value,{ onSelect(value) },enabled = enabled) }
  }
}

private fun String.choiceLabel() = lowercase().replace('_', ' ').replaceFirstChar(Char::titlecase)
