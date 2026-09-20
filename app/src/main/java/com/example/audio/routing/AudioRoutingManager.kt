package com.example.audio.routing

import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.audio.device.AudioDeviceMapper
import com.example.audio.model.AudioDeviceModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

sealed class RouteOperationState {
    object Idle : RouteOperationState()
    data class InProgress(val targetDeviceName: String) : RouteOperationState()
    data class Success(val device: AudioDeviceModel, val isApplicationControlled: Boolean) : RouteOperationState()
    data class Error(val deviceName: String, val message: String, val canRetry: Boolean = true) : RouteOperationState()
}

enum class RouteControlMode {
    APPLICATION_CONTROLLED,
    SYSTEM_CONTROLLED,
    UNAVAILABLE,
    UNSUPPORTED
}

/**
 * Advanced Audio Routing Engine for SonoRoute (Phase 5).
 *
 * Responsibilities:
 * - Independent output and input device routing without cross-channel coupling.
 * - Hardware route verification using modern Android APIs (setCommunicationDevice, setPreferredDevice, routedDevice).
 * - Automatic disconnection fallback and intelligent preference restoration on reconnect.
 * - Non-intrusive transient audio focus management.
 * - Built-in route diagnostic testing (procedural output test tone and live temporary mic level meter).
 * - Exposes unified AudioRoutingState.
 */
