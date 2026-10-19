package com.example.ui.viewmodel

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.BackendPreferences
import com.example.data.local.HitutDatabase
import com.example.data.model.BackendHealthInfo
import com.example.data.model.BackendHealthStatus
import com.example.data.model.ChatMessage
import com.example.data.model.ChatSession
import com.example.data.model.GeneratedMedia
import com.example.data.model.ImageBackendType
import com.example.data.model.VideoBackendTarget
import com.example.data.model.VideoJobStatus
import com.example.data.repository.HitutRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class HitutScreen {
    CHAT,
    IMAGE,
    VIDEO,
    BACKEND_SETTINGS
}

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class HitutViewModel(application: Application) : AndroidViewModel(application) {
    private val database = HitutDatabase.getDatabase(application)
    val preferences = BackendPreferences(application)
    val repository = HitutRepository(application, database.hitutDao(), preferences)

    // Current Screen
    private val _currentScreen = MutableStateFlow(HitutScreen.CHAT)
    val currentScreen: StateFlow<HitutScreen> = _currentScreen.asStateFlow()

    // Active Chat Session
    private val _activeSessionId = MutableStateFlow<String?>(null)
    val activeSessionId: StateFlow<String?> = _activeSessionId.asStateFlow()

    val chatSessions: StateFlow<List<ChatSession>> = repository.getSessions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val currentMessages: StateFlow<List<ChatMessage>> = _activeSessionId.flatMapLatest { id ->
        if (id == null) flowOf(emptyList()) else repository.getMessages(id)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val savedMedia: StateFlow<List<GeneratedMedia>> = repository.getSavedMedia()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Chat UI state
    private val _chatInputText = MutableStateFlow("")
    val chatInputText: StateFlow<String> = _chatInputText.asStateFlow()

    private val _isChatLoading = MutableStateFlow(false)
    val isChatLoading: StateFlow<Boolean> = _isChatLoading.asStateFlow()

    private val _quotaExceededAlert = MutableStateFlow(false)
    val quotaExceededAlert: StateFlow<Boolean> = _quotaExceededAlert.asStateFlow()

    // Image Generation state
    private val _imagePrompt = MutableStateFlow("")
    val imagePrompt: StateFlow<String> = _imagePrompt.asStateFlow()

    private val _selectedAspectRatio = MutableStateFlow("1:1")
    val selectedAspectRatio: StateFlow<String> = _selectedAspectRatio.asStateFlow()

    private val _imageReferenceBitmap = MutableStateFlow<Bitmap?>(null)
    val imageReferenceBitmap: StateFlow<Bitmap?> = _imageReferenceBitmap.asStateFlow()

    private val _isGeneratingImage = MutableStateFlow(false)
    val isGeneratingImage: StateFlow<Boolean> = _isGeneratingImage.asStateFlow()

    private val _currentImageResult = MutableStateFlow<GeneratedMedia?>(null)
    val currentImageResult: StateFlow<GeneratedMedia?> = _currentImageResult.asStateFlow()

    private val _imageErrorMessage = MutableStateFlow<String?>(null)
    val imageErrorMessage: StateFlow<String?> = _imageErrorMessage.asStateFlow()

    // Video Generation state
    private val _videoMode = MutableStateFlow("TEXT_TO_VIDEO") // "TEXT_TO_VIDEO" or "IMAGE_TO_VIDEO"
    val videoMode: StateFlow<String> = _videoMode.asStateFlow()

    private val _videoPrompt = MutableStateFlow("")
    val videoPrompt: StateFlow<String> = _videoPrompt.asStateFlow()

    private val _motionPrompt = MutableStateFlow("")
    val motionPrompt: StateFlow<String> = _motionPrompt.asStateFlow()

    private val _videoReferenceBitmap = MutableStateFlow<Bitmap?>(null)
    val videoReferenceBitmap: StateFlow<Bitmap?> = _videoReferenceBitmap.asStateFlow()

    private val _videoAspectRatio = MutableStateFlow("16:9")
    val videoAspectRatio: StateFlow<String> = _videoAspectRatio.asStateFlow()

    private val _videoDuration = MutableStateFlow(4)
    val videoDuration: StateFlow<Int> = _videoDuration.asStateFlow()

    private val _isGeneratingVideo = MutableStateFlow(false)
    val isGeneratingVideo: StateFlow<Boolean> = _isGeneratingVideo.asStateFlow()

    private val _videoJobStatus = MutableStateFlow<VideoJobStatus?>(null)
    val videoJobStatus: StateFlow<VideoJobStatus?> = _videoJobStatus.asStateFlow()

    private val _currentVideoResult = MutableStateFlow<GeneratedMedia?>(null)
    val currentVideoResult: StateFlow<GeneratedMedia?> = _currentVideoResult.asStateFlow()

    private val _videoErrorMessage = MutableStateFlow<String?>(null)
    val videoErrorMessage: StateFlow<String?> = _videoErrorMessage.asStateFlow()

    // Backend Health status
    private val _primaryColabHealth = MutableStateFlow(BackendHealthInfo("Google Colab (Utama)"))
    val primaryColabHealth: StateFlow<BackendHealthInfo> = _primaryColabHealth.asStateFlow()

    private val _backupKaggleHealth = MutableStateFlow(BackendHealthInfo("Kaggle Notebook (Cadangan)"))
    val backupKaggleHealth: StateFlow<BackendHealthInfo> = _backupKaggleHealth.asStateFlow()

    private val _geminiChatHealth = MutableStateFlow(BackendHealthInfo("Gemini AI Studio"))
    val geminiChatHealth: StateFlow<BackendHealthInfo> = _geminiChatHealth.asStateFlow()

    private val _isCheckingHealth = MutableStateFlow(false)
    val isCheckingHealth: StateFlow<Boolean> = _isCheckingHealth.asStateFlow()

    private val _snackbarMessage = MutableStateFlow<String?>(null)
    val snackbarMessage: StateFlow<String?> = _snackbarMessage.asStateFlow()

    private var videoPollingJob: Job? = null

    init {
        // Initialize with default or latest session if available
        viewModelScope.launch {
            checkAllBackendsHealth()
        }
    }

    fun navigateTo(screen: HitutScreen) {
        _currentScreen.value = screen
    }

    fun showSnackbar(message: String) {
        _snackbarMessage.value = message
    }

    fun clearSnackbar() {
        _snackbarMessage.value = null
    }

    fun dismissQuotaAlert() {
        _quotaExceededAlert.value = false
    }

    // --- CHAT ACTIONS ---

    fun onChatInputChanged(text: String) {
        _chatInputText.value = text
    }

    fun selectSession(sessionId: String) {
        _activeSessionId.value = sessionId
    }

    fun startNewChat() {
        viewModelScope.launch {
            val session = repository.createNewSession("Percakapan Baru")
            _activeSessionId.value = session.id
            _chatInputText.value = ""
        }
    }

    fun deleteSession(sessionId: String) {
        viewModelScope.launch {
            repository.deleteSession(sessionId)
            if (_activeSessionId.value == sessionId) {
                _activeSessionId.value = null
            }
        }
    }

    fun clearAllChatHistory() {
        viewModelScope.launch {
            repository.clearAllHistory()
            _activeSessionId.value = null
            showSnackbar("Semua riwayat chat berhasil dihapus.")
        }
    }

    fun sendMessage() {
        val text = _chatInputText.value.trim()
        if (text.isBlank() || _isChatLoading.value) return

        viewModelScope.launch {
            var sessionId = _activeSessionId.value
            if (sessionId == null) {
                val newSession = repository.createNewSession(text)
                sessionId = newSession.id
                _activeSessionId.value = sessionId
            }

            _chatInputText.value = ""
            _isChatLoading.value = true

            val result = repository.sendMessage(sessionId, text)
            _isChatLoading.value = false

            if (result.isFailure) {
                val ex = result.exceptionOrNull()
                if (ex?.message?.contains("429") == true || ex?.message?.contains("Kuota") == true) {
                    _quotaExceededAlert.value = true
                }
            }
        }
    }

    fun regenerateMessage(lastUserMessage: String) {
        _chatInputText.value = lastUserMessage
        sendMessage()
    }

    // --- IMAGE ACTIONS ---

    fun onImagePromptChanged(text: String) {
        _imagePrompt.value = text
    }

    fun onAspectRatioSelected(ratio: String) {
        _selectedAspectRatio.value = ratio
    }

    fun onReferenceBitmapSelected(bitmap: Bitmap?) {
        _imageReferenceBitmap.value = bitmap
    }

    fun generateImage() {
        val prompt = _imagePrompt.value.trim()
        if (prompt.isBlank() || _isGeneratingImage.value) return

        viewModelScope.launch {
            _isGeneratingImage.value = true
            _imageErrorMessage.value = null

            val result = repository.generateImage(
                prompt = prompt,
                aspectRatio = _selectedAspectRatio.value,
                referenceBitmap = _imageReferenceBitmap.value
            )

            _isGeneratingImage.value = false
            if (result.isSuccess) {
                _currentImageResult.value = result.getOrThrow()
                showSnackbar("Gambar berhasil dibuat!")
            } else {
                val err = result.exceptionOrNull()?.localizedMessage ?: "Gagal membuat gambar"
                _imageErrorMessage.value = err
                showSnackbar("Error: $err")
            }
        }
    }

    fun downloadCurrentImage() {
        val current = _currentImageResult.value ?: return
        val path = current.localUri ?: return
        viewModelScope.launch {
            val res = repository.saveImageToGallery(path)
            if (res.isSuccess) {
                showSnackbar("Gambar berhasil diunduh ke Galeri: ${res.getOrThrow()}")
            } else {
                showSnackbar("Gagal mengunduh gambar: ${res.exceptionOrNull()?.message}")
            }
        }
    }

    // --- VIDEO ACTIONS ---

    fun setVideoMode(mode: String) {
        _videoMode.value = mode
    }

    fun onVideoPromptChanged(text: String) {
        _videoPrompt.value = text
    }

    fun onMotionPromptChanged(text: String) {
        _motionPrompt.value = text
    }

    fun onVideoReferenceBitmapSelected(bitmap: Bitmap?) {
        _videoReferenceBitmap.value = bitmap
    }

    fun onVideoAspectRatioSelected(ratio: String) {
        _videoAspectRatio.value = ratio
    }

    fun onVideoDurationSelected(duration: Int) {
        _videoDuration.value = duration
    }

    fun generateVideo() {
        if (_isGeneratingVideo.value) return
        val isImg2Vid = _videoMode.value == "IMAGE_TO_VIDEO"

        if (isImg2Vid && _videoReferenceBitmap.value == null) {
            showSnackbar("Pilih gambar referensi terlebih dahulu untuk Image-to-Video.")
            return
        }

        val prompt = if (isImg2Vid) _motionPrompt.value.trim() else _videoPrompt.value.trim()
        if (prompt.isBlank()) {
            showSnackbar("Masukkan prompt video terlebih dahulu.")
            return
        }

        viewModelScope.launch {
            _isGeneratingVideo.value = true
            _videoErrorMessage.value = null
            _videoJobStatus.value = VideoJobStatus("", "SUBMITTING", 5, null, null)

            val submitResult = if (isImg2Vid) {
                repository.submitImageToVideo(
                    imageBitmap = _videoReferenceBitmap.value!!,
                    motionPrompt = prompt,
                    durationSeconds = _videoDuration.value,
                    aspectRatio = _videoAspectRatio.value
                )
            } else {
                repository.submitTextToVideo(
                    prompt = prompt,
                    durationSeconds = _videoDuration.value,
                    aspectRatio = _videoAspectRatio.value
                )
            }

            if (submitResult.isFailure) {
                _isGeneratingVideo.value = false
                val err = submitResult.exceptionOrNull()?.localizedMessage ?: "Gagal mengirim tugas video."
                _videoErrorMessage.value = err
                _videoJobStatus.value = null
                showSnackbar(err)
                return@launch
            }

            val jobId = submitResult.getOrThrow()
            _videoJobStatus.value = VideoJobStatus(jobId, "QUEUED", 10, null, null)
            startVideoPolling(jobId, prompt)
        }
    }

    private fun startVideoPolling(jobId: String, prompt: String) {
        videoPollingJob?.cancel()
        videoPollingJob = viewModelScope.launch {
            var attempts = 0
            val maxAttempts = 120 // up to 10 minutes (5s interval)
            while (attempts < maxAttempts) {
                delay(5000)
                attempts++

                val statusResult = repository.pollVideoJob(jobId)
                if (statusResult.isSuccess) {
                    val status = statusResult.getOrThrow()
                    _videoJobStatus.value = status

                    if (status.status == "COMPLETED" && !status.videoUrl.isNullOrBlank()) {
                        _isGeneratingVideo.value = false
                        val saved = repository.saveVideoMediaRecord(
                            prompt = prompt,
                            videoUrl = status.videoUrl,
                            durationSeconds = _videoDuration.value,
                            aspectRatio = _videoAspectRatio.value
                        )
                        _currentVideoResult.value = saved
                        showSnackbar("Video berhasil dibuat!")
                        break
                    } else if (status.status == "FAILED") {
                        _isGeneratingVideo.value = false
                        val err = status.errorMessage ?: "Proses render video di backend gagal."
                        _videoErrorMessage.value = err
                        showSnackbar("Error Video: $err")
                        break
                    }
                } else {
                    // One poll failed, keep trying or alert
                    if (attempts > 3) {
                        _videoJobStatus.value = _videoJobStatus.value?.copy(
                            errorMessage = "Koneksi ke backend tersendat, mencoba ulang..."
                        )
                    }
                }
            }

            if (_isGeneratingVideo.value) {
                _isGeneratingVideo.value = false
                _videoErrorMessage.value = "Waktu tunggu habis (timeout). Periksa GPU dan sesi Colab Anda."
            }
        }
    }

    fun downloadCurrentVideo() {
        val current = _currentVideoResult.value ?: return
        viewModelScope.launch {
            val res = repository.downloadVideoToStorage(current.mediaUrl)
            if (res.isSuccess) {
                showSnackbar("Video tersimpan di Movies: ${res.getOrThrow()}")
            } else {
                showSnackbar("Gagal mengunduh video: ${res.exceptionOrNull()?.message}")
            }
        }
    }

    // --- BACKEND HEALTH CHECKS ---

    fun checkAllBackendsHealth() {
        viewModelScope.launch {
            _isCheckingHealth.value = true
            _primaryColabHealth.value = repository.checkVideoBackendHealth(VideoBackendTarget.PRIMARY_COLAB)
            _backupKaggleHealth.value = repository.checkVideoBackendHealth(VideoBackendTarget.BACKUP_KAGGLE)
            _geminiChatHealth.value = repository.checkChatBackendHealth()
            _isCheckingHealth.value = false
        }
    }

    fun checkPrimaryHealth() {
        viewModelScope.launch {
            _primaryColabHealth.value = repository.checkVideoBackendHealth(VideoBackendTarget.PRIMARY_COLAB)
        }
    }

    fun checkBackupHealth() {
        viewModelScope.launch {
            _backupKaggleHealth.value = repository.checkVideoBackendHealth(VideoBackendTarget.BACKUP_KAGGLE)
        }
    }

    fun checkChatHealth() {
        viewModelScope.launch {
            _geminiChatHealth.value = repository.checkChatBackendHealth()
        }
    }

    override fun onCleared() {
        super.onCleared()
        videoPollingJob?.cancel()
    }
}
