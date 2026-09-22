package com.example.audio.session

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity

/**
 * Foreground Service for active SonoRoute sessions.
 * Ensures uninterrupted audio routing, microphone capture, and monitoring in the background.
 * Provides a persistent notification displaying actual verified hardware routes and quick controls.
 */
class AudioSessionService : Service() {

    companion object {
        private const val TAG = "AudioSessionService"
        const val CHANNEL_ID = "sonoroute_active_session_channel"
        private const val NOTIFICATION_ID = 2001

        const val ACTION_START = "com.example.action.START_SESSION_SERVICE"
        const val ACTION_STOP = "com.example.action.STOP_SESSION_SERVICE"
        const val ACTION_UPDATE = "com.example.action.UPDATE_SESSION_NOTIFICATION"
        const val ACTION_TOGGLE_MONITOR = "com.example.action.TOGGLE_MONITOR"
        const val ACTION_STOP_SESSION = "com.example.action.STOP_SESSION"

        const val EXTRA_OUTPUT_NAME = "extra_output_name"
        const val EXTRA_INPUT_NAME = "extra_input_name"
        const val EXTRA_MONITORING_ON = "extra_monitoring_on"

        fun start(context: Context, outputName: String, inputName: String, isMonitoring: Boolean) {
            val intent = Intent(context, AudioSessionService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_OUTPUT_NAME, outputName)
                putExtra(EXTRA_INPUT_NAME, inputName)
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

        fun update(context: Context, outputName: String, inputName: String, isMonitoring: Boolean) {
            val intent = Intent(context, AudioSessionService::class.java).apply {
                action = ACTION_UPDATE
                putExtra(EXTRA_OUTPUT_NAME, outputName)
                putExtra(EXTRA_INPUT_NAME, inputName)
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

    private var currentOutputName: String = "System Speaker"
    private var currentInputName: String = "System Mic"
    private var isMonitoring: Boolean = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                Log.d(TAG, "Stopping foreground audio session service")
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_START, ACTION_UPDATE -> {
                intent.getStringExtra(EXTRA_OUTPUT_NAME)?.let { currentOutputName = it }
                intent.getStringExtra(EXTRA_INPUT_NAME)?.let { currentInputName = it }
                isMonitoring = intent.getBooleanExtra(EXTRA_MONITORING_ON, false)

                val notification = buildNotification()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(
                        NOTIFICATION_ID,
                        notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                    )
                } else {
                    startForeground(NOTIFICATION_ID, notification)
                }
            }
        }
        return START_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Active Audio Session",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows actual active hardware routes and quick controls"
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        // Tap notification to open app / Control Panel
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("open_control_panel", true)
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        // Action: Output / Input quick open
        val outputActionIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("open_control_panel", true)
            putExtra("target_tab", "output")
        }
        val outputPendingIntent = PendingIntent.getActivity(
            this,
            1,
            outputActionIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        val inputActionIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("open_control_panel", true)
            putExtra("target_tab", "input")
        }
        val inputPendingIntent = PendingIntent.getActivity(
            this,
            2,
            inputActionIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        val monitorActionIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("toggle_monitoring", true)
        }
        val monitorPendingIntent = PendingIntent.getActivity(
            this,
            3,
            monitorActionIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        val stopActionIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("stop_session", true)
        }
        val stopPendingIntent = PendingIntent.getActivity(
            this,
            4,
            stopActionIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        val monitorLabel = if (isMonitoring) "Monitor: ON" else "Monitor: OFF"

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("SonoRoute — Audio Session Active")
            .setContentText("Out: $currentOutputName | In: $currentInputName")
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    "Actual Output: $currentOutputName\nActual Input: $currentInputName\nMonitoring: ${if (isMonitoring) "Active" else "Off"}"
                )
            )
            .setContentIntent(openAppPendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(android.R.drawable.ic_media_play, "Output", outputPendingIntent)
            .addAction(android.R.drawable.ic_btn_speak_now, "Input", inputPendingIntent)
            .addAction(android.R.drawable.ic_menu_manage, monitorLabel, monitorPendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopPendingIntent)
            .build()
    }
}
