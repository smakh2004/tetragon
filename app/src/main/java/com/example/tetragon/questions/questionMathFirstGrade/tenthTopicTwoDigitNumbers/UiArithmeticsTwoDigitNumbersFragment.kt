package com.example.tetragon.questions.questionMathFirstGrade.tenthTopicTwoDigitNumbers

import android.media.MediaPlayer
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.example.tetragon.R
import com.example.tetragon.questions.questionMathFirstGrade.Math1GradeQuestionActivity
import com.example.tetragon.questions.questionMathFirstGrade.MathGrade1Type
import kotlin.random.Random

class UiArithmeticsTwoDigitNumbersFragment : Fragment(R.layout.fragment_ui_arithmetics_two_digit_numbers) {

    private lateinit var problemRow: LinearLayout
    private lateinit var firstNumberText: TextView
    private lateinit var problemImage: ImageView
    private lateinit var options: List<LinearLayout>
    private lateinit var checkBtn: Button
    private lateinit var checkBtnBack: View
    private lateinit var btnBack: View
    private lateinit var seeBtn: FrameLayout
    private lateinit var seeEnabledButton: Button
    private lateinit var stateAnswer: TextView
    private lateinit var answer: TextView

    private var correctAnswer = 0
    private var selectedOptionIndex: Int? = null
    private var isAnswerChecked = false
    private var isIncorrectAttempt = false
    private var isFirstAttempt = true

    private var currentProblem: String = ""
    private var mediaPlayer: MediaPlayer? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        problemRow = view.findViewById(R.id.problemRow)
        firstNumberText = view.findViewById(R.id.firstNumber)
        problemImage = view.findViewById(R.id.problemImage)

        options = listOf(
            view.findViewById(R.id.option1),
            view.findViewById(R.id.option2),
            view.findViewById(R.id.option3)
        )

        checkBtn = requireActivity().findViewById(R.id.check_enabled_btn)
        checkBtnBack = requireActivity().findViewById(R.id.check_enabled_button_background)
        btnBack = requireActivity().findViewById(R.id.btnBackground)
        seeBtn = requireActivity().findViewById(R.id.see_btn_container)
        seeEnabledButton = requireActivity().findViewById(R.id.see_enabled_btn)
        stateAnswer = requireActivity().findViewById(R.id.stateAnswer)
        answer = requireActivity().findViewById(R.id.answer)

