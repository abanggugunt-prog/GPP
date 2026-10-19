package com.example.data.remote

import android.util.Log
import com.example.data.model.ChatMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

class GeminiChatClient {
    private val client = NetworkClient.okHttpClient
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun generateResponse(
        apiKey: String,
        modelName: String,
        history: List<ChatMessage>,
        newPrompt: String
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
                return@withContext Result.failure(
                    IllegalStateException(
                        "API Key Gemini belum disetel. Buka Secrets Panel di AI Studio atau masukkan API Key gratis Anda pada Pengaturan Backend."
                    )
                )
            }

            // Target endpoint according to gemini-api skill
            val effectiveModel = if (modelName.isBlank()) "gemini-3.5-flash" else modelName
            val url = "https://generativelanguage.googleapis.com/v1beta/models/$effectiveModel:generateContent?key=$apiKey"

            val rootJson = JSONObject()

            // System instruction
            val systemInstruction = JSONObject().apply {
                val partsArray = JSONArray().apply {
                    put(JSONObject().put("text", "Kamu adalah HITUT AI, asisten AI pribadi yang serba bisa, cerdas, ramah, dan jujur. Jawab dalam Bahasa Indonesia yang komunikatif, terstruktur, dan solutif. Jika menjawab kode, berikan penjelasan ringkas dan format Markdown."))
                }
                put("parts", partsArray)
            }
            rootJson.put("systemInstruction", systemInstruction)

            // Contents array with history
            val contentsArray = JSONArray()

            // Take last 12 messages for conversation context window
            val relevantHistory = history.takeLast(12)
            for (msg in relevantHistory) {
                val contentObj = JSONObject()
                // Map roles to Gemini roles: "user" or "model"
                val geminiRole = if (msg.role == "user") "user" else "model"
                contentObj.put("role", geminiRole)
                val parts = JSONArray().apply {
                    put(JSONObject().put("text", msg.content))
                }
                contentObj.put("parts", parts)
                contentsArray.put(contentObj)
            }

            // Append current user prompt
            val currentTurn = JSONObject().apply {
                put("role", "user")
                put("parts", JSONArray().apply {
                    put(JSONObject().put("text", newPrompt))
                })
            }
            contentsArray.put(currentTurn)

            rootJson.put("contents", contentsArray)

            // Generation config
            val genConfig = JSONObject().apply {
                put("temperature", 0.7)
                put("topP", 0.95)
                put("topK", 40)
            }
            rootJson.put("generationConfig", genConfig)

            val requestBody = rootJson.toString().toRequestBody(jsonMediaType)
            val request = Request.Builder()
                .url(url)
                .post(requestBody)
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                if (response.code == 429) {
                    return@withContext Result.failure(
                        QuotaExceededException("Kuota gratis Gemini harian/menit tercapai (HTTP 429). Silakan tunggu sejenak atau ganti ke Backend Cadangan di menu Pengaturan.")
                    )
                }
                // Try parse error message from JSON
                val errorMessage = try {
                    val errJson = JSONObject(responseBody)
                    errJson.optJSONObject("error")?.optString("message") ?: "HTTP ${response.code}: $responseBody"
                } catch (e: Exception) {
                    "HTTP ${response.code}: $responseBody"
                }
                return@withContext Result.failure(Exception(errorMessage))
            }

            val respJson = JSONObject(responseBody)
            val candidates = respJson.optJSONArray("candidates")
            if (candidates == null || candidates.length() == 0) {
                return@withContext Result.failure(Exception("Tidak ada respons dari model."))
            }

            val firstCandidate = candidates.getJSONObject(0)
            val content = firstCandidate.optJSONObject("content")
            val parts = content?.optJSONArray("parts")
            val textBuilder = StringBuilder()

            if (parts != null) {
                for (i in 0 until parts.length()) {
                    val p = parts.getJSONObject(i)
                    if (p.has("text")) {
                        textBuilder.append(p.getString("text"))
                    }
                }
            }

            val resultText = textBuilder.toString()
            if (resultText.isBlank()) {
                Result.failure(Exception("Model mengembalikan teks kosong."))
            } else {
                Result.success(resultText)
            }
        } catch (e: QuotaExceededException) {
            Result.failure(e)
        } catch (e: Exception) {
            Log.e("GeminiChatClient", "Error generating response", e)
            Result.failure(e)
        }
    }

    suspend fun ping(apiKey: String, modelName: String): Pair<Boolean, Long> = withContext(Dispatchers.IO) {
        val start = System.currentTimeMillis()
        try {
            if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
                return@withContext Pair(false, -1)
            }
            val effectiveModel = if (modelName.isBlank()) "gemini-3.5-flash" else modelName
            val url = "https://generativelanguage.googleapis.com/v1beta/models/$effectiveModel:generateContent?key=$apiKey"
            val simpleJson = JSONObject().apply {
                put("contents", JSONArray().apply {
                    put(JSONObject().apply {
                        put("parts", JSONArray().apply {
                            put(JSONObject().put("text", "Ping"))
                        })
                    })
                })
            }
            val request = Request.Builder()
                .url(url)
                .post(simpleJson.toString().toRequestBody(jsonMediaType))
                .build()

            val response = client.newCall(request).execute()
            val duration = System.currentTimeMillis() - start
            Pair(response.isSuccessful, duration)
        } catch (e: Exception) {
            Pair(false, -1)
        }
    }
}

class QuotaExceededException(message: String) : Exception(message)
