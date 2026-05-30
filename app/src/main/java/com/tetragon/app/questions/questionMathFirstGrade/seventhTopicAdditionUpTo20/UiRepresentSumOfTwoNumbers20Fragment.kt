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

class UiRepresentSumOfTwoNumbers20Fragment :
    Fragment(R.layout.fragment_ui_represent_sum_of_two_numbers20) {

    private lateinit var questionText: TextView
    private lateinit var options: List<LinearLayout>
    private lateinit var checkBtn: Button
    private lateinit var checkBtnBack: View
    private lateinit var btnBack: ConstraintLayout
    private lateinit var seeBtn: FrameLayout
    private lateinit var seeEnabledButton: Button
    private lateinit var stateAnswer: TextView
    private lateinit var answer: TextView

    private var correctAnswer: Pair<Int, Int> = 1 to 1
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
        val number = Random.nextInt(2, 21)
        val pairs = mutableListOf<Pair<Int, Int>>()
        for (i in 1 until number) pairs.add(i to (number - i))
        correctAnswer = pairs.random()

        // --- Localized Spannable Logic ---
        val numberStr = number.toString()
        val sumKeyword = getString(R.string.keyword_sum)
        val twoNumbersKeyword = getString(R.string.keyword_two_numbers)
        val fullSentence = getString(R.string.instruction_represent_sum_two, numberStr, twoNumbersKeyword)

        val spannable = SpannableString(fullSentence)
        val blue = ContextCompat.getColor(requireContext(), R.color.blue_2)

        highlightKeyword(spannable, fullSentence, numberStr, blue)
        highlightKeyword(spannable, fullSentence, sumKeyword, blue)
        highlightKeyword(spannable, fullSentence, twoNumbersKeyword, blue)

        questionText.text = spannable

        val usedSums = mutableSetOf<Int>()
        usedSums.add(number)

        val finalOptions = mutableListOf<Pair<Int, Int>>()
        finalOptions.add(correctAnswer)

        while (finalOptions.size < 3) {
            var fakeSum = Random.nextInt(2, 21)
            while (usedSums.contains(fakeSum)) {
                fakeSum = Random.nextInt(2, 21)
            }
            val a = Random.nextInt(1, fakeSum)
            usedSums.add(fakeSum)
            finalOptions.add(a to (fakeSum - a))
        }

        val shuffled = finalOptions.shuffled()
        options.forEachIndexed { index, layout ->
            val tv = layout.getChildAt(0) as TextView
            tv.text = getString(R.string.double_sum_template, shuffled[index].first, shuffled[index].second)
            layout.setBackgroundResource(R.drawable.custom_background)
        }

        resetState()
    }

    private fun highlightKeyword(spannable: SpannableString, full: String, key: String, color: Int) {
        val index = full.indexOf(key)
        if (index != -1) {
            spannable.setSpan(ForegroundColorSpan(color), index, index + key.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
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
        answer.visibility = View.VISIBLE
        setupInitialButtonState()
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

        val isCorrect = (parts[0] + parts[1]) == (correctAnswer.first + correctAnswer.second)

        stateContainer.visibility = View.VISIBLE

        if (isCorrect) {
            playSound(R.raw.correct)
            activity.isCorrectAnswerShowing = true
            activity.playSuccessAnimation()
            val isFinished = activity.incrementProgress()
            if (isFirstAttempt) activity.totalXp += MathGrade1Type.ADDITION_TWO_REPRESENT_20.xp
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
        answer.text = getString(R.string.label_answer, "${correctAnswer.first} + ${correctAnswer.second}")
        applyButtonColors(R.color.green_1, R.color.green_2, R.color.green_4)
    }

    private fun showIncorrectState(container: FrameLayout, circle: ImageView, index: Int) {
        container.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.red_4))
        circle.setImageResource(R.drawable.wrong_circle)
        options[index].setBackgroundResource(R.drawable.option_incorrect)
        stateAnswer.text = getString(R.string.state_incorrect)
        answer.visibility = View.GONE
        seeBtn.visibility = View.VISIBLE
        seeEnabledButton.text = getString(R.string.btn_see_solution)
        applyButtonColors(R.color.red_1, R.color.red_2, R.color.red_4)
    }

    private fun setupSeeSolution(container: FrameLayout, circle: ImageView) {
        seeEnabledButton.setOnClickListener {
            seeBtn.visibility = View.GONE
            stateAnswer.text = getString(R.string.state_solution)
            answer.text = getString(R.string.label_answer, "${correctAnswer.first} + ${correctAnswer.second}")
            answer.visibility = View.VISIBLE
            circle.setImageResource(R.drawable.solution_lamp_icon)
            container.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.gray_2))

            val correctSum = correctAnswer.first + correctAnswer.second
            options.forEach { layout ->
                val tv = layout.getChildAt(0) as TextView
                val sum = tv.text.toString().split("+").sumOf { it.trim().toInt() }
                layout.setBackgroundResource(if (sum == correctSum) R.drawable.option_showed else R.drawable.custom_background)
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
        requireActivity().findViewById<FrameLayout>(R.id.stateContainer).visibility = View.INVISIBLE
        seeBtn.visibility = View.GONE
        stateAnswer.text = ""
        answer.visibility = View.VISIBLE
        setupInitialButtonState()
    }

    private fun resetUIForNext() {
        val activity = requireActivity() as Math1GradeQuestionActivity
        activity.isResultCurrentlyVisible = false
        activity.hideSuccessAnimation()
        requireActivity().findViewById<FrameLayout>(R.id.stateContainer).visibility = View.INVISIBLE
        stateAnswer.text = ""
        answer.text = ""
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