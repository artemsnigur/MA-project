package com.example.cityfixmaas.data.firebase

import android.util.Log
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/** Firestore collection names and document ids, in one place. */
object Paths {
    const val USERS = "users"
    const val NIFS = "nifs"
    const val CATEGORIES = "categories"
    const val TICKETS = "tickets"
    const val PUBLIC_TICKETS = "publicTickets"
    const val MEDIA = "media"
    const val HISTORY = "history"
    const val MESSAGES = "messages"

    /** NIFs are unique per role: one person may be both a citizen and an operator. */
    fun nifKey(role: String, nif: String) = "${role}_$nif"
}

private const val TAG = "CityFixFirestore"

/**
 * Live document as a Flow. A listener error (e.g. permission denied after sign-out) ends the
 * flow quietly instead of crashing the collector; it is logged.
 */
fun <T> DocumentReference.observe(map: (DocumentSnapshot) -> T): Flow<T> = callbackFlow {
    val registration = addSnapshotListener { snapshot, error ->
        when {
            error != null -> {
                Log.w(TAG, "Listener on $path failed", error)
                close()
            }
            snapshot != null -> trySend(map(snapshot))
        }
    }
    awaitClose { registration.remove() }
}

/** Live query result as a Flow; same error handling as [observe]. */
fun <T> Query.observe(map: (QuerySnapshot) -> T): Flow<T> = callbackFlow {
    val registration = addSnapshotListener { snapshot, error ->
        when {
            error != null -> {
                Log.w(TAG, "Query listener failed", error)
                close()
            }
            snapshot != null -> trySend(map(snapshot))
        }
    }
    awaitClose { registration.remove() }
}

/**
 * Runs a Firebase call and turns failures into messages the UI can show as they are.
 * Cancellation is rethrown so coroutine scopes keep working.
 */
suspend fun <T> firebaseResult(block: suspend () -> T): Result<T> = try {
    Result.success(block())
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    Log.w(TAG, "Firebase call failed", e)
    Result.failure(Exception(userMessage(e), e))
}

private fun userMessage(e: Exception): String = when (e) {
    is FirebaseNetworkException -> "No internet connection"
    is FirebaseAuthWeakPasswordException -> "Password is too weak"
    is FirebaseAuthUserCollisionException -> "This email is already registered"
    is FirebaseAuthInvalidUserException -> "Wrong email or password"
    is FirebaseAuthInvalidCredentialsException -> "Wrong email or password"
    is FirebaseFirestoreException -> when (e.code) {
        FirebaseFirestoreException.Code.PERMISSION_DENIED -> "You are not allowed to do this"
        FirebaseFirestoreException.Code.UNAVAILABLE -> "No internet connection"
        else -> "Something went wrong, please try again"
    }
    // Our own require()/check() failures already carry a user-facing message.
    is IllegalArgumentException, is IllegalStateException -> e.message ?: "Something went wrong"
    else -> "Something went wrong, please try again"
}
