package com.flow.todo.ui

import androidx.compose.animation.AnimatedContent
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flow.todo.domain.model.FlowTab
import com.flow.todo.ui.components.FluidIslandNavBar
import com.flow.todo.ui.components.FluidTopBar
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
 * - Integrated Quick Capture & Settings bottom sheets
 */
@Composable
fun FlowAppShell(
    viewModel: FlowViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isReducedMotion = FluidTheme.isReducedMotion
    val colors = FluidTheme.colors

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
            "Today & Next" to if (total > 0) {
                "$completed of $total fulfilled today"
            } else {
                "No urgent commitments today"
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

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.bgBase)
            .drawBehind {
                // Living Flow ambient soft radial glow centered near the top edge
                val glowRadius = size.width * 0.9f
                val glowAlpha = if (colors.isDark) 0.08f else 0.05f
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            colors.accent.copy(alpha = glowAlpha),
                            Color.Transparent
                        ),
                        center = Offset(size.width * 0.5f, 0f),
                        radius = glowRadius
                    ),
                    radius = glowRadius,
                    center = Offset(size.width * 0.5f, 0f)
                )
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
                        animationSpec = FluidSprings.morph(false),
                        initialOffsetX = { 50 * direction }
                    ) + fadeIn(
                        animationSpec = tween(durationMillis = 200)
                    ) + scaleIn(
                        initialScale = 0.97f,
                        animationSpec = FluidSprings.morph(false)
                    )).togetherWith(
                        slideOutHorizontally(
                            animationSpec = FluidSprings.morph(false),
                            targetOffsetX = { -50 * direction }
                        ) + fadeOut(
                            animationSpec = tween(durationMillis = 150)
                        ) + scaleOut(
                            targetScale = 0.97f,
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
                    projects = uiState.projects,
                    completedTodayCount = uiState.completedTodayCount,
                    totalTodayTargetCount = uiState.totalTodayTargetCount,
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

        // Living Flow Quick-Capture Sheet
        if (uiState.isQuickCaptureOpen) {
            QuickCaptureSheet(
                projects = uiState.projects,
                onDismiss = { viewModel.setQuickCaptureOpen(false) },
                onSaveTask = { title, notes, bucket, dueDateEpochDay, projectId, isFlagged ->
                    viewModel.addTask(
                        title = title,
                        notes = notes,
                        bucket = bucket,
                        dueDateEpochDay = dueDateEpochDay,
                        projectId = projectId,
                        isFlagged = isFlagged
                    )
                }
            )
        }

        // Living Flow Settings & Accessibility Sheet
        if (uiState.isSettingsOpen) {
            FluidSettingsSheet(
                preferences = uiState.accessibility,
                onUpdatePreferences = viewModel::updateAccessibility,
                onDismiss = { viewModel.setSettingsOpen(false) }
            )
        }
    }
}
