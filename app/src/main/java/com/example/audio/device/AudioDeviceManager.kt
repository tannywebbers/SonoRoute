package com.example.audio.device

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.example.audio.diagnostics.AudioDiagnostics
import com.example.audio.diagnostics.AudioDiagnosticsProvider
import com.example.audio.model.AudioDeviceModel
import com.example.audio.model.DeviceConnectionEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AudioDeviceManager(
    private val context: Context,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default)
) {
    companion object {
        private const val TAG = "AudioDeviceManager"
    }

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val mainHandler = Handler(Looper.getMainLooper())
    private val diagnosticsProvider = AudioDiagnosticsProvider(context)

    private val _outputDevices = MutableStateFlow<List<AudioDeviceModel>>(emptyList())
    val outputDevices: StateFlow<List<AudioDeviceModel>> = _outputDevices.asStateFlow()

    private val _inputDevices = MutableStateFlow<List<AudioDeviceModel>>(emptyList())
    val inputDevices: StateFlow<List<AudioDeviceModel>> = _inputDevices.asStateFlow()

    private val _activeOutputDevice = MutableStateFlow<AudioDeviceModel?>(null)
    val activeOutputDevice: StateFlow<AudioDeviceModel?> = _activeOutputDevice.asStateFlow()

    private val _activeInputDevice = MutableStateFlow<AudioDeviceModel?>(null)
    val activeInputDevice: StateFlow<AudioDeviceModel?> = _activeInputDevice.asStateFlow()

    private val _connectionEvents = MutableSharedFlow<DeviceConnectionEvent>(replay = 0, extraBufferCapacity = 16)
    val connectionEvents: SharedFlow<DeviceConnectionEvent> = _connectionEvents.asSharedFlow()

    private val _diagnostics = MutableStateFlow<AudioDiagnostics?>(null)
    val diagnostics: StateFlow<AudioDiagnostics?> = _diagnostics.asStateFlow()

    private var isRegistered = false

    private val deviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
            Log.d(TAG, "onAudioDevicesAdded count=${addedDevices?.size ?: 0}")
            addedDevices?.forEach { info ->
                val model = AudioDeviceMapper.map(info)
                scope.launch {
                    _connectionEvents.emit(DeviceConnectionEvent(model.name, true))
                }
            }
            refreshDevices()
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
            Log.d(TAG, "onAudioDevicesRemoved count=${removedDevices?.size ?: 0}")
            removedDevices?.forEach { info ->
                val model = AudioDeviceMapper.map(info)
                scope.launch {
                    _connectionEvents.emit(DeviceConnectionEvent(model.name, false))
                }
            }
            refreshDevices()
        }
    }

    init {
        registerCallbacks()
        refreshDevices()
    }

    fun registerCallbacks() {
        if (!isRegistered) {
            try {
                audioManager.registerAudioDeviceCallback(deviceCallback, mainHandler)
                isRegistered = true
                Log.d(TAG, "AudioDeviceCallback registered successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to register AudioDeviceCallback", e)
            }
        }
    }

    fun unregisterCallbacks() {
        if (isRegistered) {
            try {
                audioManager.unregisterAudioDeviceCallback(deviceCallback)
                isRegistered = false
                Log.d(TAG, "AudioDeviceCallback unregistered")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to unregister AudioDeviceCallback", e)
            }
        }
    }

    fun refreshDevices() {
        try {
            val devices = audioManager.getDevices(AudioManager.GET_DEVICES_ALL)
            val allRawDevices = devices.toList()

            val activeCommDevice: AudioDeviceInfo? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                audioManager.communicationDevice
            } else {
                null
            }

            val outputs = allRawDevices
                .filter { it.isSink }
                .map { info ->
                    val isComm = activeCommDevice?.id == info.id
                    AudioDeviceMapper.map(info, isCommunicationDevice = isComm)
                }

            val inputs = allRawDevices
                .filter { it.isSource }
                .map { info ->
                    val isComm = activeCommDevice?.id == info.id
                    AudioDeviceMapper.map(info, isCommunicationDevice = isComm)
                }

            _outputDevices.value = outputs
            _inputDevices.value = inputs

            _activeOutputDevice.value = resolveActiveOutput(outputs, activeCommDevice)
            _activeInputDevice.value = resolveActiveInput(inputs, activeCommDevice)

            _diagnostics.value = diagnosticsProvider.getDiagnostics(allRawDevices)
        } catch (e: Exception) {
            Log.e(TAG, "Error refreshing devices", e)
        }
    }

    private fun resolveActiveOutput(
        outputs: List<AudioDeviceModel>,
        activeCommDevice: AudioDeviceInfo?
    ): AudioDeviceModel? {
        if (activeCommDevice != null && activeCommDevice.isSink) {
            val match = outputs.find { it.id == activeCommDevice.id }
            if (match != null) return match
        }
        return outputs.find { it.isBluetooth }
            ?: outputs.find { it.isUsb }
            ?: outputs.find { it.isWired }
            ?: outputs.find { it.isBuiltIn && it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
            ?: outputs.firstOrNull()
    }

    private fun resolveActiveInput(
        inputs: List<AudioDeviceModel>,
        activeCommDevice: AudioDeviceInfo?
    ): AudioDeviceModel? {
        if (activeCommDevice != null && activeCommDevice.isSource) {
            val match = inputs.find { it.id == activeCommDevice.id }
            if (match != null) return match
        }
        return inputs.find { it.isBluetooth }
            ?: inputs.find { it.isUsb }
            ?: inputs.find { it.isWired }
            ?: inputs.find { it.isBuiltIn }
            ?: inputs.firstOrNull()
    }
}
