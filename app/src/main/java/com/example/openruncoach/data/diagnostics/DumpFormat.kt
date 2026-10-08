package com.example.openruncoach.data.diagnostics

import com.google.gson.GsonBuilder
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import com.google.gson.JsonSerializer
import com.google.gson.Gson
import com.google.gson.TypeAdapter
import com.google.gson.TypeAdapterFactory
import com.google.gson.reflect.TypeToken
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonWriter
import com.example.openruncoach.domain.workout.IntensityTarget
import com.example.openruncoach.domain.workout.SegmentCompletion
import com.example.openruncoach.domain.workout.WorkoutBlock
import com.example.openruncoach.domain.workout.PlannedWorkout
import com.example.openruncoach.domain.workout.WorkoutEstimateCalculator
import com.example.openruncoach.domain.workout.WorkoutExecutionResult
import com.example.openruncoach.domain.goal.TrainingGoal
import com.example.openruncoach.domain.goal.targetPaceSecondsPerKm
import java.io.File
import java.io.OutputStream
import java.io.Writer
import java.time.Instant
import java.time.LocalDate
import java.util.Base64
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.math.cos

const val DUMP_FORMAT_VERSION = 1
enum class DumpPrivacyMode { SHARE_SAFE, FULL }

internal object DumpJson {
  val gson = GsonBuilder().serializeNulls().disableHtmlEscaping().serializeSpecialFloatingPointValues()
    .registerTypeAdapterFactory(DumpDomainTypes)
    .registerTypeAdapter(Instant::class.java, JsonSerializer<Instant> { value, _, _ -> JsonPrimitive(value.toString()) })
    .registerTypeAdapter(LocalDate::class.java, JsonSerializer<LocalDate> { value, _, _ -> JsonPrimitive(value.toString()) })
    .create()

  fun write(writer: Writer, value: Any?, mode: DumpPrivacyMode) {
    gson.toJson(DumpPrivacy.sanitize(gson.toJsonTree(value), mode), writer)
  }
}

/** Preserve sealed variant identity, including fieldless OpenCompletion/NoIntensityTarget. */
private object DumpDomainTypes : TypeAdapterFactory {
  override fun <T> create(gson: Gson, type: TypeToken<T>): TypeAdapter<T>? {
    if (!listOf(WorkoutBlock::class.java, SegmentCompletion::class.java, IntensityTarget::class.java)
        .any { it.isAssignableFrom(type.rawType) } && type.rawType !in
        setOf(TrainingGoal::class.java, PlannedWorkout::class.java, WorkoutExecutionResult::class.java)) return null
    val delegate = gson.getDelegateAdapter(this, type)
    return object : TypeAdapter<T>() {
      override fun write(out: JsonWriter, value: T?) {
        if (value == null) { out.nullValue(); return }
        // An interface delegate has no fields; use the actual concrete model adapter.
        val concrete = if (type.rawType.isInterface) gson.getDelegateAdapter(DumpDomainTypes, TypeToken.get(value.javaClass)) else delegate
        @Suppress("UNCHECKED_CAST")
        val tree = (concrete as TypeAdapter<Any>).toJsonTree(value).asJsonObject
        tree.addProperty("modelType", value.javaClass.simpleName)
        when (value) {
          is TrainingGoal -> {
            tree.addProperty("distanceMeters", value.distanceMeters)
            tree.add("targetPaceSecondsPerKm", gson.toJsonTree(value.targetTimeSeconds?.let { targetPaceSecondsPerKm(it, value.distanceMeters) }))
          }
          is PlannedWorkout -> {
            tree.add("estimate", gson.toJsonTree(WorkoutEstimateCalculator.calculate(value)))
            tree.addProperty("durationMinutes", value.durationMinutes)
            tree.addProperty("targetRpeMin", value.targetRpeMin)
            tree.addProperty("targetRpeMax", value.targetRpeMax)
          }
          is WorkoutExecutionResult -> tree.addProperty("isCompleted", value.isCompleted)
          is IntensityTarget -> tree.add("confidence", gson.toJsonTree(value.confidence))
        }
        gson.getAdapter(JsonElement::class.java).write(out, tree)
      }
      override fun read(input: JsonReader): T = throw UnsupportedOperationException("Export only")
    }
  }
}

