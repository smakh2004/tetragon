package com.example.tetragon.fragments

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
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
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

class HomeFragment : Fragment() {

    private lateinit var streakNumberTextView: TextView
    private lateinit var streakIcon: ImageView
    private lateinit var classLabel: TextView
    private lateinit var topicNameDisplay: TextView
    private lateinit var classBtn: FrameLayout
    private lateinit var streakContainer: ConstraintLayout

    private lateinit var chatContainer: ConstraintLayout
    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val db: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    private var streakListener: ListenerRegistration? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
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
            // 1. Try to get from Intent/Arguments
            // 2. Fallback to saved SharedPreferences
            val savedGrade = GradeManager.getGrade(requireContext())
            val savedSubject = GradeManager.getSubject(requireContext())

            val targetGrade = arguments?.getInt("target_grade", savedGrade) ?: savedGrade
            val targetSubject = arguments?.getString("target_subject", savedSubject) ?: savedSubject

            displayGrade(targetGrade, targetSubject)
        }

        listenToStreakChanges()
    }

    /**
     * Replaces the child fragment and updates the class label
     */
    private fun displayGrade(grade: Int, subject: String) {
        // 1. Handle Bottom Navigation Visibility
        val mainActivity = activity as? MainActivity
        if (subject == "PHYSICS") {
            mainActivity?.setMiniGamesVisible(false)
        } else {
            mainActivity?.setMiniGamesVisible(true)
        }

        // 2. Fragment Selection Logic
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

        classLabel.text = "$subject - GRADE $grade"
    }

    /**
     * Called by child fragments when the visible topic changes
     */
    fun onTopicChanged(title: String) {
        topicNameDisplay.text = title
    }

    /**
     * Listen to streak changes from Firestore
     */
    private fun listenToStreakChanges() {
        val user = auth.currentUser ?: return
        streakListener = db.collection("users").document(user.uid)
            .addSnapshotListener { snapshot, _ ->
                val streak = snapshot?.getLong("streak") ?: 0
                streakNumberTextView.text = streak.toString()
                streakIcon.setImageResource(if (streak > 0) R.drawable.streak else R.drawable.streak_null)
            }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        streakListener?.remove()
    }
}