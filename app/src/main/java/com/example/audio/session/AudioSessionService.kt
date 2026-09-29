package com.example.audio.session

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import com.aistudio.audiorouter.rkxw.R
import com.example.MainActivity
import java.util.concurrent.Executors

class AudioSessionService : Service() {

    companion object {
        private const val TAG = "AudioSessionService"
        const val CHANNEL_ID = "sonoroute_audio_session_channel"
        const val NOTIFICATION_ID = 2001
        private const val WATCHDOG_INTERVAL_MS = 700L

        const val ACTION_START = "com.example.action.START_SESSION_SERVICE"
        const val ACTION_STOP = "com.example.action.STOP_SESSION_SERVICE"
        const val ACTION_UPDATE = "com.example.action.UPDATE_SESSION_NOTIFICATION"

        const val EXTRA_OUTPUT_NAME = "extra_output_name"
        const val EXTRA_INPUT_NAME = "extra_input_name"
        const val EXTRA_OUTPUT_ID = "extra_output_id"
        const val EXTRA_OUTPUT_TYPE = "extra_output_type"
        const val EXTRA_INPUT_ID = "extra_input_id"
        const val EXTRA_INPUT_TYPE = "extra_input_type"
        const val EXTRA_MONITORING_ON = "extra_monitoring_on"

        fun start(
            context: Context,
            outputName: String,
            inputName: String,
            outputId: Int = -1,
            outputType: Int = -1,
            inputId: Int = -1,
            inputType: Int = -1,
            isMonitoring: Boolean = false
        ) {
            val intent = Intent(context, AudioSessionService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_OUTPUT_NAME, outputName)
                putExtra(EXTRA_INPUT_NAME, inputName)
                putExtra(EXTRA_OUTPUT_ID, outputId)
                putExtra(EXTRA_OUTPUT_TYPE, outputType)
                putExtra(EXTRA_INPUT_ID, inputId)
                putExtra(EXTRA_INPUT_TYPE, inputType)
                putExtra(EXTRA_MONITORING_ON, isMonitoring)
            }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to start AudioSessionService", e)
            }
        }

        fun update(
            context: Context,
            outputName: String,
            inputName: String,
            outputId: Int = -1,
            outputType: Int = -1,
            inputId: Int = -1,
            inputType: Int = -1,
            isMonitoring: Boolean = false
        ) {
            val intent = Intent(context, AudioSessionService::class.java).apply {
                action = ACTION_UPDATE
                putExtra(EXTRA_OUTPUT_NAME, outputName)
                putExtra(EXTRA_INPUT_NAME, inputName)
                putExtra(EXTRA_OUTPUT_ID, outputId)
                putExtra(EXTRA_OUTPUT_TYPE, outputType)
                putExtra(EXTRA_INPUT_ID, inputId)
                putExtra(EXTRA_INPUT_TYPE, inputType)
                putExtra(EXTRA_MONITORING_ON, isMonitoring)
            }
            try {
                context.startService(intent)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to update AudioSessionService", e)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, AudioSessionService::class.java).apply {
                action = ACTION_STOP
            }
            try {
                context.startService(intent)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to stop AudioSessionService", e)
            }
        }
    }

    private var currentOutputName = "System Default"
    private var currentInputName = "System Default"
    private var currentOutputId = -1
    private var currentOutputType = -1
    private var currentInputId = -1
    private var currentInputType = -1
    private var isMonitoring = false

    private lateinit var audioManager: AudioManager
    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private var commListener: AudioManager.OnCommunicationDeviceChangedListener? = null
    private var holder: RouteHolder? = null

    private val watchdogRunnable = object : Runnable {
        override fun run() {
            try {
val routed = currentOutputId != -1 || currentInputId != -1
                if (routed) {
                    val h = holder
                    if (h == null) {
                        createHolder()
                    } else if (!h.isHolding()) {
                        h.restart()
                    }
                    reassertCommunicationRoute()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Route watchdog error", e)
            }
            mainHandler.postDelayed(this, WATCHDOG_INTERVAL_MS)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        createNotificationChannel()
        mainHandler.postDelayed(watchdogRunnable, WATCHDOG_INTERVAL_MS)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val listener = AudioManager.OnCommunicationDeviceChangedListener { device ->
                Log.d(TAG, "Communication device changed: ${device?.id} (${device?.productName})")
                // Re-assert desired communication device if route was altered by external event
                if (currentOutputId != -1 && (device == null || (device.id != currentOutputId && device.type != currentOutputType))) {
                    reassertCommunicationRoute()
                }
            }
            commListener = listener
            try {
                audioManager.addOnCommunicationDeviceChangedListener(executor, listener)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to addOnCommunicationDeviceChangedListener", e)
            }
        }
    }

    private fun createHolder() {
        holder = RouteHolder(audioManager).also {
            it.configure(currentInputId, currentOutputId)
            it.start()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == ACTION_STOP) {
            Log.d(TAG, "Stopping foreground audio session service")
            cleanupRouting()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        if (action == ACTION_START || action == ACTION_UPDATE) {
            intent?.getStringExtra(EXTRA_OUTPUT_NAME)?.let { currentOutputName = it }
            intent?.getStringExtra(EXTRA_INPUT_NAME)?.let { currentInputName = it }
            currentOutputId = intent?.getIntExtra(EXTRA_OUTPUT_ID, -1) ?: -1
            currentOutputType = intent?.getIntExtra(EXTRA_OUTPUT_TYPE, -1) ?: -1
            currentInputId = intent?.getIntExtra(EXTRA_INPUT_ID, -1) ?: -1
            currentInputType = intent?.getIntExtra(EXTRA_INPUT_TYPE, -1) ?: -1
            isMonitoring = intent?.getBooleanExtra(EXTRA_MONITORING_ON, false) ?: false
        } else if (intent == null) {
            // START_STICKY restart: restore last desired route from persisted prefs
            Log.d(TAG, "Service restarted with null intent, restoring desired route from prefs")
            restoreDesiredRouteFromPrefs()
        }

        if (currentOutputId != -1 || currentInputId != -1) {
            val h = holder
            if (h == null) {
                createHolder()
            } else {
                h.configure(currentInputId, currentOutputId)
                h.start()
            }
        }

        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                var serviceType = ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    serviceType = serviceType or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                }
                startForeground(NOTIFICATION_ID, notification, serviceType)
            } catch (e: Exception) {
                try {
                    startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
                } catch (e2: Exception) {
                    startForeground(NOTIFICATION_ID, notification)
                }
            }
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        reassertCommunicationRoute()
        return START_STICKY
    }

    private fun restoreDesiredRouteFromPrefs() {
        try {
            val prefs = getSharedPreferences("sonoroute_routing_prefs", Context.MODE_PRIVATE)
            if (prefs.contains("pref_out_type") && prefs.contains("pref_out_name")) {
                currentOutputId = prefs.getInt("pref_out_id", -1)
                currentOutputType = prefs.getInt("pref_out_type", -1)
                currentOutputName = prefs.getString("pref_out_name", "System Default") ?: "System Default"
            }
            if (prefs.contains("pref_in_type") && prefs.contains("pref_in_name")) {
                currentInputId = prefs.getInt("pref_in_id", -1)
                currentInputType = prefs.getInt("pref_in_type", -1)
                currentInputName = prefs.getString("pref_in_name", "System Default") ?: "System Default"
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to restore desired route from prefs", e)
        }
    }

    private fun reassertCommunicationRoute() {
        try {
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION

            // Configure hardware routing for media & external apps
            when (currentOutputType) {
                AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> {
                    audioManager.isSpeakerphoneOn = true
                    audioManager.stopBluetoothSco()
                    audioManager.isBluetoothScoOn = false
                }
                AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
                AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> {
                    audioManager.isSpeakerphoneOn = false
                    audioManager.startBluetoothSco()
                    audioManager.isBluetoothScoOn = true
                }
                AudioDeviceInfo.TYPE_BUILTIN_EARPIECE,
                AudioDeviceInfo.TYPE_WIRED_HEADSET,
                AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> {
                    audioManager.isSpeakerphoneOn = false
                    audioManager.stopBluetoothSco()
                    audioManager.isBluetoothScoOn = false
                }
            }

            // Android 12+ CommunicationDevice routing: prefer the routed output (sink),
            // fall back to the routed input when no output is requested.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                (currentOutputId != -1 || currentInputId != -1)
            ) {
                val available = audioManager.availableCommunicationDevices
                val desiredId = if (currentOutputId != -1) currentOutputId else currentInputId
                val desiredType = if (currentOutputId != -1) currentOutputType else currentInputType
                val target = available.find { it.id == desiredId }
                    ?: available.find { it.type == desiredType }
                if (target != null) {
                    val active = audioManager.communicationDevice
                    val matched = active != null && (active.id == target.id || active.type == target.type)
                    if (!matched) {
                        val ok = audioManager.setCommunicationDevice(target)
                        Log.d(TAG, "Service reasserted communication device: ${target.id} (${target.productName}) result=$ok")
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error in reassertCommunicationRoute", e)
        }
    }

    private fun cleanupRouting() {
        try {
            mainHandler.removeCallbacks(watchdogRunnable)
            try {
                holder?.stop()
            } catch (e: Exception) {
                Log.w(TAG, "Error stopping route holder", e)
            }
            holder = null
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                audioManager.clearCommunicationDevice()
                commListener?.let {
                    try {
                        audioManager.removeOnCommunicationDeviceChangedListener(it)
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to remove listener", e)
                    }
                }
            }
            audioManager.isSpeakerphoneOn = false
            audioManager.stopBluetoothSco()
            audioManager.isBluetoothScoOn = false
            audioManager.mode = AudioManager.MODE_NORMAL
        } catch (e: Exception) {
            Log.w(TAG, "Error in cleanupRouting", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cleanupRouting()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Active Audio Session",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Maintains persistent system-wide audio routing across all external apps"
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("open_control_panel", true)
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopActionIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("stop_session", true)
        }
        val stopPendingIntent = PendingIntent.getActivity(
            this,
            4,
            stopActionIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val contentText = "Out: $currentOutputName | In: $currentInputName"
        val bigText = "Global Route Enforced\nOutput: $currentOutputName\nInput: $currentInputName\nAudio active across all external applications."

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_call_answer)
            .setContentTitle("SonoRoute — Global Audio Route Active")
            .setContentText(contentText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
            .setContentIntent(openAppPendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(R.drawable.ic_call_decline, "Reset to Default", stopPendingIntent)
            .build()
    }

    /**
     * Holds the OS audio route by keeping quiet, always-active streams pinned to the
     * chosen devices via preferredDevice. The system will not revert a route that an
     * app is actively using, so the route stays forced instead of drifting back.
     */
    private class RouteHolder(private val audioManager: AudioManager) {
        private val sampleRate = 48000
        private val encoding = AudioFormat.ENCODING_PCM_16BIT

        @Volatile
        private var inputId: Int = -1

        @Volatile
        private var outputId: Int = -1

        @Volatile
        private var running = false

        private var record: AudioRecord? = null
        private var track: AudioTrack? = null
        private var recordThread: Thread? = null
        private var trackThread: Thread? = null

        @Synchronized
        fun configure(inputId: Int, outputId: Int) {
            this.inputId = inputId
            this.outputId = outputId
            if (running) restart()
        }

        @Synchronized
        fun start() {
            if (running) return
            if (inputId == -1 && outputId == -1) return
            running = true
            openStreams()
        }

        @Synchronized
        fun restart() {
            stopStreams()
            if (running) openStreams()
        }

        @Synchronized
        fun stop() {
            running = false
            stopStreams()
        }

        @Synchronized
        fun isHolding(): Boolean {
            if (!running) return false
            val wantsInput = inputId != -1
            val wantsOutput = outputId != -1
            val inputAlive = recordThread?.isAlive == true
            val outputAlive = trackThread?.isAlive == true
            return when {
                wantsInput && wantsOutput -> inputAlive && outputAlive
                wantsInput -> inputAlive
                wantsOutput -> outputAlive
                else -> false
            }
        }

        private fun openStreams() {
            val channelIn = AudioFormat.CHANNEL_IN_MONO
            val channelOut = AudioFormat.CHANNEL_OUT_MONO

            if (inputId != -1) {
                try {
                    val inputInfo = audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS)
                        .find { it.id == inputId }
                    if (inputInfo != null) {
                        val source = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            MediaRecorder.AudioSource.VOICE_COMMUNICATION
                        } else {
                            MediaRecorder.AudioSource.MIC
                        }
                        val minBuf = AudioRecord.getMinBufferSize(sampleRate, channelIn, encoding)
                            .coerceAtLeast(2048)
                        val rec = AudioRecord(source, sampleRate, channelIn, encoding, minBuf)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            rec.preferredDevice = inputInfo
                        }
                        record = rec
                        recordThread = Thread({
                            pumpInput(rec)
                        }, "sonoroute-hold-input").apply {
                            isDaemon = true
                            start()
                        }
                        Log.d(TAG, "Holding input route on device $inputId")
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to open hold input stream", e)
                }
            }

            if (outputId != -1) {
                try {
                    val outputInfo = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                        .find { it.id == outputId }
                    if (outputInfo != null) {
                        val attributes = AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                        val format = AudioFormat.Builder()
                            .setSampleRate(sampleRate)
                            .setChannelMask(channelOut)
                            .setEncoding(encoding)
                            .build()
                        val minBuf = AudioTrack.getMinBufferSize(sampleRate, channelOut, encoding)
                            .coerceAtLeast(2048)
                        val tr = AudioTrack.Builder()
                            .setAudioAttributes(attributes)
                            .setAudioFormat(format)
                            .setBufferSizeInBytes(minBuf)
                            .setTransferMode(AudioTrack.MODE_STREAM)
                            .build()
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            tr.preferredDevice = outputInfo
                        }
                        track = tr
                        trackThread = Thread({
                            pumpOutputSilence(tr)
                        }, "sonoroute-hold-output").apply {
                            isDaemon = true
                            start()
                        }
                        Log.d(TAG, "Holding output route on device $outputId")
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to open hold output stream", e)
                }
            }
        }

        private fun pumpInput(rec: AudioRecord) {
            val buffer = ShortArray(512)
            try {
                if (rec.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                    rec.startRecording()
                }
                while (running && rec.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    rec.read(buffer, 0, buffer.size)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Hold input pump stopped", e)
            } finally {
                try {
                    if (rec.recordingState == AudioRecord.RECORDSTATE_RECORDING) rec.stop()
                } catch (e: Exception) {
                    Log.w(TAG, "Hold input stop failed", e)
                }
            }
        }

        private fun pumpOutputSilence(tr: AudioTrack) {
            val silence = ShortArray(1024)
            try {
                tr.play()
                while (running && tr.playState == AudioTrack.PLAYSTATE_PLAYING) {
                    tr.write(silence, 0, silence.size)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Hold output pump stopped", e)
            } finally {
                try {
                    tr.stop()
                } catch (e: Exception) {
                    Log.w(TAG, "Hold output stop failed", e)
                }
            }
        }

        private fun stopStreams() {
            recordThread?.interrupt()
            recordThread = null
            trackThread?.interrupt()
            trackThread = null
            try {
                record?.stop()
            } catch (e: Exception) {
                Log.w(TAG, "Hold input stop failed", e)
            }
            try {
                record?.release()
            } catch (e: Exception) {
                Log.w(TAG, "Hold input release failed", e)
            }
            record = null
            try {
                track?.stop()
            } catch (e: Exception) {
                Log.w(TAG, "Hold output stop failed", e)
            }
            try {
                track?.release()
            } catch (e: Exception) {
                Log.w(TAG, "Hold output release failed", e)
            }
            track = null
        }
    }
}
