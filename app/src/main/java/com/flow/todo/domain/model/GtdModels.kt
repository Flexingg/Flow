package com.flow.todo.domain.model

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
import java.util.Locale

enum class GtdBucket(val label: String, val description: String) {
    INBOX("Inbox", "Unclarified thoughts and quick captures"),
    NEXT_ACTION("Next Action", "Ready to do as soon as possible"),
    SCHEDULED("Scheduled", "Assigned to a specific date"),
    SOMEDAY("Someday / Maybe", "Ideas and tasks for the future")
}

enum class FlowTab(val label: String) {
    INBOX("Inbox"),
    TODAY_NEXT("Today"),
    CALENDAR("Calendar"),
    PROJECTS("Projects"),
    GOALS("Goals")
}

enum class RecurrenceType(val label: String) {
    NONE("One-time"),
    DAILY("Daily"),
    WEEKLY("Weekly"),
    MONTHLY_DAY("Monthly (Day X)"),
    MONTHLY_NTH_WEEKDAY("Monthly (Nth Weekday)")
}

/**
 * Supports:
 * - DAILY: every [interval] days (default 1)
 * - WEEKLY: every [interval] weeks on [dayOfWeek]
 * - MONTHLY_DAY: on day [dayOfMonth] (1..31) of every month
 * - MONTHLY_NTH_WEEKDAY: on the [nthWeekday] (1=1st, 2=2nd, 3=3rd, 4=4th, -1=Last) [dayOfWeek] of every month
 */
