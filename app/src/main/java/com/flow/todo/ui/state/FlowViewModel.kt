package com.flow.todo.ui.state

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.flow.todo.data.cloud.FirestoreSyncManager
import com.flow.todo.data.health.HealthConnectManager
import com.flow.todo.data.repository.FlowRepository
import com.flow.todo.data.system.ShizukuSystemColorManager
import com.flow.todo.data.usage.AppUsageManager
import com.flow.todo.domain.model.AccessibilityPreferences
import com.flow.todo.domain.model.AppUsageMetricType
import com.flow.todo.domain.model.AppUsageSnapshot
import com.flow.todo.domain.model.CloudSyncState
import com.flow.todo.domain.model.ContributionChartSummary
import com.flow.todo.domain.model.ContributionDayCell
import com.flow.todo.domain.model.DailyCompletionRecord
import com.flow.todo.domain.model.EvaluatedGoal
import com.flow.todo.domain.model.FirebaseCloudConfig
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
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
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
    val lastSyncedSystemColorHex: String? = null,
    val cloudSyncState: CloudSyncState = CloudSyncState(),
    val contributionChart: ContributionChartSummary = ContributionChartSummary()
)

class FlowViewModel(
    private val repository: FlowRepository,
    private val healthConnectManager: HealthConnectManager? = null,
    private val shizukuManager: ShizukuSystemColorManager? = null,
    private val appUsageManager: AppUsageManager? = null,
    private val firestoreSyncManager: FirestoreSyncManager? = null
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
        val dailyHistory: List<DailyCompletionRecord>,
        val appUsage: AppUsageSnapshot
    )

    private data class SystemHealthState(
        val healthSnapshot: HealthDailySnapshot = HealthDailySnapshot(),
        val shizukuStatus: ShizukuStatus = ShizukuStatus.NOT_INSTALLED,
        val lastAppliedHex: String? = null
    )

    private data class DailyRecordSnapshot(
        val completedTasks: Int,
        val completedGoals: Int,
        val totalGoals: Int,
        val momentum: Float
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

    private val cloudSyncFlow: Flow<CloudSyncState> =
        firestoreSyncManager?.state ?: MutableStateFlow(CloudSyncState())

    private val domainDataFlow: Flow<RepositoryDomainData> = combine(
        repository.observeTasks(),
        repository.observeProjects(),
        repository.observeGoals(),
        repository.observeDailyHistory(),
        appUsageSnapshotFlow
    ) { tasks, projects, goals, history, usage ->
        RepositoryDomainData(
            tasks = tasks,
            projects = projects,
            goals = goals,
            dailyHistory = history,
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
        systemHealthFlow,
        cloudSyncFlow
    ) { flags, domainData, accessibility, sysHealth, cloudSync ->
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

        val contributionChart = buildContributionChart(
            allTasks = tasks,
            dailyHistory = domainData.dailyHistory,
            todayEpochDay = todayEpochDay,
            completedTodayTasks = completedTodayCount,
            completedTodayGoals = completedGoalsCount,
            totalTodayGoals = totalGoalsCount,
            todayMomentum = effectiveDailyProgress
        )

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
            lastSyncedSystemColorHex = sysHealth.lastAppliedHex,
            cloudSyncState = cloudSync,
            contributionChart = contributionChart
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

        viewModelScope.launch {
            uiState
                .map {
                    DailyRecordSnapshot(
                        completedTasks = it.completedTodayCount,
                        completedGoals = it.completedGoalsCount,
                        totalGoals = it.totalGoalsCount,
                        momentum = it.goalMomentumProgress
                    )
                }
                .distinctUntilChanged()
                .collect { snap ->
                    val todayEpochDay = LocalDate.now().toEpochDay()
                    val record = DailyCompletionRecord(
                        epochDay = todayEpochDay,
                        tasksCompletedCount = snap.completedTasks,
                        goalsCompletedCount = snap.completedGoals,
                        totalGoalsCount = snap.totalGoals,
                        momentumScore = snap.momentum,
                        updatedAt = System.currentTimeMillis()
                    )
                    repository.upsertDailyHistory(record)
                    firestoreSyncManager?.pushDailyHistory(record)
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

    private fun buildContributionChart(
        allTasks: List<TaskItem>,
        dailyHistory: List<DailyCompletionRecord>,
        todayEpochDay: Long,
        completedTodayTasks: Int,
        completedTodayGoals: Int,
        totalTodayGoals: Int,
        todayMomentum: Float
    ): ContributionChartSummary {
        val today = LocalDate.ofEpochDay(todayEpochDay)
        val thisMonday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val startMonday = thisMonday.minusWeeks(51)

        val completedTasksByDay = mutableMapOf<Long, Int>()
        allTasks.filter { it.isCompleted }.forEach { task ->
            val epochDay = if (task.completedAt != null) {
                runCatching {
                    Instant.ofEpochMilli(task.completedAt)
                        .atZone(ZoneId.systemDefault())
                        .toLocalDate()
                        .toEpochDay()
                }.getOrNull() ?: task.dueDateEpochDay
            } else {
                task.dueDateEpochDay
            }
            if (epochDay != null) {
                completedTasksByDay[epochDay] = (completedTasksByDay[epochDay] ?: 0) + 1
            }
        }

        val historyByDay = dailyHistory.associateBy { it.epochDay }
        val totalDays = 52 * 7
        val cells = ArrayList<ContributionDayCell>(totalDays)
        val fullDateFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.getDefault())

        for (i in 0 until totalDays) {
            val date = startMonday.plusDays(i.toLong())
            val epochDay = date.toEpochDay()
            val isToday = epochDay == todayEpochDay
            val isFuture = epochDay > todayEpochDay
            val dayOfWeekIndex = date.dayOfWeek.value - 1 // 0=Mon .. 6=Sun

            val historyRecord = historyByDay[epochDay]
            val tasksFromList = completedTasksByDay[epochDay] ?: 0
            val tasksCount = if (isToday) {
                maxOf(tasksFromList, historyRecord?.tasksCompletedCount ?: 0, completedTodayTasks)
            } else {
                maxOf(tasksFromList, historyRecord?.tasksCompletedCount ?: 0)
            }

            val goalsCompleted = if (isToday) {
                maxOf(completedTodayGoals, historyRecord?.goalsCompletedCount ?: 0)
            } else {
                historyRecord?.goalsCompletedCount ?: 0
            }

            val totalGoals = if (isToday) {
                if (totalTodayGoals > 0) totalTodayGoals else (historyRecord?.totalGoalsCount ?: 0)
            } else {
                historyRecord?.totalGoalsCount ?: 0
            }

            val momentumScore = if (isToday) {
                if (todayMomentum > 0f) todayMomentum else (historyRecord?.momentumScore ?: 0f)
            } else {
                historyRecord?.momentumScore ?: 0f
            }

            // taskLevel: 0 when 0 tasks, 1 when 1 task, 2 when 2 tasks, 3 when 3–4 tasks, 4 when >= 5 tasks
            val taskLevel = when {
                tasksCount <= 0 -> 0
                tasksCount == 1 -> 1
                tasksCount == 2 -> 2
                tasksCount in 3..4 -> 3
                else -> 4
            }

            // goalLevel: 0 when 0 goals met and momentum < 0.1f, 1 when momentum < 0.35f, 2 when momentum < 0.65f, 3 when momentum < 0.90f, 4 when momentum >= 0.90f or all goals met (goalsCompleted >= totalGoals && totalGoals > 0)
            val allGoalsMet = goalsCompleted >= totalGoals && totalGoals > 0
            val goalLevel = when {
                allGoalsMet || momentumScore >= 0.90f -> 4
                momentumScore >= 0.65f -> 3
                momentumScore >= 0.35f -> 2
                momentumScore >= 0.10f || goalsCompleted > 0 -> 1
                else -> 0
            }

            // combinedLevel: maxOf(taskLevel, goalLevel) (or ((taskLevel + goalLevel + 1) / 2).coerceIn(1, 4) when either > 0)
            val combinedLevel = if (isFuture) {
                0
            } else if (taskLevel == 0 && goalLevel == 0) {
                0
            } else {
                ((taskLevel + goalLevel + 1) / 2).coerceIn(1, 4)
            }

            val cell = ContributionDayCell(
                epochDay = epochDay,
                dateLabel = date.format(fullDateFormatter),
                dayOfWeekIndex = dayOfWeekIndex,
                isToday = isToday,
                isFuture = isFuture,
                tasksCompleted = if (isFuture) 0 else tasksCount,
                goalsCompleted = if (isFuture) 0 else goalsCompleted,
                totalGoals = if (isFuture) 0 else totalGoals,
                momentumScore = if (isFuture) 0f else momentumScore,
                combinedLevel = combinedLevel,
                taskLevel = if (isFuture) 0 else taskLevel,
                goalLevel = if (isFuture) 0 else goalLevel
            )
            cells.add(cell)
        }

        val weeks = cells.chunked(7)
        val pastAndTodayCells = cells.filter { !it.isFuture }
        val todayCell = pastAndTodayCells.find { it.isToday }

        var currentStreak = 0
        val startIndex = if (todayCell != null && todayCell.combinedLevel > 0) {
            pastAndTodayCells.indexOf(todayCell)
        } else if (todayCell != null) {
            pastAndTodayCells.indexOf(todayCell) - 1
        } else {
            pastAndTodayCells.size - 1
        }

        for (idx in startIndex downTo 0) {
            if (pastAndTodayCells[idx].combinedLevel > 0) {
                currentStreak++
            } else {
                break
            }
        }

        var longestStreak = 0
        var tempStreak = 0
        for (cell in pastAndTodayCells) {
            if (cell.combinedLevel > 0) {
                tempStreak++
                if (tempStreak > longestStreak) {
                    longestStreak = tempStreak
                }
            } else {
                tempStreak = 0
            }
        }
        longestStreak = maxOf(longestStreak, currentStreak)

        return ContributionChartSummary(
            cells = cells,
            weeks = weeks,
            currentStreakDays = currentStreak,
            longestStreakDays = longestStreak,
            totalTasksCompletedInWindow = pastAndTodayCells.sumOf { it.tasksCompleted },
            totalGoalsMetInWindow = pastAndTodayCells.sumOf { it.goalsCompleted },
            activeDaysCount = pastAndTodayCells.count { it.combinedLevel > 0 }
        )
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
            firestoreSyncManager?.pushGoal(newGoal)
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
            firestoreSyncManager?.deleteRemoteGoal(goalId)
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
            firestoreSyncManager?.pushTask(newTask)
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
                firestoreSyncManager?.pushTask(completedOccurrence)
                firestoreSyncManager?.pushTask(nextOccurrence)
            } else {
                val updatedTask = task.copy(
                    isCompleted = nowCompleted,
                    completedAt = if (nowCompleted) nowMillis else null
                )
                repository.upsertTask(updatedTask)
                firestoreSyncManager?.pushTask(updatedTask)
            }
        }
    }

    fun toggleTaskFlag(taskId: String) {
        val task = uiState.value.allTasks.find { it.id == taskId } ?: return
        val updatedTask = task.copy(isFlagged = !task.isFlagged)
        viewModelScope.launch {
            repository.upsertTask(updatedTask)
            firestoreSyncManager?.pushTask(updatedTask)
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
            firestoreSyncManager?.pushTask(updatedTask)
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
            firestoreSyncManager?.pushTask(updatedTask)
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
            firestoreSyncManager?.pushTask(updatedTask)
        }
    }

    fun assignTaskProject(taskId: String, projectId: String?) {
        val task = uiState.value.allTasks.find { it.id == taskId } ?: return
        val updatedTask = task.copy(projectId = projectId)
        viewModelScope.launch {
            repository.upsertTask(updatedTask)
            firestoreSyncManager?.pushTask(updatedTask)
        }
    }

    fun deleteTask(taskId: String) {
        viewModelScope.launch {
            repository.deleteTask(taskId)
            firestoreSyncManager?.deleteRemoteTask(taskId)
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
            firestoreSyncManager?.pushProject(newProject)
        }
    }

    fun deleteProject(projectId: String) {
        viewModelScope.launch {
            repository.deleteProject(projectId)
            firestoreSyncManager?.deleteRemoteProject(projectId)
        }
    }

    fun updateAccessibility(transform: (AccessibilityPreferences) -> AccessibilityPreferences) {
        viewModelScope.launch {
            repository.updateAccessibilityPreferences(transform)
            val updated = repository.accessibilityPreferences.value
            firestoreSyncManager?.pushPreferences(updated)
            if (updated.syncSystemColorsWithShizuku) {
                shizukuManager?.applyMomentumColorForProgress(uiState.value.dailyProgress, force = true)
            }
        }
    }

    fun getGoogleSignInIntent(): android.content.Intent? = firestoreSyncManager?.getGoogleSignInIntent()

    fun onGoogleSignInResult(data: android.content.Intent?) {
        viewModelScope.launch {
            firestoreSyncManager?.handleGoogleSignInResult(data)
        }
    }

    fun signInWithEmail(email: String, password: String, isSignUp: Boolean) {
        viewModelScope.launch {
            firestoreSyncManager?.signInWithEmail(email, password, isSignUp)
        }
    }

    fun signOutCloud() {
        firestoreSyncManager?.signOut()
    }

    fun syncWithCloudNow() {
        viewModelScope.launch {
            firestoreSyncManager?.syncAllNow()
        }
    }

    fun updateFirebaseConfig(config: FirebaseCloudConfig) {
        firestoreSyncManager?.updateFirebaseConfig(config)
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
            appUsageManager: AppUsageManager? = null,
            firestoreSyncManager: FirestoreSyncManager? = null
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    if (modelClass.isAssignableFrom(FlowViewModel::class.java)) {
                        return FlowViewModel(
                            repository = repository,
                            healthConnectManager = healthConnectManager,
                            shizukuManager = shizukuManager,
                            appUsageManager = appUsageManager,
                            firestoreSyncManager = firestoreSyncManager
                        ) as T
                    }
                    throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
                }
            }
    }
}
