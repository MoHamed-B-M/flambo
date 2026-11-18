package com.pranshulgg.recordmaster.core.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [Recording::class],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun recordingDao(): RecordingDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE recordings ADD COLUMN authorName TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE recordings ADD COLUMN customName TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE recordings ADD COLUMN fileSize INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE recordings ADD COLUMN duration INTEGER NOT NULL DEFAULT 0")
                db.execSQL("UPDATE recordings SET duration = durationMs")
            }
        }

        fun get(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "recordmaster.db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .also { INSTANCE = it }
            }
    }
}
