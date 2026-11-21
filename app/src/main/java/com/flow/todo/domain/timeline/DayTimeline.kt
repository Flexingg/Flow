package com.flow.todo.domain.timeline

import com.flow.todo.domain.model.CalendarEventItem
import com.flow.todo.domain.model.TaskItem
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.max
import kotlin.math.min

enum class TimelineKind { EVENT, TASK }

/** One block on the day timeline, in minutes after local midnight (clipped to the day). */
data class TimelineItem(
    val id: String,
    val kind: TimelineKind,
    val title: String,
    val startMinute: Int,
    val endMinute: Int,
    val colorArgb: Long,
    val isDone: Boolean,
    /** Starred task (Eisenhower "important"). */
    val isImportant: Boolean = false,
    /** False when the block started before / ends after this day (can't be dragged safely). */
    val isWithinDay: Boolean = true
) {
    val durationMinutes: Int get() = endMinute - startMinute
}

data class PositionedItem(val item: TimelineItem, val lane: Int, val laneCount: Int)

data class FreeGap(val startMinute: Int, val endMinute: Int) {
    val durationMinutes: Int get() = endMinute - startMinute
}

data class DayTimelineModel(
    val epochDay: Long,
    val allDayEvents: List<CalendarEventItem>,
    /** Tasks on this day without a time (plus overdue ones when viewing today): the "unscheduled" tray. */
    val unscheduledTasks: List<TaskItem>,
    val timed: List<PositionedItem>,
    val gaps: List<FreeGap>,
    /** Minute of "now" when this model is for today, else null. */
    val nowMinute: Int?,
    /** First/last hour worth showing (expands to fit items). */
    val firstHour: Int,
    val lastHour: Int
)

object DayTimeline {
    const val DAY_MINUTES = 24 * 60
    const val SNAP_MINUTES = 15
    const val MIN_GAP_MINUTES = 30
    private const val TASK_COLOR = 0xFF8E8E93L

    fun snap(minute: Int, step: Int = SNAP_MINUTES): Int =
        (Math.round(minute / step.toDouble()) * step).toInt().coerceIn(0, DAY_MINUTES)

    /** Keeps a block of [duration] inside the day when moved to [start]. */
    fun clampStart(start: Int, duration: Int): Int = start.coerceIn(0, max(0, DAY_MINUTES - duration))

    fun build(
        epochDay: Long,
        events: List<CalendarEventItem>,
        tasks: List<TaskItem>,
        nowMillis: Long,
        zone: ZoneId = ZoneId.systemDefault(),
        dayStartHour: Int = 7,
        dayEndHour: Int = 22
    ): DayTimelineModel {
        val date = LocalDate.ofEpochDay(epochDay)
        val dayStart = date.atStartOfDay(zone).toInstant().toEpochMilli()
        val dayEnd = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val now = Instant.ofEpochMilli(nowMillis).atZone(zone)
        val todayEpoch = now.toLocalDate().toEpochDay()
        val isToday = epochDay == todayEpoch

        val dayEvents = events.filter { it.startEpochMillis < dayEnd && it.endEpochMillis > dayStart ||
            (it.startEpochMillis == it.endEpochMillis && it.startEpochMillis in dayStart until dayEnd) }
        val allDay = dayEvents.filter { it.isAllDay }.sortedBy { it.title.lowercase() }

        val eventItems = dayEvents.filter { !it.isAllDay }.map { e ->
            val start = if (e.startEpochMillis <= dayStart) 0 else ((e.startEpochMillis - dayStart) / 60_000L).toInt()
            val end = if (e.endEpochMillis >= dayEnd) DAY_MINUTES else ((e.endEpochMillis - dayStart) / 60_000L).toInt()
            TimelineItem(
                id = e.id, kind = TimelineKind.EVENT, title = e.title,
                startMinute = start.coerceIn(0, DAY_MINUTES),
                endMinute = max(end, start + 15).coerceAtMost(DAY_MINUTES).coerceAtLeast(start + 1),
                colorArgb = e.calendarColorHex, isDone = e.isCompleted,
                isWithinDay = e.startEpochMillis >= dayStart && e.endEpochMillis <= dayEnd
            )
        }
        val taskItems = tasks.filter { it.dueDateEpochDay == epochDay && it.startMinute != null }.map { t ->
            val start = t.startMinute!!.coerceIn(0, DAY_MINUTES - 5)
            TimelineItem(
                id = t.id, kind = TimelineKind.TASK, title = t.title,
                startMinute = start, endMinute = min(DAY_MINUTES, start + t.effectiveDurationMinutes),
                colorArgb = TASK_COLOR, isDone = t.isCompleted, isImportant = t.isFlagged
            )
        }
        val timed = layoutLanes(eventItems + taskItems)

        val unscheduled = tasks.filter { t ->
            !t.isCompleted && t.startMinute == null && (
                t.dueDateEpochDay == epochDay || (isToday && (t.dueDateEpochDay ?: Long.MAX_VALUE) < todayEpoch)
            )
        }.sortedWith(compareBy<TaskItem> { it.dueDateEpochDay }.thenByDescending { it.isFlagged }.thenBy { it.createdAt })

        val nowMinute = if (isToday) now.hour * 60 + now.minute else null
        val itemsMin = timed.minOfOrNull { it.item.startMinute } ?: (dayStartHour * 60)
        val itemsMax = timed.maxOfOrNull { it.item.endMinute } ?: (dayEndHour * 60)
        val firstHour = min(dayStartHour, itemsMin / 60).let { if (nowMinute != null) min(it, nowMinute / 60) else it }
        val lastHour = max(dayEndHour, (itemsMax + 59) / 60).let { if (nowMinute != null) max(it, nowMinute / 60 + 1) else it }
            .coerceAtMost(24)

        val gapsFrom = when {
            epochDay < todayEpoch -> null // no free time in the past
            nowMinute != null -> max(firstHour * 60, roundUp(nowMinute, SNAP_MINUTES))
            else -> firstHour * 60
        }
        val gaps = if (gapsFrom == null) emptyList() else freeGaps(timed.map { it.item }, gapsFrom, lastHour * 60)

        return DayTimelineModel(epochDay, allDay, unscheduled, timed, gaps, nowMinute, firstHour, lastHour)
    }

