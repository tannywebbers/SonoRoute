package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.audio.model.AudioDeviceModel
import com.example.audio.model.DeviceCategory
import com.example.audio.performance.AudioFormatOption
import com.example.audio.performance.AudioPerformanceManager
import com.example.audio.performance.AudioPerformanceMode
import com.example.audio.performance.BufferOption
import com.example.audio.performance.ChannelOption
import com.example.audio.performance.LatencyLevel
import com.example.ui.AudioProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AudioPerformanceManagerTest {

    private lateinit var context: Context
    private lateinit var performanceManager: AudioPerformanceManager

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        performanceManager = AudioPerformanceManager(context)
    }

    @Test
    fun `initial performance state has valid sample rate and buffer`() {
        val state = performanceManager.performanceState.value
        assertNotNull(state)
        assertTrue(state.actualSampleRate > 0)
        assertTrue(state.actualBufferSizeFrames > 0)
        assertEquals(AudioPerformanceMode.STANDARD, state.actualPerformanceMode)
        assertEquals(BufferOption.AUTO, state.requestedBufferOption)
        assertNull(state.requestedCustomBufferFrames)
        assertTrue(state.estimatedBufferLatencyMs > 0.0)
    }

    @Test
    fun `setPerformanceMode updates performance mode in state`() {
        performanceManager.setPerformanceMode(AudioPerformanceMode.STABLE)
        val state = performanceManager.performanceState.value
        assertEquals(AudioPerformanceMode.STABLE, state.actualPerformanceMode)
    }

    @Test
    fun `setBufferOption updates buffer option in state`() {
        performanceManager.setBufferOption(BufferOption.BALANCED)
        val state = performanceManager.performanceState.value
        assertEquals(BufferOption.BALANCED, state.requestedBufferOption)
        assertNull(state.requestedCustomBufferFrames)
    }

    @Test
    fun `setCustomBufferFrames updates requested buffer frames`() {
        performanceManager.setCustomBufferFrames(256)
        val state = performanceManager.performanceState.value
        assertEquals(256, state.requestedCustomBufferFrames)
    }

    @Test
    fun `setChannelOption updates channel configuration`() {
        performanceManager.setChannelOption(ChannelOption.MONO)
        val state = performanceManager.performanceState.value
        assertEquals(1, state.actualChannelCount)

        performanceManager.setChannelOption(ChannelOption.STEREO)
        val stateStereo = performanceManager.performanceState.value
        assertEquals(2, stateStereo.actualChannelCount)
    }

    @Test
    fun `setFormatOption updates format preference`() {
        performanceManager.setFormatOption(AudioFormatOption.PCM_FLOAT)
        val state = performanceManager.performanceState.value
        assertEquals("PCM Float", state.actualFormat)
    }

    @Test
    fun `resetToDefaults restores initial default preferences`() {
        performanceManager.setPerformanceMode(AudioPerformanceMode.STABLE)
        performanceManager.setBufferOption(BufferOption.STABLE)
        performanceManager.setCustomBufferFrames(1024)

        performanceManager.resetToDefaults()

        val state = performanceManager.performanceState.value
        assertEquals(AudioPerformanceMode.STANDARD, state.actualPerformanceMode)
        assertEquals(BufferOption.AUTO, state.requestedBufferOption)
        assertNull(state.requestedCustomBufferFrames)
    }

    @Test
    fun `onActiveDeviceChanged re-evaluates buffers for new device`() {
        val sampleDevice = AudioDeviceModel(
            id = 42,
            name = "Studio Headphones",
            type = 4,
            typeLabel = "Wired Headphones",
            category = DeviceCategory.WIRED,
            isInput = false,
            isOutput = true,
            isSelected = true,
            isBuiltIn = false
        )

        performanceManager.onActiveDeviceChanged(sampleDevice)
        val state = performanceManager.performanceState.value
        assertTrue(state.actualBufferSizeFrames > 0)
    }

    @Test
    fun `setLatencyLevel updates requested latency level and buffer`() {
        performanceManager.setLatencyLevel(LatencyLevel.VERY_LOW)
        val state = performanceManager.performanceState.value
        assertEquals(LatencyLevel.VERY_LOW, state.requestedLatencyLevel)
    }

    @Test
    fun `profile switching switches profile-isolated preferences`() {
        // Configure profile GAMING
        performanceManager.onProfileChanged(AudioProfile.GAMING)
        performanceManager.setLatencyLevel(LatencyLevel.VERY_LOW)
        assertEquals(LatencyLevel.VERY_LOW, performanceManager.performanceState.value.requestedLatencyLevel)

        // Configure profile STANDARD
        performanceManager.onProfileChanged(AudioProfile.STANDARD)
        performanceManager.setLatencyLevel(LatencyLevel.BALANCED)
        assertEquals(LatencyLevel.BALANCED, performanceManager.performanceState.value.requestedLatencyLevel)

        // Switch back to GAMING and verify it retained VERY_LOW
        performanceManager.onProfileChanged(AudioProfile.GAMING)
        assertEquals(LatencyLevel.VERY_LOW, performanceManager.performanceState.value.requestedLatencyLevel)
    }

    @Test
    fun `setCustomSampleRate validates and updates state`() {
        val success = performanceManager.setCustomSampleRate(48000)
        assertTrue(success)
        val state = performanceManager.performanceState.value
        assertEquals(48000, state.requestedSampleRate)

        // Invalid sample rate
        val invalid = performanceManager.setCustomSampleRate(500)
        org.junit.Assert.assertFalse(invalid)
    }

    @Test
    fun `resetProfileToDefaults resets current profile only`() {
        performanceManager.onProfileChanged(AudioProfile.VOICE_CHAT)
        performanceManager.setLatencyLevel(LatencyLevel.HIGH)
        assertEquals(LatencyLevel.HIGH, performanceManager.performanceState.value.requestedLatencyLevel)

        performanceManager.resetProfileToDefaults()
        val state = performanceManager.performanceState.value
        assertEquals(LatencyLevel.LOW, state.requestedLatencyLevel)
    }
}
