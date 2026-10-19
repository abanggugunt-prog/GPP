package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import com.example.ui.components.HitutBottomNav
import com.example.ui.components.HitutDrawer
import com.example.ui.components.HitutTopBar
import com.example.ui.screens.BackendSettingsScreen
import com.example.ui.screens.ChatScreen
import com.example.ui.screens.ImageGenScreen
import com.example.ui.screens.VideoGenScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.HitutScreen
import com.example.ui.viewmodel.HitutViewModel
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val viewModel: HitutViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val isDarkTheme by viewModel.preferences.isDarkTheme.collectAsState()

            MyApplicationTheme(darkTheme = isDarkTheme) {
                HitutApp(viewModel = viewModel)
            }
        }
    }
}

@Composable
fun HitutApp(viewModel: HitutViewModel) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val currentScreen by viewModel.currentScreen.collectAsState()
    val isDarkTheme by viewModel.preferences.isDarkTheme.collectAsState()
    val chatHealth by viewModel.geminiChatHealth.collectAsState()
    val sessions by viewModel.chatSessions.collectAsState()
    val activeSessionId by viewModel.activeSessionId.collectAsState()
    val messages by viewModel.currentMessages.collectAsState()
    val inputText by viewModel.chatInputText.collectAsState()
    val isChatLoading by viewModel.isChatLoading.collectAsState()
    val quotaAlert by viewModel.quotaExceededAlert.collectAsState()
    val snackbarMsg by viewModel.snackbarMessage.collectAsState()

    // Show snackbar messages
    LaunchedEffect(snackbarMsg) {
        if (snackbarMsg != null) {
            snackbarHostState.showSnackbar(snackbarMsg!!)
            viewModel.clearSnackbar()
        }
    }

    // Handle back button: close drawer first, or return to Chat screen
    BackHandler(enabled = drawerState.isOpen || currentScreen != HitutScreen.CHAT) {
        if (drawerState.isOpen) {
            scope.launch { drawerState.close() }
        } else if (currentScreen != HitutScreen.CHAT) {
            viewModel.navigateTo(HitutScreen.CHAT)
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            HitutDrawer(
                currentScreen = currentScreen,
                sessions = sessions,
                activeSessionId = activeSessionId,
                onSelectScreen = { screen ->
                    viewModel.navigateTo(screen)
                    scope.launch { drawerState.close() }
                },
                onSelectSession = { id ->
                    viewModel.selectSession(id)
                    scope.launch { drawerState.close() }
                },
                onNewChat = {
                    viewModel.startNewChat()
                    viewModel.navigateTo(HitutScreen.CHAT)
                    scope.launch { drawerState.close() }
                },
                onDeleteSession = viewModel::deleteSession,
                onClearAllHistory = viewModel::clearAllChatHistory
            )
        }
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                HitutTopBar(
                    currentScreen = currentScreen,
                    isDarkTheme = isDarkTheme,
                    chatHealthStatus = chatHealth.status,
                    onMenuClick = {
                        scope.launch {
                            if (drawerState.isClosed) drawerState.open() else drawerState.close()
                        }
                    },
                    onNewChatClick = viewModel::startNewChat,
                    onToggleTheme = {
                        viewModel.preferences.setDarkTheme(!isDarkTheme)
                    },
                    onBackendPillClick = {
                        viewModel.navigateTo(HitutScreen.BACKEND_SETTINGS)
                    }
                )
            },
            bottomBar = {
                HitutBottomNav(
                    currentScreen = currentScreen,
                    onSelectScreen = viewModel::navigateTo
                )
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                when (currentScreen) {
                    HitutScreen.CHAT -> {
                        ChatScreen(
                            viewModel = viewModel,
                            messages = messages,
                            inputText = inputText,
                            isLoading = isChatLoading,
                            quotaExceededAlert = quotaAlert,
                            onNavigateToBackend = {
                                viewModel.dismissQuotaAlert()
                                viewModel.navigateTo(HitutScreen.BACKEND_SETTINGS)
                            }
                        )
                    }

                    HitutScreen.IMAGE -> {
                        ImageGenScreen(viewModel = viewModel)
                    }

                    HitutScreen.VIDEO -> {
                        VideoGenScreen(
                            viewModel = viewModel,
                            onNavigateToSettings = {
                                viewModel.navigateTo(HitutScreen.BACKEND_SETTINGS)
                            }
                        )
                    }

                    HitutScreen.BACKEND_SETTINGS -> {
                        BackendSettingsScreen(viewModel = viewModel)
                    }
                }
            }
        }
    }
}
