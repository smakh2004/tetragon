package com.example.tetragon.questions.questionMathFirstGrade.eleventhTopicProblemSolving

import android.media.MediaPlayer
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.example.tetragon.R
import com.example.tetragon.questions.questionMathFirstGrade.Math1GradeQuestionActivity
import com.example.tetragon.questions.questionMathFirstGrade.MathGrade1Type
import kotlin.random.Random

class UiFishArithmeticProblemFragment : Fragment(R.layout.fragment_ui_fish_arithmetic_problem) {

    private lateinit var fishImages: List<ImageView>
    private lateinit var options: List<LinearLayout>
    private lateinit var questionTextView: TextView

    // Activity UI Elements
    private lateinit var checkBtn: Button
    private lateinit var checkBtnBack: View
    private lateinit var btnBack: ConstraintLayout
    private lateinit var seeBtn: FrameLayout
    private lateinit var seeEnabledButton: Button
    private lateinit var stateAnswer: TextView
    private lateinit var answer: TextView

    private var totalFish = 0
    private var eatenFish = 0
    private var correctAnswer = 0
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
        questionTextView = view.findViewById(R.id.questionText)

        fishImages = listOf(
            view.findViewById(R.id.fish1), view.findViewById(R.id.fish2),
            view.findViewById(R.id.fish3), view.findViewById(R.id.fish4),
            view.findViewById(R.id.fish5), view.findViewById(R.id.fish6),
            view.findViewById(R.id.fish7), view.findViewById(R.id.fish8),
            view.findViewById(R.id.fish9), view.findViewById(R.id.fish10)
        )

        options = listOf(
            view.findViewById(R.id.option1),
            view.findViewById(R.id.option2),
            view.findViewById(R.id.option3)
        )

