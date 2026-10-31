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
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
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
import com.flow.todo.ui.components.FluidWaveSplashOverlay
import com.flow.todo.ui.components.rememberSplashTransitionState
import com.flow.todo.ui.screens.FluidSettingsSheet
import com.flow.todo.ui.screens.InboxScreen
import com.flow.todo.ui.screens.ProjectsScreen
import com.flow.todo.ui.screens.QuickCaptureSheet
import com.flow.todo.ui.screens.TodayNextScreen
import com.flow.todo.ui.state.FlowViewModel
import com.flow.todo.ui.theme.FluidSprings
import com.flow.todo.ui.theme.FluidTheme
import com.flow.todo.ui.theme.fluidProgressiveBlur
import java.time.LocalDate

/**
 * Living Flow App Shell:
 * - Full-bleed edge-to-edge layout with dynamic ambient soft radial glow
 * - Per-tab LazyListState tracking for progressive blur / morphing TopBar
 * - Floating Island Navigation Bar with progressive bottom blur ramp
 * - Smooth spring-morph AnimatedContent screen transitions
 * - Integrated Quick Capture & Settings bottom sheets with Health Connect & Shizuku System UI Color Sync
 */
@Composable
fun FlowAppShell(
    viewModel: FlowViewModel,
    onRequestHealthPermissions: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isReducedMotion = FluidTheme.isReducedMotion
    val colors = FluidTheme.colors

    var isSplashFinished by rememberSaveable { mutableStateOf(false) }
    val splashTransition = rememberSplashTransitionState(
        isAlreadyFinished = isSplashFinished,
        onSplashFinished = { isSplashFinished = true }
    )

    // Separate scroll states per tab so scroll positions are retained during tab navigation
    val inboxListState = rememberLazyListState()
    val todayListState = rememberLazyListState()
    val projectsListState = rememberLazyListState()

    val currentListState = when (uiState.selectedTab) {
        FlowTab.INBOX -> inboxListState
        FlowTab.TODAY_NEXT -> todayListState
        FlowTab.PROJECTS -> projectsListState
    }

    // TopBar elevates and morphs when content scrolls beneath it
    val isScrolled by remember {
        derivedStateOf {
            currentListState.firstVisibleItemIndex > 0 || currentListState.firstVisibleItemScrollOffset > 24
        }
    }

    val (currentTitle, currentSubtitle) = when (uiState.selectedTab) {
        FlowTab.INBOX -> {
            val pendingCount = uiState.inboxTasks.count { !it.isCompleted }
            "Inbox" to if (pendingCount > 0) {
                "$pendingCount ${if (pendingCount == 1) "item" else "items"} to clarify"
            } else {
                "Inbox Zero achieved ✨"
            }
        }
        FlowTab.TODAY_NEXT -> {
            val total = uiState.totalTodayTargetCount
            val completed = uiState.completedTodayCount
            val upcoming = uiState.upcomingWeekTotalCount
            "Today & Next" to if (total > 0) {
                "$completed of $total today • $upcoming queued this week"
            } else {
                "No urgent commitments today • $upcoming queued this week"
            }
        }
        FlowTab.PROJECTS -> {
            val projectCount = uiState.projects.size
            "Projects" to "$projectCount active ${if (projectCount == 1) "outcome" else "outcomes"}"
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
                    FlowTab.INBOX -> InboxScreen(
                        tasks = uiState.inboxTasks,
                        projects = uiState.projects,
                        onToggleComplete = viewModel::toggleTaskCompletion,
                        onToggleFlag = viewModel::toggleTaskFlag,
                        onMoveBucket = viewModel::updateTaskBucket,
                        onAssignToday = { taskId ->
                            viewModel.assignTaskDate(taskId, LocalDate.now().toEpochDay())
                        },
                        onDeleteTask = viewModel::deleteTask,
                        onQuickAddClick = { viewModel.setQuickCaptureOpen(true) },
                        contentPadding = screenContentPadding,
                        listState = inboxListState
                    )

                    FlowTab.TODAY_NEXT -> TodayNextScreen(
                        todayTasks = uiState.todayTasks,
                        nextActionTasks = uiState.nextActionTasks,
                        upcomingWeekDays = uiState.upcomingWeekDays,
                        laterScheduledTasks = uiState.laterScheduledTasks,
                        upcomingWeekTotalCount = uiState.upcomingWeekTotalCount,
                        projects = uiState.projects,
                        completedTodayCount = uiState.completedTodayCount,
                        totalTodayTargetCount = uiState.totalTodayTargetCount,
                        dailyProgress = uiState.dailyProgress,
                        healthSnapshot = uiState.healthSnapshot,
                        shizukuStatus = uiState.shizukuStatus,
                        syncSystemColorsWithShizuku = uiState.accessibility.syncSystemColorsWithShizuku,
                        onRequestHealthPermissions = onRequestHealthPermissions,
                        onRefreshHealthStats = viewModel::refreshHealthAndSystemStatus,
                        onAddQuickHealthGoal = { metric, target ->
                            viewModel.addQuickHealthGoalTask(metric, target, isDailyRecurring = true)
                        },
                        onOpenSettings = { viewModel.setSettingsOpen(true) },
                        onToggleComplete = viewModel::toggleTaskCompletion,
                        onToggleFlag = viewModel::toggleTaskFlag,
                        onMoveBucket = viewModel::updateTaskBucket,
                        onAssignToday = { taskId ->
                            viewModel.assignTaskDate(taskId, LocalDate.now().toEpochDay())
                        },
                        onDeleteTask = viewModel::deleteTask,
                        contentPadding = screenContentPadding,
                        listState = todayListState
                    )

                    FlowTab.PROJECTS -> ProjectsScreen(
                        projectsWithStats = uiState.projectsWithStats,
                        somedayTasks = uiState.somedayTasks,
                        allProjects = uiState.projects,
                        onToggleComplete = viewModel::toggleTaskCompletion,
                        onToggleFlag = viewModel::toggleTaskFlag,
                        onMoveBucket = viewModel::updateTaskBucket,
                        onAssignToday = { taskId ->
                            viewModel.assignTaskDate(taskId, LocalDate.now().toEpochDay())
                        },
                        onDeleteTask = viewModel::deleteTask,
                        onAddProject = viewModel::addProject,
                        onDeleteProject = viewModel::deleteProject,
                        contentPadding = screenContentPadding,
                        listState = projectsListState
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

            // Floating Island Navigation Bar with Quick-Capture Button
            FluidIslandNavBar(
                selectedTab = uiState.selectedTab,
                inboxBadgeCount = uiState.inboxTasks.count { !it.isCompleted },
                onTabSelected = viewModel::selectTab,
                onQuickAddClick = { viewModel.setQuickCaptureOpen(true) },
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
        }

        // Living Flow Quick-Capture Sheet
        if (uiState.isQuickCaptureOpen) {
            QuickCaptureSheet(
                projects = uiState.projects,
                onDismiss = { viewModel.setQuickCaptureOpen(false) },
                onSaveTask = { title, notes, bucket, dueDateEpochDay, projectId, isFlagged, recurrenceRule, healthGoal ->
                    viewModel.addTask(
                        title = title,
                        notes = notes,
                        bucket = bucket,
                        dueDateEpochDay = dueDateEpochDay,
                        projectId = projectId,
                        isFlagged = isFlagged,
                        recurrenceRule = recurrenceRule,
                        healthGoal = healthGoal
                    )
                }
            )
        }

        // Living Flow Settings, Momentum Spectrum, Shizuku & Health Connect Sheet
        if (uiState.isSettingsOpen) {
            FluidSettingsSheet(
                preferences = uiState.accessibility,
                onUpdatePreferences = viewModel::updateAccessibility,
                onDismiss = { viewModel.setSettingsOpen(false) },
                dailyProgress = uiState.dailyProgress,
                shizukuStatus = uiState.shizukuStatus,
                lastSyncedSystemColorHex = uiState.lastSyncedSystemColorHex,
                healthSnapshot = uiState.healthSnapshot,
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
