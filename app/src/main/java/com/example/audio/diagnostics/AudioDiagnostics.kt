package com.example.audio.diagnostics

import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build

data class AudioDiagnostics(
    val androidVersion: String,
    val sdkInt: Int,
    val manufacturer: String,
    val model: String,
    val lowLatencyFeature: Boolean,
    val proAudioFeature: Boolean,
    val outputFeature: Boolean,
    val microphoneFeature: Boolean,
    val sampleRateProperty: String?,
    val bufferFramesProperty: String?,
    val communicationDeviceSupported: Boolean,
    val currentAudioMode: String,
    val isSpeakerphoneOn: Boolean,
    val isBluetoothScoOn: Boolean,
    val totalOutputDevices: Int,
    val totalInputDevices: Int,
    val hasBluetoothAudio: Boolean,
    val hasUsbAudio: Boolean,
    val hasWiredAudio: Boolean,
    val communicationDeviceName: String?
)

class AudioDiagnosticsProvider(private val context: Context) {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val packageManager = context.packageManager

    fun getDiagnostics(allDevices: List<AudioDeviceInfo>): AudioDiagnostics {
        val lowLatencyFeature = packageManager.hasSystemFeature(PackageManager.FEATURE_AUDIO_LOW_LATENCY)
        val proAudioFeature = packageManager.hasSystemFeature(PackageManager.FEATURE_AUDIO_PRO)
        val outputFeature = packageManager.hasSystemFeature(PackageManager.FEATURE_AUDIO_OUTPUT)
        val microphoneFeature = packageManager.hasSystemFeature(PackageManager.FEATURE_MICROPHONE)

        val sampleRateProp = audioManager.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE)
        val bufferFramesProp = audioManager.getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER)

        var commDeviceName: String? = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val commDevice = audioManager.communicationDevice
            if (commDevice != null) {
                commDeviceName = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    commDevice.productName?.toString() ?: "Device #${commDevice.id}"
                } else {
                    "Device #${commDevice.id}"
                }
            }
        }

        val outputs = allDevices.filter { it.isSink }
        val inputs = allDevices.filter { it.isSource }

        val hasBt = allDevices.any { dev ->
            dev.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
            dev.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && dev.type == 26) ||
            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && dev.type == 27)
        }

        val hasUsb = allDevices.any { dev ->
            dev.type == AudioDeviceInfo.TYPE_USB_DEVICE ||
            dev.type == AudioDeviceInfo.TYPE_USB_HEADSET ||
            dev.type == AudioDeviceInfo.TYPE_USB_ACCESSORY
        }

        val hasWired = allDevices.any { dev ->
            dev.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
            dev.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
            dev.type == AudioDeviceInfo.TYPE_LINE_ANALOG
        }

        return AudioDiagnostics(
            androidVersion = Build.VERSION.RELEASE ?: "",
            sdkInt = Build.VERSION.SDK_INT,
            manufacturer = Build.MANUFACTURER ?: "",
            model = Build.MODEL ?: "",
            lowLatencyFeature = lowLatencyFeature,
            proAudioFeature = proAudioFeature,
            outputFeature = outputFeature,
            microphoneFeature = microphoneFeature,
            sampleRateProperty = sampleRateProp,
            bufferFramesProperty = bufferFramesProp,
            communicationDeviceSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
            currentAudioMode = audioModeToString(audioManager.mode),
            isSpeakerphoneOn = audioManager.isSpeakerphoneOn,
            isBluetoothScoOn = audioManager.isBluetoothScoOn,
            totalOutputDevices = outputs.size,
            totalInputDevices = inputs.size,
            hasBluetoothAudio = hasBt,
            hasUsbAudio = hasUsb,
            hasWiredAudio = hasWired,
            communicationDeviceName = commDeviceName
        )
    }

    private fun audioModeToString(mode: Int): String = when (mode) {
        AudioManager.MODE_NORMAL -> "MODE_NORMAL"
        AudioManager.MODE_RINGTONE -> "MODE_RINGTONE"
        AudioManager.MODE_IN_CALL -> "MODE_IN_CALL"
        AudioManager.MODE_IN_COMMUNICATION -> "MODE_IN_COMMUNICATION"
        AudioManager.MODE_CALL_SCREENING -> "MODE_CALL_SCREENING"
        else -> "MODE_UNKNOWN ($mode)"
    }
}
