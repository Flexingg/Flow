package com.flow.todo.ui.screens

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
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flow.todo.domain.model.GtdBucket
import com.flow.todo.domain.model.Project
import com.flow.todo.domain.model.TaskItem
import com.flow.todo.ui.components.FluidFilterChip
import com.flow.todo.ui.components.FluidInsetCard
import com.flow.todo.ui.components.FluidPillButton
import com.flow.todo.ui.theme.FluidShapes
import com.flow.todo.ui.theme.FluidSpacing
import com.flow.todo.ui.theme.FluidTheme

@Composable
fun InboxScreen(
    tasks: List<TaskItem>,
    projects: List<Project>,
    onToggleComplete: (String) -> Unit,
    onToggleFlag: (String) -> Unit,
    onMoveBucket: (String, GtdBucket) -> Unit,
    onAssignToday: (String) -> Unit,
    onDeleteTask: (String) -> Unit,
    onQuickAddClick: () -> Unit,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    listState: LazyListState = rememberLazyListState(),
    modifier: Modifier = Modifier
) {
    // Filter State: 0 = All, 1 = Uncompleted, 2 = Completed
    var selectedFilterIndex by remember { mutableIntStateOf(0) }
    val uncompletedCount = tasks.count { !it.isCompleted }
    val completedCount = tasks.count { it.isCompleted }

    val filteredTasks = remember(tasks, selectedFilterIndex) {
        when (selectedFilterIndex) {
            1 -> tasks.filter { !it.isCompleted }
            2 -> tasks.filter { it.isCompleted }
            else -> tasks
        }
    }

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

        // Top GTD Clarify Banner Card
        item {
            FluidInsetCard(
                modifier = Modifier.fillMaxWidth(),
                accentGlowColor = if (uncompletedCount > 0) {
                    FluidTheme.colors.accent.copy(alpha = 0.35f)
                } else {
                    FluidTheme.colors.success.copy(alpha = 0.35f)
                }
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(FluidShapes.md)
                            .background(
                                if (uncompletedCount > 0) {
                                    FluidTheme.colors.accent.copy(alpha = 0.14f)
                                } else {
                                    FluidTheme.colors.success.copy(alpha = 0.14f)
                                }
                            )
                            .border(
                                1.dp,
                                if (uncompletedCount > 0) {
                                    FluidTheme.colors.accent.copy(alpha = 0.30f)
                                } else {
                                    FluidTheme.colors.success.copy(alpha = 0.30f)
                                },
                                FluidShapes.md
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (uncompletedCount > 0) Icons.Rounded.Inbox else Icons.Rounded.CheckCircle,
                            contentDescription = null,
                            tint = if (uncompletedCount > 0) FluidTheme.colors.accent else FluidTheme.colors.success,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(FluidSpacing.md))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "GTD Clarify & Process",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = FluidTheme.colors.textPrimary
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (uncompletedCount > 0) {
                                "$uncompletedCount unclarified ${if (uncompletedCount == 1) "item" else "items"} waiting for next action."
                            } else {
                                "All captured thoughts clarified. Inbox Zero achieved!"
                            },
                            fontSize = 13.sp,
                            color = FluidTheme.colors.textSecondary
                        )
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(FluidSpacing.md))
        }

        // Filter Chips Row
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(FluidSpacing.sm)
            ) {
                FluidFilterChip(
                    label = "All (${tasks.size})",
                    selected = selectedFilterIndex == 0,
                    onClick = { selectedFilterIndex = 0 }
                )
                FluidFilterChip(
                    label = "Uncompleted ($uncompletedCount)",
                    selected = selectedFilterIndex == 1,
                    onClick = { selectedFilterIndex = 1 }
                )
                FluidFilterChip(
                    label = "Completed ($completedCount)",
                    selected = selectedFilterIndex == 2,
                    onClick = { selectedFilterIndex = 2 }
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(FluidSpacing.md))
        }

        // Tasks or Living Flow Empty State
        if (filteredTasks.isEmpty()) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 48.dp, horizontal = FluidSpacing.xl),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .shadow(
                                elevation = 12.dp,
                                shape = CircleShape,
                                spotColor = FluidTheme.colors.accent.copy(alpha = 0.35f),
                                ambientColor = FluidTheme.colors.accent.copy(alpha = 0.2f)
                            )
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(
                                        FluidTheme.colors.accent.copy(alpha = 0.22f),
                                        FluidTheme.colors.accent.copy(alpha = 0.06f)
                                    )
                                )
                            )
                            .border(1.5.dp, FluidTheme.colors.accent.copy(alpha = 0.35f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Inbox,
                            contentDescription = null,
                            tint = FluidTheme.colors.accent,
                            modifier = Modifier.size(36.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(FluidSpacing.xl))

                    Text(
                        text = if (tasks.isEmpty()) "Your Inbox is completely clear" else "No matching tasks",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = FluidTheme.colors.textPrimary,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(FluidSpacing.xs))

                    Text(
                        text = if (tasks.isEmpty()) {
                            "Capture tasks, thoughts, and ideas instantly so your mind stays free to focus."
                        } else {
                            "Try switching filters to view your other tasks."
                        },
                        fontSize = 14.sp,
                        color = FluidTheme.colors.textSecondary,
                        textAlign = TextAlign.Center
                    )

                    if (tasks.isEmpty()) {
                        Spacer(modifier = Modifier.height(FluidSpacing.xl))
                        FluidPillButton(
                            text = "Capture a thought",
                            onClick = onQuickAddClick,
                            isPrimary = true,
                            leadingIcon = Icons.Rounded.Add
                        )
                    }
                }
            }
        } else {
            item {
                FluidInsetCard(modifier = Modifier.fillMaxWidth()) {
                    filteredTasks.forEachIndexed { index, task ->
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

        // Floating nav bar clearance spacer
        item {
            Spacer(modifier = Modifier.height(100.dp))
        }
    }
}
