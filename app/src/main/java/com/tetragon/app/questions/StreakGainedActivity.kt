package com.tetragon.app.questions

import android.media.MediaPlayer
import android.os.Bundle
import android.view.View
import android.view.animation.DecelerateInterpolator
import app.rive.runtime.kotlin.core.Rive
import app.rive.runtime.kotlin.core.PlayableInstance
import app.rive.runtime.kotlin.core.ViewModelInstance
import app.rive.runtime.kotlin.controllers.RiveFileController
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

    private lateinit var binding: ActivityStreakGainedBinding
    private var mediaPlayer: MediaPlayer? = null

    private val auth by lazy { FirebaseAuth.getInstance() }
    private val db by lazy { FirebaseFirestore.getInstance() }
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    private val STATE_MACHINE = "State Machine 1"
    private val VIEW_MODEL_NAME = "ViewModel1" // Matches your Rive Data ViewModel name
    private var isRiveInitialized = false

    // Retain the ViewModelInstance reference for direct updates
    private var viewModelInstance: ViewModelInstance? = null
    private var pendingStreak: Long? = null

    private val INTRO_HOLD_DURATION_MS = 4500L
    private val INTRO_MOVE_DURATION_MS = 600L
    private val INTRO_FADE_DURATION_MS = 500L
    private val INTRO_ENLARGE_SCALE = 1.1f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Rive.init(this)
        binding = ActivityStreakGainedBinding.inflate(layoutInflater)
        setContentView(binding.root)

        playStreakSound()
        setupIntroAnimation()

        binding.streakRiveView.registerListener(object : RiveFileController.Listener {
            override fun notifyPlay(animation: PlayableInstance) {
                if (!isRiveInitialized) {
                    val file = binding.streakRiveView.controller.file
                    if (file != null) {
                        setupRiveDefaultLayout(file)
                        isRiveInitialized = true
                    }
                }
            }
            override fun notifyPause(animation: PlayableInstance) {}
            override fun notifyStop(animation: PlayableInstance) {}
            override fun notifyLoop(animation: PlayableInstance) {}
            override fun notifyStateChanged(stateMachineName: String, stateName: String) {}
        })

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

            // Feed updated data & bound streak count into Rive
            applyLiveDataToRive(newStreak, updatedVisited)
        }

        binding.continueEnabledBtn.setOnClickListener {
            finish()
        }
    }

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

    private fun setupIntroAnimation() {
        binding.continueEnabledBtn.isEnabled = false

        binding.root.post {
            val screenCenterY = binding.main.height / 2f
            val viewCenterY = binding.streakRiveView.top + binding.streakRiveView.height / 2f
            val offsetY = screenCenterY - viewCenterY

            binding.streakRiveView.apply {
                translationY = offsetY
                scaleX = INTRO_ENLARGE_SCALE
                scaleY = INTRO_ENLARGE_SCALE
            }

            binding.root.postDelayed({ playIntroAnimation() }, INTRO_HOLD_DURATION_MS)
        }
    }

    private fun playIntroAnimation() {
        binding.streakRiveView.animate()
            .translationY(0f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(INTRO_MOVE_DURATION_MS)
            .setInterpolator(DecelerateInterpolator())
            .start()

        binding.streakMrSquare.animate()
            .alpha(1f)
            .setDuration(INTRO_FADE_DURATION_MS)
            .setStartDelay(150L)
            .start()

        binding.continueBtnContainer.apply {
            visibility = View.VISIBLE
            animate()
                .alpha(1f)
                .setDuration(INTRO_FADE_DURATION_MS)
                .setStartDelay(200L)
                .withEndAction { binding.continueEnabledBtn.isEnabled = true }
                .start()
        }
    }

    private fun setupRiveDefaultLayout(file: app.rive.runtime.kotlin.core.File) {
        try {
            val vm = file.getViewModelByName(VIEW_MODEL_NAME) ?: return
            val instance = vm.createDefaultInstance()
            viewModelInstance = instance

            // Assign ViewModelInstance to the state machine controller
            binding.streakRiveView.controller.stateMachines.firstOrNull()?.viewModelInstance = instance

            // Fill the 7 label slots so TODAY always sits in the "Th" slot (position 4)
            applyRotatedWeekLabels(instance)

            // Apply pending streak count if Firestore already returned before Rive initialized
            pendingStreak?.let { streak ->
                bindStreakCountToViewModel(streak)
                pendingStreak = null
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * The Rive ViewModel exposes 7 string slots named in fixed visual order:
     * "Mo","Tu","We","Th","Fr","Sa","Su". These are POSITIONS, not weekdays.
     * The 4th slot ("Th") represents TODAY, with the 3 previous days to its left
     * and the 3 upcoming days to its right. This fills each slot with the correct
     * localized weekday label based on the current date.
     */
    private fun applyRotatedWeekLabels(instance: ViewModelInstance) {
        // Slot property names in their left-to-right visual order.
        val slotNames = listOf("Mo", "Tu", "We", "Th", "Fr", "Sa", "Su")
        val todaySlot = slotNames.indexOf("Th") // = 3, the slot that is "today"

        // Localized short labels, indexed Monday(0)..Sunday(6).
        val dayLabels = listOf(
            getString(R.string.mo),
            getString(R.string.tu),
            getString(R.string.we),
            getString(R.string.th),
            getString(R.string.fr),
            getString(R.string.sa),
            getString(R.string.su)
        )

        // Today as a Monday(0)..Sunday(6) index.
        val dow = Calendar.getInstance().get(Calendar.DAY_OF_WEEK) // 1=Sun..7=Sat
        val todayIndex = (dow + 5) % 7

        slotNames.forEachIndexed { slot, propName ->
            val offset = slot - todaySlot                      // -3..+3 relative to today
            val dayIndex = ((todayIndex + offset) % 7 + 7) % 7 // safe positive modulo
            instance.getStringProperty(propName)?.value = dayLabels[dayIndex]
        }
    }

    private fun applyLiveDataToRive(newStreak: Long, visitedDays: Map<String, Boolean>) {
        try {
            val allInputs = listOf("4", "1,4", "2,4", "3,4", "1,2,4", "1,3,4", "2,3,4", "1,2,3,4")

            val todayMidnight = getLocalMidnight()
            val day3AgoKey = dateFormat.format((todayMidnight.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -3) }.time)
            val day2AgoKey = dateFormat.format((todayMidnight.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -2) }.time)
            val day1AgoKey = dateFormat.format((todayMidnight.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -1) }.time)

            val is1Active = visitedDays[day3AgoKey] == true
            val is2Active = visitedDays[day2AgoKey] == true
            val is3Active = visitedDays[day1AgoKey] == true

            val activeKey = when {
                newStreak >= 4L || (is1Active && is2Active && is3Active) -> "1,2,3,4"
                is2Active && is3Active -> "2,3,4"
                is1Active && is3Active -> "1,3,4"
                is1Active && is2Active -> "1,2,4"
                is3Active || newStreak == 2L -> "3,4"
                is2Active -> "2,4"
                is1Active -> "1,4"
                else -> "4"
            }

            allInputs.forEach { inputName ->
                binding.streakRiveView.setBooleanState(STATE_MACHINE, inputName, inputName == activeKey)
            }

            // Bind StreakCount to the ViewModel
            if (viewModelInstance != null) {
                bindStreakCountToViewModel(newStreak)
            } else {
                pendingStreak = newStreak
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Binds the new streak count directly to the 'StreakCount' property
     * inside Rive's ViewModelInstance.
     */
    private fun bindStreakCountToViewModel(streak: Long) {
        viewModelInstance?.getNumberProperty("StreakCount")?.let { prop ->
            prop.value = streak.toFloat()
        }
    }

    private fun getLocalMidnight(): Calendar = getLocalMidnight(Calendar.getInstance())

    private fun getLocalMidnight(calendar: Calendar): Calendar {
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaPlayer?.let {
            if (it.isPlaying) {
                it.stop()
            }
            it.release()
            mediaPlayer = null
        }
    }
}