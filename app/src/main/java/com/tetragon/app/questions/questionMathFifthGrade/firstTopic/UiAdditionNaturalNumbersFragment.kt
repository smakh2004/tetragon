package com.tetragon.app.questions.questionMathFifthGrade.firstTopic

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.media.MediaPlayer
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.tetragon.app.R
import com.tetragon.app.questions.questionMathFifthGrade.Math5GradeQuestionActivity
import com.tetragon.app.questions.questionMathFifthGrade.MathGrade5Type
import kotlin.random.Random

class UiAdditionNaturalNumbersFragment : Fragment(R.layout.fragment_ui_addition_natural_numbers) {

    // Display Numbers
    private lateinit var tvTopHundred: TextView
    private lateinit var tvTopOnes: TextView
    private lateinit var tvBottomThousand: TextView
    private lateinit var tvBottomTens: TextView
    private lateinit var tvResThousand: TextView
    private lateinit var tvResHundred: TextView
    private lateinit var tvResTens: TextView
    private lateinit var tvResOnes: TextView

    // Input Texts
    private lateinit var tvInputThousandsFirstText: TextView
    private lateinit var tvInputTensFirstText: TextView
    private lateinit var tvInputHundredsSecondText: TextView
    private lateinit var tvBottomOnesInputText: TextView

    // Input Containers
    private lateinit var frameInputThousandsFirst: FrameLayout
    private lateinit var frameInputTensFirst: FrameLayout
    private lateinit var frameInputHundredsSecond: FrameLayout
    private lateinit var frameOnesSecond: FrameLayout

    // Visual Feedback (Boxes and Cursors)
    private lateinit var boxThousFirst: ImageView
    private lateinit var boxTensFirst: ImageView
    private lateinit var boxHundSecond: ImageView
    private lateinit var boxOnesSecond: ImageView

    private lateinit var cursorThousFirst: View
    private lateinit var cursorTensFirst: View
    private lateinit var cursorHundSecond: View
    private lateinit var cursorOnesSecond: View

    // Global UI components from Activity
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

    private var correctThousandsFirst = 0
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

        val activity = requireActivity() as Math5GradeQuestionActivity

        // Initialize Display Views
        tvTopHundred = view.findViewById(R.id.tvTopHundred)
        tvTopOnes = view.findViewById(R.id.tvTopOnes)
        tvBottomThousand = view.findViewById(R.id.tvBottomThousand)
        tvBottomTens = view.findViewById(R.id.tvBottomTens)
        tvResThousand = view.findViewById(R.id.tvResThousand)
        tvResHundred = view.findViewById(R.id.tvResHundred)
        tvResTens = view.findViewById(R.id.tvResTens)
        tvResOnes = view.findViewById(R.id.tvResOnes)

        // Initialize Inputs
        tvInputThousandsFirstText = view.findViewById(R.id.tvInputThousandsFirstText)
        tvInputTensFirstText = view.findViewById(R.id.tvInputTensFirstText)
        tvInputHundredsSecondText = view.findViewById(R.id.tvInputHundredsSecondText)
        tvBottomOnesInputText = view.findViewById(R.id.tvBottomOnesInputText)

        frameInputThousandsFirst = view.findViewById(R.id.frameInputThousandsFirst)
        frameInputTensFirst = view.findViewById(R.id.frameInputTensFirst)
        frameInputHundredsSecond = view.findViewById(R.id.frameInputHundredsSecond)
        frameOnesSecond = view.findViewById(R.id.frameOnesSecond)

        // Initialize Visuals
        boxThousFirst = view.findViewById(R.id.boxThousFirst)
        boxTensFirst = view.findViewById(R.id.boxTensFirst)
        boxHundSecond = view.findViewById(R.id.boxHundSecond)
        boxOnesSecond = view.findViewById(R.id.boxOnesSecond)

        cursorThousFirst = view.findViewById(R.id.cursorThousFirst)
        cursorTensFirst = view.findViewById(R.id.cursorTensFirst)
        cursorHundSecond = view.findViewById(R.id.cursorHundSecond)
        cursorOnesSecond = view.findViewById(R.id.cursorOnesSecond)