        setupInitialButtonState()
        generateProblem()
        setupOptionClicks()
        setupCheckButton()
    }

    private fun playSound(soundResId: Int) {
        mediaPlayer?.release()
        mediaPlayer = MediaPlayer.create(requireContext(), soundResId)
        mediaPlayer?.setOnCompletionListener { it.release() }
        mediaPlayer?.start()
    }

    private fun setupInitialButtonState() {
        btnBack.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.white))
        checkBtn.backgroundTintList =
            ContextCompat.getColorStateList(requireContext(), R.color.blue_2)
        checkBtnBack.backgroundTintList =
            ContextCompat.getColorStateList(requireContext(), R.color.blue_1)
        checkBtn.isEnabled = false
    }

    // ---------------------- Problem Generation ----------------------

    private fun generateProblem() {
        currentProblem = generateArithmeticProblem()
        correctAnswer = evaluateProblem(currentProblem)

        firstNumberText.text = "$currentProblem ="
        problemImage.setImageResource(R.drawable.answer_blue_box)

        val optionSet = mutableSetOf(correctAnswer)

        // Ensure distractors are also only between 1 and 10
        while (optionSet.size < 3) {
            optionSet.add(Random.Default.nextInt(1, 101))
        }

        val shuffledOptions = optionSet.shuffled()

        options.forEachIndexed { index, layout ->
            val tv = layout.getChildAt(0) as TextView
            tv.text = shuffledOptions[index].toString()
            layout.setBackgroundResource(R.drawable.custom_background)
        }

        val problemAnswerText =
            requireView().findViewById<TextView>(R.id.problemAnswerText)

        problemAnswerText.visibility = View.GONE

        selectedOptionIndex = null
        isAnswerChecked = false
        isIncorrectAttempt = false
        isFirstAttempt = true
        seeBtn.visibility = View.GONE

        checkBtn.isEnabled = false
        checkBtn.text = "CHECK"

        requireActivity()
            .findViewById<FrameLayout>(R.id.check_enabled_btn_container)
            .visibility = View.INVISIBLE

        requireActivity()
            .findViewById<FrameLayout>(R.id.check_disabled_btn_container)
            .visibility = View.VISIBLE

        setupInitialButtonState()
    }

    private fun generateArithmeticProblem(): String {
        val useThreeTerms = Random.nextBoolean()
        val useParentheses = useThreeTerms && Random.nextBoolean()

        var a = Random.nextInt(10, 91) // Start with a decent base
        var b: Int
        var c: Int

        return if (useThreeTerms) {
            if (useParentheses) {
                // Logic for (a + b) - c or a + (b - c)
                val type = Random.nextInt(2)
                if (type == 0) { // (a + b) - c
                    a = Random.nextInt(1, 50)
                    b = Random.nextInt(1, 50)
                    val sum = a + b
                    c = Random.nextInt(1, sum) // Ensures positive
                    "($a+$b)-$c"
                } else { // a + (b - c)
                    b = Random.nextInt(10, 50)
                    c = Random.nextInt(1, b) // Ensures inside () is positive
                    a = Random.nextInt(1, 100 - (b - c)) // Ensures total < 100
                    "$a+($b-$c)"
                }
            } else {
                // Logic for a + b - c or a - b + c
                val type = Random.nextInt(2)
                if (type == 0) { // a + b - c
                    a = Random.nextInt(1, 50)
                    b = Random.nextInt(1, 50)
                    c = Random.nextInt(1, a + b)
                    "$a+$b-$c"
                } else { // a - b + c
                    a = Random.nextInt(20, 100)
                    b = Random.nextInt(1, a)
                    c = Random.nextInt(1, 101 - (a - b))
                    "$a-$b+$c"
                }
            }
        } else {
            // Simple 2-term problem: a + b or a - b
            if (Random.nextBoolean()) {
                a = Random.nextInt(1, 100)
                b = Random.nextInt(1, 101 - a)
                "$a+$b"
            } else {
                a = Random.nextInt(2, 101)
                b = Random.nextInt(1, a)
                "$a-$b"
            }
        }
    }

    private fun evaluateProblem(problem: String): Int {
        // Basic parser for the generated patterns
        val cleaned = problem.replace("(", "").replace(")", "")
        val tokens = mutableListOf<String>()
        var currentNum = ""

        // Split into numbers and operators while keeping the operators
        for (char in problem) {
            if (char.isDigit()) {
                currentNum += char
            } else if (char == '+' || char == '-' || char == '(' || char == ')') {
                if (currentNum.isNotEmpty()) {
                    tokens.add(currentNum)
                    currentNum = ""
                }
                if (char != '(' && char != ')') tokens.add(char.toString())
            }
        }
        if (currentNum.isNotEmpty()) tokens.add(currentNum)

        // Calculate (Handling parentheses by the specific logic we generated)
        // Since we only do simple addition/subtraction, order is standard left-to-right
        // once parentheses are "dissolved".
        var result = tokens[0].toInt()
        var i = 1
        while (i < tokens.size) {
            val op = tokens[i]
            val nextVal = tokens[i + 1].toInt()
            if (op == "+") result += nextVal else result -= nextVal
            i += 2
        }
        return result
    }

    // ---------------------- Option Clicks ----------------------

    private fun setupOptionClicks() {

        options.forEachIndexed { index, layout ->

            layout.setOnClickListener {

                if (isAnswerChecked) return@setOnClickListener

                selectedOptionIndex = index

                highlightSelectedOption(index)

                enableCheckButton()

                val problemAnswerText =
                    requireView().findViewById<TextView>(R.id.problemAnswerText)

                val chosenNumber =
                    (layout.getChildAt(0) as TextView).text.toString()

                problemAnswerText.text = chosenNumber
                problemAnswerText.visibility = View.VISIBLE
            }
        }
    }

    private fun highlightSelectedOption(selectedIndex: Int) {

        options.forEachIndexed { i, layout ->

            layout.setBackgroundResource(
                if (i == selectedIndex)
                    R.drawable.option_selected
                else
                    R.drawable.custom_background
            )
        }
    }

    private fun enableCheckButton() {

        checkBtn.isEnabled = true

        requireActivity()
            .findViewById<FrameLayout>(R.id.check_enabled_btn_container)
            .visibility = View.VISIBLE

        requireActivity()
            .findViewById<FrameLayout>(R.id.check_disabled_btn_container)
            .visibility = View.INVISIBLE
    }

    // ---------------------- Check Answer ----------------------

    private fun setupCheckButton() {
        checkBtn.setOnClickListener {
            val stateContainer = requireActivity().findViewById<FrameLayout>(R.id.stateContainer)
            val circleState = requireActivity().findViewById<ImageView>(R.id.circleState)

            if (!isAnswerChecked) {
                selectedOptionIndex?.let {
                    checkAnswer(it, stateContainer, circleState)
                }
            } else {
                if (isIncorrectAttempt) {
                    resetForTryAgain()
                } else {
                    val activity = requireActivity() as Math1GradeQuestionActivity
                    if (checkBtn.text == "FINISH") {
                        activity.navigateToXpGained()
                    } else {
                        // GATE: Check and trigger milestone before showing next question
                        val isMilestoneActive = activity.checkAndTriggerMilestone()

                        if (!isMilestoneActive) {
                            resetUIForNext()
                            activity.showRandomQuestion()
                            setupOptionClicks()
                        }
                    }
                }
            }
        }
    }

    private fun checkAnswer(index: Int, stateContainer: FrameLayout, circleState: ImageView) {
        isAnswerChecked = true
        val chosen = (options[index].getChildAt(0) as TextView).text.toString().toInt()
        val activity = requireActivity() as Math1GradeQuestionActivity

        activity.isResultCurrentlyVisible = true
        stateContainer.visibility = View.VISIBLE

        val problemAnswerText = requireView().findViewById<TextView>(R.id.problemAnswerText)
        problemAnswerText.text = chosen.toString()
        problemAnswerText.visibility = View.VISIBLE

        if (chosen == correctAnswer) {
            playSound(R.raw.correct)
            activity.isCorrectAnswerShowing = true

            // 1. Play the Rive success animation (Mr. Square)
            activity.playSuccessAnimation()
            problemImage.setImageResource(R.drawable.answer_correct_box)

            val isFinished = activity.incrementProgress()

            if (isFirstAttempt) {
                activity.totalXp += MathGrade1Type.ARITHMETICS_TWO_DIGIT_NUMBERS.xp
            }

            // 2. Register correct answer for milestones/streaks
            activity.handleCorrectAnswer()

            isIncorrectAttempt = false
            checkBtn.text = if (isFinished) "FINISH" else "CONTINUE"
            showCorrectState(stateContainer, circleState, index)

        } else {
            playSound(R.raw.wrong)
            problemImage.setImageResource(R.drawable.answer_incorrect_box)
            isIncorrectAttempt = true
            checkBtn.text = "TRY AGAIN"
            showIncorrectState(stateContainer, circleState, index)
            setupSeeSolution(stateContainer, circleState, index)
        }
        isFirstAttempt = false
    }

    private fun showCorrectState(
        stateContainer: FrameLayout,
        circleState: ImageView,
        index: Int
    ) {

        stateContainer.setBackgroundColor(
            ContextCompat.getColor(requireContext(), R.color.green_3)
        )

        circleState.setImageResource(R.drawable.correct_tick_icon)

        options[index].setBackgroundResource(R.drawable.option_correct)

        stateAnswer.text = "Correct!"

        answer.text = "Answer: $correctAnswer"

        applyButtonColors(
            R.color.green_1,
            R.color.green_2,
            R.color.green_4
        )
    }

    private fun showIncorrectState(
        stateContainer: FrameLayout,
        circleState: ImageView,
        index: Int
    ) {

        stateContainer.setBackgroundColor(
            ContextCompat.getColor(requireContext(), R.color.red_4)
        )

        circleState.setImageResource(R.drawable.wrong_circle)

        options[index].setBackgroundResource(R.drawable.option_incorrect)

        stateAnswer.text = "Incorrect!"

        answer.visibility = View.GONE

        seeBtn.visibility = View.VISIBLE

        applyButtonColors(
            R.color.red_1,
            R.color.red_2,
            R.color.red_4
        )
    }

    private fun setupSeeSolution(
        stateContainer: FrameLayout,
        circleState: ImageView,
        wrongIndex: Int
    ) {

        val problemAnswerText =
            requireView().findViewById<TextView>(R.id.problemAnswerText)

        seeEnabledButton.setOnClickListener {

            seeBtn.visibility = View.GONE

            stateAnswer.text = "Solution"

            answer.text = "Answer: $correctAnswer"

            answer.visibility = View.VISIBLE

            checkBtn.text = "CONTINUE"

            problemImage.setImageResource(R.drawable.answer_solution_box)

            problemAnswerText.text = correctAnswer.toString()

            problemAnswerText.visibility = View.VISIBLE

            circleState.setImageResource(R.drawable.solution_lamp_icon)

            isAnswerChecked = true
            isIncorrectAttempt = false

            checkBtn.backgroundTintList =
                ContextCompat.getColorStateList(requireContext(), R.color.black_3)

            checkBtnBack.backgroundTintList =
                ContextCompat.getColorStateList(requireContext(), R.color.black_2)

            btnBack.setBackgroundColor(
                ContextCompat.getColor(requireContext(), R.color.gray_2)
            )

            stateContainer.setBackgroundColor(
                ContextCompat.getColor(requireContext(), R.color.gray_2)
            )

            options.forEach { layout ->

                val tv = layout.getChildAt(0) as TextView

                layout.setBackgroundResource(
                    if (tv.text.toString().toInt() == correctAnswer)
                        R.drawable.option_showed
                    else
                        R.drawable.custom_background
                )
            }
        }
    }

    private fun applyButtonColors(
        buttonColor: Int,
        backColor: Int,
        backgroundColor: Int
    ) {

        checkBtn.backgroundTintList =
            ContextCompat.getColorStateList(requireContext(), buttonColor)

        checkBtnBack.backgroundTintList =
            ContextCompat.getColorStateList(requireContext(), backColor)

        btnBack.setBackgroundColor(
            ContextCompat.getColor(requireContext(), backgroundColor)
        )
    }

    private fun resetForTryAgain() {

        val activity = requireActivity() as Math1GradeQuestionActivity
        activity.isResultCurrentlyVisible = false

        val problemAnswerText =
            requireView().findViewById<TextView>(R.id.problemAnswerText)

        val stateContainer =
            requireActivity().findViewById<FrameLayout>(R.id.stateContainer)

        isAnswerChecked = false
        isIncorrectAttempt = false
        selectedOptionIndex = null

        options.forEach {
            it.setBackgroundResource(R.drawable.custom_background)
        }

        problemImage.setImageResource(R.drawable.answer_blue_box)

        problemAnswerText.visibility = View.GONE

        stateContainer.visibility = View.INVISIBLE

        seeBtn.visibility = View.GONE

        stateAnswer.text = ""

        answer.visibility = View.VISIBLE

        answer.text = ""

        checkBtn.text = "CHECK"

        checkBtn.isEnabled = false

        requireActivity()
            .findViewById<FrameLayout>(R.id.check_enabled_btn_container)
            .visibility = View.INVISIBLE

        requireActivity()
            .findViewById<FrameLayout>(R.id.check_disabled_btn_container)
            .visibility = View.VISIBLE

        setupInitialButtonState()
    }

    private fun resetUIForNext() {
        val activity = requireActivity() as Math1GradeQuestionActivity
        activity.isResultCurrentlyVisible = false
        activity.hideSuccessAnimation() // Clear animation state

        requireActivity().findViewById<FrameLayout>(R.id.stateContainer).visibility = View.INVISIBLE
        stateAnswer.text = ""
        answer.text = ""
        answer.visibility = View.VISIBLE
        seeBtn.visibility = View.GONE
        checkBtn.text = "CHECK"

        setupInitialButtonState()
        options.forEach { it.setBackgroundResource(R.drawable.custom_background) }
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaPlayer?.release()
        mediaPlayer = null
    }
}