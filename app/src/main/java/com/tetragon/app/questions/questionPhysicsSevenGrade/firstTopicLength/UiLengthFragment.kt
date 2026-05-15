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

class UiLengthFragment : Fragment(R.layout.fragment_ui_length) {

    private lateinit var questionText: TextView
    private lateinit var riveAnimation: RiveAnimationView

    private val viewModelKeys = arrayOf("1_number", "2_number", "3_number", "4_number")
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

    private var inputValue: Int = 0
    private var correctAnswer: Int = 0
    private var conversionType = 0 // 0 = m→cm, 1 = km→m

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
                vmi.getNumberProperty(key)?.let { prop ->
                    optionProperties[key] = prop
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun generateProblem() {
        conversionType = (0..1).random()

        if (conversionType == 0) {
            inputValue = (2..12).random()
            correctAnswer = inputValue * 100
        } else {
            inputValue = (2..9).random()
            correctAnswer = inputValue * 1000
        }

        val optionsSet = mutableSetOf(correctAnswer)
        while (optionsSet.size < 4) {
            val wrong = if (conversionType == 0) {
                (1..15).random() * 100
            } else {
                val candidate = (1..9).random() * 1000
                if (candidate != correctAnswer) candidate else (candidate + 1000).coerceAtMost(9000)
            }
            if (wrong != correctAnswer && wrong > 0 && wrong <= 9000) {
                optionsSet.add(wrong)
            }
        }

        val sorted = optionsSet.toList().sorted()
        optionMapping.clear()

        viewModelKeys.forEachIndexed { index, key ->
            val value = sorted[index]
            optionMapping[index + 1] = value
            optionProperties[key]?.value = value.toFloat()
        }

        riveAnimation.controller.stateMachines.firstOrNull()?.viewModelInstance?.let { vmi ->
            vmi.getStringProperty("unit")?.let { unitProp ->
                unitProp.value = if (conversionType == 0) "cm" else "m"
            }
        }

        if (isAdded) {
            updateQuestionText()
            resetFragmentState()
        }
    }

    private fun updateQuestionText() {
        val unitFromStr = if (conversionType == 0) getString(R.string.unit_meters_full) else getString(R.string.unit_kilometers_full)
        val unitToStr = if (conversionType == 0) getString(R.string.unit_centimeters_full) else getString(R.string.unit_meters_full)

        val sentence = getString(R.string.question_convert_length, inputValue, unitFromStr, unitToStr)

        val spannable = SpannableString(sentence)
        val blue = ContextCompat.getColor(requireContext(), R.color.blue_2)

        val numberPart = "$inputValue"
        val numberStart = sentence.indexOf(numberPart)
        if (numberStart != -1) {
            spannable.setSpan(ForegroundColorSpan(blue), numberStart, numberStart + numberPart.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }

        val fromStart = sentence.indexOf(unitFromStr)
        if (fromStart != -1) {
            spannable.setSpan(ForegroundColorSpan(blue), fromStart, fromStart + unitFromStr.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }

        val toStart = sentence.lastIndexOf(unitToStr)
        if (toStart != -1) {
            spannable.setSpan(ForegroundColorSpan(blue), toStart, toStart + unitToStr.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
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
        val selectedIndex = getAnswerChoiceValue().toInt()
        val userValue = optionMapping[selectedIndex] ?: -1

        isAnswerChecked = true
        val activity = requireActivity() as Physics7GradeQuestionActivity
        activity.isResultCurrentlyVisible = true
        stateContainer.visibility = View.VISIBLE
        riveAnimation.setBooleanState(STATE_MACHINE, INPUT_ANSWERED, true)

        if (userValue == correctAnswer) {
            playSound(R.raw.correct)
            activity.playSuccessAnimation()
            val isFinished = activity.incrementProgress()
            if (isFirstAttempt) activity.totalXp += PhysicsGrade7Type.LENGTH.xp
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

        val unitFrom = if (conversionType == 0) "m" else "km"
        val unitTo = if (conversionType == 0) "cm" else "m"
        answerDisplay.text = getString(R.string.label_conversion_result, inputValue, unitFrom, correctAnswer, unitTo)

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

            answerDisplay.text = if (conversionType == 0) {
                getString(R.string.solution_m_to_cm, correctAnswer)
            } else {
                getString(R.string.solution_km_to_m, correctAnswer)
            }

            answerDisplay.visibility = View.VISIBLE
            circleState.setImageResource(R.drawable.solution_lamp_icon)
            stateContainer.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.gray_2))

            riveAnimation.setBooleanState(STATE_MACHINE, INPUT_ANSWERED, true)
            val correctIndex = optionMapping.filterValues { it == correctAnswer }.keys.firstOrNull()
            correctIndex?.let { riveAnimation.setNumberState(STATE_MACHINE, INPUT_CHOICE, it.toFloat()) }

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
        val activity = requireActivity() as Physics7GradeQuestionActivity
        activity.isResultCurrentlyVisible = false
    }

    private fun resetFragmentState() {
        isAnswerChecked = false
        isIncorrectAttempt = false
        hideResultUI()
        riveAnimation.setBooleanState(STATE_MACHINE, INPUT_ANSWERED, false)
        riveAnimation.setNumberState(STATE_MACHINE, INPUT_CHOICE, 0f)
        disableCheckButton()
    }

    private fun resetForTryAgain() {
        isAnswerChecked = false
        isIncorrectAttempt = false
        hideResultUI()
        riveAnimation.setBooleanState(STATE_MACHINE, INPUT_ANSWERED, false)
        riveAnimation.setNumberState(STATE_MACHINE, INPUT_CHOICE, 0f)
        disableCheckButton()
        mainHandler.post(checkRunnable)
    }

    private fun resetUIForNext() {
        val activity = requireActivity() as Physics7GradeQuestionActivity
        activity.hideSuccessAnimation()
        mainHandler.removeCallbacks(checkRunnable)
        activity.isResultCurrentlyVisible = false
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
                    if (checkBtn.text == getString(R.string.btn_finish)) activity.navigateToXpGained()
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