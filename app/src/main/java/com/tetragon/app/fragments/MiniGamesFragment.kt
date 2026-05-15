package com.tetragon.app.fragments

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import android.widget.LinearLayout
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.widget.NestedScrollView
import androidx.fragment.app.Fragment
import app.rive.runtime.kotlin.RiveAnimationView
import com.tetragon.app.MainActivity
import com.tetragon.app.R
import com.tetragon.app.utils.mathStormUtils.OnlineGameData
import com.tetragon.app.connectivityCheck.userPresenceUtils.UserPresenceHelper
import com.tetragon.app.ui.uiCashStorm.UiCashStormActivity
import com.tetragon.app.ui.uiMathStorm.MathStormActivity
import com.tetragon.app.ui.uiMathStormOnline.OnlineWaitingRoomMathStormActivity
import com.tetragon.app.ui.uiMathStormPrivate.RoomActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

class MiniGamesFragment : Fragment() {

    // --- MATH STORM ---
    private lateinit var mathStormButton: Button
    private lateinit var mathStormScore: TextView
    private lateinit var startMathStromEnabledButtonContainer: FrameLayout
    private lateinit var startMathStromDisabledButtonContainer: FrameLayout
    private lateinit var startMathStormDisabledBtn: TextView
    private var scoreListener: ListenerRegistration? = null

    // --- BATTLE STORM (ONLINE) ---
    private lateinit var playOnlineBtn: Button
    private lateinit var playOnlineContainer: FrameLayout
    private lateinit var playOnlineDisabledContainer: FrameLayout
    private lateinit var playOnlineDisabledTxt: TextView
    private var isStartingOnline = false
    private lateinit var onlineScoreText: TextView
    private var onlineScoreListener: ListenerRegistration? = null

    // --- PRIVATE GAME ---
    private lateinit var playPrivateGame: Button
    private lateinit var playPrivateEnabledButtonGameContainer: FrameLayout
    private lateinit var playPrivateDisabledButtonGameContainer: FrameLayout
    private lateinit var playPrivateDisabledButtonGameText: TextView

    // --- CASH STORM ---
    private lateinit var cashStormButton: Button
    private lateinit var cashStormBalanceText: TextView
    private lateinit var cashStormEnabledBtnContainer: FrameLayout
    private lateinit var cashStormDisabledBtnContainer: FrameLayout
    private lateinit var cashStormDisabledBtnText: TextView
    private var cashScoreListener: ListenerRegistration? = null

    // --- PRESENCE ---
    private lateinit var onlinePlayersText: TextView
    private val databaseRef = FirebaseDatabase.getInstance().getReference("status")
    private var presenceListener: ValueEventListener? = null
    private lateinit var playerIcon: ImageView

    // --- MINI GAME ATTEMPTS & SUBSCRIPTION ---
    private lateinit var attemptsText: TextView
    private lateinit var attemptsContainer: LinearLayout
    private lateinit var progressContainer: ConstraintLayout
    private var attemptsListener: ListenerRegistration? = null
    private var subscriptionListener: ListenerRegistration? = null
    private var remainingAttempts = 5L
    private var isSubscribed = false

    // --- TIMER ---
    private lateinit var resetTimerText: TextView
    private lateinit var ivClockIcon: RiveAnimationView
    private var countdownHandler = android.os.Handler()
    private var countdownRunnable: Runnable? = null

    // --- PROGRESS UI ---
    private lateinit var progressActive: View
    private lateinit var step1: View
    private lateinit var step2: View
    private lateinit var step3: View
    private lateinit var step4: View
    private lateinit var step5: View

    // --- SCROLL LOGIC ---
    private lateinit var nestedScrollView: NestedScrollView
    private lateinit var topBarShadow: View

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_mini_games, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initViews(view)
        UserPresenceHelper.startTracking()

        presenceListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                var onlineCount = 0
                for (child in snapshot.children) {
                    val state = child.child("state").getValue(String::class.java)
                    if (state == "online") onlineCount++
                }
                if (onlineCount <= 1) {
                    onlinePlayersText.text = getString(R.string.online_count, 0)
                    onlinePlayersText.setTextColor(resources.getColor(R.color.gray_1, null))
                    playerIcon.setImageResource(R.drawable.profile_offline)
                } else {
                    onlinePlayersText.text = getString(R.string.online_count, onlineCount - 1)
                    onlinePlayersText.setTextColor(resources.getColor(R.color.blue_1, null))
                    playerIcon.setImageResource(R.drawable.profile_online)
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        databaseRef.addValueEventListener(presenceListener!!)
        setupClickListeners()
    }

    private fun initViews(view: View) {
        nestedScrollView = view.findViewById(R.id.nestedScrollView)
        topBarShadow = view.findViewById(R.id.topBarShadow)

        nestedScrollView.setOnScrollChangeListener(NestedScrollView.OnScrollChangeListener { _, _, scrollY, _, _ ->
            topBarShadow.visibility = if (scrollY > 0) View.VISIBLE else View.GONE
        })

        // Math Storm
        mathStormScore = view.findViewById(R.id.math_storm_score_text)
        mathStormButton = view.findViewById(R.id.start_math_storm_enabled_btn)
        startMathStromEnabledButtonContainer = view.findViewById(R.id.start_math_storm_enabled_btn_container)
        startMathStromDisabledButtonContainer = view.findViewById(R.id.start_math_storm_disabled_btn_container)
        startMathStormDisabledBtn = view.findViewById(R.id.start_math_storm_disabled_btn)

        // Battle Storm
        playOnlineBtn = view.findViewById(R.id.play_online_btn)
        playOnlineContainer = view.findViewById(R.id.play_online_bton_container)
        playOnlineDisabledContainer = view.findViewById(R.id.play_online_disabled_container)
        playOnlineDisabledTxt = view.findViewById(R.id.play_online_disabled_container_txt)
        onlineScoreText = view.findViewById(R.id.play_online_score_text)

        // Private Game
        playPrivateGame = view.findViewById(R.id.private_game_enabled_btn)
        playPrivateEnabledButtonGameContainer = view.findViewById(R.id.private_game_enabled_btn_container)
        playPrivateDisabledButtonGameContainer = view.findViewById(R.id.private_game_disabled_btn_container)
        playPrivateDisabledButtonGameText = view.findViewById(R.id.private_game_disabled_btn)

        // Cash Storm
        cashStormButton = view.findViewById(R.id.start_cash_storm_btn)
        cashStormBalanceText = view.findViewById(R.id.cash_storm_balance_text)
        cashStormEnabledBtnContainer = view.findViewById(R.id.cash_storm_enabled_btn_container)
        cashStormDisabledBtnContainer = view.findViewById(R.id.cash_storm_disabled_btn_container)
        cashStormDisabledBtnText = view.findViewById(R.id.start_cash_storm_disabled_btn)

        // UI Containers to hide for subscribers
        attemptsContainer = view.findViewById(R.id.attemptsContainer)
        progressContainer = view.findViewById(R.id.linearLayout4)

        // General UI
        onlinePlayersText = view.findViewById(R.id.online_players)
        playerIcon = view.findViewById(R.id.player_icon)
        attemptsText = view.findViewById(R.id.attempts)
        resetTimerText = view.findViewById(R.id.reset_timer_text)
        ivClockIcon = view.findViewById(R.id.ivClockIcon)
        progressActive = view.findViewById(R.id.progressActive)
        step1 = view.findViewById(R.id.step1)
        step2 = view.findViewById(R.id.step2)
        step3 = view.findViewById(R.id.step3)
        step4 = view.findViewById(R.id.step4)
        step5 = view.findViewById(R.id.step5)
    }

