package com.flow.todo.ui.state

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.flow.todo.data.calendar.CalendarImportManager
import com.flow.todo.data.calendar.DeviceCalendarSync
import com.flow.todo.domain.model.DeviceCalendarInfo
import com.flow.todo.domain.timeline.DayTimeline
import com.flow.todo.domain.timeline.DayTimelineModel
import com.flow.todo.data.cloud.FirestoreSyncManager
import com.flow.todo.data.health.HealthConnectManager
import com.flow.todo.data.repository.FlowRepository
import com.flow.todo.data.system.ShizukuSystemColorManager
import com.flow.todo.data.usage.AppUsageManager
import com.flow.todo.data.usage.AppUsageShieldMonitor
import com.flow.todo.domain.model.AccessibilityPreferences
import com.flow.todo.domain.model.AppUsageMetricType
import com.flow.todo.domain.model.AppUsageShieldAlert
import com.flow.todo.domain.model.AppUsageSnapshot
import com.flow.todo.domain.model.CalendarDailySummary
import com.flow.todo.domain.model.CalendarEventItem
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
import com.flow.todo.domain.model.HealthDailySnapshot
import com.flow.todo.domain.model.HealthGoalSpec
import com.flow.todo.domain.model.HealthMetricType
import com.flow.todo.domain.model.RecurrenceRule
import com.flow.todo.domain.model.RecurrenceType
import com.flow.todo.domain.model.ShizukuStatus
import com.flow.todo.domain.model.SubtaskItem
import com.flow.todo.domain.model.TaskItem
import com.flow.todo.widget.FlowCalendarWidgetProvider
import com.flow.todo.widget.FlowGlassWidgetProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
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

import com.flow.todo.domain.model.ParsedTaskDraft
import com.flow.todo.domain.tasks.TaskSections
import kotlinx.coroutines.delay

val FlowGoal.isEnabled: Boolean
    get() = this.id !in FlowViewModel.globalDisabledGoalIds.value

data class FlowUiState(
    val selectedTab: FlowTab = FlowTab.TASKS,
    val allTasks: List<TaskItem> = emptyList(),
    /** The Tasks screen: overdue / today / upcoming / no date / done today. */
    val taskSections: TaskSections = TaskSections(),
    val goals: List<FlowGoal> = emptyList(),
    val evaluatedGoals: List<EvaluatedGoal> = emptyList(),
    val completedGoalsCount: Int = 0,
    val totalGoalsCount: Int = 0,
    val goalMomentumProgress: Float = 0f,
    val appUsageSnapshot: AppUsageSnapshot = AppUsageSnapshot(),
    val isGoalCreatorOpen: Boolean = false,
    val disabledGoalIds: Set<String> = emptySet(),
    /** Overdue + due today (open) + everything completed today. Drives progress and the widget. */
    val todayTasks: List<TaskItem> = emptyList(),
    val completedTodayCount: Int = 0,
    val totalTodayTargetCount: Int = 0,
    val isQuickCaptureOpen: Boolean = false,
    val quickCaptureInitialTitle: String = "",
    val quickCaptureAutoVoice: Boolean = false,
    val isSettingsOpen: Boolean = false,
    val accessibility: AccessibilityPreferences = AccessibilityPreferences(),
    val dailyProgress: Float = 0f,
    val healthSnapshot: HealthDailySnapshot = HealthDailySnapshot(),
    val shizukuStatus: ShizukuStatus = ShizukuStatus.NOT_INSTALLED,
    val lastSyncedSystemColorHex: String? = null,
    val cloudSyncState: CloudSyncState = CloudSyncState(),
    val contributionChart: ContributionChartSummary = ContributionChartSummary(),
    val activeShieldAlert: AppUsageShieldAlert? = null,
    val shieldAlerts: List<AppUsageShieldAlert> = emptyList(),
    val allCalendarEvents: List<CalendarEventItem> = emptyList(),
    val todayCalendarEvents: List<CalendarEventItem> = emptyList(),
    val selectedCalendarDate: LocalDate = LocalDate.now(),
    val selectedDateCalendarEvents: List<CalendarEventItem> = emptyList(),
    val calendarDailySummary: CalendarDailySummary = CalendarDailySummary(epochDay = LocalDate.now().toEpochDay()),
    val isCalendarImporting: Boolean = false,
    val calendarImportMessage: String? = null,
    /** Day screen: the selected day's timeline (events + time-blocked tasks + free gaps + tray). */
    val dayTimeline: DayTimelineModel = DayTimeline.build(LocalDate.now().toEpochDay(), emptyList(), emptyList(), System.currentTimeMillis()),
    /** Per-day dots for the week strip and month view. */
    val dayMarkers: Map<Long, DayMarker> = emptyMap(),
    /** Google / device calendars Flow can write new events into. */
    val writableCalendars: List<DeviceCalendarInfo> = emptyList(),
    val canWriteCalendar: Boolean = false,
    /** One-off feedback ("Repeating event: change it in Google Calendar"). */
    val userMessage: String? = null
)

