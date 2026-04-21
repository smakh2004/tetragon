package com.example.tetragon.questions.questionMathSecondGrade.fifthTopic

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
import com.example.tetragon.R
import com.example.tetragon.questions.questionMathSecondGrade.Math2GradeQuestionActivity
import com.example.tetragon.questions.questionMathSecondGrade.MathGrade2Type

class UiMeterToCmFragment : Fragment(R.layout.fragment_ui_meter_to_cm) {

    private lateinit var instructionText: TextView
    private lateinit var firstNumber: TextView
    private lateinit var unitMeterText: TextView
    private lateinit var riveAnimation: RiveAnimationView
    private lateinit var problemImage: ImageView
    private lateinit var problemAnswerText: TextView

    private val viewModelKeys = arrayOf("0_number", "1_number", "2_number", "3_number", "4_number")
    private val optionProperties = mutableMapOf<String, ViewModelNumberProperty>()
    private val optionMapping = mutableMapOf<Int, Int>()

    private lateinit var checkBtn: Button
    private lateinit var checkBtnBack: View
    private lateinit var btnBack: ConstraintLayout
    private lateinit var seeBtn: FrameLayout
    private lateinit var seeEnabledButton: Button
    private lateinit var stateAnswer: TextView
    private lateinit var answerDisplay: TextView
    private lateinit var stateContainer: FrameLayout
    private lateinit var circleState: ImageView

    private var targetMeter: Int = 0
    private var correctAnswerCm: Int = 0
    private var isAnswerChecked = false
    private var isIncorrectAttempt = false
    private var isFirstAttempt = true
    private var isInitialized = false
    private var mediaPlayer: MediaPlayer? = null

    private val STATE_MACHINE = "State Machine 1"
    private val INPUT_ANSWERED = "answered"
    private val INPUT_CHOICE = "answerChoice"

