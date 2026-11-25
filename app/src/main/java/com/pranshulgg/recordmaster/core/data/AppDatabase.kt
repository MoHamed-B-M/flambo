package com.pranshulgg.recordmaster.core.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [Recording::class, Bookmark::class],
    version = 4,
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

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `bookmarks` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`recordingId` INTEGER NOT NULL, " +
                        "`timestampMs` INTEGER NOT NULL, " +
                        "`note` TEXT, " +
                        "FOREIGN KEY(`recordingId`) REFERENCES `recordings`(`id`) " +
                        "ON UPDATE NO ACTION ON DELETE CASCADE)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_bookmarks_recordingId` " +
                        "ON `bookmarks` (`recordingId`)"
                )
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Nullable columns keep existing rows as "no location" instead of
                // fabricating coordinates at (0, 0).
                db.execSQL("ALTER TABLE recordings ADD COLUMN latitude REAL")
                db.execSQL("ALTER TABLE recordings ADD COLUMN longitude REAL")
                db.execSQL("ALTER TABLE recordings ADD COLUMN locatedAt INTEGER")
            }
        }

        fun get(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "recordmaster.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                    .build()
                    .also { INSTANCE = it }
            }
    }
}
