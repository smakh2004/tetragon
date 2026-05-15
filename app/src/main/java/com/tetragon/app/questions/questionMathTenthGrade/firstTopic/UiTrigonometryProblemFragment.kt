package com.tetragon.app.questions.questionMathTenthGrade.firstTopic

import android.media.MediaPlayer
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.tetragon.app.R
import com.tetragon.app.questions.questionMathTenthGrade.Math10GradeQuestionActivity
import com.tetragon.app.questions.questionMathTenthGrade.MathGrade10Type
import java.util.regex.Pattern

class UiTrigonometryProblemFragment : Fragment(R.layout.fragment_ui_trigonometry_problem) {

    data class TrigQuestion(
        val expression: String,
        val correctAnswer: String,
        val options: List<String>,
        val correctIndex: Int
    )

    private lateinit var questionText: TextView
    private lateinit var problemImage: ImageView
    private lateinit var options: List<View>
    private lateinit var checkBtn: Button
    private lateinit var checkBtnBack: View
    private lateinit var btnBack: View
    private lateinit var seeBtn: FrameLayout
    private lateinit var seeEnabledButton: Button
    private lateinit var stateAnswer: TextView
    private lateinit var answerText: TextView
    private lateinit var problemAnswerContainer: LinearLayout

    private var currentQuestion: TrigQuestion? = null
    private var correctAnswerStr = ""
    private var currentCorrectIndex = 0
    private var selectedOptionIndex: Int? = null
    private var isAnswerChecked = false
    private var isIncorrectAttempt = false
    private var isFirstAttempt = true

    private var mediaPlayer: MediaPlayer? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        questionText = view.findViewById(R.id.firstNumber)
        problemImage = view.findViewById(R.id.problemImage)
        problemAnswerContainer = view.findViewById(R.id.problemAnswerText)

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
        answerText = activity.findViewById(R.id.answer)

