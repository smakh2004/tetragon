package com.example.tetragon.questions.questionMathFirstGrade

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
import com.example.tetragon.databinding.ActivityQuestionQctivityBinding
import com.example.tetragon.questions.FiveCorrectAnswerFragment
import com.example.tetragon.questions.SubjectConstants
import com.example.tetragon.questions.XpGainedActivity
import com.example.tetragon.questions.questionMathFirstGrade.firstTopicCountingNumbers.*
import com.example.tetragon.questions.questionMathFirstGrade.secondTopicAddition.*
import com.example.tetragon.questions.questionMathFirstGrade.thirdTopicSubtraction.*
import com.example.tetragon.questions.questionMathFirstGrade.fourthTopicOddOrEvenNumbers.*
import com.example.tetragon.questions.questionMathFirstGrade.fifthTopicComparison.*
import com.example.tetragon.questions.questionMathFirstGrade.sixthTopicParentheses.*
import com.example.tetragon.questions.questionMathFirstGrade.seventhTopicAdditionUpTo20.*
import com.example.tetragon.questions.questionMathFirstGrade.eighthTopicSubtractionUpTo20.*
import com.example.tetragon.questions.questionMathFirstGrade.ninthTopicRoundNumbers.*
import com.example.tetragon.questions.questionMathFirstGrade.tenthTopicTwoDigitNumbers.*
import com.example.tetragon.questions.questionMathFirstGrade.eleventhTopicProblemSolving.*
import com.example.tetragon.questions.questionMathFirstGrade.twelvesTopicLengthCentimeter.ShowCmInRullerFragment
import com.example.tetragon.questions.questionMathFirstGrade.twelvesTopicLengthCentimeter.UiDmInCmFragment
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlinx.coroutines.launch

class Math1GradeQuestionActivity : AppCompatActivity() {

    private lateinit var binding: ActivityQuestionQctivityBinding
    private lateinit var selectedTopic: MathGrade1Topic

    private var lastQuestionType: MathGrade1Type? = null

    var totalXp: Int = 0

    var isResultCurrentlyVisible: Boolean = false
    var isCorrectAnswerShowing: Boolean = false

    private var correctAnswersCount = 0

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
    }

    fun checkAndTriggerMilestone(): Boolean {
        if (correctAnswersCount >= 5) {
            correctAnswersCount = 0
            triggerMilestoneSequence()
            return true // Milestone is happening
        }
        return false // No milestone, proceed normally
    }

    private fun triggerMilestoneSequence() {
        isResultCurrentlyVisible = false

        // 1. Hide the interaction UI and result states
        binding.stateContainer.visibility = View.GONE
        binding.correctMrSquare.visibility = View.GONE
        binding.btnBackground.visibility = View.GONE

        // 2. Swap to the milestone fragment
        supportFragmentManager.beginTransaction()
            .setCustomAnimations(R.anim.slide_in_right, R.anim.slide_out_left)
            .replace(R.id.questionFragmentContainer, FiveCorrectAnswerFragment())
            .commit()

        // 3. Automated 3-second delay
        lifecycleScope.launch {
            kotlinx.coroutines.delay(2500)

            // Restore UI visibility for the next question
            binding.btnBackground.visibility = View.VISIBLE

            // Load the next question automatically
            showRandomQuestion()
        }
    }

    // Add this helper so fragments can check the count safely
    fun getCorrectAnswersCount(): Int = correctAnswersCount

    fun playSuccessAnimation() {
        binding.correctMrSquare.apply {
            // 1. Randomly decide which animation file to load
            val randomResource = if ((0..1).random() == 0) {
                R.raw.mr_square_correct
            } else {
                R.raw.mr_square_correct_2
            }

            // 2. Assign the chosen resource to the RiveAnimationView
            setRiveResource(randomResource)

            // 3. Make it visible and play the state machine
            visibility = View.VISIBLE
            fireState("State Machine 1", "play")
        }
    }

    fun hideSuccessAnimation() {
        binding.correctMrSquare.apply {
            // ✅ Trigger the exit transition in your State Machine
            fireState("State Machine 1", "finish")

            // Optional: If you want to be 100% sure it stops drawing
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
                MathGrade1Type.BASIC_AI_ADDITION,
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
                MathGrade1Type.DM_IN_CM,
            )
        }
    }

    fun showRandomQuestion() {
        isResultCurrentlyVisible = false

        // ✅ Reset the animation state immediately
        hideSuccessAnimation()

        binding.correctMrSquare.visibility = View.INVISIBLE

        val availableTypes = getTypesForTopic(selectedTopic).toMutableList()
        lastQuestionType?.let { availableTypes.remove(it) }

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

        // Apply the Right-to-Left animation here
        supportFragmentManager.beginTransaction()
            .setCustomAnimations(
                R.anim.slide_in_right,  // New fragment enters from the right
                R.anim.slide_out_left,  // Old fragment exits to the left
                R.anim.slide_in_left,   // For popBackStack (optional)
                R.anim.slide_out_right  // For popBackStack (optional)
            )
            .replace(R.id.questionFragmentContainer, fragment)
            .commit()
    }

    fun incrementProgress(): Boolean {

        val progressBar = binding.progressBar
        val increment = progressBar.max / 10

        val newProgress = (progressBar.progress + increment).coerceAtMost(progressBar.max)

        // ✅ Smooth animation
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            progressBar.setProgress(newProgress, true)
        } else {
            // fallback for older devices
            val animator = android.animation.ObjectAnimator.ofInt(
                progressBar,
                "progress",
                progressBar.progress,
                newProgress
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
            putExtra(SubjectConstants.EXTRA_GRADE, 1) // Grade 1
            putExtra(SubjectConstants.EXTRA_SUBJECT, SubjectConstants.SUBJECT_MATH)
        }
        startActivity(intent)
        overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
        finish()
    }

    private fun showQuitBottomSheet() {

        val dialog = BottomSheetDialog(this)

        val view = layoutInflater.inflate(
            R.layout.dialog_quit,
            null
        )

        dialog.setContentView(view)

        val titleText = view.findViewById<TextView>(R.id.titleText)
        val messageText = view.findViewById<TextView>(R.id.messageText)
        val continueButton = view.findViewById<Button>(R.id.noButton)
        val finishButton = view.findViewById<Button>(R.id.finishButton)

        titleText.text = "Are you sure?"
        messageText.text =
            "If you exit, you will lose all the points you gained in this lesson."

        continueButton.text = "CONTINUE LESSON"
        finishButton.text = "EXIT"

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

                            binding.correctMrSquare.visibility =
                                if (isCorrectAnswerShowing)
                                    View.VISIBLE
                                else
                                    View.INVISIBLE
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
                    }
                }
            }
        }
    }
}