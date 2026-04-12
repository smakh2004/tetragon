package com.example.tetragon.utils.soundUtils

import android.content.Context

object SoundManager {

    private const val PREF_NAME = "app_settings"
    private const val KEY_SOUND = "sound_enabled"

    fun isSoundEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_SOUND, true) // default = ON
    }

    fun setSoundEnabled(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_SOUND, enabled).apply()
    }
}