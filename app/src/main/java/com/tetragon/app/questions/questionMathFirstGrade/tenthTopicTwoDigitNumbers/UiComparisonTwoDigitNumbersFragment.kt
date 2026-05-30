package com.tetragon.app.questions.questionMathFirstGrade.tenthTopicTwoDigitNumbers

import android.media.MediaPlayer
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.view.View
import android.widget.*
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.tetragon.app.R
import com.tetragon.app.questions.questionMathFirstGrade.Math1GradeQuestionActivity
import com.tetragon.app.questions.questionMathFirstGrade.MathGrade1Type
import kotlin.random.Random

class UiComparisonTwoDigitNumbersFragment : Fragment(R.layout.fragment_ui_comparison_two_digit_numbers) {

    private lateinit var firstNumber: TextView
    private lateinit var secondNumber: TextView
    private lateinit var instructionText: TextView
    private lateinit var options: List<LinearLayout>
    private lateinit var checkBtn: Button
    private lateinit var checkBtnBack: View
    private lateinit var btnBack: ConstraintLayout
    private lateinit var seeBtn: FrameLayout
    private lateinit var seeEnabledButton: Button
    private lateinit var stateAnswer: TextView
    private lateinit var answer: TextView
    private lateinit var problemImage: ImageView
    private lateinit var problemAnswerText: TextView

