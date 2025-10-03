package com.jprinz.luciddreamprovider.presentation

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.core.app.NotificationCompat
import com.jprinz.luciddreamprovider.R

class VibrationService : Service() {

    private lateinit var vibrator: Vibrator
    private lateinit var alarmManager: AlarmManager
    private var vibrationPendingIntent: PendingIntent? = null

    private var vibrationIntervalMillis = DEFAULT_VIBRATION_INTERVAL_MINUTES * 60 * 1000L
    private var vibrateInSleepMode = false // Neue Zustandsvariable
    private var vibrationPattern = longArrayOf(0, 500, 200, 500)

    companion object {
        const val EXTRA_INTERVAL_MINUTES = "extra_interval_minutes"
        const val EXTRA_VIBRATE_IN_SLEEP_MODE = "extra_vibrate_in_sleep_mode" // Neuer Schlüssel
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "VibrationServiceChannel"
    }

    override fun onCreate() {
        super.onCreate()
        vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager

        // Lade alle Einstellungen aus SharedPreferences beim Erstellen des Service
        val sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedIntervalMinutes = sharedPreferences.getInt(KEY_VIBRATION_INTERVAL_MINUTES, DEFAULT_VIBRATION_INTERVAL_MINUTES)
        vibrationIntervalMillis = savedIntervalMinutes * 60 * 1000L
        vibrateInSleepMode = sharedPreferences.getBoolean(KEY_VIBRATION_IN_SLEEP_MODE_ENABLED, false)

        createNotificationChannel()
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Vibration Service")
            .setContentText("Vibrations are active.")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_REMOTE_MESSAGING
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val editor = sharedPreferences.edit()

        intent?.let {
            // Intervall aktualisieren, falls im Intent vorhanden
            if (it.hasExtra(EXTRA_INTERVAL_MINUTES)) {
                val intervalMinutes = it.getIntExtra(EXTRA_INTERVAL_MINUTES, DEFAULT_VIBRATION_INTERVAL_MINUTES)
                vibrationIntervalMillis = intervalMinutes * 60 * 1000L
                editor.putInt(KEY_VIBRATION_INTERVAL_MINUTES, intervalMinutes)
            }
            // Schlafmodus-Einstellung aktualisieren, falls im Intent vorhanden
            if (it.hasExtra(EXTRA_VIBRATE_IN_SLEEP_MODE)) {
                vibrateInSleepMode = it.getBooleanExtra(EXTRA_VIBRATE_IN_SLEEP_MODE, false)
                editor.putBoolean(KEY_VIBRATION_IN_SLEEP_MODE_ENABLED, vibrateInSleepMode)
            }
        }
        editor.apply() // Alle Änderungen speichern

        if (intent?.action == VibrationReceiver.ACTION_TRIGGER_VIBRATION || intent?.action == null) {
             vibrate()
        }
        scheduleNextVibration()

        return START_STICKY
    }

    private fun vibrate() {
        // Überprüfe den "Nicht stören"-Modus (Schlafmodus)
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val isDndActive = notificationManager.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL &&
                          notificationManager.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_UNKNOWN

        // Vibriere nur, wenn (Schlafmodus-Vibration an ist) ODER (Nicht stören Modus aus ist)
        if (vibrateInSleepMode || !isDndActive) {
            if (vibrator.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createWaveform(vibrationPattern, -1))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(vibrationPattern, -1)
                }
            }
        }
    }

    private fun scheduleNextVibration() {
        val intent = Intent(this, VibrationReceiver::class.java).apply {
            action = VibrationReceiver.ACTION_TRIGGER_VIBRATION
        }

        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        vibrationPendingIntent = PendingIntent.getBroadcast(this, 0, intent, flags)

        val triggerAtMillis = System.currentTimeMillis() + vibrationIntervalMillis

        if (vibrationIntervalMillis <= 0) return

        try {
            when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && alarmManager.canScheduleExactAlarms() -> {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, vibrationPendingIntent!!)
                }
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.M -> {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, vibrationPendingIntent!!)
                }
                else -> {
                    alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAtMillis, vibrationPendingIntent!!)
                }
            }
        } catch (e: SecurityException) {
            stopSelf()
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onDestroy() {
        super.onDestroy()
        vibrator.cancel()
        vibrationPendingIntent?.let {
            alarmManager.cancel(it)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(Service.STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Vibration Service Channel",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }
}

class VibrationReceiver : android.content.BroadcastReceiver() {
    companion object {
        const val ACTION_TRIGGER_VIBRATION = "com.jprinz.luciddreamprovider.TRIGGER_VIBRATION"
    }
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_TRIGGER_VIBRATION) {
            val serviceIntent = Intent(context, VibrationService::class.java)
            serviceIntent.action = ACTION_TRIGGER_VIBRATION
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        }
    }
}
