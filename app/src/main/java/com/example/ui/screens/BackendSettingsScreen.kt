package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.BackendHealthInfo
import com.example.data.model.BackendHealthStatus
import com.example.data.model.ImageBackendType
import com.example.data.model.VideoBackendTarget
import com.example.ui.theme.StatusOffline
import com.example.ui.theme.StatusOnline
import com.example.ui.theme.StatusWarning
import com.example.ui.viewmodel.HitutViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun BackendSettingsScreen(viewModel: HitutViewModel) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    val primaryUrl by viewModel.preferences.primaryVideoUrl.collectAsState()
    val backupUrl by viewModel.preferences.backupVideoUrl.collectAsState()
    val activeTarget by viewModel.preferences.activeVideoTarget.collectAsState()
    val autoFailover by viewModel.preferences.autoFailoverEnabled.collectAsState()
    val customGeminiKey by viewModel.preferences.customGeminiApiKey.collectAsState()
    val chatModel by viewModel.preferences.chatModel.collectAsState()
    val imageBackendType by viewModel.preferences.imageBackendType.collectAsState()

    val primaryHealth by viewModel.primaryColabHealth.collectAsState()
    val backupHealth by viewModel.backupKaggleHealth.collectAsState()
    val chatHealth by viewModel.geminiChatHealth.collectAsState()
    val isChecking by viewModel.isCheckingHealth.collectAsState()

    var editingPrimaryUrl by remember(primaryUrl) { mutableStateOf(primaryUrl) }
    var editingBackupUrl by remember(backupUrl) { mutableStateOf(backupUrl) }
    var editingGeminiKey by remember(customGeminiKey) { mutableStateOf(customGeminiKey) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp)
            .imePadding()
    ) {
        // Section: Header & Live Check All
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Status & Konfigurasi",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Pengalihan otomatis & backend cadangan Rp0",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Button(
                onClick = viewModel::checkAllBackendsHealth,
                enabled = !isChecking,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                modifier = Modifier.testTag("test_all_backends_button")
            ) {
                if (isChecking) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.NetworkCheck,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                Text("Tes Semua", fontSize = 12.sp)
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // SECTION 1: AI VIDEO BACKEND & FAILOVER
        Text(
            text = "1. SISTEM BACKEND VIDEO (DENGAN FAILOVER)",
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Black),
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(8.dp))

        // Auto Failover Toggle Card
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Sync,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Failover Otomatis",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Beralih otomatis ke server cadangan jika server utama terputus / sesi Colab berakhir.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = autoFailover,
                    onCheckedChange = viewModel.preferences::setAutoFailoverEnabled,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.primary,
                        checkedTrackColor = MaterialTheme.colorScheme.primaryContainer
                    ),
                    modifier = Modifier.testTag("auto_failover_switch")
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Primary Colab Backend Card
        BackendCard(
            title = "Backend Video Utama: Google Colab",
            subtitle = "Jalankan file notebook 'hitut_video_backend.ipynb' gratis di Colab dengan GPU T4.",
            health = primaryHealth,
            isSelected = activeTarget == VideoBackendTarget.PRIMARY_COLAB,
            urlValue = editingPrimaryUrl,
            onUrlChange = { editingPrimaryUrl = it },
            onSaveUrl = {
                viewModel.preferences.setPrimaryVideoUrl(editingPrimaryUrl)
                Toast.makeText(context, "URL Colab disimpan", Toast.LENGTH_SHORT).show()
                viewModel.checkPrimaryHealth()
            },
            onSelectActive = { viewModel.preferences.setActiveVideoTarget(VideoBackendTarget.PRIMARY_COLAB) },
            onTestConnection = viewModel::checkPrimaryHealth,
            placeholder = "https://xxxx-xx-xx-xx.ngrok-free.app atau localtunnel URL"
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Backup Kaggle Backend Card
        BackendCard(
            title = "Backend Video Cadangan: Kaggle Notebook",
            subtitle = "Alternatif kuota GPU 30 jam/minggu jika batas Google Colab harian tercapai.",
            health = backupHealth,
            isSelected = activeTarget == VideoBackendTarget.BACKUP_KAGGLE,
            urlValue = editingBackupUrl,
            onUrlChange = { editingBackupUrl = it },
            onSaveUrl = {
                viewModel.preferences.setBackupVideoUrl(editingBackupUrl)
                Toast.makeText(context, "URL Kaggle disimpan", Toast.LENGTH_SHORT).show()
                viewModel.checkBackupHealth()
            },
            onSelectActive = { viewModel.preferences.setActiveVideoTarget(VideoBackendTarget.BACKUP_KAGGLE) },
            onTestConnection = viewModel::checkBackupHealth,
            placeholder = "https://yyyy-yy-yy-yy.ngrok-free.app atau endpoint Kaggle"
        )

        Spacer(modifier = Modifier.height(24.dp))

        // SECTION 2: AI CHAT BACKEND
        Text(
            text = "2. BACKEND AI CHAT",
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Black),
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(8.dp))

        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Layanan: Google AI Studio (Gratis Rp0)",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        HealthStatusRow(chatHealth)
                    }

                    OutlinedButton(
                        onClick = viewModel::checkChatHealth,
                        modifier = Modifier.testTag("ping_chat_button")
                    ) {
                        Text("Tes", fontSize = 12.sp)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Pilihan Model:",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(6.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = chatModel == "gemini-3.5-flash",
                        onClick = { viewModel.preferences.setChatModel("gemini-3.5-flash") },
                        label = { Text("Gemini 3.5 Flash (Cepat & Bebas)") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                        )
                    )
                    FilterChip(
                        selected = chatModel == "gemini-3.1-pro-preview",
                        onClick = { viewModel.preferences.setChatModel("gemini-3.1-pro-preview") },
                        label = { Text("Gemini 3.1 Pro") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                        )
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Custom API Key Override
                Text(
                    text = "API Key Kustom (Opsional jika kuota utama habis):",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = editingGeminiKey,
                    onValueChange = { editingGeminiKey = it },
                    placeholder = { Text("Biarkan kosong untuk memakai kunci bawaan AI Studio") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    trailingIcon = {
                        Button(
                            onClick = {
                                viewModel.preferences.setCustomGeminiApiKey(editingGeminiKey)
                                Toast.makeText(context, "API Key kustom disimpan", Toast.LENGTH_SHORT).show()
                                viewModel.checkChatHealth()
                            },
                            modifier = Modifier.padding(end = 4.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Simpan", fontSize = 11.sp)
                        }
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // SECTION 3: AI GAMBAR BACKEND
        Text(
            text = "3. BACKEND AI GENERATOR GAMBAR",
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Black),
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(8.dp))

        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Pilih Mesin Gambar:",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(8.dp))

                val engines = listOf(
                    Triple(ImageBackendType.POLLINATIONS_FREE, "Pollinations AI (Rekomendasi)", "100% Gratis Rp0 tanpa API Key, tanpa batas token, model Flux"),
                    Triple(ImageBackendType.GEMINI_IMAGE, "Google Gemini 2.5 Flash Image", "Menggunakan kuota API Studio Gemini Anda"),
                    Triple(ImageBackendType.CUSTOM_COLAB_SD, "Colab Stable Diffusion Endpoint", "Menggunakan GPU Colab pribadi Anda")
                )

                engines.forEach { (type, name, desc) ->
                    val isChosen = imageBackendType == type
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isChosen) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { viewModel.preferences.setImageBackendType(type) }
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (isChosen) Icons.Default.RadioButtonChecked else Icons.Default.RadioButtonUnchecked,
                                contentDescription = null,
                                tint = if (isChosen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = name,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                    color = if (isChosen) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = desc,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (isChosen) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // SECTION 4: PANDUAN COLAB BACKEND
        Text(
            text = "4. PANDUAN MENJALANKAN BACKEND COLAB",
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Black),
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(8.dp))

        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.HelpOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Cara Menjalankan Backend Video Rp0",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                val guideSteps = listOf(
                    "1. Buka Google Colab di browser (colab.research.google.com).",
                    "2. Buat notebook baru atau impor file 'hitut_video_backend.ipynb' dari repo HITUT AI.",
                    "3. Pada menu 'Runtime' -> 'Change runtime type', pilih GPU 'T4' gratis.",
                    "4. Jalankan semua sel notebook untuk memasang dependensi video & menyalakan tunnel publik ngrok / localtunnel.",
                    "5. Salin URL publik yang dicetak di output notebook (contoh: https://xxxx.ngrok-free.app).",
                    "6. Tempelkan URL tersebut pada kolom 'Backend Video Utama' di atas dan klik 'Simpan & Tes'.",
                    "7. Jika sesi Colab gratis mati setelah beberapa jam, jalankan kembali dan perbarui URL di aplikasi tanpa perlu menginstal ulang APK!"
                )

                guideSteps.forEach { step ->
                    Text(
                        text = step,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 3.dp),
                        lineHeight = 18.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun BackendCard(
    title: String,
    subtitle: String,
    health: BackendHealthInfo,
    isSelected: Boolean,
    urlValue: String,
    onUrlChange: (String) -> Unit,
    onSaveUrl: () -> Unit,
    onSelectActive: () -> Unit,
    onTestConnection: () -> Unit,
    placeholder: String
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        border = if (isSelected) androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onSelectActive() }
                ) {
                    Icon(
                        imageVector = if (isSelected) Icons.Default.RadioButtonChecked else Icons.Default.RadioButtonUnchecked,
                        contentDescription = null,
                        tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                OutlinedButton(
                    onClick = onTestConnection,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Tes", fontSize = 11.sp)
                }
            }

            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
            )

            HealthStatusRow(health)

            Spacer(modifier = Modifier.height(10.dp))

            // URL input field
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = urlValue,
                    onValueChange = onUrlChange,
                    placeholder = { Text(placeholder, fontSize = 12.sp) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                    )
                )
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = onSaveUrl,
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Simpan", fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun HealthStatusRow(health: BackendHealthInfo) {
    val (dotColor, statusText) = when (health.status) {
        BackendHealthStatus.ONLINE -> Pair(StatusOnline, "Online (${health.latencyMs} ms) - ${health.message}")
        BackendHealthStatus.OFFLINE -> Pair(StatusOffline, "Terputus / Offline: ${health.message}")
        BackendHealthStatus.QUOTA_EXCEEDED -> Pair(StatusWarning, "Batas Kuota Tercapai")
        BackendHealthStatus.CHECKING -> Pair(Color.Yellow, "Sedang memeriksa koneksi...")
        BackendHealthStatus.UNKNOWN -> Pair(Color.Gray, "Status belum dicek")
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 2.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(dotColor)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = statusText,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
