package com.example.tetragon.questions.questionMathSecondGrade.firstTopic

import android.media.MediaPlayer
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.example.tetragon.R
import com.example.tetragon.questions.questionMathSecondGrade.Math2GradeQuestionActivity
import com.example.tetragon.questions.questionMathSecondGrade.MathGrade2Type
import kotlin.random.Random

class UiArithmeticsBasicsFragment : Fragment(R.layout.fragment_ui_arithmetics_basics) {

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

    // ---------------------- Problem Generation ----------------------

    private fun generateProblem() {
        currentProblem = generateArithmeticProblem()
        correctAnswer = evaluateProblem(currentProblem)

        firstNumberText.text = "$currentProblem ="
        problemImage.setImageResource(R.drawable.answer_blue_box)

        // --- FIX: Hide the state container and reset text for the new problem ---
        val activity = requireActivity()
        val stateContainer = activity.findViewById<FrameLayout>(R.id.stateContainer)
        stateContainer.visibility = View.INVISIBLE
        stateAnswer.text = ""
        answer.text = ""
        // -----------------------------------------------------------------------

        val optionSet = mutableSetOf(correctAnswer)
        while (optionSet.size < 3) {
            val dist = Random.nextInt(1, 101)
            if (dist != correctAnswer) optionSet.add(dist)
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

        activity.findViewById<FrameLayout>(R.id.check_enabled_btn_container).visibility = View.INVISIBLE
        activity.findViewById<FrameLayout>(R.id.check_disabled_btn_container).visibility = View.VISIBLE

        setupInitialButtonState()
    }

    private fun generateArithmeticProblem(): String {
        val useThreeTerms = Random.nextBoolean()
        val useParentheses = useThreeTerms && Random.nextBoolean()

        return if (useThreeTerms) {
            if (useParentheses) {
                if (Random.nextBoolean()) {
                    val a = Random.nextInt(1, 50); val b = Random.nextInt(1, 50)
                    "($a+$b)-${Random.nextInt(1, a + b)}"
                } else {
                    val b = Random.nextInt(10, 50); val c = Random.nextInt(1, b)
                    "${Random.nextInt(1, 100 - (b - c))}+($b-$c)"
                }
            } else {
                if (Random.nextBoolean()) {
                    val a = Random.nextInt(1, 50); val b = Random.nextInt(1, 50)
                    "$a+$b-${Random.nextInt(1, a + b)}"
                } else {
                    val a = Random.nextInt(20, 100); val b = Random.nextInt(1, a)
                    "$a-$b+${Random.nextInt(1, 101 - (a - b))}"
                }
            }
        } else {
            if (Random.nextBoolean()) {
                val a = Random.nextInt(1, 100)
                "$a+${Random.nextInt(1, 101 - a)}"
            } else {
                val a = Random.nextInt(2, 101)
                "$a-${Random.nextInt(1, a)}"
            }
        }
    }

    private fun evaluateProblem(problem: String): Int {
        val cleaned = problem.replace("(", "").replace(")", "")
        val tokens = mutableListOf<String>()
        var currentNum = ""
        for (char in cleaned) {
            if (char.isDigit()) currentNum += char
            else {
                if (currentNum.isNotEmpty()) { tokens.add(currentNum); currentNum = "" }
                tokens.add(char.toString())
            }
        }
        if (currentNum.isNotEmpty()) tokens.add(currentNum)

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

    // ---------------------- Interaction ----------------------

    private fun setupOptionClicks() {
        options.forEachIndexed { index, layout ->
            layout.setOnClickListener {
                if (isAnswerChecked) return@setOnClickListener
                selectedOptionIndex = index
                highlightSelectedOption(index)
                enableCheckButton()

                val problemAnswerText = requireView().findViewById<TextView>(R.id.problemAnswerText)
                problemAnswerText.text = (layout.getChildAt(0) as TextView).text
                problemAnswerText.visibility = View.VISIBLE
            }
        }
    }

    private fun highlightSelectedOption(selectedIndex: Int) {
        options.forEachIndexed { i, layout ->
            layout.setBackgroundResource(if (i == selectedIndex) R.drawable.option_selected else R.drawable.custom_background)
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
                    val activity = requireActivity() as Math2GradeQuestionActivity
                    if (checkBtn.text == "FINISH") {
                        activity.navigateToXpGained()
                    } else {
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
        val activity = requireActivity() as Math2GradeQuestionActivity

        activity.isResultCurrentlyVisible = true
        stateContainer.visibility = View.VISIBLE

        val problemAnswerText = requireView().findViewById<TextView>(R.id.problemAnswerText)
        problemAnswerText.text = chosen.toString()
        problemAnswerText.visibility = View.VISIBLE

        if (chosen == correctAnswer) {
            playSound(R.raw.correct)
            activity.isCorrectAnswerShowing = true
            activity.playSuccessAnimation()
            problemImage.setImageResource(R.drawable.answer_correct_box)

            val isFinished = activity.incrementProgress()
            if (isFirstAttempt) activity.totalXp += MathGrade2Type.ARITHMETICS_BASICS.xp

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

    private fun showCorrectState(stateContainer: FrameLayout, circleState: ImageView, index: Int) {
        stateContainer.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.green_3))
        circleState.setImageResource(R.drawable.correct_tick_icon)
        options[index].setBackgroundResource(R.drawable.option_correct)
        stateAnswer.text = "Correct!"
        answer.text = "Answer: $correctAnswer"
        applyButtonColors(R.color.green_1, R.color.green_2, R.color.green_4)
    }

    private fun showIncorrectState(stateContainer: FrameLayout, circleState: ImageView, index: Int) {
        stateContainer.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.red_4))
        circleState.setImageResource(R.drawable.wrong_circle)
        options[index].setBackgroundResource(R.drawable.option_incorrect)
        stateAnswer.text = "Incorrect!"
        answer.visibility = View.GONE
        seeBtn.visibility = View.VISIBLE
        applyButtonColors(R.color.red_1, R.color.red_2, R.color.red_4)
    }

    private fun setupSeeSolution(stateContainer: FrameLayout, circleState: ImageView, wrongIndex: Int) {
        val problemAnswerText = requireView().findViewById<TextView>(R.id.problemAnswerText)
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
            applyButtonColors(R.color.black_3, R.color.black_2, R.color.gray_2)
            stateContainer.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.gray_2))
            options.forEach { layout ->
                val tv = layout.getChildAt(0) as TextView
                layout.setBackgroundResource(if (tv.text.toString().toInt() == correctAnswer) R.drawable.option_showed else R.drawable.custom_background)
            }
        }
    }

    private fun applyButtonColors(btn: Int, btnB: Int, bg: Int) {
        checkBtn.backgroundTintList = ContextCompat.getColorStateList(requireContext(), btn)
        checkBtnBack.backgroundTintList = ContextCompat.getColorStateList(requireContext(), btnB)
        btnBack.setBackgroundColor(ContextCompat.getColor(requireContext(), bg))
    }

    private fun resetForTryAgain() {
        val activity = requireActivity() as Math2GradeQuestionActivity
        activity.isResultCurrentlyVisible = false
        val problemAnswerText = requireView().findViewById<TextView>(R.id.problemAnswerText)
        val stateContainer = activity.findViewById<FrameLayout>(R.id.stateContainer)

        isAnswerChecked = false
        isIncorrectAttempt = false
        selectedOptionIndex = null
        options.forEach { it.setBackgroundResource(R.drawable.custom_background) }
        problemImage.setImageResource(R.drawable.answer_blue_box)
        problemAnswerText.visibility = View.GONE
        stateContainer.visibility = View.INVISIBLE
        seeBtn.visibility = View.GONE
        stateAnswer.text = ""
        answer.visibility = View.VISIBLE
        answer.text = ""
        checkBtn.text = "CHECK"
        checkBtn.isEnabled = false

        activity.findViewById<FrameLayout>(R.id.check_enabled_btn_container).visibility = View.INVISIBLE
        activity.findViewById<FrameLayout>(R.id.check_disabled_btn_container).visibility = View.VISIBLE
        setupInitialButtonState()
    }

    private fun resetUIForNext() {
        val activity = requireActivity() as Math2GradeQuestionActivity
        activity.isResultCurrentlyVisible = false
        activity.hideSuccessAnimation()
        activity.findViewById<FrameLayout>(R.id.stateContainer).visibility = View.INVISIBLE
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