package com.example.tetragon.questions.questionMathThirdGrade.thirdTopic

import android.media.MediaPlayer
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import app.rive.runtime.kotlin.RiveAnimationView
import app.rive.runtime.kotlin.controllers.RiveFileController
import app.rive.runtime.kotlin.core.PlayableInstance
import app.rive.runtime.kotlin.core.SMIBoolean
import com.example.tetragon.R
import com.example.tetragon.questions.questionMathThirdGrade.Math3GradeQuestionActivity
import com.example.tetragon.questions.questionMathThirdGrade.MathGrade3Type

class UiFractionPizzaSixPiecesFragment : Fragment(R.layout.fragment_ui_fraction_pizza_six_pieces) {

    private lateinit var riveAnimation: RiveAnimationView
    private lateinit var animationOverlay: View
    private lateinit var questionText: TextView

    private lateinit var checkBtn: Button
    private lateinit var checkBtnBack: View
    private lateinit var btnBack: ConstraintLayout
    private lateinit var seeBtn: FrameLayout
    private lateinit var seeEnabledButton: Button
    private lateinit var stateAnswer: TextView
    private lateinit var answerDisplay: TextView
    private lateinit var stateContainer: FrameLayout
    private lateinit var circleState: ImageView

    private var targetNumerator = 0
    private val PIZZA_SLICES = 6
    private var isAnswerChecked = false
    private var isIncorrectAttempt = false
    private var isFirstAttempt = true
    private var mediaPlayer: MediaPlayer? = null
    private val STATE_MACHINE = "State Machine 1"

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initViews(view)

        riveAnimation.setRiveResource(
            R.raw.pizza_6,
            stateMachineName = STATE_MACHINE,
            autoplay = true
        )

