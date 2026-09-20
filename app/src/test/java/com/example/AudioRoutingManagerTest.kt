package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.audio.model.AudioDeviceModel
import com.example.audio.model.DeviceCategory
import com.example.audio.routing.AudioRoutingManager
import com.example.audio.routing.RouteControlMode
import com.example.audio.routing.RouteOperationState
import com.example.ui.AudioProfile
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AudioRoutingManagerTest {

    private lateinit var context: Context
    private lateinit var routingManager: AudioRoutingManager

    private val testSpeaker = AudioDeviceModel(
        id = 1,
        name = "Phone Speaker",
        type = 2,
        typeLabel = "Phone Speaker",
        category = DeviceCategory.BUILT_IN,
        isInput = false,
        isOutput = true,
        isSelected = false,
        isBuiltIn = true
    )

    private val testHeadphones = AudioDeviceModel(
        id = 2,
        name = "Wired Headphones",
        type = 4,
        typeLabel = "Wired Headphones",
        category = DeviceCategory.WIRED,
        isInput = false,
        isOutput = true,
        isSelected = false,
        isWired = true
    )

    private val testBuiltInMic = AudioDeviceModel(
        id = 10,
        name = "Phone Microphone",
        type = 15,
        typeLabel = "Built-in Microphone",
        category = DeviceCategory.BUILT_IN,
        isInput = true,
        isOutput = false,
        isSelected = false,
        isBuiltIn = true
    )

    private val testHeadsetMic = AudioDeviceModel(
        id = 11,
        name = "Headset Microphone",
        type = 11,
        typeLabel = "Wired Headset",
        category = DeviceCategory.WIRED,
        isInput = true,
        isOutput = false,
        isSelected = false,
        isWired = true
    )

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        routingManager = AudioRoutingManager(context)
    }

    @Test
    fun testInitialRoutingState() {
        assertEquals(RouteOperationState.Idle, routingManager.routeState.value)
        assertEquals(RouteControlMode.SYSTEM_CONTROLLED, routingManager.outputControlMode.value)
        assertEquals(RouteControlMode.SYSTEM_CONTROLLED, routingManager.inputControlMode.value)
        assertNull(routingManager.userSelectedOutput.value)
        assertNull(routingManager.userSelectedInput.value)

        val routingState = routingManager.routingState.value
        assertEquals("System controlled", routingState.outputStatus)
        assertEquals("System controlled", routingState.inputStatus)
        assertFalse(routingState.isOutputTesting)
        assertFalse(routingState.isMicrophoneTesting)
    }

    @Test
    fun testResetOutputToSystemDefault() {
        routingManager.resetOutputToSystemDefault()
        assertNull(routingManager.userSelectedOutput.value)
        assertEquals(RouteControlMode.SYSTEM_CONTROLLED, routingManager.outputControlMode.value)
        assertEquals(RouteOperationState.Idle, routingManager.routeState.value)
    }

    @Test
    fun testResetInputToSystemDefault() {
        routingManager.resetInputToSystemDefault()
        assertNull(routingManager.userSelectedInput.value)
        assertEquals(RouteControlMode.SYSTEM_CONTROLLED, routingManager.inputControlMode.value)
    }

    @Test
    fun testSelectOutputDeviceDoesNotOverrideInputState() = runBlocking {
        routingManager.updateAvailableDevices(
            outputs = listOf(testSpeaker, testHeadphones),
            inputs = listOf(testBuiltInMic, testHeadsetMic)
        )

        // Select Output
        val result = routingManager.selectOutputDevice(testHeadphones)
        // Check output route
        assertEquals(RouteControlMode.APPLICATION_CONTROLLED, routingManager.outputControlMode.value)
        assertEquals(testHeadphones.id, routingManager.userSelectedOutput.value?.id)

        // Verify independent input state is unaffected
        assertEquals(RouteControlMode.SYSTEM_CONTROLLED, routingManager.inputControlMode.value)
        assertNull(routingManager.userSelectedInput.value)
    }

    @Test
    fun testSelectInputDeviceDoesNotOverrideOutputState() = runBlocking {
        routingManager.updateAvailableDevices(
            outputs = listOf(testSpeaker, testHeadphones),
            inputs = listOf(testBuiltInMic, testHeadsetMic)
        )

        // Select Input
        val result = routingManager.selectInputDevice(testHeadsetMic)
        // Check input route
        assertEquals(RouteControlMode.APPLICATION_CONTROLLED, routingManager.inputControlMode.value)
        assertEquals(testHeadsetMic.id, routingManager.userSelectedInput.value?.id)

        // Verify independent output state is unaffected
        assertEquals(RouteControlMode.SYSTEM_CONTROLLED, routingManager.outputControlMode.value)
        assertNull(routingManager.userSelectedOutput.value)
    }

    @Test
    fun testHandleDeviceRemovedClearsActiveRoute() = runBlocking {
        routingManager.updateAvailableDevices(
            outputs = listOf(testSpeaker, testHeadphones),
            inputs = listOf(testBuiltInMic)
        )
        routingManager.selectOutputDevice(testHeadphones)
        assertEquals(testHeadphones.id, routingManager.userSelectedOutput.value?.id)

        // Device disconnected
        routingManager.handleDeviceRemoved(
            removedDeviceId = testHeadphones.id,
            fallbackSpeaker = testSpeaker,
            fallbackMic = testBuiltInMic
        )

        // Should switch back to fallback or system controlled
        val currentOutput = routingManager.userSelectedOutput.value
        assertTrue(currentOutput == null || currentOutput.id == testSpeaker.id)
    }

    @Test
    fun testHandleDeviceAddedRestoresUserPreferredDevice() = runBlocking {
        routingManager.updateAvailableDevices(
            outputs = listOf(testSpeaker, testHeadphones),
            inputs = listOf(testBuiltInMic)
        )
        // User previously selected testHeadphones
        routingManager.selectOutputDevice(testHeadphones)

        // Now device disconnected
        routingManager.handleDeviceRemoved(testHeadphones.id, testSpeaker, testBuiltInMic)

        // Reconnect event
        routingManager.handleDeviceAdded(testHeadphones)

        // Preferred route restored
        assertEquals(testHeadphones.id, routingManager.userSelectedOutput.value?.id)
    }

    @Test
    fun testOutputDiagnosticTestRunsCleanly() {
        var statusReceived = ""
        routingManager.testOutputRoute(testSpeaker) { status ->
            statusReceived = status
        }
        // Verification that test execution sets isOutputTesting state or delivers status
        assertNotNull(routingManager.routingState.value.outputTestResult)
    }

    @Test
    fun testMicrophoneDiagnosticTestCompletesWithoutCrash() {
        routingManager.testMicrophoneRoute(
            targetDevice = testBuiltInMic,
            onAmplitude = {},
            onComplete = { success, msg -> }
        )
        assertNotNull(routingManager.routingState.value.microphoneTestResult)
    }

    @Test
    fun testRoutingPersistenceAcrossInstances() {
        // Provide available devices to initial instance
        routingManager.updateAvailableDevices(
            outputs = listOf(testSpeaker, testHeadphones),
            inputs = listOf(testBuiltInMic, testHeadsetMic)
        )

        // Select preferred devices in the first instance
        routingManager.selectOutputDevice(testHeadphones)
        routingManager.selectInputDevice(testHeadsetMic)

        // Instantiate a second AudioRoutingManager simulating app relaunch
        val newRoutingManager = AudioRoutingManager(context)
        newRoutingManager.updateAvailableDevices(
            outputs = listOf(testSpeaker, testHeadphones),
            inputs = listOf(testBuiltInMic, testHeadsetMic)
        )

        // Verify preferences were restored from SharedPreferences
        assertEquals(testHeadphones.id, newRoutingManager.userSelectedOutput.value?.id)
        assertEquals(testHeadsetMic.id, newRoutingManager.userSelectedInput.value?.id)
    }

    @Test
    fun testAudioProfileDefinitions() {
        val profiles = AudioProfile.values()
        assertEquals(6, profiles.size)
        assertTrue(profiles.contains(AudioProfile.STANDARD))
        assertTrue(profiles.contains(AudioProfile.GAMING))
        assertTrue(profiles.contains(AudioProfile.VOICE_CHAT))
        assertTrue(profiles.contains(AudioProfile.RECORDING))
        assertTrue(profiles.contains(AudioProfile.SCREEN_SHARING))
        assertTrue(profiles.contains(AudioProfile.MEDIA))
        assertEquals("Standard Audio", AudioProfile.STANDARD.title)
    }
}
