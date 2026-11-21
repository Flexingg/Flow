package com.flow.todo.ui.todo

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flow.todo.domain.model.TaskItem
import com.flow.todo.domain.tasks.TaskSections
import com.flow.todo.ui.theme.FluidShapes
import com.flow.todo.ui.theme.FluidSpacing
import com.flow.todo.ui.theme.FluidTheme
import com.flow.todo.ui.theme.GlassLevel
import com.flow.todo.ui.theme.fluidGlass
import java.time.LocalDate

enum class Quadrant(val title: String, val hint: String, val important: Boolean, val urgent: Boolean) {
    DO("Do first", "Important · Urgent", true, true),
    SCHEDULE("Schedule", "Important · Not urgent", true, false),
    QUICK("Quick wins", "Urgent · Not important", false, true),
    LATER("Later", "Neither", false, false);

    companion object {
        fun of(task: TaskItem, todayEpoch: Long): Quadrant {
            val urgent = task.isUrgent(todayEpoch)
            return when {
                task.isFlagged && urgent -> DO
                task.isFlagged -> SCHEDULE
                urgent -> QUICK
                else -> LATER
            }
        }
    }
}

/**
 * Eisenhower matrix over all open tasks. Important = starred; urgent = due today/tomorrow/overdue
 * unless the task was explicitly moved. Tap a task to move it to another quadrant.
 */
@Composable
fun EisenhowerMatrix(
    openTasks: List<TaskItem>,
    onToggleComplete: (String) -> Unit,
    onMoveToQuadrant: (taskId: String, important: Boolean, urgent: Boolean) -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier
) {
    val todayEpoch = LocalDate.now().toEpochDay()
    val grouped = remember(openTasks, todayEpoch) { openTasks.groupBy { Quadrant.of(it, todayEpoch) } }
    val colors = FluidTheme.colors
    val quadColor = mapOf(
        Quadrant.DO to colors.danger,
        Quadrant.SCHEDULE to colors.accent,
        Quadrant.QUICK to colors.warning,
        Quadrant.LATER to colors.textTertiary
    )

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(top = contentPadding.calculateTopPadding(), bottom = contentPadding.calculateBottomPadding())
            .padding(horizontal = FluidSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(FluidSpacing.sm)
    ) {
        Text(
            "★ Star = important. Urgent = due by tomorrow (or moved there). Tap a task to move it.",
            fontSize = 12.sp, color = colors.textTertiary
        )
        listOf(Quadrant.DO to Quadrant.SCHEDULE, Quadrant.QUICK to Quadrant.LATER).forEach { (left, right) ->
            Row(horizontalArrangement = Arrangement.spacedBy(FluidSpacing.sm)) {
                listOf(left, right).forEach { q ->
                    QuadrantCard(
                        quadrant = q,
                        color = quadColor.getValue(q),
                        tasks = grouped[q].orEmpty().sortedWith(compareBy<TaskItem> { it.dueDateEpochDay ?: Long.MAX_VALUE }.thenBy { it.createdAt }),
                        onToggleComplete = onToggleComplete,
                        onMove = onMoveToQuadrant,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
        Spacer(Modifier.height(80.dp))
    }
}

@Composable
private fun QuadrantCard(
    quadrant: Quadrant,
    color: Color,
    tasks: List<TaskItem>,
    onToggleComplete: (String) -> Unit,
    onMove: (String, Boolean, Boolean) -> Unit,
    modifier: Modifier
) {
    val colors = FluidTheme.colors
    Column(
        modifier
            .heightIn(min = 220.dp)
            .fluidGlass(GlassLevel.THIN, FluidShapes.lg)
            .border(1.dp, color.copy(alpha = 0.35f), FluidShapes.lg)
            .padding(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(color))
            Spacer(Modifier.width(6.dp))
            Text(quadrant.title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary, modifier = Modifier.weight(1f))
            Text("${tasks.size}", fontSize = 12.sp, color = colors.textTertiary)
        }
        Text(quadrant.hint, fontSize = 10.sp, color = colors.textTertiary)
        Spacer(Modifier.height(6.dp))
        if (tasks.isEmpty()) {
            Text("Nothing here", fontSize = 12.sp, color = colors.textTertiary, modifier = Modifier.padding(vertical = 8.dp))
        }
        tasks.take(12).forEach { task -> MatrixTaskRow(task, color, onToggleComplete, onMove) }
        if (tasks.size > 12) Text("+${tasks.size - 12} more", fontSize = 11.sp, color = colors.textTertiary)
    }
}

@Composable
private fun MatrixTaskRow(
    task: TaskItem,
    color: Color,
    onToggleComplete: (String) -> Unit,
    onMove: (String, Boolean, Boolean) -> Unit
) {
    val colors = FluidTheme.colors
    var menu by remember { mutableStateOf(false) }
    Box {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clip(FluidShapes.sm)
                .clickable { menu = true }
                .padding(vertical = 5.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(18.dp).clip(CircleShape).border(1.5.dp, color, CircleShape).clickable { onToggleComplete(task.id) }
            ) {
                if (task.isCompleted) Icon(Icons.Rounded.Check, null, tint = color, modifier = Modifier.size(12.dp))
            }
            Spacer(Modifier.width(6.dp))
            Column(Modifier.weight(1f)) {
                Text(task.title, fontSize = 13.sp, color = colors.textPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                TaskSections.dueLabel(task.dueDateEpochDay)?.let { Text(it, fontSize = 10.sp, color = colors.textTertiary) }
            }
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            Quadrant.entries.forEach { q ->
                DropdownMenuItem(
                    text = { Text("Move to ${q.title}") },
                    onClick = { menu = false; onMove(task.id, q.important, q.urgent) }
                )
            }
            DropdownMenuItem(text = { Text("Mark done") }, onClick = { menu = false; onToggleComplete(task.id) })
        }
    }
}
