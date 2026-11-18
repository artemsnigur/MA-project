package com.example.cityfixmaas.data.firebase

import android.net.Uri
import com.example.cityfixmaas.data.media.ImageCompressor
import com.example.cityfixmaas.data.model.AccountStatus
import com.example.cityfixmaas.data.model.RegistrationForm
import com.example.cityfixmaas.data.model.SessionState
import com.example.cityfixmaas.data.model.User
import com.example.cityfixmaas.data.model.UserRole
import com.example.cityfixmaas.data.repository.AuthRepository
import com.example.cityfixmaas.data.validation.Validation
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.tasks.await

class FirebaseAuthRepository(
    private val auth: FirebaseAuth,
    private val db: FirebaseFirestore,
    private val images: ImageCompressor,
    scope: CoroutineScope,
) : AuthRepository {

    private val users = db.collection(Paths.USERS)

    /**
     * True between creating the Auth account and writing users/{uid}. During that window the
     * session stays Loading instead of flashing Anonymous (the profile does not exist yet).
     */
    private val registering = MutableStateFlow(false)

    @OptIn(ExperimentalCoroutinesApi::class)
    override val session: StateFlow<SessionState> = authUid()
        .flatMapLatest { uid ->
            if (uid == null) {
                flowOf(SessionState.Anonymous)
            } else {
                val profile = users.document(uid).observe { it.toObject(User::class.java) }
                combine(profile, registering) { user, isRegistering ->
                    when {
                        user != null -> SessionState.SignedIn(user)
                        isRegistering -> SessionState.Loading
                        // Auth account without a profile (should not happen): treat as signed out.
                        else -> SessionState.Anonymous
                    }
                }
            }
        }
        .stateIn(scope, SharingStarted.Eagerly, SessionState.Loading)

    /** Current Firebase uid; the listener fires once Firebase has restored the cached user. */
    private fun authUid(): Flow<String?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { trySend(it.currentUser?.uid) }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }.distinctUntilChanged()

    override suspend fun signIn(email: String, password: String): Result<Unit> = firebaseResult {
        Validation.email(email)?.let { throw IllegalArgumentException(it) }
        require(password.isNotEmpty()) { "Password is required" }
        auth.signInWithEmailAndPassword(email.trim(), password).await()
        Unit
    }

    override suspend fun registerCitizen(form: RegistrationForm): Result<Unit> =
        firebaseResult { register(form, UserRole.CITIZEN, AccountStatus.PENDING) }

    override suspend fun registerOperator(form: RegistrationForm): Result<Unit> =
        firebaseResult { register(form.copy(location = null), UserRole.OPERATOR, AccountStatus.ACTIVE) }

    /**
     * Order matters, so a failure never leaves a half-registered account behind:
     * validate → NIF pre-check → photo → Auth account → one batch (profile + NIF claim).
     * If the batch fails, the new Auth account is deleted so the email can be used again.
     */
    private suspend fun register(form: RegistrationForm, role: UserRole, status: AccountStatus) {
        Validation.registration(form, role)?.let { throw IllegalArgumentException(it) }
        val email = form.email.trim().lowercase()
        val nif = form.nif.trim()
        val nifRef = db.collection(Paths.NIFS).document(Paths.nifKey(role.name, nif))

        // Server read: a cached "missing" answer would let a duplicate NIF through.
        check(!nifRef.get(Source.SERVER).await().exists()) { "This NIF is already registered" }

        val photo = form.photoUri?.let {
            images.toBase64Jpeg(it, ImageCompressor.PROFILE_MAX_DIMENSION, ImageCompressor.PROFILE_MAX_BASE64)
        }

        registering.value = true
        try {
            val firebaseUser = auth.createUserWithEmailAndPassword(email, form.password).await().user
                ?: error("Registration failed, please try again")
            val user = User(
                id = firebaseUser.uid,
                role = role,
                status = status,
                name = form.name.trim(),
                email = email,
                phone = Validation.normalizePhone(form.phone),
                nif = nif,
                location = form.location,
                photoBase64 = photo,
                impactDetectionEnabled = form.impactDetectionEnabled,
                createdAt = System.currentTimeMillis(),
            )
            try {
                db.batch()
                    .set(users.document(user.id), user)
                    .set(nifRef, mapOf("uid" to user.id))
                    .commit()
                    .await()
            } catch (e: Exception) {
                // Most likely another account claimed the NIF in the meantime (rules deny the write).
                runCatching { firebaseUser.delete().await() }
                throw IllegalStateException("Registration failed: this NIF may already be registered", e)
            }
        } finally {
            registering.value = false
        }
    }

    override suspend fun sendPasswordReset(email: String): Result<Unit> = firebaseResult {
        Validation.email(email)?.let { throw IllegalArgumentException(it) }
        auth.sendPasswordResetEmail(email.trim()).await()
        Unit
    }

    override suspend fun updateProfile(phone: String, photoUri: Uri?): Result<Unit> = firebaseResult {
        val uid = auth.currentUser?.uid ?: error("Not signed in")
        Validation.phone(phone)?.let { throw IllegalArgumentException(it) }
        val changes = mutableMapOf<String, Any>("phone" to Validation.normalizePhone(phone))
        if (photoUri != null) {
            changes["photoBase64"] = images.toBase64Jpeg(
                photoUri, ImageCompressor.PROFILE_MAX_DIMENSION, ImageCompressor.PROFILE_MAX_BASE64,
            )
        }
        // Not awaited on purpose: Firestore applies it locally at once and syncs when online.
        users.document(uid).update(changes)
        Unit
    }

    override suspend fun signOut() {
        auth.signOut()
    }
}
