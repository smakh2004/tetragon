package com.tetragon.app.questions.questionMathFirstGrade.secondTopicAddition.medium

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

class UiTrippleAdditionInteractiveFragment : Fragment(R.layout.fragment_ui_tripple_addition_interactive) {

    private lateinit var tripleAddInteractiveEquationText: TextView
    private lateinit var tripleAddInteractiveRive: RiveAnimationView
    private lateinit var tripleAddInteractiveAnswerText: TextView
    private lateinit var tripleAddInteractiveAnswerBox: ImageView

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
            if (!isAdded || isAnswerChecked || !::tripleAddInteractiveRive.isInitialized) return

            val currentChoice = getAnswerChoiceValue().toInt()

            if (currentChoice > 0) {
                val selectedValue = optionMapping[currentChoice]
                if (selectedValue != null) {
                    tripleAddInteractiveAnswerText.text = selectedValue.toString()
                    tripleAddInteractiveAnswerText.visibility = View.VISIBLE
                }
                if (!checkBtn.isEnabled) enableCheckButton()
            } else {
                if (checkBtn.isEnabled) {
                    disableCheckButton()
                    tripleAddInteractiveAnswerText.visibility = View.GONE
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
            tripleAddInteractiveRive.post {
                setupRiveViewModel()
                generateProblem()
                isInitialized = true
            }
        }
        setupCheckButton()
        mainHandler.post(checkRunnable)
    }

    private fun initViews(view: View) {
        tripleAddInteractiveEquationText = view.findViewById(R.id.tripleAddInteractiveEquationText)
        tripleAddInteractiveAnswerText = view.findViewById(R.id.tripleAddInteractiveAnswerText)
        tripleAddInteractiveAnswerBox = view.findViewById(R.id.tripleAddInteractiveAnswerBox)
        tripleAddInteractiveRive = view.findViewById(R.id.tripleAddInteractiveRive)

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
        // Three single-digit addends (e.g. 4 + 1 + 5). Each 1..5, so sum is 3..15.
        val addend1 = Random.nextInt(1, 6)
        val addend2 = Random.nextInt(1, 6)
        val addend3 = Random.nextInt(1, 6)
        correctAnswer = addend1 + addend2 + addend3

        val fullText = "$addend1 + $addend2 + $addend3 ="
        val spannable = SpannableStringBuilder(fullText)
        // Color every "+" sign, not just the first
        var idx = fullText.indexOf("+")
        while (idx != -1) {
            spannable.setSpan(
                ForegroundColorSpan(ContextCompat.getColor(requireContext(), R.color.blue_2)),
                idx, idx + 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            idx = fullText.indexOf("+", idx + 1)
        }
        tripleAddInteractiveEquationText.text = spannable

        // Build 4 unique options around the correct answer, clamped to a sensible range
        val optionSet = mutableSetOf(correctAnswer)
        while (optionSet.size < 4) {
            val distractor = correctAnswer + Random.nextInt(-3, 4)
            if (distractor != correctAnswer && distractor in 1..18) optionSet.add(distractor)
        }

        // Options displayed in increasing order
        val sortedOptions = optionSet.toList().sorted()
        optionMapping.clear()
        viewModelKeys.forEachIndexed { index, key ->
            val value = sortedOptions[index]
            optionMapping[index + 1] = value
            optionProperties[key]?.value = value.toFloat()
        }

        tripleAddInteractiveAnswerBox.setImageResource(R.drawable.answer_blue_box)
        tripleAddInteractiveAnswerText.visibility = View.GONE

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
        val chosenValue = optionMapping[selectedChoice] ?: 0

        activity.isResultCurrentlyVisible = true
        stateContainer.visibility = View.VISIBLE
        tripleAddInteractiveRive.setBooleanState(STATE_MACHINE, INPUT_ANSWERED, true)

        if (chosenValue == correctAnswer) {
            playSound(R.raw.correct)
            activity.isCorrectAnswerShowing = true
            activity.playSuccessAnimation()
            tripleAddInteractiveAnswerBox.setImageResource(R.drawable.answer_correct_box)

            val isFinished = activity.incrementProgress()
            if (isFirstAttempt) activity.totalXp += MathGrade1Type.ADDITION_TRIPLE_ANIMATION.xp
            activity.handleCorrectAnswer()

            isIncorrectAttempt = false
            checkBtn.text = if (isFinished) getString(R.string.btn_finish) else getString(R.string.btn_continue)
            showCorrectState(stateContainer, circleState)
        } else {
            playSound(R.raw.wrong)
            tripleAddInteractiveAnswerBox.setImageResource(R.drawable.answer_incorrect_box)
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

        tripleAddInteractiveAnswerBox.setImageResource(R.drawable.answer_blue_box)
        tripleAddInteractiveAnswerText.visibility = View.GONE
        activity.findViewById<FrameLayout>(R.id.stateContainer).visibility = View.INVISIBLE
        seeBtn.visibility = View.GONE

        tripleAddInteractiveRive.setBooleanState(STATE_MACHINE, INPUT_ANSWERED, false)
        tripleAddInteractiveRive.setNumberState(STATE_MACHINE, INPUT_CHOICE, 0f)

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

            tripleAddInteractiveAnswerBox.setImageResource(R.drawable.answer_solution_box)
            tripleAddInteractiveAnswerText.text = correctAnswer.toString()
            tripleAddInteractiveAnswerText.visibility = View.VISIBLE
            circleState.setImageResource(R.drawable.solution_lamp_icon)

            isIncorrectAttempt = false
            applyButtonColors(R.color.black_3, R.color.black_2, R.color.gray_2)
            stateContainer.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.gray_2))

            val correctIdx = optionMapping.filterValues { it == correctAnswer }.keys.firstOrNull()
            correctIdx?.let { tripleAddInteractiveRive.setNumberState(STATE_MACHINE, INPUT_CHOICE, it.toFloat()) }
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
            val file = tripleAddInteractiveRive.controller.file ?: return
            val vm = file.getViewModelByName("ViewModel1") ?: return
            val vmi = vm.createDefaultInstance()
            tripleAddInteractiveRive.controller.stateMachines.firstOrNull()?.viewModelInstance = vmi

            viewModelKeys.forEach { key ->
                vmi.getNumberProperty(key)?.let { prop -> optionProperties[key] = prop }
            }
        } catch (e: Exception) { e.printStackTrace() }
    }

    private fun getAnswerChoiceValue(): Float {
        var value = 0f
        tripleAddInteractiveRive.controller.stateMachines.firstOrNull()?.inputs?.forEach {
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