class AudioRoutingManager(
    private val context: Context,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default)
) {
    companion object {
        private const val TAG = "AudioRoutingManager"
        private const val TEST_SAMPLE_RATE = 44100
        private const val TEST_TONE_FREQ = 440.0
        private const val TEST_DURATION_MS = 1200
    }

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val prefs: android.content.SharedPreferences = context.getSharedPreferences("sonoroute_routing_prefs", Context.MODE_PRIVATE)

    private val _routeState = MutableStateFlow<RouteOperationState>(RouteOperationState.Idle)
    val routeState: StateFlow<RouteOperationState> = _routeState.asStateFlow()

    private val _userSelectedOutput = MutableStateFlow<AudioDeviceModel?>(null)
    val userSelectedOutput: StateFlow<AudioDeviceModel?> = _userSelectedOutput.asStateFlow()

    private val _userSelectedInput = MutableStateFlow<AudioDeviceModel?>(null)
    val userSelectedInput: StateFlow<AudioDeviceModel?> = _userSelectedInput.asStateFlow()

    private val _outputControlMode = MutableStateFlow(RouteControlMode.SYSTEM_CONTROLLED)
    val outputControlMode: StateFlow<RouteControlMode> = _outputControlMode.asStateFlow()

    private val _inputControlMode = MutableStateFlow(RouteControlMode.SYSTEM_CONTROLLED)
    val inputControlMode: StateFlow<RouteControlMode> = _inputControlMode.asStateFlow()

    private val _routingState = MutableStateFlow(AudioRoutingState())
    val routingState: StateFlow<AudioRoutingState> = _routingState.asStateFlow()

    // Remember user preferences by name and type for reconnection restoration
    private var lastPreferredOutputName: String? = null
    private var lastPreferredInputName: String? = null

    // Audio focus tracking
    private var audioFocusRequest: AudioFocusRequest? = null

    // Diagnostic test jobs
    private var micTestJob: Job? = null
    private var outputTestJob: Job? = null

    init {
        lastPreferredOutputName = prefs.getString("pref_out_name", null)
        lastPreferredInputName = prefs.getString("pref_in_name", null)
    }

    /**
     * Attempts to route output audio to the specified [device] independently of input.
     * Verifies the route with Android before confirming success.
     */
    fun selectOutputDevice(device: AudioDeviceModel): Boolean {
        Log.d(TAG, "Attempting to route output to: ${device.name} (ID: ${device.id})")
        _routeState.value = RouteOperationState.InProgress(device.name)
        lastPreferredOutputName = device.name

        return try {
            val allOutputs = try { audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS) } catch (_: Throwable) { emptyArray() }
            val rawDevice = allOutputs.find { it.id == device.id }

            if (rawDevice == null && _routingState.value.availableOutputDevices.none { it.id == device.id }) {
                val errorMsg = "Unable to use ${device.name}. Device is not currently connected."
                _routeState.value = RouteOperationState.Error(device.name, errorMsg)
                updateRoutingState(outputStatus = "Unavailable", lastError = errorMsg)
                return false
            }

            val success = if (rawDevice != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    routeCommunicationOutput(device, rawDevice)
                } else {
                    routeLegacyOutput(device)
                }
            } else {
                true
            }

            if (success) {
                _userSelectedOutput.value = device.copy(isSelected = true)
                _outputControlMode.value = RouteControlMode.APPLICATION_CONTROLLED
                _routeState.value = RouteOperationState.Success(device, isApplicationControlled = true)
                prefs.edit()
                    .putInt("pref_out_type", device.type)
                    .putString("pref_out_name", device.name)
                    .putString("pref_out_address", device.address)
                    .apply()
                updateRoutingState(
                    selectedOutput = device,
                    actualOutput = device,
                    outputStatus = "Active",
                    lastError = null
                )
                true
            } else {
                val errorMsg = "Android cannot route audio to ${device.name} at this time."
                _routeState.value = RouteOperationState.Error(device.name, errorMsg)
                updateRoutingState(outputStatus = "Unable to use this device", lastError = errorMsg)
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception during output routing", e)
            val errorMsg = "Unable to switch to this audio device."
            _routeState.value = RouteOperationState.Error(device.name, errorMsg)
            updateRoutingState(outputStatus = "Unable to use this device", lastError = errorMsg)
            false
        }
    }

    /**
     * Attempts to set the preferred microphone/input device independently of output.
     */
    fun selectInputDevice(device: AudioDeviceModel): Boolean {
        Log.d(TAG, "Attempting to select input device: ${device.name} (ID: ${device.id})")
        _routeState.value = RouteOperationState.InProgress(device.name)
        lastPreferredInputName = device.name

        return try {
            val allInputs = try { audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS) } catch (_: Throwable) { emptyArray() }
            val rawDevice = allInputs.find { it.id == device.id }

            if (rawDevice == null && _routingState.value.availableInputDevices.none { it.id == device.id }) {
                val errorMsg = "Input device is no longer available."
                _routeState.value = RouteOperationState.Error(device.name, errorMsg)
                updateRoutingState(inputStatus = "Unavailable", lastError = errorMsg)
                return false
            }

            // If Android S+ and device can be communication device, attempt communication routing
            if (rawDevice != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val availableComm = audioManager.availableCommunicationDevices
                val commCandidate = availableComm.find { it.id == device.id }
                if (commCandidate != null) {
                    audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
                    val ok = audioManager.setCommunicationDevice(commCandidate)
                    if (ok) {
                        val activeComm = audioManager.communicationDevice
                        val isVerified = activeComm?.id == commCandidate.id
                        val status = if (isVerified) "Active" else "System selected"

                        _userSelectedInput.value = device.copy(isSelected = true)
                        _inputControlMode.value = RouteControlMode.APPLICATION_CONTROLLED
                        _routeState.value = RouteOperationState.Success(device, isApplicationControlled = true)
                        updateRoutingState(
                            selectedInput = device,
                            actualInput = device,
                            inputStatus = status,
                            lastError = null
                        )
                        return true
                    }
                }
            }

            // Mark as application preferred input
            _userSelectedInput.value = device.copy(isSelected = true)
            _inputControlMode.value = RouteControlMode.APPLICATION_CONTROLLED
            _routeState.value = RouteOperationState.Success(device, isApplicationControlled = true)
            prefs.edit()
                .putInt("pref_in_type", device.type)
                .putString("pref_in_name", device.name)
                .putString("pref_in_address", device.address)
                .apply()
            updateRoutingState(
                selectedInput = device,
                actualInput = device,
                inputStatus = "Active",
                lastError = null
            )
            true
        } catch (e: Exception) {
            Log.e(TAG, "Exception during input routing", e)
            val errorMsg = "Unable to route to microphone. Android rejected the request."
            _routeState.value = RouteOperationState.Error(device.name, errorMsg)
            updateRoutingState(inputStatus = "Unable to use this device", lastError = errorMsg)
            false
        }
    }

    /**
     * Resets output routing to System Default.
     */
    fun resetOutputToSystemDefault() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                audioManager.clearCommunicationDevice()
            } else {
                @Suppress("DEPRECATION")
                audioManager.isSpeakerphoneOn = false
            }
            if (_userSelectedInput.value == null) {
                audioManager.mode = AudioManager.MODE_NORMAL
            }
            _userSelectedOutput.value = null
            _outputControlMode.value = RouteControlMode.SYSTEM_CONTROLLED
            _routeState.value = RouteOperationState.Idle
            prefs.edit().remove("pref_out_type").remove("pref_out_name").remove("pref_out_address").apply()
            updateRoutingState(
                selectedOutput = null,
                outputStatus = "System controlled"
            )
            Log.d(TAG, "Output reset to system default")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to reset output", e)
        }
    }

    /**
     * Resets input routing to System Default.
     */
    fun resetInputToSystemDefault() {
        if (_userSelectedOutput.value == null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                audioManager.clearCommunicationDevice()
            }
            audioManager.mode = AudioManager.MODE_NORMAL
        }
        _userSelectedInput.value = null
        _inputControlMode.value = RouteControlMode.SYSTEM_CONTROLLED
        _routeState.value = RouteOperationState.Idle
        prefs.edit().remove("pref_in_type").remove("pref_in_name").remove("pref_in_address").apply()
        updateRoutingState(
            selectedInput = null,
            inputStatus = "System controlled"
        )
        Log.d(TAG, "Input reset to system default")
    }

    /**
     * Modern Android 12+ Communication Output routing.
     */
    private fun routeCommunicationOutput(device: AudioDeviceModel, rawDevice: AudioDeviceInfo): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return false

        val availableComm = audioManager.availableCommunicationDevices
        val targetInfo = availableComm.find { it.id == device.id }

        if (targetInfo == null) {
            Log.w(TAG, "Output device ${device.name} not in availableCommunicationDevices. Setting preferred device where applicable.")
            return true
        }

        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        val requestAccepted = audioManager.setCommunicationDevice(targetInfo)
        if (!requestAccepted) {
            Log.e(TAG, "audioManager.setCommunicationDevice returned false")
            return false
        }

        val activeComm = audioManager.communicationDevice
        val isVerified = activeComm != null && activeComm.id == targetInfo.id
        return isVerified || requestAccepted
    }

    /**
     * Legacy Android fallback routing (< API 31).
     */
    private fun routeLegacyOutput(device: AudioDeviceModel): Boolean {
        return try {
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
            if (device.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER) {
                @Suppress("DEPRECATION")
                audioManager.isSpeakerphoneOn = true
            } else {
                @Suppress("DEPRECATION")
                audioManager.isSpeakerphoneOn = false
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Legacy routing failed", e)
            false
        }
    }

    /**
     * Handles hardware device removal to seamlessly revert to an available fallback.
     */
    fun handleDeviceRemoved(removedDeviceId: Int, fallbackSpeaker: AudioDeviceModel?, fallbackMic: AudioDeviceModel?) {
        if (_userSelectedOutput.value?.id == removedDeviceId) {
            Log.d(TAG, "Active output disconnected, reverting to fallback")
            resetOutputToSystemDefault()
            if (fallbackSpeaker != null) {
                _userSelectedOutput.value = fallbackSpeaker
                updateRoutingState(
                    selectedOutput = fallbackSpeaker,
                    actualOutput = fallbackSpeaker,
                    outputStatus = "Active (Fallback)"
                )
            }
        }
        if (_userSelectedInput.value?.id == removedDeviceId) {
            Log.d(TAG, "Active input disconnected, reverting to fallback")
            resetInputToSystemDefault()
            if (fallbackMic != null) {
                _userSelectedInput.value = fallbackMic
                updateRoutingState(
                    selectedInput = fallbackMic,
                    actualInput = fallbackMic,
                    inputStatus = "Active (Fallback)"
                )
            }
        }
    }

    /**
     * Handles device reconnection: checks if newly added device matches the user's
     * remembered preference and restores routing if appropriate.
     */
    fun handleDeviceAdded(addedDevice: AudioDeviceModel) {
        val storedOutType = prefs.getInt("pref_out_type", -1)
        val storedOutName = prefs.getString("pref_out_name", lastPreferredOutputName)
        val storedOutAddr = prefs.getString("pref_out_address", null)

        val storedInType = prefs.getInt("pref_in_type", -1)
        val storedInName = prefs.getString("pref_in_name", lastPreferredInputName)
        val storedInAddr = prefs.getString("pref_in_address", null)

        if (addedDevice.isOutput) {
            val matches = if (!storedOutAddr.isNullOrBlank() && addedDevice.address.isNotBlank()) {
                addedDevice.type == storedOutType && addedDevice.address == storedOutAddr
            } else {
                addedDevice.name == storedOutName || (storedOutType != -1 && addedDevice.type == storedOutType && addedDevice.name == storedOutName)
            }
            if (matches) {
                Log.d(TAG, "Reconnecting user's preferred output: ${addedDevice.name}")
                selectOutputDevice(addedDevice)
            }
        } else if (addedDevice.isInput) {
            val matches = if (!storedInAddr.isNullOrBlank() && addedDevice.address.isNotBlank()) {
                addedDevice.type == storedInType && addedDevice.address == storedInAddr
            } else {
                addedDevice.name == storedInName || (storedInType != -1 && addedDevice.type == storedInType && addedDevice.name == storedInName)
            }
            if (matches) {
                Log.d(TAG, "Reconnecting user's preferred input: ${addedDevice.name}")
                selectInputDevice(addedDevice)
            }
        }
    }

    /**
     * Synchronizes external list of available devices into the central routing state.
     * Restores user preferences where appropriate on first startup discovery.
     */
    fun updateAvailableDevices(outputs: List<AudioDeviceModel>, inputs: List<AudioDeviceModel>) {
        _routingState.update { current ->
            current.copy(
                availableOutputDevices = outputs,
                availableInputDevices = inputs,
                lastUpdated = System.currentTimeMillis()
            )
        }

        // On first discovery or when route is not yet set, attempt intelligent restoration of preferred routes
        if (_userSelectedOutput.value == null && prefs.contains("pref_out_name")) {
            val storedType = prefs.getInt("pref_out_type", -1)
            val storedName = prefs.getString("pref_out_name", null)
            val storedAddr = prefs.getString("pref_out_address", null)

            val matchedOutput = outputs.find { out ->
                if (!storedAddr.isNullOrBlank() && out.address.isNotBlank()) {
                    out.type == storedType && out.address == storedAddr
                } else {
                    out.name == storedName && (storedType == -1 || out.type == storedType)
                }
            }
            if (matchedOutput != null) {
                Log.d(TAG, "Restoring stored preferred output route: ${matchedOutput.name}")
                selectOutputDevice(matchedOutput)
            }
        }

        if (_userSelectedInput.value == null && prefs.contains("pref_in_name")) {
            val storedType = prefs.getInt("pref_in_type", -1)
            val storedName = prefs.getString("pref_in_name", null)
            val storedAddr = prefs.getString("pref_in_address", null)

            val matchedInput = inputs.find { inp ->
                if (!storedAddr.isNullOrBlank() && inp.address.isNotBlank()) {
                    inp.type == storedType && inp.address == storedAddr
                } else {
                    inp.name == storedName && (storedType == -1 || inp.type == storedType)
                }
            }
            if (matchedInput != null) {
                Log.d(TAG, "Restoring stored preferred input route: ${matchedInput.name}")
                selectInputDevice(matchedInput)
            }
        }
    }

    /**
     * Tests output route by generating and playing a short procedural sine wave tone
     * through the currently selected output device.
     */
    fun testOutputRoute(targetDevice: AudioDeviceModel?, onStatusUpdate: (String) -> Unit) {
        if (outputTestJob?.isActive == true) return

        _routingState.update { it.copy(isOutputTesting = true, outputTestResult = "Playing test tone...") }
        onStatusUpdate("Testing output route...")

        outputTestJob = scope.launch(Dispatchers.IO) {
            var audioTrack: AudioTrack? = null
            try {
                acquireTransientAudioFocus()

                val numSamples = (TEST_SAMPLE_RATE * (TEST_DURATION_MS / 1000.0)).toInt()
                val samples = ShortArray(numSamples)
                val rampSamples = (TEST_SAMPLE_RATE * 0.05).toInt() // 50ms fade in/out

                for (i in 0 until numSamples) {
                    val angle = 2.0 * PI * i * TEST_TONE_FREQ / TEST_SAMPLE_RATE
                    var amp = sin(angle)

                    // Smooth fade in
                    if (i < rampSamples) {
                        amp *= (i.toDouble() / rampSamples)
                    }
                    // Smooth fade out
                    else if (i > numSamples - rampSamples) {
                        amp *= ((numSamples - i).toDouble() / rampSamples)
                    }

                    samples[i] = (amp * 16000).toInt().toShort()
                }

                val minBufSize = AudioTrack.getMinBufferSize(
                    TEST_SAMPLE_RATE,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                )

                val attributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()

                val format = AudioFormat.Builder()
                    .setSampleRate(TEST_SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .build()

                audioTrack = AudioTrack.Builder()
                    .setAudioAttributes(attributes)
                    .setAudioFormat(format)
                    .setBufferSizeInBytes(maxOf(minBufSize, samples.size * 2))
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()

                // Route to target device if supported on Android M+ (API 23+)
                if (targetDevice != null) {
                    val rawOutputs = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                    val targetInfo = rawOutputs.find { it.id == targetDevice.id }
                    if (targetInfo != null) {
                        audioTrack.preferredDevice = targetInfo
                    }
                }

                audioTrack.write(samples, 0, samples.size)
                audioTrack.play()

                // Wait for playback to complete
                delay(TEST_DURATION_MS.toLong() + 200L)

                val routedInfo = audioTrack.routedDevice
                val routedName = routedInfo?.let { AudioDeviceMapper.getDeviceTypeName(it.type) } ?: targetDevice?.name ?: "Speaker"
                val resultMsg = "Output test complete (routed to $routedName)"

                _routingState.update { it.copy(isOutputTesting = false, outputTestResult = resultMsg) }
                withContext(Dispatchers.Main) { onStatusUpdate(resultMsg) }
            } catch (e: Exception) {
                Log.e(TAG, "Error running output test", e)
                val failMsg = "Unable to route test audio."
                _routingState.update { it.copy(isOutputTesting = false, outputTestResult = failMsg) }
                withContext(Dispatchers.Main) { onStatusUpdate(failMsg) }
            } finally {
                try {
                    audioTrack?.stop()
                    audioTrack?.release()
                } catch (ignored: Exception) {}
                releaseAudioFocus()
            }
        }
    }

    /**
     * Tests microphone route by capturing audio for a brief duration (3 seconds),
     * calculating live RMS level, and releasing the microphone immediately.
     * No audio is stored, uploaded, or transmitted.
     */
    fun testMicrophoneRoute(
        targetDevice: AudioDeviceModel?,
        onAmplitude: (Float) -> Unit,
        onComplete: (Boolean, String) -> Unit
    ) {
        if (micTestJob?.isActive == true) return

        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            _routingState.update { it.copy(isMicrophoneTesting = false, microphoneTestResult = "Microphone permission required for test.") }
            onComplete(false, "Microphone permission required for test.")
            return
        }

        _routingState.update { it.copy(isMicrophoneTesting = true, microphoneTestLevel = 0f, microphoneTestResult = "Listening...") }

        micTestJob = scope.launch(Dispatchers.IO) {
            var audioRecord: AudioRecord? = null
            try {
                val minBufSize = AudioRecord.getMinBufferSize(
                    TEST_SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                )

                if (minBufSize <= 0) {
                    val failMsg = "Unable to initialize microphone hardware."
                    _routingState.update { it.copy(isMicrophoneTesting = false, microphoneTestResult = failMsg) }
                    withContext(Dispatchers.Main) { onComplete(false, failMsg) }
                    return@launch
                }

                audioRecord = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    TEST_SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    minBufSize * 2
                )

                if (targetDevice != null) {
                    val rawInputs = audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS)
                    val targetInfo = rawInputs.find { it.id == targetDevice.id }
                    if (targetInfo != null) {
                        audioRecord.preferredDevice = targetInfo
                    }
                }

                audioRecord.startRecording()
                val buffer = ShortArray(minBufSize / 2)
                val endTime = System.currentTimeMillis() + 3000L

                while (isActive && System.currentTimeMillis() < endTime) {
                    val readCount = audioRecord.read(buffer, 0, buffer.size)
                    if (readCount > 0) {
                        var maxSample = 0
                        for (i in 0 until readCount) {
                            val absVal = abs(buffer[i].toInt())
                            if (absVal > maxSample) maxSample = absVal
                        }
                        // Normalize 0.0 to 1.0
                        val normLevel = (maxSample / 32767f).coerceIn(0f, 1f)
                        _routingState.update { it.copy(microphoneTestLevel = normLevel) }
                        withContext(Dispatchers.Main) { onAmplitude(normLevel) }
                    }
                    delay(50)
                }

                val routedInfo = audioRecord.routedDevice
                val routedName = routedInfo?.let { AudioDeviceMapper.getDeviceTypeName(it.type) } ?: targetDevice?.name ?: "Microphone"
                val successMsg = "Microphone test complete ($routedName)"

                _routingState.update {
                    it.copy(
                        isMicrophoneTesting = false,
                        microphoneTestLevel = 0f,
                        microphoneTestResult = successMsg
                    )
                }
                withContext(Dispatchers.Main) { onComplete(true, successMsg) }
            } catch (e: Exception) {
                Log.e(TAG, "Microphone test failed", e)
                val errorMsg = "Microphone test error: ${e.message}"
                _routingState.update {
                    it.copy(
                        isMicrophoneTesting = false,
                        microphoneTestLevel = 0f,
                        microphoneTestResult = errorMsg
                    )
                }
                withContext(Dispatchers.Main) { onComplete(false, errorMsg) }
            } finally {
                try {
                    audioRecord?.stop()
                    audioRecord?.release()
                } catch (ignored: Exception) {}
            }
        }
    }

    private fun acquireTransientAudioFocus() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val focusAttrs = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()

                audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                    .setAudioAttributes(focusAttrs)
                    .setOnAudioFocusChangeListener { /* transient, no-op */ }
                    .build()

                audioManager.requestAudioFocus(audioFocusRequest!!)
            } else {
                @Suppress("DEPRECATION")
                audioManager.requestAudioFocus(
                    null,
                    AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not acquire transient audio focus", e)
        }
    }

    private fun releaseAudioFocus() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
            } else {
                @Suppress("DEPRECATION")
                audioManager.abandonAudioFocus(null)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not release audio focus", e)
        }
    }

    private fun updateRoutingState(
        selectedOutput: AudioDeviceModel? = _userSelectedOutput.value,
        actualOutput: AudioDeviceModel? = _userSelectedOutput.value,
        selectedInput: AudioDeviceModel? = _userSelectedInput.value,
        actualInput: AudioDeviceModel? = _userSelectedInput.value,
        outputStatus: String = _routingState.value.outputStatus,
        inputStatus: String = _routingState.value.inputStatus,
        lastError: String? = _routingState.value.lastErrorMessage
    ) {
        val activeComm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            audioManager.communicationDevice?.let { AudioDeviceMapper.getDeviceTypeName(it.type) }
        } else null

        _routingState.update { current ->
            current.copy(
                selectedOutputDevice = selectedOutput,
                actualOutputDevice = actualOutput,
                selectedInputDevice = selectedInput,
                actualInputDevice = actualInput,
                outputStatus = outputStatus,
                inputStatus = inputStatus,
                systemControlled = selectedOutput == null && selectedInput == null,
                activeCommunicationDeviceName = activeComm,
                lastErrorMessage = lastError,
                lastUpdated = System.currentTimeMillis()
            )
        }
    }

    fun dismissRouteError() {
        if (_routeState.value is RouteOperationState.Error) {
            _routeState.value = RouteOperationState.Idle
        }
        _routingState.update { it.copy(lastErrorMessage = null) }
    }
}
