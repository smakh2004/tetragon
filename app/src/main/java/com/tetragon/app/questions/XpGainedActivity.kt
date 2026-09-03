package com.tetragon.app.questions

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import app.rive.runtime.kotlin.core.Rive
import app.rive.runtime.kotlin.core.ViewModelInstance
import com.tetragon.app.R
import com.tetragon.app.databinding.ActivityXpGainedBinding
import com.tetragon.app.gameModel.calculateLevel
import com.tetragon.app.utils.languageChangeUtils.BaseActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import java.text.SimpleDateFormat
import java.util.Locale

class XpGainedActivity : BaseActivity() {

    companion object {
        /** Extras produced by the question activity (accuracy in %, lesson length in seconds). */
        const val EXTRA_ACCURACY = "EXTRA_ACCURACY"
        const val EXTRA_TIME_SECONDS = "EXTRA_TIME_SECONDS"

        private const val MAX_RIVE_ATTEMPTS = 60
        private const val RIVE_RETRY_DELAY_MS = 50L

        /** How long the animation plays alone before the continue button appears. */
        private const val CONTINUE_BTN_DELAY_MS = 2000L
        private const val CONTINUE_BTN_FADE_MS = 200L

        private const val RIVE_VIEW_MODEL = "ViewModel1"

        // ViewModel1 — text
        private const val PROP_LESSON = "lesson"                                  // String
        private const val PROP_COMPLETE = "complete"                              // String
        private const val PROP_LESSON_COMPLETE = "lessonComplete"                 // String
        private const val PROP_LESSON_COMPLETE_DESC = "lessonCompleteDescription" // String

        // ViewModel1 — card labels
        private const val PROP_TOTAL_XP_LABEL = "totalXP"                         // String
        private const val PROP_ACCURACY_LABEL = "accuracy"                        // String
        private const val PROP_TIME_LABEL = "timeString"                          // String

        // ViewModel1 — values
        private const val PROP_XP = "xp"                                          // Number
        private const val PROP_ANSWERS = "answers"                                // Number (accuracy %)
        private const val PROP_TIME = "time"                                      // String (m:ss)
        private const val PROP_ANIMATION = "animation"                            // Number (1, 2 or 3)
    }

