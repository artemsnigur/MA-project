package com.example.cityfixmaas.data.firebase

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.cityfixmaas.data.model.AccountStatus
import com.example.cityfixmaas.data.model.Category
import com.example.cityfixmaas.data.model.LatLng
import com.example.cityfixmaas.data.model.RegistrationForm
import com.example.cityfixmaas.data.model.SessionState
import com.example.cityfixmaas.data.model.User
import com.example.cityfixmaas.data.model.UserRole
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.toObject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Checks B1 against the real Firebase project (needs internet). Creates test accounts named
 * b1-test-* and deletes them at the end.
 */
@RunWith(AndroidJUnit4::class)
class FirebaseB1SmokeTest {

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val repo = FirebaseAuthRepository(context, auth, db, CoroutineScope(Dispatchers.Default))
    private val admin = FirebaseUserAdminRepository(db)
    private val categories = FirebaseCategoryRepository(db)

    @Test
    fun registerSignInActivateAndCategories(): Unit = runBlocking {
        val stamp = System.currentTimeMillis()
        val nif = (stamp % 1_000_000_000).toString().padStart(9, '1')
        val password = "test-pass-1"
        auth.signOut()

        // A citizen registers and waits for activation.
        val citizenEmail = "b1-test-$stamp@example.com"
        val form = RegistrationForm(
            name = "Test Citizen", email = citizenEmail, password = password,
            phone = "912345678", nif = nif, location = LatLng(40.2, -8.42),
        )
        repo.registerCitizen(form).getOrThrow()

        // Registration sends a confirmation email and signs out; signing in fails until it is confirmed.
        assertNull(auth.currentUser)
        val unconfirmed = repo.signIn(citizenEmail, password)
        assertEquals("Confirm your email first", unconfirmed.exceptionOrNull()?.message?.substringBefore(":"))
        assertEquals(SessionState.Anonymous, waitFor(repo.session) { it != SessionState.Loading })

        // Another citizen with the same NIF is rejected.
        val duplicate = repo.registerCitizen(form.copy(email = "b1-test-$stamp-2@example.com"))
        assertEquals("This NIF is already registered", duplicate.exceptionOrNull()?.message)

        // The profile was saved as pending (read with a plain Firebase sign-in) and the phone can be edited.
        auth.signInWithEmailAndPassword(citizenEmail, password).await()
        val citizen = profile()
        assertEquals(AccountStatus.PENDING, citizen.status)
        assertEquals(LatLng(40.2, -8.42), citizen.location)
        repo.updateProfile("239123456", null, impactDetectionEnabled = false).getOrThrow()
        assertEquals("239123456", profile().phone)
        auth.signOut()

        // An operator registers (same NIF is fine: other role), activates the citizen, adds a category.
        val operatorEmail = "b1-test-$stamp@cm-coimbra.pt"
        repo.registerOperator(form.copy(name = "Test Operator", email = operatorEmail)).getOrThrow()
        auth.signInWithEmailAndPassword(operatorEmail, password).await()
        assertEquals(UserRole.OPERATOR, profile().role)

        waitFor(admin.observePendingCitizens()) { list -> list.any { it.id == citizen.id } }
        admin.activateCitizen(citizen.id).getOrThrow()
        waitFor(admin.observeUser(citizen.id)) { it?.status == AccountStatus.ACTIVE }

        val categoryName = "Test category $stamp"
        categories.saveCategory(Category(name = categoryName)).getOrThrow()
        val saved = waitFor(categories.observeCategories(activeOnly = false)) { list -> list.any { it.name == categoryName } }
            .first { it.name == categoryName }

        // Clean up.
        db.collection("categories").document(saved.id).delete().await()
        db.collection("users").document(auth.currentUser!!.uid).delete().await()
        auth.currentUser!!.delete().await()
        auth.signInWithEmailAndPassword(citizenEmail, password).await()
        db.collection("users").document(citizen.id).delete().await()
        auth.currentUser!!.delete().await()
    }

    private suspend fun profile(): User =
        db.collection("users").document(auth.currentUser!!.uid).get().await().toObject<User>()!!

    private suspend fun <T> waitFor(flow: Flow<T>, condition: (T) -> Boolean): T =
        withTimeout(20_000) { flow.first(condition) }
}
