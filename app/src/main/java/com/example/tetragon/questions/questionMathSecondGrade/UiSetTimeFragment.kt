package com.example.tetragon.questions.questionMathSecondGrade

import android.media.MediaPlayer
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
import app.rive.runtime.kotlin.core.SMINumber
import com.example.tetragon.R

class UiSetTimeFragment : Fragment(R.layout.fragment_ui_set_time) {

    private lateinit var questionText: TextView
    private lateinit var riveClock: RiveAnimationView

    private lateinit var checkBtn: Button
    private lateinit var checkBtnBack: View
    private lateinit var btnBack: ConstraintLayout
    private lateinit var seeBtn: FrameLayout
    private lateinit var seeEnabledButton: Button
    private lateinit var stateAnswer: TextView
    private lateinit var answerDisplay: TextView
    private lateinit var stateContainer: FrameLayout
    private lateinit var circleState: ImageView

    private var targetHour: Int = 0
    private var targetMinute: Int = 0
    private var isAnswerChecked = false
    private var isIncorrectAttempt = false
    private var isFirstAttempt = true
    private var isUserInteracting = false
    private var mediaPlayer: MediaPlayer? = null

    private val STATE_MACHINE = "State Machine 1"
    private val INPUT_ANSWERED = "answered"
    private val INPUT_MINUTES = "minutes"
    private val INPUT_HOURS = "hours"

    private val mainHandler = Handler(Looper.getMainLooper())

