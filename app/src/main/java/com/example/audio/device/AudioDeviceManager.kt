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

/**
 * Dedicated manager responsible for real Android audio device discovery,
 * lifecycle monitoring, and hardware connection state tracking.
 */
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

    private val _connectionEvents = MutableSharedFlow<DeviceConnectionEvent>(extraBufferCapacity = 16)
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
                    _connectionEvents.emit(
                        DeviceConnectionEvent(
                            deviceName = model.name,
                            isConnected = true
                        )
                    )
                }
            }
            refreshDevices()
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
            Log.d(TAG, "onAudioDevicesRemoved count=${removedDevices?.size ?: 0}")
            removedDevices?.forEach { info ->
                val model = AudioDeviceMapper.map(info)
                scope.launch {
                    _connectionEvents.emit(
                        DeviceConnectionEvent(
                            deviceName = model.name,
                            isConnected = false
                        )
                    )
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

    /**
     * Inspects real hardware audio devices via [AudioManager.getDevices].
     */
    fun refreshDevices() {
        try {
            val allRawDevices = audioManager.getDevices(AudioManager.GET_DEVICES_ALL).toList()

            // Check communication device if API 31+
            val activeCommDevice: AudioDeviceInfo? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                audioManager.communicationDevice
            } else null

            val outputs = mutableListOf<AudioDeviceModel>()
            val inputs = mutableListOf<AudioDeviceModel>()

            for (raw in allRawDevices) {
                val isCommDevice = activeCommDevice != null && raw.id == activeCommDevice.id

                if (raw.isSink) {
                    outputs.add(AudioDeviceMapper.map(raw, isSelected = isCommDevice, isCommunicationDevice = isCommDevice))
                }
                if (raw.isSource) {
                    inputs.add(AudioDeviceMapper.map(raw, isSelected = isCommDevice, isCommunicationDevice = isCommDevice))
                }
            }

            // Deduplicate devices by ID while preserving order
            val distinctOutputs = outputs.distinctBy { it.id }
            val distinctInputs = inputs.distinctBy { it.id }

            _outputDevices.value = distinctOutputs
            _inputDevices.value = distinctInputs

            // Resolve active output
            val primaryOutput = resolveActiveOutput(distinctOutputs, activeCommDevice)
            _activeOutputDevice.value = primaryOutput

            // Resolve active input
            val primaryInput = resolveActiveInput(distinctInputs, activeCommDevice)
            _activeInputDevice.value = primaryInput

            // Update diagnostics
            _diagnostics.value = diagnosticsProvider.getDiagnostics(allRawDevices)

            Log.d(TAG, "Refreshed: ${distinctOutputs.size} outputs, ${distinctInputs.size} inputs")
        } catch (e: Exception) {
            Log.e(TAG, "Error refreshing audio devices", e)
        }
    }

    private fun resolveActiveOutput(
        outputs: List<AudioDeviceModel>,
        activeCommDevice: AudioDeviceInfo?
    ): AudioDeviceModel? {
        if (outputs.isEmpty()) return null

        // 1. If explicit communication device matches an output
        if (activeCommDevice != null) {
            val comm = outputs.find { it.id == activeCommDevice.id }
            if (comm != null) return comm.copy(isSelected = true)
        }

        // 2. Otherwise Android's priority routing: Wired > Bluetooth > Speaker
        val wired = outputs.find { it.isWired }
        if (wired != null) return wired.copy(isSelected = true)

        val bt = outputs.find { it.isBluetooth }
        if (bt != null) return bt.copy(isSelected = true)

        val usb = outputs.find { it.isUsb }
        if (usb != null) return usb.copy(isSelected = true)

        val speaker = outputs.find { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
        if (speaker != null) return speaker.copy(isSelected = true)

        return outputs.firstOrNull()?.copy(isSelected = true)
    }

    private fun resolveActiveInput(
        inputs: List<AudioDeviceModel>,
        activeCommDevice: AudioDeviceInfo?
    ): AudioDeviceModel? {
        if (inputs.isEmpty()) return null

        // 1. If explicit communication device matches an input
        if (activeCommDevice != null) {
            val comm = inputs.find { it.id == activeCommDevice.id }
            if (comm != null) return comm.copy(isSelected = true)
        }

        // 2. Priority: Wired Headset Mic > Bluetooth Mic > USB Mic > Built-in Mic
        val wired = inputs.find { it.isWired }
        if (wired != null) return wired.copy(isSelected = true)

        val bt = inputs.find { it.isBluetooth }
        if (bt != null) return bt.copy(isSelected = true)

        val usb = inputs.find { it.isUsb }
        if (usb != null) return usb.copy(isSelected = true)

        val builtin = inputs.find { it.type == AudioDeviceInfo.TYPE_BUILTIN_MIC }
        if (builtin != null) return builtin.copy(isSelected = true)

        return inputs.firstOrNull()?.copy(isSelected = true)
    }
}
