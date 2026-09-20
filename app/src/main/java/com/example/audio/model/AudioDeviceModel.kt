package com.example.audio.model

/**
 * Clean domain representation of an Android Audio Device.
 * Populated strictly from real [android.media.AudioDeviceInfo] properties.
 */
data class AudioDeviceModel(
    val id: Int,
    val name: String,
    val type: Int,
    val typeLabel: String,
    val category: DeviceCategory,
    val isInput: Boolean,
    val isOutput: Boolean,
    val isAvailable: Boolean = true,
    val isSelected: Boolean = false,
    val isCommunicationDevice: Boolean = false,
    val isBuiltIn: Boolean = false,
    val isBluetooth: Boolean = false,
    val isWired: Boolean = false,
    val isUsb: Boolean = false,
    val sampleRates: List<Int> = emptyList(),
    val channelCounts: List<Int> = emptyList(),
    val supportedEncodings: List<String> = emptyList(),
    val address: String = ""
)

enum class DeviceCategory(val label: String) {
    BUILT_IN("Built-in"),
    WIRED("Wired"),
    BLUETOOTH("Bluetooth"),
    USB("USB"),
    HDMI("HDMI / Display"),
    OTHER("External / Other")
}

data class DeviceConnectionEvent(
    val deviceName: String,
    val isConnected: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)
