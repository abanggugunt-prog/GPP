package com.example.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.example.ui.viewmodel.HitutScreen

@Composable
fun HitutBottomNav(
    currentScreen: HitutScreen,
    onSelectScreen: (HitutScreen) -> Unit
) {
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        NavigationBarItem(
            icon = {
                Icon(
                    imageVector = if (currentScreen == HitutScreen.CHAT) Icons.Filled.ChatBubble else Icons.Outlined.ChatBubbleOutline,
                    contentDescription = "Chat"
                )
            },
            label = { Text("Chat") },
            selected = currentScreen == HitutScreen.CHAT,
            onClick = { onSelectScreen(HitutScreen.CHAT) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = MaterialTheme.colorScheme.primary,
                selectedTextColor = MaterialTheme.colorScheme.primary,
                indicatorColor = MaterialTheme.colorScheme.primaryContainer
            ),
            modifier = Modifier.testTag("nav_item_chat")
        )

        NavigationBarItem(
            icon = {
                Icon(
                    imageVector = if (currentScreen == HitutScreen.IMAGE) Icons.Filled.Image else Icons.Outlined.Image,
                    contentDescription = "Gambar"
                )
            },
            label = { Text("Gambar") },
            selected = currentScreen == HitutScreen.IMAGE,
            onClick = { onSelectScreen(HitutScreen.IMAGE) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = MaterialTheme.colorScheme.primary,
                selectedTextColor = MaterialTheme.colorScheme.primary,
                indicatorColor = MaterialTheme.colorScheme.primaryContainer
            ),
            modifier = Modifier.testTag("nav_item_image")
        )

        NavigationBarItem(
            icon = {
                Icon(
                    imageVector = if (currentScreen == HitutScreen.VIDEO) Icons.Filled.Videocam else Icons.Outlined.Videocam,
                    contentDescription = "Video"
                )
            },
            label = { Text("Video") },
            selected = currentScreen == HitutScreen.VIDEO,
            onClick = { onSelectScreen(HitutScreen.VIDEO) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = MaterialTheme.colorScheme.primary,
                selectedTextColor = MaterialTheme.colorScheme.primary,
                indicatorColor = MaterialTheme.colorScheme.primaryContainer
            ),
            modifier = Modifier.testTag("nav_item_video")
        )

        NavigationBarItem(
            icon = {
                Icon(
                    imageVector = if (currentScreen == HitutScreen.BACKEND_SETTINGS) Icons.Filled.Dns else Icons.Outlined.Dns,
                    contentDescription = "Backend"
                )
            },
            label = { Text("Backend") },
            selected = currentScreen == HitutScreen.BACKEND_SETTINGS,
            onClick = { onSelectScreen(HitutScreen.BACKEND_SETTINGS) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = MaterialTheme.colorScheme.primary,
                selectedTextColor = MaterialTheme.colorScheme.primary,
                indicatorColor = MaterialTheme.colorScheme.primaryContainer
            ),
            modifier = Modifier.testTag("nav_item_backend")
        )
    }
}
