package com.example.tetragon.questions.questionMathEleventhGrade.firstTopic

import android.media.MediaPlayer
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.view.View
import android.widget.*
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.example.tetragon.R
import com.example.tetragon.questions.questionMathEleventhGrade.Math11GradeQuestionActivity
import com.example.tetragon.questions.questionMathEleventhGrade.MathGrade11Type
import java.util.regex.Pattern

class UiDerivativesProblemFragment : Fragment(R.layout.fragment_ui_derivatives_problem) {

    data class DerivativeQuestion(
        val expression: String,
        val correctAnswer: String,
        val options: List<String>,
        val correctIndex: Int
    )

    private lateinit var derivativeTarget: TextView
    private lateinit var problemImage: ImageView
    private lateinit var options: List<View>
    private lateinit var problemAnswerContainer: View

    // Activity Shared Views
    private lateinit var checkBtn: Button
    private lateinit var checkBtnBack: View
    private lateinit var btnBack: View
    private lateinit var seeBtn: FrameLayout
    private lateinit var seeEnabledButton: Button
    private lateinit var stateAnswer: TextView
    private lateinit var answerText: TextView

    private var currentQuestion: DerivativeQuestion? = null
    private var correctAnswerStr = ""
    private var currentCorrectIndex = 0
    private var selectedOptionIndex: Int? = null
    private var isAnswerChecked = false
    private var isIncorrectAttempt = false
    private var isFirstAttempt = true

    private var mediaPlayer: MediaPlayer? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 1. Initialize Local fragment views
        derivativeTarget = view.findViewById(R.id.derivativeTarget)
        problemImage = view.findViewById(R.id.problemImage)
        problemAnswerContainer = view.findViewById(R.id.problemAnswerText)

        options = listOf(
            view.findViewById(R.id.option1),
            view.findViewById(R.id.option2),
            view.findViewById(R.id.option3)
        )

        // 2. Initialize Activity views
        val activity = requireActivity()
        checkBtn = activity.findViewById(R.id.check_enabled_btn)
        checkBtnBack = activity.findViewById(R.id.check_enabled_button_background)
        btnBack = activity.findViewById(R.id.btnBackground)
        seeBtn = activity.findViewById(R.id.see_btn_container)
        seeEnabledButton = activity.findViewById(R.id.see_enabled_btn)
        stateAnswer = activity.findViewById(R.id.stateAnswer)
        answerText = activity.findViewById(R.id.answer)

