package com.example.audio.session

import android.media.AudioAttributes
import android.media.AudioManager
import com.example.audio.model.AudioDeviceModel
import com.example.audio.performance.AudioFormatOption
import com.example.audio.performance.LatencyLevel
import com.example.ui.AudioProfile

/**
 * Logical audio session types supported by SonoRoute.
 */
enum class SessionType(val title: String, val subtitle: String) {
    GENERAL("General Audio", "Standard balanced audio configuration"),
    MEDIA("Media Playback", "High-fidelity audio playback stream"),
    GAMING("Gaming", "Ultra-low latency audio pipeline"),
    RECORDING("Recording", "Stable uncompressed microphone capture"),
    VOICE_CHAT("Voice Chat", "Low-latency duplex communication route"),
    SCREEN_SHARING("Screen Sharing", "Capture-compatible audio configuration");

    fun toAudioProfile(): AudioProfile = when (this) {
        GENERAL -> AudioProfile.STANDARD
        MEDIA -> AudioProfile.MEDIA
        GAMING -> AudioProfile.GAMING
        RECORDING -> AudioProfile.RECORDING
        VOICE_CHAT -> AudioProfile.VOICE_CHAT
        SCREEN_SHARING -> AudioProfile.SCREEN_SHARING
    }

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

/**
 * Lifecycle states of a SonoRoute-managed audio session.
 * IDLE -> STARTING -> CONFIGURING -> ACTIVE -> PAUSING -> PAUSED -> STOPPING -> IDLE
 */
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

/**
 * State of microphone hardware resource ownership.
 * Prevents multiple simultaneous consumers from colliding.
 */
enum class MicrophoneResourceState(val label: String) {
    AVAILABLE("Available"),
    REQUESTED("Requested"),
    ACTIVE("Active"),
    BUSY("Busy"),
    RELEASED("Released"),
    ERROR("Error")
}

/**
 * State of audio output playback resource ownership.
 */
enum class OutputResourceState(val label: String) {
    AVAILABLE("Available"),
    REQUESTED("Requested"),
    ACTIVE("Active"),
    BUSY("Busy"),
    RELEASED("Released"),
    ERROR("Error")
}

/**
 * Central audio session model representing an active or configured audio pipeline.
 * Independent of the UI.
 */
data class AudioSession(
    val sessionType: SessionType = SessionType.GENERAL,
    val outputDevice: AudioDeviceModel? = null,
    val inputDevice: AudioDeviceModel? = null,
    val latencyLevel: LatencyLevel = LatencyLevel.BALANCED,
    val bufferSize: Int = 256,
    val sampleRate: Int = 48000,
    val channelCount: Int = 2,
    val audioFormat: AudioFormatOption = AudioFormatOption.AUTO,
    val audioUsage: Int = AudioAttributes.USAGE_MEDIA,
    val audioContentType: Int = AudioAttributes.CONTENT_TYPE_MUSIC,
    val audioMode: Int = AudioManager.MODE_NORMAL,
    val lifecycleState: SessionLifecycleState = SessionLifecycleState.IDLE,
    val isRecording: Boolean = false,
    val isPlaying: Boolean = false,
    val isCommunication: Boolean = false,
    val isMonitoringEnabled: Boolean = false,
    val monitoringLevel: Float = 0.8f,
    val estimatedBufferLatencyMs: Float = 0f,
    val outputUnderruns: Int = 0,
    val inputOverruns: Int = 0,
    val isBufferUnstable: Boolean = false,
    val errorMessage: String? = null,
    val activeSinceMs: Long? = null
)

/**
 * Lightweight local diagnostics event entry for the session event log.
 */
data class SessionEvent(
    val id: Long,
    val timestamp: String,
    val category: String,
    val message: String
)
