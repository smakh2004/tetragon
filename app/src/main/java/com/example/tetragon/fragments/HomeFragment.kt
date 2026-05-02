package com.example.tetragon.fragments

import android.content.Intent
import android.os.Bundle
import android.os.CountDownTimer
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.example.tetragon.MainActivity
import com.example.tetragon.R
import com.example.tetragon.gameModel.GradeManager
import com.example.tetragon.questions.questionMathEighthGrade.Math8GradeFragment
import com.example.tetragon.questions.questionMathEleventhGrade.Math11GradeFragment
import com.example.tetragon.questions.questionMathFifthGrade.Math5GradeFragment
import com.example.tetragon.questions.questionMathFirstGrade.Math1GradeFragment
import com.example.tetragon.questions.questionMathFourthGrade.Math4GradeFragment
import com.example.tetragon.questions.questionMathNinthGrade.Math9GradeFragment
import com.example.tetragon.questions.questionMathSecondGrade.Math2GradeFragment
import com.example.tetragon.questions.questionMathSeventhGrade.Math7GradeFragment
import com.example.tetragon.questions.questionMathSixthGrade.Math6GradeFragment
import com.example.tetragon.questions.questionMathTenthGrade.Math10GradeFragment
import com.example.tetragon.questions.questionMathThirdGrade.Math3GradeFragment
import com.example.tetragon.questions.questionPhysicsEighthGrade.Physics8GradeFragment
import com.example.tetragon.questions.questionPhysicsEleventhGrade.Physics11GradeFragment
import com.example.tetragon.questions.questionPhysicsNinthGrade.Physics9GradeFragment
import com.example.tetragon.questions.questionPhysicsSevenGrade.Physics7GradeFragment
import com.example.tetragon.questions.questionPhysicsTenthGrade.Physics10GradeFragment
import com.example.tetragon.aiChatBot.ChatActivity
import com.example.tetragon.streakCalendar.StreakCalendarActivity
import com.example.tetragon.ui.NaturalSciencesActivity
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import java.util.concurrent.TimeUnit

class HomeFragment : Fragment() {

    private lateinit var streakNumberTextView: TextView
    private lateinit var streakIcon: ImageView
    private lateinit var classLabel: TextView
    private lateinit var topicNameDisplay: TextView
    private lateinit var classBtn: FrameLayout
    private lateinit var streakContainer: ConstraintLayout
    private lateinit var chatContainer: ConstraintLayout

    private lateinit var starCountText: TextView
    private lateinit var starIcon: ImageView

    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val db: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    private var userDataListener: ListenerRegistration? = null

    private var countDownTimer: CountDownTimer? = null
    // 30 Minutes per star
    private val REGEN_TIME_MILLIS = 1800000L
    private val MAX_STARS = 15

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_home, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        chatContainer = view.findViewById(R.id.chatContainer)
        streakContainer = view.findViewById(R.id.constraintLayout6)
        streakNumberTextView = view.findViewById(R.id.streakNumber)
        streakIcon = view.findViewById(R.id.streakIcon)
        classLabel = view.findViewById(R.id.class_label)
        topicNameDisplay = view.findViewById(R.id.topic_name)
        classBtn = view.findViewById(R.id.class_btn)
        starCountText = view.findViewById(R.id.starCountText)
        starIcon = view.findViewById(R.id.starIcon)

        streakContainer.setOnClickListener {
            startActivity(Intent(requireContext(), StreakCalendarActivity::class.java))
        }

        classBtn.setOnClickListener {
            startActivity(Intent(requireContext(), NaturalSciencesActivity::class.java))
        }

        chatContainer.setOnClickListener {
            startActivity(Intent(requireContext(), ChatActivity::class.java))
        }

        if (savedInstanceState == null) {
            val savedGrade = GradeManager.getGrade(requireContext())
            val savedSubject = GradeManager.getSubject(requireContext())
            val targetGrade = arguments?.getInt("target_grade", savedGrade) ?: savedGrade
            val targetSubject = arguments?.getString("target_subject", savedSubject) ?: savedSubject
            displayGrade(targetGrade, targetSubject)
        }

