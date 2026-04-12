package com.example.tetragon.connectivityCheck.userPresenceUtils

import android.app.Application
import androidx.lifecycle.ProcessLifecycleOwner
import com.example.tetragon.connectivityCheck.AppLifecycleObserver

class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        // Start tracking user presence when app launches
        UserPresenceHelper.startTracking()

        // Track foreground/background to set offline
        ProcessLifecycleOwner.Companion.get().lifecycle.addObserver(AppLifecycleObserver())
    }
}