        riveAnimation.post {
            setupRiveListener()
            generateProblem()
        }
    }

    private fun initViews(view: View) {
        riveAnimation = view.findViewById(R.id.pizza_6)
        animationOverlay = view.findViewById(R.id.animationOverlay)
        questionText = view.findViewById(R.id.questionText)

        val activity = requireActivity() as Math3GradeQuestionActivity
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
                    if (checkBtn.text == "FINISH") activity.navigateToXpGained()
                    else {
                        val isMilestoneActive = activity.checkAndTriggerMilestone()
                        if (!isMilestoneActive) {
                            activity.isResultCurrentlyVisible = false
                            activity.hideSuccessAnimation()
                            activity.showRandomQuestion()
                        }
                    }
                }
            }
        }
        disableCheckButton()
    }

    private fun setupRiveListener() {
        riveAnimation.registerListener(object : RiveFileController.Listener {
            override fun notifyStateChanged(stateMachineName: String, stateName: String) {
                // Secondary safeguard: Do nothing if the answer is already locked
                if (isAnswerChecked) return

                if (stateName.contains("_pressed")) {
                    val sliceIndex = stateName.substringAfter("1/6_")
                        .substringBefore("_")
                        .toIntOrNull()

                    if (sliceIndex != null) {
                        activity?.runOnUiThread { toggleSlice(sliceIndex) }
                    }
                }

                if (!isAnswerChecked) {
                    activity?.runOnUiThread { validateCheckButton() }
                }
            }
            override fun notifyLoop(animation: PlayableInstance) {}
            override fun notifyPause(animation: PlayableInstance) {}
            override fun notifyPlay(animation: PlayableInstance) {}
            override fun notifyStop(animation: PlayableInstance) {}
        })
    }

    private fun toggleSlice(index: Int) {
        if (isAnswerChecked) return
        val inputName = "1/6_$index"
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
            if (input.name.contains("1/6_") && input is SMIBoolean && input.value) {
                isAnySliceTrue = true
            }
        }
        if (isAnySliceTrue) enableCheckButton() else disableCheckButton()
    }

    private fun checkAnswer() {
        var selectedCount = 0
        for (i in 1..PIZZA_SLICES) {
            if (getRiveBoolean("1/6_$i")) selectedCount++
        }

        val isCorrect = (selectedCount == targetNumerator)
        isAnswerChecked = true

        // Show the transparent overlay to block all touches on the pizza
        animationOverlay.visibility = View.VISIBLE

        val activity = requireActivity() as Math3GradeQuestionActivity
        activity.isResultCurrentlyVisible = true
        stateContainer.visibility = View.VISIBLE

        if (isCorrect) {
            playSound(R.raw.correct)
            activity.playSuccessAnimation()
            val isFinished = activity.incrementProgress()
            if (isFirstAttempt) activity.totalXp += MathGrade3Type.PIZZA_6.xp
            activity.handleCorrectAnswer()
            isIncorrectAttempt = false
            checkBtn.text = if (isFinished) "FINISH" else "CONTINUE"
            showCorrectState()
        } else {
            playSound(R.raw.wrong)
            isIncorrectAttempt = true
            checkBtn.text = "TRY AGAIN"
            showIncorrectState()
            setupSeeSolution()
        }
        isFirstAttempt = false
    }

    private fun generateProblem() {
        targetNumerator = (1..6).random()
        updateQuestionText()

        isAnswerChecked = false
        isIncorrectAttempt = false
        animationOverlay.visibility = View.GONE // Hide shield for new question
        resetAllVisualSlices()

        stateContainer.visibility = View.INVISIBLE
        seeBtn.visibility = View.GONE
        checkBtn.text = "CHECK"
        btnBack.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.white))

        disableCheckButton()
    }

    private fun updateQuestionText() {
        val fractionText = "$targetNumerator/6"
        val sentence = "Show $fractionText of the pizza."
        val spannable = SpannableString(sentence)
        val start = sentence.indexOf(fractionText)
        if (start != -1) {
            spannable.setSpan(
                ForegroundColorSpan(ContextCompat.getColor(requireContext(), R.color.blue_2)),
                start, start + fractionText.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        questionText.text = spannable
    }

    private fun setupSeeSolution() {
        seeEnabledButton.setOnClickListener {
            isAnswerChecked = true
            isIncorrectAttempt = false
            animationOverlay.visibility = View.VISIBLE // Keep shield active during solution
            seeBtn.visibility = View.GONE

            stateAnswer.text = "Solution"
            answerDisplay.text = "The answer is $targetNumerator out of 6 slices."
            answerDisplay.visibility = View.VISIBLE
            circleState.setImageResource(R.drawable.solution_lamp_icon)
            stateContainer.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.gray_2))

            resetAllVisualSlices()
            for (i in 1..targetNumerator) {
                riveAnimation.setBooleanState(STATE_MACHINE, "1/6_$i", true)
            }

            checkBtn.text = "CONTINUE"
            enableCheckButton()
            applyButtonColors(R.color.black_3, R.color.black_2, R.color.gray_2)
        }
    }

    private fun resetAllVisualSlices() {
        riveAnimation.controller.stateMachines.firstOrNull()?.inputs?.forEach {
            if (it.name.contains("1/6_") && it is SMIBoolean) {
                riveAnimation.setBooleanState(STATE_MACHINE, it.name, false)
            }
        }
    }

    private fun resetForTryAgain() {
        val activity = requireActivity() as Math3GradeQuestionActivity
        activity.isResultCurrentlyVisible = false
        isAnswerChecked = false
        isIncorrectAttempt = false
        animationOverlay.visibility = View.GONE // Remove shield so user can try again

        stateContainer.visibility = View.INVISIBLE
        seeBtn.visibility = View.GONE
        checkBtn.text = "CHECK"
        btnBack.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.white))

        resetAllVisualSlices()
        disableCheckButton()
    }

    private fun showCorrectState() {
        stateContainer.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.green_3))
        circleState.setImageResource(R.drawable.correct_tick_icon)
        stateAnswer.text = "Correct!"
        answerDisplay.text = "Great job!"
        answerDisplay.visibility = View.VISIBLE
        applyButtonColors(R.color.green_1, R.color.green_2, R.color.green_4)
    }

    private fun showIncorrectState() {
        stateContainer.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.red_4))
        circleState.setImageResource(R.drawable.wrong_circle)
        stateAnswer.text = "Incorrect!"
        answerDisplay.visibility = View.GONE
        seeBtn.visibility = View.VISIBLE
        applyButtonColors(R.color.red_1, R.color.red_2, R.color.red_4)
    }

    private fun disableCheckButton() {
        checkBtn.isEnabled = false
        val activity = requireActivity()
        activity.findViewById<FrameLayout>(R.id.check_enabled_btn_container).visibility = View.INVISIBLE
        activity.findViewById<FrameLayout>(R.id.check_disabled_btn_container).visibility = View.VISIBLE
    }

    private fun enableCheckButton() {
        checkBtn.isEnabled = true
        val activity = requireActivity()
        activity.findViewById<FrameLayout>(R.id.check_enabled_btn_container).visibility = View.VISIBLE
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