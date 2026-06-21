package com.tetragon.app.questions.questionMathFifthGrade.firstTopic.hard

import android.media.MediaPlayer
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.tetragon.app.R
import com.tetragon.app.questions.questionMathFifthGrade.Math5GradeQuestionActivity
import com.tetragon.app.questions.questionMathFifthGrade.MathGrade5Type
import kotlin.random.Random

class UiSumOfNaturalNumbersDivisibleFragment : Fragment(R.layout.fragment_ui_sum_of_natural_numbers_divisable) {

    private lateinit var instructionText: TextView
    private lateinit var options: List<LinearLayout>

    private lateinit var checkBtn: Button
    private lateinit var checkBtnBack: View
    private lateinit var btnBack: View
    private lateinit var seeBtn: FrameLayout
    private lateinit var seeEnabledButton: Button
    private lateinit var stateAnswer: TextView
    private lateinit var answer: TextView
    private lateinit var stateContainer: FrameLayout
    private lateinit var circleState: ImageView

    private var maxRangeLimit = 0
    private var correctAnswer = 0
    private var selectedOptionIndex: Int? = null

    private var isAnswerChecked = false
    private var isIncorrectAttempt = false
    private var isFirstAttempt = true
    private var mediaPlayer: MediaPlayer? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initViews(view)
        setupInitialButtonState()
        generateProblem()
        setupOptionClicks()

