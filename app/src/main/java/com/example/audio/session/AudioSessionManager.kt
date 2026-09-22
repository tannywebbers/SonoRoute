package com.example.audio.session

import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.audio.focus.AudioFocusManager
import com.example.audio.focus.FocusState
import com.example.audio.model.AudioDeviceModel
import com.example.audio.model.DeviceCategory
import com.example.audio.performance.AudioPerformanceManager
import com.example.audio.performance.AudioPerformanceState
import com.example.audio.routing.AudioRoutingManager
import com.example.audio.routing.RouteControlMode
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong

/**
 * AudioSessionManager (Phase 6).
 *
 * Central coordinator integrating:
 * - Routing Engine (AudioRoutingManager)
 * - Performance Engine (AudioPerformanceManager)
 * - Central Audio Focus (AudioFocusManager)
 * - Microphone & Output Resource Management
 * - Safe Microphone Monitoring with feedback safeguards
 * - AudioPlaybackCapture capability detection
 * - In-memory Session Event Log with clean reset
 */
class AudioSessionManager(
    private val context: Context,
    private val routingManager: AudioRoutingManager,
    private val performanceManager: AudioPerformanceManager,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default)
) {
    companion object {
        private const val TAG = "AudioSessionManager"
        private const val MAX_EVENT_LOG_SIZE = 40
    }

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val sessionPrefs = context.getSharedPreferences("sonoroute_session_prefs", Context.MODE_PRIVATE)
    private val eventIdCounter = AtomicLong(1L)
    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    val focusManager = AudioFocusManager(context) { focusState ->
        logEvent("FOCUS", "Audio focus transitioned to ${focusState.label}")
    }

    // Active session state
    private val _activeSession = MutableStateFlow(
        AudioSession(
            sessionType = SessionType.GENERAL,
            lifecycleState = SessionLifecycleState.IDLE
        )
    )
    val activeSession: StateFlow<AudioSession> = _activeSession.asStateFlow()

    // Resource ownership states
    private val _micResourceState = MutableStateFlow(MicrophoneResourceState.AVAILABLE)
    val micResourceState: StateFlow<MicrophoneResourceState> = _micResourceState.asStateFlow()

    private val _outputResourceState = MutableStateFlow(OutputResourceState.AVAILABLE)
    val outputResourceState: StateFlow<OutputResourceState> = _outputResourceState.asStateFlow()

    // Monitoring
    private val _isMonitoringEnabled = MutableStateFlow(false)
    val isMonitoringEnabled: StateFlow<Boolean> = _isMonitoringEnabled.asStateFlow()

    private val _monitoringLevel = MutableStateFlow(sessionPrefs.getFloat("monitoring_level", 0.8f))
    val monitoringLevel: StateFlow<Float> = _monitoringLevel.asStateFlow()

    private val _monitoringFeedbackWarning = MutableStateFlow<String?>(null)
    val monitoringFeedbackWarning: StateFlow<String?> = _monitoringFeedbackWarning.asStateFlow()

    // Lightweight circular diagnostics event log
    private val _eventLog = MutableStateFlow<List<SessionEvent>>(emptyList())
    val eventLog: StateFlow<List<SessionEvent>> = _eventLog.asStateFlow()

    private var monitoringJob: Job? = null
    private var monitoringTrack: AudioTrack? = null
    private var monitoringRecord: AudioRecord? = null

    init {
        logEvent("SYSTEM", "Audio session engine initialized")
        evaluateFeedbackWarning(routingManager.userSelectedOutput.value)
    }

    // =========================================================================
    // Event Logging
    // =========================================================================

    fun logEvent(category: String, message: String) {
        val event = SessionEvent(
            id = eventIdCounter.getAndIncrement(),
            timestamp = timeFormat.format(Date()),
            category = category,
            message = message
        )
        _eventLog.update { current ->
            (listOf(event) + current).take(MAX_EVENT_LOG_SIZE)
        }
    }

    /**
     * Clears only the local session diagnostic event history without resetting preferences.
     */
    fun clearEventLog() {
        _eventLog.value = emptyList()
    }

    // =========================================================================
    // Resource Management
    // =========================================================================

    @Synchronized
    fun requestMicrophoneAccess(tag: String): Pair<Boolean, String?> {
        val current = _micResourceState.value
        if (current == MicrophoneResourceState.ACTIVE || current == MicrophoneResourceState.BUSY) {
            return Pair(false, "Microphone is currently in use.")
        }
        _micResourceState.value = MicrophoneResourceState.ACTIVE
        logEvent("HARDWARE", "Microphone access granted to $tag")
        return Pair(true, null)
    }

    @Synchronized
    fun releaseMicrophoneAccess(tag: String) {
        if (_micResourceState.value == MicrophoneResourceState.ACTIVE || _micResourceState.value == MicrophoneResourceState.BUSY) {
            _micResourceState.value = MicrophoneResourceState.AVAILABLE
            logEvent("HARDWARE", "Microphone access released by $tag")
        }
    }

    @Synchronized
    fun requestOutputAccess(tag: String): Pair<Boolean, String?> {
        val current = _outputResourceState.value
        if (current == OutputResourceState.ACTIVE || current == OutputResourceState.BUSY) {
            return Pair(false, "Output audio stream is currently in use.")
        }
        _outputResourceState.value = OutputResourceState.ACTIVE
        logEvent("HARDWARE", "Output stream access granted to $tag")
        return Pair(true, null)
    }

    @Synchronized
    fun releaseOutputAccess(tag: String) {
        if (_outputResourceState.value == OutputResourceState.ACTIVE || _outputResourceState.value == OutputResourceState.BUSY) {
            _outputResourceState.value = OutputResourceState.AVAILABLE
            logEvent("HARDWARE", "Output stream access released by $tag")
        }
    }

    // =========================================================================
    // Session Lifecycle Management
    // =========================================================================

    /**
     * Starts an audio session with the specified logical session type.
     */
    fun startSession(sessionType: SessionType) {
        if (_activeSession.value.lifecycleState == SessionLifecycleState.ACTIVE &&
            _activeSession.value.sessionType == sessionType
        ) {
            return
        }

        logEvent("SESSION", "Starting ${sessionType.title} session...")
        _activeSession.update { it.copy(lifecycleState = SessionLifecycleState.STARTING, sessionType = sessionType) }

        // Request Audio Focus
        val focusGranted = focusManager.requestSessionFocus(sessionType)
        if (!focusGranted) {
            logEvent("FOCUS", "Transient audio focus requested for session")
        }

        // Reconcile performance parameters
        val perfState = performanceManager.performanceState.value
        val (usage, contentType, audioMode) = mapSessionAttributes(sessionType)

        val outputDev = routingManager.userSelectedOutput.value
        val inputDev = routingManager.userSelectedInput.value

        val bufferLatencyMs = ((perfState.actualBufferSizeFrames.toFloat() / perfState.actualSampleRate.toFloat()) * 1000f * 2)

        _activeSession.value = AudioSession(
            sessionType = sessionType,
            outputDevice = outputDev,
            inputDevice = inputDev,
            latencyLevel = perfState.actualLatencyLevel,
            bufferSize = perfState.actualBufferSizeFrames,
            sampleRate = perfState.actualSampleRate,
            channelCount = perfState.actualChannelCount,
            audioFormat = perfState.requestedFormatOption,
            audioUsage = usage,
            audioContentType = contentType,
            audioMode = audioMode,
            lifecycleState = SessionLifecycleState.ACTIVE,
            isRecording = sessionType == SessionType.RECORDING,
            isPlaying = true,
            isCommunication = sessionType == SessionType.VOICE_CHAT,
            isMonitoringEnabled = _isMonitoringEnabled.value,
            monitoringLevel = _monitoringLevel.value,
            estimatedBufferLatencyMs = bufferLatencyMs,
            activeSinceMs = System.currentTimeMillis()
        )

        logEvent("SESSION", "${sessionType.title} session is now ACTIVE (${perfState.actualBufferSizeFrames} frames @ ${perfState.actualSampleRate}Hz)")
        updateSessionServiceNotification()
    }

    /**
     * Stops the active audio session and releases associated resources.
     */
    fun stopSession() {
        if (_activeSession.value.lifecycleState == SessionLifecycleState.IDLE) return

        logEvent("SESSION", "Stopping active audio session...")
        _activeSession.update { it.copy(lifecycleState = SessionLifecycleState.STOPPING) }

        // Stop monitoring if running
        stopMonitoringInternal()

        // Release Audio Focus
        focusManager.abandonFocus()

        AudioSessionService.stop(context)

        _activeSession.value = AudioSession(
            sessionType = _activeSession.value.sessionType,
            lifecycleState = SessionLifecycleState.IDLE
        )
        logEvent("SESSION", "Audio session is now IDLE")
    }

    /**
     * Pauses the active audio session, temporarily releasing hardware capture/playback.
     */
    fun pauseSession() {
        if (_activeSession.value.lifecycleState != SessionLifecycleState.ACTIVE) return

        logEvent("SESSION", "Pausing active audio session...")
        _activeSession.update { it.copy(lifecycleState = SessionLifecycleState.PAUSING) }

        // Pause monitoring if running
        stopMonitoringInternal()

        _activeSession.update { it.copy(lifecycleState = SessionLifecycleState.PAUSED) }
        logEvent("SESSION", "Audio session is now PAUSED")
    }

    /**
     * Resumes a previously paused audio session and restores monitoring if active.
     */
    fun resumeSession() {
        if (_activeSession.value.lifecycleState != SessionLifecycleState.PAUSED) return

        logEvent("SESSION", "Resuming audio session...")
        _activeSession.update { it.copy(lifecycleState = SessionLifecycleState.STARTING) }

        // Re-assert audio focus
        focusManager.requestSessionFocus(_activeSession.value.sessionType)

        _activeSession.update { it.copy(lifecycleState = SessionLifecycleState.ACTIVE) }
        logEvent("SESSION", "Audio session resumed (ACTIVE)")

        if (_isMonitoringEnabled.value) {
            startMonitoringStream()
        }
    }

    /**
     * Quickly increases buffer to the next preset to restore audio stability.
     */
    fun increaseBuffer(): Boolean {
        val success = performanceManager.increaseBufferToNextPreset()
        if (success) {
            val perf = performanceManager.performanceState.value
            reconfigureActiveSession(routingManager.userSelectedOutput.value, routingManager.userSelectedInput.value, perf)
            logEvent("BUFFER", "Buffer increased to ${perf.actualBufferSizeFrames} frames to eliminate underruns")
            _activeSession.update { it.copy(isBufferUnstable = false) }
        }
        return success
    }

    /**
     * Reports audio buffer underruns, flagging instability when underruns >= 2.
     */
    fun notifyBufferUnderrun(underruns: Int) {
        if (underruns > 0) {
            _activeSession.update {
                it.copy(
                    outputUnderruns = underruns,
                    isBufferUnstable = underruns >= 2
                )
            }
        }
    }

    /**
     * Safe reconfiguration when hardware route, buffer, or sample rate changes.
     */
    fun reconfigureActiveSession(
        outputDevice: AudioDeviceModel?,
        inputDevice: AudioDeviceModel?,
        perfState: AudioPerformanceState
    ) {
        evaluateFeedbackWarning(outputDevice)

        if (_activeSession.value.lifecycleState != SessionLifecycleState.ACTIVE) {
            // Just update cached target devices
            _activeSession.update { it.copy(outputDevice = outputDevice, inputDevice = inputDevice) }
            return
        }

        val current = _activeSession.value
        val (usage, contentType, audioMode) = mapSessionAttributes(current.sessionType)
        val bufferLatencyMs = ((perfState.actualBufferSizeFrames.toFloat() / perfState.actualSampleRate.toFloat()) * 1000f * 2)

        _activeSession.value = current.copy(
            outputDevice = outputDevice,
            inputDevice = inputDevice,
            latencyLevel = perfState.actualLatencyLevel,
            bufferSize = perfState.actualBufferSizeFrames,
            sampleRate = perfState.actualSampleRate,
            channelCount = perfState.actualChannelCount,
            audioFormat = perfState.requestedFormatOption,
            audioUsage = usage,
            audioContentType = contentType,
            audioMode = audioMode,
            estimatedBufferLatencyMs = bufferLatencyMs
        )

        logEvent("SESSION", "Session reconfigured: Out=${outputDevice?.name ?: "System"}, In=${inputDevice?.name ?: "System"}, Buf=${perfState.actualBufferSizeFrames}")

        // Restart monitoring if active to pick up new route/buffer parameters cleanly
        if (_isMonitoringEnabled.value) {
            restartMonitoringStream()
        }
    }

    private fun mapSessionAttributes(sessionType: SessionType): Triple<Int, Int, Int> {
        return when (sessionType) {
            SessionType.GAMING -> Triple(
                AudioAttributes.USAGE_GAME,
                AudioAttributes.CONTENT_TYPE_SONIFICATION,
                AudioManager.MODE_NORMAL
            )
            SessionType.VOICE_CHAT -> Triple(
                AudioAttributes.USAGE_VOICE_COMMUNICATION,
                AudioAttributes.CONTENT_TYPE_SPEECH,
                AudioManager.MODE_IN_COMMUNICATION
            )
            SessionType.RECORDING -> Triple(
                AudioAttributes.USAGE_MEDIA,
                AudioAttributes.CONTENT_TYPE_SPEECH,
                AudioManager.MODE_NORMAL
            )
            SessionType.SCREEN_SHARING -> Triple(
                AudioAttributes.USAGE_MEDIA,
                AudioAttributes.CONTENT_TYPE_MUSIC,
                AudioManager.MODE_NORMAL
            )
            SessionType.MEDIA -> Triple(
                AudioAttributes.USAGE_MEDIA,
                AudioAttributes.CONTENT_TYPE_MUSIC,
                AudioManager.MODE_NORMAL
            )
            SessionType.GENERAL -> Triple(
                AudioAttributes.USAGE_MEDIA,
                AudioAttributes.CONTENT_TYPE_MUSIC,
                AudioManager.MODE_NORMAL
            )
        }
    }

    // =========================================================================
    // Microphone Monitoring (Requirements 9 & 10)
    // =========================================================================

    /**
     * Toggles real-time microphone monitoring pass-through with feedback prevention.
     */
    fun setMicrophoneMonitoring(enabled: Boolean, overrideSpeakerWarning: Boolean = false): Pair<Boolean, String?> {
        if (enabled == _isMonitoringEnabled.value) return Pair(true, null)

        if (enabled) {
            // Safety feedback protection: if speaker output is detected, require explicit confirmation
            val isSpeaker = _monitoringFeedbackWarning.value != null
            if (isSpeaker && !overrideSpeakerWarning) {
                return Pair(false, "FEEDBACK_CONFIRMATION_REQUIRED")
            }

            // Check mic permission
            if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                return Pair(false, "Microphone permission required for monitoring.")
            }

            // Acquire mic and output ownership
            val micAccess = requestMicrophoneAccess("Microphone Monitoring")
            if (!micAccess.first) {
                return Pair(false, micAccess.second)
            }
            val outAccess = requestOutputAccess("Microphone Monitoring")
            if (!outAccess.first) {
                releaseMicrophoneAccess("Microphone Monitoring")
                return Pair(false, outAccess.second)
            }

            _isMonitoringEnabled.value = true
            _activeSession.update { it.copy(isMonitoringEnabled = true) }
            val speakerNotice = if (isSpeaker) " (Speaker override confirmed)" else ""
            logEvent("MONITOR", "Microphone monitoring enabled (Level: ${(_monitoringLevel.value * 100).toInt()}%)$speakerNotice")

            startMonitoringStream()
            return Pair(true, null)
        } else {
            _isMonitoringEnabled.value = false
            _activeSession.update { it.copy(isMonitoringEnabled = false) }
            stopMonitoringInternal()
            releaseMicrophoneAccess("Microphone Monitoring")
            releaseOutputAccess("Microphone Monitoring")
            logEvent("MONITOR", "Microphone monitoring stopped")
            return Pair(true, null)
        }
    }

    fun setMonitoringLevel(level: Float) {
        val clamped = level.coerceIn(0.0f, 1.0f)
        _monitoringLevel.value = clamped
        sessionPrefs.edit().putFloat("monitoring_level", clamped).apply()
        _activeSession.update { it.copy(monitoringLevel = clamped) }
        logEvent("MONITOR", "Monitoring volume set to ${(clamped * 100).toInt()}%")
    }

    private fun evaluateFeedbackWarning(outputDevice: AudioDeviceModel?) {
        val isSpeaker = outputDevice == null ||
                outputDevice.category == DeviceCategory.BUILT_IN ||
                outputDevice.name.contains("Speaker", ignoreCase = true)
        _monitoringFeedbackWarning.value = if (isSpeaker) {
            "Use headphones to avoid audio feedback."
        } else {
            null
        }
    }

    private fun startMonitoringStream() {
        monitoringJob?.cancel()
        val perf = performanceManager.performanceState.value
        val sampleRate = if (perf.actualSampleRate > 0) perf.actualSampleRate else 48000
        val targetBufferFrames = if (perf.actualBufferSizeFrames > 0) perf.actualBufferSizeFrames else 256

        monitoringJob = scope.launch(Dispatchers.IO) {
            var audioRecord: AudioRecord? = null
            var audioTrack: AudioTrack? = null

            try {
                val minRecordBuf = AudioRecord.getMinBufferSize(
                    sampleRate,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                )
                val bufferSizeInBytes = maxOf(minRecordBuf, targetBufferFrames * 2 * 2)

                audioRecord = AudioRecord(
                    MediaRecorder.AudioSource.VOICE_RECOGNITION,
                    sampleRate,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufferSizeInBytes
                )

                val minTrackBuf = AudioTrack.getMinBufferSize(
                    sampleRate,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                )
                val trackBufferSizeInBytes = maxOf(minTrackBuf, targetBufferFrames * 2 * 2)

                audioTrack = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    AudioTrack.Builder()
                        .setAudioAttributes(
                            AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_MEDIA)
                                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                                .build()
                        )
                        .setAudioFormat(
                            AudioFormat.Builder()
                                .setSampleRate(sampleRate)
                                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                                .build()
                        )
                        .setBufferSizeInBytes(trackBufferSizeInBytes)
                        .setTransferMode(AudioTrack.MODE_STREAM)
                        .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
                        .build()
                } else {
                    @Suppress("DEPRECATION")
                    AudioTrack(
                        AudioManager.STREAM_MUSIC,
                        sampleRate,
                        AudioFormat.CHANNEL_OUT_MONO,
                        AudioFormat.ENCODING_PCM_16BIT,
                        trackBufferSizeInBytes,
                        AudioTrack.MODE_STREAM
                    )
                }

                monitoringTrack = audioTrack
                monitoringRecord = audioRecord

                if (audioRecord.state != AudioRecord.STATE_INITIALIZED || audioTrack.state != AudioTrack.STATE_INITIALIZED) {
                    logEvent("MONITOR", "Hardware initialization failed for monitoring")
                    return@launch
                }

                // Bind streams to requested hardware devices with active route tracking
                val targetInput = routingManager.userSelectedInput.value
                val targetOutput = routingManager.userSelectedOutput.value

                routingManager.bindActiveRecord(audioRecord, targetInput) { actualDev, isVerified, reason ->
                    logEvent("MONITOR_ROUTE", "Mic: ${actualDev?.name ?: "Default"} (Verified: $isVerified - $reason)")
                    updateSessionServiceNotification()
                }

                routingManager.bindActiveTrack(audioTrack, targetOutput) { actualDev, isVerified, reason ->
                    logEvent("MONITOR_ROUTE", "Output: ${actualDev?.name ?: "Default"} (Verified: $isVerified - $reason)")
                    updateSessionServiceNotification()
                }

                audioRecord.startRecording()
                audioTrack.play()

                updateSessionServiceNotification()

                val audioBuffer = ShortArray(targetBufferFrames.coerceAtLeast(64))
                var loopCount = 0

                while (isActive && _isMonitoringEnabled.value) {
                    val readCount = audioRecord.read(audioBuffer, 0, audioBuffer.size)
                    if (readCount > 0) {
                        val gain = _monitoringLevel.value
                        if (gain < 0.99f) {
                            for (i in 0 until readCount) {
                                audioBuffer[i] = (audioBuffer[i] * gain).toInt().coerceIn(-32768, 32767).toShort()
                            }
                        }
                        audioTrack.write(audioBuffer, 0, readCount)

                        loopCount++
                        if (loopCount % 20 == 0 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                            val underruns = audioTrack.underrunCount
                            if (underruns > 0) {
                                _activeSession.update {
                                    it.copy(
                                        outputUnderruns = underruns,
                                        isBufferUnstable = underruns >= 2
                                    )
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception in monitoring loop", e)
                logEvent("MONITOR", "Monitoring halted: ${e.message}")
            } finally {
                try {
                    audioRecord?.stop()
                    audioRecord?.release()
                } catch (_: Exception) {}
                try {
                    audioTrack?.stop()
                    audioTrack?.release()
                } catch (_: Exception) {}
                monitoringRecord = null
                monitoringTrack = null
            }
        }
    }

    private fun restartMonitoringStream() {
        if (!_isMonitoringEnabled.value) return
        stopMonitoringInternal()
        startMonitoringStream()
    }

    private fun stopMonitoringInternal() {
        monitoringJob?.cancel()
        monitoringJob = null
        try {
            monitoringRecord?.stop()
            monitoringRecord?.release()
        } catch (_: Exception) {}
        monitoringRecord = null
        try {
            monitoringTrack?.stop()
            monitoringTrack?.release()
        } catch (_: Exception) {}
        monitoringTrack = null
    }

    // =========================================================================
    // Capability Diagnostics (Playback Capture, Bluetooth, USB, Wired)
    // =========================================================================

    /**
     * Checks whether Android AudioPlaybackCapture is supported on this device (API 29+).
     */
    fun isAudioPlaybackCaptureSupported(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
    }

    fun getAudioPlaybackCaptureStatus(): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            "Supported (Android 10+ with target application permission)"
        } else {
            "Unavailable (< Android 10)"
        }
    }

    /**
     * Returns Bluetooth codec information where Android exposes it,
     * noting device/codec-dependent latency.
     */
    fun getBluetoothCodecInfo(hasBtConnected: Boolean): String {
        return if (hasBtConnected) {
            "Standard SBC / AAC (Negotiated by system Bluetooth stack; Bluetooth adds device/codec-dependent latency)"
        } else {
            "No Bluetooth device currently connected"
        }
    }

    fun getUsbAudioInfo(hasUsbConnected: Boolean): String {
        return if (hasUsbConnected) {
            "USB Audio connected (Supports PCM 16-bit / 24-bit / Float, 48-192 kHz)"
        } else {
            "No USB audio device currently connected"
        }
    }

    fun getWiredAudioInfo(hasWiredConnected: Boolean): String {
        return if (hasWiredConnected) {
            "Wired analog path detected (Standard 3.5mm / Line I/O)"
        } else {
            "No wired audio device currently connected"
        }
    }

    /**
     * Updates the persistent foreground notification with the latest verified hardware routes.
     */
    fun updateSessionServiceNotification() {
        if (_activeSession.value.lifecycleState == SessionLifecycleState.ACTIVE || _isMonitoringEnabled.value) {
            val outName = routingManager.actualOutput.value?.name
                ?: routingManager.userSelectedOutput.value?.name
                ?: "System Output"
            val inName = routingManager.actualInput.value?.name
                ?: routingManager.userSelectedInput.value?.name
                ?: "System Mic"
            AudioSessionService.start(context, outName, inName, _isMonitoringEnabled.value)
        }
    }

    /**
     * Global cleanup method to ensure all resources are released safely.
     */
    fun releaseAll() {
        stopSession()
        stopMonitoringInternal()
        releaseMicrophoneAccess("Engine Cleanup")
        releaseOutputAccess("Engine Cleanup")
        focusManager.abandonFocus()
    }
}
