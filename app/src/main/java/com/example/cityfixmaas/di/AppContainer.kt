package com.example.cityfixmaas.di

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
import com.example.cityfixmaas.data.repository.AuthRepository
import com.example.cityfixmaas.data.repository.CategoryRepository
import com.example.cityfixmaas.data.repository.LocationProvider
import com.example.cityfixmaas.data.repository.MediaRepository
import com.example.cityfixmaas.data.repository.MessageRepository
import com.example.cityfixmaas.data.repository.PublicRepository
import com.example.cityfixmaas.data.repository.TicketRepository
import com.example.cityfixmaas.data.repository.UserAdminRepository

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
    val public: PublicRepository
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
    override val public: PublicRepository = FakePublicRepository(backend)
    override val messages: MessageRepository = FakeMessageRepository(backend)
    override val location: LocationProvider = FakeLocationProvider()
}

/** Access the container inside `viewModelFactory { initializer { ... } }`. */
fun CreationExtras.appContainer(): AppContainer =
    (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as CityFixApp).container
