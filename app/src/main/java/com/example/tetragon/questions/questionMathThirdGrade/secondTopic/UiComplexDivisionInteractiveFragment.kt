package com.example.tetragon.questions.questionMathThirdGrade.secondTopic

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
import com.example.tetragon.questions.questionMathThirdGrade.Math3GradeQuestionActivity
import com.example.tetragon.questions.questionMathThirdGrade.MathGrade3Type
import kotlin.random.Random

class UiComplexDivisionInteractiveFragment : Fragment(R.layout.fragment_ui_complex_division_interactive) {

    private lateinit var complexDivInteractiveEquationText: TextView
    private lateinit var complexDivInteractiveRive: RiveAnimationView
    private lateinit var complexDivInteractiveAnswerText: TextView
    private lateinit var complexDivInteractiveAnswerBox: ImageView

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

    private var correctAnswer = 0
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
            if (!isAdded || isAnswerChecked || !::complexDivInteractiveRive.isInitialized) return

            val currentChoice = getAnswerChoiceValue().toInt()

            if (currentChoice > 0) {
                val selectedValue = optionMapping[currentChoice]
                if (selectedValue != null) {
                    complexDivInteractiveAnswerText.text = selectedValue.toString()
                    complexDivInteractiveAnswerText.visibility = View.VISIBLE
                }
                if (!checkBtn.isEnabled) enableCheckButton()
            } else {
                if (checkBtn.isEnabled) {
                    disableCheckButton()
                    complexDivInteractiveAnswerText.visibility = View.GONE
                }
            }
            mainHandler.postDelayed(this, 50)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initViews(view)
        setupInitialButtonState()

