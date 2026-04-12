package com.example.tetragon.questions.questionMathFirstGrade.sixthTopicParentheses

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

class UiParenthesesFragment : Fragment(R.layout.fragment_ui_parentheses) { // Ensure this matches your XML filename

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

        // Activity UI elements
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
        checkBtn.backgroundTintList = ContextCompat.getColorStateList(requireContext(), R.color.blue_2)
        checkBtnBack.backgroundTintList = ContextCompat.getColorStateList(requireContext(), R.color.blue_1)
        checkBtn.isEnabled = false
    }

    // ---------------------- Problem Generation (Modified for Parentheses) ----------------------

    private fun generateProblem() {
        currentProblem = generateParenthesesProblem()
        correctAnswer = evaluateParenthesesProblem(currentProblem)

        firstNumberText.text = "$currentProblem ="
        problemImage.setImageResource(R.drawable.answer_blue_box)

        val optionSet = mutableSetOf(correctAnswer)

        // Distractors between 1 and 10
        while (optionSet.size < 3) {
            val distractor = Random.nextInt(1, 11)
            optionSet.add(distractor)
        }

        val shuffledOptions = optionSet.shuffled()

        options.forEachIndexed { index, layout ->
            val tv = layout.getChildAt(0) as TextView
            tv.text = shuffledOptions[index].toString()
            layout.setBackgroundResource(R.drawable.custom_background)
        }

        val problemAnswerText = requireView().findViewById<TextView>(R.id.problemAnswerText)
        problemAnswerText.visibility = View.GONE

        selectedOptionIndex = null
        isAnswerChecked = false
        isIncorrectAttempt = false
        isFirstAttempt = true
        seeBtn.visibility = View.GONE
        checkBtn.isEnabled = false
        checkBtn.text = "CHECK"

        requireActivity().findViewById<FrameLayout>(R.id.check_enabled_btn_container).visibility = View.INVISIBLE
        requireActivity().findViewById<FrameLayout>(R.id.check_disabled_btn_container).visibility = View.VISIBLE

        setupInitialButtonState()
    }

    // ---------------------- Problem Generation (Strictly 1-10) ----------------------

    private fun generateParenthesesProblem(): String {
        val isAddition = Random.nextBoolean()
        val useParenthesesAtEnd = Random.nextBoolean()
        val maxTotal = 10

        return if (isAddition) {
            // Addition: a + (b + c) or (a + b) + c
            // Start with small numbers so sum doesn't exceed 10
            val a = Random.nextInt(1, 6) // 1..5
            val b = Random.nextInt(1, 4) // 1..3
            // Ensure c makes the total at most 10, but at least 1
            val remaining = maxTotal - (a + b)
            val c = if (remaining > 1) Random.nextInt(1, remaining + 1) else 1

            if (useParenthesesAtEnd) "$a+($b+$c)" else "($a+$b)+$c"
        } else {
            // Subtraction: a - (b + c) or (a - b) - c
            // We use (b + c) inside to keep 1st-grade logic simple and positive
            val first = Random.nextInt(5, 11) // Start with 5-10

            if (useParenthesesAtEnd) {
                // Form: a - (b + c) -> Result must be >= 1
                // So (b + c) must be <= a - 1
                val maxInnerSum = first - 1
                val b = Random.nextInt(1, maxInnerSum)
                val c = Random.nextInt(1, (maxInnerSum - b) + 1)
                "$first-($b+$c)"
            } else {
                // Form: (a - b) - c -> a-b must be >= 2, result must be >= 1
                val b = Random.nextInt(1, first - 1)
                val currentStep = first - b
                val c = Random.nextInt(1, currentStep)
                "($first-$b)-$c"
            }
        }
    }

    private fun evaluateParenthesesProblem(problem: String): Int {
        // 1. Find and solve parentheses
        val startIndex = problem.indexOf('(')
        val endIndex = problem.indexOf(')')

        return if (startIndex != -1 && endIndex != -1) {
            val innerExp = problem.substring(startIndex + 1, endIndex)
            val innerResult = solveSimple(innerExp)

            // 2. Build the new expression with the result
            val leftPart = problem.substring(0, startIndex)
            val rightPart = problem.substring(endIndex + 1)

            solveSimple("$leftPart$innerResult$rightPart")
        } else {
            solveSimple(problem)
        }
    }

    private fun solveSimple(expression: String): Int {
        return when {
            expression.contains("+") -> {
                val parts = expression.split("+")
                parts[0].toInt() + parts[1].toInt()
            }
            expression.contains("-") -> {
                val parts = expression.split("-")
                // Handle cases where the split might leave empty strings if '-' is at start
                val cleanParts = expression.split("-").filter { it.isNotEmpty() }
                if (cleanParts.size == 2) {
                    parts[0].toInt() - parts[1].toInt()
                } else {
                    cleanParts[0].toInt()
                }
            }
            else -> expression.toInt()
        }
    }

    // ---------------------- Option Clicks ----------------------

    private fun setupOptionClicks() {
        options.forEachIndexed { index, layout ->
            layout.setOnClickListener {
                if (isAnswerChecked) return@setOnClickListener
                selectedOptionIndex = index
                highlightSelectedOption(index)
                enableCheckButton()

                val problemAnswerText = requireView().findViewById<TextView>(R.id.problemAnswerText)
                val chosenNumber = (layout.getChildAt(0) as TextView).text.toString()
                problemAnswerText.text = chosenNumber
                problemAnswerText.visibility = View.VISIBLE
            }
        }
    }

    private fun highlightSelectedOption(selectedIndex: Int) {
        options.forEachIndexed { i, layout ->
            layout.setBackgroundResource(
                if (i == selectedIndex) R.drawable.option_selected else R.drawable.custom_background
            )
        }
    }

    private fun enableCheckButton() {
        checkBtn.isEnabled = true
        requireActivity().findViewById<FrameLayout>(R.id.check_enabled_btn_container).visibility = View.VISIBLE
        requireActivity().findViewById<FrameLayout>(R.id.check_disabled_btn_container).visibility = View.INVISIBLE
    }

    // ---------------------- Check Answer ----------------------

    private fun setupCheckButton() {
        checkBtn.setOnClickListener {
            val stateContainer = requireActivity().findViewById<FrameLayout>(R.id.stateContainer)
            val circleState = requireActivity().findViewById<ImageView>(R.id.circleState)

            if (!isAnswerChecked) {
                selectedOptionIndex?.let { checkAnswer(it, stateContainer, circleState) }
            } else {
                if (isIncorrectAttempt) {
                    resetForTryAgain()
                } else {
                    val activity = requireActivity() as Math1GradeQuestionActivity

                    if (checkBtn.text == "FINISH") {
                        activity.navigateToXpGained()
                    } else {
                        // GATE: Trigger milestone only after clicking CONTINUE
                        val isMilestoneActive = activity.checkAndTriggerMilestone()

                        if (!isMilestoneActive) {
                            resetUIForNext()
                            activity.showRandomQuestion()
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

        if (chosen == correctAnswer) {
            playSound(R.raw.correct)
            activity.isCorrectAnswerShowing = true

            // 1. Play success animation (Mr. Square celebration)
            activity.playSuccessAnimation()
            problemImage.setImageResource(R.drawable.answer_correct_box)

            val isFinished = activity.incrementProgress()

            if (isFirstAttempt) {
                activity.totalXp += MathGrade1Type.PARENTHESES.xp
            }

            // 2. Log progress for milestones/streaks in Activity
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

    private fun showCorrectState(container: FrameLayout, circle: ImageView, index: Int) {
        container.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.green_3))
        circle.setImageResource(R.drawable.correct_tick_icon)
        options[index].setBackgroundResource(R.drawable.option_correct)
        stateAnswer.text = "Correct!"
        answer.text = "Answer: $correctAnswer"
        applyButtonColors(R.color.green_1, R.color.green_2, R.color.green_4)
    }

    private fun showIncorrectState(container: FrameLayout, circle: ImageView, index: Int) {
        container.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.red_4))
        circle.setImageResource(R.drawable.wrong_circle)
        options[index].setBackgroundResource(R.drawable.option_incorrect)
        stateAnswer.text = "Incorrect!"
        answer.visibility = View.GONE
        seeBtn.visibility = View.VISIBLE
        applyButtonColors(R.color.red_1, R.color.red_2, R.color.red_4)
    }

    private fun setupSeeSolution(container: FrameLayout, circle: ImageView, wrongIndex: Int) {
        seeEnabledButton.setOnClickListener {
            seeBtn.visibility = View.GONE
            stateAnswer.text = "Solution"
            answer.text = "Answer: $correctAnswer"
            answer.visibility = View.VISIBLE
            checkBtn.text = "CONTINUE"
            problemImage.setImageResource(R.drawable.answer_solution_box)
            requireView().findViewById<TextView>(R.id.problemAnswerText).text = correctAnswer.toString()
            circle.setImageResource(R.drawable.solution_lamp_icon)
            isAnswerChecked = true
            isIncorrectAttempt = false

            applyButtonColors(R.color.black_3, R.color.black_2, R.color.gray_2)
            container.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.gray_2))

            options.forEach { layout ->
                val tv = layout.getChildAt(0) as TextView
                layout.setBackgroundResource(
                    if (tv.text.toString().toInt() == correctAnswer) R.drawable.option_showed else R.drawable.custom_background
                )
            }
        }
    }

    private fun applyButtonColors(btn: Int, back: Int, bg: Int) {
        checkBtn.backgroundTintList = ContextCompat.getColorStateList(requireContext(), btn)
        checkBtnBack.backgroundTintList = ContextCompat.getColorStateList(requireContext(), back)
        btnBack.setBackgroundColor(ContextCompat.getColor(requireContext(), bg))
    }

    private fun resetForTryAgain() {
        (requireActivity() as Math1GradeQuestionActivity).isResultCurrentlyVisible = false
        isAnswerChecked = false
        isIncorrectAttempt = false
        selectedOptionIndex = null
        options.forEach { it.setBackgroundResource(R.drawable.custom_background) }
        problemImage.setImageResource(R.drawable.answer_blue_box)
        requireView().findViewById<TextView>(R.id.problemAnswerText).visibility = View.GONE
        requireActivity().findViewById<FrameLayout>(R.id.stateContainer).visibility = View.INVISIBLE
        seeBtn.visibility = View.GONE
        answer.visibility = View.VISIBLE
        checkBtn.text = "CHECK"
        checkBtn.isEnabled = false
        requireActivity().findViewById<FrameLayout>(R.id.check_enabled_btn_container).visibility = View.INVISIBLE
        requireActivity().findViewById<FrameLayout>(R.id.check_disabled_btn_container).visibility = View.VISIBLE
        setupInitialButtonState()
    }

    private fun resetUIForNext() {
        val activity = requireActivity() as Math1GradeQuestionActivity
        activity.isResultCurrentlyVisible = false
        activity.hideSuccessAnimation() // Ensure Mr. Square is reset

        requireActivity().findViewById<FrameLayout>(R.id.stateContainer).visibility = View.INVISIBLE
        answer.visibility = View.VISIBLE
        seeBtn.visibility = View.GONE
        checkBtn.text = "CHECK"

        setupInitialButtonState()
        options.forEach { it.setBackgroundResource(R.drawable.custom_background) }
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaPlayer?.release()
    }
}