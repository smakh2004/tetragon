package com.example.tetragon.questions

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import app.rive.runtime.kotlin.core.Rive
import com.example.tetragon.R
import com.example.tetragon.databinding.ActivityXpGainedBinding
import com.example.tetragon.gameModel.calculateLevel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import java.util.Calendar
import java.util.Date

class XpGainedActivity : AppCompatActivity() {

    private lateinit var binding: ActivityXpGainedBinding
    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val db: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }

    private lateinit var topicKey: String
    private lateinit var subject: String
    private var grade: Int = 1
    private var xpGained: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Rive.init(this)

        binding = ActivityXpGainedBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 🔹 Get intent data using SubjectConstants
        topicKey = intent.getStringExtra(SubjectConstants.EXTRA_TOPIC) ?: return
        xpGained = intent.getIntExtra(SubjectConstants.EXTRA_XP, 0)

        // 🔹 Get Dynamic Subject/Grade data (Defaults to Class 1 Math)
        grade = intent.getIntExtra(SubjectConstants.EXTRA_GRADE, 1)
        subject = intent.getStringExtra(SubjectConstants.EXTRA_SUBJECT) ?: SubjectConstants.SUBJECT_MATH

        checkProgressAndDisplay()

        binding.continueEnabledBtn.setOnClickListener {
            checkAndNavigateStreak()
        }
    }

    /**
     * 🔹 Generates dynamic Firestore key based on Grade and Subject.
     * Example: Grade 7 + "Physics" -> "class7PhysicsProgress"
     */
    private fun getFirestoreProgressKey(): String {
        // Ensures subject starts with uppercase (e.g., "physics" -> "Physics")
        val formattedSubject = subject.lowercase().replaceFirstChar { it.uppercase() }
        return "class${grade}${formattedSubject}Progress"
    }

    // =====================================================
    // 🔹 PROGRESS & UI DISPLAY
    // =====================================================

    private fun checkProgressAndDisplay() {
        val user = auth.currentUser ?: return
        val progressKey = getFirestoreProgressKey()

        db.collection("users").document(user.uid).get().addOnSuccessListener { snapshot ->
            val progressMap = snapshot.get(progressKey) as? Map<String, Long>
            val currentTopicProgress = progressMap?.get(topicKey) ?: 0L

            if (currentTopicProgress >= 100) {
                binding.textView3.text = "0"
                binding.description.text = "Topic Mastered! Max XP reached."
                binding.xpAnimation.setNumberState("State Machine 1", "XP", 0f)
            } else {
                binding.textView3.text = xpGained.toString()
                binding.description.text = "Experience points collected!"
                binding.xpAnimation.setNumberState("State Machine 1", "XP", xpGained.toFloat())

                saveXpAndLevelToFirestore(xpGained)
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

        userDocRef.get().addOnSuccessListener { snapshot ->
            val progressMap = snapshot.get(progressKey) as? Map<String, Long>
            val currentTopicProgress = progressMap?.get(topicKey) ?: 0L

            if (currentTopicProgress >= 100) return@addOnSuccessListener

            userDocRef.update(
                mapOf(
                    "xp" to FieldValue.increment(xp.toLong()),
                    "monthlyXP" to FieldValue.increment(xp.toLong())
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
    // 🔹 STREAK & NAVIGATION (STAYS THE SAME)
    // =====================================================

    private fun checkAndNavigateStreak() {
        val user = auth.currentUser ?: return
        val userDocRef = db.collection("users").document(user.uid)

        userDocRef.get().addOnSuccessListener { snapshot ->
            val lastStreakTimestamp = snapshot.getTimestamp("lastStreakDate")
            val todayDate = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.time

            val lastStreakDate: Date? = lastStreakTimestamp?.toDate()?.let {
                Calendar.getInstance().apply {
                    time = it
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.time
            }

            if (lastStreakDate == null || lastStreakDate != todayDate) {
                startActivity(Intent(this, StreakGainedActivity::class.java))
                overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
            }
            finish()
        }.addOnFailureListener { finish() }
    }

    override fun onBackPressed() {
        checkAndNavigateStreak()
    }
}