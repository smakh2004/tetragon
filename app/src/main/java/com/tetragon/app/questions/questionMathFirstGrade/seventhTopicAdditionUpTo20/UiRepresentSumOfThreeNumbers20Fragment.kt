package com.tetragon.app.questions.questionMathFirstGrade.seventhTopicAdditionUpTo20

import android.media.MediaPlayer
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.view.View
import android.widget.*
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.tetragon.app.R
import com.tetragon.app.questions.questionMathFirstGrade.Math1GradeQuestionActivity
import com.tetragon.app.questions.questionMathFirstGrade.MathGrade1Type
import kotlin.random.Random

class UiRepresentSumOfThreeNumbers20Fragment :
    Fragment(R.layout.fragment_ui_represent_sum_of_three_numbers20) {

    private lateinit var questionText: TextView
    private lateinit var options: List<LinearLayout>
    private lateinit var checkBtn: Button
    private lateinit var checkBtnBack: View
    private lateinit var btnBack: ConstraintLayout
    private lateinit var seeBtn: FrameLayout
    private lateinit var seeEnabledButton: Button
    private lateinit var stateAnswer: TextView
    private lateinit var answer: TextView

    private var correctAnswer: Triple<Int, Int, Int> = Triple(1, 1, 1)
    private var selectedOptionIndex: Int? = null
    private var isAnswerChecked = false
    private var isIncorrectAttempt = false
    private var isFirstAttempt = true
    private var mediaPlayer: MediaPlayer? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initViews(view)
        setupInitialButtonState()
        generateProblem()
        setupOptionClicks()
        setupCheckButton()
    }

    private fun initViews(view: View) {
        questionText = view.findViewById(R.id.questionText)

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

    private fun setupInitialButtonState() {
        btnBack.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.white))
        checkBtn.backgroundTintList = ContextCompat.getColorStateList(requireContext(), R.color.blue_2)
        checkBtnBack.backgroundTintList = ContextCompat.getColorStateList(requireContext(), R.color.blue_1)
        checkBtn.isEnabled = false
    }

    private fun generateProblem() {
        val number = Random.nextInt(3, 21)

        val triples = mutableListOf<Triple<Int, Int, Int>>()
        for (a in 1..number - 2) {
            for (b in 1..number - a - 1) {
                val c = number - a - b
                if (c > 0) triples.add(Triple(a, b, c))
            }
        }
        correctAnswer = triples.random()

        // --- Localized Spannable Logic ---
        val numberStr = number.toString()
        val threeNumbersKeyword = getString(R.string.keyword_three_numbers)
        val fullSentence = getString(R.string.instruction_represent_sum, numberStr, threeNumbersKeyword)
        val spannable = SpannableString(fullSentence)
        val blueColor = ContextCompat.getColor(requireContext(), R.color.blue_2)

        // Highlight the Target Number
        val numberIndex = fullSentence.indexOf(numberStr)
        if (numberIndex != -1) {
            spannable.setSpan(ForegroundColorSpan(blueColor), numberIndex, numberIndex + numberStr.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }

        // Highlight "three numbers" (localized)
        val keywordIndex = fullSentence.indexOf(threeNumbersKeyword)
        if (keywordIndex != -1) {
            spannable.setSpan(ForegroundColorSpan(blueColor), keywordIndex, keywordIndex + threeNumbersKeyword.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        questionText.text = spannable

        val usedSums = mutableSetOf<Int>()
        usedSums.add(number)

        val finalOptions = mutableListOf<Triple<Int, Int, Int>>()
        finalOptions.add(correctAnswer)

        while (finalOptions.size < 3) {
            var fakeSum = Random.nextInt(3, 21)
            while (usedSums.contains(fakeSum)) {
                fakeSum = Random.nextInt(3, 21)
            }
            val fA = Random.nextInt(1, fakeSum - 1)
            val fB = Random.nextInt(1, fakeSum - fA)
            val fC = fakeSum - fA - fB
            if (fC > 0) {
                usedSums.add(fakeSum)
                finalOptions.add(Triple(fA, fB, fC))
            }
        }

        val shuffled = finalOptions.shuffled()
        options.forEachIndexed { index, layout ->
            val triple = shuffled[index]
            val tv = layout.getChildAt(0) as TextView
            tv.text = getString(R.string.triple_sum_template, triple.first, triple.second, triple.third)
            layout.setBackgroundResource(R.drawable.custom_background)
        }

        resetState()
    }

    private fun resetState() {
        selectedOptionIndex = null
        isAnswerChecked = false
        isIncorrectAttempt = false
        isFirstAttempt = true
        checkBtn.isEnabled = false
        checkBtn.text = getString(R.string.btn_check)
        requireActivity().findViewById<FrameLayout>(R.id.check_enabled_btn_container).visibility = View.INVISIBLE
        requireActivity().findViewById<FrameLayout>(R.id.check_disabled_btn_container).visibility = View.VISIBLE
        requireActivity().findViewById<FrameLayout>(R.id.stateContainer).visibility = View.INVISIBLE
        seeBtn.visibility = View.GONE
        stateAnswer.text = ""
        answer.text = ""
    }

    private fun setupOptionClicks() {
        options.forEachIndexed { index, layout ->
            layout.setOnClickListener {
                if (isAnswerChecked) return@setOnClickListener
                selectedOptionIndex = index
                highlightSelectedOption(index)
                enableCheckButton()
            }
        }
    }

    private fun highlightSelectedOption(index: Int) {
        options.forEachIndexed { i, layout ->
            layout.setBackgroundResource(if (i == index) R.drawable.option_selected else R.drawable.custom_background)
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
        val activity = requireActivity() as Math1GradeQuestionActivity
        activity.isResultCurrentlyVisible = true

        val chosenText = (options[index].getChildAt(0) as TextView).text.toString()
        val parts = chosenText.split("+").map { it.trim().toInt() }

        val chosenSorted = parts.sorted()
        val correctSorted = listOf(correctAnswer.first, correctAnswer.second, correctAnswer.third).sorted()
        val isCorrect = chosenSorted == correctSorted

        stateContainer.visibility = View.VISIBLE

        if (isCorrect) {
            playSound(R.raw.correct)
            activity.isCorrectAnswerShowing = true
            activity.playSuccessAnimation()

            val isFinished = activity.incrementProgress()
            if (isFirstAttempt) {
                activity.totalXp += MathGrade1Type.ADDITION_THREE_REPRESENTATION_20.xp
            }

            activity.handleCorrectAnswer()
            isIncorrectAttempt = false
            checkBtn.text = if (isFinished) getString(R.string.btn_finish) else getString(R.string.btn_continue)
            showCorrectState(stateContainer, circleState, index)
        } else {
            playSound(R.raw.wrong)
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
        answer.text = getString(R.string.label_answer, "${correctAnswer.first} + ${correctAnswer.second} + ${correctAnswer.third}")
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
            answer.text = getString(R.string.label_answer, "${correctAnswer.first} + ${correctAnswer.second} + ${correctAnswer.third}")
            answer.visibility = View.VISIBLE
            circle.setImageResource(R.drawable.solution_lamp_icon)
            container.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.gray_2))

            val correctSorted = listOf(correctAnswer.first, correctAnswer.second, correctAnswer.third).sorted()

            options.forEach { layout ->
                val tv = layout.getChildAt(0) as TextView
                val parts = tv.text.toString().split("+").map { it.trim().toInt() }
                val isCorrect = parts.sorted() == correctSorted

                layout.setBackgroundResource(if (isCorrect) R.drawable.option_showed else R.drawable.custom_background)
            }

            applyButtonColors(R.color.black_3, R.color.black_2, R.color.gray_2)
            checkBtn.text = getString(R.string.btn_continue)
            isIncorrectAttempt = false
            isAnswerChecked = true
        }
    }

    private fun applyButtonColors(btn: Int, back: Int, bg: Int) {
        checkBtn.backgroundTintList = ContextCompat.getColorStateList(requireContext(), btn)
        checkBtnBack.backgroundTintList = ContextCompat.getColorStateList(requireContext(), back)
        btnBack.setBackgroundColor(ContextCompat.getColor(requireContext(), bg))
    }

    private fun resetForTryAgain() {
        val activity = requireActivity() as Math1GradeQuestionActivity
        activity.isResultCurrentlyVisible = false
        isAnswerChecked = false
        isIncorrectAttempt = false
        selectedOptionIndex = null

        options.forEach { it.setBackgroundResource(R.drawable.custom_background) }
        checkBtn.text = getString(R.string.btn_check)
        checkBtn.isEnabled = false
        requireActivity().findViewById<FrameLayout>(R.id.check_enabled_btn_container).visibility = View.INVISIBLE
        requireActivity().findViewById<FrameLayout>(R.id.check_disabled_btn_container).visibility = View.VISIBLE
        setupInitialButtonState()
        requireActivity().findViewById<FrameLayout>(R.id.stateContainer).visibility = View.INVISIBLE
        seeBtn.visibility = View.GONE
        stateAnswer.text = ""
        answer.visibility = View.VISIBLE
    }

    private fun resetUIForNext() {
        val activity = requireActivity() as Math1GradeQuestionActivity
        activity.isResultCurrentlyVisible = false
        activity.hideSuccessAnimation()
        requireActivity().findViewById<FrameLayout>(R.id.stateContainer).visibility = View.INVISIBLE
        stateAnswer.text = ""
        answer.text = ""
        answer.visibility = View.VISIBLE
        seeBtn.visibility = View.GONE
        checkBtn.text = getString(R.string.btn_check)
        setupInitialButtonState()
        options.forEach { it.setBackgroundResource(R.drawable.custom_background) }
    }

    private fun playSound(resId: Int) {
        mediaPlayer?.release()
        mediaPlayer = MediaPlayer.create(requireContext(), resId)
        mediaPlayer?.setOnCompletionListener { it.release() }
        mediaPlayer?.start()
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaPlayer?.release()
        mediaPlayer = null
    }
}