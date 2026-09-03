package com.tetragon.app.questions

import android.media.MediaPlayer
import android.os.Bundle
import android.util.Log
import android.view.View
import app.rive.runtime.kotlin.core.Rive
import app.rive.runtime.kotlin.core.ViewModelInstance
import com.tetragon.app.R
import com.tetragon.app.databinding.ActivityStreakGainedBinding
import com.tetragon.app.utils.languageChangeUtils.BaseActivity
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

class StreakGainedActivity : BaseActivity() {

    companion object {
        private const val MAX_RIVE_ATTEMPTS = 60
        private const val RIVE_RETRY_DELAY_MS = 50L

        /** How long the animation plays alone before the continue button appears. */
        private const val CONTINUE_BTN_DELAY_MS = 4500L
        private const val CONTINUE_BTN_FADE_MS = 200L

        private const val RIVE_VIEW_MODEL = "ViewModel1"

        // ViewModel1 properties
        private const val PROP_STREAK_COUNT = "streakCount"     // Number
        private const val PROP_DAY_STREAK = "dayStreak"         // String ("day streak")
        private const val PROP_MOTIVATION = "motivationText"    // String

        // ViewModel1 — weekday labels (String), left to right
        private const val PROP_MO = "mo"
        private const val PROP_TU = "tu"
        private const val PROP_WE = "we"
        private const val PROP_TH = "th"
        private const val PROP_FR = "fr"
        private const val PROP_SA = "sa"
        private const val PROP_SU = "su"
    }

    private lateinit var binding: ActivityStreakGainedBinding
    private var mediaPlayer: MediaPlayer? = null

    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val db: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    // ---------------- Rive (ViewModel1) ----------------
    private var streakVmi: ViewModelInstance? = null

    /** The streak read from Firestore. Flushed into Rive once the view model binds. */
    private var pendingStreak: Long? = null

    // ---------------- Delayed continue button ----------------
    private var showContinueRunnable: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Rive.init(this)

        binding = ActivityStreakGainedBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Hold the artboard still until ViewModel1 is populated and bound. Without this the
        // state machine can advance past its entry transition using the property defaults.
        runCatching { binding.streakRiveView.pause() }

        playStreakSound()

        // bind the Rive view model as early as possible so the values have a target
        bindStreakRive()
        loadStreakFromFirestore()
        scheduleContinueButton()

