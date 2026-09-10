package com.flambo.recorder.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

// v1 -> v2 adds the saved transcript column, preserving existing recordings.
private val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE recordings ADD COLUMN transcriptText TEXT NOT NULL DEFAULT ''")
    }
}

// v2 -> v3 adds the cleaned-audio path for "Clean audio".
private val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE recordings ADD COLUMN enhancedPath TEXT NOT NULL DEFAULT ''")
    }
}

@Database(entities = [Recording::class], version = 3, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun recordingDao(): RecordingDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "flambo.db"
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                 .fallbackToDestructiveMigration()
                 .build().also { INSTANCE = it }
            }
    }
}
