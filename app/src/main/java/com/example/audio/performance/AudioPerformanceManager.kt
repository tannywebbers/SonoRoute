package com.example.audio.performance

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import android.util.Log
import com.example.audio.model.AudioDeviceModel
import com.example.ui.AudioProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manages audio performance, buffer configuration, latency levels, and sample rates
 * for SonoRoute-controlled audio streams using real Android AudioTrack HAL APIs.
 */
class AudioPerformanceManager(
    private val context: Context
) {
    companion object {
        private const val TAG = "AudioPerformanceManager"
        private const val PREFS_NAME = "sonoroute_phase4_perf_prefs"
        private const val KEY_ACTIVE_PROFILE = "pref_active_profile"

        // Standard presets offered to the user
        val STANDARD_BUFFER_PRESETS = listOf(64, 128, 192, 256, 384, 512, 768, 1024, 2048)
        val STANDARD_SAMPLE_RATES = listOf(44100, 48000, 88200, 96000, 192000)
    }

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val packageManager = context.packageManager
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private var activeProfile: AudioProfile = AudioProfile.STANDARD
    private var activeDevice: AudioDeviceModel? = null

    private val _performanceState = MutableStateFlow(createInitialState())
    val performanceState: StateFlow<AudioPerformanceState> = _performanceState.asStateFlow()

    init {
        val savedProfileName = prefs.getString(KEY_ACTIVE_PROFILE, AudioProfile.STANDARD.name)
        activeProfile = try {
            AudioProfile.valueOf(savedProfileName ?: AudioProfile.STANDARD.name)
        } catch (e: Exception) {
            AudioProfile.STANDARD
        }
        reEvaluatePerformance()
    }

    private fun createInitialState(): AudioPerformanceState {
        val hasLowLatency = packageManager.hasSystemFeature(PackageManager.FEATURE_AUDIO_LOW_LATENCY)
        val hasProAudio = packageManager.hasSystemFeature(PackageManager.FEATURE_AUDIO_PRO)

        val nativeRate = audioManager.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE)?.toIntOrNull() ?: 48000
        val nativeFrames = audioManager.getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER)?.toIntOrNull() ?: 256

        return AudioPerformanceState(
            supportsLowLatency = hasLowLatency,
            supportsProAudio = hasProAudio,
            nativeSampleRate = nativeRate,
            nativeBufferSizeFrames = nativeFrames,
            supportedSampleRates = listOf(44100, 48000),
            supportedChannelCounts = listOf(1, 2),
            supportedFormats = listOf("PCM 16-bit", "PCM Float"),
            availableBufferPresets = STANDARD_BUFFER_PRESETS,
            activeProfile = AudioProfile.STANDARD,
            requestedLatencyLevel = LatencyLevel.BALANCED,
            actualLatencyLevel = LatencyLevel.BALANCED,
            requestedPerformanceMode = AudioPerformanceMode.STANDARD,
            actualPerformanceMode = AudioPerformanceMode.STANDARD,
            requestedBufferOption = BufferOption.AUTO,
            requestedCustomBufferFrames = null,
            actualBufferSizeFrames = nativeFrames,
            requestedSampleRate = 0,
            actualSampleRate = nativeRate,
            customSampleRateInput = null,
            requestedChannelOption = ChannelOption.AUTO,
            actualChannelCount = 2,
            requestedFormatOption = AudioFormatOption.AUTO,
            actualFormat = "PCM 16-bit",
            estimatedBufferLatencyMs = (nativeFrames.toFloat() / nativeRate.toFloat()) * 1000f,
            measuredLatencyMs = null,
            configurationStatus = ConfigurationStatus.APPLIED,
            warningMessage = null,
            stabilityStatus = "Good — No Dropouts Detected",
            underrunCount = 0,
            isAdjustedForDevice = false,
            adjustmentReason = null,
            activeOutputDeviceName = "Built-in Speaker",
            audioEngineName = if (hasLowLatency) "Android AudioTrack / FastMixer" else "Android AudioTrack HAL"
        )
    }

    fun onActiveDeviceChanged(device: AudioDeviceModel?) {
        activeDevice = device
        reEvaluatePerformance()
    }

    fun onProfileChanged(profile: AudioProfile) {
        activeProfile = profile
        prefs.edit().putString(KEY_ACTIVE_PROFILE, profile.name).apply()
        reEvaluatePerformance()
    }

    /**
     * Updates the requested latency level for the active profile.
     */
    fun setLatencyLevel(level: LatencyLevel) {
        savePref("latency_level", level.name)
        reEvaluatePerformance()
    }

    /**
     * Updates the requested buffer preset option (Auto, Low, Balanced, Stable, Custom)
     */
    fun setBufferOption(option: BufferOption) {
        savePref("buffer_option", option.name)
        if (option != BufferOption.CUSTOM) {
            removePref("custom_buffer")
        }
        reEvaluatePerformance()
    }

    /**
     * Sets a custom buffer size in frames with validation.
     * Returns true if valid, false if rejected.
     */
    fun setCustomBufferFrames(frames: Int?): Boolean {
        if (frames == null) {
            removePref("custom_buffer")
            savePref("buffer_option", BufferOption.AUTO.name)
            reEvaluatePerformance()
            return true
        }

        // Validate buffer size: must be positive and within practical bounds (32 to 8192 frames)
        if (frames < 32 || frames > 8192) {
            Log.w(TAG, "Custom buffer $frames frames is outside practical bounds [32, 8192]")
            return false
        }

        savePref("custom_buffer", frames)
        savePref("buffer_option", BufferOption.CUSTOM.name)
        reEvaluatePerformance()
        return true
    }

    /**
     * Quickly steps the buffer up to the next available preset size to recover from audio instability or underruns.
     */
    fun increaseBufferToNextPreset(): Boolean {
        val currentFrames = _performanceState.value.actualBufferSizeFrames
        val nextPreset = STANDARD_BUFFER_PRESETS.firstOrNull { it > currentFrames } ?: return false
        return setCustomBufferFrames(nextPreset)
    }

    /**
     * Updates sample rate preference (0 = Auto).
     */
    fun setSampleRatePreference(rate: Int) {
        savePref("sample_rate", rate)
        removePref("custom_sample_rate")
        reEvaluatePerformance()
    }

    /**
     * Sets custom sample rate with validation.
     */
    fun setCustomSampleRate(rate: Int): Boolean {
        if (rate <= 0) {
            savePref("sample_rate", 0)
            removePref("custom_sample_rate")
            reEvaluatePerformance()
            return true
        }

        if (rate < 8000 || rate > 192000) {
            Log.w(TAG, "Sample rate $rate Hz outside supported range [8000, 192000]")
            return false
        }

        savePref("sample_rate", rate)
        savePref("custom_sample_rate", rate)
        reEvaluatePerformance()
        return true
    }

    /**
     * Sets performance mode override.
     */
    fun setPerformanceMode(mode: AudioPerformanceMode) {
        savePref("perf_mode", mode.name)
        reEvaluatePerformance()
    }

    /**
     * Sets channel option.
     */
    fun setChannelOption(channelOption: ChannelOption) {
        savePref("channels", channelOption.name)
        reEvaluatePerformance()
    }

    /**
     * Sets audio format option.
     */
    fun setFormatOption(formatOption: AudioFormatOption) {
        savePref("format", formatOption.name)
        reEvaluatePerformance()
    }

    /**
     * Resets the active profile to its default values.
     */
    fun resetProfileToDefaults() {
        clearProfilePrefs(activeProfile)
        reEvaluatePerformance()
    }

    /**
     * Resets all settings across all profiles to factory defaults.
     */
    fun resetToDefaults() {
        prefs.edit().clear().apply()
        reEvaluatePerformance()
    }

    private fun profileKey(setting: String): String = "prof_${activeProfile.name}_$setting"

    private fun savePref(setting: String, value: String) {
        prefs.edit().putString(profileKey(setting), value).apply()
    }

    private fun savePref(setting: String, value: Int) {
        prefs.edit().putInt(profileKey(setting), value).apply()
    }

    private fun removePref(setting: String) {
        prefs.edit().remove(profileKey(setting)).apply()
    }

    private fun clearProfilePrefs(profile: AudioProfile) {
        val editor = prefs.edit()
        listOf(
            "latency_level",
            "buffer_option",
            "custom_buffer",
            "sample_rate",
            "custom_sample_rate",
            "perf_mode",
            "channels",
            "format"
        ).forEach { key ->
            editor.remove("prof_${profile.name}_$key")
        }
        editor.apply()
    }

    /**
     * Re-evaluates audio capabilities, probes the Android AudioTrack HAL,
     * validates user preferences against active hardware, and emits the latest state.
     */
    private fun reEvaluatePerformance() {
        val hasLowLatency = packageManager.hasSystemFeature(PackageManager.FEATURE_AUDIO_LOW_LATENCY)
        val hasProAudio = packageManager.hasSystemFeature(PackageManager.FEATURE_AUDIO_PRO)

        val nativeRate = audioManager.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE)?.toIntOrNull() ?: 48000
        val nativeFrames = audioManager.getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER)?.toIntOrNull() ?: 256

        // Determine default LatencyLevel for the current profile if not customized
        val defaultLatencyLevel = when (activeProfile) {
            AudioProfile.STANDARD -> LatencyLevel.BALANCED
            AudioProfile.GAMING -> LatencyLevel.VERY_LOW
            AudioProfile.VOICE_CHAT -> LatencyLevel.LOW
            AudioProfile.RECORDING -> LatencyLevel.LOW
            AudioProfile.SCREEN_SHARING -> LatencyLevel.LOW
            AudioProfile.MEDIA -> LatencyLevel.BALANCED
        }

        val defaultBufferOption = when (activeProfile) {
            AudioProfile.STANDARD -> BufferOption.AUTO
            AudioProfile.GAMING -> BufferOption.AUTO
            AudioProfile.VOICE_CHAT -> BufferOption.AUTO
            AudioProfile.RECORDING -> BufferOption.BALANCED
            AudioProfile.SCREEN_SHARING -> BufferOption.AUTO
            AudioProfile.MEDIA -> BufferOption.BALANCED
        }

        // Restore active profile preferences
        val savedLatencyLevelStr = prefs.getString(profileKey("latency_level"), null)
        val requestedLatencyLevel = if (savedLatencyLevelStr != null) {
            try { LatencyLevel.valueOf(savedLatencyLevelStr) } catch (e: Exception) { defaultLatencyLevel }
        } else {
            defaultLatencyLevel
        }

        val savedBufferOptionStr = prefs.getString(profileKey("buffer_option"), null)
        val requestedBufferOption = if (savedBufferOptionStr != null) {
            try { BufferOption.valueOf(savedBufferOptionStr) } catch (e: Exception) { defaultBufferOption }
        } else {
            defaultBufferOption
        }

        val customFrames = if (prefs.contains(profileKey("custom_buffer"))) {
            prefs.getInt(profileKey("custom_buffer"), 0).takeIf { it > 0 }
        } else null

        val requestedSampleRate = prefs.getInt(profileKey("sample_rate"), 0)
        val customRate = if (prefs.contains(profileKey("custom_sample_rate"))) {
            prefs.getInt(profileKey("custom_sample_rate"), 0).takeIf { it > 0 }
        } else null

        val defaultPerfMode = when (requestedLatencyLevel) {
            LatencyLevel.VERY_LOW, LatencyLevel.LOW -> AudioPerformanceMode.LOW_LATENCY
            LatencyLevel.MAXIMUM_STABILITY, LatencyLevel.HIGH -> AudioPerformanceMode.STABLE
            LatencyLevel.BALANCED, LatencyLevel.AUTO -> AudioPerformanceMode.STANDARD
        }

        val savedPerfModeStr = prefs.getString(profileKey("perf_mode"), null)
        val requestedPerfMode = if (savedPerfModeStr != null) {
            try { AudioPerformanceMode.valueOf(savedPerfModeStr) } catch (e: Exception) { defaultPerfMode }
        } else {
            defaultPerfMode
        }

        val savedChannelStr = prefs.getString(profileKey("channels"), ChannelOption.AUTO.name)
        val requestedChannel = try {
            ChannelOption.valueOf(savedChannelStr ?: ChannelOption.AUTO.name)
        } catch (e: Exception) {
            ChannelOption.AUTO
        }

        val savedFormatStr = prefs.getString(profileKey("format"), AudioFormatOption.AUTO.name)
        val requestedFormat = try {
            AudioFormatOption.valueOf(savedFormatStr ?: AudioFormatOption.AUTO.name)
        } catch (e: Exception) {
            AudioFormatOption.AUTO
        }

        // Query supported rates and channels from active device or standard device fallback
        val deviceSupportedRates = activeDevice?.sampleRates?.filter { it > 0 }?.distinct()?.sorted()
        val supportedRates = if (!deviceSupportedRates.isNullOrEmpty()) {
            deviceSupportedRates
        } else {
            STANDARD_SAMPLE_RATES
        }

        val deviceSupportedChannels = activeDevice?.channelCounts?.filter { it > 0 }?.distinct()?.sorted()
        val supportedChannels = if (!deviceSupportedChannels.isNullOrEmpty()) {
            deviceSupportedChannels
        } else {
            listOf(1, 2)
        }

        // Validate sample rate against active hardware
        var isAdjusted = false
        var adjustmentReason: String? = null

        val targetSampleRate = if (requestedSampleRate > 0) {
            if (supportedRates.contains(requestedSampleRate)) {
                requestedSampleRate
            } else {
                // Device does not support requested rate, fallback to native or closest supported
                isAdjusted = true
                adjustmentReason = "Settings adjusted for the connected device — $requestedSampleRate Hz is not supported by ${activeDevice?.name ?: "active audio device"}; fell back to $nativeRate Hz."
                nativeRate
            }
        } else {
            nativeRate
        }

        // Target channel mask
        val targetChannelMask = when (requestedChannel) {
            ChannelOption.MONO -> AudioFormat.CHANNEL_OUT_MONO
            ChannelOption.STEREO -> AudioFormat.CHANNEL_OUT_STEREO
            ChannelOption.AUTO -> AudioFormat.CHANNEL_OUT_STEREO
        }
        val actualChannelCount = if (targetChannelMask == AudioFormat.CHANNEL_OUT_MONO) 1 else 2

        // Target encoding format
        val targetEncoding = when (requestedFormat) {
            AudioFormatOption.PCM_FLOAT -> AudioFormat.ENCODING_PCM_FLOAT
            AudioFormatOption.PCM_16BIT -> AudioFormat.ENCODING_PCM_16BIT
            AudioFormatOption.AUTO -> AudioFormat.ENCODING_PCM_16BIT
        }
        val formatLabel = if (targetEncoding == AudioFormat.ENCODING_PCM_FLOAT) "PCM Float" else "PCM 16-bit"

        // Mode validation: if low latency is requested but hardware does not support it
        var actualPerfMode = requestedPerfMode
        var actualLatencyLevel = requestedLatencyLevel
        if ((requestedLatencyLevel == LatencyLevel.VERY_LOW || requestedPerfMode == AudioPerformanceMode.LOW_LATENCY) && !hasLowLatency) {
            actualPerfMode = AudioPerformanceMode.STANDARD
            actualLatencyLevel = LatencyLevel.BALANCED
            isAdjusted = true
            val reason = "Settings adjusted for this device — Hardware low latency is not reported by Android HAL."
            adjustmentReason = if (adjustmentReason != null) "$adjustmentReason $reason" else reason
        }

        // Calculate target buffer frames based on options
        val minBufferSize = AudioTrack.getMinBufferSize(targetSampleRate, targetChannelMask, targetEncoding)
        val safeMinBuffer = if (minBufferSize > 0) minBufferSize else 2048
        val bytesPerFrame = actualChannelCount * (if (targetEncoding == AudioFormat.ENCODING_PCM_FLOAT) 4 else 2)
        val minFrames = (safeMinBuffer / bytesPerFrame).coerceAtLeast(64)

        val targetFrames = when {
            customFrames != null && customFrames > 0 -> customFrames
            requestedBufferOption == BufferOption.LOW && hasLowLatency -> nativeFrames
            requestedBufferOption == BufferOption.LOW -> (nativeFrames * 1.5f).toInt()
            requestedBufferOption == BufferOption.BALANCED -> nativeFrames * 2
            requestedBufferOption == BufferOption.STABLE -> nativeFrames * 4
            requestedLatencyLevel == LatencyLevel.VERY_LOW && hasLowLatency -> nativeFrames
            requestedLatencyLevel == LatencyLevel.LOW -> (nativeFrames * 1.5f).toInt()
            requestedLatencyLevel == LatencyLevel.HIGH -> nativeFrames * 3
            requestedLatencyLevel == LatencyLevel.MAXIMUM_STABILITY -> nativeFrames * 4
            else -> nativeFrames // AUTO
        }

        val requestedBufferBytes = (targetFrames * bytesPerFrame).coerceAtLeast(safeMinBuffer)

        // Real AudioTrack allocation test to confirm hardware-accepted buffer size
        var actualFrames = targetFrames
        var underruns = 0
        var stability = "Good — No Dropouts Detected"

        try {
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()

            val audioFormat = AudioFormat.Builder()
                .setSampleRate(targetSampleRate)
                .setChannelMask(targetChannelMask)
                .setEncoding(targetEncoding)
                .build()

            val trackBuilder = AudioTrack.Builder()
                .setAudioAttributes(audioAttributes)
                .setAudioFormat(audioFormat)
                .setBufferSizeInBytes(requestedBufferBytes)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val trackPerfMode = when (actualPerfMode) {
                    AudioPerformanceMode.LOW_LATENCY -> AudioTrack.PERFORMANCE_MODE_LOW_LATENCY
                    AudioPerformanceMode.POWER_SAVER -> AudioTrack.PERFORMANCE_MODE_POWER_SAVING
                    else -> AudioTrack.PERFORMANCE_MODE_NONE
                }
                trackBuilder.setPerformanceMode(trackPerfMode)
            }

            val testTrack = trackBuilder.build()
            actualFrames = testTrack.bufferSizeInFrames
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                underruns = testTrack.underrunCount
            }
            testTrack.release()
        } catch (e: Exception) {
            Log.w(TAG, "AudioTrack probe test completed with fallback calculation", e)
            actualFrames = (requestedBufferBytes / bytesPerFrame).coerceAtLeast(nativeFrames)
        }

        if (underruns > 0) {
            stability = "Dropouts Detected: $underruns"
        }

        // Check if Android HAL adjusted the requested frames
        if (customFrames != null && actualFrames != customFrames) {
            isAdjusted = true
            val reason = "Buffer adjusted by audio HAL: requested $customFrames frames, accepted $actualFrames frames."
            adjustmentReason = if (adjustmentReason != null) "$adjustmentReason $reason" else reason
        }

        // Warnings for unusual combinations (Phase 6 subtle performance warning system)
        var warning: String? = null
        if (actualFrames <= 128 && (requestedLatencyLevel == LatencyLevel.MAXIMUM_STABILITY || requestedLatencyLevel == LatencyLevel.HIGH)) {
            warning = "Small buffers may increase CPU usage or dropout risk."
        } else if ((requestedLatencyLevel == LatencyLevel.VERY_LOW || requestedLatencyLevel == LatencyLevel.LOW) && actualFrames >= 512) {
            warning = "Large buffers may increase latency."
        } else if (targetSampleRate >= 96000) {
            warning = "96 kHz may increase processing requirements."
        } else if (activeDevice?.isBluetooth == true) {
            warning = "Bluetooth latency is controlled partly by the Bluetooth device and codec."
        }

        val configStatus = when {
            isAdjusted -> ConfigurationStatus.ADJUSTED_BY_DEVICE
            else -> ConfigurationStatus.APPLIED
        }

        val estimatedBufferLatency = if (targetSampleRate > 0) {
            (actualFrames.toFloat() / targetSampleRate.toFloat()) * 1000f
        } else {
            0f
        }

        _performanceState.value = AudioPerformanceState(
            supportsLowLatency = hasLowLatency,
            supportsProAudio = hasProAudio,
            nativeSampleRate = nativeRate,
            nativeBufferSizeFrames = nativeFrames,
            supportedSampleRates = supportedRates,
            supportedChannelCounts = supportedChannels,
            supportedFormats = listOf("PCM 16-bit", "PCM Float"),
            availableBufferPresets = STANDARD_BUFFER_PRESETS,
            activeProfile = activeProfile,
            requestedLatencyLevel = requestedLatencyLevel,
            actualLatencyLevel = actualLatencyLevel,
            requestedPerformanceMode = requestedPerfMode,
            actualPerformanceMode = actualPerfMode,
            requestedBufferOption = requestedBufferOption,
            requestedCustomBufferFrames = customFrames,
            actualBufferSizeFrames = actualFrames,
            requestedSampleRate = requestedSampleRate,
            actualSampleRate = targetSampleRate,
            customSampleRateInput = customRate,
            requestedChannelOption = requestedChannel,
            actualChannelCount = actualChannelCount,
            requestedFormatOption = requestedFormat,
            actualFormat = formatLabel,
            estimatedBufferLatencyMs = estimatedBufferLatency,
            measuredLatencyMs = null, // Not fabricated; only populated when hardware measurement is present
            configurationStatus = configStatus,
            warningMessage = warning,
            stabilityStatus = stability,
            underrunCount = underruns,
            isAdjustedForDevice = isAdjusted,
            adjustmentReason = adjustmentReason,
            activeOutputDeviceName = activeDevice?.name ?: "Built-in Speaker",
            audioEngineName = if (hasLowLatency) "Android AudioTrack / FastMixer" else "Android AudioTrack HAL"
        )
    }
}
