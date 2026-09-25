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
        _currentFocusState.value = newState
        onFocusChanged?.invoke(newState)
        Log.d(TAG, "Audio focus changed: $newState")
    }

    fun requestSessionFocus(sessionType: SessionType): Boolean {
        val usage = when (sessionType) {
            SessionType.VOICE_CHAT, SessionType.GENERAL -> AudioAttributes.USAGE_VOICE_COMMUNICATION
            SessionType.MEDIA -> AudioAttributes.USAGE_MEDIA
            SessionType.GAMING -> AudioAttributes.USAGE_GAME
            SessionType.RECORDING, SessionType.SCREEN_SHARING -> AudioAttributes.USAGE_MEDIA
        }
        val contentType = when (sessionType) {
            SessionType.VOICE_CHAT -> AudioAttributes.CONTENT_TYPE_SPEECH
            SessionType.MEDIA -> AudioAttributes.CONTENT_TYPE_MUSIC
            SessionType.GAMING -> AudioAttributes.CONTENT_TYPE_SONIFICATION
            SessionType.RECORDING, SessionType.SCREEN_SHARING, SessionType.GENERAL -> AudioAttributes.CONTENT_TYPE_SPEECH
        }
        return requestFocusInternal(usage, contentType, AudioManager.AUDIOFOCUS_GAIN)
    }

    fun requestTransientFocus(): Boolean {
        return requestFocusInternal(
            AudioAttributes.USAGE_VOICE_COMMUNICATION,
            AudioAttributes.CONTENT_TYPE_SPEECH,
            AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
        )
    }

    private fun requestFocusInternal(usage: Int, contentType: Int, focusGain: Int): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val attributes = AudioAttributes.Builder()
                    .setUsage(usage)
                    .setContentType(contentType)
                    .build()
                val request = AudioFocusRequest.Builder(focusGain)
                    .setAudioAttributes(attributes)
                    .setOnAudioFocusChangeListener(focusChangeListener)
                    .setAcceptsDelayedFocusGain(true)
                    .setWillPauseWhenDucked(false)
                    .build()
                focusRequest = request
                val result = audioManager.requestAudioFocus(request)
                val granted = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
                if (granted) {
                    _currentFocusState.value = FocusState.GAIN
                }
                granted
            } else {
                @Suppress("DEPRECATION")
                val result = audioManager.requestAudioFocus(focusChangeListener, AudioManager.STREAM_VOICE_CALL, focusGain)
                val granted = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
                if (granted) {
                    _currentFocusState.value = FocusState.GAIN
                }
                granted
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error requesting audio focus", e)
            false
        }
    }

    fun abandonFocus() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
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
