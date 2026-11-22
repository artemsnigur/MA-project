package com.example.cityfixmaas.data.firebase

import com.example.cityfixmaas.data.AppConfig
import com.google.firebase.auth.FirebaseUser
import android.content.Context
import android.net.Uri
import com.example.cityfixmaas.data.media.ImageSize
import com.example.cityfixmaas.data.media.uriToBase64Jpeg
import com.example.cityfixmaas.data.model.AccountStatus
import com.example.cityfixmaas.data.model.RegistrationForm
import com.example.cityfixmaas.data.model.SessionState
import com.example.cityfixmaas.data.model.User
import com.example.cityfixmaas.data.model.UserRole
import com.example.cityfixmaas.data.repository.AuthRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.snapshots
import com.google.firebase.firestore.toObject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class FirebaseAuthRepository(
    private val context: Context,
    private val auth: FirebaseAuth,
    db: FirebaseFirestore,
    scope: CoroutineScope,
) : AuthRepository {

    private val users = db.collection("users")

    /** Firebase Auth tells who is signed in; their profile comes from users/{uid}. */
    @OptIn(ExperimentalCoroutinesApi::class)
    override val session: StateFlow<SessionState> = signedInUid()
        .flatMapLatest { uid ->
            if (uid == null) {
                flowOf(SessionState.Anonymous)
            } else {
                users.document(uid).snapshots()
                    .map { doc ->
                        val user = doc.toObject<User>()
                        if (user == null) SessionState.Anonymous else SessionState.SignedIn(user)
                    }
                    .catch { emit(SessionState.Anonymous) }
            }
        }
        .stateIn(scope, SharingStarted.Eagerly, SessionState.Loading)

    /**
     * Emits the uid of the signed-in user (or null) every time it changes. An account whose email
     * is not confirmed yet counts as signed out, so the app never shows its screens.
     */
    private fun signedInUid() = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { trySend(it.currentUser?.takeIf { user -> user.emailConfirmed() }?.uid) }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    /**
     * Two steps: the password, then the email. Until the citizen or operator opens the link we sent
     * to their inbox, signing in sends a new link and fails.
     */
    override suspend fun signIn(email: String, password: String): Result<Unit> = runCatching {
        val user = auth.signInWithEmailAndPassword(email.trim(), password).await().user!!
        if (!user.emailConfirmed()) {
            // Firebase limits how often it sends these emails; the earlier link still works then.
            runCatching { user.sendEmailVerification().await() }
            auth.signOut()
            throw IllegalStateException("Confirm your email first: open the link we sent to ${user.email}")
        }
    }

    private fun FirebaseUser.emailConfirmed() = isEmailVerified || email in AppConfig.EMAIL_CONFIRMATION_EXEMPT

    override suspend fun registerCitizen(form: RegistrationForm): Result<Unit> = runCatching {
        register(form, UserRole.CITIZEN, AccountStatus.PENDING)
    }

    override suspend fun registerOperator(form: RegistrationForm): Result<Unit> = runCatching {
        register(form.copy(location = null), UserRole.OPERATOR, AccountStatus.ACTIVE)
    }

    /**
     * The form is already checked by RegisterViewModel (ui/auth/Validation).
     * 1. Create the Firebase Auth account (this also signs in).
     * 2. Make sure no other user with this role has the same NIF; if one does, delete the new
     *    account again. 3. Save the profile in users/{uid}. 4. Send the confirmation email and
     *    sign out: the first sign-in works after the link is opened.
     */
    private suspend fun register(form: RegistrationForm, role: UserRole, status: AccountStatus) {
        val email = form.email.trim().lowercase()
        val account = auth.createUserWithEmailAndPassword(email, form.password).await().user!!

        val sameNif = users
            .whereEqualTo("role", role.name)
            .whereEqualTo("nif", form.nif.trim())
            .get().await()
        if (!sameNif.isEmpty) {
            account.delete().await()
            throw IllegalArgumentException("This NIF is already registered")
        }

        val photo = form.photoUri?.let { toBase64(it) }
        val user = User(
            id = account.uid,
            role = role,
            status = status,
            name = form.name.trim(),
            email = email,
            phone = form.phone.trim(),
            nif = form.nif.trim(),
            location = form.location,
            photoBase64 = photo,
            impactDetectionEnabled = form.impactDetectionEnabled,
            createdAt = System.currentTimeMillis(),
        )
        users.document(user.id).set(user).await()
        if (!account.emailConfirmed()) {
            runCatching { account.sendEmailVerification().await() } // if Firebase refuses now, signing in sends it again
            auth.signOut()
        }
    }

    override suspend fun sendPasswordReset(email: String): Result<Unit> = runCatching {
        auth.sendPasswordResetEmail(email.trim()).await()
    }

    override suspend fun updateProfile(phone: String, photoUri: Uri?, impactDetectionEnabled: Boolean): Result<Unit> = runCatching {
        val uid = auth.currentUser!!.uid
        val changes = mutableMapOf<String, Any>("phone" to phone.trim(), "impactDetectionEnabled" to impactDetectionEnabled)
        if (photoUri != null) changes["photoBase64"] = toBase64(photoUri)
        users.document(uid).update(changes).await()
    }

    override suspend fun signOut() {
        auth.signOut()
    }

    private suspend fun toBase64(uri: Uri) = withContext(Dispatchers.IO) {
        uriToBase64Jpeg(context, uri, ImageSize.PROFILE)
    }
}