data class RecurrenceRule(
    val type: RecurrenceType = RecurrenceType.NONE,
    val interval: Int = 1,
    val dayOfMonth: Int = LocalDate.now().dayOfMonth,
    val nthWeekday: Int = 1, // 1, 2, 3, 4, or -1 (Last)
    val dayOfWeek: DayOfWeek = LocalDate.now().dayOfWeek
) {
    val isRecurring: Boolean
        get() = type != RecurrenceType.NONE

    fun toDisplayLabel(): String {
        val shortDay = dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())
        return when (type) {
            RecurrenceType.NONE -> ""
            RecurrenceType.DAILY -> if (interval <= 1) "Daily" else "Every $interval days"
            RecurrenceType.WEEKLY -> if (interval <= 1) "Weekly • $shortDay" else "Every $interval wks • $shortDay"
            RecurrenceType.MONTHLY_DAY -> "Monthly • ${ordinalSuffix(dayOfMonth)}"
            RecurrenceType.MONTHLY_NTH_WEEKDAY -> {
                val nthLabel = when (nthWeekday) {
                    1 -> "1st"
                    2 -> "2nd"
                    3 -> "3rd"
                    4 -> "4th"
                    -1 -> "Last"
                    else -> "${nthWeekday}th"
                }
                "Monthly • $nthLabel $shortDay"
            }
        }
    }

    fun nextOccurrenceAfter(baseEpochDay: Long): Long {
        val baseDate = LocalDate.ofEpochDay(baseEpochDay)
        val safeInterval = interval.coerceAtLeast(1)
        return when (type) {
            RecurrenceType.NONE -> baseEpochDay
            RecurrenceType.DAILY -> baseDate.plusDays(safeInterval.toLong()).toEpochDay()
            RecurrenceType.WEEKLY -> {
                val nextCandidate = baseDate.with(TemporalAdjusters.next(dayOfWeek))
                if (safeInterval == 1) {
                    nextCandidate.toEpochDay()
                } else {
                    baseDate.plusWeeks(safeInterval.toLong()).with(dayOfWeek).toEpochDay()
                }
            }
            RecurrenceType.MONTHLY_DAY -> {
                val nextMonth = YearMonth.from(baseDate).plusMonths(safeInterval.toLong())
                val clampedDay = dayOfMonth.coerceIn(1, nextMonth.lengthOfMonth())
                nextMonth.atDay(clampedDay).toEpochDay()
            }
            RecurrenceType.MONTHLY_NTH_WEEKDAY -> {
                val thisMonthDate = resolveNthWeekday(YearMonth.from(baseDate), nthWeekday, dayOfWeek)
                if (thisMonthDate.isAfter(baseDate) && safeInterval == 1) {
                    thisMonthDate.toEpochDay()
                } else {
                    val nextMonth = YearMonth.from(baseDate).plusMonths(safeInterval.toLong())
                    resolveNthWeekday(nextMonth, nthWeekday, dayOfWeek).toEpochDay()
                }
            }
        }
    }

    fun firstMatchingOrAfter(fromDate: LocalDate): LocalDate {
        return when (type) {
            RecurrenceType.NONE -> fromDate
            RecurrenceType.DAILY -> fromDate
            RecurrenceType.WEEKLY -> {
                if (fromDate.dayOfWeek == dayOfWeek) fromDate
                else fromDate.with(TemporalAdjusters.next(dayOfWeek))
            }
            RecurrenceType.MONTHLY_DAY -> {
                val ym = YearMonth.from(fromDate)
                val clampedThisMonth = ym.atDay(dayOfMonth.coerceIn(1, ym.lengthOfMonth()))
                if (!clampedThisMonth.isBefore(fromDate)) {
                    clampedThisMonth
                } else {
                    val nextYm = ym.plusMonths(1)
                    nextYm.atDay(dayOfMonth.coerceIn(1, nextYm.lengthOfMonth()))
                }
            }
            RecurrenceType.MONTHLY_NTH_WEEKDAY -> {
                val ym = YearMonth.from(fromDate)
                val thisMonthTarget = resolveNthWeekday(ym, nthWeekday, dayOfWeek)
                if (!thisMonthTarget.isBefore(fromDate)) {
                    thisMonthTarget
                } else {
                    resolveNthWeekday(ym.plusMonths(1), nthWeekday, dayOfWeek)
                }
            }
        }
    }

    fun occursOnDate(targetDate: LocalDate, anchorEpochDay: Long?): Boolean {
        val anchorDate = anchorEpochDay?.let { LocalDate.ofEpochDay(it) }
        if (anchorDate != null && targetDate.isBefore(anchorDate)) return false
        if (anchorDate != null && targetDate == anchorDate) return true

        val safeInterval = interval.coerceAtLeast(1)
        return when (type) {
            RecurrenceType.NONE -> anchorDate == targetDate
            RecurrenceType.DAILY -> {
                if (anchorDate == null) true
                else {
                    val daysDiff = targetDate.toEpochDay() - anchorDate.toEpochDay()
                    daysDiff >= 0 && daysDiff % safeInterval == 0L
                }
            }
            RecurrenceType.WEEKLY -> targetDate.dayOfWeek == dayOfWeek
            RecurrenceType.MONTHLY_DAY -> {
                val ym = YearMonth.from(targetDate)
                val expectedDay = dayOfMonth.coerceIn(1, ym.lengthOfMonth())
                targetDate.dayOfMonth == expectedDay
            }
            RecurrenceType.MONTHLY_NTH_WEEKDAY -> {
                val ym = YearMonth.from(targetDate)
                resolveNthWeekday(ym, nthWeekday, dayOfWeek) == targetDate
            }
        }
    }

    fun toStorageString(): String? {
        if (type == RecurrenceType.NONE) return null
        return "${type.name}|$interval|$dayOfMonth|$nthWeekday|${dayOfWeek.name}"
    }

    companion object {
        fun fromStorageString(raw: String?): RecurrenceRule? {
            if (raw.isNullOrBlank()) return null
            return runCatching {
                val parts = raw.split("|")
                val type = RecurrenceType.valueOf(parts.getOrNull(0) ?: return null)
                if (type == RecurrenceType.NONE) return null
                val interval = parts.getOrNull(1)?.toIntOrNull() ?: 1
                val dayOfMonth = parts.getOrNull(2)?.toIntOrNull() ?: 1
                val nthWeekday = parts.getOrNull(3)?.toIntOrNull() ?: 1
                val dayOfWeek = parts.getOrNull(4)?.let { DayOfWeek.valueOf(it) } ?: DayOfWeek.MONDAY
                RecurrenceRule(
                    type = type,
                    interval = interval,
                    dayOfMonth = dayOfMonth,
                    nthWeekday = nthWeekday,
                    dayOfWeek = dayOfWeek
                )
            }.getOrNull()
        }

        fun resolveNthWeekday(yearMonth: YearMonth, nthWeekday: Int, dayOfWeek: DayOfWeek): LocalDate {
            return if (nthWeekday == -1) {
                yearMonth.atEndOfMonth().with(TemporalAdjusters.lastInMonth(dayOfWeek))
            } else {
                val candidate = yearMonth.atDay(1).with(TemporalAdjusters.dayOfWeekInMonth(nthWeekday, dayOfWeek))
                if (candidate.month == yearMonth.month) {
                    candidate
                } else {
                    yearMonth.atEndOfMonth().with(TemporalAdjusters.lastInMonth(dayOfWeek))
                }
            }
        }

        fun ordinalSuffix(day: Int): String {
            if (day in 11..13) return "${day}th"
            return when (day % 10) {
                1 -> "${day}st"
                2 -> "${day}nd"
                3 -> "${day}rd"
                else -> "${day}th"
            }
        }
    }
}

