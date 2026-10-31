package com.flow.todo.domain.model

enum class GtdBucket(val label: String, val description: String) {
    INBOX("Inbox", "Unclarified thoughts and quick captures"),
    NEXT_ACTION("Next Action", "Ready to do as soon as possible"),
    SCHEDULED("Scheduled", "Assigned to a specific date"),
    SOMEDAY("Someday / Maybe", "Ideas and tasks for the future")
}

enum class FlowTab(val label: String) {
    INBOX("Inbox"),
    TODAY_NEXT("Today & Next"),
    PROJECTS("Projects")
}

data class Project(
    val id: String,
    val name: String,
    val colorHex: Long,
    val createdAt: Long = System.currentTimeMillis()
)

data class TaskItem(
    val id: String,
    val title: String,
    val notes: String = "",
    val bucket: GtdBucket = GtdBucket.INBOX,
    val dueDateEpochDay: Long? = null,
    val projectId: String? = null,
    val isFlagged: Boolean = false,
    val isCompleted: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null
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
    val useDynamicColor: Boolean = true
)
