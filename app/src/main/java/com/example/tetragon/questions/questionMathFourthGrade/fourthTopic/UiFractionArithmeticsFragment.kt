package com.example.tetragon.questions.questionMathFourthGrade.fourthTopic

import android.media.MediaPlayer
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import app.rive.runtime.kotlin.RiveAnimationView
import app.rive.runtime.kotlin.controllers.RiveFileController
import app.rive.runtime.kotlin.core.PlayableInstance
import app.rive.runtime.kotlin.core.SMIBoolean
import com.example.tetragon.R
import com.example.tetragon.questions.questionMathFourthGrade.Math4GradeQuestionActivity
import com.example.tetragon.questions.questionMathFourthGrade.MathGrade4Type

class UiFractionArithmeticsFragment : Fragment(R.layout.fragment_ui_fraction_arithmetics) {

    private lateinit var riveAnimation: RiveAnimationView
    private lateinit var animationOverlay: View
    private lateinit var questionText: TextView
    private lateinit var tvNum1: TextView
    private lateinit var tvDenom1: TextView
    private lateinit var tvNum2: TextView
    private lateinit var tvDenom2: TextView
    private lateinit var tvOperator: TextView

    private lateinit var addBtn: LinearLayout
    private lateinit var minusBtn: LinearLayout
    private lateinit var addEnabledContainer: FrameLayout
    private lateinit var addDisabledContainer: FrameLayout
    private lateinit var minusEnabledContainer: FrameLayout
    private lateinit var minusDisabledContainer: FrameLayout

    private lateinit var checkBtn: Button
    private lateinit var checkBtnBack: View
    private lateinit var btnBack: ConstraintLayout
    private lateinit var seeBtn: FrameLayout
    private lateinit var seeEnabledButton: Button
    private lateinit var stateAnswer: TextView
    private lateinit var answerDisplay: TextView
    private lateinit var stateContainer: FrameLayout
    private lateinit var circleState: ImageView

    private var n1 = 0
    private var n2 = 0
    private var targetNumeratorResult = 0
    private var targetDenominatorResult = 0
    private var currentDenominator = 1
    private var isAddition = true

    private var isAnswerChecked = false
    private var isIncorrectAttempt = false
    private var isFirstAttempt = true
    private var mediaPlayer: MediaPlayer? = null

    private val STATE_MACHINE = "State Machine 1"
    private val FRACTION_INPUT = "fraction"

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initViews(view)

        riveAnimation.setRiveResource(
            R.raw.fraction,
            stateMachineName = STATE_MACHINE,
            autoplay = true
        )

