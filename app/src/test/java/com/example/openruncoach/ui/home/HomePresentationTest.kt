package com.example.openruncoach.ui.home

import com.example.openruncoach.domain.*
import com.example.openruncoach.domain.workout.*
import com.example.openruncoach.ui.components.BarState
import com.example.openruncoach.ui.workout.listSummary
import java.time.*
import java.util.Locale
import org.junit.Assert.*
import org.junit.Test

class HomePresentationTest {
  private val before = HomePreviewData.state(HomeDayState.BeforeRun)
  private val clock = HomePreviewData.clock(HomeDayState.BeforeRun)
  private val today = LocalDate.now(clock)
  private fun present(state: HomeUiState = before, at: Clock = clock) = homePresentation(state, at, Locale.US)

  @Test fun plannedUnfinishedWorkoutTodaySelectsBeforeRun() {
    val result = present()
    assertEquals(HomeDayState.BeforeRun, result.dayState)
    assertEquals("fixture-long", result.workoutId)
    assertTrue(result.quickFacts.isNotEmpty())
    assertNull(result.runId)
  }

  @Test fun completedRunTodayTakesPrecedenceOverAnotherUnfinishedWorkout() {
    val state = HomePreviewData.state(HomeDayState.AfterRun).copy(nextWorkout = before.nextWorkout)
    val result = present(state, HomePreviewData.clock(HomeDayState.AfterRun))
    assertEquals(HomeDayState.AfterRun, result.dayState)
    assertEquals("fixture-today", result.runId)
    assertTrue(result.quickFacts.size >= 2)
    assertFalse(result.quickFacts.any { it.label in listOf("km", "time", "/km", "distance", "pace") })
  }

  @Test fun restDayWithFutureWorkoutDoesNotUseFutureWorkoutAsTodaysPrescription() {
    val result = present(HomePreviewData.state(HomeDayState.RestDay), HomePreviewData.clock(HomeDayState.RestDay))
    assertEquals(HomeDayState.RestDay, result.dayState)
    assertEquals("Rest day", result.title)
    assertEquals(listOf("km done", "runs", "to go", "readiness"), result.quickFacts.map { it.label })
  }

  @Test fun absentPlanAndRunsProducesRestWithoutEmptyCardsOrSampleValues() {
    val result = present(HomeUiState(isLoading = false, todaySources = HomeTodaySources()))
    assertEquals(HomeDayState.RestDay, result.dayState)
    assertNull(result.goal)
    assertNull(result.week)
    assertNull(result.coach)
    assertNull(result.lastRun)
    assertNull(result.checkoutRunId)
    assertTrue(result.form.isEmpty())
    assertTrue(result.nextUp.isEmpty())
    assertTrue(result.heroMetrics.isEmpty())
  }

  @Test fun datesUseClockZoneAndRunCompletionDateIncludingOvernightRuns() {
    val at = Clock.fixed(Instant.parse("2026-11-08T23:30:00Z"), ZoneId.of("Europe/Berlin"))
    val run = before.latestRun!!.copy(startTime = Instant.parse("2026-11-08T22:30:00Z"), endTime = Instant.parse("2026-11-08T23:15:00Z"))
    val result = present(before.copy(latestRun = run), at)
    assertEquals(HomeDayState.AfterRun, result.dayState)
    assertTrue(result.date.startsWith("MON 9 NOV"))
  }

  @Test fun staleIsTodayFlagCannotTurnTomorrowsWorkoutIntoBeforeRun() {
    val state = before.copy(nextWorkout = before.nextWorkout!!.copy(date = today.plusDays(1), isToday = true))
    assertEquals(HomeDayState.RestDay, present(state).dayState)
  }

  @Test fun adjustedRestIsNotPresentedAsARun() {
    assertEquals(HomeDayState.RestDay, present(before.copy(nextWorkout = before.nextWorkout!!.copy(type = WorkoutType.REST))).dayState)
  }

