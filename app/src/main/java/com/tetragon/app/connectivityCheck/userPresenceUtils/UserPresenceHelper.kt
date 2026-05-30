package com.tetragon.app.connectivityCheck.userPresenceUtils

import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ServerValue
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.database
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.firestore

object UserPresenceHelper {

    private val auth = FirebaseAuth.getInstance()
    private val database = Firebase.database.reference
    private val firestore = Firebase.firestore

    // FIXED: Keeps a reference to the active network listener so it can be un-hooked cleanly
    private var connectedListener: ValueEventListener? = null

    fun startTracking() {
        val currentUser = auth.currentUser ?: return
        val userId = currentUser.uid

        val userStatusDbRef = database.child("status/$userId")
        val userStatusFirestoreRef = firestore.collection("users").document(userId)

        val isOffline = mapOf(
            "state" to "offline",
            "lastChanged" to ServerValue.TIMESTAMP
        )

        val isOnlineBase = mapOf(
            "state" to "online",
            "lastChanged" to ServerValue.TIMESTAMP
        )

        // Ensure we don't duplicate listeners if called multiple times sequentially
        stopTracking()

        // Get firstName from Firestore
        userStatusFirestoreRef.get().addOnSuccessListener { snapshot ->
            // Safety Check: If user timed out or was kicked while fetching name, cancel operation
            if (auth.currentUser == null) return@addOnSuccessListener

            val firstName = snapshot.getString("firstName") ?: "Unknown"
            val isOnline = isOnlineBase + mapOf("firstName" to firstName)

            connectedListener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val connected = snapshot.getValue(Boolean::class.java) ?: false
                    if (connected) {
                        // Set online in Realtime DB including firstName
                        userStatusDbRef.setValue(isOnline)
                        userStatusDbRef.onDisconnect().setValue(isOffline)

                        // Update Firestore online state
                        userStatusFirestoreRef.set(
                            mapOf(
                                "online" to true,
                                "lastOnline" to FieldValue.serverTimestamp()
                            ),
                            SetOptions.merge()
                        )
                    } else {
                        // If disconnected → mark offline in Firestore
                        userStatusFirestoreRef.set(
                            mapOf(
                                "online" to false,
                                "lastOnline" to FieldValue.serverTimestamp()
                            ),
                            SetOptions.merge()
                        )
                    }
                }

                override fun onCancelled(error: DatabaseError) {}
            }

            // Listen connection state
            database.child(".info/connected").addValueEventListener(connectedListener as ValueEventListener)
        }
    }

    // FIXED: New explicit removal strategy called by MainActivity to prevent loops
    fun stopTracking() {
        connectedListener?.let {
            database.child(".info/connected").removeEventListener(it)
            connectedListener = null
        }
    }

    fun setOffline() {
        // Automatically isolate structural logic during logout
        stopTracking()

        val currentUser = auth.currentUser ?: return
        val userId = currentUser.uid

        val userStatusDbRef = database.child("status/$userId")
        val userStatusFirestoreRef = firestore.collection("users").document(userId)

        val isOffline = mapOf(
            "state" to "offline",
            "lastChanged" to ServerValue.TIMESTAMP
        )

        userStatusDbRef.setValue(isOffline)

        userStatusFirestoreRef.set(
            mapOf(
                "online" to false,
                "lastOnline" to FieldValue.serverTimestamp()
            ),
            SetOptions.merge()
        )
    }
}