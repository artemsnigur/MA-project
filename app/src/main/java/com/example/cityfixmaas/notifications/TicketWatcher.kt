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
import androidx.core.content.edit
import com.example.cityfixmaas.MainActivity
import com.example.cityfixmaas.R
import com.example.cityfixmaas.data.AppConfig
import com.example.cityfixmaas.data.model.AccountStatus
import com.example.cityfixmaas.data.model.LifecycleState
import com.example.cityfixmaas.data.model.Ticket
import com.example.cityfixmaas.data.model.User
import com.example.cityfixmaas.data.model.UserRole
import com.example.cityfixmaas.data.model.SessionState
import com.example.cityfixmaas.di.AppContainer
import com.example.cityfixmaas.ui.components.labelRes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Local notifications about tickets, and the automatic close after 5 days.
 *
 * It compares each ticket with what the user saw last time (kept in SharedPreferences) and notifies:
 * - citizens: state changes, new operator messages, and 24 h before a resolved ticket closes;
 * - operators: new citizen messages.
 * It runs while the app is alive ([start]) and every 15 minutes in the background (TicketCheckWorker).
 */
class TicketWatcher(private val context: Context, private val container: AppContainer) {

    /** Follows the signed-in user's tickets for as long as the app process lives. */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun start(scope: CoroutineScope) {
        createChannel()
        scope.launch {
            container.auth.session
                .flatMapLatest { session -> ticketsOf(session) }
                .collect { (tickets, user) -> handle(tickets, user) }
        }
    }

    /** One check, used by the background worker. */
    suspend fun checkOnce() {
        createChannel()
        val session = container.auth.session.first { it != SessionState.Loading }
        val (tickets, user) = ticketsOf(session).first()
        handle(tickets, user)
    }

    private fun ticketsOf(session: SessionState): Flow<Pair<List<Ticket>, User>> = when {
        session !is SessionState.SignedIn || session.user.status == AccountStatus.PENDING -> emptyFlow()
        session.user.role == UserRole.OPERATOR -> container.tickets.observeAllTickets().map { it to session.user }
        else -> container.tickets.observeMyTickets().map { it to session.user }
    }

    private suspend fun handle(tickets: List<Ticket>, user: User) {
        val role = user.role
        // What this user has already seen, kept per user in case several accounts share the phone.
        val prefs = context.getSharedPreferences("ticket-notifications-${user.id}", Context.MODE_PRIVATE)
        container.tickets.closeExpiredTickets(tickets)
        val now = System.currentTimeMillis()
        tickets.forEach { ticket ->
            val seenState = prefs.getString("state_${ticket.id}", null)
            val seenMessageAt = prefs.getLong("message_${ticket.id}", -1)

            // A ticket seen for the first time is only remembered, never announced.
            if (seenState != null) {
                if (role == UserRole.CITIZEN && seenState != ticket.lifecycleState.name && ticket.stateChangedBy != user.id) {
                    notify(ticket, context.getString(R.string.notif_state_changed, context.getString(ticket.lifecycleState.labelRes())))
                }
                if (ticket.lastMessageAt > seenMessageAt && ticket.lastMessageRole != role) {
                    notify(ticket, context.getString(R.string.notif_new_message))
                }
            }
            val closesIn = (ticket.resolvedAt ?: 0) + AppConfig.REOPEN_WINDOW_MS - now
            val reminderKey = "reminded_${ticket.id}_${ticket.resolvedAt}"
            if (role == UserRole.CITIZEN && ticket.lifecycleState == LifecycleState.RESOLVED &&
                closesIn in 1..24 * 3_600_000L && !prefs.getBoolean(reminderKey, false)
            ) {
                notify(ticket, context.getString(R.string.notif_close_reminder))
                prefs.edit { putBoolean(reminderKey, true) }
            }
            prefs.edit {
                putString("state_${ticket.id}", ticket.lifecycleState.name)
                putLong("message_${ticket.id}", ticket.lastMessageAt)
            }
        }
    }

    private fun createChannel() {
        val channel = NotificationChannelCompat.Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_DEFAULT)
            .setName(context.getString(R.string.notif_channel_name))
            .build()
        NotificationManagerCompat.from(context).createNotificationChannel(channel)
    }

    /** One notification per ticket and kind (a newer one of the same kind replaces it); tapping it opens the app. */
    private fun notify(ticket: Ticket, title: String) {
        // Android 13+ asks the user (see AppShell); older versions allow notifications by default.
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val openApp = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(ticket.description)
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify((ticket.id + title).hashCode(), notification)
    }

    private companion object {
        const val CHANNEL_ID = "tickets"
    }
}
