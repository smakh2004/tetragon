package com.example.tetragon.fragments

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.example.tetragon.R
import com.example.tetragon.streakCalendar.StreakCalendarActivity
import com.example.tetragon.ui.uiSettings.SettingsActivity
import com.example.tetragon.ui.WelcomeActivity
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

class ProfileFragment : Fragment(R.layout.fragment_profile) {

    // Profile
    private lateinit var fullNameText: TextView
    private lateinit var joinedText: TextView
    private lateinit var settingsIcon: ImageView

    // Weekly streak
    private lateinit var streakCountText: TextView
    private lateinit var streakSubtitle: TextView
    private lateinit var tickViews: List<ImageView>
    private lateinit var dayLabels: List<TextView>
    private lateinit var streakContainer: LinearLayout

    // Bottom stats
    private lateinit var maxStreakText: TextView
    private lateinit var xpText: TextView
    private lateinit var battleWinsText: TextView
    private lateinit var mathStormText: TextView
    private lateinit var levelText: TextView

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Profile info
        fullNameText = view.findViewById(R.id.fullNameText)
        joinedText = view.findViewById(R.id.joinedText)
        settingsIcon = view.findViewById(R.id.settings)

        // Streak UI
        streakCountText = view.findViewById(R.id.streak_count_text)
        streakSubtitle = view.findViewById(R.id.streak_subtitle)
        tickViews = listOf(
            view.findViewById(R.id.mo_tick),
            view.findViewById(R.id.tu_tick),
            view.findViewById(R.id.we_tick),
            view.findViewById(R.id.th_tick),
            view.findViewById(R.id.fr_tick),
            view.findViewById(R.id.sa_tick),
            view.findViewById(R.id.su_tick)
        )
        dayLabels = listOf(
            view.findViewById(R.id.mo_label),
            view.findViewById(R.id.tu_label),
            view.findViewById(R.id.we_label),
            view.findViewById(R.id.th_label),
            view.findViewById(R.id.fr_label),
            view.findViewById(R.id.sa_label),
            view.findViewById(R.id.su_label)
        )
        streakContainer = view.findViewById(R.id.linearLayout7)

        // Bottom stats UI
        maxStreakText = view.findViewById(R.id.max_streak_value)
        xpText = view.findViewById(R.id.xp_value)
        battleWinsText = view.findViewById(R.id.battleStormCount)
        mathStormText = view.findViewById(R.id.mathStormCount)
        levelText = view.findViewById(R.id.level)

        loadUserData()
        loadStreak()
        loadBottomStats()

        // Set Navigation Click Listener
        streakContainer.setOnClickListener {
            val intent = Intent(requireContext(), StreakCalendarActivity::class.java)
            startActivity(intent)
        }
    }

    private fun loadUserData() {
        val userId = auth.currentUser?.uid ?: return

        settingsIcon.setOnClickListener {
            startActivity(Intent(requireContext(), SettingsActivity::class.java))
            requireActivity().overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
        }

        db.collection("users")
            .document(userId)
            .get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    val firstName = document.getString("firstName") ?: ""
                    val lastName = document.getString("lastName") ?: ""
                    fullNameText.text = "$firstName $lastName"

                    val registeredAt = document.getTimestamp("registeredAt")
                    registeredAt?.let {
                        val formatter = SimpleDateFormat("MMMM yyyy", Locale.ENGLISH)
                        joinedText.text = "Joined ${formatter.format(it.toDate())}"
                    }
                    val level = document.getLong("level") ?: 1L
                    levelText.text = "Level $level"
                }
            }
            .addOnFailureListener {
                startActivity(Intent(requireContext(), WelcomeActivity::class.java))
            }
    }

    private fun loadStreak() {
        val user = auth.currentUser ?: return
        val userDoc = db.collection("users").document(user.uid)

        userDoc.get().addOnSuccessListener { snapshot ->

            val currentStreak = snapshot.getLong("streak") ?: 0L
            val maxStreak = snapshot.getLong("maxStreak") ?: 0L
            val visitedDays =
                snapshot.get("weeklyStreakDays") as? Map<String, Boolean>
                    ?: emptyMap()

            // ✅ JUST SHOW DATA (NO CALCULATION, NO UPDATE)

            streakCountText.text = "$currentStreak day streak"

            streakSubtitle.text = when {
                currentStreak > 10 -> "YOU ARE BEAST!"
                currentStreak > 0 -> "GOOD JOB!"
                else -> "TIME FOR PRACTICE!"
            }

            val streakImage =
                if (currentStreak > 0)
                    R.drawable.streak
                else
                    R.drawable.streak_null

            view?.findViewById<ImageView>(R.id.streak_activation)
                ?.setImageResource(streakImage)

            maxStreakText.text = maxStreak.toString()

            // Show rolling week based on stored data
            val todayMidnight = getLocalMidnight()
            updateRollingWeek(visitedDays, todayMidnight)
        }
    }

    private fun loadBottomStats() {
        val userId = auth.currentUser?.uid ?: return
        val userRef = db.collection("users").document(userId)

        // Fetch XP
        userRef.get().addOnSuccessListener { doc ->
            val xp = doc.getLong("xp") ?: 0L
            xpText.text = xp.toString()
        }

        // Fetch MathStorm High Score
        userRef
            .collection("games")
            .document("MathStorm")
            .get()
            .addOnSuccessListener { doc ->
                val mathHighScore = doc.getLong("highScore") ?: 0L
                mathStormText.text = mathHighScore.toString()
            }

        // Fetch Online MathStorm Score
        userRef
            .collection("games")
            .document("OnlineMathStorm")
            .get()
            .addOnSuccessListener { doc ->
                val onlineScore = doc.getLong("onlineScore") ?: 0L
                battleWinsText.text = onlineScore.toString()
            }
    }

    private fun getLocalMidnight(): Calendar {
        return getLocalMidnight(Calendar.getInstance())
    }

    private fun getLocalMidnight(calendar: Calendar): Calendar {
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar
    }

    private fun updateRollingWeek(visitedDays: Map<String, Boolean>, todayCal: Calendar) {
        for (i in -3..3) {
            val cal = todayCal.clone() as Calendar
            cal.add(Calendar.DAY_OF_YEAR, i)

            val dateKey = dateFormat.format(cal.time)
            val dayName = getShortDayName(cal)
            val isVisited = visitedDays[dateKey] == true
            val isFuture = cal.after(todayCal)
            val index = i + 3 // today is center

            // Update label
            dayLabels[index].text = dayName
            tickViews[index].setImageResource(
                when {
                    isFuture -> R.drawable.streak_not_activated
                    isVisited -> R.drawable.streak_activated
                    else -> R.drawable.streak_not_activated
                }
            )
            dayLabels[index].setTextColor(
                if (isVisited) resources.getColor(R.color.text_color, null)
                else resources.getColor(R.color.gray_1, null)
            )
        }
    }

    private fun getShortDayName(calendar: Calendar): String {
        return when (calendar.get(Calendar.DAY_OF_WEEK)) {
            Calendar.MONDAY -> "Mo"
            Calendar.TUESDAY -> "Tu"
            Calendar.WEDNESDAY -> "We"
            Calendar.THURSDAY -> "Th"
            Calendar.FRIDAY -> "Fr"
            Calendar.SATURDAY -> "Sa"
            Calendar.SUNDAY -> "Su"
            else -> ""
        }
    }
}