data class Project(
    val id: String,
    val name: String,
    val colorHex: Long,
    val createdAt: Long = System.currentTimeMillis()
)

enum class HealthMetricType(
    val label: String,
    val unit: String,
    val defaultTarget: Double,
    val presetTargets: List<Double>,
    val isMacronutrient: Boolean = false
) {
    STEPS("Steps", "steps", 10000.0, listOf(5000.0, 8000.0, 10000.0, 12000.0, 15000.0)),
    ACTIVE_CALORIES("Active Calories", "kcal", 500.0, listOf(300.0, 450.0, 600.0, 800.0)),
    EXERCISE_MINUTES("Exercise Time", "min", 30.0, listOf(20.0, 30.0, 45.0, 60.0)),
    SLEEP_HOURS("Sleep Duration", "hrs", 7.5, listOf(6.5, 7.0, 7.5, 8.0, 8.5)),
    PROTEIN_GRAMS("Protein", "g", 150.0, listOf(80.0, 120.0, 150.0, 180.0, 220.0), isMacronutrient = true),
    CARBS_GRAMS("Carbohydrates", "g", 220.0, listOf(50.0, 100.0, 150.0, 220.0, 300.0), isMacronutrient = true),
    FAT_GRAMS("Total Fat", "g", 65.0, listOf(40.0, 55.0, 65.0, 80.0, 100.0), isMacronutrient = true),
    DIETARY_CALORIES("Nutrition Calories", "kcal", 2000.0, listOf(1500.0, 1800.0, 2000.0, 2400.0, 2800.0), isMacronutrient = true),
    FIBER_GRAMS("Dietary Fiber", "g", 30.0, listOf(20.0, 25.0, 30.0, 38.0, 45.0), isMacronutrient = true),
    SUGAR_GRAMS("Sugar", "g", 30.0, listOf(15.0, 25.0, 30.0, 50.0), isMacronutrient = true),
    DISTANCE_KM("Distance", "km", 5.0, listOf(3.0, 5.0, 8.0, 10.0)),
    HYDRATION_LITERS("Hydration", "L", 2.5, listOf(1.5, 2.0, 2.5, 3.0, 3.5)),
    FLOORS_CLIMBED("Floors Climbed", "floors", 10.0, listOf(5.0, 10.0, 15.0, 20.0)),
    WEIGHT_KG("Body Weight", "kg", 70.0, listOf(60.0, 65.0, 70.0, 75.0, 80.0)),
    RESTING_HEART_RATE("Avg Heart Rate", "bpm", 65.0, listOf(55.0, 60.0, 65.0, 70.0));

    fun formatValue(value: Double): String {
        return when (this) {
            STEPS, ACTIVE_CALORIES, EXERCISE_MINUTES, FLOORS_CLIMBED, RESTING_HEART_RATE,
            PROTEIN_GRAMS, CARBS_GRAMS, FAT_GRAMS, DIETARY_CALORIES, FIBER_GRAMS, SUGAR_GRAMS ->
                String.format(Locale.US, "%,d", value.toLong())
            SLEEP_HOURS, DISTANCE_KM, HYDRATION_LITERS, WEIGHT_KG ->
                String.format(Locale.US, "%.1f", value)
        }
    }
}

