package com.flow.todo.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DateRange
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DirectionsRun
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.SelfImprovement
import androidx.compose.material.icons.rounded.Today
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flow.todo.domain.model.GtdBucket
import com.flow.todo.domain.model.HealthDailySnapshot
import com.flow.todo.domain.model.Project
import com.flow.todo.domain.model.TaskItem
import com.flow.todo.ui.components.FluidGlowingProgressBar
import com.flow.todo.ui.components.FluidMarkdownText
import com.flow.todo.ui.components.FluidMenuAction
import com.flow.todo.ui.components.FluidStretchyContextMenu
import com.flow.todo.ui.theme.FluidShapes
import com.flow.todo.ui.theme.FluidSpacing
import com.flow.todo.ui.theme.FluidSprings
import com.flow.todo.ui.theme.FluidTheme
import com.flow.todo.ui.theme.fluidInteractive
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun TaskRow(
    task: TaskItem,
    project: Project?,
    onToggleComplete: () -> Unit,
    onToggleFlag: () -> Unit,
    onMoveBucket: (GtdBucket) -> Unit,
    onAssignToday: () -> Unit,
    onDelete: () -> Unit,
    onToggleSubtask: (String) -> Unit = {},
    onAddSubtask: (String) -> Unit = {},
    onDeleteSubtask: (String) -> Unit = {},
    healthSnapshot: HealthDailySnapshot? = null,
    onStartFocus: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var menuExpanded by remember { mutableStateOf(false) }
    var subtasksExpanded by remember { mutableStateOf(true) }
    var showAddSubtaskInput by remember { mutableStateOf(false) }
    var newSubtaskTitle by remember { mutableStateOf("") }

    val isReducedMotion = FluidTheme.isReducedMotion
    val accentTint = project?.let { Color(it.colorHex) } ?: FluidTheme.colors.accent

    // Bubbly Checkbox Spring Spec
    val checkboxSpringSpec: FiniteAnimationSpec<Float> = if (isReducedMotion) {
        tween(durationMillis = 150)
    } else {
        spring(dampingRatio = 0.35f, stiffness = 420f)
    }

    val checkboxScale by animateFloatAsState(
        targetValue = if (task.isCompleted) 1.0f else 0.92f,
        animationSpec = checkboxSpringSpec,
        label = "checkboxScale"
    )

    // Bubble Ring Burst Animatable
    val burstAnimatable = remember { Animatable(0f) }
    LaunchedEffect(task.isCompleted) {
        if (task.isCompleted && !isReducedMotion) {
            burstAnimatable.snapTo(0f)
            burstAnimatable.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 420, easing = FastOutSlowInEasing)
            )
        } else {
            burstAnimatable.snapTo(0f)
        }
    }
    val burstProgress = burstAnimatable.value

    val checkboxBgColor by animateColorAsState(
        targetValue = if (task.isCompleted) accentTint else Color.Transparent,
        animationSpec = FluidSprings.morph(isReducedMotion),
        label = "checkboxBg"
    )

    val checkboxBorderColor by animateColorAsState(
        targetValue = if (task.isCompleted) accentTint else FluidTheme.colors.textTertiary.copy(alpha = 0.35f),
        animationSpec = FluidSprings.morph(isReducedMotion),
        label = "checkboxBorder"
    )

    // Bubbly row scale pulse when completed
    val rowScale by animateFloatAsState(
        targetValue = if (task.isCompleted) 0.985f else 1.0f,
        animationSpec = FluidSprings.release(isReducedMotion),
        label = "taskRowScale"
    )

    // Title color smooth transition
    val titleColor by animateColorAsState(
        targetValue = if (task.isCompleted) FluidTheme.colors.textTertiary else FluidTheme.colors.textPrimary,
        animationSpec = FluidSprings.morph(isReducedMotion),
        label = "taskTitleColor"
    )

    // Bouncy bubble badge entrance/exit specs
    val badgeEnter = scaleIn(FluidSprings.release(isReducedMotion)) + expandHorizontally()
    val badgeExit = scaleOut(FluidSprings.release(isReducedMotion)) + shrinkHorizontally()

    val menuActions = remember(task, project, onStartFocus) {
        val actions = mutableListOf<FluidMenuAction>()
        if (onStartFocus != null && !task.isCompleted) {
            actions.add(
                FluidMenuAction(
                    label = "Deep Flow Focus (25m)",
                    icon = Icons.Rounded.SelfImprovement,
                    onSelect = {
                        menuExpanded = false
                        onStartFocus()
                    }
                )
            )
        }
        actions.add(
            FluidMenuAction(
                label = "Do Today",
                icon = Icons.Rounded.Today,
                onSelect = {
                    menuExpanded = false
                    onAssignToday()
                }
            )
        )
        actions.add(
            FluidMenuAction(
                label = "Add / Manage Subtasks",
                icon = Icons.Rounded.Checklist,
                onSelect = {
                    menuExpanded = false
                    subtasksExpanded = true
                    showAddSubtaskInput = true
                }
            )
        )
            FluidMenuAction(
                label = "Move to Next Action",
                icon = Icons.Rounded.PlayArrow,
                onSelect = {
                    menuExpanded = false
                    onMoveBucket(GtdBucket.NEXT_ACTION)
                }
            ),
            FluidMenuAction(
                label = "Move to Someday",
                icon = Icons.Rounded.Lightbulb,
                onSelect = {
                    menuExpanded = false
                    onMoveBucket(GtdBucket.SOMEDAY)
                }
            ),
            FluidMenuAction(
                label = if (task.isFlagged) "Unmark Flag" else "Toggle Priority Flag",
                icon = Icons.Rounded.Flag,
                onSelect = {
                    menuExpanded = false
                    onToggleFlag()
                }
            ),
            FluidMenuAction(
                label = "Delete Task",
                icon = Icons.Rounded.DeleteOutline,
                isDestructive = true,
                onSelect = {
                    menuExpanded = false
                    onDelete()
                }
            )
        )
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = rowScale
                scaleY = rowScale
            }
            .defaultMinSize(minHeight = 56.dp)
            .fluidInteractive(pressScale = 0.98f, onClick = { menuExpanded = true })
            .padding(horizontal = FluidSpacing.md, vertical = FluidSpacing.sm)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top
        ) {
            // Tactile Circular Checkbox (24.dp) with 44.dp touch target & Bubble Ring Burst
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .drawBehind {
                        if (burstProgress > 0f && burstProgress < 1f) {
                            val minRadius = 12.dp.toPx()
                            val maxRadius = size.minDimension * 0.48f
                            val currentRadius = minRadius + (maxRadius - minRadius) * burstProgress
                            val burstAlpha = ((1f - burstProgress) * 0.75f).coerceIn(0f, 1f)
                            val strokeWidth = (3.dp.toPx() * (1f - burstProgress)).coerceAtLeast(1.5f)

                            // Expanding outer bubble ring
                            drawCircle(
                                color = accentTint.copy(alpha = burstAlpha),
                                radius = currentRadius,
                                center = center,
                                style = Stroke(width = strokeWidth)
                            )
                            // Soft inner burst halo
                            drawCircle(
                                color = accentTint.copy(alpha = burstAlpha * 0.35f),
                                radius = currentRadius * 0.85f,
                                center = center
                            )
                        }
                    }
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple(bounded = false, radius = 22.dp),
                        onClick = onToggleComplete
                    ),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .scale(checkboxScale)
                        .then(
                            if (task.isCompleted) {
                                Modifier.shadow(
                                    elevation = 6.dp,
                                    shape = CircleShape,
                                    spotColor = accentTint,
                                    ambientColor = accentTint
                                )
                            } else {
                                Modifier
                            }
                        )
                        .clip(CircleShape)
                        .background(checkboxBgColor)
                        .border(1.5.dp, checkboxBorderColor, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    this@Row.AnimatedVisibility(
                        visible = task.isCompleted,
                        enter = if (isReducedMotion) {
                            fadeIn(tween(150))
                        } else {
                            scaleIn(spring(dampingRatio = 0.35f, stiffness = 500f)) + fadeIn(tween(100))
                        },
                        exit = if (isReducedMotion) {
                            fadeOut(tween(150))
                        } else {
                            scaleOut(spring(dampingRatio = 0.5f, stiffness = 500f)) + fadeOut(tween(100))
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = "Completed",
                            tint = Color.White,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(FluidSpacing.sm))

            // Task Content: Title, Rich Markdown Notes preview, Badges, Interactive Subtasks
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = FluidSpacing.xs)
            ) {
                Text(
                    text = task.title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = titleColor,
                    textDecoration = if (task.isCompleted) {
                        TextDecoration.LineThrough
                    } else {
                        TextDecoration.None
                    },
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                if (task.notes.isNotBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    FluidMarkdownText(
                        markdown = task.notes,
                        maxLines = if (subtasksExpanded) 8 else 2,
                        fontSize = 13.sp,
                        color = FluidTheme.colors.textSecondary
                    )
                }

                // Badges row: Project, Subtasks, Recurrence, Due Date, Health Goal, Context, Flag with bubbly micro-interactions
                val hasBadges = project != null ||
                    task.subtasks.isNotEmpty() ||
                    task.dueDateEpochDay != null ||
                    task.isFlagged ||
                    task.recurrenceRule?.isRecurring == true ||
                    task.healthGoal != null ||
                    !task.contextTag.isNullOrBlank()

                this@Column.AnimatedVisibility(
                    visible = hasBadges,
                    enter = badgeEnter,
                    exit = badgeExit
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(top = FluidSpacing.xs)
                    ) {
                        // Project Badge
                        if (project != null) {
                            val projectColor = Color(project.colorHex)
                            Box(
                                modifier = Modifier
                                    .clip(FluidShapes.full)
                                    .background(projectColor.copy(alpha = 0.12f))
                                    .border(1.dp, projectColor.copy(alpha = 0.28f), FluidShapes.full)
                                    .padding(horizontal = 7.dp, vertical = 2.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(projectColor)
                                    )
                                    Text(
                                        text = project.name,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = projectColor,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }

                        // Subtasks Progress Badge (toggles checklist expansion)
                        if (task.subtasks.isNotEmpty()) {
                            val allSubtasksDone = task.completedSubtasksCount == task.totalSubtasksCount
                            val subtaskBadgeColor = if (allSubtasksDone) {
                                FluidTheme.colors.success
                            } else {
                                FluidTheme.colors.accent
                            }
                            Box(
                                modifier = Modifier
                                    .clip(FluidShapes.full)
                                    .background(subtaskBadgeColor.copy(alpha = 0.12f))
                                    .border(1.dp, subtaskBadgeColor.copy(alpha = 0.28f), FluidShapes.full)
                                    .clickable { subtasksExpanded = !subtasksExpanded }
                                    .padding(horizontal = 7.dp, vertical = 2.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Checklist,
                                        contentDescription = "Subtasks",
                                        tint = subtaskBadgeColor,
                                        modifier = Modifier.size(11.dp)
                                    )
                                    Text(
                                        text = "${task.completedSubtasksCount}/${task.totalSubtasksCount}",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = subtaskBadgeColor
                                    )
                                }
                            }
                        }

                        // Recurrence Badge
                        if (task.recurrenceRule?.isRecurring == true) {
                            Box(
                                modifier = Modifier
                                    .clip(FluidShapes.full)
                                    .background(FluidTheme.colors.accent.copy(alpha = 0.12f))
                                    .border(1.dp, FluidTheme.colors.accent.copy(alpha = 0.28f), FluidShapes.full)
                                    .padding(horizontal = 7.dp, vertical = 2.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Repeat,
                                        contentDescription = "Recurring",
                                        tint = FluidTheme.colors.accent,
                                        modifier = Modifier.size(11.dp)
                                    )
                                    Text(
                                        text = task.recurrenceRule.toDisplayLabel(),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = FluidTheme.colors.accent
                                    )
                                }
                            }
                        }

                        // Due Date Badge
                        if (task.dueDateEpochDay != null) {
                            val todayEpoch = LocalDate.now().toEpochDay()
                            val (dueLabel, dueColor) = when {
                                task.dueDateEpochDay < todayEpoch -> "Overdue" to FluidTheme.colors.danger
                                task.dueDateEpochDay == todayEpoch -> "Today" to FluidTheme.colors.accent
                                task.dueDateEpochDay == todayEpoch + 1 -> "Tomorrow" to FluidTheme.colors.warning
                                else -> {
                                    val date = LocalDate.ofEpochDay(task.dueDateEpochDay)
                                    date.format(DateTimeFormatter.ofPattern("MMM d")) to FluidTheme.colors.textSecondary
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .clip(FluidShapes.full)
                                    .background(dueColor.copy(alpha = 0.12f))
                                    .border(1.dp, dueColor.copy(alpha = 0.28f), FluidShapes.full)
                                    .padding(horizontal = 7.dp, vertical = 2.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.DateRange,
                                        contentDescription = null,
                                        tint = dueColor,
                                        modifier = Modifier.size(11.dp)
                                    )
                                    Text(
                                        text = dueLabel,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = dueColor
                                    )
                                }
                            }
                        }

                        // Health Goal Badge
                        if (task.healthGoal != null) {
                            val currentVal = healthSnapshot?.valueFor(task.healthGoal.metricType) ?: 0.0
                            Box(
                                modifier = Modifier
                                    .clip(FluidShapes.full)
                                    .background(FluidTheme.colors.accent.copy(alpha = 0.12f))
                                    .border(1.dp, FluidTheme.colors.accent.copy(alpha = 0.28f), FluidShapes.full)
                                    .padding(horizontal = 7.dp, vertical = 2.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.DirectionsRun,
                                        contentDescription = "Health Goal",
                                        tint = FluidTheme.colors.accent,
                                        modifier = Modifier.size(11.dp)
                                    )
                                    Text(
                                        text = task.healthGoal.toDisplayLabel(currentVal),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = FluidTheme.colors.accent
                                    )
                                }
                            }
                        }

                        // GTD Context Badge (@work, @home, @errands, etc.)
                        if (!task.contextTag.isNullOrBlank()) {
                            val ctxColor = FluidTheme.colors.accent
                            Box(
                                modifier = Modifier
                                    .clip(FluidShapes.full)
                                    .background(ctxColor.copy(alpha = 0.12f))
                                    .border(1.dp, ctxColor.copy(alpha = 0.28f), FluidShapes.full)
                                    .padding(horizontal = 7.dp, vertical = 2.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.LocationOn,
                                        contentDescription = "Context",
                                        tint = ctxColor,
                                        modifier = Modifier.size(11.dp)
                                    )
                                    Text(
                                        text = task.contextTag,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = ctxColor
                                    )
                                }
                            }
                        }

                        // Priority Flag - Bubbly pop-in
                        this@Row.AnimatedVisibility(
                            visible = task.isFlagged,
                            enter = badgeEnter,
                            exit = badgeExit
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(FluidShapes.full)
                                    .background(FluidTheme.colors.warning.copy(alpha = 0.12f))
                                    .border(1.dp, FluidTheme.colors.warning.copy(alpha = 0.28f), FluidShapes.full)
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Flag,
                                        contentDescription = "Flagged",
                                        tint = FluidTheme.colors.warning,
                                        modifier = Modifier.size(11.dp)
                                    )
                                    Text(
                                        text = "Flag",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = FluidTheme.colors.warning
                                    )
                                }
                            }
                        }
                    }
                }

                // Interactive Subtask Checklists & Inline Subtask Creator
                this@Column.AnimatedVisibility(
                    visible = (task.subtasks.isNotEmpty() && subtasksExpanded) || showAddSubtaskInput,
                    enter = expandVertically(FluidSprings.morph(isReducedMotion)) + fadeIn(),
                    exit = shrinkVertically(FluidSprings.morph(isReducedMotion)) + fadeOut()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        task.subtasks.forEach { subtask ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .defaultMinSize(minHeight = 28.dp)
                                    .clip(FluidShapes.sm)
                                    .background(
                                        if (FluidTheme.colors.isDark) {
                                            Color.White.copy(alpha = 0.04f)
                                        } else {
                                            Color.Black.copy(alpha = 0.025f)
                                        }
                                    )
                                    .clickable { onToggleSubtask(subtask.id) }
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Mini circular checkbox (16.dp)
                                Box(
                                    modifier = Modifier
                                        .size(16.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (subtask.isCompleted) accentTint else Color.Transparent
                                        )
                                        .border(
                                            width = 1.25.dp,
                                            color = if (subtask.isCompleted) {
                                                accentTint
                                            } else {
                                                FluidTheme.colors.textTertiary.copy(alpha = 0.45f)
                                            },
                                            shape = CircleShape
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (subtask.isCompleted) {
                                        Icon(
                                            imageVector = Icons.Rounded.Check,
                                            contentDescription = "Subtask completed",
                                            tint = Color.White,
                                            modifier = Modifier.size(10.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                Text(
                                    text = subtask.title,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Normal,
                                    color = if (subtask.isCompleted) {
                                        FluidTheme.colors.textTertiary
                                    } else {
                                        FluidTheme.colors.textPrimary
                                    },
                                    textDecoration = if (subtask.isCompleted) {
                                        TextDecoration.LineThrough
                                    } else {
                                        TextDecoration.None
                                    },
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )

                                Box(
                                    modifier = Modifier
                                        .size(20.dp)
                                        .clip(CircleShape)
                                        .clickable { onDeleteSubtask(subtask.id) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Close,
                                        contentDescription = "Remove subtask",
                                        tint = FluidTheme.colors.textTertiary.copy(alpha = 0.7f),
                                        modifier = Modifier.size(12.dp)
                                    )
                                }
                            }
                        }

                        // Compact inline "+ Add subtask..." row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = 28.dp)
                                .clip(FluidShapes.sm)
                                .border(
                                    width = 1.dp,
                                    color = FluidTheme.colors.divider,
                                    shape = FluidShapes.sm
                                )
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Add,
                                contentDescription = null,
                                tint = FluidTheme.colors.accent,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(modifier = Modifier.weight(1f)) {
                                if (newSubtaskTitle.isEmpty()) {
                                    Text(
                                        text = "+ Add subtask...",
                                        fontSize = 12.sp,
                                        color = FluidTheme.colors.textTertiary
                                    )
                                }
                                BasicTextField(
                                    value = newSubtaskTitle,
                                    onValueChange = { newSubtaskTitle = it },
                                    singleLine = true,
                                    textStyle = TextStyle(
                                        fontSize = 12.sp,
                                        color = FluidTheme.colors.textPrimary
                                    ),
                                    cursorBrush = SolidColor(FluidTheme.colors.accent),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            if (newSubtaskTitle.isNotBlank()) {
                                Box(
                                    modifier = Modifier
                                        .clip(FluidShapes.full)
                                        .background(accentTint.copy(alpha = 0.16f))
                                        .clickable {
                                            val clean = newSubtaskTitle.trim()
                                            if (clean.isNotEmpty()) {
                                                onAddSubtask(clean)
                                                newSubtaskTitle = ""
                                            }
                                        }
                                        .padding(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "Add",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = accentTint
                                    )
                                }
                            }
                        }
                    }
                }

                // Health Goal live progress bar
                if (task.healthGoal != null) {
                    val currentVal = healthSnapshot?.valueFor(task.healthGoal.metricType) ?: 0.0
                    val goalProgress = if (task.isCompleted) 1f else task.healthGoal.progressFraction(currentVal)
                    FluidGlowingProgressBar(
                        progress = goalProgress,
                        accentColor = accentTint,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp)
                    )
                }
            }

            // Trailing "More / Clarify" Action
            IconButton(
                onClick = { menuExpanded = true },
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.MoreVert,
                    contentDescription = "Clarify Task Options",
                    tint = FluidTheme.colors.textTertiary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // ColorOS 17 Stretchy Context Menu
        FluidStretchyContextMenu(
            expanded = menuExpanded,
            onDismissRequest = { menuExpanded = false },
            actions = menuActions,
            modifier = Modifier.align(Alignment.TopEnd)
        )
    }
}
