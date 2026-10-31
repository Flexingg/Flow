package com.flow.todo.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.flow.todo.data.local.FlowDao
import com.flow.todo.data.local.toDomain
import com.flow.todo.data.local.toEntity
import com.flow.todo.domain.model.AccessibilityPreferences
import com.flow.todo.domain.model.GtdBucket
import com.flow.todo.domain.model.Project
import com.flow.todo.domain.model.TaskItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

class FlowRepository(
    private val dao: FlowDao,
    context: Context? = null
) {
    private val sharedPreferences: SharedPreferences? = context?.applicationContext?.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    private val _accessibilityPreferences = MutableStateFlow(loadInitialPreferences())
    val accessibilityPreferences: StateFlow<AccessibilityPreferences> = _accessibilityPreferences.asStateFlow()

    private fun loadInitialPreferences(): AccessibilityPreferences {
        val prefs = sharedPreferences ?: return AccessibilityPreferences()
        val darkThemeString = prefs.getString(KEY_DARK_THEME_OVERRIDE, "SYSTEM")
        val darkThemeOverride = when (darkThemeString) {
            "DARK" -> true
            "LIGHT" -> false
            else -> null
        }
        return AccessibilityPreferences(
            reduceTransparency = prefs.getBoolean(KEY_REDUCE_TRANSPARENCY, false),
            reduceMotion = prefs.getBoolean(KEY_REDUCE_MOTION, false),
            increasedContrast = prefs.getBoolean(KEY_INCREASED_CONTRAST, false),
            darkThemeOverride = darkThemeOverride
        )
    }

    fun observeTasks(): Flow<List<TaskItem>> = dao.observeTasks().map { entities ->
        entities.map { it.toDomain() }
    }

    fun observeProjects(): Flow<List<Project>> = dao.observeProjects().map { entities ->
        entities.map { it.toDomain() }
    }

    suspend fun upsertTask(task: TaskItem) {
        dao.upsertTask(task.toEntity())
    }

    suspend fun upsertTasks(tasks: List<TaskItem>) {
        dao.upsertTasks(tasks.map { it.toEntity() })
    }

    suspend fun deleteTask(taskId: String) {
        dao.deleteTask(taskId)
    }

    suspend fun upsertProject(project: Project) {
        dao.upsertProject(project.toEntity())
    }

    suspend fun upsertProjects(projects: List<Project>) {
        dao.upsertProjects(projects.map { it.toEntity() })
    }

    suspend fun deleteProject(projectId: String) {
        dao.clearProjectFromTasks(projectId)
        dao.deleteProject(projectId)
    }

    suspend fun updateAccessibilityPreferences(transform: (AccessibilityPreferences) -> AccessibilityPreferences) {
        val updated = transform(_accessibilityPreferences.value)
        _accessibilityPreferences.value = updated
        sharedPreferences?.edit()?.apply {
            putBoolean(KEY_REDUCE_TRANSPARENCY, updated.reduceTransparency)
            putBoolean(KEY_REDUCE_MOTION, updated.reduceMotion)
            putBoolean(KEY_INCREASED_CONTRAST, updated.increasedContrast)
            putString(
                KEY_DARK_THEME_OVERRIDE,
                when (updated.darkThemeOverride) {
                    true -> "DARK"
                    false -> "LIGHT"
                    null -> "SYSTEM"
                }
            )
            apply()
        }
    }

    suspend fun seedInitialDataIfNeeded() {
        if (dao.getProjectCount() > 0) return

        val now = System.currentTimeMillis()
        val todayEpochDay = LocalDate.now().toEpochDay()

        val seedProjects = listOf(
            Project(
                id = "proj_work",
                name = "Product Launch",
                colorHex = 0xFF2F6BFF,
                createdAt = now - 86_400_000L * 3
            ),
            Project(
                id = "proj_personal",
                name = "Life & Wellness",
                colorHex = 0xFF22B35E,
                createdAt = now - 86_400_000L * 2
            ),
            Project(
                id = "proj_ideas",
                name = "Creative Studio",
                colorHex = 0xFFFF9F0A,
                createdAt = now - 86_400_000L
            )
        )

        val seedTasks = listOf(
            TaskItem(
                id = "task_seed_1",
                title = "Review quarterly user feedback summary",
                notes = "Identify top 3 friction points and synthesize recurring feature requests",
                bucket = GtdBucket.INBOX,
                dueDateEpochDay = null,
                projectId = "proj_work",
                isFlagged = false,
                isCompleted = false,
                createdAt = now - 3_600_000L * 5,
                completedAt = null
            ),
            TaskItem(
                id = "task_seed_2",
                title = "Buy ergonomic desk setup essentials",
                notes = "Look into curved monitor arm and supportive wrist cushion",
                bucket = GtdBucket.INBOX,
                dueDateEpochDay = null,
                projectId = "proj_personal",
                isFlagged = false,
                isCompleted = false,
                createdAt = now - 3_600_000L * 4,
                completedAt = null
            ),
            TaskItem(
                id = "task_seed_3",
                title = "Finalize onboarding flow & microcopy",
                notes = "Polish welcome steps, keep headings punchy, test tone of voice",
                bucket = GtdBucket.NEXT_ACTION,
                dueDateEpochDay = null,
                projectId = "proj_work",
                isFlagged = true,
                isCompleted = false,
                createdAt = now - 3_600_000L * 3,
                completedAt = null
            ),
            TaskItem(
                id = "task_seed_4",
                title = "Schedule annual wellness health checkup",
                notes = "Contact clinic downtown and confirm insurance network coverage",
                bucket = GtdBucket.NEXT_ACTION,
                dueDateEpochDay = null,
                projectId = "proj_personal",
                isFlagged = false,
                isCompleted = false,
                createdAt = now - 3_600_000L * 2,
                completedAt = null
            ),
            TaskItem(
                id = "task_seed_5",
                title = "Roadmap alignment & sprint planning",
                notes = "Walk through core release milestones with product and design leads",
                bucket = GtdBucket.SCHEDULED,
                dueDateEpochDay = todayEpochDay,
                projectId = "proj_work",
                isFlagged = true,
                isCompleted = false,
                createdAt = now - 3_600_000L,
                completedAt = null
            ),
            TaskItem(
                id = "task_seed_6",
                title = "Experiment with generative canvas interactions",
                notes = "Explore fluid spring physics and particle gestures for creative studio",
                bucket = GtdBucket.SOMEDAY,
                dueDateEpochDay = null,
                projectId = "proj_ideas",
                isFlagged = false,
                isCompleted = false,
                createdAt = now,
                completedAt = null
            )
        )

        dao.upsertProjects(seedProjects.map { it.toEntity() })
        dao.upsertTasks(seedTasks.map { it.toEntity() })
    }

    companion object {
        private const val PREFS_NAME = "flow_accessibility_prefs"
        private const val KEY_REDUCE_TRANSPARENCY = "reduce_transparency"
        private const val KEY_REDUCE_MOTION = "reduce_motion"
        private const val KEY_INCREASED_CONTRAST = "increased_contrast"
        private const val KEY_DARK_THEME_OVERRIDE = "dark_theme_override"
    }
}
