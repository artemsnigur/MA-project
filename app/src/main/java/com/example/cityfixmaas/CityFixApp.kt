package com.example.cityfixmaas

import android.app.Application
import com.example.cityfixmaas.di.AppContainer
import com.example.cityfixmaas.di.FakeAppContainer
import com.example.cityfixmaas.di.FirebaseAppContainer

class CityFixApp : Application() {
    /** Fake data for UI work. Becomes FirebaseAppContainer(this) once B3 is merged. */
    val container: AppContainer by lazy { if (USE_FIREBASE) FirebaseAppContainer(this) else FakeAppContainer() }

    companion object {
        /** Set to true locally to try real sign-in and registration. */
        const val USE_FIREBASE = false
    }
}
