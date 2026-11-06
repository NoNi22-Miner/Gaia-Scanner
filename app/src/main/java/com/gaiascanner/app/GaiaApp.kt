package com.gaiascanner.app

import android.app.Application
import android.content.Context
import android.util.Log
import com.gaiascanner.app.work.Notifications
import kotlinx.coroutines.launch

class GaiaApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        Notifications.createChannel(this)
        // Trash older than 30 days is deleted for good (owner decision, phase 3).
        container.appScope.launch {
            runCatching { container.library.purgeOldTrash() }
                .onFailure { Log.w("GaiaApp", "Trash clean-up failed", it) }
        }
    }
}

val Context.appContainer: AppContainer
    get() = (applicationContext as GaiaApp).container
