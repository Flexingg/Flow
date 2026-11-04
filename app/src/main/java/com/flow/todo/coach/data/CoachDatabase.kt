package com.flow.todo.coach.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import com.flow.todo.coach.CoachLogEntry
import com.flow.todo.coach.CoachPersona
import com.flow.todo.coach.CoachPersonas
import com.flow.todo.coach.CoachTone
import com.flow.todo.coach.TopicStatus
import com.flow.todo.coach.TopicType
import kotlinx.coroutines.flow.Flow

/**
 * What the coach has commented on. Only topic identity/state is stored (never the message text)
 * so the selector can avoid repeating itself without hoarding conversation content.
 *
 * Lives in its own database file so the main Flow database (which uses destructive migration)
 * is not touched by this feature.
 */
@Entity(tableName = "coach_log", indices = [Index("topicKey"), Index("timeMillis")])
data class CoachLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timeMillis: Long,
    val topicKey: String,
    val label: String,
    val typeName: String,
    val snapshot: Double,
    val statusName: String,
    val personaId: String,
    val toneName: String
)

@Entity(tableName = "coach_personas")
data class CoachPersonaEntity(
    @PrimaryKey val id: String,
    val name: String,
    val emoji: String,
    val prompt: String,
    val edge: Int,
    val createdAt: Long
)

fun CoachLogEntity.toDomain() = CoachLogEntry(
    timeMillis = timeMillis,
    topicKey = topicKey,
    type = runCatching { TopicType.valueOf(typeName) }.getOrDefault(TopicType.GENERAL),
    snapshot = snapshot,
    status = runCatching { TopicStatus.valueOf(statusName) }.getOrDefault(TopicStatus.INFO),
    personaId = personaId,
    tone = runCatching { CoachTone.valueOf(toneName) }.getOrDefault(CoachTone.STEADY)
)

fun CoachPersonaEntity.toDomain(): CoachPersona = CoachPersonas.customPersona(id, name, emoji, prompt, edge)

@Dao
interface CoachDao {
    @Insert
    suspend fun insertLog(rows: List<CoachLogEntity>)

    @Query("SELECT * FROM coach_log WHERE timeMillis >= :since ORDER BY timeMillis DESC")
    suspend fun logSince(since: Long): List<CoachLogEntity>

    @Query("DELETE FROM coach_log WHERE timeMillis < :before")
    suspend fun pruneLog(before: Long)

    @Query("DELETE FROM coach_log")
    suspend fun clearLog()

    @Query("SELECT COUNT(*) FROM coach_log")
    fun observeLogCount(): Flow<Int>

    @Query("SELECT * FROM coach_personas ORDER BY createdAt ASC")
    fun observePersonas(): Flow<List<CoachPersonaEntity>>

    @Query("SELECT * FROM coach_personas ORDER BY createdAt ASC")
    suspend fun getPersonas(): List<CoachPersonaEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPersona(persona: CoachPersonaEntity)

    @Query("DELETE FROM coach_personas WHERE id = :id")
    suspend fun deletePersona(id: String)
}

@Database(entities = [CoachLogEntity::class, CoachPersonaEntity::class], version = 1, exportSchema = false)
abstract class CoachDatabase : RoomDatabase() {
    abstract fun coachDao(): CoachDao

    companion object {
        @Volatile
        private var INSTANCE: CoachDatabase? = null

        fun getInstance(context: Context): CoachDatabase = INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(context.applicationContext, CoachDatabase::class.java, "flow_coach.db")
                .fallbackToDestructiveMigration()
                .build()
                .also { INSTANCE = it }
        }
    }
}
