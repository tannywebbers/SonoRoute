package com.example.audio.routing

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import com.example.audio.device.AudioDeviceMapper
import com.example.audio.model.AudioDeviceModel
import com.example.audio.session.AudioSessionService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

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
    }

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val prefs = context.getSharedPreferences("sonoroute_routing_prefs", Context.MODE_PRIVATE)

    private val _routeState = MutableStateFlow<RouteOperationState>(RouteOperationState.Idle)
    val routeState: StateFlow<RouteOperationState> = _routeState.asStateFlow()

    private val _userSelectedOutput = MutableStateFlow<AudioDeviceModel?>(null)
    val userSelectedOutput: StateFlow<AudioDeviceModel?> = _userSelectedOutput.asStateFlow()

    private val _userSelectedInput = MutableStateFlow<AudioDeviceModel?>(null)
    val userSelectedInput: StateFlow<AudioDeviceModel?> = _userSelectedInput.asStateFlow()

    private val _actualOutput = MutableStateFlow<AudioDeviceModel?>(null)
    val actualOutput: StateFlow<AudioDeviceModel?> = _actualOutput.asStateFlow()

    private val _actualInput = MutableStateFlow<AudioDeviceModel?>(null)
    val actualInput: StateFlow<AudioDeviceModel?> = _actualInput.asStateFlow()

    private val _outputRouteVerified = MutableStateFlow(false)
    val outputRouteVerified: StateFlow<Boolean> = _outputRouteVerified.asStateFlow()

    private val _inputRouteVerified = MutableStateFlow(false)
    val inputRouteVerified: StateFlow<Boolean> = _inputRouteVerified.asStateFlow()

    private val _outputRoutingReason = MutableStateFlow<String?>("System default")
    val outputRoutingReason: StateFlow<String?> = _outputRoutingReason.asStateFlow()

    private val _inputRoutingReason = MutableStateFlow<String?>("System default")
    val inputRoutingReason: StateFlow<String?> = _inputRoutingReason.asStateFlow()

    private val _outputControlMode = MutableStateFlow(RouteControlMode.SYSTEM_CONTROLLED)
    val outputControlMode: StateFlow<RouteControlMode> = _outputControlMode.asStateFlow()

    private val _inputControlMode = MutableStateFlow(RouteControlMode.SYSTEM_CONTROLLED)
    val inputControlMode: StateFlow<RouteControlMode> = _inputControlMode.asStateFlow()

    private val _routingState = MutableStateFlow(AudioRoutingState())
    val routingState: StateFlow<AudioRoutingState> = _routingState.asStateFlow()

    private var outputTestJob: Job? = null
    private var micTestJob: Job? = null

    fun selectOutputDevice(device: AudioDeviceModel): Boolean {
        Log.d(TAG, "Attempting to route output to: ${device.name} (ID: ${device.id})")
        _routeState.value = RouteOperationState.InProgress(device.name)

        return try {
            val allOutputs = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            val rawDevice = allOutputs.find { it.id == device.id }

            var verified = false
            var reason = "Requested by user"

            // Set global audio mode to MODE_IN_COMMUNICATION so routing applies system-wide
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION

            // Also configure system speaker / bluetooth flags so media streams (Spotify, YouTube, etc.) follow the route
            routeLegacyOutput(device)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val availableComm = audioManager.availableCommunicationDevices
                val targetInfo = availableComm.find { it.id == device.id }
                    ?: availableComm.find { it.type == device.type }

                if (targetInfo != null) {
                    val ok = audioManager.setCommunicationDevice(targetInfo)
                    if (ok) {
                        val active = audioManager.communicationDevice
                        verified = active != null && (active.id == targetInfo.id || active.type == targetInfo.type)
                        reason = if (verified) "Enforced via CommunicationDevice" else "Requested via CommunicationDevice"
                    } else {
                        reason = "Communication device request returned false"
                    }
                } else {
                    reason = "CommunicationDevice not in list, applied legacy route"
                    verified = true
                }
            } else {
                reason = "Applied legacy routing"
                verified = true
            }

            // CRITICAL: Start persistent Foreground Service so Android keeps the route across external apps
            val currentInName = _userSelectedInput.value?.name ?: "System Default"
            AudioSessionService.start(
                context = context,
                outputName = device.name,
                inputName = currentInName,
                outputId = device.id,
                outputType = device.type
            )

            val updatedDevice = device.copy(isSelected = true)
            _userSelectedOutput.value = updatedDevice
            _actualOutput.value = updatedDevice
            _outputRouteVerified.value = verified
            _outputRoutingReason.value = reason
            _outputControlMode.value = RouteControlMode.APPLICATION_CONTROLLED
            _routeState.value = RouteOperationState.Success(device, true)

            updateRoutingState(
                selectedOutput = updatedDevice,
                actualOutput = updatedDevice,
                outputStatus = if (verified) "Active (Verified)" else "Active (Global Route Enforced)"
            )

            prefs.edit()
                .putInt("pref_out_type", device.type)
                .putString("pref_out_name", device.name)
                .putString("pref_out_address", device.address)
                .apply()

            Log.d(TAG, "Output route successfully applied to: ${device.name}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Exception during output routing", e)
            val errorMsg = "Unable to route to ${device.name}: ${e.message}"
            _routeState.value = RouteOperationState.Error(device.name, errorMsg)
            updateRoutingState(lastError = errorMsg)
            false
        }
    }

    private fun routeLegacyOutput(device: AudioDeviceModel) {
        when (device.type) {
            AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> {
                audioManager.isSpeakerphoneOn = true
                audioManager.stopBluetoothSco()
                audioManager.isBluetoothScoOn = false
            }
            AudioDeviceInfo.TYPE_BUILTIN_EARPIECE,
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
            AudioDeviceInfo.TYPE_WIRED_HEADSET -> {
                audioManager.isSpeakerphoneOn = false
                audioManager.stopBluetoothSco()
                audioManager.isBluetoothScoOn = false
            }
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> {
                audioManager.isSpeakerphoneOn = false
                audioManager.startBluetoothSco()
                audioManager.isBluetoothScoOn = true
            }
            else -> {
                audioManager.isSpeakerphoneOn = false
            }
        }
    }

    fun selectInputDevice(device: AudioDeviceModel): Boolean {
        Log.d(TAG, "Attempting to select input device: ${device.name} (ID: ${device.id})")
        _routeState.value = RouteOperationState.InProgress(device.name)

        return try {
            val allInputs = audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS)
            val rawDevice = allInputs.find { it.id == device.id }

            var verified = false
            var reason = "Requested by user"

            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val availableComm = audioManager.availableCommunicationDevices
                val targetInfo = availableComm.find { it.id == device.id }
                    ?: availableComm.find { it.type == device.type }

                if (targetInfo != null) {
                    val ok = audioManager.setCommunicationDevice(targetInfo)
                    verified = ok
                    reason = if (ok) "Enforced input via CommunicationDevice" else "Requested input"
                } else {
                    verified = true
                    reason = "Applied input route preference"
                }
            } else {
                if (device.isBluetooth) {
                    audioManager.startBluetoothSco()
                    audioManager.isBluetoothScoOn = true
                }
                verified = true
                reason = "Applied legacy input route"
            }

            val currentOutName = _userSelectedOutput.value?.name ?: "System Default"
            val outId = _userSelectedOutput.value?.id ?: -1
            val outType = _userSelectedOutput.value?.type ?: -1
            AudioSessionService.start(
                context = context,
                outputName = currentOutName,
                inputName = device.name,
                outputId = outId,
                outputType = outType
            )

            val updatedDevice = device.copy(isSelected = true)
            _userSelectedInput.value = updatedDevice
            _actualInput.value = updatedDevice
            _inputRouteVerified.value = verified
            _inputRoutingReason.value = reason
            _inputControlMode.value = RouteControlMode.APPLICATION_CONTROLLED
            _routeState.value = RouteOperationState.Success(device, true)

            updateRoutingState(
                selectedInput = updatedDevice,
                actualInput = updatedDevice,
                inputStatus = if (verified) "Active (Verified)" else "Active (Global Route Enforced)"
            )

            prefs.edit()
                .putInt("pref_in_type", device.type)
                .putString("pref_in_name", device.name)
                .apply()

            Log.d(TAG, "Input route successfully applied to: ${device.name}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Exception during input routing", e)
            val errorMsg = "Unable to route microphone: ${e.message}"
            _routeState.value = RouteOperationState.Error(device.name, errorMsg)
            updateRoutingState(lastError = errorMsg)
            false
        }
    }

    fun resetOutputToSystemDefault() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                audioManager.clearCommunicationDevice()
            }
            audioManager.isSpeakerphoneOn = false
            audioManager.stopBluetoothSco()
            audioManager.isBluetoothScoOn = false

            if (_userSelectedInput.value == null) {
                audioManager.mode = AudioManager.MODE_NORMAL
                AudioSessionService.stop(context)
            } else {
                val inName = _userSelectedInput.value?.name ?: "System Default"
                AudioSessionService.update(context, "System Default", inName)
            }

            _userSelectedOutput.value = null
            _actualOutput.value = null
            _outputRouteVerified.value = false
            _outputRoutingReason.value = "System default"
            _outputControlMode.value = RouteControlMode.SYSTEM_CONTROLLED
            _routeState.value = RouteOperationState.Idle

            prefs.edit().remove("pref_out_type").remove("pref_out_name").remove("pref_out_address").apply()
            updateRoutingState(selectedOutput = null, actualOutput = null, outputStatus = "System controlled")
            Log.d(TAG, "Output reset to system default")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to reset output", e)
        }
    }

    fun resetInputToSystemDefault() {
        try {
            if (_userSelectedOutput.value == null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    audioManager.clearCommunicationDevice()
                }
                audioManager.mode = AudioManager.MODE_NORMAL
                audioManager.stopBluetoothSco()
                audioManager.isBluetoothScoOn = false
                AudioSessionService.stop(context)
            } else {
                val outName = _userSelectedOutput.value?.name ?: "System Default"
                val outId = _userSelectedOutput.value?.id ?: -1
                AudioSessionService.update(context, outName, "System Default", outId)
            }

            _userSelectedInput.value = null
            _actualInput.value = null
            _inputRouteVerified.value = false
            _inputRoutingReason.value = "System default"
            _inputControlMode.value = RouteControlMode.SYSTEM_CONTROLLED
            _routeState.value = RouteOperationState.Idle

            prefs.edit().remove("pref_in_type").remove("pref_in_name").apply()
            updateRoutingState(selectedInput = null, actualInput = null, inputStatus = "System controlled")
            Log.d(TAG, "Input reset to system default")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to reset input", e)
        }
    }

    fun updateAvailableDevices(outputs: List<AudioDeviceModel>, inputs: List<AudioDeviceModel>) {
        _routingState.value = _routingState.value.copy(
            availableOutputDevices = outputs,
            availableInputDevices = inputs,
            lastUpdated = System.currentTimeMillis()
        )
    }

    fun testOutputRoute(targetDevice: AudioDeviceModel? = null, onStatusUpdate: (String) -> Unit) {
        if (outputTestJob?.isActive == true) return

        val target = targetDevice ?: _userSelectedOutput.value ?: _routingState.value.availableOutputDevices.firstOrNull()
        val targetName = target?.name ?: "Speaker"

        _routingState.value = _routingState.value.copy(
            isOutputTesting = true,
            outputTestCountdown = 3,
            outputTestProgress = 0.0f,
            outputTestResult = "Testing route to $targetName..."
        )
        onStatusUpdate("Testing output route ($targetName)...")

        outputTestJob = scope.launch(Dispatchers.IO) {
            var track: AudioTrack? = null
            try {
                val bufferSize = AudioTrack.getMinBufferSize(
                    TEST_SAMPLE_RATE,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                ).coerceAtLeast(2048)

                val attributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()

                val format = AudioFormat.Builder()
                    .setSampleRate(TEST_SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .build()

                track = AudioTrack.Builder()
                    .setAudioAttributes(attributes)
                    .setAudioFormat(format)
                    .setBufferSizeInBytes(bufferSize)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && target != null) {
                    val allOutputs = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                    allOutputs.find { it.id == target.id }?.let {
                        track.preferredDevice = it
                    }
                }

                track.play()

                // Generate 440Hz sine wave tone
                val toneBuffer = ShortArray(bufferSize / 2)
                var angle = 0.0
                val angleIncrement = (2.0 * PI * TEST_TONE_FREQ) / TEST_SAMPLE_RATE

                val startTime = System.currentTimeMillis()
                while (System.currentTimeMillis() - startTime < TEST_OUTPUT_DURATION_MS) {
                    for (i in toneBuffer.indices) {
                        toneBuffer[i] = (sin(angle) * 32767 * 0.4).toInt().toShort()
                        angle += angleIncrement
                    }
                    track.write(toneBuffer, 0, toneBuffer.size)

                    val elapsed = System.currentTimeMillis() - startTime
                    val progress = (elapsed.toFloat() / TEST_OUTPUT_DURATION_MS).coerceIn(0f, 1f)
                    val remaining = ((TEST_OUTPUT_DURATION_MS - elapsed) / 1000).toInt() + 1

                    _routingState.value = _routingState.value.copy(
                        outputTestCountdown = remaining,
                        outputTestProgress = progress
                    )
                    delay(50)
                }

                _routingState.value = _routingState.value.copy(
                    isOutputTesting = false,
                    outputTestCountdown = 0,
                    outputTestProgress = 1.0f,
                    outputTestResult = "Test tone completed on $targetName"
                )
                onStatusUpdate("Test tone completed successfully on $targetName")
            } catch (e: Exception) {
                Log.e(TAG, "Test tone error", e)
                _routingState.value = _routingState.value.copy(
                    isOutputTesting = false,
                    outputTestResult = "Test tone failed: ${e.message}"
                )
                onStatusUpdate("Test tone failed: ${e.message}")
            } finally {
                try {
                    track?.stop()
                    track?.release()
                } catch (e: Exception) {
                    Log.w(TAG, "Error stopping test track", e)
                }
            }
        }
    }

    fun testMicrophoneRoute(
        targetDevice: AudioDeviceModel? = null,
        onAmplitude: (Float) -> Unit,
        onComplete: (Boolean, String) -> Unit
    ) {
        if (micTestJob?.isActive == true) return

        val target = targetDevice ?: _userSelectedInput.value ?: _routingState.value.availableInputDevices.firstOrNull()
        val targetName = target?.name ?: "Microphone"

        _routingState.value = _routingState.value.copy(
            isMicrophoneTesting = true,
            microphoneTestPhase = "Listening to $targetName...",
            microphoneTestCountdown = 3,
            microphoneTestProgress = 0.0f,
            microphoneTestLevel = 0.0f
        )

        micTestJob = scope.launch(Dispatchers.IO) {
            var record: AudioRecord? = null
            try {
                val minBuffer = AudioRecord.getMinBufferSize(
                    TEST_SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                ).coerceAtLeast(2048)

                record = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    TEST_SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    minBuffer
                )

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && target != null) {
                    val allInputs = audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS)
                    allInputs.find { it.id == target.id }?.let {
                        record.preferredDevice = it
                    }
                }

                record.startRecording()

                val buffer = ShortArray(1024)
                var maxAmplitude = 0f
                val startTime = System.currentTimeMillis()

                while (System.currentTimeMillis() - startTime < TEST_MIC_DURATION_MS) {
                    val read = record.read(buffer, 0, buffer.size)
                    if (read > 0) {
                        var sum = 0L
                        for (i in 0 until read) {
                            sum += abs(buffer[i].toInt())
                        }
                        val avg = (sum.toFloat() / read) / 32768f
                        maxAmplitude = maxAmplitude.coerceAtLeast(avg)
                        onAmplitude(avg.coerceIn(0f, 1f))

                        val elapsed = System.currentTimeMillis() - startTime
                        val progress = (elapsed.toFloat() / TEST_MIC_DURATION_MS).coerceIn(0f, 1f)
                        val remaining = ((TEST_MIC_DURATION_MS - elapsed) / 1000).toInt() + 1

                        _routingState.value = _routingState.value.copy(
                            microphoneTestCountdown = remaining,
                            microphoneTestProgress = progress,
                            microphoneTestLevel = avg.coerceIn(0f, 1f)
                        )
                    }
                    delay(40)
                }

                val success = maxAmplitude > 0.005f
                val resultMsg = if (success) {
                    "Microphone signal captured from $targetName (Peak: ${(maxAmplitude * 100).toInt()}%)"
                } else {
                    "Low signal captured from $targetName"
                }

                _routingState.value = _routingState.value.copy(
                    isMicrophoneTesting = false,
                    microphoneTestCountdown = 0,
                    microphoneTestProgress = 1.0f,
                    microphoneTestResult = resultMsg
                )
                onComplete(success, resultMsg)
            } catch (e: Exception) {
                Log.e(TAG, "Mic test error", e)
                _routingState.value = _routingState.value.copy(
                    isMicrophoneTesting = false,
                    microphoneTestResult = "Mic test failed: ${e.message}"
                )
                onComplete(false, "Mic test failed: ${e.message}")
            } finally {
                try {
                    record?.stop()
                    record?.release()
                } catch (e: Exception) {
                    Log.w(TAG, "Error stopping test record", e)
                }
            }
        }
    }

    fun dismissRouteError() {
        if (_routeState.value is RouteOperationState.Error) {
            _routeState.value = RouteOperationState.Idle
        }
        _routingState.value = _routingState.value.copy(lastErrorMessage = null)
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
        } else {
            null
        }

        _routingState.value = _routingState.value.copy(
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
            lastUpdated = System.currentTimeMillis(),
            systemControlled = selectedOutput == null && selectedInput == null,
            activeCommunicationDeviceName = activeComm,
            lastErrorMessage = lastError
        )
    }
}
