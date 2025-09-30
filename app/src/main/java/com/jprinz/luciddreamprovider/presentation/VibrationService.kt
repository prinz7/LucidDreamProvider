package com.jprinz.luciddreamprovider.presentation

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo // Import für ServiceInfo hinzugefügt
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
    private var vibrationPattern = longArrayOf(0, 500, 200, 500) // Default pattern: Off, 500ms On, 200ms Off, 500ms On

    companion object {
        const val EXTRA_INTERVAL_MINUTES = "extra_interval_minutes"
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "VibrationServiceChannel"
    }

    override fun onCreate() {
        super.onCreate()
        vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager

        val sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedIntervalMinutes = sharedPreferences.getInt(KEY_VIBRATION_INTERVAL_MINUTES, DEFAULT_VIBRATION_INTERVAL_MINUTES)
        vibrationIntervalMillis = savedIntervalMinutes * 60 * 1000L

        createNotificationChannel()
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Vibration Service")
            .setContentText("Vibrations are active.")
            .setSmallIcon(R.mipmap.ic_launcher) // Stellen Sie sicher, dass R.mipmap.ic_launcher existiert
            .setOngoing(true) // Wichtig für Vordergrunddienste
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
        intent?.let {
            val intervalMinutes = it.getIntExtra(EXTRA_INTERVAL_MINUTES, -1)
            if (intervalMinutes != -1) {
                vibrationIntervalMillis = intervalMinutes * 60 * 1000L
                val sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                sharedPreferences.edit().putInt(KEY_VIBRATION_INTERVAL_MINUTES, intervalMinutes).apply()
            }
        }
        
        // Fix: Nur vibrieren, wenn die Aktion vom Receiver kommt ODER wenn es der erste Start ist (intent action nicht gesetzt)
        // Dies verhindert eine doppelte Vibration beim Start aus der Activity, wenn der Alarm direkt danach auch feuert.
        if (intent?.action == VibrationReceiver.ACTION_TRIGGER_VIBRATION || intent?.action == null) {
             vibrate()
        }
        scheduleNextVibration()

        return START_STICKY
    }

    private fun vibrate() {
        if (vibrator.hasVibrator()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createWaveform(vibrationPattern, -1))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(vibrationPattern, -1)
            }
        }
    }

    private fun scheduleNextVibration() {
        val intent = Intent(this, VibrationReceiver::class.java).apply {
            action = VibrationReceiver.ACTION_TRIGGER_VIBRATION
        }

        // Sicherstellen, dass FLAG_IMMUTABLE für PendingIntent verwendet wird
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
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT -> {
                    alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAtMillis, vibrationPendingIntent!!)
                }
                else -> {
                    alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAtMillis, vibrationPendingIntent!!)
                }
            }
        } catch (e: SecurityException) {
            // Loggen oder behandeln Sie den Fehler, wenn keine Berechtigung für exakte Alarme vorhanden ist
            // Dies sollte nicht passieren, wenn die UI die Berechtigung prüft, aber als Sicherheitsnetz.
            stopSelf() // Stoppt den Dienst, wenn keine Alarme geplant werden können.
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
        // stopForeground mit true oder STOP_FOREGROUND_REMOVE, je nach API-Level
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
            // Der Service sollte selbst entscheiden, ob er vibriert und den nächsten Alarm plant,
            // basierend auf dem Intent, mit dem er gestartet wird (in onStartCommand).
            // Hier übergeben wir die Aktion, damit onStartCommand entsprechend reagieren kann.
            serviceIntent.action = ACTION_TRIGGER_VIBRATION 
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        }
    }
}
