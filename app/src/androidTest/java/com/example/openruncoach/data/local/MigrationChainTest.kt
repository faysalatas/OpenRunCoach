package com.example.openruncoach.data.local

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MigrationChainTest {
  private val instrumentation = InstrumentationRegistry.getInstrumentation()
  private val schemaVersions = instrumentation.context.assets
    .list(OpenRunCoachDatabase::class.java.name).orEmpty()
    .filter { it.endsWith(".json") }
    .map { it.removeSuffix(".json").toInt() }
    .sorted()
  private val latestVersion = schemaVersions.maxOrNull()
    ?: error("Exported Room schemas must be included in the test assets")

  @get:Rule
  val helper = MigrationTestHelper(instrumentation, OpenRunCoachDatabase::class.java)

  @Test
  fun everyExportedSchemaMigratesToLatest() {
    for (version in schemaVersions.dropLast(1)) {
      val databaseName = "migration-chain-from-$version"
      try {
        helper.createDatabase(databaseName, version).close()
        helper.runMigrationsAndValidate(
          databaseName, latestVersion, true, *OpenRunCoachDatabase.ALL_MIGRATIONS,
        ).use { database ->
          assertEquals("Migration from $version", latestVersion, database.version)
        }
      } finally {
        instrumentation.targetContext.deleteDatabase(databaseName)
      }
    }
  }

  @Test
  fun migrationsAndExportedSchemasFormCompleteChainToCurrentDatabase() {
    assertEquals("Exported schema versions must have no gaps", (1..latestVersion).toList(), schemaVersions)
    assertEquals(
      "Registered migrations must form the complete adjacent chain",
      (1 until latestVersion).map { it to it + 1 },
      OpenRunCoachDatabase.ALL_MIGRATIONS.map { it.startVersion to it.endVersion },
    )
    val database = Room.inMemoryDatabaseBuilder(
      instrumentation.targetContext, OpenRunCoachDatabase::class.java,
    ).build()
    try {
      assertEquals("Exported schemas must match @Database(version)",
        latestVersion, database.openHelper.readableDatabase.version)
    } finally {
      database.close()
    }
  }
}