        listenToUserData()
    }

    private fun displayGrade(grade: Int, subject: String) {
        val mainActivity = activity as? MainActivity
        mainActivity?.setMiniGamesVisible(subject != "PHYSICS")

        // 1. Get the translated Subject Name
        val translatedSubject = when (subject) {
            "MATH" -> getString(R.string.math_label)
            "PHYSICS" -> getString(R.string.physics_label)
            else -> subject // Fallback
        }

        // 2. Select Fragment (Keep your existing logic)
        val fragment = if (subject == "PHYSICS") {
            when (grade) {
                7 -> Physics7GradeFragment()
                8 -> Physics8GradeFragment()
                9 -> Physics9GradeFragment()
                10 -> Physics10GradeFragment()
                11 -> Physics11GradeFragment()
                else -> Physics7GradeFragment()
            }
        } else {
            when (grade) {
                1 -> Math1GradeFragment()
                2 -> Math2GradeFragment()
                3 -> Math3GradeFragment()
                4 -> Math4GradeFragment()
                5 -> Math5GradeFragment()
                6 -> Math6GradeFragment()
                7 -> Math7GradeFragment()
                8 -> Math8GradeFragment()
                9 -> Math9GradeFragment()
                10 -> Math10GradeFragment()
                11 -> Math11GradeFragment()
                else -> Math1GradeFragment()
            }
        }

        childFragmentManager.beginTransaction()
            .replace(R.id.home_content_frame, fragment)
            .commit()

        // 3. Set translated text: e.g., "Math - 5 Grade" or "Математика - 5 класс"
        classLabel.text = getString(R.string.grade_label_format, translatedSubject, grade)
    }

    fun onTopicChanged(title: String) {
        topicNameDisplay.text = title
    }

    private fun listenToUserData() {
        val user = auth.currentUser ?: return
        userDataListener = db.collection("users").document(user.uid)
            .addSnapshotListener { snapshot, _ ->
                if (snapshot == null || !isAdded) return@addSnapshotListener

                // 1. STREAK LOGIC
                val streak = snapshot.getLong("streak") ?: 0
                streakNumberTextView.text = streak.toString()
                streakIcon.setImageResource(if (streak > 0) R.drawable.streak else R.drawable.streak_null)

                // 2. STAR / SUBSCRIPTION LOGIC
                val isInfinity = snapshot.getBoolean("subscription") ?: false
                val stars = snapshot.getLong("stars") ?: 15L
                val lastStarUsed = snapshot.getTimestamp("lastStarUsedTime")

                countDownTimer?.cancel()

                when {
                    isInfinity -> {
                        starCountText.text = "∞"
                        starIcon.setImageResource(R.drawable.star_infinity)
                        starCountText.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_color))
                    }
                    else -> {
                        starIcon.setImageResource(if (stars <= 0) R.drawable.star_null else R.drawable.star)

                        if (stars < MAX_STARS) {
                            if (lastStarUsed != null) {
                                startRegenTimer(lastStarUsed, stars.toInt())
                            } else {
                                // If user has < 15 stars but no timestamp, we force one to start the recovery
                                db.collection("users").document(user.uid).update("lastStarUsedTime", Timestamp.now())
                            }
                        } else {
                            // Full stars (15)
                            starCountText.text = stars.toString()
                            starCountText.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_color))
                        }
                    }
                }
            }
    }

    private fun startRegenTimer(lastUsed: Timestamp, currentStars: Int) {
        val currentTime = System.currentTimeMillis()
        val lastUsedMillis = lastUsed.toDate().time
        val elapsedTime = currentTime - lastUsedMillis

        // Calculate total star recovery based on elapsed time (offline recovery)
        val starsToRecover = (elapsedTime / REGEN_TIME_MILLIS).toInt()
        if (starsToRecover > 0) {
            applyRecovery(currentStars, lastUsedMillis, starsToRecover)
            return
        }

        val timeLeft = REGEN_TIME_MILLIS - elapsedTime

        countDownTimer = object : CountDownTimer(timeLeft, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                if (!isAdded) return

                if (currentStars <= 0) {
                    // Show timer when user is out of stars
                    val minutes = TimeUnit.MILLISECONDS.toMinutes(millisUntilFinished)
                    val seconds = TimeUnit.MILLISECONDS.toSeconds(millisUntilFinished) % 60
                    starCountText.text = String.format("%02dm %02ds", minutes, seconds)
                    starCountText.setTextColor(android.graphics.Color.parseColor("#FA236E"))
                } else {
                    // Show current count while timer runs in background
                    starCountText.text = currentStars.toString()
                    starCountText.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_color))
                }
            }

            override fun onFinish() {
                applyRecovery(currentStars, lastUsedMillis, 1)
            }
        }.start()
    }

    private fun applyRecovery(currentStars: Int, lastUsedMillis: Long, amount: Int) {
        val user = auth.currentUser ?: return
        val nextStars = (currentStars + amount).coerceAtMost(MAX_STARS)
        val updates = mutableMapOf<String, Any>("stars" to nextStars)

        if (nextStars < MAX_STARS) {
            // Shift the start time forward by the time consumed to avoid "losing" milliseconds
            val newTimeMillis = lastUsedMillis + (amount * REGEN_TIME_MILLIS)
            updates["lastStarUsedTime"] = Timestamp(java.util.Date(newTimeMillis))
        } else {
            // Reset reached
            updates["lastStarUsedTime"] = com.google.firebase.firestore.FieldValue.delete()
        }

        db.collection("users").document(user.uid).update(updates)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        userDataListener?.remove()
        countDownTimer?.cancel()
    }
}