        riveAnimation.post {
            setupRiveListener()
            generateProblem()
        }
    }

    private fun initViews(view: View) {
        riveAnimation = view.findViewById(R.id.fraction)
        animationOverlay = view.findViewById(R.id.animationOverlay)
        questionText = view.findViewById(R.id.questionText)
        tvNum1 = view.findViewById(R.id.tvNum1)
        tvDenom1 = view.findViewById(R.id.tvDenom1)
        tvNum2 = view.findViewById(R.id.tvNum2)
        tvDenom2 = view.findViewById(R.id.tvDenom2)
        tvOperator = view.findViewById(R.id.tvOperator)

        addBtn = view.findViewById(R.id.add_btn)
        minusBtn = view.findViewById(R.id.minus_btn)
        addEnabledContainer = view.findViewById(R.id.add_enabled_container)
        addDisabledContainer = view.findViewById(R.id.add_disabled_container)
        minusEnabledContainer = view.findViewById(R.id.minus_enabled_container)
        minusDisabledContainer = view.findViewById(R.id.minus_disabled_container)

        val activity = requireActivity() as Math4GradeQuestionActivity
        checkBtn = activity.findViewById(R.id.check_enabled_btn)
        checkBtnBack = activity.findViewById(R.id.check_enabled_button_background)
        btnBack = activity.findViewById(R.id.btnBackground)
        seeBtn = activity.findViewById(R.id.see_btn_container)
        seeEnabledButton = activity.findViewById(R.id.see_enabled_btn)
        stateAnswer = activity.findViewById(R.id.stateAnswer)
        answerDisplay = activity.findViewById(R.id.answer)
        stateContainer = activity.findViewById(R.id.stateContainer)
        circleState = activity.findViewById(R.id.circleState)

        addBtn.setOnClickListener { if (!isAnswerChecked) changeDenominator(1) }
        minusBtn.setOnClickListener { if (!isAnswerChecked) changeDenominator(-1) }

        checkBtn.setOnClickListener {
            if (!isAnswerChecked) {
                checkAnswer()
            } else {
                if (isIncorrectAttempt) {
                    resetForTryAgain()
                } else {
                    if (checkBtn.text == getString(R.string.btn_finish)) activity.navigateToXpGained()
                    else {
                        activity.isResultCurrentlyVisible = false
                        activity.hideSuccessAnimation()
                        activity.showRandomQuestion()
                    }
                }
            }
        }
        disableCheckButton()
    }

    private fun setupRiveListener() {
        riveAnimation.registerListener(object : RiveFileController.Listener {
            override fun notifyStateChanged(stateMachineName: String, stateName: String) {
                if (stateName.contains("_pressed") && !isAnswerChecked) {
                    val parts = stateName.split("_")
                    val denomInState = parts[0].substringAfter("/").toIntOrNull()
                    val sliceIndex = parts.getOrNull(1)?.toIntOrNull()

                    if (denomInState == currentDenominator && sliceIndex != null) {
                        activity?.runOnUiThread {
                            toggleSlice(currentDenominator, sliceIndex)
                        }
                    }
                }

                if (!isAnswerChecked) {
                    activity?.runOnUiThread {
                        validateCheckButton()
                    }
                }
            }
            override fun notifyLoop(animation: PlayableInstance) {}
            override fun notifyPause(animation: PlayableInstance) {}
            override fun notifyPlay(animation: PlayableInstance) {}
            override fun notifyStop(animation: PlayableInstance) {}
        })
    }

    private fun toggleSlice(denom: Int, index: Int) {
        val inputName = "1/${denom}_$index"
        val currentState = getRiveBoolean(inputName)
        riveAnimation.setBooleanState(STATE_MACHINE, inputName, !currentState)
        validateCheckButton()
    }

    private fun getRiveBoolean(name: String): Boolean {
        var value = false
        riveAnimation.controller.stateMachines.firstOrNull()?.inputs?.forEach {
            if (it.name == name && it is SMIBoolean) value = it.value
        }
        return value
    }

    private fun validateCheckButton() {
        var isAnySliceTrue = false
        riveAnimation.controller.stateMachines.firstOrNull()?.inputs?.forEach { input ->
            if (input.name.contains("/") && input is SMIBoolean && input.value) {
                isAnySliceTrue = true
            }
        }
        if (isAnySliceTrue) enableCheckButton() else disableCheckButton()
    }

    private fun changeDenominator(delta: Int) {
        val newVal = currentDenominator + delta
        if (newVal in 1..6) {
            currentDenominator = newVal
            resetAllVisualSlices()
            riveAnimation.setNumberState(STATE_MACHINE, FRACTION_INPUT, currentDenominator.toFloat())
            updateLayoutButtonsUI()
            validateCheckButton()
        }
    }

    private fun generateProblem() {
        targetDenominatorResult = (2..6).random()
        isAddition = (0..1).random() == 1

        if (isAddition) {
            tvOperator.text = "+"
            n1 = (1 until targetDenominatorResult).random()
            n2 = 1
            if (n1 + n2 > targetDenominatorResult) n1 = targetDenominatorResult - 1
            targetNumeratorResult = n1 + n2
        } else {
            tvOperator.text = "-"
            n1 = (2..targetDenominatorResult).random()
            n2 = (1 until n1).random()
            targetNumeratorResult = n1 - n2
        }

        tvNum1.text = n1.toString()
        tvDenom1.text = targetDenominatorResult.toString()
        tvNum2.text = n2.toString()
        tvDenom2.text = targetDenominatorResult.toString()

        updateQuestionText()

        isAnswerChecked = false
        isIncorrectAttempt = false
        currentDenominator = 1
        animationOverlay.visibility = View.GONE

        resetAllVisualSlices()
        riveAnimation.setNumberState(STATE_MACHINE, FRACTION_INPUT, 1f)

        stateContainer.visibility = View.INVISIBLE
        seeBtn.visibility = View.GONE
        checkBtn.text = getString(R.string.btn_check)
        btnBack.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.white))

        updateLayoutButtonsUI()
        disableCheckButton()
    }

    private fun updateQuestionText() {
        val sentence = getString(R.string.title_fraction_arithmetics)
        val wordToHighlight = getString(R.string.highlight_fraction)
        val spannable = SpannableString(sentence)
        val start = sentence.indexOf(wordToHighlight)
        if (start != -1) {
            spannable.setSpan(
                ForegroundColorSpan(ContextCompat.getColor(requireContext(), R.color.blue_2)),
                start, start + wordToHighlight.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        questionText.text = spannable
    }

    private fun checkAnswer() {
        var selectedCount = 0
        for (i in 1..currentDenominator) {
            if (getRiveBoolean("1/${currentDenominator}_$i")) selectedCount++
        }

        isAnswerChecked = true
        animationOverlay.visibility = View.VISIBLE

        val activity = requireActivity() as Math4GradeQuestionActivity
        activity.isResultCurrentlyVisible = true
        stateContainer.visibility = View.VISIBLE
        updateLayoutButtonsUI()

        val isVisualCorrect = selectedCount * targetDenominatorResult == targetNumeratorResult * currentDenominator

        if (isVisualCorrect) {
            playSound(R.raw.correct)
            activity.playSuccessAnimation()

            val isFinished = activity.incrementProgress()
            if (isFirstAttempt) activity.totalXp += MathGrade4Type.FRACTION.xp

            activity.handleCorrectAnswer()
            isIncorrectAttempt = false

            checkBtn.text = if (isFinished) getString(R.string.btn_finish) else getString(R.string.btn_continue)
            showCorrectState()
        } else {
            playSound(R.raw.wrong)
            isIncorrectAttempt = true
            checkBtn.text = getString(R.string.btn_try_again)
            showIncorrectState()
            setupSeeSolution()
        }
        isFirstAttempt = false
    }

    private fun setupSeeSolution() {
        seeEnabledButton.setOnClickListener {
            isAnswerChecked = true
            isIncorrectAttempt = false
            seeBtn.visibility = View.GONE
            stateAnswer.text = getString(R.string.state_solution)

            answerDisplay.text = getString(R.string.label_answer_fraction, formatAnswer(targetNumeratorResult, targetDenominatorResult))
            answerDisplay.visibility = View.VISIBLE

            currentDenominator = targetDenominatorResult
            riveAnimation.setNumberState(STATE_MACHINE, FRACTION_INPUT, currentDenominator.toFloat())

            resetAllVisualSlices()
            for (i in 1..targetNumeratorResult) {
                riveAnimation.setBooleanState(STATE_MACHINE, "1/${targetDenominatorResult}_$i", true)
            }

            circleState.setImageResource(R.drawable.solution_lamp_icon)
            stateContainer.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.gray_2))
            applyButtonColors(R.color.black_3, R.color.black_2, R.color.gray_2)
            checkBtn.text = getString(R.string.btn_continue)
            updateLayoutButtonsUI()
        }
    }

    private fun formatAnswer(num: Int, denom: Int): String {
        if (num == 0) return "0"
        if (num == denom) return "1"

        val commonDivisor = gcd(num, denom)
        val simplifiedNum = num / commonDivisor
        val simplifiedDenom = denom / commonDivisor

        return "$simplifiedNum/$simplifiedDenom"
    }

    private fun gcd(a: Int, b: Int): Int {
        var x = a
        var y = b
        while (y != 0) {
            val temp = y
            y = x % y
            x = temp
        }
        return x
    }

    private fun resetAllVisualSlices() {
        riveAnimation.controller.stateMachines.firstOrNull()?.inputs?.forEach {
            if (it.name.contains("/") && it is SMIBoolean) {
                riveAnimation.setBooleanState(STATE_MACHINE, it.name, false)
            }
        }
    }

    private fun resetForTryAgain() {
        val activity = requireActivity() as Math4GradeQuestionActivity
        activity.isResultCurrentlyVisible = false
        isAnswerChecked = false
        isIncorrectAttempt = false
        animationOverlay.visibility = View.GONE
        stateContainer.visibility = View.INVISIBLE
        seeBtn.visibility = View.GONE
        checkBtn.text = getString(R.string.btn_check)
        btnBack.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.white))
        resetAllVisualSlices()
        disableCheckButton()
        updateLayoutButtonsUI()
    }

    private fun showCorrectState() {
        stateContainer.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.green_3))
        circleState.setImageResource(R.drawable.correct_tick_icon)
        stateAnswer.text = getString(R.string.state_correct)
        answerDisplay.text = getString(R.string.label_answer_fraction, formatAnswer(targetNumeratorResult, targetDenominatorResult))
        answerDisplay.visibility = View.VISIBLE
        applyButtonColors(R.color.green_1, R.color.green_2, R.color.green_4)
    }

    private fun showIncorrectState() {
        stateContainer.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.red_4))
        circleState.setImageResource(R.drawable.wrong_circle)
        stateAnswer.text = getString(R.string.state_incorrect)
        answerDisplay.visibility = View.GONE
        seeBtn.visibility = View.VISIBLE
        seeEnabledButton.text = getString(R.string.btn_see_solution)
        applyButtonColors(R.color.red_1, R.color.red_2, R.color.red_4)
    }

    private fun updateLayoutButtonsUI() {
        if (isAnswerChecked) {
            addEnabledContainer.visibility = View.GONE
            addDisabledContainer.visibility = View.VISIBLE
            minusEnabledContainer.visibility = View.GONE
            minusDisabledContainer.visibility = View.VISIBLE
            return
        }
        val canAdd = currentDenominator < 6
        val canMinus = currentDenominator > 1
        addEnabledContainer.visibility = if (canAdd) View.VISIBLE else View.GONE
        addDisabledContainer.visibility = if (canAdd) View.GONE else View.VISIBLE
        minusEnabledContainer.visibility = if (canMinus) View.VISIBLE else View.GONE
        minusDisabledContainer.visibility = if (canMinus) View.GONE else View.VISIBLE
    }

    private fun disableCheckButton() {
        checkBtn.isEnabled = false
        val activity = requireActivity() as Math4GradeQuestionActivity
        activity.findViewById<FrameLayout>(R.id.check_enabled_btn_container).visibility = View.INVISIBLE
        checkBtnBack.visibility = View.INVISIBLE
        activity.findViewById<FrameLayout>(R.id.check_disabled_btn_container).visibility = View.VISIBLE
    }

    private fun enableCheckButton() {
        checkBtn.isEnabled = true
        val activity = requireActivity() as Math4GradeQuestionActivity
        activity.findViewById<FrameLayout>(R.id.check_enabled_btn_container).visibility = View.VISIBLE
        checkBtnBack.visibility = View.VISIBLE
        activity.findViewById<FrameLayout>(R.id.check_disabled_btn_container).visibility = View.INVISIBLE
        checkBtn.backgroundTintList = ContextCompat.getColorStateList(requireContext(), R.color.blue_2)
        checkBtnBack.backgroundTintList = ContextCompat.getColorStateList(requireContext(), R.color.blue_1)
    }

    private fun applyButtonColors(btn: Int, back: Int, bg: Int) {
        checkBtn.backgroundTintList = ContextCompat.getColorStateList(requireContext(), btn)
        checkBtnBack.backgroundTintList = ContextCompat.getColorStateList(requireContext(), back)
        btnBack.setBackgroundColor(ContextCompat.getColor(requireContext(), bg))
    }

    private fun playSound(resId: Int) {
        mediaPlayer?.release()
        mediaPlayer = MediaPlayer.create(requireContext(), resId)
        mediaPlayer?.start()
    }

    override fun onDestroy() {
        mediaPlayer?.release()
        super.onDestroy()
    }
}