data class HealthGoalSpec(
    val metricType: HealthMetricType,
    val targetValue: Double
) {
    fun toStorageString(): String = "${metricType.name}|$targetValue"

    fun toDisplayLabel(currentValue: Double? = null): String {
        val targetStr = metricType.formatValue(targetValue)
        return if (currentValue != null) {
            val currentStr = metricType.formatValue(currentValue)
            "${metricType.label}: $currentStr / $targetStr ${metricType.unit}"
        } else {
            "${metricType.label}: $targetStr ${metricType.unit}"
        }
    }

    fun progressFraction(currentValue: Double?): Float {
        if (currentValue == null || targetValue <= 0.0) return 0f
        return (currentValue / targetValue).toFloat().coerceIn(0f, 1f)
    }

    companion object {
        fun fromStorageString(raw: String?): HealthGoalSpec? {
            if (raw.isNullOrBlank()) return null
            return runCatching {
                val parts = raw.split("|")
                val type = HealthMetricType.valueOf(parts[0])
                val target = parts[1].toDouble()
                HealthGoalSpec(type, target)
            }.getOrNull()
        }
    }
}

data class HealthDailySnapshot(
    val isSdkAvailable: Boolean = false,
    val grantedPermissionCount: Int = 0,
    val totalPermissionCount: Int = 19,
    val metrics: Map<HealthMetricType, Double> = emptyMap(),
    val lastSyncedMillis: Long = 0L
) {
    val hasAnyPermission: Boolean
        get() = grantedPermissionCount > 0

    val hasAllPermissions: Boolean
        get() = grantedPermissionCount >= totalPermissionCount && totalPermissionCount > 0

    fun valueFor(type: HealthMetricType): Double = metrics[type] ?: 0.0
}

/**
 * First-Class Goal System (separate from GTD Tasks):
 * Supports 3 distinct goal sources:
 * 1. TASK_COMPLETION: X tasks completed in a Day or Week (in general or from a specific Project)
 * 2. HEALTH_CONNECT: Biometric goal (Steps, Calories, Sleep, Exercise, Water, Distance, etc.) >= or <= X
 * 3. APP_USAGE: App screen-time (active minutes) or launch count (times opened) <= or >= X in a Day
 */
enum class GoalCategory(val label: String, val subtitle: String) {
    TASK_COMPLETION("Task Milestone", "Tasks completed in a day or week (general or project)"),
    HEALTH_CONNECT("Health Connect", "Live biometric stats from Android Health Connect"),
    APP_USAGE("App Usage Limit", "Daily active screen time or times opened for an app")
}

enum class GoalComparison(val label: String, val shortLabel: String, val symbol: String) {
    AT_LEAST("At Least (More than / ≥)", "More than", "≥"),
    AT_MOST("At Most (Less than / ≤)", "Less than", "≤")
}

enum class GoalPeriod(val label: String) {
    DAILY("Daily"),
    WEEKLY("Weekly")
}

enum class AppUsageMetricType(
    val label: String,
    val unit: String,
    val defaultLimit: Double,
    val presetValues: List<Double>
) {
    ACTIVE_MINUTES("Active Screen Time", "min", 30.0, listOf(10.0, 15.0, 30.0, 45.0, 60.0, 90.0)),
    LAUNCH_COUNT("Times Opened", "opens", 5.0, listOf(2.0, 3.0, 5.0, 8.0, 10.0, 15.0));

    fun formatValue(value: Double): String {
        return String.format(Locale.US, "%,d", value.toLong())
    }
}

data class InstalledAppInfo(
    val packageName: String,
    val appName: String
)

data class AppUsageStat(
    val packageName: String,
    val appName: String,
    val activeMinutesToday: Double = 0.0,
    val launchCountToday: Int = 0
)

data class AppUsageSnapshot(
    val hasUsagePermission: Boolean = false,
    val statsByPackage: Map<String, AppUsageStat> = emptyMap(),
    val installedApps: List<InstalledAppInfo> = emptyList(),
    val lastSyncedMillis: Long = 0L
) {
    fun statFor(packageName: String): AppUsageStat? = statsByPackage[packageName]
}

data class FlowGoal(
    val id: String,
    val title: String,
    val category: GoalCategory,
    val comparison: GoalComparison = GoalComparison.AT_LEAST,
    val period: GoalPeriod = GoalPeriod.DAILY,
    val targetValue: Double,
    val projectId: String? = null, // For TASK_COMPLETION: null = any task, non-null = specific project
    val healthMetric: HealthMetricType? = null, // For HEALTH_CONNECT
    val targetPackageName: String? = null, // For APP_USAGE
    val targetAppName: String? = null, // For APP_USAGE
    val appUsageMetric: AppUsageMetricType? = null, // For APP_USAGE
    val createdAt: Long = System.currentTimeMillis()
)

