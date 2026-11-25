package com.example.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.RingtoneManager
import android.media.ToneGenerator
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.data.OrderStatus
import com.example.data.StatusTransition

object OrderAlertPolicy {
    val ALERT_STATUSES = setOf(
        OrderStatus.PAYMENT_CONFIRMED.rawValue,
        OrderStatus.OUT_FOR_DELIVERY.rawValue
    )

    fun shouldAlert(previousStatus: String?, newStatus: String?): Boolean {
        if (newStatus.isNullOrBlank()) return false
        if (previousStatus == newStatus) return false
        return newStatus in ALERT_STATUSES
    }

    fun transitionKey(orderId: String, newStatus: String): String = "$orderId:$newStatus"
}

interface OrderStatusNotifier {
    fun notifyIfMilestone(transition: StatusTransition?)
}

class OrderAlertNotifier(private val context: Context) : OrderStatusNotifier {

    private val appContext = context.applicationContext
    private val prefs by lazy {
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    override fun notifyIfMilestone(transition: StatusTransition?) {
        if (transition == null) return
        if (!OrderAlertPolicy.shouldAlert(transition.previousStatus, transition.newStatus)) return

        val key = OrderAlertPolicy.transitionKey(transition.orderId, transition.newStatus)
        val alreadyNotified = prefs.getStringSet(KEY_NOTIFIED_SET, emptySet()) ?: emptySet()
        if (key in alreadyNotified) return

        prefs.edit()
            .putStringSet(KEY_NOTIFIED_SET, (alreadyNotified + key).takeLastSet(60))
            .apply()

        playSoundAlert()
        postLocalNotification(transition)
    }

    private fun postLocalNotification(transition: StatusTransition) {
        ensureNotificationChannel()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }

        val (title, body) = when (transition.newStatus) {
            OrderStatus.PAYMENT_CONFIRMED.rawValue -> {
                "✅ ¡Pago confirmado! (${transition.orderNumber})" to
                    "Verificamos tu transferencia. Tu pedido pasó a cocina y WhatsApp ya está desbloqueado."
            }
            OrderStatus.OUT_FOR_DELIVERY.rawValue -> {
                "🛵 ¡Tu pizza va en camino! (${transition.orderNumber})" to
                    "Nuestro repartidor ya salió hacia tu dirección en Ciudad del Este."
            }
            else -> return
        }

        val launchIntent = Intent(appContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_OPEN_ORDERS_TAB, true)
            putExtra(EXTRA_ORDER_ID, transition.orderId)
        }
        val pendingIntent = PendingIntent.getActivity(
            appContext,
            transition.orderId.hashCode(),
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_pizza)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setAutoCancel(true)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setContentIntent(pendingIntent)
            .build()

        try {
            NotificationManagerCompat.from(appContext)
                .notify((transition.orderId + transition.newStatus).hashCode(), notification)
        } catch (_: SecurityException) {
            // Ignored gracefully if notification permission was revoked
        }
    }

    private fun playSoundAlert() {
        try {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            if (uri != null) {
                val ringtone = RingtoneManager.getRingtone(appContext, uri)
                if (ringtone != null) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        ringtone.audioAttributes = AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    }
                    ringtone.play()
                    return
                }
            }
        } catch (_: Exception) {
            // Fall through to ToneGenerator fallback
        }

        try {
            val tone = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 85)
            tone.startTone(ToneGenerator.TONE_PROP_BEEP2, 350)
        } catch (_: Exception) {
            // Audio unavailable in headless environments
        }
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                ?: return
            if (manager.getNotificationChannel(CHANNEL_ID) != null) return
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Actualizaciones de Pedidos",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alertas cuando tu pago es confirmado o tu pizza sale en delivery."
                enableVibration(true)
            }
            manager.createNotificationChannel(channel)
        }
    }

    private fun Set<String>.takeLastSet(limit: Int): Set<String> {
        return if (size <= limit) this else toList().takeLast(limit).toSet()
    }

    companion object {
        const val CHANNEL_ID = "pizza_club_order_updates"
        const val EXTRA_OPEN_ORDERS_TAB = "extra_open_orders_tab"
        const val EXTRA_ORDER_ID = "extra_order_id"
        private const val PREFS_NAME = "pizza_club_alert_prefs"
        private const val KEY_NOTIFIED_SET = "notified_order_transitions"
    }
}