    private fun setupClickListeners() {
        playOnlineBtn.setOnClickListener {
            (activity as? MainActivity)?.setBottomNavigationEnabled(false)
            if (isStartingOnline) return@setOnClickListener
            isStartingOnline = true
            disableAllButtonsForLoading()
            // Localized Loading
            playOnlineDisabledTxt.text = getString(R.string.loading_caps)
            val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return@setOnClickListener
            OnlineGameData.myID = uid
            OnlineGameData.findOrCreateRoom(uid) { game ->
                OnlineGameData.saveGameModel(game)
                startActivity(Intent(requireContext(), OnlineWaitingRoomMathStormActivity::class.java))
                requireActivity().overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
            }
        }

        mathStormButton.setOnClickListener {
            if (!isSubscribed && remainingAttempts <= 0) return@setOnClickListener
            disableAllButtonsForLoading()
            // FIXED: Localized Loading
            startMathStormDisabledBtn.text = getString(R.string.loading_caps)
            decreaseAttempt {
                startActivity(Intent(requireContext(), MathStormActivity::class.java))
                requireActivity().overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
            }
        }

        playPrivateGame.setOnClickListener {
            disableAllButtonsForLoading()
            // FIXED: Localized Loading
            playPrivateDisabledButtonGameText.text = getString(R.string.loading_caps)
            startActivity(Intent(requireContext(), RoomActivity::class.java))
            requireActivity().overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
        }

        cashStormButton.setOnClickListener {
            if (!isSubscribed && remainingAttempts <= 0) return@setOnClickListener
            disableAllButtonsForLoading()
            // FIXED: Localized Loading
            cashStormDisabledBtnText.text = getString(R.string.loading_caps)
            decreaseAttempt {
                startActivity(Intent(requireContext(), UiCashStormActivity::class.java))
                requireActivity().overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
            }
        }
    }

    private fun disableAllButtonsForLoading() {
        (activity as? MainActivity)?.setBottomNavigationEnabled(false)
        startMathStromEnabledButtonContainer.visibility = View.INVISIBLE
        startMathStromDisabledButtonContainer.visibility = View.VISIBLE
        playOnlineContainer.visibility = View.GONE
        playOnlineDisabledContainer.visibility = View.VISIBLE
        playPrivateEnabledButtonGameContainer.visibility = View.INVISIBLE
        playPrivateDisabledButtonGameContainer.visibility = View.VISIBLE
        cashStormEnabledBtnContainer.visibility = View.INVISIBLE
        cashStormDisabledBtnContainer.visibility = View.VISIBLE
    }

    override fun onStart() {
        super.onStart()
        resetButtons()
        attachScoreListeners()
        ensureOnlineScoreDocumentExists()
        attachAttemptsListener()
        attachSubscriptionListener()
    }

    private fun resetButtons() {
        startMathStromEnabledButtonContainer.visibility = View.VISIBLE
        startMathStromDisabledButtonContainer.visibility = View.INVISIBLE
        startMathStormDisabledBtn.text = getString(R.string.start_xp_format, 10)

        isStartingOnline = false
        playOnlineContainer.visibility = View.VISIBLE
        playOnlineDisabledContainer.visibility = View.INVISIBLE
        playOnlineDisabledTxt.text = getString(R.string.play_online_xp)

        playPrivateEnabledButtonGameContainer.visibility = View.VISIBLE
        playPrivateDisabledButtonGameContainer.visibility = View.INVISIBLE
        playPrivateDisabledButtonGameText.text = getString(R.string.private_game_caps)

        cashStormEnabledBtnContainer.visibility = View.VISIBLE
        cashStormDisabledBtnContainer.visibility = View.INVISIBLE
        cashStormDisabledBtnText.text = getString(R.string.start_xp_format, 10)
    }

    override fun onStop() {
        super.onStop()
        scoreListener?.remove()
        onlineScoreListener?.remove()
        cashScoreListener?.remove()
        attemptsListener?.remove()
        subscriptionListener?.remove()
        countdownRunnable?.let { countdownHandler.removeCallbacks(it) }
    }