    private val mainHandler = Handler(Looper.getMainLooper())
    private val checkRunnable = object : Runnable {
        override fun run() {
            if (!isAdded || isAnswerChecked || !::riveAnimation.isInitialized) return

            val currentChoice = getAnswerChoiceValue().toInt()
            val mappedValue = optionMapping[currentChoice] ?: 0

            problemAnswerText.text = mappedValue.toString()
            problemAnswerText.visibility = View.VISIBLE

            if (currentChoice > 0 && !checkBtn.isEnabled) {
                enableCheckButton()
            }
            mainHandler.postDelayed(this, 50)
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
                vmi.getNumberProperty(key)?.let { prop -> optionProperties[key] = prop }
            }
            vmi.getStringProperty("unit")?.let { it.value = "cm" }
        } catch (e: Exception) { e.printStackTrace() }
    }

    private fun generateProblem() {
        targetMeter = (1..8).random()
        correctAnswerCm = targetMeter * 100

        firstNumber.text = targetMeter.toString()
        // Style the "m =" part blue
        setStyledText(unitMeterText, "m =", arrayOf("m"))

        optionMapping.clear()
        for (i in 0..8) {
            val value = i * 100
            optionMapping[i] = value
            if (i % 2 == 0) {
                val vmIndex = i / 2
                optionProperties["${vmIndex}_number"]?.value = value.toFloat()
            }
        }
        resetFragmentState()
    }

    private fun initViews(view: View) {
        instructionText = view.findViewById(R.id.instructionText)
        setStyledText(instructionText, "Convert meter to centimeter.", arrayOf("meter", "centimeter"))

        firstNumber = view.findViewById(R.id.firstNumber)
        unitMeterText = view.findViewById(R.id.unitMeterText)
        riveAnimation = view.findViewById(R.id.riveAnimation)
        problemImage = view.findViewById(R.id.problemImage)
        problemAnswerText = view.findViewById(R.id.problemAnswerText)

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
            } else if (isIncorrectAttempt) {
                resetForTryAgain()
            } else {
                if (checkBtn.text == "FINISH") activity.navigateToXpGained()
                else {
                    if (!activity.checkAndTriggerMilestone()) {
                        resetUIForNext()
                        activity.showRandomQuestion()
                    }
                }
            }
        }
        setupInitialButtonState()
        disableCheckButton()
    }

    private fun checkAnswer() {
        val selectedChoice = getAnswerChoiceValue().toInt()
        val userValue = optionMapping[selectedChoice] ?: -1

        isAnswerChecked = true
        val activity = requireActivity() as Math2GradeQuestionActivity
        activity.isResultCurrentlyVisible = true
        stateContainer.visibility = View.VISIBLE

        riveAnimation.setBooleanState(STATE_MACHINE, INPUT_ANSWERED, true)

        if (userValue == correctAnswerCm) {
            playSound(R.raw.correct)
            problemImage.setImageResource(R.drawable.answer_correct_box)
            activity.playSuccessAnimation()

            val isFinished = activity.incrementProgress()
            if (isFirstAttempt) activity.totalXp += MathGrade2Type.METER_TO_CM.xp

            activity.handleCorrectAnswer()
            isIncorrectAttempt = false
            checkBtn.text = if (isFinished) "FINISH" else "CONTINUE"
            showCorrectState()
        } else {
            playSound(R.raw.wrong)
            problemImage.setImageResource(R.drawable.answer_incorrect_box)
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

            val solutionText = "1 m = 100 cm, so $targetMeter m = $correctAnswerCm cm"
            setStyledText(answerDisplay, solutionText, arrayOf("m", "cm"))

            answerDisplay.visibility = View.VISIBLE
            circleState.setImageResource(R.drawable.solution_lamp_icon)
            stateContainer.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.gray_2))

            problemImage.setImageResource(R.drawable.answer_solution_box)
            problemAnswerText.text = correctAnswerCm.toString()

            riveAnimation.setBooleanState(STATE_MACHINE, INPUT_ANSWERED, true)
            val correctChoiceIndex = (correctAnswerCm / 100).toFloat()
            riveAnimation.setNumberState(STATE_MACHINE, INPUT_CHOICE, correctChoiceIndex)

            applyButtonColors(R.color.black_3, R.color.black_2, R.color.gray_2)
            checkBtn.text = "CONTINUE"
            isIncorrectAttempt = false
            isAnswerChecked = true
        }
    }

    private fun resetFragmentState() {
        isAnswerChecked = false
        isIncorrectAttempt = false
        checkBtn.text = "CHECK"
        stateContainer.visibility = View.INVISIBLE
        seeBtn.visibility = View.GONE
        problemImage.setImageResource(R.drawable.answer_blue_box)
        problemAnswerText.visibility = View.GONE

        riveAnimation.setBooleanState(STATE_MACHINE, INPUT_ANSWERED, false)
        riveAnimation.setNumberState(STATE_MACHINE, INPUT_CHOICE, 0f)

        setupInitialButtonState()
        disableCheckButton()
    }

    private fun resetForTryAgain() {
        val activity = requireActivity() as Math2GradeQuestionActivity
        activity.isResultCurrentlyVisible = false
        mainHandler.removeCallbacks(checkRunnable)
        resetFragmentState()
        mainHandler.postDelayed(checkRunnable, 200)
    }

    private fun resetUIForNext() {
        val activity = requireActivity() as Math2GradeQuestionActivity
        activity.isResultCurrentlyVisible = false
        activity.hideSuccessAnimation()
        stateContainer.visibility = View.INVISIBLE
        setupInitialButtonState()
        mainHandler.removeCallbacks(checkRunnable)
    }

    private fun showCorrectState() {
        stateContainer.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.green_3))
        circleState.setImageResource(R.drawable.correct_tick_icon)
        stateAnswer.text = "Correct!"

        val resultText = "$targetMeter m = $correctAnswerCm cm"
        setStyledText(answerDisplay, resultText, arrayOf("m", "cm"))

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

    private fun setupInitialButtonState() {
        btnBack.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.white))
        checkBtn.backgroundTintList = ContextCompat.getColorStateList(requireContext(), R.color.blue_2)
        checkBtnBack.backgroundTintList = ContextCompat.getColorStateList(requireContext(), R.color.blue_1)
    }

    private fun getAnswerChoiceValue(): Float {
        var value = 0f
        riveAnimation.controller.stateMachines.firstOrNull()?.inputs?.forEach {
            if (it.name == INPUT_CHOICE && it is SMINumber) value = it.value
        }
        return value
    }

    private fun enableCheckButton() {
        checkBtn.isEnabled = true
        requireActivity().findViewById<FrameLayout>(R.id.check_enabled_btn_container).visibility = View.VISIBLE
        requireActivity().findViewById<FrameLayout>(R.id.check_disabled_btn_container).visibility = View.INVISIBLE
        checkBtn.backgroundTintList = ContextCompat.getColorStateList(requireContext(), R.color.blue_2)
        checkBtnBack.backgroundTintList = ContextCompat.getColorStateList(requireContext(), R.color.blue_1)
    }

    private fun disableCheckButton() {
        checkBtn.isEnabled = false
        requireActivity().findViewById<FrameLayout>(R.id.check_enabled_btn_container).visibility = View.INVISIBLE
        requireActivity().findViewById<FrameLayout>(R.id.check_disabled_btn_container).visibility = View.VISIBLE
    }

    private fun applyButtonColors(btn: Int, back: Int, bg: Int) {
        checkBtn.backgroundTintList = ContextCompat.getColorStateList(requireContext(), btn)
        checkBtnBack.backgroundTintList = ContextCompat.getColorStateList(requireContext(), back)
        btnBack.setBackgroundColor(ContextCompat.getColor(requireContext(), bg))
    }

    private fun setStyledText(textView: TextView, fullText: String, wordsToStyle: Array<String>) {
        val spannable = SpannableString(fullText)
        val blueColor = ContextCompat.getColor(requireContext(), R.color.blue_2)
        wordsToStyle.forEach { word ->
            var start = fullText.indexOf(word)
            while (start != -1) {
                spannable.setSpan(ForegroundColorSpan(blueColor), start, start + word.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                start = fullText.indexOf(word, start + word.length)
            }
        }
        textView.text = spannable
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