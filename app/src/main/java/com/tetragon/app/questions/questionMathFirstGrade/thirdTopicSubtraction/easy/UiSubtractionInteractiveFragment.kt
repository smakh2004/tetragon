package com.tetragon.app.questions.questionMathFirstGrade.thirdTopicSubtraction.easy

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
import com.tetragon.app.R
import com.tetragon.app.questions.questionMathFirstGrade.Math1GradeQuestionActivity
import com.tetragon.app.questions.questionMathFirstGrade.MathGrade1Type
import kotlin.random.Random

class UiSubtractionInteractiveFragment : Fragment(R.layout.fragment_ui_subtraction_interactive) {

    private lateinit var subtractionInteractiveEquationText: TextView
    private lateinit var subtractionInteractiveRive: RiveAnimationView
    private lateinit var subtractionInteractiveAnswerText: TextView
    private lateinit var subtractionInteractiveAnswerBox: ImageView

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
            if (!isAdded || isAnswerChecked || !::subtractionInteractiveRive.isInitialized) return

            val currentChoice = getAnswerChoiceValue().toInt()

            if (currentChoice > 0) {
                val selectedValue = optionMapping[currentChoice]
                if (selectedValue != null) {
                    subtractionInteractiveAnswerText.text = selectedValue.toString()
                    subtractionInteractiveAnswerText.visibility = View.VISIBLE
                }
                if (!checkBtn.isEnabled) enableCheckButton()
            } else {
                if (checkBtn.isEnabled) {
                    disableCheckButton()
                    subtractionInteractiveAnswerText.visibility = View.GONE
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
            subtractionInteractiveRive.post {
                setupRiveViewModel()
                generateProblem()
                isInitialized = true
            }
        }
        setupCheckButton()
        mainHandler.post(checkRunnable)
    }

    private fun initViews(view: View) {
        subtractionInteractiveEquationText = view.findViewById(R.id.subtractionInteractiveEquationText)
        subtractionInteractiveAnswerText = view.findViewById(R.id.subtractionInteractiveAnswerText)
        subtractionInteractiveAnswerBox = view.findViewById(R.id.subtractionInteractiveAnswerBox)
        subtractionInteractiveRive = view.findViewById(R.id.subtractionInteractiveRive)

        val activity = requireActivity() as Math1GradeQuestionActivity
        checkBtn = activity.findViewById(R.id.check_enabled_btn)
        checkBtnBack = activity.findViewById(R.id.check_enabled_button_background)
        btnBack = activity.findViewById(R.id.btnBackground)
        seeBtn = activity.findViewById(R.id.see_btn_container)
        seeEnabledButton = activity.findViewById(R.id.see_enabled_btn)
        stateAnswer = activity.findViewById(R.id.stateAnswer)
        answerDisplay = activity.findViewById(R.id.answer)
    }

