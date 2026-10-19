package com.example.openruncoach.data.diagnostics

import android.content.Context
import android.net.Uri
import android.os.Build
import androidx.health.connect.client.HealthConnectClient
import androidx.room.withTransaction
import com.example.openruncoach.BuildConfig
import com.example.openruncoach.OpenRunCoachEnvironment
import com.example.openruncoach.data.*
import com.example.openruncoach.data.samsunghealth.SamsungHealthDataSource
import com.example.openruncoach.data.samsunghealth.SamsungMatchSelectionRepository
import com.example.openruncoach.data.toActualSummary
import com.example.openruncoach.domain.RunActualResolver
import com.example.openruncoach.domain.RunSummary
import com.example.openruncoach.domain.aggregateActualPaceSecondsPerKm
import com.example.openruncoach.domain.athlete.AthleteStateBuilder
import com.example.openruncoach.domain.coaching.CoachingEngine
import com.example.openruncoach.domain.evidence.CanonicalTrainingEvidence
import com.example.openruncoach.domain.evidence.TrainingEvidencePolicy
import com.example.openruncoach.domain.evidence.TrainingEvidencePurpose
import com.example.openruncoach.domain.goal.GoalStrategyResolver
import com.example.openruncoach.domain.metrics.RunContinuityEvaluator
import com.example.openruncoach.domain.metrics.RunTimeSeries
import com.example.openruncoach.domain.metrics.metricValues
import com.example.openruncoach.domain.route.RunPacingSummaryCalculator
import com.example.openruncoach.domain.workout.WorkoutResponse
import com.example.openruncoach.domain.workout.WorkoutType
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

data class DiagnosticDumpResult(val applicationTableCount: Int, val exportedTableCount: Int, val optionalErrorCount: Int)

/** Export-only support service. Captures the environment at construction, including its database. */
class DiagnosticDumpRepository(context: Context, private val environment: OpenRunCoachEnvironment) {
  private val context = context.applicationContext

  suspend fun export(uri: Uri, mode: DumpPrivacyMode, progress: (String) -> Unit): DiagnosticDumpResult = withContext(Dispatchers.IO) {
    progress("Preparing dump…")
    val scratch = File(context.cacheDir, "diagnostic-dumps").apply { mkdirs() }
    val archiveFile = File.createTempFile("dump-", ".zip", scratch)
    try {
      val result = writeArchive(archiveFile, scratch, mode, progress)
      progress("Saving ZIP…")
      currentCoroutineContext().ensureActive()
      val output = checkNotNull(context.contentResolver.openOutputStream(uri, "wt")) { "Destination unavailable" }
      output.use { destination -> archiveFile.inputStream().buffered().use { it.copyTo(destination) } }
      result
    } finally { archiveFile.delete() }
  }

