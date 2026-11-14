package com.flow.todo.widget.agenda

import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CalendarAgendaTest {
    private val zone = ZoneId.of("UTC")
    private val day = LocalDate.of(2026, 11, 14)
    private val today = day.toEpochDay()
    private fun at(h: Int, m: Int = 0, plusDays: Long = 0) =
        day.plusDays(plusDays).atTime(h, m).atZone(zone).toInstant().toEpochMilli()

    private fun ev(id: String, sh: Int, eh: Int, allDay: Boolean = false, done: Boolean = false, plusDays: Long = 0) =
        AgendaEventInput(id, "Event $id", at(sh, plusDays = plusDays), at(eh, plusDays = plusDays), allDay, done, 0xFF2F6BFF)

    private fun task(id: String, due: Long?, done: Boolean = false, recurring: Boolean = false, flagged: Boolean = false) =
        AgendaTaskInput(id, "Task $id", due, done, if (done) at(9) else null, flagged, recurring)

    private fun build(events: List<AgendaEventInput>, tasks: List<AgendaTaskInput> = emptyList(), hour: Int = 10, rows: Int = 6) =
        CalendarAgenda.build(events, tasks, at(hour), zone, rows, use24h = true, locale = Locale.US)

    @Test fun ordersAllDayThenChronologicalThenTasks() {
        val a = build(listOf(ev("b", 14, 15), ev("a", 9, 10), ev("x", 0, 0, allDay = true)), listOf(task("t", today)))
        assertEquals(listOf("x", "a", "b", "t"), a.rows.map { it.id })
        assertEquals("All day", a.rows[0].timeLabel)
        assertEquals("9:00", a.rows[1].timeLabel)
        assertEquals("Sat, Nov 14", a.dateLabel)
        assertEquals(3, a.eventCount)
        assertEquals(1, a.openTaskCount)
    }

    @Test fun marksInProgressAndOverdue() {
        val a = build(listOf(ev("now", 9, 11)), listOf(task("late", today - 2)), hour = 10)
        assertTrue(a.rows.first { it.id == "now" }.isNow)
        val late = a.rows.first { it.id == "late" }
        assertTrue(late.isOverdue)
        assertEquals("Overdue 2d", late.timeLabel)
    }

    @Test fun overflowDropsPastItemsFirstAndCountsHidden() {
        val events = listOf(ev("past1", 7, 8), ev("past2", 8, 9), ev("up1", 12, 13), ev("up2", 15, 16))
        val a = build(events, listOf(task("t", today)), hour = 10, rows = 3)
        assertEquals(setOf("up1", "up2", "t"), a.rows.map { it.id }.toSet())
        assertEquals(2, a.hiddenCount)
    }

    @Test fun recurringTasksAreNotToggleable() {
        val a = build(emptyList(), listOf(task("r", today, recurring = true), task("n", today)))
        assertFalse(a.rows.first { it.id == "r" }.canToggle)
        assertTrue(a.rows.first { it.id == "n" }.canToggle)
    }

    @Test fun fillsSpareRowsWithTomorrowOnlyWhenTodayFits() {
        val a = build(listOf(ev("today", 12, 13), ev("tmr", 9, 10, plusDays = 1)))
        val tmr = a.rows.last()
        assertEquals("tmr", tmr.id)
        assertTrue(tmr.isTomorrow)
        assertFalse(tmr.canToggle)
        assertEquals("Sun 9:00", tmr.timeLabel)

        val full = build(listOf(ev("a", 12, 13), ev("b", 14, 15), ev("tmr", 9, 10, plusDays = 1)), rows = 2)
        assertTrue(full.rows.none { it.isTomorrow })
    }

    @Test fun excludesFutureTasksAndOtherDaysEvents() {
        val a = build(listOf(ev("y", 9, 10, plusDays = -1)), listOf(task("future", today + 3), task("undated", null)))
        assertTrue(a.rows.isEmpty())
    }

    @Test fun showsTasksDoneToday() {
        val a = build(emptyList(), listOf(task("d", today, done = true)))
        assertTrue(a.rows.single().isDone)
        assertEquals(0, a.openTaskCount)
    }
}
