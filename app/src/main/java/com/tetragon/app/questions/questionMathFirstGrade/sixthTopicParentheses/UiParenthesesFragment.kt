package com.tetragon.app.questions.questionMathFirstGrade.sixthTopicParentheses

import android.media.MediaPlayer
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.tetragon.app.R
import com.tetragon.app.questions.questionMathFirstGrade.Math1GradeQuestionActivity
import com.tetragon.app.questions.questionMathFirstGrade.MathGrade1Type
import kotlin.random.Random

class UiParenthesesFragment : Fragment(R.layout.fragment_ui_parentheses) {

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

        val activity = requireActivity()
        checkBtn = activity.findViewById(R.id.check_enabled_btn)
        checkBtnBack = activity.findViewById(R.id.check_enabled_button_background)
        btnBack = activity.findViewById(R.id.btnBackground)
        seeBtn = activity.findViewById(R.id.see_btn_container)
        seeEnabledButton = activity.findViewById(R.id.see_enabled_btn)
        stateAnswer = activity.findViewById(R.id.stateAnswer)
        answer = activity.findViewById(R.id.answer)

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

    private fun generateProblem() {
        currentProblem = generateParenthesesProblem()
        correctAnswer = evaluateParenthesesProblem(currentProblem)

        // Using a template for the math problem display
        firstNumberText.text = getString(R.string.math_problem_equal_template, currentProblem)
        problemImage.setImageResource(R.drawable.answer_blue_box)

        val optionSet = mutableSetOf(correctAnswer)
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
        checkBtn.text = getString(R.string.btn_check)

        requireActivity().findViewById<FrameLayout>(R.id.check_enabled_btn_container).visibility = View.INVISIBLE
        requireActivity().findViewById<FrameLayout>(R.id.check_disabled_btn_container).visibility = View.VISIBLE

        setupInitialButtonState()
    }

    private fun generateParenthesesProblem(): String {
        val isAddition = Random.nextBoolean()
        val useParenthesesAtEnd = Random.nextBoolean()
        val maxTotal = 10

        return if (isAddition) {
            val a = Random.nextInt(1, 6)
            val b = Random.nextInt(1, 4)
            val remaining = maxTotal - (a + b)
            val c = if (remaining > 1) Random.nextInt(1, remaining + 1) else 1

            if (useParenthesesAtEnd) "$a+($b+$c)" else "($a+$b)+$c"
        } else {
            val first = Random.nextInt(5, 11)
            if (useParenthesesAtEnd) {
                val maxInnerSum = first - 1
                val b = Random.nextInt(1, maxInnerSum)
                val c = Random.nextInt(1, (maxInnerSum - b) + 1)
                "$first-($b+$c)"
            } else {
                val b = Random.nextInt(1, first - 1)
                val currentStep = first - b
                val c = Random.nextInt(1, currentStep)
                "($first-$b)-$c"
            }
        }
    }