  internal suspend fun writeArchive(file: File, scratch: File, mode: DumpPrivacyMode, progress: (String) -> Unit): DiagnosticDumpResult {
    val created = Instant.now()
    var result: DiagnosticDumpResult? = null
    DumpArchive(file.outputStream().buffered(), scratch, mode).use { archive ->
      // DataStore/provider permission APIs are independent stores, sampled once and timestamped.
      // Simulation never reads production preferences, provider permissions, or candidate selections.
      val settingsExported = archive.optional("settings") {
        archive.json("settings.json", mapOf(
          "sampledAt" to Instant.now(),
          "preferredWorkoutSourcePackage" to if (environment.isSimulation) null else RunSettingsRepository(context).preferredSourcePackage.first(),
          "units" to "metric",
          "simulationMode" to environment.isSimulation,
          "note" to if (environment.isSimulation) "Production DataStore settings are not read in simulation." else "Only user-visible behavior settings are allowlisted; map provider URLs and build credentials are excluded.",
        ))
      }
      if (settingsExported == null) archive.json("settings.json", mapOf("status" to "UNAVAILABLE", "errorSection" to "settings"))
      exportProviderStatus(archive)
      val database = environment.database
      // All raw SQL and suspend DAO reads participate in the SAME Room transaction/connection.
      // There are no DML statements or mutating repository calls. The transaction reserves the
      // Room writer connection, so concurrent app writes wait until the snapshot is complete.
      // ZIP creation is local; the potentially slow SAF destination is opened after the transaction.
      database.withTransaction {
        val snapshotStarted = Instant.now()
        val snapshotClock = Clock.fixed(environment.clock.instant(), environment.clock.zone)
        val snapshotContext = currentCoroutineContext()
        val raw = DatabaseTableExporter(database.openHelper.readableDatabase) { snapshotContext.ensureActive() }
        val tables = raw.tables()
        archive.json("manifest.json", manifest(created, snapshotStarted, snapshotClock, mode, database.openHelper.readableDatabase.version))
        archive.entry("README.txt") { it.write(readme(created, mode)) }
        archive.json("capabilities.json", mapOf("features" to CAPABILITIES,
          "isolatedSimulationAvailable" to BuildConfig.DEBUG, "simulationMode" to environment.isSimulation))
        archive.json("database/schema.json", raw.schema(tables))
        progress("Exporting database…")
        val counts = linkedMapOf<String, Long>()
        tables.forEach { table ->
          currentCoroutineContext().ensureActive()
          archive.optional("database/tables/${table.name}") {
            archive.entry("database/tables/${tableFileName(table.name)}.jsonl") { writer ->
              counts[table.name] = raw.export(table, writer, mode)
            }
          }
        }
        progress("Exporting run data…")
        val runs = archive.optional("resolved/run_catalog") { database.runDao().getAllRuns() }.orEmpty().mapNotNull { row ->
          archive.optional("resolved/run/${archiveId(row.healthConnectRecordId)}") { row.toRunSummary() }
        }.sortedBy { it.healthConnectRecordId }
        val canonicalRuns = runs.filter { it.isCanonical }.map { run ->
          archive.optional("resolved/actual/${archiveId(run.healthConnectRecordId)}") {
            database.runDao().getActualRun(run.healthConnectRecordId)?.toActualSummary()
          } ?: RunActualResolver.resolve(run)
        }
        val members = runs.groupBy { it.physicalRunId }
        val associations = archive.optional("resolved/association_catalog") { database.coachingWorkflowDao().getAllAssociations() }
          .orEmpty().sortedBy { it.plannedWorkoutId }
        val associationsByPhysical = associations.associateBy { it.physicalRunId }
        val responseRepository = WorkoutResponseRepository(database)
        val responses = mutableListOf<WorkoutResponse>()
        val summaries = mutableListOf<Map<String, Any?>>()
        val semanticChecks = mutableListOf<IntegrityCheck>()
        canonicalRuns.forEachIndexed { index, run ->
          currentCoroutineContext().ensureActive()
          progress("Exporting run data… ${index + 1}/${canonicalRuns.size}")
          val association = associationsByPhysical[run.physicalRunId]
          val title = archive.optional("runs/${archiveId(run.healthConnectRecordId)}/title") {
            association?.let { database.coachingWorkflowDao().getWorkout(it.plannedWorkoutId) }
              ?.let { StructuredWorkoutPersistenceMapper.toDomain(it).name }
          }
          val summary = mapOf(
            "canonicalRunId" to run.healthConnectRecordId,
            "physicalRunId" to run.physicalRunId,
            "directory" to "runs/${archiveId(run.healthConnectRecordId)}",
            "run" to run,
            "canonicalActual" to canonicalActual(run),
            "sourceRecords" to members[run.physicalRunId].orEmpty(),
            "association" to association,
            "associationStatus" to if (association == null) "UNASSOCIATED" else "ASSOCIATED",
            "title" to title,
            "associatedWorkoutId" to association?.plannedWorkoutId,
            "executionId" to archive.optional("runs/${archiveId(run.healthConnectRecordId)}/execution_id") {
              database.coachingWorkflowDao().getExecutionForRun(run.healthConnectRecordId)?.plannedWorkoutId
            },
          )
          summaries += summary
          archive.optional("runs/${archiveId(run.healthConnectRecordId)}") {
            exportRun(archive, raw, run, summary, responseRepository, semanticChecks)?.let(responses::add)
          }
        }
        archive.optional("resolved/canonical_runs") { archive.json("resolved/canonical_runs.json", summaries) }
        archive.json("resolved/workout_associations.json", associations)
        archive.json("resolved/workout_responses.json", responses.sortedBy { it.runId })
        archive.json("providers/canonical_matching.json", mapOf(
          "note" to "Persisted physical groups and canonical match decisions only; no candidate search or canonical rebuild was run.",
          "groups" to members.toSortedMap(),
        ))
        exportResolvedState(archive, raw, canonicalRuns, responses, snapshotClock)
        val integrityExported = archive.optional("integrity") {
          val checker = DumpIntegrityChecker(raw)
          val feedbackConflicts = RunFeedbackRepository(database).getAllResolutions().filter { it.legacyConflict }
          archive.json("integrity.json", mapOf("checks" to (checker.checks() + semanticChecks), "counts" to checker.counts(),
            "feedbackOwnershipConflicts" to feedbackConflicts, "repairsPerformed" to false))
        }
        if (integrityExported == null) archive.json("integrity.json", mapOf("status" to "UNAVAILABLE", "errorSection" to "integrity", "repairsPerformed" to false))
        archive.json("summary.json", mapOf(
          "applicationTableCount" to tables.size,
          "exportedTableCount" to counts.size,
          "tableRowCounts" to counts,
          "tableFiles" to tables.associate { it.name to "database/tables/${tableFileName(it.name)}.jsonl" },
          "canonicalRunCount" to canonicalRuns.size,
          "providerRecordCount" to counts["runs"],
          "resolvedProviderRecordCount" to runs.size,
          "resolvedResponseCount" to responses.size,
          "optionalErrorCount" to archive.errors.size,
        ))
        result = DiagnosticDumpResult(tables.size, counts.size, archive.errors.size)
      }
      progress("Finalizing ZIP…")
      archive.json("errors.json", archive.errors)
    }
    return requireNotNull(result)
  }

