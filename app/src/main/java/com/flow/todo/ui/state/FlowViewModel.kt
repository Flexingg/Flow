package com.flow.todo.ui.state

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.flow.todo.data.repository.FlowRepository
import com.flow.todo.domain.model.AccessibilityPreferences
import com.flow.todo.domain.model.FlowTab
import com.flow.todo.domain.model.GtdBucket
import com.flow.todo.domain.model.Project
import com.flow.todo.domain.model.ProjectWithStats
import com.flow.todo.domain.model.TaskItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

data class FlowUiState(
    val selectedTab: FlowTab = FlowTab.INBOX,
    val allTasks: List<TaskItem> = emptyList(),
    val projects: List<Project> = emptyList(),
    val inboxTasks: List<TaskItem> = emptyList(),
    val todayTasks: List<TaskItem> = emptyList(),
    val nextActionTasks: List<TaskItem> = emptyList(),
    val somedayTasks: List<TaskItem> = emptyList(),
    val projectsWithStats: List<ProjectWithStats> = emptyList(),
    val completedTodayCount: Int = 0,
    val totalTodayTargetCount: Int = 0,
    val isQuickCaptureOpen: Boolean = false,
    val isSettingsOpen: Boolean = false,
    val accessibility: AccessibilityPreferences = AccessibilityPreferences()
)

class FlowViewModel(private val repository: FlowRepository) : ViewModel() {

    private data class LocalUiFlags(
        val selectedTab: FlowTab = FlowTab.INBOX,
        val isQuickCaptureOpen: Boolean = false,
        val isSettingsOpen: Boolean = false
    )

    private val _localFlags = MutableStateFlow(LocalUiFlags())