    private lateinit var binding: ActivityXpGainedBinding
    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val db: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }

    // ---------------- Rive (ViewModel1) ----------------
    private var lessonVmi: ViewModelInstance? = null

    /** Values produced before the view model is ready get flushed once it binds. */
    private var pendingXp: Float? = null
    private var pendingDescription: String? = null

    /**
     * Which celebration animation plays — 1, 2 or 3, rolled fresh on every visit to this
     * screen (nothing is remembered between lessons) and written into the view model BEFORE
     * the instance is handed to the state machine, so the state machine never evaluates its
     * entry transition against the property's default value.
     */
    private val animationType: Float = (1..3).random().toFloat()

    /**
     * Which headline/description pair is shown, rolled once per lesson. The two arrays are
     * parallel, so the same index keeps a title and its matching line together.
     * Lazy, not a field initializer — resources are only safe to touch after onCreate.
     */
    private val messageIndex: Int by lazy {
        val titles = resources.getStringArray(R.array.rive_lesson_complete_titles)
        if (titles.isEmpty()) 0 else titles.indices.random()
    }

    // ---------------- Delayed continue button ----------------
    private var showContinueRunnable: Runnable? = null

    private lateinit var topicKey: String
    private lateinit var subject: String
    private var grade: Int = 1
    private var xpGained: Int = 0
    private var accuracy: Int = 0
    private var timeSeconds: Long = 0L
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Rive.init(this)

        binding = ActivityXpGainedBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Hold the artboard still until ViewModel1 is populated and bound. Without this the
        // state machine can advance past its entry transition using the property defaults.
        runCatching { binding.xpAnimation.pause() }

        // 🔹 Get intent data using SubjectConstants
        topicKey = intent.getStringExtra(SubjectConstants.EXTRA_TOPIC) ?: return
        xpGained = intent.getIntExtra(SubjectConstants.EXTRA_XP, 0)

        // 🔹 Lesson stats for the result cards
        accuracy = intent.getIntExtra(EXTRA_ACCURACY, 0).coerceIn(0, 100)
        timeSeconds = intent.getLongExtra(EXTRA_TIME_SECONDS, 0L).coerceAtLeast(0L)

        // 🔹 Get Dynamic Subject/Grade data (Defaults to Class 1 Math)
        grade = intent.getIntExtra(SubjectConstants.EXTRA_GRADE, 1)
        subject = intent.getStringExtra(SubjectConstants.EXTRA_SUBJECT) ?: SubjectConstants.SUBJECT_MATH

        // bind the Rive view model as early as possible so xp/labels have a target
        bindLessonRive()
        checkProgressAndDisplay()
        scheduleContinueButton()

        binding.continueEnabledBtn.setOnClickListener {
            checkAndNavigateStreak()
        }
    }

    /** Lesson length as m:ss (the `time` property is a string in Rive). */
    private fun formatTime(seconds: Long): String =
        String.format(Locale.US, "%d:%02d", seconds / 60, seconds % 60)

    /** Randomly chosen headline, e.g. "Lesson Complete!" / "Level Up!". */
    private fun lessonCompleteTitle(): String {
        val titles = resources.getStringArray(R.array.rive_lesson_complete_titles)
        return titles.getOrNull(messageIndex) ?: getString(R.string.rive_lesson_complete)
    }

    /** The line that goes with [lessonCompleteTitle] — same index, so the pair always matches. */
    private fun lessonCompleteDescription(): String {
        val descriptions = resources.getStringArray(R.array.rive_lesson_complete_descriptions)
        return descriptions.getOrNull(messageIndex)
            ?: getString(R.string.rive_lesson_complete_description)
    }

    // =====================================================
    // 🔹 CONTINUE BUTTON REVEAL
    // =====================================================

    /**
     * The button starts INVISIBLE (not GONE) so the Rive view keeps its size, and fades
     * in 2 seconds later once the celebration has had time to play.
     */
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
     * Binds ViewModel1 to the lesson_finished artboard (the .riv itself is declared in the
     * layout XML). Retries while the file is still loading.
     *
     * Order matters: the instance is filled with every value we already know (animation type
     * first) and only then attached to the artboard and state machines, so the state machine
     * cannot pick a branch from the property defaults. Playback starts afterwards.
     */
    private fun bindLessonRive(attempt: Int = 0) {
        binding.xpAnimation.post {
            if (isFinishing || isDestroyed) return@post

            val riveController = binding.xpAnimation.controller
            val file = riveController.file
            val stateMachine = riveController.stateMachines.firstOrNull()

            if (file == null || stateMachine == null) {
                if (attempt < MAX_RIVE_ATTEMPTS) {
                    binding.xpAnimation.postDelayed(
                        { bindLessonRive(attempt + 1) },
                        RIVE_RETRY_DELAY_MS
                    )
                }
                return@post
            }

            try {
                if (lessonVmi == null) {
                    val vm = file.getViewModelByName(RIVE_VIEW_MODEL) ?: return@post
                    val root = vm.createDefaultInstance()

                    // 1. Populate the instance while nothing is observing it yet.
                    lessonVmi = root
                    applyLessonValues()

                    // 2. Only now hand the filled instance to the artboard / state machines.
                    riveController.activeArtboard?.viewModelInstance = root
                    riveController.stateMachines.forEach { it.viewModelInstance = root }
                } else {
                    applyLessonValues()
                }

                // 3. Release the artboard now that it has real values to read.
                runCatching { binding.xpAnimation.play() }

            } catch (e: Exception) {
                Log.e("XpGained", "Error binding Rive view model: ${e.message}")
                runCatching { binding.xpAnimation.play() }
            }
        }
    }

    /** Writes every known value into ViewModel1. Animation type goes first, deliberately. */
    private fun applyLessonValues() {
        // ---- which celebration animation plays (1, 2 or 3, random per visit) ----
        setRiveNumber(PROP_ANIMATION, animationType)

        // ---- localized headline (en / ru / uz via resources) ----
        setRiveText(PROP_LESSON, getString(R.string.rive_lesson))
        setRiveText(PROP_COMPLETE, getString(R.string.rive_complete))

        // ---- randomly chosen congratulation pair (title + its matching line) ----
        setRiveText(PROP_LESSON_COMPLETE, lessonCompleteTitle())
        setRiveText(
            PROP_LESSON_COMPLETE_DESC,
            pendingDescription ?: lessonCompleteDescription()
        )

        // ---- localized result card labels ----
        setRiveText(PROP_TOTAL_XP_LABEL, getString(R.string.rive_total_xp))
        setRiveText(PROP_ACCURACY_LABEL, getString(R.string.rive_accuracy))
        setRiveText(PROP_TIME_LABEL, getString(R.string.rive_time))

        // ---- lesson stats ----
        setRiveNumber(PROP_ANSWERS, accuracy.toFloat())
        setRiveText(PROP_TIME, formatTime(timeSeconds))

        // ---- flush anything produced before binding ----
        pendingXp?.let { setRiveNumber(PROP_XP, it) }
    }

    private fun setRiveText(property: String, value: String) {
        val instance = lessonVmi ?: return
        runCatching { instance.getStringProperty(property)?.value = value }
    }

    private fun setRiveNumber(property: String, value: Float) {
        val instance = lessonVmi ?: return
        runCatching { instance.getNumberProperty(property)?.value = value }
    }

    private fun pushXp(xp: Int) {
        pendingXp = xp.toFloat()
        setRiveNumber(PROP_XP, xp.toFloat())
    }

    private fun pushDescription(text: String) {
        pendingDescription = text
        setRiveText(PROP_LESSON_COMPLETE_DESC, text)
    }

    // =====================================================
    // 🔹 PROGRESS & UI DISPLAY
    // =====================================================

    private fun getFirestoreProgressKey(): String {
        val formattedSubject = subject.lowercase().replaceFirstChar { it.uppercase() }
        return "class${grade}${formattedSubject}Progress"
    }

    private fun checkProgressAndDisplay() {
        val user = auth.currentUser ?: return
        val progressKey = getFirestoreProgressKey()

        db.collection("users").document(user.uid).get().addOnSuccessListener { snapshot ->
            val progressMap = snapshot.get(progressKey) as? Map<String, Long>
            val currentTopicProgress = progressMap?.get(topicKey) ?: 0L

            if (currentTopicProgress >= 100) {
                val reviewXp = 10
                pushXp(reviewXp)
                pushDescription(getString(R.string.xp_topic_mastered))

                saveReviewXpToFirestore(reviewXp)
            } else {
                pushXp(xpGained)

                saveXpAndLevelToFirestore(xpGained)
            }
        }
    }

    // =====================================================
    // 🔹 SAVE REVIEW XP (topic already mastered — flat reward, no topic-progress write)
    // =====================================================

    private fun saveReviewXpToFirestore(xp: Int) {
        val user = auth.currentUser ?: return
        val userDocRef = db.collection("users").document(user.uid)
        val todayKey = dateFormat.format(java.util.Date())

        userDocRef.get().addOnSuccessListener { snapshot ->
            userDocRef.update(
                mapOf(
                    "xp" to FieldValue.increment(xp.toLong()),
                    "monthlyXP" to FieldValue.increment(xp.toLong()),
                    "dailyXPGains.$todayKey" to FieldValue.increment(xp.toLong())
                )
            ).addOnSuccessListener {
                val currentTotalXp = snapshot.getLong("xp") ?: 0L
                val newLevel = calculateLevel(currentTotalXp + xp)
                userDocRef.update("level", newLevel)
                // Note: no updateTopicProgress() call here — topic is already at/above
                // 100, so we deliberately don't touch progress on a review pass.
            }
        }
    }

    // =====================================================
    // 🔹 SAVE XP + LEVEL + TOPIC PROGRESS
    // =====================================================

    private fun saveXpAndLevelToFirestore(xp: Int) {
        val user = auth.currentUser ?: return
        val userDocRef = db.collection("users").document(user.uid)
        val progressKey = getFirestoreProgressKey()
        val todayKey = dateFormat.format(java.util.Date())

        userDocRef.get().addOnSuccessListener { snapshot ->
            val progressMap = snapshot.get(progressKey) as? Map<String, Long>
            val currentTopicProgress = progressMap?.get(topicKey) ?: 0L

            if (currentTopicProgress >= 100) return@addOnSuccessListener

            userDocRef.update(
                mapOf(
                    "xp" to FieldValue.increment(xp.toLong()),
                    "monthlyXP" to FieldValue.increment(xp.toLong()),
                    "dailyXPGains.$todayKey" to FieldValue.increment(xp.toLong())
                )
            ).addOnSuccessListener {
                val currentTotalXp = snapshot.getLong("xp") ?: 0L
                val newLevel = calculateLevel(currentTotalXp + xp)
                userDocRef.update("level", newLevel)

                updateTopicProgress(user.uid, xp)
            }
        }
    }

    private fun updateTopicProgress(userId: String, xp: Int) {
        val userDocRef = db.collection("users").document(userId)
        val progressKey = getFirestoreProgressKey()
        val topicPath = "$progressKey.$topicKey"
        val totalProgressToAdd = (10 + (xp / 10)).toLong()

        userDocRef.update(topicPath, FieldValue.increment(totalProgressToAdd))
            .addOnFailureListener {
                val topicMap = mapOf(topicKey to totalProgressToAdd)
                userDocRef.set(
                    mapOf(progressKey to topicMap),
                    SetOptions.merge()
                )
            }
    }

    // =====================================================
    // 🔹 STREAK & NAVIGATION
    // =====================================================

    private fun checkAndNavigateStreak() {
        val user = auth.currentUser ?: return
        val userDocRef = db.collection("users").document(user.uid)

        userDocRef.get().addOnSuccessListener { snapshot ->
            val lastStreakTimestamp = snapshot.getTimestamp("lastStreakDate")
            val todayDate = java.util.Calendar.getInstance().apply {
                set(java.util.Calendar.HOUR_OF_DAY, 0)
                set(java.util.Calendar.MINUTE, 0)
                set(java.util.Calendar.SECOND, 0)
                set(java.util.Calendar.MILLISECOND, 0)
            }.time

            val lastStreakDate: java.util.Date? = lastStreakTimestamp?.toDate()?.let {
                java.util.Calendar.getInstance().apply {
                    time = it
                    set(java.util.Calendar.HOUR_OF_DAY, 0)
                    set(java.util.Calendar.MINUTE, 0)
                    set(java.util.Calendar.SECOND, 0)
                    set(java.util.Calendar.MILLISECOND, 0)
                }.time
            }

            if (lastStreakDate == null || lastStreakDate != todayDate) {
                startActivity(Intent(this, StreakGainedActivity::class.java))
                overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
            }
            finish()
        }.addOnFailureListener { finish() }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        checkAndNavigateStreak()
    }

    override fun onDestroy() {
        showContinueRunnable?.let { binding.continueBtnContainer.removeCallbacks(it) }
        showContinueRunnable = null
        binding.continueBtnContainer.animate().cancel()

        lessonVmi = null
        super.onDestroy()
    }
}