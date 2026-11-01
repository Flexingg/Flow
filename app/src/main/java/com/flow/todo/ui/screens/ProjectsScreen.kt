package com.flow.todo.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.flow.todo.domain.model.GtdBucket
import com.flow.todo.domain.model.Project
import com.flow.todo.domain.model.ProjectWithStats
import com.flow.todo.domain.model.TaskItem
import com.flow.todo.ui.components.FluidGlowingProgressBar
import com.flow.todo.ui.components.FluidInsetCard
import com.flow.todo.ui.components.FluidPillButton
import com.flow.todo.ui.theme.FluidShapes
import com.flow.todo.ui.theme.FluidSpacing
import com.flow.todo.ui.theme.FluidSprings
import com.flow.todo.ui.theme.FluidTheme
import com.flow.todo.ui.theme.GlassLevel
import com.flow.todo.ui.theme.fluidGlass

private val PROJECT_COLOR_SWATCHES = listOf(
    0xFF2F6BFFL, // Ocean Blue
    0xFF22B35EL, // Emerald Flow
    0xFFFF9F0AL, // Amber Sun
    0xFFAF52DEL, // Purple Orchid
    0xFFFF375FL, // Rose Coral
    0xFF00C7BEL  // Cyan Wave
)

@Composable
fun ProjectsScreen(
    projectsWithStats: List<ProjectWithStats>,
    somedayTasks: List<TaskItem>,
    allProjects: List<Project>,
    onToggleComplete: (String) -> Unit,
    onToggleFlag: (String) -> Unit,
    onMoveBucket: (String, GtdBucket) -> Unit,
    onAssignToday: (String) -> Unit,
    onDeleteTask: (String) -> Unit,
    onAddProject: (String, Long) -> Unit,
    onDeleteProject: (String) -> Unit,
    onToggleSubtask: (String, String) -> Unit = { _, _ -> },
    onAddSubtask: (String, String) -> Unit = { _, _ -> },
    onDeleteSubtask: (String, String) -> Unit = { _, _ -> },
    onStartFocus: (taskId: String, taskTitle: String) -> Unit = { _, _ -> },
    contentPadding: PaddingValues = PaddingValues(0.dp),
    listState: LazyListState = rememberLazyListState(),
    modifier: Modifier = Modifier
) {
    val isReducedMotion = FluidTheme.isReducedMotion
    var showCreateDialog by remember { mutableStateOf(false) }
    // Project ID -> expanded state (defaults to true)
    val expandedStates = remember { mutableStateMapOf<String, Boolean>() }

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

        // Header Row with "+ New Project" Button
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Projects & Horizons",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = FluidTheme.colors.textPrimary
                    )
                    Text(
                        text = "Multi-step commitments & future goals",
                        fontSize = 12.sp,
                        color = FluidTheme.colors.textSecondary
                    )
                }

                FluidPillButton(
                    text = "New Project",
                    onClick = { showCreateDialog = true },
                    isPrimary = false,
                    leadingIcon = Icons.Rounded.Add,
                    modifier = Modifier.height(40.dp)
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(FluidSpacing.md))
        }

        // Projects List with Expandable Cards
        if (projectsWithStats.isEmpty()) {
            item {
                FluidInsetCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "No active projects yet. Create one to organize multi-step outcomes.",
                        fontSize = 14.sp,
                        color = FluidTheme.colors.textTertiary,
                        modifier = Modifier.padding(vertical = FluidSpacing.md)
                    )
                }
            }
        } else {
            items(
                count = projectsWithStats.size,
                key = { index -> projectsWithStats[index].project.id }
            ) { index ->
                val projectWithStats = projectsWithStats[index]
                val project = projectWithStats.project
                val projectColor = Color(project.colorHex)
                val isExpanded = expandedStates[project.id] ?: true

                val rotationAngle by animateFloatAsState(
                    targetValue = if (isExpanded) 180f else 0f,
                    animationSpec = FluidSprings.release(isReducedMotion),
                    label = "arrowRotation"
                )

                FluidInsetCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = FluidSpacing.md),
                    accentGlowColor = projectColor.copy(alpha = 0.35f)
                ) {
                    // Project Card Header
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = {
                                    expandedStates[project.id] = !isExpanded
                                }
                            ),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(14.dp)
                                    .clip(CircleShape)
                                    .background(projectColor)
                                    .border(1.5.dp, Color.White.copy(alpha = 0.5f), CircleShape)
                            )

                            Column {
                                Text(
                                    text = project.name,
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = FluidTheme.colors.textPrimary
                                )
                                // Project Completion Counters AnimatedContent
                                AnimatedContent(
                                    targetState = projectWithStats.completedCount to projectWithStats.totalCount,
                                    transitionSpec = {
                                        if (isReducedMotion) {
                                            fadeIn(tween(150)) togetherWith fadeOut(tween(150))
                                        } else {
                                            (scaleIn(initialScale = 0.85f, animationSpec = FluidSprings.release(false)) + fadeIn(tween(150)))
                                                .togetherWith(
                                                    scaleOut(targetScale = 0.85f, animationSpec = FluidSprings.release(false)) + fadeOut(tween(150))
                                                )
                                        }
                                    },
                                    label = "projectStatsCount"
                                ) { (completed, total) ->
                                    Text(
                                        text = "$completed/$total completed",
                                        fontSize = 12.sp,
                                        color = FluidTheme.colors.textSecondary
                                    )
                                }
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            IconButton(
                                onClick = { onDeleteProject(project.id) },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.DeleteOutline,
                                    contentDescription = "Delete Project",
                                    tint = FluidTheme.colors.textTertiary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            Icon(
                                imageVector = Icons.Rounded.ExpandMore,
                                contentDescription = if (isExpanded) "Collapse" else "Expand",
                                tint = FluidTheme.colors.textSecondary,
                                modifier = Modifier
                                    .size(24.dp)
                                    .rotate(rotationAngle)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(FluidSpacing.sm))

                    // Luminous Progress Bar tinted with project color
                    FluidGlowingProgressBar(
                        progress = projectWithStats.progress,
                        accentColor = projectColor,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Bouncy Expandable Task List via FluidSprings.morph
                    AnimatedVisibility(
                        visible = isExpanded,
                        enter = expandVertically(animationSpec = FluidSprings.morph(isReducedMotion)) + fadeIn(tween(180)),
                        exit = shrinkVertically(animationSpec = FluidSprings.morph(isReducedMotion)) + fadeOut(tween(150))
                    ) {
                        Column(modifier = Modifier.padding(top = FluidSpacing.sm)) {
                            if (projectWithStats.tasks.isEmpty()) {
                                Text(
                                    text = "No tasks assigned to this project yet.",
                                    fontSize = 13.sp,
                                    color = FluidTheme.colors.textTertiary,
                                    modifier = Modifier.padding(vertical = FluidSpacing.sm)
                                )
                            } else {
                                projectWithStats.tasks.forEachIndexed { taskIndex, task ->
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
                                                if (taskIndex > 0) {
                                                    HorizontalDivider(
                                                        thickness = 1.dp,
                                                        color = FluidTheme.colors.divider,
                                                        modifier = Modifier.padding(horizontal = FluidSpacing.md)
                                                    )
                                                }
                                                TaskRow(
                                                    task = task,
                                                    project = project,
                                                    onToggleComplete = { onToggleComplete(task.id) },
                                                    onToggleFlag = { onToggleFlag(task.id) },
                                                    onMoveBucket = { bucket -> onMoveBucket(task.id, bucket) },
                                                    onAssignToday = { onAssignToday(task.id) },
                                                    onDelete = { onDeleteTask(task.id) },
                                                    onToggleSubtask = { subtaskId -> onToggleSubtask(task.id, subtaskId) },
                                                    onAddSubtask = { title -> onAddSubtask(task.id, title) },
                                                    onDeleteSubtask = { subtaskId -> onDeleteSubtask(task.id, subtaskId) },
                                                    onStartFocus = { onStartFocus(task.id, task.title) }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(FluidSpacing.md))
        }

        // Bottom Section: Someday / Maybe
        item {
            FluidInsetCard(modifier = Modifier.fillMaxWidth()) {
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
                                .background(FluidTheme.colors.warning.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Lightbulb,
                                contentDescription = null,
                                tint = FluidTheme.colors.warning,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "Someday / Maybe",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = FluidTheme.colors.textPrimary
                            )
                            Text(
                                text = "Incubating ideas & future aspirations",
                                fontSize = 11.sp,
                                color = FluidTheme.colors.textTertiary
                            )
                        }
                    }

                    // Someday task count badge with bouncy AnimatedContent
                    Box(
                        modifier = Modifier
                            .clip(FluidShapes.full)
                            .background(FluidTheme.colors.divider)
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        AnimatedContent(
                            targetState = somedayTasks.size,
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
                            label = "somedayTasksCount"
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

                if (somedayTasks.isEmpty()) {
                    Text(
                        text = "No someday ideas recorded yet.",
                        fontSize = 14.sp,
                        color = FluidTheme.colors.textTertiary,
                        modifier = Modifier.padding(vertical = FluidSpacing.sm, horizontal = FluidSpacing.xs)
                    )
                } else {
                    somedayTasks.forEachIndexed { index, task ->
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
                                        project = allProjects.find { it.id == task.projectId },
                                        onToggleComplete = { onToggleComplete(task.id) },
                                        onToggleFlag = { onToggleFlag(task.id) },
                                        onMoveBucket = { bucket -> onMoveBucket(task.id, bucket) },
                                        onAssignToday = { onAssignToday(task.id) },
                                        onDelete = { onDeleteTask(task.id) },
                                        onToggleSubtask = { subtaskId -> onToggleSubtask(task.id, subtaskId) },
                                        onAddSubtask = { title -> onAddSubtask(task.id, title) },
                                        onDeleteSubtask = { subtaskId -> onDeleteSubtask(task.id, subtaskId) },
                                        onStartFocus = { onStartFocus(task.id, task.title) }
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

    // New Project Dialog styled with Living Flow glass
    if (showCreateDialog) {
        var projectName by remember { mutableStateOf("") }
        var selectedColorHex by remember { mutableLongStateOf(PROJECT_COLOR_SWATCHES[0]) }

        Dialog(onDismissRequest = { showCreateDialog = false }) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fluidGlass(level = GlassLevel.THICK, shape = FluidShapes.xl)
                    .padding(24.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "New Project",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = FluidTheme.colors.textPrimary
                    )

                    Spacer(modifier = Modifier.height(FluidSpacing.xs))

                    Text(
                        text = "Give your multi-step commitment a name and theme color.",
                        fontSize = 13.sp,
                        color = FluidTheme.colors.textSecondary
                    )

                    Spacer(modifier = Modifier.height(FluidSpacing.lg))

                    // Text Input
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(FluidShapes.md)
                            .background(
                                if (FluidTheme.colors.isDark) {
                                    Color.White.copy(alpha = 0.08f)
                                } else {
                                    Color.Black.copy(alpha = 0.04f)
                                }
                            )
                            .border(
                                1.dp,
                                FluidTheme.colors.divider,
                                FluidShapes.md
                            )
                            .padding(horizontal = 16.dp, vertical = 14.dp)
                    ) {
                        if (projectName.isEmpty()) {
                            Text(
                                text = "e.g. Design System Rollout",
                                fontSize = 15.sp,
                                color = FluidTheme.colors.textTertiary
                            )
                        }
                        BasicTextField(
                            value = projectName,
                            onValueChange = { projectName = it },
                            singleLine = true,
                            textStyle = TextStyle(
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium,
                                color = FluidTheme.colors.textPrimary
                            ),
                            cursorBrush = SolidColor(FluidTheme.colors.accent),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Spacer(modifier = Modifier.height(FluidSpacing.lg))

                    Text(
                        text = "Color Accent",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = FluidTheme.colors.textSecondary
                    )

                    Spacer(modifier = Modifier.height(FluidSpacing.sm))

                    // 6 Color Accent Swatches with bouncy selection scale pulse
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        PROJECT_COLOR_SWATCHES.forEach { colorHex ->
                            val swatchColor = Color(colorHex)
                            val isSelected = selectedColorHex == colorHex

                            val swatchScale by animateFloatAsState(
                                targetValue = if (isSelected) 1.20f else 0.95f,
                                animationSpec = FluidSprings.release(isReducedMotion),
                                label = "swatchBounce_$colorHex"
                            )

                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .graphicsLayer {
                                        scaleX = swatchScale
                                        scaleY = swatchScale
                                    }
                                    .clip(CircleShape)
                                    .background(swatchColor)
                                    .border(
                                        width = if (isSelected) 2.5.dp else 1.dp,
                                        color = if (isSelected) Color.White else Color.Transparent,
                                        shape = CircleShape
                                    )
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = ripple(bounded = false, radius = 20.dp),
                                        onClick = { selectedColorHex = colorHex }
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Rounded.Check,
                                        contentDescription = "Selected",
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(FluidSpacing.xl))

                    // Dialog Actions
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(FluidSpacing.md)
                    ) {
                        FluidPillButton(
                            text = "Cancel",
                            onClick = { showCreateDialog = false },
                            isPrimary = false,
                            modifier = Modifier.weight(1f)
                        )

                        FluidPillButton(
                            text = "Create",
                            onClick = {
                                if (projectName.isNotBlank()) {
                                    onAddProject(projectName.trim(), selectedColorHex)
                                    showCreateDialog = false
                                }
                            },
                            isPrimary = true,
                            enabled = projectName.isNotBlank(),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}
