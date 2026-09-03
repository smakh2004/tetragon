package com.tetragon.app.fragments

import android.graphics.Color
import android.os.Bundle
import android.os.CountDownTimer
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import app.rive.runtime.kotlin.RiveAnimationView
import app.rive.runtime.kotlin.core.Rive
import app.rive.runtime.kotlin.core.ViewModelInstance
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.tetragon.app.R
import com.tetragon.app.otherProfile.OtherUserProfileActivity
import com.tetragon.app.utils.leaderboardUtils.LeaderboardAdapter
import com.tetragon.app.utils.leaderboardUtils.LeaderboardUser
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class LeaderboardFragment : Fragment() {

    // ---------------- UI ----------------
    private lateinit var recycler: RecyclerView
    private lateinit var leaderboardRive: RiveAnimationView
    private lateinit var loadingLayout: LinearLayout
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout

    // ---------------- Firebase ----------------
    private val db = FirebaseFirestore.getInstance()
    private val rtdb = FirebaseDatabase.getInstance().getReference("status")

    private var leaderboardListener: ListenerRegistration? = null

    /** Only 4th place and below — the podium lives inside the Rive animation. */
    private val usersList = mutableListOf<LeaderboardUser>()
    private var leaderboardAdapter: LeaderboardAdapter? = null

    private var activeCountdownTimer: CountDownTimer? = null

    // ---------------- Rive top block (ViewModel2) ----------------
    private var leaderboardVmi: ViewModelInstance? = null
    private val firstSlot = PlaceSlot(PATH_FIRST_PLACE)
    private val secondSlot = PlaceSlot(PATH_SECOND_PLACE)
    private val thirdSlot = PlaceSlot(PATH_THIRD_PLACE)

    /** Everything produced before the .riv finished loading, flushed on bind. */
    private var pendingDate: String? = null
    private var pendingDaysLeft: String? = null
    private var pendingPlace: String? = null

    private var podium: List<LeaderboardUser> = emptyList()

    companion object {
        private const val MAX_RIVE_ATTEMPTS = 60
        private const val RIVE_RETRY_DELAY_MS = 50L

        /** Root view model bound to the leaderboard artboard. */
        private const val LEADERBOARD_VIEW_MODEL_NAME = "ViewModel2"

        /** Nested ViewModel1 instances inside ViewModel2. */
        private const val PATH_FIRST_PLACE = "firstPlace"
        private const val PATH_SECOND_PLACE = "secondPlace"
        private const val PATH_THIRD_PLACE = "thirdPlace"

        // ViewModel2 strings
        private const val PROP_LEADERBOARD_STRING = "leaderboardString"
        private const val PROP_LEADERBOARD_DESCRIPTION = "leaderboardDescription"
        private const val PROP_DATE = "date"
        private const val PROP_DAYS_LEFT = "daysLeft"
        private const val PROP_PLACE = "place"

        /** ViewModel1 — String, carries the label itself ("50 XP"). */
        private const val PROP_XP = "xp"

        /** ViewModel1 — podium player name. */
        private const val PROP_FIRST_NAME = "firstName"

        /**
         * Rive text runs don't ellipsize, so long names are cut here.
         * The podium slots are narrow — raise this if your artboard has more room.
         */
        private const val MAX_PODIUM_NAME_CHARS = 10

        private val AVATAR_NUMBERS =
            listOf("face", "hair", "glasses", "hat", "mustache", "body")

        private val AVATAR_COLORS = listOf(
            "skinColor", "hairColor", "glassColor", "capColor",
            "mustacheColor", "clothColor", "backgroundColor", "eyebrowColor", "eyeColor"
        )

        private const val PODIUM_SIZE = 3

        /** The RecyclerView starts at 4th place. */
        private const val LIST_START_RANK = PODIUM_SIZE + 1
    }

    /**
     * One podium position. Writes go to the nested ViewModel1 instance when the runtime
     * hands one back, otherwise they fall back to path access on the root
     * ("firstPlace/xp"), exactly like the battle top bar.
     */
    private inner class PlaceSlot(val path: String) {
        var vmi: ViewModelInstance? = null

        fun number(name: String, value: Float) {
            val direct = vmi
            if (direct != null) {
                runCatching { direct.getNumberProperty(name)?.value = value }
            } else {
                leaderboardVmi?.let {
                    runCatching { it.getNumberProperty("$path/$name")?.value = value }
                }
            }
        }

        fun string(name: String, value: String) {
            val direct = vmi
            if (direct != null) {
                runCatching { direct.getStringProperty(name)?.value = value }
            } else {
                leaderboardVmi?.let {
                    runCatching { it.getStringProperty("$path/$name")?.value = value }
                }
            }
        }

        fun boolean(name: String, value: Boolean) {
            val direct = vmi
            if (direct != null) {
                runCatching { direct.getBooleanProperty(name)?.value = value }
            } else {
                leaderboardVmi?.let {
                    runCatching { it.getBooleanProperty("$path/$name")?.value = value }
                }
            }
        }

        fun color(name: String, value: Int) {
            val direct = vmi
            if (direct != null) {
                runCatching { direct.getColorProperty(name)?.value = value }
            } else {
                leaderboardVmi?.let {
                    runCatching { it.getColorProperty("$path/$name")?.value = value }
                }
            }
        }

        /** Name, monthly XP and avatar look for this podium position. */
        fun apply(user: LeaderboardUser?) {
            string(PROP_FIRST_NAME, ellipsize(user?.firstName))
            string(PROP_XP, "${user?.monthlyXP ?: 0L} XP")

            val config = user?.avatarConfig

            AVATAR_NUMBERS.forEach { key ->
                number(key, (config?.get(key) as? Number)?.toFloat() ?: 1f)
            }

            val hatValue = (config?.get("hat") as? Number)?.toInt() ?: 1
            boolean("hatOn", hatValue > 1)

            AVATAR_COLORS.forEach { propName ->
                val hex = config?.get(propName) as? String ?: return@forEach
                val colorInt = runCatching { Color.parseColor(hex) }.getOrNull() ?: return@forEach
                color(propName, colorInt)
            }
        }
    }

    /** "Abduvali Abduaxatov" -> "Abduvali A…" — Rive can't do this itself. */
    private fun ellipsize(name: String?): String {
        val clean = name?.trim().orEmpty()
        if (clean.length <= MAX_PODIUM_NAME_CHARS) return clean
        return clean.take(MAX_PODIUM_NAME_CHARS).trimEnd() + "…"
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        Rive.init(requireContext())
        val view = inflater.inflate(R.layout.fragment_leaderboard, container, false)

        recycler = view.findViewById(R.id.leaderboardRecycler)
        leaderboardRive = view.findViewById(R.id.leaderboardRive)
        loadingLayout = view.findViewById(R.id.loadingLayout)
        swipeRefreshLayout = view.findViewById(R.id.swipeRefreshLayout)

        recycler.layoutManager = LinearLayoutManager(requireContext())

        val currentUserEmail = FirebaseAuth.getInstance().currentUser?.email
        leaderboardAdapter =
            LeaderboardAdapter(usersList, currentUserEmail, LIST_START_RANK) { selectedUser ->
                val intent =
                    android.content.Intent(requireContext(), OtherUserProfileActivity::class.java)
                val explicitRank = usersList.indexOf(selectedUser) + LIST_START_RANK
                intent.putExtra("USER_DATA", selectedUser)
                intent.putExtra("USER_RANK", explicitRank)
                startActivity(intent)
            }
        recycler.adapter = leaderboardAdapter

        context?.let { ctx ->
            swipeRefreshLayout.setColorSchemeColors(ContextCompat.getColor(ctx, R.color.blue_2))
        }

        swipeRefreshLayout.setOnRefreshListener {
            updateMonthUI()
            loadLeaderboard()
        }

        // bind the Rive view model as early as possible so month/days/place have a target
        bindLeaderboardRive()

        updateMonthUI()
        loadLeaderboard()

        return view
    }

    // ==================== RIVE TOP BLOCK ====================

    /**
     * Binds ViewModel2 to the leaderboard artboard and resolves the three nested
     * ViewModel1 instances. Retries while the .riv file is still loading, then flushes
     * everything we already know (labels, month, days left, place, podium).
     */
    private fun bindLeaderboardRive(attempt: Int = 0) {
        leaderboardRive.post {
            if (!isAdded || view == null) return@post

            val riveController = leaderboardRive.controller
            val file = riveController.file
            val stateMachine = riveController.stateMachines.firstOrNull()

            if (file == null || stateMachine == null) {
                if (attempt < MAX_RIVE_ATTEMPTS) {
                    leaderboardRive.postDelayed(
                        { bindLeaderboardRive(attempt + 1) },
                        RIVE_RETRY_DELAY_MS
                    )
                }
                return@post
            }

            try {
                if (leaderboardVmi == null) {
                    val vm = file.getViewModelByName(LEADERBOARD_VIEW_MODEL_NAME) ?: return@post
                    val root = vm.createDefaultInstance()

                    riveController.activeArtboard?.viewModelInstance = root
                    riveController.stateMachines.forEach { it.viewModelInstance = root }
                    leaderboardVmi = root

                    firstSlot.vmi =
                        runCatching { root.getInstanceProperty(PATH_FIRST_PLACE) }.getOrNull()
                    secondSlot.vmi =
                        runCatching { root.getInstanceProperty(PATH_SECOND_PLACE) }.getOrNull()
                    thirdSlot.vmi =
                        runCatching { root.getInstanceProperty(PATH_THIRD_PLACE) }.getOrNull()
                }

                // ---- localized static labels (en / ru / uz via resources) ----
                setRootString(PROP_LEADERBOARD_STRING, getString(R.string.leaderboard))
                setRootString(
                    PROP_LEADERBOARD_DESCRIPTION,
                    getString(R.string.get_a_reward_at_the_end_of_the_month)
                )

                // ---- flush anything produced before binding ----
                pendingDate?.let { setRootString(PROP_DATE, it) }
                pendingDaysLeft?.let { setRootString(PROP_DAYS_LEFT, it) }
                pendingPlace?.let { setRootString(PROP_PLACE, it) }
                pushPodium()

            } catch (e: Exception) {
                Log.e("LeaderboardFragment", "Error binding Rive leaderboard: ${e.message}")
            }
        }
    }

    private fun setRootString(name: String, value: String) {
        leaderboardVmi?.let { runCatching { it.getStringProperty(name)?.value = value } }
    }

    private fun setDateText(text: String) {
        pendingDate = text
        setRootString(PROP_DATE, text)
    }

    private fun setDaysLeftText(text: String) {
        pendingDaysLeft = text
        setRootString(PROP_DAYS_LEFT, text)
    }

    private fun setPlaceText(text: String) {
        pendingPlace = text
        setRootString(PROP_PLACE, text)
    }

    /** Pushes the current top 3 into firstPlace / secondPlace / thirdPlace. */
    private fun pushPodium() {
        listOf(firstSlot, secondSlot, thirdSlot).forEachIndexed { index, slot ->
            slot.apply(podium.getOrNull(index))
        }
    }

    // ==================== MONTH / COUNTDOWN ====================

    private fun updateMonthUI() {
        val calendar = Calendar.getInstance()
        val monthFormat = SimpleDateFormat("MMMM", Locale.getDefault())
        setDateText(monthFormat.format(calendar.time).uppercase())

        activeCountdownTimer?.cancel()

        val targetCalendar = Calendar.getInstance().apply {
            add(Calendar.MONTH, 1)
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val msLeft = targetCalendar.timeInMillis - calendar.timeInMillis
        val twentyFourHoursInMs = TimeUnit.HOURS.toMillis(24)

        if (msLeft <= twentyFourHoursInMs && msLeft > 0) {
            activeCountdownTimer = object : CountDownTimer(msLeft, 1000) {
                override fun onTick(millisUntilFinished: Long) {
                    if (!isAdded) return
                    val hours = TimeUnit.MILLISECONDS.toHours(millisUntilFinished)
                    val minutes = TimeUnit.MILLISECONDS.toMinutes(millisUntilFinished) % 60
                    val seconds = TimeUnit.MILLISECONDS.toSeconds(millisUntilFinished) % 60
                    setDaysLeftText(getString(R.string.countdown_format, hours, minutes, seconds))
                }

                override fun onFinish() {
                    if (!isAdded) return
                    setDaysLeftText(getString(R.string.countdown_format, 0, 0, 0))
                }
            }.start()
        } else {
            val today = calendar.get(Calendar.DAY_OF_MONTH)
            val maxDays = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
            val daysLeft = maxDays - today

            setDaysLeftText(
                if (daysLeft == 1) getString(R.string.days_left_singular)
                else getString(R.string.days_left_plural, daysLeft)
            )
        }
    }

    // ==================== DATA ====================

    private fun loadLeaderboard() {
        if (!swipeRefreshLayout.isRefreshing) {
            loadingLayout.visibility = View.VISIBLE
            leaderboardRive.visibility = View.INVISIBLE
            recycler.visibility = View.INVISIBLE
        }

        // Fetch master system configuration month context first
        db.collection("system").document("leaderboard").get()
            .addOnSuccessListener { task ->
                if (!isAdded) return@addOnSuccessListener
                val globalMonthKey = task.getString("lastMonth") ?: ""
                executeLeaderboardQuery(globalMonthKey)
            }
            .addOnFailureListener {
                if (isAdded) showMainContent()
            }
    }

    private fun executeLeaderboardQuery(globalMonthKey: String) {
        val currentUserEmail = FirebaseAuth.getInstance().currentUser?.email
        val localMonthKey = SimpleDateFormat("yyyy-MM", Locale.ENGLISH).format(Date())

        leaderboardListener?.remove()

        leaderboardListener = db.collection("users")
            .orderBy("monthlyXP", Query.Direction.DESCENDING)
            .limit(100)
            .addSnapshotListener { result, error ->
                if (error != null || result == null || !isAdded) {
                    if (isAdded) { showMainContent() }
                    return@addSnapshotListener
                }

                val tempUsers = mutableListOf<LeaderboardUser>()

                for (document in result) {
                    val lbUser = document.toObject(LeaderboardUser::class.java)
                    lbUser.uid = document.id

                    // Check if user record profile has synced with current system month
                    val userLastResetMonth = document.getString("lastResetMonth") ?: ""

                    // If month flipped or this document's last reset is outdated, visual wipe to 0 XP
                    if (globalMonthKey != localMonthKey ||
                        (userLastResetMonth.isNotEmpty() && userLastResetMonth != globalMonthKey)
                    ) {
                        lbUser.monthlyXP = 0L
                    }
                    tempUsers.add(lbUser)
                }

                // Re-sort locally because visual elements may have shifted to 0 XP
                tempUsers.sortByDescending { it.monthlyXP }

                var myRank = 0
                for ((index, lbUser) in tempUsers.withIndex()) {
                    if (lbUser.email == currentUserEmail) {
                        myRank = index + 1
                        break
                    }
                }

                val topThree = tempUsers.take(PODIUM_SIZE)
                val rest = tempUsers.drop(PODIUM_SIZE)

                rtdb.addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        if (!isAdded) return

                        for (lbUser in tempUsers) {
                            val state = snapshot.child(lbUser.uid).child("state")
                                .getValue(String::class.java)
                            lbUser.isOnline = (state == "online")
                        }

                        usersList.clear()
                        usersList.addAll(rest)
                        leaderboardAdapter?.notifyDataSetChanged()

                        podium = topThree
                        pushPodium()
                        setPlaceText(
                            if (myRank > 0) getString(R.string.place_format, myRank) else ""
                        )

                        showMainContent()
                    }

                    override fun onCancelled(error: DatabaseError) {
                        if (isAdded) { showMainContent() }
                    }
                })
            }
    }

    private fun showMainContent() {
        loadingLayout.visibility = View.GONE
        swipeRefreshLayout.isRefreshing = false

        leaderboardRive.visibility = View.VISIBLE
        recycler.visibility = View.VISIBLE
    }

    override fun onDestroyView() {
        leaderboardListener?.remove()
        leaderboardListener = null

        activeCountdownTimer?.cancel()
        activeCountdownTimer = null

        leaderboardVmi = null
        firstSlot.vmi = null
        secondSlot.vmi = null
        thirdSlot.vmi = null

        super.onDestroyView()
    }
}