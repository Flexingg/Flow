package com.flow.todo.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [TaskEntity::class, ProjectEntity::class, GoalEntity::class, DailyHistoryEntity::class, CalendarEventEntity::class],
    version = 10,
    exportSchema = false
)
abstract class FlowDatabase : RoomDatabase() {

    abstract fun flowDao(): FlowDao

    companion object {
        /** v10: task time blocks (start/duration) and Eisenhower urgency. Nullable, so no defaults. */
        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tasks ADD COLUMN startMinute INTEGER")
                db.execSQL("ALTER TABLE tasks ADD COLUMN durationMinutes INTEGER")
                db.execSQL("ALTER TABLE tasks ADD COLUMN urgency INTEGER")
            }
        }

        @Volatile
        private var INSTANCE: FlowDatabase? = null

        fun getInstance(context: Context): FlowDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    FlowDatabase::class.java,
                    "flow_database"
                )
                    // Real migrations first: the destructive fallback would wipe every task.
                    .addMigrations(MIGRATION_9_10)
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