  @Test fun ignoredDuplicateAndFutureRunsCannotSelectAfterRun() {
    val latest = before.latestRun!!.copy(startTime = clock.instant().minusSeconds(600), endTime = clock.instant().minusSeconds(10))
    listOf(latest.copy(isCanonical = false), latest.copy(relevance = RunRelevance.IGNORED), latest.copy(endTime = clock.instant().plusSeconds(10))).forEach {
      assertEquals(HomeDayState.BeforeRun, present(before.copy(latestRun = it)).dayState)
    }
  }

  @Test fun estimatedTimeHiddenWithoutPaceOrDurationSource() {
    val sources = before.todaySources!!
    val plan = sources.plan!!
    val weeks = plan.weeks.map { week -> week.copy(workouts = week.workouts.map { entry ->
      if (entry.workout.id != "fixture-long") entry else entry.copy(workout = HomePreviewData.workout("fixture-long", today, WorkoutType.LONG_RUN, 8_000.0, null))
    }) }
    val result = present(before.copy(nextWorkout = before.nextWorkout!!.copy(paceLabel = null), todaySources = sources.copy(plan = plan.copy(weeks = weeks))))
    assertFalse(result.quickFacts.any { it.label == "est. time" })
    assertFalse(result.subtitle.orEmpty().contains("/km"))
    assertTrue(result.quickFacts.any { it.label == "distance" })
  }

  @Test fun previousRunDifferenceRequiresAuthoritativeSameTypeAndKnownDistance() {
    val result = present()
    assertTrue(result.quickFacts.any { it.value == "+1 km" })
    val sources = before.todaySources!!
    assertFalse(present(before.copy(todaySources = sources.copy(completedWorkoutTypesByRunId = emptyMap())))
      .quickFacts.any { it.label.startsWith("vs last") })
    val unknown = sources.canonicalRuns.map { if (it.healthConnectRecordId == "fixture-long-previous") it.copy(distanceMeters = null) else it }
    assertFalse(present(before.copy(todaySources = sources.copy(canonicalRuns = unknown)))
      .quickFacts.any { it.label.startsWith("vs last") })
  }

  @Test fun missingPrescribedEffortIsHidden() {
    val sources = before.todaySources!!
    val plan = sources.plan!!
    val weeks = plan.weeks.map { week -> week.copy(workouts = week.workouts.map { entry ->
      if (entry.workout.id != "fixture-long") entry else entry.copy(workout = HomePreviewData.workout("fixture-long", today, WorkoutType.LONG_RUN, rpe = null))
    }) }
    assertFalse(present(before.copy(todaySources = sources.copy(plan = plan.copy(weeks = weeks))))
      .quickFacts.any { it.label == "effort /10" })
  }

  @Test fun structuredTargetsRemainSegmentsRatherThanWholeRunPace() {
    val sources = before.todaySources!!
    val plan = sources.plan!!
    val work = WorkoutSegment("repeat-work", 0, SegmentCategory.WORK, "Work", DistanceCompletion(400.0), PaceTarget(300.0, 330.0))
    val recovery = WorkoutSegment("repeat-recovery", 1, SegmentCategory.RECOVERY, "Recover", DurationCompletion(120))
    val workout = HomePreviewData.workout("fixture-long", today, WorkoutType.INTERVALS).copy(
      blocks = listOf(RepeatBlock("repeats", 0, 6, listOf(work, recovery))))
    val summary = workout.listSummary()
    val weeks = plan.weeks.map { week -> week.copy(workouts = week.workouts.map { entry ->
      if (entry.workout.id != workout.id) entry else entry.copy(workout = workout)
    }) }
    val result = present(before.copy(nextWorkout = before.nextWorkout!!.copy(type = workout.type,
      paceLabel = summary.paceLabel, distanceLabel = summary.distanceLabel), todaySources = sources.copy(plan = plan.copy(weeks = weeks))))
    assertTrue(result.subtitle.orEmpty().contains("6 ×"))
    assertFalse(result.subtitle.orEmpty().contains("/km"))
  }

