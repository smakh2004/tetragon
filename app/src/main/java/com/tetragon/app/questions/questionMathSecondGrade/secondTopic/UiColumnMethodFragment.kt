package com.tetragon.app.questions.questionMathSecondGrade.secondTopic

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.media.MediaPlayer
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.tetragon.app.R
import com.tetragon.app.questions.questionMathSecondGrade.Math2GradeQuestionActivity
import com.tetragon.app.questions.questionMathSecondGrade.MathGrade2Type
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

    private var fullNum1 = 0
    private var fullNum2 = 0
    private var correctCarry = 0
    private var correctSecondDigit = 0

    private var isAnswerChecked = false
    private var isIncorrectAttempt = false
    private var isFirstAttempt = true
    private var isSolutionShown = false
    private var mediaPlayer: MediaPlayer? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val activity = requireActivity() as Math2GradeQuestionActivity

        // Initialize Fragment Views
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
        checkBtn = activity.findViewById(R.id.check_enabled_btn)
        checkBtnBack = activity.findViewById(R.id.check_enabled_button_background)
        btnBack = activity.findViewById(R.id.btnBackground)
        seeBtn = activity.findViewById(R.id.see_btn_container)
        seeEnabledButton = activity.findViewById(R.id.see_enabled_btn)
        stateAnswer = activity.findViewById(R.id.stateAnswer)
        answer = activity.findViewById(R.id.answer)

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
        boxCarry.setImageResource(R.drawable.answer_default_box)
        boxEmpty.setImageResource(R.drawable.answer_default_box)

        cursorAnimator?.cancel()
        cursorCarry.visibility = View.GONE
        cursorSecond.visibility = View.GONE

        activeInput = targetTextView
        activeBox = targetBox
        activeCursor = targetCursor
        activeBox?.setImageResource(R.drawable.answer_blue_box)

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
        cursor.translationX = if (input.text.isNotEmpty()) 10f * density else 0f
    }

    private fun setupKeyboard(view: View) {
        val buttonIds = listOf(R.id.btn0, R.id.btn1, R.id.btn2, R.id.btn3, R.id.btn4, R.id.btn5, R.id.btn6, R.id.btn7, R.id.btn8, R.id.btn9)
        buttonIds.forEach { id ->
            view.findViewById<Button>(id).setOnClickListener {
                if (isAnswerChecked) return@setOnClickListener
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
        val isBothFilled = tvCarryInput.text.isNotEmpty() && tvSecondNumInput.text.isNotEmpty()
        if (isBothFilled) enableCheckButton() else disableCheckButton()
    }

    private fun generateProblem() {
        val totalSum = Random.nextInt(20, 91)
        val maxNum1 = totalSum - 10
        fullNum1 = Random.nextInt(1, maxNum1 + 1)
        fullNum2 = totalSum - fullNum1

        correctCarry = fullNum1 / 10
        correctSecondDigit = fullNum2 % 10

        tvTopNumber.text = (fullNum1 % 10).toString()
        tvStaticSecondNum.text = (fullNum2 / 10).toString()
        tvResultTens.text = (totalSum / 10).toString()
        tvResultOnes.text = (totalSum % 10).toString()

        tvCarryInput.text = ""
        tvSecondNumInput.text = ""
        isAnswerChecked = false
        isIncorrectAttempt = false
        isFirstAttempt = true
        isSolutionShown = false
        seeBtn.visibility = View.GONE

        setFocus(tvCarryInput, boxCarry, cursorCarry)
        checkBtn.text = getString(R.string.btn_check)
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

        val userResult = (userCarry * 10 + (fullNum1 % 10)) + ((fullNum2 / 10) * 10 + userDigit)
        val targetSum = fullNum1 + fullNum2

        stateContainer.visibility = View.VISIBLE

        if (userResult == targetSum && userCarry >= 0 && userDigit >= 0) {
            playSound(R.raw.correct)
            activity.isCorrectAnswerShowing = true
            activity.playSuccessAnimation()

            boxCarry.setImageResource(R.drawable.answer_correct_box)
            boxEmpty.setImageResource(R.drawable.answer_correct_box)

            val isFinished = activity.incrementProgress()
            if (isFirstAttempt) {
                activity.totalXp += MathGrade2Type.COLUMN_METHOD_ADDITION.xp
            }

            activity.handleCorrectAnswer()
            isIncorrectAttempt = false
            checkBtn.text = if (isFinished) getString(R.string.btn_finish) else getString(R.string.btn_continue)
            showCorrectState(stateContainer, circleState)
        } else {
            playSound(R.raw.wrong)
            boxCarry.setImageResource(R.drawable.answer_incorrect_box)
            boxEmpty.setImageResource(R.drawable.answer_incorrect_box)
            isIncorrectAttempt = true
            checkBtn.text = getString(R.string.btn_try_again)
            showIncorrectState(stateContainer, circleState)
            setupSeeSolution(stateContainer, circleState)
        }
        isFirstAttempt = false
    }

    private fun setupCheckButton() {
        checkBtn.setOnClickListener {
            val activity = requireActivity() as Math2GradeQuestionActivity
            val stateContainer = activity.findViewById<FrameLayout>(R.id.stateContainer)
            val circleState = activity.findViewById<ImageView>(R.id.circleState)

            if (!isAnswerChecked) {
                checkAnswer(stateContainer, circleState)
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

    private fun setupSeeSolution(stateContainer: FrameLayout, circleState: ImageView) {
        seeEnabledButton.setOnClickListener {
            isSolutionShown = true
            seeBtn.visibility = View.GONE
            stateAnswer.text = getString(R.string.state_solution)
            val solutionText = "$fullNum1 + $fullNum2 = ${fullNum1 + fullNum2}"
            answer.text = getString(R.string.label_answer, solutionText)
            answer.visibility = View.VISIBLE
            checkBtn.text = getString(R.string.btn_continue)

            boxCarry.setImageResource(R.drawable.answer_solution_box)
            boxEmpty.setImageResource(R.drawable.answer_solution_box)
            tvCarryInput.text = correctCarry.toString()
            tvSecondNumInput.text = correctSecondDigit.toString()

            circleState.setImageResource(R.drawable.solution_lamp_icon)

            isIncorrectAttempt = false
            isFirstAttempt = false
            applyButtonColors(R.color.black_3, R.color.black_2, R.color.gray_2)
            stateContainer.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.gray_2))
        }
    }

    private fun resetForTryAgain() {
        val activity = requireActivity() as Math2GradeQuestionActivity
        activity.isResultCurrentlyVisible = false
        val stateContainer = activity.findViewById<FrameLayout>(R.id.stateContainer)

        isAnswerChecked = false
        isIncorrectAttempt = false
        tvCarryInput.text = ""
        tvSecondNumInput.text = ""
        boxCarry.setImageResource(R.drawable.answer_blue_box)
        boxEmpty.setImageResource(R.drawable.answer_blue_box)

        stateContainer.visibility = View.INVISIBLE
        seeBtn.visibility = View.GONE
        setFocus(tvCarryInput, boxCarry, cursorCarry)
        checkBtn.text = getString(R.string.btn_check)
        disableCheckButton()
        setupInitialButtonState()
    }

    private fun showCorrectState(stateContainer: FrameLayout, circleState: ImageView) {
        stateContainer.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.green_3))
        circleState.setImageResource(R.drawable.correct_tick_icon)
        stateAnswer.text = getString(R.string.state_correct)
        val solutionText = "$fullNum1 + $fullNum2 = ${fullNum1 + fullNum2}"
        answer.text = getString(R.string.label_answer, solutionText)
        answer.visibility = View.VISIBLE
        applyButtonColors(R.color.green_1, R.color.green_2, R.color.green_4)
    }

    private fun showIncorrectState(stateContainer: FrameLayout, circleState: ImageView) {
        stateContainer.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.red_4))
        circleState.setImageResource(R.drawable.wrong_circle)
        stateAnswer.text = getString(R.string.state_incorrect)
        answer.visibility = View.GONE
        seeBtn.visibility = View.VISIBLE
        seeEnabledButton.text = getString(R.string.btn_see_solution)
        applyButtonColors(R.color.red_1, R.color.red_2, R.color.red_4)
    }

    private fun resetUIForNext() {
        val activity = requireActivity() as Math2GradeQuestionActivity
        activity.isResultCurrentlyVisible = false
        activity.hideSuccessAnimation()
        activity.findViewById<FrameLayout>(R.id.stateContainer).visibility = View.INVISIBLE
        seeBtn.visibility = View.GONE
        checkBtn.text = getString(R.string.btn_check)
        setupInitialButtonState()
    }

    private fun setupInitialButtonState() {
        btnBack.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.white))
        checkBtn.backgroundTintList = ContextCompat.getColorStateList(requireContext(), R.color.blue_2)
        checkBtnBack.backgroundTintList = ContextCompat.getColorStateList(requireContext(), R.color.blue_1)
    }

    private fun enableCheckButton() {
        checkBtn.isEnabled = true
        val activity = requireActivity() as Math2GradeQuestionActivity
        activity.findViewById<FrameLayout>(R.id.check_enabled_btn_container).visibility = View.VISIBLE
        checkBtnBack.visibility = View.VISIBLE
        activity.findViewById<FrameLayout>(R.id.check_disabled_btn_container).visibility = View.INVISIBLE
    }

    private fun disableCheckButton() {
        checkBtn.isEnabled = false
        val activity = requireActivity() as Math2GradeQuestionActivity
        activity.findViewById<FrameLayout>(R.id.check_enabled_btn_container).visibility = View.INVISIBLE
        checkBtnBack.visibility = View.INVISIBLE
        activity.findViewById<FrameLayout>(R.id.check_disabled_btn_container).visibility = View.VISIBLE
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