  private suspend fun exportRun(
    archive: DumpArchive,
    raw: DatabaseTableExporter,
    run: RunSummary,
    summary: Map<String, Any?>,
    responses: WorkoutResponseRepository,
    semanticChecks: MutableList<IntegrityCheck>,
  ): WorkoutResponse? {
    val base = "runs/${archiveId(run.healthConnectRecordId)}"
    val database = environment.database
    val workflow = database.coachingWorkflowDao()
    val association = workflow.getAssociationForPhysicalRun(run.physicalRunId)
    val execution = archive.optional("$base/execution") { workflow.getExecutionForRun(run.healthConnectRecordId)?.toDomain() }
    val workoutRecord = archive.optional("$base/prescription_record") { association?.let { workflow.getWorkout(it.plannedWorkoutId) } }
    val workout = archive.optional("$base/prescription") { workoutRecord?.let(StructuredWorkoutPersistenceMapper::toDomain) }
    archive.json("$base/association.json", mapOf("canonicalRunId" to run.healthConnectRecordId,
      "physicalRunId" to run.physicalRunId, "associationStatus" to if (association == null) "UNASSOCIATED" else "ASSOCIATED",
      "association" to association, "plannedWorkout" to workout,
      "plannedWorkoutLifecycleState" to workoutRecord?.workout?.lifecycleState, "execution" to execution))
    archive.json("$base/execution.json", execution)
    val checkout = archive.optional("$base/checkout_resolution") { RunFeedbackRepository(database).getResolution(run.physicalRunId) }
    archive.json("$base/checkout.json", checkout?.authoritativeFeedback)
    archive.json("$base/feedback_ownership.json", checkout)
    archive.json("$base/check_in.json", association?.let {
      raw.queryRows("SELECT * FROM pre_run_check_ins WHERE plannedWorkoutId = ?", arrayOf(it.plannedWorkoutId)).firstOrNull()
    })
    archive.json("$base/adjustment.json", association?.let { workflow.getAdjustment(it.plannedWorkoutId) })
    val metrics = archive.optional("$base/metrics") { RunMetricsRepository(database.runMetricsDao()).getRunMetrics(run.healthConnectRecordId) }
    archive.json("$base/summary.json", summary + mapOf(
      "enrichmentMeasurements" to mapOf("distanceMeters" to metrics?.distanceMeters,
        "durationSeconds" to metrics?.durationSeconds, "averagePaceSecondsPerKm" to metrics?.averagePaceSecondsPerKm),
    ))
    archive.json("$base/training_evidence.json", mapOf(
      "physicalRunId" to run.physicalRunId,
      "policy" to "TrainingEvidencePolicy",
      "minimumStandaloneSessionSeconds" to TrainingEvidencePolicy.MINIMUM_STANDALONE_SESSION_SECONDS,
      "decisions" to CanonicalTrainingEvidence.evaluate(run, metrics, checkout?.authoritativeFeedback, execution, workout),
    ))
    archive.json("$base/metrics.json", metrics?.let {
      DumpJson.gson.toJsonTree(it.copy(series = RunTimeSeries())).asJsonObject.apply {
        remove("series")
        add("seriesFiles", DumpJson.gson.toJsonTree(mapOf("speed" to "speed.jsonl", "heartRate" to "heart_rate.jsonl",
          "cadence" to "cadence.jsonl", "power" to "power.jsonl")))
        add("resolvedSeriesCounts", DumpJson.gson.toJsonTree(mapOf("speed" to it.series.speed.size,
          "heartRate" to it.series.heartRate.size, "cadence" to it.series.cadence.size, "power" to it.series.power.size)))
      }
    })
    val routeSnapshot = archive.optional("$base/route_resolution") {
      val stored = database.runRouteDao().getPhysicalRoute(run.healthConnectRecordId) ?: return@optional null
      val resolved = RunRouteRepository(database.runRouteDao()).get(run.physicalRunId)
      resolved to mapOf("selectedStorageOwnerRunId" to stored.route.runId, "physicalRunId" to stored.physicalRunId,
        "source" to stored.route.source, "sourceAppName" to stored.route.sourceAppName,
        "storedPointCount" to stored.points.size, "resolvedPointCount" to resolved?.route?.points?.size,
        "storedSplitCount" to stored.splits.size, "persistedSplitCount" to stored.splits.size,
        "resolvedSplitCount" to resolved?.splits?.size, "splitDerivationSource" to resolved?.splitDerivationSource)
    }
    val routeData = routeSnapshot?.first
    archive.json("$base/provenance.json", mapOf(
      "canonicalRunId" to run.healthConnectRecordId,
      "summary" to mapOf("sourcePackage" to run.sourcePackageName, "sourceAppName" to run.sourceAppName,
        "selectionReason" to run.canonicalSelectionReason),
      "metrics" to metrics?.metricValues(),
      "route" to routeData?.route?.let { mapOf("source" to it.source, "sourceAppName" to it.sourceAppName, "confidence" to it.confidence, "physicalRunId" to it.runId) },
      "routeStorage" to routeSnapshot?.second,
    ))
    archive.optional("$base/route") {
      archive.entry("$base/route.json") { writer ->
        val route = routeData?.route
        if (route == null) writer.write("null") else {
          val metadata = DumpJson.gson.toJsonTree(route.copy(points = emptyList())).asJsonObject
          metadata.remove("points")
          metadata.addProperty("pointCount", route.points.size)
          metadata.addProperty("coordinateSystem", if (archive.mode == DumpPrivacyMode.FULL) "WGS84" else "local_relative_meters")
          val safeMetadata = DumpPrivacy.sanitize(metadata, archive.mode).asJsonObject
          writer.write(safeMetadata.toString().dropLast(1))
          writer.write(",\"points\":[")
          val projection = RelativeRouteCoordinates()
          route.points.forEachIndexed { index, point ->
            if (index > 0) writer.write(",")
            val fields = linkedMapOf<String, Any?>("sequence" to point.sequence, "timestamp" to point.timestamp,
              "altitudeMeters" to point.altitudeMeters, "horizontalAccuracyMeters" to point.horizontalAccuracyMeters,
              "verticalAccuracyMeters" to point.verticalAccuracyMeters)
            if (archive.mode == DumpPrivacyMode.FULL) {
              fields["latitude"] = point.latitude; fields["longitude"] = point.longitude
            } else fields.putAll(projection.point(point.latitude, point.longitude))
            DumpJson.write(writer, fields, archive.mode)
          }
          writer.write("]}")
        }
      }
    }
    archive.optional("$base/splits") {
      val pacing = RunPacingSummaryCalculator.summarize(run, routeData)
      val distance = pacing.actualDistanceMeters
      val averagePace = pacing.wholeRunAveragePaceSecondsPerKm
      val halves = pacing.equalDistanceHalves
      semanticChecks += wholeRunPaceIntegrity(run.healthConnectRecordId, run.averagePaceSecondsPerKm, averagePace)
      archive.json("$base/splits.json", mapOf(
        "persistedSplitCount" to routeData?.persistedSplitCount,
        "resolvedSplitCount" to routeData?.splits?.size,
        "splitDerivationSource" to routeData?.splitDerivationSource,
        "actualDistanceMeters" to distance,
        "wholeRunAveragePaceSecondsPerKm" to averagePace,
        "baselineSource" to pacing.baselineSource,
        "baselineProvenance" to run.actualProvenance,
        "splits" to routeData?.splits,
        "deltaVsAverageSecondsPerKm" to routeData?.splits?.map { split ->
          mapOf("ordinal" to split.ordinal, "deltaSecondsPerKm" to pacing.deltasSecondsPerKm[split.ordinal])
        },
        "equalDistanceHalves" to halves?.let { mapOf("evidence" to it,
          "firstHalfPaceSecondsPerKm" to it.firstHalfPaceSecondsPerKm, "secondHalfPaceSecondsPerKm" to it.secondHalfPaceSecondsPerKm,
          "deltaSecondsPerKm" to it.deltaSecondsPerKm, "direction" to it.direction) },
      ))
    }
    archive.optional("$base/series") {
      mapOf("speed" to "SPEED", "heart_rate" to "HEART_RATE", "cadence" to "CADENCE", "power" to "POWER").forEach { (file, type) ->
        archive.entry("$base/$file.jsonl") { writer ->
          raw.forEachRow("SELECT * FROM run_metric_samples WHERE runId = ? AND metricType = ? ORDER BY timestampEpochMillis, source",
            arrayOf(run.healthConnectRecordId, type)) { row -> DumpJson.write(writer, row, archive.mode); writer.write("\n") }
        }
      }
      archive.jsonl("$base/elevation.jsonl", routeData?.route?.let { route -> route.points.filter { it.altitudeMeters != null }.map {
        mapOf("timestamp" to it.timestamp, "altitudeMeters" to it.altitudeMeters, "source" to route.source, "sourceAppName" to route.sourceAppName)
      } }.orEmpty())
    }
    archive.optional("$base/continuity") {
      archive.json("$base/continuity.json", mapOf("result" to RunContinuityEvaluator().evaluate(metrics),
        "resolvedSpeedSampleCount" to metrics?.series?.speed?.size, "resolvedCadenceSampleCount" to metrics?.series?.cadence?.size,
        "storedSpeedSampleCount" to database.runMetricsDao().countSamples(run.healthConnectRecordId, "SPEED"),
        "storedCadenceSampleCount" to database.runMetricsDao().countSamples(run.healthConnectRecordId, "CADENCE"),
        "note" to "Production evaluator represents continuity as an enum; no separate confidence or evidence object is stored."))
    }
    archive.optional("$base/provider_selection") {
      archive.json("$base/provider_selection.json", mapOf("sampledAt" to Instant.now(), "selectedSamsungCandidateId" to
        if (environment.isSimulation) null else SamsungMatchSelectionRepository(context).selectedCandidateId(run.healthConnectRecordId),
        "note" to "Saved inspector choice only; candidate records are not fetched or rematched."))
    }
    val response = archive.optional("$base/response") { responses.getForRun(run.healthConnectRecordId) }
    archive.json("$base/response.json", response)
    return response
  }

