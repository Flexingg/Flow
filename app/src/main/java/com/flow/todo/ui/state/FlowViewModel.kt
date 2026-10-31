package com.flow.todo.ui.state

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.flow.todo.data.health.HealthConnectManager
import com.flow.todo.data.repository.FlowRepository
import com.flow.todo.data.system.ShizukuSystemColorManager
import com.flow.todo.data.usage.AppUsageManager
import com.flow.todo.domain.model.AccessibilityPreferences
import com.flow.todo.domain.model.AppUsageMetricType
import com.flow.todo.domain.model.AppUsageSnapshot
import com.flow.todo.domain.model.EvaluatedGoal
import com.flow.todo.domain.model.FlowGoal
import com.flow.todo.domain.model.FlowTab
import com.flow.todo.domain.model.GoalCategory
import com.flow.todo.domain.model.GoalComparison
import com.flow.todo.domain.model.GoalPeriod
import com.flow.todo.domain.model.GtdBucket
import com.flow.todo.domain.model.HealthDailySnapshot
import com.flow.todo.domain.model.HealthGoalSpec
import com.flow.todo.domain.model.HealthMetricType
import com.flow.todo.domain.model.Project
import com.flow.todo.domain.model.ProjectWithStats
import com.flow.todo.domain.model.RecurrenceRule
import com.flow.todo.domain.model.RecurrenceType
import com.flow.todo.domain.model.ShizukuStatus
import com.flow.todo.domain.model.TaskItem
import com.flow.todo.domain.model.UpcomingDayGroup
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
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
import kotlin.math.roundToLong

val FlowGoal.isEnabled: Boolean
    get() = this.id !in FlowViewModel.globalDisabledGoalIds.value

data class FlowUiState(
    val selectedTab: FlowTab = FlowTab.INBOX,
    val allTasks: List<TaskItem> = emptyList(),
    val projects: List<Project> = emptyList(),
    val goals: List<FlowGoal> = emptyList(),
    val evaluatedGoals: List<EvaluatedGoal> = emptyList(),
    val completedGoalsCount: Int = 0,
    val totalGoalsCount: Int = 0,
    val goalMomentumProgress: Float = 0f,
    val appUsageSnapshot: AppUsageSnapshot = AppUsageSnapshot(),
    val isGoalCreatorOpen: Boolean = false,
    val disabledGoalIds: Set<String> = emptySet(),
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
    val accessibility: AccessibilityPreferences = AccessibilityPreferences(),
    val dailyProgress: Float = 0f,
    val healthSnapshot: HealthDailySnapshot = HealthDailySnapshot(),
    val shizukuStatus: ShizukuStatus = ShizukuStatus.NOT_INSTALLED,
    val lastSyncedSystemColorHex: String? = null
)

