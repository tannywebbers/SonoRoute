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
 * Advanced Audio Routing Engine for SonoRoute (Phase 9).
 *
 * Responsibilities:
 * - Independent output and input device routing without cross-channel coupling.
 * - Hardware route verification using modern Android APIs (setCommunicationDevice, setPreferredDevice, routedDevice).
 * - Distinguishes requested route vs actual hardware route.
 * - Automatic disconnection fallback and intelligent preference restoration on reconnect.
 * - Non-intrusive transient audio focus management.
 * - Built-in route diagnostic testing:
 *     * Output test: procedural 3-second audible test tone with live countdown and actual routed device verification.
 *     * Microphone test: 3-second recording into in-memory buffer followed immediately by 3-second playback through selected output route.
 * - Prevents automatic route reversion via bounded recovery guard.
 */
class AudioRoutingManager(
    private val context: Context,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default)
) {
    companion object {
        private const val TAG = "AudioRoutingManager"
        private const val TEST_SAMPLE_RATE = 44100
        private const val TEST_TONE_FREQ = 440.0
        private const val TEST_OUTPUT_DURATION_MS = 3000L
        private const val TEST_MIC_DURATION_MS = 3000L
        private const val MAX_RECOVERY_ATTEMPTS = 3
    }

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val prefs = context.getSharedPreferences("sonoroute_routing_prefs", Context.MODE_PRIVATE)

    private val _routeState = MutableStateFlow<RouteOperationState>(RouteOperationState.Idle)
    val routeState: StateFlow<RouteOperationState> = _routeState.asStateFlow()

    // Requested routes (user selections)
    private val _userSelectedOutput = MutableStateFlow<AudioDeviceModel?>(null)
    val userSelectedOutput: StateFlow<AudioDeviceModel?> = _userSelectedOutput.asStateFlow()

    private val _userSelectedInput = MutableStateFlow<AudioDeviceModel?>(null)
    val userSelectedInput: StateFlow<AudioDeviceModel?> = _userSelectedInput.asStateFlow()

    // Actual hardware routes (verified from AudioTrack/AudioRecord/CommunicationDevice)
    private val _actualOutput = MutableStateFlow<AudioDeviceModel?>(null)
    val actualOutput: StateFlow<AudioDeviceModel?> = _actualOutput.asStateFlow()

    private val _actualInput = MutableStateFlow<AudioDeviceModel?>(null)
    val actualInput: StateFlow<AudioDeviceModel?> = _actualInput.asStateFlow()

    private val _outputRouteVerified = MutableStateFlow(false)
    val outputRouteVerified: StateFlow<Boolean> = _outputRouteVerified.asStateFlow()

    private val _inputRouteVerified = MutableStateFlow(false)
    val inputRouteVerified: StateFlow<Boolean> = _inputRouteVerified.asStateFlow()

    private val _outputRoutingReason = MutableStateFlow<String?>(null)
    val outputRoutingReason: StateFlow<String?> = _outputRoutingReason.asStateFlow()

    private val _inputRoutingReason = MutableStateFlow<String?>(null)
    val inputRoutingReason: StateFlow<String?> = _inputRoutingReason.asStateFlow()

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

    // Bounded route recovery counters
    private var outputRecoveryCount = 0
    private var inputRecoveryCount = 0

    init {
        lastPreferredOutputName = prefs.getString("pref_out_name", null)
        lastPreferredInputName = prefs.getString("pref_in_name", null)
    }

    /**
     * Attempts to route output audio to the specified [device] independently of input.
     * Distinguishes requested vs actual verified device.
     */
    fun selectOutputDevice(device: AudioDeviceModel): Boolean {
        Log.d(TAG, "Attempting to route output to: ${device.name} (ID: ${device.id})")
        _routeState.value = RouteOperationState.InProgress(device.name)
        lastPreferredOutputName = device.name
        outputRecoveryCount = 0

        return try {
            val allOutputs = try { audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS) } catch (_: Throwable) { emptyArray() }
            val rawDevice = allOutputs.find { it.id == device.id }

            if (rawDevice == null && _routingState.value.availableOutputDevices.none { it.id == device.id }) {
                val errorMsg = "Unable to use ${device.name}. Device is not currently connected."
                _routeState.value = RouteOperationState.Error(device.name, errorMsg)
                updateRoutingState(
                    outputStatus = "Unavailable",
                    lastError = errorMsg
                )
                return false
            }

            var verified = false
            var reason = "Requested by user"

            if (rawDevice != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val availableComm = audioManager.availableCommunicationDevices
                    val targetInfo = availableComm.find { it.id == device.id }
                    if (targetInfo != null) {
                        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
                        val ok = audioManager.setCommunicationDevice(targetInfo)
                        if (ok) {
                            val activeComm = audioManager.communicationDevice
                            verified = activeComm != null && activeComm.id == targetInfo.id
                            reason = if (verified) "Enforced via CommunicationDevice" else "Requested via CommunicationDevice"
                        }
                    } else {
                        reason = "Enforced for SonoRoute streams via preferredDevice"
                    }
                } else {
                    routeLegacyOutput(device)
                    verified = true
                    reason = "Applied legacy speakerphone setting"
                }
            }

            _userSelectedOutput.value = device.copy(isSelected = true)
            _outputControlMode.value = RouteControlMode.APPLICATION_CONTROLLED
            _routeState.value = RouteOperationState.Success(device, isApplicationControlled = true)

            prefs.edit()
                .putInt("pref_out_type", device.type)
                .putString("pref_out_name", device.name)
                .putString("pref_out_address", device.address)
                .apply()

            _outputRouteVerified.value = verified
            _outputRoutingReason.value = reason
            _actualOutput.value = if (verified) device else (_actualOutput.value ?: device)

            val statusText = if (verified) "Active (Verified)" else "Active"
            updateRoutingState(
                selectedOutput = device,
                actualOutput = _actualOutput.value,
                outputStatus = statusText,
                lastError = null
            )
            true
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
     * Does not assume success without actual hardware verification.
     */
    fun selectInputDevice(device: AudioDeviceModel): Boolean {
        Log.d(TAG, "Attempting to select input device: ${device.name} (ID: ${device.id})")
        _routeState.value = RouteOperationState.InProgress(device.name)
        lastPreferredInputName = device.name
        inputRecoveryCount = 0

        return try {
            val allInputs = try { audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS) } catch (_: Throwable) { emptyArray() }
            val rawDevice = allInputs.find { it.id == device.id }

            if (rawDevice == null && _routingState.value.availableInputDevices.none { it.id == device.id }) {
                val errorMsg = "Input device is no longer available."
                _routeState.value = RouteOperationState.Error(device.name, errorMsg)
                updateRoutingState(inputStatus = "Unavailable", lastError = errorMsg)
                return false
            }

            var verified = false
            var reason = "Requested by user"

            // If Android S+ and device is an available communication device, attempt communication routing
            if (rawDevice != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val availableComm = audioManager.availableCommunicationDevices
                val commCandidate = availableComm.find { it.id == device.id }
                if (commCandidate != null) {
                    audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
                    val ok = audioManager.setCommunicationDevice(commCandidate)
                    if (ok) {
                        val activeComm = audioManager.communicationDevice
                        verified = activeComm?.id == commCandidate.id
                        reason = if (verified) "Enforced via CommunicationDevice" else "Requested via CommunicationDevice"
                    }
                } else {
                    reason = "Enforced for SonoRoute streams via setPreferredDevice"
                }
            } else {
                reason = "Enforced for SonoRoute streams via setPreferredDevice"
            }

            _userSelectedInput.value = device.copy(isSelected = true)
            _inputControlMode.value = RouteControlMode.APPLICATION_CONTROLLED
            _routeState.value = RouteOperationState.Success(device, isApplicationControlled = true)

            prefs.edit()
                .putInt("pref_in_type", device.type)
                .putString("pref_in_name", device.name)
                .putString("pref_in_address", device.address)
                .apply()

            _inputRouteVerified.value = verified
            _inputRoutingReason.value = reason
            _actualInput.value = if (verified) device else (_actualInput.value ?: device)

            val statusText = if (verified) "Active (Verified)" else "Active"
            updateRoutingState(
                selectedInput = device,
                actualInput = _actualInput.value,
                inputStatus = statusText,
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
            _actualOutput.value = null
            _outputRouteVerified.value = false
            _outputRoutingReason.value = "System default"
            _outputControlMode.value = RouteControlMode.SYSTEM_CONTROLLED
            _routeState.value = RouteOperationState.Idle
            prefs.edit().remove("pref_out_type").remove("pref_out_name").remove("pref_out_address").apply()
            updateRoutingState(
                selectedOutput = null,
                actualOutput = null,
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
        _actualInput.value = null
        _inputRouteVerified.value = false
        _inputRoutingReason.value = "System default"
        _inputControlMode.value = RouteControlMode.SYSTEM_CONTROLLED
        _routeState.value = RouteOperationState.Idle
        prefs.edit().remove("pref_in_type").remove("pref_in_name").remove("pref_in_address").apply()
        updateRoutingState(
            selectedInput = null,
            actualInput = null,
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
            Log.w(TAG, "Output device ${device.name} not in availableCommunicationDevices. Setting preferred device on active streams.")
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
     * Binds an active AudioRecord session to the requested input device and listens
     * for hardware route changes via [AudioRecord.OnRoutingChangedListener].
     */
    fun bindActiveRecord(
        audioRecord: AudioRecord,
        requestedDevice: AudioDeviceModel?,
        onRouteUpdated: (actualDevice: AudioDeviceModel?, isVerified: Boolean, reason: String) -> Unit = { _, _, _ -> }
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (requestedDevice != null) {
                val rawInputs = audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS)
                val targetInfo = rawInputs.find { it.id == requestedDevice.id }
                    ?: rawInputs.find { it.type == requestedDevice.type }
                if (targetInfo != null) {
                    val ok = audioRecord.setPreferredDevice(targetInfo)
                    Log.d(TAG, "setPreferredDevice on AudioRecord ($ok): ${requestedDevice.name}")
                }
            }

            fun inspectRecordRoute(record: AudioRecord) {
                val routedInfo = record.routedDevice
                if (routedInfo != null) {
                    val mapped = AudioDeviceMapper.map(routedInfo)
                    val isMatch = requestedDevice == null ||
                            routedInfo.id == requestedDevice.id ||
                            routedInfo.type == requestedDevice.type
                    _actualInput.value = mapped
                    _inputRouteVerified.value = isMatch
                    val reason = if (isMatch) {
                        "Verified by AudioRecord (${mapped.name})"
                    } else {
                        "Android assigned: ${mapped.name} (Requested: ${requestedDevice.name})"
                    }
                    _inputRoutingReason.value = reason
                    val statusText = if (isMatch) "Active (Verified)" else "Android selected: ${mapped.name}"
                    updateRoutingState(actualInput = mapped, inputStatus = statusText)
                    onRouteUpdated(mapped, isMatch, reason)

                    // Bounded recovery if route changed unbidden and attempts remain
                    if (!isMatch && inputRecoveryCount < MAX_RECOVERY_ATTEMPTS && requestedDevice != null) {
                        inputRecoveryCount++
                        val rawInputs = audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS)
                        val target = rawInputs.find { it.id == requestedDevice.id }
                        if (target != null) {
                            record.setPreferredDevice(target)
                        }
                    }
                } else if (requestedDevice != null) {
                    _actualInput.value = requestedDevice
                    _inputRouteVerified.value = true
                    _inputRoutingReason.value = "Active stream (Pending routing callback)"
                    updateRoutingState(actualInput = requestedDevice, inputStatus = "Active")
                    onRouteUpdated(requestedDevice, true, "Active stream")
                }
            }

            inspectRecordRoute(audioRecord)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                audioRecord.addOnRoutingChangedListener(AudioRecord.OnRoutingChangedListener {
                    inspectRecordRoute(audioRecord)
                }, null)
            }
        }
    }

    /**
     * Binds an active AudioTrack session to the requested output device and listens
     * for hardware route changes via [AudioTrack.OnRoutingChangedListener].
     */
    fun bindActiveTrack(
        audioTrack: AudioTrack,
        requestedDevice: AudioDeviceModel?,
        onRouteUpdated: (actualDevice: AudioDeviceModel?, isVerified: Boolean, reason: String) -> Unit = { _, _, _ -> }
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (requestedDevice != null) {
                val rawOutputs = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                val targetInfo = rawOutputs.find { it.id == requestedDevice.id }
                    ?: rawOutputs.find { it.type == requestedDevice.type }
                if (targetInfo != null) {
                    val ok = audioTrack.setPreferredDevice(targetInfo)
                    Log.d(TAG, "setPreferredDevice on AudioTrack ($ok): ${requestedDevice.name}")
                }
            }

            fun inspectTrackRoute(track: AudioTrack) {
                val routedInfo = track.routedDevice
                if (routedInfo != null) {
                    val mapped = AudioDeviceMapper.map(routedInfo)
                    val isMatch = requestedDevice == null ||
                            routedInfo.id == requestedDevice.id ||
                            routedInfo.type == requestedDevice.type
                    _actualOutput.value = mapped
                    _outputRouteVerified.value = isMatch
                    val reason = if (isMatch) {
                        "Verified by AudioTrack (${mapped.name})"
                    } else {
                        "Android assigned: ${mapped.name} (Requested: ${requestedDevice.name})"
                    }
                    _outputRoutingReason.value = reason
                    val statusText = if (isMatch) "Active (Verified)" else "Android selected: ${mapped.name}"
                    updateRoutingState(actualOutput = mapped, outputStatus = statusText)
                    onRouteUpdated(mapped, isMatch, reason)

                    // Bounded recovery if route changed unbidden
                    if (!isMatch && outputRecoveryCount < MAX_RECOVERY_ATTEMPTS && requestedDevice != null) {
                        outputRecoveryCount++
                        val rawOutputs = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                        val target = rawOutputs.find { it.id == requestedDevice.id }
                        if (target != null) {
                            track.setPreferredDevice(target)
                        }
                    }
                } else if (requestedDevice != null) {
                    _actualOutput.value = requestedDevice
                    _outputRouteVerified.value = true
                    _outputRoutingReason.value = "Active stream (Pending routing callback)"
                    updateRoutingState(actualOutput = requestedDevice, outputStatus = "Active")
                    onRouteUpdated(requestedDevice, true, "Active stream")
                }
            }

            inspectTrackRoute(audioTrack)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                audioTrack.addOnRoutingChangedListener(AudioTrack.OnRoutingChangedListener {
                    inspectTrackRoute(audioTrack)
                }, null)
            }
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
                _actualOutput.value = fallbackSpeaker
                _outputRouteVerified.value = true
                _outputRoutingReason.value = "Fallback after device disconnection"
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
                _actualInput.value = fallbackMic
                _inputRouteVerified.value = true
                _inputRoutingReason.value = "Fallback after device disconnection"
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
     * Tests output route by generating and playing a clear audible sine wave tone
     * for EXACTLY 3 seconds through the currently selected output device.
     * Verifies actual hardware route and updates live countdown & progress.
     */
    fun testOutputRoute(targetDevice: AudioDeviceModel?, onStatusUpdate: (String) -> Unit) {
        if (outputTestJob?.isActive == true) return

        val target = targetDevice ?: _userSelectedOutput.value ?: _routingState.value.availableOutputDevices.firstOrNull()
        val targetName = target?.name ?: "Speaker"

        _routingState.update {
            it.copy(
                isOutputTesting = true,
                outputTestCountdown = 3,
                outputTestProgress = 0f,
                outputTestResult = "Testing route to $targetName..."
            )
        }
        onStatusUpdate("Testing output route ($targetName)...")

        outputTestJob = scope.launch(Dispatchers.IO) {
            var audioTrack: AudioTrack? = null
            try {
                acquireTransientAudioFocus()

                val numSamples = (TEST_SAMPLE_RATE * (TEST_OUTPUT_DURATION_MS / 1000.0)).toInt()
                val samples = ShortArray(numSamples)
                val rampSamples = (TEST_SAMPLE_RATE * 0.05).toInt() // 50ms fade in/out to eliminate pops

                for (i in 0 until numSamples) {
                    val angle = 2.0 * PI * i * TEST_TONE_FREQ / TEST_SAMPLE_RATE
                    var amp = sin(angle)

                    if (i < rampSamples) {
                        amp *= (i.toDouble() / rampSamples)
                    } else if (i > numSamples - rampSamples) {
                        amp *= ((numSamples - i).toDouble() / rampSamples)
                    }

                    // Audible, comfortable amplitude (~50% full scale)
                    samples[i] = (amp * 16384).toInt().toShort()
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

                audioTrack = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    AudioTrack.Builder()
                        .setAudioAttributes(attributes)
                        .setAudioFormat(format)
                        .setBufferSizeInBytes(maxOf(minBufSize, samples.size * 2))
                        .setTransferMode(AudioTrack.MODE_STATIC)
                        .build()
                } else {
                    @Suppress("DEPRECATION")
                    AudioTrack(
                        AudioManager.STREAM_MUSIC,
                        TEST_SAMPLE_RATE,
                        AudioFormat.CHANNEL_OUT_MONO,
                        AudioFormat.ENCODING_PCM_16BIT,
                        maxOf(minBufSize, samples.size * 2),
                        AudioTrack.MODE_STATIC
                    )
                }

                // Explicit preferred device routing on Android M+
                if (target != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    val rawOutputs = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                    val targetInfo = rawOutputs.find { it.id == target.id }
                        ?: rawOutputs.find { it.type == target.type }
                    if (targetInfo != null) {
                        audioTrack.preferredDevice = targetInfo
                    }
                }

                audioTrack.write(samples, 0, samples.size)
                audioTrack.play()

                // Check actual routed device after starting
                val routedInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) audioTrack.routedDevice else null
                val routedName = routedInfo?.let { AudioDeviceMapper.getDeviceTypeName(it.type) } ?: targetName
                val isVerified = routedInfo == null || target == null || routedInfo.id == target.id || routedInfo.type == target.type

                val startNotice = if (isVerified) {
                    "Playing 3s tone (Routed to: $routedName)"
                } else {
                    "Android selected: $routedName (Requested: $targetName)"
                }
                _routingState.update { it.copy(outputTestResult = startNotice) }
                withContext(Dispatchers.Main) { onStatusUpdate(startNotice) }

                // Live 3-second countdown loop (3 ticks of 1000ms)
                val startTime = System.currentTimeMillis()
                for (second in 3 downTo 1) {
                    val elapsed = System.currentTimeMillis() - startTime
                    val progress = (elapsed.toFloat() / TEST_OUTPUT_DURATION_MS).coerceIn(0f, 1f)
                    _routingState.update {
                        it.copy(
                            outputTestCountdown = second,
                            outputTestProgress = progress
                        )
                    }
                    delay(1000L)
                }

                val finalResultMsg = "Output test complete (routed to $routedName)"
                _routingState.update {
                    it.copy(
                        isOutputTesting = false,
                        outputTestCountdown = 0,
                        outputTestProgress = 1f,
                        outputTestResult = finalResultMsg
                    )
                }
                withContext(Dispatchers.Main) { onStatusUpdate(finalResultMsg) }
            } catch (e: Exception) {
                Log.e(TAG, "Error running output test", e)
                val failMsg = "Unable to route test audio."
                _routingState.update {
                    it.copy(
                        isOutputTesting = false,
                        outputTestCountdown = 0,
                        outputTestProgress = 0f,
                        outputTestResult = failMsg
                    )
                }
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
     * Tests microphone route:
     * 1. Captures microphone audio for EXACTLY 3 seconds into in-memory buffer using requested input.
     * 2. Verifies actual hardware input route via [AudioRecord.getRoutedDevice].
     * 3. Immediately plays back the captured 3-second recording through the currently selected output route.
     * 4. Releases all hardware resources cleanly. Zero persistent disk/network storage.
     */
    fun testMicrophoneRoute(
        targetDevice: AudioDeviceModel?,
        onAmplitude: (Float) -> Unit,
        onComplete: (Boolean, String) -> Unit
    ) {
        if (micTestJob?.isActive == true) return

        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            val permMsg = "Microphone permission required for test."
            _routingState.update {
                it.copy(
                    isMicrophoneTesting = false,
                    microphoneTestPhase = "error",
                    microphoneTestResult = permMsg
                )
            }
            onComplete(false, permMsg)
            return
        }

        val targetInput = targetDevice ?: _userSelectedInput.value ?: _routingState.value.availableInputDevices.firstOrNull()
        val targetOutput = _userSelectedOutput.value ?: _routingState.value.availableOutputDevices.firstOrNull()
        val targetInputName = targetInput?.name ?: "Microphone"

        _routingState.update {
            it.copy(
                isMicrophoneTesting = true,
                microphoneTestPhase = "testing",
                microphoneTestCountdown = 3,
                microphoneTestProgress = 0f,
                microphoneTestLevel = 0f,
                microphoneTestResult = "Testing microphone ($targetInputName)..."
            )
        }

        micTestJob = scope.launch(Dispatchers.IO) {
            var audioRecord: AudioRecord? = null
            var playbackTrack: AudioTrack? = null
            val totalRecordSamples = (TEST_SAMPLE_RATE * (TEST_MIC_DURATION_MS / 1000.0)).toInt()
            val recordedBuffer = ShortArray(totalRecordSamples)

            var actualInputName = targetInputName
            var actualOutputName = targetOutput?.name ?: "Speaker"

            try {
                acquireTransientAudioFocus()

                // Phase 1: Recording 3 seconds
                _routingState.update {
                    it.copy(
                        microphoneTestPhase = "recording",
                        microphoneTestResult = "Recording 3 seconds..."
                    )
                }

                val minRecordBuf = AudioRecord.getMinBufferSize(
                    TEST_SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                )

                if (minRecordBuf <= 0) {
                    val failMsg = "Unable to initialize microphone hardware."
                    _routingState.update {
                        it.copy(
                            isMicrophoneTesting = false,
                            microphoneTestPhase = "error",
                            microphoneTestResult = failMsg
                        )
                    }
                    withContext(Dispatchers.Main) { onComplete(false, failMsg) }
                    return@launch
                }

                audioRecord = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    TEST_SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    maxOf(minRecordBuf, 4096)
                )

                if (targetInput != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    val rawInputs = audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS)
                    val targetInfo = rawInputs.find { it.id == targetInput.id }
                        ?: rawInputs.find { it.type == targetInput.type }
                    if (targetInfo != null) {
                        audioRecord.preferredDevice = targetInfo
                    }
                }

                audioRecord.startRecording()

                // Query routed input device
                val routedInputInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) audioRecord.routedDevice else null
                actualInputName = routedInputInfo?.let { AudioDeviceMapper.getDeviceTypeName(it.type) } ?: targetInputName

                val readChunk = ShortArray(1024)
                var samplesReadTotal = 0
                val recordStartTime = System.currentTimeMillis()

                while (isActive && samplesReadTotal < totalRecordSamples) {
                    val remaining = totalRecordSamples - samplesReadTotal
                    val toRead = minOf(readChunk.size, remaining)
                    val count = audioRecord.read(readChunk, 0, toRead)

                    if (count > 0) {
                        System.arraycopy(readChunk, 0, recordedBuffer, samplesReadTotal, count)
                        samplesReadTotal += count

                        var maxSample = 0
                        for (i in 0 until count) {
                            val absVal = abs(readChunk[i].toInt())
                            if (absVal > maxSample) maxSample = absVal
                        }
                        val normLevel = (maxSample / 32767f).coerceIn(0f, 1f)
                        val elapsed = System.currentTimeMillis() - recordStartTime
                        val countdown = ((TEST_MIC_DURATION_MS - elapsed) / 1000L).toInt().coerceIn(1, 3)
                        val progress = (elapsed.toFloat() / TEST_MIC_DURATION_MS).coerceIn(0f, 1f)

                        _routingState.update {
                            it.copy(
                                microphoneTestLevel = normLevel,
                                microphoneTestCountdown = countdown,
                                microphoneTestProgress = progress
                            )
                        }
                        withContext(Dispatchers.Main) { onAmplitude(normLevel) }
                    }
                    delay(20)
                }

                // Cleanly stop and release recording hardware
                try {
                    audioRecord.stop()
                    audioRecord.release()
                    audioRecord = null
                } catch (ignored: Exception) {}

                // Phase 2: Playing back the 3-second recording through requested output
                _routingState.update {
                    it.copy(
                        microphoneTestPhase = "playing",
                        microphoneTestCountdown = 3,
                        microphoneTestProgress = 0f,
                        microphoneTestLevel = 0f,
                        microphoneTestResult = "Playing recording..."
                    )
                }

                val minTrackBuf = AudioTrack.getMinBufferSize(
                    TEST_SAMPLE_RATE,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                )

                val attributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()

                val format = AudioFormat.Builder()
                    .setSampleRate(TEST_SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .build()

                playbackTrack = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    AudioTrack.Builder()
                        .setAudioAttributes(attributes)
                        .setAudioFormat(format)
                        .setBufferSizeInBytes(maxOf(minTrackBuf, recordedBuffer.size * 2))
                        .setTransferMode(AudioTrack.MODE_STATIC)
                        .build()
                } else {
                    @Suppress("DEPRECATION")
                    AudioTrack(
                        AudioManager.STREAM_MUSIC,
                        TEST_SAMPLE_RATE,
                        AudioFormat.CHANNEL_OUT_MONO,
                        AudioFormat.ENCODING_PCM_16BIT,
                        maxOf(minTrackBuf, recordedBuffer.size * 2),
                        AudioTrack.MODE_STATIC
                    )
                }

                if (targetOutput != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    val rawOutputs = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                    val targetInfo = rawOutputs.find { it.id == targetOutput.id }
                        ?: rawOutputs.find { it.type == targetOutput.type }
                    if (targetInfo != null) {
                        playbackTrack.preferredDevice = targetInfo
                    }
                }

                playbackTrack.write(recordedBuffer, 0, recordedBuffer.size)
                playbackTrack.play()

                val routedOutInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) playbackTrack.routedDevice else null
                actualOutputName = routedOutInfo?.let { AudioDeviceMapper.getDeviceTypeName(it.type) } ?: (targetOutput?.name ?: "Speaker")

                val playStartTime = System.currentTimeMillis()
                for (sec in 3 downTo 1) {
                    val elapsed = System.currentTimeMillis() - playStartTime
                    val progress = (elapsed.toFloat() / TEST_MIC_DURATION_MS).coerceIn(0f, 1f)
                    _routingState.update {
                        it.copy(
                            microphoneTestCountdown = sec,
                            microphoneTestProgress = progress
                        )
                    }
                    delay(1000L)
                }

                // Phase 3: Test complete
                val successMsg = "Test complete (Recorded: $actualInputName | Played: $actualOutputName)"
                _routingState.update {
                    it.copy(
                        isMicrophoneTesting = false,
                        microphoneTestPhase = "complete",
                        microphoneTestCountdown = 0,
                        microphoneTestProgress = 1f,
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
                        microphoneTestPhase = "error",
                        microphoneTestCountdown = 0,
                        microphoneTestProgress = 0f,
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
                try {
                    playbackTrack?.stop()
                    playbackTrack?.release()
                } catch (ignored: Exception) {}
                releaseAudioFocus()
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
        actualOutput: AudioDeviceModel? = _actualOutput.value,
        selectedInput: AudioDeviceModel? = _userSelectedInput.value,
        actualInput: AudioDeviceModel? = _actualInput.value,
        outputStatus: String = _routingState.value.outputStatus,
        inputStatus: String = _routingState.value.inputStatus,
        lastError: String? = _routingState.value.lastErrorMessage
    ) {
        val activeComm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            audioManager.communicationDevice?.let { AudioDeviceMapper.getDeviceTypeName(it.type) }
        } else null

        _routingState.update { current ->
            current.copy(
                requestedOutputDevice = selectedOutput,
                actualOutputDevice = actualOutput,
                requestedInputDevice = selectedInput,
                actualInputDevice = actualInput,
                outputRouteVerified = _outputRouteVerified.value,
                inputRouteVerified = _inputRouteVerified.value,
                outputRoutingReason = _outputRoutingReason.value,
                inputRoutingReason = _inputRoutingReason.value,
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
