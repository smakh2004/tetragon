package com.tetragon.app.questions.questionPhysicsTenthGrade

import android.animation.ObjectAnimator
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.Button
import android.widget.TextView
import androidx.activity.addCallback
import androidx.activity.viewModels
import androidx.lifecycle.*
import app.rive.runtime.kotlin.core.Rive
import com.tetragon.app.R
import com.tetragon.app.connectivityCheck.AndroidConnectivityObserver
import com.tetragon.app.connectivityCheck.ConnectivityViewModel
import com.tetragon.app.databinding.ActivityPhysics10GradeQuestionBinding
import com.tetragon.app.questions.FiveCorrectAnswerFragment
import com.tetragon.app.questions.SubjectConstants
import com.tetragon.app.questions.XpGainedActivity
import com.tetragon.app.questions.questionPhysicsTenthGrade.firstTopic.UiFindForceBILFragment
import com.tetragon.app.utils.languageChangeUtils.BaseActivity
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class Physics10GradeQuestionActivity : BaseActivity() {

    private lateinit var binding: ActivityPhysics10GradeQuestionBinding
    private lateinit var selectedTopic: PhysicsGrade10Topic

    private var lastType: PhysicsGrade10Type? = null

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

        binding = ActivityPhysics10GradeQuestionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.exitBtn.setOnClickListener { showQuitBottomSheet() }

        onBackPressedDispatcher.addCallback(this) {
            showQuitBottomSheet()
        }

        val topicName = intent.getStringExtra("TOPIC_KEY")
            ?: throw IllegalArgumentException("TOPIC_KEY missing for Grade 10 Physics")

        selectedTopic = PhysicsGrade10Topic.valueOf(topicName)

        observeConnectivity()

        if (savedInstanceState == null) {
            showRandomQuestion()
        }
    }

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

    private fun getTypesForTopic(topic: PhysicsGrade10Topic): List<PhysicsGrade10Type> {
        return when (topic) {
            PhysicsGrade10Topic.MAGNETISM -> listOf(PhysicsGrade10Type.FORCE_BIL)
        }
    }

    fun showRandomQuestion() {
        isResultCurrentlyVisible = false
        hideSuccessAnimation()

        val available = getTypesForTopic(selectedTopic).toMutableList()
        lastType?.let { available.remove(it) }

        val nextType = if (available.isNotEmpty()) available.random() else getTypesForTopic(selectedTopic).random()
        lastType = nextType

        val fragment = when (nextType) {
            PhysicsGrade10Type.FORCE_BIL -> UiFindForceBILFragment()
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
            val animator = ObjectAnimator.ofInt(
                progressBar, "progress", progressBar.progress, newProgress
            )
            animator.duration = 500
            animator.interpolator = DecelerateInterpolator()
            animator.start()
        }
        return newProgress >= progressBar.max
    }

    fun navigateToXpGained() {
        val intent = Intent(this, XpGainedActivity::class.java).apply {
            putExtra(SubjectConstants.EXTRA_XP, totalXp)
            putExtra(SubjectConstants.EXTRA_TOPIC, selectedTopic.name)
            putExtra(SubjectConstants.EXTRA_GRADE, 10)
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

        view.findViewById<TextView>(R.id.titleText).text = getString(R.string.quit_title)
        view.findViewById<TextView>(R.id.messageText).text = getString(R.string.quit_message)

        view.findViewById<Button>(R.id.noButton).apply {
            text = getString(R.string.continue_text)
            setOnClickListener { dialog.dismiss() }
        }

        view.findViewById<Button>(R.id.finishButton).apply {
            text = getString(R.string.exit_btn)
            setOnClickListener {
                finish()
                dialog.dismiss()
            }
        }
        dialog.show()
    }

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