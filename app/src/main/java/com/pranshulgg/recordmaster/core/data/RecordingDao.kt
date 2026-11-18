package com.pranshulgg.recordmaster.core.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface RecordingDao {

    @Query("SELECT * FROM recordings WHERE isTrashed = 0 ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<Recording>>

    @Query("SELECT * FROM recordings WHERE isTrashed = 1 ORDER BY trashedAt DESC")
    fun observeTrash(): Flow<List<Recording>>

    @Query("SELECT * FROM recordings WHERE isTrashed = 1")
    suspend fun getTrash(): List<Recording>

    @Query("SELECT * FROM recordings ORDER BY createdAt DESC")
    suspend fun getAll(): List<Recording>

    @Query("SELECT title FROM recordings WHERE isTrashed = 0")
    suspend fun activeTitles(): List<String>

    @Query("SELECT * FROM recordings WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): Recording?

    @Query("SELECT * FROM recordings WHERE filePath = :path LIMIT 1")
    suspend fun getByPath(path: String): Recording?

    @Query("SELECT * FROM recordings WHERE filePath = :path LIMIT 1")
    fun observeByPath(path: String): Flow<Recording?>

    @Query("SELECT * FROM recordings WHERE id = :id LIMIT 1")
    fun observeById(id: Long): Flow<Recording?>

    @Query("SELECT filePath FROM recordings")
    suspend fun getAllPaths(): List<String>

    @Query("SELECT enhancedPath FROM recordings WHERE enhancedPath != ''")
    suspend fun getAllEnhancedPaths(): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(recording: Recording): Long

    @Update
    suspend fun update(recording: Recording)

    @Query("DELETE FROM recordings WHERE id = :id")
    suspend fun deletePermanently(id: Long)

    @Query("DELETE FROM recordings WHERE isTrashed = 1 AND trashedAt < :cutoff")
    suspend fun purgeOldTrash(cutoff: Long): Int

    @Query(
        "SELECT * FROM recordings WHERE isTrashed = 0 AND " +
            "(title LIKE '%' || :query || '%' OR tags LIKE '%' || :query || '%' " +
            "OR transcriptText LIKE '%' || :query || '%') ORDER BY createdAt DESC"
    )
    fun search(query: String): Flow<List<Recording>>

    @Query("UPDATE recordings SET transcriptText = :transcript WHERE id = :id")
    suspend fun updateTranscript(id: Long, transcript: String)

    @Query("UPDATE recordings SET enhancedPath = :path WHERE id = :id")
    suspend fun updateEnhancedPath(id: Long, path: String)

    // --- Bookmarks (one recording -> many markers) ------------------------

    @Transaction
    @Query("SELECT * FROM recordings WHERE id = :id LIMIT 1")
    suspend fun getWithBookmarks(id: Long): RecordingWithBookmarks?

    @Transaction
    @Query("SELECT * FROM recordings WHERE id = :id LIMIT 1")
    fun observeWithBookmarks(id: Long): Flow<RecordingWithBookmarks?>

    @Query("SELECT * FROM bookmarks WHERE recordingId = :recordingId ORDER BY timestampMs ASC")
    suspend fun getBookmarks(recordingId: Long): List<Bookmark>

    @Query("SELECT * FROM bookmarks WHERE recordingId = :recordingId ORDER BY timestampMs ASC")
    fun observeBookmarks(recordingId: Long): Flow<List<Bookmark>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBookmark(bookmark: Bookmark): Long

    @Update
    suspend fun updateBookmark(bookmark: Bookmark)

    @Delete
    suspend fun deleteBookmark(bookmark: Bookmark)

    @Query("DELETE FROM bookmarks WHERE recordingId = :recordingId")
    suspend fun deleteBookmarks(recordingId: Long)
}
