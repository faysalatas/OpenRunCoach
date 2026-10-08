package com.example.openruncoach.data.diagnostics

import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.openruncoach.OpenRunCoachEnvironment
import com.example.openruncoach.data.RunMetricsRepository
import com.example.openruncoach.data.RunRouteRepository
import com.example.openruncoach.data.StructuredWorkoutPersistenceMapper
import com.example.openruncoach.data.WorkoutResponseRepository
import com.example.openruncoach.data.WorkoutExecutionRepository
import com.example.openruncoach.data.TrainingGoalRepository
import com.example.openruncoach.data.TrainingPlanRepository
import com.example.openruncoach.data.local.*
import com.example.openruncoach.domain.goal.GoalDistance
import com.example.openruncoach.domain.goal.GoalType
import com.example.openruncoach.domain.goal.TrainingGoalDraft
import com.example.openruncoach.domain.goal.TrainingGoalFactory
import com.example.openruncoach.domain.metrics.*
import com.example.openruncoach.domain.plan.*
import com.example.openruncoach.domain.profile.ProfileConfidence
import com.example.openruncoach.domain.route.RunRoute
import com.example.openruncoach.domain.route.RunRoutePoint
import com.example.openruncoach.domain.workout.StructuredWorkoutSamples
import com.example.openruncoach.domain.workout.WorkoutType
import com.example.openruncoach.domain.workout.simpleDurationWorkout
import com.google.gson.JsonParser
import java.io.File
import java.io.StringWriter
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.zip.ZipFile
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DiagnosticDumpTest {
  private val context: Context = ApplicationProvider.getApplicationContext()

  @Test fun futureApplicationTableIsDiscoveredAndAllSqliteValueTypesStreamInPkOrder() = runBlocking {
    val database = database()
    try {
      val sqlite = database.openHelper.writableDatabase
      sqlite.execSQL("CREATE TABLE future_table (keyPart TEXT NOT NULL, sequence INTEGER NOT NULL, missing TEXT, amount REAL, text TEXT, payload BLOB, api_key TEXT DEFAULT 'hidden-default', PRIMARY KEY(keyPart, sequence))")
      sqlite.execSQL("INSERT INTO future_table VALUES ('b', 2, NULL, 1.25, 'Läufer 🏃', X'0001FF', 'hidden-token')")
      sqlite.execSQL("INSERT INTO future_table VALUES ('a', 3, NULL, 2.5, 'second', X'', 'hidden-token')")
      val raw = DatabaseTableExporter(sqlite)
      val tables = raw.tables()
      val future = tables.single { it.name == "future_table" }
      assertFalse(tables.any { it.name in setOf("room_master_table", "android_metadata", "sqlite_sequence") })
      assertEquals("\"keyPart\", \"sequence\"", DatabaseTableExporter.orderBy(future))
      assertEquals("[REDACTED]", future.columns.single { it.name == "api_key" }.defaultValue)
      val output = StringWriter()
      assertEquals(2L, raw.export(future, output, DumpPrivacyMode.FULL))
      val lines = output.toString().trim().lines().map { JsonParser.parseString(it).asJsonObject }
      assertEquals("a", lines[0]["keyPart"].asString)
      assertEquals(2L, lines[1]["sequence"].asLong)
      assertTrue(lines[1]["missing"].isJsonNull)
      assertEquals(1.25, lines[1]["amount"].asDouble, 0.0)
      assertEquals("Läufer 🏃", lines[1]["text"].asString)
      assertEquals("BLOB", lines[1]["payload"].asJsonObject["type"].asString)
      assertEquals("AAH/", lines[1]["payload"].asJsonObject["value"].asString)
      assertFalse(output.toString().contains("hidden-token"))
      assertEquals(output.toString(), StringWriter().also { raw.export(future, it, DumpPrivacyMode.FULL) }.toString())

      val scratch = File(context.cacheDir, "dump-enumeration-test").apply { mkdirs() }
      val archiveFile = File(scratch, "dump.zip")
      try {
        DiagnosticDumpRepository(context, environment(database)).writeArchive(archiveFile, scratch, DumpPrivacyMode.SHARE_SAFE) {}
        ZipFile(archiveFile).use { zip ->
          assertNotNull(zip.getEntry("openruncoach-dump/database/tables/future_table.jsonl"))
          val summary = json(zip, "summary.json").asJsonObject
          assertEquals(tables.size, summary["applicationTableCount"].asInt)
          assertEquals(tables.size, summary["exportedTableCount"].asInt)
        }
      } finally { scratch.deleteRecursively() }
    } finally { database.close() }
  }

  @Test fun shareSafeAndFullPipelineArchivesPreserveProvenanceAssociationAndReadOnlyState() = runBlocking {
    val database = database()
    val scratch = File(context.cacheDir, "dump-pipeline-test").apply { mkdirs() }
    try {
      seed(database)
      // Persist the ordinary production profile before exporting; the dump must leave it unchanged.
      environment(database).profileRepository().recalculate()
      val raw = DatabaseTableExporter(database.openHelper.readableDatabase)
      val before = storedState(raw)
      DumpPrivacyMode.entries.forEach { mode ->
        val archiveFile = File(scratch, "$mode.zip")
        database.withTransaction {
          val changesBefore = raw.queryRows("SELECT total_changes() AS count").single()["count"]
          DiagnosticDumpRepository(context, environment(database)).writeArchive(archiveFile, scratch, mode) {}
          assertEquals(changesBefore, raw.queryRows("SELECT total_changes() AS count").single()["count"])
        }
        assertEquals(before, storedState(raw))
        ZipFile(archiveFile).use { zip ->
          val manifest = json(zip, "manifest.json").asJsonObject
          assertTrue(manifest["simulationMode"].asBoolean)
          assertEquals("isolated synthetic database", manifest["databaseScope"].asString)
          assertEquals(DUMP_FORMAT_VERSION, manifest["dumpFormatVersion"].asInt)
          assertEquals(mode.name, manifest["privacyMode"].asString)
          assertTrue(json(zip, "errors.json").asJsonArray.toString(), json(zip, "errors.json").asJsonArray.isEmpty)
          val base = "runs/${archiveId(RUN_ID)}"
          val provenance = json(zip, "$base/provenance.json").asJsonObject
          assertEquals("Strava", provenance["summary"].asJsonObject["sourceAppName"].asString)
          assertEquals("SAMSUNG_HEALTH", provenance["route"].asJsonObject["source"].asString)
          assertEquals(RUN_ID, provenance["routeStorage"].asJsonObject["selectedStorageOwnerRunId"].asString)
          assertEquals(3, provenance["routeStorage"].asJsonObject["storedPointCount"].asInt)
          assertEquals("SAMSUNG_HEALTH", provenance["metrics"].asJsonObject["AVERAGE_HEART_RATE"].asJsonObject["source"].asString)
          val association = json(zip, "$base/association.json").asJsonObject
          assertEquals(RUN_ID, association["canonicalRunId"].asString)
          assertEquals("workout-1", association["association"].asJsonObject["plannedWorkoutId"].asString)
          assertEquals(RUN_ID, association["execution"].asJsonObject["actualRunId"].asString)
          val expected = WorkoutResponseRepository(database).getForRun(RUN_ID)
          val responseJson = StringWriter().also { DumpJson.write(it, expected, mode) }.toString()
          assertEquals(JsonParser.parseString(responseJson), json(zip, "$base/response.json"))
          assertFalse(json(zip, "resolved/athlete_state.json").isJsonNull)
          val route = json(zip, "$base/route.json").asJsonObject
          val points = route["points"].asJsonArray
          assertEquals(3, route["pointCount"].asInt)
          if (mode == DumpPrivacyMode.SHARE_SAFE) {
            assertFalse(points[0].asJsonObject.has("latitude"))
            assertFalse(points[0].asJsonObject.has("longitude"))
            assertEquals(0.0, points[0].asJsonObject["xMeters"].asDouble, 0.0)
            assertTrue(points[1].asJsonObject["xMeters"].asDouble > 0.0)
            // Check the WHOLE archive, especially the raw route table, for original coordinates.
            zip.entries().asSequence().forEach { entry ->
              val text = zip.getInputStream(entry).bufferedReader().use { it.readText() }
              assertFalse(entry.name, text.contains("52.123456"))
              assertFalse(entry.name, text.contains("13.654321"))
            }
          } else {
            assertEquals(52.123456, points[0].asJsonObject["latitude"].asDouble, 0.0)
            assertTrue(text(zip, "database/tables/run_route_points.jsonl").contains("13.654321"))
          }
        }
      }
    } finally { database.close(); scratch.deleteRecursively() }
  }

  @Test fun foreignKeyOrphanIsReportedAndNeverRepaired() = runBlocking {
    val database = database()
    try {
      val sqlite = database.openHelper.writableDatabase
      sqlite.execSQL("CREATE TABLE future_parent (id TEXT PRIMARY KEY)")
      sqlite.execSQL("CREATE TABLE future_child (id TEXT PRIMARY KEY, parentId TEXT REFERENCES future_parent(id))")
      sqlite.execSQL("PRAGMA foreign_keys = OFF")
      sqlite.execSQL("INSERT INTO future_child VALUES ('orphan', 'missing')")
      sqlite.execSQL("PRAGMA foreign_keys = ON")
      val raw = DatabaseTableExporter(sqlite)
      val before = raw.queryRows("SELECT * FROM future_child")
      val report = DumpIntegrityChecker(raw).checks().single { it.check == "foreign_key_orphans" }
      assertEquals("REVIEW", report.status)
      assertEquals(1, report.count)
      assertEquals("future_child", report.affectedIds.single()["table"])
      assertEquals(before, raw.queryRows("SELECT * FROM future_child"))
    } finally { database.close() }
  }

  @Test fun consistencyDiagnosticsResolveSplitsAndFeedbackConflictsWithoutMutatingAnyTable() = runBlocking {
    val database = database()
    val scratch = File(context.cacheDir, "dump-consistency-test").apply { mkdirs() }
    try {
      seed(database)
      val run = database.runDao().getRun(RUN_ID)!!.copy(distanceMeters = 1952.169,
        durationSeconds = 848, endTimeEpochMillis = START.plusSeconds(848).toEpochMilli())
      val fragmentId = "cab0974e-5b8d-4415-bf62-fbf30f810db6"
      database.runDao().upsertAll(listOf(run, run.copy(healthConnectRecordId = fragmentId, physicalRunId = fragmentId,
        durationSeconds = 54, endTimeEpochMillis = START.plusSeconds(54).toEpochMilli(), distanceMeters = 5.861,
        averagePaceSecondsPerKm = 9213.4447)))
      database.runFeedbackDao().insertFeedback(RunFeedbackEntity("legacy-conflict", "provider-copy", 9, "HARD", "MILD",
        "NONE", "COMPLETED", null, "Preserve legacy notes", START.toEpochMilli(), START.toEpochMilli()))
      RunMetricsRepository(database.runMetricsDao()).save(RunMetrics(RUN_ID, START, START.plusSeconds(848),
        MetricValue(848L, MetricSource.HEALTH_CONNECT, MetricConfidence.HIGH),
        distanceMeters = MetricValue(2001.5, MetricSource.HEALTH_CONNECT, MetricConfidence.HIGH,
          secondaryValue = 2001.5, secondarySource = MetricSource.SAMSUNG_HEALTH),
        averagePaceSecondsPerKm = MetricValue(424.0, MetricSource.DERIVED, MetricConfidence.HIGH), generatedAt = START))
      RunRouteRepository(database.runRouteDao()).saveForPhysicalRun(RunRoute("physical-1", MetricSource.SAMSUNG_HEALTH,
        "Samsung Health", MetricConfidence.HIGH, (0..400).map { i -> RunRoutePoint(i, START.plusMillis(i * 2120L),
          1952.169 * i / 400 / 6371008.8 * 180 / Math.PI, 0.0) }), emptyList(), "physical-1")
      val raw = DatabaseTableExporter(database.openHelper.readableDatabase)
      val before = storedState(raw)
      val archiveFile = File(scratch, "consistency.zip")
      database.withTransaction {
        val changesBefore = raw.queryRows("SELECT total_changes() AS count").single()["count"]
        DiagnosticDumpRepository(context, environment(database)).writeArchive(archiveFile, scratch, DumpPrivacyMode.SHARE_SAFE) {}
        assertEquals(changesBefore, raw.queryRows("SELECT total_changes() AS count").single()["count"])
      }
      assertEquals(before, storedState(raw))
      ZipFile(archiveFile).use { zip ->
        val base = "runs/${archiveId(RUN_ID)}"
        val splits = json(zip, "$base/splits.json").asJsonObject
        assertEquals(0, splits["persistedSplitCount"].asInt)
        assertEquals(2, splits["resolvedSplitCount"].asInt)
        assertEquals("ROUTE_DERIVED_ON_READ", splits["splitDerivationSource"].asString)
        val actual = json(zip, "$base/summary.json").asJsonObject["canonicalActual"].asJsonObject
        val actualPace = 848000.0 / 1952.169
        assertEquals(1952.169, actual["distanceMeters"].asDouble, 0.000001)
        assertEquals(848L, actual["durationSeconds"].asLong)
        assertEquals(actualPace, actual["averagePaceSecondsPerKm"].asDouble, 0.000001)
        assertEquals("com.strava", actual["source"].asJsonObject["packageName"].asString)
        val enrichment = json(zip, "$base/summary.json").asJsonObject["enrichmentMeasurements"].asJsonObject
        assertEquals(2001.5, enrichment["distanceMeters"].asJsonObject["value"].asDouble, 0.0)
        assertEquals("SAMSUNG_HEALTH", enrichment["distanceMeters"].asJsonObject["secondarySource"].asString)
        assertEquals(actualPace, splits["wholeRunAveragePaceSecondsPerKm"].asDouble, 0.000001)
        assertEquals("RESOLVED_CANONICAL_ACTUAL_DURATION_OVER_DISTANCE", splits["baselineSource"].asString)
        val rows = splits["splits"].asJsonArray
        val deltas = splits["deltaVsAverageSecondsPerKm"].asJsonArray
        rows.forEachIndexed { index, row ->
          assertEquals(row.asJsonObject["averagePaceSecondsPerKm"].asDouble - actualPace,
            deltas[index].asJsonObject["deltaSecondsPerKm"].asDouble, 0.000001)
        }
        val semantic = json(zip, "integrity.json").asJsonObject["checks"].asJsonArray
          .filter { it.asJsonObject["check"].asString == "whole_run_pace_vs_split_baseline" }
        assertTrue(semantic.isNotEmpty())
        assertTrue(semantic.all { it.asJsonObject["status"].asString == "PASS" })
        val feedback = json(zip, "$base/feedback_ownership.json").asJsonObject
        assertTrue(feedback["legacyConflict"].asBoolean)
        assertEquals("checkout-1", feedback["authoritativeFeedback"].asJsonObject["id"].asString)
        assertEquals(2, feedback["originalRecords"].asJsonArray.size())
        val decisions = json(zip, "runs/${archiveId(fragmentId)}/training_evidence.json").asJsonObject["decisions"].asJsonObject
        assertFalse(decisions["SESSION_FREQUENCY"].asJsonObject["eligible"].asBoolean)
        assertFalse(decisions["EASY_PACE"].asJsonObject["eligible"].asBoolean)
        assertTrue(decisions["ACTUAL_VOLUME"].asJsonObject["eligible"].asBoolean)
        assertEquals(1, json(zip, "integrity.json").asJsonObject["feedbackOwnershipConflicts"].asJsonArray.size())
      }
    } finally { database.close(); scratch.deleteRecursively() }
  }

  @Test fun activePlanExportDoesNotReconcileAnEligiblePendingCheckout() = runBlocking {
    val database = database()
    val scratch = File(context.cacheDir, "dump-no-reconcile-test").apply { mkdirs() }
    try {
      val runId = "pending-run"
      val day = LocalDate.of(2026, 9, 28)
      val beforeRun = START.minusSeconds(86_400)
      val environment = environment(database)
      val profiles = environment.profileRepository()
      val goal = TrainingGoalRepository(database, profiles,
        TrainingGoalFactory(Clock.fixed(beforeRun, ZoneOffset.UTC), idProvider = { "goal-1" }))
        .createGoal(TrainingGoalDraft(GoalDistance.FIVE_K, goalType = GoalType.COMPLETE_DISTANCE))
      val workout = simpleDurationWorkout("pending-workout", day, WorkoutType.EASY_RUN, 30, 3, 4, beforeRun, goalId = goal.id)
      val plan = TrainingPlan("pending-plan", goal.id, beforeRun, day, day.plusDays(6),
        listOf(TrainingWeek(0, day, listOf(PlannedWorkoutEntry(WeeklyWorkoutSlot.EARLY_WEEK, workout, "Saved planned session.")),
          null, 1_800L, WeekLoadClassification.NORMAL, "Saved week.", listOf("Saved reason."))),
        listOf(TrainingBlock(TrainingBlockType.BASE, 0, 0, "Aerobic base.", "Conservative running.")),
        1, "Saved plan.", ProfileConfidence.LOW, TrainingPlanStatus.ACTIVE)
      TrainingPlanRepository(database).saveAsActive(plan)
      database.runDao().upsertAll(listOf(RunEntity(runId, START.toEpochMilli(), START.plusSeconds(1_800).toEpochMilli(),
        1_800, 5_000.0, null, null, 360.0, START.toEpochMilli())))
      database.runFeedbackDao().insertFeedback(RunFeedbackEntity("pending-feedback", runId, 3, "EASY", "NONE", "NONE",
        "COMPLETED", 4, null, START.toEpochMilli(), START.toEpochMilli()))
      profiles.recalculate()
      val raw = DatabaseTableExporter(database.openHelper.readableDatabase)
      val before = storedState(raw)
      val file = File(scratch, "dump.zip")
      DiagnosticDumpRepository(context, environment).writeArchive(file, scratch, DumpPrivacyMode.SHARE_SAFE) {}
      assertEquals(before, storedState(raw))
      assertTrue(database.coachingWorkflowDao().getAllAssociations().isEmpty())
      ZipFile(file).use { zip ->
        assertEquals("pending-plan", json(zip, "resolved/active_plan.json").asJsonObject["id"].asString)
        val exportedGoal = json(zip, "resolved/active_goal.json").asJsonObject
        assertEquals(5_000.0, exportedGoal["distanceMeters"].asDouble, 0.0)
        assertFalse(exportedGoal.has("completionDate"))
        val home = json(zip, "resolved/current_home_state.json").asJsonObject
        assertEquals("pending-workout", home["nextWorkout"].asJsonObject["workout"].asJsonObject["id"].asString)
        assertEquals("PLANNED", json(zip, "resolved/workouts/${archiveId(workout.id)}.json").asJsonObject["lifecycleState"].asString)
      }
      // Prove the fixture would cause a mutation if the normal active-plan getter were used.
      assertEquals(1, WorkoutExecutionRepository(database, environment.clock).reconcileExistingCheckouts())
    } finally { database.close(); scratch.deleteRecursively() }
  }

  @Test fun corruptOptionalMetricsStillProduceUsableArchiveWithErrorReport() = runBlocking {
    val database = database()
    val scratch = File(context.cacheDir, "dump-optional-test").apply { mkdirs() }
    try {
      seed(database)
      database.openHelper.writableDatabase.execSQL("UPDATE run_metric_values SET source = 'FUTURE_UNSUPPORTED_SOURCE' WHERE metric = 'AVERAGE_HEART_RATE'")
      val file = File(scratch, "dump.zip")
      val result = DiagnosticDumpRepository(context, environment(database)).writeArchive(file, scratch, DumpPrivacyMode.SHARE_SAFE) {}
      assertTrue(result.optionalErrorCount > 0)
      ZipFile(file).use { zip ->
        assertTrue(text(zip, "database/tables/run_metric_values.jsonl").contains("FUTURE_UNSUPPORTED_SOURCE"))
        assertTrue(text(zip, "errors.json").contains("metrics"))
        assertNotNull(zip.getEntry("openruncoach-dump/resolved/canonical_runs.json"))
        json(zip, "integrity.json")
      }
    } finally { database.close(); scratch.deleteRecursively() }
  }

  private fun database() = Room.inMemoryDatabaseBuilder(context, OpenRunCoachDatabase::class.java).build()
  private fun environment(database: OpenRunCoachDatabase) = OpenRunCoachEnvironment("dump-test", database,
    Clock.fixed(START.plusSeconds(3_600), ZoneOffset.UTC), isSimulation = true)

  private fun storedState(raw: DatabaseTableExporter) = raw.tables().associate { table ->
    table.name to StringWriter().also { raw.export(table, it, DumpPrivacyMode.FULL) }.toString()
  }

  private suspend fun seed(database: OpenRunCoachDatabase) {
    val run = RunEntity(RUN_ID, START.toEpochMilli(), START.plusSeconds(1_800).toEpochMilli(), 1_800, 5_000.0,
      145.0, 160L, 360.0, START.plusSeconds(2_000).toEpochMilli(), sourcePackageName = "com.strava",
      sourceAppName = "Strava", physicalRunId = "physical-1")
    database.runDao().upsertAll(listOf(run, run.copy(healthConnectRecordId = "provider-copy", isCanonical = false,
      sourcePackageName = "com.sec.android.app.shealth", sourceAppName = "Samsung Health")))
    RunMetricsRepository(database.runMetricsDao()).save(RunMetrics(RUN_ID, START, START.plusSeconds(1_800),
      MetricValue(1_800L, MetricSource.HEALTH_CONNECT, MetricConfidence.HIGH),
      distanceMeters = MetricValue(5_000.0, MetricSource.HEALTH_CONNECT, MetricConfidence.HIGH),
      averageHeartRate = MetricValue(145.0, MetricSource.SAMSUNG_HEALTH, MetricConfidence.HIGH, sourceAppName = "Samsung Health"),
      series = RunTimeSeries(speed = (0L..1_800L step 10).map { TimedMetricSample(START.plusSeconds(it), 2.8, MetricSource.HEALTH_CONNECT) }),
      generatedAt = START.plusSeconds(2_000)))
    RunRouteRepository(database.runRouteDao()).saveForPhysicalRun(RunRoute("physical-1", MetricSource.SAMSUNG_HEALTH,
      "Samsung Health", MetricConfidence.HIGH, (0..2).map { index -> RunRoutePoint(index, START.plusSeconds(index * 900L),
        52.123456 + index * 0.001, 13.654321 + index * 0.001, altitudeMeters = 50.0 + index, horizontalAccuracyMeters = 5.0) }),
      emptyList(), "physical-1")
    val workout = StructuredWorkoutSamples.easyRun().copy(id = "workout-1")
    database.coachingWorkflowDao().replaceWorkout(StructuredWorkoutPersistenceMapper.toRecord(workout, "ANALYZED"))
    database.coachingWorkflowDao().upsertAssociation(WorkoutRunAssociationEntity("workout-1", "physical-1", RUN_ID, "MANUAL", START.toEpochMilli()))
    database.coachingWorkflowDao().upsertExecution(WorkoutExecutionEntity("workout-1", RUN_ID, "physical-1",
      "COMPLETED_AS_PLANNED", "ORIGINAL_PRESCRIPTION", null, 2_100L, 5_000.0, 1_800L, null, "Completed saved session.", START.toEpochMilli()))
    database.runFeedbackDao().insertFeedback(RunFeedbackEntity("checkout-1", RUN_ID, 4, "APPROPRIATE", "MILD", "NONE", "COMPLETED", 4,
      "Comfortable run", START.toEpochMilli(), START.toEpochMilli()))
  }

  private fun json(zip: ZipFile, path: String) = JsonParser.parseString(text(zip, path))
  private fun text(zip: ZipFile, path: String) = zip.getInputStream(requireNotNull(zip.getEntry("openruncoach-dump/$path"))).bufferedReader(Charsets.UTF_8).use { it.readText() }

  private companion object {
    const val RUN_ID = "canonical/run-1"
    val START: Instant = Instant.parse("2026-09-28T08:00:00Z")
  }
}
