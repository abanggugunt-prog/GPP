package com.example.data.remote

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Environment
import android.util.Base64
import android.util.Log
import com.example.data.model.ImageBackendType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.URLEncoder
import java.util.UUID
import kotlin.random.Random

class ImageGenClient(private val context: Context) {
    private val client = NetworkClient.okHttpClient
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun generateImage(
        prompt: String,
        aspectRatio: String = "1:1",
        backendType: ImageBackendType = ImageBackendType.POLLINATIONS_FREE,
        geminiApiKey: String = "",
        customColabUrl: String = "",
        referenceImageBase64: String? = null
    ): Result<Pair<String, String?>> = withContext(Dispatchers.IO) {
        try {
            when (backendType) {
                ImageBackendType.POLLINATIONS_FREE -> {
                    // 100% Free Rp0, high quality generation via Pollinations AI
                    val (width, height) = when (aspectRatio) {
                        "16:9" -> Pair(1280, 720)
                        "9:16" -> Pair(720, 1280)
                        "4:3" -> Pair(1024, 768)
                        "3:4" -> Pair(768, 1024)
                        else -> Pair(1024, 1024)
                    }

                    val seed = Random.nextInt(10000, 999999)
                    val encodedPrompt = URLEncoder.encode(prompt, "UTF-8")
                    val imageUrl = "https://image.pollinations.ai/prompt/$encodedPrompt?width=$width&height=$height&seed=$seed&nologo=true&enhance=true&model=flux"

                    // Test/Download the generated image to local app storage for permanent caching & fast display
                    val request = Request.Builder().url(imageUrl).build()
                    val response = client.newCall(request).execute()

                    if (!response.isSuccessful) {
                        return@withContext Result.failure(Exception("Gagal mengambil gambar dari Pollinations AI (HTTP ${response.code})."))
                    }

                    val bytes = response.body?.bytes()
                        ?: return@withContext Result.failure(Exception("Gambar yang diterima kosong."))

                    // Save to local cache file
                    val file = File(context.filesDir, "hitut_img_${UUID.randomUUID()}.jpg")
                    FileOutputStream(file).use { out ->
                        out.write(bytes)
                    }

                    Result.success(Pair(imageUrl, file.absolutePath))
                }

                ImageBackendType.GEMINI_IMAGE -> {
                    if (geminiApiKey.isBlank() || geminiApiKey == "MY_GEMINI_API_KEY") {
                        return@withContext Result.failure(
                            Exception("API Key Gemini belum disetel untuk backend gambar Gemini.")
                        )
                    }

                    val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash-image:generateContent?key=$geminiApiKey"

                    val rootJson = JSONObject()
                    val partsArray = JSONArray()

                    // Text prompt
                    partsArray.put(JSONObject().put("text", prompt))

                    // Reference image if provided
                    if (!referenceImageBase64.isNullOrBlank()) {
                        val inlineData = JSONObject().apply {
                            put("mimeType", "image/jpeg")
                            put("data", referenceImageBase64)
                        }
                        partsArray.put(JSONObject().put("inlineData", inlineData))
                    }

                    val contents = JSONArray().apply {
                        put(JSONObject().put("parts", partsArray))
                    }
                    rootJson.put("contents", contents)

                    val genConfig = JSONObject().apply {
                        put("responseModalities", JSONArray().apply {
                            put("TEXT")
                            put("IMAGE")
                        })
                        put("imageConfig", JSONObject().apply {
                            put("aspectRatio", aspectRatio)
                            put("imageSize", "1K")
                        })
                    }
                    rootJson.put("generationConfig", genConfig)

                    val req = Request.Builder()
                        .url(url)
                        .post(rootJson.toString().toRequestBody(jsonMediaType))
                        .build()

                    val resp = client.newCall(req).execute()
                    val bodyString = resp.body?.string() ?: ""

                    if (!resp.isSuccessful) {
                        return@withContext Result.failure(Exception("Gemini Image HTTP ${resp.code}: $bodyString"))
                    }

                    val respJson = JSONObject(bodyString)
                    val candidates = respJson.optJSONArray("candidates")
                    val firstCandidate = candidates?.optJSONObject(0)
                    val parts = firstCandidate?.optJSONObject("content")?.optJSONArray("parts")

                    var foundImageBytes: ByteArray? = null
                    if (parts != null) {
                        for (i in 0 until parts.length()) {
                            val part = parts.getJSONObject(i)
                            if (part.has("inlineData")) {
                                val inlineData = part.getJSONObject("inlineData")
                                val base64Data = inlineData.getString("data")
                                foundImageBytes = Base64.decode(base64Data, Base64.DEFAULT)
                                break
                            }
                        }
                    }

                    if (foundImageBytes == null) {
                        return@withContext Result.failure(Exception("Model tidak mengembalikan data gambar inline. Pastikan prompt dan konfigurasi sesuai."))
                    }

                    val file = File(context.filesDir, "hitut_gemini_${UUID.randomUUID()}.jpg")
                    FileOutputStream(file).use { out ->
                        out.write(foundImageBytes)
                    }

                    Result.success(Pair(file.toURI().toString(), file.absolutePath))
                }

                ImageBackendType.CUSTOM_COLAB_SD -> {
                    if (customColabUrl.isBlank()) {
                        return@withContext Result.failure(
                            Exception("URL Backend Colab untuk generator gambar belum diisi di Pengaturan Backend.")
                        )
                    }

                    val endpoint = "${customColabUrl.trim().removeSuffix("/")}/generate-image"
                    val reqJson = JSONObject().apply {
                        put("prompt", prompt)
                        put("aspect_ratio", aspectRatio)
                        if (!referenceImageBase64.isNullOrBlank()) {
                            put("reference_image_base64", referenceImageBase64)
                        }
                    }

                    val request = Request.Builder()
                        .url(endpoint)
                        .post(reqJson.toString().toRequestBody(jsonMediaType))
                        .build()

                    val response = client.newCall(request).execute()
                    val respBody = response.body?.string() ?: ""

                    if (!response.isSuccessful) {
                        return@withContext Result.failure(Exception("Colab SD HTTP ${response.code}: $respBody"))
                    }

                    val json = JSONObject(respBody)
                    val imgUrl = json.optString("image_url", "")
                    val base64Str = json.optString("image_base64", "")

                    if (base64Str.isNotBlank()) {
                        val decoded = Base64.decode(base64Str, Base64.DEFAULT)
                        val file = File(context.filesDir, "hitut_sd_${UUID.randomUUID()}.jpg")
                        FileOutputStream(file).use { out -> out.write(decoded) }
                        Result.success(Pair(imgUrl.ifBlank { file.toURI().toString() }, file.absolutePath))
                    } else if (imgUrl.isNotBlank()) {
                        Result.success(Pair(imgUrl, null))
                    } else {
                        Result.failure(Exception("Colab backend tidak mengembalikan URL atau base64 gambar yang valid."))
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("ImageGenClient", "Image generation failed", e)
            Result.failure(e)
        }
    }

    suspend fun saveImageToGallery(localPath: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val sourceFile = File(localPath)
            if (!sourceFile.exists()) {
                return@withContext Result.failure(Exception("File gambar tidak ditemukan."))
            }

            val picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
            val hitutDir = File(picturesDir, "HITUT_AI")
            if (!hitutDir.exists()) {
                hitutDir.mkdirs()
            }

            val targetFile = File(hitutDir, "HITUT_${System.currentTimeMillis()}.jpg")
            sourceFile.copyTo(targetFile, overwrite = true)

            Result.success(targetFile.absolutePath)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
