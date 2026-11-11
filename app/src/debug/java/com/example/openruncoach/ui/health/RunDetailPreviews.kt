package com.example.openruncoach.ui.health

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.example.openruncoach.domain.*
import com.example.openruncoach.domain.metrics.*
import com.example.openruncoach.domain.route.*
import com.example.openruncoach.domain.workout.*
import com.example.openruncoach.domain.coaching.CoachingEngine
import com.example.openruncoach.theme.OpenRunCoachTheme
import com.example.openruncoach.ui.home.HomeDayState
import com.example.openruncoach.ui.home.HomePreviewData
import java.time.LocalDate

/** Synthetic debug read models only. No production repository uses these records. */
internal object RunDetailPreviewData {
  val clock = HomePreviewData.clock(HomeDayState.AfterRun)
  fun state(): RunDetailUiState {
    val run = HomePreviewData.run("detail-fixture", "2026-11-04", 2_980.0, 1_300).copy(sourceAppName = "Synthetic provider")
    val workout = HomePreviewData.workout("detail-plan", LocalDate.parse("2026-11-04"), WorkoutType.RECOVERY_RUN,
      3_000.0, PaceTarget(410.0,460.0),RpeTarget(2,3))
    fun metric(value: Double) = MetricValue(value,MetricSource.HEALTH_CONNECT,MetricConfidence.HIGH,sourceAppName="Synthetic provider")
    fun samples(values: List<Double>) = (0..260).map { i -> TimedMetricSample(run.startTime.plusSeconds(i*5L),values[(i*values.size/261).coerceAtMost(values.lastIndex)]) }
    val metrics = RunMetrics(run.healthConnectRecordId,run.startTime,run.endTime,
      MetricValue(run.durationSeconds,MetricSource.HEALTH_CONNECT,MetricConfidence.HIGH),
      averageHeartRate=metric(146.0), maxHeartRate=metric(158.0),averageCadence=metric(168.0),maxCadence=metric(174.0),
      elevationGainMeters=metric(24.0),vo2Max=metric(41.0),averagePowerWatts=metric(180.0),
      series=RunTimeSeries(heartRate=samples(listOf(132.0,139.0,143.0,147.0,150.0,152.0,151.0,154.0,156.0,158.0,155.0)),
        speed=samples(listOf(2.19,2.23,2.27,2.30,2.28,2.32,2.30,2.33,2.35,2.32,2.35)),
        cadence=samples(listOf(160.0,164.0,166.0,168.0,169.0,170.0,168.0,170.0,171.0,174.0,170.0))),generatedAt=clock.instant())
    val feedback = RunFeedback("feedback-fixture",run.physicalRunId,2,CardioEffort.EASY,MuscleFatigue.NONE,PainLevel.NONE,
      CompletionStatus.COMPLETED,4,"Comfortable and relaxed.",run.endTime,run.endTime)
    val execution = WorkoutExecutionResult(workout.id,run.healthConnectRecordId,WorkoutExecutionStatus.COMPLETED_AS_PLANNED,
      WorkoutComparisonBasis.ORIGINAL_PRESCRIPTION,3000.0,null,run.distanceMeters,run.durationSeconds,null,"Fixture execution.",run.endTime)
    val response = WorkoutResponseEvaluator().evaluate(workout,null,execution,run,feedback,metrics)
    val route = HomePreviewData.state(HomeDayState.AfterRun).todaySources!!.route!!.route.copy(runId=run.physicalRunId,
      points=listOf(0.0 to 0.0,.0001 to .0005,.0008 to .0008,.0010 to .0002,.0003 to -.0001,0.0 to 0.0).mapIndexed { i,p ->
        RunRoutePoint(i,run.startTime.plusSeconds(i*260L),p.first,p.second) })
    val splits = listOf(444.0,434.0,430.0).mapIndexed { i,pace -> RunSplit(run.physicalRunId,i+1,RunSplitDerivation.SOURCE_LAP,
      run.startTime.plusSeconds(i*434L),run.startTime.plusSeconds((i+1)*434L),if(i==2)980.0 else 1000.0,
      pace*(if(i==2).98 else 1.0),pace,146.0+i,168.0,i==2,MetricConfidence.HIGH) }
    return RunDetailUiState(run=run,runLoaded=true,feedback=feedback,feedbackLoaded=true,form=FeedbackForm("2",readiness="4",notes=feedback.notes!!),
      metrics=metrics,charts=RunChartPreparer.prepare(metrics,canonicalDurationSeconds=run.durationSeconds,canonicalDistanceMeters=run.distanceMeters),
      plannedWorkout=workout,associationLoaded=true,workoutExecution=execution,workoutResponse=response,
      coachingDecision=CoachingEngine(clock).analyze(run,listOf(run),feedback,execution,response),routeData=RunRouteData(route,splits),
      splitDeltasSecondsPerKm=splits.associate { it.ordinal to (it.averagePaceSecondsPerKm-run.averagePaceSecondsPerKm!!) })
  }
}

@Preview(name="Run detail light",widthDp=390,heightDp=844,uiMode=Configuration.UI_MODE_NIGHT_NO)
@Composable private fun RunLight() { OpenRunCoachTheme { RunDetailContent(RunDetailPreviewData.state(),RunDetailPreviewData.clock) } }
@Preview(name="Run detail dark",widthDp=390,heightDp=844,uiMode=Configuration.UI_MODE_NIGHT_YES)
@Composable private fun RunDark() { OpenRunCoachTheme { RunDetailContent(RunDetailPreviewData.state(),RunDetailPreviewData.clock) } }
@Preview(name="Run detail 200%",widthDp=390,heightDp=844,fontScale=2f)
@Composable private fun RunLarge() { OpenRunCoachTheme { RunDetailContent(RunDetailPreviewData.state(),RunDetailPreviewData.clock) } }
