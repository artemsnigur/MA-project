package com.example.cityfixmaas

import android.app.Application
import com.example.cityfixmaas.di.AppContainer
import com.example.cityfixmaas.di.FakeAppContainer
import com.example.cityfixmaas.di.FirebaseAppContainer

class CityFixApp : Application() {
    /** Fakes until B3 is merged; then the Firebase container becomes the only one. */
    val container: AppContainer by lazy {
        if (USE_FIREBASE) FirebaseAppContainer(this) else FakeAppContainer()
    }

    companion object {
        /** Set to true locally to try real sign-in and registration (B1). Keep false in commits until B3. */
        const val USE_FIREBASE = false
    }
}