data class EvaluatedGoal(
    val goal: FlowGoal,
    val currentValue: Double,
    val targetValue: Double,
    val isCompleted: Boolean,
    val momentumContribution: Float, // 0f..1f contribution to the Red->Purple->Blue->Yellow->Green color code
    val barFillFraction: Float, // 0f..1f visual fill for the Goal Bar
    val formattedProgressText: String, // e.g. "6,420 / ≥ 10,000 steps" or "12 / ≤ 30 min active"
    val subtitleBadgeText: String, // e.g. "Daily • Health Connect" or "Weekly • Project: Work"
    val isOverLimit: Boolean = false // true when AT_MOST goal is exceeded
)

enum class ShizukuStatus(val label: String, val description: String) {
    READY_SECURE_SETTINGS("Active (Direct)", "WRITE_SECURE_SETTINGS granted • Instant System UI morphing"),
    READY_SHIZUKU("Active (Shizuku)", "Shizuku authorized • System UI colors morph with goal progress"),
    PERMISSION_REQUIRED("Permission Needed", "Tap to authorize Flow in Shizuku"),
    NOT_RUNNING("Shizuku Not Running", "Open & start Shizuku to sync Android System UI colors"),
    NOT_INSTALLED("Shizuku Not Installed", "Install Shizuku to unlock OS-wide Material You color morphing");

    val isReady: Boolean
        get() = this == READY_SECURE_SETTINGS || this == READY_SHIZUKU
}

data class SubtaskItem(
    val id: String = java.util.UUID.randomUUID().toString(),
    val title: String,
    val isCompleted: Boolean = false
) {
    fun toStoragePart(): String {
        val safeTitle = title.replace("|", " ").replace(";;", " ")
        return "$id|${if (isCompleted) 1 else 0}|$safeTitle"
    }

    companion object {
        fun listToStorageString(subtasks: List<SubtaskItem>): String? {
            if (subtasks.isEmpty()) return null
            return subtasks.joinToString(";;") { it.toStoragePart() }
        }

        fun listFromStorageString(raw: String?): List<SubtaskItem> {
            if (raw.isNullOrBlank()) return emptyList()
            return raw.split(";;").mapNotNull { entry ->
                val parts = entry.split("|", limit = 3)
                if (parts.size < 3 || parts[2].isBlank()) null
                else SubtaskItem(
                    id = parts[0].ifBlank { java.util.UUID.randomUUID().toString() },
                    isCompleted = parts[1] == "1",
                    title = parts[2]
                )
            }
        }
    }
}

data class TaskItem(
    val id: String,
    val title: String,
    val notes: String = "",
    val bucket: GtdBucket = GtdBucket.INBOX,
    val dueDateEpochDay: Long? = null,
    val projectId: String? = null,
    val isFlagged: Boolean = false,
    val isCompleted: Boolean = false,
    val recurrenceRule: RecurrenceRule? = null,
    val healthGoal: HealthGoalSpec? = null,
    val subtasks: List<SubtaskItem> = emptyList(),
    val contextTag: String? = null, // e.g. "@work", "@home", "@errands"
    val createdAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null
) {
    val completedSubtasksCount: Int
        get() = subtasks.count { it.isCompleted }

    val totalSubtasksCount: Int
        get() = subtasks.size

    val subtaskProgress: Float
        get() = if (subtasks.isEmpty()) 0f else completedSubtasksCount.toFloat() / subtasks.size.toFloat()
}

/**
 * Procedural Ambient Soundscapes for Deep Flow Focus Mode (Zero file assets needed)
 */
enum class AmbientSoundscape(val id: String, val label: String, val description: String) {
    OFF("off", "Mute", "Pure silence"),
    RAIN_SHOWER("rain", "Rain Shower", "Gentle acoustic rainfall with soft droplet transients"),
    FOREST_STREAM("stream", "River Flow", "Organic liquid streaming soundscape"),
    BROWN_NOISE("brown", "Deep Focus Noise", "Low-frequency smoothed noise for deep concentration"),
    BINAURAL_ALPHA("alpha", "Binaural Alpha (10Hz)", "10Hz binaural beat for flow state entrainment")
}

