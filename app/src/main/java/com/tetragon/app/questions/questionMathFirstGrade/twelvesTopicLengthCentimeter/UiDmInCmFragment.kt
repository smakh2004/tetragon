package com.tetragon.app.questions.questionMathFirstGrade.twelvesTopicLengthCentimeter

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
import com.tetragon.app.questions.questionMathFirstGrade.Math1GradeQuestionActivity
import com.tetragon.app.questions.questionMathFirstGrade.MathGrade1Type

class UiDmInCmFragment : Fragment(R.layout.fragment_ui_dm_in_cm) {

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

    private var targetDm: Int = 0
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
            val currentVal = getAnswerChoiceValue()
            if (currentVal > 0f && !checkBtn.isEnabled) {
                enableCheckButton()
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

            // Dynamically set the unit based on the system language
            vmi.getStringProperty("unit")?.let { unitProp ->
                unitProp.value = getString(R.string.keyword_cm)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun generateProblem() {
        targetDm = (2..9).random()
        correctAnswerCm = targetDm * 10

        val optionsCount = viewModelKeys.size
        val optionsSet = mutableSetOf(correctAnswerCm)

        while (optionsSet.size < optionsCount) {
            optionsSet.add((1..10).random() * 10)
        }

        val sortedOptions = optionsSet.toList().sorted()
        optionMapping.clear()

        viewModelKeys.forEachIndexed { index, key ->
            val value = sortedOptions[index]
            optionProperties[key]?.value = value.toFloat()
            optionMapping[index + 1] = value
        }

        updateQuestionText()
        resetFragmentState()
    }

    private fun getAnswerChoiceValue(): Float {
        var value = 0f
        riveAnimation.controller.stateMachines.firstOrNull()?.inputs?.forEach { input ->
            if (input.name == INPUT_CHOICE && input is SMINumber) {
                value = input.value
            }
        }
        return value
    }

    private fun checkAnswer() {
        val selectedIndex = getAnswerChoiceValue().toInt()
        val userValue = optionMapping[selectedIndex] ?: -1

        isAnswerChecked = true
        val activity = requireActivity() as Math1GradeQuestionActivity
        activity.isResultCurrentlyVisible = true
        stateContainer.visibility = View.VISIBLE

        riveAnimation.setBooleanState(STATE_MACHINE, INPUT_ANSWERED, true)

        if (userValue == correctAnswerCm) {
            playSound(R.raw.correct)
            activity.playSuccessAnimation()
            val isFinished = activity.incrementProgress()
            if (isFirstAttempt) activity.totalXp += MathGrade1Type.DM_IN_CM.xp
            activity.handleCorrectAnswer()
            isIncorrectAttempt = false
            checkBtn.text = if (isFinished) getString(R.string.btn_finish) else getString(R.string.btn_continue)
            showCorrectState()
        } else {
            playSound(R.raw.wrong)
            isIncorrectAttempt = true
            activity.handleIncorrectAnswer()
            checkBtn.text = getString(R.string.btn_try_again)
            showIncorrectState()
            setupSeeSolution()
        }
        isFirstAttempt = false
    }

    private fun updateQuestionText() {
        val targetDmText = getString(R.string.dm_unit_template, targetDm)
        val cmTarget = getString(R.string.keyword_cm)
        val sentence = getString(R.string.question_show_dm_in_cm, targetDmText, cmTarget)

        val spannable = SpannableString(sentence)
        val blueColor = ContextCompat.getColor(requireContext(), R.color.blue_2)

        val dmStart = sentence.indexOf(targetDmText)
        if (dmStart != -1) {
            spannable.setSpan(ForegroundColorSpan(blueColor), dmStart, dmStart + targetDmText.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }

        val cmStart = sentence.lastIndexOf(cmTarget)
        if (cmStart != -1) {
            spannable.setSpan(ForegroundColorSpan(blueColor), cmStart, cmStart + cmTarget.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }

        questionText.text = spannable
    }

    private fun initViews(view: View) {
        questionText = view.findViewById(R.id.questionText)
        riveAnimation = view.findViewById(R.id.topic12)

        val activity = requireActivity() as Math1GradeQuestionActivity
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
                val activity = requireActivity() as Math1GradeQuestionActivity
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
        btnBack.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.white))
        disableCheckButton()
    }

    private fun showCorrectState() {
        stateContainer.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.green_3))
        circleState.setImageResource(R.drawable.correct_tick_icon)
        stateAnswer.text = getString(R.string.state_correct)
        answerDisplay.text = getString(R.string.label_dm_to_cm_result, targetDm, correctAnswerCm)
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

    private fun setupSeeSolution() {
        seeEnabledButton.setOnClickListener {
            seeBtn.visibility = View.GONE
            stateAnswer.text = getString(R.string.state_solution)
            answerDisplay.text = getString(R.string.solution_dm_to_cm, targetDm, correctAnswerCm)
            answerDisplay.visibility = View.VISIBLE
            circleState.setImageResource(R.drawable.solution_lamp_icon)
            stateContainer.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.gray_2))

            riveAnimation.setBooleanState(STATE_MACHINE, INPUT_ANSWERED, true)
            val correctIndex = optionMapping.filterValues { it == correctAnswerCm }.keys.firstOrNull()
            correctIndex?.let {
                riveAnimation.setNumberState(STATE_MACHINE, INPUT_CHOICE, it.toFloat())
            }

            applyButtonColors(R.color.black_3, R.color.black_2, R.color.gray_2)
            checkBtn.text = getString(R.string.btn_continue)
            isIncorrectAttempt = false
            isAnswerChecked = true
        }
    }

    private fun resetFragmentState() {
        isAnswerChecked = false
        isIncorrectAttempt = false
        checkBtn.text = getString(R.string.btn_check)
        stateContainer.visibility = View.INVISIBLE
        seeBtn.visibility = View.GONE
        stateAnswer.text = ""
        answerDisplay.text = ""
        answerDisplay.visibility = View.VISIBLE

        riveAnimation.setBooleanState(STATE_MACHINE, INPUT_ANSWERED, false)
        riveAnimation.setNumberState(STATE_MACHINE, INPUT_CHOICE, 0f)

        disableCheckButton()
    }

    private fun resetForTryAgain() {
        val activity = requireActivity() as Math1GradeQuestionActivity
        activity.isResultCurrentlyVisible = false

        mainHandler.removeCallbacks(checkRunnable)

        isAnswerChecked = false
        isIncorrectAttempt = false

        riveAnimation.setBooleanState(STATE_MACHINE, INPUT_ANSWERED, false)
        riveAnimation.setNumberState(STATE_MACHINE, INPUT_CHOICE, 0f)

        stateContainer.visibility = View.INVISIBLE
        seeBtn.visibility = View.GONE
        stateAnswer.text = ""
        answerDisplay.text = ""
        answerDisplay.visibility = View.VISIBLE

        checkBtn.text = getString(R.string.btn_check)
        btnBack.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.white))

        disableCheckButton()

        mainHandler.postDelayed(checkRunnable, 200)
    }

    private fun resetUIForNext() {
        val activity = requireActivity() as Math1GradeQuestionActivity
        activity.isResultCurrentlyVisible = false
        activity.hideSuccessAnimation()
        mainHandler.removeCallbacks(checkRunnable)
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
        mediaPlayer?.release()
        super.onDestroyView()
    }
}