  private suspend fun exportResolvedState(archive: DumpArchive, raw: DatabaseTableExporter, runs: List<RunSummary>, responses: List<WorkoutResponse>, snapshotClock: Clock) {
    val database = environment.database
    archive.optional("resolved/evidence_contributors") {
      val feedbackRepository = RunFeedbackRepository(database)
      val feedback = feedbackRepository.getAllResolutions()
      archive.json("resolved/feedback_ownership.json", feedback)
      val feedbackByPhysical = feedback.associate { it.physicalRunId to it.authoritativeFeedback }
      val workflow = database.coachingWorkflowDao()
      val executionByPhysical = workflow.getAllExecutions().associateBy { it.physicalRunId }
      val evidence = runs.map { run ->
        val association = workflow.getAssociationForPhysicalRun(run.physicalRunId)
        val workout = association?.let { workflow.getWorkout(it.plannedWorkoutId) }?.let(StructuredWorkoutPersistenceMapper::toDomain)
        val metrics = RunMetricsRepository(database.runMetricsDao()).getRunMetrics(run.healthConnectRecordId)
        val decisions = CanonicalTrainingEvidence.evaluate(run, metrics, feedbackByPhysical[run.physicalRunId],
          executionByPhysical[run.physicalRunId]?.toDomain(), workout)
        mapOf("physicalRunId" to run.physicalRunId, "canonicalRunId" to run.healthConnectRecordId,
          "canonicalActual" to canonicalActual(run),
          "startTime" to run.startTime, "inLast28Days" to (!run.startTime.isBefore(snapshotClock.instant().minusSeconds(28L * 86400)) &&
            !run.startTime.isAfter(snapshotClock.instant())), "decisions" to decisions)
      }
      archive.json("resolved/training_evidence.json", mapOf("evaluatedAt" to snapshotClock.instant(), "runs" to evidence,
        "note" to "Current policy decisions. The saved profile may predate this policy; dump creation does not recalculate or persist it."))
    }
    val profile = archive.optional("resolved/performance_profile") { environment.profileRepository().getProfile() }
    archive.json("resolved/performance_profile.json", profile)
    val state = archive.optional("resolved/athlete_state") { profile?.let { AthleteStateBuilder().build(it, runs, responses) } }
    archive.json("resolved/athlete_state.json", state)
    archive.json("resolved/athlete_state_context.json", mapOf(
      "basis" to "Production AthleteStateBuilder using the saved performance profile and responses resolved from saved inputs.",
      "profileCalculatedAt" to profile?.calculatedAt,
      "resolvedResponseCount" to responses.size,
      "earlierExportErrors" to archive.errors.map { it.section },
      "note" to "AthleteState and WorkoutResponse are not persisted independently. No profile recalculation/persistence or AthleteState rebuild workflow was run. A missing saved profile produces null state.",
    ))
    val goal = archive.optional("resolved/active_goal") { TrainingGoalRepository(database, environment.profileRepository()).getActiveGoal() }
    archive.optional("resolved/active_goal_serialization") { archive.json("resolved/active_goal.json", goal) }
    archive.optional("resolved/goals") { archive.json("resolved/goals.json", database.trainingGoalDao().getAllGoals().map { it.toDomain() }) }
    val plans = TrainingPlanRepository(database)
    val plan = archive.optional("resolved/active_plan") { plans.getActivePlanSnapshot() }
    archive.json("resolved/active_plan.json", plan)
    archive.optional("resolved/plans") {
      // Plans are loaded one at a time; include superseded and archived state.
      database.trainingPlanDao().getAllPlans().sortedBy { it.id }.forEach { saved ->
        archive.optional("resolved/plans/${archiveId(saved.id)}") {
          archive.json("resolved/plans/${archiveId(saved.id)}.json", plans.getPlanSnapshot(saved.id))
        }
      }
    }
    archive.optional("resolved/prescriptions") {
      // Identity list is small. Full structured blocks/targets/cues are mapped one workout at a time.
      raw.queryRows("SELECT id FROM planned_workouts ORDER BY id").forEach { row ->
        val id = row["id"] as String
        archive.optional("resolved/workouts/${archiveId(id)}") {
          val record = database.coachingWorkflowDao().getWorkout(id)
          archive.json("resolved/workouts/${archiveId(id)}.json", mapOf(
            "workoutId" to id, "lifecycleState" to record?.workout?.lifecycleState,
            "isPreview" to record?.workout?.isPreview, "prescription" to record?.let(StructuredWorkoutPersistenceMapper::toDomain)))
        }
      }
    }
    archive.optional("resolved/goal_strategy") { archive.json("resolved/goal_strategy.json", if (goal != null && state != null) GoalStrategyResolver().resolve(goal, state) else null) }
    val today = LocalDate.now(snapshotClock)
    val upcoming = plan?.weeks.orEmpty().flatMap { it.workouts }
      .filter { (it.workout.type != WorkoutType.REST || it.originalWorkout.type != WorkoutType.REST) &&
        it.executionResult == null && !it.workout.date.isBefore(today) }
      .sortedWith(compareBy({ it.workout.date }, { it.workout.name }))
    archive.optional("resolved/current_home_state") {
      val latest = runs.filter { it.relevance == com.example.openruncoach.domain.RunRelevance.ACTIVE }
        .sortedWith(compareByDescending<RunSummary> { it.startTime }.thenByDescending { it.healthConnectRecordId }).firstOrNull()
      val feedback = latest?.let { RunFeedbackRepository(database).getFeedbackForRun(it.physicalRunId) }
      val latestExecution = plan?.weeks.orEmpty().flatMap { it.workouts }.mapNotNull { it.executionResult }
        .firstOrNull { it.actualRunId == latest?.healthConnectRecordId }
      val decision = if (latest != null && feedback != null) CoachingEngine(snapshotClock).analyze(
        latest, runs, feedback, latestExecution, responses.firstOrNull { it.runId == latest.healthConnectRecordId },
      ) else null
      archive.json("resolved/current_home_state.json", mapOf(
        "date" to today, "activeGoal" to goal, "activePlanId" to plan?.id,
        "nextWorkout" to upcoming.firstOrNull(), "upcomingWorkouts" to upcoming.take(5),
        "latestCanonicalRun" to latest, "coachingDecision" to decision,
        "currentWeekActuals" to runs.filter { it.relevance == com.example.openruncoach.domain.RunRelevance.ACTIVE &&
          it.startTime.atZone(snapshotClock.zone).toLocalDate() in today.with(java.time.DayOfWeek.MONDAY)..today.with(java.time.DayOfWeek.SUNDAY)
        }.let { week -> mapOf("runs" to week.map { canonicalActual(it) },
          "averagePaceSecondsPerKm" to aggregateActualPaceSecondsPerKm(week)) },
        "athleteState" to state, "performanceProfile" to profile,
        "note" to "Semantic domain snapshot; plan executions are read as saved, without Home/Plan reconciliation.",
      ))
    }
  }

