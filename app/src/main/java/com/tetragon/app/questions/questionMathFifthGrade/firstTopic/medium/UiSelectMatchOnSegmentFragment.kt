package com.tetragon.app.questions.questionMathFifthGrade.firstTopic.medium

import android.media.MediaPlayer
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.view.View
import android.widget.*
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import app.rive.runtime.kotlin.RiveAnimationView
import app.rive.runtime.kotlin.core.SMINumber
import app.rive.runtime.kotlin.core.ViewModelNumberProperty
import com.tetragon.app.R
import com.tetragon.app.questions.questionMathFifthGrade.Math5GradeQuestionActivity
import com.tetragon.app.questions.questionMathFifthGrade.MathGrade5Type

class UiSelectMatchOnSegmentFragment : Fragment(R.layout.fragment_ui_select_match_on_segment) {

    private lateinit var questionText: TextView
    private lateinit var riveAnimation: RiveAnimationView
    private lateinit var animationTouchBlocker: View

    private val viewModelKeys = arrayOf("-4", "-3", "-2", "-1", "0", "1", "2", "3", "4")
    private val optionProperties = mutableMapOf<String, ViewModelNumberProperty>()

    private lateinit var checkBtn: Button
    private lateinit var checkBtnBack: View
    private lateinit var btnBack: ConstraintLayout
    private lateinit var seeBtn: FrameLayout
    private lateinit var seeEnabledButton: Button
    private lateinit var stateAnswer: TextView
    private lateinit var answerDisplay: TextView
    private lateinit var stateContainer: FrameLayout
    private lateinit var circleState: ImageView

    private var targetValue: Int = 0
    private var isGreaterOrEqualType: Boolean = true

    private var isAnswerChecked = false
    private var isIncorrectAttempt = false
    private var isFirstAttempt = true
    private var isInitialized = false
    private var mediaPlayer: MediaPlayer? = null

    private val STATE_MACHINE = "State Machine 1"
    private val INPUT_CHOICE = "answer"

    private val mainHandler = Handler(Looper.getMainLooper())