    private var correctAnswer = ""
    private var selectedOptionIndex: Int? = null
    private var isAnswerChecked = false
    private var isIncorrectAttempt = false
    private var isFirstAttempt = true
    private var mediaPlayer: MediaPlayer? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initViews(view)
        setupInstructionText()
        setupInitialButtonState()
        generateProblem()
        setupOptionClicks()
        setupCheckButton()
    }

    private fun initViews(view: View) {
        firstNumber = view.findViewById(R.id.firstNumber)
        secondNumber = view.findViewById(R.id.secondNumber)
        instructionText = view.findViewById(R.id.instructionText)
        problemImage = view.findViewById(R.id.problemImage)
        problemAnswerText = view.findViewById(R.id.problemAnswerText)

        options = listOf(
            view.findViewById(R.id.option1),
            view.findViewById(R.id.option2),
            view.findViewById(R.id.option3)
        )

        val activity = requireActivity()
        checkBtn = activity.findViewById(R.id.check_enabled_btn)
        checkBtnBack = activity.findViewById(R.id.check_enabled_button_background)
        btnBack = activity.findViewById(R.id.btnBackground)
        seeBtn = activity.findViewById(R.id.see_btn_container)
        seeEnabledButton = activity.findViewById(R.id.see_enabled_btn)
        stateAnswer = activity.findViewById(R.id.stateAnswer)
        answer = activity.findViewById(R.id.answer)
    }

    private fun setupInstructionText() {
        val fullText = getString(R.string.instruction_comparison)
        val spannable = SpannableString(fullText)
        val wordToStyle = getString(R.string.instruction_comparison_keyword)

        val start = fullText.indexOf(wordToStyle)
        if (start != -1) {
            spannable.setSpan(
                ForegroundColorSpan(ContextCompat.getColor(requireContext(), R.color.blue_2)),
                start,
                start + wordToStyle.length,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
        instructionText.text = spannable
    }

    private fun generateProblem() {
        val problemType = Random.nextInt(0, 3)
        val leftValue: Int
        val rightValue: Int

        when (problemType) {
            0 -> { // ADDITION
                leftValue = Random.nextInt(2, 101)
                val a = Random.nextInt(1, leftValue)
                val b = leftValue - a
                firstNumber.text = getString(R.string.arithmetic_plus_template, a, b)

                rightValue = Random.nextInt(2, 101)
                val c = Random.nextInt(1, rightValue)
                val d = rightValue - c
                secondNumber.text = getString(R.string.arithmetic_plus_template, c, d)
            }
            1 -> { // SUBTRACTION
                val a = Random.nextInt(1, 101)
                val b = Random.nextInt(0, a + 1)
                leftValue = a - b
                firstNumber.text = getString(R.string.arithmetic_minus_template, a, b)

                val c = Random.nextInt(1, 101)
                val d = Random.nextInt(0, c + 1)
                rightValue = c - d
                secondNumber.text = getString(R.string.arithmetic_minus_template, c, d)
            }
            else -> { // SIMPLE NUMBERS
                leftValue = Random.nextInt(1, 101)
                rightValue = Random.nextInt(1, 101)
                firstNumber.text = leftValue.toString()
                secondNumber.text = rightValue.toString()
            }
        }

        correctAnswer = when {
            leftValue > rightValue -> ">"
            leftValue < rightValue -> "<"
            else -> "="
        }

        val shuffledOptions = listOf(">", "<", "=").shuffled()
        options.forEachIndexed { index, layout ->
            val tv = layout.getChildAt(0) as TextView
            tv.text = shuffledOptions[index]
            layout.setBackgroundResource(R.drawable.custom_background)
        }

        problemAnswerText.text = ""
        problemAnswerText.visibility = View.GONE
        problemImage.setImageResource(R.drawable.answer_blue_box)

        selectedOptionIndex = null
        isAnswerChecked = false
        isIncorrectAttempt = false
        seeBtn.visibility = View.GONE
        isFirstAttempt = true

        checkBtn.text = getString(R.string.btn_check)
        setupInitialButtonState()
    }

    private fun setupOptionClicks() {
        options.forEachIndexed { index, layout ->
            layout.setOnClickListener {
                if (isAnswerChecked) return@setOnClickListener
                selectedOptionIndex = index
                highlightSelectedOption(index)
                enableCheckButton()

                val chosenSymbol = (layout.getChildAt(0) as TextView).text.toString()
                problemAnswerText.text = chosenSymbol
                problemAnswerText.visibility = View.VISIBLE
            }
        }
    }

    private fun checkAnswer(index: Int, stateContainer: FrameLayout, circleState: ImageView) {
        isAnswerChecked = true
        val chosen = (options[index].getChildAt(0) as TextView).text.toString()
        val activity = requireActivity() as Math1GradeQuestionActivity

        activity.isResultCurrentlyVisible = true
        stateContainer.visibility = View.VISIBLE

        if (chosen == correctAnswer) {
            playSound(R.raw.correct)
            problemImage.setImageResource(R.drawable.answer_correct_box)
            activity.isCorrectAnswerShowing = true
            activity.playSuccessAnimation()

            val isFinished = activity.incrementProgress()
            if (isFirstAttempt) activity.totalXp += MathGrade1Type.COMPARISON_TWO_DIGITS.xp
            activity.handleCorrectAnswer()

            isIncorrectAttempt = false
            checkBtn.text = if (isFinished) getString(R.string.btn_finish) else getString(R.string.btn_continue)
            showCorrectState(stateContainer, circleState, index)
        } else {
            playSound(R.raw.wrong)
            problemImage.setImageResource(R.drawable.answer_incorrect_box)
            isIncorrectAttempt = true
            activity.handleIncorrectAnswer()
            checkBtn.text = getString(R.string.btn_try_again)
            showIncorrectState(stateContainer, circleState, index)
            setupSeeSolution(stateContainer, circleState)
        }
        isFirstAttempt = false
    }

    private fun setupSeeSolution(stateContainer: FrameLayout, circleState: ImageView) {
        seeEnabledButton.setOnClickListener {
            seeBtn.visibility = View.GONE
            stateAnswer.text = getString(R.string.state_solution)
            answer.text = getString(R.string.label_answer, correctAnswer)
            answer.visibility = View.VISIBLE
            checkBtn.text = getString(R.string.btn_continue)

            problemAnswerText.text = correctAnswer
            problemAnswerText.visibility = View.VISIBLE
            problemImage.setImageResource(R.drawable.answer_solution_box)
            circleState.setImageResource(R.drawable.solution_lamp_icon)

            isAnswerChecked = true
            isIncorrectAttempt = false

            applyButtonColors(R.color.black_3, R.color.black_2, R.color.gray_2)
            stateContainer.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.gray_2))

            options.forEach { layout ->
                val tv = layout.getChildAt(0) as TextView
                layout.setBackgroundResource(
                    if (tv.text.toString() == correctAnswer) R.drawable.option_showed
                    else R.drawable.custom_background
                )
            }
        }
    }

    private fun resetForTryAgain() {
        val activity = requireActivity() as Math1GradeQuestionActivity
        activity.isResultCurrentlyVisible = false

        isAnswerChecked = false
        isIncorrectAttempt = false
        selectedOptionIndex = null

        options.forEach { it.setBackgroundResource(R.drawable.custom_background) }
        problemImage.setImageResource(R.drawable.answer_blue_box)
        problemAnswerText.visibility = View.GONE

        checkBtn.text = getString(R.string.btn_check)
        setupInitialButtonState()
        requireActivity().findViewById<FrameLayout>(R.id.stateContainer).visibility = View.INVISIBLE
        seeBtn.visibility = View.GONE
        stateAnswer.text = ""
        answer.text = ""
    }

    private fun resetUIForNext() {
        val activity = requireActivity() as Math1GradeQuestionActivity
        activity.isResultCurrentlyVisible = false
        activity.hideSuccessAnimation()

        requireActivity().findViewById<FrameLayout>(R.id.stateContainer).visibility = View.INVISIBLE
        stateAnswer.text = ""
        answer.text = ""
        seeBtn.visibility = View.GONE
        checkBtn.text = getString(R.string.btn_check)

        setupInitialButtonState()
        options.forEach { it.setBackgroundResource(R.drawable.custom_background) }
    }

    private fun setupCheckButton() {
        checkBtn.setOnClickListener {
            val stateContainer = requireActivity().findViewById<FrameLayout>(R.id.stateContainer)
            val circleState = requireActivity().findViewById<ImageView>(R.id.circleState)

            if (!isAnswerChecked) {
                selectedOptionIndex?.let { checkAnswer(it, stateContainer, circleState) }
            } else if (isIncorrectAttempt) {
                resetForTryAgain()
            } else {
                val activity = requireActivity() as Math1GradeQuestionActivity
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

    private fun playSound(soundResId: Int) {
        mediaPlayer?.release()
        mediaPlayer = MediaPlayer.create(requireContext(), soundResId)
        mediaPlayer?.setOnCompletionListener { it.release() }
        mediaPlayer?.start()
    }

    private fun setupInitialButtonState() {
        btnBack.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.white))
        checkBtn.backgroundTintList = ContextCompat.getColorStateList(requireContext(), R.color.blue_2)
        checkBtnBack.backgroundTintList = ContextCompat.getColorStateList(requireContext(), R.color.blue_1)
        checkBtn.isEnabled = false
        requireActivity().findViewById<FrameLayout>(R.id.check_enabled_btn_container).visibility = View.INVISIBLE
        requireActivity().findViewById<FrameLayout>(R.id.check_disabled_btn_container).visibility = View.VISIBLE
    }

    private fun highlightSelectedOption(selectedIndex: Int) {
        options.forEachIndexed { i, layout ->
            layout.setBackgroundResource(if (i == selectedIndex) R.drawable.option_selected else R.drawable.custom_background)
        }
    }

    private fun enableCheckButton() {
        checkBtn.isEnabled = true
        requireActivity().findViewById<FrameLayout>(R.id.check_enabled_btn_container).visibility = View.VISIBLE
        requireActivity().findViewById<FrameLayout>(R.id.check_disabled_btn_container).visibility = View.INVISIBLE
    }

    private fun showCorrectState(stateContainer: FrameLayout, circleState: ImageView, index: Int) {
        stateContainer.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.green_3))
        circleState.setImageResource(R.drawable.correct_tick_icon)
        options[index].setBackgroundResource(R.drawable.option_correct)
        stateAnswer.text = getString(R.string.state_correct)
        answer.text = getString(R.string.label_answer, correctAnswer)
        answer.visibility = View.VISIBLE
        applyButtonColors(R.color.green_1, R.color.green_2, R.color.green_4)
    }

    private fun showIncorrectState(stateContainer: FrameLayout, circleState: ImageView, index: Int) {
        stateContainer.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.red_4))
        circleState.setImageResource(R.drawable.wrong_circle)
        options[index].setBackgroundResource(R.drawable.option_incorrect)
        stateAnswer.text = getString(R.string.state_incorrect)
        answer.visibility = View.GONE
        seeBtn.visibility = View.VISIBLE
        seeEnabledButton.text = getString(R.string.btn_see_solution)
        applyButtonColors(R.color.red_1, R.color.red_2, R.color.red_4)
    }

    private fun applyButtonColors(buttonColor: Int, backColor: Int, backgroundColor: Int) {
        checkBtn.backgroundTintList = ContextCompat.getColorStateList(requireContext(), buttonColor)
        checkBtnBack.backgroundTintList = ContextCompat.getColorStateList(requireContext(), backColor)
        btnBack.setBackgroundColor(ContextCompat.getColor(requireContext(), backgroundColor))
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaPlayer?.release()
        mediaPlayer = null
    }
}