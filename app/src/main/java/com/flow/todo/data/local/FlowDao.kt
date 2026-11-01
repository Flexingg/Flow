package com.flow.todo.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface FlowDao {
    @Query("SELECT * FROM tasks ORDER BY isCompleted ASC, isFlagged DESC, createdAt DESC")
    fun observeTasks(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM projects ORDER BY createdAt ASC")
    fun observeProjects(): Flow<List<ProjectEntity>>

    @Query("SELECT * FROM goals ORDER BY createdAt ASC")
    fun observeGoals(): Flow<List<GoalEntity>>

    @Query("SELECT * FROM daily_history ORDER BY epochDay ASC")
    fun observeDailyHistory(): Flow<List<DailyHistoryEntity>>

    @Query("SELECT * FROM tasks")
    suspend fun getAllTasks(): List<TaskEntity>

    @Query("SELECT * FROM projects")
    suspend fun getAllProjects(): List<ProjectEntity>

    @Query("SELECT * FROM goals")
    suspend fun getAllGoals(): List<GoalEntity>

    @Query("SELECT * FROM daily_history")
    suspend fun getAllDailyHistory(): List<DailyHistoryEntity>

    @Query("SELECT COUNT(*) FROM projects")
    suspend fun getProjectCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTask(task: TaskEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTasks(tasks: List<TaskEntity>)

    @Query("DELETE FROM tasks WHERE id = :taskId")
    suspend fun deleteTask(taskId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProject(project: ProjectEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProjects(projects: List<ProjectEntity>)

    @Query("DELETE FROM projects WHERE id = :projectId")
    suspend fun deleteProject(projectId: String)

    @Query("UPDATE tasks SET projectId = NULL WHERE projectId = :projectId")
    suspend fun clearProjectFromTasks(projectId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertGoal(goal: GoalEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertGoals(goals: List<GoalEntity>)

    @Query("DELETE FROM goals WHERE id = :goalId")
    suspend fun deleteGoal(goalId: String)

    @Query("DELETE FROM goals WHERE projectId = :projectId")
    suspend fun deleteGoalsForProject(projectId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDailyHistory(record: DailyHistoryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDailyHistoryList(records: List<DailyHistoryEntity>)
}
