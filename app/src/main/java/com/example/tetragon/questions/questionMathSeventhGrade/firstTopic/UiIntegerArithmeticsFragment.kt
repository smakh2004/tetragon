package com.example.tetragon.questions.questionMathSeventhGrade.firstTopic

import android.graphics.Color
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
import com.example.tetragon.questions.questionMathSeventhGrade.Math7GradeQuestionActivity
import com.example.tetragon.questions.questionMathSeventhGrade.MathGrade7Type
import kotlin.random.Random

class UiIntegerArithmeticsFragment  : Fragment(R.layout.fragment_ui_integer_arithmetics) {

    private lateinit var firstNumberText: TextView
    private lateinit var problemImage: ImageView
    private lateinit var options: List<LinearLayout>
    private lateinit var checkBtn: Button
    private lateinit var checkBtnBack: View
    private lateinit var btnBack: View
    private lateinit var seeBtn: FrameLayout
    private lateinit var seeEnabledButton: Button
    private lateinit var stateAnswer: TextView
    private lateinit var answer: TextView
    private lateinit var problemAnswerText: TextView

    private var correctAnswer = 0
    private var selectedOptionIndex: Int? = null
    private var isAnswerChecked = false
    private var isIncorrectAttempt = false
    private var isFirstAttempt = true

    private var mediaPlayer: MediaPlayer? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        firstNumberText = view.findViewById(R.id.firstNumber)
        problemImage = view.findViewById(R.id.problemImage)
        problemAnswerText = view.findViewById(R.id.problemAnswerText)

        options = listOf(
            view.findViewById(R.id.option1),
            view.findViewById(R.id.option2),
            view.findViewById(R.id.option3)
        )

        // Activity UI components
        checkBtn = requireActivity().findViewById(R.id.check_enabled_btn)
        checkBtnBack = requireActivity().findViewById(R.id.check_enabled_button_background)
        btnBack = requireActivity().findViewById(R.id.btnBackground)
        seeBtn = requireActivity().findViewById(R.id.see_btn_container)
        seeEnabledButton = requireActivity().findViewById(R.id.see_enabled_btn)
        stateAnswer = requireActivity().findViewById(R.id.stateAnswer)
        answer = requireActivity().findViewById(R.id.answer)

