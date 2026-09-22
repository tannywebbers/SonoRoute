package com.example.ui

import android.app.Application
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.audio.device.AudioDeviceManager
import com.example.audio.diagnostics.AudioDiagnostics
import com.example.audio.focus.FocusState
import com.example.audio.model.AudioDeviceModel
import com.example.audio.performance.AudioFormatOption
import com.example.audio.performance.AudioPerformanceManager
import com.example.audio.performance.AudioPerformanceMode
import com.example.audio.performance.AudioPerformanceState
import com.example.audio.performance.BufferOption
import com.example.audio.performance.ChannelOption
import com.example.audio.performance.LatencyLevel
import com.example.audio.routing.AudioRoutingManager
import com.example.audio.routing.AudioRoutingState
import com.example.audio.routing.RouteControlMode
import com.example.audio.routing.RouteOperationState
import com.example.audio.session.AudioSession
import com.example.audio.session.AudioSessionManager
import com.example.audio.session.MicrophoneResourceState
import com.example.audio.session.OutputResourceState
import com.example.audio.session.SessionEvent
import com.example.audio.session.SessionType
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class AudioProfile(val title: String, val subtitle: String) {
    STANDARD("Standard Audio", "Balanced audio output and input"),
    GAMING("Gaming", "Ultra-low latency audio pipeline"),
    RECORDING("Recording", "Stable uncompressed microphone capture"),
    VOICE_CHAT("Voice Chat", "Communication-focused audio routing"),
    SCREEN_SHARING("Screen Sharing", "Capture-compatible audio configuration"),
    MEDIA("Media Playback", "High-fidelity audio playback stream")
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val audioDeviceManager = AudioDeviceManager(application, viewModelScope)
    private val audioRoutingManager = AudioRoutingManager(application)
    private val audioPerformanceManager = AudioPerformanceManager(application)
    val audioSessionManager = AudioSessionManager(
        context = application,
        routingManager = audioRoutingManager,
        performanceManager = audioPerformanceManager,
        scope = viewModelScope
    )

    val outputDevices: StateFlow<List<AudioDeviceModel>> = audioDeviceManager.outputDevices
    val inputDevices: StateFlow<List<AudioDeviceModel>> = audioDeviceManager.inputDevices
    val diagnostics: StateFlow<AudioDiagnostics?> = audioDeviceManager.diagnostics

    val routeState: StateFlow<RouteOperationState> = audioRoutingManager.routeState
    val outputControlMode: StateFlow<RouteControlMode> = audioRoutingManager.outputControlMode
    val inputControlMode: StateFlow<RouteControlMode> = audioRoutingManager.inputControlMode
    val routingState: StateFlow<AudioRoutingState> = audioRoutingManager.routingState
    val performanceState: StateFlow<AudioPerformanceState> = audioPerformanceManager.performanceState

    val activeSession: StateFlow<AudioSession> = audioSessionManager.activeSession
    val micResourceState: StateFlow<MicrophoneResourceState> = audioSessionManager.micResourceState
    val outputResourceState: StateFlow<OutputResourceState> = audioSessionManager.outputResourceState
    val isMonitoringEnabled: StateFlow<Boolean> = audioSessionManager.isMonitoringEnabled
    val monitoringLevel: StateFlow<Float> = audioSessionManager.monitoringLevel
    val monitoringFeedbackWarning: StateFlow<String?> = audioSessionManager.monitoringFeedbackWarning
    val eventLog: StateFlow<List<SessionEvent>> = audioSessionManager.eventLog
    val focusState: StateFlow<FocusState> = audioSessionManager.focusManager.currentFocusState
    val playbackCaptureSupported: Boolean = audioSessionManager.isAudioPlaybackCaptureSupported()
    val playbackCaptureStatus: String = audioSessionManager.getAudioPlaybackCaptureStatus()

    // Effective active output: user-selected route takes precedence; fallback to system-resolved output
    val activeOutputDevice: StateFlow<AudioDeviceModel?> = combine(
        audioRoutingManager.userSelectedOutput,
        audioDeviceManager.activeOutputDevice
    ) { userSelected, systemActive ->
        userSelected ?: systemActive
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    // Effective active input: user-selected route takes precedence; fallback to system-resolved input
    val activeInputDevice: StateFlow<AudioDeviceModel?> = combine(
        audioRoutingManager.userSelectedInput,
        audioDeviceManager.activeInputDevice
    ) { userSelected, systemActive ->
        userSelected ?: systemActive
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _bannerMessage = MutableStateFlow<String?>(null)
    val bannerMessage: StateFlow<String?> = _bannerMessage.asStateFlow()

    private val _hasMicPermission = MutableStateFlow(checkMicPermission())
    val hasMicPermission: StateFlow<Boolean> = _hasMicPermission.asStateFlow()

    private val _hasBluetoothPermission = MutableStateFlow(checkBluetoothPermission())
    val hasBluetoothPermission: StateFlow<Boolean> = _hasBluetoothPermission.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _currentProfile = MutableStateFlow(AudioProfile.STANDARD)
    val currentProfile: StateFlow<AudioProfile> = _currentProfile.asStateFlow()

    init {
        viewModelScope.launch {
            activeOutputDevice.collect { device ->
                audioPerformanceManager.onActiveDeviceChanged(device)
            }
        }

        viewModelScope.launch {
            combine(outputDevices, inputDevices) { outputs, inputs ->
                Pair(outputs, inputs)
            }.collect { (outputs, inputs) ->
                audioRoutingManager.updateAvailableDevices(outputs, inputs)
            }
        }

        viewModelScope.launch {
            combine(
                activeOutputDevice,
                activeInputDevice,
                performanceState
            ) { output, input, perf ->
                Triple(output, input, perf)
            }.collect { (output, input, perf) ->
                audioSessionManager.reconfigureActiveSession(output, input, perf)
            }
        }

        viewModelScope.launch {
            audioDeviceManager.connectionEvents.collect { event ->
                val action = if (event.isConnected) "connected" else "disconnected"
                _bannerMessage.value = "${event.deviceName} $action"
                audioSessionManager.logEvent("HARDWARE", "${event.deviceName} $action")

                if (event.isConnected) {
                    val addedDevice = (outputDevices.value + inputDevices.value).find { it.name == event.deviceName }
                    if (addedDevice != null) {
                        audioRoutingManager.handleDeviceAdded(addedDevice)
                    }
                } else {
                    val removedOutput = outputDevices.value.find { it.name == event.deviceName }
                    val fallbackSpeaker = outputDevices.value.find { it.isBuiltIn && it.isOutput }
                    val fallbackMic = inputDevices.value.find { it.isBuiltIn && it.isInput }
                    val removedInput = inputDevices.value.find { it.name == event.deviceName }

                    if (removedOutput != null || removedInput != null) {
                        val devId = removedOutput?.id ?: removedInput?.id ?: -1
                        audioRoutingManager.handleDeviceRemoved(devId, fallbackSpeaker, fallbackMic)
                    }
                }
            }
        }
    }

    fun refresh() {
        _hasMicPermission.value = checkMicPermission()
        _hasBluetoothPermission.value = checkBluetoothPermission()
        audioDeviceManager.refreshDevices()
    }

    fun onPullToRefresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            refresh()
            // Native-feeling subtle settle
            delay(350)
            _isRefreshing.value = false
        }
    }

    /**
     * User requests routing output to [device].
     */
    fun selectOutput(device: AudioDeviceModel) {
        viewModelScope.launch {
            audioSessionManager.logEvent("ROUTING", "Output route requested: ${device.name}")
            audioRoutingManager.selectOutputDevice(device)
            audioDeviceManager.refreshDevices()
        }
    }

    /**
     * User requests routing input to [device].
     */
    fun selectInput(device: AudioDeviceModel) {
        viewModelScope.launch {
            audioSessionManager.logEvent("ROUTING", "Input route requested: ${device.name}")
            audioRoutingManager.selectInputDevice(device)
            audioDeviceManager.refreshDevices()
        }
    }

    /**
     * Restores output to Android system default.
     */
    fun resetOutputToSystemDefault() {
        viewModelScope.launch {
            audioSessionManager.logEvent("ROUTING", "Reset output to system default")
            audioRoutingManager.resetOutputToSystemDefault()
            audioDeviceManager.refreshDevices()
        }
    }

    /**
     * Restores input to Android system default.
     */
    fun resetInputToSystemDefault() {
        viewModelScope.launch {
            audioSessionManager.logEvent("ROUTING", "Reset input to system default")
            audioRoutingManager.resetInputToSystemDefault()
            audioDeviceManager.refreshDevices()
        }
    }

    fun selectProfile(profile: AudioProfile) {
        _currentProfile.value = profile
        audioPerformanceManager.onProfileChanged(profile)
        val sessionType = SessionType.fromAudioProfile(profile)
        audioSessionManager.startSession(sessionType)
        audioSessionManager.logEvent("SESSION", "Profile selected: ${profile.title}")
    }

    fun setLatencyLevel(level: LatencyLevel) {
        audioPerformanceManager.setLatencyLevel(level)
        audioSessionManager.logEvent("PERFORMANCE", "Latency level requested: ${level.title}")
    }

    fun setPerformanceMode(mode: AudioPerformanceMode) {
        audioPerformanceManager.setPerformanceMode(mode)
    }

    fun setBufferOption(option: BufferOption) {
        audioPerformanceManager.setBufferOption(option)
        audioSessionManager.logEvent("PERFORMANCE", "Buffer preset selected: ${option.title}")
    }

    fun setCustomBufferFrames(frames: Int?): Boolean {
        val result = audioPerformanceManager.setCustomBufferFrames(frames)
        if (result && frames != null) {
            audioSessionManager.logEvent("PERFORMANCE", "Custom buffer set: $frames frames")
        }
        return result
    }

    fun setSampleRatePreference(rate: Int) {
        audioPerformanceManager.setSampleRatePreference(rate)
        val label = if (rate == 0) "Auto" else "$rate Hz"
        audioSessionManager.logEvent("PERFORMANCE", "Sample rate set: $label")
    }

    fun setCustomSampleRate(rate: Int): Boolean {
        val result = audioPerformanceManager.setCustomSampleRate(rate)
        if (result) {
            val label = if (rate <= 0) "Auto" else "$rate Hz"
            audioSessionManager.logEvent("PERFORMANCE", "Custom sample rate set: $label")
        }
        return result
    }

    fun setChannelOption(channelOption: ChannelOption) {
        audioPerformanceManager.setChannelOption(channelOption)
    }

    fun setFormatOption(formatOption: AudioFormatOption) {
        audioPerformanceManager.setFormatOption(formatOption)
    }

    fun resetProfileToDefaults() {
        audioPerformanceManager.resetProfileToDefaults()
        audioSessionManager.logEvent("SESSION", "Profile reset to defaults: ${_currentProfile.value.title}")
    }

    fun resetPerformanceDefaults() {
        audioPerformanceManager.resetToDefaults()
        audioSessionManager.logEvent("PERFORMANCE", "All performance settings reset to defaults")
    }

    fun startSession(profile: AudioProfile = _currentProfile.value) {
        val sessionType = SessionType.fromAudioProfile(profile)
        audioSessionManager.startSession(sessionType)
    }

    fun pauseSession() {
        audioSessionManager.pauseSession()
    }

    fun resumeSession() {
        audioSessionManager.resumeSession()
    }

    fun stopSession() {
        audioSessionManager.stopSession()
    }

    fun increaseBuffer(): Boolean {
        return audioSessionManager.increaseBuffer()
    }

    fun setMicrophoneMonitoring(enabled: Boolean, overrideSpeakerWarning: Boolean = false): Pair<Boolean, String?> {
        return audioSessionManager.setMicrophoneMonitoring(enabled, overrideSpeakerWarning)
    }

    fun setMonitoringLevel(level: Float) {
        audioSessionManager.setMonitoringLevel(level)
    }

    fun clearDiagnosticsLog() {
        audioSessionManager.clearEventLog()
    }

    fun testOutputRoute(onStatusUpdate: (String) -> Unit = {}) {
        val (hasAccess, err) = audioSessionManager.requestOutputAccess("Output Route Test")
        if (!hasAccess) {
            onStatusUpdate(err ?: "Output stream is currently in use.")
            return
        }
        audioRoutingManager.testOutputRoute(activeOutputDevice.value) { status ->
            if (status.contains("Finished") || status.contains("complete", ignoreCase = true) || status.contains("error", ignoreCase = true) || status.contains("Failed")) {
                audioSessionManager.releaseOutputAccess("Output Route Test")
            }
            onStatusUpdate(status)
        }
    }

    fun testMicrophoneRoute(
        onAmplitude: (Float) -> Unit,
        onComplete: (Boolean, String) -> Unit
    ) {
        val (hasAccess, err) = audioSessionManager.requestMicrophoneAccess("Microphone Route Test")
        if (!hasAccess) {
            onComplete(false, err ?: "Microphone is currently in use.")
            return
        }
        audioRoutingManager.testMicrophoneRoute(
            activeInputDevice.value,
            onAmplitude = onAmplitude,
            onComplete = { success, msg ->
                audioSessionManager.releaseMicrophoneAccess("Microphone Route Test")
                onComplete(success, msg)
            }
        )
    }

    fun dismissRouteError() {
        audioRoutingManager.dismissRouteError()
    }

    fun dismissBanner() {
        _bannerMessage.value = null
    }

    fun onPermissionResult(permission: String, isGranted: Boolean) {
        if (permission == android.Manifest.permission.RECORD_AUDIO) {
            _hasMicPermission.value = isGranted
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            permission == android.Manifest.permission.BLUETOOTH_CONNECT
        ) {
            _hasBluetoothPermission.value = isGranted
        }
        refresh()
    }

    private fun checkMicPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            getApplication(),
            android.Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun checkBluetoothPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(
                getApplication(),
                android.Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    override fun onCleared() {
        super.onCleared()
        audioDeviceManager.unregisterCallbacks()
        audioSessionManager.releaseAll()
    }
}

