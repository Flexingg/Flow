package com.flow.todo.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DateRange
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DirectionsRun
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.EventBusy
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Today
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flow.todo.domain.model.HealthDailySnapshot
import com.flow.todo.domain.model.TaskItem
import com.flow.todo.ui.components.FluidGlowingProgressBar
import com.flow.todo.ui.components.FluidMarkdownText
import com.flow.todo.ui.components.FluidMenuAction
import com.flow.todo.ui.components.FluidStretchyContextMenu
import com.flow.todo.ui.theme.FluidShapes
import com.flow.todo.ui.theme.FluidSprings
import com.flow.todo.ui.theme.FluidTheme
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Compact task row: checkbox · title · one line of meta.
 *
 * Uses the standard [clickable] (not the custom press modifier) so the row
 * plays nicely inside the scrolling list: starting a scroll on a row never
 * selects it. Tap the row for options; tap the subtask count to expand the checklist.
 */
@Composable
fun TaskRow(
    task: TaskItem,
    onToggleComplete: () -> Unit,
    onToggleFlag: () -> Unit,
    /** Set (or clear with null) the due date as an epoch day. */
    onSetDueDate: (Long?) -> Unit,
    onDelete: () -> Unit,
    onToggleSubtask: (String) -> Unit = {},
    onAddSubtask: (String) -> Unit = {},
    onDeleteSubtask: (String) -> Unit = {},
    healthSnapshot: HealthDailySnapshot? = null,
    onEdit: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val colors = FluidTheme.colors
    val isReducedMotion = FluidTheme.isReducedMotion
    val accent = colors.accent

    var menuExpanded by remember { mutableStateOf(false) }
    var subtasksExpanded by remember { mutableStateOf(false) }
    var newSubtaskTitle by remember { mutableStateOf("") }

    val checkboxFill by animateColorAsState(
        targetValue = if (task.isCompleted) accent else Color.Transparent,
        animationSpec = FluidSprings.morph(isReducedMotion),
        label = "checkboxFill"
    )
    val checkboxBorder by animateColorAsState(
        targetValue = if (task.isCompleted) accent else colors.textTertiary.copy(alpha = 0.5f),
        animationSpec = FluidSprings.morph(isReducedMotion),
        label = "checkboxBorder"
    )
    val titleColor by animateColorAsState(
        targetValue = if (task.isCompleted) colors.textTertiary else colors.textPrimary,
        animationSpec = FluidSprings.morph(isReducedMotion),
        label = "taskTitleColor"
    )

    val menuActions = remember(task, onEdit) {
        val todayEpoch = LocalDate.now().toEpochDay()
        buildList {
            if (onEdit != null) {
                add(FluidMenuAction(label = "Edit", icon = Icons.Rounded.Edit, onSelect = {
                    menuExpanded = false; onEdit()
                }))
            }
            if (task.dueDateEpochDay != todayEpoch) {
                add(FluidMenuAction(label = "Do today", icon = Icons.Rounded.Today, onSelect = {
                    menuExpanded = false; onSetDueDate(todayEpoch)
                }))
            }
            if (task.dueDateEpochDay != todayEpoch + 1) {
                add(FluidMenuAction(label = "Tomorrow", icon = Icons.Rounded.DateRange, onSelect = {
                    menuExpanded = false; onSetDueDate(todayEpoch + 1)
                }))
            }
            if (task.dueDateEpochDay != null) {
                add(FluidMenuAction(label = "Remove date", icon = Icons.Rounded.EventBusy, onSelect = {
                    menuExpanded = false; onSetDueDate(null)
                }))
            }
            add(FluidMenuAction(label = "Subtasks", icon = Icons.Rounded.Checklist, onSelect = {
                menuExpanded = false; subtasksExpanded = true
            }))
            add(FluidMenuAction(label = if (task.isFlagged) "Unflag" else "Flag", icon = Icons.Rounded.Flag, onSelect = {
                menuExpanded = false; onToggleFlag()
            }))
            add(FluidMenuAction(label = "Delete", icon = Icons.Rounded.DeleteOutline, isDestructive = true, onSelect = {
                menuExpanded = false; onDelete()
            }))
        }
    }

    Box(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(FluidShapes.sm)
                .clickable { menuExpanded = true }
                .padding(end = 8.dp, top = 2.dp, bottom = 2.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 44.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Checkbox: 20dp circle inside a 40dp touch target
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onToggleComplete),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(checkboxFill)
                            .border(1.5.dp, checkboxBorder, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        if (task.isCompleted) {
                            Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = "Completed",
                                tint = Color.White,
                                modifier = Modifier.size(13.dp)
                            )
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(vertical = 6.dp)
                ) {
                    Text(
                        text = task.title,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = titleColor,
                        textDecoration = if (task.isCompleted) TextDecoration.LineThrough else TextDecoration.None,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    if (task.notes.isNotBlank()) {
                        FluidMarkdownText(
                            markdown = task.notes,
                            maxLines = 1,
                            fontSize = 12.sp,
                            color = colors.textTertiary
                        )
                    }

                    MetaLine(
                        task = task,
                        healthSnapshot = healthSnapshot,
                        onToggleSubtasks = { subtasksExpanded = !subtasksExpanded }
                    )
                }
            }

            // Subtask checklist (collapsed by default)
            AnimatedVisibility(
                visible = subtasksExpanded,
                enter = expandVertically(FluidSprings.morph(isReducedMotion)) + fadeIn(),
                exit = shrinkVertically(FluidSprings.morph(isReducedMotion)) + fadeOut()
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(start = 40.dp, bottom = 4.dp)) {
                    task.subtasks.forEach { subtask ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = 32.dp)
                                .clip(FluidShapes.xs)
                                .clickable { onToggleSubtask(subtask.id) }
                                .padding(start = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(16.dp)
                                    .clip(CircleShape)
                                    .background(if (subtask.isCompleted) accent else Color.Transparent)
                                    .border(
                                        1.25.dp,
                                        if (subtask.isCompleted) accent else colors.textTertiary.copy(alpha = 0.5f),
                                        CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (subtask.isCompleted) {
                                    Icon(
                                        Icons.Rounded.Check,
                                        contentDescription = "Subtask completed",
                                        tint = Color.White,
                                        modifier = Modifier.size(10.dp)
                                    )
                                }
                            }
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = subtask.title,
                                fontSize = 13.sp,
                                color = if (subtask.isCompleted) colors.textTertiary else colors.textPrimary,
                                textDecoration = if (subtask.isCompleted) TextDecoration.LineThrough else TextDecoration.None,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .clickable { onDeleteSubtask(subtask.id) },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Rounded.Close,
                                    contentDescription = "Remove subtask",
                                    tint = colors.textTertiary,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }

                    // Inline "Add subtask" field
                    val submit = {
                        val clean = newSubtaskTitle.trim()
                        if (clean.isNotEmpty()) {
                            onAddSubtask(clean)
                            newSubtaskTitle = ""
                        }
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 32.dp)
                            .padding(start = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Rounded.Add,
                            contentDescription = null,
                            tint = colors.textTertiary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Box(modifier = Modifier.weight(1f)) {
                            if (newSubtaskTitle.isEmpty()) {
                                Text("Add subtask", fontSize = 13.sp, color = colors.textTertiary)
                            }
                            BasicTextField(
                                value = newSubtaskTitle,
                                onValueChange = { newSubtaskTitle = it },
                                singleLine = true,
                                textStyle = TextStyle(fontSize = 13.sp, color = colors.textPrimary),
                                cursorBrush = SolidColor(accent),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(onDone = { submit() }),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        if (newSubtaskTitle.isNotBlank()) {
                            Text(
                                text = "Add",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = accent,
                                modifier = Modifier
                                    .clip(FluidShapes.xs)
                                    .clickable { submit() }
                                    .padding(horizontal = 8.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }

            // Health goal progress (thin, only for health-linked tasks)
            if (task.healthGoal != null) {
                val currentVal = healthSnapshot?.valueFor(task.healthGoal.metricType) ?: 0.0
                val goalProgress = if (task.isCompleted) 1f else task.healthGoal.progressFraction(currentVal)
                FluidGlowingProgressBar(
                    progress = goalProgress,
                    accentColor = accent,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 40.dp, bottom = 6.dp)
                )
            }
        }

        FluidStretchyContextMenu(
            expanded = menuExpanded,
            onDismissRequest = { menuExpanded = false },
            actions = menuActions,
            modifier = Modifier.align(Alignment.TopEnd)
        )
    }
}

/** Single line of small, plain metadata: due · repeat · subtasks · health · flag. */
@Composable
private fun MetaLine(
    task: TaskItem,
    healthSnapshot: HealthDailySnapshot?,
    onToggleSubtasks: () -> Unit
) {
    val colors = FluidTheme.colors
    val hasMeta = task.dueDateEpochDay != null ||
        task.recurrenceRule?.isRecurring == true ||
        task.subtasks.isNotEmpty() ||
        task.healthGoal != null ||
        task.isFlagged
    if (!hasMeta) return

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.padding(top = 3.dp)
    ) {
        task.dueDateEpochDay?.let { due ->
            val todayEpoch = LocalDate.now().toEpochDay()
            val (label, color) = when {
                due < todayEpoch -> "Overdue" to colors.danger
                due == todayEpoch -> "Today" to colors.accent
                due == todayEpoch + 1 -> "Tomorrow" to colors.warning
                else -> LocalDate.ofEpochDay(due).format(DateTimeFormatter.ofPattern("MMM d")) to colors.textSecondary
            }
            MetaItem(Icons.Rounded.DateRange, label, color)
        }

        if (task.recurrenceRule?.isRecurring == true) {
            MetaItem(Icons.Rounded.Repeat, task.recurrenceRule.toDisplayLabel(), colors.textSecondary)
        }

        if (task.subtasks.isNotEmpty()) {
            val allDone = task.completedSubtasksCount == task.totalSubtasksCount
            MetaItem(
                icon = Icons.Rounded.Checklist,
                label = "${task.completedSubtasksCount}/${task.totalSubtasksCount}",
                color = if (allDone) colors.success else colors.textSecondary,
                modifier = Modifier
                    .clip(FluidShapes.xs)
                    .clickable(onClick = onToggleSubtasks)
            )
        }

        if (task.healthGoal != null) {
            val currentVal = healthSnapshot?.valueFor(task.healthGoal.metricType) ?: 0.0
            MetaItem(Icons.Rounded.DirectionsRun, task.healthGoal.toDisplayLabel(currentVal), colors.textSecondary)
        }

        if (task.isFlagged) {
            Icon(
                Icons.Rounded.Flag,
                contentDescription = "Flagged",
                tint = colors.warning,
                modifier = Modifier.size(13.dp)
            )
        }
    }
}

@Composable
private fun MetaItem(
    icon: ImageVector,
    label: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        modifier = modifier
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(12.dp))
        Text(text = label, fontSize = 12.sp, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
