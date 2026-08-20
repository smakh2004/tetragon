package com.tetragon.app.utils

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.appcompat.app.AlertDialog
import com.tetragon.app.BuildConfig
import com.tetragon.app.R
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

object AppUpdateManager {

    private const val TAG = "AppUpdateManager"
    private const val COLLECTION = "system"
    private const val DOCUMENT = "appConfig"

    private var dialogShowing = false

    /**
     * Live listener used from MainActivity so a manual Firestore edit shows
     * the dialog immediately for users already inside the app, without
     * requiring a restart.
     */
    fun attachUpdateListener(activity: Activity): ListenerRegistration {
        return FirebaseFirestore.getInstance()
            .collection(COLLECTION)
            .document(DOCUMENT)
            .addSnapshotListener { doc, error ->
                if (error != null || doc == null || !doc.exists()) return@addSnapshotListener
                if (activity.isFinishing || activity.isDestroyed) return@addSnapshotListener

                val minSupported = doc.getLong("minSupportedVersionCode") ?: 0L
                val latest = doc.getLong("latestVersionCode") ?: 0L
                val message = doc.getString("updateMessage")
                val current = BuildConfig.VERSION_CODE.toLong()

                if (current < minSupported || current < latest) {
                    showUpdateDialog(activity, message)
                }
            }
    }

    /**
     * One-shot re-check used on resume so the dialog reappears if the user
     * returned (e.g. from Play Store) without actually updating.
     */
    fun recheckAndShowIfNeeded(activity: Activity) {
        FirebaseFirestore.getInstance()
            .collection(COLLECTION)
            .document(DOCUMENT)
            .get()
            .addOnSuccessListener { doc ->
                if (activity.isFinishing || activity.isDestroyed) return@addOnSuccessListener
                if (!doc.exists()) return@addOnSuccessListener

                val minSupported = doc.getLong("minSupportedVersionCode") ?: 0L
                val latest = doc.getLong("latestVersionCode") ?: 0L
                val message = doc.getString("updateMessage")
                val current = BuildConfig.VERSION_CODE.toLong()

                if (current < minSupported || current < latest) {
                    showUpdateDialog(activity, message)
                }
            }
    }

    private fun showUpdateDialog(activity: Activity, message: String?) {
        if (dialogShowing) return
        dialogShowing = true

        AlertDialog.Builder(activity)
            .setTitle(activity.getString(R.string.update_available_title))
            .setMessage(message ?: activity.getString(R.string.update_available_message))
            .setCancelable(false)
            .setPositiveButton(activity.getString(R.string.update_now)) { _, _ ->
                dialogShowing = false
                openPlayStore(activity)
            }
            .setOnDismissListener { dialogShowing = false }
            .show()
    }

    private fun openPlayStore(activity: Activity) {
        val packageName = activity.packageName
        try {
            activity.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName")).apply {
                    setPackage("com.android.vending")
                }
            )
        } catch (e: Exception) {
            Log.w(TAG, "Play Store app unavailable, falling back to browser", e)
            try {
                activity.startActivity(
                    Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse("https://play.google.com/store/apps/details?id=$packageName")
                    )
                )
            } catch (e2: Exception) {
                Log.e(TAG, "No app available to handle Play Store link", e2)
            }
        }
    }
}