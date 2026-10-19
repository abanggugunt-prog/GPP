package com.example.data.local

import android.content.Context
import android.content.SharedPreferences
import com.example.BuildConfig
import com.example.data.model.ChatBackendType
import com.example.data.model.ImageBackendType
import com.example.data.model.VideoBackendTarget
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class BackendPreferences(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("hitut_backend_prefs", Context.MODE_PRIVATE)

    private val _primaryVideoUrl = MutableStateFlow(
        prefs.getString(KEY_PRIMARY_VIDEO_URL, "") ?: ""
    )
    val primaryVideoUrl: StateFlow<String> = _primaryVideoUrl.asStateFlow()

    private val _backupVideoUrl = MutableStateFlow(
        prefs.getString(KEY_BACKUP_VIDEO_URL, "") ?: ""
    )
    val backupVideoUrl: StateFlow<String> = _backupVideoUrl.asStateFlow()

    private val _activeVideoTarget = MutableStateFlow(
        VideoBackendTarget.valueOf(
            prefs.getString(KEY_ACTIVE_VIDEO_TARGET, VideoBackendTarget.PRIMARY_COLAB.name)
                ?: VideoBackendTarget.PRIMARY_COLAB.name
        )
    )
    val activeVideoTarget: StateFlow<VideoBackendTarget> = _activeVideoTarget.asStateFlow()

    private val _autoFailoverEnabled = MutableStateFlow(
        prefs.getBoolean(KEY_AUTO_FAILOVER, true)
    )
    val autoFailoverEnabled: StateFlow<Boolean> = _autoFailoverEnabled.asStateFlow()

    private val _customGeminiApiKey = MutableStateFlow(
        prefs.getString(KEY_CUSTOM_GEMINI_KEY, "") ?: ""
    )
    val customGeminiApiKey: StateFlow<String> = _customGeminiApiKey.asStateFlow()

    private val _chatModel = MutableStateFlow(
        prefs.getString(KEY_CHAT_MODEL, "gemini-3.5-flash") ?: "gemini-3.5-flash"
    )
    val chatModel: StateFlow<String> = _chatModel.asStateFlow()

    private val _customChatUrl = MutableStateFlow(
        prefs.getString(KEY_CUSTOM_CHAT_URL, "") ?: ""
    )
    val customChatUrl: StateFlow<String> = _customChatUrl.asStateFlow()

    private val _chatBackendType = MutableStateFlow(
        ChatBackendType.valueOf(
            prefs.getString(KEY_CHAT_BACKEND_TYPE, ChatBackendType.GEMINI_DIRECT.name)
                ?: ChatBackendType.GEMINI_DIRECT.name
        )
    )
    val chatBackendType: StateFlow<ChatBackendType> = _chatBackendType.asStateFlow()

    private val _imageBackendType = MutableStateFlow(
        ImageBackendType.valueOf(
            prefs.getString(KEY_IMAGE_BACKEND_TYPE, ImageBackendType.POLLINATIONS_FREE.name)
                ?: ImageBackendType.POLLINATIONS_FREE.name
        )
    )
    val imageBackendType: StateFlow<ImageBackendType> = _imageBackendType.asStateFlow()

    private val _isDarkTheme = MutableStateFlow(
        prefs.getBoolean(KEY_DARK_THEME, true)
    )
    val isDarkTheme: StateFlow<Boolean> = _isDarkTheme.asStateFlow()

    fun setPrimaryVideoUrl(url: String) {
        val trimmed = url.trim().removeSuffix("/")
        prefs.edit().putString(KEY_PRIMARY_VIDEO_URL, trimmed).apply()
        _primaryVideoUrl.value = trimmed
    }

    fun setBackupVideoUrl(url: String) {
        val trimmed = url.trim().removeSuffix("/")
        prefs.edit().putString(KEY_BACKUP_VIDEO_URL, trimmed).apply()
        _backupVideoUrl.value = trimmed
    }

    fun setActiveVideoTarget(target: VideoBackendTarget) {
        prefs.edit().putString(KEY_ACTIVE_VIDEO_TARGET, target.name).apply()
        _activeVideoTarget.value = target
    }

    fun setAutoFailoverEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_FAILOVER, enabled).apply()
        _autoFailoverEnabled.value = enabled
    }

    fun setCustomGeminiApiKey(key: String) {
        val trimmed = key.trim()
        prefs.edit().putString(KEY_CUSTOM_GEMINI_KEY, trimmed).apply()
        _customGeminiApiKey.value = trimmed
    }

    fun setChatModel(model: String) {
        prefs.edit().putString(KEY_CHAT_MODEL, model).apply()
        _chatModel.value = model
    }

    fun setCustomChatUrl(url: String) {
        val trimmed = url.trim().removeSuffix("/")
        prefs.edit().putString(KEY_CUSTOM_CHAT_URL, trimmed).apply()
        _customChatUrl.value = trimmed
    }

    fun setChatBackendType(type: ChatBackendType) {
        prefs.edit().putString(KEY_CHAT_BACKEND_TYPE, type.name).apply()
        _chatBackendType.value = type
    }

    fun setImageBackendType(type: ImageBackendType) {
        prefs.edit().putString(KEY_IMAGE_BACKEND_TYPE, type.name).apply()
        _imageBackendType.value = type
    }

    fun setDarkTheme(isDark: Boolean) {
        prefs.edit().putBoolean(KEY_DARK_THEME, isDark).apply()
        _isDarkTheme.value = isDark
    }

    /**
     * Resolves the active Gemini API key: either user custom key if provided, or BuildConfig.GEMINI_API_KEY.
     */
    fun getEffectiveGeminiApiKey(): String {
        val custom = _customGeminiApiKey.value
        if (custom.isNotBlank()) {
            return custom
        }
        return try {
            BuildConfig.GEMINI_API_KEY
        } catch (e: Exception) {
            ""
        }
    }

    /**
     * Resolves the active video endpoint URL based on currently chosen target.
     */
    fun getActiveVideoEndpointUrl(): String {
        return when (_activeVideoTarget.value) {
            VideoBackendTarget.PRIMARY_COLAB -> _primaryVideoUrl.value
            VideoBackendTarget.BACKUP_KAGGLE -> _backupVideoUrl.value
            VideoBackendTarget.CUSTOM_URL -> _primaryVideoUrl.value
        }
    }

    companion object {
        private const val KEY_PRIMARY_VIDEO_URL = "key_primary_video_url"
        private const val KEY_BACKUP_VIDEO_URL = "key_backup_video_url"
        private const val KEY_ACTIVE_VIDEO_TARGET = "key_active_video_target"
        private const val KEY_AUTO_FAILOVER = "key_auto_failover"
        private const val KEY_CUSTOM_GEMINI_KEY = "key_custom_gemini_key"
        private const val KEY_CHAT_MODEL = "key_chat_model"
        private const val KEY_CUSTOM_CHAT_URL = "key_custom_chat_url"
        private const val KEY_CHAT_BACKEND_TYPE = "key_chat_backend_type"
        private const val KEY_IMAGE_BACKEND_TYPE = "key_image_backend_type"
        private const val KEY_DARK_THEME = "key_dark_theme"
    }
}