        if (!isInitialized) {
            complexDivInteractiveRive.post {
                setupRiveViewModel()
                generateProblem()
                isInitialized = true
            }
        }
        setupCheckButton()
        mainHandler.post(checkRunnable)
    }

    private fun initViews(view: View) {
        complexDivInteractiveEquationText = view.findViewById(R.id.complexDivInteractiveEquationText)
        complexDivInteractiveAnswerText = view.findViewById(R.id.complexDivInteractiveAnswerText)
        complexDivInteractiveAnswerBox = view.findViewById(R.id.complexDivInteractiveAnswerBox)
        complexDivInteractiveRive = view.findViewById(R.id.complexDivInteractiveRive)

        val activity = requireActivity() as Math3GradeQuestionActivity
        checkBtn = activity.findViewById(R.id.check_enabled_btn)
        checkBtnBack = activity.findViewById(R.id.check_enabled_button_background)
        btnBack = activity.findViewById(R.id.btnBackground)
        seeBtn = activity.findViewById(R.id.see_btn_container)
        seeEnabledButton = activity.findViewById(R.id.see_enabled_btn)
        stateAnswer = activity.findViewById(R.id.stateAnswer)
        answerDisplay = activity.findViewById(R.id.answer)
    }

    private fun generateProblem() {
        val divisor = Random.nextInt(2, 11)
        correctAnswer = Random.nextInt(2, 11)
        val dividend = divisor * correctAnswer

        val fullText = "$dividend ÷ $divisor ="
        val spannable = SpannableStringBuilder(fullText)
        val signIndex = fullText.indexOf("÷")
        if (signIndex != -1) {
            spannable.setSpan(
                ForegroundColorSpan(ContextCompat.getColor(requireContext(), R.color.blue_2)),
                signIndex, signIndex + 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
        complexDivInteractiveEquationText.text = spannable

        val optionSet = mutableSetOf(correctAnswer)
        while (optionSet.size < 4) {
            val distractor = correctAnswer + Random.nextInt(-4, 5)
            if (distractor != correctAnswer && distractor > 0) {
                optionSet.add(distractor)
            }
        }

        val sortedOptions = optionSet.toList().sorted()

        optionMapping.clear()
        viewModelKeys.forEachIndexed { index, key ->
            val value = sortedOptions[index]
            optionMapping[index + 1] = value
            optionProperties[key]?.value = value.toFloat()
        }

        complexDivInteractiveAnswerBox.setImageResource(R.drawable.answer_blue_box)
        complexDivInteractiveAnswerText.visibility = View.GONE

        isAnswerChecked = false
        isIncorrectAttempt = false
        isFirstAttempt = true
        seeBtn.visibility = View.GONE
        checkBtn.text = getString(R.string.btn_check)
        disableCheckButton()
        setupInitialButtonState()
    }

    private fun checkAnswer() {
        isAnswerChecked = true
        val activity = requireActivity() as Math3GradeQuestionActivity
        val stateContainer = activity.findViewById<FrameLayout>(R.id.stateContainer)
        val circleState = activity.findViewById<ImageView>(R.id.circleState)

        val selectedChoice = getAnswerChoiceValue().toInt()
        val chosenValue = optionMapping[selectedChoice] ?: 0

        activity.isResultCurrentlyVisible = true
        stateContainer.visibility = View.VISIBLE
        complexDivInteractiveRive.setBooleanState(STATE_MACHINE, INPUT_ANSWERED, true)

        if (chosenValue == correctAnswer) {
            playSound(R.raw.correct)
            activity.playSuccessAnimation()
            complexDivInteractiveAnswerBox.setImageResource(R.drawable.answer_correct_box)

            val isFinished = activity.incrementProgress()
            if (isFirstAttempt) activity.totalXp += MathGrade3Type.DIVISION_INTERACTIVE.xp
            activity.handleCorrectAnswer()

            isIncorrectAttempt = false
            checkBtn.text = if (isFinished) getString(R.string.btn_finish) else getString(R.string.btn_continue)
            showCorrectState(stateContainer, circleState)
        } else {
            playSound(R.raw.wrong)
            complexDivInteractiveAnswerBox.setImageResource(R.drawable.answer_incorrect_box)
            isIncorrectAttempt = true
            checkBtn.text = getString(R.string.btn_try_again)
            showIncorrectState(stateContainer, circleState)
            setupSeeSolution(stateContainer, circleState)
        }
        isFirstAttempt = false
    }

    private fun enableCheckButton() {
        checkBtn.isEnabled = true
        val activity = requireActivity() as Math3GradeQuestionActivity
        activity.findViewById<FrameLayout>(R.id.check_enabled_btn_container).visibility = View.VISIBLE
        activity.findViewById<FrameLayout>(R.id.check_disabled_btn_container).visibility = View.INVISIBLE
    }

    private fun disableCheckButton() {
        checkBtn.isEnabled = false
        val activity = requireActivity() as Math3GradeQuestionActivity
        activity.findViewById<FrameLayout>(R.id.check_enabled_btn_container).visibility = View.INVISIBLE
        activity.findViewById<FrameLayout>(R.id.check_disabled_btn_container).visibility = View.VISIBLE
    }

    private fun setupCheckButton() {
        checkBtn.setOnClickListener {
            val activity = requireActivity() as Math3GradeQuestionActivity
            if (!isAnswerChecked) {
                if (getAnswerChoiceValue() > 0) checkAnswer()
            } else {
                if (isIncorrectAttempt) {
                    resetForTryAgain()
                } else {
                    if (checkBtn.text == getString(R.string.btn_finish)) activity.navigateToXpGained()
                    else {
                        if (!activity.checkAndTriggerMilestone()) {
                            resetUIForNext()
                            activity.showRandomQuestion()
                        }
                    }
                }
            }
        }
    }

    private fun resetForTryAgain() {
        val activity = requireActivity() as Math3GradeQuestionActivity
        activity.isResultCurrentlyVisible = false
        isAnswerChecked = false
        isIncorrectAttempt = false

        complexDivInteractiveAnswerBox.setImageResource(R.drawable.answer_blue_box)
        complexDivInteractiveAnswerText.visibility = View.GONE
        activity.findViewById<FrameLayout>(R.id.stateContainer).visibility = View.INVISIBLE
        seeBtn.visibility = View.GONE

        complexDivInteractiveRive.setBooleanState(STATE_MACHINE, INPUT_ANSWERED, false)
        complexDivInteractiveRive.setNumberState(STATE_MACHINE, INPUT_CHOICE, 0f)

        disableCheckButton()
        setupInitialButtonState()
        mainHandler.post(checkRunnable)
    }

    private fun setupSeeSolution(stateContainer: FrameLayout, circleState: ImageView) {
        seeEnabledButton.setOnClickListener {
            seeBtn.visibility = View.GONE
            stateAnswer.text = getString(R.string.state_solution)
            answerDisplay.text = getString(R.string.label_answer, correctAnswer.toString())
            answerDisplay.visibility = View.VISIBLE
            checkBtn.text = getString(R.string.btn_continue)

            complexDivInteractiveAnswerBox.setImageResource(R.drawable.answer_solution_box)
            complexDivInteractiveAnswerText.text = correctAnswer.toString()
            complexDivInteractiveAnswerText.visibility = View.VISIBLE
            circleState.setImageResource(R.drawable.solution_lamp_icon)

            isIncorrectAttempt = false
            applyButtonColors(R.color.black_3, R.color.black_2, R.color.gray_2)
            stateContainer.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.gray_2))

            val correctIdx = optionMapping.filterValues { it == correctAnswer }.keys.firstOrNull()
            correctIdx?.let { complexDivInteractiveRive.setNumberState(STATE_MACHINE, INPUT_CHOICE, it.toFloat()) }
        }
    }

    private fun showCorrectState(container: FrameLayout, circle: ImageView) {
        container.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.green_3))
        circle.setImageResource(R.drawable.correct_tick_icon)
        stateAnswer.text = getString(R.string.state_correct)
        answerDisplay.text = getString(R.string.label_answer, correctAnswer.toString())
        answerDisplay.visibility = View.VISIBLE
        applyButtonColors(R.color.green_1, R.color.green_2, R.color.green_4)
    }

    private fun showIncorrectState(container: FrameLayout, circle: ImageView) {
        container.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.red_4))
        circle.setImageResource(R.drawable.wrong_circle)
        stateAnswer.text = getString(R.string.state_incorrect)
        answerDisplay.visibility = View.GONE
        seeBtn.visibility = View.VISIBLE
        seeEnabledButton.text = getString(R.string.btn_see_solution)
        applyButtonColors(R.color.red_1, R.color.red_2, R.color.red_4)
    }

    private fun setupRiveViewModel() {
        try {
            val file = complexDivInteractiveRive.controller.file ?: return
            val vm = file.getViewModelByName("ViewModel1") ?: return
            val vmi = vm.createDefaultInstance()
            complexDivInteractiveRive.controller.stateMachines.firstOrNull()?.viewModelInstance = vmi

            viewModelKeys.forEach { key ->
                vmi.getNumberProperty(key)?.let { prop -> optionProperties[key] = prop }
            }
        } catch (e: Exception) { e.printStackTrace() }
    }

    private fun getAnswerChoiceValue(): Float {
        var value = 0f
        complexDivInteractiveRive.controller.stateMachines.firstOrNull()?.inputs?.forEach {
            if (it.name == INPUT_CHOICE && it is SMINumber) value = it.value
        }
        return value
    }

    private fun setupInitialButtonState() {
        btnBack.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.white))
        applyButtonColors(R.color.blue_2, R.color.blue_1, R.color.white)
    }

    private fun resetUIForNext() {
        val activity = requireActivity() as Math3GradeQuestionActivity
        activity.isResultCurrentlyVisible = false
        activity.hideSuccessAnimation()
        activity.findViewById<FrameLayout>(R.id.stateContainer).visibility = View.INVISIBLE
        seeBtn.visibility = View.GONE
        checkBtn.text = getString(R.string.btn_check)
        setupInitialButtonState()
    }

    private fun applyButtonColors(buttonColor: Int, backColor: Int, backgroundColor: Int) {
        val context = requireContext()
        checkBtn.backgroundTintList = ContextCompat.getColorStateList(context, buttonColor)
        checkBtnBack.backgroundTintList = ContextCompat.getColorStateList(context, backColor)
        btnBack.setBackgroundColor(ContextCompat.getColor(context, backgroundColor))
        checkBtnBack.invalidate()
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