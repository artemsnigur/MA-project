package com.example.cityfixmaas.data.firebase

import com.example.cityfixmaas.data.model.AccountStatus
import com.example.cityfixmaas.data.model.User
import com.example.cityfixmaas.data.model.UserRole
import com.example.cityfixmaas.data.repository.UserAdminRepository
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.Flow

/** Operator-only; security rules reject these reads and writes for anyone else. */
class FirebaseUserAdminRepository(db: FirebaseFirestore) : UserAdminRepository {

    private val users = db.collection(Paths.USERS)

    // Sorted on the device: ordering in the query would need another composite index.
    override fun observePendingCitizens(): Flow<List<User>> = users
        .whereEqualTo("role", UserRole.CITIZEN.name)
        .whereEqualTo("status", AccountStatus.PENDING.name)
        .observe { snapshot -> snapshot.toObjects(User::class.java).sortedBy { it.createdAt } }

    override fun observeUser(uid: String): Flow<User?> =
        users.document(uid).observe { it.toObject(User::class.java) }

    override suspend fun activateCitizen(uid: String): Result<Unit> = firebaseResult {
        // Field update only, so it never overwrites a concurrent profile change.
        users.document(uid).update("status", AccountStatus.ACTIVE.name)
        Unit
    }
}