/** Applied to raw rows AND domain objects. Settings additionally use an explicit allowlist. */
internal object DumpPrivacy {
  private fun normalized(key: String) = key.lowercase().filter(Char::isLetterOrDigit)
  fun isSecret(key: String): Boolean = normalized(key).let { name ->
    listOf("token", "secret", "password", "passwd", "cookie", "credential", "apikey", "authorization", "privatekey").any(name::contains) ||
      name in setOf("androidid", "advertisingid", "accountid", "email", "emailaddress", "deviceid")
  }
  fun isCoordinate(key: String): Boolean = normalized(key).let {
    it.contains("latitude") || it.contains("longitude") || it in setOf("lat", "lon", "lng", "coordinates", "geohash", "polyline", "encodedpolyline", "bbox")
  }
  private fun isCoordinateOrigin(key: String, value: JsonElement): Boolean =
    normalized(key) == "origin" && (value.isJsonArray || value.isJsonObject || (value.isJsonPrimitive && value.asJsonPrimitive.isNumber))

  private val credentialText = Regex(
    "(?i)(bearer\\s+\\S+|(?:access[_-]?token|refresh[_-]?token|api[_-]?key|client[_-]?secret|password|authorization|cookie)\\s*[:=]\\s*[^\\s,;]+|sk-[A-Za-z0-9_-]{12,}|eyJ[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+)"
  )
  private val bearerText = Regex("(?i)bearer\\s+[^\\s,;]+")
  private val privateKeyText = Regex("-----BEGIN [^-]*PRIVATE KEY-----[\\s\\S]*?-----END [^-]*PRIVATE KEY-----")
  private val coordinatePairText = Regex("(?<![\\w.])(-?\\d{1,2}\\.\\d{4,})\\s*[,;/]\\s*(-?\\d{1,3}\\.\\d{4,})(?![\\w.])")
  private val labeledCoordinateText = Regex("(?i)(?:latitude|lat)\\s*[:=]\\s*(-?\\d{1,2}(?:\\.\\d+)?)[\\s,;]+(?:longitude|lon|lng)\\s*[:=]\\s*(-?\\d{1,3}(?:\\.\\d+)?)")
  fun safeText(value: String): String = credentialText.replace(
    bearerText.replace(privateKeyText.replace(value, "[REDACTED]"), "[REDACTED]"), "[REDACTED]")
  private fun locationSafeText(value: String): String {
    fun redact(match: MatchResult): String = if (match.groupValues[1].toDouble() in -90.0..90.0 &&
      match.groupValues[2].toDouble() in -180.0..180.0) "[REDACTED_LOCATION]" else match.value
    return coordinatePairText.replace(labeledCoordinateText.replace(value, ::redact), ::redact)
  }

  fun sanitize(element: JsonElement, mode: DumpPrivacyMode): JsonElement = when {
    element.isJsonObject -> JsonObject().apply {
      element.asJsonObject.entrySet().forEach { (key, value) ->
        when {
          isSecret(key) -> addProperty(key, "[REDACTED]")
          mode == DumpPrivacyMode.SHARE_SAFE && (isCoordinate(key) || isCoordinateOrigin(key, value)) ->
            addProperty(key, "[REDACTED_LOCATION]")
          else -> add(key, sanitize(value, mode))
        }
      }
    }
    element.isJsonArray -> com.google.gson.JsonArray().apply { element.asJsonArray.forEach { add(sanitize(it, mode)) } }
    element.isJsonPrimitive && element.asJsonPrimitive.isNumber && !element.asDouble.isFinite() -> JsonObject().apply {
      addProperty("type", "REAL"); addProperty("value", element.asString)
    }
    element.isJsonPrimitive && element.asJsonPrimitive.isString -> {
      val text = element.asString
      // Future tables may embed structured settings/provider payloads in TEXT columns.
      val nested = if (text.trimStart().startsWith("{") || text.trimStart().startsWith("[")) {
        runCatching { com.google.gson.JsonParser.parseString(text) }.getOrNull()
      } else null
      JsonPrimitive(if (nested != null) DumpJson.gson.toJson(sanitize(nested, mode)) else {
        val safe = safeText(text)
        if (mode == DumpPrivacyMode.SHARE_SAFE) locationSafeText(safe) else safe
      })
    }
    else -> element
  }
}

