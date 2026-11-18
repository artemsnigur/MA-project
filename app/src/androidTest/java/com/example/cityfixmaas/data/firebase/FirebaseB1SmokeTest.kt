package com.example.cityfixmaas.data.firebase

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.cityfixmaas.data.media.ImageCompressor
import com.example.cityfixmaas.data.model.AccountStatus
import com.example.cityfixmaas.data.model.LatLng
import com.example.cityfixmaas.data.model.RegistrationForm
import com.example.cityfixmaas.data.model.SessionState
import com.example.cityfixmaas.data.model.UserRole
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * End-to-end check of B1 against the real Firebase project (needs network).
 * Creates throwaway accounts named b1-smoke-*; their Auth users are deleted at the end,
 * their users/ and nifs/ documents stay (rules forbid deleting them from the app).
 */
@RunWith(AndroidJUnit4::class)
class FirebaseB1SmokeTest {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val repo = FirebaseAuthRepository(auth, db, ImageCompressor(context), scope)
    private val admin = FirebaseUserAdminRepository(db)
    private val categories = FirebaseCategoryRepository(db)

    private val stamp = System.currentTimeMillis()
    private val password = "smoke-pass-1"

    @After
    fun tearDown() {
        scope.cancel()
    }

    @Test
    fun registerSignInActivateAndCategories(): Unit = runBlocking {
        auth.signOut()
        awaitValue(repo.session) { it is SessionState.Anonymous }

        // Citizen registration → PENDING profile mapped back from Firestore.
        val citizenEmail = "b1-smoke-$stamp@example.com"
        val nif = validNif(stamp)
        val form = RegistrationForm(
            name = "Smoke Citizen", email = citizenEmail, password = password,
            phone = "+351 912 345 678", nif = nif, location = LatLng(40.2, -8.42),
        )
        repo.registerCitizen(form).getOrThrow()
        val citizen = (awaitValue(repo.session) { it is SessionState.SignedIn } as SessionState.SignedIn).user
        assertEquals(UserRole.CITIZEN, citizen.role)
        assertEquals(AccountStatus.PENDING, citizen.status)
        assertEquals("912345678", citizen.phone)
        assertEquals(LatLng(40.2, -8.42), citizen.location)
        val citizenUid = citizen.id

        // Same NIF again → rejected by the pre-check, no new Auth account.
        repo.signOut()
        val duplicate = repo.registerCitizen(form.copy(email = "b1-smoke-$stamp-dup@example.com"))
        assertEquals("This NIF is already registered", duplicate.exceptionOrNull()?.message)
        assertEquals(null, auth.currentUser)

        // Wrong password → friendly message; right password → signed in.
        assertEquals("Wrong email or password", repo.signIn(citizenEmail, "nope-nope").exceptionOrNull()?.message)
        repo.signIn(citizenEmail, password).getOrThrow()
        awaitValue(repo.session) { (it as? SessionState.SignedIn)?.user?.id == citizenUid }
        repo.updateProfile("239 123 456", null).getOrThrow()
        awaitValue(repo.session) { (it as? SessionState.SignedIn)?.user?.phone == "239123456" }
        repo.signOut()

        // Operator registration (municipal domain) → ACTIVE; sees and activates the citizen.
        val operatorEmail = "b1-smoke-$stamp@cm-coimbra.pt"
        repo.registerOperator(
            form.copy(name = "Smoke Operator", email = operatorEmail, nif = validNif(stamp + 1)),
        ).getOrThrow()
        val operator = (awaitValue(repo.session) { it is SessionState.SignedIn } as SessionState.SignedIn).user
        assertEquals(UserRole.OPERATOR, operator.role)
        assertEquals(null, operator.location)

        val pending = awaitValue(admin.observePendingCitizens()) { list -> list.any { it.id == citizenUid } }
        assertTrue(pending.all { it.status == AccountStatus.PENDING })
        admin.activateCitizen(citizenUid).getOrThrow()
        awaitValue(admin.observeUser(citizenUid)) { it?.status == AccountStatus.ACTIVE }

        // Categories: seeding is idempotent; the defaults are there.
        categories.seedDefaultsIfEmpty()
        val names = awaitValue(categories.observeCategories(activeOnly = false)) { it.isNotEmpty() }.map { it.name }
        assertTrue(names.containsAll(FirebaseCategoryRepository.DEFAULT_CATEGORIES.map { it.name }))

        // Clean up the Auth accounts.
        auth.currentUser?.delete()?.await()
        auth.signInWithEmailAndPassword(citizenEmail, password).await()
        auth.currentUser?.delete()?.await()
    }

    private suspend fun <T> awaitValue(flow: Flow<T>, predicate: (T) -> Boolean): T =
        withTimeout(20_000) { flow.first(predicate) }

    /** A 9-digit NIF with a valid check digit, derived from [seed]. */
    private fun validNif(seed: Long): String {
        val body = "2" + (seed % 10_000_000).toString().padStart(7, '0')
        val sum = body.indices.sumOf { i -> (body[i] - '0') * (9 - i) }
        val r = sum % 11
        return body + (if (r < 2) 0 else 11 - r)
    }
}
