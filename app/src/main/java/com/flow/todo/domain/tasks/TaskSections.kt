package com.flow.todo.domain.tasks

import com.flow.todo.domain.model.TaskItem
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

data class TaskDayGroup(val epochDay: Long, val label: String, val tasks: List<TaskItem>)

/** The Tasks screen, organized purely by due date. */
data class TaskSections(
    val overdue: List<TaskItem> = emptyList(),
    val today: List<TaskItem> = emptyList(),
    val upcoming: List<TaskDayGroup> = emptyList(),
    val noDate: List<TaskItem> = emptyList(),
    val doneToday: List<TaskItem> = emptyList()
) {
    val openCount: Int get() = overdue.size + today.size + upcoming.sumOf { it.tasks.size } + noDate.size
    val isEmpty: Boolean get() = openCount == 0 && doneToday.isEmpty()

    companion object {
        fun build(
            tasks: List<TaskItem>,
            today: LocalDate = LocalDate.now(),
            zone: ZoneId = ZoneId.systemDefault(),
            locale: Locale = Locale.getDefault()
        ): TaskSections {
            val todayEpoch = today.toEpochDay()
            val startOfToday = today.atStartOfDay(zone).toInstant().toEpochMilli()
            val open = tasks.filter { !it.isCompleted }
            val importantFirst = compareByDescending<TaskItem> { it.isFlagged }.thenBy { it.createdAt }
            val dayFmt = DateTimeFormatter.ofPattern("EEE, MMM d", locale)

            val upcoming = open.filter { (it.dueDateEpochDay ?: Long.MIN_VALUE) > todayEpoch }
                .groupBy { it.dueDateEpochDay!! }
                .toSortedMap()
                .map { (day, items) ->
                    val label = when (day) {
                        todayEpoch + 1 -> "Tomorrow"
                        else -> LocalDate.ofEpochDay(day).format(dayFmt)
                    }
                    TaskDayGroup(day, label, items.sortedWith(importantFirst))
                }

            return TaskSections(
                overdue = open.filter { (it.dueDateEpochDay ?: Long.MAX_VALUE) < todayEpoch }
                    .sortedWith(compareBy<TaskItem> { it.dueDateEpochDay }.thenByDescending { it.isFlagged }),
                today = open.filter { it.dueDateEpochDay == todayEpoch }.sortedWith(importantFirst),
                upcoming = upcoming,
                noDate = open.filter { it.dueDateEpochDay == null }
                    .sortedWith(compareByDescending<TaskItem> { it.isFlagged }.thenByDescending { it.createdAt }),
                doneToday = tasks.filter { it.isCompleted && (it.completedAt ?: 0L) >= startOfToday }
                    .sortedByDescending { it.completedAt }
            )
        }

        /** Short due label for a task row: "Today", "Tomorrow", "Yesterday", "3d overdue", "Mon", "Nov 30". */
        fun dueLabel(dueEpochDay: Long?, today: LocalDate = LocalDate.now(), locale: Locale = Locale.getDefault()): String? {
            if (dueEpochDay == null) return null
            val delta = dueEpochDay - today.toEpochDay()
            return when {
                delta == 0L -> "Today"
                delta == 1L -> "Tomorrow"
                delta == -1L -> "Yesterday"
                delta < -1L -> "${-delta}d overdue"
                delta in 2..6 -> LocalDate.ofEpochDay(dueEpochDay).format(DateTimeFormatter.ofPattern("EEE", locale))
                else -> LocalDate.ofEpochDay(dueEpochDay).format(DateTimeFormatter.ofPattern("MMM d", locale))
            }
        }

        fun completedOn(task: TaskItem, zone: ZoneId): LocalDate? =
            task.completedAt?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() }
    }
}
