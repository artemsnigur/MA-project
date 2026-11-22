package com.example.cityfixmaas.notifications

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.cityfixmaas.MainActivity
import com.example.cityfixmaas.R

/** Shows the ticket notifications, both the local ones (TicketWatcher) and the remote ones (push). */
object TicketNotifications {
    private const val CHANNEL_ID = "tickets"

    /** One notification per ticket and kind (a newer one of the same kind replaces it); tapping it opens the app. */
    fun show(context: Context, ticketId: String, title: String, text: String) {
        // Android 13+ asks the user (see AppShell); older versions allow notifications by default.
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val manager = NotificationManagerCompat.from(context)
        manager.createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_DEFAULT)
                .setName(context.getString(R.string.notif_channel_name))
                .build(),
        )
        val openApp = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .build()
        manager.notify((ticketId + title).hashCode(), notification)
    }
}
