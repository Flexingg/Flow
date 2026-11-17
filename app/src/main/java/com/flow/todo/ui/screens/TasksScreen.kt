package com.flow.todo.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flow.todo.domain.model.HealthDailySnapshot
import com.flow.todo.domain.model.TaskItem
import com.flow.todo.domain.tasks.TaskSections
import com.flow.todo.ui.components.FluidGlowingProgressBar
import com.flow.todo.ui.components.FluidInsetCard
import com.flow.todo.ui.theme.FluidSpacing
import com.flow.todo.ui.theme.FluidTheme

/**
 * The lightweight task list: everything organized by when it's due.
 * Overdue → Today → Upcoming (by day) → No date, with today's completions tucked at the bottom.
 */
@Composable
fun TasksScreen(
    sections: TaskSections,
    healthSnapshot: HealthDailySnapshot,
    completedTodayCount: Int,
    totalTodayCount: Int,
    dailyProgress: Float,
    onToggleComplete: (String) -> Unit,
    onToggleFlag: (String) -> Unit,
    onSetDueDate: (taskId: String, epochDay: Long?) -> Unit,
    onDeleteTask: (String) -> Unit,
    onUpdateText: (taskId: String, title: String, notes: String) -> Unit,
    onToggleSubtask: (String, String) -> Unit,
    onAddSubtask: (String, String) -> Unit,
    onDeleteSubtask: (String, String) -> Unit,
    onQuickAdd: () -> Unit,
    coachCard: (@Composable () -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    listState: LazyListState = rememberLazyListState(),
    modifier: Modifier = Modifier
) {
    val colors = FluidTheme.colors
    var editing by remember { mutableStateOf<TaskItem?>(null) }
    var showDone by rememberSaveable { mutableStateOf(false) }
    var showNoDate by rememberSaveable { mutableStateOf(true) }

    @Composable
    fun TaskItemRow(task: TaskItem) {
        TaskRow(
            task = task,
            onToggleComplete = { onToggleComplete(task.id) },
            onToggleFlag = { onToggleFlag(task.id) },
            onSetDueDate = { day -> onSetDueDate(task.id, day) },
            onDelete = { onDeleteTask(task.id) },
            onToggleSubtask = { sub -> onToggleSubtask(task.id, sub) },
            onAddSubtask = { sub -> onAddSubtask(task.id, sub) },
            onDeleteSubtask = { sub -> onDeleteSubtask(task.id, sub) },
            healthSnapshot = healthSnapshot,
            onEdit = { editing = task },
            modifier = Modifier.padding(bottom = 6.dp)
        )
    }

    fun LazyListScope.section(
        key: String,
        title: String,
        tasks: List<TaskItem>,
        titleColor: Color? = null,
        collapsed: Boolean = false,
        onToggleCollapsed: (() -> Unit)? = null
    ) {
        if (tasks.isEmpty()) return
        item(key = "header_$key") {
            SectionHeader(
                title = title,
                count = tasks.size,
                color = titleColor,
                collapsed = collapsed,
                onClick = onToggleCollapsed
            )
        }
        if (!collapsed) {
            items(tasks, key = { "${key}_${it.id}" }) { TaskItemRow(it) }
        }
    }

    LazyColumn(
        state = listState,
        contentPadding = contentPadding,
        modifier = modifier.fillMaxSize().padding(horizontal = FluidSpacing.lg)
    ) {
        item { Spacer(Modifier.height(FluidSpacing.sm)) }

        if (coachCard != null) {
            item(key = "ai_coach_card") {
                coachCard()
                Spacer(Modifier.height(FluidSpacing.md))
            }
        }

        // Compact progress line
        item(key = "today_progress") {
            Column(Modifier.fillMaxWidth().padding(bottom = FluidSpacing.md)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (totalTodayCount == 0) "Nothing due today" else "$completedTodayCount of $totalTodayCount done today",
                        fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = colors.textPrimary,
                        modifier = Modifier.weight(1f)
                    )
                    Text("${(dailyProgress.coerceIn(0f, 1f) * 100).toInt()}%", fontSize = 13.sp, color = colors.accent, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(6.dp))
                FluidGlowingProgressBar(progress = dailyProgress, modifier = Modifier.fillMaxWidth())
            }
        }

        if (sections.openCount == 0) {
            item(key = "empty") {
                FluidInsetCard(Modifier.fillMaxWidth().clickable(onClick = onQuickAdd)) {
                    Text("All clear ✨", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary)
                    Text("Tap + to add a task. Try \"pay rent friday\" or \"call mom tomorrow\".", fontSize = 13.sp, color = colors.textSecondary)
                }
                Spacer(Modifier.height(FluidSpacing.md))
            }
        }

        section("overdue", "Overdue", sections.overdue, titleColor = colors.danger)
        section("today", "Today", sections.today, titleColor = colors.accent)
        sections.upcoming.forEach { group ->
            section("day_${group.epochDay}", group.label, group.tasks)
        }
        section(
            "nodate", "No date", sections.noDate,
            collapsed = !showNoDate, onToggleCollapsed = { showNoDate = !showNoDate }
        )
        section(
            "done", "Done today", sections.doneToday, titleColor = colors.success,
            collapsed = !showDone, onToggleCollapsed = { showDone = !showDone }
        )

        item { Spacer(Modifier.height(96.dp)) }
    }

    editing?.let { task ->
        var title by remember(task.id) { mutableStateOf(task.title) }
        var notes by remember(task.id) { mutableStateOf(task.notes) }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text("Edit task") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Title") }, singleLine = true)
                    OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("Notes") }, minLines = 2)
                }
            },
            confirmButton = {
                TextButton(enabled = title.isNotBlank(), onClick = {
                    onUpdateText(task.id, title, notes)
                    editing = null
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { editing = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun SectionHeader(
    title: String,
    count: Int,
    color: Color?,
    collapsed: Boolean,
    onClick: (() -> Unit)?
) {
    val colors = FluidTheme.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(top = FluidSpacing.sm, bottom = 6.dp)
    ) {
        Text(
            title.uppercase(), fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp,
            color = color ?: colors.textSecondary
        )
        Text("  $count", fontSize = 12.sp, color = colors.textTertiary, fontWeight = FontWeight.SemiBold)
        if (onClick != null) {
            Spacer(Modifier.weight(1f))
            Text(if (collapsed) "Show" else "Hide", fontSize = 12.sp, color = colors.textTertiary)
        }
    }
}
