package io.pulpit.ink.data.db

import androidx.room.*
import io.pulpit.ink.data.model.SermonJob
import io.pulpit.ink.data.model.SermonSegment
import kotlinx.coroutines.flow.Flow

@Dao
interface SermonDao {
    @Query("SELECT * FROM sermon_jobs ORDER BY createdAt DESC")
    fun getAllJobsFlow(): Flow<List<SermonJob>>

    @Query("SELECT * FROM sermon_jobs WHERE id = :jobId")
    fun getJobByIdFlow(jobId: String): Flow<SermonJob?>

    @Query("SELECT * FROM sermon_jobs WHERE id = :jobId")
    suspend fun getJobById(jobId: String): SermonJob?

    @Query("SELECT * FROM sermon_segments WHERE jobId = :jobId ORDER BY startSec ASC")
    fun getSegmentsByJobIdFlow(jobId: String): Flow<List<SermonSegment>>

    @Query("SELECT * FROM sermon_segments WHERE jobId = :jobId ORDER BY startSec ASC")
    suspend fun getSegmentsByJobId(jobId: String): List<SermonSegment>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertJob(job: SermonJob)

    @Update
    suspend fun updateJob(job: SermonJob)

    @Query("DELETE FROM sermon_jobs WHERE id = :jobId")
    suspend fun deleteJobById(jobId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSegments(segments: List<SermonSegment>)

    @Query("UPDATE sermon_segments SET text = :text WHERE id = :segmentId")
    suspend fun updateSegmentText(segmentId: Int, text: String)

    @Query("DELETE FROM sermon_segments WHERE jobId = :jobId")
    suspend fun deleteSegmentsByJobId(jobId: String)
}
