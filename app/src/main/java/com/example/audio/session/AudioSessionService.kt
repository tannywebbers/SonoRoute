package com.example.audio.session

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.IBinder
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

        const val ACTION_START = "com.example.action.START_SESSION_SERVICE"
        const val ACTION_STOP = "com.example.action.STOP_SESSION_SERVICE"
        const val ACTION_UPDATE = "com.example.action.UPDATE_SESSION_NOTIFICATION"

        const val EXTRA_OUTPUT_NAME = "extra_output_name"
        const val EXTRA_INPUT_NAME = "extra_input_name"
        const val EXTRA_OUTPUT_ID = "extra_output_id"
        const val EXTRA_OUTPUT_TYPE = "extra_output_type"
        const val EXTRA_MONITORING_ON = "extra_monitoring_on"

        fun start(
            context: Context,
            outputName: String,
            inputName: String,
            outputId: Int = -1,
            outputType: Int = -1,
            isMonitoring: Boolean = false
        ) {
            val intent = Intent(context, AudioSessionService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_OUTPUT_NAME, outputName)
                putExtra(EXTRA_INPUT_NAME, inputName)
                putExtra(EXTRA_OUTPUT_ID, outputId)
                putExtra(EXTRA_OUTPUT_TYPE, outputType)
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
            isMonitoring: Boolean = false
        ) {
            val intent = Intent(context, AudioSessionService::class.java).apply {
                action = ACTION_UPDATE
                putExtra(EXTRA_OUTPUT_NAME, outputName)
                putExtra(EXTRA_INPUT_NAME, inputName)
                putExtra(EXTRA_OUTPUT_ID, outputId)
                putExtra(EXTRA_OUTPUT_TYPE, outputType)
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
    private var isMonitoring = false

    private lateinit var audioManager: AudioManager
    private val executor = Executors.newSingleThreadExecutor()
    private var commListener: AudioManager.OnCommunicationDeviceChangedListener? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        createNotificationChannel()

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

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == ACTION_STOP) {
            Log.d(TAG, "Stopping foreground audio session service")
            cleanupRouting()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        intent?.getStringExtra(EXTRA_OUTPUT_NAME)?.let { currentOutputName = it }
        intent?.getStringExtra(EXTRA_INPUT_NAME)?.let { currentInputName = it }
        val outId = intent?.getIntExtra(EXTRA_OUTPUT_ID, -1) ?: -1
        if (outId != -1) {
            currentOutputId = outId
        }
        val outType = intent?.getIntExtra(EXTRA_OUTPUT_TYPE, -1) ?: -1
        if (outType != -1) {
            currentOutputType = outType
        }
        isMonitoring = intent?.getBooleanExtra(EXTRA_MONITORING_ON, false) ?: false

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

            // Android 12+ CommunicationDevice routing
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && currentOutputId != -1) {
                val available = audioManager.availableCommunicationDevices
                val target = available.find { it.id == currentOutputId }
                    ?: available.find { it.type == currentOutputType }
                if (target != null) {
                    val ok = audioManager.setCommunicationDevice(target)
                    Log.d(TAG, "Service reasserted communication device: ${target.id} (${target.productName}) result=$ok")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error in reassertCommunicationRoute", e)
        }
    }

    private fun cleanupRouting() {
        try {
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
}
