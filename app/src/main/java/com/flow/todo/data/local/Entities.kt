package com.flow.todo.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.flow.todo.domain.model.AppUsageMetricType
import com.flow.todo.domain.model.CalendarEventItem
import com.flow.todo.domain.model.DailyCompletionRecord
import com.flow.todo.domain.model.FlowGoal
import com.flow.todo.domain.model.GoalCategory
import com.flow.todo.domain.model.GoalComparison
import com.flow.todo.domain.model.GoalPeriod
import com.flow.todo.domain.model.HealthGoalSpec
import com.flow.todo.domain.model.HealthMetricType
import com.flow.todo.domain.model.Project
import com.flow.todo.domain.model.RecurrenceRule
import com.flow.todo.domain.model.SubtaskItem
import com.flow.todo.domain.model.TaskItem

@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey val id: String,
    val name: String,
    val colorHex: Long,
    val createdAt: Long
)

@Entity(
    tableName = "tasks",
    indices = [
        Index(value = ["bucketName", "isCompleted"]),
        Index(value = ["dueDateEpochDay"]),
        Index(value = ["projectId"]),
        Index(value = ["completedAt"]),
        Index(value = ["contextTag"])
    ]
)
data class TaskEntity(
    @PrimaryKey val id: String,
    val title: String,
    val notes: String,
    val bucketName: String,
    val dueDateEpochDay: Long?,
    val projectId: String?,
    val isFlagged: Boolean,
    val isCompleted: Boolean,
    val recurrenceRuleRaw: String? = null,
    val healthGoalRaw: String? = null,
    val subtasksRaw: String? = null,
    val contextTag: String? = null,
    val createdAt: Long,
    val completedAt: Long?,
    // Added in DB v10 (MIGRATION_9_10): time blocks + Eisenhower urgency.
    val startMinute: Int? = null,
    val durationMinutes: Int? = null,
    /** null = automatic, 0 = not urgent, 1 = urgent */
    val urgency: Int? = null
)

@Entity(
    tableName = "goals",
    indices = [
        Index(value = ["categoryName"]),
        Index(value = ["periodName"])
    ]
)
data class GoalEntity(
    @PrimaryKey val id: String,
    val title: String,
    val categoryName: String,
    val comparisonName: String,
    val periodName: String,
    val targetValue: Double,
    val projectId: String? = null,
    val healthMetricName: String? = null,
    val targetPackageName: String? = null,
    val targetAppName: String? = null,
    val appUsageMetricName: String? = null,
    val createdAt: Long
)

@Entity(tableName = "daily_history")
data class DailyHistoryEntity(
    @PrimaryKey val epochDay: Long,
    val tasksCompletedCount: Int,
    val goalsCompletedCount: Int,
    val totalGoalsCount: Int,
    val momentumScore: Float,
    val updatedAt: Long
)

@Entity(
    tableName = "calendar_events",
    indices = [
        Index(value = ["startEpochMillis"]),
        Index(value = ["isCompleted"]),
        Index(value = ["source"])
    ]
)
data class CalendarEventEntity(
    @PrimaryKey val id: String,
    val calendarId: Long,
    val calendarDisplayName: String,
    val calendarColorHex: Long,
    val accountName: String,
    val title: String,
    val description: String,
    val location: String,
    val startEpochMillis: Long,
    val endEpochMillis: Long,
    val isAllDay: Boolean,
    val isCompleted: Boolean,
    val completedAt: Long?,
    val source: String
)

fun ProjectEntity.toDomain(): Project = Project(
    id = id,
    name = name,
    colorHex = colorHex,
    createdAt = createdAt
)

fun Project.toEntity(): ProjectEntity = ProjectEntity(
    id = id,
    name = name,
    colorHex = colorHex,
    createdAt = createdAt
)

fun TaskEntity.toDomain(): TaskItem = TaskItem(
    id = id,
    title = title,
    notes = notes,
    dueDateEpochDay = dueDateEpochDay,
    isFlagged = isFlagged,
    isCompleted = isCompleted,
    recurrenceRule = RecurrenceRule.fromStorageString(recurrenceRuleRaw),
    healthGoal = HealthGoalSpec.fromStorageString(healthGoalRaw),
    subtasks = SubtaskItem.listFromStorageString(subtasksRaw),
    createdAt = createdAt,
    completedAt = completedAt,
    startMinute = startMinute,
    durationMinutes = durationMinutes,
    urgentOverride = urgency?.let { it == 1 }
)