    val uiState: StateFlow<FlowUiState> = combine(
        _localFlags,
        repository.observeTasks(),
        repository.observeProjects(),
        repository.accessibilityPreferences
    ) { flags, tasks, projects, accessibility ->
        val todayEpochDay = LocalDate.now().toEpochDay()

        val inboxTasks = tasks.filter { it.bucket == GtdBucket.INBOX }

        val todayTasks = tasks.filter { task ->
            val isDueTodayOrPast = task.dueDateEpochDay != null && task.dueDateEpochDay <= todayEpochDay
            val isFlaggedActive = task.isFlagged && !task.isCompleted
            val isFlaggedCompletedToday = task.isFlagged && task.isCompleted && isCompletedToday(task.completedAt, todayEpochDay)
            isDueTodayOrPast || isFlaggedActive || isFlaggedCompletedToday
        }

        val todayTaskIds = todayTasks.map { it.id }.toSet()
        val nextActionTasks = tasks.filter { it.bucket == GtdBucket.NEXT_ACTION && it.id !in todayTaskIds }
        val somedayTasks = tasks.filter { it.bucket == GtdBucket.SOMEDAY }

        val projectsWithStats = projects.map { project ->
            val projectTasks = tasks.filter { it.projectId == project.id }
            val completedCount = projectTasks.count { it.isCompleted }
            ProjectWithStats(
                project = project,
                tasks = projectTasks,
                completedCount = completedCount,
                totalCount = projectTasks.size
            )
        }

        val completedTodayCount = todayTasks.count { it.isCompleted }
        val totalTodayTargetCount = todayTasks.size

        FlowUiState(
            selectedTab = flags.selectedTab,
            allTasks = tasks,
            projects = projects,
            inboxTasks = inboxTasks,
            todayTasks = todayTasks,
            nextActionTasks = nextActionTasks,
            somedayTasks = somedayTasks,
            projectsWithStats = projectsWithStats,
            completedTodayCount = completedTodayCount,
            totalTodayTargetCount = totalTodayTargetCount,
            isQuickCaptureOpen = flags.isQuickCaptureOpen,
            isSettingsOpen = flags.isSettingsOpen,
            accessibility = accessibility
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = FlowUiState()
    )

    fun selectTab(tab: FlowTab) {
        _localFlags.update { it.copy(selectedTab = tab) }
    }

    fun setQuickCaptureOpen(open: Boolean) {
        _localFlags.update { it.copy(isQuickCaptureOpen = open) }
    }

    fun setSettingsOpen(open: Boolean) {
        _localFlags.update { it.copy(isSettingsOpen = open) }
    }

    fun addTask(
        title: String,
        notes: String = "",
        bucket: GtdBucket = GtdBucket.INBOX,
        dueDateEpochDay: Long? = null,
        projectId: String? = null,
        isFlagged: Boolean = false
    ) {
        if (title.isBlank()) return
        val newTask = TaskItem(
            id = UUID.randomUUID().toString(),
            title = title.trim(),
            notes = notes.trim(),
            bucket = bucket,
            dueDateEpochDay = dueDateEpochDay,
            projectId = projectId,
            isFlagged = isFlagged,
            isCompleted = false,
            createdAt = System.currentTimeMillis(),
            completedAt = null
        )
        viewModelScope.launch {
            repository.upsertTask(newTask)
        }
    }

    fun toggleTaskCompletion(taskId: String) {
        val task = uiState.value.allTasks.find { it.id == taskId } ?: return
        val nowCompleted = !task.isCompleted
        val updatedTask = task.copy(
            isCompleted = nowCompleted,
            completedAt = if (nowCompleted) System.currentTimeMillis() else null
        )
        viewModelScope.launch {
            repository.upsertTask(updatedTask)
        }
    }

    fun toggleTaskFlag(taskId: String) {
        val task = uiState.value.allTasks.find { it.id == taskId } ?: return
        val updatedTask = task.copy(isFlagged = !task.isFlagged)
        viewModelScope.launch {
            repository.upsertTask(updatedTask)
        }
    }

    fun updateTaskBucket(taskId: String, bucket: GtdBucket, dueDateEpochDay: Long? = null) {
        val task = uiState.value.allTasks.find { it.id == taskId } ?: return
        val effectiveDueDate = if (dueDateEpochDay != null) {
            dueDateEpochDay
        } else if (bucket == GtdBucket.SCHEDULED) {
            task.dueDateEpochDay ?: LocalDate.now().toEpochDay()
        } else {
            task.dueDateEpochDay
        }
        val updatedTask = task.copy(
            bucket = bucket,
            dueDateEpochDay = effectiveDueDate
        )
        viewModelScope.launch {
            repository.upsertTask(updatedTask)
        }
    }

    fun assignTaskDate(taskId: String, dueDateEpochDay: Long?) {
        val task = uiState.value.allTasks.find { it.id == taskId } ?: return
        val updatedBucket = if (dueDateEpochDay != null && task.bucket == GtdBucket.INBOX) {
            GtdBucket.SCHEDULED
        } else {
            task.bucket
        }
        val updatedTask = task.copy(
            dueDateEpochDay = dueDateEpochDay,
            bucket = updatedBucket
        )
        viewModelScope.launch {
            repository.upsertTask(updatedTask)
        }
    }

    fun assignTaskProject(taskId: String, projectId: String?) {
        val task = uiState.value.allTasks.find { it.id == taskId } ?: return
        val updatedTask = task.copy(projectId = projectId)
        viewModelScope.launch {
            repository.upsertTask(updatedTask)
        }
    }

    fun deleteTask(taskId: String) {
        viewModelScope.launch {
            repository.deleteTask(taskId)
        }
    }

    fun addProject(name: String, colorHex: Long) {
        if (name.isBlank()) return
        val newProject = Project(
            id = UUID.randomUUID().toString(),
            name = name.trim(),
            colorHex = colorHex,
            createdAt = System.currentTimeMillis()
        )
        viewModelScope.launch {
            repository.upsertProject(newProject)
        }
    }

    fun deleteProject(projectId: String) {
        viewModelScope.launch {
            repository.deleteProject(projectId)
        }
    }

    fun updateAccessibility(transform: (AccessibilityPreferences) -> AccessibilityPreferences) {
        viewModelScope.launch {
            repository.updateAccessibilityPreferences(transform)
        }
    }

    private fun isCompletedToday(completedAt: Long?, todayEpochDay: Long): Boolean {
        if (completedAt == null) return false
        return runCatching {
            Instant.ofEpochMilli(completedAt)
                .atZone(ZoneId.systemDefault())
                .toLocalDate()
                .toEpochDay() == todayEpochDay
        }.getOrDefault(false)
    }

    companion object {
        fun provideFactory(repository: FlowRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    if (modelClass.isAssignableFrom(FlowViewModel::class.java)) {
                        return FlowViewModel(repository) as T
                    }
                    throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
                }
            }
    }
}