  @Test fun unsupportedProfileTilesAndAllFormSectionAreHiddenWithoutSources() {
    val state = before.copy(progress = null, todaySources = before.todaySources!!.copy(profile = null))
    assertTrue(present(state).form.isEmpty())
    val easyOnly = present(state.copy(progress = before.progress)).form
    assertEquals(listOf("Easy pace"), easyOnly.map { it.title })
    assertEquals("25 s/km faster", easyOnly.single().delta)
  }

  @Test fun unavailableReadinessAndCadenceDoNotBecomeZeroOrDefault() {
    val sources = before.todaySources!!
    val profile = sources.profile!!
    val result = present(before.copy(todaySources = sources.copy(profile = profile.copy(
      typicalEasyCadence = null, feedback = profile.feedback.copy(averageReadiness = null)))))
    assertFalse(result.form.any { it.title in listOf("Cadence", "Readiness") })
    assertTrue(result.form.any { it.title == "Heart rate, easy" })
  }

  @Test fun invalidMetricsNeverRenderNanInfinityOrZeroSentinels() {
    val run = before.latestRun!!.copy(startTime = clock.instant().minusSeconds(600), endTime = clock.instant().minusSeconds(10),
      distanceMeters = Double.NaN, averagePaceSecondsPerKm = Double.POSITIVE_INFINITY, durationSeconds = 0)
    val result = present(before.copy(latestRun = run))
    assertTrue(result.heroMetrics.isEmpty())
    assertNull(result.lastRun!!.distance)
    assertNull(result.lastRun.pace)
  }

  @Test fun plannedFallbackIsNeverDisplayedAsRecordedWeeklyDistance() {
    val result = present(before.copy(weeklyMetrics = HomeWeeklyMetrics(0, 21_000.0, null, emptyList(), true, null, null)))
    assertEquals("13.0", result.week!!.distance)
    assertEquals("21.0", result.week.plannedDistance)
    assertEquals(BarState.TodayPlanned, result.week.bars.last().state)
  }

  @Test fun unknownActualDistanceHidesAggregateAndBarRatherThanBecomingRest() {
    val sources = before.todaySources!!
    val runs = sources.canonicalRuns.map { if (it.healthConnectRecordId == "fixture-wed") it.copy(distanceMeters = null) else it }
    val result = present(before.copy(todaySources = sources.copy(canonicalRuns = runs)))
    assertNull(result.week!!.distance)
    assertTrue(result.week.bars[2].value.isNaN())
    assertEquals(BarState.Done, result.week.bars[2].state)
    assertFalse(result.goal!!.bars.any { it.label == "Weekly distance" })
  }

  @Test fun noCheckoutKeepsAdherenceUnknownDespiteCompletedExecution() {
    val state = HomePreviewData.state(HomeDayState.AfterRun)
    val result = present(state, HomePreviewData.clock(HomeDayState.AfterRun))
    assertNotNull(result.checkoutRunId)
    assertFalse(result.chips.any { it.label == "As planned" })
    assertFalse(result.lastRun!!.asPlanned)
  }

  @Test fun unrelatedFeedbackDoesNotHideCheckout() {
    val state = HomePreviewData.state(HomeDayState.AfterRun)
    val feedback = feedback("unrelated")
    assertNotNull(present(state.copy(todaySources = state.todaySources!!.copy(latestFeedback = feedback)), HomePreviewData.clock(HomeDayState.AfterRun)).checkoutRunId)
  }

  @Test fun savedPhysicalRunFeedbackHidesCheckout() {
    val state = HomePreviewData.state(HomeDayState.AfterRun)
    val result = present(state.copy(todaySources = state.todaySources!!.copy(latestFeedback = feedback(state.latestRun!!.physicalRunId))), HomePreviewData.clock(HomeDayState.AfterRun))
    assertNull(result.checkoutRunId)
  }

  @Test fun newLongestRequiresKnownPreviousDistances() {
    val state = HomePreviewData.state(HomeDayState.AfterRun)
    val at = HomePreviewData.clock(HomeDayState.AfterRun)
    assertTrue(present(state, at).chips.any { it.label == "New longest run" })
    val sources = state.todaySources!!
    val unknown = sources.canonicalRuns.map { if (it.healthConnectRecordId == "fixture-mon") it.copy(distanceMeters = null) else it }
    assertFalse(present(state.copy(todaySources = sources.copy(canonicalRuns = unknown)), at).chips.any { it.label == "New longest run" })
  }