data class DayMarker(val eventCount: Int = 0, val openTaskCount: Int = 0)

class FlowViewModel(
    private val repository: FlowRepository,
    private val healthConnectManager: HealthConnectManager? = null,
    private val shizukuManager: ShizukuSystemColorManager? = null,
    private val appUsageManager: AppUsageManager? = null,
    private val firestoreSyncManager: FirestoreSyncManager? = null,
    private val appUsageShieldMonitor: AppUsageShieldMonitor? = null,
    private val appContext: android.content.Context? = null,
    private val calendarImportManager: CalendarImportManager? = null,
    private val deviceCalendarSync: DeviceCalendarSync? = null
) : ViewModel() {

    private data class LocalUiFlags(
        val selectedTab: FlowTab = FlowTab.TASKS,
        val isQuickCaptureOpen: Boolean = false,
        val quickCaptureInitialTitle: String = "",
        val quickCaptureAutoVoice: Boolean = false,
        val isSettingsOpen: Boolean = false,
        val isGoalCreatorOpen: Boolean = false,
        val disabledGoalIds: Set<String> = emptySet(),
        val selectedCalendarDate: LocalDate = LocalDate.now(),
        val isCalendarImporting: Boolean = false,
        val calendarImportMessage: String? = null,
        val writableCalendars: List<DeviceCalendarInfo> = emptyList(),
        val userMessage: String? = null,
        /** Bumped every minute so the now line / free gaps move. */
        val clockTick: Long = 0L
    )

    private data class RepositoryDomainData(
        val tasks: List<TaskItem>,
        val goals: List<FlowGoal>,
        val dailyHistory: List<DailyCompletionRecord>,
        val calendarEvents: List<CalendarEventItem>,
        val appUsage: AppUsageSnapshot
    )

    private data class SystemHealthState(
        val healthSnapshot: HealthDailySnapshot = HealthDailySnapshot(),
        val shizukuStatus: ShizukuStatus = ShizukuStatus.NOT_INSTALLED,
        val lastAppliedHex: String? = null,
        val activeShieldAlert: AppUsageShieldAlert? = null,
        val shieldAlerts: List<AppUsageShieldAlert> = emptyList()
    )

    private data class DailyRecordSnapshot(
        val completedTasks: Int,
        val completedGoals: Int,
        val totalGoals: Int,
        val momentum: Float
    )

    private val _localFlags = MutableStateFlow(LocalUiFlags())

    /** Shared across activity recreations because the ViewModel survives them. */
    private var lastSystemColorApplyMillis = 0L
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

    private val activeShieldAlertFlow: Flow<AppUsageShieldAlert?> =
        appUsageShieldMonitor?.activeBannerAlert ?: MutableStateFlow(null)

    private val shieldAlertsFlow: Flow<List<AppUsageShieldAlert>> =
        appUsageShieldMonitor?.alerts ?: MutableStateFlow(emptyList())

    private data class CoreDomainEntities(
        val tasks: List<TaskItem>,
        val goals: List<FlowGoal>,
        val dailyHistory: List<DailyCompletionRecord>,
        val calendarEvents: List<CalendarEventItem>
    )

    private val domainDataFlow: Flow<RepositoryDomainData> = combine(
        combine(
            repository.observeTasks(),
            repository.observeGoals(),
            repository.observeDailyHistory(),
            repository.observeCalendarEvents()
        ) { tasks, goals, history, calendarEvents ->
            CoreDomainEntities(tasks, goals, history, calendarEvents)
        },
        appUsageSnapshotFlow
    ) { core, usage ->
        RepositoryDomainData(
            tasks = core.tasks,
            goals = core.goals,
            dailyHistory = core.dailyHistory,
            calendarEvents = core.calendarEvents,
            appUsage = usage
        )
    }

    private val systemHealthFlow: Flow<SystemHealthState> = combine(
        healthSnapshotFlow,
        shizukuStatusFlow,
        lastAppliedHexFlow,
        activeShieldAlertFlow,
        shieldAlertsFlow
    ) { health, shizuku, hex, activeShield, allShields ->
        SystemHealthState(
            healthSnapshot = health,
            shizukuStatus = shizuku,
            lastAppliedHex = hex,
            activeShieldAlert = activeShield,
            shieldAlerts = allShields
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

        val tasks = domainData.tasks
        val goals = domainData.goals
        val usage = domainData.appUsage

        val taskSections = TaskSections.build(tasks, today)
        val todayTasks = taskSections.overdue + taskSections.today + taskSections.doneToday

        val completedTodayCount = todayTasks.count { it.isCompleted }
        val totalTodayTargetCount = todayTasks.size

        // Evaluate first-class Flow goals
        val evaluatedGoals = goals.map { goal ->
            evaluateGoal(
                goal = goal,
                allTasks = tasks,
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

        val calendarEvents = domainData.calendarEvents
        val zone = ZoneId.systemDefault()
        val startOfTodayMillis = today.atStartOfDay(zone).toInstant().toEpochMilli()
        val endOfTodayMillis = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1L

        val todayCalendarEvents = calendarEvents.filter {
            it.startEpochMillis <= endOfTodayMillis && it.endEpochMillis >= startOfTodayMillis
        }
        val completedTodayCalendarCount = todayCalendarEvents.count { it.isCompleted }

        val startOfSelectedDayMillis = flags.selectedCalendarDate.atStartOfDay(zone).toInstant().toEpochMilli()
        val endOfSelectedDayMillis = flags.selectedCalendarDate.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1L
        val selectedDateCalendarEvents = calendarEvents.filter {
            it.startEpochMillis <= endOfSelectedDayMillis && it.endEpochMillis >= startOfSelectedDayMillis
        }

        val calendarDailySummary = CalendarDailySummary(
            epochDay = todayEpochDay,
            totalEventsCount = todayCalendarEvents.size,
            completedEventsCount = completedTodayCalendarCount,
            momentumContribution = if (todayCalendarEvents.isNotEmpty()) completedTodayCalendarCount.toFloat() / todayCalendarEvents.size.toFloat() else 0f
        )

        // Fallback progress for when no goals are configured yet (combines tasks and calendar events)
        val fallbackTaskProgress = run {
            val totalTodayItems = todayTasks.size + todayCalendarEvents.size
            if (totalTodayItems == 0) {
                0f
            } else {
                val taskScore = todayTasks.sumOf { task ->
                    when {
                        task.isCompleted -> 1.0
                        task.healthGoal != null -> {
                            val currentVal = sysHealth.healthSnapshot.valueFor(task.healthGoal.metricType)
                            task.healthGoal.progressFraction(currentVal).toDouble()
                        }
                        else -> 0.0
                    }
                }
                val calScore = completedTodayCalendarCount.toDouble()
                ((taskScore + calScore) / totalTodayItems.toDouble()).toFloat().coerceIn(0f, 1f)
            }
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
            completedTodayTasks = completedTodayCount + completedTodayCalendarCount,
            completedTodayGoals = completedGoalsCount,
            totalTodayGoals = totalGoalsCount,
            todayMomentum = effectiveDailyProgress
        )

        FlowUiState(
            selectedTab = flags.selectedTab,
            allTasks = tasks,
            taskSections = taskSections,
            goals = goals,
            evaluatedGoals = evaluatedGoals,
            completedGoalsCount = completedGoalsCount,
            totalGoalsCount = totalGoalsCount,
            goalMomentumProgress = goalMomentumProgress,
            appUsageSnapshot = usage,
            isGoalCreatorOpen = flags.isGoalCreatorOpen,
            disabledGoalIds = flags.disabledGoalIds,
            todayTasks = todayTasks,
            completedTodayCount = completedTodayCount,
            totalTodayTargetCount = totalTodayTargetCount,
            isQuickCaptureOpen = flags.isQuickCaptureOpen,
            quickCaptureInitialTitle = flags.quickCaptureInitialTitle,
            quickCaptureAutoVoice = flags.quickCaptureAutoVoice,
            isSettingsOpen = flags.isSettingsOpen,
            accessibility = accessibility,
            dailyProgress = effectiveDailyProgress,
            healthSnapshot = sysHealth.healthSnapshot,
            shizukuStatus = sysHealth.shizukuStatus,
            lastSyncedSystemColorHex = sysHealth.lastAppliedHex,
            cloudSyncState = cloudSync,
            contributionChart = contributionChart,
            activeShieldAlert = sysHealth.activeShieldAlert,
            shieldAlerts = sysHealth.shieldAlerts,
            allCalendarEvents = calendarEvents,
            todayCalendarEvents = todayCalendarEvents,
            selectedCalendarDate = flags.selectedCalendarDate,
            selectedDateCalendarEvents = selectedDateCalendarEvents,
            calendarDailySummary = calendarDailySummary,
            isCalendarImporting = flags.isCalendarImporting,
            calendarImportMessage = flags.calendarImportMessage,
            dayTimeline = DayTimeline.build(
                epochDay = flags.selectedCalendarDate.toEpochDay(),
                events = calendarEvents,
                tasks = tasks,
                nowMillis = System.currentTimeMillis(),
                zone = zone
            ),
            dayMarkers = buildDayMarkers(calendarEvents, tasks, zone),
            writableCalendars = flags.writableCalendars,
            canWriteCalendar = deviceCalendarSync?.canWrite() == true,
            userMessage = flags.userMessage
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = FlowUiState()
    )

    private var unregisterCalendarObserver: (() -> Unit)? = null

    init {
        viewModelScope.launch {
            refreshHealthAndSystemStatus()
        }

        // Two-way calendar: mirror the phone calendar now and whenever it changes (Google syncs edits in).
        startCalendarMirror()

        // Move the now line and free-time gaps along with the clock.
        viewModelScope.launch {
            while (true) {
                delay(60_000L - (System.currentTimeMillis() % 60_000L))
                _localFlags.update { it.copy(clockTick = it.clockTick + 1) }
            }
        }

        // System color sync is debounced and rate-limited: every change re-themes the whole OS (and
        // recreates this activity), so applying on each progress tick makes adding goals laggy.
        // Waits for progress to settle, never applies while the goal creator is open, and applies at
        // most once per SYSTEM_COLOR_MIN_INTERVAL_MS (always ending on the latest value).
        viewModelScope.launch {
            uiState
                .map { Triple(it.accessibility.syncSystemColorsWithShizuku, it.dailyProgress, it.isGoalCreatorOpen) }
                .distinctUntilChanged()
                .collectLatest { (syncEnabled, progress, goalCreatorOpen) ->
                    if (!syncEnabled || goalCreatorOpen) return@collectLatest
                    val sinceLast = System.currentTimeMillis() - lastSystemColorApplyMillis
                    delay(maxOf(SYSTEM_COLOR_SETTLE_MS, SYSTEM_COLOR_MIN_INTERVAL_MS - sinceLast))
                    if (shizukuManager?.applyMomentumColorForProgress(progress) == true) {
                        lastSystemColorApplyMillis = System.currentTimeMillis()
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

        // Keep the Calendar agenda widget in sync with events and tasks (debounced: bulk imports fire many updates)
        viewModelScope.launch {
            uiState
                .map { it.allCalendarEvents to it.allTasks }
                .distinctUntilChanged()
                .collectLatest {
                    delay(600)
                    appContext?.let { ctx -> FlowCalendarWidgetProvider.requestUpdate(ctx) }
                }
        }

        // Monitor App Usage Shield 80% & 100% Limits and Sync Home Screen Glass Widget & Quick Settings Tile
        viewModelScope.launch {
            uiState.collect { state ->
                appUsageShieldMonitor?.evaluateGoals(
                    evaluatedGoals = state.evaluatedGoals,
                    shieldEnabled = state.accessibility.enableAppUsageShield
                )

                val ctx = appContext
                if (ctx != null) {
                    val sections = state.taskSections
                    val topUncompleted = sections.overdue.firstOrNull()
                        ?: sections.today.firstOrNull()
                        ?: sections.upcoming.firstOrNull()?.tasks?.firstOrNull()
                        ?: sections.noDate.firstOrNull()

                    val recentLevels = state.contributionChart.cells
                        .filter { !it.isFuture }
                        .takeLast(84)
                        .map { it.combinedLevel }

                    FlowGlassWidgetProvider.saveWidgetSnapshotAndRefresh(
                        context = ctx,
                        momentumProgress = state.dailyProgress,
                        completedToday = state.completedTodayCount,
                        totalToday = state.totalTodayTargetCount,
                        completedGoals = state.completedGoalsCount,
                        totalGoals = state.totalGoalsCount,
                        streakDays = state.contributionChart.currentStreakDays,
                        topTaskId = topUncompleted?.id,
                        topTaskTitle = topUncompleted?.title,
                        recentHeatmapLevels = recentLevels,
                        stopHexes = state.accessibility.momentumStopHexes
                    )
                }
            }
        }
    }

    private fun evaluateGoal(
        goal: FlowGoal,
        allTasks: List<TaskItem>,
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
                subtitle = "${goal.period.label} • All tasks"
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
        _localFlags.update {
            if (open) {
                it.copy(isQuickCaptureOpen = true)
            } else {
                it.copy(
                    isQuickCaptureOpen = false,
                    quickCaptureInitialTitle = "",
                    quickCaptureAutoVoice = false
                )
            }
        }
    }

    fun openQuickCaptureWithDraft(initialTitle: String = "", autoStartVoice: Boolean = false) {
        _localFlags.update {
            it.copy(
                isQuickCaptureOpen = true,
                quickCaptureInitialTitle = initialTitle,
                quickCaptureAutoVoice = autoStartVoice
            )
        }
    }

    fun dismissShieldAlert(goalId: String) {
        appUsageShieldMonitor?.dismissBannerAlert(goalId)
    }

    fun triggerTestShieldNudge() {
        appUsageShieldMonitor?.triggerTestShieldNudge(uiState.value.evaluatedGoals)
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
        dueDateEpochDay: Long? = null,
        isFlagged: Boolean = false,
        recurrenceRule: RecurrenceRule? = null,
        healthGoal: HealthGoalSpec? = null,
        subtasks: List<SubtaskItem> = emptyList(),
        startMinute: Int? = null,
        durationMinutes: Int? = null
    ) {
        if (title.isBlank()) return
        val hasRecurrence = recurrenceRule != null && recurrenceRule.isRecurring
        val effectiveDueDate = when {
            dueDateEpochDay != null -> dueDateEpochDay
            hasRecurrence -> recurrenceRule!!.firstMatchingOrAfter(LocalDate.now()).toEpochDay()
            else -> null
        }
        val newTask = TaskItem(
            id = UUID.randomUUID().toString(),
            title = title.trim(),
            notes = notes.trim(),
            dueDateEpochDay = effectiveDueDate,
            isFlagged = isFlagged,
            isCompleted = false,
            recurrenceRule = if (hasRecurrence) recurrenceRule else null,
            healthGoal = healthGoal,
            subtasks = subtasks,
            createdAt = System.currentTimeMillis(),
            completedAt = null,
            // A time only makes sense with a day; default to today.
            startMinute = startMinute,
            durationMinutes = durationMinutes
        ).let { if (it.startMinute != null && it.dueDateEpochDay == null) it.copy(dueDateEpochDay = LocalDate.now().toEpochDay()) else it }
        viewModelScope.launch {
            repository.upsertTask(newTask)
            firestoreSyncManager?.pushTask(newTask)
        }
    }

    fun addTasksBatch(drafts: List<ParsedTaskDraft>) {
        viewModelScope.launch {
            val tasks = drafts.map { draft ->
                val hasRecurrence = draft.recurrenceRule != null && draft.recurrenceRule.isRecurring
                val effectiveDueDate = when {
                    draft.dueDateEpochDay != null -> draft.dueDateEpochDay
                    hasRecurrence -> draft.recurrenceRule!!.firstMatchingOrAfter(LocalDate.now()).toEpochDay()
                    else -> null
                }
                TaskItem(
                    id = UUID.randomUUID().toString(),
                    title = draft.title.trim(),
                    notes = draft.notes.trim(),
                    dueDateEpochDay = effectiveDueDate,
                    isFlagged = draft.isFlagged,
                    isCompleted = false,
                    recurrenceRule = if (hasRecurrence) draft.recurrenceRule else null,
                    healthGoal = draft.healthGoal,
                    startMinute = draft.startMinute,
                    durationMinutes = draft.durationMinutes,
                    createdAt = System.currentTimeMillis()
                )
            }
            repository.upsertTasks(tasks)
            tasks.forEach { firestoreSyncManager?.pushTask(it) }
        }
    }

    fun toggleSubtaskCompletion(taskId: String, subtaskId: String) {
        val task = uiState.value.allTasks.find { it.id == taskId } ?: return
        val updatedSubtasks = task.subtasks.map { sub ->
            if (sub.id == subtaskId) sub.copy(isCompleted = !sub.isCompleted) else sub
        }
        val updatedTask = task.copy(subtasks = updatedSubtasks)
        viewModelScope.launch {
            repository.upsertTask(updatedTask)
            firestoreSyncManager?.pushTask(updatedTask)
        }
    }

    fun addSubtask(taskId: String, subtaskTitle: String) {
        val trimmed = subtaskTitle.trim()
        if (trimmed.isBlank()) return
        val task = uiState.value.allTasks.find { it.id == taskId } ?: return
        val newSubtask = SubtaskItem(title = trimmed, isCompleted = false)
        val updatedTask = task.copy(subtasks = task.subtasks + newSubtask)
        viewModelScope.launch {
            repository.upsertTask(updatedTask)
            firestoreSyncManager?.pushTask(updatedTask)
        }
    }

    fun deleteSubtask(taskId: String, subtaskId: String) {
        val task = uiState.value.allTasks.find { it.id == taskId } ?: return
        val updatedTask = task.copy(subtasks = task.subtasks.filterNot { it.id == subtaskId })
        viewModelScope.launch {
            repository.upsertTask(updatedTask)
            firestoreSyncManager?.pushTask(updatedTask)
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
                    dueDateEpochDay = nextDueEpochDay,
                    isCompleted = false,
                    subtasks = task.subtasks.map { it.copy(isCompleted = false) },
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

    fun assignTaskDate(taskId: String, dueDateEpochDay: Long?) {
        val task = uiState.value.allTasks.find { it.id == taskId } ?: return
        val updatedTask = task.copy(dueDateEpochDay = dueDateEpochDay)
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
        val updatedTask = task.copy(
            recurrenceRule = if (hasRecurrence) recurrenceRule else null,
            dueDateEpochDay = nextDue
        )
        viewModelScope.launch {
            repository.upsertTask(updatedTask)
            firestoreSyncManager?.pushTask(updatedTask)
        }
    }

    /** Inline edit of a task's title and notes from the task row. */
    fun updateTaskText(taskId: String, title: String, notes: String) {
        val task = uiState.value.allTasks.find { it.id == taskId } ?: return
        if (title.isBlank()) return
        val updatedTask = task.copy(title = title.trim(), notes = notes.trim())
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

    fun selectCalendarDate(date: LocalDate) {
        _localFlags.update { it.copy(selectedCalendarDate = date) }
    }

    fun toggleCalendarEventCompletion(eventId: String) {
        viewModelScope.launch {
            val event = repository.getAllCalendarEvents().firstOrNull { it.id == eventId } ?: return@launch
            val newCompleted = !event.isCompleted
            val completedAt = if (newCompleted) System.currentTimeMillis() else null
            repository.updateCalendarEventCompletion(eventId, newCompleted, completedAt)
        }
    }

    fun importDeviceCalendars() {
        val manager = calendarImportManager ?: return
        viewModelScope.launch {
            _localFlags.update { it.copy(isCalendarImporting = true, calendarImportMessage = "Syncing device calendars...") }
            val count = manager.importDeviceCalendarEvents()
            startCalendarMirror()
            _localFlags.update {
                it.copy(
                    isCalendarImporting = false,
                    calendarImportMessage = if (count > 0) "Successfully imported $count calendar events!" else "No events found or calendar permission required"
                )
            }
        }
    }

    fun importIcsFromUri(uri: android.net.Uri) {
        val manager = calendarImportManager ?: return
        viewModelScope.launch {
            _localFlags.update { it.copy(isCalendarImporting = true, calendarImportMessage = "Importing .ics calendar...") }
            val count = manager.importIcsFromUri(uri)
            _localFlags.update {
                it.copy(
                    isCalendarImporting = false,
                    calendarImportMessage = if (count > 0) "Successfully imported $count events from .ics!" else "Could not read any events from .ics file"
                )
            }
        }
    }

    fun importIcsContent(content: String) {
        val manager = calendarImportManager ?: return
        viewModelScope.launch {
            _localFlags.update { it.copy(isCalendarImporting = true, calendarImportMessage = "Importing .ics calendar...") }
            val count = manager.importIcsContent(content)
            _localFlags.update {
                it.copy(
                    isCalendarImporting = false,
                    calendarImportMessage = if (count > 0) "Successfully imported $count events from .ics!" else "Could not read any events from .ics file"
                )
            }
        }
    }

    // ---------------------------------------------------------------- Day timeline / two-way calendar

    private fun startCalendarMirror() {
        val sync = deviceCalendarSync ?: return
        if (!sync.canRead()) return
        unregisterCalendarObserver?.invoke()
        unregisterCalendarObserver = sync.observeChanges { viewModelScope.launch { sync.refresh() } }
        viewModelScope.launch {
            sync.refresh()
            loadWritableCalendars()
        }
    }

    /** Call after the calendar permission is granted. */
    fun onCalendarPermissionChanged() = startCalendarMirror()

    fun loadWritableCalendars() {
        val sync = deviceCalendarSync ?: return
        viewModelScope.launch {
            val cals = sync.writableCalendars()
            _localFlags.update { it.copy(writableCalendars = cals) }
        }
    }

    fun clearUserMessage() {
        _localFlags.update { it.copy(userMessage = null) }
    }

    private fun showMessage(message: String) {
        _localFlags.update { it.copy(userMessage = message) }
    }

    /** Puts a task on the timeline (or moves it). [startMinute] null = keep the day, drop the time. */
    fun scheduleTask(taskId: String, epochDay: Long, startMinute: Int?, durationMinutes: Int? = null) {
        val task = uiState.value.allTasks.find { it.id == taskId } ?: return
        val updated = task.copy(
            dueDateEpochDay = epochDay,
            startMinute = startMinute?.let { DayTimeline.clampStart(it, durationMinutes ?: task.effectiveDurationMinutes) },
            durationMinutes = durationMinutes ?: task.durationMinutes
        )
        viewModelScope.launch {
            repository.upsertTask(updated)
            firestoreSyncManager?.pushTask(updated)
        }
    }

    fun resizeTask(taskId: String, durationMinutes: Int) {
        val task = uiState.value.allTasks.find { it.id == taskId } ?: return
        val updated = task.copy(durationMinutes = durationMinutes.coerceIn(15, 16 * 60))
        viewModelScope.launch {
            repository.upsertTask(updated)
            firestoreSyncManager?.pushTask(updated)
        }
    }

    /** Eisenhower quadrant: important = starred, urgent = explicit override. */
    fun setTaskQuadrant(taskId: String, important: Boolean, urgent: Boolean) {
        val task = uiState.value.allTasks.find { it.id == taskId } ?: return
        val updated = task.copy(isFlagged = important, urgentOverride = urgent)
        viewModelScope.launch {
            repository.upsertTask(updated)
            firestoreSyncManager?.pushTask(updated)
        }
    }

    /** Moves/resizes a calendar event; writes through to Google Calendar for synced calendars. */
    fun rescheduleEvent(eventId: String, epochDay: Long, startMinute: Int, endMinute: Int) {
        val sync = deviceCalendarSync ?: return
        viewModelScope.launch {
            val event = repository.getAllCalendarEvents().firstOrNull { it.id == eventId } ?: return@launch
            val zone = ZoneId.systemDefault()
            val dayStart = LocalDate.ofEpochDay(epochDay).atStartOfDay(zone).toInstant().toEpochMilli()
            val result = sync.reschedule(event, dayStart + startMinute * 60_000L, dayStart + endMinute * 60_000L)
            if (result is DeviceCalendarSync.WriteResult.Refused) showMessage(result.reason)
        }
    }

    fun createCalendarEvent(calendarId: Long, title: String, epochDay: Long, startMinute: Int, durationMinutes: Int) {
        val sync = deviceCalendarSync ?: return
        viewModelScope.launch {
            val zone = ZoneId.systemDefault()
            val dayStart = LocalDate.ofEpochDay(epochDay).atStartOfDay(zone).toInstant().toEpochMilli()
            val start = dayStart + startMinute * 60_000L
            val result = sync.create(calendarId, title, start, start + durationMinutes * 60_000L)
            showMessage(if (result is DeviceCalendarSync.WriteResult.Refused) result.reason else "Event added to your calendar")
        }
    }

    fun deleteCalendarEvent(eventId: String) {
        val sync = deviceCalendarSync ?: return
        viewModelScope.launch {
            val event = repository.getAllCalendarEvents().firstOrNull { it.id == eventId } ?: return@launch
            val result = sync.delete(event)
            if (result is DeviceCalendarSync.WriteResult.Refused) showMessage(result.reason)
        }
    }

    private fun buildDayMarkers(events: List<CalendarEventItem>, tasks: List<TaskItem>, zone: ZoneId): Map<Long, DayMarker> {
        val eventsByDay = HashMap<Long, Int>()
        for (e in events) {
            val first = Instant.ofEpochMilli(e.startEpochMillis).atZone(zone).toLocalDate().toEpochDay()
            val lastMillis = if (e.endEpochMillis > e.startEpochMillis) e.endEpochMillis - 1 else e.startEpochMillis
            val last = Instant.ofEpochMilli(lastMillis).atZone(zone).toLocalDate().toEpochDay().coerceAtMost(first + 31)
            for (d in first..last) eventsByDay[d] = (eventsByDay[d] ?: 0) + 1
        }
        val tasksByDay = tasks.filter { !it.isCompleted && it.dueDateEpochDay != null }.groupingBy { it.dueDateEpochDay!! }.eachCount()
        return (eventsByDay.keys + tasksByDay.keys).associateWith { DayMarker(eventsByDay[it] ?: 0, tasksByDay[it] ?: 0) }
    }

    override fun onCleared() {
        unregisterCalendarObserver?.invoke()
        super.onCleared()
    }

    fun clearCalendarImportMessage() {
        _localFlags.update { it.copy(calendarImportMessage = null) }
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
        /** Quiet period after the last progress change before re-theming the system. */
        private const val SYSTEM_COLOR_SETTLE_MS = 3_000L
        /** Minimum time between two automatic system color changes. */
        private const val SYSTEM_COLOR_MIN_INTERVAL_MS = 20_000L

        internal val globalDisabledGoalIds = MutableStateFlow<Set<String>>(emptySet())

        fun provideFactory(
            repository: FlowRepository,
            healthConnectManager: HealthConnectManager? = null,
            shizukuManager: ShizukuSystemColorManager? = null,
            appUsageManager: AppUsageManager? = null,
            firestoreSyncManager: FirestoreSyncManager? = null,
            appUsageShieldMonitor: AppUsageShieldMonitor? = null,
            appContext: android.content.Context? = null,
            calendarImportManager: CalendarImportManager? = null,
            deviceCalendarSync: DeviceCalendarSync? = null
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
                            firestoreSyncManager = firestoreSyncManager,
                            appUsageShieldMonitor = appUsageShieldMonitor,
                            appContext = appContext,
                            calendarImportManager = calendarImportManager,
                            deviceCalendarSync = deviceCalendarSync
                        ) as T
                    }
                    throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
                }
            }
    }
}
