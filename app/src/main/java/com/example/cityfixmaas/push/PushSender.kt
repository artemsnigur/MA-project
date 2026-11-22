package com.example.cityfixmaas.push

import com.example.cityfixmaas.data.AppConfig
import com.example.cityfixmaas.data.model.LifecycleState
import com.example.cityfixmaas.data.model.Ticket
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** What a push message is about; the receiving phone turns it into text in its own language. */
enum class PushKind { STATE, MESSAGE, NEARBY }

/**
 * Sends remote notifications through the push relay (server/push-relay: a free Google Apps
 * Script web app that calls FCM). Citizens of a ticket = its author and followers; operators
 * get messages through the FCM topic "operators".
 * Fire and forget: a push that cannot be sent (e.g. offline) is simply lost.
 */
class PushSender(private val auth: FirebaseAuth) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun toCitizens(ticket: Ticket, kind: PushKind, state: LifecycleState? = null) =
        send(ticket, kind, state, toUids = listOf(ticket.authorId) + ticket.followerIds, topic = null)

    fun toOperators(ticket: Ticket, kind: PushKind, state: LifecycleState? = null) =
        send(ticket, kind, state, toUids = emptyList(), topic = OPERATORS_TOPIC)

    private fun send(ticket: Ticket, kind: PushKind, state: LifecycleState?, toUids: List<String>, topic: String?) {
        if (AppConfig.PUSH_RELAY_URL.isBlank()) return
        scope.launch {
            runCatching {
                val idToken = auth.currentUser?.getIdToken(false)?.await()?.token ?: return@launch
                val data = JSONObject()
                    .put("kind", kind.name)
                    .put("ticketId", ticket.id)
                    .put("description", ticket.description)
                    .put("state", state?.name.orEmpty())
                val body = JSONObject()
                    .put("idToken", idToken)
                    .put("toUids", JSONArray(toUids))
                    .put("topic", topic ?: JSONObject.NULL)
                    .put("data", data)
                val connection = URL(AppConfig.PUSH_RELAY_URL).openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                connection.outputStream.use { it.write(body.toString().toByteArray()) }
                connection.responseCode // sends the request; the answer is not needed
                connection.disconnect()
            }
        }
    }

    companion object {
        const val OPERATORS_TOPIC = "operators"
    }
}