  @Test fun goalWithoutMeasurementsKeepsTitleOnlyAndDoesNotInventAssessmentOrDate() {
    val state = before.copy(todaySources = HomeTodaySources(), activeGoal = HomeGoalSummary("Complete 10K"))
    val goal = present(state).goal!!
    assertEquals("Complete 10K", goal.title)
    assertNull(goal.progress)
    assertNull(goal.subtitle)
    assertTrue(goal.bars.isEmpty())
  }

  @Test fun missingRecordedRouteHasNoPlaceholderThumbnail() {
    assertTrue(present(before.copy(todaySources = before.todaySources!!.copy(route = null))).lastRun!!.route.isEmpty())
    assertTrue(present().lastRun!!.route.isNotEmpty())
  }

  @Test fun nextUpIsLimitedToTwoFutureWorkoutsAndKeepsRealIds() {
    val result = present()
    assertEquals(listOf("fixture-next-easy", "fixture-next-recovery"), result.nextUp.map { it.id })
  }

  @Test fun loadingAndErrorsDoNotFlashRestPrescriptionOrOldMetrics() {
    listOf(before.copy(isLoading = true), before.copy(errorMessage = "Could not load training data.")).forEach { state ->
      val result = present(state)
      assertEquals("Today", result.title)
      assertNull(result.reason)
      assertNull(result.workoutId)
      assertTrue(result.quickFacts.isEmpty())
      assertNull(result.goal)
      assertNull(result.week)
      assertTrue(result.form.isEmpty())
    }
  }

  @Test fun adherenceChipRequiresTheExistingResponseAndReportedCheckout() {
    val state = HomePreviewData.state(HomeDayState.AfterRun)
    val sources = state.todaySources!!
    val run = state.latestRun!!
    val answer = feedback(run.healthConnectRecordId)
    val entry = sources.plan!!.weeks.flatMap { it.workouts }.first { it.executionResult?.actualRunId == run.healthConnectRecordId }
    val response = WorkoutResponseEvaluator().evaluate(entry.originalWorkout, null, entry.executionResult!!, run, answer)
    assertEquals(PrescriptionAdherence.AS_PLANNED, response.adherence)
    val result = present(state.copy(todaySources = sources.copy(latestFeedback = answer, latestResponse = response)), HomePreviewData.clock(HomeDayState.AfterRun))
    assertTrue(result.chips.any { it.label == "As planned" })
    assertTrue(result.lastRun!!.asPlanned)
    assertNull(result.checkoutRunId)
    val unknown = present(state.copy(todaySources = sources.copy(latestFeedback = answer,
      latestResponse = response.copy(adherence = PrescriptionAdherence.INSUFFICIENT_EVIDENCE))), HomePreviewData.clock(HomeDayState.AfterRun))
    assertFalse(unknown.chips.any { it.label == "As planned" })
  }

  @Test fun goalWeekProgressUsesTheSameClockZoneAsTheWeekCard() {
    val at = Clock.fixed(Instant.parse("2026-11-08T14:30:00Z"), ZoneId.of("Pacific/Kiritimati"))
    val run = before.latestRun!!.copy(startTime = Instant.parse("2026-11-08T11:00:00Z"),
      endTime = Instant.parse("2026-11-08T11:30:00Z"))
    val result = present(before.copy(latestRun = run,
      todaySources = before.todaySources!!.copy(canonicalRuns = listOf(run))), at)
    assertEquals("5.0", result.week!!.distance)
    assertEquals("5.0 of 8.0 km planned", result.goal!!.bars.first { it.label == "Weekly distance" }.value)
  }