data class FocusSessionState(
    val isActive: Boolean = false,
    val isPaused: Boolean = false,
    val remainingSeconds: Int = 25 * 60,
    val totalSeconds: Int = 25 * 60,
    val targetTaskId: String? = null,
    val targetTaskTitle: String? = null,
    val soundscape: AmbientSoundscape = AmbientSoundscape.RAIN_SHOWER,
    val isPlayingAudio: Boolean = false
) {
    val progress: Float
        get() {
            if (totalSeconds <= 0) return 0f
            val elapsed = totalSeconds - remainingSeconds
            return (elapsed.toFloat() / totalSeconds.toFloat()).coerceIn(0f, 1f)
        }

    val formattedRemainingTime: String
        get() {
            val mins = remainingSeconds / 60
            val secs = remainingSeconds % 60
            return String.format(Locale.US, "%02d:%02d", mins, secs)
        }

    val formattedRemaining: String
        get() = formattedRemainingTime
}

/**
 * GTD Contexts (e.g. @work, @home, @errands)
 */
data class GtdContext(
    val tag: String,
    val label: String,
    val colorHex: Long,
    val defaultKeywords: List<String> = emptyList()
) {
    companion object {
        val ALL = listOf(
            GtdContext("@work", "Work", 0xFF2F6BFF, listOf("work", "office", "email", "client", "meeting", "code", "report")),
            GtdContext("@home", "Home", 0xFF22B35E, listOf("home", "house", "clean", "cook", "laundry", "fix", "dishes")),
            GtdContext("@errands", "Errands", 0xFFFF9F0A, listOf("errand", "buy", "groceries", "store", "pharmacy", "post", "shop")),
            GtdContext("@gym", "Fitness", 0xFFFF3B5C, listOf("gym", "workout", "run", "lift", "stretch", "training", "exercise")),
            GtdContext("@calls", "Calls", 0xFFAF52DE, listOf("call", "phone", "zoom", "sync", "talk", "chat"))
        )

        fun findMatchingContext(text: String): GtdContext? {
            val lower = text.lowercase(Locale.getDefault())
            // Check explicit tag
            ALL.firstOrNull { lower.contains(it.tag.lowercase(Locale.getDefault())) }?.let { return it }
            // Check keywords
            return ALL.firstOrNull { ctx ->
                ctx.defaultKeywords.any { kw -> lower.contains(Regex("\\b$kw\\b")) }
            }
        }
    }
}

/**
 * Multi-Task Draft for Voice Brain-Dump and multi-clause parser
 */
data class ParsedTaskDraft(
    val id: String = java.util.UUID.randomUUID().toString(),
    val title: String,
    val notes: String = "",
    val bucket: GtdBucket = GtdBucket.INBOX,
    val dueDateEpochDay: Long? = null,
    val projectId: String? = null,
    val isFlagged: Boolean = false,
    val recurrenceRule: RecurrenceRule? = null,
    val healthGoal: HealthGoalSpec? = null,
    val contextTag: String? = null
)

/**
 * Daily Rituals: Morning "Wake to Flow" and Evening "Sunset Review"
 */
enum class DailyRitualType {
    MORNING_WAKE,
    EVENING_SUNSET
}

data class DailyRitualState(
    val isMorningOpen: Boolean = false,
    val isEveningOpen: Boolean = false,
    val top3TaskIds: Set<String> = emptySet(),
    val morningCompletedEpochDay: Long? = null,
    val eveningCompletedEpochDay: Long? = null
)

data class ParsedTaskSuggestion(
    val cleanedTitle: String,
    val dueDateEpochDay: Long? = null,
    val dueDateLabel: String? = null,
    val matchedProject: Project? = null,
    val suggestedNewProjectName: String? = null,
    val isFlagged: Boolean = false,
    val bucket: GtdBucket? = null,
    val recurrenceRule: RecurrenceRule? = null,
    val extractedSubtasks: List<String> = emptyList(),
    val detectedTokens: List<String> = emptyList(),
    val contextTag: String? = null
) {
    val hasAnyDetection: Boolean
        get() = dueDateEpochDay != null || matchedProject != null || suggestedNewProjectName != null ||
            isFlagged || bucket != null || recurrenceRule != null || extractedSubtasks.isNotEmpty() || contextTag != null
}

