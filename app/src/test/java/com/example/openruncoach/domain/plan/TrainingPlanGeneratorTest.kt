package com.example.openruncoach.domain.plan

import com.example.openruncoach.domain.goal.GoalAssessment
import com.example.openruncoach.domain.goal.GoalAssessmentLevel
import com.example.openruncoach.domain.goal.GoalCompletionMilestone
import com.example.openruncoach.domain.goal.GoalCompletionReadiness
import com.example.openruncoach.domain.goal.GoalDistance
import com.example.openruncoach.domain.goal.GoalStatus
import com.example.openruncoach.domain.goal.GoalType
import com.example.openruncoach.domain.goal.StartingPerformanceSnapshot
import com.example.openruncoach.domain.goal.TrainingGoal
import com.example.openruncoach.domain.profile.AthletePerformanceProfile
import com.example.openruncoach.domain.profile.FeedbackAggregates
import com.example.openruncoach.domain.profile.FitnessTrend
import com.example.openruncoach.domain.profile.LongestRecentRun
import com.example.openruncoach.domain.profile.PaceTrend
import com.example.openruncoach.domain.profile.ProfileBaseline
import com.example.openruncoach.domain.profile.ProfileConfidence
import com.example.openruncoach.domain.profile.TrainingConsistency
import com.example.openruncoach.domain.profile.ValueTrend
import com.example.openruncoach.domain.workout.DistanceCompletion
import com.example.openruncoach.domain.workout.PaceTarget
import com.example.openruncoach.domain.workout.RpeTarget
import com.example.openruncoach.domain.workout.RepeatBlock
import com.example.openruncoach.domain.workout.SegmentCategory
import com.example.openruncoach.domain.workout.SingleSegmentBlock
import com.example.openruncoach.domain.workout.WorkoutSegment
import com.example.openruncoach.domain.workout.WorkoutEstimateCalculator
import com.example.openruncoach.domain.workout.WorkoutPurpose
import com.example.openruncoach.domain.workout.WorkoutType
import com.example.openruncoach.domain.workout.prescribedRunningDistanceMeters
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrainingPlanGeneratorTest {
  private val now = Instant.parse("2026-09-24T12:00:00Z")
  private val generator = TrainingPlanGenerator(Clock.fixed(now, ZoneOffset.UTC), ZoneOffset.UTC)

  @Test fun noRunsStartsWithOneAssessmentWithoutInventedPace() {
    val plan = generator.generate(goal(), profile(totalRuns = 0, runs = 0, averageRuns = 0.0, confidence = ProfileConfidence.VERY_LOW, easyPace = null))
    assertTrue(plan.isAssessmentPhase)
    assertEquals(1, plan.weeks.first().workouts.size)
    assertEquals(WorkoutType.ASSESSMENT, plan.weeks.first().workouts.single().workout.type)
    assertTrue(plan.weeks.first().workouts.single().workout.blocks.flatMap { it.segments }
      .flatMap { listOf(it.primaryIntensity) + it.secondaryIntensities }.none { it is PaceTarget })
    assertTrue(plan.weeks.flattenTypes().none(::isHard))
  }

  @Test fun lowDataAssessmentPlanPreservesTheUserSelectedCompletionDate() {
    val date = LocalDate.parse("2026-10-18")
    val plan = generator.generate(
      goal(GoalDistance.FIVE_K),
      profile(totalRuns = 0, runs = 0, averageRuns = 0.0, confidence = ProfileConfidence.VERY_LOW, easyPace = null),
      completionMilestone = scheduledMilestone(date),
    )
    assertTrue(plan.isAssessmentPhase)
    assertEquals(date, plan.completionMilestone?.scheduledDate)
    assertNull(plan.completionMilestone?.workoutId)
  }

  @Test fun veryLowConfidenceKeepsQualityWorkOut() {
    val plan = generator.generate(goal(GoalDistance.FIVE_K, GoalType.IMPROVE_PERFORMANCE), profile(confidence = ProfileConfidence.VERY_LOW))
    assertTrue(plan.weeks.flattenTypes().none(::isHard))
    assertTrue(plan.explanation.contains("aerobic"))
  }

  @Test fun lowWeeklyVolumeDoesNotJumpToExcessiveFrequency() {
    val plan = generator.generate(goal(), profile(averageRuns = 1.5, weeklyDistance = 5_000.0, confidence = ProfileConfidence.LOW))
    assertTrue(plan.weeks.first().workouts.size <= 3)
  }

  @Test fun establishedFrequencyIsPreservedAtFourRuns() {
    val plan = generator.generate(goal(GoalDistance.FIVE_K, GoalType.IMPROVE_PERFORMANCE), establishedProfile())
    assertEquals(4, plan.weeks.first().workouts.size)
  }

  @Test fun fourthRunIsAddedOnlyAfterRecoveryAndWithoutQualityProgression() {
    val plan = generator.generate(
      goal(GoalDistance.FIVE_K, GoalType.IMPROVE_PERFORMANCE),
      profile(
        averageRuns = 3.5,
        confidence = ProfileConfidence.MEDIUM,
        consistency = TrainingConsistency.CONSISTENT,
        activeWeeks = 5,
      ),
      completionMilestone = scheduledMilestone(LocalDate.parse("2026-12-20")),
    )
    assertTrue(plan.weeks.take(4).all { it.workouts.size == 3 })
    assertEquals(4, plan.weeks[4].workouts.size)
    assertTrue(plan.weeks[4].workouts.none { isHard(it.workout.type) })
  }

  @Test fun fiveKCompletionPlanKeepsMajorityAerobic() {
    val plan = generator.generate(goal(GoalDistance.FIVE_K), profile())
    assertTrue(plan.weeks.all(::hasAerobicMajority))
    assertFalse(plan.weeks.flattenTypes().contains(WorkoutType.INTERVALS))
  }

  @Test fun tenKCompletionStartsBelowGoalDistance() {
    val plan = generator.generate(goal(GoalDistance.TEN_K), profile(longest = 4_800.0))
    assertTrue(longRunDistance(plan.weeks.first())!! in 4_800.0..<10_000.0)
  }

  @Test fun fiveKImproveMayIncludeThresholdForEligibleRunner() {
    val plan = generator.generate(goal(GoalDistance.FIVE_K, GoalType.IMPROVE_PERFORMANCE), establishedProfile())
    assertTrue(plan.weeks.flattenTypes().contains(WorkoutType.THRESHOLD))
  }

  @Test fun targetTimeDoesNotAssignGoalPaceToEasyRuns() {
    val plan = generator.generate(goal(GoalDistance.FIVE_K, GoalType.TARGET_TIME, 1_800), establishedProfile())
    val easyTargets = plan.weeks.flatMap { it.workouts }.filter { it.workout.type == WorkoutType.EASY_RUN }
      .flatMap { it.workout.blocks }.flatMap { it.segments }
      .flatMap { listOf(it.primaryIntensity) + it.secondaryIntensities }
      .filterIsInstance<PaceTarget>()
    assertTrue(easyTargets.isNotEmpty())
    assertTrue(easyTargets.none { 360.0 in it.minimumSecondsPerKm..it.maximumSecondsPerKm })
    assertTrue(plan.weeks.flatMap { it.workouts }.flatMap { it.workout.cues }.any { it.contains("context") })
  }

  @Test fun easyRunUsesDistancePaceAndDerivedDuration() {
    val easy = generator.generate(goal(GoalDistance.FIVE_K), profile()).weeks.first().workouts
      .first { it.workout.type == WorkoutType.EASY_RUN }.workout
    val segment = easy.blocks.single().segments.single()
    assertTrue(segment.completion is DistanceCompletion)
    assertTrue(segment.allTargets().any { it is PaceTarget })
    assertTrue(segment.allTargets().any { it is RpeTarget })
    val estimate = WorkoutEstimateCalculator.calculate(easy)
    assertTrue(estimate.estimatedMinimumDurationSeconds != null)
    assertTrue(estimate.estimatedMaximumDurationSeconds!! > estimate.estimatedMinimumDurationSeconds!!)
    val distance = (segment.completion as DistanceCompletion).meters / 1_000.0
    val pace = segment.allTargets().filterIsInstance<PaceTarget>().single()
    assertEquals((distance * pace.minimumSecondsPerKm).toLong(), estimate.estimatedMinimumDurationSeconds)
    assertEquals((distance * pace.maximumSecondsPerKm).toLong(), estimate.estimatedMaximumDurationSeconds)
  }

  @Test fun paceEngineUsesObservedEasyBaselineWithExplainableSource() {
    val prescription = PacePrescriptionEngine().prescribe(
      profile(), goal(GoalDistance.FIVE_K), WorkoutType.EASY_RUN, WorkoutPurpose.AEROBIC_BASE,
      4_000.0, TrainingBlockType.FOUNDATION,
    )!!
    assertEquals(PacePrescriptionSource.OBSERVED_EASY_BASELINE, prescription.source)
    assertTrue(prescription.explanation.isNotBlank())
    assertTrue(prescription.minSecondsPerKm < prescription.maxSecondsPerKm)
  }

  @Test fun longRunPaceIsNoFasterThanEasyPaceAndCanExceedFiveK() {
    val plan = generator.generate(goal(GoalDistance.FIVE_K, GoalType.IMPROVE_PERFORMANCE), profile())
    val easyPace = plan.weeks.first().workouts.first { it.workout.type == WorkoutType.EASY_RUN }.workout.singlePace()!!
    val longPaces = plan.weeks.flatMap { it.workouts }.filter { it.workout.type == WorkoutType.LONG_RUN }
      .mapNotNull { it.workout.singlePace() }
    assertTrue(longPaces.all { it.minimumSecondsPerKm >= easyPace.minimumSecondsPerKm })
    assertTrue(plan.weeks.flatMap { it.workouts }.filter { it.workout.type == WorkoutType.LONG_RUN }
      .mapNotNull { it.workout.singleDistance() }.any { it > 5_000.0 })
  }

  @Test fun fiveKCompletionBuildsToControlledFullDistance() {
    val plan = generator.generate(goal(GoalDistance.FIVE_K), profile(), completionMilestone = scheduledMilestone())
    assertTrue(plan.weeks.first().workouts.mapNotNull { it.workout.singleDistance() }.any { it < 5_000.0 })
    val completion = plan.weeks.flatMap { it.workouts }.map { it.workout }
      .first { it.name == "5K Completion Run" }
    assertEquals(5_000.0, completion.singleDistance()!!, 0.001)
    assertTrue(completion.targetRpeMax <= 5)
  }

  @Test fun completeFiveKKeepsEveryPreGoalWorkoutBelowTargetAndSchedulesOneFinalGoalRun() {
    val selectedGoal = goal(GoalDistance.FIVE_K)
    val plan = generator.generate(selectedGoal, profile(longest = 4_800.0), completionMilestone = scheduledMilestone())
    val workouts = plan.weeks.flatMap { it.workouts }.map { it.workout }
    val completionIndex = workouts.indexOfFirst { it.purpose == WorkoutPurpose.GOAL_COMPLETION }
    assertEquals(1, workouts.count { it.purpose == WorkoutPurpose.GOAL_COMPLETION })
    assertTrue(workouts.take(completionIndex).all { it.prescribedRunningDistanceMeters() < 5_000.0 })
    assertTrue(workouts.take(completionIndex).filter { it.type == WorkoutType.EASY_RUN }
      .all { it.prescribedRunningDistanceMeters() < 5_000.0 })
    assertTrue(workouts.take(completionIndex).filter { it.type == WorkoutType.LONG_RUN }
      .all { it.prescribedRunningDistanceMeters() < 5_000.0 })
    assertEquals(5_000.0, workouts[completionIndex].prescribedRunningDistanceMeters(), 0.001)
    assertTrue(workouts.drop(completionIndex + 1).all { it.type in setOf(WorkoutType.REST, WorkoutType.RECOVERY_RUN) })
  }

  @Test fun completeTenKUsesTheSameSingleCompletionSemantics() {
    val selectedGoal = goal(GoalDistance.TEN_K)
    val plan = generator.generate(
      selectedGoal,
      profile(longest = 9_300.0, weeklyDistance = 24_000.0),
      completionMilestone = scheduledMilestone(),
    )
    val workouts = plan.weeks.flatMap { it.workouts }.map { it.workout }
    val completion = workouts.single { it.purpose == WorkoutPurpose.GOAL_COMPLETION }
    val completionIndex = workouts.indexOf(completion)
    assertTrue(workouts.take(completionIndex).all { it.prescribedRunningDistanceMeters() < 10_000.0 })
    assertEquals(10_000.0, completion.prescribedRunningDistanceMeters(), 0.001)
    assertTrue(workouts.drop(completionIndex + 1).all { it.type in setOf(WorkoutType.REST, WorkoutType.RECOVERY_RUN) })
  }

  @Test fun readyButUnscheduledDoesNotCreateACompletionRunOrInventADate() {
    val plan = generator.generate(
      goal(GoalDistance.FIVE_K),
      establishedProfile(),
      completionMilestone = readyMilestone(),
    )
    assertTrue(plan.weeks.flatMap { it.workouts }.none { it.workout.purpose == WorkoutPurpose.GOAL_COMPLETION })
    assertTrue(plan.explanation.contains("ready to choose"))
  }

  @Test fun postponedCompletionKeepsAdaptiveTrainingWithoutAFakeDate() {
    val plan = generator.generate(
      goal(GoalDistance.FIVE_K),
      profile(),
      completionMilestone = readyMilestone().copy(promptDismissed = true),
    )
    assertTrue(plan.weeks.isNotEmpty())
    assertTrue(plan.weeks.flatMap { it.workouts }.none { it.workout.purpose == WorkoutPurpose.GOAL_COMPLETION })
    assertTrue(plan.weeks.flatMap { it.workouts }.any { it.workout.type == WorkoutType.LONG_RUN })
  }

  @Test fun userSelectedSaturdayPlacesCompletionOnSaturdayAndProtectsSunday() {
    val saturday = LocalDate.parse("2026-10-17")
    val plan = generator.generate(
      goal(GoalDistance.FIVE_K),
      profile(),
      completionMilestone = scheduledMilestone(saturday),
    )
    val week = plan.weeks.single { candidate -> candidate.workouts.any { it.workout.purpose == WorkoutPurpose.GOAL_COMPLETION } }
    val completion = week.workouts.single { it.workout.purpose == WorkoutPurpose.GOAL_COMPLETION }.workout
    assertEquals(saturday, completion.date)
    assertTrue(week.workouts.filter { it.workout.date == saturday.plusDays(1) }
      .all { it.workout.type in setOf(WorkoutType.REST, WorkoutType.RECOVERY_RUN) })
    assertTrue(week.workouts.none {
      it.workout.date == saturday.plusDays(1) && it.workout.type == WorkoutType.LONG_RUN &&
        it.workout.purpose != WorkoutPurpose.GOAL_COMPLETION
    })
    assertTrue(TrainingPlanValidator().validate(plan, goal(GoalDistance.FIVE_K), true).isValid)
  }

  @Test fun userSelectedSundayReplacesTheNormalLongRunWithoutADuplicate() {
    val sunday = LocalDate.parse("2026-10-18")
    val plan = generator.generate(
      goal(GoalDistance.FIVE_K),
      profile(),
      completionMilestone = scheduledMilestone(sunday),
    )
    val week = plan.weeks.single { candidate -> candidate.workouts.any { it.workout.purpose == WorkoutPurpose.GOAL_COMPLETION } }
    assertEquals(sunday, week.workouts.single { it.workout.purpose == WorkoutPurpose.GOAL_COMPLETION }.workout.date)
    assertEquals(1, week.workouts.count { it.workout.type == WorkoutType.LONG_RUN })
    assertEquals(1, week.workouts.count { it.workout.purpose == WorkoutPurpose.GOAL_COMPLETION })
  }

  @Test fun earlyScheduledCompletionEndsThePlanInsteadOfLeavingBuildWeeksAfterIt() {
    val saturday = LocalDate.parse("2026-10-03")
    val selectedGoal = goal(GoalDistance.FIVE_K)
    val plan = generator.generate(
      selectedGoal,
      profile(),
      completionMilestone = scheduledMilestone(saturday),
    )
    val workouts = plan.weeks.flatMap { it.workouts }.map { it.workout }
    val completion = workouts.single { it.purpose == WorkoutPurpose.GOAL_COMPLETION }
    assertEquals(saturday, completion.date)
    assertEquals(2, plan.weeks.size)
    assertTrue(workouts.filter { it.date.isAfter(saturday) }
      .all { it.type in setOf(WorkoutType.REST, WorkoutType.RECOVERY_RUN) })
    assertTrue(TrainingPlanValidator().validate(plan, selectedGoal, true).isValid)
  }

  @Test fun targetTimeFiveKAllowsLongRunsBeyondRaceDistance() {
    val plan = generator.generate(goal(GoalDistance.FIVE_K, GoalType.TARGET_TIME, 1_800), establishedProfile())
    assertTrue(plan.weeks.flatMap { it.workouts }.map { it.workout }
      .filter { it.type == WorkoutType.LONG_RUN }.any { it.prescribedRunningDistanceMeters() > 5_000.0 })
  }

  @Test fun fiveKImproveContainsControlledFiveKAndLongerLongRun() {
    val plan = generator.generate(goal(GoalDistance.FIVE_K, GoalType.IMPROVE_PERFORMANCE), establishedProfile())
    val controlled = plan.weeks.flatMap { it.workouts }.map { it.workout }.first { it.name == "Controlled 5K" }
    assertEquals(5_000.0, controlled.singleDistance()!!, 0.001)
    assertTrue(controlled.targetRpeMax <= 6)
    assertTrue(plan.weeks.flatMap { it.workouts }.map { it.workout }
      .filter { it.type == WorkoutType.LONG_RUN }.mapNotNull { it.singleDistance() }.any { it > 5_000.0 })
  }

  @Test fun planMilestoneProgressesFromControlledFiveKToGoalPaceAttempt() {
    val plan = generator.generate(
      goal(GoalDistance.FIVE_K, GoalType.TARGET_TIME, 1_800),
      establishedProfile(),
      completionMilestone = scheduledMilestone(LocalDate.parse("2026-12-20")),
    )
    val workouts = plan.weeks.flatMap { it.workouts }.map { it.workout }
    val controlledPace = workouts.first { it.name == "Controlled 5K" }.singlePace()!!
    val attemptPace = workouts.first { it.name == "5K Goal Attempt" }.singlePace()!!
    assertTrue(controlledPace.minimumSecondsPerKm > 360.0)
    assertEquals(360.0, attemptPace.minimumSecondsPerKm, 0.001)
    assertEquals(360.0, attemptPace.maximumSecondsPerKm, 0.001)
  }

  @Test fun lowConfidencePaceRangeIsBroadAndKeepsRpeGuardrail() {
    val easy = generator.generate(goal(GoalDistance.FIVE_K), profile(confidence = ProfileConfidence.LOW))
      .weeks.first().workouts.first { it.workout.type == WorkoutType.EASY_RUN }.workout
    val segment = easy.blocks.single().segments.single()
    val pace = segment.allTargets().filterIsInstance<PaceTarget>().single()
    assertTrue(pace.maximumSecondsPerKm - pace.minimumSecondsPerKm >= 60.0)
    assertTrue(segment.allTargets().any { it is RpeTarget && it.minimum == 3 && it.maximum == 4 })
    assertTrue(easy.cues.any { it.contains("broad", ignoreCase = true) })
  }

  @Test fun weeklyDistanceEqualsSumOfPrescribedDistances() {
    val plan = generator.generate(goal(GoalDistance.FIVE_K), profile())
    plan.weeks.forEach { week ->
      val prescribed = week.workouts.sumOf { WorkoutEstimateCalculator.calculate(it.workout).knownDistanceMeters }
      assertEquals(prescribed, week.targetDistanceMeters!!, 0.001)
    }
  }

  @Test fun validatorRejectsPhysiologicallyInsanePace() {
    val plan = generator.generate(goal(GoalDistance.FIVE_K), profile(), completionMilestone = scheduledMilestone())
    val week = plan.weeks.first()
    val entry = week.workouts.first { it.workout.type == WorkoutType.EASY_RUN }
    val block = entry.workout.blocks.single() as SingleSegmentBlock
    val invalidSegment = block.segment.copy(primaryIntensity = PaceTarget(90.0, 100.0))
    val invalidWorkout = entry.workout.copy(blocks = listOf(block.copy(segment = invalidSegment)))
    val invalidWeek = week.copy(workouts = week.workouts.map { if (it.workout.id == invalidWorkout.id) it.copy(workout = invalidWorkout) else it })
    val invalidPlan = plan.copy(weeks = plan.weeks.map { if (it.weekIndex == invalidWeek.weekIndex) invalidWeek else it })
    assertFalse(TrainingPlanValidator().validate(invalidPlan, goal(GoalDistance.FIVE_K), true).isValid)
  }

  @Test fun validatorAcceptsLongRunBeyondRaceDistance() {
    val selectedGoal = goal(GoalDistance.FIVE_K, GoalType.IMPROVE_PERFORMANCE)
    val plan = generator.generate(selectedGoal, profile())
    assertTrue(plan.weeks.flatMap { it.workouts }.map { it.workout }
      .filter { it.type == WorkoutType.LONG_RUN }.mapNotNull { it.singleDistance() }.any { it > 5_000.0 })
    assertTrue(TrainingPlanValidator().validate(plan, selectedGoal, true).isValid)
  }

  @Test fun validatorRejectsPreGoalLongRunAtOrBeyondCompleteDistance() {
    val selectedGoal = goal(GoalDistance.FIVE_K)
    val plan = generator.generate(selectedGoal, profile(), completionMilestone = scheduledMilestone())
    val week = plan.weeks.first()
    val entry = week.workouts.first { it.workout.type == WorkoutType.LONG_RUN }
    val block = entry.workout.blocks.single() as SingleSegmentBlock
    val invalid = entry.workout.copy(blocks = listOf(block.copy(segment = block.segment.copy(completion = DistanceCompletion(5_600.0)))))
    val invalidPlan = plan.copy(weeks = plan.weeks.map {
      if (it.weekIndex == week.weekIndex) it.copy(workouts = it.workouts.map { item -> if (item.workout.id == invalid.id) item.copy(workout = invalid) else item }) else it
    })
    val result = TrainingPlanValidator().validate(invalidPlan, selectedGoal, true)
    assertFalse(result.isValid)
    assertTrue(result.errors.any { it.contains("pre-goal") })
  }

  @Test fun validatorUsesTotalRunningDistanceAcrossStructuredSegments() {
    val selectedGoal = goal(GoalDistance.FIVE_K)
    val plan = generator.generate(selectedGoal, profile(), completionMilestone = scheduledMilestone())
    val week = plan.weeks.first()
    val entry = week.workouts.first()
    fun segment(id: String, order: Int, category: SegmentCategory, meters: Double) = WorkoutSegment(
      id, order, category, id, DistanceCompletion(meters), RpeTarget(3, 4),
    )
    val structured = entry.workout.copy(blocks = listOf(
      SingleSegmentBlock("warm", 0, segment("warm-segment", 0, SegmentCategory.WARMUP, 1_000.0)),
      RepeatBlock("work", 1, 4, listOf(
        segment("work-segment", 0, SegmentCategory.WORK, 800.0),
        segment("walk-segment", 1, SegmentCategory.WALK, 200.0),
      )),
      SingleSegmentBlock("cool", 2, segment("cool-segment", 0, SegmentCategory.COOLDOWN, 1_000.0)),
    ))
    assertEquals(5_200.0, structured.prescribedRunningDistanceMeters(), 0.001)
    val invalidPlan = plan.copy(weeks = plan.weeks.map {
      if (it.weekIndex == week.weekIndex) it.copy(workouts = it.workouts.map { item -> if (item.workout.id == structured.id) item.copy(workout = structured) else item }) else it
    })
    assertFalse(TrainingPlanValidator().validate(invalidPlan, selectedGoal, true).isValid)
  }

  @Test fun validatorRejectsDuplicateCompletionRuns() {
    val selectedGoal = goal(GoalDistance.FIVE_K)
    val plan = generator.generate(selectedGoal, profile(), completionMilestone = scheduledMilestone())
    val completion = plan.weeks.flatMap { it.workouts }.single { it.workout.purpose == WorkoutPurpose.GOAL_COMPLETION }
    val duplicate = completion.copy(workout = completion.workout.copy(id = completion.workout.id + ":duplicate"))
    val invalidPlan = plan.copy(weeks = plan.weeks.mapIndexed { index, week ->
      if (index == week.weekIndex && week.weekIndex == plan.weeks.lastIndex) week.copy(workouts = week.workouts + duplicate) else week
    })
    val result = TrainingPlanValidator().validate(invalidPlan, selectedGoal, true)
    assertFalse(result.isValid)
    assertTrue(result.errors.any { it.contains("only one") })
  }

  @Test fun halfMarathonUsesTempoRatherThanEarlyIntervals() {
    val plan = generator.generate(goal(GoalDistance.HALF_MARATHON, GoalType.IMPROVE_PERFORMANCE), establishedProfile())
    assertTrue(plan.weeks.flattenTypes().contains(WorkoutType.TEMPO_RUN))
    assertFalse(plan.weeks.flattenTypes().contains(WorkoutType.INTERVALS))
  }

  @Test fun customDistancePlanRetainsGoalAndExplanation() {
    val plan = generator.generate(goal(GoalDistance.CUSTOM, customDistance = 15_000.0), profile())
    assertEquals("goal", plan.goalId)
    assertTrue(plan.weeks.all { it.reasons.isNotEmpty() })
  }

  @Test fun weeklyVolumeProgressionIsBounded() {
    val plan = generator.generate(goal(), profile(weeklyDistance = 10_000.0, confidence = ProfileConfidence.LOW))
    val first = plan.weeks[0].targetDistanceMeters!!
    val second = plan.weeks[1].targetDistanceMeters!!
    assertTrue(second / first <= 1.05 + 0.0001)
  }

  @Test fun fourthWeekIsReducedRecoveryWeek() {
    val plan = generator.generate(goal(), profile(weeklyDistance = 10_000.0))
    assertEquals(WeekLoadClassification.RECOVERY, plan.weeks[3].loadClassification)
    assertTrue(plan.weeks[3].targetDistanceMeters!! < plan.weeks[2].targetDistanceMeters!!)
    assertTrue(longRunDistance(plan.weeks[3])!! < longRunDistance(plan.weeks[2])!!)
  }

  @Test fun generatedVolumeIsNeverNegative() {
    val plan = generator.generate(goal(), profile(weeklyDistance = 0.0, weeklyDuration = 0L))
    assertTrue(plan.weeks.all { it.targetDistanceMeters == null || it.targetDistanceMeters >= 0.0 })
    assertTrue(plan.weeks.all { it.targetDurationSeconds == null || it.targetDurationSeconds >= 0L })
  }

  @Test fun hardSessionCapAndSpacingAreRespected() {
    val plan = generator.generate(goal(GoalDistance.FIVE_K, GoalType.IMPROVE_PERFORMANCE), establishedProfile())
    plan.weeks.forEach { week ->
      val hard = week.workouts.filter { isHard(it.workout.type) }
      assertTrue(hard.size <= 2)
      hard.zipWithNext().forEach { (first, second) ->
        assertTrue(java.time.temporal.ChronoUnit.DAYS.between(first.workout.date, second.workout.date) >= 2)
      }
    }
  }

  @Test fun everyWeekIncludesALongRunAndAerobicMajority() {
    val plan = generator.generate(goal(), profile())
    assertTrue(plan.weeks.all { week -> week.workouts.count { it.workout.type == WorkoutType.LONG_RUN } == 1 })
    assertTrue(plan.weeks.all(::hasAerobicMajority))
  }

  @Test fun lowDataAthleteGetsNoIntervals() {
    val plan = generator.generate(goal(GoalDistance.FIVE_K, GoalType.IMPROVE_PERFORMANCE), profile(confidence = ProfileConfidence.LOW))
    assertFalse(plan.weeks.flattenTypes().contains(WorkoutType.INTERVALS))
  }

  @Test fun thresholdEligibilityUsesSeveralEvidenceInputs() {
    val eligible = generator.qualityEligibility(establishedProfile())
    assertTrue(eligible.thresholdEligible)
    assertTrue(eligible.reasons.size >= 4)
    val painLimited = generator.qualityEligibility(establishedProfile(pain = 0.4))
    assertFalse(painLimited.thresholdEligible)
  }

  @Test fun highRecentEffortPreventsQualityProgression() {
    assertFalse(establishedProfile(averageRpe = 8.0).let(generator::qualityEligibility).thresholdEligible)
  }

  @Test fun intervalEligibilityRequiresStrongerEvidence() {
    assertTrue(generator.qualityEligibility(establishedProfile()).intervalEligible)
    assertFalse(generator.qualityEligibility(establishedProfile(confidence = ProfileConfidence.MEDIUM)).intervalEligible)
  }

  @Test fun longRunStartsNearCapabilityProgressesAndDropsForRecovery() {
    val plan = generator.generate(goal(GoalDistance.TEN_K), profile(longest = 4_800.0))
    val distances = plan.weeks.map(::longRunDistance)
    assertTrue(distances[0]!! - 4_800.0 <= 750.0)
    assertTrue(distances[1]!! > distances[0]!!)
    assertTrue(distances[3]!! < distances[2]!!)
    assertTrue(distances.take(3).all { it!! < 10_000.0 })
  }

  @Test fun unscheduledPlanCreatesFourWeekForecast() {
    assertEquals(4, generator.generate(goal(), profile()).weeks.size)
  }

  @Test fun planCompletionMilestoneOwnsTheForecastHorizon() {
    val date = LocalDate.parse("2026-10-17")
    val plan = generator.generate(goal(), profile(), completionMilestone = scheduledMilestone(date))
    assertEquals(date, plan.completionMilestone?.scheduledDate)
    assertEquals(date, plan.weeks.flatMap { it.workouts }.single { it.workout.purpose == WorkoutPurpose.GOAL_COMPLETION }.workout.date)
  }

  @Test fun fixedClockProducesDeterministicPlan() {
    val first = generator.generate(goal(), profile())
    val second = generator.generate(goal(), profile())
    assertEquals(first, second)
    assertEquals(LocalDate.parse("2026-09-24"), first.startDate)
  }

  @Test fun everyWeekAndWorkoutIsExplainable() {
    val plan = generator.generate(goal(), profile())
    assertTrue(plan.weeks.all { it.explanation.isNotBlank() && it.reasons.isNotEmpty() })
    assertTrue(plan.weeks.flatMap { it.workouts }.all { it.generationReason.isNotBlank() && it.workout.explanation.isNotBlank() })
  }

  @Test fun activatingNewPlanSupersedesOnlyActivePlan() {
    val first = generator.generate(goal(), profile())
    val archived = first.copy(id = "archived", status = TrainingPlanStatus.ARCHIVED)
    val next = first.copy(id = "next")
    val result = activateTrainingPlan(listOf(first, archived), next)
    assertEquals(1, result.count { it.status == TrainingPlanStatus.ACTIVE })
    assertEquals(TrainingPlanStatus.SUPERSEDED, result.first { it.id == first.id }.status)
    assertEquals(TrainingPlanStatus.ARCHIVED, result.first { it.id == archived.id }.status)
  }

  private fun goal(
    distance: GoalDistance = GoalDistance.TEN_K,
    type: GoalType = GoalType.COMPLETE_DISTANCE,
    targetTime: Long? = null,
    customDistance: Double? = null,
  ) = TrainingGoal(
    id = "goal",
    distance = distance,
    customDistanceMeters = customDistance,
    goalType = type,
    targetTimeSeconds = targetTime,
    createdAt = now,
    status = GoalStatus.ACTIVE,
    startingPerformanceSnapshot = StartingPerformanceSnapshot(
      null, null, 10_000.0, 3_600, 4_800.0, 2_100, 3.0, 430.0, 145.0,
      ProfileConfidence.LOW, now,
    ),
    confidenceAtCreation = ProfileConfidence.LOW,
    assessmentAtCreation = GoalAssessment(GoalAssessmentLevel.LIMITED_DATA, listOf("Limited history")),
  )

  private fun scheduledMilestone(date: LocalDate = LocalDate.parse("2026-10-17")) = GoalCompletionMilestone(
    "goal",
    GoalCompletionReadiness.GOAL_COMPLETION_SCHEDULED,
    date,
    false,
    listOf("User selected the date."),
    now,
  )

  private fun readyMilestone() = GoalCompletionMilestone(
    "goal",
    GoalCompletionReadiness.GOAL_COMPLETION_READY,
    null,
    false,
    listOf("Performance evidence supports scheduling."),
    now,
  )

  private fun profile(
    totalRuns: Int = 6,
    runs: Int = 6,
    averageRuns: Double = 3.0,
    weeklyDistance: Double = 10_000.0,
    weeklyDuration: Long = 4_200,
    longest: Double? = 4_800.0,
    confidence: ProfileConfidence = ProfileConfidence.LOW,
    consistency: TrainingConsistency = TrainingConsistency.DEVELOPING,
    activeWeeks: Int = 3,
    completion: Double? = 0.9,
    pain: Double? = 0.0,
    averageRpe: Double? = 4.0,
    easyPace: Double? = 430.0,
  ) = AthletePerformanceProfile(
    totalCanonicalRuns = totalRuns,
    runsLast7Days = minOf(runs, 3),
    runsLast28Days = runs,
    activeWeeksLast8Weeks = activeWeeks,
    distanceLast7DaysMeters = weeklyDistance,
    durationLast7DaysSeconds = weeklyDuration,
    distanceLast28DaysMeters = weeklyDistance * 2.4,
    durationLast28DaysSeconds = (weeklyDuration * 2.4).toLong(),
    completedWeeklyVolumes = emptyList(),
    averageWeeklyDistanceLast4WeeksMeters = weeklyDistance,
    averageWeeklyDurationLast4WeeksSeconds = weeklyDuration,
    longestRunLast28Days = longest?.let { LongestRecentRun("long", now.minusSeconds(86_400), it, 2_100) },
    averageRunsPerWeek = averageRuns,
    currentRunningStreakWeeks = activeWeeks,
    consistency = consistency,
    consistencyReasons = listOf("Fixture"),
    performanceBenchmarks = emptyList(),
    recentPaceTrend = PaceTrend.STABLE,
    typicalEasyPace = easyPace?.let {
      ProfileBaseline(it, it - 20.0, it + 20.0, if (confidence <= ProfileConfidence.LOW) 2 else 6, confidence, listOf("Fixture"))
    },
    typicalEasyHeartRate = null,
    recentAverageHeartRate = 145.0,
    recentHeartRateTrend = ValueTrend.STABLE,
    vo2MaxLatest = null,
    vo2MaxPrevious = null,
    vo2MaxTrend = FitnessTrend.INSUFFICIENT_DATA,
    typicalEasyCadence = null,
    recentCadenceTrend = ValueTrend.INSUFFICIENT_DATA,
    feedback = FeedbackAggregates(6, averageRpe, completion, 0.0, 0.0, pain, 0.0, 4.0),
    calculatedAt = now,
    profileConfidence = confidence,
    confidenceReasons = listOf("Fixture"),
    dataQualityNotes = emptyList(),
  )

  private fun establishedProfile(
    confidence: ProfileConfidence = ProfileConfidence.HIGH,
    pain: Double? = 0.0,
    averageRpe: Double? = 4.0,
  ) = profile(
    totalRuns = 20,
    runs = 12,
    averageRuns = 4.5,
    weeklyDistance = 30_000.0,
    weeklyDuration = 10_800,
    longest = 12_000.0,
    confidence = confidence,
    consistency = TrainingConsistency.CONSISTENT,
    activeWeeks = 6,
    completion = 0.9,
    pain = pain,
    averageRpe = averageRpe,
  )

  private fun List<TrainingWeek>.flattenTypes() = flatMap { week -> week.workouts.map { it.workout.type } }

  private fun hasAerobicMajority(week: TrainingWeek): Boolean = week.workouts.count { !isHard(it.workout.type) } > week.workouts.size / 2

  private fun isHard(type: WorkoutType) = type in setOf(WorkoutType.TEMPO_RUN, WorkoutType.THRESHOLD, WorkoutType.INTERVALS, WorkoutType.RACE_SPECIFIC)

  private fun longRunDistance(week: TrainingWeek): Double? = week.workouts
    .first { it.workout.type == WorkoutType.LONG_RUN }.workout.blocks.single().segments.single().completion
    .let { (it as? com.example.openruncoach.domain.workout.DistanceCompletion)?.meters }

  private fun com.example.openruncoach.domain.workout.WorkoutSegment.allTargets() =
    listOf(primaryIntensity) + secondaryIntensities

  private fun com.example.openruncoach.domain.workout.StructuredWorkout.singleDistance(): Double? =
    blocks.singleOrNull()?.segments?.singleOrNull()?.completion.let { (it as? DistanceCompletion)?.meters }

  private fun com.example.openruncoach.domain.workout.StructuredWorkout.singlePace(): PaceTarget? =
    blocks.singleOrNull()?.segments?.singleOrNull()?.allTargets()?.filterIsInstance<PaceTarget>()?.firstOrNull()
}
