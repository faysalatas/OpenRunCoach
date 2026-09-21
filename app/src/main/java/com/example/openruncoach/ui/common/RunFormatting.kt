package com.example.openruncoach.ui.common

import com.example.openruncoach.domain.coaching.CoachingAction
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlin.math.roundToLong

fun formatRunDate(startTime: Instant): String =
  DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
    .withLocale(Locale.getDefault())
    .withZone(ZoneId.systemDefault())
    .format(startTime)

fun formatHistoryDate(startTime: Instant): String =
  DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
    .withLocale(Locale.getDefault())
    .withZone(ZoneId.systemDefault())
    .format(startTime)

fun formatDistance(distanceMeters: Double?): String =
  distanceMeters?.let { "%.2f km".format(Locale.getDefault(), it / 1000) } ?: "—"

fun formatDuration(seconds: Long): String =
  if (seconds >= 3600) {
    "%d:%02d:%02d".format(seconds / 3600, (seconds % 3600) / 60, seconds % 60)
  } else {
    "%d:%02d".format(seconds / 60, seconds % 60)
  }

fun formatPace(paceSecondsPerKm: Double?): String =
  paceSecondsPerKm?.roundToLong()?.let { seconds ->
    "%d:%02d min/km".format(seconds / 60, seconds % 60)
  } ?: "—"

fun coachingActionLabel(action: CoachingAction): String = when (action) {
  CoachingAction.PROGRESS -> "Progress"
  CoachingAction.SMALL_PROGRESS -> "Small progression"
  CoachingAction.MAINTAIN -> "Maintain"
  CoachingAction.REDUCE -> "Reduce"
  CoachingAction.RECOVERY -> "Recovery"
}
