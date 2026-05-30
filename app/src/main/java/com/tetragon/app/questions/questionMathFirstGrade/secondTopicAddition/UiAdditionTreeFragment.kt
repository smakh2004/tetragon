package com.tetragon.app.questions.questionMathFirstGrade.secondTopicAddition

import android.media.MediaPlayer
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.view.View
import android.widget.*
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.tetragon.app.R
import com.tetragon.app.questions.questionMathFirstGrade.Math1GradeQuestionActivity
import com.tetragon.app.questions.questionMathFirstGrade.MathGrade1Type
import kotlin.random.Random

class UiAdditionTreeFragment : Fragment(R.layout.fragment_ui_addition_tree) {

    // Tree specific views
    private lateinit var numLeftText: TextView
    private lateinit var numRightText: TextView
    private lateinit var problemAnswerText: TextView
    private lateinit var problemImage: ImageView
    private lateinit var instructionText: TextView

    private lateinit var options: List<LinearLayout>

    // Activity UI elements
    private lateinit var checkBtn: Button
    private lateinit var checkBtnBack: View
    private lateinit var btnBack: View
    private lateinit var seeBtn: FrameLayout
    private lateinit var seeEnabledButton: Button
    private lateinit var stateAnswer: TextView
    private lateinit var answer: TextView

    private var correctAnswer = 0
    private var selectedOptionIndex: Int? = null
    private var isAnswerChecked = false
    private var isIncorrectAttempt = false
    private var isFirstAttempt = true

    private var mediaPlayer: MediaPlayer? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialize Views
        numLeftText = view.findViewById(R.id.firstNumber)
        numRightText = view.findViewById(R.id.secondNumber)
        problemImage = view.findViewById(R.id.problemImage)
        problemAnswerText = view.findViewById(R.id.problemAnswerText)
        instructionText = view.findViewById(R.id.instructionText)

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

        // Apply blue styling to the first word of the instruction
        setupInstructionText()

