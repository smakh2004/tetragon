package com.tetragon.app.questions.questionMathFirstGrade

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.activity.addCallback
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.lifecycle.*
import app.rive.runtime.kotlin.core.Rive
import com.tetragon.app.R
import com.tetragon.app.connectivityCheck.AndroidConnectivityObserver
import com.tetragon.app.connectivityCheck.ConnectivityViewModel
import com.tetragon.app.databinding.ActivityQuestionQctivityBinding
import com.tetragon.app.questions.FiveCorrectAnswerFragment
import com.tetragon.app.questions.SubjectConstants
import com.tetragon.app.questions.XpGainedActivity
import com.tetragon.app.questions.questionMathFirstGrade.firstTopicCountingNumbers.*
import com.tetragon.app.questions.questionMathFirstGrade.secondTopicAddition.*
import com.tetragon.app.questions.questionMathFirstGrade.thirdTopicSubtraction.*
import com.tetragon.app.questions.questionMathFirstGrade.fourthTopicOddOrEvenNumbers.*
import com.tetragon.app.questions.questionMathFirstGrade.fifthTopicComparison.*
import com.tetragon.app.questions.questionMathFirstGrade.sixthTopicParentheses.*
import com.tetragon.app.questions.questionMathFirstGrade.seventhTopicAdditionUpTo20.*
import com.tetragon.app.questions.questionMathFirstGrade.eighthTopicSubtractionUpTo20.*
import com.tetragon.app.questions.questionMathFirstGrade.ninthTopicRoundNumbers.*
import com.tetragon.app.questions.questionMathFirstGrade.tenthTopicTwoDigitNumbers.*
import com.tetragon.app.questions.questionMathFirstGrade.eleventhTopicProblemSolving.*
import com.tetragon.app.questions.questionMathFirstGrade.twelvesTopicLengthCentimeter.ShowCmInRullerFragment
import com.tetragon.app.questions.questionMathFirstGrade.twelvesTopicLengthCentimeter.UiDmInCmFragment
import com.tetragon.app.utils.languageChangeUtils.BaseActivity
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.tetragon.app.questions.MathComplexity
import kotlinx.coroutines.launch

class Math1GradeQuestionActivity : BaseActivity() {

    private lateinit var binding: ActivityQuestionQctivityBinding
    private lateinit var selectedTopic: MathGrade1Topic

    private var lastQuestionType: MathGrade1Type? = null

    var totalXp: Int = 0

    var isResultCurrentlyVisible: Boolean = false
    var isCorrectAnswerShowing: Boolean = false

    private var correctAnswersCount = 0

    private var currentComplexity: MathComplexity = MathComplexity.EASY

    // ✅ Track the last displayed complexity level to handle animation filters
    private var previousComplexity: MathComplexity? = null

    private var consecutiveCorrectAtLevel = 0
    private var isOnSecondChance = false

    private val viewModel: ConnectivityViewModel by viewModels {
        object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                if (modelClass.isAssignableFrom(ConnectivityViewModel::class.java)) {
                    return ConnectivityViewModel(
                        AndroidConnectivityObserver(applicationContext)
                    ) as T
                }
                throw IllegalArgumentException("Unknown ViewModel class")
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Rive.init(this)

        binding = ActivityQuestionQctivityBinding.inflate(layoutInflater)
        setContentView(binding.root)

        updateComplexityUi(currentComplexity)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            window.decorView.systemUiVisibility =
                window.decorView.systemUiVisibility or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR

            window.navigationBarColor =
                ContextCompat.getColor(this, R.color.white)
        }

        binding.exitBtn.setOnClickListener { showQuitBottomSheet() }

        onBackPressedDispatcher.addCallback(this) {
            showQuitBottomSheet()
        }

        val topicName = intent.getStringExtra("TOPIC_KEY")
            ?: throw IllegalArgumentException("TOPIC_KEY missing")

        selectedTopic = MathGrade1Topic.valueOf(topicName)

