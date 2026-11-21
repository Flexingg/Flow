package com.flow.todo.data.calendar

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import com.flow.todo.data.repository.FlowRepository
import com.flow.todo.domain.model.CalendarEventItem
import com.flow.todo.domain.model.DeviceCalendarInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.TimeZone

/**
 * Two-way bridge to the phone's calendar provider. Google Calendar (and any other synced account)
 * keeps the provider in sync with the cloud, so writing here is writing to Google Calendar.
 *
 * - Reads: [CalendarImportManager.importDeviceCalendarEvents] mirrors a rolling window into Room;
 *   [observeChanges] re-runs it whenever the provider changes (edits made in Google Calendar).
 * - Writes: move / resize / create / delete single events. Instances of recurring events are
 *   refused (editing one occurrence needs an exception event; do that in Google Calendar).
 * - ".ics" imports have no provider row, so their edits stay local.
 */
class DeviceCalendarSync(
    private val context: Context,
    private val repository: FlowRepository,
    private val importer: CalendarImportManager
) {
    sealed class WriteResult {
        object Ok : WriteResult()
        data class Refused(val reason: String) : WriteResult()
    }

    /** Parsed from the import id "device_{calendarId}_{eventId}_{beginMillis}". */
    private data class DeviceRef(val calendarId: Long, val eventId: Long)

    fun canWrite(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED

    fun canRead(): Boolean = importer.hasCalendarPermission()

    suspend fun refresh(): Int = importer.importDeviceCalendarEvents()

    /** Calendars the user can add events to (Google calendars they own or can edit). */
    suspend fun writableCalendars(): List<DeviceCalendarInfo> = withContext(Dispatchers.IO) {
        if (!canRead()) return@withContext emptyList()
        val result = mutableListOf<DeviceCalendarInfo>()
        runCatching {
            context.contentResolver.query(
                CalendarContract.Calendars.CONTENT_URI,
                arrayOf(
                    CalendarContract.Calendars._ID,
                    CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
                    CalendarContract.Calendars.ACCOUNT_NAME,
                    CalendarContract.Calendars.CALENDAR_COLOR,
                    CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL,
                    CalendarContract.Calendars.IS_PRIMARY
                ),
                "${CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL} >= ? AND ${CalendarContract.Calendars.VISIBLE} = 1",
                arrayOf(CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR.toString()),
                "${CalendarContract.Calendars.IS_PRIMARY} DESC, ${CalendarContract.Calendars.CALENDAR_DISPLAY_NAME} ASC"
            )?.use { c ->
                while (c.moveToNext()) {
                    val raw = c.getInt(3)
                    result += DeviceCalendarInfo(
                        id = c.getLong(0),
                        displayName = c.getString(1) ?: "Calendar",
                        accountName = c.getString(2) ?: "",
                        colorHex = if (raw != 0) raw.toLong() and 0xFFFFFFFFL else 0xFF2F6BFF
                    )
                }
            }
        }
        result
    }

    /** Moves (and/or resizes) an event to [startMillis, endMillis). */
    suspend fun reschedule(event: CalendarEventItem, startMillis: Long, endMillis: Long): WriteResult =
        withContext(Dispatchers.IO) {
            if (endMillis <= startMillis) return@withContext WriteResult.Refused("End must be after start")
            if (event.source != SOURCE_DEVICE) {
                repository.upsertCalendarEvent(event.copy(startEpochMillis = startMillis, endEpochMillis = endMillis))
                return@withContext WriteResult.Ok
            }
            val ref = parse(event.id) ?: return@withContext WriteResult.Refused("Unknown calendar event")
            if (!canWrite()) return@withContext WriteResult.Refused("Allow calendar access to edit events")
            if (isRecurring(ref.eventId)) {
                return@withContext WriteResult.Refused("Repeating event: change it in Google Calendar")
            }
            val values = ContentValues().apply {
                put(CalendarContract.Events.DTSTART, startMillis)
                put(CalendarContract.Events.DTEND, endMillis)
            }
            val rows = runCatching {
                context.contentResolver.update(
                    ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, ref.eventId), values, null, null
                )
            }.getOrDefault(0)
            if (rows <= 0) return@withContext WriteResult.Refused("This calendar is read-only")
            // Optimistic local update (the id encodes the start time, so swap the row), then re-mirror.
            repository.deleteCalendarEvent(event.id)
            repository.upsertCalendarEvent(
                event.copy(
                    id = "device_${ref.calendarId}_${ref.eventId}_$startMillis",
                    startEpochMillis = startMillis,
                    endEpochMillis = endMillis
                )
            )
            refresh()
            WriteResult.Ok
        }

    /** Creates a timed event in [calendarId]. */
    suspend fun create(calendarId: Long, title: String, startMillis: Long, endMillis: Long): WriteResult =
        withContext(Dispatchers.IO) {
            if (!canWrite()) return@withContext WriteResult.Refused("Allow calendar access to add events")
            if (title.isBlank()) return@withContext WriteResult.Refused("Event needs a title")
            val values = ContentValues().apply {
                put(CalendarContract.Events.CALENDAR_ID, calendarId)
                put(CalendarContract.Events.TITLE, title.trim())
                put(CalendarContract.Events.DTSTART, startMillis)
                put(CalendarContract.Events.DTEND, endMillis)
                put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
            }
            val uri = runCatching { context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values) }.getOrNull()
                ?: return@withContext WriteResult.Refused("Couldn't add the event to that calendar")
            refresh()
            if (uri.lastPathSegment == null) WriteResult.Refused("Event not saved") else WriteResult.Ok
        }

    suspend fun delete(event: CalendarEventItem): WriteResult = withContext(Dispatchers.IO) {
        if (event.source != SOURCE_DEVICE) {
            repository.deleteCalendarEvent(event.id)
            return@withContext WriteResult.Ok
        }
        val ref = parse(event.id) ?: return@withContext WriteResult.Refused("Unknown calendar event")
        if (!canWrite()) return@withContext WriteResult.Refused("Allow calendar access to delete events")
        if (isRecurring(ref.eventId)) return@withContext WriteResult.Refused("Repeating event: delete it in Google Calendar")
        val rows = runCatching {
            context.contentResolver.delete(ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, ref.eventId), null, null)
        }.getOrDefault(0)
        if (rows <= 0) return@withContext WriteResult.Refused("This calendar is read-only")
        repository.deleteCalendarEvent(event.id)
        WriteResult.Ok
    }

    /**
     * Calls [onChange] (debounced) whenever the calendar provider changes, e.g. after Google
     * Calendar syncs an edit made on the web. Returns an unregister function.
     */
    fun observeChanges(onChange: () -> Unit): () -> Unit {
        if (!canRead()) return {}
        val handler = Handler(Looper.getMainLooper())
        val fire = Runnable { onChange() }
        val observer = object : ContentObserver(handler) {
            override fun onChange(selfChange: Boolean) {
                handler.removeCallbacks(fire)
                handler.postDelayed(fire, 1_500L)
            }
        }
        return runCatching {
            context.contentResolver.registerContentObserver(CalendarContract.Events.CONTENT_URI, true, observer)
            val unregister: () -> Unit = {
                handler.removeCallbacks(fire)
                runCatching { context.contentResolver.unregisterContentObserver(observer) }
            }
            unregister
        }.getOrDefault({})
    }

    private fun isRecurring(eventId: Long): Boolean = runCatching {
        context.contentResolver.query(
            ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId),
            arrayOf(CalendarContract.Events.RRULE, CalendarContract.Events.RDATE),
            null, null, null
        )?.use { c ->
            c.moveToFirst() && (!c.getString(0).isNullOrBlank() || !c.getString(1).isNullOrBlank())
        } ?: false
    }.getOrDefault(true) // if we can't tell, don't risk rewriting a whole series

    private fun parse(id: String): DeviceRef? {
        val parts = id.split('_')
        if (parts.size < 4 || parts[0] != "device") return null
        val cal = parts[1].toLongOrNull() ?: return null
        val ev = parts[2].toLongOrNull() ?: return null
        return DeviceRef(cal, ev)
    }

    companion object {
        const val SOURCE_DEVICE = "DEVICE"
    }
}