        checkBtn.setOnClickListener {
            val activity = requireActivity() as Math5GradeQuestionActivity
            if (!isAnswerChecked) {
                selectedOptionIndex?.let { index -> checkAnswer(index) }
            } else {
                if (isIncorrectAttempt) {
                    resetForTryAgain()
                } else {
                    if (checkBtn.text == getString(R.string.btn_finish)) {
                        activity.navigateToXpGained()
                    } else {
                        val isMilestoneActive = activity.checkAndTriggerMilestone()
                        if (!isMilestoneActive) {
                            resetUIForNext()
                            activity.showRandomQuestion()
                        }
                    }
                }
            }
        }
    }

    private fun initViews(view: View) {
        instructionText = view.findViewById(R.id.instructionText)
        options = listOf(
            view.findViewById(R.id.option1),
            view.findViewById(R.id.option2),
            view.findViewById(R.id.option3)
        )

        val activity = requireActivity() as Math5GradeQuestionActivity
        checkBtn = activity.findViewById(R.id.check_enabled_btn)
        checkBtnBack = activity.findViewById(R.id.check_enabled_button_background)
        btnBack = activity.findViewById(R.id.btnBackground)
        seeBtn = activity.findViewById(R.id.see_btn_container)
        seeEnabledButton = activity.findViewById(R.id.see_enabled_btn)
        stateAnswer = activity.findViewById(R.id.stateAnswer)
        answer = activity.findViewById(R.id.answer)
        stateContainer = activity.findViewById(R.id.stateContainer)
        circleState = activity.findViewById(R.id.circleState)
    }

    private fun generateProblem() {
        // Vary the problem limits to give students unique challenges
        val rangeSamples = listOf(50, 60, 75, 90, 100, 120)
        maxRangeLimit = rangeSamples[Random.nextInt(rangeSamples.size)]

        // Calculate sequence sum mathematically: 3 + 6 + 9 + ... + maxDivisibleBy3
        val totalTerms = maxRangeLimit / 3
        val lastTerm = totalTerms * 3
        correctAnswer = (totalTerms * (3 + lastTerm)) / 2

        val template = getString(R.string.question_sum_divisible_by_three)
        val fullText = String.format(template, maxRangeLimit)

        val spannable = SpannableString(fullText)
        val colorBlue = ContextCompat.getColor(requireContext(), R.color.blue_2)

        // Find and color all numbers (sequences of digits) inside the final text
        val numberRegex = Regex("\\d+")
        numberRegex.findAll(fullText).forEach { matchResult ->
            spannable.setSpan(
                ForegroundColorSpan(colorBlue),
                matchResult.range.first,
                matchResult.range.last + 1,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
        instructionText.text = spannable

        // Generate robust mathematical distractors
        val distractors = mutableSetOf<Int>()

        // Distractor 1: Forgetting to skip non-divisible numbers (Normal sum up to maxTerms)
        val standardSum = (totalTerms * (totalTerms + 1)) / 2
        if (standardSum != correctAnswer && standardSum > 0) distractors.add(standardSum)

        // Distractor 2: Off-by-one term counting error
        val offTermSum = ((totalTerms - 1) * (3 + (lastTerm - 3))) / 2
        if (offTermSum != correctAnswer && offTermSum > 0) distractors.add(offTermSum)

        // Ensure we always have distinct alternatives filled up
        while (distractors.size < 2) {
            val dynamicOffset = Random.nextInt(-3, 4) * 3
            val candidate = correctAnswer + dynamicOffset + Random.nextInt(1, 10)
            if (candidate != correctAnswer && candidate > 0) distractors.add(candidate)
        }

        val optionsList = (distractors.toList().take(2) + correctAnswer).shuffled()

        options.forEachIndexed { index, layout ->
            val tv = layout.getChildAt(0) as TextView
            tv.text = optionsList[index].toString()
            layout.setBackgroundResource(R.drawable.custom_background)
        }

        resetFragmentState()
    }

    private fun setupOptionClicks() {
        options.forEachIndexed { index, layout ->
            layout.setOnClickListener {
                if (isAnswerChecked) return@setOnClickListener
                selectedOptionIndex = index
                highlightSelectedOption(index)
                enableCheckButton()
            }
        }
    }

    private fun highlightSelectedOption(selectedIndex: Int) {
        options.forEachIndexed { i, layout ->
            layout.setBackgroundResource(
                if (i == selectedIndex) R.drawable.option_selected
                else R.drawable.custom_background
            )
        }
    }

    private fun checkAnswer(index: Int) {
        isAnswerChecked = true
        val activity = requireActivity() as Math5GradeQuestionActivity
        val chosenText = (options[index].getChildAt(0) as TextView).text.toString()

        activity.isResultCurrentlyVisible = true
        stateContainer.visibility = View.VISIBLE

        if (chosenText == correctAnswer.toString()) {
            playSound(R.raw.correct)
            activity.isCorrectAnswerShowing = true
            activity.playSuccessAnimation()

            val isFinished = activity.incrementProgress()
            if (isFirstAttempt) {
                // Adjust to match your existing enum declaration mappings for hard tasks
                activity.totalXp += MathGrade5Type.SUM_OF_FIRST_NATURAL_NUMBERS_DIVISIBLE.xp
            }
            activity.handleCorrectAnswer()

            isIncorrectAttempt = false
            checkBtn.text = if (isFinished) getString(R.string.btn_finish) else getString(R.string.btn_continue)
            showCorrectState(index)
        } else {
            playSound(R.raw.wrong)
            isIncorrectAttempt = true
            activity.handleIncorrectAnswer()
            checkBtn.text = getString(R.string.btn_try_again)
            showIncorrectState(index)
            setupSeeSolution()
        }
        isFirstAttempt = false
    }

    private fun showCorrectState(index: Int) {
        stateContainer.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.green_3))
        circleState.setImageResource(R.drawable.correct_tick_icon)
        options[index].setBackgroundResource(R.drawable.option_correct)
        stateAnswer.text = getString(R.string.state_correct)
        statusAnswerLabel(correctAnswer.toString())
        answer.visibility = View.VISIBLE
        applyButtonColors(R.color.green_1, R.color.green_2, R.color.green_4)
        enableCheckButton()
    }

    private fun showIncorrectState(index: Int) {
        stateContainer.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.red_4))
        circleState.setImageResource(R.drawable.wrong_circle)
        options[index].setBackgroundResource(R.drawable.option_incorrect)
        stateAnswer.text = getString(R.string.state_incorrect)
        seeEnabledButton.text = getString(R.string.btn_see_solution)
        answer.visibility = View.GONE
        seeBtn.visibility = View.VISIBLE
        applyButtonColors(R.color.red_1, R.color.red_2, R.color.red_4)
        enableCheckButton()
    }

    private fun setupSeeSolution() {
        seeEnabledButton.setOnClickListener {
            seeBtn.visibility = View.GONE
            stateAnswer.text = getString(R.string.state_solution)

            val totalTerms = maxRangeLimit / 3
            val firstTerm = 3
            val lastTerm = totalTerms * 3

            // General formula for arithmetic progression: S = n * (a1 + an) / 2
            val formula = "n × (a₁ + aₙ) ÷ 2"
            val substitution = "($totalTerms × ($firstTerm + $lastTerm)) ÷ 2"
            val solutionExplanation = "Formula: $formula\nSum = $substitution = $correctAnswer"

            statusAnswerLabel(solutionExplanation)
            answer.visibility = View.VISIBLE

            checkBtn.text = getString(R.string.btn_continue)
            circleState.setImageResource(R.drawable.solution_lamp_icon)

            isIncorrectAttempt = false
            applyButtonColors(R.color.black_3, R.color.black_2, R.color.gray_2)
            stateContainer.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.gray_2))

            options.forEach { layout ->
                val tv = layout.getChildAt(0) as TextView
                if (tv.text.toString() == correctAnswer.toString()) {
                    layout.setBackgroundResource(R.drawable.option_showed)
                } else {
                    layout.setBackgroundResource(R.drawable.custom_background)
                }
            }
        }
    }

    private fun resetFragmentState() {
        val activity = requireActivity() as Math5GradeQuestionActivity
        activity.isCorrectAnswerShowing = false

        selectedOptionIndex = null
        isAnswerChecked = false
        isIncorrectAttempt = false
        isFirstAttempt = true

        stateContainer.visibility = View.INVISIBLE
        seeBtn.visibility = View.GONE
        stateAnswer.text = ""
        answer.text = ""
        answer.visibility = View.VISIBLE

        checkBtn.text = getString(R.string.btn_check)
        setupInitialButtonState()
        disableCheckButton()
    }

    private fun resetForTryAgain() {
        val activity = requireActivity() as Math5GradeQuestionActivity
        activity.isResultCurrentlyVisible = false

        isAnswerChecked = false
        isIncorrectAttempt = false
        selectedOptionIndex = null
        options.forEach { it.setBackgroundResource(R.drawable.custom_background) }

        checkBtn.text = getString(R.string.btn_check)
        setupInitialButtonState()
        disableCheckButton()

        stateContainer.visibility = View.INVISIBLE
        seeBtn.visibility = View.GONE
        stateAnswer.text = ""
        answer.visibility = View.VISIBLE
    }

    private fun resetUIForNext() {
        val activity = requireActivity() as Math5GradeQuestionActivity
        activity.isResultCurrentlyVisible = false
        activity.hideSuccessAnimation()

        stateContainer.visibility = View.INVISIBLE
        stateAnswer.text = ""
        answer.text = ""
        answer.visibility = View.VISIBLE
        seeBtn.visibility = View.GONE

        checkBtn.text = getString(R.string.btn_check)
        setupInitialButtonState()
        disableCheckButton()
        options.forEach { it.setBackgroundResource(R.drawable.custom_background) }
    }

    private fun statusAnswerLabel(textResult: String) {
        answer.text = getString(R.string.label_answer, textResult)
    }

    private fun enableCheckButton() {
        checkBtn.isEnabled = true
        val activity = requireActivity() as Math5GradeQuestionActivity
        activity.findViewById<FrameLayout>(R.id.check_enabled_btn_container).visibility = View.VISIBLE
        activity.findViewById<FrameLayout>(R.id.check_disabled_btn_container).visibility = View.INVISIBLE
        checkBtnBack.visibility = View.VISIBLE
    }

    private fun disableCheckButton() {
        checkBtn.isEnabled = false
        val activity = requireActivity() as Math5GradeQuestionActivity
        activity.findViewById<FrameLayout>(R.id.check_disabled_btn_container).visibility = View.VISIBLE
        activity.findViewById<FrameLayout>(R.id.check_enabled_btn_container).visibility = View.INVISIBLE
        checkBtnBack.visibility = View.VISIBLE
    }

    private fun applyButtonColors(buttonColor: Int, backColor: Int, backgroundColor: Int) {
        checkBtn.backgroundTintList = ContextCompat.getColorStateList(requireContext(), buttonColor)
        checkBtnBack.backgroundTintList = ContextCompat.getColorStateList(requireContext(), backColor)
        btnBack.setBackgroundColor(ContextCompat.getColor(requireContext(), backgroundColor))
    }

    private fun setupInitialButtonState() {
        btnBack.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.white))
        checkBtn.backgroundTintList = ContextCompat.getColorStateList(requireContext(), R.color.blue_2)
        checkBtnBack.backgroundTintList = ContextCompat.getColorStateList(requireContext(), R.color.blue_1)
    }

    private fun playSound(soundResId: Int) {
        try {
            mediaPlayer?.release()
            mediaPlayer = MediaPlayer.create(requireContext(), soundResId)
            mediaPlayer?.setOnCompletionListener { it.release() }
            mediaPlayer?.start()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        mediaPlayer?.release()
        mediaPlayer = null
    }
}