        observeConnectivity()

        if (savedInstanceState == null) {
            showRandomQuestion()
        }
    }

    fun handleCorrectAnswer() {
        correctAnswersCount++
        consecutiveCorrectAtLevel++
        isOnSecondChance = false

        if (consecutiveCorrectAtLevel >= 3) {
            currentComplexity = when (currentComplexity) {
                MathComplexity.EASY -> MathComplexity.MEDIUM
                MathComplexity.MEDIUM -> MathComplexity.HARD
                MathComplexity.HARD -> MathComplexity.HARD
            }
            consecutiveCorrectAtLevel = 0
        }
    }

    fun handleIncorrectAnswer() {
        consecutiveCorrectAtLevel = 0

        if (!isOnSecondChance) {
            isOnSecondChance = true
        } else {
            currentComplexity = when (currentComplexity) {
                MathComplexity.HARD -> MathComplexity.MEDIUM
                MathComplexity.MEDIUM -> MathComplexity.EASY
                MathComplexity.EASY -> MathComplexity.EASY
            }
            isOnSecondChance = false
        }
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

        // Force reset snapshot tracking during milestone sweeps
        previousComplexity = null

        binding.complexityContainer.animate()
            .alpha(0f)
            .setDuration(300)
            .withEndAction {
                binding.complexityContainer.visibility = View.GONE
            }
            .start()

        supportFragmentManager.beginTransaction()
            .setCustomAnimations(R.anim.slide_in_right, R.anim.slide_out_left)
            .replace(R.id.questionFragmentContainer, FiveCorrectAnswerFragment())
            .commit()

        lifecycleScope.launch {
            kotlinx.coroutines.delay(2500)
            binding.btnBackground.visibility = View.VISIBLE
            showRandomQuestion()
        }
    }

    fun getCorrectAnswersCount(): Int = correctAnswersCount

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
    }

    private fun getTypesForTopic(topic: MathGrade1Topic): List<MathGrade1Type> {
        return when (topic) {
            MathGrade1Topic.COUNT_NUMBERS -> listOf(
                MathGrade1Type.APPLE,
                MathGrade1Type.FIND_MISSED_NUMBER,
                MathGrade1Type.ANGLES,
                MathGrade1Type.FIND_NEXT_NUMBER
            )

            MathGrade1Topic.ADDITION -> listOf(
                MathGrade1Type.ADDITION_NUMBERS,
                MathGrade1Type.ADDITION_MISSED_NUMBER,
                MathGrade1Type.ADDITION_TWO_REPRESENTATION,
                MathGrade1Type.ADDITION_THREE_REPRESENTATION,
                MathGrade1Type.ADDITION_VISUAL_PROBLEM,
                MathGrade1Type.ADDITION_TREE,
                MathGrade1Type.BASIC_AI_ADDITION
            )

            MathGrade1Topic.SUBTRACTION -> listOf(
                MathGrade1Type.SUBTRACTION_NUMBERS,
                MathGrade1Type.SUBTRACTION_MISSED_NUMBER,
                MathGrade1Type.SUBTRACTION_TWO_REPRESENTATION
            )

            MathGrade1Topic.ODD_OR_EVEN -> listOf(
                MathGrade1Type.UP_TO_EVEN_OR_ODD,
                MathGrade1Type.EVEN_OR_ODD,
                MathGrade1Type.FIND_EVEN_OR_ODD_SEQUENCE,
                MathGrade1Type.CONTINUE_SEQUENCE_ODD_OR_EVEN
            )

            MathGrade1Topic.COMPARISON -> listOf(
                MathGrade1Type.COMPARISON,
                MathGrade1Type.COMPARISON_TRUE_OR_FALSE,
                MathGrade1Type.COMPARISON_GRAPE_OR_STRAWBERRY
            )

            MathGrade1Topic.PARENTHESES -> listOf(
                MathGrade1Type.PARENTHESES,
                MathGrade1Type.PARENTHESES_MISSED
            )

            MathGrade1Topic.ADDITION_UP_TO_20 -> listOf(
                MathGrade1Type.ADDITION_20,
                MathGrade1Type.ADDITION_MISSED_NUMBER_20,
                MathGrade1Type.ADDITION_THREE_REPRESENTATION_20,
                MathGrade1Type.ADDITION_TWO_REPRESENT_20,
                MathGrade1Type.ADDITION_VISUAL_PROBLEM_20
            )

            MathGrade1Topic.SUBTRACTION_UP_TO_20 -> listOf(
                MathGrade1Type.SUBTRACTION_MISSED_NUMBER_20,
                MathGrade1Type.SUBTRACTION_TWO_REPRESENTATION_20,
                MathGrade1Type.SUBTRACTION_NUMBERS_20
            )

            MathGrade1Topic.ROUND_NUMBERS -> listOf(
                MathGrade1Type.CONTINUE_SEQUENCE_ROUND_NUMBERS,
                MathGrade1Type.ARITHMETICS_ROUND_NUMBERS,
                MathGrade1Type.CLOSEST_ROUND_NUMBERS,
                MathGrade1Type.HOW_MANY_TENS_ROUND_NUMBERS
            )

            MathGrade1Topic.TWO_DIGIT_NUMBERS -> listOf(
                MathGrade1Type.COMPARISON_TWO_DIGITS,
                MathGrade1Type.ARITHMETICS_TWO_DIGIT_NUMBERS,
                MathGrade1Type.COMPARISON_TRUE_OR_FALSE_TWO_DIGIT_NUMBERS,
                MathGrade1Type.ADDITION_TWO_DIGIT_NUMBER_REPRESENTATION,
                MathGrade1Type.ARITHMETICS_VISUAL_TWO_DIGIT_NUMBERS_PROBLEM,
                MathGrade1Type.PARENTHESES_TWO_DIGIT_NUMBERS,
                MathGrade1Type.PARENTHESES_MISSED_TWO_DIGIT_NUMBERS
            )

            MathGrade1Topic.PROBLEM_SOLVING -> listOf(
                MathGrade1Type.AZIZ_APPLE_PROBLEM,
                MathGrade1Type.FISH
            )

            MathGrade1Topic.LENGTH_CENTIMETER -> listOf(
                MathGrade1Type.CM_RULER,
                MathGrade1Type.DM_IN_CM
            )
        }
    }

    fun showRandomQuestion() {
        isResultCurrentlyVisible = false
        hideSuccessAnimation()
        binding.correctMrSquare.visibility = View.INVISIBLE

        // ✅ Check if the complexity has actually changed before playing the animation
        if (currentComplexity != previousComplexity) {
            updateComplexityUi(currentComplexity)

            binding.complexityContainer.apply {
                visibility = View.VISIBLE
                alpha = 0f
                animate()
                    .alpha(1f)
                    .setDuration(400)
                    .start()
            }
            // Update historical memory snapshot
            previousComplexity = currentComplexity
        } else {
            // ✅ Fallback safety case: keeps view visibility sound without triggering transitions
            binding.complexityContainer.visibility = View.VISIBLE
            binding.complexityContainer.alpha = 1f
        }

        val allTypes = getTypesForTopic(selectedTopic)
        val atLevel = allTypes.filter { it.complexity == currentComplexity }
        val pool = if (atLevel.isNotEmpty()) atLevel else allTypes

        val availableTypes = pool.toMutableList()
        if (availableTypes.size > 1) lastQuestionType?.let { availableTypes.remove(it) }

        val nextType = availableTypes.random()
        lastQuestionType = nextType

        val fragment = when (nextType) {
            MathGrade1Type.APPLE -> UiAppleQuestionFragment()
            MathGrade1Type.FIND_MISSED_NUMBER -> UiFindMIssedNumberInSequenceFragment()
            MathGrade1Type.ANGLES -> UiNumberOfAnglesShapeFragment()
            MathGrade1Type.FIND_NEXT_NUMBER -> UiFindNextNumberFragment()

            MathGrade1Type.ADDITION_NUMBERS -> UiAdditionFragment()
            MathGrade1Type.ADDITION_MISSED_NUMBER -> UiFindMissedNumberInAddition()
            MathGrade1Type.ADDITION_TWO_REPRESENTATION -> UiRepresentSumOfTwoNumbersFragment()
            MathGrade1Type.ADDITION_THREE_REPRESENTATION -> UiRepresentSumOfThreeNumbersFragment()
            MathGrade1Type.ADDITION_VISUAL_PROBLEM -> UiVisualAdditionProblemFragment()
            MathGrade1Type.ADDITION_TREE -> UiAdditionTreeFragment()
            MathGrade1Type.BASIC_AI_ADDITION -> UiAiAdditionFragment()

            MathGrade1Type.SUBTRACTION_NUMBERS -> UiSubtractionFragment()
            MathGrade1Type.SUBTRACTION_MISSED_NUMBER -> UiFindMissedNumberInSubtractionFragment()
            MathGrade1Type.SUBTRACTION_TWO_REPRESENTATION -> UiRepresentSubtractionOfTwoNumbersFragment()

            MathGrade1Type.UP_TO_EVEN_OR_ODD -> UiWhatIsEvenOrOddNumberUpToFragment()
            MathGrade1Type.EVEN_OR_ODD -> UiOddOrEvenNumberFragment()
            MathGrade1Type.FIND_EVEN_OR_ODD_SEQUENCE -> UiFindOddOrEvenNumbersInSequenceFragment()
            MathGrade1Type.CONTINUE_SEQUENCE_ODD_OR_EVEN -> UiContinueSequenceOddOrEvenFragment()

            MathGrade1Type.COMPARISON -> UiComparisonNumbersFragment()
            MathGrade1Type.COMPARISON_TRUE_OR_FALSE -> UiTrueOrFalseComparisonFragment()
            MathGrade1Type.COMPARISON_GRAPE_OR_STRAWBERRY -> UiComparisonAppleAndStrawberryFragment()

            MathGrade1Type.PARENTHESES -> UiParenthesesFragment()
            MathGrade1Type.PARENTHESES_MISSED -> UiParenthesesMissedFragment()

            MathGrade1Type.ADDITION_20 -> UiAdditionUpTo20Fragment()
            MathGrade1Type.ADDITION_MISSED_NUMBER_20 -> UiFindMissedNumberInAddition20Fragment()
            MathGrade1Type.ADDITION_THREE_REPRESENTATION_20 -> UiRepresentSumOfThreeNumbers20Fragment()
            MathGrade1Type.ADDITION_TWO_REPRESENT_20 -> UiRepresentSumOfTwoNumbers20Fragment()
            MathGrade1Type.ADDITION_VISUAL_PROBLEM_20 -> UiVisualAdditionProblem20Fragment()

            MathGrade1Type.SUBTRACTION_MISSED_NUMBER_20 -> UiFindMissedNumberInSubtraction20Fragment()
            MathGrade1Type.SUBTRACTION_TWO_REPRESENTATION_20 -> UiRepresentSubtractionOfTwoNumbers20Fragment()
            MathGrade1Type.SUBTRACTION_NUMBERS_20 -> UiSubtraction20Fragment()

            MathGrade1Type.CONTINUE_SEQUENCE_ROUND_NUMBERS -> UiContinueSequenceRoundNumbersFragment()
            MathGrade1Type.ARITHMETICS_ROUND_NUMBERS -> UiArithmeticsRoundNumbersFragment()
            MathGrade1Type.CLOSEST_ROUND_NUMBERS -> UiFindCloseRoundNumberFragment()
            MathGrade1Type.HOW_MANY_TENS_ROUND_NUMBERS -> UiHowManyTensFragment()

            MathGrade1Type.COMPARISON_TWO_DIGITS -> UiComparisonTwoDigitNumbersFragment()
            MathGrade1Type.ARITHMETICS_TWO_DIGIT_NUMBERS -> UiArithmeticsTwoDigitNumbersFragment()
            MathGrade1Type.COMPARISON_TRUE_OR_FALSE_TWO_DIGIT_NUMBERS -> UiTrueOrFalseComparisonTwoDigitNumbersFragment()
            MathGrade1Type.ADDITION_TWO_DIGIT_NUMBER_REPRESENTATION -> UiRepresentSumOfTwoDigitNumbersFragment()
            MathGrade1Type.ARITHMETICS_VISUAL_TWO_DIGIT_NUMBERS_PROBLEM -> UiVisualArithmeticsTwoDigitNumbersFragment()
            MathGrade1Type.PARENTHESES_TWO_DIGIT_NUMBERS -> UiParenthesesTwoDigitNumbersFragment()
            MathGrade1Type.PARENTHESES_MISSED_TWO_DIGIT_NUMBERS -> UiParenthesesMissedTwoDigitNumbersFragment()

            MathGrade1Type.FISH -> UiFishArithmeticProblemFragment()
            MathGrade1Type.AZIZ_APPLE_PROBLEM -> UiApplesAzizProblemFragment()

            MathGrade1Type.CM_RULER -> ShowCmInRullerFragment()
            MathGrade1Type.DM_IN_CM -> UiDmInCmFragment()
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

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
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
            putExtra(SubjectConstants.EXTRA_GRADE, 1)
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

        val titleText = view.findViewById<TextView>(R.id.titleText)
        val messageText = view.findViewById<TextView>(R.id.messageText)
        val continueButton = view.findViewById<Button>(R.id.noButton)
        val finishButton = view.findViewById<Button>(R.id.finishButton)

        titleText.text = getString(R.string.quit_title)
        messageText.text = getString(R.string.quit_message)
        continueButton.text = getString(R.string.continue_btn)
        finishButton.text = getString(R.string.exit_btn)

        continueButton.setOnClickListener { dialog.dismiss() }
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
                        binding.complexityContainer.visibility = View.VISIBLE

                        if (isResultCurrentlyVisible) {
                            binding.stateContainer.visibility = View.VISIBLE
                            binding.correctMrSquare.visibility =
                                if (isCorrectAnswerShowing) View.VISIBLE else View.INVISIBLE
                        } else {
                            binding.stateContainer.visibility = View.INVISIBLE
                            binding.correctMrSquare.visibility = View.INVISIBLE
                        }
                    } else {
                        binding.internetConnection.visibility = View.VISIBLE
                        binding.offlineContainer.visibility = View.VISIBLE
                        binding.topBarContainer.visibility = View.GONE
                        binding.questionFragmentContainer.visibility = View.GONE
                        binding.btnBackground.visibility = View.GONE
                        binding.stateContainer.visibility = View.GONE
                        binding.correctMrSquare.visibility = View.GONE
                        binding.complexityContainer.visibility = View.GONE
                    }
                }
            }
        }
    }

    private fun updateComplexityUi(complexity: MathComplexity) {
        val (iconRes, textRes, colorRes) = when (complexity) {
            MathComplexity.EASY -> Triple(
                R.drawable.easy,
                R.string.complexity_easy,
                R.color.green_1
            )

            MathComplexity.MEDIUM -> Triple(
                R.drawable.medium,
                R.string.complexity_medium,
                R.color.orange_1
            )

            MathComplexity.HARD -> Triple(R.drawable.hard, R.string.complexity_hard, R.color.red_1)
        }
        binding.complexityIcon.setImageResource(iconRes)
        binding.complexityText.setText(textRes)
        binding.complexityText.setTextColor(ContextCompat.getColor(this, colorRes))
    }
}