  private suspend fun exportProviderStatus(archive: DumpArchive) {
    if (environment.isSimulation) {
      listOf("health_connect", "samsung_health").forEach {
        archive.json("providers/$it.json", mapOf("status" to "NOT_QUERIED_SIMULATION", "note" to "Isolated synthetic database; production providers were not accessed."))
      }
      return
    }
    val healthExported = archive.optional("providers/health_connect") {
      val health = HealthConnectRepository(context)
      val available = health.availabilityStatus()
      val granted = if (available == HealthConnectClient.SDK_AVAILABLE) HealthConnectClient.getOrCreate(context).permissionController.getGrantedPermissions() else emptySet()
      archive.json("providers/health_connect.json", mapOf("sampledAt" to Instant.now(), "availabilityStatus" to available,
        "requiredPermissions" to health.requiredPermissions.sorted(), "grantedPermissions" to granted.sorted(), "readOnlyIntegration" to true))
    }
    if (healthExported == null) archive.json("providers/health_connect.json", mapOf("status" to "UNAVAILABLE", "errorSection" to "providers/health_connect"))
    val samsungExported = archive.optional("providers/samsung_health") {
      val samsung = SamsungHealthDataSource(context)
      val precheck = samsung.platformPrecheck()
      archive.json("providers/samsung_health.json", mapOf("sampledAt" to Instant.now(), "sdkLibraryLoaded" to true,
        "applicationDetection" to samsung.samsungHealthDetection(), "platformPrecheck" to precheck,
        "permissions" to if (precheck == null) samsung.grantedReadPermissions() else null,
        "integration" to "experimental enrichment and diagnostics"))
    }
    if (samsungExported == null) archive.json("providers/samsung_health.json", mapOf("status" to "UNAVAILABLE", "errorSection" to "providers/samsung_health"))
  }