enum class ShieldSeverity(val label: String) {
    WARNING_80("80% Gentle Nudge"),
    LIMIT_100("100% Limit Reached")
}

data class AppUsageShieldAlert(
    val goalId: String,
    val appName: String,
    val packageName: String,
    val severity: ShieldSeverity,
    val usageRatio: Float,
    val formattedStatus: String,
    val timestampMillis: Long = System.currentTimeMillis()
)

data class UpcomingDayGroup(
    val epochDay: Long,
    val dayTitle: String,
    val dateSubtitle: String,
    val isTomorrow: Boolean = false,
    val tasks: List<TaskItem>
)

data class ProjectWithStats(
    val project: Project,
    val tasks: List<TaskItem>,
    val completedCount: Int,
    val totalCount: Int
) {
    val progress: Float
        get() = if (totalCount == 0) 0f else completedCount.toFloat() / totalCount.toFloat()
}

data class MomentumPalettePreset(
    val name: String,
    val subtitle: String,
    val stopsHex: List<Long>
)

val DEFAULT_MOMENTUM_STOPS_HEX: List<Long> = listOf(
    0xFFFF3B5CL, // 0% Start: Crimson Red
    0xFFAF52DEL, // 25% Spark: Vivid Purple
    0xFF2F6BFFL, // 50% Flow: Cobalt Blue
    0xFFFFB800L, // 75% Surge: Solar Gold
    0xFF22B35EL  // 100% Complete: Emerald Green
)

val MOMENTUM_COLOR_SWATCHES: List<Pair<String, Long>> = listOf(
    "Crimson" to 0xFFFF3B5CL,
    "Hot Pink" to 0xFFFF2D55L,
    "Rose" to 0xFFFF6482L,
    "Coral" to 0xFFFF6B4AL,
    "Sunset Orange" to 0xFFFF9500L,
    "Solar Gold" to 0xFFFFB800L,
    "Electric Lime" to 0xFFA8E000L,
    "Emerald" to 0xFF22B35EL,
    "Mint Teal" to 0xFF00C7BEL,
    "Aqua Cyan" to 0xFF00D2FFL,
    "Sky Blue" to 0xFF32ADE6L,
    "Cobalt Blue" to 0xFF2F6BFFL,
    "Royal Indigo" to 0xFF5856D6L,
    "Vivid Purple" to 0xFFAF52DEL,
    "Neon Violet" to 0xFFBF5AF2L,
    "Lavender" to 0xFFD4A5FFL
)

val MOMENTUM_PALETTE_PRESETS: List<MomentumPalettePreset> = listOf(
    MomentumPalettePreset(
        name = "Classic Flow",
        subtitle = "Red → Purple → Blue → Gold → Green",
        stopsHex = DEFAULT_MOMENTUM_STOPS_HEX
    ),
    MomentumPalettePreset(
        name = "Oceanic Tide",
        subtitle = "Indigo → Cobalt → Sky → Cyan → Mint",
        stopsHex = listOf(0xFF5856D6L, 0xFF2F6BFFL, 0xFF32ADE6L, 0xFF00D2FFL, 0xFF00C7BEL)
    ),
    MomentumPalettePreset(
        name = "Sunset Ember",
        subtitle = "Pink → Purple → Coral → Orange → Gold",
        stopsHex = listOf(0xFFFF2D55L, 0xFFAF52DEL, 0xFFFF6B4AL, 0xFFFF9500L, 0xFFFFB800L)
    ),
    MomentumPalettePreset(
        name = "Cyber Neon",
        subtitle = "Hot Pink → Violet → Cobalt → Lime → Cyan",
        stopsHex = listOf(0xFFFF2D55L, 0xFFBF5AF2L, 0xFF2F6BFFL, 0xFFA8E000L, 0xFF00D2FFL)
    ),
    MomentumPalettePreset(
        name = "Botanical Zen",
        subtitle = "Coral → Gold → Lime → Emerald → Mint",
        stopsHex = listOf(0xFFFF6B4AL, 0xFFFFB800L, 0xFFA8E000L, 0xFF22B35EL, 0xFF00C7BEL)
    )
)

