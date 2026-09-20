package com.example.audio.performance

import com.example.ui.AudioProfile

/**
 * Latency level choices available to the user in SonoRoute.
 * Influences the default buffer target while allowing manual user override.
 */
enum class LatencyLevel(
    val title: String,
    val description: String,
    val defaultBufferOption: BufferOption
) {
    AUTO(
        title = "Auto",
        description = "Let SonoRoute select an appropriate configuration based on active hardware.",
        defaultBufferOption = BufferOption.AUTO
    ),
    VERY_LOW(
        title = "Very Low",
        description = "Prioritize the lowest practical latency supported by the current audio path.",
        defaultBufferOption = BufferOption.LOW
    ),
    LOW(
        title = "Low",
        description = "Prioritize low response latency while maintaining reasonable stability.",
        defaultBufferOption = BufferOption.LOW
    ),
    BALANCED(
        title = "Balanced",
        description = "Balance responsiveness and stability for everyday audio playback.",
        defaultBufferOption = BufferOption.BALANCED
    ),
    HIGH(
        title = "High",
        description = "Allow larger buffers for additional stability against dropouts.",
        defaultBufferOption = BufferOption.STABLE
    ),
    MAXIMUM_STABILITY(
        title = "Maximum Stability",
        description = "Prioritize avoiding audio dropouts over low latency with maximum buffering.",
        defaultBufferOption = BufferOption.STABLE
    )
}

/**
 * Real audio performance modes supported by Android AudioTrack HAL.
 */
enum class AudioPerformanceMode(val title: String, val description: String) {
    STANDARD(
        title = "Standard",
        description = "Balanced performance and compatibility."
    ),
    LOW_LATENCY(
        title = "Low Latency",
        description = "Prioritizes faster audio response where supported."
    ),
    STABLE(
        title = "Stable",
        description = "Prioritizes reliable playback and reduced risk of audio dropouts."
    ),
    POWER_SAVER(
        title = "Power Saver",
        description = "Prioritizes lower processing and battery usage."
    )
}

/**
 * Buffer configuration options.
 */
enum class BufferOption(val title: String, val description: String) {
    AUTO("Auto", "Automatically selected based on device capabilities"),
    LOW("Low", "Minimized frame buffer for reduced latency"),
    BALANCED("Balanced", "Balanced frame buffer for daily media"),
    STABLE("Stable", "Extended buffer to safeguard against underruns"),
    CUSTOM("Custom", "User-specified buffer frame count")
}

/**
 * Live configuration status of audio settings.
 */
enum class ConfigurationStatus(val label: String) {
    APPLIED("Applied"),
    ADJUSTED_BY_DEVICE("Adjusted by device"),
    SYSTEM_CONTROLLED("System controlled"),
    NOT_SUPPORTED("Not supported"),
    REQUIRES_RESTART("Requires restart")
}

/**
 * Channel configuration options.
 */
enum class ChannelOption(val title: String, val channels: Int) {
    AUTO("System / Auto", 0),
    STEREO("Stereo (2 Ch)", 2),
    MONO("Mono (1 Ch)", 1)
}

/**
 * Audio format options.
 */
enum class AudioFormatOption(val title: String, val encoding: Int) {
    AUTO("Auto", 0),
    PCM_16BIT("PCM 16-bit", android.media.AudioFormat.ENCODING_PCM_16BIT),
    PCM_FLOAT("PCM Float", android.media.AudioFormat.ENCODING_PCM_FLOAT)
}

/**
 * Complete snapshot of audio performance capabilities and actual active configuration.
 * Only contains values that Android and the underlying audio engine actually expose.
 */
data class AudioPerformanceState(
    val supportsLowLatency: Boolean,
    val supportsProAudio: Boolean,
    val nativeSampleRate: Int,
    val nativeBufferSizeFrames: Int,
    val supportedSampleRates: List<Int>,
    val supportedChannelCounts: List<Int>,
    val supportedFormats: List<String>,
    val availableBufferPresets: List<Int>,
    val activeProfile: AudioProfile,
    // Latency Level
    val requestedLatencyLevel: LatencyLevel,
    val actualLatencyLevel: LatencyLevel,
    // Performance Mode
    val requestedPerformanceMode: AudioPerformanceMode,
    val actualPerformanceMode: AudioPerformanceMode,
    // Buffer Size
    val requestedBufferOption: BufferOption,
    val requestedCustomBufferFrames: Int?,
    val actualBufferSizeFrames: Int,
    // Sample Rate
    val requestedSampleRate: Int, // 0 = Auto
    val actualSampleRate: Int,
    val customSampleRateInput: Int?,
    // Channel & Format
    val requestedChannelOption: ChannelOption,
    val actualChannelCount: Int,
    val requestedFormatOption: AudioFormatOption,
    val actualFormat: String,
    // Latency Metrics
    val estimatedBufferLatencyMs: Float,
    val measuredLatencyMs: Float? = null, // Only populated if reliable measurement exists
    // Status & Warnings
    val configurationStatus: ConfigurationStatus,
    val warningMessage: String?,
    val stabilityStatus: String,
    val underrunCount: Int,
    val isAdjustedForDevice: Boolean,
    val adjustmentReason: String?,
    val activeOutputDeviceName: String = "Built-in Speaker",
    val audioEngineName: String = "Android AudioTrack HAL"
)