  private fun manifest(created: Instant, snapshotStarted: Instant, snapshotClock: Clock, mode: DumpPrivacyMode, schemaVersion: Int): Map<String, Any?> = mapOf(
    "dumpFormatVersion" to DUMP_FORMAT_VERSION, "createdAt" to created, "privacyMode" to mode,
    "appVersion" to BuildConfig.VERSION_NAME, "versionCode" to BuildConfig.VERSION_CODE, "buildType" to BuildConfig.BUILD_TYPE,
    "databaseSchemaVersion" to schemaVersion, "androidVersion" to Build.VERSION.RELEASE, "androidApiLevel" to Build.VERSION.SDK_INT,
    "manufacturer" to Build.MANUFACTURER, "model" to Build.MODEL, "locale" to Locale.getDefault().toLanguageTag(),
    "timezone" to ZoneId.systemDefault().id, "databaseSnapshotStartedAt" to snapshotStarted,
    "applicationClockInstant" to snapshotClock.instant(), "applicationClockTimezone" to snapshotClock.zone.id,
    "simulationMode" to environment.isSimulation, "databaseScope" to if (environment.isSimulation) "isolated synthetic database" else "production local database",
    "databaseSnapshot" to "Single Room transaction, SELECT/PRAGMA and suspend DAO reads only. App writes wait for completion; no app data is changed.",
    "externalStatusSnapshot" to "Settings and provider permission status are sampled separately; see sampledAt. They cannot be atomic with Room.",
  )