        setupInitialButtonState()
        generateDerivativeProblem()
        setupOptionClicks()
        setupCheckButton()
    }

    private fun generateDerivativeProblem() {
        val pool = listOf(
            DerivativeQuestion("(x²) =", "2x", listOf("x", "2x", "2"), 1),
            DerivativeQuestion("(x³) =", "3x²", listOf("3x²", "2x²", "3x"), 0),
            DerivativeQuestion("(5x) =", "5", listOf("5x", "5", "0"), 1),
            DerivativeQuestion("(x⁴) =", "4x³", listOf("x³", "4x", "4x³"), 2),
            DerivativeQuestion("(10) =", "0", listOf("1", "10", "0"), 2),
            DerivativeQuestion("(2x²) =", "4x", listOf("2x", "4x", "4"), 1),
            DerivativeQuestion("(x) =", "1", listOf("0", "x", "1"), 2)
        )

        val question = pool.random()
        currentQuestion = question
        correctAnswerStr = question.correctAnswer
        currentCorrectIndex = question.correctIndex

        derivativeTarget.text = colorVariables(question.expression)

        options.forEachIndexed { i, view ->
            updateLayoutWithText(view, question.options[i], isOption = true)
        }

        problemImage.setImageResource(R.drawable.answer_blue_box)
        problemAnswerContainer.visibility = View.GONE
        resetInternalState()
    }

    private fun setupOptionClicks() {
        options.forEachIndexed { index, layout ->
            layout.setOnClickListener {
                if (isAnswerChecked) return@setOnClickListener
                selectedOptionIndex = index
                highlightSelectedOption(index)
                enableCheckButton()

                val selectedValue = currentQuestion?.options?.get(index) ?: ""
                updateLayoutWithText(problemAnswerContainer, selectedValue, isOption = false)
                problemAnswerContainer.visibility = View.VISIBLE
            }
        }
    }

    private fun updateLayoutWithText(rootView: View, value: String, isOption: Boolean) {
        val textViewId = if (isOption) getTextViewId(rootView.id) else R.id.blueBoxResult
        val textView = rootView.findViewById<TextView>(textViewId)
        val displayValue = value.replace("_", "/")
        textView?.text = colorVariables(displayValue)
    }

    private fun getTextViewId(id: Int) = when(id) {
        R.id.option1 -> R.id.option1Text
        R.id.option2 -> R.id.option2Text
        else -> R.id.option3Text
    }

    private fun colorVariables(text: String): SpannableString {
        val spannable = SpannableString(text)
        val blueColor = ContextCompat.getColor(requireContext(), R.color.blue_1)
        val matcher = Pattern.compile("x").matcher(text)
        while (matcher.find()) {
            spannable.setSpan(ForegroundColorSpan(blueColor), matcher.start(), matcher.end(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        return spannable
    }

    private fun setupCheckButton() {
        checkBtn.setOnClickListener {
            val activity = requireActivity() as Math11GradeQuestionActivity
            val stateContainer = activity.findViewById<FrameLayout>(R.id.stateContainer)
            val circleState = activity.findViewById<ImageView>(R.id.circleState)

            if (!isAnswerChecked) {
                selectedOptionIndex?.let { checkAnswer(it, stateContainer, circleState) }
            } else {
                if (isIncorrectAttempt) {
                    resetForTryAgain()
                } else {
                    if (checkBtn.text == "FINISH") {
                        activity.navigateToXpGained()
                    } else if (!activity.checkAndTriggerMilestone()) {
                        resetUIForNext()
                        activity.showRandomQuestion()
                    }
                }
            }
        }
    }

    private fun checkAnswer(index: Int, stateContainer: FrameLayout, circleState: ImageView) {
        isAnswerChecked = true
        val activity = requireActivity() as Math11GradeQuestionActivity
        val isCorrect = index == currentCorrectIndex

        activity.isResultCurrentlyVisible = true
        stateContainer.visibility = View.VISIBLE

        if (isCorrect) {
            playSound(R.raw.correct)
            activity.isCorrectAnswerShowing = true
            activity.playSuccessAnimation()
            problemImage.setImageResource(R.drawable.answer_correct_box)

            val isFinished = activity.incrementProgress()
            if (isFirstAttempt) activity.totalXp += MathGrade11Type.DERIVATIVES_PROBLEM.xp

            activity.handleCorrectAnswer()
            isIncorrectAttempt = false
            checkBtn.text = if (isFinished) "FINISH" else "CONTINUE"
            showCorrectState(stateContainer, circleState, index)
        } else {
            playSound(R.raw.wrong)
            problemImage.setImageResource(R.drawable.answer_incorrect_box)
            isIncorrectAttempt = true
            checkBtn.text = "TRY AGAIN"
            showIncorrectState(stateContainer, circleState, index)
            setupSeeSolution(stateContainer, circleState)
        }
        isFirstAttempt = false
    }

    private fun setupSeeSolution(stateContainer: FrameLayout, circleState: ImageView) {
        seeEnabledButton.setOnClickListener {
            seeBtn.visibility = View.GONE
            stateAnswer.text = "Solution"
            answerText.text = "Answer: ${correctAnswerStr.replace("_", "/")}"
            answerText.visibility = View.VISIBLE
            checkBtn.text = "CONTINUE"
            problemImage.setImageResource(R.drawable.answer_solution_box)
            updateLayoutWithText(problemAnswerContainer, correctAnswerStr, isOption = false)
            circleState.setImageResource(R.drawable.solution_lamp_icon)

            isAnswerChecked = true
            isIncorrectAttempt = false

            // RESET options: remove the red background and highlight the correct one
            options.forEach { it.setBackgroundResource(R.drawable.custom_background) }
            options[currentCorrectIndex].setBackgroundResource(R.drawable.option_showed)

            applyButtonColors(R.color.black_3, R.color.black_2, R.color.gray_2)
            stateContainer.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.gray_2))
        }
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

    private fun showCorrectState(sc: FrameLayout, cs: ImageView, idx: Int) {
        sc.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.green_3))
        cs.setImageResource(R.drawable.correct_tick_icon)
        options[idx].setBackgroundResource(R.drawable.option_correct)
        stateAnswer.text = "Correct!"
        answerText.text = "Answer: ${correctAnswerStr.replace("_", "/")}"
        answerText.visibility = View.VISIBLE
        applyButtonColors(R.color.green_1, R.color.green_2, R.color.green_4)
    }

    private fun showIncorrectState(sc: FrameLayout, cs: ImageView, idx: Int) {
        sc.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.red_4))
        cs.setImageResource(R.drawable.wrong_circle)
        options[idx].setBackgroundResource(R.drawable.option_incorrect)
        stateAnswer.text = "Incorrect!"
        answerText.visibility = View.GONE
        seeBtn.visibility = View.VISIBLE
        applyButtonColors(R.color.red_1, R.color.red_2, R.color.red_4)
    }

    private fun applyButtonColors(btn: Int, bck: Int, bg: Int) {
        checkBtn.backgroundTintList = ContextCompat.getColorStateList(requireContext(), btn)
        checkBtnBack.backgroundTintList = ContextCompat.getColorStateList(requireContext(), bck)
        btnBack.setBackgroundColor(ContextCompat.getColor(requireContext(), bg))
    }

    private fun setupInitialButtonState() {
        btnBack.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.white))
        checkBtn.backgroundTintList = ContextCompat.getColorStateList(requireContext(), R.color.blue_2)
        checkBtnBack.backgroundTintList = ContextCompat.getColorStateList(requireContext(), R.color.blue_1)
        checkBtn.isEnabled = false
    }

    private fun resetInternalState() {
        selectedOptionIndex = null
        isAnswerChecked = false
        isIncorrectAttempt = false
        isFirstAttempt = true
        seeBtn.visibility = View.GONE
        checkBtn.isEnabled = false
        checkBtn.text = "CHECK"
        requireActivity().findViewById<FrameLayout>(R.id.check_enabled_btn_container).visibility = View.INVISIBLE
        requireActivity().findViewById<FrameLayout>(R.id.check_disabled_btn_container).visibility = View.VISIBLE
    }

    private fun resetForTryAgain() {
        val activity = requireActivity() as Math11GradeQuestionActivity
        activity.isResultCurrentlyVisible = false
        isAnswerChecked = false
        isIncorrectAttempt = false
        selectedOptionIndex = null
        options.forEach { it.setBackgroundResource(R.drawable.custom_background) }
        problemImage.setImageResource(R.drawable.answer_blue_box)
        problemAnswerContainer.visibility = View.GONE
        activity.findViewById<FrameLayout>(R.id.stateContainer).visibility = View.INVISIBLE
        seeBtn.visibility = View.GONE
        checkBtn.text = "CHECK"
        checkBtn.isEnabled = false
        activity.findViewById<FrameLayout>(R.id.check_enabled_btn_container).visibility = View.INVISIBLE
        activity.findViewById<FrameLayout>(R.id.check_disabled_btn_container).visibility = View.VISIBLE
        setupInitialButtonState()
    }

    private fun resetUIForNext() {
        val activity = requireActivity() as Math11GradeQuestionActivity
        activity.isResultCurrentlyVisible = false
        activity.hideSuccessAnimation()
        activity.findViewById<FrameLayout>(R.id.stateContainer).visibility = View.INVISIBLE
        seeBtn.visibility = View.GONE
        checkBtn.text = "CHECK"
        setupInitialButtonState()
    }

    private fun playSound(resId: Int) {
        mediaPlayer?.release()
        mediaPlayer = MediaPlayer.create(requireContext(), resId)
        mediaPlayer?.start()
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaPlayer?.release()
    }
}