        // Activity UI from Math5GradeQuestionActivity
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
        setFocus(tvInputThousandsFirstText, boxThousFirst, cursorThousFirst)
        frameInputThousandsFirst.setOnClickListener { setFocus(tvInputThousandsFirstText, boxThousFirst, cursorThousFirst) }
        frameInputTensFirst.setOnClickListener { setFocus(tvInputTensFirstText, boxTensFirst, cursorTensFirst) }
        frameInputHundredsSecond.setOnClickListener { setFocus(tvInputHundredsSecondText, boxHundSecond, cursorHundSecond) }
        frameOnesSecond.setOnClickListener { setFocus(tvBottomOnesInputText, boxOnesSecond, cursorOnesSecond) }
    }

    private fun setFocus(targetTextView: TextView, targetBox: ImageView, targetCursor: View) {
        if (isAnswerChecked) return

        listOf(boxThousFirst, boxTensFirst, boxHundSecond, boxOnesSecond).forEach { it.setImageResource(R.drawable.answer_default_box) }
        cursorAnimator?.cancel()
        listOf(cursorThousFirst, cursorTensFirst, cursorHundSecond, cursorOnesSecond).forEach { it.visibility = View.GONE }

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
        val isReady = tvInputThousandsFirstText.text.isNotEmpty() &&
                tvInputTensFirstText.text.isNotEmpty() &&
                tvInputHundredsSecondText.text.isNotEmpty() &&
                tvBottomOnesInputText.text.isNotEmpty()
        if (isReady) enableCheckButton() else disableCheckButton()
    }

    private fun generateProblem() {
        val activity = requireActivity() as Math5GradeQuestionActivity
        activity.findViewById<FrameLayout>(R.id.stateContainer).visibility = View.GONE

        targetSum = Random.nextInt(2000, 9999)
        fullNum1 = Random.nextInt(1000, targetSum - 1000)
        fullNum2 = targetSum - fullNum1

        correctThousandsFirst = fullNum1 / 1000
        correctTensFirst = (fullNum1 / 10) % 10
        correctHundredsSecond = (fullNum2 / 100) % 10
        correctOnesSecond = fullNum2 % 10

        tvTopHundred.text = ((fullNum1 / 100) % 10).toString()
        tvTopOnes.text = (fullNum1 % 10).toString()
        tvBottomThousand.text = (fullNum2 / 1000).toString()
        tvBottomTens.text = ((fullNum2 / 10) % 10).toString()

        tvResThousand.text = (targetSum / 1000).toString()
        tvResHundred.text = ((targetSum / 100) % 10).toString()
        tvResTens.text = ((targetSum / 10) % 10).toString()
        tvResOnes.text = (targetSum % 10).toString()

        tvInputThousandsFirstText.text = ""
        tvInputTensFirstText.text = ""
        tvInputHundredsSecondText.text = ""
        tvBottomOnesInputText.text = ""

        isAnswerChecked = false
        isIncorrectAttempt = false
        isFirstAttempt = true
        isSolutionShown = false
        seeBtn.visibility = View.GONE

        setFocus(tvInputThousandsFirstText, boxThousFirst, cursorThousFirst)
        checkBtn.text = getString(R.string.btn_check)
        disableCheckButton()
        setupInitialButtonState()
    }

    private fun checkAnswer(stateContainer: FrameLayout, circleState: ImageView) {
        cursorAnimator?.cancel()
        activeCursor?.visibility = View.GONE
        isAnswerChecked = true

        val activity = requireActivity() as Math5GradeQuestionActivity
        activity.isResultCurrentlyVisible = true

        val u1 = tvInputThousandsFirstText.text.toString().toIntOrNull() ?: -1
        val u2 = tvInputTensFirstText.text.toString().toIntOrNull() ?: -1
        val u3 = tvInputHundredsSecondText.text.toString().toIntOrNull() ?: -1
        val u4 = tvBottomOnesInputText.text.toString().toIntOrNull() ?: -1

        stateContainer.visibility = View.VISIBLE

        if (u1 == correctThousandsFirst && u2 == correctTensFirst && u3 == correctHundredsSecond && u4 == correctOnesSecond) {
            playSound(R.raw.correct)
            activity.isCorrectAnswerShowing = true
            activity.playSuccessAnimation()

            setAllBoxes(R.drawable.answer_correct_box)

            val isFinished = activity.incrementProgress()
            if (isFirstAttempt) {
                activity.totalXp += MathGrade5Type.ADDITION_NATURAL_NUMBERS.xp
            }
            activity.handleCorrectAnswer()
            isIncorrectAttempt = false
            checkBtn.text = if (isFinished) getString(R.string.btn_finish) else getString(R.string.btn_continue)
            showCorrectState(stateContainer, circleState)
        } else {
            playSound(R.raw.wrong)
            setAllBoxes(R.drawable.answer_incorrect_box)
            isIncorrectAttempt = true
            checkBtn.text = getString(R.string.btn_try_again)
            showIncorrectState(stateContainer, circleState)
            setupSeeSolution(stateContainer, circleState)
        }
        isFirstAttempt = false
    }

    private fun setAllBoxes(resId: Int) {
        boxThousFirst.setImageResource(resId)
        boxTensFirst.setImageResource(resId)
        boxHundSecond.setImageResource(resId)
        boxOnesSecond.setImageResource(resId)
    }

    private fun setupCheckButton() {
        checkBtn.setOnClickListener {
            val activity = requireActivity() as Math5GradeQuestionActivity
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
            val fullEquation = "$fullNum1 + $fullNum2 = $targetSum"
            answer.text = getString(R.string.label_answer, fullEquation)
            answer.visibility = View.VISIBLE
            checkBtn.text = getString(R.string.btn_continue)

            setAllBoxes(R.drawable.answer_solution_box)

            tvInputThousandsFirstText.text = correctThousandsFirst.toString()
            tvInputTensFirstText.text = correctTensFirst.toString()
            tvInputHundredsSecondText.text = correctHundredsSecond.toString()
            tvBottomOnesInputText.text = correctOnesSecond.toString()

            circleState.setImageResource(R.drawable.solution_lamp_icon)
            isIncorrectAttempt = false
            applyButtonColors(R.color.black_3, R.color.black_2, R.color.gray_2)
            stateContainer.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.gray_2))
        }
    }

    private fun resetForTryAgain() {
        val activity = requireActivity() as Math5GradeQuestionActivity
        activity.isResultCurrentlyVisible = false
        activity.findViewById<FrameLayout>(R.id.stateContainer).visibility = View.GONE

        isAnswerChecked = false
        isIncorrectAttempt = false
        tvInputThousandsFirstText.text = ""
        tvInputTensFirstText.text = ""
        tvInputHundredsSecondText.text = ""
        tvBottomOnesInputText.text = ""

        setAllBoxes(R.drawable.answer_default_box)
        seeBtn.visibility = View.GONE
        setFocus(tvInputThousandsFirstText, boxThousFirst, cursorThousFirst)
        checkBtn.text = getString(R.string.btn_check)
        disableCheckButton()
        setupInitialButtonState()
    }

    private fun showCorrectState(stateContainer: FrameLayout, circleState: ImageView) {
        stateContainer.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.green_3))
        circleState.setImageResource(R.drawable.correct_tick_icon)
        stateAnswer.text = getString(R.string.state_correct)
        val fullEquation = "$fullNum1 + $fullNum2 = $targetSum"
        answer.text = getString(R.string.label_answer, fullEquation)
        answer.visibility = View.VISIBLE
        applyButtonColors(R.color.green_1, R.color.green_2, R.color.green_4)
        enableCheckButton()
    }

    private fun showIncorrectState(stateContainer: FrameLayout, circleState: ImageView) {
        stateContainer.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.red_4))
        circleState.setImageResource(R.drawable.wrong_circle)
        stateAnswer.text = getString(R.string.state_incorrect)
        seeEnabledButton.text = getString(R.string.btn_see_solution)
        answer.visibility = View.GONE
        seeBtn.visibility = View.VISIBLE
        applyButtonColors(R.color.red_1, R.color.red_2, R.color.red_4)
        enableCheckButton()
    }

    private fun resetUIForNext() {
        val activity = requireActivity() as Math5GradeQuestionActivity
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
        val activity = requireActivity() as Math5GradeQuestionActivity
        activity.findViewById<FrameLayout>(R.id.check_enabled_btn_container).visibility = View.VISIBLE
        checkBtnBack.visibility = View.VISIBLE
        activity.findViewById<FrameLayout>(R.id.check_disabled_btn_container).visibility = View.INVISIBLE
    }

    private fun disableCheckButton() {
        checkBtn.isEnabled = false
        val activity = requireActivity() as Math5GradeQuestionActivity
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