    private val checkRunnable = object : Runnable {
        override fun run() {
            if (!isAdded || isAnswerChecked || !::riveClock.isInitialized) return

            val currentMin = getRiveValue(INPUT_MINUTES)
            val currentHr = getRiveValue(INPUT_HOURS)

            if (!isUserInteracting) {
                // Fetch the values we 'locked' during reset
                val lockedHr = riveClock.getTag() as? Float ?: -1f
                val lockedMin = riveClock.getTag(R.id.topic12) as? Float ?: -1f

                // We use a slightly larger threshold (0.5) to avoid accidental triggers from Rive float precision
                if (Math.abs(currentMin - lockedMin) > 0.5f || Math.abs(currentHr - lockedHr) > 0.5f) {
                    isUserInteracting = true
                    enableCheckButton()
                }
            }

            mainHandler.postDelayed(this, 100)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initViews(view)
        generateProblem()

        // DELAY the start of the polling to allow Rive to finish its internal setup
        mainHandler.postDelayed(checkRunnable, 500)
    }

    private fun generateProblem() {
        targetHour = (1..12).random()
        targetMinute = listOf(0, 15, 30, 45).random()

        var randomStartHour: Int
        var randomStartMin: Int
        do {
            randomStartHour = (0..11).random()
            randomStartMin = listOf(0, 15, 30, 45).random()
        } while (randomStartHour == (targetHour % 12) && randomStartMin == targetMinute)

        updateQuestionText()
        resetFragmentState(randomStartHour, randomStartMin)
    }

    private fun getRiveValue(inputName: String): Float {
        var value = 0f
        riveClock.controller.stateMachines.firstOrNull()?.inputs?.forEach { input ->
            if (input.name == inputName && input is SMINumber) value = input.value
        }
        return value
    }

    private fun updateQuestionText() {
        val timeText = String.format("%d:%02d", targetHour, targetMinute)
        val sentence = "Set this time $timeText."
        val spannable = SpannableString(sentence)
        val blueColor = ContextCompat.getColor(requireContext(), R.color.blue_2)

        val start = sentence.indexOf(timeText)
        if (start != -1) {
            spannable.setSpan(ForegroundColorSpan(blueColor), start, start + timeText.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        questionText.text = spannable
    }

    private fun checkAnswer() {
        val userMinute = getRiveValue(INPUT_MINUTES).toInt()
        val userHour = getRiveValue(INPUT_HOURS).toInt()

        isAnswerChecked = true
        val activity = requireActivity() as Math2GradeQuestionActivity
        activity.isResultCurrentlyVisible = true
        stateContainer.visibility = View.VISIBLE
        riveClock.setBooleanState(STATE_MACHINE, INPUT_ANSWERED, true)

        val logicTargetHr = targetHour % 12
        val logicUserHr = userHour % 12

        if (logicTargetHr == logicUserHr && targetMinute == userMinute) {
            playSound(R.raw.correct)
            activity.playSuccessAnimation()
            val isFinished = activity.incrementProgress()
            if (isFirstAttempt) activity.totalXp += 10
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

    private fun setupSeeSolution() {
        seeEnabledButton.setOnClickListener {
            seeBtn.visibility = View.GONE
            stateAnswer.text = "Solution"
            val timeText = String.format("%d:%02d", targetHour, targetMinute)
            answerDisplay.text = "The correct time is $timeText"
            answerDisplay.visibility = View.VISIBLE

            circleState.setImageResource(R.drawable.solution_lamp_icon)
            stateContainer.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.gray_2))

            riveClock.setBooleanState(STATE_MACHINE, "hour_hand_active", true)
            riveClock.setBooleanState(STATE_MACHINE, "minute_hand_active", true)

            riveClock.setNumberState(STATE_MACHINE, INPUT_HOURS, (targetHour % 12).toFloat())
            riveClock.setNumberState(STATE_MACHINE, INPUT_MINUTES, targetMinute.toFloat())
            riveClock.setBooleanState(STATE_MACHINE, INPUT_ANSWERED, true)

            applyButtonColors(R.color.black_3, R.color.black_2, R.color.gray_2)
            checkBtn.text = "CONTINUE"
            isIncorrectAttempt = false
        }
    }

    private fun initViews(view: View) {
        questionText = view.findViewById(R.id.questionText)
        riveClock = view.findViewById(R.id.topic12)

        val activity = requireActivity() as Math2GradeQuestionActivity
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
        disableCheckButton()
    }

    private fun resetFragmentState(startHr: Int, startMin: Int) {
        isAnswerChecked = false
        isIncorrectAttempt = false
        isUserInteracting = false
        checkBtn.text = "CHECK"
        stateContainer.visibility = View.INVISIBLE
        seeBtn.visibility = View.GONE

        riveClock.setBooleanState(STATE_MACHINE, "hour_hand_active", true)
        riveClock.setBooleanState(STATE_MACHINE, "minute_hand_active", true)

        // Set the numbers
        riveClock.setNumberState(STATE_MACHINE, INPUT_HOURS, startHr.toFloat())
        riveClock.setNumberState(STATE_MACHINE, INPUT_MINUTES, startMin.toFloat())

        // Lock exactly what the Rive engine reports as its current values
        riveClock.setTag(startHr.toFloat())
        riveClock.setTag(R.id.topic12, startMin.toFloat())

        riveClock.setBooleanState(STATE_MACHINE, INPUT_ANSWERED, false)
        btnBack.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.white))

        disableCheckButton()
    }

    private fun resetForTryAgain() {
        val activity = requireActivity() as Math2GradeQuestionActivity
        activity.isResultCurrentlyVisible = false
        mainHandler.removeCallbacks(checkRunnable)

        isAnswerChecked = false
        isIncorrectAttempt = false
        isUserInteracting = false

        // Lock where the hands currently are
        val currentHr = getRiveValue(INPUT_HOURS)
        val currentMin = getRiveValue(INPUT_MINUTES)

        riveClock.setTag(currentHr)
        riveClock.setTag(R.id.topic12, currentMin)

        riveClock.setBooleanState(STATE_MACHINE, INPUT_ANSWERED, false)
        stateContainer.visibility = View.INVISIBLE
        seeBtn.visibility = View.GONE
        stateAnswer.text = ""
        answerDisplay.text = ""
        checkBtn.text = "CHECK"
        btnBack.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.white))

        disableCheckButton()

        // Delay restarting the polling so it doesn't catch a frame jitter
        mainHandler.postDelayed(checkRunnable, 300)
    }

    private fun resetUIForNext() {
        val activity = requireActivity() as Math2GradeQuestionActivity
        activity.isResultCurrentlyVisible = false
        activity.hideSuccessAnimation()
        mainHandler.removeCallbacks(checkRunnable)
    }

    private fun showCorrectState() {
        stateContainer.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.green_3))
        circleState.setImageResource(R.drawable.correct_tick_icon)
        stateAnswer.text = "Correct!"
        answerDisplay.text = "Answer: ${String.format("%d:%02d", targetHour, targetMinute)}"
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
        super.onDestroyView()
    }

    override fun onDestroy() {
        mediaPlayer?.release()
        super.onDestroy()
    }
}