/** Local equirectangular projection, per route; no origin is serialized. Handles the dateline. */
internal class RelativeRouteCoordinates {
  private var origin: Pair<Double, Double>? = null
  fun point(latitude: Double, longitude: Double): Map<String, Double?> {
    if (!latitude.isFinite() || !longitude.isFinite() || latitude !in -90.0..90.0 || longitude !in -180.0..180.0) {
      return mapOf("xMeters" to null, "yMeters" to null)
    }
    val base = origin ?: (latitude to longitude).also { origin = it }
    val longitudeDelta = ((longitude - base.second + 540.0) % 360.0) - 180.0
    return mapOf(
      "xMeters" to Math.toRadians(longitudeDelta) * EARTH_RADIUS_METERS * cos(Math.toRadians(base.first)),
      "yMeters" to Math.toRadians(latitude - base.first) * EARTH_RADIUS_METERS,
    )
  }
  private companion object { const val EARTH_RADIUS_METERS = 6_371_000.0 }
}

internal fun blobValue(bytes: ByteArray): Map<String, String> = mapOf("type" to "BLOB", "encoding" to "base64", "value" to Base64.getEncoder().encodeToString(bytes))

internal data class DumpError(val section: String, val exceptionType: String, val safeMessage: String)
internal class DumpStorageException(cause: java.io.IOException) : java.io.IOException("Diagnostic archive storage failed", cause)

/** Each entry is staged on disk so an optional failure never leaves half-written JSON in the ZIP. */
internal class DumpArchive(output: OutputStream, private val scratch: File, val mode: DumpPrivacyMode) : AutoCloseable {
  private val zip = ZipOutputStream(output, Charsets.UTF_8)
  val errors = mutableListOf<DumpError>()

  suspend fun <T> optional(section: String, block: suspend () -> T): T? = try {
    block()
  } catch (error: kotlinx.coroutines.CancellationException) {
    throw error
  } catch (error: DumpStorageException) {
    // Destination/disk failures are fatal; silently continuing would claim a successful export.
    throw error
  } catch (error: Exception) {
    errors += DumpError(section, error.javaClass.simpleName, "Section unavailable; inspect its raw table inputs where present.")
    null
  } catch (error: LinkageError) {
    errors += DumpError(section, error.javaClass.simpleName, "Optional provider library unavailable.")
    null
  }

  fun entry(path: String, block: (Writer) -> Unit) {
    val file = try { File.createTempFile("entry-", ".tmp", scratch) } catch (error: java.io.IOException) { throw DumpStorageException(error) }
    try {
      file.writer(Charsets.UTF_8).buffered().use(block)
      zip.putNextEntry(ZipEntry("openruncoach-dump/$path").apply { time = 0L })
      file.inputStream().buffered().use { it.copyTo(zip) }
      zip.closeEntry()
    } catch (error: java.io.IOException) {
      throw DumpStorageException(error)
    } finally { file.delete() }
  }

  fun json(path: String, value: Any?) = entry(path) { DumpJson.write(it, value, mode) }
  fun jsonl(path: String, values: Iterable<Any?>) = entry(path) { writer ->
    values.forEach { DumpJson.write(writer, it, mode); writer.write("\n") }
  }
  override fun close() = zip.close()
}