        // Binding to Activity Layout
        checkBtn = requireActivity().findViewById(R.id.check_enabled_btn)
        checkBtnBack = requireActivity().findViewById(R.id.check_enabled_button_background)
        btnBack = requireActivity().findViewById(R.id.btnBackground)
        seeBtn = requireActivity().findViewById(R.id.see_btn_container)
        seeEnabledButton = requireActivity().findViewById(R.id.see_enabled_btn)
        stateAnswer = requireActivity().findViewById(R.id.stateAnswer)
        answer = requireActivity().findViewById(R.id.answer)
    }

    private fun generateProblem() {
        // 1. Generate Logic
        totalFish = Random.Default.nextInt(2, 11)
        eatenFish = Random.Default.nextInt(1, totalFish)
        correctAnswer = totalFish - eatenFish

        // 2. Set Styled Question Text
        val rawText = "Aziz ate $eatenFish fish out of $totalFish, how many fish are left?"
        val spannable = SpannableString(rawText)
        val blueColor = ContextCompat.getColor(requireContext(), R.color.blue_2)

        // Helper function to color all occurrences of a string
        fun colorize(target: String) {
            var startPos = rawText.indexOf(target)
            while (startPos >= 0) {
                spannable.setSpan(
                    ForegroundColorSpan(blueColor),
                    startPos,
                    startPos + target.length,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                startPos = rawText.indexOf(target, startPos + target.length)
            }
        }

        // Apply blue strictly to the numbers and the word "fish" in the question
        colorize(eatenFish.toString())
        colorize(totalFish.toString())
        colorize("fish")

        questionTextView.text = spannable

        // 3. Setup Visuals
        fishImages.forEachIndexed { index, imageView ->
            imageView.clearColorFilter()
            when {
                index < eatenFish -> {
                    imageView.visibility = View.VISIBLE
                    imageView.setImageResource(R.drawable.fish_bones)
                }
                index < totalFish -> {
                    imageView.visibility = View.VISIBLE
                    imageView.setImageResource(R.drawable.fish)
                }
                else -> imageView.visibility = View.GONE
            }
        }

        // 4. Setup Options (Numbers stay default color)
        val optionSet = mutableSetOf(correctAnswer)
        while (optionSet.size < 3) {
            optionSet.add(Random.Default.nextInt(1, 11))
        }
        val shuffledOptions = optionSet.shuffled()

        options.forEachIndexed { index, layout ->
            val tv = layout.getChildAt(0) as TextView
            tv.text = shuffledOptions[index].toString()
            // No setTextColor here - it will use the @color/text_color from your XML
            layout.setBackgroundResource(R.drawable.custom_background)
        }

        // Reset State variables
        selectedOptionIndex = null
        isAnswerChecked = false
        isIncorrectAttempt = false
        isFirstAttempt = true

        // UI Reinforcement
        seeBtn.visibility = View.GONE
        checkBtn.isEnabled = false
        checkBtn.text = "CHECK"

        // Ensure the containers are toggled correctly for a fresh start
        requireActivity().findViewById<FrameLayout>(R.id.check_enabled_btn_container).visibility = View.INVISIBLE
        requireActivity().findViewById<FrameLayout>(R.id.check_disabled_btn_container).visibility = View.VISIBLE

        setupInitialButtonState()
    }

    private fun checkAnswer(index: Int, stateContainer: FrameLayout, circleState: ImageView) {
        isAnswerChecked = true
        val activity = requireActivity() as Math1GradeQuestionActivity
        val chosenNumber = (options[index].getChildAt(0) as TextView).text.toString().toInt()

        if (chosenNumber == correctAnswer) {
            playSound(R.raw.correct)

            // 1. Progress and XP
            val isFinished = activity.incrementProgress()
            if (isFirstAttempt) activity.totalXp += MathGrade1Type.FISH.xp

            // 2. Increment the internal count
            activity.handleCorrectAnswer()

            // 3. ALWAYS show the success UI immediately
            activity.isResultCurrentlyVisible = true
            stateContainer.visibility = View.VISIBLE
            activity.playSuccessAnimation() // Trigger Mr. Square

            checkBtn.text = if (isFinished) "FINISH" else "CONTINUE"
            isIncorrectAttempt = false
            showCorrectState(stateContainer, circleState, index)
        } else {
            // Incorrect logic remains the same
            playSound(R.raw.wrong)
            activity.isResultCurrentlyVisible = true
            stateContainer.visibility = View.VISIBLE
            isIncorrectAttempt = true
            checkBtn.text = "TRY AGAIN"
            showIncorrectState(stateContainer, circleState, index)
            setupSeeSolution(stateContainer, circleState)
        }
        isFirstAttempt = false
    }

    // --- REUSED UI LOGIC FROM YOUR TEMPLATE ---

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
                    val activity = requireActivity() as Math1GradeQuestionActivity

                    if (checkBtn.text == "FINISH") {
                        activity.navigateToXpGained()
                    } else {
                        // GATE: Check for milestone ONLY on CONTINUE click
                        val isMilestoneActive = activity.checkAndTriggerMilestone()

                        if (!isMilestoneActive) {
                            // Regular flow: next question
                            resetUIForNext()
                            activity.showRandomQuestion()
                        }
                    }
                }
            }
        }
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
        answer.visibility = View.GONE
        stateAnswer.text = "Incorrect!"
        seeBtn.visibility = View.VISIBLE
        applyButtonColors(R.color.red_1, R.color.red_2, R.color.red_4)
    }

    private fun setupSeeSolution(stateContainer: FrameLayout, circleState: ImageView) {
        seeEnabledButton.setOnClickListener {
            seeBtn.visibility = View.GONE
            stateAnswer.text = "Solution"
            answer.text = "Answer: $correctAnswer"
            answer.visibility = View.VISIBLE
            checkBtn.text = "CONTINUE"
            isAnswerChecked = true
            isIncorrectAttempt = false
            checkBtn.backgroundTintList = ContextCompat.getColorStateList(requireContext(), R.color.black_3)
            checkBtnBack.backgroundTintList = ContextCompat.getColorStateList(requireContext(), R.color.black_2)
            btnBack.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.gray_2))
            circleState.setImageResource(R.drawable.solution_lamp_icon)
            stateContainer.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.gray_2))
            options.forEach { layout ->
                val tv = layout.getChildAt(0) as TextView
                layout.setBackgroundResource(
                    if (tv.text.toString().toInt() == correctAnswer) R.drawable.option_showed
                    else R.drawable.custom_background
                )
            }
        }
    }

    private fun applyButtonColors(buttonColor: Int, backColor: Int, backgroundColor: Int) {
        checkBtn.backgroundTintList = ContextCompat.getColorStateList(requireContext(), buttonColor)
        checkBtnBack.backgroundTintList = ContextCompat.getColorStateList(requireContext(), backColor)
        btnBack.setBackgroundColor(ContextCompat.getColor(requireContext(), backgroundColor))
    }

    private fun setupInitialButtonState() {
        btnBack.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.white))
        checkBtn.backgroundTintList = ContextCompat.getColorStateList(requireContext(), R.color.blue_2)
        checkBtnBack.backgroundTintList = ContextCompat.getColorStateList(requireContext(), R.color.blue_1)
        checkBtn.isEnabled = false
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
        setupInitialButtonState()
        requireActivity().findViewById<FrameLayout>(R.id.stateContainer).visibility = View.INVISIBLE
        seeBtn.visibility = View.GONE
        stateAnswer.text = ""
        answer.visibility = View.VISIBLE
    }

    private fun resetUIForNext() {
        val activity = requireActivity() as Math1GradeQuestionActivity
        activity.isResultCurrentlyVisible = false
        activity.hideSuccessAnimation() // Clear Mr. Square

        // 1. Reset Activity Shared UI
        requireActivity().findViewById<FrameLayout>(R.id.stateContainer).visibility = View.INVISIBLE
        stateAnswer.text = ""
        answer.text = ""
        answer.visibility = View.VISIBLE
        seeBtn.visibility = View.GONE

        // 2. Reset Button to DISABLED CHECK state
        checkBtn.text = "CHECK"
        checkBtn.isEnabled = false

        // 3. Switch containers to show the gray/disabled version
        requireActivity().findViewById<FrameLayout>(R.id.check_enabled_btn_container).visibility = View.INVISIBLE
        requireActivity().findViewById<FrameLayout>(R.id.check_disabled_btn_container).visibility = View.VISIBLE

        // 4. Reset Fragment-specific visuals
        setupInitialButtonState()
        options.forEach { it.setBackgroundResource(R.drawable.custom_background) }
    }

    private fun playSound(soundResId: Int) {
        mediaPlayer?.release()
        mediaPlayer = MediaPlayer.create(requireContext(), soundResId)
        mediaPlayer?.start()
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaPlayer?.release()
    }
}