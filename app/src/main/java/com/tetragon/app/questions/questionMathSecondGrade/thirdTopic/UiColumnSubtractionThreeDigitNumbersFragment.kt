package com.tetragon.app.questions.questionMathSecondGrade.thirdTopic

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

class UiColumnSubtractionThreeDigitNumbersFragment : Fragment(R.layout.fragment_ui_column_subtraction_three_digit_numbers) {

    private lateinit var tvMinuendHundreds: TextView
    private lateinit var tvMinuendOnes: TextView
    private lateinit var tvSubtrahendTens: TextView
    private lateinit var tvResHundreds: TextView
    private lateinit var tvResTens: TextView
    private lateinit var tvResOnes: TextView

    private lateinit var tvInputMinuendTensText: TextView
    private lateinit var tvInputSubtrahendHundredsText: TextView
    private lateinit var tvInputSubtrahendOnesText: TextView

    private lateinit var frameInputMinuendTens: FrameLayout
    private lateinit var frameInputSubtrahendHundreds: FrameLayout
    private lateinit var frameInputSubtrahendOnes: FrameLayout

    private lateinit var boxMinuendTens: ImageView
    private lateinit var boxSubtrahendHundreds: ImageView
    private lateinit var boxSubtrahendOnes: ImageView

    private lateinit var cursorMinuendTens: View
    private lateinit var cursorSubtrahendHundreds: View
    private lateinit var cursorSubtrahendOnes: View

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

    private var minuend = 0
    private var subtrahend = 0
    private var difference = 0

    private var correctMinuendTens = 0
    private var correctSubtrahendHundreds = 0
    private var correctSubtrahendOnes = 0

    private var isAnswerChecked = false
    private var isIncorrectAttempt = false
    private var isFirstAttempt = true
    private var isSolutionShown = false
    private var mediaPlayer: MediaPlayer? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val activity = requireActivity() as Math2GradeQuestionActivity

        // Initialize UI using IDs from the Subtraction XML
        tvMinuendHundreds = view.findViewById(R.id.tvMinuendHundreds)
        tvMinuendOnes = view.findViewById(R.id.tvMinuendOnes)
        tvSubtrahendTens = view.findViewById(R.id.tvSubtrahendTens)
        tvResHundreds = view.findViewById(R.id.tvResHundreds)
        tvResTens = view.findViewById(R.id.tvResTens)
        tvResOnes = view.findViewById(R.id.tvResOnes)

        tvInputMinuendTensText = view.findViewById(R.id.tvInputMinuendTensText)
        tvInputSubtrahendHundredsText = view.findViewById(R.id.tvInputSubtrahendHundredsText)
        tvInputSubtrahendOnesText = view.findViewById(R.id.tvInputSubtrahendOnesText)

        frameInputMinuendTens = view.findViewById(R.id.frameInputMinuendTens)
        frameInputSubtrahendHundreds = view.findViewById(R.id.frameInputSubtrahendHundreds)
        frameInputSubtrahendOnes = view.findViewById(R.id.frameInputSubtrahendOnes)

        boxMinuendTens = view.findViewById(R.id.boxMinuendTens)
        boxSubtrahendHundreds = view.findViewById(R.id.boxSubtrahendHundreds)
        boxSubtrahendOnes = view.findViewById(R.id.boxSubtrahendOnes)

