package com.example.data.remote

import android.content.Context
import android.os.Environment
import android.util.Base64
import android.util.Log
import com.example.data.model.BackendHealthInfo
import com.example.data.model.BackendHealthStatus
import com.example.data.model.VideoJobStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

class VideoGenClient(private val context: Context) {
    private val client = NetworkClient.okHttpClient
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    /**
     * Pings the video backend health endpoint (/health or /).
     */
    suspend fun checkHealth(baseUrl: String): BackendHealthInfo = withContext(Dispatchers.IO) {
        val trimmed = baseUrl.trim().removeSuffix("/")
        if (trimmed.isBlank()) {
            return@withContext BackendHealthInfo(
                target = baseUrl,
                status = BackendHealthStatus.OFFLINE,
                message = "URL backend belum diatur",
                lastChecked = System.currentTimeMillis()
            )
        }

        val start = System.currentTimeMillis()
        try {
            val healthUrl = "$trimmed/health"
            val request = Request.Builder().url(healthUrl).get().build()
            val response = client.newCall(request).execute()
            val latency = System.currentTimeMillis() - start

            if (response.isSuccessful) {
                val body = response.body?.string() ?: ""
                val msg = try {
                    val json = JSONObject(body)
                    json.optString("gpu_name", "Online & Siap")
                } catch (e: Exception) {
                    "Online ($latency ms)"
                }
                BackendHealthInfo(
                    target = trimmed,
                    status = BackendHealthStatus.ONLINE,
                    latencyMs = latency,
                    message = msg,
                    lastChecked = System.currentTimeMillis()
                )
            } else {
                BackendHealthInfo(
                    target = trimmed,
                    status = BackendHealthStatus.OFFLINE,
                    latencyMs = latency,
                    message = "HTTP ${response.code}: Backend merespons gagal",
                    lastChecked = System.currentTimeMillis()
                )
            }
        } catch (e: Exception) {
            val latency = System.currentTimeMillis() - start
            BackendHealthInfo(
                target = trimmed,
                status = BackendHealthStatus.OFFLINE,
                latencyMs = latency,
                message = "Tidak dapat terhubung (${e.localizedMessage ?: "Timeout"})",
                lastChecked = System.currentTimeMillis()
            )
        }
    }

    /**
     * Submit Text-to-Video Job
     */
    suspend fun submitTextToVideo(
        baseUrl: String,
        prompt: String,
        durationSeconds: Int = 4,
        aspectRatio: String = "16:9"
    ): Result<String> = withContext(Dispatchers.IO) {
        val trimmed = baseUrl.trim().removeSuffix("/")
        if (trimmed.isBlank()) {
            return@withContext Result.failure(
                Exception("URL backend video belum diatur. Silakan jalankan Google Colab HITUT AI dan masukkan URL publiknya.")
            )
        }

        try {
            val url = "$trimmed/generate-text-to-video"
            val json = JSONObject().apply {
                put("prompt", prompt)
                put("duration_seconds", durationSeconds)
                put("aspect_ratio", aspectRatio)
            }

            val request = Request.Builder()
                .url(url)
                .post(json.toString().toRequestBody(jsonMediaType))
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("Gagal membuat pekerjaan video (HTTP ${response.code}): $body"))
            }

            val respJson = JSONObject(body)
            val jobId = respJson.optString("job_id", "")
            if (jobId.isBlank()) {
                return@withContext Result.failure(Exception("Format respons backend tidak mengandung 'job_id'."))
            }

