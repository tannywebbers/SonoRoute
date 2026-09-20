package com.example.audio.device

import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.os.Build
import com.example.audio.model.AudioDeviceModel
import com.example.audio.model.DeviceCategory

object AudioDeviceMapper {

    fun map(
        info: AudioDeviceInfo,
        isSelected: Boolean = false,
        isCommunicationDevice: Boolean = false
    ): AudioDeviceModel {
        val type = info.type
        val isInput = info.isSource
        val isOutput = info.isSink

        val (category, isBuiltIn, isBluetooth, isWired, isUsb) = classify(type)
        val typeLabel = resolveTypeLabel(type)

        // Read productName safely
        val rawName = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.productName?.toString()?.takeIf { it.isNotBlank() }
        } else {
            null
        }
        val cleanName = rawName ?: defaultNameForType(type, isInput)

        val address = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                info.address ?: ""
            } catch (_: Throwable) {
                ""
            }
        } else {
            ""
        }

        val sampleRates = try {
            info.sampleRates?.toList()?.filter { it > 0 } ?: emptyList()
        } catch (_: Throwable) {
            emptyList()
        }

        val channelCounts = try {
            info.channelCounts?.toList()?.filter { it > 0 } ?: emptyList()
        } catch (_: Throwable) {
            emptyList()
        }

        val encodings = try {
            info.encodings?.map { encodingToString(it) }?.filter { it.isNotBlank() } ?: emptyList()
        } catch (_: Throwable) {
            emptyList()
        }

        return AudioDeviceModel(
            id = info.id,
            name = cleanName,
            type = type,
            typeLabel = typeLabel,
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
            supportedEncodings = encodings,
            address = address
        )
    }

    private fun classify(type: Int): Quintuple<DeviceCategory, Boolean, Boolean, Boolean, Boolean> {
        return when (type) {
            AudioDeviceInfo.TYPE_BUILTIN_EARPIECE,
            AudioDeviceInfo.TYPE_BUILTIN_SPEAKER,
            AudioDeviceInfo.TYPE_BUILTIN_MIC -> {
                Quintuple(DeviceCategory.BUILT_IN, true, false, false, false)
            }
            AudioDeviceInfo.TYPE_WIRED_HEADSET,
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
            AudioDeviceInfo.TYPE_LINE_ANALOG,
            AudioDeviceInfo.TYPE_LINE_DIGITAL,
            AudioDeviceInfo.TYPE_AUX_LINE -> {
                Quintuple(DeviceCategory.WIRED, false, false, true, false)
            }
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            AudioDeviceInfo.TYPE_BLE_HEADSET,
            AudioDeviceInfo.TYPE_BLE_SPEAKER,
            AudioDeviceInfo.TYPE_BLE_BROADCAST,
            AudioDeviceInfo.TYPE_HEARING_AID -> {
                Quintuple(DeviceCategory.BLUETOOTH, false, true, false, false)
            }
            AudioDeviceInfo.TYPE_USB_DEVICE,
            AudioDeviceInfo.TYPE_USB_ACCESSORY,
            AudioDeviceInfo.TYPE_USB_HEADSET -> {
                Quintuple(DeviceCategory.USB, false, false, false, true)
            }
            AudioDeviceInfo.TYPE_HDMI,
            AudioDeviceInfo.TYPE_HDMI_ARC,
            AudioDeviceInfo.TYPE_HDMI_EARC -> {
                Quintuple(DeviceCategory.HDMI, false, false, false, false)
            }
            else -> {
                Quintuple(DeviceCategory.OTHER, false, false, false, false)
            }
        }
    }

    fun resolveTypeLabel(type: Int): String {
        return when (type) {
            AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> "Phone Earpiece"
            AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "Phone Speaker"
            AudioDeviceInfo.TYPE_BUILTIN_MIC -> "Built-in Microphone"
            AudioDeviceInfo.TYPE_WIRED_HEADSET -> "Wired Headset (Mic + Output)"
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> "Wired Headphones"
            AudioDeviceInfo.TYPE_LINE_ANALOG -> "Analog Line Out"
            AudioDeviceInfo.TYPE_LINE_DIGITAL -> "Digital Line Out"
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "Bluetooth SCO Communication"
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> "Bluetooth A2DP Stereo"
            AudioDeviceInfo.TYPE_BLE_HEADSET -> "Bluetooth LE Headset"
            AudioDeviceInfo.TYPE_BLE_SPEAKER -> "Bluetooth LE Speaker"
            AudioDeviceInfo.TYPE_BLE_BROADCAST -> "Bluetooth LE Broadcast"
            AudioDeviceInfo.TYPE_HEARING_AID -> "Hearing Aid"
            AudioDeviceInfo.TYPE_USB_DEVICE -> "USB Audio Device / DAC"
            AudioDeviceInfo.TYPE_USB_ACCESSORY -> "USB Audio Accessory"
            AudioDeviceInfo.TYPE_USB_HEADSET -> "USB Headset"
            AudioDeviceInfo.TYPE_HDMI -> "HDMI Display Audio"
            AudioDeviceInfo.TYPE_HDMI_ARC -> "HDMI Audio Return Channel (ARC)"
            AudioDeviceInfo.TYPE_HDMI_EARC -> "HDMI Enhanced ARC (eARC)"
            AudioDeviceInfo.TYPE_DOCK -> "Audio Dock"
            AudioDeviceInfo.TYPE_FM -> "FM Radio"
            AudioDeviceInfo.TYPE_FM_TUNER -> "FM Tuner"
            AudioDeviceInfo.TYPE_TV_TUNER -> "TV Tuner"
            AudioDeviceInfo.TYPE_TELEPHONY -> "Telephony"
            AudioDeviceInfo.TYPE_AUX_LINE -> "Auxiliary Line"
            AudioDeviceInfo.TYPE_IP -> "Network IP Audio"
            AudioDeviceInfo.TYPE_BUS -> "System Bus Audio"
            AudioDeviceInfo.TYPE_REMOTE_SUBMIX -> "Remote Submix"
            else -> "Audio Device (Type #$type)"
        }
    }

    private fun defaultNameForType(type: Int, isInput: Boolean): String {
        return when (type) {
            AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> "Phone Earpiece"
            AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "Phone Speaker"
            AudioDeviceInfo.TYPE_BUILTIN_MIC -> "Phone Microphone"
            AudioDeviceInfo.TYPE_WIRED_HEADSET -> if (isInput) "Wired Headset Mic" else "Wired Headset"
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> "Wired Headphones"
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> if (isInput) "Bluetooth Headset Mic" else "Bluetooth Headset"
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> "Bluetooth Audio"
            AudioDeviceInfo.TYPE_BLE_HEADSET -> if (isInput) "Bluetooth LE Mic" else "Bluetooth LE Headset"
            AudioDeviceInfo.TYPE_BLE_SPEAKER -> "Bluetooth LE Speaker"
            AudioDeviceInfo.TYPE_USB_DEVICE -> if (isInput) "USB Microphone" else "USB Audio Device"
            AudioDeviceInfo.TYPE_USB_HEADSET -> if (isInput) "USB Headset Mic" else "USB Headset"
            AudioDeviceInfo.TYPE_HDMI -> "HDMI Audio"
            else -> if (isInput) "Audio Input" else "Audio Output"
        }
    }

    private fun encodingToString(encoding: Int): String {
        return when (encoding) {
            AudioFormat.ENCODING_PCM_16BIT -> "PCM 16-bit"
            AudioFormat.ENCODING_PCM_8BIT -> "PCM 8-bit"
            AudioFormat.ENCODING_PCM_FLOAT -> "PCM 32-bit Float"
            AudioFormat.ENCODING_AC3 -> "Dolby Digital (AC-3)"
            AudioFormat.ENCODING_E_AC3 -> "Dolby Digital Plus (E-AC-3)"
            AudioFormat.ENCODING_DTS -> "DTS"
            AudioFormat.ENCODING_DTS_HD -> "DTS-HD"
            AudioFormat.ENCODING_MP3 -> "MP3"
            AudioFormat.ENCODING_AAC_LC -> "AAC-LC"
            AudioFormat.ENCODING_AAC_HE_V1 -> "AAC-HE v1"
            AudioFormat.ENCODING_AAC_HE_V2 -> "AAC-HE v2"
            AudioFormat.ENCODING_IEC61937 -> "IEC 61937 (Pass-through)"
            AudioFormat.ENCODING_DOLBY_TRUEHD -> "Dolby TrueHD"
            AudioFormat.ENCODING_AAC_ELD -> "AAC-ELD"
            AudioFormat.ENCODING_AAC_XHE -> "xHE-AAC"
            AudioFormat.ENCODING_AC4 -> "AC-4"
            AudioFormat.ENCODING_E_AC3_JOC -> "E-AC-3 JOC (Atmos)"
            AudioFormat.ENCODING_DOLBY_MAT -> "Dolby MAT"
            else -> "Encoding #$encoding"
        }
    }

    fun getDeviceTypeName(type: Int): String = resolveTypeLabel(type)

    private data class Quintuple<A, B, C, D, E>(
        val first: A,
        val second: B,
        val third: C,
        val fourth: D,
        val fifth: E
    )
}
