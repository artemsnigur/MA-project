package com.example.cityfixmaas.data.firebase

import com.example.cityfixmaas.data.model.Category
import com.example.cityfixmaas.data.repository.CategoryRepository
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.snapshots
import com.google.firebase.firestore.toObjects
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class FirebaseCategoryRepository(db: FirebaseFirestore) : CategoryRepository {

    private val categories = db.collection("categories")

    override fun observeCategories(activeOnly: Boolean): Flow<List<Category>> = categories
        .snapshots()
        .map { result ->
            result.toObjects<Category>()
                .filter { !activeOnly || it.active }
                .sortedBy { it.name }
        }
        .catch { emit(emptyList()) }

    override suspend fun saveCategory(category: Category): Result<Unit> = runCatching {
        require(category.name.isNotBlank()) { "Name is required" }
        if (category.id.isBlank()) {
            val doc = categories.document() // new random id
            doc.set(category.copy(id = doc.id, name = category.name.trim())).await()
        } else {
            categories.document(category.id).update("name", category.name.trim()).await()
        }
    }

    override suspend fun setActive(id: String, active: Boolean): Result<Unit> = runCatching {
        categories.document(id).update("active", active).await()
    }
}
