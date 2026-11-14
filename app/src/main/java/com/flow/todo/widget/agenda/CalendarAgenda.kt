package com.flow.todo.widget.agenda

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Plain inputs so the agenda builder is a pure, unit-testable function. */
data class AgendaEventInput(
    val id: String,
    val title: String,
    val startMillis: Long,
    val endMillis: Long,
    val isAllDay: Boolean,
    val isCompleted: Boolean,
    val colorArgb: Long
)

data class AgendaTaskInput(
    val id: String,
    val title: String,
    val dueEpochDay: Long?,
    val isCompleted: Boolean,
    val completedAtMillis: Long?,
    val isFlagged: Boolean,
    val isRecurring: Boolean
)

enum class AgendaKind { EVENT, TASK }

data class AgendaRow(
    val id: String,
    val kind: AgendaKind,
    val title: String,
    /** "9:30 AM", "All day", "Task", "Overdue 2d", or "Tue 9:30 AM" for tomorrow. */
    val timeLabel: String,
    val colorArgb: Long,
    val isDone: Boolean,
    val isOverdue: Boolean,
    /** Event currently in progress. */
    val isNow: Boolean,
    /** False for rows the widget can't safely check off (recurring tasks, future-day events). */
    val canToggle: Boolean,
    val isTomorrow: Boolean = false
)

data class Agenda(
    val dateLabel: String,
    val eventCount: Int,
    val openTaskCount: Int,
    val rows: List<AgendaRow>,
    /** Today items that didn't fit. */
    val hiddenCount: Int
)

object CalendarAgenda {
    private const val TASK_COLOR = 0xFF8E8E93L
    private const val OVERDUE_COLOR = 0xFFFF3B5CL

    fun build(
        events: List<AgendaEventInput>,
        tasks: List<AgendaTaskInput>,
        nowMillis: Long,
        zone: ZoneId,
        maxRows: Int,
        use24h: Boolean = false,
        locale: Locale = Locale.getDefault()
    ): Agenda {
        val now = Instant.ofEpochMilli(nowMillis).atZone(zone)
        val today = now.toLocalDate()
        val todayEpoch = today.toEpochDay()
        val dayStart = startOf(today, zone)
        val dayEnd = startOf(today.plusDays(1), zone)
        val timeFmt = DateTimeFormatter.ofPattern(if (use24h) "H:mm" else "h:mm a", locale)

        // ---- Events overlapping today ----
        val todayEvents = events.filter { it.startMillis < dayEnd && it.endMillis > dayStart || it.startMillis in dayStart until dayEnd }
            .sortedWith(compareByDescending<AgendaEventInput> { it.isAllDay }.thenBy { it.startMillis })
        val eventRows = todayEvents.map { e ->
            val ended = !e.isAllDay && e.endMillis <= nowMillis
            AgendaRow(
                id = e.id,
                kind = AgendaKind.EVENT,
                title = e.title.ifBlank { "(No title)" },
                timeLabel = when {
                    e.isAllDay -> "All day"
                    e.startMillis < dayStart -> "Until " + Instant.ofEpochMilli(e.endMillis).atZone(zone).format(timeFmt)
                    else -> Instant.ofEpochMilli(e.startMillis).atZone(zone).format(timeFmt)
                },
                colorArgb = e.colorArgb,
                isDone = e.isCompleted,
                isOverdue = false,
                isNow = !e.isAllDay && nowMillis in e.startMillis until e.endMillis,
                canToggle = true
            ) to ended
        }

        // ---- Tasks: overdue, due today, and done today ----
        val openTasks = tasks.filter { !it.isCompleted && it.dueEpochDay != null && it.dueEpochDay <= todayEpoch }
            .sortedWith(compareBy<AgendaTaskInput> { it.dueEpochDay }.thenByDescending { it.isFlagged })
        val doneToday = tasks.filter {
            it.isCompleted && it.dueEpochDay == todayEpoch && (it.completedAtMillis ?: 0L) >= dayStart
        }
        val taskRows = openTasks.map { t ->
            val daysLate = todayEpoch - (t.dueEpochDay ?: todayEpoch)
            AgendaRow(
                id = t.id,
                kind = AgendaKind.TASK,
                title = t.title.ifBlank { "(Untitled task)" },
                timeLabel = if (daysLate > 0) "Overdue ${daysLate}d" else if (t.isFlagged) "Task ⚑" else "Task",
                colorArgb = if (daysLate > 0) OVERDUE_COLOR else TASK_COLOR,
                isDone = false,
                isOverdue = daysLate > 0,
                isNow = false,
                canToggle = !t.isRecurring
            )
        } + doneToday.map { t ->
            AgendaRow(t.id, AgendaKind.TASK, t.title.ifBlank { "(Untitled task)" }, "Done", TASK_COLOR,
                isDone = true, isOverdue = false, isNow = false, canToggle = !t.isRecurring)
        }

        // Priority when space is short: open/upcoming items first, then finished/past ones.
        val upcomingEvents = eventRows.filter { (row, ended) -> !ended && !row.isDone }.map { it.first }
        val pastEvents = eventRows.filter { (row, ended) -> ended || row.isDone }.map { it.first }
        val openTaskRows = taskRows.filter { !it.isDone }
        val doneTaskRows = taskRows.filter { it.isDone }

        val prioritized = upcomingEvents + openTaskRows + pastEvents + doneTaskRows
        val shownIds = prioritized.take(maxRows.coerceAtLeast(0)).map { it.id }.toSet()
        // Display in a natural order: events chronologically, then tasks.
        val shown = (eventRows.map { it.first } + taskRows).filter { it.id in shownIds }
        val hidden = (eventRows.size + taskRows.size) - shown.size

        // ---- Fill spare rows with tomorrow's events ----
        val spare = maxRows - shown.size
        val tomorrowRows = if (spare > 0 && hidden == 0) {
            val tStart = dayEnd
            val tEnd = startOf(today.plusDays(2), zone)
            val dayName = today.plusDays(1).format(DateTimeFormatter.ofPattern("EEE", locale))
            events.filter { it.startMillis in tStart until tEnd && !it.isCompleted }
                .sortedWith(compareByDescending<AgendaEventInput> { it.isAllDay }.thenBy { it.startMillis })
                .take(spare)
                .map { e ->
                    AgendaRow(
                        id = e.id, kind = AgendaKind.EVENT, title = e.title.ifBlank { "(No title)" },
                        timeLabel = if (e.isAllDay) "$dayName · All day"
                        else "$dayName " + Instant.ofEpochMilli(e.startMillis).atZone(zone).format(timeFmt),
                        colorArgb = e.colorArgb, isDone = false, isOverdue = false, isNow = false,
                        canToggle = false, isTomorrow = true
                    )
                }
        } else emptyList()

        return Agenda(
            dateLabel = today.format(DateTimeFormatter.ofPattern("EEE, MMM d", locale)),
            eventCount = todayEvents.size,
            openTaskCount = openTasks.size,
            rows = shown + tomorrowRows,
            hiddenCount = hidden
        )
    }

    private fun startOf(date: LocalDate, zone: ZoneId): Long = date.atStartOfDay(zone).toInstant().toEpochMilli()
}
