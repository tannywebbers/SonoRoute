package com.example.audio.routing

import com.example.audio.model.AudioDeviceModel

/**
 * Central routing state model for SonoRoute.
 * Maintains independent representations of output and input routing states,
 * verification statuses, and available hardware devices.
 */
data class AudioRoutingState(
    val requestedOutputDevice: AudioDeviceModel? = null,
    val actualOutputDevice: AudioDeviceModel? = null,
    val requestedInputDevice: AudioDeviceModel? = null,
    val actualInputDevice: AudioDeviceModel? = null,
    val outputRouteVerified: Boolean = false,
    val inputRouteVerified: Boolean = false,
    val outputRoutingReason: String? = null,
    val inputRoutingReason: String? = null,
    val outputStatus: String = "System controlled",
    val inputStatus: String = "System controlled",
    val availableOutputDevices: List<AudioDeviceModel> = emptyList(),
    val availableInputDevices: List<AudioDeviceModel> = emptyList(),
    val lastUpdated: Long = System.currentTimeMillis(),
    val systemControlled: Boolean = true,
    val activeCommunicationDeviceName: String? = null,
    val isOutputTesting: Boolean = false,
    val outputTestCountdown: Int = 0,
    val outputTestProgress: Float = 0f,
    val outputTestResult: String? = null,
    val isMicrophoneTesting: Boolean = false,
    val microphoneTestPhase: String? = null,
    val microphoneTestCountdown: Int = 0,
    val microphoneTestProgress: Float = 0f,
    val microphoneTestLevel: Float = 0f,
    val microphoneTestResult: String? = null,
    val lastErrorMessage: String? = null
) {
    // Backwards-compatible properties
    val selectedOutputDevice: AudioDeviceModel? get() = requestedOutputDevice
    val selectedInputDevice: AudioDeviceModel? get() = requestedInputDevice
}
