package com.flambo.recorder.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface RecordingDao {

    @Query("SELECT * FROM recordings WHERE isTrashed = 0 ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<Recording>>

    @Query("SELECT * FROM recordings WHERE isTrashed = 1 ORDER BY trashedAt DESC")
    fun observeTrash(): Flow<List<Recording>>

    @Query("SELECT * FROM recordings WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): Recording?

    @Query("SELECT * FROM recordings WHERE id = :id LIMIT 1")
    fun observeById(id: Long): Flow<Recording?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(recording: Recording): Long

    @Update
    suspend fun update(recording: Recording)

    @Query("DELETE FROM recordings WHERE id = :id")
    suspend fun deletePermanently(id: Long)

    @Query("DELETE FROM recordings WHERE isTrashed = 1 AND trashedAt < :cutoff")
    suspend fun purgeOldTrash(cutoff: Long): Int

    @Query("SELECT * FROM recordings WHERE isTrashed = 0 AND (title LIKE '%' || :query || '%' OR tags LIKE '%' || :query || '%' OR transcriptText LIKE '%' || :query || '%') ORDER BY createdAt DESC")
    fun search(query: String): Flow<List<Recording>>

    @Query("UPDATE recordings SET transcriptText = :transcript WHERE id = :id")
    suspend fun updateTranscript(id: Long, transcript: String)
}