  @Test fun originalPrescriptionComparisonDoesNotUseLaterAdjustedTargets() {
    val state = HomePreviewData.state(HomeDayState.AfterRun)
    val sources = state.todaySources!!
    val plan = sources.plan!!
    val updated = plan.copy(weeks = plan.weeks.map { week -> week.copy(workouts = week.workouts.map { entry ->
      if (entry.workout.id != "fixture-long") entry else entry.copy(workout = entry.workout.copy(
        name = "Adjusted recovery", type = WorkoutType.RECOVERY_RUN,
        blocks = listOf(SingleSegmentBlock("adjusted-block", 0,
          WorkoutSegment("adjusted-segment", 0, SegmentCategory.EASY, "Recover", DurationCompletion(600), PaceTarget(500.0, 550.0))))))
    }) })
    val result = present(state.copy(todaySources = sources.copy(plan = updated)), HomePreviewData.clock(HomeDayState.AfterRun))
    assertEquals("Long run", result.title)
    assertFalse(result.chips.any { it.label.contains("8:20") })
  }

  @Test fun coachCopyPreservesTheExistingActionAndReportedPainWithoutChoosingANewAction() {
    val sources = before.todaySources!!
    val answer = feedback(before.latestRun!!.healthConnectRecordId).copy(painLevel = PainLevel.SIGNIFICANT)
    val decision = before.coachingDecision!!.copy(action = com.example.openruncoach.domain.coaching.CoachingAction.RECOVERY,
      reasons = listOf("Workout completed", "RPE 3/10", "Significant pain reported"))
    val copy = present(before.copy(coachingDecision = decision, todaySources = sources.copy(latestFeedback = answer))).coach!!
    assertTrue(copy.contains("You completed your run."))
    assertTrue(copy.contains("Significant pain reported."))
    assertTrue(copy.contains("Prioritize recovery before progressing the next session."))
  }

  @Test fun countdownRequiresAnExplicitScheduledCompletionDate() {
    val sources = before.todaySources!!
    val plan = sources.plan!!
    assertFalse(present(before.copy(todaySources = sources.copy(plan = plan.copy(endDate = today.plusWeeks(5)))))
      .goal!!.subtitle.orEmpty().contains("weeks left"))
    val milestone = com.example.openruncoach.domain.goal.GoalCompletionMilestone(sources.goal!!.id,
      com.example.openruncoach.domain.goal.GoalCompletionReadiness.GOAL_COMPLETION_READY,
      today.plusWeeks(5), false, listOf("Explicit saved fixture date."), clock.instant(), planId = plan.id)
    assertTrue(present(before.copy(todaySources = sources.copy(plan = plan.copy(completionMilestone = milestone))))
      .goal!!.subtitle.orEmpty().contains("5 weeks left"))
  }

  @Test fun afterRunFactsHideCardWithZeroOrOneFactAndKeepTwoWithoutHeroDuplicates() {
    val state = HomePreviewData.state(HomeDayState.AfterRun)
    val sources = state.todaySources!!
    val at = HomePreviewData.clock(HomeDayState.AfterRun)
    assertTrue(present(state.copy(todaySources = sources.copy(latestMetrics = null)), at).quickFacts.isEmpty())
    assertTrue(present(state.copy(latestRun = state.latestRun!!.copy(averageHeartRate = null),
      todaySources = sources.copy(latestMetrics = null)), at).quickFacts.isEmpty())
    val two = present(state.copy(todaySources = sources.copy(latestMetrics = sources.latestMetrics!!.copy(elevationGainMeters = null))), at).quickFacts
    assertEquals(listOf("avg heart rate", "cadence"), two.map { it.label })
    assertEquals("168 spm", two.last().value)
  }

  @Test fun afterRunFactsRequireMetricsFromThisRecordedRunAndInterval() {
    val state = HomePreviewData.state(HomeDayState.AfterRun)
    val sources = state.todaySources!!
    val at = HomePreviewData.clock(HomeDayState.AfterRun)
    assertTrue(present(state.copy(todaySources = sources.copy(latestMetrics = sources.latestMetrics!!.copy(runId = "another-run"))), at).quickFacts.isEmpty())
    assertTrue(present(state.copy(todaySources = sources.copy(latestMetrics = sources.latestMetrics.copy(startTime = sources.latestMetrics.startTime.minusSeconds(60)))), at).quickFacts.isEmpty())
  }

