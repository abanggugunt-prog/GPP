package com.example.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.util.Base64
import com.example.data.local.BackendPreferences
import com.example.data.local.HitutDao
import com.example.data.model.BackendHealthInfo
import com.example.data.model.BackendHealthStatus
import com.example.data.model.ChatMessage
import com.example.data.model.ChatSession
import com.example.data.model.GeneratedMedia
import com.example.data.model.ImageBackendType
import com.example.data.model.VideoBackendTarget
import com.example.data.model.VideoJobStatus
import com.example.data.remote.GeminiChatClient
import com.example.data.remote.ImageGenClient
import com.example.data.remote.QuotaExceededException
import com.example.data.remote.VideoGenClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.UUID

class HitutRepository(
    private val context: Context,
    private val dao: HitutDao,
    val preferences: BackendPreferences
) {
    private val geminiClient = GeminiChatClient()
    private val imageClient = ImageGenClient(context)
    private val videoClient = VideoGenClient(context)

    // Database Streams
    fun getSessions(): Flow<List<ChatSession>> = dao.getAllSessions()
    fun getMessages(sessionId: String): Flow<List<ChatMessage>> = dao.getMessagesForSession(sessionId)
    fun getSavedMedia(): Flow<List<GeneratedMedia>> = dao.getAllMedia()

    // --- CHAT OPERATIONS ---

    suspend fun createNewSession(firstPrompt: String = "Percakapan Baru"): ChatSession = withContext(Dispatchers.IO) {
        val title = if (firstPrompt.length > 30) firstPrompt.take(30) + "..." else firstPrompt
        val session = ChatSession(
            title = title,
            modelUsed = preferences.chatModel.value
        )
        dao.insertSession(session)
        session
    }

    suspend fun deleteSession(sessionId: String) = withContext(Dispatchers.IO) {
        dao.deleteMessagesForSession(sessionId)
        dao.deleteSession(sessionId)
    }

    suspend fun clearAllHistory() = withContext(Dispatchers.IO) {
        dao.clearAllMessages()
        dao.clearAllSessions()
    }

    suspend fun sendMessage(
        sessionId: String,
        userPrompt: String
    ): Result<ChatMessage> = withContext(Dispatchers.IO) {
        // 1. Save user message to database
        val userMsg = ChatMessage(
            sessionId = sessionId,
            role = "user",
            content = userPrompt
        )
        dao.insertMessage(userMsg)

        // 2. Fetch history for context
        val history = dao.getMessagesForSession(sessionId).first()

        // 3. Prepare AI placeholder message
        val aiMsgId = UUID.randomUUID().toString()
        val pendingAiMsg = ChatMessage(
            id = aiMsgId,
            sessionId = sessionId,
            role = "model",
            content = "Mengetik...",
            status = "PENDING"
        )
        dao.insertMessage(pendingAiMsg)

        val apiKey = preferences.getEffectiveGeminiApiKey()
        val model = preferences.chatModel.value

        val result = geminiClient.generateResponse(
            apiKey = apiKey,
            modelName = model,
            history = history.filter { it.id != aiMsgId },
            newPrompt = userPrompt
        )

        return@withContext if (result.isSuccess) {
            val responseText = result.getOrThrow()
            val completedMsg = pendingAiMsg.copy(
                content = responseText,
                status = "SUCCESS"
            )
            dao.updateMessage(completedMsg)
            // Update session timestamp & title if initial
            val session = dao.getSessionById(sessionId)
            if (session != null) {
                val newTitle = if (session.title == "Percakapan Baru") {
                    if (userPrompt.length > 25) userPrompt.take(25) + "..." else userPrompt
                } else session.title
                dao.updateSession(session.copy(title = newTitle, lastUpdatedAt = System.currentTimeMillis()))
            }
            Result.success(completedMsg)
        } else {
            val ex = result.exceptionOrNull()
            val isQuota = ex is QuotaExceededException
            val errorMsg = pendingAiMsg.copy(
                content = ex?.message ?: "Terjadi kesalahan saat memproses permintaan.",
                status = if (isQuota) "QUOTA_LIMITED" else "ERROR",
                errorMessage = ex?.localizedMessage
            )
            dao.updateMessage(errorMsg)
            Result.failure(ex ?: Exception("Unknown error"))
        }
    }

    // --- IMAGE GENERATION ---

    suspend fun generateImage(
        prompt: String,
        aspectRatio: String = "1:1",
        referenceBitmap: Bitmap? = null
    ): Result<GeneratedMedia> = withContext(Dispatchers.IO) {
        val backendType = preferences.imageBackendType.value
        val geminiKey = preferences.getEffectiveGeminiApiKey()
        val colabUrl = preferences.getActiveVideoEndpointUrl()

        var refBase64: String? = null
        if (referenceBitmap != null) {
            val stream = ByteArrayOutputStream()
            referenceBitmap.compress(Bitmap.CompressFormat.JPEG, 80, stream)
            refBase64 = Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
        }

        val result = imageClient.generateImage(
            prompt = prompt,
            aspectRatio = aspectRatio,
            backendType = backendType,
            geminiApiKey = geminiKey,
            customColabUrl = colabUrl,
            referenceImageBase64 = refBase64
        )

        if (result.isSuccess) {
            val (url, localPath) = result.getOrThrow()
            val media = GeneratedMedia(
                type = "IMAGE",
                prompt = prompt,
                mediaUrl = url,
                localUri = localPath,
                aspectRatio = aspectRatio,
                status = "COMPLETED",
                backendSource = backendType.name
            )
            dao.insertMedia(media)
            Result.success(media)
        } else {
            Result.failure(result.exceptionOrNull() ?: Exception("Gagal menghasilkan gambar."))
        }
    }

    suspend fun saveImageToGallery(localPath: String): Result<String> {
        return imageClient.saveImageToGallery(localPath)
    }

    // --- VIDEO GENERATION & FAILOVER ---

    suspend fun checkVideoBackendHealth(target: VideoBackendTarget): BackendHealthInfo {
        val url = when (target) {
            VideoBackendTarget.PRIMARY_COLAB -> preferences.primaryVideoUrl.value
            VideoBackendTarget.BACKUP_KAGGLE -> preferences.backupVideoUrl.value
            VideoBackendTarget.CUSTOM_URL -> preferences.primaryVideoUrl.value
        }
        return videoClient.checkHealth(url)
    }

    suspend fun checkChatBackendHealth(): BackendHealthInfo {
        val key = preferences.getEffectiveGeminiApiKey()
        val model = preferences.chatModel.value
        val (ok, latency) = geminiClient.ping(key, model)
        return BackendHealthInfo(
            target = "Google Gemini ($model)",
            status = if (ok) BackendHealthStatus.ONLINE else BackendHealthStatus.OFFLINE,
            latencyMs = latency,
            message = if (ok) "Siap digunakan ($latency ms)" else "Koneksi gagal atau API Key belum disetel",
            lastChecked = System.currentTimeMillis()
        )
    }

    /**
     * Resolves which video backend to use, executing automatic failover if primary is unreachable.
     */
    suspend fun resolveActiveVideoBackend(): Pair<String, String> = withContext(Dispatchers.IO) {
        val currentTarget = preferences.activeVideoTarget.value
        val primaryUrl = preferences.primaryVideoUrl.value
        val backupUrl = preferences.backupVideoUrl.value
        val autoFailover = preferences.autoFailoverEnabled.value

        // If target is primary, check if online
        if (currentTarget == VideoBackendTarget.PRIMARY_COLAB) {
            if (primaryUrl.isNotBlank()) {
                val health = videoClient.checkHealth(primaryUrl)
                if (health.status == BackendHealthStatus.ONLINE) {
                    return@withContext Pair(primaryUrl, "Google Colab (Utama)")
                }
            }

            // Primary is not online, check backup if auto-failover enabled
            if (autoFailover && backupUrl.isNotBlank()) {
                val backupHealth = videoClient.checkHealth(backupUrl)
                if (backupHealth.status == BackendHealthStatus.ONLINE) {
                    // Switch to backup
                    preferences.setActiveVideoTarget(VideoBackendTarget.BACKUP_KAGGLE)
                    return@withContext Pair(backupUrl, "Kaggle Notebook (Cadangan Otomatis)")
                }
            }
            return@withContext Pair(primaryUrl, "Google Colab (Utama - Offline)")
        } else {
            // Target is backup
            if (backupUrl.isNotBlank()) {
                val backupHealth = videoClient.checkHealth(backupUrl)
                if (backupHealth.status == BackendHealthStatus.ONLINE) {
                    return@withContext Pair(backupUrl, "Kaggle Notebook (Cadangan)")
                }
            }
            // Check if primary recovered
            if (autoFailover && primaryUrl.isNotBlank()) {
                val primaryHealth = videoClient.checkHealth(primaryUrl)
                if (primaryHealth.status == BackendHealthStatus.ONLINE) {
                    preferences.setActiveVideoTarget(VideoBackendTarget.PRIMARY_COLAB)
                    return@withContext Pair(primaryUrl, "Google Colab (Pulih Otomatis)")
                }
            }
            return@withContext Pair(backupUrl, "Kaggle Notebook (Cadangan - Offline)")
        }
    }

    suspend fun submitTextToVideo(
        prompt: String,
        durationSeconds: Int = 4,
        aspectRatio: String = "16:9"
    ): Result<String> {
        val (backendUrl, _) = resolveActiveVideoBackend()
        return videoClient.submitTextToVideo(backendUrl, prompt, durationSeconds, aspectRatio)
    }

    suspend fun submitImageToVideo(
        imageBitmap: Bitmap,
        motionPrompt: String,
        durationSeconds: Int = 4,
        aspectRatio: String = "16:9"
    ): Result<String> {
        val (backendUrl, _) = resolveActiveVideoBackend()
        val stream = ByteArrayOutputStream()
        imageBitmap.compress(Bitmap.CompressFormat.JPEG, 80, stream)
        val base64 = Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
        return videoClient.submitImageToVideo(backendUrl, base64, motionPrompt, durationSeconds, aspectRatio)
    }

    suspend fun pollVideoJob(jobId: String): Result<VideoJobStatus> {
        val backendUrl = preferences.getActiveVideoEndpointUrl()
        return videoClient.pollJobStatus(backendUrl, jobId)
    }

    suspend fun saveVideoMediaRecord(
        prompt: String,
        videoUrl: String,
        durationSeconds: Int,
        aspectRatio: String
    ): GeneratedMedia = withContext(Dispatchers.IO) {
        val media = GeneratedMedia(
            type = "VIDEO",
            prompt = prompt,
            mediaUrl = videoUrl,
            aspectRatio = aspectRatio,
            durationSeconds = durationSeconds,
            status = "COMPLETED",
            backendSource = preferences.activeVideoTarget.value.name
        )
        dao.insertMedia(media)
        media
    }

    suspend fun downloadVideoToStorage(videoUrl: String): Result<String> {
        return videoClient.downloadVideoToStorage(videoUrl)
    }
}
