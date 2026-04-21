package com.example.tetragon.questions.questionMathSecondGrade

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
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.*
import app.rive.runtime.kotlin.core.Rive
import com.example.tetragon.R
import com.example.tetragon.connectivityCheck.AndroidConnectivityObserver
import com.example.tetragon.connectivityCheck.ConnectivityViewModel
import com.example.tetragon.databinding.ActivityQuestionQctivityBinding
import com.example.tetragon.questions.FiveCorrectAnswerFragment
import com.example.tetragon.questions.SubjectConstants
import com.example.tetragon.questions.XpGainedActivity
import com.example.tetragon.questions.questionMathSecondGrade.firstTopic.UiAdditionInteractiveFragment
import com.example.tetragon.questions.questionMathSecondGrade.firstTopic.UiArithmeticsBasicsFragment
import com.example.tetragon.questions.questionMathSecondGrade.secondTopic.UiColumnAdditionPairsFragment
import com.example.tetragon.questions.questionMathSecondGrade.secondTopic.UiColumnMethodFragment
import com.example.tetragon.questions.questionMathFirstGrade.secondTopicAddition.UiAiAdditionFragment
import com.example.tetragon.questions.questionMathSecondGrade.eighthTopic.UiDivisionFragment
import com.example.tetragon.questions.questionMathSecondGrade.eighthTopic.UiDivisionInteractiveFragment
import com.example.tetragon.questions.questionMathSecondGrade.fifthTopic.UiMeasurementArithmeticsFragment
import com.example.tetragon.questions.questionMathSecondGrade.fifthTopic.UiMeterToCmFragment
import com.example.tetragon.questions.questionMathSecondGrade.fourthTopic.UiComparisonThreeDigitNumbersFragment
import com.example.tetragon.questions.questionMathSecondGrade.fourthTopic.UiComparisonThreeGigitNumbersArithmeticsFragment
import com.example.tetragon.questions.questionMathSecondGrade.seventhTopic.UiMultiplicationFragment
import com.example.tetragon.questions.questionMathSecondGrade.seventhTopic.UiMultiplicationInteractiveFragment
import com.example.tetragon.questions.questionMathSecondGrade.sixthTopic.UiSetTimeFragment
import com.example.tetragon.questions.questionMathSecondGrade.sixthTopic.UiTimeArithmeticsFragment
import com.example.tetragon.questions.questionMathSecondGrade.thirdTopic.UiColumnAdditionThreeDigitNumbersFragment
import com.example.tetragon.questions.questionMathSecondGrade.thirdTopic.UiColumnSubtractionThreeDigitNumbersFragment
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class Math2GradeQuestionActivity : AppCompatActivity() {

    private lateinit var binding: ActivityQuestionQctivityBinding
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
        binding = ActivityQuestionQctivityBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // UI Styling
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            window.decorView.systemUiVisibility = window.decorView.systemUiVisibility or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
            window.navigationBarColor = ContextCompat.getColor(this, R.color.white)
        }

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
            MathGrade2Topic.ADDITION_SUBTRACTION_BASICS -> listOf(
                MathGrade2Type.ARITHMETICS_BASICS,
                MathGrade2Type.ADDITION_INTERACTIVE
            )

            MathGrade2Topic.COLUMN_METHOD -> listOf(
                MathGrade2Type.COLUMN_METHOD_ADDITION,
                MathGrade2Type.COLUMN_METHOD_ADDITION_MATCH
            )

            MathGrade2Topic.THREE_DIGIT_NUMBERS -> listOf(
                MathGrade2Type.COLUMN_METHOD_ADDITION_THREE_DIGITS,
                MathGrade2Type.COLUMN_METHOD_SUBTRACTION_THREE_DIGITS,
            )

            MathGrade2Topic.COMPARISON_THREE_DIGIT_NUMBERS -> listOf(
                MathGrade2Type.COMPARISON_THREE_DIGITS,
                MathGrade2Type.COMPARISON_THREE_DIGITS_ARITHMETICS,
            )

            MathGrade2Topic.LENGTH_MEASUREMENT -> listOf(
                MathGrade2Type.METER_TO_CM,
                MathGrade2Type.MEASUREMENT_ARITHMETICS,
            )

            MathGrade2Topic.TIME -> listOf(
                MathGrade2Type.SET_TIME,
                MathGrade2Type.TIME_ARITHMETICS
            )

            MathGrade2Topic.MULTIPLICATION -> listOf(
                MathGrade2Type.MULTIPLICATION_TABLE,
                MathGrade2Type.MULTIPLICATION_INTERACTIVE
            )

            MathGrade2Topic.DIVISION -> listOf(
                MathGrade2Type.DIVISION_SIMPLE,
                MathGrade2Type.DIVISION_INTERACTIVE,
            )
        }
    }

    fun showRandomQuestion() {
        isResultCurrentlyVisible = false
        hideSuccessAnimation()
        binding.correctMrSquare.visibility = View.INVISIBLE

        // 1. Get all possible types for this topic
        val allTypes = getTypesForTopic(selectedTopic)

        // 2. Filter out the last question type to prevent back-to-back repeats
        val filteredTypes = allTypes.filter { it != lastQuestionType }

        // 3. Pick from the filtered list.
        // If for some reason the filtered list is empty (only 1 type exists total),
        // fallback to the full list.
        val nextType = if (filteredTypes.isNotEmpty()) {
            filteredTypes.random()
        } else {
            allTypes.random()
        }

        // 4. Update the tracker
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

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
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
            putExtra(SubjectConstants.EXTRA_GRADE, 2) // Grade 2
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

        // 1. Find the TextViews and Buttons by ID
        val titleText = view.findViewById<TextView>(R.id.titleText)
        val messageText = view.findViewById<TextView>(R.id.messageText)
        val continueButton = view.findViewById<Button>(R.id.noButton)
        val finishButton = view.findViewById<Button>(R.id.finishButton)

        // 2. Set the custom text
        titleText.text = "Are you sure?"
        messageText.text = "If you exit, you will lose all the points you gained in this lesson."

        continueButton.text = "CONTINUE LESSON"
        finishButton.text = "EXIT"

        // 3. Set the Click Listeners
        continueButton.setOnClickListener {
            dialog.dismiss()
        }

        finishButton.setOnClickListener {
            finish()
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun observeConnectivity() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.isConnected.collect { isConnected ->
                    if (isConnected) {
                        binding.internetConnection.visibility = View.GONE
                        binding.offlineContainer.visibility = View.GONE
                        binding.topBarContainer.visibility = View.VISIBLE
                        binding.questionFragmentContainer.visibility = View.VISIBLE
                        binding.btnBackground.visibility = View.VISIBLE
                        if (isResultCurrentlyVisible) {
                            binding.stateContainer.visibility = View.VISIBLE
                            binding.correctMrSquare.visibility = if (isCorrectAnswerShowing) View.VISIBLE else View.INVISIBLE
                        }
                    } else {
                        binding.internetConnection.visibility = View.VISIBLE
                        binding.offlineContainer.visibility = View.VISIBLE
                        binding.topBarContainer.visibility = View.GONE
                        binding.questionFragmentContainer.visibility = View.GONE
                        binding.btnBackground.visibility = View.GONE
                        binding.stateContainer.visibility = View.GONE
                        binding.correctMrSquare.visibility = View.GONE
                    }
                }
            }
        }
    }
}