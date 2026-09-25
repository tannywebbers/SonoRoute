package com.example.audio.session

import com.example.audio.model.AudioDeviceModel
import com.example.audio.performance.AudioFormatOption
import com.example.audio.performance.LatencyLevel
import com.example.ui.AudioProfile

enum class SessionLifecycleState(val label: String) {
    IDLE("Idle"),
    STARTING("Starting"),
    CONFIGURING("Configuring"),
    ACTIVE("Active"),
    PAUSING("Pausing"),
    PAUSED("Paused"),
    STOPPING("Stopping"),
    ERROR("Error")
}

enum class SessionType(val label: String) {
    GENERAL("General Audio"),
    MEDIA("Media Playback"),
    GAMING("Low Latency Gaming"),
    RECORDING("Studio Recording"),
    VOICE_CHAT("Voice Chat / VoIP"),
    SCREEN_SHARING("Screen Audio Sharing");

    companion object {
        fun fromAudioProfile(profile: AudioProfile): SessionType = when (profile) {
            AudioProfile.STANDARD -> GENERAL
            AudioProfile.MEDIA -> MEDIA
            AudioProfile.GAMING -> GAMING
            AudioProfile.RECORDING -> RECORDING
            AudioProfile.VOICE_CHAT -> VOICE_CHAT
            AudioProfile.SCREEN_SHARING -> SCREEN_SHARING
        }
    }
}

enum class MicrophoneResourceState(val label: String) {
    AVAILABLE("Available"),
    REQUESTED("Requested"),
    ACTIVE("Active"),
    BUSY("Busy"),
    RELEASED("Released"),
    ERROR("Error")
}

enum class OutputResourceState(val label: String) {
    AVAILABLE("Available"),
    REQUESTED("Requested"),
    ACTIVE("Active"),
    BUSY("Busy"),
    RELEASED("Released"),
    ERROR("Error")
}

data class SessionEvent(
    val id: Long,
    val timestamp: String,
    val category: String,
    val message: String
)

data class AudioSession(
    val sessionType: SessionType = SessionType.GENERAL,
    val outputDevice: AudioDeviceModel? = null,
    val inputDevice: AudioDeviceModel? = null,
    val latencyLevel: LatencyLevel = LatencyLevel.BALANCED,
    val bufferSize: Int = 192,
    val sampleRate: Int = 48000,
    val channelCount: Int = 2,
    val audioFormat: AudioFormatOption = AudioFormatOption.PCM_16BIT,
    val audioUsage: Int = 1,
    val audioContentType: Int = 2,
    val audioMode: Int = 0,
    val lifecycleState: SessionLifecycleState = SessionLifecycleState.IDLE,
    val isRecording: Boolean = false,
    val isPlaying: Boolean = false,
    val isCommunication: Boolean = false,
    val isMonitoringEnabled: Boolean = false,
    val monitoringLevel: Float = 0.0f,
    val estimatedBufferLatencyMs: Float = 4.0f,
    val outputUnderruns: Int = 0,
    val inputOverruns: Int = 0,
    val isBufferUnstable: Boolean = false,
    val errorMessage: String? = null,
    val activeSinceMs: Long? = null
)
