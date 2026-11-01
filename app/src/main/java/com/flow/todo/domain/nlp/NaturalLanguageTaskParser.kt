package com.flow.todo.domain.nlp

import com.flow.todo.domain.model.GtdBucket
import com.flow.todo.domain.model.GtdContext
import com.flow.todo.domain.model.ParsedTaskDraft
import com.flow.todo.domain.model.ParsedTaskSuggestion
import com.flow.todo.domain.model.Project
import com.flow.todo.domain.model.RecurrenceRule
import com.flow.todo.domain.model.RecurrenceType
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
import java.util.Locale

object NaturalLanguageTaskParser {

    private val priorityRegex = Regex("""(?i)(?:^|\s)(!flag|!high|!urgent|!important|!p1|!!)(?=\s|$)""")
    private val bucketRegex = Regex("""(?i)(?:^|\s)@(inbox|next|someday|maybe|scheduled)(?=\s|$)""")
    private val contextTagRegex = Regex("""(?i)(?:^|\s)(@(work|home|errands|gym|calls|store|office|desk))(?=\s|$)""")
    private val projectRegex = Regex("""#\"([^\"]+)\"|#([A-Za-z0-9_-]+)""")

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
    private val timeSuffixRegex = Regex("""(?i)\bat\s+(\d{1,2}(?::\d{2})?\s*(?:am|pm)?)\b""")
    private val checklistLineRegex = Regex("""^\s*(?:[-*]\s*\[\s*\]|\[\s*\]|[-*])\s+(.+)$""")

    fun parse(
        rawTitle: String,
        rawNotes: String = "",
        projects: List<Project> = emptyList(),
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

        // 2. GTD Bucket
        var bucket: GtdBucket? = null
        val bucketMatch = bucketRegex.find(workingTitle)
        if (bucketMatch != null) {
            val rawBucket = bucketMatch.groupValues[1].lowercase(Locale.US)
            bucket = when (rawBucket) {
                "inbox" -> GtdBucket.INBOX
                "next" -> GtdBucket.NEXT_ACTION
                "scheduled" -> GtdBucket.SCHEDULED
                "someday", "maybe" -> GtdBucket.SOMEDAY
                else -> null
            }
            if (bucket != null) {
                detectedTokens.add("📥 @${bucket.label}")
                workingTitle = workingTitle.removeRange(bucketMatch.range)
            }
        }

        // 3. Project (#ProjectName or #"Multi Word")
        var matchedProject: Project? = null
        var suggestedNewProjectName: String? = null
        val projectMatch = projectRegex.find(workingTitle)
        if (projectMatch != null) {
            val tagText = projectMatch.groupValues[1].ifBlank { projectMatch.groupValues[2] }.trim()
            if (tagText.isNotBlank()) {
                val found = projects.firstOrNull { it.name.equals(tagText, ignoreCase = true) }
                    ?: projects.firstOrNull { it.name.startsWith(tagText, ignoreCase = true) }
                    ?: projects.firstOrNull { it.name.contains(tagText, ignoreCase = true) }

                if (found != null) {
                    matchedProject = found
                    detectedTokens.add("📁 #${found.name}")
                } else {
                    val formattedNewName = tagText.replace('-', ' ').replace('_', ' ').trim()
                    suggestedNewProjectName = formattedNewName
                    detectedTokens.add("📁 #$formattedNewName (New)")
                }
                workingTitle = workingTitle.removeRange(projectMatch.range)
            }
        }

        // 3.5 GTD Context Tag (@work, @home, @errands, etc.)
        var contextTag: String? = null
        val ctxMatch = contextTagRegex.find(workingTitle)
        if (ctxMatch != null) {
            val matchedTag = ctxMatch.groupValues[1].lowercase(Locale.getDefault())
            contextTag = matchedTag
            val label = GtdContext.ALL.firstOrNull { it.tag.equals(matchedTag, ignoreCase = true) }?.label ?: matchedTag
            detectedTokens.add("📍 $matchedTag ($label)")
            workingTitle = workingTitle.removeRange(ctxMatch.range)
        } else {
            // Semantic keyword auto-detection if no explicit @tag
            val semanticContext = GtdContext.findMatchingContext(workingTitle)
            if (semanticContext != null) {
                contextTag = semanticContext.tag
                detectedTokens.add("📍 ${semanticContext.tag} (${semanticContext.label})")
            }
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
        val timeMatch = timeSuffixRegex.find(workingTitle)
        if (timeMatch != null) {
            val timeStr = timeMatch.groupValues[1].trim()
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
            matchedProject = matchedProject,
            suggestedNewProjectName = suggestedNewProjectName,
            isFlagged = isFlagged,
            bucket = bucket,
            recurrenceRule = recurrenceRule,
            extractedSubtasks = extractedSubtasks,
            detectedTokens = detectedTokens,
            contextTag = contextTag
        )
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
        projects: List<Project> = emptyList(),
        today: LocalDate = LocalDate.now()
    ): List<ParsedTaskDraft> {
        val clauses = splitMultiTaskInput(input)
        return clauses.map { clause ->
            val suggestion = parse(clause, projects = projects, today = today)
            ParsedTaskDraft(
                title = suggestion.cleanedTitle,
                bucket = suggestion.bucket ?: GtdBucket.INBOX,
                dueDateEpochDay = suggestion.dueDateEpochDay,
                projectId = suggestion.matchedProject?.id,
                isFlagged = suggestion.isFlagged,
                recurrenceRule = suggestion.recurrenceRule,
                contextTag = suggestion.contextTag
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