    private fun evaluateParenthesesProblem(problem: String): Int {
        val startIndex = problem.indexOf('(')
        val endIndex = problem.indexOf(')')

        return if (startIndex != -1 && endIndex != -1) {
            val innerExp = problem.substring(startIndex + 1, endIndex)
            val innerResult = solveSimple(innerExp)
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
                val cleanParts = expression.split("-").filter { it.isNotEmpty() }
                if (cleanParts.size == 2) {
                    cleanParts[0].toInt() - cleanParts[1].toInt()
                } else {
                    cleanParts[0].toInt()
                }
            }
            else -> expression.toInt()
        }
    }

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
        }
    }

    private fun checkAnswer(index: Int, stateContainer: FrameLayout, circleState: ImageView) {
        isAnswerChecked = true
        val chosenText = (options[index].getChildAt(0) as TextView).text.toString()
        val chosen = chosenText.toInt()
        val activity = requireActivity() as Math1GradeQuestionActivity
        activity.isResultCurrentlyVisible = true
        stateContainer.visibility = View.VISIBLE

        val problemAnswerText = requireView().findViewById<TextView>(R.id.problemAnswerText)
        problemAnswerText.text = chosenText

        if (chosen == correctAnswer) {
            playSound(R.raw.correct)
            activity.isCorrectAnswerShowing = true
            activity.playSuccessAnimation()
            problemImage.setImageResource(R.drawable.answer_correct_box)

            val isFinished = activity.incrementProgress()
            if (isFirstAttempt) {
                activity.totalXp += MathGrade1Type.PARENTHESES.xp
            }
            activity.handleCorrectAnswer()

            isIncorrectAttempt = false
            checkBtn.text = if (isFinished) getString(R.string.btn_finish) else getString(R.string.btn_continue)
            showCorrectState(stateContainer, circleState, index)
        } else {
            playSound(R.raw.wrong)
            problemImage.setImageResource(R.drawable.answer_incorrect_box)
            isIncorrectAttempt = true
            activity.handleIncorrectAnswer()
            checkBtn.text = getString(R.string.btn_try_again)
            showIncorrectState(stateContainer, circleState, index)
            setupSeeSolution(stateContainer, circleState)
        }
        isFirstAttempt = false
    }

    private fun showCorrectState(container: FrameLayout, circle: ImageView, index: Int) {
        container.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.green_3))
        circle.setImageResource(R.drawable.correct_tick_icon)
        options[index].setBackgroundResource(R.drawable.option_correct)
        stateAnswer.text = getString(R.string.state_correct)
        answer.text = getString(R.string.label_answer, correctAnswer.toString())
        applyButtonColors(R.color.green_1, R.color.green_2, R.color.green_4)
    }

    private fun showIncorrectState(container: FrameLayout, circle: ImageView, index: Int) {
        container.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.red_4))
        circle.setImageResource(R.drawable.wrong_circle)
        options[index].setBackgroundResource(R.drawable.option_incorrect)
        stateAnswer.text = getString(R.string.state_incorrect)
        answer.visibility = View.GONE
        seeEnabledButton.text = getString(R.string.btn_see_solution)
        seeBtn.visibility = View.VISIBLE
        applyButtonColors(R.color.red_1, R.color.red_2, R.color.red_4)
    }

    private fun setupSeeSolution(container: FrameLayout, circle: ImageView) {
        seeEnabledButton.setOnClickListener {
            seeBtn.visibility = View.GONE
            stateAnswer.text = getString(R.string.state_solution)
            answer.text = getString(R.string.label_answer, correctAnswer.toString())
            answer.visibility = View.VISIBLE
            checkBtn.text = getString(R.string.btn_continue)
            problemImage.setImageResource(R.drawable.answer_solution_box)
            requireView().findViewById<TextView>(R.id.problemAnswerText).text = correctAnswer.toString()
            requireView().findViewById<TextView>(R.id.problemAnswerText).visibility = View.VISIBLE
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
        checkBtn.text = getString(R.string.btn_check)
        checkBtn.isEnabled = false
        requireActivity().findViewById<FrameLayout>(R.id.check_enabled_btn_container).visibility = View.INVISIBLE
        requireActivity().findViewById<FrameLayout>(R.id.check_disabled_btn_container).visibility = View.VISIBLE
        setupInitialButtonState()
    }

    private fun resetUIForNext() {
        val activity = requireActivity() as Math1GradeQuestionActivity
        activity.isResultCurrentlyVisible = false
        activity.hideSuccessAnimation()

        requireActivity().findViewById<FrameLayout>(R.id.stateContainer).visibility = View.INVISIBLE
        answer.visibility = View.VISIBLE
        seeBtn.visibility = View.GONE
        checkBtn.text = getString(R.string.btn_check)

        setupInitialButtonState()
        options.forEach { it.setBackgroundResource(R.drawable.custom_background) }
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaPlayer?.release()
    }
}