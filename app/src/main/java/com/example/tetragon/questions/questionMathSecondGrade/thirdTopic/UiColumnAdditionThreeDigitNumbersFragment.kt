package com.example.tetragon.questions.questionMathSecondGrade.thirdTopic

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

class UiColumnAdditionThreeDigitNumbersFragment : Fragment(R.layout.fragment_ui_column_addition_three_digit_numbers) {

    private lateinit var tvTop1: TextView
    private lateinit var tvTop3: TextView
    private lateinit var tvBottom2: TextView
    private lateinit var tvRes1: TextView
    private lateinit var tvRes2: TextView
    private lateinit var tvRes3: TextView

    private lateinit var tvInputTensFirstNumText: TextView
    private lateinit var tvInputHundredsSecondNumText: TextView
    private lateinit var tvBottomInputText: TextView

    private lateinit var frameInputTensFirstNum: FrameLayout
    private lateinit var frameInputHundredsSecondNum: FrameLayout
    private lateinit var frameBottomInput: FrameLayout

    private lateinit var boxTensFirst: ImageView
    private lateinit var boxHundredsSecond: ImageView
    private lateinit var boxOnesSecond: ImageView

    private lateinit var cursorTensFirst: View
    private lateinit var cursorHundredsSecond: View
    private lateinit var cursorOnesSecond: View

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
    private var targetSum = 0

    private var correctTensFirst = 0
    private var correctHundredsSecond = 0
    private var correctOnesSecond = 0

    private var isAnswerChecked = false
    private var isIncorrectAttempt = false
    private var isFirstAttempt = true
    private var isSolutionShown = false
    private var mediaPlayer: MediaPlayer? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val activity = requireActivity() as Math2GradeQuestionActivity

        // Initialize UI
        tvTop1 = view.findViewById(R.id.tvTop1)
        tvTop3 = view.findViewById(R.id.tvTop3)
        tvBottom2 = view.findViewById(R.id.tvBottom2)
        tvRes1 = view.findViewById(R.id.tvRes1)
        tvRes2 = view.findViewById(R.id.tvRes2)
        tvRes3 = view.findViewById(R.id.tvRes3)

        tvInputTensFirstNumText = view.findViewById(R.id.tvInputTensFirstNumText)
        tvInputHundredsSecondNumText = view.findViewById(R.id.tvInputHundredsSecondNumText)
        tvBottomInputText = view.findViewById(R.id.tvBottomInputText)

        frameInputTensFirstNum = view.findViewById(R.id.frameInputTensFirstNum)
        frameInputHundredsSecondNum = view.findViewById(R.id.frameInputHundredsSecondNum)
        frameBottomInput = view.findViewById(R.id.frameBottomInput)

        boxTensFirst = view.findViewById(R.id.boxTensFirst)
        boxHundredsSecond = view.findViewById(R.id.boxHundredsSecond)
        boxOnesSecond = view.findViewById(R.id.boxOnesSecond)

        cursorTensFirst = view.findViewById(R.id.cursorTensFirst)
        cursorHundredsSecond = view.findViewById(R.id.cursorHundredsSecond)
        cursorOnesSecond = view.findViewById(R.id.cursorOnesSecond)

        checkBtn = activity.findViewById(R.id.check_enabled_btn)
        checkBtnBack = activity.findViewById(R.id.check_enabled_button_background)
        btnBack = activity.findViewById(R.id.btnBackground)
        seeBtn = activity.findViewById(R.id.see_btn_container)
        seeEnabledButton = activity.findViewById(R.id.see_enabled_btn)
        stateAnswer = activity.findViewById(R.id.stateAnswer)
        answer = activity.findViewById(R.id.answer)

