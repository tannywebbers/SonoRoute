package com.example.audio.routing

import com.example.audio.model.AudioDeviceModel

/**
 * Central routing state model for SonoRoute.
 * Maintains independent representations of output and input routing states,
 * verification statuses, and available hardware devices.
 */
data class AudioRoutingState(
    val selectedOutputDevice: AudioDeviceModel? = null,
    val actualOutputDevice: AudioDeviceModel? = null,
    val selectedInputDevice: AudioDeviceModel? = null,
    val actualInputDevice: AudioDeviceModel? = null,
    val outputStatus: String = "System controlled",
    val inputStatus: String = "System controlled",
    val availableOutputDevices: List<AudioDeviceModel> = emptyList(),
    val availableInputDevices: List<AudioDeviceModel> = emptyList(),
    val lastUpdated: Long = System.currentTimeMillis(),
    val systemControlled: Boolean = true,
    val activeCommunicationDeviceName: String? = null,
    val isOutputTesting: Boolean = false,
    val outputTestResult: String? = null,
    val isMicrophoneTesting: Boolean = false,
    val microphoneTestLevel: Float = 0f,
    val microphoneTestResult: String? = null,
    val lastErrorMessage: String? = null
)
