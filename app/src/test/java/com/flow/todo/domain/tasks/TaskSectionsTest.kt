package com.flow.todo.domain.tasks

import com.flow.todo.domain.model.TaskItem
import com.flow.todo.domain.nlp.NaturalLanguageTaskParser
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskSectionsTest {
    private val zone = ZoneId.of("UTC")
    private val today = LocalDate.of(2026, 11, 17) // Tuesday
    private val t = today.toEpochDay()
    private fun task(id: String, due: Long?, done: Boolean = false, flagged: Boolean = false, created: Long = 0, completedAt: Long? = null) =
        TaskItem(id = id, title = id, dueDateEpochDay = due, isFlagged = flagged, isCompleted = done, createdAt = created, completedAt = completedAt)

    private fun build(vararg tasks: TaskItem) = TaskSections.build(tasks.toList(), today, zone, Locale.US)

    @Test fun sectionsByDueDate() {
        val s = build(task("late", t - 2), task("now", t), task("tmr", t + 1), task("later", t + 9), task("nodate", null))
        assertEquals(listOf("late"), s.overdue.map { it.id })
        assertEquals(listOf("now"), s.today.map { it.id })
        assertEquals(listOf("Tomorrow", "Thu, Nov 26"), s.upcoming.map { it.label })
        assertEquals(listOf("nodate"), s.noDate.map { it.id })
        assertEquals(5, s.openCount)
    }

    @Test fun flaggedFirstWithinDay() {
        val s = build(task("a", t, created = 1), task("b", t, flagged = true, created = 2))
        assertEquals(listOf("b", "a"), s.today.map { it.id })
    }

    @Test fun doneTodayOnlyIncludesTodaysCompletions() {
        val startToday = today.atStartOfDay(zone).toInstant().toEpochMilli()
        val s = build(task("d1", t, done = true, completedAt = startToday + 1000), task("old", t - 3, done = true, completedAt = startToday - 1000))
        assertEquals(listOf("d1"), s.doneToday.map { it.id })
        assertTrue(s.overdue.isEmpty())
    }

    @Test fun dueLabels() {
        assertEquals("Today", TaskSections.dueLabel(t, today, Locale.US))
        assertEquals("Tomorrow", TaskSections.dueLabel(t + 1, today, Locale.US))
        assertEquals("3d overdue", TaskSections.dueLabel(t - 3, today, Locale.US))
        assertEquals("Fri", TaskSections.dueLabel(t + 3, today, Locale.US))
        assertEquals("Dec 1", TaskSections.dueLabel(t + 14, today, Locale.US))
        assertNull(TaskSections.dueLabel(null, today, Locale.US))
    }

    @Test fun parserKeepsDatesFlagsAndIgnoresOldGtdSyntax() {
        val p = NaturalLanguageTaskParser.parse("Call mom tomorrow !flag", today = today)
        assertEquals("Call mom", p.cleanedTitle)
        assertEquals(t + 1, p.dueDateEpochDay)
        assertTrue(p.isFlagged)
        val drafts = NaturalLanguageTaskParser.parseMultiple("buy milk today; pay rent friday", today = today)
        assertEquals(listOf("buy milk", "pay rent"), drafts.map { it.title })
        assertEquals(listOf(t, t + 3), drafts.map { it.dueDateEpochDay })
    }

    @Test fun parserReadmeExamplesWork() {
        val gym = NaturalLanguageTaskParser.parse("gym every monday", today = today)
        assertEquals("gym", gym.cleanedTitle)
        assertTrue(gym.recurrenceRule?.isRecurring == true)
        val pack = NaturalLanguageTaskParser.parse("pack // passport // charger", today = today)
        assertEquals("pack", pack.cleanedTitle)
        assertEquals(listOf("passport", "charger"), pack.extractedSubtasks)
        val rent = NaturalLanguageTaskParser.parse("pay rent friday", today = today)
        assertEquals("pay rent", rent.cleanedTitle)
        assertEquals(t + 3, rent.dueDateEpochDay)
    }
}
