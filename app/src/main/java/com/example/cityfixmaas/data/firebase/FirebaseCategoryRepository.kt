package com.example.cityfixmaas.data.firebase

import com.example.cityfixmaas.data.model.Category
import com.example.cityfixmaas.data.repository.CategoryRepository
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.tasks.await

class FirebaseCategoryRepository(private val db: FirebaseFirestore) : CategoryRepository {

    private val categories = db.collection(Paths.CATEGORIES)

    // The collection is tiny, so filtering and sorting on the device avoids an index.
    override fun observeCategories(activeOnly: Boolean): Flow<List<Category>> =
        categories.observe { snapshot ->
            snapshot.toObjects(Category::class.java)
                .filter { !activeOnly || it.active }
                .sortedBy { it.name.lowercase() }
        }

    override suspend fun saveCategory(category: Category): Result<Unit> = firebaseResult {
        val name = category.name.trim()
        require(name.isNotEmpty()) { "Name is required" }
        if (category.id.isBlank()) {
            val ref = categories.document()
            ref.set(category.copy(id = ref.id, name = name))
        } else {
            categories.document(category.id).update("name", name)
        }
        Unit
    }

    override suspend fun setActive(id: String, active: Boolean): Result<Unit> = firebaseResult {
        categories.document(id).update("active", active)
        Unit
    }

    /**
     * Creates the default categories when the collection is empty. Runs when an operator signs
     * in (only operators may write categories). Fixed ids make a repeated run harmless.
     */
    suspend fun seedDefaultsIfEmpty() {
        firebaseResult {
            if (categories.limit(1).get(Source.SERVER).await().isEmpty) {
                val batch = db.batch()
                DEFAULT_CATEGORIES.forEach { batch.set(categories.document(it.id), it) }
                batch.commit().await()
            }
        }
    }

    companion object {
        val DEFAULT_CATEGORIES = listOf(
            Category("cat-roads", "Public Roads"),
            Category("cat-water", "Water"),
            Category("cat-lighting", "Lighting"),
            Category("cat-sanitation", "Sanitation"),
        )
    }
}
