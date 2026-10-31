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
    TODAY_NEXT("Today & Queue"),
    PROJECTS("Projects")
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
                // First check if this month's Nth weekday is still strictly in the future relative to baseDate
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
    val presetTargets: List<Double>
) {
    STEPS("Steps", "steps", 10000.0, listOf(5000.0, 8000.0, 10000.0, 12000.0, 15000.0)),
    ACTIVE_CALORIES("Active Calories", "kcal", 500.0, listOf(300.0, 450.0, 600.0, 800.0)),
    EXERCISE_MINUTES("Exercise Time", "min", 30.0, listOf(20.0, 30.0, 45.0, 60.0)),
    SLEEP_HOURS("Sleep Duration", "hrs", 7.5, listOf(6.5, 7.0, 7.5, 8.0, 8.5)),
    DISTANCE_KM("Distance", "km", 5.0, listOf(3.0, 5.0, 8.0, 10.0)),
    HYDRATION_LITERS("Hydration", "L", 2.5, listOf(1.5, 2.0, 2.5, 3.0, 3.5)),
    FLOORS_CLIMBED("Floors Climbed", "floors", 10.0, listOf(5.0, 10.0, 15.0, 20.0)),
    WEIGHT_KG("Body Weight", "kg", 70.0, listOf(60.0, 65.0, 70.0, 75.0, 80.0)),
    RESTING_HEART_RATE("Avg Heart Rate", "bpm", 65.0, listOf(55.0, 60.0, 65.0, 70.0));

    fun formatValue(value: Double): String {
        return when (this) {
            STEPS, ACTIVE_CALORIES, EXERCISE_MINUTES, FLOORS_CLIMBED, RESTING_HEART_RATE ->
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

enum class ShizukuStatus(val label: String, val description: String) {
    READY_SECURE_SETTINGS("Active (Direct)", "WRITE_SECURE_SETTINGS granted • Instant System UI morphing"),
    READY_SHIZUKU("Active (Shizuku)", "Shizuku authorized • System UI colors morph with daily progress"),
    PERMISSION_REQUIRED("Permission Needed", "Tap to authorize Flow in Shizuku"),
    NOT_RUNNING("Shizuku Not Running", "Open & start Shizuku to sync Android System UI colors"),
    NOT_INSTALLED("Shizuku Not Installed", "Install Shizuku to unlock OS-wide Material You color morphing");

    val isReady: Boolean
        get() = this == READY_SECURE_SETTINGS || this == READY_SHIZUKU
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
    val createdAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null
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

data class AccessibilityPreferences(
    val reduceTransparency: Boolean = false,
    val reduceMotion: Boolean = false,
    val increasedContrast: Boolean = false,
    val darkThemeOverride: Boolean? = null, // null = follow system, true = dark, false = light
    val useDynamicColor: Boolean = true,
    val useMomentumColorSpectrum: Boolean = true, // Red -> Purple -> Blue -> Yellow -> Green based on daily progress
    val syncSystemColorsWithShizuku: Boolean = false // Sync Android System UI Material You seed color via Shizuku
)
