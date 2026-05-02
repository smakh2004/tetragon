package com.example.tetragon.questions.questionPhysicsEighthGrade

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.activity.addCallback
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.*
import app.rive.runtime.kotlin.core.Rive
import com.example.tetragon.R
import com.example.tetragon.connectivityCheck.AndroidConnectivityObserver
import com.example.tetragon.connectivityCheck.ConnectivityViewModel
import com.example.tetragon.databinding.ActivityPhysics8GradeQuestionBinding
import com.example.tetragon.questions.FiveCorrectAnswerFragment
import com.example.tetragon.questions.SubjectConstants
import com.example.tetragon.questions.XpGainedActivity
import com.example.tetragon.questions.questionPhysicsEighthGrade.firstTopic.UiFindSpeedFragment
import com.example.tetragon.utils.languageChangeUtils.BaseActivity
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class Physics8GradeQuestionActivity : BaseActivity() {

    private lateinit var binding: ActivityPhysics8GradeQuestionBinding
    private lateinit var selectedTopic: PhysicsGrade8Topic

    private var lastType: PhysicsGrade8Type? = null

    var totalXp: Int = 0
    private var correctAnswersCount = 0

    var isResultCurrentlyVisible: Boolean = false
    var isCorrectAnswerShowing: Boolean = false

    private val viewModel: ConnectivityViewModel by viewModels {
        object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return ConnectivityViewModel(
                    AndroidConnectivityObserver(applicationContext)
                ) as T
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Rive.init(this)

        binding = ActivityPhysics8GradeQuestionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // UI Styling for Navigation Bar
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            window.decorView.systemUiVisibility =
                window.decorView.systemUiVisibility or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
            window.navigationBarColor = ContextCompat.getColor(this, R.color.white)
        }

        binding.exitBtn.setOnClickListener { showQuitBottomSheet() }

        onBackPressedDispatcher.addCallback(this) {
            showQuitBottomSheet()
        }

        val topicName = intent.getStringExtra("TOPIC_KEY")
            ?: throw IllegalArgumentException("TOPIC_KEY missing for Grade 8 Physics")

        selectedTopic = PhysicsGrade8Topic.valueOf(topicName)

        observeConnectivity()

        if (savedInstanceState == null) {
            showRandomQuestion()
        }
    }

    // --- Answer Handling ---

    fun handleCorrectAnswer() {
        correctAnswersCount++
        isCorrectAnswerShowing = true
    }

    fun checkAndTriggerMilestone(): Boolean {
        if (correctAnswersCount >= 5) {
            correctAnswersCount = 0
            triggerMilestone()
            return true
        }
        return false
    }

    private fun triggerMilestone() {
        isResultCurrentlyVisible = false

        binding.stateContainer.visibility = View.GONE
        binding.correctMrSquare.visibility = View.GONE
        binding.btnBackground.visibility = View.GONE

        supportFragmentManager.beginTransaction()
            .setCustomAnimations(R.anim.slide_in_right, R.anim.slide_out_left)
            .replace(R.id.questionFragmentContainer, FiveCorrectAnswerFragment())
            .commit()

        lifecycleScope.launch {
            delay(2500)
            binding.btnBackground.visibility = View.VISIBLE
            showRandomQuestion()
        }
    }

    // --- Animations ---

    fun playSuccessAnimation() {
        binding.correctMrSquare.apply {
            val randomResource = if ((0..1).random() == 0) {
                R.raw.mr_square_correct
            } else {
                R.raw.mr_square_correct_2
            }
            setRiveResource(randomResource)
            visibility = View.VISIBLE
            fireState("State Machine 1", "play")
        }
    }

    fun hideSuccessAnimation() {
        binding.correctMrSquare.apply {
            fireState("State Machine 1", "finish")
            stop()
            visibility = View.INVISIBLE
        }
        isCorrectAnswerShowing = false
    }

    // --- Question Logic ---

    private fun getTypesForTopic(topic: PhysicsGrade8Topic): List<PhysicsGrade8Type> {
        // Placeholder for 8th Grade Physics topics (e.g., Thermodynamics, Electricity)
        return when (topic) {
            PhysicsGrade8Topic.FORCE_AND_MOTION -> listOf(
                PhysicsGrade8Type.FIND_SPEED
            )
        }
    }

    fun showRandomQuestion() {
        isResultCurrentlyVisible = false
        hideSuccessAnimation()

        val available = getTypesForTopic(selectedTopic).toMutableList()
        lastType?.let { available.remove(it) }

        val nextType = if (available.isNotEmpty()) available.random() else getTypesForTopic(selectedTopic).random()
        lastType = nextType

        // Map your 8th Grade fragments here
        val fragment = when (nextType) {
            PhysicsGrade8Type.FIND_SPEED -> UiFindSpeedFragment()
        }

        supportFragmentManager.beginTransaction()
            .setCustomAnimations(
                R.anim.slide_in_right, R.anim.slide_out_left,
                R.anim.slide_in_left, R.anim.slide_out_right
            )
            .replace(R.id.questionFragmentContainer, fragment)
            .commit()
    }

    fun incrementProgress(): Boolean {
        val progressBar = binding.progressBar
        val increment = progressBar.max / 10
        val newProgress = (progressBar.progress + increment).coerceAtMost(progressBar.max)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            progressBar.setProgress(newProgress, true)
        } else {
            val animator = android.animation.ObjectAnimator.ofInt(
                progressBar, "progress", progressBar.progress, newProgress
            )
            animator.duration = 500
            animator.interpolator = android.view.animation.DecelerateInterpolator()
            animator.start()
        }
        return newProgress >= progressBar.max
    }

    fun navigateToXpGained() {
        val intent = Intent(this, XpGainedActivity::class.java).apply {
            putExtra(SubjectConstants.EXTRA_XP, totalXp)
            putExtra(SubjectConstants.EXTRA_TOPIC, selectedTopic.name)
            putExtra(SubjectConstants.EXTRA_GRADE, 8)
            putExtra(SubjectConstants.EXTRA_SUBJECT, SubjectConstants.SUBJECT_PHYSICS)
        }
        startActivity(intent)
        overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
        finish()
    }

    private fun showQuitBottomSheet() {
        val dialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.dialog_quit, null)
        dialog.setContentView(view)

        // Localized Title and Message
        view.findViewById<TextView>(R.id.titleText).text = getString(R.string.quit_title)
        view.findViewById<TextView>(R.id.messageText).text = getString(R.string.quit_message)

        // Localized "CONTINUE" button
        view.findViewById<Button>(R.id.noButton).apply {
            text = getString(R.string.continue_text)
            setOnClickListener { dialog.dismiss() }
        }

        // Localized "EXIT" button
        view.findViewById<Button>(R.id.finishButton).apply {
            text = getString(R.string.exit_btn)
            setOnClickListener {
                finish()
                dialog.dismiss()
            }
        }
        dialog.show()
    }

    // --- Connectivity ---

    private fun observeConnectivity() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.isConnected.collect { isConnected ->
                    updateUIForConnectivity(isConnected)
                }
            }
        }
    }

    private fun updateUIForConnectivity(isConnected: Boolean) {
        val visibility = if (isConnected) View.VISIBLE else View.GONE
        val inverseVisibility = if (isConnected) View.GONE else View.VISIBLE

        binding.internetConnection.visibility = inverseVisibility
        binding.offlineContainer.visibility = inverseVisibility

        binding.topBarContainer.visibility = visibility
        binding.questionFragmentContainer.visibility = visibility
        binding.btnBackground.visibility = visibility

        if (isConnected && isResultCurrentlyVisible) {
            binding.stateContainer.visibility = View.VISIBLE
            binding.correctMrSquare.visibility = if (isCorrectAnswerShowing) View.VISIBLE else View.INVISIBLE
        } else {
            binding.stateContainer.visibility = View.GONE
        }
    }
}