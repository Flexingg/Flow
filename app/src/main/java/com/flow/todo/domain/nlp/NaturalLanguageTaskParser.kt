package com.flow.todo.domain.nlp

import com.flow.todo.domain.model.ParsedTaskDraft
import com.flow.todo.domain.model.ParsedTaskSuggestion
import com.flow.todo.domain.model.RecurrenceRule
import com.flow.todo.domain.model.RecurrenceType
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
import java.util.Locale

object NaturalLanguageTaskParser {

    private val priorityRegex = Regex("""(?i)(?:^|\s)(!flag|!high|!urgent|!important|!p1|!!)(?=\s|$)""")

    private val dailyRegex = Regex("""(?i)\b(every\s+day|daily)\b""")
    private val everyNDaysRegex = Regex("""(?i)\bevery\s+(\d+)\s+days?\b""")
    private val nthWeekdayRegex = Regex(
        """(?i)\bevery\s+(1st|2nd|3rd|4th|first|second|third|fourth|last)\s+(monday|tuesday|wednesday|thursday|friday|saturday|sunday|mon|tue|wed|thu|fri|sat|sun)\b"""
    )
    private val monthlyDayRegex = Regex("""(?i)\bevery\s+month\s+on\s+(?:the\s+)?(\d{1,2})(?:st|nd|rd|th)?\b""")
    private val weeklyOnDayRegex = Regex(
        """(?i)\b(?:every\s+week\s+on|every|weekly\s+on)\s+(monday|tuesday|wednesday|thursday|friday|saturday|sunday|mon|tue|wed|thu|fri|sat|sun)s?\b"""
    )
    private val weeklyRegex = Regex("""(?i)\b(every\s+week|weekly)\b""")

    private val todayRegex = Regex("""(?i)\b(today|tonight)\b""")
    private val tomorrowRegex = Regex("""(?i)\b(tomorrow|tmrw|tmw)\b""")
    private val weekendRegex = Regex("""(?i)\b(this\s+weekend)\b""")
    private val nextWeekRegex = Regex("""(?i)\b(next\s+week)\b""")
    private val inNDaysRegex = Regex("""(?i)\bin\s+(\d+)\s+days?\b""")
    private val weekdayDueRegex = Regex(
        """(?i)\b(on\s+|next\s+|this\s+)?(monday|tuesday|wednesday|thursday|friday|saturday|sunday)\b"""
    )
    private val timeSuffixRegex = Regex("""(?i)\b(?:at|@)\s*(\d{1,2})(?::(\d{2}))?\s*(am|pm|a|p)?\b""")
    private val durationRegex = Regex("""(?i)\bfor\s+(\d+(?:\.\d+)?)\s*(h|hr|hrs|hour|hours|m|min|mins|minute|minutes)\b""")
    private val checklistLineRegex = Regex("""^\s*(?:[-*]\s*\[\s*\]|\[\s*\]|[-*])\s+(.+)$""")

