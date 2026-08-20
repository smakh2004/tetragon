package com.tetragon.app.fragments

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.widget.NestedScrollView
import androidx.fragment.app.Fragment
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import app.rive.runtime.kotlin.RiveAnimationView
import app.rive.runtime.kotlin.core.Rive
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.tetragon.app.R
import com.tetragon.app.avatarSelection.AvatarSelectionActivity
import com.tetragon.app.otherProfile.WeeklyProgressGraphView
import com.tetragon.app.streakCalendar.StreakCalendarActivity
import com.tetragon.app.ui.WelcomeActivity
import com.tetragon.app.ui.uiSettings.SettingsActivity
import java.text.SimpleDateFormat
import java.util.*

class ProfileFragment : Fragment(R.layout.fragment_profile) {

    private lateinit var fullNameText: TextView
    private lateinit var joinedText: TextView
    private lateinit var settingsIcon: ImageView

    private lateinit var streakCountText: TextView
    private lateinit var streakSubtitle: TextView
    private lateinit var tickViews: List<ImageView>
    private lateinit var dayLabels: List<TextView>
    private lateinit var streakContainer: LinearLayout

    private lateinit var maxStreakText: TextView
    private lateinit var xpText: TextView
    private lateinit var mathStormText: TextView
    private lateinit var battleWinsText: TextView
    private lateinit var cashStormText: TextView
    private lateinit var leaderboardText: TextView

    private lateinit var loadingOverlayContainer: FrameLayout
    private lateinit var weeklyProgressGraph: WeeklyProgressGraphView
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout

    private lateinit var profileRiveAvatar: RiveAnimationView

    // Category keys that map to Rive number properties (matches the avatar creator)
    private val avatarNumberKeys = listOf("face", "hair", "glasses", "hat", "mustache", "body")

    // Default config applied and saved on first login (all values 1, default background)
    private val defaultBackgroundHex = "#00AEEF"
    private fun buildDefaultAvatarConfig(): HashMap<String, Any> {
        val config = HashMap<String, Any>()
        avatarNumberKeys.forEach { config[it] = 1L }
        config["backgroundColor"] = defaultBackgroundHex
        return config
    }

    private var completedQueries = 0
    private val totalQueriesExpected = 6

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        try {
            Rive.init(requireContext())
        } catch (_: Exception) {
        }

        val profileScrollView = view.findViewById<NestedScrollView>(R.id.profileScrollView)
        val profileLineDivider = view.findViewById<View>(R.id.profileLineDivider)
        loadingOverlayContainer = view.findViewById(R.id.loadingOverlayContainer)
        weeklyProgressGraph = view.findViewById(R.id.weeklyProgressGraph)
        swipeRefreshLayout = view.findViewById(R.id.swipeRefreshLayout)
        profileRiveAvatar = view.findViewById(R.id.profileRiveAvatar)

        context?.let { ctx ->
            swipeRefreshLayout.setColorSchemeColors(ContextCompat.getColor(ctx, R.color.blue_2))
        }
        swipeRefreshLayout.setSlingshotDistance(0)
        swipeRefreshLayout.setProgressViewEndTarget(false, 140)
        swipeRefreshLayout.setOnRefreshListener { refreshPageData(isManualSwipe = true) }

        fullNameText = view.findViewById(R.id.fullNameText)
        joinedText = view.findViewById(R.id.joinedText)
        settingsIcon = view.findViewById(R.id.settings)

        streakCountText = view.findViewById(R.id.streak_count_text)
        streakSubtitle = view.findViewById(R.id.streak_subtitle)
        tickViews = listOf(view.findViewById(R.id.mo_tick), view.findViewById(R.id.tu_tick), view.findViewById(R.id.we_tick), view.findViewById(R.id.th_tick), view.findViewById(R.id.fr_tick), view.findViewById(R.id.sa_tick), view.findViewById(R.id.su_tick))
        dayLabels = listOf(view.findViewById(R.id.mo_label), view.findViewById(R.id.tu_label), view.findViewById(R.id.we_label), view.findViewById(R.id.th_label), view.findViewById(R.id.fr_label), view.findViewById(R.id.sa_label), view.findViewById(R.id.su_label))
        streakContainer = view.findViewById(R.id.linearLayout7)

        maxStreakText = view.findViewById(R.id.max_streak_value)
        xpText = view.findViewById(R.id.xp_value)
        mathStormText = view.findViewById(R.id.mathStormCount)
        battleWinsText = view.findViewById(R.id.battleStormCount)
        cashStormText = view.findViewById(R.id.cashStormCount)
        leaderboardText = view.findViewById(R.id.leaderboardPosition)

