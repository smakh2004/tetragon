package com.tetragon.app.ui

import android.app.Application
import app.rive.runtime.kotlin.core.Rive

class TetragonApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Loads Rive's native library. MUST run before any activity inflates
        // a RiveAnimationView, otherwise inflation crashes.
        Rive.init(this)
    }
}