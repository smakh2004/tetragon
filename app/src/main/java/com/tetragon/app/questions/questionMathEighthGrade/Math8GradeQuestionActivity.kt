package com.tetragon.app.questions.questionMathEighthGrade

import android.animation.ObjectAnimator
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
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
import com.tetragon.app.databinding.ActivityMath8GradeQuestionBinding
import com.tetragon.app.questions.FiveCorrectAnswerFragment
import com.tetragon.app.questions.SubjectConstants
import com.tetragon.app.questions.XpGainedActivity
import com.tetragon.app.questions.questionMathEighthGrade.firstTopic.UiSquareRootOneFragment
import com.tetragon.app.utils.languageChangeUtils.BaseActivity
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class Math8GradeQuestionActivity : BaseActivity() {

    private lateinit var binding: ActivityMath8GradeQuestionBinding
    private lateinit var selectedTopic: MathGrade8Topic
    private var lastQuestionType: MathGrade8Type? = null

    var totalXp: Int = 0
    var isResultCurrentlyVisible: Boolean = false
    var isCorrectAnswerShowing: Boolean = false
    private var correctAnswersCount = 0

    // ---- Lesson stats for the result screen ----
    // correctAnswersCount is reset at every milestone, so accuracy needs its own counters.
    private var lessonCorrectAnswers = 0
    private var lessonAnswerAttempts = 0
    private var lessonStartElapsed: Long = 0L

    private val viewModel: ConnectivityViewModel by viewModels {
        object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return ConnectivityViewModel(AndroidConnectivityObserver(applicationContext)) as T
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Rive.init(this)
        binding = ActivityMath8GradeQuestionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // lesson timer (elapsedRealtime is immune to clock changes)
        lessonStartElapsed = SystemClock.elapsedRealtime()

        binding.exitBtn.setOnClickListener { showQuitBottomSheet() }
        onBackPressedDispatcher.addCallback(this) { showQuitBottomSheet() }

        val topicName = intent.getStringExtra("TOPIC_KEY")
            ?: throw IllegalArgumentException("TOPIC_KEY missing")

        selectedTopic = MathGrade8Topic.valueOf(topicName)

        observeConnectivity()

        if (savedInstanceState == null) {
            showRandomQuestion()
        }
    }

    fun handleCorrectAnswer() {
        correctAnswersCount++
        lessonCorrectAnswers++
        lessonAnswerAttempts++
    }

    /** Call this from the question fragments on a wrong answer, otherwise accuracy stays at 100%. */
    fun handleIncorrectAnswer() {
        lessonAnswerAttempts++
    }

    fun checkAndTriggerMilestone(): Boolean {
        if (correctAnswersCount >= 5) {
            correctAnswersCount = 0
            triggerMilestoneSequence()
            return true
        }
        return false
    }

    private fun triggerMilestoneSequence() {
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
            val randomResource = if ((0..1).random() == 0) R.raw.mr_square_correct else R.raw.mr_square_correct_2
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
    }

    private fun getTypesForTopic(topic: MathGrade8Topic): List<MathGrade8Type> {
        return when (topic) {
            MathGrade8Topic.SQUARE_ROOTS -> listOf(MathGrade8Type.SQUARE_ROOT_ONE)
        }
    }

    fun showRandomQuestion() {
        isResultCurrentlyVisible = false
        hideSuccessAnimation()
        binding.correctMrSquare.visibility = View.INVISIBLE

        val allTypes = getTypesForTopic(selectedTopic)
        val filteredTypes = allTypes.filter { it != lastQuestionType }
        val nextType = if (filteredTypes.isNotEmpty()) filteredTypes.random() else allTypes.random()

        lastQuestionType = nextType

        val fragment = when (nextType) {
            MathGrade8Type.SQUARE_ROOT_ONE -> UiSquareRootOneFragment()
        }

        supportFragmentManager.beginTransaction()
            .setCustomAnimations(R.anim.slide_in_right, R.anim.slide_out_left, R.anim.slide_in_left, R.anim.slide_out_right)
            .replace(R.id.questionFragmentContainer, fragment)
            .commit()
    }

    fun incrementProgress(): Boolean {
        val progressBar = binding.progressBar
        val increment = progressBar.max / 10
        val newProgress = (progressBar.progress + increment).coerceAtMost(progressBar.max)

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            progressBar.setProgress(newProgress, true)
        } else {
            ObjectAnimator.ofInt(progressBar, "progress", progressBar.progress, newProgress).apply {
                duration = 500
                interpolator = DecelerateInterpolator()
                start()
            }
        }
        return newProgress >= progressBar.max
    }

    /** Correct answers as a percentage of every answer attempt in this lesson. */
    private fun lessonAccuracy(): Int =
        if (lessonAnswerAttempts <= 0) 0
        else ((lessonCorrectAnswers * 100f) / lessonAnswerAttempts).toInt().coerceIn(0, 100)

    fun navigateToXpGained() {
        val elapsedSeconds = (SystemClock.elapsedRealtime() - lessonStartElapsed) / 1000

        val intent = Intent(this, XpGainedActivity::class.java).apply {
            putExtra(SubjectConstants.EXTRA_XP, totalXp)
            putExtra(SubjectConstants.EXTRA_TOPIC, selectedTopic.name)
            putExtra(SubjectConstants.EXTRA_GRADE, 8)
            putExtra(SubjectConstants.EXTRA_SUBJECT, SubjectConstants.SUBJECT_MATH)
            putExtra(XpGainedActivity.EXTRA_ACCURACY, lessonAccuracy())
            putExtra(XpGainedActivity.EXTRA_TIME_SECONDS, elapsedSeconds)
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
            text = getString(R.string.continue_btn)
            setOnClickListener { dialog.dismiss() }
        }
        view.findViewById<Button>(R.id.finishButton).apply {
            text = getString(R.string.exit_btn)
            setOnClickListener { finish(); dialog.dismiss() }
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
        val isVisible = isConnected
        binding.internetConnection.visibility = if (isVisible) View.GONE else View.VISIBLE
        binding.offlineContainer.visibility = if (isVisible) View.GONE else View.VISIBLE
        binding.topBarContainer.visibility = if (isVisible) View.VISIBLE else View.GONE
        binding.questionFragmentContainer.visibility = if (isVisible) View.VISIBLE else View.GONE
        binding.btnBackground.visibility = if (isVisible) View.VISIBLE else View.GONE

        if (isVisible && isResultCurrentlyVisible) {
            binding.stateContainer.visibility = View.VISIBLE
            binding.correctMrSquare.visibility = if (isCorrectAnswerShowing) View.VISIBLE else View.INVISIBLE
        } else {
            binding.stateContainer.visibility = View.GONE
        }
    }
}