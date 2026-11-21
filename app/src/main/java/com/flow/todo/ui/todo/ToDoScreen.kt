package com.flow.todo.ui.todo

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flow.todo.domain.model.HealthDailySnapshot
import com.flow.todo.domain.model.TaskItem
import com.flow.todo.domain.tasks.TaskSections
import com.flow.todo.ui.screens.TasksScreen
import com.flow.todo.ui.theme.FluidShapes
import com.flow.todo.ui.theme.FluidSpacing
import com.flow.todo.ui.theme.FluidTheme
import com.flow.todo.ui.theme.GlassLevel
import com.flow.todo.ui.theme.fluidGlass

/** To-Do tab: the due-date list or the Eisenhower matrix. */
@Composable
fun ToDoScreen(
    sections: TaskSections,
    openTasks: List<TaskItem>,
    healthSnapshot: HealthDailySnapshot,
    completedTodayCount: Int,
    totalTodayCount: Int,
    dailyProgress: Float,
    onToggleComplete: (String) -> Unit,
    onToggleFlag: (String) -> Unit,
    onSetDueDate: (String, Long?) -> Unit,
    onDeleteTask: (String) -> Unit,
    onUpdateText: (String, String, String) -> Unit,
    onToggleSubtask: (String, String) -> Unit,
    onAddSubtask: (String, String) -> Unit,
    onDeleteSubtask: (String, String) -> Unit,
    onMoveToQuadrant: (String, Boolean, Boolean) -> Unit,
    onQuickAdd: () -> Unit,
    coachCard: (@Composable () -> Unit)?,
    contentPadding: PaddingValues,
    listState: LazyListState
) {
    var matrix by rememberSaveable { mutableStateOf(false) }
    val inner = PaddingValues(top = 0.dp, bottom = contentPadding.calculateBottomPadding())

    Column(Modifier.fillMaxSize().padding(top = contentPadding.calculateTopPadding())) {
        Segmented(matrix = matrix, onChange = { matrix = it })
        Box(Modifier.weight(1f)) {
            if (matrix) {
                EisenhowerMatrix(
                    openTasks = openTasks,
                    onToggleComplete = onToggleComplete,
                    onMoveToQuadrant = onMoveToQuadrant,
                    contentPadding = PaddingValues(top = FluidSpacing.sm, bottom = contentPadding.calculateBottomPadding())
                )
            } else {
                TasksScreen(
                    sections = sections,
                    healthSnapshot = healthSnapshot,
                    completedTodayCount = completedTodayCount,
                    totalTodayCount = totalTodayCount,
                    dailyProgress = dailyProgress,
                    onToggleComplete = onToggleComplete,
                    onToggleFlag = onToggleFlag,
                    onSetDueDate = onSetDueDate,
                    onDeleteTask = onDeleteTask,
                    onUpdateText = onUpdateText,
                    onToggleSubtask = onToggleSubtask,
                    onAddSubtask = onAddSubtask,
                    onDeleteSubtask = onDeleteSubtask,
                    onQuickAdd = onQuickAdd,
                    coachCard = coachCard,
                    contentPadding = inner,
                    listState = listState
                )
            }
        }
    }
}

@Composable
private fun Segmented(matrix: Boolean, onChange: (Boolean) -> Unit) {
    val colors = FluidTheme.colors
    Row(
        Modifier
            .padding(horizontal = FluidSpacing.lg, vertical = FluidSpacing.xs)
            .fillMaxWidth()
            .fluidGlass(GlassLevel.THIN, FluidShapes.full)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        listOf(false to "List", true to "Matrix").forEach { (value, label) ->
            val selected = matrix == value
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .weight(1f)
                    .clip(FluidShapes.full)
                    .background(if (selected) colors.accent.copy(alpha = 0.18f) else Color.Transparent)
                    .clickable { onChange(value) }
                    .padding(vertical = 8.dp)
            ) {
                Text(label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = if (selected) colors.accent else colors.textSecondary)
            }
        }
    }
}
