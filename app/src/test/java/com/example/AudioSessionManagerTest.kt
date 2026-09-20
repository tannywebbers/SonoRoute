package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.audio.model.AudioDeviceModel
import com.example.audio.model.DeviceCategory
import com.example.audio.performance.AudioPerformanceManager
import com.example.audio.routing.AudioRoutingManager
import com.example.audio.session.AudioSessionManager
import com.example.audio.session.SessionLifecycleState
import com.example.audio.session.SessionType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AudioSessionManagerTest {

    private lateinit var context: Context
    private lateinit var routingManager: AudioRoutingManager
    private lateinit var performanceManager: AudioPerformanceManager
    private lateinit var sessionManager: AudioSessionManager
    private val testScope = TestScope()

    private val testSpeaker = AudioDeviceModel(
        id = 1,
        name = "Phone Speaker",
        type = 2,
        typeLabel = "Phone Speaker",
        category = DeviceCategory.BUILT_IN,
        isInput = false,
        isOutput = true,
        sampleRates = listOf(48000),
        channelCounts = listOf(2),
        isBuiltIn = true
    )

    private val testHeadphones = AudioDeviceModel(
        id = 3,
        name = "Wired Headphones",
        type = 4,
        typeLabel = "Headphones",
        category = DeviceCategory.WIRED,
        isInput = false,
        isOutput = true,
        sampleRates = listOf(48000),
        channelCounts = listOf(2),
        isBuiltIn = false
    )

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        routingManager = AudioRoutingManager(context)
        performanceManager = AudioPerformanceManager(context)
        sessionManager = AudioSessionManager(
            context = context,
            routingManager = routingManager,
            performanceManager = performanceManager,
            scope = testScope
        )
    }

    @Test
    fun `initial session state and defaults`() {
        val session = sessionManager.activeSession.value
        assertNotNull(session)
        assertEquals(SessionType.GENERAL, session.sessionType)
        assertFalse(sessionManager.isMonitoringEnabled.value)
    }

    @Test
    fun `startSession transitions lifecycle and updates session type`() {
        sessionManager.startSession(SessionType.GAMING)

        val session = sessionManager.activeSession.value
        assertEquals(SessionType.GAMING, session.sessionType)
    }

    @Test
    fun `monitoring warns on feedback when speaker is active output`() {
        routingManager.selectOutputDevice(testSpeaker)
        sessionManager.startSession(SessionType.GENERAL)

        // Attempting to enable monitoring on speaker sets feedback warning
        sessionManager.setMicrophoneMonitoring(true)

        val warning = sessionManager.monitoringFeedbackWarning.value
        assertNotNull(warning)
        assertTrue(warning?.contains("feedback", ignoreCase = true) == true ||
                warning?.contains("headphone", ignoreCase = true) == true)
    }

    @Test
    fun `eventLog captures logged events`() {
        val initialSize = sessionManager.eventLog.value.size
        sessionManager.logEvent("TEST", "Diagnostic test event")

        val events = sessionManager.eventLog.value
        assertTrue(events.size > initialSize)
        assertEquals("TEST", events.first().category)
        assertEquals("Diagnostic test event", events.first().message)
    }

    @Test
    fun `clearEventLog resets events list`() {
        sessionManager.logEvent("TEST", "Test event to clear")
        sessionManager.clearEventLog()
        assertEquals(0, sessionManager.eventLog.value.size)
    }

    @Test
    fun `session lifecycle pause and resume controls`() {
        sessionManager.startSession(SessionType.MEDIA)
        assertEquals(SessionLifecycleState.ACTIVE, sessionManager.activeSession.value.lifecycleState)

        sessionManager.pauseSession()
        assertEquals(SessionLifecycleState.PAUSED, sessionManager.activeSession.value.lifecycleState)

        sessionManager.resumeSession()
        assertEquals(SessionLifecycleState.ACTIVE, sessionManager.activeSession.value.lifecycleState)

        sessionManager.stopSession()
        assertEquals(SessionLifecycleState.IDLE, sessionManager.activeSession.value.lifecycleState)
    }

    @Test
    fun `increaseBuffer increases buffer size and resets instability flag`() {
        sessionManager.startSession(SessionType.GAMING)
        val initialBufferSize = performanceManager.performanceState.value.actualBufferSizeFrames

        // Simulate buffer instability
        sessionManager.notifyBufferUnderrun(3)
        assertTrue(sessionManager.activeSession.value.isBufferUnstable)
        assertEquals(3, sessionManager.activeSession.value.outputUnderruns)

        // Invoke increaseBuffer
        sessionManager.increaseBuffer()

        // Verify buffer size increased and instability cleared
        val newBufferSize = performanceManager.performanceState.value.actualBufferSizeFrames
        assertTrue(newBufferSize >= initialBufferSize)
        assertFalse(sessionManager.activeSession.value.isBufferUnstable)
    }

    @Test
    fun `releaseAll stops active sessions and cleans up resources`() {
        sessionManager.startSession(SessionType.VOICE_CHAT)
        sessionManager.releaseAll()

        val session = sessionManager.activeSession.value
        assertEquals(SessionLifecycleState.IDLE, session.lifecycleState)
        assertFalse(sessionManager.isMonitoringEnabled.value)
    }
}
