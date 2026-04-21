package com.example.tetragon.questions.questionMathSecondGrade.eighthTopic

import android.media.MediaPlayer
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.view.View
import android.widget.*
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import app.rive.runtime.kotlin.RiveAnimationView
import app.rive.runtime.kotlin.core.SMINumber
import app.rive.runtime.kotlin.core.ViewModelNumberProperty
import com.example.tetragon.R
import com.example.tetragon.questions.questionMathSecondGrade.Math2GradeQuestionActivity
import com.example.tetragon.questions.questionMathSecondGrade.MathGrade2Type
import kotlin.random.Random

class UiDivisionInteractiveFragment : Fragment(R.layout.fragment_ui_division_interactive) {

    private lateinit var questionText: TextView
    private lateinit var divEquationText: TextView
    private lateinit var riveAnimation: RiveAnimationView
    private lateinit var divAnswerText: TextView
    private lateinit var divAnswerBox: ImageView

    private val viewModelKeys = arrayOf("1_number", "2_number", "3_number", "4_number")
    private val optionProperties = mutableMapOf<String, ViewModelNumberProperty>()
    private val optionMapping = mutableMapOf<Int, Int>()

    private lateinit var checkBtn: Button
    private lateinit var checkBtnBack: View
    private lateinit var btnBack: View
    private lateinit var seeBtn: FrameLayout
    private lateinit var seeEnabledButton: Button
    private lateinit var stateAnswer: TextView
    private lateinit var answerDisplay: TextView
    private lateinit var stateContainer: FrameLayout
    private lateinit var circleState: ImageView

    private var correctAnswer: Int = 0
    private var currentProblemString: String = ""

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

