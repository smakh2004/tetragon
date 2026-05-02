package com.example.tetragon.questions

import android.os.Bundle
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import app.rive.runtime.kotlin.core.Rive
import com.example.tetragon.R
import com.example.tetragon.databinding.ActivityStreakGainedBinding
import com.example.tetragon.utils.languageChangeUtils.BaseActivity
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

class StreakGainedActivity : BaseActivity() {

    private lateinit var binding: ActivityStreakGainedBinding

    private val auth by lazy { FirebaseAuth.getInstance() }
    private val db by lazy { FirebaseFirestore.getInstance() }

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    private lateinit var tickViews: List<ImageView>
    private lateinit var dayLabels: List<TextView>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Initialize Rive audio engine
        Rive.init(this)
        binding = ActivityStreakGainedBinding.inflate(layoutInflater)
        setContentView(binding.root)

        tickViews = listOf(
            binding.moTick,
            binding.tuTick,
            binding.weTick,
            binding.thTick,
            binding.frTick,
            binding.saTick,
            binding.suTick
        )

        dayLabels = listOf(
            binding.moLabel,
            binding.tuLabel,
            binding.weLabel,
            binding.thLabel,
            binding.frLabel,
            binding.saLabel,
            binding.suLabel
        )

        val user = auth.currentUser ?: return
        val userDoc = db.collection("users").document(user.uid)

        val todayMidnight = getLocalMidnight()
        val todayKey = dateFormat.format(todayMidnight.time)

        userDoc.get().addOnSuccessListener { snapshot ->

            // ✅ STREAK LOGIC: always based on local midnight
            val lastStreakTs = snapshot.getTimestamp("lastStreakDate")
            val currentStreak = snapshot.getLong("streak") ?: 0L
            val maxStreak = snapshot.getLong("maxStreak") ?: 0L
            val visitedDays = snapshot.get("weeklyStreakDays") as? Map<String, Boolean> ?: emptyMap()

            val updatedVisited = visitedDays.toMutableMap()
            var newStreak = currentStreak

            if (lastStreakTs != null) {
                val lastCal = Calendar.getInstance().apply { time = lastStreakTs.toDate() }
                val lastMidnight = getLocalMidnight(lastCal) // normalize last streak to 00:00

                val diffDays = TimeUnit.MILLISECONDS.toDays(todayMidnight.timeInMillis - lastMidnight.timeInMillis)

                newStreak = when (diffDays) {
                    0L -> currentStreak       // already counted today
                    1L -> currentStreak + 1   // consecutive day
                    else -> 1L                // missed one or more days
                }
            } else {
                newStreak = 1L
            }

            // Update max streak
            val updatedMaxStreak = maxOf(newStreak, maxStreak)

            // Mark today as visited
            val todayKey = dateFormat.format(todayMidnight.time)
            updatedVisited[todayKey] = true

            // Save updates to Firestore
            userDoc.update(
                mapOf(
                    "streak" to newStreak,
                    "maxStreak" to updatedMaxStreak,
                    "lastStreakDate" to Timestamp(todayMidnight.time), // always midnight
                    "weeklyStreakDays" to updatedVisited
                )
            )

            // Display streak
            binding.textView3.text = newStreak.toString()

            // Update the rolling week (today always center)
            updateRollingWeek(updatedVisited, todayMidnight)
        }

        binding.continueEnabledBtn.setOnClickListener {
            finish()
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

    private fun updateRollingWeek(
        visitedDays: Map<String, Boolean>,
        todayCal: Calendar
    ) {
        for (i in -3..3) {
            val cal = todayCal.clone() as Calendar
            cal.add(Calendar.DAY_OF_YEAR, i)

            val dateKey = dateFormat.format(cal.time)
            val dayName = getShortDayName(cal)

            val isVisited = visitedDays[dateKey] == true
            val isFuture = cal.after(todayCal)

            val index = i + 3  // today always center (index 3)

            // Update day label
            dayLabels[index].text = dayName

            // Update tick circle
            tickViews[index].setImageResource(
                when {
                    isFuture -> R.drawable.circle_tick_gray
                    isVisited -> R.drawable.circle_tick_blue
                    else -> R.drawable.circle_tick_gray
                }
            )
        }
    }

    private fun getShortDayName(calendar: Calendar): String {
        return when (calendar.get(Calendar.DAY_OF_WEEK)) {
            Calendar.MONDAY -> getString(R.string.mo)
            Calendar.TUESDAY -> getString(R.string.tu)
            Calendar.WEDNESDAY -> getString(R.string.we)
            Calendar.THURSDAY -> getString(R.string.th)
            Calendar.FRIDAY -> getString(R.string.fr)
            Calendar.SATURDAY -> getString(R.string.sa)
            Calendar.SUNDAY -> getString(R.string.su)
            else -> ""
        }
    }
}