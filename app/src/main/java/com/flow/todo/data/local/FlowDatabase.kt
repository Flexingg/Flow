package com.flow.todo.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [TaskEntity::class, ProjectEntity::class],
    version = 2,
    exportSchema = false
)
abstract class FlowDatabase : RoomDatabase() {

    abstract fun flowDao(): FlowDao

    companion object {
        @Volatile
        private var INSTANCE: FlowDatabase? = null

        fun getInstance(context: Context): FlowDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    FlowDatabase::class.java,
                    "flow_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