            if (currentChoice > 0) {
                val selectedValue = optionMapping[currentChoice]
                if (selectedValue != null) {
                    divAnswerText.text = selectedValue.toString()
                    divAnswerText.visibility = View.VISIBLE
                }

                if (!checkBtn.isEnabled) {
                    enableCheckButton()
                }
            } else {
                if (checkBtn.isEnabled) {
                    disableCheckButton()
                    divAnswerText.visibility = View.GONE
                }
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
                vmi.getNumberProperty(key)?.let { prop ->
                    optionProperties[key] = prop
                }
            }
        } catch (e: Exception) { e.printStackTrace() }
    }

    // ---------------------- PROBLEM GENERATION ----------------------

    private fun generateProblem() {
        // Logic to ensure whole numbers: Dividend = Divisor * Quotient
        val divisor = Random.nextInt(2, 10)
        correctAnswer = Random.nextInt(1, 10)
        val dividend = divisor * correctAnswer

        currentProblemString = "$dividend ÷ $divisor ="

        val optionsSet = mutableSetOf(correctAnswer)
        while (optionsSet.size < 4) {
            val wrong = Random.nextInt(1, 20)
            if (wrong != correctAnswer) optionsSet.add(wrong)
        }

        val sortedOptions = optionsSet.toList().sorted()
        optionMapping.clear()

        viewModelKeys.forEachIndexed { index, key ->
            val value = sortedOptions[index]
            optionMapping[index + 1] = value
            optionProperties[key]?.value = value.toFloat()
        }

        if (isAdded) {
            setColoredEquation(currentProblemString)
            divAnswerText.visibility = View.GONE
            divAnswerBox.setImageResource(R.drawable.answer_blue_box)
            resetFragmentState()
        }
    }

    private fun setColoredEquation(equation: String) {
        val spannable = SpannableStringBuilder(equation)
        val divIconIndex = equation.indexOf("÷")

        if (divIconIndex != -1) {
            spannable.setSpan(
                ForegroundColorSpan(ContextCompat.getColor(requireContext(), R.color.blue_2)),
                divIconIndex,
                divIconIndex + 1,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
        divEquationText.text = spannable
    }

    // ---------------------- CHECKING LOGIC ----------------------

    private fun checkAnswer() {
        val selectedIndex = getAnswerChoiceValue().toInt()
        val userValue = optionMapping[selectedIndex] ?: -1

        isAnswerChecked = true
        val activity = requireActivity() as Math2GradeQuestionActivity
        activity.isResultCurrentlyVisible = true
        stateContainer.visibility = View.VISIBLE
        riveAnimation.setBooleanState(STATE_MACHINE, INPUT_ANSWERED, true)

        divAnswerText.text = userValue.toString()
        divAnswerText.visibility = View.VISIBLE

        if (userValue == correctAnswer) {
            playSound(R.raw.correct)
            divAnswerBox.setImageResource(R.drawable.answer_correct_box)
            activity.playSuccessAnimation()
            val isFinished = activity.incrementProgress()

            if (isFirstAttempt) {
                // Assuming you have a DIV_INTERACTIVE constant, otherwise default to multiplication or common XP
                activity.totalXp += MathGrade2Type.DIVISION_INTERACTIVE.xp
            }

            activity.handleCorrectAnswer()
            isIncorrectAttempt = false
            checkBtn.text = if (isFinished) "FINISH" else "CONTINUE"
            showCorrectState()
        } else {
            playSound(R.raw.wrong)
            divAnswerBox.setImageResource(R.drawable.answer_incorrect_box)
            isIncorrectAttempt = true
            checkBtn.text = "TRY AGAIN"
            showIncorrectState()
            setupSeeSolution()
        }
        isFirstAttempt = false
    }

    private fun showCorrectState() {
        stateContainer.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.green_3))
        circleState.setImageResource(R.drawable.correct_tick_icon)
        stateAnswer.text = "Correct!"
        answerDisplay.text = "Answer: $correctAnswer"
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

    private fun setupSeeSolution() {
        seeEnabledButton.setOnClickListener {
            seeBtn.visibility = View.GONE
            stateAnswer.text = "Solution"
            answerDisplay.text = "Answer: $correctAnswer"
            answerDisplay.visibility = View.VISIBLE
            circleState.setImageResource(R.drawable.solution_lamp_icon)

            checkBtn.text = "CONTINUE"
            divAnswerBox.setImageResource(R.drawable.answer_solution_box)
            divAnswerText.text = correctAnswer.toString()
            divAnswerText.visibility = View.VISIBLE

            isAnswerChecked = true
            isIncorrectAttempt = false

            applyButtonColors(R.color.black_3, R.color.black_2, R.color.gray_2)
            stateContainer.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.gray_2))

            val correctIdx = optionMapping.filterValues { it == correctAnswer }.keys.firstOrNull()
            correctIdx?.let { riveAnimation.setNumberState(STATE_MACHINE, INPUT_CHOICE, it.toFloat()) }
        }
    }

    private fun initViews(view: View) {
        questionText = view.findViewById(R.id.divQuestionText)
        divEquationText = view.findViewById(R.id.divEquationText)
        divAnswerText = view.findViewById(R.id.divAnswerText)
        divAnswerBox = view.findViewById(R.id.divAnswerBox)
        riveAnimation = view.findViewById(R.id.divRiveAnimation)

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
                    if (checkBtn.text == "FINISH") activity.navigateToXpGained()
                    else {
                        val isMilestoneActive = activity.checkAndTriggerMilestone()
                        if (!isMilestoneActive) {
                            resetUIForNext()
                            activity.showRandomQuestion()
                        }
                    }
                }
            }
        }
        setupInitialButtonState()
    }

    private fun setupInitialButtonState() {
        btnBack.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.white))
        checkBtn.backgroundTintList = ContextCompat.getColorStateList(requireContext(), R.color.blue_2)
        checkBtnBack.backgroundTintList = ContextCompat.getColorStateList(requireContext(), R.color.blue_1)
        checkBtn.isEnabled = false
    }

    private fun resetForTryAgain() {
        val activity = requireActivity() as Math2GradeQuestionActivity
        activity.isResultCurrentlyVisible = false
        isAnswerChecked = false
        isIncorrectAttempt = false
        divAnswerText.visibility = View.GONE
        stateContainer.visibility = View.INVISIBLE
        seeBtn.visibility = View.GONE
        divAnswerBox.setImageResource(R.drawable.answer_blue_box)
        riveAnimation.setBooleanState(STATE_MACHINE, INPUT_ANSWERED, false)
        riveAnimation.setNumberState(STATE_MACHINE, INPUT_CHOICE, 0f)

        disableCheckButton()
        setupInitialButtonState()

        mainHandler.post(checkRunnable)
    }

    private fun resetUIForNext() {
        val activity = requireActivity() as Math2GradeQuestionActivity
        activity.isResultCurrentlyVisible = false
        activity.hideSuccessAnimation()
        stateContainer.visibility = View.INVISIBLE
        stateAnswer.text = ""
        answerDisplay.text = ""
        answerDisplay.visibility = View.VISIBLE
        seeBtn.visibility = View.GONE
        checkBtn.text = "CHECK"
        setupInitialButtonState()
    }

    private fun resetFragmentState() {
        isAnswerChecked = false
        isIncorrectAttempt = false
        stateContainer.visibility = View.INVISIBLE
        riveAnimation.setBooleanState(STATE_MACHINE, INPUT_ANSWERED, false)
        riveAnimation.setNumberState(STATE_MACHINE, INPUT_CHOICE, 0f)
        disableCheckButton()
    }

    private fun getAnswerChoiceValue(): Float {
        var value = 0f
        riveAnimation.controller.stateMachines.firstOrNull()?.inputs?.forEach {
            if (it.name == INPUT_CHOICE && it is SMINumber) value = it.value
        }
        return value
    }

    private fun disableCheckButton() {
        checkBtn.isEnabled = false
        checkBtn.text = "CHECK"
        checkBtn.backgroundTintList = ContextCompat.getColorStateList(requireContext(), R.color.blue_2)
        checkBtnBack.backgroundTintList = ContextCompat.getColorStateList(requireContext(), R.color.blue_1)
        btnBack.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.white))

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