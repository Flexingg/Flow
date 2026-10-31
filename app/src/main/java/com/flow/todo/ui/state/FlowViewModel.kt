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
import com.flow.todo.domain.model.RecurrenceRule
import com.flow.todo.domain.model.TaskItem
import com.flow.todo.domain.model.UpcomingDayGroup
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
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import java.util.UUID

data class FlowUiState(
    val selectedTab: FlowTab = FlowTab.INBOX,
    val allTasks: List<TaskItem> = emptyList(),
    val projects: List<Project> = emptyList(),
    val inboxTasks: List<TaskItem> = emptyList(),
    val todayTasks: List<TaskItem> = emptyList(),
    val nextActionTasks: List<TaskItem> = emptyList(),
    val upcomingWeekDays: List<UpcomingDayGroup> = emptyList(),
    val laterScheduledTasks: List<TaskItem> = emptyList(),
    val upcomingWeekTotalCount: Int = 0,
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
    private val dateFormatter = DateTimeFormatter.ofPattern("MMM d", Locale.getDefault())

    val uiState: StateFlow<FlowUiState> = combine(
        _localFlags,
        repository.observeTasks(),
        repository.observeProjects(),
        repository.accessibilityPreferences
    ) { flags, tasks, projects, accessibility ->
        val today = LocalDate.now()
        val todayEpochDay = today.toEpochDay()
        val nextWeekEndEpochDay = today.plusDays(7).toEpochDay()

        val inboxTasks = tasks.filter { it.bucket == GtdBucket.INBOX }

        val todayTasks = tasks.filter { task ->
            val isDueTodayOrPast = task.dueDateEpochDay != null && task.dueDateEpochDay <= todayEpochDay
            val isRecurringToday = !task.isCompleted &&
                task.recurrenceRule?.isRecurring == true &&
                task.recurrenceRule.occursOnDate(today, task.dueDateEpochDay)
            val isFlaggedActive = task.isFlagged && !task.isCompleted
            val isFlaggedCompletedToday = task.isFlagged && task.isCompleted && isCompletedToday(task.completedAt, todayEpochDay)
            isDueTodayOrPast || isRecurringToday || isFlaggedActive || isFlaggedCompletedToday
        }

        val todayTaskIds = todayTasks.map { it.id }.toSet()
        val nextActionTasks = tasks.filter { it.bucket == GtdBucket.NEXT_ACTION && it.id !in todayTaskIds }
        val somedayTasks = tasks.filter { it.bucket == GtdBucket.SOMEDAY }

        // Build 7-day Upcoming Week Queue (Tomorrow through Today + 7 days)
        val activeTasks = tasks.filter { !it.isCompleted }
        val upcomingWeekDays = (1L..7L).map { offset ->
            val targetDate = today.plusDays(offset)
            val targetEpoch = targetDate.toEpochDay()
            val dayName = targetDate.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())
            val dayTitle = when (offset) {
                1L -> "Tomorrow • $dayName"
                else -> dayName
            }
            val dateSubtitle = targetDate.format(dateFormatter)

            // Match tasks explicitly due on targetEpoch OR recurring tasks whose rule occurs on targetDate
            val dayTasks = activeTasks.filter { task ->
                val isExplicitlyDue = task.dueDateEpochDay == targetEpoch
                val isRecurringMatch = task.recurrenceRule?.isRecurring == true &&
                    task.recurrenceRule.occursOnDate(targetDate, task.dueDateEpochDay)
                isExplicitlyDue || isRecurringMatch
            }.map { task ->
                if (task.dueDateEpochDay != targetEpoch && task.recurrenceRule?.isRecurring == true) {
                    // Projected occurrence for this day in the queue
                    task.copy(dueDateEpochDay = targetEpoch)
                } else {
                    task
                }
            }

            UpcomingDayGroup(
                epochDay = targetEpoch,
                dayTitle = dayTitle,
                dateSubtitle = dateSubtitle,
                isTomorrow = offset == 1L,
                tasks = dayTasks
            )
        }

        val laterScheduledTasks = activeTasks.filter { task ->
            task.dueDateEpochDay != null && task.dueDateEpochDay > nextWeekEndEpochDay
        }.sortedBy { it.dueDateEpochDay }

        val upcomingWeekTotalCount = upcomingWeekDays.sumOf { it.tasks.size }

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
            upcomingWeekDays = upcomingWeekDays,
            laterScheduledTasks = laterScheduledTasks,
            upcomingWeekTotalCount = upcomingWeekTotalCount,
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
        isFlagged: Boolean = false,
        recurrenceRule: RecurrenceRule? = null
    ) {
        if (title.isBlank()) return
        val hasRecurrence = recurrenceRule != null && recurrenceRule.isRecurring
        val effectiveDueDate = when {
            dueDateEpochDay != null -> dueDateEpochDay
            hasRecurrence -> recurrenceRule!!.firstMatchingOrAfter(LocalDate.now()).toEpochDay()
            bucket == GtdBucket.SCHEDULED -> LocalDate.now().toEpochDay()
            else -> null
        }
        val effectiveBucket = if (hasRecurrence && bucket == GtdBucket.INBOX) {
            GtdBucket.SCHEDULED
        } else {
            bucket
        }
        val newTask = TaskItem(
            id = UUID.randomUUID().toString(),
            title = title.trim(),
            notes = notes.trim(),
            bucket = effectiveBucket,
            dueDateEpochDay = effectiveDueDate,
            projectId = projectId,
            isFlagged = isFlagged,
            isCompleted = false,
            recurrenceRule = if (hasRecurrence) recurrenceRule else null,
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
        val nowMillis = System.currentTimeMillis()
        val rule = task.recurrenceRule

        viewModelScope.launch {
            if (nowCompleted && rule != null && rule.isRecurring) {
                // Complete current occurrence and spawn the next recurring occurrence automatically
                val completedOccurrence = task.copy(
                    isCompleted = true,
                    recurrenceRule = null, // Archive completed single instance without duplicating future projections
                    completedAt = nowMillis
                )
                val anchorDay = task.dueDateEpochDay ?: LocalDate.now().toEpochDay()
                val nextDueEpochDay = rule.nextOccurrenceAfter(anchorDay)
                val nextOccurrence = task.copy(
                    id = UUID.randomUUID().toString(),
                    bucket = GtdBucket.SCHEDULED,
                    dueDateEpochDay = nextDueEpochDay,
                    isCompleted = false,
                    recurrenceRule = rule,
                    createdAt = nowMillis,
                    completedAt = null
                )
                repository.upsertTasks(listOf(completedOccurrence, nextOccurrence))
            } else {
                val updatedTask = task.copy(
                    isCompleted = nowCompleted,
                    completedAt = if (nowCompleted) nowMillis else null
                )
                repository.upsertTask(updatedTask)
            }
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

    fun updateTaskRecurrence(taskId: String, recurrenceRule: RecurrenceRule?) {
        val task = uiState.value.allTasks.find { it.id == taskId } ?: return
        val hasRecurrence = recurrenceRule != null && recurrenceRule.isRecurring
        val nextDue = if (hasRecurrence && task.dueDateEpochDay == null) {
            recurrenceRule!!.firstMatchingOrAfter(LocalDate.now()).toEpochDay()
        } else {
            task.dueDateEpochDay
        }
        val updatedBucket = if (hasRecurrence && task.bucket == GtdBucket.INBOX) {
            GtdBucket.SCHEDULED
        } else {
            task.bucket
        }
        val updatedTask = task.copy(
            recurrenceRule = if (hasRecurrence) recurrenceRule else null,
            dueDateEpochDay = nextDue,
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
