package com.example.cityfixmaas

import android.app.Application
import com.example.cityfixmaas.di.AppContainer
import com.example.cityfixmaas.di.FakeAppContainer
import com.example.cityfixmaas.di.FirebaseAppContainer
import com.example.cityfixmaas.notifications.TicketWatcher
import com.example.cityfixmaas.worker.TicketCheckWorker
import kotlinx.coroutines.MainScope

class CityFixApp : Application() {
    /** Real Firebase data; set USE_FIREBASE = false to try screens with the in-memory fakes. */
    val container: AppContainer by lazy { if (USE_FIREBASE) FirebaseAppContainer(this) else FakeAppContainer() }

    /** Ticket notifications and the automatic close after 5 days. */
    val watcher by lazy { TicketWatcher(this, container) }

    override fun onCreate() {
        super.onCreate()
        watcher.start(MainScope()) // lives as long as the app process
        TicketCheckWorker.schedule(this)
    }

    companion object {
        const val USE_FIREBASE = true
    }
}