        setupInitialButtonState()
        generateProblem()
        setupOptionClicks()
        setupCheckButton()
    }

    private fun generateProblem() {
        // Generate two numbers between -15 and 15
        val num1 = Random.nextInt(-15, 15)
        val num2 = Random.nextInt(-15, 15)
        correctAnswer = num1 + num2

        // Format problem text: e.g., "-8 + 5 ="
        val operator = if (num2 >= 0) "+" else "-"
        val problemString = "$num1 $operator ${kotlin.math.abs(num2)} ="
        firstNumberText.text = colorMinusSigns(problemString)

        problemImage.setImageResource(R.drawable.answer_blue_box)

        // Options logic
        val optionSet = mutableSetOf(correctAnswer)
        while (optionSet.size < 3) {
            val distractor = correctAnswer + Random.nextInt(-5, 6)
            if (distractor != correctAnswer) optionSet.add(distractor)
        }

        val shuffledOptions = optionSet.shuffled()
        options.forEachIndexed { index, layout ->
            val tv = layout.getChildAt(0) as TextView
            tv.text = shuffledOptions[index].toString()
            layout.setBackgroundResource(R.drawable.custom_background)
        }

        problemAnswerText.visibility = View.GONE
        resetInternalState()
    }

    private fun colorMinusSigns(text: String): SpannableString {
        val spannable = SpannableString(text)
        val blueColor = ContextCompat.getColor(requireContext(), R.color.blue_1)

        // Find all minus characters and color them blue
        text.forEachIndexed { index, char ->
            if (char == '-' || char == '−') {
                spannable.setSpan(
                    ForegroundColorSpan(blueColor),
                    index, index + 1,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
        }
        return spannable
    }

    private fun setupOptionClicks() {
        options.forEachIndexed { index, layout ->
            layout.setOnClickListener {
                if (isAnswerChecked) return@setOnClickListener
                selectedOptionIndex = index
                highlightSelectedOption(index)
                enableCheckButton()

                val chosenText = (layout.getChildAt(0) as TextView).text.toString()
                problemAnswerText.text = colorMinusSigns(chosenText)
                problemAnswerText.visibility = View.VISIBLE
            }
        }
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
                    val activity = requireActivity() as Math7GradeQuestionActivity
                    if (checkBtn.text == "FINISH") {
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

    private fun checkAnswer(index: Int, stateContainer: FrameLayout, circleState: ImageView) {
        isAnswerChecked = true
        val chosen = (options[index].getChildAt(0) as TextView).text.toString().toInt()
        val activity = requireActivity() as Math7GradeQuestionActivity

        activity.isResultCurrentlyVisible = true
        stateContainer.visibility = View.VISIBLE

        if (chosen == correctAnswer) {
            playSound(R.raw.correct)
            activity.isCorrectAnswerShowing = true
            activity.playSuccessAnimation()
            problemImage.setImageResource(R.drawable.answer_correct_box)

            val isFinished = activity.incrementProgress()
            if (isFirstAttempt) activity.totalXp += MathGrade7Type.INTEGERS_ARITHMETICS.xp

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
            answer.text = "Answer: $correctAnswer"
            answer.visibility = View.VISIBLE
            checkBtn.text = "CONTINUE"
            problemImage.setImageResource(R.drawable.answer_solution_box)

            problemAnswerText.text = colorMinusSigns(correctAnswer.toString())
            problemAnswerText.visibility = View.VISIBLE
            circleState.setImageResource(R.drawable.solution_lamp_icon)

            isAnswerChecked = true
            isIncorrectAttempt = false

            applyButtonColors(R.color.black_3, R.color.black_2, R.color.gray_2)
            stateContainer.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.gray_2))

            options.forEach { layout ->
                val tv = layout.getChildAt(0) as TextView
                layout.setBackgroundResource(
                    if (tv.text.toString().toInt() == correctAnswer) R.drawable.option_showed
                    else R.drawable.custom_background
                )
            }
        }
    }

    // --- Helper Methods (Standard reset/UI logic) ---

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
        answer.text = "Answer: $correctAnswer"
        applyButtonColors(R.color.green_1, R.color.green_2, R.color.green_4)
    }

    private fun showIncorrectState(sc: FrameLayout, cs: ImageView, idx: Int) {
        sc.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.red_4))
        cs.setImageResource(R.drawable.wrong_circle)
        options[idx].setBackgroundResource(R.drawable.option_incorrect)
        stateAnswer.text = "Incorrect!"
        answer.visibility = View.GONE
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

    private fun playSound(resId: Int) {
        mediaPlayer?.release()
        mediaPlayer = MediaPlayer.create(requireContext(), resId)
        mediaPlayer?.start()
    }

    private fun resetForTryAgain() {
        val activity = requireActivity() as Math7GradeQuestionActivity
        activity.isResultCurrentlyVisible = false
        isAnswerChecked = false
        isIncorrectAttempt = false
        selectedOptionIndex = null
        options.forEach { it.setBackgroundResource(R.drawable.custom_background) }
        problemImage.setImageResource(R.drawable.answer_blue_box)
        problemAnswerText.visibility = View.GONE
        requireActivity().findViewById<FrameLayout>(R.id.stateContainer).visibility = View.INVISIBLE
        seeBtn.visibility = View.GONE
        checkBtn.text = "CHECK"
        checkBtn.isEnabled = false
        requireActivity().findViewById<FrameLayout>(R.id.check_enabled_btn_container).visibility = View.INVISIBLE
        requireActivity().findViewById<FrameLayout>(R.id.check_disabled_btn_container).visibility = View.VISIBLE
        setupInitialButtonState()
    }

    private fun resetUIForNext() {
        val activity = requireActivity() as Math7GradeQuestionActivity
        activity.isResultCurrentlyVisible = false
        activity.hideSuccessAnimation()
        requireActivity().findViewById<FrameLayout>(R.id.stateContainer).visibility = View.INVISIBLE
        seeBtn.visibility = View.GONE
        checkBtn.text = "CHECK"
        setupInitialButtonState()
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaPlayer?.release()
    }
}