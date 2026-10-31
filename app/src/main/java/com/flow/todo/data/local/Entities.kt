package com.flow.todo.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.flow.todo.domain.model.GtdBucket
import com.flow.todo.domain.model.HealthGoalSpec
import com.flow.todo.domain.model.Project
import com.flow.todo.domain.model.RecurrenceRule
import com.flow.todo.domain.model.TaskItem

@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey val id: String,
    val name: String,
    val colorHex: Long,
    val createdAt: Long
)

@Entity(tableName = "tasks")
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
    val createdAt: Long,
    val completedAt: Long?
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
    bucket = runCatching { GtdBucket.valueOf(bucketName) }.getOrDefault(GtdBucket.INBOX),
    dueDateEpochDay = dueDateEpochDay,
    projectId = projectId,
    isFlagged = isFlagged,
    isCompleted = isCompleted,
    recurrenceRule = RecurrenceRule.fromStorageString(recurrenceRuleRaw),
    healthGoal = HealthGoalSpec.fromStorageString(healthGoalRaw),
    createdAt = createdAt,
    completedAt = completedAt
)

fun TaskItem.toEntity(): TaskEntity = TaskEntity(
    id = id,
    title = title,
    notes = notes,
    bucketName = bucket.name,
    dueDateEpochDay = dueDateEpochDay,
    projectId = projectId,
    isFlagged = isFlagged,
    isCompleted = isCompleted,
    recurrenceRuleRaw = recurrenceRule?.toStorageString(),
    healthGoalRaw = healthGoal?.toStorageString(),
    createdAt = createdAt,
    completedAt = completedAt
)
