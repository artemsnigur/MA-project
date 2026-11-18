package com.example.cityfixmaas.data.firebase

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.cityfixmaas.data.model.AccountStatus
import com.example.cityfixmaas.data.model.Category
import com.example.cityfixmaas.data.model.LatLng
import com.example.cityfixmaas.data.model.RegistrationForm
import com.example.cityfixmaas.data.model.SessionState
import com.example.cityfixmaas.data.model.UserRole
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
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
        val citizen = signedInUser()
        assertEquals(AccountStatus.PENDING, citizen.status)
        assertEquals(LatLng(40.2, -8.42), citizen.location)

        // Another citizen with the same NIF is rejected.
        repo.signOut()
        val duplicate = repo.registerCitizen(form.copy(email = "b1-test-$stamp-2@example.com"))
        assertEquals("This NIF is already registered", duplicate.exceptionOrNull()?.message)

        // Sign in and edit the phone.
        repo.signIn(citizenEmail, password).getOrThrow()
        repo.updateProfile("239123456", null).getOrThrow()
        waitFor(repo.session) { (it as? SessionState.SignedIn)?.user?.phone == "239123456" }
        repo.signOut()

        // An operator registers (same NIF is fine: other role), activates the citizen, adds a category.
        val operatorEmail = "b1-test-$stamp@cm-coimbra.pt"
        repo.registerOperator(form.copy(name = "Test Operator", email = operatorEmail)).getOrThrow()
        assertEquals(UserRole.OPERATOR, signedInUser().role)

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

    private suspend fun signedInUser() =
        (waitFor(repo.session) { it is SessionState.SignedIn } as SessionState.SignedIn).user

    private suspend fun <T> waitFor(flow: Flow<T>, condition: (T) -> Boolean): T =
        withTimeout(20_000) { flow.first(condition) }
}
