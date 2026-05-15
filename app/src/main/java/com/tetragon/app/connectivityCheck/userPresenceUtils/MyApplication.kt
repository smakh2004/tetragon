package com.tetragon.app.connectivityCheck.userPresenceUtils

import android.app.Application
import androidx.lifecycle.ProcessLifecycleOwner
import com.tetragon.app.connectivityCheck.AppLifecycleObserver

class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        // Start tracking user presence when app launches
        UserPresenceHelper.startTracking()

        // Track foreground/background to set offline
        ProcessLifecycleOwner.Companion.get().lifecycle.addObserver(AppLifecycleObserver())
    }
}