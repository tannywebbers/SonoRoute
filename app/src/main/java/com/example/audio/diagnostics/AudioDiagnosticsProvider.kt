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

    @Suppress("DEPRECATION")
    fun getDiagnostics(allDevices: List<AudioDeviceInfo>): AudioDiagnostics {
        val lowLatencyFeature = packageManager.hasSystemFeature(PackageManager.FEATURE_AUDIO_LOW_LATENCY)
        val proAudioFeature = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            packageManager.hasSystemFeature(PackageManager.FEATURE_AUDIO_PRO)
        } else false
        val outputFeature = packageManager.hasSystemFeature(PackageManager.FEATURE_AUDIO_OUTPUT)
        val microphoneFeature = packageManager.hasSystemFeature(PackageManager.FEATURE_MICROPHONE)

        val sampleRateProp = audioManager.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE)
        val bufferFramesProp = audioManager.getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER)

        val commDeviceName = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            audioManager.communicationDevice?.let { dev ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    dev.productName?.toString() ?: "Device #${dev.id}"
                } else {
                    "Device #${dev.id}"
                }
            }
        } else null

        val outputs = allDevices.filter { it.isSink }
        val inputs = allDevices.filter { it.isSource }

        val hasBt = allDevices.any {
            it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
            it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && it.type == AudioDeviceInfo.TYPE_BLE_HEADSET) ||
            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && it.type == AudioDeviceInfo.TYPE_BLE_SPEAKER)
        }

        val hasUsb = allDevices.any {
            it.type == AudioDeviceInfo.TYPE_USB_DEVICE ||
            it.type == AudioDeviceInfo.TYPE_USB_HEADSET ||
            it.type == AudioDeviceInfo.TYPE_USB_ACCESSORY
        }

        val hasWired = allDevices.any {
            it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
            it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
            it.type == AudioDeviceInfo.TYPE_LINE_ANALOG
        }

        return AudioDiagnostics(
            androidVersion = Build.VERSION.RELEASE,
            sdkInt = Build.VERSION.SDK_INT,
            manufacturer = Build.MANUFACTURER,
            model = Build.MODEL,
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

    private fun audioModeToString(mode: Int): String {
        return when (mode) {
            AudioManager.MODE_NORMAL -> "MODE_NORMAL"
            AudioManager.MODE_RINGTONE -> "MODE_RINGTONE"
            AudioManager.MODE_IN_CALL -> "MODE_IN_CALL"
            AudioManager.MODE_IN_COMMUNICATION -> "MODE_IN_COMMUNICATION"
            AudioManager.MODE_CALL_SCREENING -> "MODE_CALL_SCREENING"
            else -> "MODE_UNKNOWN ($mode)"
        }
    }
}