        // Divider appears only when the scrollable content is scrolled
        profileScrollView.setOnScrollChangeListener(NestedScrollView.OnScrollChangeListener { _, _, scrollY, _, _ ->
            profileLineDivider.visibility = if (scrollY > 0) View.VISIBLE else View.INVISIBLE
        })

        streakContainer.setOnClickListener { startActivity(Intent(requireContext(), StreakCalendarActivity::class.java)) }
        settingsIcon.setOnClickListener {
            context?.let {
                startActivity(Intent(it, SettingsActivity::class.java))
                requireActivity().overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
            }
        }

        // Tap the avatar animation itself → open the creator
        profileRiveAvatar.setOnClickListener {
            startActivity(Intent(requireContext(), AvatarSelectionActivity::class.java))
            requireActivity().overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
        }

        refreshPageData(isManualSwipe = false)
    }

    private fun refreshPageData(isManualSwipe: Boolean) {
        completedQueries = 0

        if (!isManualSwipe) {
            loadingOverlayContainer.alpha = 1f
            loadingOverlayContainer.visibility = View.VISIBLE
        }

        loadUserData()
        loadStreak()
        loadBottomStats()
        loadRank()
    }

    private fun loadStreak() {
        val user = auth.currentUser ?: return
        db.collection("users").document(user.uid).get().addOnSuccessListener { snapshot ->
            if (!isAdded) return@addOnSuccessListener

            val currentStreak = snapshot.getLong("streak") ?: 0L
            maxStreakText.text = (snapshot.getLong("maxStreak") ?: 0L).toString()
            streakCountText.text = getString(R.string.day_streak_format, currentStreak.toInt())
            streakSubtitle.text = when {
                currentStreak > 10 -> getString(R.string.streak_beast)
                currentStreak > 0 -> getString(R.string.streak_good)
                else -> getString(R.string.streak_practice)
            }
            view?.findViewById<ImageView>(R.id.streak_activation)?.setImageResource(if (currentStreak > 0) R.drawable.streak else R.drawable.streak_null)

            updateRollingWeek(snapshot.get("weeklyStreakDays") as? Map<String, Boolean> ?: emptyMap(), getLocalMidnight())

            val dailyXpMap = snapshot.get("dailyXPGains") as? Map<*, *> ?: emptyMap<String, Any>()
            updateGraph(dailyXpMap)

            checkQueryProgress()
        }
    }

    private fun updateGraph(dailyXpMap: Map<*, *>) {
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
    }

    private fun checkQueryProgress() {
        completedQueries++
        if (completedQueries >= totalQueriesExpected) {
            if (!isAdded || context == null) return
            swipeRefreshLayout.isRefreshing = false
            if (loadingOverlayContainer.visibility == View.VISIBLE) {
                loadingOverlayContainer.animate().alpha(0f).setDuration(250).withEndAction {
                    if (isAdded && context != null) loadingOverlayContainer.visibility = View.GONE
                }
            }
        }
    }

    private fun loadRank() {
        val userId = auth.currentUser?.uid ?: return
        db.collection("users").orderBy("monthlyXP", Query.Direction.DESCENDING).get().addOnSuccessListener { snapshot ->
            var rank = 0
            for ((index, doc) in snapshot.withIndex()) { if (doc.id == userId) { rank = index + 1; break } }
            if (isAdded) leaderboardText.text = if (rank > 0) "#$rank" else "-"
            checkQueryProgress()
        }.addOnFailureListener { checkQueryProgress() }
    }

    private fun loadUserData() {
        val userId = auth.currentUser?.uid ?: return
        db.collection("users").document(userId).get().addOnSuccessListener { document ->
            if (!isAdded) return@addOnSuccessListener
            if (document != null && document.exists()) {

                val existingConfig = document.get("avatarConfig") as? Map<*, *>
                if (existingConfig == null) {
                    // First login (no avatar yet) → assign all default values (1) and save them
                    val defaultConfig = buildDefaultAvatarConfig()
                    db.collection("users").document(userId)
                        .update("avatarConfig", defaultConfig)
                    applyAvatarConfig(defaultConfig)
                } else {
                    applyAvatarConfig(existingConfig)
                }

                fullNameText.text = "${document.getString("firstName") ?: ""} ${document.getString("lastName") ?: ""}"
                document.getTimestamp("registeredAt")?.let {
                    joinedText.text = getString(R.string.joined_format, SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(it.toDate()))
                }
            }
            checkQueryProgress()
        }.addOnFailureListener { if (isAdded) startActivity(Intent(requireContext(), WelcomeActivity::class.java)) }
    }

    private fun loadBottomStats() {
        val userId = auth.currentUser?.uid ?: return
        val userRef = db.collection("users").document(userId)
        userRef.get().addOnSuccessListener { doc -> xpText.text = (doc.getLong("xp") ?: 0L).toString(); checkQueryProgress() }
        userRef.collection("games").document("MathStorm").get().addOnSuccessListener { doc -> mathStormText.text = (doc.getLong("highScore") ?: 0L).toString(); checkQueryProgress() }
        userRef.collection("games").document("OnlineMathStorm").get().addOnSuccessListener { doc -> battleWinsText.text = (doc.getLong("onlineScore") ?: 0L).toString(); checkQueryProgress() }
        userRef.collection("games").document("CashStorm").get().addOnSuccessListener { doc -> cashStormText.text = (doc.getLong("highScore") ?: 0L).toString(); checkQueryProgress() }
    }

    // Apply a saved (or default) avatarConfig map to the profile's Rive avatar
    private fun applyAvatarConfig(config: Map<*, *>?) {
        if (!isAdded || config == null) return

        profileRiveAvatar.post {
            try {
                val file = profileRiveAvatar.controller.file ?: return@post
                val vm = file.getViewModelByName("ViewModel1") ?: return@post
                val vmi = vm.createDefaultInstance()
                profileRiveAvatar.controller.stateMachines.firstOrNull()?.viewModelInstance = vmi

                avatarNumberKeys.forEach { key ->
                    val value = (config[key] as? Number)?.toInt() ?: 1
                    vmi.getNumberProperty(key)?.value = value.toFloat()
                    if (key == "hat") {
                        vmi.getBooleanProperty("hatOn")?.value = value > 1
                    }
                }

                // Background color
                (config["backgroundColor"] as? String)?.let { hex ->
                    runCatching { Color.parseColor(hex) }.getOrNull()?.let { color ->
                        vmi.getColorProperty("backgroundColor")?.value = color
                    }
                }

                // >>> ADD HERE: per-part colors <
                val colorProps = listOf("skinColor", "hairColor", "glassColor", "capColor", "mustacheColor", "clothColor")
                colorProps.forEach { propName ->
                    (config[propName] as? String)?.let { hex ->
                        runCatching { Color.parseColor(hex) }.getOrNull()?.let { c ->
                            vmi.getColorProperty(propName)?.value = c
                        }
                    }
                }

            } catch (e: Exception) {
                android.util.Log.e("ProfileFragment", "Avatar config error: ${e.message}")
            }
        }
    }

    private fun getLocalMidnight(): Calendar = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }

    private fun updateRollingWeek(visitedDays: Map<String, Boolean>, todayCal: Calendar) {
        for (i in -3..3) {
            val cal = todayCal.clone() as Calendar
            cal.add(Calendar.DAY_OF_YEAR, i)
            val dateKey = dateFormat.format(cal.time)
            val isVisited = visitedDays[dateKey] == true
            val index = i + 3
            dayLabels[index].text = getShortDayName(cal)
            tickViews[index].setImageResource(if (isVisited) R.drawable.streak_activated else R.drawable.streak_not_activated)
            dayLabels[index].setTextColor(resources.getColor(if (isVisited) R.color.text_color else R.color.gray_1, null))
        }
    }

    private fun getShortDayName(c: Calendar): String = when (c.get(Calendar.DAY_OF_WEEK)) {
        Calendar.MONDAY -> getString(R.string.mo); Calendar.TUESDAY -> getString(R.string.tu); Calendar.WEDNESDAY -> getString(R.string.we); Calendar.THURSDAY -> getString(R.string.th); Calendar.FRIDAY -> getString(R.string.fr); Calendar.SATURDAY -> getString(R.string.sa); Calendar.SUNDAY -> getString(R.string.su); else -> ""
    }

    override fun onResume() {
        super.onResume()
        if (::profileRiveAvatar.isInitialized) profileRiveAvatar.play()
        auth.currentUser?.uid?.let { uid ->
            db.collection("users").document(uid).get().addOnSuccessListener { doc ->
                if (isAdded) applyAvatarConfig(doc.get("avatarConfig") as? Map<*, *>)
            }
        }
    }

    override fun onPause() {
        super.onPause()
        if (::profileRiveAvatar.isInitialized) profileRiveAvatar.pause()
    }
}