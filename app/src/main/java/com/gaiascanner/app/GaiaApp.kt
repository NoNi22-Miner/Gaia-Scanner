package com.gaiascanner.app

import android.app.Application
import android.content.Context
import com.gaiascanner.app.work.Notifications

class GaiaApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        Notifications.createChannel(this)
    }
}

val Context.appContainer: AppContainer
    get() = (applicationContext as GaiaApp).container
