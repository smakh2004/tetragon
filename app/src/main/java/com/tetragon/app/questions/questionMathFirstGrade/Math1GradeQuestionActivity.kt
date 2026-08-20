package com.tetragon.app.questions.questionMathFirstGrade

import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.View
import android.view.animation.AnticipateOvershootInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextView
import androidx.activity.addCallback
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.*
import app.rive.runtime.kotlin.core.Rive
import com.tetragon.app.R
import com.tetragon.app.connectivityCheck.AndroidConnectivityObserver
import com.tetragon.app.connectivityCheck.ConnectivityViewModel
import com.tetragon.app.databinding.ActivityQuestionQctivityBinding
import com.tetragon.app.questions.FiveCorrectAnswerFragment
import com.tetragon.app.questions.EightCorrectAnswersFragment
import com.tetragon.app.questions.MathComplexity
import com.tetragon.app.questions.SubjectConstants
import com.tetragon.app.questions.XpGainedActivity
import com.tetragon.app.questions.questionMathFirstGrade.eighthTopicSubtractionUpTo20.*
import com.tetragon.app.questions.questionMathFirstGrade.eleventhTopicProblemSolving.*
import com.tetragon.app.questions.questionMathFirstGrade.fifthTopicComparison.*
import com.tetragon.app.questions.questionMathFirstGrade.firstTopicCountingNumbers.easy.UiNumberTracer.UiNumberTraceFragment
import com.tetragon.app.questions.questionMathFirstGrade.fourthTopicOddOrEvenNumbers.*
import com.tetragon.app.questions.questionMathFirstGrade.ninthTopicRoundNumbers.*
import com.tetragon.app.questions.questionMathFirstGrade.seventhTopicAdditionUpTo20.*
import com.tetragon.app.questions.questionMathFirstGrade.sixthTopicParentheses.*
import com.tetragon.app.questions.questionMathFirstGrade.tenthTopicTwoDigitNumbers.*
import com.tetragon.app.questions.questionMathFirstGrade.thirdTopicSubtraction.*
import com.tetragon.app.questions.questionMathFirstGrade.twelvesTopicLengthCentimeter.ShowCmInRullerFragment
import com.tetragon.app.questions.questionMathFirstGrade.twelvesTopicLengthCentimeter.UiDmInCmFragment
import com.tetragon.app.utils.languageChangeUtils.BaseActivity
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.tetragon.app.questions.questionMathFirstGrade.firstTopicCounting.hard.UiCountBackFragment
import com.tetragon.app.questions.questionMathFirstGrade.firstTopicCounting.hard.UiCountOnFragment
import com.tetragon.app.questions.questionMathFirstGrade.firstTopicCounting.hard.UiEvenNeighborsFragment
import com.tetragon.app.questions.questionMathFirstGrade.firstTopicCounting.hard.UiFindParityNumberFragment
import com.tetragon.app.questions.questionMathFirstGrade.firstTopicCountingNumbers.easy.UiAppleCountFragment
import com.tetragon.app.questions.questionMathFirstGrade.firstTopicCountingNumbers.easy.UiAppleQuestionFragment
import com.tetragon.app.questions.questionMathFirstGrade.firstTopicCountingNumbers.easy.UiCountWithHandsFragment
import com.tetragon.app.questions.questionMathFirstGrade.firstTopicCountingNumbers.hard.UiConnectionNumberFragment
import com.tetragon.app.questions.questionMathFirstGrade.firstTopicCountingNumbers.hard.UiCountByDragFragment
import com.tetragon.app.questions.questionMathFirstGrade.firstTopicCountingNumbers.hard.UiCountByNumberFragment
import com.tetragon.app.questions.questionMathFirstGrade.firstTopicCountingNumbers.hard.UiFindNextNumberFragment
import com.tetragon.app.questions.questionMathFirstGrade.firstTopicCountingNumbers.medium.UiFindLargestNumberFragment
import com.tetragon.app.questions.questionMathFirstGrade.firstTopicCountingNumbers.medium.UiFindMIssedNumberInSequenceFragment
import com.tetragon.app.questions.questionMathFirstGrade.firstTopicCountingNumbers.medium.UiFindSmallestNumberFragment
import com.tetragon.app.questions.questionMathFirstGrade.firstTopicCountingNumbers.medium.UiNumberOfAnglesShapeFragment
import com.tetragon.app.questions.questionMathFirstGrade.fourthTopicOddOrEvenNumbers.easy.UiContinueSequenceOddOrEvenFragment
import com.tetragon.app.questions.questionMathFirstGrade.fourthTopicOddOrEvenNumbers.easy.UiOddEvenSubtractionFragment
import com.tetragon.app.questions.questionMathFirstGrade.fourthTopicOddOrEvenNumbers.hard.UiChooseTwoParityFragment
import com.tetragon.app.questions.questionMathFirstGrade.fourthTopicOddOrEvenNumbers.hard.UiConnectParityNumbersFragment
import com.tetragon.app.questions.questionMathFirstGrade.fourthTopicOddOrEvenNumbers.hard.UiWhatIsEvenOrOddNumberUpToFragment
import com.tetragon.app.questions.questionMathFirstGrade.fourthTopicOddOrEvenNumbers.medium.UiFindOddOrEvenNumbersInSequenceFragment
import com.tetragon.app.questions.questionMathFirstGrade.fourthTopicOddOrEvenNumbers.medium.UiOddOrEvenNumberFragment
import com.tetragon.app.questions.questionMathFirstGrade.secondTopicAddition.easy.UiAdditionFragment
import com.tetragon.app.questions.questionMathFirstGrade.secondTopicAddition.easy.UiSimpleAdditionInteractiveFragment
import com.tetragon.app.questions.questionMathFirstGrade.secondTopicAddition.easy.UiSimpleAdditionPairsFragment
import com.tetragon.app.questions.questionMathFirstGrade.secondTopicAddition.easy.UiVisualAdditionProblemFragment
import com.tetragon.app.questions.questionMathFirstGrade.secondTopicAddition.hard.UiBalancedAdditionFragment
import com.tetragon.app.questions.questionMathFirstGrade.secondTopicAddition.hard.UiParityOperationFragment
import com.tetragon.app.questions.questionMathFirstGrade.secondTopicAddition.hard.UiRepeatedAdditionInteractiveFragment
import com.tetragon.app.questions.questionMathFirstGrade.secondTopicAddition.hard.UiRepresentSumOfThreeNumbersFragment
import com.tetragon.app.questions.questionMathFirstGrade.secondTopicAddition.hard.UiRepresentSumOfTwoNumbersFragment
import com.tetragon.app.questions.questionMathFirstGrade.secondTopicAddition.medium.UiAdditionTreeFragment
import com.tetragon.app.questions.questionMathFirstGrade.secondTopicAddition.medium.UiFindMissedNumberInAddition
import com.tetragon.app.questions.questionMathFirstGrade.secondTopicAddition.medium.UiOddEvenAdditionFragment
import com.tetragon.app.questions.questionMathFirstGrade.secondTopicAddition.medium.UiParityRuleFragment
import com.tetragon.app.questions.questionMathFirstGrade.secondTopicAddition.medium.UiTrippleAdditionInteractiveFragment
import com.tetragon.app.questions.questionMathFirstGrade.thirdTopicSubtraction.easy.UiSimpleSubtractionPairsFragment
import com.tetragon.app.questions.questionMathFirstGrade.thirdTopicSubtraction.easy.UiSubtractionFragment
import com.tetragon.app.questions.questionMathFirstGrade.thirdTopicSubtraction.easy.UiSubtractionInteractiveFragment
import com.tetragon.app.questions.questionMathFirstGrade.thirdTopicSubtraction.easy.UiVisualSubtractionProblemFragment
import com.tetragon.app.questions.questionMathFirstGrade.thirdTopicSubtraction.hard.UiBalancedSubtractionFragment
import com.tetragon.app.questions.questionMathFirstGrade.thirdTopicSubtraction.hard.UiRepresentDifferenceOfThreeNumbersFragment
import com.tetragon.app.questions.questionMathFirstGrade.thirdTopicSubtraction.hard.UiRepresentSubtractionOfTwoNumbersFragment
import com.tetragon.app.questions.questionMathFirstGrade.thirdTopicSubtraction.hard.UiSubtractionTreeFragment
import com.tetragon.app.questions.questionMathFirstGrade.thirdTopicSubtraction.medium.UiFindMissedNumberInSubtractionFragment
import com.tetragon.app.questions.questionMathFirstGrade.thirdTopicSubtraction.medium.UiSubtractionDragFragment
import com.tetragon.app.questions.questionMathFirstGrade.thirdTopicSubtraction.medium.UiSubtractionMissingDragFragment
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class Math1GradeQuestionActivity : BaseActivity() {

    private lateinit var binding: ActivityQuestionQctivityBinding
    private lateinit var selectedTopic: MathGrade1Topic
    private var lastQuestionType: MathGrade1Type? = null

    var totalXp: Int = 0
    var isResultCurrentlyVisible: Boolean = false
    var isCorrectAnswerShowing: Boolean = false
    private var correctAnswersCount = 0
    private var streakAnswersCount = 0
    private var currentComplexity: MathComplexity = MathComplexity.EASY
    private var previousComplexity: MathComplexity? = null
    private var consecutiveCorrectAtLevel = 0
    private var isOnSecondChance = false
    private var progressIncrementCount = 0

    // Tracking variables for custom structural Layout progress layout logic
    private var currentLogicalProgress = 0
    private val maxProgress = 12

    private var milestoneMediaPlayer: MediaPlayer? = null

    // Queue tracking system for the Round-Robin logic
    private var remainingTypesInRound: MutableList<MathGrade1Type> = mutableListOf()
    private var lastTrackedComplexity: MathComplexity? = null

    private val viewModel: ConnectivityViewModel by viewModels {
        object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return ConnectivityViewModel(AndroidConnectivityObserver(applicationContext)) as T
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Rive.init(this)
        binding = ActivityQuestionQctivityBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Force the background track container frame to natively clip its children's overshoots
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            binding.progressBarTrack.clipToOutline = true
        }

        // Initialize layout params safely inside the FrameLayout track container structure
        currentLogicalProgress = 0
        val initialParams = binding.progressBarActive.layoutParams as FrameLayout.LayoutParams
        initialParams.width = 0
        binding.progressBarActive.layoutParams = initialParams

        updateComplexityUi(currentComplexity)
        binding.exitBtn.setOnClickListener { showQuitBottomSheet() }
        onBackPressedDispatcher.addCallback(this) { showQuitBottomSheet() }

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
        streakAnswersCount++
        consecutiveCorrectAtLevel++
        isOnSecondChance = false

        if (consecutiveCorrectAtLevel >= 3) {
            val oldComplexity = currentComplexity

            currentComplexity = when (currentComplexity) {
                MathComplexity.EASY -> MathComplexity.MEDIUM
                MathComplexity.MEDIUM -> MathComplexity.HARD
                MathComplexity.HARD -> MathComplexity.HARD
            }

            if ((oldComplexity == MathComplexity.EASY && currentComplexity == MathComplexity.MEDIUM) ||
                (oldComplexity == MathComplexity.MEDIUM && currentComplexity == MathComplexity.HARD)) {

                // 1. Immediately animate the complexity container change alongside the level up
                binding.complexityContainer.animate()
                    .alpha(0f)
                    .scaleX(0.8f)
                    .scaleY(0.8f)
                    .setDuration(150)
                    .withEndAction {
                        // 2. Set the new icon, text, and text colors while invisible
                        updateComplexityUi(currentComplexity)
                        previousComplexity = currentComplexity // sync tracking variable

                        // 3. Animate it back into view with its new values
                        binding.complexityContainer.animate()
                            .alpha(1f)
                            .scaleX(1f)
                            .scaleY(1f)
                            .setDuration(250)
                            .setInterpolator(DecelerateInterpolator())
                            .start()
                    }.start()

                // 4. Fire the milestone layout/audio
                playLevelUpAnimation()
            }

            consecutiveCorrectAtLevel = 0
        }
    }

    fun handleIncorrectAnswer() {
        consecutiveCorrectAtLevel = 0
        streakAnswersCount = 0
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
        // 1. Fall back to checking the 8-streak milestone (10-streak removed)
        if (streakAnswersCount == 8) {
            correctAnswersCount = 0
            triggerMilestoneSequence(EightCorrectAnswersFragment())
            return true
        }

        // 2. Fall back to checking the 5-streak milestone
        if (streakAnswersCount == 5) {
            correctAnswersCount = 0
            triggerMilestoneSequence(FiveCorrectAnswerFragment())
            return true
        }

        return false
    }

    private fun triggerMilestoneSequence(fragment: Fragment) {
        isResultCurrentlyVisible = false
        binding.stateContainer.visibility = View.GONE
        binding.correctMrSquare.visibility = View.GONE
        binding.btnBackground.visibility = View.GONE
        previousComplexity = null

        binding.complexityContainer.animate().alpha(0f).setDuration(300).withEndAction {
            binding.complexityContainer.visibility = View.GONE
        }.start()

        supportFragmentManager.beginTransaction()
            .setCustomAnimations(R.anim.slide_in_right, R.anim.slide_out_left)
            .replace(R.id.questionFragmentContainer, fragment)
            .commit()

        lifecycleScope.launch {
            delay(2500)
            binding.btnBackground.visibility = View.VISIBLE
            showRandomQuestion()
        }
    }

    fun playSuccessAnimation() {
        binding.correctMrSquare.apply {
            val randomResource =
                if ((0..1).random() == 0) R.raw.mr_square_correct else R.raw.mr_square_correct_2
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

    private fun playLevelUpAnimation() {
        progressIncrementCount = 0

        triggerHapticFeedback()

        try {
            milestoneMediaPlayer?.stop()
            milestoneMediaPlayer?.release()

            milestoneMediaPlayer = MediaPlayer.create(this, R.raw.energy).apply {
                setOnCompletionListener {
                    it.release()
                    if (milestoneMediaPlayer == it) {
                        milestoneMediaPlayer = null
                    }
                }
                start()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        binding.levelUpAnimation.visibility = View.VISIBLE
        binding.levelUpAnimationRiveView.fireState("State Machine 1", "play")

        lifecycleScope.launch {
            delay(3000)
            hideLevelUpAnimation()
        }
    }

    fun hideLevelUpAnimation() {
        binding.levelUpAnimationRiveView.stop()
        binding.levelUpAnimation.visibility = View.INVISIBLE
    }

    private fun triggerHapticFeedback() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                val vibrator = vibratorManager.defaultVibrator
                vibrator.vibrate(VibrationEffect.createOneShot(300, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createOneShot(300, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(300)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun getTypesForTopic(topic: MathGrade1Topic): List<MathGrade1Type> {
        return when (topic) {
            MathGrade1Topic.COUNT_NUMBERS -> listOf(
                MathGrade1Type.APPLE, MathGrade1Type.APPLE_COUNT, MathGrade1Type.FIND_NEXT_NUMBER,
                MathGrade1Type.COUNT_BY, MathGrade1Type.FIND_MISSED_NUMBER, MathGrade1Type.ANGLES,
                MathGrade1Type.FIND_LARGEST_NUMBER, MathGrade1Type.FIND_SMALLEST_NUMBER,
                MathGrade1Type.NUMBER_DRAWING, MathGrade1Type.HAND_COUNT, MathGrade1Type.COUNT_BY_DRAG,
                MathGrade1Type.NUMBER_CONNECTING,
            )
            MathGrade1Topic.ADDITION -> listOf(
                MathGrade1Type.ADDITION_NUMBERS, MathGrade1Type.ADDITION_MISSED_NUMBER,
                MathGrade1Type.FIND_COUNT_ON, MathGrade1Type.ADDITION_ANIMATION,
                MathGrade1Type.ADDITION_TRIPLE_ANIMATION, MathGrade1Type.ADDITION_MATCH,
                MathGrade1Type.ADDITION_TWO_REPRESENTATION, MathGrade1Type.ADDITION_THREE_REPRESENTATION,
                MathGrade1Type.ADDITION_VISUAL_PROBLEM, MathGrade1Type.ADDITION_TREE,
                MathGrade1Type.ADDITION_NUMBERS_REPEATED, MathGrade1Type.ADDITION_BALANCED
            )
            MathGrade1Topic.SUBTRACTION -> listOf(
                MathGrade1Type.SUBTRACTION_NUMBERS, MathGrade1Type.SUBTRACTION_ANIMATION,
                MathGrade1Type.SUBTRACTION_VISUAL_PROBLEM, MathGrade1Type.FIND_COUNT_BACK,
                MathGrade1Type.SUBTRACTION_MISSED_NUMBER, MathGrade1Type.SUBTRACTION_DRAG,
                MathGrade1Type.SUBTRACTION_DRAG_THREE, MathGrade1Type.SUBTRACTION_MATCH,
                MathGrade1Type.SUBTRACTION_TWO_REPRESENTATION, MathGrade1Type.SUBTRACTION_REPRESENTATION,
                MathGrade1Type.SUBTRACTION_TREE, MathGrade1Type.SUBTRACTION_BALANCED,
            )
            MathGrade1Topic.ODD_OR_EVEN -> listOf(
                MathGrade1Type.CONTINUE_SEQUENCE_ODD_OR_EVEN, MathGrade1Type.SUBTRACTION_ODD_EVEN,
                MathGrade1Type.ADDITION_ODD_EVEN, MathGrade1Type.ADDITION_PARITY_RULE,
                MathGrade1Type.UP_TO_EVEN_OR_ODD, MathGrade1Type.EVEN_OR_ODD,
                MathGrade1Type.FIND_PARITY_NUMBER, MathGrade1Type.EVEN_NEIGHBORS,
                MathGrade1Type.FIND_EVEN_OR_ODD_SEQUENCE, MathGrade1Type.PARITY_OPERATION,
                MathGrade1Type.CONNECT_PARITY, MathGrade1Type.CHOOSE_TWO_PARITY
            )
            MathGrade1Topic.COMPARISON -> listOf(
                MathGrade1Type.COMPARISON, MathGrade1Type.COMPARISON_TRUE_OR_FALSE,
                MathGrade1Type.COMPARISON_GRAPE_OR_STRAWBERRY
            )
            MathGrade1Topic.PARENTHESES -> listOf(
                MathGrade1Type.PARENTHESES, MathGrade1Type.PARENTHESES_MISSED
            )
            MathGrade1Topic.ADDITION_UP_TO_20 -> listOf(
                MathGrade1Type.ADDITION_20, MathGrade1Type.ADDITION_MISSED_NUMBER_20,
                MathGrade1Type.ADDITION_THREE_REPRESENTATION_20, MathGrade1Type.ADDITION_TWO_REPRESENT_20,
                MathGrade1Type.ADDITION_VISUAL_PROBLEM_20
            )
            MathGrade1Topic.SUBTRACTION_UP_TO_20 -> listOf(
                MathGrade1Type.SUBTRACTION_MISSED_NUMBER_20, MathGrade1Type.SUBTRACTION_TWO_REPRESENTATION_20,
                MathGrade1Type.SUBTRACTION_NUMBERS_20
            )
            MathGrade1Topic.ROUND_NUMBERS -> listOf(
                MathGrade1Type.CONTINUE_SEQUENCE_ROUND_NUMBERS, MathGrade1Type.ARITHMETICS_ROUND_NUMBERS,
                MathGrade1Type.CLOSEST_ROUND_NUMBERS, MathGrade1Type.HOW_MANY_TENS_ROUND_NUMBERS
            )
            MathGrade1Topic.TWO_DIGIT_NUMBERS -> listOf(
                MathGrade1Type.COMPARISON_TWO_DIGITS, MathGrade1Type.ARITHMETICS_TWO_DIGIT_NUMBERS,
                MathGrade1Type.COMPARISON_TRUE_OR_FALSE_TWO_DIGIT_NUMBERS, MathGrade1Type.ADDITION_TWO_DIGIT_NUMBER_REPRESENTATION,
                MathGrade1Type.ARITHMETICS_VISUAL_TWO_DIGIT_NUMBERS_PROBLEM, MathGrade1Type.PARENTHESES_TWO_DIGIT_NUMBERS,
                MathGrade1Type.PARENTHESES_MISSED_TWO_DIGIT_NUMBERS
            )
            MathGrade1Topic.PROBLEM_SOLVING -> listOf(MathGrade1Type.AZIZ_APPLE_PROBLEM, MathGrade1Type.FISH)
            MathGrade1Topic.LENGTH_CENTIMETER -> listOf(MathGrade1Type.CM_RULER, MathGrade1Type.DM_IN_CM)
        }
    }

    fun showRandomQuestion() {
        isResultCurrentlyVisible = false
        hideSuccessAnimation()
        binding.correctMrSquare.visibility = View.INVISIBLE

        if (currentComplexity != previousComplexity) {
            updateComplexityUi(currentComplexity)
            binding.complexityContainer.apply {
                visibility = View.VISIBLE
                alpha = 0f
                animate().alpha(1f).setDuration(400).start()
            }
            previousComplexity = currentComplexity
        } else {
            binding.complexityContainer.visibility = View.VISIBLE
            binding.complexityContainer.alpha = 1f
        }

        val allTypes = getTypesForTopic(selectedTopic)

        // 1. Filter by current complexity level safely
        var targetedLevelPool = allTypes.filter { it.complexity == currentComplexity }

        // Safety Guard A: Fall back to all available types if no questions match this complexity tier
        if (targetedLevelPool.isEmpty()) {
            targetedLevelPool = allTypes
        }

        // Safety Guard B: If the structural pool setup remains completely empty, exit cleanly
        if (targetedLevelPool.isEmpty()) {
            finish()
            return
        }

        // 2. Refresh the execution queue if the difficulty shifted or all questions were exhausted
        if (currentComplexity != lastTrackedComplexity || remainingTypesInRound.isEmpty()) {
            lastTrackedComplexity = currentComplexity
            remainingTypesInRound = targetedLevelPool.toMutableList()
            remainingTypesInRound.shuffle()

            // Safety Guard C: Validate list length before shift-mutating elements to prevent index crashes
            if (remainingTypesInRound.size > 1 && remainingTypesInRound.first() == lastQuestionType) {
                val duplicateElement = remainingTypesInRound.removeAt(0)
                remainingTypesInRound.add(duplicateElement)
            }
        }

        // Safety Guard D: Double check data size availability right before structural removal
        if (remainingTypesInRound.isEmpty()) {
            remainingTypesInRound = allTypes.toMutableList()
            remainingTypesInRound.shuffle()
        }

        val nextType = remainingTypesInRound.removeAt(0)
        lastQuestionType = nextType

        val fragment = when (nextType) {
            // easy 1 topic
            MathGrade1Type.APPLE -> UiAppleQuestionFragment()
            MathGrade1Type.APPLE_COUNT -> UiAppleCountFragment()
            MathGrade1Type.NUMBER_DRAWING -> UiNumberTraceFragment()
            MathGrade1Type.HAND_COUNT -> UiCountWithHandsFragment()
            // medium 1 topic
            MathGrade1Type.FIND_MISSED_NUMBER -> UiFindMIssedNumberInSequenceFragment()
            MathGrade1Type.ANGLES -> UiNumberOfAnglesShapeFragment()
            MathGrade1Type.FIND_LARGEST_NUMBER -> UiFindLargestNumberFragment()
            MathGrade1Type.FIND_SMALLEST_NUMBER -> UiFindSmallestNumberFragment()
            // hard 1 topic
            MathGrade1Type.COUNT_BY -> UiCountByNumberFragment()
            MathGrade1Type.FIND_NEXT_NUMBER -> UiFindNextNumberFragment()
            MathGrade1Type.COUNT_BY_DRAG -> UiCountByDragFragment()
            MathGrade1Type.NUMBER_CONNECTING -> UiConnectionNumberFragment()

            // easy 2 topic
            MathGrade1Type.ADDITION_NUMBERS -> UiAdditionFragment()
            MathGrade1Type.ADDITION_VISUAL_PROBLEM -> UiVisualAdditionProblemFragment()
            MathGrade1Type.FIND_COUNT_ON -> UiCountOnFragment()
            MathGrade1Type.ADDITION_ANIMATION -> UiSimpleAdditionInteractiveFragment()
            // medium 2 topic
            MathGrade1Type.ADDITION_MISSED_NUMBER -> UiFindMissedNumberInAddition()
            MathGrade1Type.ADDITION_TREE -> UiAdditionTreeFragment()
            MathGrade1Type.ADDITION_MATCH -> UiSimpleAdditionPairsFragment()
            MathGrade1Type.ADDITION_TRIPLE_ANIMATION -> UiTrippleAdditionInteractiveFragment()
            // hard 2 topic
            MathGrade1Type.ADDITION_TWO_REPRESENTATION -> UiRepresentSumOfTwoNumbersFragment()
            MathGrade1Type.ADDITION_THREE_REPRESENTATION -> UiRepresentSumOfThreeNumbersFragment()
            MathGrade1Type.ADDITION_NUMBERS_REPEATED -> UiRepeatedAdditionInteractiveFragment()
            MathGrade1Type.ADDITION_BALANCED -> UiBalancedAdditionFragment()

            // easy 3 topic
            MathGrade1Type.SUBTRACTION_NUMBERS -> UiSubtractionFragment()
            MathGrade1Type.SUBTRACTION_ANIMATION -> UiSubtractionInteractiveFragment()
            MathGrade1Type.SUBTRACTION_VISUAL_PROBLEM -> UiVisualSubtractionProblemFragment()
            MathGrade1Type.FIND_COUNT_BACK -> UiCountBackFragment()
            // medium 3 topic
            MathGrade1Type.SUBTRACTION_MISSED_NUMBER -> UiFindMissedNumberInSubtractionFragment()
            MathGrade1Type.SUBTRACTION_DRAG -> UiSubtractionDragFragment()
            MathGrade1Type.SUBTRACTION_DRAG_THREE -> UiSubtractionMissingDragFragment()
            MathGrade1Type.SUBTRACTION_MATCH -> UiSimpleSubtractionPairsFragment()
            // hard 3 topic
            MathGrade1Type.SUBTRACTION_TWO_REPRESENTATION -> UiRepresentSubtractionOfTwoNumbersFragment()
            MathGrade1Type.SUBTRACTION_REPRESENTATION -> UiRepresentDifferenceOfThreeNumbersFragment()
            MathGrade1Type.SUBTRACTION_TREE -> UiSubtractionTreeFragment()
            MathGrade1Type.SUBTRACTION_BALANCED -> UiBalancedSubtractionFragment()

            // easy 4 topic
            MathGrade1Type.CONTINUE_SEQUENCE_ODD_OR_EVEN -> UiContinueSequenceOddOrEvenFragment()
            MathGrade1Type.SUBTRACTION_ODD_EVEN -> UiOddEvenSubtractionFragment()
            MathGrade1Type.ADDITION_ODD_EVEN -> UiOddEvenAdditionFragment()
            MathGrade1Type.ADDITION_PARITY_RULE -> UiParityRuleFragment()
            // medium 4 topic
            MathGrade1Type.EVEN_OR_ODD -> UiOddOrEvenNumberFragment()
            MathGrade1Type.FIND_EVEN_OR_ODD_SEQUENCE -> UiFindOddOrEvenNumbersInSequenceFragment()
            MathGrade1Type.FIND_PARITY_NUMBER -> UiFindParityNumberFragment()
            MathGrade1Type.EVEN_NEIGHBORS -> UiEvenNeighborsFragment()
            // hard 4 topic
            MathGrade1Type.UP_TO_EVEN_OR_ODD -> UiWhatIsEvenOrOddNumberUpToFragment()
            MathGrade1Type.PARITY_OPERATION -> UiParityOperationFragment()
            MathGrade1Type.CONNECT_PARITY -> UiConnectParityNumbersFragment()
            MathGrade1Type.CHOOSE_TWO_PARITY -> UiChooseTwoParityFragment()

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
            .setCustomAnimations(R.anim.slide_in_right, R.anim.slide_out_left, R.anim.slide_in_left, R.anim.slide_out_right)
            .replace(R.id.questionFragmentContainer, fragment)
            .commit()
    }

    fun incrementProgress(): Boolean {
        val oldProgress = currentLogicalProgress
        currentLogicalProgress = (currentLogicalProgress + 1).coerceAtMost(maxProgress)

        // Dynamically extract the track view's physical width bounds
        val totalTrackWidth = binding.progressBarTrack.width.toFloat()

        // Calculate explicit target dimensions in pixel limits
        val startWidth = (oldProgress.toFloat() / maxProgress.toFloat()) * totalTrackWidth
        val targetWidth = (currentLogicalProgress.toFloat() / maxProgress.toFloat()) * totalTrackWidth

        // ValueAnimator transitioning absolute pixel bounds cleanly inside FrameLayout parent
        val progressAnimator = ValueAnimator.ofFloat(startWidth, targetWidth).apply {
            duration = 500

            // Re-enabled high-tension playful bounce safely since parent FrameLayout hard-clips layout limits
            interpolator = AnticipateOvershootInterpolator(1.2f)

            addUpdateListener { animator ->
                val animatedWidth = animator.animatedValue as Float
                // Safety clamp layout to block edge leaks beyond the container layout boundaries
                val clampedWidth = animatedWidth.coerceIn(0f, totalTrackWidth).toInt()

                // Explicitly casts layout params to FrameLayout to avoid ClassCastExceptions
                val currentParams = binding.progressBarActive.layoutParams as FrameLayout.LayoutParams
                currentParams.width = clampedWidth
                binding.progressBarActive.layoutParams = currentParams
            }
        }

        progressAnimator.start()
        progressIncrementCount++
        return currentLogicalProgress >= maxProgress
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
                    val isVisible = isConnected
                    binding.internetConnection.visibility = if (isVisible) View.GONE else View.VISIBLE
                    binding.offlineContainer.visibility = if (isVisible) View.GONE else View.VISIBLE
                    binding.topBarContainer.visibility = if (isVisible) View.VISIBLE else View.GONE
                    binding.questionFragmentContainer.visibility = if (isVisible) View.VISIBLE else View.GONE
                    binding.btnBackground.visibility = if (isVisible) View.VISIBLE else View.GONE
                    binding.complexityContainer.visibility = if (isVisible) View.VISIBLE else View.GONE

                    if (isVisible && isResultCurrentlyVisible) {
                        binding.stateContainer.visibility = View.VISIBLE
                        binding.correctMrSquare.visibility = if (isCorrectAnswerShowing) View.VISIBLE else View.INVISIBLE
                    } else {
                        binding.stateContainer.visibility = View.INVISIBLE
                        binding.correctMrSquare.visibility = View.INVISIBLE
                    }
                }
            }
        }
    }

    private fun updateComplexityUi(complexity: MathComplexity) {
        // 1. Map UI text and icon assets (keeping green for EASY text/icon)
        val (iconRes, textRes, textColorRes) = when (complexity) {
            MathComplexity.EASY -> Triple(R.drawable.easy, R.string.complexity_easy, R.color.green_1)
            MathComplexity.MEDIUM -> Triple(R.drawable.medium, R.string.complexity_medium, R.color.orange_1)
            MathComplexity.HARD -> Triple(R.drawable.hard, R.string.complexity_hard, R.color.red_1)
        }
        binding.complexityIcon.setImageResource(iconRes)
        binding.complexityText.setText(textRes)
        binding.complexityText.setTextColor(ContextCompat.getColor(this, textColorRes))

        // 2. Map progress bar tint independently (forcing blue_2 for EASY bar)
        val progressBarColorRes = when (complexity) {
            MathComplexity.EASY -> R.color.blue_2
            MathComplexity.MEDIUM -> R.color.orange_1
            MathComplexity.HARD -> R.color.red_1
        }
        binding.progressBarActive.backgroundTintList = ContextCompat.getColorStateList(this, progressBarColorRes)
    }

    override fun onDestroy() {
        super.onDestroy()
        milestoneMediaPlayer?.release()
        milestoneMediaPlayer = null
    }
}