        setupFocusLogic()
        setupKeyboard(view)
        generateProblem()
        setupCheckButton()
    }

    private fun setupFocusLogic() {
        setFocus(tvInputTensFirstNumText, boxTensFirst, cursorTensFirst)
        frameInputTensFirstNum.setOnClickListener { setFocus(tvInputTensFirstNumText, boxTensFirst, cursorTensFirst) }
        frameInputHundredsSecondNum.setOnClickListener { setFocus(tvInputHundredsSecondNumText, boxHundredsSecond, cursorHundredsSecond) }
        frameBottomInput.setOnClickListener { setFocus(tvBottomInputText, boxOnesSecond, cursorOnesSecond) }
    }

    private fun setFocus(targetTextView: TextView, targetBox: ImageView, targetCursor: View) {
        if (isAnswerChecked) return

        boxTensFirst.setImageResource(R.drawable.answer_default_box)
        boxHundredsSecond.setImageResource(R.drawable.answer_default_box)
        boxOnesSecond.setImageResource(R.drawable.answer_default_box)

        cursorAnimator?.cancel()
        cursorTensFirst.visibility = View.GONE
        cursorHundredsSecond.visibility = View.GONE
        cursorOnesSecond.visibility = View.GONE

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
        val isReady = tvInputTensFirstNumText.text.isNotEmpty() &&
                tvInputHundredsSecondNumText.text.isNotEmpty() &&
                tvBottomInputText.text.isNotEmpty()
        if (isReady) enableCheckButton() else disableCheckButton()
    }

    private fun generateProblem() {
        val activity = requireActivity() as Math2GradeQuestionActivity
        activity.findViewById<FrameLayout>(R.id.stateContainer).visibility = View.GONE

        targetSum = Random.nextInt(200, 999)
        fullNum1 = Random.nextInt(100, targetSum - 100)
        fullNum2 = targetSum - fullNum1

        correctTensFirst = (fullNum1 / 10) % 10
        correctHundredsSecond = fullNum2 / 100
        correctOnesSecond = fullNum2 % 10

        tvTop1.text = (fullNum1 / 100).toString()
        tvTop3.text = (fullNum1 % 10).toString()
        tvBottom2.text = ((fullNum2 / 10) % 10).toString()

        tvRes1.text = (targetSum / 100).toString()
        tvRes2.text = ((targetSum / 10) % 10).toString()
        tvRes3.text = (targetSum % 10).toString()

        tvInputTensFirstNumText.text = ""
        tvInputHundredsSecondNumText.text = ""
        tvBottomInputText.text = ""

        isAnswerChecked = false
        isIncorrectAttempt = false
        isFirstAttempt = true
        isSolutionShown = false
        seeBtn.visibility = View.GONE

        setFocus(tvInputTensFirstNumText, boxTensFirst, cursorTensFirst)
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

        val uTens1 = tvInputTensFirstNumText.text.toString().toIntOrNull() ?: -1
        val uHund2 = tvInputHundredsSecondNumText.text.toString().toIntOrNull() ?: -1
        val uOnes2 = tvBottomInputText.text.toString().toIntOrNull() ?: -1

        stateContainer.visibility = View.VISIBLE

        if (uTens1 == correctTensFirst && uHund2 == correctHundredsSecond && uOnes2 == correctOnesSecond) {
            playSound(R.raw.correct)
            activity.isCorrectAnswerShowing = true
            activity.playSuccessAnimation()

            boxTensFirst.setImageResource(R.drawable.answer_correct_box)
            boxHundredsSecond.setImageResource(R.drawable.answer_correct_box)
            boxOnesSecond.setImageResource(R.drawable.answer_correct_box)

            val isFinished = activity.incrementProgress()
            if (isFirstAttempt) {
                activity.totalXp += MathGrade2Type.COLUMN_METHOD_ADDITION_THREE_DIGITS.xp
            }
            activity.handleCorrectAnswer()
            isIncorrectAttempt = false
            checkBtn.text = if (isFinished) getString(R.string.btn_finish) else getString(R.string.btn_continue)
            showCorrectState(stateContainer, circleState)
        } else {
            playSound(R.raw.wrong)
            boxTensFirst.setImageResource(R.drawable.answer_incorrect_box)
            boxHundredsSecond.setImageResource(R.drawable.answer_incorrect_box)
            boxOnesSecond.setImageResource(R.drawable.answer_incorrect_box)

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
            val solutionText = "$fullNum1 + $fullNum2 = $targetSum"
            answer.text = getString(R.string.label_answer, solutionText)
            answer.visibility = View.VISIBLE
            checkBtn.text = getString(R.string.btn_continue)

            boxTensFirst.setImageResource(R.drawable.answer_solution_box)
            boxHundredsSecond.setImageResource(R.drawable.answer_solution_box)
            boxOnesSecond.setImageResource(R.drawable.answer_solution_box)

            tvInputTensFirstNumText.text = correctTensFirst.toString()
            tvInputHundredsSecondNumText.text = correctHundredsSecond.toString()
            tvBottomInputText.text = correctOnesSecond.toString()

            circleState.setImageResource(R.drawable.solution_lamp_icon)
            isIncorrectAttempt = false
            applyButtonColors(R.color.black_3, R.color.black_2, R.color.gray_2)
            stateContainer.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.gray_2))
        }
    }

    private fun resetForTryAgain() {
        val activity = requireActivity() as Math2GradeQuestionActivity
        activity.isResultCurrentlyVisible = false
        activity.findViewById<FrameLayout>(R.id.stateContainer).visibility = View.GONE

        isAnswerChecked = false
        isIncorrectAttempt = false
        tvInputTensFirstNumText.text = ""
        tvInputHundredsSecondNumText.text = ""
        tvBottomInputText.text = ""

        boxTensFirst.setImageResource(R.drawable.answer_blue_box)
        boxHundredsSecond.setImageResource(R.drawable.answer_default_box)
        boxOnesSecond.setImageResource(R.drawable.answer_default_box)

        seeBtn.visibility = View.GONE
        setFocus(tvInputTensFirstNumText, boxTensFirst, cursorTensFirst)
        checkBtn.text = getString(R.string.btn_check)
        disableCheckButton()
        setupInitialButtonState()
    }

    private fun showCorrectState(stateContainer: FrameLayout, circleState: ImageView) {
        stateContainer.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.green_3))
        circleState.setImageResource(R.drawable.correct_tick_icon)
        stateAnswer.text = getString(R.string.state_correct)
        val solutionText = "$fullNum1 + $fullNum2 = $targetSum"
        answer.text = getString(R.string.label_answer, solutionText)
        answer.visibility = View.VISIBLE
        applyButtonColors(R.color.green_1, R.color.green_2, R.color.green_4)

        enableCheckButton()
    }

    private fun showIncorrectState(stateContainer: FrameLayout, circleState: ImageView) {
        stateContainer.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.red_4))
        circleState.setImageResource(R.drawable.wrong_circle)
        stateAnswer.text = getString(R.string.state_incorrect)
        answer.visibility = View.GONE
        seeBtn.visibility = View.VISIBLE
        seeEnabledButton.text = getString(R.string.btn_see_solution)
        applyButtonColors(R.color.red_1, R.color.red_2, R.color.red_4)

        enableCheckButton()
    }

    private fun resetUIForNext() {
        val activity = requireActivity() as Math2GradeQuestionActivity
        activity.isResultCurrentlyVisible = false
        activity.hideSuccessAnimation()
        activity.findViewById<FrameLayout>(R.id.stateContainer).visibility = View.GONE
        seeBtn.visibility = View.GONE
        setupInitialButtonState()
        checkBtn.text = getString(R.string.btn_check)

        disableCheckButton()
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