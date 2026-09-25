package com.example.audio.device

import android.media.AudioDeviceInfo
import android.os.Build
import com.example.audio.model.AudioDeviceModel
import com.example.audio.model.DeviceCategory

object AudioDeviceMapper {

    fun map(
        info: AudioDeviceInfo,
        isSelected: Boolean = false,
        isCommunicationDevice: Boolean = false
    ): AudioDeviceModel {
        val name = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val prod = info.productName?.toString()?.trim()
            if (!prod.isNullOrEmpty()) prod else resolveTypeLabel(info.type, info.isSource)
        } else {
            resolveTypeLabel(info.type, info.isSource)
        }

        val type = info.type
        val isInput = info.isSource
        val isOutput = info.isSink
        val category = resolveCategory(type)

        val isBuiltIn = category == DeviceCategory.BUILT_IN
        val isBluetooth = category == DeviceCategory.BLUETOOTH
        val isWired = category == DeviceCategory.WIRED
        val isUsb = category == DeviceCategory.USB

        val sampleRates = info.sampleRates.toList()
        val channelCounts = info.channelCounts.toList()
        val supportedEncodings = info.encodings.map { encodingToString(it) }

        val address = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.address ?: ""
        } else {
            ""
        }

        return AudioDeviceModel(
            id = info.id,
            name = name,
            type = type,
            typeLabel = resolveTypeLabel(type, isInput),
            category = category,
            isInput = isInput,
            isOutput = isOutput,
            isAvailable = true,
            isSelected = isSelected,
            isCommunicationDevice = isCommunicationDevice,
            isBuiltIn = isBuiltIn,
            isBluetooth = isBluetooth,
            isWired = isWired,
            isUsb = isUsb,
            sampleRates = sampleRates,
            channelCounts = channelCounts,
            supportedEncodings = supportedEncodings,
            address = address
        )
    }

    fun resolveCategory(type: Int): DeviceCategory = when (type) {
        AudioDeviceInfo.TYPE_BUILTIN_EARPIECE,
        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER,
        AudioDeviceInfo.TYPE_BUILTIN_MIC,
        15 -> DeviceCategory.BUILT_IN

        AudioDeviceInfo.TYPE_WIRED_HEADSET,
        AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
        AudioDeviceInfo.TYPE_LINE_ANALOG,
        AudioDeviceInfo.TYPE_LINE_DIGITAL -> DeviceCategory.WIRED

        AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
        26, // TYPE_BLE_HEADSET
        27  // TYPE_BLE_SPEAKER
        -> DeviceCategory.BLUETOOTH

        AudioDeviceInfo.TYPE_USB_DEVICE,
        AudioDeviceInfo.TYPE_USB_ACCESSORY,
        AudioDeviceInfo.TYPE_USB_HEADSET -> DeviceCategory.USB

        AudioDeviceInfo.TYPE_HDMI,
        AudioDeviceInfo.TYPE_HDMI_ARC,
        AudioDeviceInfo.TYPE_HDMI_EARC -> DeviceCategory.HDMI

        else -> DeviceCategory.OTHER
    }

    fun resolveTypeLabel(type: Int, isInput: Boolean = false): String = when (type) {
        AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> "Phone Earpiece"
        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "Phone Speaker"
        AudioDeviceInfo.TYPE_BUILTIN_MIC -> "Built-in Microphone"
        AudioDeviceInfo.TYPE_WIRED_HEADSET -> if (isInput) "Headset Microphone" else "Wired Headset"
        AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> "Wired Headphones"
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> if (isInput) "Bluetooth Headset Mic" else "Bluetooth Headset"
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> "Bluetooth Audio"
        AudioDeviceInfo.TYPE_HDMI -> "HDMI Audio"
        AudioDeviceInfo.TYPE_USB_DEVICE -> if (isInput) "USB Microphone" else "USB Audio Device"
        15 -> "Phone Microphone"
        AudioDeviceInfo.TYPE_USB_HEADSET -> if (isInput) "USB Headset Mic" else "USB Headset"
        26 -> if (isInput) "Bluetooth LE Mic" else "Bluetooth LE Headset"
        27 -> "Bluetooth LE Speaker"
        else -> if (isInput) "Audio Input" else "Audio Output"
    }

    fun getDeviceTypeName(type: Int): String = resolveTypeLabel(type)

    fun encodingToString(encoding: Int): String = when (encoding) {
        2 -> "PCM 16-bit"
        3 -> "PCM 8-bit"
        4 -> "PCM 32-bit Float"
        5 -> "Dolby Digital (AC-3)"
        6 -> "Dolby Digital Plus (E-AC-3)"
        7 -> "DTS"
        8 -> "DTS-HD"
        9 -> "MP3"
        10 -> "AAC-LC"
        11 -> "AAC-HE v1"
        12 -> "AAC-HE v2"
        else -> "Encoding #$encoding"
    }
}
