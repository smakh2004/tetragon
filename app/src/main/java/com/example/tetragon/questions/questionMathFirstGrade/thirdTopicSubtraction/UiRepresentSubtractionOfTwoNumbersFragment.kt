package com.example.tetragon.questions.questionMathFirstGrade.thirdTopicSubtraction

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
import com.example.tetragon.R
import com.example.tetragon.questions.questionMathFirstGrade.Math1GradeQuestionActivity
import com.example.tetragon.questions.questionMathFirstGrade.MathGrade1Type
import kotlin.random.Random

class UiRepresentSubtractionOfTwoNumbersFragment :
    Fragment(R.layout.fragment_ui_represent_subtraction_of_two_numbers) {

    private lateinit var questionText: TextView
    private lateinit var options: List<LinearLayout>
    private lateinit var checkBtn: Button
    private lateinit var checkBtnBack: View
    private lateinit var btnBack: ConstraintLayout
    private lateinit var seeBtn: FrameLayout
    private lateinit var seeEnabledButton: Button
    private lateinit var stateAnswer: TextView
    private lateinit var answer: TextView

    // Correct answer stored as (Minuend, Subtrahend) e.g., 10 - 4
    private var correctAnswer: Pair<Int, Int> = 10 to 4
    private var selectedOptionIndex: Int? = null
    private var isAnswerChecked = false
    private var isIncorrectAttempt = false
    private var isFirstAttempt = true
    private var mediaPlayer: MediaPlayer? = null

    private lateinit var checkEnabledContainer: FrameLayout
    private lateinit var checkDisabledContainer: FrameLayout
    private lateinit var stateContainer: FrameLayout
    private lateinit var circleState: ImageView

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

        checkBtn = requireActivity().findViewById(R.id.check_enabled_btn)
        checkBtnBack = requireActivity().findViewById(R.id.check_enabled_button_background)
        btnBack = requireActivity().findViewById(R.id.btnBackground)
        seeBtn = requireActivity().findViewById(R.id.see_btn_container)
        seeEnabledButton = requireActivity().findViewById(R.id.see_enabled_btn)
        stateAnswer = requireActivity().findViewById(R.id.stateAnswer)
        answer = requireActivity().findViewById(R.id.answer)
        checkEnabledContainer = view.findViewById(R.id.check_enabled_btn_container)
        checkDisabledContainer = view.findViewById(R.id.check_disabled_btn_container)
        stateContainer = view.findViewById(R.id.stateContainer)
        circleState = view.findViewById(R.id.circleState)
    }

    private fun setupInitialButtonState() {
        btnBack.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.white))
        checkBtn.backgroundTintList =
            ContextCompat.getColorStateList(requireContext(), R.color.blue_2)
        checkBtnBack.backgroundTintList =
            ContextCompat.getColorStateList(requireContext(), R.color.blue_1)
        checkBtn.isEnabled = false
    }

    // ---------------------- Generate Subtraction Problem ----------------------
    private fun generateProblem() {
        // 1. Pick the target result the child needs to find (1 to 9)
        val targetResult = Random.nextInt(1, 10)

        // 2. Generate all possible pairs (a - b = targetResult) where a <= 10
        val possiblePairs = mutableListOf<Pair<Int, Int>>()
        for (minuend in (targetResult + 1)..10) {
            val subtrahend = minuend - targetResult
            possiblePairs.add(minuend to subtrahend)
        }

        // 3. Select the correct answer from valid pairs
        correctAnswer = if (possiblePairs.isNotEmpty()) {
            possiblePairs.random()
        } else {
            // Fallback just in case: if target is 9, only 10-1 works
            10 to (10 - targetResult)
        }

        // --- Question Text Styling ---
        val sentence = "Represent $targetResult as subtraction of two numbers."
        val spannable = SpannableString(sentence)
        val blue = ContextCompat.getColor(requireContext(), R.color.blue_2)

        val numberIndex = sentence.indexOf(targetResult.toString())
        if (numberIndex != -1) {
            spannable.setSpan(
                ForegroundColorSpan(blue),
                numberIndex,
                numberIndex + targetResult.toString().length,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }

        val subIndex = sentence.indexOf("subtraction")
        if (subIndex != -1) {
            spannable.setSpan(
                ForegroundColorSpan(blue),
                subIndex,
                subIndex + "subtraction".length,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
        questionText.text = spannable

        // --- Generate Options ---
        val optionSet = mutableSetOf(correctAnswer)
        while (optionSet.size < 3) {
            val fakeMinuend = Random.nextInt(2, 11) // Max 10
            val fakeSubtrahend = Random.nextInt(1, fakeMinuend) // Ensure positive result

            // Ensure fake answer doesn't accidentally equal targetResult
            if (fakeMinuend - fakeSubtrahend != targetResult) {
                optionSet.add(fakeMinuend to fakeSubtrahend)
            }
        }

        val shuffled = optionSet.shuffled()
        options.forEachIndexed { index, layout ->
            val tv = layout.getChildAt(0) as TextView
            tv.text = "${shuffled[index].first} - ${shuffled[index].second}"
            layout.setBackgroundResource(R.drawable.custom_background)
        }

        resetFragmentState()
    }

    private fun resetFragmentState() {
        selectedOptionIndex = null
        isAnswerChecked = false
        isIncorrectAttempt = false
        isFirstAttempt = true

        checkBtn.isEnabled = false
        checkBtn.text = "CHECK"

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
            layout.setBackgroundResource(
                if (i == index) R.drawable.option_selected
                else R.drawable.custom_background
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
                    if (checkBtn.text == "FINISH") {
                        activity.navigateToXpGained()
                    } else {
                        // GATE: Trigger milestone only after the user chooses to proceed
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
        val parts = chosenText.split("-").map { it.trim().toInt() }

        // Order matters in subtraction representation
        val isCorrect = parts[0] == correctAnswer.first && parts[1] == correctAnswer.second

        stateContainer.visibility = View.VISIBLE

        if (isCorrect) {
            playSound(R.raw.correct)
            activity.isCorrectAnswerShowing = true

            // 1. Play success animation (Mr. Square)
            activity.playSuccessAnimation()

            val isFinished = activity.incrementProgress()

            if (isFirstAttempt) {
                activity.totalXp += MathGrade1Type.SUBTRACTION_TWO_REPRESENTATION.xp
            }

            // 2. Register correct answer for milestones/streaks
            activity.handleCorrectAnswer()

            isIncorrectAttempt = false
            checkBtn.text = if (isFinished) "FINISH" else "CONTINUE"
            showCorrectState(stateContainer, circleState, index)
        } else {
            playSound(R.raw.wrong)
            isIncorrectAttempt = true
            checkBtn.text = "TRY AGAIN"
            showIncorrectState(stateContainer, circleState, index)
            setupSeeSolution(stateContainer, circleState)
        }
        isFirstAttempt = false
    }

    private fun showCorrectState(stateContainer: FrameLayout, circleState: ImageView, index: Int) {
        stateContainer.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.green_3))
        circleState.setImageResource(R.drawable.correct_tick_icon)
        options[index].setBackgroundResource(R.drawable.option_correct)
        stateAnswer.text = "Correct!"
        answer.text = "Answer: ${correctAnswer.first} - ${correctAnswer.second}"
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

    private fun setupSeeSolution(stateContainer: FrameLayout, circleState: ImageView) {
        seeEnabledButton.setOnClickListener {
            seeBtn.visibility = View.GONE
            stateAnswer.text = "Solution"
            answer.text = "Answer: ${correctAnswer.first} - ${correctAnswer.second}"
            answer.visibility = View.VISIBLE
            circleState.setImageResource(R.drawable.solution_lamp_icon)
            stateContainer.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.gray_2))

            options.forEach { layout ->
                val tv = layout.getChildAt(0) as TextView
                val parts = tv.text.toString().split("-").map { it.trim().toInt() }
                val isCorrect = parts[0] == correctAnswer.first && parts[1] == correctAnswer.second
                layout.setBackgroundResource(if (isCorrect) R.drawable.option_showed else R.drawable.custom_background)
            }

            applyButtonColors(R.color.black_3, R.color.black_2, R.color.gray_2)
            checkBtn.text = "CONTINUE"
            isIncorrectAttempt = false
            isAnswerChecked = true
        }
    }

    private fun applyButtonColors(buttonColor: Int, backColor: Int, backgroundColor: Int) {
        checkBtn.backgroundTintList = ContextCompat.getColorStateList(requireContext(), buttonColor)
        checkBtnBack.backgroundTintList = ContextCompat.getColorStateList(requireContext(), backColor)
        btnBack.setBackgroundColor(ContextCompat.getColor(requireContext(), backgroundColor))
    }

    private fun resetForTryAgain() {
        val activity = requireActivity() as Math1GradeQuestionActivity
        activity.isResultCurrentlyVisible = false

        isAnswerChecked = false
        isIncorrectAttempt = false
        selectedOptionIndex = null
        options.forEach { it.setBackgroundResource(R.drawable.custom_background) }
        checkBtn.text = "CHECK"
        checkBtn.isEnabled = false
        requireActivity().findViewById<FrameLayout>(R.id.check_enabled_btn_container).visibility = View.INVISIBLE
        requireActivity().findViewById<FrameLayout>(R.id.check_disabled_btn_container).visibility = View.VISIBLE
        btnBack.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.white))
        checkBtn.backgroundTintList = ContextCompat.getColorStateList(requireContext(), R.color.blue_2)
        checkBtnBack.backgroundTintList = ContextCompat.getColorStateList(requireContext(), R.color.blue_1)
        requireActivity().findViewById<FrameLayout>(R.id.stateContainer).visibility = View.INVISIBLE
        seeBtn.visibility = View.GONE
        stateAnswer.text = ""
        answer.visibility = View.VISIBLE
    }

    private fun resetUIForNext() {
        val activity = requireActivity() as Math1GradeQuestionActivity
        activity.isResultCurrentlyVisible = false
        activity.hideSuccessAnimation() // Clear Mr. Square state

        requireActivity().findViewById<FrameLayout>(R.id.stateContainer).visibility = View.INVISIBLE
        stateAnswer.text = ""
        answer.text = ""
        answer.visibility = View.VISIBLE
        seeBtn.visibility = View.GONE
        checkBtn.text = "CHECK"

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