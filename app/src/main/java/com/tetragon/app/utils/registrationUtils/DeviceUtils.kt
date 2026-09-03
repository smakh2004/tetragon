package com.tetragon.app.utils.registrationUtils

import android.annotation.SuppressLint
import android.content.Context
import android.provider.Settings
import java.util.UUID

object DeviceUtils {

    private const val PREFS = "device_prefs"
    private const val KEY_FALLBACK_ID = "fallback_device_id"

    /**
     * Settings.Secure.getString() returns a platform type that CAN be null (some ROMs and
     * emulator images, and before the first unlock on a few devices). The old signature
     * declared a non-null String, so Kotlin's inserted null check threw a
     * NullPointerException instead of returning anything — and that crash would land in
     * the middle of registration or login, where this is called.
     *
     * A random UUID persisted in SharedPreferences is used as the fallback. It stays
     * stable for the install, which is all the single-active-device session check needs.
     */
    @SuppressLint("HardwareIds")
    fun getDeviceId(context: Context): String {
        val androidId: String? = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ANDROID_ID
        )

        if (!androidId.isNullOrBlank() && androidId != "9774d56d682e549c") {
            // ("9774d56d682e549c" is a known duplicate ANDROID_ID shared by many buggy devices.)
            return androidId
        }

        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.getString(KEY_FALLBACK_ID, null)?.let { return it }

        val generated = UUID.randomUUID().toString()
        prefs.edit().putString(KEY_FALLBACK_ID, generated).apply()
        return generated
    }
}