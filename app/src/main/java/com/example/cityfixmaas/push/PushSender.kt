package com.example.cityfixmaas.push

import com.example.cityfixmaas.data.model.LifecycleState
import com.example.cityfixmaas.data.model.Ticket
import com.example.cityfixmaas.data.relay.Relay
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/** What a push message is about; the receiving phone turns it into text in its own language. */
enum class PushKind { STATE, MESSAGE, NEARBY }

/**
 * Sends remote notifications through the relay (server/relay), which calls FCM. Citizens of a
 * ticket = its author and followers; operators get messages through the FCM topic "operators".
 * Fire and forget: a push that cannot be sent (e.g. offline) is simply lost.
 */
class PushSender {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun toCitizens(ticket: Ticket, kind: PushKind, state: LifecycleState? = null) =
        send(ticket, kind, state, toUids = listOf(ticket.authorId) + ticket.followerIds, topic = null)

    fun toOperators(ticket: Ticket, kind: PushKind, state: LifecycleState? = null) =
        send(ticket, kind, state, toUids = emptyList(), topic = OPERATORS_TOPIC)

    private fun send(ticket: Ticket, kind: PushKind, state: LifecycleState?, toUids: List<String>, topic: String?) {
        scope.launch {
            val data = JSONObject()
                .put("kind", kind.name)
                .put("ticketId", ticket.id)
                .put("description", ticket.description)
                .put("state", state?.name.orEmpty())
            val body = JSONObject()
                .put("toUids", JSONArray(toUids))
                .put("topic", topic ?: JSONObject.NULL)
                .put("data", data)
            runCatching { Relay.call("push", body) }
        }
    }

    companion object {
        const val OPERATORS_TOPIC = "operators"
    }
}
