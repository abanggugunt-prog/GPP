package com.example.data.model

enum class BackendHealthStatus {
    ONLINE,
    OFFLINE,
    QUOTA_EXCEEDED,
    CHECKING,
    UNKNOWN
}

enum class ChatBackendType {
    GEMINI_DIRECT,
    CUSTOM_OPENAI_COMPATIBLE
}

enum class ImageBackendType {
    POLLINATIONS_FREE, // 100% Free Rp0, high quality, no API key needed
    GEMINI_IMAGE,      // gemini-2.5-flash-image
    CUSTOM_COLAB_SD    // Self-hosted Stable Diffusion / FLUX endpoint
}

enum class VideoBackendTarget {
    PRIMARY_COLAB,
    BACKUP_KAGGLE,
    CUSTOM_URL
}

data class BackendHealthInfo(
    val target: String,
    val status: BackendHealthStatus = BackendHealthStatus.UNKNOWN,
    val latencyMs: Long = -1,
    val message: String = "Belum dicek",
    val lastChecked: Long = 0
)

data class VideoJobStatus(
    val jobId: String,
    val status: String, // PENDING, PROCESSING, COMPLETED, FAILED
    val progressPercent: Int = 0,
    val videoUrl: String? = null,
    val errorMessage: String? = null
)