/**
 * bucketName / projectId / contextTag are legacy GTD columns. They stay in the schema (the main
 * database uses destructive migration, so dropping columns would wipe data) but are no longer used.
 */
fun TaskItem.toEntity(): TaskEntity = TaskEntity(
    id = id,
    title = title,
    notes = notes,
    bucketName = LEGACY_BUCKET,
    dueDateEpochDay = dueDateEpochDay,
    projectId = null,
    isFlagged = isFlagged,
    isCompleted = isCompleted,
    recurrenceRuleRaw = recurrenceRule?.toStorageString(),
    healthGoalRaw = healthGoal?.toStorageString(),
    subtasksRaw = SubtaskItem.listToStorageString(subtasks),
    contextTag = null,
    createdAt = createdAt,
    completedAt = completedAt,
    startMinute = startMinute,
    durationMinutes = durationMinutes,
    urgency = urgentOverride?.let { if (it) 1 else 0 }
)

/** Written to the unused legacy bucket column. */
const val LEGACY_BUCKET = "TASK"

fun GoalEntity.toDomain(): FlowGoal = FlowGoal(
    id = id,
    title = title,
    category = runCatching { GoalCategory.valueOf(categoryName) }.getOrDefault(GoalCategory.TASK_COMPLETION),
    comparison = runCatching { GoalComparison.valueOf(comparisonName) }.getOrDefault(GoalComparison.AT_LEAST),
    period = runCatching { GoalPeriod.valueOf(periodName) }.getOrDefault(GoalPeriod.DAILY),
    targetValue = targetValue,
    projectId = projectId,
    healthMetric = healthMetricName?.let { runCatching { HealthMetricType.valueOf(it) }.getOrNull() },
    targetPackageName = targetPackageName,
    targetAppName = targetAppName,
    appUsageMetric = appUsageMetricName?.let { runCatching { AppUsageMetricType.valueOf(it) }.getOrNull() },
    createdAt = createdAt
)

fun FlowGoal.toEntity(): GoalEntity = GoalEntity(
    id = id,
    title = title,
    categoryName = category.name,
    comparisonName = comparison.name,
    periodName = period.name,
    targetValue = targetValue,
    projectId = projectId,
    healthMetricName = healthMetric?.name,
    targetPackageName = targetPackageName,
    targetAppName = targetAppName,
    appUsageMetricName = appUsageMetric?.name,
    createdAt = createdAt
)

fun DailyHistoryEntity.toDomain(): DailyCompletionRecord = DailyCompletionRecord(
    epochDay = epochDay,
    tasksCompletedCount = tasksCompletedCount,
    goalsCompletedCount = goalsCompletedCount,
    totalGoalsCount = totalGoalsCount,
    momentumScore = momentumScore,
    updatedAt = updatedAt
)

fun DailyCompletionRecord.toEntity(): DailyHistoryEntity = DailyHistoryEntity(
    epochDay = epochDay,
    tasksCompletedCount = tasksCompletedCount,
    goalsCompletedCount = goalsCompletedCount,
    totalGoalsCount = totalGoalsCount,
    momentumScore = momentumScore,
    updatedAt = updatedAt
)

fun CalendarEventEntity.toDomain(): CalendarEventItem = CalendarEventItem(
    id = id,
    calendarId = calendarId,
    calendarDisplayName = calendarDisplayName,
    calendarColorHex = calendarColorHex,
    accountName = accountName,
    title = title,
    description = description,
    location = location,
    startEpochMillis = startEpochMillis,
    endEpochMillis = endEpochMillis,
    isAllDay = isAllDay,
    isCompleted = isCompleted,
    completedAt = completedAt,
    source = source
)

fun CalendarEventItem.toEntity(): CalendarEventEntity = CalendarEventEntity(
    id = id,
    calendarId = calendarId,
    calendarDisplayName = calendarDisplayName,
    calendarColorHex = calendarColorHex,
    accountName = accountName,
    title = title,
    description = description,
    location = location,
    startEpochMillis = startEpochMillis,
    endEpochMillis = endEpochMillis,
    isAllDay = isAllDay,
    isCompleted = isCompleted,
    completedAt = completedAt,
    source = source
)