            Result.success(jobId)
        } catch (e: Exception) {
            Log.e("VideoGenClient", "submitTextToVideo error", e)
            Result.failure(e)
        }
    }

    /**
     * Submit Image-to-Video Job
     */
    suspend fun submitImageToVideo(
        baseUrl: String,
        imageBase64: String,
        motionPrompt: String,
        durationSeconds: Int = 4,
        aspectRatio: String = "16:9"
    ): Result<String> = withContext(Dispatchers.IO) {
        val trimmed = baseUrl.trim().removeSuffix("/")
        if (trimmed.isBlank()) {
            return@withContext Result.failure(
                Exception("URL backend video belum diatur. Silakan jalankan Google Colab HITUT AI dan masukkan URL publiknya.")
            )
        }

        try {
            val url = "$trimmed/generate-image-to-video"
            val json = JSONObject().apply {
                put("image_base64", imageBase64)
                put("motion_prompt", motionPrompt)
                put("duration_seconds", durationSeconds)
                put("aspect_ratio", aspectRatio)
            }

            val request = Request.Builder()
                .url(url)
                .post(json.toString().toRequestBody(jsonMediaType))
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("Gagal mengirim pekerjaan Image-to-Video (HTTP ${response.code}): $body"))
            }

            val respJson = JSONObject(body)
            val jobId = respJson.optString("job_id", "")
            if (jobId.isBlank()) {
                return@withContext Result.failure(Exception("Format respons backend tidak valid (tidak ada job_id)."))
            }

            Result.success(jobId)
        } catch (e: Exception) {
            Log.e("VideoGenClient", "submitImageToVideo error", e)
            Result.failure(e)
        }
    }

    /**
     * Poll Job Status
     */
    suspend fun pollJobStatus(
        baseUrl: String,
        jobId: String
    ): Result<VideoJobStatus> = withContext(Dispatchers.IO) {
        val trimmed = baseUrl.trim().removeSuffix("/")
        try {
            val url = "$trimmed/status/$jobId"
            val request = Request.Builder().url(url).get().build()
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("Status polling HTTP ${response.code}: $body"))
            }

            val json = JSONObject(body)
            val status = json.optString("status", "UNKNOWN")
            val progress = json.optInt("progress_percent", 0)
            val videoUrl = json.optString("video_url", "").ifBlank { null }
            val videoBase64 = json.optString("video_base64", "").ifBlank { null }
            val errorMsg = json.optString("error", "").ifBlank { null }

            var resolvedUrl = videoUrl
            if (resolvedUrl == null && !videoBase64.isNullOrBlank()) {
                // Save base64 video to local file
                val decoded = Base64.decode(videoBase64, Base64.DEFAULT)
                val file = File(context.filesDir, "hitut_video_${UUID.randomUUID()}.mp4")
                FileOutputStream(file).use { out -> out.write(decoded) }
                resolvedUrl = file.toURI().toString()
            } else if (resolvedUrl != null && resolvedUrl.startsWith("/")) {
                // Relative URL -> attach base URL
                resolvedUrl = "$trimmed$resolvedUrl"
            }

            Result.success(
                VideoJobStatus(
                    jobId = jobId,
                    status = status,
                    progressPercent = progress,
                    videoUrl = resolvedUrl,
                    errorMessage = errorMsg
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Download completed video to device's Movies / HITUT_AI directory
     */
    suspend fun downloadVideoToStorage(videoUrl: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val targetDir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
                "HITUT_AI"
            )
            if (!targetDir.exists()) {
                targetDir.mkdirs()
            }
            val targetFile = File(targetDir, "HITUT_VIDEO_${System.currentTimeMillis()}.mp4")

            if (videoUrl.startsWith("file://") || videoUrl.startsWith("/")) {
                val cleanPath = videoUrl.removePrefix("file://")
                val src = File(cleanPath)
                src.copyTo(targetFile, overwrite = true)
                return@withContext Result.success(targetFile.absolutePath)
            }

            val request = Request.Builder().url(videoUrl).build()
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("Gagal mengunduh file video (HTTP ${response.code})"))
            }

            val bytes = response.body?.bytes()
                ?: return@withContext Result.failure(Exception("Data video kosong"))

            FileOutputStream(targetFile).use { it.write(bytes) }
            Result.success(targetFile.absolutePath)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
