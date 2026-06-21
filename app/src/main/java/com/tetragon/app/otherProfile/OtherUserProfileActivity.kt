package com.tetragon.app.otherProfile

import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.tetragon.app.R
import com.tetragon.app.utils.languageChangeUtils.BaseActivity
import com.tetragon.app.utils.leaderboardUtils.LeaderboardUser
import java.text.SimpleDateFormat
import java.util.*

class OtherUserProfileActivity : BaseActivity() {

    private val db = FirebaseFirestore.getInstance()
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    private val loadingOverlay by lazy { findViewById<FrameLayout>(R.id.loadingOverlayContainer) }
    private val fullNameText by lazy { findViewById<TextView>(R.id.fullNameText) }
    private val xpValueText by lazy { findViewById<TextView>(R.id.xp_value) }
    private val maxStreakText by lazy { findViewById<TextView>(R.id.max_streak_value) }
    private val streakCountText by lazy { findViewById<TextView>(R.id.streak_count_text) }
    private val streakActivationIcon by lazy { findViewById<ImageView>(R.id.streak_activation) }

    private val mathStormText by lazy { findViewById<TextView>(R.id.mathStormCount) }
    private val battleWinsText by lazy { findViewById<TextView>(R.id.battleStormCount) }
    private val cashStormText by lazy { findViewById<TextView>(R.id.cashStormCount) }
    private val leaderboardText by lazy { findViewById<TextView>(R.id.leaderboardPosition) }
    private val onlineStatusDot by lazy { findViewById<View>(R.id.onlineStatusDot) }

    private val weeklyProgressGraph by lazy { findViewById<WeeklyProgressGraphView>(R.id.weeklyProgressGraph) }

    private lateinit var tickViews: List<ImageView>
    private lateinit var dayLabels: List<TextView>

    private var completedQueries = 0
    private val totalQueriesExpected = 4

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_other_user_profile)

        tickViews = listOf(findViewById(R.id.mo_tick), findViewById(R.id.tu_tick), findViewById(R.id.we_tick), findViewById(R.id.th_tick), findViewById(R.id.fr_tick), findViewById(R.id.sa_tick), findViewById(R.id.su_tick))
        dayLabels = listOf(findViewById(R.id.mo_label), findViewById(R.id.tu_label), findViewById(R.id.we_label), findViewById(R.id.th_label), findViewById(R.id.fr_label), findViewById(R.id.sa_label), findViewById(R.id.su_label))

        findViewById<ImageView>(R.id.back_btn).setOnClickListener { finish() }

        val user = intent.getParcelableExtra<LeaderboardUser>("USER_DATA")
        val passedRank = intent.getIntExtra("USER_RANK", 0)
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid

        if (user == null || user.uid.trim().isEmpty() || user.uid.trim() == currentUserId) {
            finish()
            return
        }

        loadingOverlay.visibility = View.VISIBLE
        fullNameText.text = "${user.firstName ?: ""} ${user.lastName ?: ""}".trim().ifEmpty { "Player" }
        xpValueText.text = user.monthlyXP.toString()
        leaderboardText.text = if (passedRank > 0) "#$passedRank" else "-"
        onlineStatusDot.visibility = if (user.isOnline) View.VISIBLE else View.GONE

        updateProfileAvatar(user.avatarName)
        fetchDetailedStats(user.uid.trim())
    }

    private fun fetchDetailedStats(uid: String) {
        val userRef = db.collection("users").document(uid)

        userRef.get().addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val doc = task.result
                if (doc != null && doc.exists()) {
                    fullNameText.text = "${doc.getString("firstName") ?: ""} ${doc.getString("lastName") ?: ""}".trim()
                    xpValueText.text = (doc.getLong("xp") ?: doc.getLong("monthlyXP") ?: 0L).toString()
                    maxStreakText.text = (doc.getLong("maxStreak") ?: 0L).toString()

                    val currentStreak = doc.getLong("streak") ?: 0L
                    streakCountText.text = getString(R.string.day_streak_format, currentStreak.toInt())
                    streakActivationIcon.setImageResource(if (currentStreak > 0) R.drawable.streak else R.drawable.streak_null)

                    val dailyXpMap = doc.get("dailyXPGains") as? Map<*, *> ?: emptyMap<String, Any>()
                    val weeklyStreakMap = doc.get("weeklyStreakDays") as? Map<String, Boolean> ?: emptyMap()
                    val today = getLocalMidnight()

                    val graphScores = FloatArray(7)
                    val graphLabels = Array(7) { "" }
                    for (i in -6..0) {
                        val cal = today.clone() as Calendar
                        cal.add(Calendar.DAY_OF_YEAR, i)
                        val dateKey = dateFormat.format(cal.time)
                        val index = i + 6

                        graphScores[index] = (dailyXpMap[dateKey] as? Number)?.toFloat() ?: 0f
                        graphLabels[index] = getShortDayName(cal)
                    }
                    weeklyProgressGraph.setData(graphScores, graphLabels)

                    for (i in -3..3) {
                        val cal = today.clone() as Calendar
                        cal.add(Calendar.DAY_OF_YEAR, i)
                        val dateKey = dateFormat.format(cal.time)
                        val index = i + 3

                        val isVisited = weeklyStreakMap[dateKey] == true
                        dayLabels[index].text = getShortDayName(cal)
                        tickViews[index].setImageResource(if (isVisited) R.drawable.streak_activated else R.drawable.streak_not_activated)
                        dayLabels[index].setTextColor(ContextCompat.getColor(this, if (isVisited) R.color.text_color else R.color.gray_1))
                    }
                }
            }
            checkQueryProgress()
        }

        val gameRefs = listOf("MathStorm" to mathStormText, "OnlineMathStorm" to battleWinsText, "CashStorm" to cashStormText)
        gameRefs.forEach { (id, view) ->
            userRef.collection("games").document(id).get().addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val key = if (id == "OnlineMathStorm") "onlineScore" else "highScore"
                    view.text = (task.result?.getLong(key) ?: 0L).toString()
                }
                checkQueryProgress()
            }
        }
    }

    private fun checkQueryProgress() {
        completedQueries++
        if (completedQueries >= totalQueriesExpected) {
            loadingOverlay.animate().alpha(0f).setDuration(250).withEndAction { loadingOverlay.visibility = View.GONE }
        }
    }

    private fun getLocalMidnight(): Calendar = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }

    private fun getShortDayName(c: Calendar): String = when (c.get(Calendar.DAY_OF_WEEK)) {
        Calendar.MONDAY -> getString(R.string.mo); Calendar.TUESDAY -> getString(R.string.tu)
        Calendar.WEDNESDAY -> getString(R.string.we); Calendar.THURSDAY -> getString(R.string.th)
        Calendar.FRIDAY -> getString(R.string.fr); Calendar.SATURDAY -> getString(R.string.sa)
        else -> getString(R.string.su)
    }

    private fun updateProfileAvatar(avatarName: String?) {
        val resId = resources.getIdentifier(avatarName ?: "player_icon", "drawable", packageName)
        findViewById<ImageView>(R.id.profileImage).setImageResource(if (resId != 0) resId else R.drawable.avatar_1)
    }
}