    private val checkRunnable = object : Runnable {
        override fun run() {
            if (!isAdded || isAnswerChecked || !::riveAnimation.isInitialized) return
            val currentVal = getAnswerChoiceValue()

            if (currentVal != 0f && !checkBtn.isEnabled) {
                enableCheckButton()
            } else if (currentVal == 0f && checkBtn.isEnabled) {
                disableCheckButton()
            }
            mainHandler.postDelayed(this, 100)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initViews(view)

        if (!isInitialized) {
            riveAnimation.post {
                setupRiveViewModel()
                generateProblem()
                isInitialized = true
            }
        }
        mainHandler.post(checkRunnable)
    }

    private fun setupRiveViewModel() {
        try {
            val file = riveAnimation.controller.file ?: return
            val vm = file.getViewModelByName("ViewModel1") ?: return
            val vmi = vm.createDefaultInstance()
            riveAnimation.controller.stateMachines.firstOrNull()?.viewModelInstance = vmi

            viewModelKeys.forEach { key ->
                vmi.getNumberProperty(key)?.let { prop ->
                    optionProperties[key] = prop
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun generateProblem() {
        val activity = requireActivity() as Math5GradeQuestionActivity
        activity.isCorrectAnswerShowing = false // FIX: Prevents visual flag leakage into subsequent problems

        isGreaterOrEqualType = (0..1).random() == 1
        targetValue = (-3..3).random()

        viewModelKeys.forEach { key ->
            val offsetIndex = key.toInt()
            val valueToAssign = targetValue + offsetIndex
            optionProperties[key]?.value = valueToAssign.toFloat()
        }

        if (isAdded) {
            updateQuestionText()
            resetFragmentState()
        }
    }

    private fun updateQuestionText() {
        val operatorSymbol = if (isGreaterOrEqualType) "≥" else "≤"
        val fullText = getString(R.string.question_select_match_inequality, operatorSymbol, targetValue)

        val spannable = SpannableString(fullText)
        val accentBlue = ContextCompat.getColor(requireContext(), R.color.blue_2)

        val equationSegment = "$operatorSymbol $targetValue"
        val expressionStart = fullText.indexOf(equationSegment)
        if (expressionStart != -1) {
            spannable.setSpan(ForegroundColorSpan(accentBlue), expressionStart, expressionStart + equationSegment.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        questionText.text = spannable
    }

    private fun getAnswerChoiceValue(): Float {
        var value = 0f
        riveAnimation.controller.stateMachines.firstOrNull()?.inputs?.forEach {
            if (it.name == INPUT_CHOICE && it is SMINumber) value = it.value
        }
        return value
    }

    private fun checkAnswer() {
        val selectedOffset = getAnswerChoiceValue()
        isAnswerChecked = true

        animationTouchBlocker.visibility = View.VISIBLE

        val activity = requireActivity() as Math5GradeQuestionActivity
        activity.isResultCurrentlyVisible = true
        stateContainer.visibility = View.VISIBLE

        val mathematicalChosenValue = targetValue + selectedOffset.toInt()

        val isCorrect = if (isGreaterOrEqualType) {
            mathematicalChosenValue >= targetValue
        } else {
            mathematicalChosenValue <= targetValue
        }

        if (isCorrect) {
            playSound(R.raw.correct)
            activity.isCorrectAnswerShowing = true
            activity.playSuccessAnimation()
            val isFinished = activity.incrementProgress()
            if (isFirstAttempt) activity.totalXp += MathGrade5Type.SHOW_MATCH_SEGMENT.xp
            activity.handleCorrectAnswer()
            isIncorrectAttempt = false
            checkBtn.text = if (isFinished) getString(R.string.btn_finish) else getString(R.string.btn_continue)
            showCorrectState()
        } else {
            playSound(R.raw.wrong)
            isIncorrectAttempt = true
            activity.handleIncorrectAnswer() // FIX: Mapped engine to follow standard layout progression tracking rules
            checkBtn.text = getString(R.string.btn_try_again)
            showIncorrectState()
            setupSeeSolution()
        }
        isFirstAttempt = false
    }

    private fun showCorrectState() {
        stateContainer.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.green_3))
        circleState.setImageResource(R.drawable.correct_tick_icon)
        stateAnswer.text = getString(R.string.state_correct)

        val operatorSymbol = if (isGreaterOrEqualType) "≥" else "≤"
        answerDisplay.text = getString(R.string.label_math_result, operatorSymbol, targetValue)
        answerDisplay.visibility = View.VISIBLE
        applyButtonColors(R.color.green_1, R.color.green_2, R.color.green_4)
    }

    private fun showIncorrectState() {
        stateContainer.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.red_4))
        circleState.setImageResource(R.drawable.wrong_circle)
        stateAnswer.text = getString(R.string.state_incorrect)
        seeEnabledButton.text = getString(R.string.btn_see_solution)
        answerDisplay.visibility = View.GONE
        seeBtn.visibility = View.VISIBLE
        applyButtonColors(R.color.red_1, R.color.red_2, R.color.red_4)
    }

    private fun setupSeeSolution() {
        seeEnabledButton.setOnClickListener {
            seeBtn.visibility = View.GONE
            stateAnswer.text = getString(R.string.state_solution)

            val correctOffset = if (isGreaterOrEqualType) 4f else -4f
            val operatorSymbol = if (isGreaterOrEqualType) "≥" else "≤"

            answerDisplay.text = getString(R.string.solution_math_inequality, operatorSymbol, targetValue)
            answerDisplay.visibility = View.VISIBLE
            circleState.setImageResource(R.drawable.solution_lamp_icon)
            stateContainer.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.gray_2))

            animationTouchBlocker.visibility = View.VISIBLE
            riveAnimation.setNumberState(STATE_MACHINE, INPUT_CHOICE, correctOffset)

            applyButtonColors(R.color.black_3, R.color.black_2, R.color.gray_2)
            checkBtn.text = getString(R.string.btn_continue)
            isIncorrectAttempt = false
            isAnswerChecked = true
        }
    }

    private fun hideResultUI() {
        stateContainer.visibility = View.INVISIBLE
        seeBtn.visibility = View.GONE
        stateAnswer.text = ""
        answerDisplay.text = ""
        btnBack.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.white))
        val activity = requireActivity() as Math5GradeQuestionActivity
        activity.isResultCurrentlyVisible = false
    }

    private fun resetFragmentState() {
        isAnswerChecked = false
        isIncorrectAttempt = false
        hideResultUI()
        animationTouchBlocker.visibility = View.GONE
        riveAnimation.setNumberState(STATE_MACHINE, INPUT_CHOICE, 0f)
        disableCheckButton()
    }

    private fun resetForTryAgain() {
        isAnswerChecked = false
        isIncorrectAttempt = false
        hideResultUI()
        animationTouchBlocker.visibility = View.GONE
        riveAnimation.setNumberState(STATE_MACHINE, INPUT_CHOICE, 0f)
        disableCheckButton()
        mainHandler.post(checkRunnable)
    }

    private fun resetUIForNext() {
        val activity = requireActivity() as Math5GradeQuestionActivity
        activity.hideSuccessAnimation()
        mainHandler.removeCallbacks(checkRunnable)
        activity.isResultCurrentlyVisible = false
    }

    private fun initViews(view: View) {
        questionText = view.findViewById(R.id.questionText)
        riveAnimation = view.findViewById(R.id.x_greater_less_animation)
        animationTouchBlocker = view.findViewById(R.id.animationTouchBlocker)

        val activity = requireActivity() as Math5GradeQuestionActivity
        checkBtn = activity.findViewById(R.id.check_enabled_btn)
        checkBtnBack = activity.findViewById(R.id.check_enabled_button_background)
        btnBack = activity.findViewById(R.id.btnBackground)
        seeBtn = activity.findViewById(R.id.see_btn_container)
        seeEnabledButton = activity.findViewById(R.id.see_enabled_btn)
        stateAnswer = activity.findViewById(R.id.stateAnswer)
        answerDisplay = activity.findViewById(R.id.answer)
        stateContainer = activity.findViewById(R.id.stateContainer)
        circleState = activity.findViewById(R.id.circleState)

        checkBtn.setOnClickListener {
            if (!isAnswerChecked) {
                checkAnswer()
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
        btnBack.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.white))
    }

    private fun disableCheckButton() {
        checkBtn.isEnabled = false
        checkBtn.text = getString(R.string.btn_check)
        requireActivity().findViewById<FrameLayout>(R.id.check_enabled_btn_container).visibility = View.INVISIBLE
        requireActivity().findViewById<FrameLayout>(R.id.check_disabled_btn_container).visibility = View.VISIBLE
    }

    private fun enableCheckButton() {
        checkBtn.isEnabled = true
        requireActivity().findViewById<FrameLayout>(R.id.check_enabled_btn_container).visibility = View.VISIBLE
        requireActivity().findViewById<FrameLayout>(R.id.check_disabled_btn_container).visibility = View.INVISIBLE
        checkBtn.backgroundTintList = ContextCompat.getColorStateList(requireContext(), R.color.blue_2)
        checkBtnBack.backgroundTintList = ContextCompat.getColorStateList(requireContext(), R.color.blue_1)
    }

    private fun applyButtonColors(btn: Int, back: Int, bg: Int) {
        checkBtn.backgroundTintList = ContextCompat.getColorStateList(requireContext(), btn)
        checkBtnBack.backgroundTintList = ContextCompat.getColorStateList(requireContext(), back)
        btnBack.setBackgroundColor(ContextCompat.getColor(requireContext(), bg))
    }

    private fun playSound(resId: Int) {
        try {
            mediaPlayer?.release()
            mediaPlayer = MediaPlayer.create(requireContext(), resId)
            mediaPlayer?.setOnCompletionListener { it.release() }
            mediaPlayer?.start()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onDestroyView() {
        mainHandler.removeCallbacks(checkRunnable)
        mediaPlayer?.release()
        mediaPlayer = null
        super.onDestroyView()
    }
}