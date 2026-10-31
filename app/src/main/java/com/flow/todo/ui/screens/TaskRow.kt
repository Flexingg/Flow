package com.flow.todo.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.material.icons.rounded.Today
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
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
    val accentTint = project?.let { Color(it.colorHex) } ?: FluidTheme.colors.accent

    val checkboxScale by animateFloatAsState(
        targetValue = if (task.isCompleted) 1.0f else 0.92f,
        animationSpec = FluidSprings.release(FluidTheme.isReducedMotion),
        label = "checkboxScale"
    )

    val checkboxBgColor by animateColorAsState(
        targetValue = if (task.isCompleted) accentTint else Color.Transparent,
        animationSpec = FluidSprings.morph(FluidTheme.isReducedMotion),
        label = "checkboxBg"
    )

    val checkboxBorderColor by animateColorAsState(
        targetValue = if (task.isCompleted) accentTint else FluidTheme.colors.textTertiary.copy(alpha = 0.35f),
        animationSpec = FluidSprings.morph(FluidTheme.isReducedMotion),
        label = "checkboxBorder"
    )

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
            .defaultMinSize(minHeight = 56.dp)
            .fluidInteractive(pressScale = 0.98f, onClick = { menuExpanded = true })
            .padding(horizontal = FluidSpacing.md, vertical = FluidSpacing.sm)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Tactile Circular Checkbox (24.dp) with 44.dp minimum touch target
            Box(
                modifier = Modifier
                    .size(44.dp)
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
                    if (task.isCompleted) {
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
                    color = if (task.isCompleted) {
                        FluidTheme.colors.textTertiary
                    } else {
                        FluidTheme.colors.textPrimary
                    },
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

                // Badges row: Project, Due Date, Flag
                val hasBadges = project != null || task.dueDateEpochDay != null || task.isFlagged
                if (hasBadges) {
                    Spacer(modifier = Modifier.height(FluidSpacing.xs))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
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

                        // Priority Flag
                        if (task.isFlagged) {
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
