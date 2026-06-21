package com.tetragon.app.questions.questionMathSecondGrade

import android.animation.ObjectAnimator
import android.content.Intent
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
import com.tetragon.app.databinding.ActivityMath2GradeQuestionBinding
import com.tetragon.app.questions.FiveCorrectAnswerFragment
import com.tetragon.app.questions.SubjectConstants
import com.tetragon.app.questions.XpGainedActivity
import com.tetragon.app.questions.questionMathSecondGrade.eighthTopic.*
import com.tetragon.app.questions.questionMathSecondGrade.fifthTopic.*
import com.tetragon.app.questions.questionMathSecondGrade.firstTopic.*
import com.tetragon.app.questions.questionMathSecondGrade.fourthTopic.*
import com.tetragon.app.questions.questionMathSecondGrade.secondTopic.*
import com.tetragon.app.questions.questionMathSecondGrade.seventhTopic.*
import com.tetragon.app.questions.questionMathSecondGrade.sixthTopic.*
import com.tetragon.app.questions.questionMathSecondGrade.thirdTopic.*
import com.tetragon.app.utils.languageChangeUtils.BaseActivity
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class Math2GradeQuestionActivity : BaseActivity() {

    private lateinit var binding: ActivityMath2GradeQuestionBinding
    private lateinit var selectedTopic: MathGrade2Topic
    private var lastQuestionType: MathGrade2Type? = null

    var totalXp: Int = 0
    var isResultCurrentlyVisible: Boolean = false
    var isCorrectAnswerShowing: Boolean = false
    private var correctAnswersCount = 0

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
        binding = ActivityMath2GradeQuestionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.exitBtn.setOnClickListener { showQuitBottomSheet() }
        onBackPressedDispatcher.addCallback(this) { showQuitBottomSheet() }

        val topicName = intent.getStringExtra("TOPIC_KEY")
            ?: throw IllegalArgumentException("TOPIC_KEY missing")

        selectedTopic = MathGrade2Topic.valueOf(topicName)

        observeConnectivity()

        if (savedInstanceState == null) {
            showRandomQuestion()
        }
    }

    fun handleCorrectAnswer() {
        correctAnswersCount++
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

    private fun getTypesForTopic(topic: MathGrade2Topic): List<MathGrade2Type> {
        return when (topic) {
            MathGrade2Topic.ADDITION_SUBTRACTION_BASICS -> listOf(MathGrade2Type.ARITHMETICS_BASICS, MathGrade2Type.ADDITION_INTERACTIVE)
            MathGrade2Topic.COLUMN_METHOD -> listOf(MathGrade2Type.COLUMN_METHOD_ADDITION, MathGrade2Type.COLUMN_METHOD_ADDITION_MATCH)
            MathGrade2Topic.THREE_DIGIT_NUMBERS -> listOf(MathGrade2Type.COLUMN_METHOD_ADDITION_THREE_DIGITS, MathGrade2Type.COLUMN_METHOD_SUBTRACTION_THREE_DIGITS)
            MathGrade2Topic.COMPARISON_THREE_DIGIT_NUMBERS -> listOf(MathGrade2Type.COMPARISON_THREE_DIGITS, MathGrade2Type.COMPARISON_THREE_DIGITS_ARITHMETICS)
            MathGrade2Topic.LENGTH_MEASUREMENT -> listOf(MathGrade2Type.METER_TO_CM, MathGrade2Type.MEASUREMENT_ARITHMETICS)
            MathGrade2Topic.TIME -> listOf(MathGrade2Type.SET_TIME, MathGrade2Type.TIME_ARITHMETICS)
            MathGrade2Topic.MULTIPLICATION -> listOf(MathGrade2Type.MULTIPLICATION_TABLE, MathGrade2Type.MULTIPLICATION_INTERACTIVE)
            MathGrade2Topic.DIVISION -> listOf(MathGrade2Type.DIVISION_SIMPLE, MathGrade2Type.DIVISION_INTERACTIVE)
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
            MathGrade2Type.ARITHMETICS_BASICS -> UiArithmeticsBasicsFragment()
            MathGrade2Type.ADDITION_INTERACTIVE -> UiAdditionInteractiveFragment()
            MathGrade2Type.COLUMN_METHOD_ADDITION -> UiColumnMethodFragment()
            MathGrade2Type.COLUMN_METHOD_ADDITION_MATCH -> UiColumnAdditionPairsFragment()
            MathGrade2Type.COLUMN_METHOD_ADDITION_THREE_DIGITS -> UiColumnAdditionThreeDigitNumbersFragment()
            MathGrade2Type.COLUMN_METHOD_SUBTRACTION_THREE_DIGITS -> UiColumnSubtractionThreeDigitNumbersFragment()
            MathGrade2Type.COMPARISON_THREE_DIGITS -> UiComparisonThreeDigitNumbersFragment()
            MathGrade2Type.COMPARISON_THREE_DIGITS_ARITHMETICS -> UiComparisonThreeGigitNumbersArithmeticsFragment()
            MathGrade2Type.METER_TO_CM -> UiMeterToCmFragment()
            MathGrade2Type.MEASUREMENT_ARITHMETICS -> UiMeasurementArithmeticsFragment()
            MathGrade2Type.SET_TIME -> UiSetTimeFragment()
            MathGrade2Type.TIME_ARITHMETICS -> UiTimeArithmeticsFragment()
            MathGrade2Type.MULTIPLICATION_TABLE -> UiMultiplicationFragment()
            MathGrade2Type.MULTIPLICATION_INTERACTIVE -> UiMultiplicationInteractiveFragment()
            MathGrade2Type.DIVISION_SIMPLE -> UiDivisionFragment()
            MathGrade2Type.DIVISION_INTERACTIVE -> UiDivisionInteractiveFragment()
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

    fun navigateToXpGained() {
        val intent = Intent(this, XpGainedActivity::class.java).apply {
            putExtra(SubjectConstants.EXTRA_XP, totalXp)
            putExtra(SubjectConstants.EXTRA_TOPIC, selectedTopic.name)
            putExtra(SubjectConstants.EXTRA_GRADE, 2)
            putExtra(SubjectConstants.EXTRA_SUBJECT, SubjectConstants.SUBJECT_MATH)
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
            setOnClickListener { finish(); dialog.dismiss() }
        }
        dialog.show()
    }

    private fun observeConnectivity() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.isConnected.collect { isConnected ->
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
        }
    }
}