package com.example.audio.performance

import com.example.ui.AudioProfile

enum class LatencyLevel(
    val title: String,
    val description: String,
    val defaultBufferOption: BufferOption
) {
    ULTRA_LOW("Ultra-Low", "Minimize latency at all costs using the fastest compliant audio path.", BufferOption.LOW),
    LOW("Low", "Prioritize low response latency while maintaining reasonable stability.", BufferOption.LOW),
    BALANCED("Balanced", "Balance responsiveness and stability for everyday audio playback.", BufferOption.BALANCED),
    HIGH("High", "Allow larger buffers for additional stability against dropouts.", BufferOption.STABLE),
    MAXIMUM_STABILITY("Maximum Stability", "Prioritize avoiding audio dropouts over low latency with maximum buffering.", BufferOption.STABLE)
}

enum class BufferOption(val title: String, val description: String) {
    AUTO("Auto", "Automatically selected based on device capabilities"),
    LOW("Low", "Minimized frame buffer for reduced latency"),
    BALANCED("Balanced", "Balanced frame buffer for daily media"),
    STABLE("Stable", "Extended buffer to safeguard against underruns"),
    CUSTOM("Custom", "User-specified buffer frame count")
}

enum class ChannelOption(val title: String, val channels: Int) {
    AUTO("System / Auto", 0),
    STEREO("Stereo (2 Ch)", 2),
    MONO("Mono (1 Ch)", 1)
}

enum class AudioFormatOption(val title: String, val encoding: Int) {
    AUTO("Auto", 0),
    PCM_16BIT("PCM 16-bit", 2),
    PCM_FLOAT("PCM Float", 4)
}

enum class AudioPerformanceMode(val title: String, val description: String) {
    LOW_LATENCY("Low Latency", "Optimized for minimal output/input delay"),
    POWER_SAVING("Power Saving", "Optimized for low battery consumption"),
    NONE("None / Default", "Standard Android audio framework configuration")
}

enum class ConfigurationStatus(val label: String) {
    APPLIED("Applied"),
    ADJUSTED_BY_DEVICE("Adjusted by device"),
    SYSTEM_CONTROLLED("System controlled"),
    NOT_SUPPORTED("Not supported"),
    REQUIRES_RESTART("Requires restart")
}

data class AudioPerformanceState(
    val supportsLowLatency: Boolean = true,
    val supportsProAudio: Boolean = false,
    val nativeSampleRate: Int = 48000,
    val nativeBufferSizeFrames: Int = 192,
    val supportedSampleRates: List<Int> = listOf(44100, 48000, 96000),
    val supportedChannelCounts: List<Int> = listOf(1, 2),
    val supportedFormats: List<String> = listOf("PCM 16-bit", "PCM Float"),
    val availableBufferPresets: List<Int> = listOf(64, 128, 192, 256, 512, 1024),
    val activeProfile: AudioProfile = AudioProfile.STANDARD,
    val requestedLatencyLevel: LatencyLevel = LatencyLevel.BALANCED,
    val actualLatencyLevel: LatencyLevel = LatencyLevel.BALANCED,
    val requestedPerformanceMode: AudioPerformanceMode = AudioPerformanceMode.LOW_LATENCY,
    val actualPerformanceMode: AudioPerformanceMode = AudioPerformanceMode.LOW_LATENCY,
    val requestedBufferOption: BufferOption = BufferOption.AUTO,
    val requestedCustomBufferFrames: Int? = null,
    val actualBufferSizeFrames: Int = 192,
    val requestedSampleRate: Int = 48000,
    val actualSampleRate: Int = 48000,
    val customSampleRateInput: Int? = null,
    val requestedChannelOption: ChannelOption = ChannelOption.AUTO,
    val actualChannelCount: Int = 2,
    val requestedFormatOption: AudioFormatOption = AudioFormatOption.AUTO,
    val actualFormat: String = "PCM 16-bit",
    val estimatedBufferLatencyMs: Float = 4.0f,
    val measuredLatencyMs: Float? = null,
    val configurationStatus: ConfigurationStatus = ConfigurationStatus.APPLIED,
    val warningMessage: String? = null,
    val stabilityStatus: String = "Stable",
    val underrunCount: Int = 0,
    val isAdjustedForDevice: Boolean = false,
    val adjustmentReason: String? = null,
    val activeOutputDeviceName: String = "Built-in Speaker",
    val audioEngineName: String = "AAudio / OpenSL ES"
)