    private fun attachScoreListeners() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val db = FirebaseFirestore.getInstance()

        scoreListener = db.collection("users").document(uid).collection("games").document("MathStorm")
            .addSnapshotListener { doc, _ ->
                val score = (doc?.getLong("highScore") ?: 0L).toInt() // Cast to Int
                mathStormScore.text = getString(R.string.record_format, score)
            }

        onlineScoreListener = db.collection("users").document(uid).collection("games").document("OnlineMathStorm")
            .addSnapshotListener { snapshot, _ ->
                val score = (snapshot?.getLong("onlineScore") ?: 0L).toInt() // Cast to Int
                onlineScoreText.text = getString(R.string.wins_format, score)
            }

        cashScoreListener = db.collection("users").document(uid).collection("games").document("CashStorm")
            .addSnapshotListener { doc, _ ->
                val score = (doc?.getLong("highScore") ?: 0L).toInt() // Cast to Int
                cashStormBalanceText.text = getString(R.string.record_format, score)
            }
    }

    private fun attachSubscriptionListener() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        subscriptionListener = FirebaseFirestore.getInstance().collection("users").document(uid)
            .addSnapshotListener { snapshot, _ ->
                if (snapshot != null && snapshot.exists()) {
                    isSubscribed = snapshot.getBoolean("subscription") ?: false
                    updateSubscriptionUI()
                }
            }
    }

    private fun updateSubscriptionUI() {
        if (isSubscribed) {
            attemptsContainer.visibility = View.GONE
            progressContainer.visibility = View.GONE
            ivClockIcon.visibility = View.GONE
            resetTimerText.visibility = View.GONE
            enableAllGameButtons()
        } else {
            attemptsContainer.visibility = View.VISIBLE
            progressContainer.visibility = View.VISIBLE
            // Note: Timer visibility is handled by the attempts listener logic
        }
    }

    private fun attachAttemptsListener() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val docRef = FirebaseFirestore.getInstance().collection("users").document(uid)
            .collection("games").document("Attempts")

        attemptsListener = docRef.addSnapshotListener { snapshot, _ ->
            if (snapshot != null && snapshot.exists()) {
                remainingAttempts = snapshot.getLong("remainingAttempts") ?: 5L
                attemptsText.text = "$remainingAttempts"
                updateProgressUI(remainingAttempts.toInt())

                if (!isSubscribed) {
                    if (remainingAttempts <= 0) {
                        disableGameButtonsExceptPrivate()
                        attemptsText.setTextColor(resources.getColor(R.color.red_1, null))
                    } else {
                        enableAllGameButtons()
                    }

                    val lastReset = snapshot.getLong("lastReset") ?: 0L
                    val now = System.currentTimeMillis()
                    val remainingTime = 60 * 60 * 1000 - (now - lastReset)

                    if (remainingAttempts.toInt() == 0 && remainingTime > 0) {
                        ivClockIcon.visibility = View.VISIBLE
                        resetTimerText.visibility = View.VISIBLE
                        startCountdown(remainingTime)
                    } else {
                        ivClockIcon.visibility = View.GONE
                        resetTimerText.visibility = View.GONE
                    }

                    if (remainingTime <= 0) {
                        docRef.update(mapOf("remainingAttempts" to 5L, "lastReset" to now))
                    }
                }
            } else {
                docRef.set(mapOf("remainingAttempts" to 5L, "lastReset" to System.currentTimeMillis()))
            }
        }
    }

    private fun updateProgressUI(attempts: Int) {
        val steps = listOf(step1, step2, step3, step4, step5)
        for (i in steps.indices) {
            steps[i].setBackgroundResource(
                if (i < attempts) R.drawable.circle_active_red else R.drawable.circle_inactive
            )
        }

        if (attempts == 0) {
            progressActive.layoutParams.width = 0
            progressActive.requestLayout()
            return
        }

        progressActive.post {
            val first = step1
            val lastActive = steps.getOrNull(attempts - 1) ?: return@post
            val firstCenter = first.x + first.width / 2
            val lastCenter = lastActive.x + lastActive.width / 2
            val shift = first.width / 2
            val newWidth = (lastCenter - firstCenter + shift).toInt()

            val params = progressActive.layoutParams as ConstraintLayout.LayoutParams
            params.width = newWidth
            params.marginStart = shift.toInt()
            progressActive.layoutParams = params
        }
    }

    private fun startCountdown(timeMillis: Long) {
        countdownRunnable?.let { countdownHandler.removeCallbacks(it) }
        countdownRunnable = object : Runnable {
            var remaining = timeMillis
            override fun run() {
                if (remaining <= 0) {
                    ivClockIcon.visibility = View.GONE
                    resetTimerText.text = getString(R.string.refresh_page)
                    return
                }
                val minutes = (remaining / (1000 * 60)) % 60
                val seconds = (remaining / 1000) % 60
                resetTimerText.text = getString(R.string.timer_format, minutes, seconds)
                remaining -= 1000
                if (!isSubscribed) countdownHandler.postDelayed(this, 1000)
            }
        }
        countdownHandler.post(countdownRunnable!!)
    }

    private fun decreaseAttempt(onSuccess: () -> Unit) {
        if (isSubscribed) {
            onSuccess()
            return
        }

        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val docRef = FirebaseFirestore.getInstance().collection("users").document(uid)
            .collection("games").document("Attempts")

        FirebaseFirestore.getInstance().runTransaction { transaction ->
            val snapshot = transaction.get(docRef)
            val current = snapshot.getLong("remainingAttempts") ?: 5L
            if (current > 0) {
                transaction.update(docRef, "remainingAttempts", current - 1)
                true
            } else false
        }.addOnSuccessListener { success -> if (success) onSuccess() }
    }

    private fun disableGameButtonsExceptPrivate() {
        val noAttempts = getString(R.string.no_attempts_caps)
        startMathStromEnabledButtonContainer.visibility = View.INVISIBLE
        startMathStromDisabledButtonContainer.visibility = View.VISIBLE
        startMathStormDisabledBtn.text = noAttempts

        playOnlineContainer.visibility = View.GONE
        playOnlineDisabledContainer.visibility = View.VISIBLE
        playOnlineDisabledTxt.text = noAttempts

        cashStormEnabledBtnContainer.visibility = View.INVISIBLE
        cashStormDisabledBtnContainer.visibility = View.VISIBLE
        cashStormDisabledBtnText.text = noAttempts

        playPrivateEnabledButtonGameContainer.visibility = View.VISIBLE
        playPrivateDisabledButtonGameContainer.visibility = View.INVISIBLE
    }

    private fun enableAllGameButtons() {
        startMathStromEnabledButtonContainer.visibility = View.VISIBLE
        startMathStromDisabledButtonContainer.visibility = View.INVISIBLE
        playOnlineContainer.visibility = View.VISIBLE
        playOnlineDisabledContainer.visibility = View.INVISIBLE
        cashStormEnabledBtnContainer.visibility = View.VISIBLE
        cashStormDisabledBtnContainer.visibility = View.INVISIBLE
        playPrivateEnabledButtonGameContainer.visibility = View.VISIBLE
        playPrivateDisabledButtonGameContainer.visibility = View.INVISIBLE
    }

    private fun ensureOnlineScoreDocumentExists() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val docRef = FirebaseFirestore.getInstance().collection("users").document(uid)
            .collection("games").document("OnlineMathStorm")
        docRef.get().addOnSuccessListener { snapshot ->
            if (!snapshot.exists()) docRef.set(mapOf("onlineScore" to 0L))
        }
    }

    override fun onResume() {
        super.onResume()
        (activity as? MainActivity)?.setBottomNavigationEnabled(true)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        presenceListener?.let { databaseRef.removeEventListener(it) }
    }
}