    /** Side-by-side lanes for overlapping blocks (greedy per overlap cluster). */
    fun layoutLanes(items: List<TimelineItem>): List<PositionedItem> {
        val sorted = items.sortedWith(compareBy<TimelineItem> { it.startMinute }.thenByDescending { it.endMinute })
        val result = mutableListOf<PositionedItem>()
        var cluster = mutableListOf<Pair<TimelineItem, Int>>()
        var clusterEnd = -1
        val laneEnds = mutableListOf<Int>()

        fun flush() {
            val lanes = (cluster.maxOfOrNull { it.second } ?: -1) + 1
            cluster.forEach { (item, lane) -> result += PositionedItem(item, lane, lanes) }
            cluster = mutableListOf()
            laneEnds.clear()
        }

        for (item in sorted) {
            if (cluster.isNotEmpty() && item.startMinute >= clusterEnd) {
                flush()
                clusterEnd = -1
            }
            var lane = laneEnds.indexOfFirst { it <= item.startMinute }
            if (lane == -1) {
                lane = laneEnds.size
                laneEnds += item.endMinute
            } else {
                laneEnds[lane] = item.endMinute
            }
            cluster += item to lane
            clusterEnd = max(clusterEnd, item.endMinute)
        }
        if (cluster.isNotEmpty()) flush()
        return result
    }

    /** Free stretches of at least [minGap] minutes in [from, to) not covered by any block. */
    fun freeGaps(items: List<TimelineItem>, from: Int, to: Int, minGap: Int = MIN_GAP_MINUTES): List<FreeGap> {
        if (to - from < minGap) return emptyList()
        val busy = items.filter { !it.isDone || it.kind == TimelineKind.EVENT }
            .map { it.startMinute to it.endMinute }
            .sortedBy { it.first }
        val gaps = mutableListOf<FreeGap>()
        var cursor = from
        for ((s, e) in busy) {
            if (e <= cursor) continue
            if (s > cursor && s - cursor >= minGap) gaps += FreeGap(cursor, min(s, to))
            cursor = max(cursor, e)
            if (cursor >= to) break
        }
        if (to - cursor >= minGap) gaps += FreeGap(cursor, to)
        return gaps.filter { it.durationMinutes >= minGap }
    }

    fun formatGap(minutes: Int): String = when {
        minutes < 60 -> "${minutes}m free"
        minutes % 60 == 0 -> "${minutes / 60}h free"
        else -> "${minutes / 60}h ${minutes % 60}m free"
    }

    private fun roundUp(value: Int, step: Int): Int = ((value + step - 1) / step) * step
}
