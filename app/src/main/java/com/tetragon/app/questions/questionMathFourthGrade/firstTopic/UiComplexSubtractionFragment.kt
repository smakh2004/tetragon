package com.tetragon.app.questions.questionMathFourthGrade.firstTopic

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.media.MediaPlayer
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.tetragon.app.R
import com.tetragon.app.questions.questionMathFourthGrade.Math4GradeQuestionActivity
import com.tetragon.app.questions.questionMathFourthGrade.MathGrade4Type
import kotlin.random.Random

class UiComplexSubtractionFragment : Fragment(R.layout.fragment_ui_complex_subtraction) {

    // Display Numbers
    private lateinit var tvTopHundredSub: TextView
    private lateinit var tvTopOnesSub: TextView
    private lateinit var tvBottomThousandSub: TextView
    private lateinit var tvBottomTensSub: TextView
    private lateinit var tvResThousandSub: TextView
    private lateinit var tvResHundredSub: TextView
    private lateinit var tvResTensSub: TextView
    private lateinit var tvResOnesSub: TextView

    // Input Texts
    private lateinit var tvInputThousandsSubFirstText: TextView
    private lateinit var tvInputTensSubFirstText: TextView
    private lateinit var tvInputHundredsSubSecondText: TextView
    private lateinit var tvBottomOnesSubInputText: TextView

    // Input Containers
    private lateinit var frameInputThousandsSubFirst: FrameLayout
    private lateinit var frameInputTensSubFirst: FrameLayout
    private lateinit var frameInputHundredsSubSecond: FrameLayout
    private lateinit var frameOnesSubSecond: FrameLayout

    // Visual Feedback
    private lateinit var boxThousSubFirst: ImageView
    private lateinit var boxTensSubFirst: ImageView
    private lateinit var boxHundSubSecond: ImageView
    private lateinit var boxOnesSubSecond: ImageView

    private lateinit var cursorThousSubFirst: View
    private lateinit var cursorTensSubFirst: View
    private lateinit var cursorHundSubSecond: View
    private lateinit var cursorOnesSubSecond: View

    // Shared UI
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
    private var targetDiff = 0

    private var correctThousandsFirst = 0
    private var correctTensFirst = 0
    private var correctHundredsSecond = 0
    private var correctOnesSecond = 0

    private var isAnswerChecked = false
    private var isIncorrectAttempt = false
    private var isFirstAttempt = true
    private var mediaPlayer: MediaPlayer? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val activity = requireActivity() as Math4GradeQuestionActivity

