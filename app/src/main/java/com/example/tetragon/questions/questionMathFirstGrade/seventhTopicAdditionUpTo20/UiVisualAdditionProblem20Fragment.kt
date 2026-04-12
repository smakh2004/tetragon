package com.example.tetragon.questions.questionMathFirstGrade.seventhTopicAdditionUpTo20

import android.media.MediaPlayer
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.example.tetragon.R
import com.example.tetragon.questions.questionMathFirstGrade.Math1GradeQuestionActivity
import com.example.tetragon.questions.questionMathFirstGrade.MathGrade1Type
import kotlin.random.Random

class UiVisualAdditionProblem20Fragment : Fragment(R.layout.fragment_ui_visual_addition_problem20) {

    private lateinit var appleValueText: TextView
    private lateinit var grapeValueText: TextView
    private lateinit var problemAnswerText: TextView
    private lateinit var problemImage: ImageView
    private lateinit var options: List<LinearLayout>

    // Activity UI Elements
    private lateinit var checkBtn: Button
    private lateinit var checkBtnBack: View
    private lateinit var btnBack: View
    private lateinit var seeBtn: FrameLayout
    private lateinit var seeEnabledButton: Button
    private lateinit var stateAnswer: TextView
    private lateinit var answer: TextView

    private var appleValue = 0
    private var grapeValue = 0
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
        appleValueText = view.findViewById(R.id.appleValueText)
        grapeValueText = view.findViewById(R.id.grapeValueText)
        problemAnswerText = view.findViewById(R.id.problemAnswerText)
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
    }

    private fun generateProblem() {
        // 1. Pick a target sum between 2 and 10 (First grade limit)
        correctAnswer = Random.nextInt(2, 21)

        // 2. Split the sum into two parts for the fruits
        // This ensures apple + grape ALWAYS equals a sum <= 10
        appleValue = Random.nextInt(1, correctAnswer)
        grapeValue = correctAnswer - appleValue

        // Update UI Legend
        appleValueText.text = "= $appleValue"
        grapeValueText.text = "= $grapeValue"

        // Reset Answer Box
        problemAnswerText.visibility = View.GONE
        problemImage.setImageResource(R.drawable.answer_blue_box)

        // 3. Generate Options (Restricted to 1-10)
        val optionSet = mutableSetOf(correctAnswer)
        while (optionSet.size < 3) {
            val fake = Random.nextInt(1, 21)
            if (fake != correctAnswer) optionSet.add(fake)
        }
        val shuffledOptions = optionSet.shuffled()

        options.forEachIndexed { index, layout ->
            val tv = layout.getChildAt(0) as TextView
            tv.text = shuffledOptions[index].toString()
            layout.setBackgroundResource(R.drawable.custom_background)
        }

        // 4. Reset States
        selectedOptionIndex = null
        isAnswerChecked = false
        isIncorrectAttempt = false
        isFirstAttempt = true
        seeBtn.visibility = View.GONE
        checkBtn.isEnabled = false
        checkBtn.text = "CHECK"

        requireActivity().findViewById<View>(R.id.check_enabled_btn_container).visibility = View.INVISIBLE
        requireActivity().findViewById<View>(R.id.check_disabled_btn_container).visibility = View.VISIBLE
        setupInitialButtonState()
    }

    private fun checkAnswer(index: Int) {
        isAnswerChecked = true
        val activity = requireActivity() as Math1GradeQuestionActivity
        activity.isResultCurrentlyVisible = true
        val chosen = (options[index].getChildAt(0) as TextView).text.toString().toInt()
        val stateContainer = requireActivity().findViewById<FrameLayout>(R.id.stateContainer)
        val circleState = requireActivity().findViewById<ImageView>(R.id.circleState)

        stateContainer.visibility = View.VISIBLE
        problemAnswerText.text = chosen.toString()
        problemAnswerText.visibility = View.VISIBLE

        if (chosen == correctAnswer) {
            playSound(R.raw.correct)
            activity.isCorrectAnswerShowing = true

            // 1. Play success animation
            activity.playSuccessAnimation()
            problemImage.setImageResource(R.drawable.answer_correct_box)

            val isFinished = activity.incrementProgress()
            if (isFirstAttempt) {
                activity.totalXp += MathGrade1Type.ADDITION_VISUAL_PROBLEM_20.xp
            }

            // 2. Log progress for milestones/streaks in activity
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
            setupSeeSolution(stateContainer, circleState)
        }
        isFirstAttempt = false
    }

    private fun setupOptionClicks() {
        options.forEachIndexed { index, layout ->
            layout.setOnClickListener {
                if (isAnswerChecked) return@setOnClickListener
                selectedOptionIndex = index
                highlightSelectedOption(index)
                enableCheckButton()

                // Show current choice in the box
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
        requireActivity().findViewById<View>(R.id.check_enabled_btn_container).visibility = View.VISIBLE
        requireActivity().findViewById<View>(R.id.check_disabled_btn_container).visibility = View.INVISIBLE
    }

    private fun setupCheckButton() {
        checkBtn.setOnClickListener {
            if (!isAnswerChecked) {
                selectedOptionIndex?.let { checkAnswer(it) }
            } else if (isIncorrectAttempt) {
                resetForTryAgain()
            } else {
                val activity = requireActivity() as Math1GradeQuestionActivity
                if (checkBtn.text == "FINISH") {
                    activity.navigateToXpGained()
                } else {
                    // GATE: Trigger milestone ONLY after clicking CONTINUE
                    val isMilestoneActive = activity.checkAndTriggerMilestone()

                    if (!isMilestoneActive) {
                        resetUIForNext()
                        activity.showRandomQuestion()
                    }
                }
            }
        }
    }

    private fun setupSeeSolution(stateContainer: FrameLayout, circleState: ImageView) {
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
                val valText = (layout.getChildAt(0) as TextView).text.toString().toInt()
                layout.setBackgroundResource(if (valText == correctAnswer) R.drawable.option_showed else R.drawable.custom_background)
            }
        }
    }

    // Standard UI Reset Methods
    private fun resetForTryAgain() {
        val activity = requireActivity() as Math1GradeQuestionActivity
        activity.isResultCurrentlyVisible = false

        // 1. Fragment internal state reset
        isAnswerChecked = false
        isIncorrectAttempt = false
        selectedOptionIndex = null

        // 2. Reset Fragment UI elements
        options.forEach { it.setBackgroundResource(R.drawable.custom_background) }
        problemAnswerText.visibility = View.GONE
        problemImage.setImageResource(R.drawable.answer_blue_box)

        // 3. Reset Activity UI elements (The Bottom Bar)
        val stateContainer = requireActivity().findViewById<FrameLayout>(R.id.stateContainer)
        stateContainer.visibility = View.INVISIBLE

        // --- THE FIX: Explicitly hide seeBtn and reset answer visibility ---
        seeBtn.visibility = View.GONE
        stateAnswer.text = ""
        answer.visibility = View.VISIBLE
        // ------------------------------------------------------------------

        // 4. Reset Button States
        checkBtn.text = "CHECK"
        checkBtn.isEnabled = false

        // Reset the button background containers
        requireActivity().findViewById<View>(R.id.check_enabled_btn_container).visibility = View.INVISIBLE
        requireActivity().findViewById<View>(R.id.check_disabled_btn_container).visibility = View.VISIBLE

        // Restore blue/white theme colors
        setupInitialButtonState()
    }

    private fun resetUIForNext() {
        val activity = requireActivity() as Math1GradeQuestionActivity
        activity.isResultCurrentlyVisible = false
        activity.hideSuccessAnimation() // Ensure Mr. Square is hidden

        requireActivity().findViewById<View>(R.id.stateContainer).visibility = View.INVISIBLE
        stateAnswer.text = ""
        answer.text = ""
        answer.visibility = View.VISIBLE
        seeBtn.visibility = View.GONE
        checkBtn.text = "CHECK"

        setupInitialButtonState()
    }

    private fun setupInitialButtonState() {
        btnBack.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.white))
        checkBtn.backgroundTintList = ContextCompat.getColorStateList(requireContext(), R.color.blue_2)
        checkBtnBack.backgroundTintList = ContextCompat.getColorStateList(requireContext(), R.color.blue_1)
    }

    private fun applyButtonColors(buttonColor: Int, backColor: Int, backgroundColor: Int) {
        checkBtn.backgroundTintList = ContextCompat.getColorStateList(requireContext(), buttonColor)
        checkBtnBack.backgroundTintList = ContextCompat.getColorStateList(requireContext(), backColor)
        btnBack.setBackgroundColor(ContextCompat.getColor(requireContext(), backgroundColor))
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