package com.tetragon.app.connectivityCheck

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.tetragon.app.connectivityCheck.userPresenceUtils.UserPresenceHelper

class AppLifecycleObserver : DefaultLifecycleObserver {

    override fun onPause(owner: LifecycleOwner) {
        super.onPause(owner)
        // App goes to background → mark offline
        UserPresenceHelper.setOffline()
    }

    override fun onResume(owner: LifecycleOwner) {
        super.onResume(owner)
        // App comes to foreground → mark online
        UserPresenceHelper.startTracking()
    }

    override fun onStop(owner: LifecycleOwner) {
        super.onStop(owner)
        // The user exited to the Home screen or switched apps
        UserPresenceHelper.setOffline()
    }

    override fun onStart(owner: LifecycleOwner) {
        super.onStart(owner)
        // The user returned to the app
        UserPresenceHelper.startTracking()
    }
}