        binding.continueEnabledBtn.setOnClickListener {
            finish()
        }
    }

    // =====================================================
    // 🔹 CONTINUE BUTTON REVEAL
    // =====================================================

    private fun scheduleContinueButton() {
        val container = binding.continueBtnContainer
        container.visibility = View.INVISIBLE
        container.alpha = 0f

        val reveal = Runnable {
            if (isFinishing || isDestroyed) return@Runnable
            container.visibility = View.VISIBLE
            container.animate()
                .alpha(1f)
                .setDuration(CONTINUE_BTN_FADE_MS)
                .start()
        }
        showContinueRunnable = reveal
        container.postDelayed(reveal, CONTINUE_BTN_DELAY_MS)
    }

    // =====================================================
    // 🔹 RIVE DATA BINDING (ViewModel1)
    // =====================================================

    /**
     * Binds ViewModel1 to the streak artboard (the .riv itself is declared in the layout
     * XML). Retries while the file is still loading.
     *
     * Order matters: the instance is filled with every value we already know and only then
     * attached to the artboard and state machines, so the state machine cannot pick a branch
     * from the property defaults. Playback starts afterwards.
     */
    private fun bindStreakRive(attempt: Int = 0) {
        binding.streakRiveView.post {
            if (isFinishing || isDestroyed) return@post

            val riveController = binding.streakRiveView.controller
            val file = riveController.file
            val stateMachine = riveController.stateMachines.firstOrNull()

            if (file == null || stateMachine == null) {
                if (attempt < MAX_RIVE_ATTEMPTS) {
                    binding.streakRiveView.postDelayed(
                        { bindStreakRive(attempt + 1) },
                        RIVE_RETRY_DELAY_MS
                    )
                }
                return@post
            }

            try {
                if (streakVmi == null) {
                    val vm = file.getViewModelByName(RIVE_VIEW_MODEL) ?: return@post
                    val root = vm.createDefaultInstance()

                    // 1. Populate the instance while nothing is observing it yet.
                    streakVmi = root
                    applyStreakValues()

                    // 2. Only now hand the filled instance to the artboard / state machines.
                    riveController.activeArtboard?.viewModelInstance = root
                    riveController.stateMachines.forEach { it.viewModelInstance = root }
                } else {
                    applyStreakValues()
                }

                // 3. Release the artboard now that it has real values to read.
                runCatching { binding.streakRiveView.play() }

            } catch (e: Exception) {
                Log.e("StreakGained", "Error binding Rive view model: ${e.message}")
                runCatching { binding.streakRiveView.play() }
            }
        }
    }

    /** Writes every known value into ViewModel1. */
    private fun applyStreakValues() {
        val streak = pendingStreak ?: 1L

        setRiveNumber(PROP_STREAK_COUNT, streak.toFloat())
        setRiveText(PROP_DAY_STREAK, resources.getQuantityString(R.plurals.day_streak, streak.toInt()))
        setRiveText(PROP_MOTIVATION, getString(R.string.streak_motivation))

        applyWeekLabels()
    }

    /** Localized weekday labels for the Mo–Su row (en / ru / uz via resources). */
    private fun applyWeekLabels() {
        setRiveText(PROP_MO, getString(R.string.mo))
        setRiveText(PROP_TU, getString(R.string.tu))
        setRiveText(PROP_WE, getString(R.string.we))
        setRiveText(PROP_TH, getString(R.string.th))
        setRiveText(PROP_FR, getString(R.string.fr))
        setRiveText(PROP_SA, getString(R.string.sa))
        setRiveText(PROP_SU, getString(R.string.su))
    }

    private fun setRiveText(property: String, value: String) {
        val instance = streakVmi ?: return
        runCatching { instance.getStringProperty(property)?.value = value }
    }

    private fun setRiveNumber(property: String, value: Float) {
        val instance = streakVmi ?: return
        runCatching { instance.getNumberProperty(property)?.value = value }
    }

    private fun pushStreak(streak: Long) {
        pendingStreak = streak
        setRiveNumber(PROP_STREAK_COUNT, streak.toFloat())
        setRiveText(PROP_DAY_STREAK, resources.getQuantityString(R.plurals.day_streak, streak.toInt()))
    }

    // =====================================================
    // 🔹 FIRESTORE STREAK
    // =====================================================

    /**
     * Reads the user doc, works out the new streak, writes it back,
     * then pushes that value into Rive.
     */
    private fun loadStreakFromFirestore() {
        val user = auth.currentUser ?: return
        val userDoc = db.collection("users").document(user.uid)

        val todayMidnight = getLocalMidnight()
        val todayKey = dateFormat.format(todayMidnight.time)

        userDoc.get().addOnSuccessListener { snapshot ->
            val lastStreakTs = snapshot.getTimestamp("lastStreakDate")
            val currentStreak = snapshot.getLong("streak") ?: 0L
            val maxStreak = snapshot.getLong("maxStreak") ?: 0L
            val visitedDays = snapshot.get("weeklyStreakDays") as? Map<String, Boolean> ?: emptyMap()

            val updatedVisited = visitedDays.toMutableMap()
            var newStreak = currentStreak

            if (lastStreakTs != null) {
                val lastCal = Calendar.getInstance().apply { time = lastStreakTs.toDate() }
                val lastMidnight = getLocalMidnight(lastCal)
                val diffDays = TimeUnit.MILLISECONDS.toDays(todayMidnight.timeInMillis - lastMidnight.timeInMillis)

                newStreak = when (diffDays) {
                    0L -> currentStreak       // Already counted today
                    1L -> currentStreak + 1   // Consecutive day
                    else -> 1L                // Reset to 1 day
                }
            } else {
                newStreak = 1L
            }

            val updatedMaxStreak = maxOf(newStreak, maxStreak)
            updatedVisited[todayKey] = true

            // Update Firestore
            userDoc.update(
                mapOf(
                    "streak" to newStreak,
                    "maxStreak" to updatedMaxStreak,
                    "lastStreakDate" to Timestamp(todayMidnight.time),
                    "weeklyStreakDays" to updatedVisited
                )
            )

            pushStreak(newStreak)
        }.addOnFailureListener { e ->
            Log.e("StreakGained", "Error loading streak: ${e.message}")
        }
    }

    // =====================================================
    // 🔹 SOUND
    // =====================================================

    private fun playStreakSound() {
        try {
            mediaPlayer = MediaPlayer.create(this, R.raw.streak_sound).apply {
                setOnCompletionListener {
                    it.release()
                    mediaPlayer = null
                }
                start()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // =====================================================
    // 🔹 DATE HELPERS
    // =====================================================

    private fun getLocalMidnight(): Calendar = getLocalMidnight(Calendar.getInstance())

    private fun getLocalMidnight(calendar: Calendar): Calendar {
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar
    }

    override fun onDestroy() {
        showContinueRunnable?.let { binding.continueBtnContainer.removeCallbacks(it) }
        showContinueRunnable = null
        binding.continueBtnContainer.animate().cancel()

        streakVmi = null

        mediaPlayer?.let {
            if (it.isPlaying) {
                it.stop()
            }
            it.release()
            mediaPlayer = null
        }
        super.onDestroy()
    }
}