package com.example.cityfixmaas

import android.app.Application
import com.example.cityfixmaas.di.AppContainer
import com.example.cityfixmaas.di.FakeAppContainer

class CityFixApp : Application() {
    /** Switch to the Firebase container here once B1–B3 are merged. */
    val container: AppContainer by lazy { FakeAppContainer() }
}
