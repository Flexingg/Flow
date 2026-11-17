package com.flow.todo.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flow.todo.domain.model.FlowTab
import com.flow.todo.ui.components.FluidIslandNavBar
import com.flow.todo.ui.components.FluidTopBar
import com.flow.todo.ui.components.FluidUsageShieldBanner
import com.flow.todo.ui.components.FluidWaveSplashOverlay
import com.flow.todo.ui.components.rememberSplashTransitionState
import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.flow.todo.ui.screens.CalendarScreen
import com.flow.todo.coach.data.CoachController
import com.flow.todo.ui.coach.CoachCard
import com.flow.todo.ui.coach.CoachSettingsSheet
import com.flow.todo.ui.screens.FluidSettingsSheet
import com.flow.todo.ui.screens.GoalCreatorSheet
import com.flow.todo.ui.screens.GoalsScreen
import com.flow.todo.ui.screens.QuickCaptureSheet
import com.flow.todo.ui.screens.TasksScreen
import com.flow.todo.ui.state.FlowViewModel
import com.flow.todo.ui.theme.FluidSprings
import com.flow.todo.ui.theme.FluidTheme
import com.flow.todo.ui.theme.MomentumSpectrum
import com.flow.todo.ui.theme.fluidProgressiveBlur
import java.time.LocalDate

/**
 * Living Flow App Shell:
 * - Full-bleed edge-to-edge layout with dynamic ambient soft radial glow
 * - Per-tab LazyListState tracking for progressive blur / morphing TopBar
 * - Floating Island Navigation Bar with progressive bottom blur ramp
 * - Smooth spring-morph AnimatedContent screen transitions
 * - Integrated Quick Capture, Goal Creator & Settings bottom sheets with Health Connect, App Usage & Shizuku System UI Color Sync
 */
