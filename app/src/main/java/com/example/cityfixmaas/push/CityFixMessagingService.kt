package com.example.cityfixmaas.push

import com.example.cityfixmaas.R
import com.example.cityfixmaas.data.model.LifecycleState
import com.example.cityfixmaas.notifications.TicketNotifications
import com.example.cityfixmaas.ui.components.labelRes
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/** Receives the push messages sent by PushSender (through FCM) and shows them as notifications. */
class CityFixMessagingService : FirebaseMessagingService() {

    /** FCM gave this phone a new address: save it so others can reach the signed-in user. */
    override fun onNewToken(token: String) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        FirebaseFirestore.getInstance().collection("users").document(uid).update("fcmToken", token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        if (data["senderUid"] == FirebaseAuth.getInstance().currentUser?.uid) return // e.g. an operator's own message to the topic
        val title = when (data["kind"]) {
            PushKind.STATE.name -> {
                val state = LifecycleState.entries.firstOrNull { it.name == data["state"] } ?: return
                getString(R.string.notif_state_changed, getString(state.labelRes()))
            }
            PushKind.MESSAGE.name -> getString(R.string.notif_new_message)
            PushKind.NEARBY.name -> getString(R.string.notif_operator_nearby)
            else -> return
        }
        TicketNotifications.show(this, data["ticketId"].orEmpty(), title, data["description"].orEmpty())
    }
}
