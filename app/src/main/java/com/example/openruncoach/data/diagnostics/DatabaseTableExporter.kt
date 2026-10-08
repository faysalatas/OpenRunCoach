package com.example.openruncoach.data.diagnostics

import android.database.Cursor
import androidx.sqlite.db.SupportSQLiteDatabase
import java.io.Writer

internal fun sqlIdentifier(value: String): String = "\"" + value.replace("\"", "\"\"") + "\""
internal fun archiveId(value: String): String = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(value.toByteArray(Charsets.UTF_8))

internal data class DumpColumn(
  val name: String,
  val declaredType: String,
  val notNull: Boolean,
  val primaryKeyPosition: Int,
  val defaultValue: Any?,
)
internal data class DumpTable(
  val name: String,
  val columns: List<DumpColumn>,
  val foreignKeys: List<Map<String, Any?>>,
  val indices: List<Map<String, Any?>>,
  val primaryKeyColumns: List<String> = columns.filter { it.primaryKeyPosition > 0 }.sortedBy { it.primaryKeyPosition }.map { it.name },
)

/** Discovers tables from SQLite metadata, independent of the Room entity registry. SELECT/PRAGMA only. */
internal class DatabaseTableExporter(private val db: SupportSQLiteDatabase, private val checkCancelled: () -> Unit = {}) {
  fun tables(): List<DumpTable> = queryRows(
    "SELECT name FROM sqlite_master WHERE type = 'table' AND name NOT LIKE 'sqlite_%' " +
      "AND name NOT IN ('android_metadata', 'room_master_table', 'room_table_modification_log') ORDER BY name"
  ).map { row ->
    val table = requireNotNull(row["name"] as? String)
    val columns = queryRows("PRAGMA table_info(${sqlIdentifier(table)})").map {
      val column = it["name"] as String
      val default = when {
        it["dflt_value"] == null -> null
        DumpPrivacy.isSecret(table) || DumpPrivacy.isSecret(column) -> "[REDACTED]"
        DumpPrivacy.isCoordinate(column) -> "[REDACTED_LOCATION]"
        else -> it["dflt_value"]
      }
      DumpColumn(it["name"] as String, it["type"] as String, (it["notnull"] as Number).toInt() != 0,
        (it["pk"] as Number).toInt(), default)
    }
    val indices = queryRows("PRAGMA index_list(${sqlIdentifier(table)})").map { index ->
      index + ("columns" to queryRows("PRAGMA index_xinfo(${sqlIdentifier(index["name"] as String)})"))
    }
    DumpTable(table, columns, queryRows("PRAGMA foreign_key_list(${sqlIdentifier(table)})"), indices)
  }

  fun schema(tables: List<DumpTable>): Map<String, Any?> = mapOf(
    "databaseVersion" to db.version,
    "tables" to tables,
    "roomMetadata" to queryRows("SELECT * FROM room_master_table ORDER BY id"),
    "viewsAndTriggers" to queryRows("SELECT type, name, tbl_name FROM sqlite_master WHERE type IN ('view', 'trigger') ORDER BY type, name"),
    "privacyNote" to "Route latitude/longitude columns are replaced by local x/y in SHARE_SAFE table rows. Sensitive columns are redacted in both modes.",
  )

  fun export(table: DumpTable, writer: Writer, mode: DumpPrivacyMode): Long {
    var previousRun: String? = null
    var projection = RelativeRouteCoordinates()
    var count = 0L
    forEachRow("SELECT * FROM ${sqlIdentifier(table.name)} ORDER BY ${orderBy(table)}") { raw ->
      val row = raw.toMutableMap()
      val secretSetting = listOf("key", "name", "settingName", "preferenceKey").any { key ->
        (row[key] as? String)?.let(DumpPrivacy::isSecret) == true
      }
      if (DumpPrivacy.isSecret(table.name) || secretSetting) {
        // Future credential tables and key/value settings must not bypass column-name filtering.
        row.keys.toList().forEach { key ->
          if (DumpPrivacy.isSecret(table.name) || key !in setOf("key", "name", "settingName", "preferenceKey")) row[key] = "[REDACTED]"
        }
      }
      if (mode == DumpPrivacyMode.SHARE_SAFE && table.name == "run_route_points") {
        val run = row["runId"] as? String
        if (run != previousRun) { projection = RelativeRouteCoordinates(); previousRun = run }
        val lat = (row.remove("latitude") as? Number)?.toDouble() ?: Double.NaN
        val lon = (row.remove("longitude") as? Number)?.toDouble() ?: Double.NaN
        row.putAll(projection.point(lat, lon))
      }
      DumpJson.write(writer, row, mode)
      writer.write("\n")
      count++
    }
    return count
  }

  fun queryRows(sql: String, args: Array<out Any?> = emptyArray()): List<Map<String, Any?>> = buildList {
    forEachRow(sql, args) { add(it) }
  }

  fun forEachRow(sql: String, args: Array<out Any?> = emptyArray(), block: (Map<String, Any?>) -> Unit) {
    db.query(sql, args).use { cursor ->
      while (cursor.moveToNext()) {
        checkCancelled()
        block(cursorRow(cursor))
      }
    }
  }

  companion object {
    fun orderBy(table: DumpTable): String {
      val primary = table.columns.filter { it.primaryKeyPosition > 0 }.sortedBy { it.primaryKeyPosition }
      // All columns provide a stable fallback even for WITHOUT ROWID tables and views of future schemas.
      return (primary.ifEmpty { table.columns }).joinToString(", ") { sqlIdentifier(it.name) }
    }

    fun cursorRow(cursor: Cursor): Map<String, Any?> = cursor.columnNames.indices.associate { index ->
      cursor.getColumnName(index) to when (cursor.getType(index)) {
        Cursor.FIELD_TYPE_NULL -> null
        Cursor.FIELD_TYPE_INTEGER -> cursor.getLong(index)
        Cursor.FIELD_TYPE_FLOAT -> cursor.getDouble(index).let { value ->
          if (value.isFinite()) value else mapOf("type" to "REAL", "value" to value.toString())
        }
        Cursor.FIELD_TYPE_STRING -> cursor.getString(index)
        Cursor.FIELD_TYPE_BLOB -> blobValue(cursor.getBlob(index))
        else -> error("Unsupported SQLite value type")
      }
    }
  }
}