        cursorMinuendTens = view.findViewById(R.id.cursorMinuendTens)
        cursorSubtrahendHundreds = view.findViewById(R.id.cursorSubtrahendHundreds)
        cursorSubtrahendOnes = view.findViewById(R.id.cursorSubtrahendOnes)

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
        setFocus(tvInputMinuendTensText, boxMinuendTens, cursorMinuendTens)
        frameInputMinuendTens.setOnClickListener { setFocus(tvInputMinuendTensText, boxMinuendTens, cursorMinuendTens) }
        frameInputSubtrahendHundreds.setOnClickListener { setFocus(tvInputSubtrahendHundredsText, boxSubtrahendHundreds, cursorSubtrahendHundreds) }
        frameInputSubtrahendOnes.setOnClickListener { setFocus(tvInputSubtrahendOnesText, boxSubtrahendOnes, cursorSubtrahendOnes) }
    }

    private fun setFocus(targetTextView: TextView, targetBox: ImageView, targetCursor: View) {
        if (isAnswerChecked) return

        boxMinuendTens.setImageResource(R.drawable.answer_default_box)
        boxSubtrahendHundreds.setImageResource(R.drawable.answer_default_box)
        boxSubtrahendOnes.setImageResource(R.drawable.answer_default_box)

        cursorAnimator?.cancel()
        cursorMinuendTens.visibility = View.GONE
        cursorSubtrahendHundreds.visibility = View.GONE
        cursorSubtrahendOnes.visibility = View.GONE

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
        val isReady = tvInputMinuendTensText.text.isNotEmpty() &&
                tvInputSubtrahendHundredsText.text.isNotEmpty() &&
                tvInputSubtrahendOnesText.text.isNotEmpty()
        if (isReady) enableCheckButton() else disableCheckButton()
    }

    private fun generateProblem() {
        val activity = requireActivity() as Math2GradeQuestionActivity
        activity.findViewById<FrameLayout>(R.id.stateContainer).visibility = View.GONE

        minuend = Random.nextInt(200, 999)
        subtrahend = Random.nextInt(100, minuend - 50)
        difference = minuend - subtrahend

        correctMinuendTens = (minuend / 10) % 10
        correctSubtrahendHundreds = subtrahend / 100
        correctSubtrahendOnes = subtrahend % 10

        tvMinuendHundreds.text = (minuend / 100).toString()
        tvMinuendOnes.text = (minuend % 10).toString()
        tvSubtrahendTens.text = ((subtrahend / 10) % 10).toString()

        tvResHundreds.text = (difference / 100).toString()
        tvResTens.text = ((difference / 10) % 10).toString()
        tvResOnes.text = (difference % 10).toString()

        tvInputMinuendTensText.text = ""
        tvInputSubtrahendHundredsText.text = ""
        tvInputSubtrahendOnesText.text = ""

        isAnswerChecked = false
        isIncorrectAttempt = false
        isFirstAttempt = true
        isSolutionShown = false
        seeBtn.visibility = View.GONE

        setFocus(tvInputMinuendTensText, boxMinuendTens, cursorMinuendTens)
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

        val uTens1 = tvInputMinuendTensText.text.toString().toIntOrNull() ?: -1
        val uHund2 = tvInputSubtrahendHundredsText.text.toString().toIntOrNull() ?: -1
        val uOnes2 = tvInputSubtrahendOnesText.text.toString().toIntOrNull() ?: -1

        stateContainer.visibility = View.VISIBLE

        if (uTens1 == correctMinuendTens && uHund2 == correctSubtrahendHundreds && uOnes2 == correctSubtrahendOnes) {
            playSound(R.raw.correct)
            activity.isCorrectAnswerShowing = true
            activity.playSuccessAnimation()

            boxMinuendTens.setImageResource(R.drawable.answer_correct_box)
            boxSubtrahendHundreds.setImageResource(R.drawable.answer_correct_box)
            boxSubtrahendOnes.setImageResource(R.drawable.answer_correct_box)

            val isFinished = activity.incrementProgress()
            if (isFirstAttempt) {
                activity.totalXp += MathGrade2Type.COLUMN_METHOD_SUBTRACTION_THREE_DIGITS.xp
            }
            activity.handleCorrectAnswer()
            isIncorrectAttempt = false
            checkBtn.text = if (isFinished) getString(R.string.btn_finish) else getString(R.string.btn_continue)
            showCorrectState(stateContainer, circleState)
        } else {
            playSound(R.raw.wrong)
            boxMinuendTens.setImageResource(R.drawable.answer_incorrect_box)
            boxSubtrahendHundreds.setImageResource(R.drawable.answer_incorrect_box)
            boxSubtrahendOnes.setImageResource(R.drawable.answer_incorrect_box)

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
            val solutionText = "$minuend - $subtrahend = $difference"
            answer.text = getString(R.string.label_answer, solutionText)
            answer.visibility = View.VISIBLE
            checkBtn.text = getString(R.string.btn_continue)

            boxMinuendTens.setImageResource(R.drawable.answer_solution_box)
            boxSubtrahendHundreds.setImageResource(R.drawable.answer_solution_box)
            boxSubtrahendOnes.setImageResource(R.drawable.answer_solution_box)

            tvInputMinuendTensText.text = correctMinuendTens.toString()
            tvInputSubtrahendHundredsText.text = correctSubtrahendHundreds.toString()
            tvInputSubtrahendOnesText.text = correctSubtrahendOnes.toString()

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
        tvInputMinuendTensText.text = ""
        tvInputSubtrahendHundredsText.text = ""
        tvInputSubtrahendOnesText.text = ""

        boxMinuendTens.setImageResource(R.drawable.answer_blue_box)
        boxSubtrahendHundreds.setImageResource(R.drawable.answer_default_box)
        boxSubtrahendOnes.setImageResource(R.drawable.answer_default_box)

        seeBtn.visibility = View.GONE
        setFocus(tvInputMinuendTensText, boxMinuendTens, cursorMinuendTens)
        checkBtn.text = getString(R.string.btn_check)
        disableCheckButton()
        setupInitialButtonState()
    }

    private fun showCorrectState(stateContainer: FrameLayout, circleState: ImageView) {
        stateContainer.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.green_3))
        circleState.setImageResource(R.drawable.correct_tick_icon)
        stateAnswer.text = getString(R.string.state_correct)
        val solutionText = "$minuend - $subtrahend = $difference"
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