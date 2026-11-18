package com.example.cityfixmaas.di

import android.content.Context
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import com.example.cityfixmaas.CityFixApp
import com.example.cityfixmaas.data.fake.FakeAuthRepository
import com.example.cityfixmaas.data.fake.FakeBackend
import com.example.cityfixmaas.data.fake.FakeCategoryRepository
import com.example.cityfixmaas.data.fake.FakeLocationProvider
import com.example.cityfixmaas.data.fake.FakeMediaRepository
import com.example.cityfixmaas.data.fake.FakeMessageRepository
import com.example.cityfixmaas.data.fake.FakePublicRepository
import com.example.cityfixmaas.data.fake.FakeTicketRepository
import com.example.cityfixmaas.data.fake.FakeUserAdminRepository
import com.example.cityfixmaas.data.firebase.FirebaseAuthRepository
import com.example.cityfixmaas.data.firebase.FirebaseCategoryRepository
import com.example.cityfixmaas.data.firebase.FirebaseUserAdminRepository
import com.example.cityfixmaas.data.media.ImageCompressor
import com.example.cityfixmaas.data.model.SessionState
import com.example.cityfixmaas.data.model.UserRole
import com.example.cityfixmaas.data.repository.AuthRepository
import com.example.cityfixmaas.data.repository.CategoryRepository
import com.example.cityfixmaas.data.repository.LocationProvider
import com.example.cityfixmaas.data.repository.MediaRepository
import com.example.cityfixmaas.data.repository.MessageRepository
import com.example.cityfixmaas.data.repository.PublicRepository
import com.example.cityfixmaas.data.repository.TicketRepository
import com.example.cityfixmaas.data.repository.UserAdminRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Manual dependency injection: one place that decides which implementation each repository uses.
 *
 * ViewModel example:
 * ```
 * class MyTicketsViewModel(private val tickets: TicketRepository) : ViewModel() {
 *     companion object {
 *         val Factory = viewModelFactory {
 *             initializer { MyTicketsViewModel(appContainer().tickets) }
 *         }
 *     }
 * }
 * ```
 */
interface AppContainer {
    val auth: AuthRepository
    val userAdmin: UserAdminRepository
    val categories: CategoryRepository
    val tickets: TicketRepository
    val media: MediaRepository
    val publicData: PublicRepository
    val messages: MessageRepository
    val location: LocationProvider
}

/** In-memory data for UI development. Firebase implementations replace these one by one (B1–B3). */
class FakeAppContainer : AppContainer {
    private val backend = FakeBackend()

    override val auth: AuthRepository = FakeAuthRepository(backend)
    override val userAdmin: UserAdminRepository = FakeUserAdminRepository(backend)
    override val categories: CategoryRepository = FakeCategoryRepository(backend)
    override val tickets: TicketRepository = FakeTicketRepository(backend)
    override val media: MediaRepository = FakeMediaRepository(backend)
    override val publicData: PublicRepository = FakePublicRepository(backend)
    override val messages: MessageRepository = FakeMessageRepository(backend)
    override val location: LocationProvider = FakeLocationProvider()
}

/**
 * Firebase-backed data. B1 covers auth, users and categories; tickets, media, public data,
 * messages and location are still fakes until B2–B3, so do not switch [CityFixApp] to this
 * container for normal UI work yet (signed-in Firebase users are unknown to the fake tickets).
 */
class FirebaseAppContainer(context: Context) : AppContainer {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val firebaseAuth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private val images = ImageCompressor(context.applicationContext)
    private val fakes = FakeBackend()

    private val firebaseCategories = FirebaseCategoryRepository(db)

    override val auth: AuthRepository = FirebaseAuthRepository(firebaseAuth, db, images, scope)
    override val userAdmin: UserAdminRepository = FirebaseUserAdminRepository(db)
    override val categories: CategoryRepository = firebaseCategories
    override val tickets: TicketRepository = FakeTicketRepository(fakes) // B2
    override val media: MediaRepository = FakeMediaRepository(fakes) // B2
    override val publicData: PublicRepository = FakePublicRepository(fakes) // B3
    override val messages: MessageRepository = FakeMessageRepository(fakes) // B3
    override val location: LocationProvider = FakeLocationProvider() // B3

    init {
        // Default categories are created by the first operator who signs in.
        scope.launch {
            auth.session
                .map { (it as? SessionState.SignedIn)?.user?.role == UserRole.OPERATOR }
                .distinctUntilChanged()
                .filter { it }
                .collect { firebaseCategories.seedDefaultsIfEmpty() }
        }
    }
}

/** Access the container inside `viewModelFactory { initializer { ... } }`. */
fun CreationExtras.appContainer(): AppContainer =
    (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as CityFixApp).container
