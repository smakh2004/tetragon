package com.tetragon.app.fragments.registrationFragments

import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.util.concurrent.TimeUnit

class RegistrationCleanupService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onTaskRemoved(rootIntent: Intent?) {
        val user = FirebaseAuth.getInstance().currentUser
        if (user == null) {
            stopSelf()
            return
        }

        // The old version fired an async Firestore get() and then called stopSelf()
        // immediately. The process is usually torn down within milliseconds of the task
        // being swiped away, so the callback — and therefore user.delete() — almost never
        // ran, leaving orphaned Auth accounts behind. Short blocking waits give the work a
        // real chance to complete; the task is already gone, so there is no UI to stall.
        try {
            val doc = Tasks.await(
                FirebaseFirestore.getInstance().collection("users").document(user.uid).get(),
                3, TimeUnit.SECONDS
            )
            if (!doc.exists()) {
                Tasks.await(user.delete(), 3, TimeUnit.SECONDS)
            }
        } catch (e: Exception) {
            // Best effort only — never crash on teardown.
        } finally {
            stopSelf()
        }
    }
}