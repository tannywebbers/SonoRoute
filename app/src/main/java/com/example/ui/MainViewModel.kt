package com.example.ui

import android.app.Application
import android.content.pm.PackageManager
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    val audioDeviceManager = AudioDeviceManager(application, viewModelScope)
    val audioRoutingManager = AudioRoutingManager(application, viewModelScope)
    val audioPerformanceManager = AudioPerformanceManager(application)
    val audioSessionManager = AudioSessionManager(application, audioRoutingManager, audioPerformanceManager, viewModelScope)

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

    val activeOutputDevice: StateFlow<AudioDeviceModel?> = combine(
        audioRoutingManager.actualOutput,
        audioDeviceManager.activeOutputDevice
    ) { routed, systemActive ->
        routed ?: systemActive
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val activeInputDevice: StateFlow<AudioDeviceModel?> = combine(
        audioRoutingManager.actualInput,
        audioDeviceManager.activeInputDevice
    ) { routed, systemActive ->
        routed ?: systemActive
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _bannerMessage = MutableStateFlow<String?>(null)
    val bannerMessage: StateFlow<String?> = _bannerMessage.asStateFlow()

    private val _hasMicPermission = MutableStateFlow(
        ContextCompat.checkSelfPermission(application, android.Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    )
    val hasMicPermission: StateFlow<Boolean> = _hasMicPermission.asStateFlow()

    private val _hasBluetoothPermission = MutableStateFlow(
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(application, android.Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        } else true
    )
    val hasBluetoothPermission: StateFlow<Boolean> = _hasBluetoothPermission.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _currentProfile = MutableStateFlow(AudioProfile.STANDARD)
    val currentProfile: StateFlow<AudioProfile> = _currentProfile.asStateFlow()

    init {
        viewModelScope.launch {
            combine(outputDevices, inputDevices) { outs, ins ->
                Pair(outs, ins)
            }.collect { (outs, ins) ->
                audioRoutingManager.updateAvailableDevices(outs, ins)
            }
        }
        viewModelScope.launch {
            audioDeviceManager.connectionEvents.collect { event ->
                val stateText = if (event.isConnected) "connected" else "disconnected"
                _bannerMessage.value = "${event.deviceName} $stateText"
                audioSessionManager.logEvent("DEVICE", "${event.deviceName} $stateText")
            }
        }
    }

    fun refresh() {
        audioDeviceManager.refreshDevices()
    }

    fun onPullToRefresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            audioDeviceManager.refreshDevices()
            kotlinx.coroutines.delay(600)
            _isRefreshing.value = false
        }
    }

    fun selectOutput(device: AudioDeviceModel) {
        viewModelScope.launch {
            audioSessionManager.logEvent("ROUTING", "Output route requested: ${device.name}")
            val ok = audioRoutingManager.selectOutputDevice(device)
            audioDeviceManager.refreshDevices()
            if (ok) {
                _bannerMessage.value = "Audio routed to ${device.name} (Global)"
            }
        }
    }

    fun selectInput(device: AudioDeviceModel) {
        viewModelScope.launch {
            audioSessionManager.logEvent("ROUTING", "Input route requested: ${device.name}")
            val ok = audioRoutingManager.selectInputDevice(device)
            audioDeviceManager.refreshDevices()
            if (ok) {
                _bannerMessage.value = "Microphone routed to ${device.name} (Global)"
            }
        }
    }

    fun resetOutputToSystemDefault() {
        viewModelScope.launch {
            audioRoutingManager.resetOutputToSystemDefault()
            audioDeviceManager.refreshDevices()
            audioSessionManager.logEvent("ROUTING", "Output reset to system default")
            _bannerMessage.value = "Output reset to System Default"
        }
    }

    fun resetInputToSystemDefault() {
        viewModelScope.launch {
            audioRoutingManager.resetInputToSystemDefault()
            audioDeviceManager.refreshDevices()
            audioSessionManager.logEvent("ROUTING", "Input reset to system default")
            _bannerMessage.value = "Input reset to System Default"
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
    }

    fun setPerformanceMode(mode: AudioPerformanceMode) {
        audioPerformanceManager.setPerformanceMode(mode)
    }

    fun setBufferOption(option: BufferOption) {
        audioPerformanceManager.setBufferOption(option)
    }

    fun setCustomBufferFrames(frames: Int?): Boolean {
        return audioPerformanceManager.setCustomBufferFrames(frames)
    }

    fun setSampleRatePreference(rate: Int) {
        audioPerformanceManager.setSampleRatePreference(rate)
    }

    fun setCustomSampleRate(rate: Int) {
        audioPerformanceManager.setCustomSampleRate(rate)
    }

    fun setChannelOption(channelOption: ChannelOption) {
        audioPerformanceManager.setChannelOption(channelOption)
    }

    fun setFormatOption(formatOption: AudioFormatOption) {
        audioPerformanceManager.setFormatOption(formatOption)
    }

    fun resetProfileToDefaults() {
        audioPerformanceManager.resetProfileToDefaults()
    }

    fun resetPerformanceDefaults() {
        audioPerformanceManager.resetPerformanceDefaults()
    }

    fun startSession(sessionType: SessionType = SessionType.GENERAL) {
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

    fun increaseBuffer() {
        audioSessionManager.increaseBuffer()
    }

    fun setMicrophoneMonitoring(enabled: Boolean, overrideSpeakerWarning: Boolean = false): Pair<Boolean, String> {
        return audioSessionManager.setMicrophoneMonitoring(enabled, overrideSpeakerWarning)
    }

    fun setMonitoringLevel(level: Float) {
        audioSessionManager.setMonitoringLevel(level)
    }

    fun clearDiagnosticsLog() {
        audioSessionManager.clearEventLog()
    }

    fun testOutputRoute(targetDevice: AudioDeviceModel? = null, onStatusUpdate: (String) -> Unit = {}) {
        audioRoutingManager.testOutputRoute(targetDevice) { status ->
            _bannerMessage.value = status
            onStatusUpdate(status)
        }
    }

    fun testMicrophoneRoute(onAmplitude: (Float) -> Unit, onComplete: (Boolean, String) -> Unit) {
        audioRoutingManager.testMicrophoneRoute(null, onAmplitude) { success, msg ->
            _bannerMessage.value = msg
            onComplete(success, msg)
        }
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
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S &&
            permission == android.Manifest.permission.BLUETOOTH_CONNECT) {
            _hasBluetoothPermission.value = isGranted
        }
    }

    override fun onCleared() {
        super.onCleared()
        audioDeviceManager.unregisterCallbacks()
        audioSessionManager.releaseAll()
    }
}
