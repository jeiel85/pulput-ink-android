package io.pulpit.ink.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sermon_jobs")
data class SermonJob(
    @PrimaryKey val id: String,
    val title: String,
    val audioPath: String,
    val durationSec: Double,
    val status: String, // "Recording", "Transcribing", "Done", "Failed"
    val errorMessage: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val outline: String? = null,
    val summary: String? = null,
    val keywords: String? = null,
    val bibleRefs: String? = null,
    val properNouns: String? = null
)
