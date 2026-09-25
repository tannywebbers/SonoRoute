package com.example.audio.routing

import com.example.audio.model.AudioDeviceModel

enum class RouteControlMode {
    SYSTEM_CONTROLLED,
    APPLICATION_CONTROLLED
}

sealed class RouteOperationState {
    object Idle : RouteOperationState()
    data class InProgress(val deviceName: String) : RouteOperationState()
    data class Success(val device: AudioDeviceModel, val isApplicationControlled: Boolean) : RouteOperationState()
    data class Error(val deviceName: String, val message: String, val canRetry: Boolean = true) : RouteOperationState()
}

data class AudioRoutingState(
    val requestedOutputDevice: AudioDeviceModel? = null,
    val actualOutputDevice: AudioDeviceModel? = null,
    val requestedInputDevice: AudioDeviceModel? = null,
    val actualInputDevice: AudioDeviceModel? = null,
    val outputRouteVerified: Boolean = false,
    val inputRouteVerified: Boolean = false,
    val outputRoutingReason: String? = null,
    val inputRoutingReason: String? = null,
    val outputStatus: String = "System default",
    val inputStatus: String = "System default",
    val availableOutputDevices: List<AudioDeviceModel> = emptyList(),
    val availableInputDevices: List<AudioDeviceModel> = emptyList(),
    val lastUpdated: Long = 0L,
    val systemControlled: Boolean = true,
    val activeCommunicationDeviceName: String? = null,
    val isOutputTesting: Boolean = false,
    val outputTestCountdown: Int = 0,
    val outputTestProgress: Float = 0.0f,
    val outputTestResult: String? = null,
    val isMicrophoneTesting: Boolean = false,
    val microphoneTestPhase: String? = null,
    val microphoneTestCountdown: Int = 0,
    val microphoneTestProgress: Float = 0.0f,
    val microphoneTestLevel: Float = 0.0f,
    val microphoneTestResult: String? = null,
    val lastErrorMessage: String? = null
) {
    val selectedOutputDevice: AudioDeviceModel?
        get() = requestedOutputDevice

    val selectedInputDevice: AudioDeviceModel?
        get() = requestedInputDevice
}