    fun parse(
        rawTitle: String,
        rawNotes: String = "",
        today: LocalDate = LocalDate.now()
    ): ParsedTaskSuggestion {
        if (rawTitle.isBlank() && rawNotes.isBlank()) {
            return ParsedTaskSuggestion(cleanedTitle = "")
        }

        var workingTitle = rawTitle
        val detectedTokens = mutableListOf<String>()
        val extractedSubtasks = mutableListOf<String>()

        // 0. Extract inline subtasks separated by " // " in rawTitle
        if (workingTitle.contains("//")) {
            val parts = workingTitle.split("//")
            workingTitle = parts.firstOrNull()?.trim().orEmpty()
            parts.drop(1).map { it.trim() }.filter { it.isNotEmpty() }.forEach { subtask ->
                extractedSubtasks.add(subtask)
            }
        }

        // 1. Priority Flag
        var isFlagged = false
        val priorityMatch = priorityRegex.find(workingTitle)
        if (priorityMatch != null) {
            isFlagged = true
            detectedTokens.add("🚩 Priority Flag")
            workingTitle = workingTitle.removeRange(priorityMatch.range)
        }

        // 4. Recurrence Rule (evaluated before single due dates so "every monday" isn't consumed as "monday")
        var recurrenceRule: RecurrenceRule? = null

        val nthMatch = nthWeekdayRegex.find(workingTitle)
        val monthlyDayMatch = if (nthMatch == null) monthlyDayRegex.find(workingTitle) else null
        val everyNDaysMatch = if (nthMatch == null && monthlyDayMatch == null) everyNDaysRegex.find(workingTitle) else null
        val weeklyOnDayMatch = if (nthMatch == null && monthlyDayMatch == null && everyNDaysMatch == null) {
            weeklyOnDayRegex.find(workingTitle)
        } else null
        val dailyMatch = if (nthMatch == null && monthlyDayMatch == null && everyNDaysMatch == null && weeklyOnDayMatch == null) {
            dailyRegex.find(workingTitle)
        } else null
        val weeklyMatch = if (
            nthMatch == null && monthlyDayMatch == null &&
            everyNDaysMatch == null && weeklyOnDayMatch == null && dailyMatch == null
        ) {
            weeklyRegex.find(workingTitle)
        } else null

        when {
            nthMatch != null -> {
                val ord = parseOrdinal(nthMatch.groupValues[1])
                val dow = parseDayOfWeek(nthMatch.groupValues[2]) ?: today.dayOfWeek
                recurrenceRule = RecurrenceRule(
                    type = RecurrenceType.MONTHLY_NTH_WEEKDAY,
                    interval = 1,
                    nthWeekday = ord,
                    dayOfWeek = dow
                )
                workingTitle = workingTitle.removeRange(nthMatch.range)
            }
            monthlyDayMatch != null -> {
                val day = (monthlyDayMatch.groupValues[1].toIntOrNull() ?: today.dayOfMonth).coerceIn(1, 31)
                recurrenceRule = RecurrenceRule(
                    type = RecurrenceType.MONTHLY_DAY,
                    interval = 1,
                    dayOfMonth = day
                )
                workingTitle = workingTitle.removeRange(monthlyDayMatch.range)
            }
            everyNDaysMatch != null -> {
                val interval = (everyNDaysMatch.groupValues[1].toIntOrNull() ?: 1).coerceIn(1, 365)
                recurrenceRule = RecurrenceRule(
                    type = RecurrenceType.DAILY,
                    interval = interval
                )
                workingTitle = workingTitle.removeRange(everyNDaysMatch.range)
            }
            weeklyOnDayMatch != null -> {
                val dow = parseDayOfWeek(weeklyOnDayMatch.groupValues[1]) ?: today.dayOfWeek
                recurrenceRule = RecurrenceRule(
                    type = RecurrenceType.WEEKLY,
                    interval = 1,
                    dayOfWeek = dow
                )
                workingTitle = workingTitle.removeRange(weeklyOnDayMatch.range)
            }
            dailyMatch != null -> {
                recurrenceRule = RecurrenceRule(
                    type = RecurrenceType.DAILY,
                    interval = 1
                )
                workingTitle = workingTitle.removeRange(dailyMatch.range)
            }
            weeklyMatch != null -> {
                recurrenceRule = RecurrenceRule(
                    type = RecurrenceType.WEEKLY,
                    interval = 1,
                    dayOfWeek = today.dayOfWeek
                )
                workingTitle = workingTitle.removeRange(weeklyMatch.range)
            }
        }

        if (recurrenceRule != null) {
            detectedTokens.add("🔁 ${recurrenceRule.toDisplayLabel()}")
        }

        // 5. Due Date
        var dueDateEpochDay: Long? = null
        var dueDateLabel: String? = null

        val todayMatch = todayRegex.find(workingTitle)
        val tomorrowMatch = if (todayMatch == null) tomorrowRegex.find(workingTitle) else null
        val weekendMatch = if (todayMatch == null && tomorrowMatch == null) weekendRegex.find(workingTitle) else null
        val nextWeekMatch = if (todayMatch == null && tomorrowMatch == null && weekendMatch == null) {
            nextWeekRegex.find(workingTitle)
        } else null
        val inNDaysMatch = if (
            todayMatch == null && tomorrowMatch == null &&
            weekendMatch == null && nextWeekMatch == null
        ) {
            inNDaysRegex.find(workingTitle)
        } else null
        val weekdayMatch = if (
            todayMatch == null && tomorrowMatch == null &&
            weekendMatch == null && nextWeekMatch == null && inNDaysMatch == null
        ) {
            weekdayDueRegex.find(workingTitle)
        } else null

        when {
            todayMatch != null -> {
                dueDateEpochDay = today.toEpochDay()
                dueDateLabel = "Today"
                workingTitle = workingTitle.removeRange(todayMatch.range)
            }
            tomorrowMatch != null -> {
                dueDateEpochDay = today.plusDays(1).toEpochDay()
                dueDateLabel = "Tomorrow"
                workingTitle = workingTitle.removeRange(tomorrowMatch.range)
            }
            weekendMatch != null -> {
                val sat = today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY))
                dueDateEpochDay = sat.toEpochDay()
                dueDateLabel = "This Weekend"
                workingTitle = workingTitle.removeRange(weekendMatch.range)
            }
            nextWeekMatch != null -> {
                val target = today.plusDays(7)
                dueDateEpochDay = target.toEpochDay()
                dueDateLabel = "Next Week"
                workingTitle = workingTitle.removeRange(nextWeekMatch.range)
            }
            inNDaysMatch != null -> {
                val days = (inNDaysMatch.groupValues[1].toIntOrNull() ?: 1).coerceIn(1, 365)
                val target = today.plusDays(days.toLong())
                dueDateEpochDay = target.toEpochDay()
                dueDateLabel = if (days == 1) "Tomorrow" else "In $days days"
                workingTitle = workingTitle.removeRange(inNDaysMatch.range)
            }
            weekdayMatch != null -> {
                val prefix = weekdayMatch.groupValues[1].trim().lowercase(Locale.US)
                val dow = parseDayOfWeek(weekdayMatch.groupValues[2])
                if (dow != null) {
                    val targetDate = if (prefix == "next") {
                        today.with(TemporalAdjusters.next(dow))
                    } else {
                        today.with(TemporalAdjusters.nextOrSame(dow))
                    }
                    dueDateEpochDay = targetDate.toEpochDay()
                    val dayName = dow.getDisplayName(TextStyle.FULL, Locale.getDefault())
                    dueDateLabel = if (targetDate == today) "Today ($dayName)" else dayName
                    workingTitle = workingTitle.removeRange(weekdayMatch.range)
                }
            }
        }

        // If recurrence rule matched without an explicit due date, anchor to first matching date
        if (dueDateEpochDay == null && recurrenceRule != null) {
            val anchorDate = recurrenceRule.firstMatchingOrAfter(today)
            dueDateEpochDay = anchorDate.toEpochDay()
            dueDateLabel = when (anchorDate) {
                today -> "Today"
                today.plusDays(1) -> "Tomorrow"
                else -> anchorDate.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()) +
                    ", ${anchorDate.month.getDisplayName(TextStyle.SHORT, Locale.getDefault())} ${anchorDate.dayOfMonth}"
            }
        }

        // Optional time suffix ("at 5pm", "at 14:30")
        var startMinute: Int? = null
        var durationMinutes: Int? = null
        val durationMatch = durationRegex.find(workingTitle)
        if (durationMatch != null) {
            val amount = durationMatch.groupValues[1].toDoubleOrNull() ?: 0.0
            val isHours = durationMatch.groupValues[2].lowercase(Locale.US).startsWith("h")
            durationMinutes = (if (isHours) amount * 60 else amount).toInt().coerceIn(5, 16 * 60)
            detectedTokens.add("⏱ ${formatDuration(durationMinutes)}")
            workingTitle = workingTitle.removeRange(durationMatch.range)
        }

        val timeMatch = timeSuffixRegex.find(workingTitle)
        if (timeMatch != null) {
            startMinute = parseClock(timeMatch.groupValues[1], timeMatch.groupValues[2], timeMatch.groupValues[3])
        }
        if (timeMatch != null && startMinute != null) {
            val timeStr = formatClock(startMinute)
            if (dueDateLabel != null) {
                dueDateLabel = "$dueDateLabel • $timeStr"
            } else {
                dueDateEpochDay = today.toEpochDay()
                dueDateLabel = "Today • $timeStr"
            }
            workingTitle = workingTitle.removeRange(timeMatch.range)
        }

        if (dueDateLabel != null) {
            detectedTokens.add("📅 $dueDateLabel")
        }

        // 6. Checklist / Subtasks Extraction from rawNotes
        if (rawNotes.isNotBlank()) {
            rawNotes.lineSequence().forEach { line ->
                val match = checklistLineRegex.matchEntire(line)
                if (match != null) {
                    val itemText = match.groupValues[1].trim()
                    if (itemText.isNotEmpty() && itemText !in extractedSubtasks) {
                        extractedSubtasks.add(itemText)
                    }
                }
            }
        }

        if (extractedSubtasks.isNotEmpty()) {
            val count = extractedSubtasks.size
            detectedTokens.add("☑️ $count Subtask${if (count == 1) "" else "s"}")
        }

        // 7. Clean up workingTitle
        val cleaned = workingTitle
            .replace(Regex("""\s+"""), " ")
            .replace(Regex("""(?i)\s+\b(on|at|by|for|in)\s*$"""), "")
            .trim()
            .trim(',', '-', '•', ':')
            .trim()

        val finalTitle = cleaned.ifBlank { rawTitle.trim() }

        return ParsedTaskSuggestion(
            cleanedTitle = finalTitle,
            dueDateEpochDay = dueDateEpochDay,
            dueDateLabel = dueDateLabel,
            isFlagged = isFlagged,
            recurrenceRule = recurrenceRule,
            extractedSubtasks = extractedSubtasks,
            detectedTokens = detectedTokens,
            startMinute = startMinute,
            durationMinutes = durationMinutes
        )
    }

    /** "3" "30" "pm" -> 930. Bare 1..7 is read as afternoon ("at 3" = 3 PM). Null when invalid. */
    internal fun parseClock(hourRaw: String, minuteRaw: String, meridiemRaw: String): Int? {
        var hour = hourRaw.toIntOrNull() ?: return null
        val minute = minuteRaw.takeIf { it.isNotBlank() }?.toIntOrNull() ?: 0
        if (minute !in 0..59) return null
        when (meridiemRaw.lowercase(Locale.US)) {
            "pm", "p" -> { if (hour !in 1..12) return null; if (hour != 12) hour += 12 }
            "am", "a" -> { if (hour !in 1..12) return null; if (hour == 12) hour = 0 }
            else -> { if (hour !in 0..23) return null; if (hour in 1..7) hour += 12 }
        }
        return hour * 60 + minute
    }

    fun formatClock(minuteOfDay: Int): String {
        val h = (minuteOfDay / 60) % 24
        val m = minuteOfDay % 60
        val h12 = if (h % 12 == 0) 12 else h % 12
        val suffix = if (h < 12) "AM" else "PM"
        return if (m == 0) "$h12 $suffix" else String.format(Locale.US, "%d:%02d %s", h12, m, suffix)
    }

    fun formatDuration(minutes: Int): String = when {
        minutes < 60 -> "${minutes}m"
        minutes % 60 == 0 -> "${minutes / 60}h"
        else -> "${minutes / 60}h ${minutes % 60}m"
    }

    /**
     * Splits multi-clause voice transcriptions or compound brain-dumps into individual tasks.
     * Handles:
     * - Newlines & numbered lists (1. ... 2. ...)
     * - Semicolons and conjunctions: ", and ", "; ", " also ", " then ", " plus "
     */
    fun splitMultiTaskInput(input: String): List<String> {
        val trimmed = input.trim()
        if (trimmed.isBlank()) return emptyList()

        val lines = trimmed.lines().map { it.trim() }.filter { it.isNotBlank() }
        if (lines.size > 1) {
            val isList = lines.all { it.matches(Regex("""^\s*(?:\d+[\.\)]|[-*•])\s+.*""")) }
            if (isList || lines.size >= 2) {
                return lines.map {
                    it.replace(Regex("""^\s*(?:\d+[\.\)]|[-*•])\s+"""), "").trim()
                }.filter { it.isNotBlank() }
            }
        }

        val clauseSplitRegex = Regex("""(?i)(?:;\s*|,\s*(?:and\s+also|and\s+then|and|also|plus)\s+|\s+(?:and\s+also|and\s+then|also|then|plus)\s+)""")
        val clauses = trimmed.split(clauseSplitRegex).map { it.trim() }.filter { it.length > 2 }
        return if (clauses.size > 1) clauses else listOf(trimmed)
    }

    fun parseMultiple(
        input: String,
        today: LocalDate = LocalDate.now()
    ): List<ParsedTaskDraft> {
        val clauses = splitMultiTaskInput(input)
        return clauses.map { clause ->
            val suggestion = parse(clause, today = today)
            ParsedTaskDraft(
                title = suggestion.cleanedTitle,
                dueDateEpochDay = suggestion.dueDateEpochDay,
                isFlagged = suggestion.isFlagged,
                recurrenceRule = suggestion.recurrenceRule,
                startMinute = suggestion.startMinute,
                durationMinutes = suggestion.durationMinutes
            )
        }
    }

    private fun parseOrdinal(raw: String): Int {
        return when (raw.lowercase(Locale.US)) {
            "1st", "first" -> 1
            "2nd", "second" -> 2
            "3rd", "third" -> 3
            "4th", "fourth" -> 4
            "last" -> -1
            else -> 1
        }
    }

    private fun parseDayOfWeek(raw: String): DayOfWeek? {
        return when (raw.lowercase(Locale.US).removeSuffix("s")) {
            "monday", "mon" -> DayOfWeek.MONDAY
            "tuesday", "tue" -> DayOfWeek.TUESDAY
            "wednesday", "wed" -> DayOfWeek.WEDNESDAY
            "thursday", "thu" -> DayOfWeek.THURSDAY
            "friday", "fri" -> DayOfWeek.FRIDAY
            "saturday", "sat" -> DayOfWeek.SATURDAY
            "sunday", "sun" -> DayOfWeek.SUNDAY
            else -> null
        }
    }
}
