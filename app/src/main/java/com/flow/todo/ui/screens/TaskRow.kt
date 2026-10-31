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
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DateRange
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flow.todo.domain.model.GtdBucket
import com.flow.todo.domain.model.Project
import com.flow.todo.domain.model.TaskItem
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
    modifier: Modifier = Modifier
) {
    var menuExpanded by remember { mutableStateOf(false) }
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

    val menuActions = remember(task, project) {
        listOf(
            FluidMenuAction(
                label = "Do Today",
                icon = Icons.Rounded.Today,
                onSelect = {
                    menuExpanded = false
                    onAssignToday()
                }
            ),
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
            verticalAlignment = Alignment.CenterVertically
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

            // Task Content: Title, Notes preview, Badges
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
                    Text(
                        text = task.notes,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Normal,
                        color = FluidTheme.colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Badges row: Project, Recurrence, Due Date, Flag with bubbly micro-interactions
                val hasBadges = project != null || task.dueDateEpochDay != null || task.isFlagged || task.recurrenceRule?.isRecurring == true
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

                        // Priority Flag - Bubbly pop-in
                        androidx.compose.animation.AnimatedVisibility(
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