        setupInitialButtonState()
        generateTrigProblem()
        setupOptionClicks()
        setupCheckButton()
    }

    private fun generateTrigProblem() {
        val pool = listOf(
            TrigQuestion("sin 30° =", "1_2", listOf("1", "1_2", "√3_2"), 1),
            TrigQuestion("cos 60° =", "1_2", listOf("1_2", "0", "1"), 0),
            TrigQuestion("tan 45° =", "1", listOf("0", "1_2", "1"), 2),
            TrigQuestion("sin 90° =", "1", listOf("1", "√2_2", "0"), 0),
            TrigQuestion("cos 30° =", "√3_2", listOf("1_2", "√3_2", "1"), 1),
            TrigQuestion("tan 60° =", "√3", listOf("1", "√3_3", "√3"), 2),
            TrigQuestion("sin 45° =", "√2_2", listOf("1_2", "√2_2", "1"), 1),
            TrigQuestion("cos 0° =", "1", listOf("√3_2", "1_2", "1"), 2)
        )

        val question = pool.random()
        currentQuestion = question
        correctAnswerStr = question.correctAnswer
        currentCorrectIndex = question.correctIndex

        questionText.text = colorTrigFunctions(question.expression)

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
        val topText = when (rootView.id) {
            R.id.option1 -> rootView.findViewById<TextView>(R.id.option1Top)
            R.id.option2 -> rootView.findViewById<TextView>(R.id.option2Top)
            R.id.option3 -> rootView.findViewById<TextView>(R.id.option3Top)
            else -> rootView.findViewById<TextView>(R.id.blueBoxTop)
        }

        val bottomText = when (rootView.id) {
            R.id.option1 -> rootView.findViewById<TextView>(R.id.option1Bottom)
            R.id.option2 -> rootView.findViewById<TextView>(R.id.option2Bottom)
            R.id.option3 -> rootView.findViewById<TextView>(R.id.option3Bottom)
            else -> rootView.findViewById<TextView>(R.id.blueBoxBottom)
        }

        val line = when (rootView.id) {
            R.id.option1 -> rootView.findViewById<View>(R.id.option1Line)
            R.id.option2 -> rootView.findViewById<View>(R.id.option2Line)
            R.id.option3 -> rootView.findViewById<View>(R.id.option3Line)
            else -> rootView.findViewById<View>(R.id.blueBoxLine)
        }

        if (value.contains("_")) {
            val parts = value.split("_")
            topText.text = parts[0]
            bottomText.text = parts[1]

            topText.textSize = if (isOption) 16f else 18f
            bottomText.visibility = View.VISIBLE
            line.visibility = View.VISIBLE

            if (rootView is LinearLayout) {
                rootView.orientation = LinearLayout.VERTICAL
                rootView.gravity = Gravity.CENTER
            }
        } else {
            topText.text = value
            topText.textSize = if (isOption) 24f else 18f

            bottomText.visibility = View.GONE
            line.visibility = View.GONE

            if (rootView is LinearLayout) {
                rootView.gravity = Gravity.CENTER
            }
        }
    }

    private fun colorTrigFunctions(text: String): SpannableString {
        val spannable = SpannableString(text)
        val blueColor = ContextCompat.getColor(requireContext(), R.color.blue_1)
        val matcher = Pattern.compile("(?i)sin|cos|tan").matcher(text)
        while (matcher.find()) {
            spannable.setSpan(ForegroundColorSpan(blueColor), matcher.start(), matcher.end(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        return spannable
    }

    private fun setupCheckButton() {
        checkBtn.setOnClickListener {
            val stateContainer = requireActivity().findViewById<FrameLayout>(R.id.stateContainer)
            val circleState = requireActivity().findViewById<ImageView>(R.id.circleState)

            if (!isAnswerChecked) {
                selectedOptionIndex?.let { checkAnswer(it, stateContainer, circleState) }
            } else {
                if (isIncorrectAttempt) {
                    resetForTryAgain()
                } else {
                    val activity = requireActivity() as Math10GradeQuestionActivity
                    if (checkBtn.text == getString(R.string.btn_finish)) activity.navigateToXpGained()
                    else if (!activity.checkAndTriggerMilestone()) {
                        resetUIForNext()
                        activity.showRandomQuestion()
                    }
                }
            }
        }
    }

    private fun checkAnswer(index: Int, stateContainer: FrameLayout, circleState: ImageView) {
        isAnswerChecked = true
        val activity = requireActivity() as Math10GradeQuestionActivity
        val isCorrect = index == currentCorrectIndex

        activity.isResultCurrentlyVisible = true
        stateContainer.visibility = View.VISIBLE

        if (isCorrect) {
            playSound(R.raw.correct)
            activity.isCorrectAnswerShowing = true
            activity.playSuccessAnimation()
            problemImage.setImageResource(R.drawable.answer_correct_box)
            val isFinished = activity.incrementProgress()
            if (isFirstAttempt) activity.totalXp += MathGrade10Type.TRIGONOMETRY_PROBLEM.xp
            activity.handleCorrectAnswer()
            isIncorrectAttempt = false
            checkBtn.text = if (isFinished) getString(R.string.btn_finish) else getString(R.string.btn_continue)
            showCorrectState(stateContainer, circleState, index)
        } else {
            playSound(R.raw.wrong)
            problemImage.setImageResource(R.drawable.answer_incorrect_box)
            isIncorrectAttempt = true
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
            answerText.text = getString(R.string.label_answer, correctAnswerStr.replace("_", "/"))
            answerText.visibility = View.VISIBLE
            checkBtn.text = getString(R.string.btn_continue)
            problemImage.setImageResource(R.drawable.answer_solution_box)
            updateLayoutWithText(problemAnswerContainer, correctAnswerStr, isOption = false)
            problemAnswerContainer.visibility = View.VISIBLE
            circleState.setImageResource(R.drawable.solution_lamp_icon)
            isAnswerChecked = true
            isIncorrectAttempt = false
            applyButtonColors(R.color.black_3, R.color.black_2, R.color.gray_2)
            stateContainer.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.gray_2))
            options[currentCorrectIndex].setBackgroundResource(R.drawable.option_showed)
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
        stateAnswer.text = getString(R.string.state_correct)
        answerText.text = getString(R.string.label_answer, correctAnswerStr.replace("_", "/"))
        applyButtonColors(R.color.green_1, R.color.green_2, R.color.green_4)
    }

    private fun showIncorrectState(sc: FrameLayout, cs: ImageView, idx: Int) {
        sc.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.red_4))
        cs.setImageResource(R.drawable.wrong_circle)
        options[idx].setBackgroundResource(R.drawable.option_incorrect)
        stateAnswer.text = getString(R.string.state_incorrect)
        answerText.visibility = View.GONE
        seeBtn.visibility = View.VISIBLE
        seeEnabledButton.text = getString(R.string.btn_see_solution)
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
        checkBtn.text = getString(R.string.btn_check)
        requireActivity().findViewById<FrameLayout>(R.id.check_enabled_btn_container).visibility = View.INVISIBLE
        requireActivity().findViewById<FrameLayout>(R.id.check_disabled_btn_container).visibility = View.VISIBLE
    }

    private fun resetForTryAgain() {
        val activity = requireActivity() as Math10GradeQuestionActivity
        activity.isResultCurrentlyVisible = false
        isAnswerChecked = false
        isIncorrectAttempt = false
        selectedOptionIndex = null
        options.forEach { it.setBackgroundResource(R.drawable.custom_background) }
        problemImage.setImageResource(R.drawable.answer_blue_box)
        problemAnswerContainer.visibility = View.GONE
        requireActivity().findViewById<FrameLayout>(R.id.stateContainer).visibility = View.INVISIBLE
        seeBtn.visibility = View.GONE
        checkBtn.text = getString(R.string.btn_check)
        checkBtn.isEnabled = false
        requireActivity().findViewById<FrameLayout>(R.id.check_enabled_btn_container).visibility = View.INVISIBLE
        requireActivity().findViewById<FrameLayout>(R.id.check_disabled_btn_container).visibility = View.VISIBLE
        setupInitialButtonState()
    }

    private fun resetUIForNext() {
        val activity = requireActivity() as Math10GradeQuestionActivity
        activity.isResultCurrentlyVisible = false
        activity.hideSuccessAnimation()
        requireActivity().findViewById<FrameLayout>(R.id.stateContainer).visibility = View.INVISIBLE
        seeBtn.visibility = View.GONE
        checkBtn.text = getString(R.string.btn_check)
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