data class AccessibilityPreferences(
    val reduceTransparency: Boolean = false,
    val reduceMotion: Boolean = false,
    val increasedContrast: Boolean = false,
    val darkThemeOverride: Boolean? = null, // null = follow system, true = dark, false = light
    val useDynamicColor: Boolean = true,
    val useMomentumColorSpectrum: Boolean = true, // Morph across momentumStopHexes based on goals
    val syncSystemColorsWithShizuku: Boolean = false, // Sync Android System UI Material You seed color via Shizuku
    val momentumStopHexes: List<Long> = DEFAULT_MOMENTUM_STOPS_HEX,
    val enableAppUsageShield: Boolean = true // Active App Usage Shield & 80%/100% Liquid-Glass Nudge
)

enum class CloudSyncStatus(val label: String) {
    NOT_CONFIGURED("Cloud Config Needed"),
    SIGNED_OUT("Signed Out"),
    SIGNING_IN("Signing In..."),
    SYNCING("Syncing with Firestore..."),
    SYNCED("Synced with Cloud"),
    ERROR("Sync Error")
}

data class FirebaseCloudConfig(
    val projectId: String = "",
    val applicationId: String = "",
    val apiKey: String = "",
    val webClientId: String = ""
) {
    val isConfigured: Boolean
        get() = projectId.isNotBlank() && applicationId.isNotBlank() && apiKey.isNotBlank()
}

data class CloudSyncState(
    val status: CloudSyncStatus = CloudSyncStatus.SIGNED_OUT,
    val userUid: String? = null,
    val userEmail: String? = null,
    val userDisplayName: String? = null,
    val isGoogleAccount: Boolean = false,
    val lastSyncedMillis: Long = 0L,
    val statusMessage: String = "Sign in with Google to sync tasks & goals across devices",
    val customConfig: FirebaseCloudConfig = FirebaseCloudConfig(),
    val isFirebaseReady: Boolean = false
) {
    val isSignedIn: Boolean
        get() = !userUid.isNullOrBlank()
}

data class DailyCompletionRecord(
    val epochDay: Long,
    val tasksCompletedCount: Int = 0,
    val goalsCompletedCount: Int = 0,
    val totalGoalsCount: Int = 0,
    val momentumScore: Float = 0f,
    val updatedAt: Long = System.currentTimeMillis()
)

data class ContributionDayCell(
    val epochDay: Long,
    val dateLabel: String,
    val dayOfWeekIndex: Int, // 0 = Mon .. 6 = Sun
    val isToday: Boolean = false,
    val isFuture: Boolean = false,
    val tasksCompleted: Int = 0,
    val goalsCompleted: Int = 0,
    val totalGoals: Int = 0,
    val momentumScore: Float = 0f,
    val combinedLevel: Int = 0, // 0..4 GitHub heatmap intensity
    val taskLevel: Int = 0,     // 0..4
    val goalLevel: Int = 0      // 0..4
)

data class ContributionChartSummary(
    val cells: List<ContributionDayCell> = emptyList(),
    val weeks: List<List<ContributionDayCell>> = emptyList(), // Columns of 7 days (Mon..Sun)
    val currentStreakDays: Int = 0,
    val longestStreakDays: Int = 0,
    val totalTasksCompletedInWindow: Int = 0,
    val totalGoalsMetInWindow: Int = 0,
    val activeDaysCount: Int = 0
)

data class CalendarEventItem(
    val id: String,
    val calendarId: Long = 0L,
    val calendarDisplayName: String = "Device Calendar",
    val calendarColorHex: Long = 0xFF2F6BFF,
    val accountName: String = "",
    val title: String,
    val description: String = "",
    val location: String = "",
    val startEpochMillis: Long,
    val endEpochMillis: Long,
    val isAllDay: Boolean = false,
    val isCompleted: Boolean = false,
    val completedAt: Long? = null,
    val source: String = "DEVICE"
) {
    val durationMinutes: Long
        get() = ((endEpochMillis - startEpochMillis) / 60_000L).coerceAtLeast(0L)
}

data class DeviceCalendarInfo(
    val id: Long,
    val displayName: String,
    val accountName: String,
    val colorHex: Long,
    val isSelected: Boolean = true
)

data class CalendarDailySummary(
    val epochDay: Long,
    val totalEventsCount: Int = 0,
    val completedEventsCount: Int = 0,
    val momentumContribution: Float = 0f
)