  private fun readme(created: Instant, mode: DumpPrivacyMode) = """OpenRunCoach diagnostic dump — format $DUMP_FORMAT_VERSION
Created: $created
Privacy: $mode
This is an export-only diagnostic artifact, not a restorable database backup.
It contains health records and subjective feedback. Share only with intended reviewers.

manifest.json: environment/build information; simulation database is explicitly marked.
summary.json: discovered/exported table counts, row counts and file mapping.
settings.json: allowlisted user-visible settings; credentials and map provider URLs excluded.
capabilities.json: modest build capability inventory.
integrity.json: reporting-only relationship/whole-run semantic checks and route/sample counts.
errors.json: optional section failures; no exception messages or stack traces are copied.
database/schema.json: SQLite version, columns, PK order, nullability, FKs, indices, Room identity, views/triggers.
database/tables/*.jsonl: every discovered application table, one UTF-8 row per line, ordered by PK (all columns otherwise).
resolved/: canonical runs, authoritative associations, structured responses, saved profile, AthleteState,
  goals, plans, structured prescriptions, goal strategy and semantic Home state.
runs/<encoded-id>/: summary, provenance, metrics, route, splits, continuity, series,
  association/prescription, execution, check-in, adjustment, checkout and response.
  Folder IDs are URL-safe Base64 of the canonical provider-record ID; summary.json maps IDs to directories.
providers/: current availability/permission status, stored inspector selections in each run,
  and persisted canonical matching decisions/groups. No provider data refresh is performed.

Identity/relationships:
  runs.healthConnectRecordId = preserved provider record ID; physicalRunId = physical activity group.
  isCanonical selects the current summary representative; relevance preserves ignored activity.
  Source records are retained, never rematched for export. Summary source may differ from each
  metric source and the route enrichment source. provenance.json retains metric-level confidence,
  verification, secondary sources and reasons. Per-sample sources remain in JSONL.
  workout_run_associations links physicalRunId/actualRunId to plannedWorkoutId.
  Execution identity is plannedWorkoutId (the table primary key); there is no separate generated execution ID.
  workout_executions stores authoritative execution; pre_run_check_ins/workout_adjustments refer to workouts;
  Checkout domain runId is physicalRunId. run_feedback retains its legacy provider-row FK for storage:
  new writes use the preserved physical identity anchor (or a stable legacy member for custom IDs).
  feedback_ownership.json identifies the authoritative whole answer and preserved legacy conflicts.
  summary.json canonicalActual is the resolved RunSummary (physical/canonical IDs, canonical interval,
  distance, duration, duration/distance pace, source and per-value fallback provenance).
  enrichmentMeasurements and metrics.json retain provider distances/paces, including secondary values;
  they may legitimately differ from a corrected canonical summary and are never overwritten by export.
  splits.json uses canonicalActual for actualDistanceMeters, wholeRunAveragePaceSecondsPerKm and deltas;
  baselineSource/baselineProvenance identify that consumption. Equal halves use the same actual distance.
  integrity.json compares that consumed baseline with canonical actual pace at 0.001 s/km tolerance;
  it does not demand equality between raw provider distances and canonical distance.
  route.json retains existing raw-distance/calibration metadata and genuine route evidence.
  splits.json distinguishes persistedSplitCount, resolvedSplitCount and splitDerivationSource.
  Missing/invalid caches resolve through the production RouteSplitDeriver without persistence.
  training_evidence.json uses the production purpose-aware TrainingEvidencePolicy; History relevance
  is separate from frequency, baseline and capability eligibility. Actual volume/safety remain visible.
  Raw rows retain inconsistent/orphaned references for investigation.
  WorkoutResponse is derived with the production evaluator from those saved inputs, never persisted by export.
  AthleteState uses the production builder with the SAVED performance profile, with its original calculatedAt.
  No profile is refreshed; null saved profile means null AthleteState. Context is in athlete_state_context.json.
  Goal has no completion date; a plan owns its Completion Run milestone. Archived/superseded rows are retained.

Units: distance/altitude/accuracy/local x/y in meters, speed m/s, pace seconds per kilometre,
  elapsed duration seconds (fields explicitly ending Minutes are minutes), HR beats/min,
  cadence steps/min, power watts, energy kilocalories, VO2max per stored model.
Timestamps: domain Instant uses ISO-8601 UTC; raw *EpochMillis is Unix milliseconds;
  *EpochDay is LocalDate days since 1970-01-01. Manifest records device and application-clock zones.
Splits: stored production splits retain their derivation labels, partial status and confidence.
  Half-distance pacing and delta-vs-average use the production RunPacingSummaryCalculator; missing evidence is null.
Continuity: the production evaluator returns CONTINUOUS, INTERRUPTED or UNKNOWN, with actual sample counts.
Series: actual persisted HR/speed/cadence/power samples; elevation uses stored route altitudes. Empty means absent.
Metrics JSON links to sample files and reports resolved counts; sample lists are in adjacent JSONL and the raw sample table.
Route: production ownership/window clipping is applied to resolved data; raw rows retain the original stored points.

SHARE_SAFE: absolute coordinates are removed from BOTH route.json and raw run_route_points rows.
  Each route starts at a private first-valid-point origin; x is east, y is north, using a local
  equirectangular projection (Earth radius 6371000 m). Origin is never exported. Dateline crossing is wrapped.
  Invalid coordinates yield null x/y. Relative geometry, timing, altitude and accuracy remain available;
  local projection is approximate, especially over large distances/near poles. Shape may still be recognizable.
FULL: latitude/longitude remain in both raw and resolved routes after the explicit in-app GPS warning.
Both modes redact secret/identifier columns and recognized credential patterns, including embedded JSON.
  SHARE_SAFE also redacts recognizable GPS coordinate pairs in free text; arbitrary personal text is not anonymized.
  Current Room tables contain no credential store; DataStore is explicitly allowlisted.
  Free-text notes remain application data. Review personal notes before sharing.
SQLite internals are excluded (sqlite_*, android_metadata, Room modification log);
  room_master_table is represented in schema metadata. BLOBs are {type:BLOB, encoding:base64, value:...}.
No APK, raw SQLite/WAL file, account/device identifier, environment variables, build secrets,
  map URL, provider record refetch, authentication storage, logcat, exception stack or screenshot is included.
All database sections use one Room transaction, with the application clock frozen at snapshot start.
  Settings/provider statuses are separately timestamped samples.
Core database discovery, disk or destination failures fail the export; optional domain/provider failures
  appear in errors.json. JSON null means absent/unresolvable; consult errors.json to distinguish failures.
""".trimIndent()

