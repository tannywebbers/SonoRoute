package com.example.audio.focus

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.util.Log
import com.example.audio.session.SessionType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Focus state representation for UI and diagnostics.
 */
enum class FocusState(val label: String) {
    NONE("No Focus"),
    GAIN("Active (Gain)"),
    GAIN_TRANSIENT("Active (Transient)"),
    GAIN_TRANSIENT_MAY_DUCK("Active (Ducking)"),
    LOSS("Lost"),
    LOSS_TRANSIENT("Lost (Transient)"),
    LOSS_TRANSIENT_CAN_DUCK("Ducked (External Audio)")
}

/**
 * Centralized Audio Focus Manager for SonoRoute (Phase 6).
 * Handles AudioFocus request and release lifecycle according to active session types.
 */
class AudioFocusManager(
    private val context: Context,
    private val onFocusChanged: ((FocusState) -> Unit)? = null
) {
    companion object {
        private const val TAG = "AudioFocusManager"
    }

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var focusRequest: AudioFocusRequest? = null

    private val _currentFocusState = MutableStateFlow(FocusState.NONE)
    val currentFocusState: StateFlow<FocusState> = _currentFocusState.asStateFlow()

    private val focusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        val newState = when (focusChange) {
            AudioManager.AUDIOFOCUS_GAIN -> FocusState.GAIN
            AudioManager.AUDIOFOCUS_LOSS -> FocusState.LOSS
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> FocusState.LOSS_TRANSIENT
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> FocusState.LOSS_TRANSIENT_CAN_DUCK
            else -> FocusState.NONE
        }
        Log.d(TAG, "Audio focus changed: $newState (code: $focusChange)")
        _currentFocusState.value = newState
        onFocusChanged?.invoke(newState)
    }

    /**
     * Requests audio focus appropriate for the given session type.
     */
    fun requestSessionFocus(sessionType: SessionType): Boolean {
        val (usage, contentType, focusGain) = when (sessionType) {
            SessionType.GAMING -> Triple(
                AudioAttributes.USAGE_GAME,
                AudioAttributes.CONTENT_TYPE_SONIFICATION,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
            )
            SessionType.VOICE_CHAT -> Triple(
                AudioAttributes.USAGE_VOICE_COMMUNICATION,
                AudioAttributes.CONTENT_TYPE_SPEECH,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT
            )
            SessionType.RECORDING -> Triple(
                AudioAttributes.USAGE_MEDIA,
                AudioAttributes.CONTENT_TYPE_SPEECH,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT
            )
            SessionType.SCREEN_SHARING -> Triple(
                AudioAttributes.USAGE_MEDIA,
                AudioAttributes.CONTENT_TYPE_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN
            )
            SessionType.MEDIA -> Triple(
                AudioAttributes.USAGE_MEDIA,
                AudioAttributes.CONTENT_TYPE_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN
            )
            SessionType.GENERAL -> Triple(
                AudioAttributes.USAGE_MEDIA,
                AudioAttributes.CONTENT_TYPE_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN
            )
        }

        return requestFocusInternal(usage, contentType, focusGain)
    }

    /**
     * Requests transient audio focus (e.g. for short test tone).
     */
    fun requestTransientFocus(): Boolean {
        return requestFocusInternal(
            usage = AudioAttributes.USAGE_MEDIA,
            contentType = AudioAttributes.CONTENT_TYPE_SONIFICATION,
            focusGain = AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
        )
    }

    private fun requestFocusInternal(usage: Int, contentType: Int, focusGain: Int): Boolean {
        return try {
            val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val playbackAttributes = AudioAttributes.Builder()
                    .setUsage(usage)
                    .setContentType(contentType)
                    .build()

                val request = AudioFocusRequest.Builder(focusGain)
                    .setAudioAttributes(playbackAttributes)
                    .setAcceptsDelayedFocusGain(false)
                    .setOnAudioFocusChangeListener(focusChangeListener)
                    .build()

                focusRequest = request
                audioManager.requestAudioFocus(request)
            } else {
                @Suppress("DEPRECATION")
                audioManager.requestAudioFocus(
                    focusChangeListener,
                    AudioManager.STREAM_MUSIC,
                    focusGain
                )
            }

            val granted = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
            _currentFocusState.value = if (granted) {
                if (focusGain == AudioManager.AUDIOFOCUS_GAIN) FocusState.GAIN else FocusState.GAIN_TRANSIENT
            } else {
                FocusState.NONE
            }
            granted
        } catch (e: Exception) {
            Log.e(TAG, "Error requesting audio focus", e)
            _currentFocusState.value = FocusState.NONE
            false
        }
    }

    /**
     * Releases audio focus immediately.
     */
    fun abandonFocus() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
                focusRequest = null
            } else {
                @Suppress("DEPRECATION")
                audioManager.abandonAudioFocus(focusChangeListener)
            }
            _currentFocusState.value = FocusState.NONE
        } catch (e: Exception) {
            Log.e(TAG, "Error abandoning audio focus", e)
        }
    }
}
