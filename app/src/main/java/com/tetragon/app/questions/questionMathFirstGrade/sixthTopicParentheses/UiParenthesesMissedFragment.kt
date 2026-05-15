package com.tetragon.app.questions.questionMathFirstGrade.sixthTopicParentheses

import android.media.MediaPlayer
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.view.View
import android.widget.*
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.tetragon.app.R
import com.tetragon.app.questions.questionMathFirstGrade.Math1GradeQuestionActivity
import com.tetragon.app.questions.questionMathFirstGrade.MathGrade1Type
import kotlin.random.Random

class UiParenthesesMissedFragment : Fragment(R.layout.fragment_ui_parentheses_missed) {

    private lateinit var firstNumberText: TextView
    private lateinit var secondNumberText: TextView
    private lateinit var instructionText: TextView
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
    private var mediaPlayer: MediaPlayer? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initViews(view)
        setupInstructionText()
        setupInitialButtonState()
        generateProblem()
        setupOptionClicks()
        setupCheckButton()
    }

    private fun initViews(view: View) {
        firstNumberText = view.findViewById(R.id.firstNumber)
        secondNumberText = view.findViewById(R.id.secondNumber)
        problemImage = view.findViewById(R.id.problemImage)
        instructionText = view.findViewById(R.id.instructionText)

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
    }

    private fun setupInstructionText() {
        val wordToStyle = getString(R.string.instruction_missed_word)
        val fullText = getString(R.string.instruction_find_missed2, wordToStyle)
        val spannable = SpannableString(fullText)

        val start = fullText.indexOf(wordToStyle)
        if (start != -1) {
            spannable.setSpan(
                ForegroundColorSpan(ContextCompat.getColor(requireContext(), R.color.blue_2)),
                start,
                start + wordToStyle.length,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
        instructionText.text = spannable
    }

    private fun generateProblem() {
        val maxTotal = 10
        val isAddition = Random.nextBoolean()
        val a: Int
        val b: Int
        val c: Int
        val finalResult: Int
        val op1: String
        val op2: String
        val useParenthesesAtEnd = Random.nextBoolean()

        if (isAddition) {
            op1 = "+"
            op2 = "+"
            a = Random.nextInt(1, 6)
            b = Random.nextInt(1, 4)
            val remaining = maxTotal - (a + b)
            c = if (remaining > 1) Random.nextInt(1, remaining + 1) else 1
            finalResult = a + b + c
        } else {
            if (useParenthesesAtEnd) {
                a = Random.nextInt(5, 11)
                val maxInnerSum = a - 1
                b = Random.nextInt(1, maxInnerSum)
                c = Random.nextInt(1, (maxInnerSum - b) + 1)
                finalResult = a - (b + c)
                op1 = "-"
                op2 = "+"
            } else {
                a = Random.nextInt(5, 11)
                b = Random.nextInt(1, a - 1)
                val firstStepResult = a - b
                c = Random.nextInt(1, firstStepResult)
                finalResult = firstStepResult - c
                op1 = "-"
                op2 = "-"
            }
        }

        val hiddenIndex = Random.nextInt(0, 3)
        when (hiddenIndex) {
            0 -> {
                correctAnswer = a
                if (useParenthesesAtEnd) {
                    firstNumberText.text = ""
                    secondNumberText.text = "$op1($b$op2$c)=$finalResult"
                } else {
                    firstNumberText.text = "("
                    secondNumberText.text = "$op1$b)$op2$c=$finalResult"
                }
            }
            1 -> {
                correctAnswer = b
                if (useParenthesesAtEnd) {
                    firstNumberText.text = "$a$op1("
                    secondNumberText.text = "$op2$c)=$finalResult"
                } else {
                    firstNumberText.text = "($a$op1"
                    secondNumberText.text = ")$op2$c=$finalResult"
                }
            }
            else -> {
                correctAnswer = c
                if (useParenthesesAtEnd) {
                    firstNumberText.text = "$a$op1($b$op2"
                    secondNumberText.text = ")=$finalResult"
                } else {
                    firstNumberText.text = "($a$op1$b)$op2"
                    secondNumberText.text = "=$finalResult"
                }
            }
        }
        setupOptions()
    }

    private fun setupOptions() {
        problemImage.setImageResource(R.drawable.answer_blue_box)
        val optionSet = mutableSetOf(correctAnswer)
        while (optionSet.size < 3) {
            optionSet.add(Random.nextInt(1, 11))
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

    private fun setupOptionClicks() {
        options.forEachIndexed { index, layout ->
            layout.setOnClickListener {
                if (isAnswerChecked) return@setOnClickListener
                selectedOptionIndex = index
                options.forEachIndexed { i, l ->
                    l.setBackgroundResource(if (i == index) R.drawable.option_selected else R.drawable.custom_background)
                }

                checkBtn.isEnabled = true
                requireActivity().findViewById<FrameLayout>(R.id.check_enabled_btn_container).visibility = View.VISIBLE
                requireActivity().findViewById<FrameLayout>(R.id.check_disabled_btn_container).visibility = View.INVISIBLE

                val problemAnswerText = requireView().findViewById<TextView>(R.id.problemAnswerText)
                problemAnswerText.text = (layout.getChildAt(0) as TextView).text
                problemAnswerText.visibility = View.VISIBLE
            }
        }
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
        val chosen = (options[index].getChildAt(0) as TextView).text.toString().toInt()
        val activity = requireActivity() as Math1GradeQuestionActivity
        activity.isResultCurrentlyVisible = true
        stateContainer.visibility = View.VISIBLE

        if (chosen == correctAnswer) {
            playSound(R.raw.correct)
            activity.isCorrectAnswerShowing = true
            activity.playSuccessAnimation()
            problemImage.setImageResource(R.drawable.answer_correct_box)

            val isFinished = activity.incrementProgress()
            if (isFirstAttempt) {
                activity.totalXp += MathGrade1Type.PARENTHESES_MISSED.xp
            }
            activity.handleCorrectAnswer()

            isIncorrectAttempt = false
            checkBtn.text = if (isFinished) getString(R.string.btn_finish) else getString(R.string.btn_continue)
            showResultState(stateContainer, circleState, index, true)
        } else {
            playSound(R.raw.wrong)
            problemImage.setImageResource(R.drawable.answer_incorrect_box)
            isIncorrectAttempt = true
            checkBtn.text = getString(R.string.btn_try_again)
            showResultState(stateContainer, circleState, index, false)
            setupSeeSolution(stateContainer, circleState)
        }
        isFirstAttempt = false
    }

    private fun showResultState(container: FrameLayout, circle: ImageView, index: Int, isCorrect: Boolean) {
        if (isCorrect) {
            container.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.green_3))
            circle.setImageResource(R.drawable.correct_tick_icon)
            options[index].setBackgroundResource(R.drawable.option_correct)
            stateAnswer.text = getString(R.string.state_correct)
            answer.text = getString(R.string.label_answer, correctAnswer.toString())
            applyButtonColors(R.color.green_1, R.color.green_2, R.color.green_4)
        } else {
            container.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.red_4))
            circle.setImageResource(R.drawable.wrong_circle)
            options[index].setBackgroundResource(R.drawable.option_incorrect)
            stateAnswer.text = getString(R.string.state_incorrect)
            answer.visibility = View.GONE
            seeEnabledButton.text = getString(R.string.btn_see_solution)
            seeBtn.visibility = View.VISIBLE
            applyButtonColors(R.color.red_1, R.color.red_2, R.color.red_4)
        }
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
            circle.setImageResource(R.drawable.solution_lamp_icon)
            isAnswerChecked = true
            isIncorrectAttempt = false
            applyButtonColors(R.color.black_3, R.color.black_2, R.color.gray_2)
            container.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.gray_2))
            options.forEach { layout ->
                val tv = layout.getChildAt(0) as TextView
                layout.setBackgroundResource(if (tv.text.toString().toInt() == correctAnswer) R.drawable.option_showed else R.drawable.custom_background)
            }
        }
    }

    private fun applyButtonColors(btn: Int, back: Int, bg: Int) {
        checkBtn.backgroundTintList = ContextCompat.getColorStateList(requireContext(), btn)
        checkBtnBack.backgroundTintList = ContextCompat.getColorStateList(requireContext(), back)
        btnBack.setBackgroundColor(ContextCompat.getColor(requireContext(), bg))
    }

    private fun setupInitialButtonState() {
        btnBack.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.white))
        checkBtn.backgroundTintList = ContextCompat.getColorStateList(requireContext(), R.color.blue_2)
        checkBtnBack.backgroundTintList = ContextCompat.getColorStateList(requireContext(), R.color.blue_1)
        checkBtn.isEnabled = false
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

    private fun playSound(id: Int) {
        mediaPlayer?.release()
        mediaPlayer = MediaPlayer.create(requireContext(), id)
        mediaPlayer?.start()
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaPlayer?.release()
    }
}