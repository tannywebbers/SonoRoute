package com.example.audio.performance

import android.content.Context
import android.media.AudioManager
import android.util.Log
import com.example.ui.AudioProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AudioPerformanceManager(private val context: Context) {
    companion object {
        private const val TAG = "AudioPerformanceManager"
    }

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private val nativeSampleRate: Int = try {
        audioManager.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE)?.toIntOrNull() ?: 48000
    } catch (e: Exception) {
        48000
    }

    private val nativeBufferSizeFrames: Int = try {
        audioManager.getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER)?.toIntOrNull() ?: 192
    } catch (e: Exception) {
        192
    }

    private val _performanceState = MutableStateFlow(
        AudioPerformanceState(
            supportsLowLatency = context.packageManager.hasSystemFeature("android.hardware.audio.low_latency"),
            supportsProAudio = context.packageManager.hasSystemFeature("android.hardware.audio.pro"),
            nativeSampleRate = nativeSampleRate,
            nativeBufferSizeFrames = nativeBufferSizeFrames,
            actualSampleRate = nativeSampleRate,
            actualBufferSizeFrames = nativeBufferSizeFrames
        )
    )
    val performanceState: StateFlow<AudioPerformanceState> = _performanceState.asStateFlow()

    fun onProfileChanged(profile: AudioProfile) {
        val current = _performanceState.value
        val (latency, mode, buffer) = when (profile) {
            AudioProfile.STANDARD -> Triple(LatencyLevel.BALANCED, AudioPerformanceMode.NONE, BufferOption.BALANCED)
            AudioProfile.GAMING -> Triple(LatencyLevel.ULTRA_LOW, AudioPerformanceMode.LOW_LATENCY, BufferOption.LOW)
            AudioProfile.RECORDING -> Triple(LatencyLevel.LOW, AudioPerformanceMode.LOW_LATENCY, BufferOption.BALANCED)
            AudioProfile.VOICE_CHAT -> Triple(LatencyLevel.BALANCED, AudioPerformanceMode.LOW_LATENCY, BufferOption.BALANCED)
            AudioProfile.SCREEN_SHARING -> Triple(LatencyLevel.BALANCED, AudioPerformanceMode.NONE, BufferOption.BALANCED)
            AudioProfile.MEDIA -> Triple(LatencyLevel.HIGH, AudioPerformanceMode.POWER_SAVING, BufferOption.STABLE)
        }
        _performanceState.value = current.copy(
            activeProfile = profile,
            requestedLatencyLevel = latency,
            actualLatencyLevel = latency,
            requestedPerformanceMode = mode,
            actualPerformanceMode = mode,
            requestedBufferOption = buffer,
            actualBufferSizeFrames = calculateBufferSize(buffer, null),
            configurationStatus = ConfigurationStatus.APPLIED
        )
        Log.d(TAG, "Audio profile changed to ${profile.title}")
    }

    fun setLatencyLevel(level: LatencyLevel) {
        val current = _performanceState.value
        val bufferOpt = level.defaultBufferOption
        _performanceState.value = current.copy(
            requestedLatencyLevel = level,
            actualLatencyLevel = level,
            requestedBufferOption = bufferOpt,
            actualBufferSizeFrames = calculateBufferSize(bufferOpt, current.requestedCustomBufferFrames),
            configurationStatus = ConfigurationStatus.APPLIED
        )
    }

    fun setPerformanceMode(mode: AudioPerformanceMode) {
        _performanceState.value = _performanceState.value.copy(
            requestedPerformanceMode = mode,
            actualPerformanceMode = mode
        )
    }

    fun setBufferOption(option: BufferOption) {
        val current = _performanceState.value
        _performanceState.value = current.copy(
            requestedBufferOption = option,
            actualBufferSizeFrames = calculateBufferSize(option, current.requestedCustomBufferFrames)
        )
    }

    fun setCustomBufferFrames(frames: Int?): Boolean {
        if (frames != null && (frames < 32 || frames > 4096)) return false
        val current = _performanceState.value
        _performanceState.value = current.copy(
            requestedCustomBufferFrames = frames,
            requestedBufferOption = BufferOption.CUSTOM,
            actualBufferSizeFrames = frames ?: nativeBufferSizeFrames
        )
        return true
    }

    fun setSampleRatePreference(rate: Int) {
        _performanceState.value = _performanceState.value.copy(
            requestedSampleRate = rate,
            actualSampleRate = rate
        )
    }

    fun setCustomSampleRate(rate: Int) {
        _performanceState.value = _performanceState.value.copy(
            customSampleRateInput = rate,
            requestedSampleRate = rate,
            actualSampleRate = rate
        )
    }

    fun setChannelOption(channels: ChannelOption) {
        val actualChannels = if (channels.channels > 0) channels.channels else 2
        _performanceState.value = _performanceState.value.copy(
            requestedChannelOption = channels,
            actualChannelCount = actualChannels
        )
    }

    fun setFormatOption(format: AudioFormatOption) {
        val formatName = if (format == AudioFormatOption.PCM_FLOAT) "PCM Float" else "PCM 16-bit"
        _performanceState.value = _performanceState.value.copy(
            requestedFormatOption = format,
            actualFormat = formatName
        )
    }

    fun resetProfileToDefaults() {
        onProfileChanged(AudioProfile.STANDARD)
    }

    fun resetPerformanceDefaults() {
        _performanceState.value = _performanceState.value.copy(
            requestedLatencyLevel = LatencyLevel.BALANCED,
            actualLatencyLevel = LatencyLevel.BALANCED,
            requestedPerformanceMode = AudioPerformanceMode.LOW_LATENCY,
            actualPerformanceMode = AudioPerformanceMode.LOW_LATENCY,
            requestedBufferOption = BufferOption.AUTO,
            actualBufferSizeFrames = nativeBufferSizeFrames,
            requestedSampleRate = nativeSampleRate,
            actualSampleRate = nativeSampleRate,
            customSampleRateInput = null,
            requestedChannelOption = ChannelOption.AUTO,
            actualChannelCount = 2,
            requestedFormatOption = AudioFormatOption.AUTO,
            actualFormat = "PCM 16-bit",
            configurationStatus = ConfigurationStatus.APPLIED
        )
    }

    fun onUnderrunDetected() {
        val current = _performanceState.value
        _performanceState.value = current.copy(
            underrunCount = current.underrunCount + 1,
            stabilityStatus = "Buffer underrun detected"
        )
    }

    fun updateActiveOutputDevice(name: String) {
        _performanceState.value = _performanceState.value.copy(
            activeOutputDeviceName = name
        )
    }

    private fun calculateBufferSize(option: BufferOption, custom: Int?): Int {
        return when (option) {
            BufferOption.AUTO -> nativeBufferSizeFrames
            BufferOption.LOW -> (nativeBufferSizeFrames / 2).coerceAtLeast(64)
            BufferOption.BALANCED -> nativeBufferSizeFrames
            BufferOption.STABLE -> nativeBufferSizeFrames * 2
            BufferOption.CUSTOM -> custom ?: nativeBufferSizeFrames
        }
    }
}
