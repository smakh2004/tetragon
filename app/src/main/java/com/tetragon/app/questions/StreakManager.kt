package com.tetragon.app.questions

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.util.Calendar

object StreakManager {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    fun checkAndResetIfMissed() {

        val user = auth.currentUser ?: return
        val userDoc = db.collection("users").document(user.uid)

        userDoc.get().addOnSuccessListener { snapshot ->

            val currentStreak = snapshot.getLong("streak") ?: 0L
            if (currentStreak == 0L) return@addOnSuccessListener

            val visitedDays =
                snapshot.get("weeklyStreakDays") as? Map<String, Boolean>
                    ?: emptyMap()

            val todayCal = getMidnight(Calendar.getInstance())
            val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())

            val todayKey = dateFormat.format(todayCal.time)

            val yesterdayCal = todayCal.clone() as Calendar
            yesterdayCal.add(Calendar.DAY_OF_YEAR, -1)
            val yesterdayKey = dateFormat.format(yesterdayCal.time)

            val todayVisited = visitedDays[todayKey] == true
            val yesterdayVisited = visitedDays[yesterdayKey] == true

            // ✅ Reset ONLY if user missed yesterday AND hasn't completed today
            if (!yesterdayVisited && !todayVisited) {
                userDoc.update("streak", 0L)
            }
        }
    }

    private fun getMidnight(calendar: Calendar): Calendar {
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar
    }
}