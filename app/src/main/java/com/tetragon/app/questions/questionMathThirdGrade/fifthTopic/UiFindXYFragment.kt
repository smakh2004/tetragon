package com.tetragon.app.questions.questionMathThirdGrade.fifthTopic

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
import com.tetragon.app.R
import com.tetragon.app.questions.questionMathFourthGrade.Math4GradeQuestionActivity
import com.tetragon.app.questions.questionMathFourthGrade.MathGrade4Type

class UiFindXYFragment : Fragment(R.layout.fragment_ui_find_x_y) {

    private lateinit var instructionText: TextView
    private lateinit var targetCoordinatesText: TextView
    private lateinit var riveAnimation: RiveAnimationView

    private lateinit var checkBtn: Button
    private lateinit var checkBtnBack: View
    private lateinit var btnBack: ConstraintLayout
    private lateinit var seeBtn: FrameLayout
    private lateinit var seeEnabledButton: Button
    private lateinit var stateAnswer: TextView
    private lateinit var answerDisplay: TextView
    private lateinit var stateContainer: FrameLayout
    private lateinit var circleState: ImageView

    private var targetX: Int = 0
    private var targetY: Int = 0

    // Flag to ensure button is disabled until the user physically starts dragging
    private var userHasInteracted = false
    private var isAnswerChecked = false
    private var isIncorrectAttempt = false
    private var isFirstAttempt = true
    private var isInitialized = false
    private var mediaPlayer: MediaPlayer? = null

    private val STATE_MACHINE = "State Machine 1"
    private val INPUT_ANSWERED = "answered"
    private val INPUT_NOB_X = "nob_x"
    private val INPUT_NOB_Y = "nob_y"

    private val mainHandler = Handler(Looper.getMainLooper())

