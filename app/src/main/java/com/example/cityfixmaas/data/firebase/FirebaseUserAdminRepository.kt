package com.example.cityfixmaas.data.firebase

import com.example.cityfixmaas.data.model.AccountStatus
import com.example.cityfixmaas.data.model.User
import com.example.cityfixmaas.data.model.UserRole
import com.example.cityfixmaas.data.repository.UserAdminRepository
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.snapshots
import com.google.firebase.firestore.toObject
import com.google.firebase.firestore.toObjects
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class FirebaseUserAdminRepository(db: FirebaseFirestore) : UserAdminRepository {

    private val users = db.collection("users")

    override fun observePendingCitizens(): Flow<List<User>> = users
        .whereEqualTo("role", UserRole.CITIZEN.name)
        .whereEqualTo("status", AccountStatus.PENDING.name)
        .snapshots()
        .map { result -> result.toObjects<User>().sortedBy { it.createdAt } }
        .catch { emit(emptyList()) }

    override fun observeUser(uid: String): Flow<User?> = users.document(uid)
        .snapshots()
        .map { it.toObject<User>() }
        .catch { emit(null) }

    override suspend fun activateCitizen(uid: String): Result<Unit> = runCatching {
        users.document(uid).update("status", AccountStatus.ACTIVE.name).await()
    }
}
