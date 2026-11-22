package com.example.cityfixmaas.notifications

import android.content.Context
import androidx.core.content.edit
import com.example.cityfixmaas.R
import com.example.cityfixmaas.data.AppConfig
import com.example.cityfixmaas.data.model.AccountStatus
import com.example.cityfixmaas.data.model.LifecycleState
import com.example.cityfixmaas.data.model.SessionState
import com.example.cityfixmaas.data.model.Ticket
import com.example.cityfixmaas.data.model.User
import com.example.cityfixmaas.data.model.UserRole
import com.example.cityfixmaas.di.AppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Time-based work on the user's tickets that no server does for us:
 * - closes tickets resolved more than 5 days ago;
 * - reminds citizens 24 h before a resolved ticket closes (a local notification).
 * Events made by people (state changes, messages, operator nearby) arrive as push messages instead.
 * It runs while the app is alive ([start]) and every 15 minutes in the background (TicketCheckWorker).
 */
class TicketWatcher(private val context: Context, private val container: AppContainer) {

    /** Follows the signed-in user's tickets for as long as the app process lives. */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun start(scope: CoroutineScope) {
        scope.launch {
            container.auth.session
                .flatMapLatest { session -> ticketsOf(session) }
                .collect { (tickets, user) -> handle(tickets, user) }
        }
    }

    /** One check, used by the background worker. */
    suspend fun checkOnce() {
        val session = container.auth.session.first { it != SessionState.Loading }
        val (tickets, user) = ticketsOf(session).firstOrNull() ?: return // nobody signed in
        handle(tickets, user)
    }

    private fun ticketsOf(session: SessionState): Flow<Pair<List<Ticket>, User>> = when {
        session !is SessionState.SignedIn || session.user.status == AccountStatus.PENDING -> emptyFlow()
        session.user.role == UserRole.OPERATOR -> container.tickets.observeAllTickets().map { it to session.user }
        else -> container.tickets.observeMyTickets().map { it to session.user }
    }

    private suspend fun handle(tickets: List<Ticket>, user: User) {
        container.tickets.closeExpiredTickets(tickets)
        if (user.role != UserRole.CITIZEN) return

        // Reminders already shown, kept per user in case several accounts share the phone.
        val prefs = context.getSharedPreferences("ticket-reminders-${user.id}", Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        tickets.filter { it.lifecycleState == LifecycleState.RESOLVED }.forEach { ticket ->
            val closesIn = (ticket.resolvedAt ?: 0) + AppConfig.REOPEN_WINDOW_MS - now
            val key = "reminded_${ticket.id}_${ticket.resolvedAt}"
            if (closesIn in 1..24 * 3_600_000L && !prefs.getBoolean(key, false)) {
                TicketNotifications.show(context, ticket.id, context.getString(R.string.notif_close_reminder), ticket.description)
                prefs.edit { putBoolean(key, true) }
            }
        }
    }
}
