package com.example.tetragon.questions.questionMathSecondGrade.secondTopic

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.media.MediaPlayer
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.example.tetragon.R
import com.example.tetragon.questions.questionMathSecondGrade.Math2GradeQuestionActivity
import com.example.tetragon.questions.questionMathSecondGrade.MathGrade2Type
import kotlin.random.Random

class UiColumnMethodFragment : Fragment(R.layout.fragment_ui_column_method) {

    private lateinit var tvTopNumber: TextView
    private lateinit var tvStaticSecondNum: TextView
    private lateinit var tvResultTens: TextView
    private lateinit var tvResultOnes: TextView
    private lateinit var tvCarryInput: TextView
    private lateinit var tvSecondNumInput: TextView
    private lateinit var frameCarry: FrameLayout
    private lateinit var frameSecondNum: FrameLayout
    private lateinit var boxCarry: ImageView
    private lateinit var boxEmpty: ImageView

    private lateinit var cursorCarry: View
    private lateinit var cursorSecond: View

    private lateinit var checkBtn: Button
    private lateinit var checkBtnBack: View
    private lateinit var btnBack: View
    private lateinit var seeBtn: FrameLayout
    private lateinit var seeEnabledButton: Button
    private lateinit var stateAnswer: TextView
    private lateinit var answer: TextView

    private var activeInput: TextView? = null
    private var activeBox: ImageView? = null
    private var activeCursor: View? = null
    private var cursorAnimator: ObjectAnimator? = null

    private var correctCarry = 0
    private var correctSecondDigit = 0
    private var isAnswerChecked = false
    private var isIncorrectAttempt = false
    private var isFirstAttempt = true
    private var mediaPlayer: MediaPlayer? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialize Views
        tvTopNumber = view.findViewById(R.id.tvTopNumber)
        tvStaticSecondNum = view.findViewById(R.id.tvStaticSecondNum)
        tvResultTens = view.findViewById(R.id.tvResultTens)
        tvResultOnes = view.findViewById(R.id.tvResultOnes)
        tvCarryInput = view.findViewById(R.id.tvCarryInput)
        tvSecondNumInput = view.findViewById(R.id.tvSecondNumInput)
        frameCarry = view.findViewById(R.id.frameCarry)
        frameSecondNum = view.findViewById(R.id.frameSecondNum)
        boxCarry = view.findViewById(R.id.boxCarry)
        boxEmpty = view.findViewById(R.id.boxEmpty)
        cursorCarry = view.findViewById(R.id.cursorCarry)
        cursorSecond = view.findViewById(R.id.cursorSecond)

        // Activity UI components
        checkBtn = requireActivity().findViewById(R.id.check_enabled_btn)
        checkBtnBack = requireActivity().findViewById(R.id.check_enabled_button_background)
        btnBack = requireActivity().findViewById(R.id.btnBackground)
        seeBtn = requireActivity().findViewById(R.id.see_btn_container)
        seeEnabledButton = requireActivity().findViewById(R.id.see_enabled_btn)
        stateAnswer = requireActivity().findViewById(R.id.stateAnswer)
        answer = requireActivity().findViewById(R.id.answer)

