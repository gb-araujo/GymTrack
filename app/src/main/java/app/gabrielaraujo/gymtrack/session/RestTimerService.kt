package app.gabrielaraujo.gymtrack.session

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import app.gabrielaraujo.gymtrack.MainActivity
import app.gabrielaraujo.gymtrack.R

/**
 * Timer de descanso em foreground service: continua contando com a tela bloqueada ou o app
 * em segundo plano e, ao terminar, vibra e mostra uma notificação com som.
 * Segura um wake lock parcial só durante o descanso para o aviso não atrasar com o aparelho dormindo.
 */
class RestTimerService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private var wakeLock: PowerManager.WakeLock? = null
    private val finish = Runnable { onFinished() }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val endsAt = intent.getLongExtra(EXTRA_ENDS_AT, 0L)
                val name = intent.getStringExtra(EXTRA_EXERCISE).orEmpty()
                start(endsAt, name)
            }
            else -> stop()
        }
        return START_NOT_STICKY
    }

    private fun start(endsAt: Long, exerciseName: String) {
        ensureChannels(this)
        NotificationManagerCompat.from(this).cancel(NOTIFICATION_DONE)
        val notification = NotificationCompat.Builder(this, CHANNEL_RUNNING)
            .setSmallIcon(R.drawable.ic_stat_timer)
            .setContentTitle("Descanso")
            .setContentText(if (exerciseName.isBlank()) "Próxima série em breve" else "Próxima série: $exerciseName")
            .setWhen(endsAt)
            .setShowWhen(true)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
            .setContentIntent(openAppIntent(this))
            .build()
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else 0
        ServiceCompat.startForeground(this, NOTIFICATION_RUNNING, notification, type)

        val delay = (endsAt - System.currentTimeMillis()).coerceAtLeast(0)
        handler.removeCallbacks(finish)
        acquireWakeLock(delay + 10_000)
        handler.postDelayed(finish, delay)
    }

    private fun onFinished() {
        vibrate()
        if (NotificationManagerCompat.from(this).areNotificationsEnabled()) {
            val done = NotificationCompat.Builder(this, CHANNEL_DONE)
                .setSmallIcon(R.drawable.ic_stat_timer)
                .setContentTitle("Descanso finalizado")
                .setContentText("Bora para a próxima série! 💪")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setAutoCancel(true)
                .setTimeoutAfter(60_000)
                .setContentIntent(openAppIntent(this))
                .build()
            try {
                NotificationManagerCompat.from(this).notify(NOTIFICATION_DONE, done)
            } catch (_: SecurityException) {
                // Permissão de notificação negada: a vibração já avisou.
            }
        }
        stop()
    }

    private fun stop() {
        handler.removeCallbacks(finish)
        releaseWakeLock()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        handler.removeCallbacks(finish)
        releaseWakeLock()
        super.onDestroy()
    }

    private fun acquireWakeLock(timeoutMs: Long) {
        releaseWakeLock()
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "RepStack:rest").apply {
            setReferenceCounted(false)
            acquire(timeoutMs)
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }

    private fun vibrate() {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        if (!vibrator.hasVibrator()) return
        vibrator.vibrate(VibrationEffect.createWaveform(VIBRATION_PATTERN, -1))
    }

    companion object {
        private const val ACTION_START = "app.gabrielaraujo.gymtrack.rest.START"
        private const val ACTION_STOP = "app.gabrielaraujo.gymtrack.rest.STOP"
        private const val EXTRA_ENDS_AT = "endsAt"
        private const val EXTRA_EXERCISE = "exercise"
        private const val CHANNEL_RUNNING = "rest_timer_running"
        private const val CHANNEL_DONE = "rest_timer_done_v1"
        private const val NOTIFICATION_RUNNING = 1001
        private const val NOTIFICATION_DONE = 1002
        private val VIBRATION_PATTERN = longArrayOf(0, 400, 200, 400, 200, 600)

        fun start(context: Context, endsAt: Long, exerciseName: String) {
            val intent = Intent(context, RestTimerService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_ENDS_AT, endsAt)
                .putExtra(EXTRA_EXERCISE, exerciseName)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, RestTimerService::class.java).setAction(ACTION_STOP)
            try {
                // Enfileira depois de um START pendente: o serviço sempre chega a chamar startForeground
                // antes de parar (parar antes disso derruba o app no Android 12+).
                context.startService(intent)
            } catch (_: IllegalStateException) {
                // App em segundo plano não pode chamar startService.
                context.stopService(intent)
            }
            NotificationManagerCompat.from(context).cancel(NOTIFICATION_RUNNING)
        }

        private fun openAppIntent(context: Context): PendingIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        private fun ensureChannels(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val nm = context.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_RUNNING, "Timer de descanso", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Contagem regressiva do descanso entre séries"
                    setShowBadge(false)
                },
            )
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_DONE, "Fim do descanso", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Aviso quando o descanso termina"
                    enableVibration(true)
                    vibrationPattern = VIBRATION_PATTERN
                    setSound(
                        RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build(),
                    )
                },
            )
        }
    }
}

/** Implementação real dos avisos de descanso, via [RestTimerService]. */
class ServiceRestTimerAlerts(private val context: Context) : RestTimerAlerts {
    override fun schedule(endsAt: Long, exerciseName: String) {
        if (endsAt <= System.currentTimeMillis()) return cancel()
        runCatching { RestTimerService.start(context, endsAt, exerciseName) }
    }

    override fun cancel() {
        runCatching { RestTimerService.stop(context) }
    }
}