@Composable
fun FlowAppShell(
    viewModel: FlowViewModel,
    onRequestHealthPermissions: () -> Unit = {},
    onRequestUsagePermission: () -> Unit = {},
    onSignInWithGoogle: () -> Unit = {},
    /** True when launched from the widget / tile / shortcut / share sheet: skip the splash for instant capture. */
    skipSplash: Boolean = false,
    /** Called when the Quick Capture sheet closes; [saved] is true if a task was added. */
    onQuickCaptureClosed: (saved: Boolean) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isReducedMotion = FluidTheme.isReducedMotion
    val colors = FluidTheme.colors

    var isSplashFinished by rememberSaveable { mutableStateOf(skipSplash) }
    val splashTransition = rememberSplashTransitionState(
        isAlreadyFinished = isSplashFinished,
        onSplashFinished = { isSplashFinished = true }
    )

    val context = androidx.compose.ui.platform.LocalContext.current

    // AI Coach (bring-your-own-key persona card + widget)
    val coach = remember { CoachController.get(context) }
    var isCoachSettingsOpen by rememberSaveable { mutableStateOf(false) }
    val coachSettingsRequested by coach.settingsRequested.collectAsStateWithLifecycle()
    LaunchedEffect(coachSettingsRequested) {
        if (coachSettingsRequested) {
            isCoachSettingsOpen = true
            coach.consumeSettingsRequest()
        }
    }
    // "Every screen open" trigger: fires when the user switches tabs (rate-limited by the coach settings)
    LaunchedEffect(uiState.selectedTab, isSplashFinished) {
        if (isSplashFinished) coach.onScreenOpen(uiState.healthSnapshot)
    }

    val calendarPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.READ_CALENDAR] == true) {
            viewModel.importDeviceCalendars()
        }
    }

    val requestCalendarPermissions: () -> Unit = {
        val hasRead = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CALENDAR
        ) == PackageManager.PERMISSION_GRANTED
        if (hasRead) {
            viewModel.importDeviceCalendars()
        } else {
            calendarPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.READ_CALENDAR,
                    Manifest.permission.WRITE_CALENDAR
                )
            )
        }
    }

    val pickIcsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.importIcsFromUri(uri)
        }
    }

    // Separate scroll states per tab so scroll positions are retained during tab navigation
    val tasksListState = rememberLazyListState()
    val calendarListState = rememberLazyListState()
    val goalsListState = rememberLazyListState()

    val currentListState = when (uiState.selectedTab) {
        FlowTab.TASKS -> tasksListState
        FlowTab.CALENDAR -> calendarListState
        FlowTab.GOALS -> goalsListState
    }

    // TopBar elevates and morphs when content scrolls beneath it
    val isScrolled by remember {
        derivedStateOf {
            currentListState.firstVisibleItemIndex > 0 || currentListState.firstVisibleItemScrollOffset > 24
        }
    }

    val (currentTitle, currentSubtitle) = when (uiState.selectedTab) {
        FlowTab.TASKS -> {
            val sections = uiState.taskSections
            val dueNow = sections.overdue.size + sections.today.size
            "Tasks" to when {
                sections.overdue.isNotEmpty() -> "${sections.overdue.size} overdue • ${sections.today.size} due today"
                dueNow > 0 -> "$dueNow due today • ${sections.openCount} open"
                sections.openCount > 0 -> "Nothing due today • ${sections.openCount} open"
                else -> "All clear ✨"
            }
        }
        FlowTab.CALENDAR -> {
            val total = uiState.todayCalendarEvents.size
            val completed = uiState.todayCalendarEvents.count { it.isCompleted }
            "Calendar" to if (total > 0) {
                "$completed of $total events completed today"
            } else {
                "Schedule & Agenda • Check events off as you go"
            }
        }
        FlowTab.GOALS -> {
            val met = uiState.completedGoalsCount
            val total = uiState.totalGoalsCount
            val stage = MomentumSpectrum.stageLabel(uiState.goalMomentumProgress)
            "Goal Bar" to if (total > 0) {
                "$met of $total goals met • $stage"
            } else {
                "Set task, health & app limits to drive your colors"
            }
        }
    }

    // Full-bleed edge-to-edge content padding: items scroll freely beneath the floating bars
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val screenContentPadding = PaddingValues(
        top = topInset + 76.dp,
        bottom = bottomInset + 96.dp
    )

    // Slow ambient breathing glow transition tinted with active Momentum / Material You accent
    val ambientTransition = rememberInfiniteTransition(label = "ambientGlowTransition")
    val ambientScale by ambientTransition.animateFloat(
        initialValue = 0.88f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 4000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ambientScale"
    )
    val ambientAlpha by ambientTransition.animateFloat(
        initialValue = if (colors.isDark) 0.08f else 0.05f,
        targetValue = if (colors.isDark) 0.16f else 0.10f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 4000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ambientAlpha"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.bgBase)
            .drawBehind {
                // Living Flow ambient soft radial glow centered near the top edge
                val effectiveRadius = (size.width * 0.9f) * (if (isReducedMotion) 1f else ambientScale)
                val effectiveAlpha = if (isReducedMotion) (if (colors.isDark) 0.10f else 0.06f) else ambientAlpha
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            colors.accent.copy(alpha = effectiveAlpha),
                            Color.Transparent
                        ),
                        center = Offset(size.width * 0.5f, 0f),
                        radius = effectiveRadius
                    ),
                    radius = effectiveRadius,
                    center = Offset(size.width * 0.5f, 0f)
                )
            }
    ) {
        // Main Screen Interface (Content + TopBar + IslandNavBar)
        // Springs into place as splash icon melts and morphs into top ambient glow
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val scale = if (splashTransition.isFinished) 1.0f else splashTransition.homeContentScale
                    val offsetY = if (splashTransition.isFinished) 0f else splashTransition.homeContentOffsetY.toPx()
                    val alphaVal = if (splashTransition.isFinished) 1.0f else splashTransition.homeContentAlpha

                    scaleX = scale
                    scaleY = scale
                    translationY = offsetY
                    alpha = alphaVal
                }
        ) {
            // Main Screen Content with Living Flow Spring-Morph Transitions
            AnimatedContent(
                targetState = uiState.selectedTab,
                transitionSpec = {
                    if (isReducedMotion) {
                        fadeIn(animationSpec = tween(durationMillis = 150)) togetherWith
                                fadeOut(animationSpec = tween(durationMillis = 150))
                    } else {
                        val direction = if (targetState.ordinal > initialState.ordinal) 1 else -1
                        (slideInHorizontally(
                            animationSpec = FluidSprings.morph(false)
                        ) { (it * 0.18f).toInt() * direction } + fadeIn(
                            animationSpec = tween(durationMillis = 220)
                        ) + scaleIn(
                            initialScale = 0.92f,
                            animationSpec = FluidSprings.morph(false)
                        )).togetherWith(
                            slideOutHorizontally(
                                animationSpec = FluidSprings.morph(false)
                            ) { (-it * 0.18f).toInt() * direction } + fadeOut(
                                animationSpec = tween(durationMillis = 180)
                            ) + scaleOut(
                                targetScale = 0.92f,
                                animationSpec = FluidSprings.morph(false)
                            )
                        )
                    }
                },
                label = "livingFlowTabTransition",
                modifier = Modifier.fillMaxSize()
            ) { tab ->
                when (tab) {
                    FlowTab.TASKS -> TasksScreen(
                        sections = uiState.taskSections,
                        healthSnapshot = uiState.healthSnapshot,
                        completedTodayCount = uiState.completedTodayCount,
                        totalTodayCount = uiState.totalTodayTargetCount,
                        dailyProgress = uiState.dailyProgress,
                        onToggleComplete = viewModel::toggleTaskCompletion,
                        onToggleFlag = viewModel::toggleTaskFlag,
                        onSetDueDate = viewModel::assignTaskDate,
                        onDeleteTask = viewModel::deleteTask,
                        onUpdateText = viewModel::updateTaskText,
                        onToggleSubtask = viewModel::toggleSubtaskCompletion,
                        onAddSubtask = viewModel::addSubtask,
                        onDeleteSubtask = viewModel::deleteSubtask,
                        onQuickAdd = { viewModel.setQuickCaptureOpen(true) },
                        coachCard = {
                            CoachCard(
                                controller = coach,
                                healthSnapshot = uiState.healthSnapshot,
                                onOpenSettings = { isCoachSettingsOpen = true }
                            )
                        },
                        contentPadding = screenContentPadding,
                        listState = tasksListState
                    )

                    FlowTab.CALENDAR -> CalendarScreen(
                        calendarEvents = uiState.allCalendarEvents,
                        scheduledTasks = uiState.allTasks,
                        selectedDate = uiState.selectedCalendarDate,
                        dailySummary = uiState.calendarDailySummary,
                        dailyProgress = uiState.dailyProgress,
                        isImporting = uiState.isCalendarImporting,
                        importMessage = uiState.calendarImportMessage,
                        onSelectDate = viewModel::selectCalendarDate,
                        onToggleEventCompletion = viewModel::toggleCalendarEventCompletion,
                        onToggleTaskCompletion = viewModel::toggleTaskCompletion,
                        onImportDeviceCalendars = requestCalendarPermissions,
                        onPickIcsFile = {
                            runCatching {
                                pickIcsLauncher.launch(arrayOf("text/calendar", "*/*"))
                            }
                        },
                        onClearImportMessage = viewModel::clearCalendarImportMessage,
                        contentPadding = screenContentPadding,
                        listState = calendarListState
                    )

                    FlowTab.GOALS -> GoalsScreen(
                        evaluatedGoals = uiState.evaluatedGoals,
                        goalMomentumProgress = uiState.goalMomentumProgress,
                        contributionChart = uiState.contributionChart,
                        healthSnapshot = uiState.healthSnapshot,
                        appUsageSnapshot = uiState.appUsageSnapshot,
                        shizukuStatus = uiState.shizukuStatus,
                        syncSystemColorsWithShizuku = uiState.accessibility.syncSystemColorsWithShizuku,
                        enableAppUsageShield = uiState.accessibility.enableAppUsageShield,
                        onToggleAppUsageShield = { enabled ->
                            viewModel.updateAccessibility { it.copy(enableAppUsageShield = enabled) }
                        },
                        onTestUsageShield = viewModel::triggerTestShieldNudge,
                        onOpenGoalCreator = { viewModel.setGoalCreatorOpen(true) },
                        onDeleteGoal = viewModel::deleteGoal,
                        onAddPresetGoal = { title, category, comparison, targetValue, period, projectId, healthMetric, targetPackageName, targetAppName, appUsageMetric ->
                            viewModel.addGoal(
                                title = title,
                                category = category,
                                comparison = comparison,
                                targetValue = targetValue,
                                period = period,
                                projectId = projectId,
                                healthMetric = healthMetric,
                                targetPackageName = targetPackageName,
                                targetAppName = targetAppName,
                                appUsageMetric = appUsageMetric
                            )
                        },
                        onRequestHealthPermissions = onRequestHealthPermissions,
                        onRequestUsagePermission = onRequestUsagePermission,
                        onRefreshMetrics = viewModel::refreshHealthAndSystemStatus,
                        onOpenSettings = { viewModel.setSettingsOpen(true) },
                        contentPadding = screenContentPadding,
                        listState = goalsListState
                    )
                }
            }

            // Bottom Progressive Scrim Ramp for Floating Island Bar
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(115.dp)
                    .fluidProgressiveBlur(fromTop = false, height = 115.dp)
            )

            // Floating Island Navigation Bar with Quick-Capture / Goal-Creator Button
            FluidIslandNavBar(
                selectedTab = uiState.selectedTab,
                tasksBadgeCount = uiState.taskSections.overdue.size + uiState.taskSections.today.size,
                onTabSelected = viewModel::selectTab,
                onQuickAddClick = {
                    if (uiState.selectedTab == FlowTab.GOALS) {
                        viewModel.setGoalCreatorOpen(true)
                    } else {
                        viewModel.setQuickCaptureOpen(true)
                    }
                },
                modifier = Modifier.align(Alignment.BottomCenter)
            )

            // Morphing Glass Top Bar
            FluidTopBar(
                title = currentTitle,
                subtitle = currentSubtitle,
                isScrolled = isScrolled,
                onSettingsClick = { viewModel.setSettingsOpen(true) },
                modifier = Modifier.align(Alignment.TopCenter)
            )

            // Floating ColorOS 17 Liquid-Glass App Usage Shield Banner
            FluidUsageShieldBanner(
                alert = uiState.activeShieldAlert,
                onDismiss = viewModel::dismissShieldAlert,
                onOpenGoals = {
                    uiState.activeShieldAlert?.let { viewModel.dismissShieldAlert(it.goalId) }
                    viewModel.selectTab(FlowTab.GOALS)
                },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = topInset + 74.dp, start = 16.dp, end = 16.dp)
            )
        }

        // Quick Capture (smart dates + voice + subtasks)
        if (uiState.isQuickCaptureOpen) {
            var captureSaved by remember { mutableStateOf(false) }
            QuickCaptureSheet(
                initialTitle = uiState.quickCaptureInitialTitle,
                autoStartVoice = uiState.quickCaptureAutoVoice,
                onDismiss = {
                    viewModel.setQuickCaptureOpen(false)
                    onQuickCaptureClosed(captureSaved)
                },
                onSaveTask = { title, notes, dueDateEpochDay, isFlagged, recurrenceRule, healthGoal, subtasks ->
                    captureSaved = true
                    viewModel.addTask(
                        title = title,
                        notes = notes,
                        dueDateEpochDay = dueDateEpochDay,
                        isFlagged = isFlagged,
                        recurrenceRule = recurrenceRule,
                        healthGoal = healthGoal,
                        subtasks = subtasks
                    )
                },
                onBatchAddTasks = { drafts ->
                    captureSaved = true
                    viewModel.addTasksBatch(drafts)
                }
            )
        }

        // Living Flow Dedicated Goal Creator Sheet (Task Milestones, Health Connect, App Usage Limits)
        if (uiState.isGoalCreatorOpen) {
            GoalCreatorSheet(
                installedApps = uiState.appUsageSnapshot.installedApps,
                hasUsagePermission = uiState.appUsageSnapshot.hasUsagePermission,
                hasHealthPermissions = uiState.healthSnapshot.hasAllPermissions,
                onRequestUsagePermission = onRequestUsagePermission,
                onRequestHealthPermissions = onRequestHealthPermissions,
                onDismiss = { viewModel.setGoalCreatorOpen(false) },
                onSaveGoal = { title, category, comparison, targetValue, period, projectId, healthMetric, targetPackageName, targetAppName, appUsageMetric ->
                    viewModel.addGoal(
                        title = title,
                        category = category,
                        comparison = comparison,
                        targetValue = targetValue,
                        period = period,
                        projectId = projectId,
                        healthMetric = healthMetric,
                        targetPackageName = targetPackageName,
                        targetAppName = targetAppName,
                        appUsageMetric = appUsageMetric
                    )
                }
            )
        }

        if (isCoachSettingsOpen) {
            CoachSettingsSheet(
                controller = coach,
                healthSnapshot = uiState.healthSnapshot,
                onDismiss = { isCoachSettingsOpen = false }
            )
        }

        // Living Flow Settings, Momentum Spectrum, Cloud Sync, Shizuku & Health Connect Sheet
        if (uiState.isSettingsOpen) {
            FluidSettingsSheet(
                preferences = uiState.accessibility,
                onUpdatePreferences = viewModel::updateAccessibility,
                onDismiss = { viewModel.setSettingsOpen(false) },
                dailyProgress = uiState.dailyProgress,
                shizukuStatus = uiState.shizukuStatus,
                lastSyncedSystemColorHex = uiState.lastSyncedSystemColorHex,
                healthSnapshot = uiState.healthSnapshot,
                cloudSyncState = uiState.cloudSyncState,
                onSignInWithGoogle = onSignInWithGoogle,
                onSignInWithEmail = viewModel::signInWithEmail,
                onSignOutCloud = viewModel::signOutCloud,
                onSyncCloudNow = viewModel::syncWithCloudNow,
                onSaveFirebaseConfig = viewModel::updateFirebaseConfig,
                onRequestShizukuPermission = viewModel::requestShizukuPermission,
                onSyncSystemColorsNow = viewModel::syncSystemColorsNow,
                onRestoreSystemPalette = viewModel::restoreOriginalSystemPalette,
                onRequestHealthPermissions = onRequestHealthPermissions,
                onRefreshHealthStats = viewModel::refreshHealthAndSystemStatus
            )
        }

        // Luminous Wave Check Splash-to-Home Living Flow Overlay
        if (!splashTransition.isFinished) {
            FluidWaveSplashOverlay(
                transitionState = splashTransition,
                onSplashFinished = { isSplashFinished = true },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
