package com.flow.todo.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Today
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flow.todo.domain.model.GtdBucket
import com.flow.todo.domain.model.Project
import com.flow.todo.domain.model.TaskItem
import com.flow.todo.ui.components.FluidGlowingProgressBar
import com.flow.todo.ui.components.FluidInsetCard
import com.flow.todo.ui.theme.FluidShapes
import com.flow.todo.ui.theme.FluidSpacing
import com.flow.todo.ui.theme.FluidSprings
import com.flow.todo.ui.theme.FluidTheme

@Composable
fun TodayNextScreen(
    todayTasks: List<TaskItem>,
    nextActionTasks: List<TaskItem>,
    projects: List<Project>,
    completedTodayCount: Int,
    totalTodayTargetCount: Int,
    onToggleComplete: (String) -> Unit,
    onToggleFlag: (String) -> Unit,
    onMoveBucket: (String, GtdBucket) -> Unit,
    onAssignToday: (String) -> Unit,
    onDeleteTask: (String) -> Unit,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    listState: LazyListState = rememberLazyListState(),
    modifier: Modifier = Modifier
) {
    val isReducedMotion = FluidTheme.isReducedMotion

    val progressFraction = if (totalTodayTargetCount > 0) {
        (completedTodayCount.toFloat() / totalTodayTargetCount.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    val progressPercent = (progressFraction * 100).toInt()

    LazyColumn(
        state = listState,
        contentPadding = contentPadding,
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = FluidSpacing.lg)
    ) {
        item {
            Spacer(modifier = Modifier.height(FluidSpacing.sm))
        }

        // Daily Momentum Hero Card
        item {
            FluidInsetCard(
                modifier = Modifier.fillMaxWidth(),
                accentGlowColor = FluidTheme.colors.accent.copy(alpha = 0.40f)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "DAILY MOMENTUM",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.2.sp,
                            color = FluidTheme.colors.accent
                        )

                        // Percentage Badge with Bouncy Slide + ScaleIn
                        Box(
                            modifier = Modifier
                                .clip(FluidShapes.full)
                                .background(FluidTheme.colors.accent.copy(alpha = 0.12f))
                                .border(1.dp, FluidTheme.colors.accent.copy(alpha = 0.28f), FluidShapes.full)
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            AnimatedContent(
                                targetState = progressPercent,
                                transitionSpec = {
                                    if (isReducedMotion) {
                                        fadeIn(tween(150)) togetherWith fadeOut(tween(150))
                                    } else {
                                        val direction = if (targetState > initialState) 1 else -1
                                        (slideInVertically(
                                            animationSpec = FluidSprings.release(false),
                                            initialOffsetY = { 16 * direction }
                                        ) + scaleIn(
                                            initialScale = 0.80f,
                                            animationSpec = FluidSprings.release(false)
                                        ) + fadeIn(tween(150))).togetherWith(
                                            slideOutVertically(
                                                animationSpec = FluidSprings.release(false),
                                                targetOffsetY = { -16 * direction }
                                            ) + scaleOut(
                                                targetScale = 0.80f,
                                                animationSpec = FluidSprings.release(false)
                                            ) + fadeOut(tween(150))
                                        )
                                    }
                                },
                                label = "momentumPercentage"
                            ) { percent ->
                                Text(
                                    text = "$percent%",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = FluidTheme.colors.accent
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(FluidSpacing.sm))

                    // Large expressive numeral showing completed / total with bouncy vertical roll
                    Row(
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.Start
                    ) {
                        AnimatedContent(
                            targetState = completedTodayCount,
                            transitionSpec = {
                                if (isReducedMotion) {
                                    fadeIn(tween(150)) togetherWith fadeOut(tween(150))
                                } else {
                                    val direction = if (targetState > initialState) 1 else -1
                                    (slideInVertically(
                                        animationSpec = FluidSprings.release(false),
                                        initialOffsetY = { 28 * direction }
                                    ) + scaleIn(
                                        initialScale = 0.75f,
                                        animationSpec = FluidSprings.release(false)
                                    ) + fadeIn(tween(150))).togetherWith(
                                        slideOutVertically(
                                            animationSpec = FluidSprings.release(false),
                                            targetOffsetY = { -28 * direction }
                                        ) + scaleOut(
                                            targetScale = 0.75f,
                                            animationSpec = FluidSprings.release(false)
                                        ) + fadeOut(tween(150))
                                    )
                                }
                            },
                            label = "completedTodayNumeral"
                        ) { count ->
                            Text(
                                text = "$count",
                                fontSize = 40.sp,
                                fontWeight = FontWeight.SemiBold,
                                letterSpacing = (-0.5).sp,
                                color = FluidTheme.colors.textPrimary
                            )
                        }

                        Text(
                            text = " / $totalTodayTargetCount",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Normal,
                            color = FluidTheme.colors.textTertiary,
                            modifier = Modifier.padding(bottom = 4.dp, start = 4.dp)
                        )
                        Spacer(modifier = Modifier.width(FluidSpacing.sm))
                        Text(
                            text = "completed today",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Normal,
                            color = FluidTheme.colors.textSecondary,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(FluidSpacing.xs))

                    Text(
                        text = when {
                            totalTodayTargetCount == 0 -> "No scheduled commitments today. Pick an action from your horizon."
                            completedTodayCount >= totalTodayTargetCount -> "All daily targets fulfilled • Pure Living Flow ✨"
                            else -> "Keep your momentum going. Focus on the next immediate physical action."
                        },
                        fontSize = 13.sp,
                        color = FluidTheme.colors.textSecondary
                    )

                    Spacer(modifier = Modifier.height(FluidSpacing.md))

                    // Luminous comet progress bar
                    FluidGlowingProgressBar(
                        progress = progressFraction,
                        accentColor = FluidTheme.colors.accent,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(FluidSpacing.lg))
        }

        // Section 1: Due Today & Flagged
        item {
            FluidInsetCard(modifier = Modifier.fillMaxWidth()) {
                // Section Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(FluidTheme.colors.accent.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Today,
                                contentDescription = null,
                                tint = FluidTheme.colors.accent,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Text(
                            text = "Due Today & Flagged",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = FluidTheme.colors.textPrimary
                        )
                    }

                    // Section count badge with bouncy scaleIn pop
                    Box(
                        modifier = Modifier
                            .clip(FluidShapes.full)
                            .background(FluidTheme.colors.divider)
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        AnimatedContent(
                            targetState = todayTasks.size,
                            transitionSpec = {
                                if (isReducedMotion) {
                                    fadeIn(tween(150)) togetherWith fadeOut(tween(150))
                                } else {
                                    (scaleIn(initialScale = 0.80f, animationSpec = FluidSprings.release(false)) + fadeIn(tween(150)))
                                        .togetherWith(
                                            scaleOut(targetScale = 0.80f, animationSpec = FluidSprings.release(false)) + fadeOut(tween(150))
                                        )
                                }
                            },
                            label = "todayTasksCount"
                        ) { count ->
                            Text(
                                text = "$count",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = FluidTheme.colors.textSecondary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(FluidSpacing.sm))

                if (todayTasks.isEmpty()) {
                    Text(
                        text = "No scheduled or flagged tasks for today.",
                        fontSize = 14.sp,
                        color = FluidTheme.colors.textTertiary,
                        modifier = Modifier.padding(vertical = FluidSpacing.sm, horizontal = FluidSpacing.xs)
                    )
                } else {
                    todayTasks.forEachIndexed { index, task ->
                        key(task.id) {
                            AnimatedVisibility(
                                visible = true,
                                enter = fadeIn(FluidSprings.morph(isReducedMotion)) +
                                        expandVertically(animationSpec = FluidSprings.morph(isReducedMotion)) +
                                        scaleIn(
                                            initialScale = 0.95f,
                                            animationSpec = FluidSprings.release(isReducedMotion)
                                        ),
                                exit = fadeOut(FluidSprings.morph(isReducedMotion)) +
                                        shrinkVertically(animationSpec = FluidSprings.morph(isReducedMotion)) +
                                        scaleOut(
                                            targetScale = 0.95f,
                                            animationSpec = FluidSprings.release(isReducedMotion)
                                        )
                            ) {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    if (index > 0) {
                                        HorizontalDivider(
                                            thickness = 1.dp,
                                            color = FluidTheme.colors.divider,
                                            modifier = Modifier.padding(horizontal = FluidSpacing.md)
                                        )
                                    }
                                    TaskRow(
                                        task = task,
                                        project = projects.find { it.id == task.projectId },
                                        onToggleComplete = { onToggleComplete(task.id) },
                                        onToggleFlag = { onToggleFlag(task.id) },
                                        onMoveBucket = { bucket -> onMoveBucket(task.id, bucket) },
                                        onAssignToday = { onAssignToday(task.id) },
                                        onDelete = { onDeleteTask(task.id) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(FluidSpacing.lg))
        }

        // Section 2: Next Actions (GTD Horizon)
        item {
            FluidInsetCard(modifier = Modifier.fillMaxWidth()) {
                // Section Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(FluidTheme.colors.success.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Bolt,
                                contentDescription = null,
                                tint = FluidTheme.colors.success,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "Next Actions",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = FluidTheme.colors.textPrimary
                            )
                            Text(
                                text = "GTD Horizon • Ready to execute",
                                fontSize = 11.sp,
                                color = FluidTheme.colors.textTertiary
                            )
                        }
                    }

                    // Section count badge with bouncy scaleIn pop
                    Box(
                        modifier = Modifier
                            .clip(FluidShapes.full)
                            .background(FluidTheme.colors.divider)
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        AnimatedContent(
                            targetState = nextActionTasks.size,
                            transitionSpec = {
                                if (isReducedMotion) {
                                    fadeIn(tween(150)) togetherWith fadeOut(tween(150))
                                } else {
                                    (scaleIn(initialScale = 0.80f, animationSpec = FluidSprings.release(false)) + fadeIn(tween(150)))
                                        .togetherWith(
                                            scaleOut(targetScale = 0.80f, animationSpec = FluidSprings.release(false)) + fadeOut(tween(150))
                                        )
                                }
                            },
                            label = "nextActionsCount"
                        ) { count ->
                            Text(
                                text = "$count",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = FluidTheme.colors.textSecondary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(FluidSpacing.sm))

                if (nextActionTasks.isEmpty()) {
                    Text(
                        text = "No pending next actions. Clarify inbox items or add next steps.",
                        fontSize = 14.sp,
                        color = FluidTheme.colors.textTertiary,
                        modifier = Modifier.padding(vertical = FluidSpacing.sm, horizontal = FluidSpacing.xs)
                    )
                } else {
                    nextActionTasks.forEachIndexed { index, task ->
                        key(task.id) {
                            AnimatedVisibility(
                                visible = true,
                                enter = fadeIn(FluidSprings.morph(isReducedMotion)) +
                                        expandVertically(animationSpec = FluidSprings.morph(isReducedMotion)) +
                                        scaleIn(
                                            initialScale = 0.95f,
                                            animationSpec = FluidSprings.release(isReducedMotion)
                                        ),
                                exit = fadeOut(FluidSprings.morph(isReducedMotion)) +
                                        shrinkVertically(animationSpec = FluidSprings.morph(isReducedMotion)) +
                                        scaleOut(
                                            targetScale = 0.95f,
                                            animationSpec = FluidSprings.release(isReducedMotion)
                                        )
                            ) {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    if (index > 0) {
                                        HorizontalDivider(
                                            thickness = 1.dp,
                                            color = FluidTheme.colors.divider,
                                            modifier = Modifier.padding(horizontal = FluidSpacing.md)
                                        )
                                    }
                                    TaskRow(
                                        task = task,
                                        project = projects.find { it.id == task.projectId },
                                        onToggleComplete = { onToggleComplete(task.id) },
                                        onToggleFlag = { onToggleFlag(task.id) },
                                        onMoveBucket = { bucket -> onMoveBucket(task.id, bucket) },
                                        onAssignToday = { onAssignToday(task.id) },
                                        onDelete = { onDeleteTask(task.id) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Floating nav bar clearance spacer
        item {
            Spacer(modifier = Modifier.height(100.dp))
        }
    }
}