    private val checkRunnable = object : Runnable {
        override fun run() {
            if (!isAdded || isAnswerChecked || !::riveAnimation.isInitialized) return

            val (currentX, currentY) = getUserSelectedCoordinates()

            // Ensure we have read real tracking data from Rive
            if (currentX >= 0f && currentY >= 0f) {
                if (!userHasInteracted) {
                    // Only trigger interaction if coordinates actively break away from (4.0, 4.0)
                    if (currentX != 4f || currentY != 4f) {
                        userHasInteracted = true
                        enableCheckButton()
                    } else {
                        // Keep it explicitly clamped off on initial load syncs
                        disableCheckButton()
                    }
                } else {
                    if (!checkBtn.isEnabled) {
                        enableCheckButton()
                    }
                }
            }
            mainHandler.postDelayed(this, 100)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initViews(view)

        // FIX: Force reset state fields immediately when view is redrawn for a new question layout
        isAnswerChecked = false
        isIncorrectAttempt = false
        userHasInteracted = false
        disableCheckButton()

        if (!isInitialized) {
            riveAnimation.post {
                generateCoordinateProblem()
                isInitialized = true
                mainHandler.post(checkRunnable)
            }
        } else {
            // For consecutive questions, generate a fresh setup layout pass cleanly
            riveAnimation.post {
                generateCoordinateProblem()
                mainHandler.post(checkRunnable)
            }
        }
    }

    private fun initViews(view: View) {
        instructionText = view.findViewById(R.id.questionText)
        targetCoordinatesText = view.findViewById(R.id.targetCoordinatesText)
        riveAnimation = view.findViewById(R.id.coordinateGridAnim)

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

    private fun generateCoordinateProblem() {
        targetX = (0..8).random()
        targetY = (0..8).random()

        val rawText = "($targetX, $targetY)"
        val spannable = SpannableString(rawText)
        val blueColor = ContextCompat.getColor(requireContext(), R.color.blue_2)

        val xString = targetX.toString()
        val yString = targetY.toString()

        val xStart = rawText.indexOf(xString)
        val yStart = rawText.lastIndexOf(yString)

        if (xStart != -1) {
            spannable.setSpan(ForegroundColorSpan(blueColor), xStart, xStart + xString.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        if (yStart != -1) {
            spannable.setSpan(ForegroundColorSpan(blueColor), yStart, yStart + yString.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }

        targetCoordinatesText.text = spannable

        try {
            riveAnimation.setBooleanState(STATE_MACHINE, INPUT_ANSWERED, false)
            riveAnimation.setNumberState(STATE_MACHINE, INPUT_NOB_X, 4f)
            riveAnimation.setNumberState(STATE_MACHINE, INPUT_NOB_Y, 4f)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        resetFragmentState()
    }

    private fun getUserSelectedCoordinates(): Pair<Float, Float> {
        var xVal = -1f
        var yVal = -1f
        riveAnimation.controller.stateMachines.firstOrNull()?.inputs?.forEach { input ->
            if (input is SMINumber) {
                if (input.name == INPUT_NOB_X) xVal = input.value
                if (input.name == INPUT_NOB_Y) yVal = input.value
            }
        }
        return Pair(xVal, yVal)
    }

    private fun checkAnswer() {
        val (userX, userY) = getUserSelectedCoordinates()

        isAnswerChecked = true
        val activity = requireActivity() as Math4GradeQuestionActivity
        activity.isResultCurrentlyVisible = true
        stateContainer.visibility = View.VISIBLE

        try {
            riveAnimation.setBooleanState(STATE_MACHINE, INPUT_ANSWERED, true)
        } catch (e: Exception) { e.printStackTrace() }

        if (userX.toInt() == targetX && userY.toInt() == targetY) {
            playSound(R.raw.correct)
            activity.playSuccessAnimation()
            val isFinished = activity.incrementProgress()

            if (isFirstAttempt) {
                activity.totalXp += MathGrade4Type.FIND_X_Y.xp
            }

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

    private fun showCorrectState() {
        stateContainer.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.green_3))
        circleState.setImageResource(R.drawable.correct_tick_icon)
        stateAnswer.text = getString(R.string.state_correct)
        answerDisplay.text = "($targetX, $targetY)"
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
            answerDisplay.text = getString(R.string.solution_find_xy_explanation, targetX, targetY)
            answerDisplay.visibility = View.VISIBLE
            circleState.setImageResource(R.drawable.solution_lamp_icon)
            stateContainer.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.gray_2))

            try {
                riveAnimation.setBooleanState(STATE_MACHINE, INPUT_ANSWERED, false)
                riveAnimation.setNumberState(STATE_MACHINE, INPUT_NOB_X, targetX.toFloat())
                riveAnimation.setNumberState(STATE_MACHINE, INPUT_NOB_Y, targetY.toFloat())

                mainHandler.post {
                    if (isAdded && ::riveAnimation.isInitialized) {
                        try {
                            mainHandler.post {
                                riveAnimation.setBooleanState(STATE_MACHINE, INPUT_ANSWERED, true)
                            }
                        } catch (e: Exception) { e.printStackTrace() }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

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
        val activity = requireActivity() as Math4GradeQuestionActivity
        activity.isResultCurrentlyVisible = false
    }

    private fun resetFragmentState() {
        isAnswerChecked = false
        isIncorrectAttempt = false
        userHasInteracted = false
        hideResultUI()
        disableCheckButton()
    }

    private fun resetForTryAgain() {
        isAnswerChecked = false
        isIncorrectAttempt = false
        userHasInteracted = false
        hideResultUI()

        try {
            riveAnimation.setBooleanState(STATE_MACHINE, INPUT_ANSWERED, false)
            riveAnimation.setNumberState(STATE_MACHINE, INPUT_NOB_X, 4f)
            riveAnimation.setNumberState(STATE_MACHINE, INPUT_NOB_Y, 4f)
        } catch (e: Exception) { e.printStackTrace() }

        disableCheckButton()
        mainHandler.post(checkRunnable)
    }

    private fun resetUIForNext() {
        val activity = requireActivity() as Math4GradeQuestionActivity
        activity.hideSuccessAnimation()
        mainHandler.removeCallbacks(checkRunnable)
        activity.isResultCurrentlyVisible = false
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
        mediaPlayer?.release()
        mediaPlayer = MediaPlayer.create(requireContext(), resId)
        mediaPlayer?.start()
    }

    override fun onDestroyView() {
        mainHandler.removeCallbacks(checkRunnable)
        mediaPlayer?.release()
        super.onDestroyView()
    }
}