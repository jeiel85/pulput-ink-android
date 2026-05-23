package com.example.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "sermon_segments",
    foreignKeys = [
        ForeignKey(
            entity = SermonJob::class,
            parentColumns = ["id"],
            childColumns = ["jobId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["jobId"])]
)
data class SermonSegment(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val jobId: String,
    val startSec: Double,
    val endSec: Double,
    val text: String,
    val speaker: String? = null
)
