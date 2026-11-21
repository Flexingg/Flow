package com.flow.todo.domain.timeline

import com.flow.todo.domain.model.CalendarEventItem
import com.flow.todo.domain.model.TaskItem
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DayTimelineTest {
    private val zone = ZoneId.of("UTC")
    private val day = LocalDate.of(2026, 11, 21)
    private val d = day.toEpochDay()
    private fun ms(h: Int, m: Int = 0, plusDays: Long = 0) = day.plusDays(plusDays).atTime(h, m).atZone(zone).toInstant().toEpochMilli()
    private fun ev(id: String, sh: Int, sm: Int, eh: Int, em: Int = 0, allDay: Boolean = false, plusDays: Long = 0) =
        CalendarEventItem(id = id, title = id, startEpochMillis = ms(sh, sm, plusDays), endEpochMillis = ms(eh, em, plusDays), isAllDay = allDay)
    private fun item(id: String, s: Int, e: Int, kind: TimelineKind = TimelineKind.EVENT, done: Boolean = false) =
        TimelineItem(id, kind, id, s, e, 0, done)

    @Test fun lanesSplitOverlapsOnly() {
        val laid = DayTimeline.layoutLanes(listOf(item("a", 540, 600), item("b", 570, 630), item("c", 660, 690)))
        val byId = laid.associateBy { it.item.id }
        assertEquals(2, byId["a"]!!.laneCount)
        assertEquals(setOf(0, 1), setOf(byId["a"]!!.lane, byId["b"]!!.lane))
        assertEquals(1, byId["c"]!!.laneCount)
        assertEquals(0, byId["c"]!!.lane)
    }

    @Test fun laneReuseAfterEarlierBlockEnds() {
        // a 9-10, b 9:30-11, c 10-10:30 -> c can reuse a's lane
        val laid = DayTimeline.layoutLanes(listOf(item("a", 540, 600), item("b", 570, 660), item("c", 600, 630))).associateBy { it.item.id }
        assertEquals(laid["a"]!!.lane, laid["c"]!!.lane)
        assertEquals(2, laid["c"]!!.laneCount)
    }

    @Test fun freeGapsBetweenBlocks() {
        val gaps = DayTimeline.freeGaps(listOf(item("a", 540, 600), item("b", 615, 660)), from = 480, to = 720)
        // 8:00-9:00 free (60), 10:00-10:15 too short, 11:00-12:00 free (60)
        assertEquals(listOf(FreeGap(480, 540), FreeGap(660, 720)), gaps)
    }

    @Test fun doneTasksDoNotBlockFreeTime() {
        val gaps = DayTimeline.freeGaps(listOf(item("t", 540, 600, TimelineKind.TASK, done = true)), 480, 660)
        assertEquals(listOf(FreeGap(480, 660)), gaps)
    }

    @Test fun snapping() {
        assertEquals(540, DayTimeline.snap(547))
        assertEquals(555, DayTimeline.snap(548))
        assertEquals(1440 - 30, DayTimeline.clampStart(1435, 30))
        assertEquals(0, DayTimeline.clampStart(-20, 30))
    }

    @Test fun buildPlacesEventsTasksAndTray() {
        val tasks = listOf(
            TaskItem(id = "timed", title = "write", dueDateEpochDay = d, startMinute = 14 * 60, durationMinutes = 60),
            TaskItem(id = "untimed", title = "call", dueDateEpochDay = d),
            TaskItem(id = "late", title = "old", dueDateEpochDay = d - 2),
            TaskItem(id = "nodate", title = "someday")
        )
        val events = listOf(ev("standup", 9, 0, 9, 30), ev("holiday", 0, 0, 0, 0, allDay = true, plusDays = 0).copy(endEpochMillis = ms(0, 0, 1)))
        val m = DayTimeline.build(d, events, tasks, nowMillis = ms(11), zone = zone)
        assertEquals(setOf("standup", "timed"), m.timed.map { it.item.id }.toSet())
        assertEquals(listOf("holiday"), m.allDayEvents.map { it.id })
        assertEquals(listOf("late", "untimed"), m.unscheduledTasks.map { it.id }) // overdue shows on today
        assertEquals(11 * 60, m.nowMinute)
        // gaps start at now: 11:00-14:00, then 15:00-22:00
        assertEquals(listOf(FreeGap(660, 840), FreeGap(900, 1320)), m.gaps)
        val timed = m.timed.first { it.item.id == "timed" }.item
        assertEquals(14 * 60 to 15 * 60, timed.startMinute to timed.endMinute)
    }

    @Test fun pastDayHasNoGapsAndNoNowLine() {
        val m = DayTimeline.build(d - 1, emptyList(), emptyList(), nowMillis = ms(11), zone = zone)
        assertTrue(m.gaps.isEmpty())
        assertNull(m.nowMinute)
    }

    @Test fun eventCrossingMidnightIsClippedAndLocked() {
        val late = CalendarEventItem(id = "party", title = "party", startEpochMillis = ms(22), endEpochMillis = ms(2, 0, 1))
        val m = DayTimeline.build(d + 1, listOf(late), emptyList(), nowMillis = ms(11), zone = zone)
        val it = m.timed.single().item
        assertEquals(0, it.startMinute)
        assertEquals(120, it.endMinute)
        assertFalse(it.isWithinDay)
        assertEquals("1h 30m free", DayTimeline.formatGap(90))
    }
}
