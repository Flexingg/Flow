package com.flow.todo.data.calendar

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import com.flow.todo.data.repository.FlowRepository
import com.flow.todo.domain.model.CalendarEventItem
import com.flow.todo.domain.model.DeviceCalendarInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.UUID

class CalendarImportManager(
    private val context: Context,
    private val repository: FlowRepository
) {
    fun hasCalendarPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CALENDAR
        ) == PackageManager.PERMISSION_GRANTED
    }

    suspend fun getDeviceCalendars(): List<DeviceCalendarInfo> = withContext(Dispatchers.IO) {
        if (!hasCalendarPermission()) return@withContext emptyList()

        val calendars = mutableListOf<DeviceCalendarInfo>()
        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            CalendarContract.Calendars.ACCOUNT_NAME,
            CalendarContract.Calendars.CALENDAR_COLOR,
            CalendarContract.Calendars.VISIBLE
        )

        runCatching {
            context.contentResolver.query(
                CalendarContract.Calendars.CONTENT_URI,
                projection,
                null,
                null,
                "${CalendarContract.Calendars.CALENDAR_DISPLAY_NAME} ASC"
            )?.use { cursor ->
                val idIdx = cursor.getColumnIndex(CalendarContract.Calendars._ID)
                val nameIdx = cursor.getColumnIndex(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME)
                val accountIdx = cursor.getColumnIndex(CalendarContract.Calendars.ACCOUNT_NAME)
                val colorIdx = cursor.getColumnIndex(CalendarContract.Calendars.CALENDAR_COLOR)

                while (cursor.moveToNext()) {
                    val id = if (idIdx >= 0) cursor.getLong(idIdx) else 0L
                    val name = if (nameIdx >= 0) cursor.getString(nameIdx) ?: "Calendar" else "Calendar"
                    val account = if (accountIdx >= 0) cursor.getString(accountIdx) ?: "" else ""
                    val rawColor = if (colorIdx >= 0) cursor.getInt(colorIdx) else 0
                    val colorHex = if (rawColor != 0) (rawColor.toLong() and 0xFFFFFFFFL) else 0xFF2F6BFF

                    calendars.add(
                        DeviceCalendarInfo(
                            id = id,
                            displayName = name,
                            accountName = account,
                            colorHex = colorHex
                        )
                    )
                }
            }
        }
        calendars
    }

    suspend fun importDeviceCalendarEvents(
        daysPast: Long = 30L,
        daysFuture: Long = 90L
    ): Int = withContext(Dispatchers.IO) {
        if (!hasCalendarPermission()) return@withContext 0

        val now = System.currentTimeMillis()
        val startRangeMillis = now - (daysPast * 86_400_000L)
        val endRangeMillis = now + (daysFuture * 86_400_000L)

        val uriBuilder = CalendarContract.Instances.CONTENT_URI.buildUpon()
        ContentUris.appendId(uriBuilder, startRangeMillis)
        ContentUris.appendId(uriBuilder, endRangeMillis)

        val projection = arrayOf(
            CalendarContract.Instances.EVENT_ID,
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.DESCRIPTION,
            CalendarContract.Instances.EVENT_LOCATION,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.ALL_DAY,
            CalendarContract.Instances.CALENDAR_ID,
            CalendarContract.Instances.CALENDAR_DISPLAY_NAME,
            CalendarContract.Instances.CALENDAR_COLOR,
            CalendarContract.Instances.ACCOUNT_NAME
        )

        val existingEvents = runCatching { repository.getAllCalendarEvents() }
            .getOrDefault(emptyList())
            .associateBy { it.id }

        val imported = mutableListOf<CalendarEventItem>()

        runCatching {
            context.contentResolver.query(
                uriBuilder.build(),
                projection,
                null,
                null,
                "${CalendarContract.Instances.BEGIN} ASC"
            )?.use { cursor ->
                val eventIdIdx = cursor.getColumnIndex(CalendarContract.Instances.EVENT_ID)
                val titleIdx = cursor.getColumnIndex(CalendarContract.Instances.TITLE)
                val descIdx = cursor.getColumnIndex(CalendarContract.Instances.DESCRIPTION)
                val locIdx = cursor.getColumnIndex(CalendarContract.Instances.EVENT_LOCATION)
                val beginIdx = cursor.getColumnIndex(CalendarContract.Instances.BEGIN)
                val endIdx = cursor.getColumnIndex(CalendarContract.Instances.END)
                val allDayIdx = cursor.getColumnIndex(CalendarContract.Instances.ALL_DAY)
                val calIdIdx = cursor.getColumnIndex(CalendarContract.Instances.CALENDAR_ID)
                val calNameIdx = cursor.getColumnIndex(CalendarContract.Instances.CALENDAR_DISPLAY_NAME)
                val calColorIdx = cursor.getColumnIndex(CalendarContract.Instances.CALENDAR_COLOR)
                val accountIdx = cursor.getColumnIndex(CalendarContract.Instances.ACCOUNT_NAME)

                while (cursor.moveToNext()) {
                    val rawEventId = if (eventIdIdx >= 0) cursor.getLong(eventIdIdx) else 0L
                    val title = (if (titleIdx >= 0) cursor.getString(titleIdx) else null)?.takeIf { it.isNotBlank() } ?: "Untitled Event"
                    val desc = if (descIdx >= 0) cursor.getString(descIdx) ?: "" else ""
                    val loc = if (locIdx >= 0) cursor.getString(locIdx) ?: "" else ""
                    val begin = if (beginIdx >= 0) cursor.getLong(beginIdx) else 0L
                    val end = if (endIdx >= 0) cursor.getLong(endIdx) else (begin + 3_600_000L)
                    val isAllDay = if (allDayIdx >= 0) cursor.getInt(allDayIdx) == 1 else false
                    val calId = if (calIdIdx >= 0) cursor.getLong(calIdIdx) else 0L
                    val calName = if (calNameIdx >= 0) cursor.getString(calNameIdx) ?: "Calendar" else "Calendar"
                    val rawColor = if (calColorIdx >= 0) cursor.getInt(calColorIdx) else 0
                    val colorHex = if (rawColor != 0) (rawColor.toLong() and 0xFFFFFFFFL) else 0xFF2F6BFF
                    val account = if (accountIdx >= 0) cursor.getString(accountIdx) ?: "" else ""

                    val id = "device_${calId}_${rawEventId}_$begin"
                    val existing = existingEvents[id]

                    imported.add(
                        CalendarEventItem(
                            id = id,
                            calendarId = calId,
                            calendarDisplayName = calName,
                            calendarColorHex = colorHex,
                            accountName = account,
                            title = title,
                            description = desc,
                            location = loc,
                            startEpochMillis = begin,
                            endEpochMillis = end,
                            isAllDay = isAllDay,
                            isCompleted = existing?.isCompleted ?: false,
                            completedAt = existing?.completedAt,
                            source = "DEVICE"
                        )
                    )
                }
            }
        }

        if (imported.isNotEmpty()) {
            repository.upsertCalendarEvents(imported)
        }
        imported.size
    }

    suspend fun importIcsFromUri(uri: Uri): Int = withContext(Dispatchers.IO) {
        val content = runCatching {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BufferedReader(InputStreamReader(stream)).readText()
            }
        }.getOrNull() ?: return@withContext 0

        importIcsContent(content)
    }

    suspend fun importIcsContent(content: String): Int = withContext(Dispatchers.IO) {
        if (content.isBlank()) return@withContext 0

        // Unfold lines: lines starting with space or tab continue the previous line
        val rawLines = content.lines()
        val unfoldedLines = mutableListOf<String>()
        for (line in rawLines) {
            if ((line.startsWith(" ") || line.startsWith("\t")) && unfoldedLines.isNotEmpty()) {
                val lastIdx = unfoldedLines.size - 1
                unfoldedLines[lastIdx] = unfoldedLines[lastIdx] + line.substring(1)
            } else {
                unfoldedLines.add(line)
            }
        }

        val existingEvents = runCatching { repository.getAllCalendarEvents() }
            .getOrDefault(emptyList())
            .associateBy { it.id }

        val parsedEvents = mutableListOf<CalendarEventItem>()
        var insideEvent = false
        var currentSummary = ""
        var currentDescription = ""
        var currentLocation = ""
        var currentStartMillis = 0L
        var currentEndMillis = 0L
        var currentIsAllDay = false
        var currentUid = ""

        for (line in unfoldedLines) {
            val trimmed = line.trim()
            if (trimmed.equals("BEGIN:VEVENT", ignoreCase = true)) {
                insideEvent = true
                currentSummary = "Untitled Event"
                currentDescription = ""
                currentLocation = ""
                currentStartMillis = 0L
                currentEndMillis = 0L
                currentIsAllDay = false
                currentUid = ""
                continue
            }

            if (trimmed.equals("END:VEVENT", ignoreCase = true) && insideEvent) {
                insideEvent = false
                if (currentStartMillis > 0L) {
                    val uid = if (currentUid.isNotBlank()) currentUid else UUID.randomUUID().toString()
                    val id = "ics_${uid}_$currentStartMillis"
                    val existing = existingEvents[id]
                    val end = if (currentEndMillis >= currentStartMillis) currentEndMillis else (currentStartMillis + 3_600_000L)

                    parsedEvents.add(
                        CalendarEventItem(
                            id = id,
                            calendarId = -1L,
                            calendarDisplayName = "Imported .ics",
                            calendarColorHex = 0xFFAF52DE, // Vibrant Purple for ICS
                            accountName = "iCalendar File",
                            title = currentSummary,
                            description = currentDescription,
                            location = currentLocation,
                            startEpochMillis = currentStartMillis,
                            endEpochMillis = end,
                            isAllDay = currentIsAllDay,
                            isCompleted = existing?.isCompleted ?: false,
                            completedAt = existing?.completedAt,
                            source = "ICS"
                        )
                    )
                }
                continue
            }

            if (!insideEvent) continue

            val colonIdx = trimmed.indexOf(':')
            if (colonIdx <= 0) continue

            val keyPart = trimmed.substring(0, colonIdx).uppercase()
            val valuePart = trimmed.substring(colonIdx + 1).replace("\\n", "\n").replace("\\,", ",").replace("\\;", ";")

            when {
                keyPart.startsWith("SUMMARY") -> {
                    currentSummary = valuePart.ifBlank { "Untitled Event" }
                }
                keyPart.startsWith("DESCRIPTION") -> {
                    currentDescription = valuePart
                }
                keyPart.startsWith("LOCATION") -> {
                    currentLocation = valuePart
                }
                keyPart.startsWith("UID") -> {
                    currentUid = valuePart.trim()
                }
                keyPart.startsWith("DTSTART") -> {
                    val parsed = parseIcsDateTime(valuePart, keyPart.contains("VALUE=DATE"))
                    currentStartMillis = parsed.first
                    currentIsAllDay = parsed.second
                }
                keyPart.startsWith("DTEND") -> {
                    val parsed = parseIcsDateTime(valuePart, keyPart.contains("VALUE=DATE"))
                    currentEndMillis = parsed.first
                }
            }
        }

        if (parsedEvents.isNotEmpty()) {
            repository.upsertCalendarEvents(parsedEvents)
        }
        parsedEvents.size
    }

    private fun parseIcsDateTime(value: String, explicitAllDay: Boolean): Pair<Long, Boolean> {
        val clean = value.trim()
        val zone = ZoneId.systemDefault()

        // Case 1: Date only (e.g. 20261102)
        if (explicitAllDay || clean.length == 8 && clean.all { it.isDigit() }) {
            return runCatching {
                val date = LocalDate.parse(clean, DateTimeFormatter.BASIC_ISO_DATE)
                val millis = date.atStartOfDay(zone).toInstant().toEpochMilli()
                Pair(millis, true)
            }.getOrDefault(Pair(System.currentTimeMillis(), true))
        }

        // Case 2: UTC timestamp (e.g. 20261102T150000Z)
        if (clean.endsWith("Z", ignoreCase = true)) {
            val stripped = clean.substring(0, clean.length - 1)
            return runCatching {
                val dt = LocalDateTime.parse(stripped, DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss"))
                Pair(dt.toInstant(ZoneOffset.UTC).toEpochMilli(), false)
            }.getOrDefault(Pair(System.currentTimeMillis(), false))
        }

        // Case 3: Local timestamp (e.g. 20261102T150000)
        return runCatching {
            val dt = LocalDateTime.parse(clean, DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss"))
            Pair(dt.atZone(zone).toInstant().toEpochMilli(), false)
        }.getOrDefault(Pair(System.currentTimeMillis(), false))
    }
}
