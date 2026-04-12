package com.example.tetragon.questions.questionMathSecondGrade.firstTopic

import android.media.MediaPlayer
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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

class UiAdditionInteractiveFragment : Fragment(R.layout.fragment_ui_addition_interactive) {

    private lateinit var questionText: TextView
    private lateinit var firstNumberText: TextView
    private lateinit var riveAnimation: RiveAnimationView
    private lateinit var problemAnswerText: TextView
    private lateinit var problemImage: ImageView

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

    // DYNAMIC UI LOGIC: Updates the blue box text immediately when a Rive option is clicked
    private val checkRunnable = object : Runnable {
        override fun run() {
            if (!isAdded || isAnswerChecked || !::riveAnimation.isInitialized) return

            val currentChoice = getAnswerChoiceValue().toInt()

            if (currentChoice > 0) {
                val selectedValue = optionMapping[currentChoice]
                if (selectedValue != null) {
                    problemAnswerText.text = selectedValue.toString()
                    problemAnswerText.visibility = View.VISIBLE
                }

                if (!checkBtn.isEnabled) {
                    enableCheckButton()
                }
            } else {
                if (checkBtn.isEnabled) {
                    disableCheckButton()
                    problemAnswerText.visibility = View.GONE
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
        currentProblemString = generateArithmeticProblem()
        correctAnswer = evaluateProblem(currentProblemString)

        val optionsSet = mutableSetOf(correctAnswer)
        while (optionsSet.size < 4) {
            val offset = Random.nextInt(-10, 11)
            val wrong = (correctAnswer + offset).coerceIn(1, 100)
            if (wrong != correctAnswer) optionsSet.add(wrong)
        }

        // --- SEQUENCE LOGIC: Sort the options from smallest to largest ---
        val sortedOptions = optionsSet.toList().sorted()
        optionMapping.clear()

        viewModelKeys.forEachIndexed { index, key ->
            val value = sortedOptions[index]
            optionMapping[index + 1] = value // Mapping: 1 -> Smallest, 4 -> Largest
            optionProperties[key]?.value = value.toFloat()
        }

        if (isAdded) {
            firstNumberText.text = "$currentProblemString ="
            problemAnswerText.visibility = View.GONE
            problemImage.setImageResource(R.drawable.answer_blue_box)
            resetFragmentState()
        }
    }

    private fun generateArithmeticProblem(): String {
        val a = Random.nextInt(1, 50)
        val b = Random.nextInt(1, 51)
        return "$a+$b"
    }

    private fun evaluateProblem(problem: String): Int {
        val parts = problem.split("+")
        return parts[0].toInt() + parts[1].toInt()
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

        problemAnswerText.text = userValue.toString()
        problemAnswerText.visibility = View.VISIBLE

        if (userValue == correctAnswer) {
            playSound(R.raw.correct)
            problemImage.setImageResource(R.drawable.answer_correct_box)
            activity.playSuccessAnimation()
            val isFinished = activity.incrementProgress()

            if (isFirstAttempt) {
                activity.totalXp += MathGrade2Type.ADDITION_INTERACTIVE.xp
            }

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
            problemImage.setImageResource(R.drawable.answer_solution_box)
            problemAnswerText.text = correctAnswer.toString()
            problemAnswerText.visibility = View.VISIBLE

            isAnswerChecked = true
            isIncorrectAttempt = false

            applyButtonColors(R.color.black_3, R.color.black_2, R.color.gray_2)
            stateContainer.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.gray_2))

            // Sync Rive selection with the correct answer
            val correctIdx = optionMapping.filterValues { it == correctAnswer }.keys.firstOrNull()
            correctIdx?.let { riveAnimation.setNumberState(STATE_MACHINE, INPUT_CHOICE, it.toFloat()) }
        }
    }

    private fun initViews(view: View) {
        questionText = view.findViewById(R.id.questionText)
        firstNumberText = view.findViewById(R.id.firstNumber)
        problemAnswerText = view.findViewById(R.id.problemAnswerText)
        problemImage = view.findViewById(R.id.problemImage)
        riveAnimation = view.findViewById(R.id.topic12)

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
        problemAnswerText.visibility = View.GONE
        stateContainer.visibility = View.INVISIBLE
        seeBtn.visibility = View.GONE
        problemImage.setImageResource(R.drawable.answer_blue_box)
        riveAnimation.setBooleanState(STATE_MACHINE, INPUT_ANSWERED, false)
        riveAnimation.setNumberState(STATE_MACHINE, INPUT_CHOICE, 0f)

        disableCheckButton()
        setupInitialButtonState() // This ensures the colors go back to white/blue

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

        // --- RESET COLORS TO ORIGINAL BLUE ---
        checkBtn.backgroundTintList = ContextCompat.getColorStateList(requireContext(), R.color.blue_2)
        checkBtnBack.backgroundTintList = ContextCompat.getColorStateList(requireContext(), R.color.blue_1)
        btnBack.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.white))
        // --------------------------------------

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