  companion object {
    fun suggestedFilename(): String = "OpenRunCoach_Dump_" + DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss", Locale.ROOT).format(java.time.ZonedDateTime.now()) + ".zip"
    internal fun tableFileName(table: String): String = if (table.matches(Regex("[A-Za-z0-9_]+"))) table else "encoded-${archiveId(table)}"
    private val CAPABILITIES = listOf("Health Connect import", "Samsung enrichment", "canonical duplicate handling",
      "route import", "route map", "route-derived splits", "manual workout assignment", "pre-run check-in",
      "post-run checkout", "WorkoutResponse", "AthleteState", "performance profile", "goals", "structured workouts",
      "deterministic training plans", "goal strategy", "diagnostic dump")
  }
}

/** Raw provider measurements live alongside this resolved semantic, never inside it. */
private fun canonicalActual(run: RunSummary): Map<String, Any?> = mapOf(
  "physicalRunId" to run.physicalRunId,
  "canonicalRunId" to run.healthConnectRecordId,
  "startTime" to run.startTime, "endTime" to run.endTime,
  "distanceMeters" to run.distanceMeters,
  "durationSeconds" to run.durationSeconds.takeIf { it > 0 },
  "averagePaceSecondsPerKm" to run.averagePaceSecondsPerKm,
  "source" to mapOf("packageName" to run.sourcePackageName, "appName" to run.sourceAppName),
  "provenance" to run.actualProvenance,
)