class FlowViewModel(
    private val repository: FlowRepository,
    private val healthConnectManager: HealthConnectManager? = null,
    private val shizukuManager: ShizukuSystemColorManager? = null,
    private val appUsageManager: AppUsageManager? = null
) : ViewModel() {

    private data class LocalUiFlags(
        val selectedTab: FlowTab = FlowTab.INBOX,
        val isQuickCaptureOpen: Boolean = false,
        val isSettingsOpen: Boolean = false,
        val isGoalCreatorOpen: Boolean = false,
        val disabledGoalIds: Set<String> = emptySet()
    )

    private data class RepositoryDomainData(
        val tasks: List<TaskItem>,
        val projects: List<Project>,
        val goals: List<FlowGoal>,
        val appUsage: AppUsageSnapshot
    )

    private data class SystemHealthState(
        val healthSnapshot: HealthDailySnapshot = HealthDailySnapshot(),
        val shizukuStatus: ShizukuStatus = ShizukuStatus.NOT_INSTALLED,
        val lastAppliedHex: String? = null
    )

    private val _localFlags = MutableStateFlow(LocalUiFlags())
    private val dateFormatter = DateTimeFormatter.ofPattern("MMM d", Locale.getDefault())

    private val healthSnapshotFlow: Flow<HealthDailySnapshot> =
        healthConnectManager?.snapshot ?: MutableStateFlow(HealthDailySnapshot())

    private val shizukuStatusFlow: Flow<ShizukuStatus> =
        shizukuManager?.status ?: MutableStateFlow(ShizukuStatus.NOT_INSTALLED)

    private val lastAppliedHexFlow: Flow<String?> =
        shizukuManager?.lastAppliedHex ?: MutableStateFlow(null)

    private val appUsageSnapshotFlow: Flow<AppUsageSnapshot> =
        appUsageManager?.snapshot ?: MutableStateFlow(AppUsageSnapshot())

    private val domainDataFlow: Flow<RepositoryDomainData> = combine(
        repository.observeTasks(),
        repository.observeProjects(),
        repository.observeGoals(),
        appUsageSnapshotFlow
    ) { tasks, projects, goals, usage ->
        RepositoryDomainData(
            tasks = tasks,
            projects = projects,
            goals = goals,
            appUsage = usage
        )
    }

    private val systemHealthFlow: Flow<SystemHealthState> = combine(
        healthSnapshotFlow,
        shizukuStatusFlow,
        lastAppliedHexFlow
    ) { health, shizuku, hex ->
        SystemHealthState(
            healthSnapshot = health,
            shizukuStatus = shizuku,
            lastAppliedHex = hex
        )
    }

    val uiState: StateFlow<FlowUiState> = combine(
        _localFlags,
        domainDataFlow,
        repository.accessibilityPreferences,
        systemHealthFlow
    ) { flags, domainData, accessibility, sysHealth ->
        val today = LocalDate.now()
        val todayEpochDay = today.toEpochDay()
        val nextWeekEndEpochDay = today.plusDays(7).toEpochDay()

        val tasks = domainData.tasks
        val projects = domainData.projects
        val goals = domainData.goals
        val usage = domainData.appUsage

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

            val dayTasks = activeTasks.filter { task ->
                val isExplicitlyDue = task.dueDateEpochDay == targetEpoch
                val isRecurringMatch = task.recurrenceRule?.isRecurring == true &&
                    task.recurrenceRule.occursOnDate(targetDate, task.dueDateEpochDay)
                isExplicitlyDue || isRecurringMatch
            }.map { task ->
                if (task.dueDateEpochDay != targetEpoch && task.recurrenceRule?.isRecurring == true) {
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

        // Evaluate first-class Flow goals
        val projectsMap = projects.associateBy { it.id }
        val evaluatedGoals = goals.map { goal ->
            evaluateGoal(
                goal = goal,
                allTasks = tasks,
                projectsMap = projectsMap,
                healthSnapshot = sysHealth.healthSnapshot,
                appUsageSnapshot = usage,
                todayEpochDay = todayEpochDay
            )
        }

        val activeGoals = evaluatedGoals.filter { it.goal.id !in flags.disabledGoalIds }
        val goalMomentumProgress = if (activeGoals.isEmpty()) {
            0f
        } else {
            (activeGoals.sumOf { it.momentumContribution.toDouble() }.toFloat() / activeGoals.size.toFloat()).coerceIn(0f, 1f)
        }
        val completedGoalsCount = activeGoals.count { it.isCompleted }
        val totalGoalsCount = activeGoals.size

        // Fallback task progress for when no goals are configured yet
        val fallbackTaskProgress = if (todayTasks.isEmpty()) {
            0f
        } else {
            val totalScore = todayTasks.sumOf { task ->
                when {
                    task.isCompleted -> 1.0
                    task.healthGoal != null -> {
                        val currentVal = sysHealth.healthSnapshot.valueFor(task.healthGoal.metricType)
                        task.healthGoal.progressFraction(currentVal).toDouble()
                    }
                    else -> 0.0
                }
            }
            (totalScore / todayTasks.size.toDouble()).toFloat().coerceIn(0f, 1f)
        }

        // CRITICAL REQUIREMENT: Set dailyProgress = goalMomentumProgress so that all color codes
        // (Red -> Purple -> Blue -> Yellow -> Green) in FluidTheme and Shizuku System UI come from
        // Goals being completed or progress towards Goals!
        val effectiveDailyProgress = if (activeGoals.isNotEmpty()) {
            goalMomentumProgress
        } else {
            fallbackTaskProgress
        }

        FlowUiState(
            selectedTab = flags.selectedTab,
            allTasks = tasks,
            projects = projects,
            goals = goals,
            evaluatedGoals = evaluatedGoals,
            completedGoalsCount = completedGoalsCount,
            totalGoalsCount = totalGoalsCount,
            goalMomentumProgress = goalMomentumProgress,
            appUsageSnapshot = usage,
            isGoalCreatorOpen = flags.isGoalCreatorOpen,
            disabledGoalIds = flags.disabledGoalIds,
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
            accessibility = accessibility,
            dailyProgress = effectiveDailyProgress,
            healthSnapshot = sysHealth.healthSnapshot,
            shizukuStatus = sysHealth.shizukuStatus,
            lastSyncedSystemColorHex = sysHealth.lastAppliedHex
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = FlowUiState()
    )

    init {
        viewModelScope.launch {
            refreshHealthAndSystemStatus()
        }

        viewModelScope.launch {
            uiState
                .map { it.accessibility.syncSystemColorsWithShizuku to it.dailyProgress }
                .distinctUntilChanged()
                .collect { (syncEnabled, progress) ->
                    if (syncEnabled) {
                        shizukuManager?.applyMomentumColorForProgress(progress)
                    }
                }
        }

        viewModelScope.launch {
            uiState
                .map { it.healthSnapshot }
                .distinctUntilChanged()
                .collect { snapshot ->
                    val uncompletedHealthTasks = uiState.value.todayTasks.filter {
                        !it.isCompleted && it.healthGoal != null
                    }
                    for (task in uncompletedHealthTasks) {
                        val goal = task.healthGoal ?: continue
                        if (goal.targetValue > 0 && snapshot.valueFor(goal.metricType) >= goal.targetValue) {
                            toggleTaskCompletion(task.id)
                        }
                    }
                }
        }
    }

    private fun evaluateGoal(
        goal: FlowGoal,
        allTasks: List<TaskItem>,
        projectsMap: Map<String, Project>,
        healthSnapshot: HealthDailySnapshot,
        appUsageSnapshot: AppUsageSnapshot,
        todayEpochDay: Long
    ): EvaluatedGoal {
        val startOfTodayMillis = LocalDate.ofEpochDay(todayEpochDay)
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
        val startOfWeekMillis = LocalDate.ofEpochDay(todayEpochDay)
            .minusDays(6)
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()

        val currentValue: Double
        val unitLabel: String
        val subtitle: String

        when (goal.category) {
            GoalCategory.TASK_COMPLETION -> {
                val matchingCompletedTasks = allTasks.filter { task ->
                    task.isCompleted &&
                        (goal.projectId == null || task.projectId == goal.projectId) &&
                        when (goal.period) {
                            GoalPeriod.DAILY -> {
                                (task.completedAt != null && task.completedAt >= startOfTodayMillis) ||
                                    (task.completedAt == null && task.dueDateEpochDay == todayEpochDay)
                            }
                            GoalPeriod.WEEKLY -> {
                                (task.completedAt != null && task.completedAt >= startOfWeekMillis) ||
                                    (task.completedAt == null && task.dueDateEpochDay != null &&
                                        task.dueDateEpochDay in (todayEpochDay - 6)..todayEpochDay)
                            }
                        }
                }
                currentValue = matchingCompletedTasks.size.toDouble()
                unitLabel = "tasks"
                val scopeName = goal.projectId?.let { projectsMap[it]?.name } ?: "All Tasks"
                subtitle = "${goal.period.label} • $scopeName"
            }
            GoalCategory.HEALTH_CONNECT -> {
                val metric = goal.healthMetric ?: HealthMetricType.STEPS
                currentValue = healthSnapshot.valueFor(metric)
                unitLabel = metric.unit
                subtitle = "Health Connect • ${metric.label}"
            }
            GoalCategory.APP_USAGE -> {
                val usageMetric = goal.appUsageMetric ?: AppUsageMetricType.ACTIVE_MINUTES
                val stat = goal.targetPackageName?.let { appUsageSnapshot.statFor(it) }
                currentValue = when (usageMetric) {
                    AppUsageMetricType.ACTIVE_MINUTES -> stat?.activeMinutesToday ?: 0.0
                    AppUsageMetricType.LAUNCH_COUNT -> (stat?.launchCountToday ?: 0).toDouble()
                }
                unitLabel = usageMetric.unit
                val appName = goal.targetAppName?.takeIf { it.isNotBlank() }
                    ?: stat?.appName
                    ?: goal.targetPackageName
                    ?: "App"
                subtitle = "App Usage • $appName"
            }
        }

        val isCompleted: Boolean
        val isOverLimit: Boolean
        val barFillFraction: Float
        val momentumContribution: Float

        when (goal.comparison) {
            GoalComparison.AT_LEAST -> {
                val target = goal.targetValue.coerceAtLeast(0.0001)
                val ratio = (currentValue / target).toFloat().coerceIn(0f, 1f)
                isCompleted = currentValue >= goal.targetValue
                isOverLimit = false
                barFillFraction = ratio
                momentumContribution = ratio
            }
            GoalComparison.AT_MOST -> {
                if (goal.targetValue <= 0.0) {
                    isCompleted = currentValue <= 0.0
                    isOverLimit = currentValue > 0.0
                    barFillFraction = if (isOverLimit) 1f else 0f
                    momentumContribution = if (isCompleted) 1f else 0f
                } else {
                    val usageRatio = (currentValue / goal.targetValue).toFloat()
                    isCompleted = currentValue <= goal.targetValue
                    isOverLimit = currentValue > goal.targetValue
                    barFillFraction = usageRatio.coerceIn(0f, 1f)
                    // For AT_MOST goals, staying under the limit contributes positively to Goal Momentum
                    momentumContribution = if (currentValue <= goal.targetValue) {
                        (1f - (usageRatio * 0.25f)).coerceIn(0.75f, 1f)
                    } else {
                        (1f - (usageRatio - 1f)).coerceIn(0f, 0.35f)
                    }
                }
            }
        }

        val currentStr = formatGoalValue(currentValue, goal, unitLabel)
        val targetStr = formatGoalValue(goal.targetValue, goal, unitLabel)
        val statusText = "$currentStr / ${goal.comparison.symbol} $targetStr $unitLabel"

        return EvaluatedGoal(
            goal = goal,
            currentValue = currentValue,
            targetValue = goal.targetValue,
            isCompleted = isCompleted,
            momentumContribution = momentumContribution,
            barFillFraction = barFillFraction,
            formattedProgressText = statusText,
            subtitleBadgeText = subtitle,
            isOverLimit = isOverLimit
        )
    }

    private fun formatGoalValue(value: Double, goal: FlowGoal, unitLabel: String): String {
        return when {
            goal.category == GoalCategory.HEALTH_CONNECT && goal.healthMetric != null ->
                goal.healthMetric.formatValue(value)
            unitLabel == "min" && value < 10.0 && value % 1.0 != 0.0 ->
                String.format(Locale.US, "%.1f", value)
            else ->
                String.format(Locale.US, "%,d", value.roundToLong())
        }
    }

    fun selectTab(tab: FlowTab) {
        _localFlags.update { it.copy(selectedTab = tab) }
    }

    fun setQuickCaptureOpen(open: Boolean) {
        _localFlags.update { it.copy(isQuickCaptureOpen = open) }
    }

    fun setSettingsOpen(open: Boolean) {
        _localFlags.update { it.copy(isSettingsOpen = open) }
    }

    fun setGoalCreatorOpen(open: Boolean) {
        _localFlags.update { it.copy(isGoalCreatorOpen = open) }
    }

    fun addGoal(
        title: String,
        category: GoalCategory,
        comparison: GoalComparison,
        targetValue: Double,
        period: GoalPeriod = GoalPeriod.DAILY,
        projectId: String? = null,
        healthMetric: HealthMetricType? = null,
        targetPackageName: String? = null,
        targetAppName: String? = null,
        appUsageMetric: AppUsageMetricType? = null
    ) {
        if (title.isBlank()) return
        val newGoal = FlowGoal(
            id = UUID.randomUUID().toString(),
            title = title.trim(),
            category = category,
            comparison = comparison,
            period = period,
            targetValue = targetValue,
            projectId = projectId,
            healthMetric = healthMetric,
            targetPackageName = targetPackageName,
            targetAppName = targetAppName,
            appUsageMetric = appUsageMetric,
            createdAt = System.currentTimeMillis()
        )
        viewModelScope.launch {
            repository.upsertGoal(newGoal)
        }
    }

    fun toggleGoalEnabled(goalId: String) {
        _localFlags.update { flags ->
            val newDisabled = if (goalId in flags.disabledGoalIds) {
                flags.disabledGoalIds - goalId
            } else {
                flags.disabledGoalIds + goalId
            }
            globalDisabledGoalIds.value = newDisabled
            flags.copy(disabledGoalIds = newDisabled)
        }
    }

    fun deleteGoal(goalId: String) {
        viewModelScope.launch {
            repository.deleteGoal(goalId)
        }
    }

    fun addTask(
        title: String,
        notes: String = "",
        bucket: GtdBucket = GtdBucket.INBOX,
        dueDateEpochDay: Long? = null,
        projectId: String? = null,
        isFlagged: Boolean = false,
        recurrenceRule: RecurrenceRule? = null,
        healthGoal: HealthGoalSpec? = null
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
            healthGoal = healthGoal,
            createdAt = System.currentTimeMillis(),
            completedAt = null
        )
        viewModelScope.launch {
            repository.upsertTask(newTask)
        }
    }

    fun addQuickHealthGoalTask(
        metricType: HealthMetricType,
        targetValue: Double = metricType.defaultTarget,
        isDailyRecurring: Boolean = true
    ) {
        val title = "Reach ${metricType.formatValue(targetValue)} ${metricType.unit} (${metricType.label})"
        val todayEpochDay = LocalDate.now().toEpochDay()
        val recurrence = if (isDailyRecurring) RecurrenceRule(type = RecurrenceType.DAILY) else null
        val goal = HealthGoalSpec(metricType = metricType, targetValue = targetValue)

        addTask(
            title = title,
            notes = "Auto-synced via Health Connect",
            bucket = GtdBucket.SCHEDULED,
            dueDateEpochDay = todayEpochDay,
            recurrenceRule = recurrence,
            healthGoal = goal
        )

        addGoal(
            title = title,
            category = GoalCategory.HEALTH_CONNECT,
            comparison = GoalComparison.AT_LEAST,
            period = GoalPeriod.DAILY,
            targetValue = targetValue,
            healthMetric = metricType
        )

        viewModelScope.launch {
            healthConnectManager?.refreshDailySnapshot()
        }
    }

    fun refreshHealthAndSystemStatus() {
        viewModelScope.launch {
            shizukuManager?.refreshStatus()
            healthConnectManager?.refreshDailySnapshot()
            appUsageManager?.refreshUsageSnapshot()
            if (uiState.value.accessibility.syncSystemColorsWithShizuku) {
                shizukuManager?.applyMomentumColorForProgress(uiState.value.dailyProgress)
            }
        }
    }

    fun requestShizukuPermission() {
        shizukuManager?.requestPermission()
    }

    fun syncSystemColorsNow() {
        viewModelScope.launch {
            shizukuManager?.applyMomentumColorForProgress(uiState.value.dailyProgress, force = true)
        }
    }

    fun restoreOriginalSystemPalette() {
        viewModelScope.launch {
            shizukuManager?.restoreOriginalSystemPalette()
        }
    }

    fun getAllHealthPermissions(): Set<String> = healthConnectManager?.allReadPermissions ?: emptySet()

    fun toggleTaskCompletion(taskId: String) {
        val task = uiState.value.allTasks.find { it.id == taskId } ?: return
        val nowCompleted = !task.isCompleted
        val nowMillis = System.currentTimeMillis()
        val rule = task.recurrenceRule

        viewModelScope.launch {
            if (nowCompleted && rule != null && rule.isRecurring) {
                val completedOccurrence = task.copy(
                    isCompleted = true,
                    recurrenceRule = null,
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
        internal val globalDisabledGoalIds = MutableStateFlow<Set<String>>(emptySet())

        fun provideFactory(
            repository: FlowRepository,
            healthConnectManager: HealthConnectManager? = null,
            shizukuManager: ShizukuSystemColorManager? = null,
            appUsageManager: AppUsageManager? = null
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    if (modelClass.isAssignableFrom(FlowViewModel::class.java)) {
                        return FlowViewModel(repository, healthConnectManager, shizukuManager, appUsageManager) as T
                    }
                    throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
                }
            }
    }
}
