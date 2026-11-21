package com.example.cityfixmaas

import android.app.Application
import com.example.cityfixmaas.di.AppContainer
import com.example.cityfixmaas.di.FakeAppContainer
import com.example.cityfixmaas.di.FirebaseAppContainer

class CityFixApp : Application() {
    /** Real Firebase data; set USE_FIREBASE = false to try screens with the in-memory fakes. */
    val container: AppContainer by lazy { if (USE_FIREBASE) FirebaseAppContainer(this) else FakeAppContainer() }

    companion object {
        const val USE_FIREBASE = false
    }
}
