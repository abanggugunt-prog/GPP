package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "generated_media")
data class GeneratedMedia(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val type: String, // "IMAGE" or "VIDEO"
    val prompt: String,
    val mediaUrl: String,
    val localUri: String? = null,
    val aspectRatio: String = "1:1",
    val durationSeconds: Int = 4,
    val timestamp: Long = System.currentTimeMillis(),
    val status: String = "COMPLETED", // "QUEUED", "PROCESSING", "COMPLETED", "FAILED"
    val errorMessage: String? = null,
    val backendSource: String = "Pollinations AI"
)
