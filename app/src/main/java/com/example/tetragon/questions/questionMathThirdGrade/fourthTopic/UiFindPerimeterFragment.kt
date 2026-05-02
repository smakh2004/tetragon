package com.example.tetragon.questions.questionMathThirdGrade.fourthTopic

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
import com.example.tetragon.questions.questionMathThirdGrade.Math3GradeQuestionActivity
import com.example.tetragon.questions.questionMathThirdGrade.MathGrade3Type

class UiFindPerimeterFragment : Fragment(R.layout.fragment_ui_find_perimeter) {

    private lateinit var questionText: TextView
    private lateinit var perimeterValueDisplay: TextView
    private lateinit var riveRectangle: RiveAnimationView

    private lateinit var checkBtn: Button
    private lateinit var checkBtnBack: View
    private lateinit var btnBack: ConstraintLayout
    private lateinit var seeBtn: FrameLayout
    private lateinit var seeEnabledButton: Button
    private lateinit var stateAnswer: TextView
    private lateinit var answerDisplay: TextView
    private lateinit var stateContainer: FrameLayout
    private lateinit var circleState: ImageView

    private var targetPerimeter: Int = 0
    private var targetWidth: Int = 2
    private var targetHeight: Int = 2

    private var isAnswerChecked = false
    private var isIncorrectAttempt = false
    private var isFirstAttempt = true
    private var isUserInteracting = false
    private var mediaPlayer: MediaPlayer? = null

    private val STATE_MACHINE = "State Machine 1"
    private val INPUT_WIDTH = "width"
    private val INPUT_HEIGHT = "height"
    private val INPUT_ANSWERED = "answered"

    private val NOB_INPUTS = listOf("1_nob", "2_nob", "3_nob", "4_nob")
    private val allowedValues = listOf(2, 4, 6, 8)
    private val mainHandler = Handler(Looper.getMainLooper())