        setupFocusLogic()
        setupKeyboard(view)
        setupInitialButtonState()
        generateProblem()
        setupCheckButton()
    }

    private fun setupFocusLogic() {
        setFocus(tvCarryInput, boxCarry, cursorCarry)
        frameCarry.setOnClickListener { setFocus(tvCarryInput, boxCarry, cursorCarry) }
        frameSecondNum.setOnClickListener { setFocus(tvSecondNumInput, boxEmpty, cursorSecond) }
    }

    private fun setFocus(targetTextView: TextView, targetBox: ImageView, targetCursor: View) {
        if (isAnswerChecked) return

        cursorAnimator?.cancel()
        activeCursor?.visibility = View.GONE
        activeBox?.alpha = 1.0f

        activeInput = targetTextView
        activeBox = targetBox
        activeCursor = targetCursor

        updateCursorPosition()
        activeCursor?.visibility = View.VISIBLE

        cursorAnimator = ObjectAnimator.ofFloat(targetCursor, "alpha", 1.0f, 0.0f).apply {
            duration = 500
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            start()
        }
    }

    private fun updateCursorPosition() {
        val cursor = activeCursor ?: return
        val input = activeInput ?: return

        val density = resources.displayMetrics.density
        val shiftAmount = 10f * density

        if (input.text.isNotEmpty()) {
            cursor.translationX = shiftAmount
        } else {
            cursor.translationX = 0f
        }
    }

    private fun setupKeyboard(view: View) {
        val buttonIds = listOf(
            R.id.btn0, R.id.btn1, R.id.btn2, R.id.btn3, R.id.btn4,
            R.id.btn5, R.id.btn6, R.id.btn7, R.id.btn8, R.id.btn9
        )

        buttonIds.forEach { id ->
            view.findViewById<Button>(id).setOnClickListener {
                if (isAnswerChecked) return@setOnClickListener

                // NEW LOGIC: Only allow input if the text field is empty
                // User must press DELETE to change a number
                if (activeInput?.text.isNullOrEmpty()) {
                    activeInput?.text = (it as Button).text.toString()
                    activeInput?.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_color))
                    updateCursorPosition()
                    toggleCheckButtonState()
                }
            }
        }

        view.findViewById<Button>(R.id.btnDel).setOnClickListener {
            if (isAnswerChecked) return@setOnClickListener
            activeInput?.text = ""
            updateCursorPosition()
            toggleCheckButtonState()
        }
    }

    private fun toggleCheckButtonState() {
        val hasInput = tvCarryInput.text.isNotEmpty() || tvSecondNumInput.text.isNotEmpty()
        if (hasInput) enableCheckButton() else disableCheckButton()
    }

    private fun generateProblem() {
        val totalSum = Random.nextInt(20, 91)
        val num1 = Random.nextInt(10, totalSum - 5)
        val num2 = totalSum - num1

        correctCarry = num1 / 10
        correctSecondDigit = num2 % 10

        tvTopNumber.text = (num1 % 10).toString()
        tvStaticSecondNum.text = (num2 / 10).toString()
        tvResultTens.text = (totalSum / 10).toString()
        tvResultOnes.text = (totalSum % 10).toString()

        tvCarryInput.text = ""
        tvSecondNumInput.text = ""
        tvCarryInput.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_color))
        tvSecondNumInput.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_color))

        boxCarry.setImageResource(R.drawable.answer_blue_box)
        boxEmpty.setImageResource(R.drawable.answer_blue_box)

        isAnswerChecked = false
        isIncorrectAttempt = false
        isFirstAttempt = true
        seeBtn.visibility = View.GONE

        setFocus(tvCarryInput, boxCarry, cursorCarry)

        checkBtn.text = "CHECK"
        disableCheckButton()
        setupInitialButtonState()
    }

    private fun checkAnswer(stateContainer: FrameLayout, circleState: ImageView) {
        cursorAnimator?.cancel()
        activeCursor?.visibility = View.GONE

        isAnswerChecked = true
        val activity = requireActivity() as Math2GradeQuestionActivity
        activity.isResultCurrentlyVisible = true

        val userCarry = tvCarryInput.text.toString().toIntOrNull() ?: -1
        val userDigit = tvSecondNumInput.text.toString().toIntOrNull() ?: -1

        val topNum = tvTopNumber.text.toString().toIntOrNull() ?: 0
        val staticSecondTens = tvStaticSecondNum.text.toString().toIntOrNull() ?: 0

        val userResult = (userCarry * 10 + topNum) + (staticSecondTens * 10 + userDigit)
        val targetSum = tvResultTens.text.toString().toInt() * 10 + tvResultOnes.text.toString().toInt()

        stateContainer.visibility = View.VISIBLE

        if (userResult == targetSum && userCarry >= 0 && userDigit >= 0) {
            playSound(R.raw.correct)
            activity.isCorrectAnswerShowing = true
            activity.playSuccessAnimation()
            boxCarry.setImageResource(R.drawable.answer_correct_box)
            boxEmpty.setImageResource(R.drawable.answer_correct_box)

            tvCarryInput.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_color))
            tvSecondNumInput.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_color))

            val isFinished = activity.incrementProgress()
            if (isFirstAttempt) activity.totalXp += MathGrade2Type.COLUMN_METHOD_ADDITION.xp
            activity.handleCorrectAnswer()

            isIncorrectAttempt = false
            checkBtn.text = if (isFinished) "FINISH" else "CONTINUE"
            showCorrectState(stateContainer, circleState)
        } else {
            playSound(R.raw.wrong)
            boxCarry.setImageResource(R.drawable.answer_incorrect_box)
            boxEmpty.setImageResource(R.drawable.answer_incorrect_box)

            tvCarryInput.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_color))
            tvSecondNumInput.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_color))

            isIncorrectAttempt = true
            checkBtn.text = "TRY AGAIN"
            showIncorrectState(stateContainer, circleState)
            setupSeeSolution(stateContainer, circleState)
        }
        isFirstAttempt = false
    }

    private fun setupSeeSolution(stateContainer: FrameLayout, circleState: ImageView) {
        seeEnabledButton.setOnClickListener {
            seeBtn.visibility = View.GONE
            stateAnswer.text = "Solution"
            answer.text = "Answer: $correctCarry and $correctSecondDigit"
            answer.visibility = View.VISIBLE
            checkBtn.text = "CONTINUE"

            boxCarry.setImageResource(R.drawable.answer_solution_box)
            boxEmpty.setImageResource(R.drawable.answer_solution_box)

            tvCarryInput.text = correctCarry.toString()
            tvSecondNumInput.text = correctSecondDigit.toString()

            tvCarryInput.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_color))
            tvSecondNumInput.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_color))

            circleState.setImageResource(R.drawable.solution_lamp_icon)
            isIncorrectAttempt = false
            applyButtonColors(R.color.black_3, R.color.black_2, R.color.gray_2)
            stateContainer.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.gray_2))
        }
    }

    private fun setupCheckButton() {
        checkBtn.setOnClickListener {
            val stateContainer = requireActivity().findViewById<FrameLayout>(R.id.stateContainer)
            val circleState = requireActivity().findViewById<ImageView>(R.id.circleState)

            if (!isAnswerChecked) {
                checkAnswer(stateContainer, circleState)
            } else {
                if (isIncorrectAttempt) {
                    resetForTryAgain()
                } else {
                    val activity = requireActivity() as Math2GradeQuestionActivity
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

    private fun resetForTryAgain() {
        val activity = requireActivity() as Math2GradeQuestionActivity
        activity.isResultCurrentlyVisible = false
        val stateContainer = requireActivity().findViewById<FrameLayout>(R.id.stateContainer)

        isAnswerChecked = false
        isIncorrectAttempt = false

        tvCarryInput.text = ""
        tvSecondNumInput.text = ""
        tvCarryInput.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_color))
        tvSecondNumInput.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_color))

        boxCarry.setImageResource(R.drawable.answer_blue_box)
        boxEmpty.setImageResource(R.drawable.answer_blue_box)

        stateContainer.visibility = View.INVISIBLE
        seeBtn.visibility = View.GONE
        stateAnswer.text = ""
        answer.text = ""

        setFocus(tvCarryInput, boxCarry, cursorCarry)
        checkBtn.text = "CHECK"
        disableCheckButton()
        setupInitialButtonState()
    }

    private fun showCorrectState(stateContainer: FrameLayout, circleState: ImageView) {
        stateContainer.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.green_3))
        circleState.setImageResource(R.drawable.correct_tick_icon)
        stateAnswer.text = "Correct!"
        answer.text = "Answer: $correctCarry and $correctSecondDigit"
        applyButtonColors(R.color.green_1, R.color.green_2, R.color.green_4)
    }

    private fun showIncorrectState(stateContainer: FrameLayout, circleState: ImageView) {
        stateContainer.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.red_4))
        circleState.setImageResource(R.drawable.wrong_circle)
        stateAnswer.text = "Incorrect!"
        answer.visibility = View.GONE
        seeBtn.visibility = View.VISIBLE
        applyButtonColors(R.color.red_1, R.color.red_2, R.color.red_4)
    }

    private fun resetUIForNext() {
        val activity = requireActivity() as Math2GradeQuestionActivity
        activity.isResultCurrentlyVisible = false
        activity.hideSuccessAnimation()
        requireActivity().findViewById<FrameLayout>(R.id.stateContainer).visibility = View.INVISIBLE
        seeBtn.visibility = View.GONE
        checkBtn.text = "CHECK"
        setupInitialButtonState()
    }

    private fun setupInitialButtonState() {
        btnBack.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.white))
        checkBtn.backgroundTintList = ContextCompat.getColorStateList(requireContext(), R.color.blue_2)
        checkBtnBack.backgroundTintList = ContextCompat.getColorStateList(requireContext(), R.color.blue_1)
    }

    private fun enableCheckButton() {
        checkBtn.isEnabled = true
        requireActivity().findViewById<FrameLayout>(R.id.check_enabled_btn_container).visibility = View.VISIBLE
        requireActivity().findViewById<FrameLayout>(R.id.check_disabled_btn_container).visibility = View.INVISIBLE
    }

    private fun disableCheckButton() {
        checkBtn.isEnabled = false
        requireActivity().findViewById<FrameLayout>(R.id.check_enabled_btn_container).visibility = View.INVISIBLE
        requireActivity().findViewById<FrameLayout>(R.id.check_disabled_btn_container).visibility = View.VISIBLE
    }

    private fun applyButtonColors(buttonColor: Int, backColor: Int, backgroundColor: Int) {
        checkBtn.backgroundTintList = ContextCompat.getColorStateList(requireContext(), buttonColor)
        checkBtnBack.backgroundTintList = ContextCompat.getColorStateList(requireContext(), backColor)
        btnBack.setBackgroundColor(ContextCompat.getColor(requireContext(), backgroundColor))
    }

    private fun playSound(soundResId: Int) {
        mediaPlayer?.release()
        mediaPlayer = MediaPlayer.create(requireContext(), soundResId)
        mediaPlayer?.start()
    }

    override fun onDestroy() {
        super.onDestroy()
        cursorAnimator?.cancel()
        mediaPlayer?.release()
        mediaPlayer = null
    }
}