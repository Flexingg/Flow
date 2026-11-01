package com.flow.todo.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.flow.todo.data.local.FlowDao
import com.flow.todo.data.local.toDomain
import com.flow.todo.data.local.toEntity
import com.flow.todo.domain.model.AccessibilityPreferences
import com.flow.todo.domain.model.FlowGoal
import com.flow.todo.domain.model.Project
import com.flow.todo.domain.model.TaskItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map

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
        val stopsRaw = prefs.getString(KEY_MOMENTUM_STOP_HEXES, null)
        val loadedStops = stopsRaw
            ?.split(",")
            ?.mapNotNull { it.trim().toLongOrNull(16) }
            ?.takeIf { it.size == 5 }
            ?: com.flow.todo.domain.model.DEFAULT_MOMENTUM_STOPS_HEX

        com.flow.todo.ui.theme.MomentumSpectrum.updateStopHexes(loadedStops)

        return AccessibilityPreferences(
            reduceTransparency = prefs.getBoolean(KEY_REDUCE_TRANSPARENCY, false),
            reduceMotion = prefs.getBoolean(KEY_REDUCE_MOTION, false),
            increasedContrast = prefs.getBoolean(KEY_INCREASED_CONTRAST, false),
            darkThemeOverride = darkThemeOverride,
            useDynamicColor = prefs.getBoolean(KEY_USE_DYNAMIC_COLOR, true),
            useMomentumColorSpectrum = prefs.getBoolean(KEY_USE_MOMENTUM_SPECTRUM, true),
            syncSystemColorsWithShizuku = prefs.getBoolean(KEY_SYNC_SHIZUKU_COLORS, false),
            momentumStopHexes = loadedStops,
            enableAppUsageShield = prefs.getBoolean(KEY_ENABLE_APP_USAGE_SHIELD, true)
        )
    }

    fun observeTasks(): Flow<List<TaskItem>> = dao.observeTasks().map { entities ->
        entities.map { it.toDomain() }
    }

    fun observeProjects(): Flow<List<Project>> = dao.observeProjects().map { entities ->
        entities.map { it.toDomain() }
    }

    fun observeGoals(): Flow<List<FlowGoal>> = dao.observeGoals().map { entities ->
        entities.map { it.toDomain() }
    }

    fun observeDailyHistory(): Flow<List<com.flow.todo.domain.model.DailyCompletionRecord>> =
        dao.observeDailyHistory().map { entities ->
            entities.map { it.toDomain() }
        }

    suspend fun getAllTasks(): List<TaskItem> = dao.getAllTasks().map { it.toDomain() }

    suspend fun getAllProjects(): List<Project> = dao.getAllProjects().map { it.toDomain() }

    suspend fun getAllGoals(): List<FlowGoal> = dao.getAllGoals().map { it.toDomain() }

    suspend fun getAllDailyHistory(): List<com.flow.todo.domain.model.DailyCompletionRecord> =
        dao.getAllDailyHistory().map { it.toDomain() }

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
        dao.deleteGoalsForProject(projectId)
        dao.deleteProject(projectId)
    }

    suspend fun upsertGoal(goal: FlowGoal) {
        dao.upsertGoal(goal.toEntity())
    }

    suspend fun upsertGoals(goals: List<FlowGoal>) {
        dao.upsertGoals(goals.map { it.toEntity() })
    }

    suspend fun deleteGoal(goalId: String) {
        dao.deleteGoal(goalId)
    }

    suspend fun upsertDailyHistory(record: com.flow.todo.domain.model.DailyCompletionRecord) {
        dao.upsertDailyHistory(record.toEntity())
    }

    suspend fun upsertDailyHistoryList(records: List<com.flow.todo.domain.model.DailyCompletionRecord>) {
        dao.upsertDailyHistoryList(records.map { it.toEntity() })
    }

    suspend fun updateAccessibilityPreferences(transform: (AccessibilityPreferences) -> AccessibilityPreferences) {
        val updated = transform(_accessibilityPreferences.value)
        com.flow.todo.ui.theme.MomentumSpectrum.updateStopHexes(updated.momentumStopHexes)
        _accessibilityPreferences.value = updated
        sharedPreferences?.edit()?.apply {
            putBoolean(KEY_REDUCE_TRANSPARENCY, updated.reduceTransparency)
            putBoolean(KEY_REDUCE_MOTION, updated.reduceMotion)
            putBoolean(KEY_INCREASED_CONTRAST, updated.increasedContrast)
            putBoolean(KEY_USE_DYNAMIC_COLOR, updated.useDynamicColor)
            putBoolean(KEY_USE_MOMENTUM_SPECTRUM, updated.useMomentumColorSpectrum)
            putBoolean(KEY_SYNC_SHIZUKU_COLORS, updated.syncSystemColorsWithShizuku)
            putBoolean(KEY_ENABLE_APP_USAGE_SHIELD, updated.enableAppUsageShield)
            putString(
                KEY_MOMENTUM_STOP_HEXES,
                updated.momentumStopHexes.joinToString(",") { java.lang.Long.toHexString(it) }
            )
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
        private const val KEY_USE_MOMENTUM_SPECTRUM = "use_momentum_spectrum"
        private const val KEY_SYNC_SHIZUKU_COLORS = "sync_shizuku_colors"
        private const val KEY_MOMENTUM_STOP_HEXES = "momentum_stop_hexes"
        private const val KEY_ENABLE_APP_USAGE_SHIELD = "enable_app_usage_shield"
    }
}