        initViews(view, activity)
        setupFocusLogic()
        setupKeyboard(view)
        generateProblem()
        setupCheckButton()
    }

    private fun initViews(view: View, activity: Math4GradeQuestionActivity) {
        tvTopHundredSub = view.findViewById(R.id.tvTopHundredSub)
        tvTopOnesSub = view.findViewById(R.id.tvTopOnesSub)
        tvBottomThousandSub = view.findViewById(R.id.tvBottomThousandSub)
        tvBottomTensSub = view.findViewById(R.id.tvBottomTensSub)
        tvResThousandSub = view.findViewById(R.id.tvResThousandSub)
        tvResHundredSub = view.findViewById(R.id.tvResHundredSub)
        tvResTensSub = view.findViewById(R.id.tvResTensSub)
        tvResOnesSub = view.findViewById(R.id.tvResOnesSub)

        tvInputThousandsSubFirstText = view.findViewById(R.id.tvInputThousandsSubFirstText)
        tvInputTensSubFirstText = view.findViewById(R.id.tvInputTensSubFirstText)
        tvInputHundredsSubSecondText = view.findViewById(R.id.tvInputHundredsSubSecondText)
        tvBottomOnesSubInputText = view.findViewById(R.id.tvBottomOnesSubInputText)

        frameInputThousandsSubFirst = view.findViewById(R.id.frameInputThousandsSubFirst)
        frameInputTensSubFirst = view.findViewById(R.id.frameInputTensSubFirst)
        frameInputHundredsSubSecond = view.findViewById(R.id.frameInputHundredsSubSecond)
        frameOnesSubSecond = view.findViewById(R.id.frameOnesSubSecond)

        boxThousSubFirst = view.findViewById(R.id.boxThousSubFirst)
        boxTensSubFirst = view.findViewById(R.id.boxTensSubFirst)
        boxHundSubSecond = view.findViewById(R.id.boxHundSubSecond)
        boxOnesSubSecond = view.findViewById(R.id.boxOnesSubSecond)

        cursorThousSubFirst = view.findViewById(R.id.cursorThousSubFirst)
        cursorTensSubFirst = view.findViewById(R.id.cursorTensSubFirst)
        cursorHundSubSecond = view.findViewById(R.id.cursorHundSubSecond)
        cursorOnesSubSecond = view.findViewById(R.id.cursorOnesSubSecond)

        checkBtn = activity.findViewById(R.id.check_enabled_btn)
        checkBtnBack = activity.findViewById(R.id.check_enabled_button_background)
        btnBack = activity.findViewById(R.id.btnBackground)
        seeBtn = activity.findViewById(R.id.see_btn_container)
        seeEnabledButton = activity.findViewById(R.id.see_enabled_btn)
        stateAnswer = activity.findViewById(R.id.stateAnswer)
        answer = activity.findViewById(R.id.answer)
    }

    private fun setupFocusLogic() {
        setFocus(tvInputThousandsSubFirstText, boxThousSubFirst, cursorThousSubFirst)
        frameInputThousandsSubFirst.setOnClickListener { setFocus(tvInputThousandsSubFirstText, boxThousSubFirst, cursorThousSubFirst) }
        frameInputTensSubFirst.setOnClickListener { setFocus(tvInputTensSubFirstText, boxTensSubFirst, cursorTensSubFirst) }
        frameInputHundredsSubSecond.setOnClickListener { setFocus(tvInputHundredsSubSecondText, boxHundSubSecond, cursorHundSubSecond) }
        frameOnesSubSecond.setOnClickListener { setFocus(tvBottomOnesSubInputText, boxOnesSubSecond, cursorOnesSubSecond) }
    }

    private fun setFocus(targetTextView: TextView, targetBox: ImageView, targetCursor: View) {
        if (isAnswerChecked) return
        listOf(boxThousSubFirst, boxTensSubFirst, boxHundSubSecond, boxOnesSubSecond).forEach { it.setImageResource(R.drawable.answer_default_box) }
        cursorAnimator?.cancel()
        listOf(cursorThousSubFirst, cursorTensSubFirst, cursorHundSubSecond, cursorOnesSubSecond).forEach { it.visibility = View.GONE }

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
        val isReady = tvInputThousandsSubFirstText.text.isNotEmpty() &&
                tvInputTensSubFirstText.text.isNotEmpty() &&
                tvInputHundredsSubSecondText.text.isNotEmpty() &&
                tvBottomOnesSubInputText.text.isNotEmpty()
        if (isReady) enableCheckButton() else disableCheckButton()
    }

    private fun generateProblem() {
        val activity = requireActivity() as Math4GradeQuestionActivity
        activity.findViewById<FrameLayout>(R.id.stateContainer).visibility = View.GONE

        fullNum1 = Random.nextInt(2000, 9999)
        fullNum2 = Random.nextInt(1000, fullNum1 - 500)
        targetDiff = fullNum1 - fullNum2

        correctThousandsFirst = fullNum1 / 1000
        correctTensFirst = (fullNum1 / 10) % 10
        correctHundredsSecond = (fullNum2 / 100) % 10
        correctOnesSecond = fullNum2 % 10

        tvTopHundredSub.text = ((fullNum1 / 100) % 10).toString()
        tvTopOnesSub.text = (fullNum1 % 10).toString()
        tvBottomThousandSub.text = (fullNum2 / 1000).toString()
        tvBottomTensSub.text = ((fullNum2 / 10) % 10).toString()

        tvResThousandSub.text = (targetDiff / 1000).toString()
        tvResHundredSub.text = ((targetDiff / 100) % 10).toString()
        tvResTensSub.text = ((targetDiff / 10) % 10).toString()
        tvResOnesSub.text = (targetDiff % 10).toString()

        tvInputThousandsSubFirstText.text = ""
        tvInputTensSubFirstText.text = ""
        tvInputHundredsSubSecondText.text = ""
        tvBottomOnesSubInputText.text = ""

        isAnswerChecked = false
        isIncorrectAttempt = false
        isFirstAttempt = true
        seeBtn.visibility = View.GONE

        setFocus(tvInputThousandsSubFirstText, boxThousSubFirst, cursorThousSubFirst)
        checkBtn.text = getString(R.string.btn_check)
        disableCheckButton()
        setupInitialButtonState()
    }

    private fun checkAnswer(stateContainer: FrameLayout, circleState: ImageView) {
        cursorAnimator?.cancel()
        activeCursor?.visibility = View.GONE
        isAnswerChecked = true

        val activity = requireActivity() as Math4GradeQuestionActivity
        activity.isResultCurrentlyVisible = true

        val u1 = tvInputThousandsSubFirstText.text.toString().toIntOrNull() ?: -1
        val u2 = tvInputTensSubFirstText.text.toString().toIntOrNull() ?: -1
        val u3 = tvInputHundredsSubSecondText.text.toString().toIntOrNull() ?: -1
        val u4 = tvBottomOnesSubInputText.text.toString().toIntOrNull() ?: -1

        stateContainer.visibility = View.VISIBLE

        if (u1 == correctThousandsFirst && u2 == correctTensFirst && u3 == correctHundredsSecond && u4 == correctOnesSecond) {
            playSound(R.raw.correct)
            activity.playSuccessAnimation()
            setAllBoxes(R.drawable.answer_correct_box)
            val isFinished = activity.incrementProgress()
            if (isFirstAttempt) activity.totalXp += MathGrade4Type.SUBTRACTION.xp
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
        boxThousSubFirst.setImageResource(resId)
        boxTensSubFirst.setImageResource(resId)
        boxHundSubSecond.setImageResource(resId)
        boxOnesSubSecond.setImageResource(resId)
    }

    private fun setupCheckButton() {
        checkBtn.setOnClickListener {
            val activity = requireActivity() as Math4GradeQuestionActivity
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
            seeBtn.visibility = View.GONE
            stateAnswer.text = getString(R.string.state_solution)
            answer.text = getString(R.string.label_answer_complex_subtraction, fullNum1, fullNum2, targetDiff)
            answer.visibility = View.VISIBLE
            checkBtn.text = getString(R.string.btn_continue)
            setAllBoxes(R.drawable.answer_solution_box)
            tvInputThousandsSubFirstText.text = correctThousandsFirst.toString()
            tvInputTensSubFirstText.text = correctTensFirst.toString()
            tvInputHundredsSubSecondText.text = correctHundredsSecond.toString()
            tvBottomOnesSubInputText.text = correctOnesSecond.toString()
            circleState.setImageResource(R.drawable.solution_lamp_icon)
            isIncorrectAttempt = false
            applyButtonColors(R.color.black_3, R.color.black_2, R.color.gray_2)
            stateContainer.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.gray_2))
        }
    }

    private fun resetForTryAgain() {
        val activity = requireActivity() as Math4GradeQuestionActivity
        activity.isResultCurrentlyVisible = false
        activity.findViewById<FrameLayout>(R.id.stateContainer).visibility = View.GONE
        isAnswerChecked = false
        isIncorrectAttempt = false
        tvInputThousandsSubFirstText.text = ""
        tvInputTensSubFirstText.text = ""
        tvInputHundredsSubSecondText.text = ""
        tvBottomOnesSubInputText.text = ""
        setAllBoxes(R.drawable.answer_default_box)
        seeBtn.visibility = View.GONE
        setFocus(tvInputThousandsSubFirstText, boxThousSubFirst, cursorThousSubFirst)
        checkBtn.text = getString(R.string.btn_check)
        disableCheckButton()
        setupInitialButtonState()
    }

    private fun showCorrectState(stateContainer: FrameLayout, circleState: ImageView) {
        stateContainer.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.green_3))
        circleState.setImageResource(R.drawable.correct_tick_icon)
        stateAnswer.text = getString(R.string.state_correct)
        answer.text = getString(R.string.label_answer_complex_subtraction, fullNum1, fullNum2, targetDiff)
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
        val activity = requireActivity() as Math4GradeQuestionActivity
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
        val activity = requireActivity() as Math4GradeQuestionActivity
        activity.findViewById<FrameLayout>(R.id.check_enabled_btn_container).visibility = View.VISIBLE
        checkBtnBack.visibility = View.VISIBLE
        activity.findViewById<FrameLayout>(R.id.check_disabled_btn_container).visibility = View.INVISIBLE
    }

    private fun disableCheckButton() {
        checkBtn.isEnabled = false
        val activity = requireActivity() as Math4GradeQuestionActivity
        activity.findViewById<FrameLayout>(R.id.check_enabled_btn_container).visibility = View.INVISIBLE
        checkBtnBack.visibility = View.INVISIBLE
        activity.findViewById<FrameLayout>(R.id.check_disabled_btn_container).visibility = View.VISIBLE
    }

    private fun applyButtonColors(btnC: Int, backC: Int, bgC: Int) {
        checkBtn.backgroundTintList = ContextCompat.getColorStateList(requireContext(), btnC)
        checkBtnBack.backgroundTintList = ContextCompat.getColorStateList(requireContext(), backC)
        btnBack.setBackgroundColor(ContextCompat.getColor(requireContext(), bgC))
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