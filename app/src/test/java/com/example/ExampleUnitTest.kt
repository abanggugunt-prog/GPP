package com.example

import com.example.data.model.BackendHealthInfo
import com.example.data.model.BackendHealthStatus
import com.example.data.model.ChatMessage
import com.example.data.model.ChatSession
import com.example.data.model.GeneratedMedia
import com.example.data.model.VideoBackendTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {
    @Test
    fun testChatSessionCreation() {
        val session = ChatSession(
            title = "Diskusi AI & Pemrograman",
            modelUsed = "gemini-3.5-flash"
        )
        assertNotNull(session.id)
        assertEquals("Diskusi AI & Pemrograman", session.title)
        assertEquals("gemini-3.5-flash", session.modelUsed)
    }

    @Test
    fun testChatMessageCreation() {
        val msg = ChatMessage(
            sessionId = "session-123",
            role = "user",
            content = "Halo HITUT AI!"
        )
        assertEquals("user", msg.role)
        assertEquals("SUCCESS", msg.status)
        assertEquals("Halo HITUT AI!", msg.content)
    }

    @Test
    fun testGeneratedMediaModel() {
        val media = GeneratedMedia(
            type = "VIDEO",
            prompt = "Elang emas terbang di atas ngarai",
            mediaUrl = "https://example.com/video.mp4",
            aspectRatio = "16:9",
            durationSeconds = 4,
            backendSource = VideoBackendTarget.PRIMARY_COLAB.name
        )
        assertEquals("VIDEO", media.type)
        assertEquals(4, media.durationSeconds)
        assertEquals("16:9", media.aspectRatio)
    }

    @Test
    fun testBackendHealthInfo() {
        val health = BackendHealthInfo(
            target = "Google Colab",
            status = BackendHealthStatus.ONLINE,
            latencyMs = 120,
            message = "Tesla T4 Ready"
        )
        assertTrue(health.status == BackendHealthStatus.ONLINE)
        assertEquals(120, health.latencyMs)
    }
}
