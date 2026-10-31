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
            darkThemeOverride = darkThemeOverride,
            useDynamicColor = prefs.getBoolean(KEY_USE_DYNAMIC_COLOR, true)
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
            putBoolean(KEY_USE_DYNAMIC_COLOR, updated.useDynamicColor)
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

    companion object {
        private const val PREFS_NAME = "flow_accessibility_prefs"
        private const val KEY_REDUCE_TRANSPARENCY = "reduce_transparency"
        private const val KEY_REDUCE_MOTION = "reduce_motion"
        private const val KEY_INCREASED_CONTRAST = "increased_contrast"
        private const val KEY_DARK_THEME_OVERRIDE = "dark_theme_override"
        private const val KEY_USE_DYNAMIC_COLOR = "use_dynamic_color"
    }
}