        setupInitialButtonState()
        generateTreeProblem()
        setupOptionClicks()
        setupCheckButton()
    }

    private fun setupInstructionText() {
        val fullText = getString(R.string.instruction_addition_tree)
        val spannable = SpannableString(fullText)

        // Find the index of the first space to color the first word dynamically
        val firstSpace = fullText.indexOf(" ")
        val end = if (firstSpace != -1) firstSpace else fullText.length

        if (end > 0) {
            spannable.setSpan(
                ForegroundColorSpan(ContextCompat.getColor(requireContext(), R.color.blue_2)),
                0, end,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
        instructionText.text = spannable
    }

    private fun generateTreeProblem() {
        val maxTargetSum = 10
        correctAnswer = Random.nextInt(2, maxTargetSum + 1)

        val leftValue = Random.nextInt(1, correctAnswer)
        val rightValue = correctAnswer - leftValue

        numLeftText.text = leftValue.toString()
        numRightText.text = rightValue.toString()

        problemImage.setImageResource(R.drawable.answer_blue_box)
        problemAnswerText.text = ""
        problemAnswerText.visibility = View.VISIBLE

        val optionSet = mutableSetOf(correctAnswer)
        while (optionSet.size < 3) {
            val distractor = Random.nextInt(1, 11)
            optionSet.add(distractor)
        }

        val shuffledOptions = optionSet.shuffled()
        options.forEachIndexed { index, layout ->
            val tv = layout.getChildAt(0) as TextView
            tv.text = shuffledOptions[index].toString()
            layout.setBackgroundResource(R.drawable.custom_background)
        }

        selectedOptionIndex = null
        isAnswerChecked = false
        isIncorrectAttempt = false
        isFirstAttempt = true
        seeBtn.visibility = View.GONE
        checkBtn.isEnabled = false
        checkBtn.text = getString(R.string.btn_check)

        requireActivity().findViewById<FrameLayout>(R.id.check_enabled_btn_container).visibility = View.INVISIBLE
        requireActivity().findViewById<FrameLayout>(R.id.check_disabled_btn_container).visibility = View.VISIBLE

        setupInitialButtonState()
    }

    private fun setupOptionClicks() {
        options.forEachIndexed { index, layout ->
            layout.setOnClickListener {
                if (isAnswerChecked) return@setOnClickListener

                selectedOptionIndex = index
                highlightSelectedOption(index)
                enableCheckButton()

                val chosenNumber = (layout.getChildAt(0) as TextView).text.toString()
                problemAnswerText.text = chosenNumber
            }
        }
    }

    private fun checkAnswer(index: Int, stateContainer: FrameLayout, circleState: ImageView) {
        isAnswerChecked = true
        val chosen = (options[index].getChildAt(0) as TextView).text.toString().toInt()
        val activity = requireActivity() as Math1GradeQuestionActivity

        activity.isResultCurrentlyVisible = true
        stateContainer.visibility = View.VISIBLE
        problemAnswerText.text = chosen.toString()

        if (chosen == correctAnswer) {
            playSound(R.raw.correct)
            activity.isCorrectAnswerShowing = true
            activity.playSuccessAnimation()
            problemImage.setImageResource(R.drawable.answer_correct_box)

            val isFinished = activity.incrementProgress()
            if (isFirstAttempt) {
                activity.totalXp += MathGrade1Type.ADDITION_TREE.xp
            }
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

    private fun highlightSelectedOption(selectedIndex: Int) {
        options.forEachIndexed { i, layout ->
            layout.setBackgroundResource(if (i == selectedIndex) R.drawable.option_selected else R.drawable.custom_background)
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
    }

    private fun resetForTryAgain() {
        val activity = requireActivity() as Math1GradeQuestionActivity
        activity.isResultCurrentlyVisible = false
        isAnswerChecked = false
        isIncorrectAttempt = false
        selectedOptionIndex = null

        options.forEach { it.setBackgroundResource(R.drawable.custom_background) }
        problemImage.setImageResource(R.drawable.answer_blue_box)
        problemAnswerText.text = ""

        requireActivity().findViewById<FrameLayout>(R.id.stateContainer).visibility = View.INVISIBLE
        seeBtn.visibility = View.GONE
        checkBtn.text = getString(R.string.btn_check)
        checkBtn.isEnabled = false

        setupInitialButtonState()
        updateCheckButtonVisibility(false)
    }

    private fun resetUIForNext() {
        val activity = requireActivity() as Math1GradeQuestionActivity
        activity.isResultCurrentlyVisible = false
        activity.hideSuccessAnimation()
        requireActivity().findViewById<FrameLayout>(R.id.stateContainer).visibility = View.INVISIBLE
        seeBtn.visibility = View.GONE
        checkBtn.text = getString(R.string.btn_check)
    }

    private fun updateCheckButtonVisibility(enabled: Boolean) {
        requireActivity().findViewById<FrameLayout>(R.id.check_enabled_btn_container).visibility = if (enabled) View.VISIBLE else View.INVISIBLE
        requireActivity().findViewById<FrameLayout>(R.id.check_disabled_btn_container).visibility = if (enabled) View.INVISIBLE else View.VISIBLE
    }

    private fun enableCheckButton() {
        checkBtn.isEnabled = true
        updateCheckButtonVisibility(true)
    }

    private fun playSound(soundResId: Int) {
        mediaPlayer?.release()
        mediaPlayer = MediaPlayer.create(requireContext(), soundResId)
        mediaPlayer?.start()
    }

    private fun setupInitialButtonState() {
        btnBack.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.white))
        checkBtn.backgroundTintList = ContextCompat.getColorStateList(requireContext(), R.color.blue_2)
        checkBtnBack.backgroundTintList = ContextCompat.getColorStateList(requireContext(), R.color.blue_1)
        checkBtn.isEnabled = false
    }

    private fun showCorrectState(stateContainer: FrameLayout, circleState: ImageView, index: Int) {
        stateContainer.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.green_3))
        circleState.setImageResource(R.drawable.correct_tick_icon)
        options[index].setBackgroundResource(R.drawable.option_correct)
        stateAnswer.text = getString(R.string.state_correct)
        answer.text = getString(R.string.label_answer, correctAnswer.toString())
        applyButtonColors(R.color.green_1, R.color.green_2, R.color.green_4)
    }

    private fun showIncorrectState(stateContainer: FrameLayout, circleState: ImageView, index: Int) {
        stateContainer.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.red_4))
        circleState.setImageResource(R.drawable.wrong_circle)
        options[index].setBackgroundResource(R.drawable.option_incorrect)
        stateAnswer.text = getString(R.string.state_incorrect)
        seeEnabledButton.text = getString(R.string.btn_see_solution)
        answer.visibility = View.GONE
        seeBtn.visibility = View.VISIBLE
        applyButtonColors(R.color.red_1, R.color.red_2, R.color.red_4)
    }

    private fun setupSeeSolution(stateContainer: FrameLayout, circleState: ImageView) {
        seeEnabledButton.setOnClickListener {
            seeBtn.visibility = View.GONE
            stateAnswer.text = getString(R.string.state_solution)
            answer.text = getString(R.string.label_answer, correctAnswer.toString())
            answer.visibility = View.VISIBLE
            checkBtn.text = getString(R.string.btn_continue)
            problemImage.setImageResource(R.drawable.answer_solution_box)
            problemAnswerText.text = correctAnswer.toString()
            circleState.setImageResource(R.drawable.solution_lamp_icon)

            isAnswerChecked = true
            isIncorrectAttempt = false

            checkBtn.backgroundTintList = ContextCompat.getColorStateList(requireContext(), R.color.black_3)
            checkBtnBack.backgroundTintList = ContextCompat.getColorStateList(requireContext(), R.color.black_2)
            btnBack.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.gray_2))
            stateContainer.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.gray_2))

            options.forEach { layout ->
                val tv = layout.getChildAt(0) as TextView
                layout.setBackgroundResource(
                    if (tv.text.toString().toInt() == correctAnswer) R.drawable.option_showed else R.drawable.custom_background
                )
            }
        }
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