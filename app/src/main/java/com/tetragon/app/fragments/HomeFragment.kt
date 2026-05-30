package com.tetragon.app.fragments

import android.content.Intent
import android.os.Bundle
import android.os.CountDownTimer
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.tetragon.app.MainActivity
import com.tetragon.app.R
import com.tetragon.app.gameModel.GradeManager
import com.tetragon.app.questions.questionMathEighthGrade.Math8GradeFragment
import com.tetragon.app.questions.questionMathEleventhGrade.Math11GradeFragment
import com.tetragon.app.questions.questionMathFifthGrade.Math5GradeFragment
import com.tetragon.app.questions.questionMathFirstGrade.Math1GradeFragment
import com.tetragon.app.questions.questionMathFourthGrade.Math4GradeFragment
import com.tetragon.app.questions.questionMathNinthGrade.Math9GradeFragment
import com.tetragon.app.questions.questionMathSecondGrade.Math2GradeFragment
import com.tetragon.app.questions.questionMathSeventhGrade.Math7GradeFragment
import com.tetragon.app.questions.questionMathSixthGrade.Math6GradeFragment
import com.tetragon.app.questions.questionMathTenthGrade.Math10GradeFragment
import com.tetragon.app.questions.questionMathThirdGrade.Math3GradeFragment
import com.tetragon.app.questions.questionPhysicsEighthGrade.Physics8GradeFragment
import com.tetragon.app.questions.questionPhysicsEleventhGrade.Physics11GradeFragment
import com.tetragon.app.questions.questionPhysicsNinthGrade.Physics9GradeFragment
import com.tetragon.app.questions.questionPhysicsSevenGrade.Physics7GradeFragment
import com.tetragon.app.questions.questionPhysicsTenthGrade.Physics10GradeFragment
import com.tetragon.app.aiChatBot.ChatActivity
import com.tetragon.app.starsBuy.StarsActivity
import com.tetragon.app.streakCalendar.StreakCalendarActivity
import com.tetragon.app.ui.NaturalSciencesActivity
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
    private lateinit var starsContainer: ConstraintLayout
    private lateinit var chatMrSquareRive: View // Add reference variable here

    private lateinit var starCountText: TextView
    private lateinit var starIcon: ImageView

    // UI Layout containers to show/hide dynamically
    private lateinit var topBar: ConstraintLayout
    private lateinit var classButtonContainer: FrameLayout
    private lateinit var homeContentFrame: FrameLayout
    private lateinit var loadingLayout: LinearLayout

    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val db: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    private var userDataListener: ListenerRegistration? = null

    private var countDownTimer: CountDownTimer? = null
    private val REGEN_TIME_MILLIS = 1800000L
    private val MAX_STARS = 15

    // Class level cache state variable to safely store premium value away from background threads
    private var isInfinityPlan: Boolean = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_home, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        chatContainer = view.findViewById(R.id.chatContainer)
        chatMrSquareRive = view.findViewById(R.id.chat_mr_square_rive) // Initialize reference
        starsContainer = view.findViewById(R.id.constraintLayout2)
        streakContainer = view.findViewById(R.id.constraintLayout6)
        streakNumberTextView = view.findViewById(R.id.streakNumber)
        streakIcon = view.findViewById(R.id.streakIcon)
        classLabel = view.findViewById(R.id.class_label)
        topicNameDisplay = view.findViewById(R.id.topic_name)
        classBtn = view.findViewById(R.id.class_btn)
        starCountText = view.findViewById(R.id.starCountText)
        starIcon = view.findViewById(R.id.starIcon)

        // Find main dashboard layouts
        topBar = view.findViewById(R.id.topBar)
        classButtonContainer = view.findViewById(R.id.class_button_container)
        homeContentFrame = view.findViewById(R.id.home_content_frame)
        loadingLayout = view.findViewById(R.id.loadingLayout)

        // Navigation Mappings
        starsContainer.setOnClickListener {
            startActivity(Intent(requireContext(), StarsActivity::class.java))
        }

        streakContainer.setOnClickListener {
            startActivity(Intent(requireContext(), StreakCalendarActivity::class.java))
        }

        classBtn.setOnClickListener {
            startActivity(Intent(requireContext(), NaturalSciencesActivity::class.java))
        }

        // DOUBLE-CLICK SECURITY ASSURANCE: Both container bounds and head asset launch ChatActivity
        chatContainer.setOnClickListener {
            startActivity(Intent(requireContext(), ChatActivity::class.java))
        }

        chatMrSquareRive.setOnClickListener {
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
        mainActivity?.setMiniGamesVisible(true)

        val translatedSubject = when (subject) {
            "MATH" -> getString(R.string.math_label)
            "PHYSICS" -> getString(R.string.physics_label)
            else -> subject
        }

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

        classLabel.text = getString(R.string.grade_label_format, translatedSubject, grade)
    }

    fun onTopicChanged(title: String) {
        topicNameDisplay.text = title
    }

    private fun listenToUserData() {
        val user = auth.currentUser ?: return

        // Show white screen loader & completely hide background layout components
        loadingLayout.visibility = View.VISIBLE
        topBar.visibility = View.INVISIBLE
        classButtonContainer.visibility = View.INVISIBLE
        homeContentFrame.visibility = View.INVISIBLE

        userDataListener = db.collection("users").document(user.uid)
            .addSnapshotListener { snapshot, _ ->
                if (!isAdded) return@addSnapshotListener

                // Clean restore: Dismiss loading layout and reveal main views safely
                loadingLayout.visibility = View.GONE
                topBar.visibility = View.VISIBLE
                classButtonContainer.visibility = View.VISIBLE
                homeContentFrame.visibility = View.VISIBLE

                if (snapshot == null) return@addSnapshotListener

                // 1. STREAK LOGIC
                val streak = snapshot.getLong("streak") ?: 0
                streakNumberTextView.text = streak.toString()
                streakIcon.setImageResource(if (streak > 0) R.drawable.streak else R.drawable.streak_null)

                // 2. STAR / SUBSCRIPTION LOGIC
                isInfinityPlan = snapshot.getBoolean("subscription") ?: false
                val stars = snapshot.getLong("stars") ?: 15L
                val lastStarUsed = snapshot.getTimestamp("lastStarUsedTime")

                countDownTimer?.cancel()

                // Keep regeneration tracking active in the background for subscribed users
                if (stars < MAX_STARS) {
                    if (lastStarUsed != null) {
                        startRegenTimer(lastStarUsed, stars.toInt())
                    } else {
                        db.collection("users").document(user.uid).update("lastStarUsedTime", Timestamp.now())
                    }
                } else {
                    // If stars are full or haven't been consumed, don't show the timer text
                    if (!isInfinityPlan) {
                        starCountText.text = stars.toString()
                        starCountText.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_color))
                    }
                }

                // Apply correct UI configuration depending on the subscription layer status
                if (isInfinityPlan) {
                    starCountText.text = "∞"
                    starIcon.setImageResource(R.drawable.star_infinity)
                    starCountText.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_color))
                } else {
                    starIcon.setImageResource(if (stars <= 0) R.drawable.star_null else R.drawable.star)
                }
            }
    }

    private fun startRegenTimer(lastUsed: Timestamp, currentStars: Int) {
        val currentTime = System.currentTimeMillis()
        val lastUsedMillis = lastUsed.toDate().time
        val elapsedTime = currentTime - lastUsedMillis

        val starsToRecover = (elapsedTime / REGEN_TIME_MILLIS).toInt()
        if (starsToRecover > 0) {
            applyRecovery(currentStars, lastUsedMillis, starsToRecover)
            return
        }

        val timeLeft = REGEN_TIME_MILLIS - elapsedTime

        countDownTimer = object : CountDownTimer(timeLeft, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                if (!isAdded) return

                if (isInfinityPlan) {
                    starCountText.text = "∞"
                    starCountText.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_color))
                    return
                }

                if (currentStars <= 0) {
                    val minutes = TimeUnit.MILLISECONDS.toMinutes(millisUntilFinished)
                    val seconds = TimeUnit.MILLISECONDS.toSeconds(millisUntilFinished) % 60

                    // Loads localized bare clocks "00m 00s" or "00м 00с" straight from resources
                    starCountText.text = getString(R.string.timer_format, minutes, seconds)
                    starCountText.setTextColor(android.graphics.Color.parseColor("#FA236E"))
                } else {
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
            val newTimeMillis = lastUsedMillis + (amount * REGEN_TIME_MILLIS)
            updates["lastStarUsedTime"] = Timestamp(java.util.Date(newTimeMillis))
        } else {
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