  @Test fun knownZeroElevationIsKeptButInvalidCadenceIsHidden() {
    val state = HomePreviewData.state(HomeDayState.AfterRun)
    val sources = state.todaySources!!
    val metrics = sources.latestMetrics!!
    val updated = metrics.copy(averageCadence = metrics.averageCadence!!.copy(value = Double.NaN),
      elevationGainMeters = metrics.elevationGainMeters!!.copy(value = 0.0))
    val facts = present(state.copy(todaySources = sources.copy(latestMetrics = updated)), HomePreviewData.clock(HomeDayState.AfterRun)).quickFacts
    assertEquals(listOf("avg heart rate", "elevation gain"), facts.map { it.label })
    assertEquals("0 m", facts.last().value)
  }

  @Test fun effortVsPlanUsesExistingResponseOnlyWithKnownCheckout() {
    val state = HomePreviewData.state(HomeDayState.AfterRun)
    val sources = state.todaySources!!
    val run = state.latestRun!!
    val answer = feedback(run.healthConnectRecordId)
    val entry = sources.plan!!.weeks.flatMap { it.workouts }.first { it.executionResult?.actualRunId == run.healthConnectRecordId }
    val response = WorkoutResponseEvaluator().evaluate(entry.originalWorkout, null, entry.executionResult!!, run, answer)
    val at = HomePreviewData.clock(HomeDayState.AfterRun)
    val known = present(state.copy(todaySources = sources.copy(latestFeedback = answer, latestResponse = response)), at).quickFacts
    assertEquals("On target", known.first { it.label == "effort vs plan" }.value)
    val unknown = present(state.copy(todaySources = sources.copy(latestFeedback = answer, latestResponse = response.copy(effortResponse = EffortResponse.UNKNOWN))), at).quickFacts
    assertFalse(unknown.any { it.label == "effort vs plan" })
    assertFalse(present(state.copy(todaySources = sources.copy(latestResponse = response)), at).quickFacts.any { it.label == "effort vs plan" })
  }

  @Test fun restFactsUseRecordedWeeklyTotalsAndRemainingPlanRatherThanNextWorkout() {
    val state = HomePreviewData.state(HomeDayState.RestDay)
    val result = present(state.copy(weeklyMetrics = HomeWeeklyMetrics(0, 21_000.0, null, emptyList(), true, null, null)), HomePreviewData.clock(HomeDayState.RestDay))
    assertEquals(listOf("13.0", "3", "8 km", "4 / 5"), result.quickFacts.map { it.value })
  }

  @Test fun restFactsHideUnknownTotalsAndRequireAtLeastTwoFacts() {
    val state = HomePreviewData.state(HomeDayState.RestDay)
    val sources = state.todaySources!!
    val at = HomePreviewData.clock(HomeDayState.RestDay)
    val unknownDistances = sources.canonicalRuns.map { it.copy(distanceMeters = null) }
    val two = present(state.copy(todaySources = sources.copy(canonicalRuns = unknownDistances)), at).quickFacts
    assertEquals(listOf("runs", "readiness"), two.map { it.label })
    assertTrue(present(state.copy(todaySources = sources.copy(canonicalRuns = unknownDistances, profile = null)), at).quickFacts.isEmpty())
    val withoutPlan = present(state.copy(todaySources = sources.copy(plan = null, profile = null)), at).quickFacts
    assertEquals(listOf("km done", "runs"), withoutPlan.map { it.label })
  }

  @Test fun beforeRunDoesNotCompareAnEasyWorkoutToTheLastLongRun() {
    val result = present(before.copy(nextWorkout = before.nextWorkout!!.copy(type = WorkoutType.EASY_RUN)))
    assertFalse(result.quickFacts.any { it.label.startsWith("vs last") })
  }

  private fun feedback(runId: String) = RunFeedback("fixture-feedback", runId, 3, CardioEffort.APPROPRIATE,
    MuscleFatigue.MILD, PainLevel.NONE, CompletionStatus.COMPLETED, null, null, clock.instant(), clock.instant())
}
