package com.example.audio.session

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import com.example.audio.focus.AudioFocusManager
import com.example.audio.model.AudioDeviceModel
import com.example.audio.performance.AudioPerformanceManager
import com.example.audio.performance.AudioPerformanceState
import com.example.audio.routing.AudioRoutingManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.abs

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
    private val eventIdCounter = AtomicLong(1L)
    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    val focusManager = AudioFocusManager(context)

    private val _activeSession = MutableStateFlow(AudioSession())
    val activeSession: StateFlow<AudioSession> = _activeSession.asStateFlow()

    private val _micResourceState = MutableStateFlow(MicrophoneResourceState.AVAILABLE)
    val micResourceState: StateFlow<MicrophoneResourceState> = _micResourceState.asStateFlow()

    private val _outputResourceState = MutableStateFlow(OutputResourceState.AVAILABLE)
    val outputResourceState: StateFlow<OutputResourceState> = _outputResourceState.asStateFlow()

    private val _isMonitoringEnabled = MutableStateFlow(false)
    val isMonitoringEnabled: StateFlow<Boolean> = _isMonitoringEnabled.asStateFlow()

    private val _monitoringLevel = MutableStateFlow(0.8f)
    val monitoringLevel: StateFlow<Float> = _monitoringLevel.asStateFlow()

    private val _monitoringFeedbackWarning = MutableStateFlow<String?>(null)
    val monitoringFeedbackWarning: StateFlow<String?> = _monitoringFeedbackWarning.asStateFlow()

    private val _eventLog = MutableStateFlow<List<SessionEvent>>(emptyList())
    val eventLog: StateFlow<List<SessionEvent>> = _eventLog.asStateFlow()

    private var monitoringJob: Job? = null
    private var monitoringTrack: AudioTrack? = null
    private var monitoringRecord: AudioRecord? = null

    init {
        logEvent("SYSTEM", "Audio session manager initialized")
    }

    fun logEvent(category: String, message: String) {
        val event = SessionEvent(
            id = eventIdCounter.getAndIncrement(),
            timestamp = timeFormat.format(Date()),
            category = category,
            message = message
        )
        val current = _eventLog.value.toMutableList()
        current.add(0, event)
        if (current.size > MAX_EVENT_LOG_SIZE) {
            current.removeAt(current.size - 1)
        }
        _eventLog.value = current
        Log.d(TAG, "[$category] $message")
    }

    fun clearEventLog() {
        _eventLog.value = emptyList()
    }

    fun startSession(sessionType: SessionType) {
        logEvent("SESSION", "Starting ${sessionType.label} session")
        _activeSession.value = _activeSession.value.copy(
            sessionType = sessionType,
            lifecycleState = SessionLifecycleState.STARTING,
            activeSinceMs = System.currentTimeMillis()
        )

        focusManager.requestSessionFocus(sessionType)

        val outName = routingManager.userSelectedOutput.value?.name ?: "System Default"
        val inName = routingManager.userSelectedInput.value?.name ?: "System Default"
        val outId = routingManager.userSelectedOutput.value?.id ?: -1

        AudioSessionService.start(
            context,
            outputName = outName,
            inputName = inName,
            outputId = outId,
            isMonitoring = _isMonitoringEnabled.value
        )

        _activeSession.value = _activeSession.value.copy(
            lifecycleState = SessionLifecycleState.ACTIVE,
            outputDevice = routingManager.userSelectedOutput.value,
            inputDevice = routingManager.userSelectedInput.value
        )
        logEvent("SESSION", "Session active (${sessionType.label})")
    }

    fun stopSession() {
        logEvent("SESSION", "Stopping audio session")
        stopMonitoringInternal()
        focusManager.abandonFocus()
        AudioSessionService.stop(context)

        _activeSession.value = _activeSession.value.copy(
            lifecycleState = SessionLifecycleState.IDLE,
            activeSinceMs = null
        )
        _micResourceState.value = MicrophoneResourceState.AVAILABLE
        _outputResourceState.value = OutputResourceState.AVAILABLE
    }

    fun pauseSession() {
        logEvent("SESSION", "Pausing audio session")
        stopMonitoringInternal()
        _activeSession.value = _activeSession.value.copy(
            lifecycleState = SessionLifecycleState.PAUSED
        )
    }

    fun resumeSession() {
        logEvent("SESSION", "Resuming audio session")
        _activeSession.value = _activeSession.value.copy(
            lifecycleState = SessionLifecycleState.ACTIVE
        )
        if (_isMonitoringEnabled.value) {
            startMonitoringStream()
        }
    }

    fun increaseBuffer() {
        val currentSize = _activeSession.value.bufferSize
        val newSize = currentSize + 64
        _activeSession.value = _activeSession.value.copy(bufferSize = newSize)
        performanceManager.setCustomBufferFrames(newSize)
        logEvent("PERF", "Buffer frames increased to $newSize")
    }

    fun setMicrophoneMonitoring(enabled: Boolean, overrideSpeakerWarning: Boolean = false): Pair<Boolean, String> {
        val isSpeaker = routingManager.userSelectedOutput.value?.let {
            it.isBuiltIn && it.type == android.media.AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
        } ?: false

        if (enabled && isSpeaker && !overrideSpeakerWarning) {
            val warning = "Using live microphone monitoring with Phone Speaker may cause acoustic feedback loop. Connect headphones for optimal experience."
            _monitoringFeedbackWarning.value = warning
            logEvent("MONITOR", "Feedback warning triggered for built-in speaker")
            return Pair(false, warning)
        }

        _monitoringFeedbackWarning.value = null
        _isMonitoringEnabled.value = enabled

        if (enabled) {
            startMonitoringStream()
            logEvent("MONITOR", "Live mic monitoring enabled")
        } else {
            stopMonitoringInternal()
            logEvent("MONITOR", "Live mic monitoring disabled")
        }

        updateSessionNotification()
        return Pair(true, if (enabled) "Monitoring started" else "Monitoring stopped")
    }

    fun setMonitoringLevel(level: Float) {
        val clamped = level.coerceIn(0f, 1f)
        _monitoringLevel.value = clamped
        _activeSession.value = _activeSession.value.copy(monitoringLevel = clamped)
        monitoringTrack?.setVolume(clamped)
    }

    private fun startMonitoringStream() {
        stopMonitoringInternal()

        monitoringJob = scope.launch(Dispatchers.IO) {
            try {
                val sampleRate = 44100
                val minRecBuf = AudioRecord.getMinBufferSize(
                    sampleRate,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                ).coerceAtLeast(1024)

                val minTrackBuf = AudioTrack.getMinBufferSize(
                    sampleRate,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                ).coerceAtLeast(1024)

                val record = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    sampleRate,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    minRecBuf
                )

                val attributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()

                val format = AudioFormat.Builder()
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .build()

                val track = AudioTrack.Builder()
                    .setAudioAttributes(attributes)
                    .setAudioFormat(format)
                    .setBufferSizeInBytes(minTrackBuf)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()

                monitoringRecord = record
                monitoringTrack = track

                track.setVolume(_monitoringLevel.value)
                record.startRecording()
                track.play()

                _micResourceState.value = MicrophoneResourceState.ACTIVE
                _outputResourceState.value = OutputResourceState.ACTIVE

                val buffer = ShortArray(256)
                while (_isMonitoringEnabled.value && record.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    val read = record.read(buffer, 0, buffer.size)
                    if (read > 0) {
                        track.write(buffer, 0, read)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Monitoring stream error", e)
                logEvent("ERROR", "Monitoring error: ${e.message}")
            } finally {
                stopMonitoringInternal()
            }
        }
    }

    private fun stopMonitoringInternal() {
        monitoringJob?.cancel()
        monitoringJob = null

        try {
            monitoringRecord?.let {
                if (it.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    it.stop()
                }
                it.release()
            }
            monitoringRecord = null

            monitoringTrack?.let {
                if (it.playState == AudioTrack.PLAYSTATE_PLAYING) {
                    it.stop()
                }
                it.release()
            }
            monitoringTrack = null
        } catch (e: Exception) {
            Log.w(TAG, "Error cleaning monitoring resources", e)
        }

        _micResourceState.value = MicrophoneResourceState.AVAILABLE
        _outputResourceState.value = OutputResourceState.AVAILABLE
    }

    private fun updateSessionNotification() {
        val outName = routingManager.userSelectedOutput.value?.name ?: "System Default"
        val inName = routingManager.userSelectedInput.value?.name ?: "System Default"
        val outId = routingManager.userSelectedOutput.value?.id ?: -1
        AudioSessionService.update(
            context,
            outputName = outName,
            inputName = inName,
            outputId = outId,
            isMonitoring = _isMonitoringEnabled.value
        )
    }

    fun isAudioPlaybackCaptureSupported(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

    fun getAudioPlaybackCaptureStatus(): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            "Supported (Android 10+ AudioPlaybackCapture API)"
        } else {
            "Not supported on this Android version"
        }
    }

    fun releaseAll() {
        stopSession()
    }
}
