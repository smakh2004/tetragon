package com.example.tetragon.questions.questionMathFirstGrade.tenthTopicTwoDigitNumbers

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

class UiRepresentSumOfTwoDigitNumbersFragment :
    Fragment(R.layout.fragment_ui_represent_sum_of_two_digit_numbers) {

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

    // ---------------------- Generate Problem ----------------------
    private fun generateProblem() {
        // Target is now between 2 and 10
        val number = Random.nextInt(2, 101)

        // 1. Generate all possible correct pairs for this number
        val pairs = mutableListOf<Pair<Int, Int>>()
        for (i in 1 until number) pairs.add(i to (number - i))
        correctAnswer = pairs.random()

        // 2. UI Formatting
        val sentence = "Represent $number as sum of two numbers."
        val spannable = SpannableString(sentence)
        val blue = ContextCompat.getColor(requireContext(), R.color.blue_2)

        val numberIndex = sentence.indexOf(number.toString())
        if (numberIndex != -1) {
            spannable.setSpan(ForegroundColorSpan(blue), numberIndex, numberIndex + number.toString().length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }

        val sumIndex = sentence.indexOf("sum")
        if (sumIndex != -1) {
            spannable.setSpan(ForegroundColorSpan(blue), sumIndex, sumIndex + 3, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }

        val twoIndex = sentence.indexOf("two numbers")
        if (twoIndex != -1) {
            spannable.setSpan(ForegroundColorSpan(blue), twoIndex, twoIndex + "two numbers".length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        questionText.text = spannable

        // 3. GENERATE UNIQUE OPTIONS (Max sum of 10)
        val usedSums = mutableSetOf<Int>()
        usedSums.add(number) // Lock the correct sum

        val finalOptions = mutableListOf<Pair<Int, Int>>()
        finalOptions.add(correctAnswer)

        while (finalOptions.size < 3) {
            // Changed: max fakeSum is now 10
            var fakeSum = Random.nextInt(2, 101)
            while (usedSums.contains(fakeSum)) {
                fakeSum = Random.nextInt(2, 101)
            }

            val a = Random.nextInt(1, fakeSum)
            val b = fakeSum - a

            usedSums.add(fakeSum)
            finalOptions.add(a to b)
        }

        // 4. Update UI
        val shuffled = finalOptions.shuffled()
        options.forEachIndexed { index, layout ->
            val tv = layout.getChildAt(0) as TextView
            tv.text = "${shuffled[index].first} + ${shuffled[index].second}"
            layout.setBackgroundResource(R.drawable.custom_background)
        }

        // ... [Rest of the reset state logic] ...
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

    // ---------------------- Option Click ----------------------
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
        requireActivity().findViewById<FrameLayout>(R.id.check_enabled_btn_container).visibility =
            View.VISIBLE
        requireActivity().findViewById<FrameLayout>(R.id.check_disabled_btn_container).visibility =
            View.INVISIBLE
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
        val activity = requireActivity() as Math1GradeQuestionActivity
        activity.isResultCurrentlyVisible = true

        val chosenText = (options[index].getChildAt(0) as TextView).text.toString()
        val parts = chosenText.split("+").map { it.trim().toInt() }

        // Logic to check if the sum matches the target number
        val isCorrect = (parts[0] + parts[1] == correctAnswer.first + correctAnswer.second)

        stateContainer.visibility = View.VISIBLE

        if (isCorrect) {
            playSound(R.raw.correct)
            activity.isCorrectAnswerShowing = true

            // 1. Play success animation (Mr. Square)
            activity.playSuccessAnimation()

            val isFinished = activity.incrementProgress()

            if (isFirstAttempt)
                activity.totalXp += MathGrade1Type.ADDITION_TWO_DIGIT_NUMBER_REPRESENTATION.xp

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
        answer.text = "Answer: ${correctAnswer.first} + ${correctAnswer.second}"

        applyButtonColors(R.color.green_1, R.color.green_2, R.color.green_4)
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

        applyButtonColors(R.color.red_1, R.color.red_2, R.color.red_4)
    }

    private fun setupSeeSolution(
        stateContainer: FrameLayout,
        circleState: ImageView
    ) {
        seeEnabledButton.setOnClickListener {

            seeBtn.visibility = View.GONE

            stateAnswer.text = "Solution"
            answer.text = "Answer: ${correctAnswer.first} + ${correctAnswer.second}"
            answer.visibility = View.VISIBLE

            circleState.setImageResource(R.drawable.solution_lamp_icon)
            stateContainer.setBackgroundColor(
                ContextCompat.getColor(requireContext(), R.color.gray_2)
            )

            options.forEach { layout ->
                val tv = layout.getChildAt(0) as TextView
                val parts = tv.text.toString().split("+").map { it.trim().toInt() }

                val isCorrect =
                    (parts[0] == correctAnswer.first && parts[1] == correctAnswer.second) ||
                            (parts[0] == correctAnswer.second && parts[1] == correctAnswer.first)

                layout.setBackgroundResource(
                    if (isCorrect)
                        R.drawable.option_showed
                    else
                        R.drawable.custom_background
                )
            }

            checkBtn.backgroundTintList =
                ContextCompat.getColorStateList(requireContext(), R.color.black_3)
            checkBtnBack.backgroundTintList =
                ContextCompat.getColorStateList(requireContext(), R.color.black_2)
            btnBack.setBackgroundColor(
                ContextCompat.getColor(requireContext(), R.color.gray_2)
            )

            checkBtn.text = "CONTINUE"
            isIncorrectAttempt = false
            isAnswerChecked = true
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

        isAnswerChecked = false
        isIncorrectAttempt = false
        selectedOptionIndex = null

        // Reset option backgrounds
        options.forEach {
            it.setBackgroundResource(R.drawable.custom_background)
        }

        // Reset button text
        checkBtn.text = "CHECK"

        // 🔹 DISABLE button
        checkBtn.isEnabled = false

        // 🔹 Show disabled container
        requireActivity().findViewById<FrameLayout>(R.id.check_enabled_btn_container).visibility =
            View.INVISIBLE
        requireActivity().findViewById<FrameLayout>(R.id.check_disabled_btn_container).visibility =
            View.VISIBLE

        // Reset button colors to default blue style
        btnBack.setBackgroundColor(
            ContextCompat.getColor(requireContext(), R.color.white)
        )

        checkBtn.backgroundTintList =
            ContextCompat.getColorStateList(requireContext(), R.color.blue_2)

        checkBtnBack.backgroundTintList =
            ContextCompat.getColorStateList(requireContext(), R.color.blue_1)

        // Hide state container
        requireActivity().findViewById<FrameLayout>(R.id.stateContainer).visibility =
            View.INVISIBLE

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