    private fun generateProblem() {
        // Easy first-grade subtraction: minuend up to 10, result always positive (e.g. 9 - 4).
        val minuend = Random.nextInt(2, 11)         // 2..10
        val subtrahend = Random.nextInt(1, minuend) // 1..minuend-1
        correctAnswer = minuend - subtrahend        // 1..9

        val fullText = "$minuend - $subtrahend ="
        val spannable = SpannableStringBuilder(fullText)
        val signIndex = fullText.indexOf("-")
        if (signIndex != -1) {
            spannable.setSpan(
                ForegroundColorSpan(ContextCompat.getColor(requireContext(), R.color.blue_2)),
                signIndex, signIndex + 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
        subtractionInteractiveEquationText.text = spannable

        // Build 4 UNIQUE options in the 1..10 range.
        // Never use 0: a 0 value is indistinguishable from a Rive slot left at its
        // default, which is what makes two boxes look like duplicate "0"s.
        val optionSet = mutableSetOf(correctAnswer)
        var guard = 0
        while (optionSet.size < 4 && guard < 100) {
            guard++
            val distractor = correctAnswer + Random.nextInt(-4, 5)
            if (distractor in 1..10) optionSet.add(distractor)
        }
        // Fallback: if the near-answer window couldn't supply 4, top up from 1..10.
        var fill = 1
        while (optionSet.size < 4 && fill <= 10) {
            optionSet.add(fill)
            fill++
        }

        // Options displayed in increasing order
        val sortedOptions = optionSet.toList().sorted()
        optionMapping.clear()
        viewModelKeys.forEachIndexed { index, key ->
            val value = sortedOptions[index]
            optionMapping[index + 1] = value
            optionProperties[key]?.value = value.toFloat()
        }

        subtractionInteractiveAnswerBox.setImageResource(R.drawable.answer_blue_box)
        subtractionInteractiveAnswerText.visibility = View.GONE

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
        val activity = requireActivity() as Math1GradeQuestionActivity
        val stateContainer = activity.findViewById<FrameLayout>(R.id.stateContainer)
        val circleState = activity.findViewById<ImageView>(R.id.circleState)

        val selectedChoice = getAnswerChoiceValue().toInt()
        val chosenValue = optionMapping[selectedChoice] ?: -1

        activity.isResultCurrentlyVisible = true
        stateContainer.visibility = View.VISIBLE
        subtractionInteractiveRive.setBooleanState(STATE_MACHINE, INPUT_ANSWERED, true)

        if (chosenValue == correctAnswer) {
            playSound(R.raw.correct)
            activity.isCorrectAnswerShowing = true
            activity.playSuccessAnimation()
            subtractionInteractiveAnswerBox.setImageResource(R.drawable.answer_correct_box)

            val isFinished = activity.incrementProgress()
            if (isFirstAttempt) activity.totalXp += MathGrade1Type.SUBTRACTION_ANIMATION.xp
            activity.handleCorrectAnswer()

            isIncorrectAttempt = false
            checkBtn.text = if (isFinished) getString(R.string.btn_finish) else getString(R.string.btn_continue)
            showCorrectState(stateContainer, circleState)
        } else {
            playSound(R.raw.wrong)
            subtractionInteractiveAnswerBox.setImageResource(R.drawable.answer_incorrect_box)
            isIncorrectAttempt = true
            activity.handleIncorrectAnswer()
            checkBtn.text = getString(R.string.btn_try_again)
            showIncorrectState(stateContainer, circleState)
            setupSeeSolution(stateContainer, circleState)
        }
        isFirstAttempt = false
    }

    private fun enableCheckButton() {
        checkBtn.isEnabled = true
        val activity = requireActivity() as Math1GradeQuestionActivity
        activity.findViewById<FrameLayout>(R.id.check_enabled_btn_container).visibility = View.VISIBLE
        activity.findViewById<FrameLayout>(R.id.check_disabled_btn_container).visibility = View.INVISIBLE
    }

    private fun disableCheckButton() {
        checkBtn.isEnabled = false
        val activity = requireActivity() as Math1GradeQuestionActivity
        activity.findViewById<FrameLayout>(R.id.check_enabled_btn_container).visibility = View.INVISIBLE
        activity.findViewById<FrameLayout>(R.id.check_disabled_btn_container).visibility = View.VISIBLE
    }

    private fun setupCheckButton() {
        checkBtn.setOnClickListener {
            val activity = requireActivity() as Math1GradeQuestionActivity
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
        val activity = requireActivity() as Math1GradeQuestionActivity
        activity.isResultCurrentlyVisible = false
        isAnswerChecked = false
        isIncorrectAttempt = false

        subtractionInteractiveAnswerBox.setImageResource(R.drawable.answer_blue_box)
        subtractionInteractiveAnswerText.visibility = View.GONE
        activity.findViewById<FrameLayout>(R.id.stateContainer).visibility = View.INVISIBLE
        seeBtn.visibility = View.GONE

        subtractionInteractiveRive.setBooleanState(STATE_MACHINE, INPUT_ANSWERED, false)
        subtractionInteractiveRive.setNumberState(STATE_MACHINE, INPUT_CHOICE, 0f)

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

            subtractionInteractiveAnswerBox.setImageResource(R.drawable.answer_solution_box)
            subtractionInteractiveAnswerText.text = correctAnswer.toString()
            subtractionInteractiveAnswerText.visibility = View.VISIBLE
            circleState.setImageResource(R.drawable.solution_lamp_icon)

            isIncorrectAttempt = false
            applyButtonColors(R.color.black_3, R.color.black_2, R.color.gray_2)
            stateContainer.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.gray_2))

            val correctIdx = optionMapping.filterValues { it == correctAnswer }.keys.firstOrNull()
            correctIdx?.let { subtractionInteractiveRive.setNumberState(STATE_MACHINE, INPUT_CHOICE, it.toFloat()) }
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
            val file = subtractionInteractiveRive.controller.file ?: return
            val vm = file.getViewModelByName("ViewModel1") ?: return
            val vmi = vm.createDefaultInstance()
            subtractionInteractiveRive.controller.stateMachines.firstOrNull()?.viewModelInstance = vmi

            viewModelKeys.forEach { key ->
                vmi.getNumberProperty(key)?.let { prop -> optionProperties[key] = prop }
            }
        } catch (e: Exception) { e.printStackTrace() }
    }

    private fun getAnswerChoiceValue(): Float {
        var value = 0f
        subtractionInteractiveRive.controller.stateMachines.firstOrNull()?.inputs?.forEach {
            if (it.name == INPUT_CHOICE && it is SMINumber) value = it.value
        }
        return value
    }

    private fun setupInitialButtonState() {
        btnBack.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.white))
        applyButtonColors(R.color.blue_2, R.color.blue_1, R.color.white)
    }

    private fun resetUIForNext() {
        val activity = requireActivity() as Math1GradeQuestionActivity
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