    private val checkRunnable = object : Runnable {
        override fun run() {
            if (!isAdded || isAnswerChecked || !::riveRectangle.isInitialized) return

            val currentW = getRiveValue(INPUT_WIDTH)
            val currentH = getRiveValue(INPUT_HEIGHT)

            if (!isUserInteracting) {
                val lockedValues = riveRectangle.tag as? Pair<Float, Float> ?: Pair(2f, 2f)
                if (Math.abs(currentW - lockedValues.first) > 0.1f ||
                    Math.abs(currentH - lockedValues.second) > 0.1f) {
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
        mainHandler.postDelayed(checkRunnable, 500)
    }

    private fun generateProblem() {
        do {
            targetWidth = allowedValues.random()
            targetHeight = allowedValues.random()
            targetPerimeter = 2 * (targetWidth + targetHeight)
        } while (targetPerimeter == 8)

        updateQuestionText()
        resetFragmentState(2f, 2f)
    }

    private fun updateQuestionText() {
        val isSquare = targetWidth == targetHeight
        val type = if (isSquare) getString(R.string.label_square) else getString(R.string.label_rectangle)
        val perimeterLabel = getString(R.string.label_perimeter)

        val pValueText = getString(R.string.label_perimeter_units, targetPerimeter)
        val sentence = getString(R.string.question_create_shape_perimeter, type)

        val blueColor = ContextCompat.getColor(requireContext(), R.color.blue_2)
        val textColor = ContextCompat.getColor(requireContext(), R.color.text_color)

        val spannableSentence = SpannableString(sentence)
        val typeStart = sentence.indexOf(type)
        val perimeterWordStart = sentence.indexOf(perimeterLabel)

        if (typeStart != -1) {
            spannableSentence.setSpan(ForegroundColorSpan(blueColor), typeStart, typeStart + type.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        if (perimeterWordStart != -1) {
            spannableSentence.setSpan(ForegroundColorSpan(blueColor), perimeterWordStart, perimeterWordStart + perimeterLabel.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        questionText.text = spannableSentence

        val spannableUnits = SpannableString(pValueText)
        spannableUnits.setSpan(ForegroundColorSpan(textColor), 0, pValueText.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        perimeterValueDisplay.text = spannableUnits
    }

    private fun checkAnswer() {
        val userW = getRiveValue(INPUT_WIDTH).toInt()
        val userH = getRiveValue(INPUT_HEIGHT).toInt()
        val userPerimeter = 2 * (userW + userH)

        val isTargetSquare = (targetWidth == targetHeight)
        val isUserSquare = (userW == userH)

        isAnswerChecked = true
        val activity = requireActivity() as Math3GradeQuestionActivity
        activity.isResultCurrentlyVisible = true
        stateContainer.visibility = View.VISIBLE
        riveRectangle.setBooleanState(STATE_MACHINE, INPUT_ANSWERED, true)

        val isCorrect = (userPerimeter == targetPerimeter) && (!isTargetSquare || isUserSquare)

        if (isCorrect) {
            playSound(R.raw.correct)
            activity.playSuccessAnimation()
            val isFinished = activity.incrementProgress()
            if (isFirstAttempt) activity.totalXp += MathGrade3Type.PERIMETER.xp
            activity.handleCorrectAnswer()
            isIncorrectAttempt = false
            checkBtn.text = if (isFinished) getString(R.string.btn_finish) else getString(R.string.btn_continue)
            showCorrectState(userW, userH)
        } else {
            playSound(R.raw.wrong)
            isIncorrectAttempt = true
            checkBtn.text = getString(R.string.btn_try_again)
            showIncorrectState()

            if (userPerimeter == targetPerimeter && isTargetSquare && !isUserSquare) {
                stateAnswer.text = getString(R.string.state_almost_square)
            }
            setupSeeSolution()
        }
        isFirstAttempt = false
    }

    private fun setupSeeSolution() {
        seeEnabledButton.setOnClickListener {
            seeBtn.visibility = View.GONE
            stateAnswer.text = getString(R.string.state_solution)

            val formula = getString(R.string.label_perimeter_formula)
            val calculation = getString(R.string.label_perimeter_calculation, targetWidth, targetHeight, targetPerimeter)
            answerDisplay.text = "$formula\n$calculation"
            answerDisplay.visibility = View.VISIBLE

            circleState.setImageResource(R.drawable.solution_lamp_icon)
            stateContainer.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.gray_2))

            riveRectangle.setNumberState(STATE_MACHINE, INPUT_WIDTH, targetWidth.toFloat())
            riveRectangle.setNumberState(STATE_MACHINE, INPUT_HEIGHT, targetHeight.toFloat())

            val nobValue = calculateNobValue(targetWidth, targetHeight)
            NOB_INPUTS.forEach { name ->
                riveRectangle.setNumberState(STATE_MACHINE, name, nobValue.toFloat())
            }

            riveRectangle.setBooleanState(STATE_MACHINE, INPUT_ANSWERED, true)
            applyButtonColors(R.color.black_3, R.color.black_2, R.color.gray_2)
            checkBtn.text = getString(R.string.btn_continue)
            isIncorrectAttempt = false
        }
    }

    private fun calculateNobValue(w: Int, h: Int): Int {
        return when {
            h == 2 && w == 2 -> 0
            h == 2 && w == 4 -> 1
            h == 2 && w == 6 -> 2
            h == 2 && w == 8 -> 3
            h == 4 && w == 2 -> 4
            h == 4 && w == 4 -> 5
            h == 4 && w == 6 -> 6
            h == 4 && w == 8 -> 7
            h == 6 && w == 2 -> 8
            h == 6 && w == 4 -> 9
            h == 6 && w == 6 -> 10
            h == 6 && w == 8 -> 11
            h == 8 && w == 2 -> 12
            h == 8 && w == 4 -> 13
            h == 8 && w == 6 -> 14
            h == 8 && w == 8 -> 15
            else -> 0
        }
    }

    private fun initViews(view: View) {
        questionText = view.findViewById(R.id.questionText)
        perimeterValueDisplay = view.findViewById(R.id.textView5)
        riveRectangle = view.findViewById(R.id.perimeter)

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
            if (!isAnswerChecked) checkAnswer()
            else if (isIncorrectAttempt) resetForTryAgain()
            else handleNavigation(activity)
        }
        disableCheckButton()
    }

    private fun resetFragmentState(startW: Float, startH: Float) {
        isAnswerChecked = false
        isIncorrectAttempt = false
        isUserInteracting = false
        checkBtn.text = getString(R.string.btn_check)
        stateContainer.visibility = View.INVISIBLE
        seeBtn.visibility = View.GONE

        riveRectangle.setNumberState(STATE_MACHINE, INPUT_WIDTH, startW)
        riveRectangle.setNumberState(STATE_MACHINE, INPUT_HEIGHT, startH)
        NOB_INPUTS.forEach { riveRectangle.setNumberState(STATE_MACHINE, it, 0f) }

        riveRectangle.setBooleanState(STATE_MACHINE, INPUT_ANSWERED, false)
        riveRectangle.tag = Pair(startW, startH)
        btnBack.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.white))
        disableCheckButton()
    }

    private fun resetForTryAgain() {
        val activity = requireActivity() as Math3GradeQuestionActivity
        activity.isResultCurrentlyVisible = false
        mainHandler.removeCallbacks(checkRunnable)

        isAnswerChecked = false
        isIncorrectAttempt = false
        isUserInteracting = false

        riveRectangle.tag = Pair(getRiveValue(INPUT_WIDTH), getRiveValue(INPUT_HEIGHT))
        riveRectangle.setBooleanState(STATE_MACHINE, INPUT_ANSWERED, false)

        stateContainer.visibility = View.INVISIBLE
        seeBtn.visibility = View.GONE
        checkBtn.text = getString(R.string.btn_check)
        btnBack.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.white))

        disableCheckButton()
        mainHandler.postDelayed(checkRunnable, 300)
    }

    private fun handleNavigation(activity: Math3GradeQuestionActivity) {
        if (checkBtn.text == getString(R.string.btn_finish)) activity.navigateToXpGained()
        else if (!activity.checkAndTriggerMilestone()) {
            resetUIForNext()
            activity.showRandomQuestion()
        }
    }

    private fun resetUIForNext() {
        val activity = requireActivity() as Math3GradeQuestionActivity
        activity.isResultCurrentlyVisible = false
        activity.hideSuccessAnimation()
        mainHandler.removeCallbacks(checkRunnable)
    }

    private fun showCorrectState(w: Int, h: Int) {
        stateContainer.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.green_3))
        circleState.setImageResource(R.drawable.correct_tick_icon)
        stateAnswer.text = getString(R.string.state_correct)
        answerDisplay.text = getString(R.string.label_perimeter_calculation, w, h, targetPerimeter)
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

    private fun getRiveValue(inputName: String): Float {
        var value = 0f
        riveRectangle.controller.stateMachines.firstOrNull()?.inputs?.forEach { input ->
            if (input.name == inputName && input is SMINumber) value = input.value
        }
        return value
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