package com.example.tetragon.fragments.registrationFragments

import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class RegistrationCleanupService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onTaskRemoved(rootIntent: Intent?) {
        val auth = FirebaseAuth.getInstance()
        val db = FirebaseFirestore.getInstance()
        val user = auth.currentUser

        if (user != null) {
            // Check if the Firestore profile was actually created
            db.collection("users").document(user.uid).get()
                .addOnSuccessListener { document ->
                    if (!document.exists()) {
                        // Profile doesn't exist; registration was abandoned. Wipe Auth.
                        user.delete()
                    }
                }
        }
        stopSelf()
    }
}