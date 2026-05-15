package com.tetragon.app.questions.questionPhysicsSevenGrade.firstTopicLength

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
import com.tetragon.app.questions.questionPhysicsSevenGrade.Physics7GradeQuestionActivity
import com.tetragon.app.questions.questionPhysicsSevenGrade.PhysicsGrade7Type

class UiTemperatureFragment : Fragment(R.layout.fragment_ui_temperature) {

    private lateinit var questionText: TextView
    private lateinit var riveAnimation: RiveAnimationView

    private val viewModelKeys = arrayOf("0_number", "1_number", "2_number", "3_number", "4_number", "5_number")
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

    private var targetTemp: Int = 0
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
            val currentVal = getAnswerChoiceValue()
            if (currentVal > 0f && !checkBtn.isEnabled) {
                enableCheckButton()
            } else if (currentVal <= 0f && checkBtn.isEnabled) {
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
                vmi.getNumberProperty(key)?.let { prop -> optionProperties[key] = prop }
            }
        } catch (e: Exception) { e.printStackTrace() }
    }

    private fun generateProblem() {
        val optionsSet = mutableSetOf<Int>()
        while (optionsSet.size < 5) {
            val nextTemp = (1..30).random() * 5
            optionsSet.add(nextTemp)
        }
        val sortedOptions = mutableListOf(0)
        sortedOptions.addAll(optionsSet.sorted())
        targetTemp = sortedOptions.subList(1, 6).random()

        optionMapping.clear()
        viewModelKeys.forEachIndexed { index, key ->
            val value = sortedOptions[index]
            optionProperties[key]?.value = value.toFloat()
            optionMapping[index] = value
        }
        updateQuestionText()
        resetFragmentState()
    }

    private fun updateQuestionText() {
        val tempString = getString(R.string.temp_celsius_format, targetTemp)
        val thermoString = getString(R.string.unit_thermometer)
        val sentence = getString(R.string.question_show_temperature, tempString, thermoString)

        val spannable = SpannableString(sentence)
        val blueColor = ContextCompat.getColor(requireContext(), R.color.blue_2)

        val tempStart = sentence.indexOf(tempString)
        if (tempStart != -1) {
            spannable.setSpan(ForegroundColorSpan(blueColor), tempStart, tempStart + tempString.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }

        val thermoStart = sentence.indexOf(thermoString)
        if (thermoStart != -1) {
            spannable.setSpan(ForegroundColorSpan(blueColor), thermoStart, thermoStart + thermoString.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        questionText.text = spannable
    }

    private fun checkAnswer() {
        val selectedIndex = getAnswerChoiceValue().toInt()
        val userValue = optionMapping[selectedIndex] ?: -999

        isAnswerChecked = true
        val activity = requireActivity() as Physics7GradeQuestionActivity
        activity.isResultCurrentlyVisible = true
        stateContainer.visibility = View.VISIBLE

        riveAnimation.setBooleanState(STATE_MACHINE, INPUT_ANSWERED, true)

        if (userValue == targetTemp) {
            playSound(R.raw.correct)
            activity.playSuccessAnimation()
            val isFinished = activity.incrementProgress()
            if (isFirstAttempt) activity.totalXp += PhysicsGrade7Type.TEMPERATURE.xp
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
        answerDisplay.text = getString(R.string.solution_temp_correct, targetTemp)
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
            answerDisplay.text = getString(R.string.solution_temp_explanation, targetTemp)
            answerDisplay.visibility = View.VISIBLE
            circleState.setImageResource(R.drawable.solution_lamp_icon)
            stateContainer.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.gray_2))

            riveAnimation.setBooleanState(STATE_MACHINE, INPUT_ANSWERED, true)
            val correctIdx = optionMapping.filterValues { it == targetTemp }.keys.firstOrNull()
            correctIdx?.let {
                riveAnimation.setNumberState(STATE_MACHINE, INPUT_CHOICE, it.toFloat())
            }

            applyButtonColors(R.color.black_3, R.color.black_2, R.color.gray_2)
            checkBtn.text = getString(R.string.btn_continue)
            isIncorrectAttempt = false
            isAnswerChecked = true
        }
    }

    private fun initViews(view: View) {
        questionText = view.findViewById(R.id.questionText)
        riveAnimation = view.findViewById(R.id.topic12)

        val activity = requireActivity() as Physics7GradeQuestionActivity
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
                    val act = requireActivity() as Physics7GradeQuestionActivity
                    if (checkBtn.text == getString(R.string.btn_finish)) {
                        act.navigateToXpGained()
                    } else {
                        val isMilestoneActive = act.checkAndTriggerMilestone()
                        if (!isMilestoneActive) {
                            resetUIForNext()
                            act.showRandomQuestion()
                        }
                    }
                }
            }
        }
        btnBack.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.white))
    }

    private fun getAnswerChoiceValue(): Float {
        var value = -1f
        riveAnimation.controller.stateMachines.firstOrNull()?.inputs?.forEach { input ->
            if (input.name == INPUT_CHOICE && input is SMINumber) {
                value = input.value
            }
        }
        return value
    }

    private fun hideResultUI() {
        stateContainer.visibility = View.INVISIBLE
        seeBtn.visibility = View.GONE
        stateAnswer.text = ""
        answerDisplay.text = ""
        btnBack.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.white))
        val activity = requireActivity() as Physics7GradeQuestionActivity
        activity.isResultCurrentlyVisible = false
    }

    private fun resetFragmentState() {
        isAnswerChecked = false
        isIncorrectAttempt = false
        hideResultUI()
        riveAnimation.setBooleanState(STATE_MACHINE, INPUT_ANSWERED, false)
        riveAnimation.setNumberState(STATE_MACHINE, INPUT_CHOICE, -1f)
        disableCheckButton()
    }

    private fun resetForTryAgain() {
        isAnswerChecked = false
        isIncorrectAttempt = false
        hideResultUI()
        riveAnimation.setBooleanState(STATE_MACHINE, INPUT_ANSWERED, false)
        riveAnimation.setNumberState(STATE_MACHINE, INPUT_CHOICE, -1f)
        disableCheckButton()
        mainHandler.post(checkRunnable)
    }

    private fun resetUIForNext() {
        val act = requireActivity() as Physics7GradeQuestionActivity
        act.hideSuccessAnimation()
        mainHandler.removeCallbacks(checkRunnable)
        act.isResultCurrentlyVisible = false
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