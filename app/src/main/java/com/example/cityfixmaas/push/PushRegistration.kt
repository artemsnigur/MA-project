package com.example.cityfixmaas.push

import com.example.cityfixmaas.data.model.SessionState
import com.example.cityfixmaas.data.model.UserRole
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * After sign-in, saves this phone's FCM token in users/{uid}.fcmToken (the push relay reads it)
 * and subscribes operators to the "operators" topic. Sign-out removes the token (AuthRepository).
 */
object PushRegistration {
    fun start(scope: CoroutineScope, session: StateFlow<SessionState>) {
        scope.launch {
            session.collect { state ->
                runCatching {
                    val messaging = FirebaseMessaging.getInstance()
                    if (state is SessionState.SignedIn) {
                        val token = messaging.token.await()
                        FirebaseFirestore.getInstance().collection("users").document(state.user.id).update("fcmToken", token)
                        if (state.user.role == UserRole.OPERATOR) {
                            messaging.subscribeToTopic(PushSender.OPERATORS_TOPIC)
                        } else {
                            messaging.unsubscribeFromTopic(PushSender.OPERATORS_TOPIC)
                        }
                    } else if (state == SessionState.Anonymous) {
                        messaging.unsubscribeFromTopic(PushSender.OPERATORS_TOPIC)
                    }
                }
            }
        }
    }
}
