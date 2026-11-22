package com.example.cityfixmaas.di

import com.example.cityfixmaas.push.PushSender
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
import com.example.cityfixmaas.data.firebase.FirebaseMediaRepository
import com.example.cityfixmaas.data.firebase.FirebaseMessageRepository
import com.example.cityfixmaas.data.firebase.FirebasePublicRepository
import com.example.cityfixmaas.data.firebase.FirebaseTicketRepository
import com.example.cityfixmaas.data.firebase.FirebaseUserAdminRepository
import com.example.cityfixmaas.data.location.FusedLocationProvider
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

/** In-memory data, handy for trying screens without Firebase (CityFixApp.USE_FIREBASE = false). */
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

/** Real data: Firebase Auth + Firestore, and the phone's GPS. */
class FirebaseAppContainer(context: Context) : AppContainer {
    private val firebaseAuth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override val auth: AuthRepository = FirebaseAuthRepository(context, firebaseAuth, db, scope)
    override val userAdmin: UserAdminRepository = FirebaseUserAdminRepository(db)
    override val categories: CategoryRepository = FirebaseCategoryRepository(db)
    private val push = PushSender()

    override val tickets: TicketRepository = FirebaseTicketRepository(context, firebaseAuth, db, push)
    override val media: MediaRepository = FirebaseMediaRepository(db)
    override val publicData: PublicRepository = FirebasePublicRepository(db)
    override val messages: MessageRepository = FirebaseMessageRepository(firebaseAuth, db, push)
    override val location: LocationProvider = FusedLocationProvider(context)
}

/** Access the container inside `viewModelFactory { initializer { ... } }`. */
fun CreationExtras.appContainer(): AppContainer =
    (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as CityFixApp).container
