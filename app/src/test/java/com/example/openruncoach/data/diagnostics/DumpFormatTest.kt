package com.example.openruncoach.data.diagnostics

import com.example.openruncoach.domain.workout.StructuredWorkoutSamples
import com.google.gson.JsonParser
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.StringWriter
import java.nio.file.Files
import java.time.Instant
import java.util.zip.ZipInputStream
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class DumpFormatTest {
  @Test fun projectionPreservesSmallRouteGeometryWithoutSerializingAnOrigin() {
    val projection = RelativeRouteCoordinates()
    assertEquals(mapOf("xMeters" to 0.0, "yMeters" to 0.0), projection.point(52.0, 13.0))
    val east = projection.point(52.0, 13.001)
    val north = projection.point(52.001, 13.0)
    assertEquals(68.458, requireNotNull(east["xMeters"]), 0.01)
    assertEquals(111.195, requireNotNull(north["yMeters"]), 0.01)
    val encoded = json(listOf(east, north), DumpPrivacyMode.SHARE_SAFE)
    assertFalse(encoded.contains("latitude"))
    assertFalse(encoded.contains("longitude"))
    assertFalse(encoded.contains("52.0"))
  }

  @Test fun invalidPointDoesNotEstablishOriginAndDatelineUsesShortPath() {
    val projection = RelativeRouteCoordinates()
    assertNull(projection.point(Double.NaN, 0.0)["xMeters"])
    assertEquals(0.0, projection.point(0.0, 179.999)["xMeters"]!!, 0.0)
    assertEquals(222.39, projection.point(0.0, -179.999)["xMeters"]!!, 0.01)
  }

  @Test fun fullCoordinatesRemainWhileShareSafeRejectsUnrecognizedCoordinateFields() {
    val point = mapOf("latitude" to 52.123, "longitude" to 13.456, "timestamp" to Instant.EPOCH)
    val full = JsonParser.parseString(json(point, DumpPrivacyMode.FULL)).asJsonObject
    assertEquals(52.123, full["latitude"].asDouble, 0.0)
    assertEquals(13.456, full["longitude"].asDouble, 0.0)
    val safe = json(point, DumpPrivacyMode.SHARE_SAFE)
    assertFalse(safe.contains("52.123"))
    assertFalse(safe.contains("13.456"))
    val note = mapOf("notes" to "GPS: 52.123456, 13.654321; lat=52.1 lon=13.2")
    assertFalse(json(note, DumpPrivacyMode.SHARE_SAFE).contains("52.123456"))
    assertFalse(json(note, DumpPrivacyMode.SHARE_SAFE).contains("lat=52.1"))
    assertTrue(json(note, DumpPrivacyMode.FULL).contains("52.123456"))
  }

  @Test fun secretsAreRedactedInBothModesIncludingNestedJsonText() {
    val settings = mapOf("preferredWorkoutSourcePackage" to "org.example.runner",
      "access_token" to "never-access", "refreshToken" to "never-refresh", "API_KEY" to "never-api",
      "password" to "never-password", "sessionCookie" to "never-cookie", "clientSecret" to "never-secret",
      "nested" to "{\"apiKey\":\"never-nested\",\"units\":\"metric\"}", "notes" to "Authorization: Bearer never-bearer",
      "keyText" to "-----BEGIN PRIVATE KEY-----\nnever-private\n-----END PRIVATE KEY-----")
    DumpPrivacyMode.entries.forEach { mode ->
      val output = json(settings, mode)
      assertFalse(output.contains("never-"))
      assertTrue(output.contains("org.example.runner"))
      assertTrue(output.contains("metric"))
    }
  }

  @Test fun scalarNullUnicodeAndBlobRepresentationsAreValidJson() {
    val parsed = JsonParser.parseString(json(mapOf("null" to null, "integer" to 9L, "real" to 1.25,
      "text" to "Läufer 🏃\nquote: \"", "blob" to blobValue(byteArrayOf(0, 1, -1))), DumpPrivacyMode.FULL)).asJsonObject
    assertTrue(parsed["null"].isJsonNull)
    assertEquals(9L, parsed["integer"].asLong)
    assertEquals(1.25, parsed["real"].asDouble, 0.0)
    assertEquals("Läufer 🏃\nquote: \"", parsed["text"].asString)
    assertEquals("AAH/", parsed["blob"].asJsonObject["value"].asString)
  }

  @Test fun nonfiniteValuesAreExplicitAndNeverInvalidJson() {
    val parsed = JsonParser.parseString(json(mapOf("badReal" to Double.POSITIVE_INFINITY), DumpPrivacyMode.FULL)).asJsonObject
    assertEquals("REAL", parsed["badReal"].asJsonObject["type"].asString)
    assertEquals("Infinity", parsed["badReal"].asJsonObject["value"].asString)
  }

  @Test fun structuredPrescriptionsKeepSealedVariantIdentityAndUtcTimestamps() {
    val encoded = json(StructuredWorkoutSamples.intervals(), DumpPrivacyMode.SHARE_SAFE)
    assertTrue(encoded.contains("RepeatBlock"))
    assertTrue(encoded.contains("DistanceCompletion"))
    assertTrue(encoded.contains("DurationCompletion"))
    assertTrue(encoded.contains("2026-09-24T12:00:00Z"))
    assertTrue(encoded.contains("\"origin\":\"SIMULATION\""))
    val technique = json(StructuredWorkoutSamples.technique(), DumpPrivacyMode.SHARE_SAFE)
    assertTrue(technique.contains("OpenCompletion"))
  }

  @Test fun optionalFailureLeavesValidZipAndSafeErrorsWithoutPartialJson() = runBlocking {
    val scratch = Files.createTempDirectory("dump-format-test").toFile()
    try {
      val bytes = ByteArrayOutputStream()
      DumpArchive(bytes, scratch, DumpPrivacyMode.SHARE_SAFE).use { archive ->
        archive.json("available.json", mapOf("ok" to true))
        archive.optional("broken") {
          archive.entry("broken.json") { writer -> writer.write("{incomplete"); error("access_token=never-export") }
        }
        archive.optional("provider") { throw java.io.IOException("password=never-export") }
        archive.json("errors.json", archive.errors)
      }
      val entries = linkedMapOf<String, String>()
      ZipInputStream(bytes.toByteArray().inputStream()).use { zip ->
        while (true) {
          val entry = zip.nextEntry ?: break
          entries[entry.name] = zip.readBytes().toString(Charsets.UTF_8)
        }
      }
      assertFalse(entries.containsKey("openruncoach-dump/broken.json"))
      entries.values.forEach { JsonParser.parseString(it) }
      val errors = requireNotNull(entries["openruncoach-dump/errors.json"])
      assertEquals(2, JsonParser.parseString(errors).asJsonArray.size())
      assertFalse(errors.contains("never-export"))
      assertTrue(scratch.listFiles().orEmpty().isEmpty())
    } finally { scratch.deleteRecursively() }
  }

  private fun json(value: Any?, mode: DumpPrivacyMode) = StringWriter().also { DumpJson.write(it, value, mode) }.toString()
}
